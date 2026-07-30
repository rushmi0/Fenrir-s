#!/usr/bin/env python3

import argparse
import asyncio
import json
import sys
import time
import uuid
from dataclasses import dataclass

import websockets
from nostr_sdk import EventBuilder, Filter, Kind, Keys, Tag

DEFAULT_RELAY = "ws://localhost:6724"
EOSE_TIMEOUT = 5.0
PUSH_TIMEOUT = 3.0   # generous vs. expected low-ms delivery; old poll could take up to 1400ms


def log(label: str, payload: str) -> None:
    print(f"{label} {payload}")


def sub_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:8]}"


async def send(ws, msg) -> None:
    raw = json.dumps(msg)
    await ws.send(raw)
    log("->", raw)


async def recv_until(ws, predicate, timeout: float):
    """Read frames until predicate(msg) is True; return the matching msg, or None on timeout."""
    loop = asyncio.get_event_loop()
    deadline = loop.time() + timeout
    while True:
        remaining = deadline - loop.time()
        if remaining <= 0:
            return None
        try:
            raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
        except asyncio.TimeoutError:
            return None
        log("<-", raw)
        msg = json.loads(raw)
        if predicate(msg):
            return msg


async def wait_eose(ws, subscription_id: str, timeout: float = EOSE_TIMEOUT) -> bool:
    msg = await recv_until(
        ws, lambda m: isinstance(m, list) and m[0] == "EOSE" and m[1] == subscription_id, timeout
    )
    return msg is not None


async def wait_event(ws, subscription_id: str, event_id: str, timeout: float):
    return await recv_until(
        ws,
        lambda m: isinstance(m, list) and m[0] == "EVENT" and m[1] == subscription_id and m[2].get("id") == event_id,
        timeout,
    )


async def wait_count(ws, subscription_id: str, timeout: float):
    return await recv_until(
        ws, lambda m: isinstance(m, list) and m[0] == "COUNT" and m[1] == subscription_id, timeout
    )


async def wait_ok(ws, event_id: str, timeout: float = EOSE_TIMEOUT) -> tuple[bool, str]:
    msg = await recv_until(
        ws, lambda m: isinstance(m, list) and m[0] == "OK" and m[1] == event_id, timeout
    )
    if msg is None:
        return False, "timed out waiting for OK"
    return bool(msg[2]), (str(msg[3]) if len(msg) > 3 else "")


async def publish(ws, event) -> tuple[bool, str]:
    await send(ws, ["EVENT", json.loads(event.as_json())])
    return await wait_ok(ws, event.id().to_hex())


# ---------------------------------------------------------------------------
# Result tracking
# ---------------------------------------------------------------------------

@dataclass
class TestResult:
    name: str
    passed: bool
    detail: str = ""


RESULTS: list[TestResult] = []


def record(name: str, passed: bool, detail: str = "") -> None:
    RESULTS.append(TestResult(name, passed, detail))
    status = "PASS" if passed else "FAIL"
    print(f"[{status}] {name}{': ' + detail if detail else ''}")


# ---------------------------------------------------------------------------
# Tests
# ---------------------------------------------------------------------------

async def test_live_push_req(relay: str) -> None:
    """A REQ subscription must receive a newly-saved matching event pushed in
    real time - no waiting for a DB poll tick."""
    keys = Keys.generate()
    async with websockets.connect(relay) as sub_ws, websockets.connect(relay) as pub_ws:
        sid = sub_id("live-req")
        filt = Filter().authors([keys.public_key()]).kinds([Kind(1)])
        await send(sub_ws, ["REQ", sid, json.loads(filt.as_json())])
        if not await wait_eose(sub_ws, sid):
            record("live_push_req", False, "no EOSE received")
            return

        note = EventBuilder.text_note("live-push-test").sign_with_keys(keys)
        ok, msg = await publish(pub_ws, note)
        if not ok:
            record("live_push_req", False, f"publish rejected: {msg}")
            return
        t_committed = time.monotonic()

        arrived = await wait_event(sub_ws, sid, note.id().to_hex(), PUSH_TIMEOUT)
        elapsed_ms = (time.monotonic() - t_committed) * 1000
        if arrived:
            record("live_push_req", True, f"pushed in {elapsed_ms:.1f} ms after OK")
        else:
            record("live_push_req", False, f"event not pushed within {PUSH_TIMEOUT}s")


