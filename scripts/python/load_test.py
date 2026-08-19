#!/usr/bin/env python3
"""High-load simulator for the Fenrir-s relay.

Spins up many concurrent WebSocket clients that hammer the relay with a
weighted mix of EVENT / REQ / COUNT, plus an optional pool of clients that
repeatedly churn connections to stress the NIP-42 AUTH handshake. Prints
live throughput and a final latency/error summary.

Examples:
    # 50 mixed clients for 30s, ramped up over 5s
    python load_test.py --relay ws://localhost:6724

    # heavier publish-only load, no ramp-up, higher concurrency
    python load_test.py --clients 200 --ramp-up 0 --event-weight 10 --req-weight 1 --count-weight 1

    # also stress the AUTH handshake (relay must have AUTH_ENABLED=true)
    python load_test.py --clients 100 --auth-clients 20
"""

import argparse
import asyncio
import json
import random
import statistics
import string
import sys
import time
import uuid
from dataclasses import dataclass, field

import websockets
from websockets.exceptions import ConnectionClosed
from nostr_sdk import EventBuilder, Filter, Kind, Keys, RelayUrl

DEFAULT_RELAY = "ws://localhost:6724"
OPS = ("EVENT", "REQ", "COUNT", "AUTH")


def sub_id(prefix: str) -> str:
    return f"{prefix}-{uuid.uuid4().hex[:8]}"


def random_content(n: int = 120) -> str:
    return "".join(random.choices(string.ascii_letters + " ", k=n))


@dataclass
class Stats:
    latencies_ms: dict = field(default_factory=lambda: {op: [] for op in OPS})
    ok: dict = field(default_factory=lambda: {op: 0 for op in OPS})
    fail: dict = field(default_factory=lambda: {op: 0 for op in OPS})
    errors: list = field(default_factory=list)

    def record(self, op: str, latency_ms: float, ok: bool, note: str | None = None) -> None:
        self.latencies_ms[op].append(latency_ms)
        if ok:
            self.ok[op] += 1
        else:
            self.fail[op] += 1
            if note:
                self.errors.append(f"{op}: {note}")

    def totals(self) -> dict:
        return {op: self.ok[op] + self.fail[op] for op in OPS}


async def recv_until(ws, predicate, timeout: float):
    """Read frames until predicate(msg) is True; return the matching msg, or None on timeout."""
    loop = asyncio.get_event_loop()
    deadline = loop.time() + timeout
    while True:
        remaining = deadline - loop.time()
        if remaining <= 0:
            return None
        raw = await asyncio.wait_for(ws.recv(), timeout=remaining)
        try:
            msg = json.loads(raw)
        except json.JSONDecodeError:
            continue
        if predicate(msg):
            return msg


async def do_event(ws, keys: Keys, stats: Stats, timeout: float) -> None:
    note = EventBuilder.text_note(random_content()).sign_with_keys(keys)
    t0 = time.monotonic()
    await ws.send(json.dumps(["EVENT", json.loads(note.as_json())]))
    try:
        msg = await recv_until(
            ws, lambda m: isinstance(m, list) and m[0] == "OK" and m[1] == note.id().to_hex(), timeout
        )
    except asyncio.TimeoutError:
        msg = None
    elapsed = (time.monotonic() - t0) * 1000
    if msg is None:
        stats.record("EVENT", elapsed, False, "timed out waiting for OK")
    else:
        ok = bool(msg[2])
        stats.record("EVENT", elapsed, ok, None if ok else (str(msg[3]) if len(msg) > 3 else "rejected"))


