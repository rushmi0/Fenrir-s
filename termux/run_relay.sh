#!/data/data/com.termux/files/usr/bin/bash

set -euo pipefail

DISTRO="ubuntu"
APP_USER="nostr"
CONTAINER_APP_DIR="/opt/fenrir-s"

BINARY="$(
    find "${CONTAINER_APP_DIR}" \
        -maxdepth 1 \
        -name 'Fenrir-s-relay-*-linux-arm64-glibc' \
        -type f \
        -printf '%f\n' \
        | head -1
)"

if [ -z "${BINARY}" ]; then
    echo "[run] ERROR: Fenrir relay binary not found." >&2
    echo "[run] Please run ./install.sh first." >&2
    exit 1
fi

if ! proot-distro login "${DISTRO}" -- \
    id "${APP_USER}" >/dev/null 2>&1; then

    echo "[run] ERROR: user '${APP_USER}' does not exist." >&2
    echo "[run] Please run ./install.sh first." >&2
    exit 1
fi

if ! proot-distro login "${DISTRO}" --user "${APP_USER}" -- \
    test -x "${CONTAINER_APP_DIR}/${BINARY}"; then

    echo "[run] ERROR: relay binary is not installed or executable." >&2
    echo "[run] Please run ./install.sh first." >&2
    exit 1
fi

echo "[run] Starting Fenrir relay..."
echo "[run] Container : ${DISTRO}"
echo "[run] User      : ${APP_USER}"
echo "[run] Binary    : ${BINARY}"

exec proot-distro login "${DISTRO}" \
    --user "${APP_USER}" \
    -- \
    bash -c "cd '${CONTAINER_APP_DIR}' && exec './${BINARY}' \"\$@\"" \
    -- "$@"