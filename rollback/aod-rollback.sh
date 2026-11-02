#!/system/bin/sh
# Only run manually when rollback is desired. Requires current temporary root.
# No device reboot or zygote restart. SystemUI and AOD app processes reload only.
[ "$(id -u)" = 0 ] || { echo 'Root required'; exit 1; }
RP=/data/adb/ksu/bin/resetprop
[ -x "$RP" ] || { echo 'resetprop missing; abort'; exit 1; }
if pm path io.local.neoaod.typeguard >/dev/null 2>&1; then
  pm uninstall io.local.neoaod.typeguard || exit 1
fi
if pm path com.dsh.neoaod >/dev/null 2>&1; then
  pm uninstall com.dsh.neoaod || exit 1
fi
"$RP" --delete ro.vivo.keyguard_full_aod.enable
restore_settings() {
  settings delete secure neo9_aod_schedule_config
  settings delete secure neo9_aod_brightness_percent
  settings put secure full_screen_aod_enabled 0
  settings put secure vivo_keyguard_aod_type 2
  settings put system use_aod_infinity 0
  settings put secure doze_always_on 0
  settings put system screen_off_remind_aod_type 2
  settings put secure screen_off_remind_aod_type 0
}
restore_settings
am force-stop com.vivo.nightpearl
killall com.android.systemui
sleep 5
restore_settings
echo 'Baseline settings restored. No device reboot performed.'
id
