#!/system/bin/sh
. "${MODDIR:-.}/nightzuku-guard.sh"
require_nightzuku_host

echo "ADB Test Module service"
echo "background=$SHIZUKU_MODULE_BACKGROUND"
echo "host=$NIGHTZUKU_HOST_PKG (api=$NIGHTZUKU_MODULES_API)"
date
