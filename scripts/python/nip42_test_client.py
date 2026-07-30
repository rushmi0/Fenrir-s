#!/usr/bin/env python3

import argparse
import asyncio
import json
import sys

import websockets
from nostr_sdk import Keys, EventBuilder, RelayUrl

CHALLENGE_WAIT_TIMEOUT = 5.0   # seconds to wait for the relay to send AUTH
AUTH_RESPONSE_TIMEOUT = 5.0    # seconds to wait for OK after sending AUTH


def log(label: str, payload: str) -> None:
    print(f"{label} {payload}")


async def wait_for_auth_challenge(ws, timeout: float) -> str | None:
    """Read messages until an AUTH challenge arrives, a REQ is provoked, or timeout."""
    try:
        while True:
            raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
            log("<-", raw)
            msg = json.loads(raw)
            if isinstance(msg, list) and len(msg) >= 2 and msg[0] == "AUTH":
                return msg[1]
    except asyncio.TimeoutError:
        return None


async def send_probe_req(ws) -> None:
    """Some relays only emit the AUTH challenge lazily, after a client action.
    Send a harmless REQ to nudge the relay into sending it."""
    req = ["REQ", "nip42-probe", {"limit": 1}]
    await ws.send(json.dumps(req))
    log("->", json.dumps(req))


async def wait_for_ok(ws, event_id: str, timeout: float) -> tuple[bool, str]:
    try:
        while True:
            raw = await asyncio.wait_for(ws.recv(), timeout=timeout)
            log("<-", raw)
            msg = json.loads(raw)
            if isinstance(msg, list) and len(msg) >= 4 and msg[0] == "OK" and msg[1] == event_id:
                return bool(msg[2]), str(msg[3])
    except asyncio.TimeoutError:
        return False, "timed out waiting for OK response"


async def run(relay: str, keys: Keys) -> int:
    relay_url = RelayUrl.parse(relay)
    print(f"pubkey (hex):   {keys.public_key().to_hex()}")
    print(f"pubkey (npub):  {keys.public_key().to_bech32()}")
    print(f"connecting to:  {relay}")

    async with websockets.connect(relay) as ws:
        challenge = await wait_for_auth_challenge(ws, CHALLENGE_WAIT_TIMEOUT)

        if challenge is None:
            print("no AUTH challenge received on connect, sending a probe REQ...")
            await send_probe_req(ws)
            challenge = await wait_for_auth_challenge(ws, CHALLENGE_WAIT_TIMEOUT)
            # Best-effort cleanup of the probe subscription.
            await ws.send(json.dumps(["CLOSE", "nip42-probe"]))

        if challenge is None:
            print("relay never sent an AUTH challenge; it may not support NIP-42.")
            return 1

        print(f"received challenge: {challenge!r}")

        auth_event = EventBuilder.auth(challenge, relay_url).sign_with_keys(keys)
        auth_msg = ["AUTH", json.loads(auth_event.as_json())]
        await ws.send(json.dumps(auth_msg))
        log("->", json.dumps(auth_msg))

        ok, message = await wait_for_ok(ws, auth_event.id().to_hex(), AUTH_RESPONSE_TIMEOUT)

        if ok:
            print(f"AUTH accepted: {message or '(no message)'}")
            return 0
        else:
            print(f"AUTH rejected: {message or '(no message)'}")
            return 1


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="NIP-42 relay authentication test client")
    parser.add_argument("--relay", default="ws://localhost:6724", help="relay WebSocket URL")
    parser.add_argument(
        "--privkey",
        default=None,
        help="nsec or hex private key to authenticate with (default: generate an ephemeral key)",
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    keys = Keys.parse(args.privkey) if args.privkey else Keys.generate()
    exit_code = asyncio.run(run(args.relay, keys))
    sys.exit(exit_code)


if __name__ == "__main__":
    main()
