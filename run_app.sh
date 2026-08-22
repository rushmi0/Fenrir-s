#!/data/data/com.termux/files/usr/bin/bash
# Reusable setup + launcher for the Fenrir-s native relay binary under Termux.
# Safe to re-run: installs proot-distro/ubuntu only if missing, then launches every time.
#
# Usage: copy this file next to the native binary (e.g. ~/app/Fenrir-s-native-linux-arm64/)
# and run it: ./run_app.sh
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"
DISTRO="ubuntu"

echo "[setup] app dir: ${APP_DIR}"

BINARY="$(find "${APP_DIR}" -maxdepth 1 -name 'Fenrir-s-relay-*-linux-arm64-glibc' -printf '%f\n' | head -1)"
if [ -z "${BINARY}" ]; then
    echo "[setup] ERROR: no Fenrir-s-relay-*-linux-arm64-glibc binary found in ${APP_DIR}" >&2
    exit 1
fi

if ! command -v proot-distro >/dev/null 2>&1; then
    echo "[setup] installing proot-distro..."
    pkg install -y proot-distro
fi

# `proot-distro list` only renders output on a real TTY, so check the container's rootfs
# on disk directly instead of parsing that command.
DISTRO_ROOTFS="${PREFIX:-/data/data/com.termux/files/usr}/var/lib/proot-distro/containers/${DISTRO}/rootfs"
if [ ! -d "${DISTRO_ROOTFS}" ]; then
    echo "[setup] installing ${DISTRO} container (this can take a while)..."
    proot-distro install "${DISTRO}"
fi

chmod +x "${APP_DIR}/${BINARY}" 2>/dev/null || true


echo "[setup] launching Fenrir relay via proot-distro (${DISTRO})..."
exec proot-distro login "${DISTRO}" -- bash -c "cd '${APP_DIR}' && exec ./${BINARY} \"\$@\"" -- "$@"
