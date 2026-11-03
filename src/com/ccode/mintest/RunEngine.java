package com.ccode.mintest;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RunEngine {

    public interface Callback {
        void onOutput(String text, boolean isError);
        void onExit(int code);
        void onError(String message);
    }

    public interface DebugCallback {
        void onDebugLine(int line);
        void onDebugVars(String vars);
    }

    private static final String[] LINKERS = {
            "/apex/com.android.runtime/bin/linker64",
            "/system/bin/linker64"
    };

    private Process process;
    private OutputStream stdin;
    private volatile boolean stopped;

    private BufferedWriter dbgWriter;
    private LocalSocket dbgSock;

    public void run(final File tccBin, final File sysroot, final File runtimeDir,
                    final File srcFile, final Callback cb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runSync(tccBin, sysroot, runtimeDir, srcFile, cb);
            }
        }, "ccode-run").start();
    }

    private void runSync(File tccBin, File sysroot, File runtimeDir,
                         File srcFile, Callback cb) {
        stopped = false;
        if (!tccBin.isFile()) {
            cb.onError("tcc 路径不是文件（是目录或缺失）：" + tccBin.getAbsolutePath()
                    + "，请重新安装本版本");
            return;
        }
        File incBase = new File(sysroot, "include");
        File incArch = new File(incBase, "aarch64-linux-android");
        List<String> base = buildBaseCommand(tccBin, sysroot, runtimeDir, srcFile);

        List<List<String>> attempts = new ArrayList<>();
        attempts.add(base);
        for (String linker : LINKERS) {
            if (new File(linker).isFile()) {
                List<String> lcmd = new ArrayList<>();
                lcmd.add(linker);
                lcmd.addAll(base);
                attempts.add(lcmd);
            }
        }

        IOException last = null;
        for (List<String> cmd : attempts) {
            try {
                runOnce(cmd, srcFile, incBase, incArch, cb);
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        cb.onError(last == null ? "无法启动 tcc"
                : (last.getMessage() == null ? last.toString() : last.getMessage()));
    }

    private List<String> buildBaseCommand(File tccBin, File sysroot,
                                         File runtimeDir, File srcFile) {
        File incBase = new File(sysroot, "include");
        File incArch = new File(incBase, "aarch64-linux-android");
        List<String> base = new ArrayList<>();
        base.add(tccBin.getAbsolutePath());
        if (new File(runtimeDir, "libtcc1.a").isFile()) {
            base.add("-L");
            base.add(runtimeDir.getAbsolutePath());
        }
        if (new File(runtimeDir, "libtcc1.a").isFile()) {
            base.add("-B");
            base.add(runtimeDir.getAbsolutePath());
        }
        File runtimeInclude = new File(runtimeDir, "include");
        if (runtimeInclude.isDirectory()) {
            base.add("-I");
            base.add(runtimeInclude.getAbsolutePath());
        }
        base.add("-I");
        base.add(incBase.getAbsolutePath());
        if (incArch.exists()) {
            base.add("-I");
            base.add(incArch.getAbsolutePath());
        }
        base.add("-run");
        base.add(srcFile.getAbsolutePath());
        return base;
    }

    private void runOnce(List<String> cmd, File srcFile,
                         File incBase, File incArch, Callback cb) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(srcFile.getParentFile());
        Map<String, String> env = pb.environment();
        try {
            env.put("C_INCLUDE_PATH", incBase.getAbsolutePath()
                    + ":" + incArch.getAbsolutePath());
            env.put("LIBRARY_PATH", "/system/lib64");
        } catch (Exception ignored) {
        }
        final Process p = pb.start();
        process = p;
        stdin = p.getOutputStream();

        Thread outThread = new Thread(() -> readLoop(p.getInputStream(), cb, false));
        Thread errThread = new Thread(() -> readLoop(p.getErrorStream(), cb, true));
        outThread.start();
        errThread.start();

        try {
            int code = p.waitFor();
            outThread.join(3000);
            errThread.join(3000);
            cb.onExit(code);
        } catch (InterruptedException e) {
            cb.onError("运行被中断");
        }
    }

    public void runDebug(final File tccBin, final File sysroot, final File runtimeDir,
                        final File srcFile, final String sockName,
                        final LocalServerSocket server, final Callback cb,
                        final DebugCallback dcb) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                runDebugSync(tccBin, sysroot, runtimeDir, srcFile, sockName, server, cb, dcb);
            }
        }, "ccode-debug").start();
    }

    private void runDebugSync(File tccBin, File sysroot, File runtimeDir, File srcFile,
                             String sockName, LocalServerSocket server, Callback cb,
                             DebugCallback dcb) {
        stopped = false;
        if (!tccBin.isFile()) {
            cb.onError("tcc 路径不是文件（是目录或缺失）：" + tccBin.getAbsolutePath()
                    + "，请重新安装本版本");
            return;
        }
        List<String> base = buildBaseCommand(tccBin, sysroot, runtimeDir, srcFile);
        File incBase = new File(sysroot, "include");
        File incArch = new File(incBase, "aarch64-linux-android");
        List<List<String>> attempts = new ArrayList<>();
        attempts.add(base);
        for (String linker : LINKERS) {
            if (new File(linker).isFile()) {
                List<String> lcmd = new ArrayList<>();
                lcmd.add(linker);
                lcmd.addAll(base);
                attempts.add(lcmd);
            }
        }
        IOException last = null;
        for (List<String> cmd : attempts) {
            try {
                runDebugOnce(cmd, srcFile, incBase, incArch, cb, dcb, server, sockName);
                return;
            } catch (IOException e) {
                last = e;
            }
        }
        cb.onError(last == null ? "无法启动 tcc"
                : (last.getMessage() == null ? last.toString() : last.getMessage()));
    }

    private void runDebugOnce(List<String> cmd, File srcFile, File incBase, File incArch,
                             Callback cb, DebugCallback dcb, LocalServerSocket server,
                             String sockName) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(srcFile.getParentFile());
        Map<String, String> env = pb.environment();
        try {
            env.put("C_INCLUDE_PATH", incBase.getAbsolutePath()
                    + ":" + incArch.getAbsolutePath());
            env.put("LIBRARY_PATH", "/system/lib64");
            env.put("CCODE_DBG_SOCK", sockName);
        } catch (Exception ignored) {
        }

        Thread acceptThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LocalSocket s = server.accept();
                    dbgSock = s;
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(s.getInputStream()));
                    dbgWriter = new BufferedWriter(
                            new OutputStreamWriter(s.getOutputStream()));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("LINE ")) {
                            try {
                                final int ln = Integer.parseInt(line.substring(5).trim());
                                dcb.onDebugLine(ln);
                            } catch (NumberFormatException ignore) {
                            }
                        } else if (line.startsWith("VARS ")) {
                            final String vars = line.substring(5);
                            dcb.onDebugVars(vars);
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }, "ccode-dbg-accept");
        acceptThread.start();

        final Process p = pb.start();
        process = p;
        stdin = p.getOutputStream();

        Thread outThread = new Thread(new Runnable() {
            @Override
            public void run() { readLoop(p.getInputStream(), cb, false); }
        });
        Thread errThread = new Thread(new Runnable() {
            @Override
            public void run() { readLoop(p.getErrorStream(), cb, true); }
        });
        outThread.start();
        errThread.start();

        try {
            int code = p.waitFor();
            outThread.join(3000);
            errThread.join(3000);
            cb.onExit(code);
        } catch (InterruptedException e) {
            cb.onError("运行被中断");
        } finally {
            try { if (dbgSock != null) dbgSock.close(); } catch (Exception ignore) {
            }
            try { server.close(); } catch (Exception ignore) {
            }
        }
    }

    public void sendDebugCommand(String cmd) {
        BufferedWriter w = dbgWriter;
        if (w == null) return;
        try {
            synchronized (w) {
                w.write(cmd);
                w.flush();
            }
        } catch (IOException ignored) {
        }
    }

    public void sendInput(String line) {
        OutputStream os = stdin;
        if (os == null) return;
        try {
            synchronized (os) {
                os.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (IOException ignored) {
        }
    }

    public void stop() {
        stopped = true;
        Process p = process;
        if (p != null) {
            p.destroy();
            try {
                p.waitFor(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            if (p.isAlive()) p.destroyForcibly();
        }
        try { if (dbgSock != null) dbgSock.close(); } catch (Exception ignored) {
        }
    }

    private void readLoop(InputStream in, Callback cb, boolean isError) {
        byte[] buf = new byte[4096];
        int n;
        try {
            while (!stopped && (n = in.read(buf)) > 0) {
                cb.onOutput(new String(buf, 0, n, StandardCharsets.UTF_8), isError);
            }
        } catch (IOException ignored) {
        }
    }
}