async def do_req(ws, keys: Keys, stats: Stats, timeout: float, limit: int) -> None:
    sid = sub_id("load-req")
    filt = Filter().authors([keys.public_key()]).kinds([Kind(1)]).limit(limit)
    t0 = time.monotonic()
    await ws.send(json.dumps(["REQ", sid, json.loads(filt.as_json())]))
    try:
        # EOSE = subscription accepted and ran; CLOSED = rejected (e.g. auth-required) - both are terminal
        msg = await recv_until(
            ws,
            lambda m: isinstance(m, list) and m[1] == sid and m[0] in ("EOSE", "CLOSED"),
            timeout,
        )
    except asyncio.TimeoutError:
        msg = None
    elapsed = (time.monotonic() - t0) * 1000
    if msg is None:
        stats.record("REQ", elapsed, False, "no EOSE/CLOSED within timeout")
    elif msg[0] == "EOSE":
        stats.record("REQ", elapsed, True)
        await ws.send(json.dumps(["CLOSE", sid]))
    else:
        stats.record("REQ", elapsed, False, str(msg[2]) if len(msg) > 2 else "closed")


async def do_count(ws, keys: Keys, stats: Stats, timeout: float) -> None:
    sid = sub_id("load-count")
    filt = Filter().authors([keys.public_key()]).kinds([Kind(1)])
    t0 = time.monotonic()
    await ws.send(json.dumps(["COUNT", sid, json.loads(filt.as_json())]))
    try:
        # COUNT = accepted; CLOSED = rejected (e.g. auth-required) - both are terminal
        msg = await recv_until(
            ws,
            lambda m: isinstance(m, list) and m[1] == sid and m[0] in ("COUNT", "CLOSED"),
            timeout,
        )
    except asyncio.TimeoutError:
        msg = None
    elapsed = (time.monotonic() - t0) * 1000
    if msg is None:
        stats.record("COUNT", elapsed, False, "no COUNT/CLOSED within timeout")
    elif msg[0] == "COUNT":
        stats.record("COUNT", elapsed, True)
    else:
        stats.record("COUNT", elapsed, False, str(msg[2]) if len(msg) > 2 else "closed")


async def try_authenticate(ws, keys: Keys, relay_url: RelayUrl, timeout: float) -> tuple[bool, str]:
    """If the relay greets us with a NIP-42 AUTH challenge, complete the handshake.
    `relay_url` is what gets signed into the AUTH event's "relay" tag - the relay
    checks its domain against its own configured RELAY_URL, which is NOT
    necessarily the address this script connects to (e.g. testing via
    ws://localhost:PORT against a relay configured with a public hostname).
    Use --auth-relay-url to override it independently of --relay.
    Returns (authenticated_or_not_required, reason)."""
    try:
        raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
    except asyncio.TimeoutError:
        return True, "no challenge offered"

    try:
        msg = json.loads(raw)
    except json.JSONDecodeError:
        return True, "non-JSON first frame"
    if not (isinstance(msg, list) and len(msg) >= 2 and msg[0] == "AUTH"):
        return True, "no AUTH challenge as first frame"

    challenge = msg[1]
    auth_event = EventBuilder.auth(challenge, relay_url).sign_with_keys(keys)
    await ws.send(json.dumps(["AUTH", json.loads(auth_event.as_json())]))
    try:
        reply = await recv_until(
            ws, lambda m: isinstance(m, list) and m[0] == "OK" and m[1] == auth_event.id().to_hex(), timeout
        )
    except asyncio.TimeoutError:
        reply = None
    if reply is None:
        return False, "timed out waiting for OK"
    ok = bool(reply[2])
    return ok, ("" if ok else (str(reply[3]) if len(reply) > 3 else "rejected"))


async def mixed_client(
    client_id: int,
    relay: str,
    auth_relay_url: RelayUrl,
    weights: dict,
    stats: Stats,
    stop_event: asyncio.Event,
    think_time: float,
    op_timeout: float,
    req_limit: int,
) -> None:
    """Persistent connection issuing a weighted-random mix of EVENT/REQ/COUNT until stopped."""
    keys = Keys.generate()
    ops = list(weights.keys())
    probs = list(weights.values())
    try:
        async with websockets.connect(relay, open_timeout=10) as ws:
            authed, reason = await try_authenticate(ws, keys, auth_relay_url, op_timeout)
            if not authed:
                stats.errors.append(f"client-{client_id}: AUTH rejected on connect: {reason}")
                return

            while not stop_event.is_set():
                op = random.choices(ops, weights=probs, k=1)[0]
                try:
                    if op == "EVENT":
                        await do_event(ws, keys, stats, op_timeout)
                    elif op == "REQ":
                        await do_req(ws, keys, stats, op_timeout, req_limit)
                    elif op == "COUNT":
                        await do_count(ws, keys, stats, op_timeout)
                except ConnectionClosed as e:
                    stats.record(op, 0.0, False, f"connection closed: {e}")
                    return
                if think_time > 0:
                    await asyncio.sleep(random.uniform(0, think_time))
    except (OSError, ConnectionClosed, asyncio.TimeoutError) as e:
        stats.errors.append(f"client-{client_id}: connect failed: {e!r}")


