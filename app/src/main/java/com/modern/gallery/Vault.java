package com.modern.gallery;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Private space: files are copied into the app's own storage (not visible to other apps or to the
 * system gallery) and shown only after the password is entered. Deleted items go to a private
 * recycle bin and are purged after 30 days.
 */
public final class Vault {
    private Vault() {
    }

    private static final long TRASH_DAYS_MS = 30L * 24 * 60 * 60 * 1000;

    private static File dir(Context c, String name) {
        File d = new File(c.getFilesDir(), name);
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File vaultDir(Context c) {
        return dir(c, "vault");
    }

    public static File trashDir(Context c) {
        return dir(c, "vault_trash");
    }

    /** Copies one gallery item into the vault. */
    public static boolean importItem(Context c, MediaItem item) {
        File dst = new File(vaultDir(c), System.nanoTime() + "__" + safeName(item.name));
        ContentResolver cr = c.getContentResolver();
        try (InputStream in = cr.openInputStream(item.uri);
             OutputStream out = new FileOutputStream(dst)) {
            if (in == null) return false;
            copy(in, out);
        } catch (Exception e) {
            dst.delete();
            return false;
        }
        if (item.dateMillis > 0) dst.setLastModified(item.dateMillis);
        return true;
    }

    public static void removeCopy(Context c, MediaItem item) {
        File[] files = vaultDir(c).listFiles();
        if (files == null) return;
        String tail = "__" + safeName(item.name);
        for (File f : files) {
            if (f.getName().endsWith(tail) && f.length() == item.size) {
                f.delete();
                return;
            }
        }
    }

    public static List<MediaItem> list(Context c, boolean trash) {
        if (trash) purge(c);
        File[] files = (trash ? trashDir(c) : vaultDir(c)).listFiles();
        List<MediaItem> out = new ArrayList<>();
        if (files == null) return out;
        for (File f : files) {
            if (!f.isFile()) continue;
            MediaItem m = new MediaItem();
            m.file = f;
            m.uri = Uri.fromFile(f);
            m.name = displayName(f.getName());
            m.size = f.length();
            m.dateMillis = f.lastModified();
            m.path = f.getParent();
            m.mime = mimeFor(m.name);
            m.video = m.mime.startsWith("video/");
            if (trash) m.deletedAt = deletedAt(f.getName());
            out.add(m);
        }
        Collections.sort(out, new Comparator<MediaItem>() {
            @Override
            public int compare(MediaItem a, MediaItem b) {
                return Long.compare(b.dateMillis, a.dateMillis);
            }
        });
        return out;
    }

    /** Moves a vault file to the private recycle bin. */
    public static boolean moveToTrash(Context c, File f) {
        File dst = new File(trashDir(c), System.currentTimeMillis() + "##" + f.getName());
        long modified = f.lastModified();
        boolean ok = f.renameTo(dst);
        if (ok) dst.setLastModified(modified);
        return ok;
    }

    public static boolean restoreFromTrash(Context c, File f) {
        String n = f.getName();
        int i = n.indexOf("##");
        String orig = i >= 0 ? n.substring(i + 2) : n;
        File dst = new File(vaultDir(c), orig);
        long modified = f.lastModified();
        boolean ok = f.renameTo(dst);
        if (ok) dst.setLastModified(modified);
        return ok;
    }

    public static boolean deleteForever(File f) {
        return f.delete();
    }

    public static void purge(Context c) {
        File[] files = trashDir(c).listFiles();
        if (files == null) return;
        long now = System.currentTimeMillis();
        for (File f : files) {
            long t = deletedAt(f.getName());
            if (t > 0 && now - t > TRASH_DAYS_MS) f.delete();
        }
    }

    /** Copies a vault file back into the public gallery (Pictures/Restored or Movies/Restored). */
    public static boolean exportToGallery(Context c, File f) {
        String name = displayName(f.getName());
        String mime = mimeFor(name);
        boolean video = mime.startsWith("video/");
        ContentResolver cr = c.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                (video ? Environment.DIRECTORY_MOVIES : Environment.DIRECTORY_PICTURES) + "/Restored");
        v.put(MediaStore.MediaColumns.DATE_TAKEN, f.lastModified());
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri collection = video
                ? MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                : MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri uri = cr.insert(collection, v);
        if (uri == null) return false;
        try (InputStream in = new java.io.FileInputStream(f);
             OutputStream out = cr.openOutputStream(uri)) {
            if (out == null) throw new java.io.IOException("no stream");
            copy(in, out);
        } catch (Exception e) {
            cr.delete(uri, null, null);
            return false;
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        cr.update(uri, done, null, null);
        f.delete();
        return true;
    }

    // ---- helpers ----

    private static void copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[64 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.flush();
    }

    private static String safeName(String name) {
        if (name == null || name.isEmpty()) return "item";
        return name.replace('/', '_').replace('\\', '_');
    }

    /** Strips the "<number>__" and "<number>##" prefixes. */
    private static String displayName(String stored) {
        String n = stored;
        int i = n.indexOf("##");
        if (i >= 0) n = n.substring(i + 2);
        i = n.indexOf("__");
        if (i >= 0) n = n.substring(i + 2);
        return n;
    }

    private static long deletedAt(String stored) {
        int i = stored.indexOf("##");
        if (i <= 0) return 0L;
        try {
            return Long.parseLong(stored.substring(0, i));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static String mimeFor(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime == null ? "image/jpeg" : mime;
    }
}
