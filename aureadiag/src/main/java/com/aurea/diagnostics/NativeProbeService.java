package com.aurea.diagnostics;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.Build;
import android.view.Display;
import com.aurea.aurea.engine.AureaEngine;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class NativeProbeService extends Service {
    private File report;
    @Override public void onCreate() { super.onCreate(); report = new File(getFilesDir(), "aurea_native_probe.txt"); }
    @Override public int onStartCommand(Intent intent, int flags, int id) {
        new Thread(this::runProbe, "AureaNativeProbe").start();
        return START_NOT_STICKY;
    }
    private synchronized void log(String s) {
        try (FileWriter w = new FileWriter(report, true)) {
            w.write(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date()) + " " + s + "\n");
            w.flush();
        } catch (Exception ignored) {}
    }
    private void copyAsset(String name, File dst) throws Exception {
        File tmp = new File(dst.getParentFile(), dst.getName() + ".tmp");
        try (InputStream in = getAssets().open(name); FileOutputStream out = new FileOutputStream(tmp)) {
            byte[] b = new byte[32768]; int n; while ((n=in.read(b))!=-1) out.write(b,0,n); out.getFD().sync();
        }
        if (dst.exists()) dst.delete();
        if (!tmp.renameTo(dst)) throw new IOException("Cannot install " + name);
    }
    private void runProbe() {
        try {
            if (report.exists()) report.delete();
            log("PROBE_PROCESS_STARTED");
            log("DEVICE " + Build.MANUFACTURER + " " + Build.MODEL + " sdk=" + Build.VERSION.SDK_INT + " abi=" + Build.SUPPORTED_ABIS[0]);
            File motor = new File(getCacheDir(), "motor");
            File projects = new File(getFilesDir(), "projetos");
            File plugins = new File(getFilesDir(), "plugins");
            motor.mkdirs(); projects.mkdirs(); new File(projects, ".miniaturas").mkdirs(); plugins.mkdirs();
            log("INSTALLING_FONTS");
            copyAsset("Roboto-Regular.ttf", new File(motor,"Roboto-Regular.ttf"));
            copyAsset("NotoNaskhArabic-Regular.ttf", new File(motor,"NotoNaskhArabic-Regular.ttf"));
            copyAsset("NotoSansJP-Regular.otf", new File(motor,"NotoSansJP-Regular.otf"));
            log("FONTS_READY cache=" + motor.getAbsolutePath() + " projects=" + projects.getAbsolutePath());

            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if (am != null) am.getMemoryInfo(mi);
            DisplayManager dm = (DisplayManager)getSystemService(DISPLAY_SERVICE);
            Display display = dm == null ? null : dm.getDisplay(Display.DEFAULT_DISPLAY);
            float hz = display == null ? 60f : display.getRefreshRate();
            boolean debug = (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;

            log("LOADING_LIBAUREA");
            AureaEngine engine = new AureaEngine();
            long handle = engine.handle();
            log("NATIVE_CREATE_RETURNED handle=0x" + Long.toHexString(handle));
            if (handle == 0) { log("FAIL nativeCreate returned zero"); return; }

            log("NATIVE_INITIALIZE_CALL_STARTED hz=" + hz + " totalMem=" + mi.totalMem + " availMem=" + mi.availMem + " codecIds=0");
            boolean ok = engine.initialize(hz, motor.getAbsolutePath(), projects.getAbsolutePath(),
                    debug, new long[]{mi.totalMem, mi.availMem}, new int[0], plugins.getAbsolutePath());
            log("NATIVE_INITIALIZE_RETURNED result=" + ok);
            String error = engine.startupError();
            log("NATIVE_STARTUP_ERROR " + (error == null ? "<null>" : error));
            try { log("NATIVE_DEVICE_SUMMARY " + engine.deviceSummary()); }
            catch (Throwable t) { log("DEVICE_SUMMARY_ERROR " + t); }
            try {
                long[] values = new long[16];
                int code = engine.memoryReport(values);
                StringBuilder s = new StringBuilder("NATIVE_MEMORY_REPORT code=").append(code).append(" values=");
                for (int i=0;i<values.length;i++) { if(i>0)s.append(','); s.append(values[i]); }
                log(s.toString());
            } catch (Throwable t) { log("MEMORY_REPORT_ERROR " + t); }
            try {
                String schema = engine.builtinPropertySchema();
                log("BUILTIN_PROPERTY_SCHEMA " + (schema == null ? "<null>" : "length=" + schema.length()));
            } catch (Throwable t) { log("PROPERTY_SCHEMA_ERROR " + t); }
            log("NATIVE_SHUTDOWN_STARTED");
            engine.shutdown();
            engine.destroy();
            log("NATIVE_DESTROY_COMPLETED");
            log("PROBE_COMPLETE initialized=" + ok);
        } catch (Throwable t) {
            log("JAVA_EXCEPTION " + t.getClass().getName() + ": " + t.getMessage());
            for (StackTraceElement e : t.getStackTrace()) log(" at " + e);
        } finally { stopSelf(); }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}