async def auth_client(
    client_id: int,
    relay: str,
    auth_relay_url: RelayUrl,
    stats: Stats,
    stop_event: asyncio.Event,
    challenge_timeout: float,
    cooldown: float,
) -> None:
    """Repeatedly reconnects and completes the NIP-42 AUTH handshake, stressing
    connection churn + auth under load. Stops itself if the relay never issues
    a challenge (e.g. AUTH_ENABLED=false)."""
    while not stop_event.is_set():
        keys = Keys.generate()
        t0 = time.monotonic()
        try:
            async with websockets.connect(relay, open_timeout=10) as ws:
                ok, reason = await try_authenticate(ws, keys, auth_relay_url, challenge_timeout)
                elapsed = (time.monotonic() - t0) * 1000
                if reason in ("no challenge offered", "no AUTH challenge as first frame"):
                    print(f"auth-client-{client_id}: no AUTH challenge received, stopping this worker")
                    return
                stats.record("AUTH", elapsed, ok, None if ok else reason)
        except (OSError, ConnectionClosed) as e:
            stats.errors.append(f"auth-client-{client_id}: {e!r}")
        if cooldown > 0:
            await asyncio.sleep(random.uniform(0, cooldown))


async def delayed_start(delay: float, coro) -> None:
    if delay > 0:
        await asyncio.sleep(delay)
    await coro


async def reporter(stats: Stats, stop_event: asyncio.Event, interval: float) -> None:
    prev = {op: 0 for op in OPS}
    while True:
        await asyncio.sleep(interval)
        totals = stats.totals()
        rates = {op: (totals[op] - prev[op]) / interval for op in OPS}
        prev = totals
        active = {op: r for op, r in rates.items() if totals[op] > 0}
        if active:
            print("[t] " + " ".join(f"{op}={rates[op]:.1f}/s(ok={stats.ok[op]},fail={stats.fail[op]})" for op in active))
        if stop_event.is_set():
            return


def percentile(sorted_vals: list, p: float) -> float:
    if not sorted_vals:
        return 0.0
    k = min(len(sorted_vals) - 1, int(len(sorted_vals) * p))
    return sorted_vals[k]


def print_summary(stats: Stats, duration: float) -> None:
    print("\n" + "=" * 78)
    print(f"LOAD TEST SUMMARY  ({duration:.1f}s wall clock)")
    print("=" * 78)
    for op in OPS:
        total = stats.ok[op] + stats.fail[op]
        if total == 0:
            continue
        lats = sorted(stats.latencies_ms[op])
        avg = statistics.mean(lats) if lats else 0.0
        print(
            f"{op:6s}  total={total:<7d} ok={stats.ok[op]:<7d} fail={stats.fail[op]:<7d} "
            f"rps={total / duration:7.1f}  avg={avg:8.1f}ms  p50={percentile(lats, 0.50):8.1f}ms "
            f"p95={percentile(lats, 0.95):8.1f}ms  p99={percentile(lats, 0.99):8.1f}ms  "
            f"max={(lats[-1] if lats else 0):8.1f}ms"
        )
    if stats.errors:
        print(f"\n{len(stats.errors)} connection-level errors (showing up to 10):")
        for e in stats.errors[:10]:
            print(f"  - {e}")


