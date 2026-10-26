package ohi.andre.consolelauncher.tuils;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * tui -backup / tui -restore: the whole t-ui folder (settings, aliases, history, saved servers, ssh keys) in a zip
 * saved in Download, which survives an uninstall.
 */

public class BackupManager {

    private static final String PREFIX = "t-ui-backup-";
    private static final String MIME = "application/zip";
    private static final long MAX_ENTRY_SIZE = 50L * 1024 * 1024;

    public static String backup(Context context) {
        File folder = Tuils.getFolder();
        if (folder == null || !folder.isDirectory()) return "T-UI folder not found";

        String name = PREFIX + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date()) + ".zip";
        int[] count = {0};

        try {
            String location;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//                MediaStore: no storage permission needed to write in Download
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, name);
                values.put(MediaStore.Downloads.MIME_TYPE, MIME);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                ContentResolver resolver = context.getContentResolver();
                Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) return "Could not create the backup file";

                try (OutputStream out = resolver.openOutputStream(uri)) {
                    zip(folder, out, count);
                }
                location = Environment.DIRECTORY_DOWNLOADS + "/" + name;
            } else {
                File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                downloads.mkdirs();
                File file = new File(downloads, name);
                try (OutputStream out = new FileOutputStream(file)) {
                    zip(folder, out, count);
                }
                location = file.getAbsolutePath();
            }

            return "Backup saved: " + location + " (" + count[0] + " files)"
                    + Tuils.NEWLINE + "It contains your ssh private key: don't share it."
                    + Tuils.NEWLINE + "To restore it: tui -restore";
        } catch (Exception e) {
            Tuils.log(e);
            return "Backup failed: " + e;
        }
    }

    private static void zip(File folder, OutputStream out, int[] count) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            add(zip, folder, Tuils.EMPTYSTRING, count);
        }
    }

    private static void add(ZipOutputStream zip, File dir, String path, int[] count) throws Exception {
        File[] files = dir.listFiles();
        if (files == null) return;

        byte[] buffer = new byte[8192];
        for (File f : files) {
            String entry = path + f.getName();
            if (f.isDirectory()) {
                add(zip, f, entry + "/", count);
                continue;
            }

            zip.putNextEntry(new ZipEntry(entry));
            try (InputStream in = new FileInputStream(f)) {
                int n;
                while ((n = in.read(buffer)) > 0) zip.write(buffer, 0, n);
            }
            zip.closeEntry();
            count[0]++;
        }
    }

//    extracts the zip over the t-ui folder
    public static String restore(Context context, Uri uri) {
        File folder = Tuils.getFolder();
        if (folder == null) return "T-UI folder not found";

        int count = 0;
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             ZipInputStream zip = new ZipInputStream(in)) {

            String root = folder.getCanonicalPath() + File.separator;
            byte[] buffer = new byte[8192];
            ZipEntry entry;
            boolean looksValid = false;

            while ((entry = zip.getNextEntry()) != null) {
                File target = new File(folder, entry.getName());
//                never write outside the t-ui folder ("../" in a crafted zip)
                if (!target.getCanonicalPath().startsWith(root)) continue;

                if (entry.isDirectory()) {
                    target.mkdirs();
                    continue;
                }
                if (entry.getName().endsWith(".xml")) looksValid = true;

                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();

                long size = 0;
                try (OutputStream out = new FileOutputStream(target)) {
                    int n;
                    while ((n = zip.read(buffer)) > 0) {
                        size += n;
                        if (size > MAX_ENTRY_SIZE) throw new Exception("file too big in the backup: " + entry.getName());
                        out.write(buffer, 0, n);
                    }
                }
                count++;
            }

            if (count == 0) return "Nothing restored: this isn't a T-UI backup";
            return "Restored " + count + " files" + (looksValid ? "" : " (this doesn't look like a T-UI backup)") + ". Restarting...";
        } catch (Exception e) {
            Tuils.log(e);
            return "Restore failed: " + e.getMessage();
        }
    }
}
