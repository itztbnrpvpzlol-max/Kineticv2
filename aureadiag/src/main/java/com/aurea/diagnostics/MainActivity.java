package com.aurea.diagnostics;

import android.app.Activity;
import android.os.Bundle;
import android.opengl.GLSurfaceView;
import android.opengl.GLES30;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends Activity {
    private TextView reportView;
    private GLSurfaceView surface;
    private final StringBuilder report = new StringBuilder();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private String deviceInfo;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        deviceInfo = "Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + "\nAndroid: " + android.os.Build.VERSION.RELEASE + " (SDK " + android.os.Build.VERSION.SDK_INT + ")"
                + "\nABI: " + android.os.Build.SUPPORTED_ABIS[0];
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(18,18,18,18);
        root.setBackgroundColor(Color.rgb(14,17,24));
        TextView title = new TextView(this);
        title.setText("AUREA DIAGNOSTICS");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title);
        TextView intro = new TextView(this);
        intro.setText("Isolated graphics checks for EGL, GLES, shaders and framebuffer rendering. This does not modify Aurea.");
        intro.setTextColor(Color.LTGRAY);
        intro.setPadding(0,8,0,12);
        root.addView(intro);
        Button run = new Button(this);
        run.setText("RUN GRAPHICS TESTS");
        root.addView(run);
        Button share = new Button(this);
        share.setText("SHARE REPORT");
        root.addView(share);
        reportView = new TextView(this);
        reportView.setTextColor(Color.rgb(180,225,190));
        reportView.setTextSize(12);
        reportView.setText(deviceInfo + "\n\nReady. Tap RUN GRAPHICS TESTS.");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(reportView);
        root.addView(scroll, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);

        surface = new GLSurfaceView(this);
        surface.setEGLContextClientVersion(3);
        surface.setRenderer(new TestRenderer());
        surface.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        run.setOnClickListener(v -> {
            completed.set(false);
            append("\n--- TEST START " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + " ---");
            surface.requestRender();
        });
        share.setOnClickListener(v -> {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType("text/plain");
            send.putExtra(Intent.EXTRA_SUBJECT, "Aurea graphics diagnostic report");
            send.putExtra(Intent.EXTRA_TEXT, deviceInfo + "\n" + report.toString());
            startActivity(Intent.createChooser(send, "Share diagnostic report"));
        });
        // Keep a real EGL surface attached while tests execute; the view is intentionally tiny.
        root.addView(surface, new LinearLayout.LayoutParams(1,1));
    }

    private void append(String s) {
        runOnUiThread(() -> {
            report.append(s).append('\n');
            reportView.setText(deviceInfo + "\n\n" + report);
        });
    }

    private class TestRenderer implements GLSurfaceView.Renderer {
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 unused,
                                                javax.microedition.khronos.egl.EGLConfig config) {
            try {
                String version = GLES30.glGetString(GLES30.GL_VERSION);
                String vendor = GLES30.glGetString(GLES30.GL_VENDOR);
                String renderer = GLES30.glGetString(GLES30.GL_RENDERER);
                String extensions = GLES30.glGetString(GLES30.GL_EXTENSIONS);
                int[] v = new int[1], f = new int[1];
                GLES30.glGetIntegerv(GLES30.GL_MAJOR_VERSION, v, 0);
                GLES30.glGetIntegerv(GLES30.GL_MINOR_VERSION, f, 0);
                append("EGL surface/context: PASS");
                append("GL_VERSION: " + version);
                append("GL_VENDOR: " + vendor);
                append("GL_RENDERER: " + renderer);
                append("GLES version: " + v[0] + "." + f[0] + (v[0] > 3 || (v[0] == 3 && f[0] >= 1) ? " PASS (3.1+)" : " FAIL (below 3.1)"));
                append("Extensions: " + (extensions == null ? "unavailable" : extensions));
                testShader();
                testFramebuffer();
                int err = GLES30.glGetError();
                append(err == GLES30.GL_NO_ERROR ? "Final GL error state: PASS" : "Final GL error state: FAIL 0x" + Integer.toHexString(err));
                append("NOTE: These tests validate the device graphics stack, not Aurea's proprietary native engine initialization.");
            } catch (Throwable t) {
                append("GRAPHICS TEST CRASH/ERROR: " + t.getClass().getName() + ": " + t.getMessage());
            }
        }
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl, int w, int h) {}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl) {}
        private void testShader() {
            String vs = "#version 300 es\nlayout(location=0) in vec4 p; void main(){gl_Position=p;}";
            String fs = "#version 300 es\nprecision mediump float; out vec4 c; void main(){c=vec4(1.0,0.0,0.0,1.0);}";
            int vertex = compile(GLES30.GL_VERTEX_SHADER, vs);
            int fragment = compile(GLES30.GL_FRAGMENT_SHADER, fs);
            if (vertex == 0 || fragment == 0) { append("Shader compile: FAIL"); return; }
            int program = GLES30.glCreateProgram();
            GLES30.glAttachShader(program, vertex);
            GLES30.glAttachShader(program, fragment);
            GLES30.glLinkProgram(program);
            int[] ok = new int[1];
            GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, ok, 0);
            append(ok[0] == GLES30.GL_TRUE ? "Vertex/fragment shader compile + link: PASS" :
                    "Shader link: FAIL " + GLES30.glGetProgramInfoLog(program));
            GLES30.glDeleteProgram(program);
            GLES30.glDeleteShader(vertex);
            GLES30.glDeleteShader(fragment);
        }
        private int compile(int type, String src) {
            int shader = GLES30.glCreateShader(type);
            GLES30.glShaderSource(shader, src);
            GLES30.glCompileShader(shader);
            int[] ok = new int[1];
            GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] != GLES30.GL_TRUE) {
                append("Shader compile FAIL: " + GLES30.glGetShaderInfoLog(shader));
                GLES30.glDeleteShader(shader);
                return 0;
            }
            return shader;
        }
        private void testFramebuffer() {
            int[] fb = new int[1], tex = new int[1];
            GLES30.glGenFramebuffers(1, fb, 0);
            GLES30.glGenTextures(1, tex, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0]);
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, 64, 64, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null);
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fb[0]);
            GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, tex[0], 0);
            int status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER);
            if (status == GLES30.GL_FRAMEBUFFER_COMPLETE) {
                GLES30.glViewport(0,0,64,64);
                GLES30.glClearColor(0.2f,0.4f,0.8f,1f);
                GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
                java.nio.ByteBuffer pixel = java.nio.ByteBuffer.allocateDirect(4);
                GLES30.glReadPixels(32,32,1,1,GLES30.GL_RGBA,GLES30.GL_UNSIGNED_BYTE,pixel);
                append("Framebuffer allocate/attach/clear/readback: PASS (" + (pixel.get(0)&255) + "," + (pixel.get(1)&255) + "," + (pixel.get(2)&255) + ")");
            } else append("Framebuffer test: FAIL status=0x" + Integer.toHexString(status));
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER,0);
            GLES30.glDeleteFramebuffers(1,fb,0);
            GLES30.glDeleteTextures(1,tex,0);
        }
    }
}
