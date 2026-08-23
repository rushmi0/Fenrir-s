#!/data/data/com.termux/files/usr/bin/bash

set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")" && pwd)"

DISTRO="ubuntu"
APP_USER="nostr"
CONTAINER_APP_DIR="/opt/fenrir-s"

echo "========================================"
echo " Fenrir-s Installer"
echo "========================================"

echo "[1/8] Checking proot-distro..."

if ! command -v proot-distro >/dev/null 2>&1; then
    echo "[install] Installing proot-distro..."
    pkg install -y proot-distro
fi

echo "[2/8] Checking Ubuntu container..."

DISTRO_ROOTFS="${PREFIX:-/data/data/com.termux/files/usr}/var/lib/proot-distro/containers/${DISTRO}/rootfs"

if [ ! -d "${DISTRO_ROOTFS}" ]; then
    echo "[install] Installing ${DISTRO} container..."
    proot-distro install "${DISTRO}"
else
    echo "[install] ${DISTRO} container already installed."
fi

echo "[3/8] Checking application binary..."

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

echo "[4/8] Preparing container user..."

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

echo "[5/8] Preparing application directory..."

proot-distro login "${DISTRO}" -- \
    mkdir -p "${CONTAINER_APP_DIR}"

echo "[6/8] Installing application binary..."

proot-distro copy \
    "${APP_DIR}/${BINARY}" \
    "${DISTRO}:${CONTAINER_APP_DIR}/${BINARY}"

echo "[install] Setting ownership..."

proot-distro login "${DISTRO}" -- \
    chown -R "${APP_USER}:${APP_USER}" "${CONTAINER_APP_DIR}"

echo "[install] Setting permissions..."

proot-distro login "${DISTRO}" -- \
    chmod 755 "${CONTAINER_APP_DIR}/${BINARY}"

echo "[7/8] Migrating Fenrir-s data..."

MIGRATION_SCRIPT="${APP_DIR}/migration.sh"

if [ ! -f "${MIGRATION_SCRIPT}" ]; then
    echo "[install] ERROR: migration.sh not found."
    echo "[install] Expected: ${MIGRATION_SCRIPT}"
    exit 1
fi

chmod +x "${MIGRATION_SCRIPT}"

"${MIGRATION_SCRIPT}"

echo "[8/8] Verifying installation..."

if ! proot-distro login "${DISTRO}" --user "${APP_USER}" -- \
    test -x "${CONTAINER_APP_DIR}/${BINARY}"; then

    echo "[install] ERROR: application binary is not executable."
    exit 1
fi

if ! proot-distro login "${DISTRO}" --user "${APP_USER}" -- \
    test -d "/home/${APP_USER}/.fenrir-s"; then

    echo "[install] WARNING: Fenrir-s data directory does not exist."
    echo "[install] This may be normal for a fresh installation."
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
echo "Data      : /home/${APP_USER}/.fenrir-s"
echo
echo "Run with:"
echo "  ./run_relay.sh"
echo