async def test_live_count(relay: str) -> None:
    """A COUNT subscription's running counter must increment on a new
    matching event without re-querying the database."""
    keys = Keys.generate()
    async with websockets.connect(relay) as sub_ws, websockets.connect(relay) as pub_ws:
        sid = sub_id("live-count")
        filt = Filter().authors([keys.public_key()]).kinds([Kind(1)])
        await send(sub_ws, ["COUNT", sid, json.loads(filt.as_json())])

        initial = await wait_count(sub_ws, sid, EOSE_TIMEOUT)
        if initial is None:
            record("live_count", False, "no initial COUNT received")
            return
        initial_count = initial[2].get("count", 0)
        if not await wait_eose(sub_ws, sid):
            record("live_count", False, "no EOSE received")
            return

        note = EventBuilder.text_note("live-count-test").sign_with_keys(keys)
        ok, msg = await publish(pub_ws, note)
        if not ok:
            record("live_count", False, f"publish rejected: {msg}")
            return

        updated = await wait_count(sub_ws, sid, PUSH_TIMEOUT)
        if updated is None:
            record("live_count", False, f"no live COUNT update within {PUSH_TIMEOUT}s")
            return
        updated_count = updated[2].get("count", 0)
        record("live_count", updated_count == initial_count + 1, f"{initial_count} -> {updated_count}")


async def test_tag_index_push(relay: str) -> None:
    """A subscription filtered by `#e` tag must be routed via the tag index,
    exercising the path distinct from author/kind indexing."""
    keys = Keys.generate()
    async with websockets.connect(relay) as sub_ws, websockets.connect(relay) as pub_ws:
        root = EventBuilder.text_note("root event for tag test").sign_with_keys(keys)
        ok, msg = await publish(pub_ws, root)
        if not ok:
            record("tag_index_push", False, f"root publish rejected: {msg}")
            return

        sid = sub_id("live-tag")
        filt = Filter().events([root.id()])
        await send(sub_ws, ["REQ", sid, json.loads(filt.as_json())])
        if not await wait_eose(sub_ws, sid):
            record("tag_index_push", False, "no EOSE received")
            return

        reply = EventBuilder.text_note("reply referencing root").tags([Tag.event(root.id())]).sign_with_keys(keys)
        ok, msg = await publish(pub_ws, reply)
        if not ok:
            record("tag_index_push", False, f"reply publish rejected: {msg}")
            return
        t_committed = time.monotonic()

        arrived = await wait_event(sub_ws, sid, reply.id().to_hex(), PUSH_TIMEOUT)
        elapsed_ms = (time.monotonic() - t_committed) * 1000
        record(
            "tag_index_push",
            arrived is not None,
            f"pushed in {elapsed_ms:.1f} ms after OK" if arrived else "not delivered",
        )


async def test_filter_isolation(relay: str) -> None:
    """Two subscriptions with disjoint filters must each receive only their
    own matching events - the indexed registry must not cross-deliver."""
    keys_a = Keys.generate()
    keys_b = Keys.generate()
    async with websockets.connect(relay) as ws_a, websockets.connect(relay) as ws_b, websockets.connect(relay) as pub_ws:
        sid_a = sub_id("iso-a")
        sid_b = sub_id("iso-b")
        await send(ws_a, ["REQ", sid_a, json.loads(Filter().authors([keys_a.public_key()]).as_json())])
        await send(ws_b, ["REQ", sid_b, json.loads(Filter().authors([keys_b.public_key()]).as_json())])
        if not await wait_eose(ws_a, sid_a) or not await wait_eose(ws_b, sid_b):
            record("filter_isolation", False, "EOSE missing")
            return

        note_a = EventBuilder.text_note("only for A").sign_with_keys(keys_a)
        ok, msg = await publish(pub_ws, note_a)
        if not ok:
            record("filter_isolation", False, f"publish rejected: {msg}")
            return

        got_a = await wait_event(ws_a, sid_a, note_a.id().to_hex(), PUSH_TIMEOUT)
        # B should NOT see it; a short timeout suffices since delivery, if it
        # happened at all, would already have arrived by the time A's did.
        got_b = await wait_event(ws_b, sid_b, note_a.id().to_hex(), 1.0)

        record(
            "filter_isolation",
            got_a is not None and got_b is None,
            f"A received={got_a is not None}, B received={got_b is not None}",
        )


async def test_close_cleanup(relay: str) -> None:
    """After CLOSE, the subscription must be fully unregistered - a
    subsequently-published matching event must NOT be delivered."""
    keys = Keys.generate()
    async with websockets.connect(relay) as sub_ws, websockets.connect(relay) as pub_ws:
        sid = sub_id("close-cleanup")
        filt = Filter().authors([keys.public_key()]).kinds([Kind(1)])
        await send(sub_ws, ["REQ", sid, json.loads(filt.as_json())])
        if not await wait_eose(sub_ws, sid):
            record("close_cleanup", False, "no EOSE received")
            return

        await send(sub_ws, ["CLOSE", sid])

        note = EventBuilder.text_note("after-close").sign_with_keys(keys)
        ok, msg = await publish(pub_ws, note)
        if not ok:
            record("close_cleanup", False, f"publish rejected: {msg}")
            return

        arrived = await wait_event(sub_ws, sid, note.id().to_hex(), 1.5)
        record(
            "close_cleanup",
            arrived is None,
            "no leak: event correctly not delivered after CLOSE"
            if arrived is None
            else "LEAK: event delivered to a closed subscription",
        )