async def run(args: argparse.Namespace) -> int:
    weights = {"EVENT": args.event_weight, "REQ": args.req_weight, "COUNT": args.count_weight}
    weights = {op: w for op, w in weights.items() if w > 0}
    if not weights and args.auth_clients == 0:
        print("all op weights are 0 and --auth-clients is 0, nothing to do", file=sys.stderr)
        return 1

    auth_relay_url = RelayUrl.parse(args.auth_relay_url or args.relay)

    stats = Stats()
    stop_event = asyncio.Event()
    tasks = []

    for i in range(args.clients):
        delay = (i / args.clients) * args.ramp_up if args.clients > 1 and args.ramp_up > 0 else 0.0
        tasks.append(asyncio.create_task(delayed_start(
            delay,
            mixed_client(
                i, args.relay, auth_relay_url, weights, stats, stop_event,
                args.think_time, args.op_timeout, args.req_limit,
            ),
        )))

    for i in range(args.auth_clients):
        delay = (i / args.auth_clients) * args.ramp_up if args.auth_clients > 1 and args.ramp_up > 0 else 0.0
        tasks.append(asyncio.create_task(delayed_start(
            delay, auth_client(i, args.relay, auth_relay_url, stats, stop_event, args.op_timeout, args.think_time),
        )))

    report_task = asyncio.create_task(reporter(stats, stop_event, args.report_interval)) if args.report_interval > 0 else None

    print(f"target: {args.relay}")
    print(f"clients: {args.clients} mixed  +  {args.auth_clients} auth-churn")
    print(f"mix: {weights}   duration={args.duration}s  ramp_up={args.ramp_up}s  think_time<={args.think_time}s\n")

    t_start = time.monotonic()
    await asyncio.sleep(args.duration)
    stop_event.set()
    elapsed = time.monotonic() - t_start

    await asyncio.gather(*tasks, return_exceptions=True)
    if report_task:
        await asyncio.gather(report_task, return_exceptions=True)

    print_summary(stats, elapsed)
    return 0 if sum(stats.fail.values()) == 0 else 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="High-load simulator for the Fenrir-s relay (EVENT/REQ/COUNT/AUTH)")
    parser.add_argument("--relay", default=DEFAULT_RELAY, help="relay WebSocket URL to connect to")
    parser.add_argument(
        "--auth-relay-url",
        default=None,
        help="URL signed into the NIP-42 AUTH event's 'relay' tag, if different from --relay "
        "(the server checks this against its own configured RELAY_URL, not the address you connect to - "
        "e.g. testing via ws://localhost:PORT against a relay configured with a public hostname). "
        "Defaults to --relay.",
    )
    parser.add_argument("--clients", type=int, default=50, help="concurrent persistent clients issuing EVENT/REQ/COUNT")
    parser.add_argument("--auth-clients", type=int, default=0, help="concurrent clients stress-testing the NIP-42 AUTH handshake (needs AUTH_ENABLED=true on the relay)")
    parser.add_argument("--duration", type=float, default=30.0, help="test duration in seconds")
    parser.add_argument("--ramp-up", type=float, default=5.0, help="spread client startup over this many seconds")
    parser.add_argument("--think-time", type=float, default=0.0, help="max random delay (s) between ops per client; 0 = fire as fast as possible")
    parser.add_argument("--op-timeout", type=float, default=5.0, help="per-op response timeout (s)")
    parser.add_argument("--req-limit", type=int, default=20, help="'limit' used in REQ filters")
    parser.add_argument("--event-weight", type=float, default=5.0, help="relative weight of EVENT ops in the mix")
    parser.add_argument("--req-weight", type=float, default=3.0, help="relative weight of REQ ops in the mix")
    parser.add_argument("--count-weight", type=float, default=2.0, help="relative weight of COUNT ops in the mix")
    parser.add_argument("--report-interval", type=float, default=5.0, help="live throughput report interval (s); 0 to disable")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    try:wr
        exit_code = asyncio.run(run(args))
    except KeyboardInterrupt:
        exit_code = 130
    sys.exit(exit_code)


if __name__ == "__main__":
    main()