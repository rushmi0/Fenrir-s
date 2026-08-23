#!/data/data/com.termux/files/usr/bin/bash

set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"

DISTRO="ubuntu"
APP_USER="nostr"
CONTAINER_APP_DIR="/opt/fenrir-s"

echo "========================================"
echo " Fenrir-s Installer"
echo "========================================"

echo "[1/7] Checking proot-distro..."

if ! command -v proot-distro >/dev/null 2>&1; then
    echo "[install] Installing proot-distro..."
    pkg install -y proot-distro
fi

echo "[2/7] Checking Ubuntu container..."

DISTRO_ROOTFS="${PREFIX:-/data/data/com.termux/files/usr}/var/lib/proot-distro/containers/${DISTRO}/rootfs"

if [ ! -d "${DISTRO_ROOTFS}" ]; then
    echo "[install] Installing ${DISTRO} container..."
    proot-distro install "${DISTRO}"
else
    echo "[install] ${DISTRO} container already installed."
fi

echo "[3/7] Checking application binary..."

BINARY="$(
    find "${APP_DIR}" \
        -maxdepth 1 \
        -name 'Fenrir-s-relay-*-linux-arm64-glibc' \
        -type f \
        -printf '%f\n' \
        | head -1
)"

if [ -z "${BINARY}" ]; then
    echo "[install] ERROR: Fenrir relay binary not found."
    exit 1
fi

echo "[install] Binary: ${BINARY}"

echo "[4/7] Preparing container user..."

if ! proot-distro login "${DISTRO}" -- \
    id "${APP_USER}" >/dev/null 2>&1; then

    echo "[install] Creating user: ${APP_USER}"

    proot-distro login "${DISTRO}" -- \
        useradd \
        --create-home \
        --shell /bin/bash \
        "${APP_USER}"
else
    echo "[install] User ${APP_USER} already exists."
fi

echo "[5/7] Preparing application directory..."

proot-distro login "${DISTRO}" -- \
    mkdir -p "${CONTAINER_APP_DIR}"

echo "[6/7] Installing application binary..."

# Copy the binary into the Ubuntu container.
proot-distro copy \
    "${APP_DIR}/${BINARY}" \
    "${DISTRO}:${CONTAINER_APP_DIR}/${BINARY}"

echo "[install] Setting ownership..."

proot-distro login "${DISTRO}" -- \
    chown -R "${APP_USER}:${APP_USER}" "${CONTAINER_APP_DIR}"

echo "[install] Setting permissions..."

proot-distro login "${DISTRO}" -- \
    chmod 755 "${CONTAINER_APP_DIR}/${BINARY}"

echo "[7/7] Verifying installation..."

if ! proot-distro login "${DISTRO}" --user "${APP_USER}" -- \
    test -x "${CONTAINER_APP_DIR}/${BINARY}"; then

    echo "[install] ERROR: application binary is not executable."
    exit 1
fi

echo
echo "========================================"
echo " Installation completed successfully"
echo "========================================"
echo
echo "Container : ${DISTRO}"
echo "User      : ${APP_USER}"
echo "Directory : ${CONTAINER_APP_DIR}"
echo "Binary    : ${BINARY}"
echo
echo "Run with:"
echo "  ./run_relay.sh"
echo