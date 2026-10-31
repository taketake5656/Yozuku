#!/system/bin/sh
. "${MODDIR:-.}/nightzuku-guard.sh"
require_nightzuku_host

echo "ADB Test Module action"
echo "MODDIR=$MODDIR"
echo "SHIZUKU_MODULE_ID=$SHIZUKU_MODULE_ID"
echo "SHIZUKU_MODULE_MODE=$SHIZUKU_MODULE_MODE"
echo "NIGHTZUKU_HOST_PKG=$NIGHTZUKU_HOST_PKG"
echo "NIGHTZUKU_MODULES_API=$NIGHTZUKU_MODULES_API"
echo "uid=$(id)"
echo "sdk=$(getprop ro.build.version.sdk)"
cmd package list packages kerneldroid.nightzuku 2>/dev/null || true
