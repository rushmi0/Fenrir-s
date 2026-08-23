#!/data/data/com.termux/files/usr/bin/bash

set -euo pipefail

DISTRO="ubuntu"
APP_USER="nostr"

SOURCE_DATA_DIR="${HOME}/.fenrir-s"
CONTAINER_DATA_DIR="/home/${APP_USER}/.fenrir-s"

echo "========================================"
echo " Fenrir-s Data Migration"
echo "========================================"

echo "[migration] Source      : ${SOURCE_DATA_DIR}"
echo "[migration] Destination : ${DISTRO}:${CONTAINER_DATA_DIR}"
echo "[migration] User        : ${APP_USER}"
echo

if ! command -v proot-distro >/dev/null 2>&1; then
    echo "[migration] ERROR: proot-distro is not installed." >&2
    echo "[migration] Please run install.sh first." >&2
    exit 1
fi

DISTRO_ROOTFS="${PREFIX:-/data/data/com.termux/files/usr}/var/lib/proot-distro/containers/${DISTRO}/rootfs"

if [ ! -d "${DISTRO_ROOTFS}" ]; then
    echo "[migration] ERROR: ${DISTRO} container is not installed." >&2
    echo "[migration] Please run install.sh first." >&2
    exit 1
fi

if ! proot-distro login "${DISTRO}" -- \
    id "${APP_USER}" >/dev/null 2>&1; then

    echo "[migration] ERROR: user '${APP_USER}' does not exist." >&2
    echo "[migration] Please run install.sh first." >&2
    exit 1
fi

if [ ! -d "${SOURCE_DATA_DIR}" ]; then
    echo "[migration] No source data found."
    echo "[migration] Nothing to migrate."
    exit 0
fi

if proot-distro login "${DISTRO}" -- \
    test -d "${CONTAINER_DATA_DIR}"; then

    echo "[migration] Destination already exists:"
    echo "             ${CONTAINER_DATA_DIR}"
    echo
    echo "[migration] Migration skipped."
    echo
    echo "[migration] Existing container data will NOT be overwritten."

    exit 0
fi

echo "[migration] Creating destination parent directory..."

proot-distro login "${DISTRO}" -- \
    mkdir -p "/home/${APP_USER}"

echo "[migration] Copying data..."

proot-distro copy \
    "${SOURCE_DATA_DIR}" \
    "${DISTRO}:${CONTAINER_DATA_DIR}"

echo "[migration] Setting ownership..."

proot-distro login "${DISTRO}" -- \
    chown -R "${APP_USER}:${APP_USER}" \
    "${CONTAINER_DATA_DIR}"

echo "[migration] Setting permissions..."

proot-distro login "${DISTRO}" -- \
    chmod -R u+rwX \
    "${CONTAINER_DATA_DIR}"

echo
echo "========================================"
echo " Migration completed successfully"
echo "========================================"
echo
echo "Source:"
echo "  ${SOURCE_DATA_DIR}"
echo
echo "Destination:"
echo "  ${CONTAINER_DATA_DIR}"
echo
echo "Owner:"
echo "  ${APP_USER}:${APP_USER}"
echo