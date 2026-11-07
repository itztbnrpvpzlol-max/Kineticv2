package com.aurea.diagnostics;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.os.Build;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.*;
import java.io.File;
import java.nio.file.Files;

public final class MainActivity extends Activity {
    private TextView reportView;
    private File reportFile;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        reportFile = new File(getFilesDir(), "aurea_native_probe.txt");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,18);
        root.setBackgroundColor(Color.rgb(14,17,24));
        TextView title = new TextView(this); title.setText("AUREA NATIVE ENGINE TESTER");
        title.setTextColor(Color.WHITE); title.setTextSize(21); root.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Loads Aurea 0.4.7's real ARM native library and runs nativeCreate, nativeInitialize, startup diagnostics and shutdown in an isolated process. If native code crashes, this screen remains open with the last recorded stage.");
        intro.setTextColor(Color.LTGRAY); intro.setPadding(0,10,0,14); root.addView(intro);
        Button run = new Button(this); run.setText("RUN AUREA NATIVE ENGINE TEST"); root.addView(run);
        Button refresh = new Button(this); refresh.setText("REFRESH REPORT"); root.addView(refresh);
        Button share = new Button(this); share.setText("SHARE REPORT"); root.addView(share);
        reportView = new TextView(this); reportView.setTextColor(Color.rgb(190,230,200)); reportView.setTextSize(12);
        reportView.setText("Ready. Tap RUN AUREA NATIVE ENGINE TEST.");
        ScrollView scroll = new ScrollView(this); scroll.addView(reportView);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        run.setOnClickListener(v -> {
            reportView.setText("Starting isolated native test...");
            startService(new Intent(this,NativeProbeService.class));
        });
        refresh.setOnClickListener(v -> showReport());
        share.setOnClickListener(v -> {
            String body=showReport();
            Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT,"Aurea native engine diagnostic report");
            send.putExtra(Intent.EXTRA_TEXT,body);
            startActivity(Intent.createChooser(send,"Share native diagnostic report"));
        });
    }
    private String readReport() {
        try { return reportFile.exists() ? new String(Files.readAllBytes(reportFile.toPath()),java.nio.charset.StandardCharsets.UTF_8) : "No native report yet."; }
        catch(Exception e){ return "Report read error: "+e; }
    }
    private String showReport() {
        String body=readReport();
        if(body.contains("NATIVE_INITIALIZE_CALL_STARTED") && !body.contains("PROBE_COMPLETE") && !isProbeAlive()) {
            body += "\n\nDIAGNOSIS: Isolated native process stopped before completion. This is consistent with a native crash during or after the last logged stage.";
            body += collectNativeExitInfo();
        }
        reportView.setText(body); return body;
    }
    private String collectNativeExitInfo() {
        if (Build.VERSION.SDK_INT < 30) return "\nProcess exit details require Android 11 or newer.";
        StringBuilder out = new StringBuilder("\n\nANDROID PROCESS EXIT DIAGNOSTICS");
        try {
            ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(am==null)return out.append("\nActivityManager unavailable.").toString();
            for(ApplicationExitInfo info:am.getHistoricalProcessExitReasons(getPackageName(),0,12)) {
                if(!(getPackageName()+":nativeprobe").equals(info.getProcessName())) continue;
                out.append("\nreason=").append(info.getReason())
                   .append(" status=").append(info.getStatus())
                   .append(" description=").append(info.getDescription());
                if(Build.VERSION.SDK_INT>=31) {
                    try(InputStream in=info.getTraceInputStream()) {
                        if(in!=null) {
                            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                            byte[] buffer=new byte[8192]; int n; int total=0;
                            while((n=in.read(buffer))>0 && total<200000) {
                                int take=Math.min(n,200000-total); bytes.write(buffer,0,take); total+=take;
                            }
                            out.append("\nNATIVE_CRASH_TRACE_BYTES=").append(total).append("\n")
                               .append(bytes.toString(java.nio.charset.StandardCharsets.UTF_8));
                        } else out.append("\nNo native crash trace supplied by Android.");
                    } catch(Exception traceError) {
                        out.append("\nTrace read error: ").append(traceError);
                    }
                }
                return out.toString();
            }
            out.append("\nNo matching historical exit record found.");
        } catch(Exception e) { out.append("\nExit diagnostics error: ").append(e); }
        return out.toString();
    }
    private boolean isProbeAlive() {
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(am==null)return false;
        for(ActivityManager.RunningAppProcessInfo p:am.getRunningAppProcesses())
            if((getPackageName()+":nativeprobe").equals(p.processName))return true;
        return false;
    }
    @Override protected void onResume(){ super.onResume(); if(reportView!=null && reportFile.exists())showReport(); }
}
