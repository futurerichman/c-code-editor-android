package com.ccode.mintest;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class EnsureAssets {

    public interface Done {
        void run();
    }

    private static final String LOCAL_TMP_BASE = "/data/local/tmp/ccode";

    public static void ensure(final Context ctx, final Done done) {
        final File dir = ctx.getFilesDir();
        final File tccBin = new File(dir, "tcc/tcc");
        final File sysroot = new File(dir, "sysroot/include");
        final File runtimeLib = new File(dir, "tcc_runtime/libtcc1.a");
        if (isElf(tccBin) && sysroot.isDirectory() && runtimeLib.isFile()) {
            setExecutable(tccBin);
            if (done != null) done.run();
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = true;
                try {
                    if (!isElf(tccBin)) {
                        File tf = new File(dir, "tcc");
                        tf.mkdirs();
                        InputStream gz = new GZIPInputStream(
                                ctx.getAssets().open("tcc/tcc.gz"));
                        OutputStream os = new FileOutputStream(tccBin);
                        copy(gz, os);
                        os.close();
                        gz.close();
                    }
                    if (!sysroot.isDirectory()) {
                        InputStream zf = ctx.getAssets().open("sysroot.zip");
                        unzip(zf, new File(dir, "sysroot"));
                        zf.close();
                    }
                    if (!runtimeLib.isFile()) {
                        InputStream zf = ctx.getAssets().open("tcc/tcc_runtime.zip");
                        unzip(zf, new File(dir, "tcc_runtime"));
                        zf.close();
                    }
                    setExecutable(tccBin);
                } catch (Exception e) {
                    ok = false;
                }
                tryDeployLocalTmp(ctx);
                if (ok && done != null) done.run();
            }
        }, "ccode-assets").start();
    }

    private static void tryDeployLocalTmp(Context ctx) {
        try {
            File dir = new File(LOCAL_TMP_BASE);
            if (!dir.exists() && !dir.mkdirs()) return;
            File tccBin = new File(dir, "tcc");
            if (!isElf(tccBin)) {
                InputStream gz = new GZIPInputStream(
                        ctx.getAssets().open("tcc/tcc.gz"));
                OutputStream os = new FileOutputStream(tccBin);
                copy(gz, os);
                os.close();
                gz.close();
            }
            setExecutable(tccBin);
            if (!new File(dir, "sysroot/include").isDirectory()) {
                InputStream zf = ctx.getAssets().open("sysroot.zip");
                unzip(zf, new File(dir, "sysroot"));
                zf.close();
            }
            if (!new File(dir, "tcc_runtime/libtcc1.a").isFile()) {
                InputStream zf = ctx.getAssets().open("tcc/tcc_runtime.zip");
                unzip(zf, new File(dir, "tcc_runtime"));
                zf.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static boolean isElf(File f) {
        try {
            if (!f.isFile() || f.length() < 4) return false;
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] magic = new byte[4];
            int n = in.read(magic);
            in.close();
            return n == 4 && magic[0] == 0x7f && magic[1] == 'E'
                    && magic[2] == 'L' && magic[3] == 'F';
        } catch (Exception ignored) {
            return false;
        }
    }

    public static File resolveTccBin(Context ctx) {
        File lt = new File(LOCAL_TMP_BASE, "tcc");
        if (lt.isFile() && lt.canExecute()) return lt;
        File f1 = new File(ctx.getFilesDir(), "tcc/tcc");
        if (f1.isFile()) return f1;
        File f2 = new File(ctx.getFilesDir(), "tcc");
        if (f2.isFile()) return f2;
        return f1;
    }

    public static File resolveRuntimeDir(Context ctx) {
        File lt = new File(LOCAL_TMP_BASE, "tcc_runtime/libtcc1.a");
        if (lt.isFile()) return new File(LOCAL_TMP_BASE, "tcc_runtime");
        return new File(ctx.getFilesDir(), "tcc_runtime");
    }

    public static File resolveSysroot(Context ctx) {
        File lt = new File(LOCAL_TMP_BASE, "sysroot/include");
        if (lt.isDirectory()) return new File(LOCAL_TMP_BASE, "sysroot");
        return new File(ctx.getFilesDir(), "sysroot");
    }

    private static void setExecutable(File f) {
        try {
            if (f.setExecutable(true, false)) return;
        } catch (Exception ignored) {
        }
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"chmod", "755", f.getAbsolutePath()});
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    private static void unzip(InputStream in, File dest) throws Exception {
        ZipInputStream zis = new ZipInputStream(in);
        ZipEntry e;
        byte[] buf = new byte[8192];
        while ((e = zis.getNextEntry()) != null) {
            String name = e.getName();
            if (name.contains("..") || name.startsWith("/")) continue;
            File f = new File(dest, name);
            if (e.isDirectory()) {
                f.mkdirs();
                continue;
            }
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            OutputStream os = new FileOutputStream(f);
            int n;
            while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
        }
        zis.close();
    }
}