async def test_disconnect_cleanup(relay: str) -> None:
    """Closing the whole WebSocket (not a CLOSE command) must also unregister
    the subscription (Gateway.onClose -> registry.unregisterSession). Server
    memory can't be inspected from here, so this proves the relay stays
    healthy and responsive for OTHER clients right after an abrupt disconnect."""
    keys = Keys.generate()
    sid = sub_id("disconnect-cleanup")
    filt = Filter().authors([keys.public_key()]).kinds([Kind(1)])

    ws = await websockets.connect(relay)
    await send(ws, ["REQ", sid, json.loads(filt.as_json())])
    await wait_eose(ws, sid)
    await ws.close()

    await asyncio.sleep(0.5)  # let the server process the close
    async with websockets.connect(relay) as probe_ws:
        probe_sid = sub_id("probe")
        await send(probe_ws, ["REQ", probe_sid, json.loads(Filter().limit(1).as_json())])
        ok = await wait_eose(probe_ws, probe_sid)
        record("disconnect_cleanup", ok, "relay healthy after abrupt disconnect" if ok else "relay unresponsive")


async def test_latency_benchmark(relay: str, n: int) -> None:
    """Publish N events into an active REQ subscription and measure push
    latency (OK confirmation -> EVENT delivery) - the number this whole
    refactor exists to shrink from 'up to 1400ms' down to low milliseconds."""
    keys = Keys.generate()
    async with websockets.connect(relay) as sub_ws, websockets.connect(relay) as pub_ws:
        sid = sub_id("bench")
        filt = Filter().authors([keys.public_key()]).kinds([Kind(1)])
        await send(sub_ws, ["REQ", sid, json.loads(filt.as_json())])
        if not await wait_eose(sub_ws, sid):
            record("latency_benchmark", False, "no EOSE received")
            return

        latencies_ms = []
        for i in range(n):
            note = EventBuilder.text_note(f"bench-{i}").sign_with_keys(keys)
            ok, _ = await publish(pub_ws, note)
            if not ok:
                continue
            t_committed = time.monotonic()
            arrived = await wait_event(sub_ws, sid, note.id().to_hex(), PUSH_TIMEOUT)
            if arrived:
                latencies_ms.append((time.monotonic() - t_committed) * 1000)

        if not latencies_ms:
            record("latency_benchmark", False, "no events delivered")
            return

        latencies_ms.sort()
        avg = sum(latencies_ms) / len(latencies_ms)
        p50 = latencies_ms[len(latencies_ms) // 2]
        p99 = latencies_ms[min(len(latencies_ms) - 1, int(len(latencies_ms) * 0.99))]
        record(
            "latency_benchmark",
            True,
            f"n={len(latencies_ms)}/{n} min={latencies_ms[0]:.1f}ms avg={avg:.1f}ms "
            f"p50={p50:.1f}ms p99={p99:.1f}ms max={latencies_ms[-1]:.1f}ms",
        )


# ---------------------------------------------------------------------------
# Runner
# ---------------------------------------------------------------------------

async def run_all(relay: str, bench_n: int) -> int:
    print(f"connecting to: {relay}\n")

    tests = [
        test_live_push_req,
        test_live_count,
        test_tag_index_push,
        test_filter_isolation,
        test_close_cleanup,
        test_disconnect_cleanup,
    ]
    for t in tests:
        print(f"--- {t.__name__} ---")
        try:
            await t(relay)
        except Exception as e:
            record(t.__name__, False, f"exception: {e!r}")
        print()

    if bench_n > 0:
        print(f"--- test_latency_benchmark (n={bench_n}) ---")
        try:
            await test_latency_benchmark(relay, bench_n)
        except Exception as e:
            record("latency_benchmark", False, f"exception: {e!r}")
        print()

    passed = sum(1 for r in RESULTS if r.passed)
    total = len(RESULTS)
    print(f"{passed}/{total} tests passed")
    return 0 if passed == total else 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Event-driven pub/sub test client for the Fenrir-s relay")
    parser.add_argument("--relay", default=DEFAULT_RELAY, help="relay WebSocket URL")
    parser.add_argument(
        "--bench-n", type=int, default=20, help="number of events for the latency benchmark (0 to skip)"
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    exit_code = asyncio.run(run_all(args.relay, args.bench_n))
    sys.exit(exit_code)


if __name__ == "__main__":
    main()