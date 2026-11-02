package io.local.neoaod.typeguard;

import java.io.File;
import java.io.FileWriter;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Neo9 temporary-root AOD: style-aware mode synchronization and AOD-only dim overlays. */
public class Main implements IXposedHookLoadPackage {

    private static final String TAG = "[NeoAOD]";
    private static File logFile;
    private static android.content.ContentResolver modeResolver;
    private static boolean settingsVisible;
    private static boolean settingsContext() {
        if (settingsVisible) return true;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) if (e.getClassName().startsWith("com.vivo.nightpearl.settings.")) return true;
        return false;
    }
    private static boolean fullSelected() {
        return modeResolver == null || android.provider.Settings.Secure.getInt(modeResolver, "screen_off_remind_aod_type", 0) == 1;
    }
    private static boolean isLocalMode() {
        return modeResolver != null && android.provider.Settings.Secure.getInt(modeResolver, "vivo_keyguard_aod_type", -1) == 1;
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpp) {
        String pkg = lpp.packageName;
        if ("com.vivo.nightpearl".equals(pkg)) {
            hookProp(lpp.classLoader);

            hookTypeObserver(lpp.classLoader);
            hookSettingsLifecycle();
            hookAodWindowDimming();


            forceBool("r4.h", lpp.classLoader, "Q");
            forceBool("r4.h", lpp.classLoader, "e0");
            forceBool("r4.h", lpp.classLoader, "f0");
        } else if ("com.android.systemui".equals(pkg)) {
            hookSystemAodOverlay(lpp.classLoader);
        }
    }

    /** Temporary-root Neo9 AOD compatibility and AOD-window dimming. */
    private void hookProp(ClassLoader cl) {
        XC_MethodHook h = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                if (!"ro.vivo.keyguard_full_aod.enable".equals(param.args[0])) return;
                log("prop.get(ro.vivo.keyguard_full_aod.enable) = " + param.getResult() + " -> \"1\"");
                param.setResult(settingsContext() || fullSelected() ? "1" : "0");
            }
        };
        try {
            XposedHelpers.findAndHookMethod("android.os.SystemProperties", cl, "get", String.class, String.class, h);
        } catch (Throwable t) {
            log("hook FAIL SystemProperties.get(2): " + t);
        }
        try {
            XposedHelpers.findAndHookMethod("android.os.SystemProperties", cl, "get", String.class, h);
        } catch (Throwable t) {
            log("hook FAIL SystemProperties.get(1): " + t);
        }
    }
    private void hookTypeObserver(ClassLoader cl) {
        XposedHelpers.findAndHookMethod("com.vivo.nightpearl.AODApplication", cl, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                final android.app.Application app = (android.app.Application) param.thisObject;
                final android.content.ContentResolver resolver = app.getContentResolver();
                modeResolver = resolver;
                final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                final Runnable reconcile = new Runnable() {
                    @Override public void run() {
                        int actual = android.provider.Settings.Secure.getInt(resolver, "vivo_keyguard_aod_type", -1);
                        int selection = android.provider.Settings.Secure.getInt(resolver, "screen_off_remind_aod_type", -1);
                        int integrated = android.provider.Settings.Secure.getInt(resolver, "vivo_integration_aod_enable", 0);
                        int mode = selection == 0 ? (integrated == 1 ? 2 : 1) : selection == 1 ? 2 : actual == 3 ? 2 : actual;
                        if (actual != mode && (mode == 1 || mode == 2)) {
                            android.provider.Settings.Secure.putInt(resolver, "vivo_keyguard_aod_type", mode);
                            log("selection reconciled: selection=" + selection + " type=" + actual + " -> " + mode);
                            return;
                        }
                        if (mode == 3) {
                            android.provider.Settings.Secure.putInt(resolver, "vivo_keyguard_aod_type", 2);
                            log("type observer restored 3 -> 2");
                            return;
                        }
                        if (mode != 1 && mode != 2) return;
                        int full = selection == 1 ? 1 : 0;
                        if (android.provider.Settings.Secure.getInt(resolver, "full_screen_aod_enabled", -1) != full)
                            android.provider.Settings.Secure.putInt(resolver, "full_screen_aod_enabled", full);
                        if (android.provider.Settings.System.getInt(resolver, "screen_off_remind", 0) == 1 &&
                            android.provider.Settings.System.getInt(resolver, "screen_off_remind_show_mode", -1) == 2) {
                            if (android.provider.Settings.System.getInt(resolver, "use_aod_infinity", 0) != 1)
                                android.provider.Settings.System.putInt(resolver, "use_aod_infinity", 1);
                            if (android.provider.Settings.Secure.getInt(resolver, "doze_always_on", 0) != 1)
                                android.provider.Settings.Secure.putInt(resolver, "doze_always_on", 1);
                        }
                        log("mode reconciled: type=" + mode + " full=" + full);
                    }
                };
                android.database.ContentObserver observer = new android.database.ContentObserver(handler) {
                    @Override public void onChange(boolean selfChange) {
                        handler.removeCallbacks(reconcile);
                        handler.postDelayed(reconcile, 300);
                    }
                };
                resolver.registerContentObserver(android.provider.Settings.Secure.getUriFor("vivo_keyguard_aod_type"), false, observer);
                resolver.registerContentObserver(android.provider.Settings.Secure.getUriFor("screen_off_remind_aod_type"), false, observer);
                resolver.registerContentObserver(android.provider.Settings.Secure.getUriFor("vivo_integration_aod_enable"), false, observer);
                resolver.registerContentObserver(android.provider.Settings.System.getUriFor("screen_off_remind_style"), false, observer);
                observer.onChange(false);
                log("type and local-mode observer active");
            }
        });
    }

    private void hookSettingsLifecycle() {
        XposedBridge.hookAllMethods(android.app.Activity.class, "onCreate", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.thisObject.getClass().getName().contains(".settings.")) settingsVisible = true;
            }
        });
        XposedBridge.hookAllMethods(android.app.Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (p.thisObject.getClass().getName().contains(".settings.")) settingsVisible = true;
            }
        });
        XposedBridge.hookAllMethods(android.app.Activity.class, "onPause", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (p.thisObject.getClass().getName().contains(".settings.")) settingsVisible = false;
            }
        });
    }
    private void hookAodWindowDimming() {
        try {
            Class<?> global = XposedHelpers.findClass("android.view.WindowManagerGlobal", null);
            XposedBridge.hookAllMethods(global, "addView", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length < 2 || !(p.args[0] instanceof android.view.View) ||
                            !(p.args[1] instanceof android.view.WindowManager.LayoutParams)) return;
                    android.view.WindowManager.LayoutParams lp = (android.view.WindowManager.LayoutParams)p.args[1];
                    if (lp.type != 2021 || !"AOD".contentEquals(lp.getTitle())) return;
                    android.view.View root = (android.view.View)p.args[0];
                    BrightnessRuntime.get(root.getContext()).setActive(root, true);
                }
            });
        } catch (Throwable t) { log("AOD overlay unavailable: " + t); }
    }
    private void hookSystemAodOverlay(ClassLoader cl) {
        try {
            Class<?> controller = XposedHelpers.findClass("com.android.systemui.statusbar.phone.ScrimController", cl);
            XposedBridge.hookAllMethods(controller, "updateScrimColor", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    android.view.View front = (android.view.View)XposedHelpers.getObjectField(p.thisObject, "mScrimInFront");
                    android.view.View root = front.getRootView();
                    boolean aod = "AOD".equals(String.valueOf(XposedHelpers.getObjectField(p.thisObject, "mState")));
                    BrightnessRuntime.get(root.getContext()).setActive(root, aod);
                }
            });
        } catch (Throwable t) { android.util.Log.e("NeoAODBrightness", "SystemUI overlay unavailable", t); }
    }
    private void forceBool(String cls, ClassLoader cl, final String method) {
        try {
            XposedHelpers.findAndHookMethod(cls, cl, method, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    log("cap " + param.method.getDeclaringClass().getName() + "." + method
                            + "() = " + param.getResult() + " -> true");
                    param.setResult("f0".equals(method) || settingsContext() || fullSelected());
                }
            });
            log("hook ok: " + cls + "." + method + "()");
        } catch (Throwable t) {
            log("hook FAIL " + cls + "." + method + "(): " + t);
        }
    }

    static void log(String msg) {
        String line = TAG + " " + msg;
        XposedBridge.log(line);
        writeFile(line);
    }

    private static synchronized void writeFile(String line) {
        try {
            if (logFile == null) {
                android.app.Application app = android.app.AndroidAppHelper.currentApplication();
                if (app == null) return;
                File dir = app.getExternalFilesDir(null);
                if (dir == null) return;
                if (!dir.exists()) dir.mkdirs();
                logFile = new File(dir, "neoaod.log");
            }
            FileWriter w = new FileWriter(logFile, true);
            try {
                w.write(line + "\n");
            } finally {
                w.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
