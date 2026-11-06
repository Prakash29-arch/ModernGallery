package com.modern.gallery;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Reads photos and videos from MediaStore. */
public final class MediaRepository {
    private MediaRepository() {
    }

    private static final String[] PROJECTION = {
            MediaStore.Files.FileColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH
    };

    /** @param trashed true to read the system recycle bin, false for normal items */
    public static List<MediaItem> load(Context context, boolean trashed) {
        List<MediaItem> out = new ArrayList<>();
        Bundle args = new Bundle();
        args.putString(ContentResolver.QUERY_ARG_SQL_SELECTION,
                MediaStore.Files.FileColumns.MEDIA_TYPE + " IN (?,?)");
        args.putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, new String[]{
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE),
                String.valueOf(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)});
        args.putInt(MediaStore.QUERY_ARG_MATCH_TRASHED,
                trashed ? MediaStore.MATCH_ONLY : MediaStore.MATCH_EXCLUDE);
        try (Cursor c = context.getContentResolver().query(
                MediaStore.Files.getContentUri("external"), PROJECTION, args, null)) {
            if (c == null) return out;
            while (c.moveToNext()) {
                MediaItem m = new MediaItem();
                m.id = c.getLong(0);
                m.name = str(c, 1);
                int type = c.getInt(2);
                m.video = type == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                m.mime = str(c, 3);
                long taken = c.getLong(4);
                long modified = c.getLong(5) * 1000L;
                m.dateMillis = taken > 0 ? taken : modified;
                m.size = c.getLong(6);
                m.width = c.getInt(7);
                m.height = c.getInt(8);
                m.duration = c.getLong(9);
                m.bucketId = c.getLong(10);
                m.bucketName = str(c, 11);
                m.path = str(c, 12);
                m.uri = ContentUris.withAppendedId(m.video
                        ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                        : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, m.id);
                out.add(m);
            }
        } catch (RuntimeException e) {
            // Permission revoked or provider error: show what we have.
        }
        Collections.sort(out, new Comparator<MediaItem>() {
            @Override
            public int compare(MediaItem a, MediaItem b) {
                return Long.compare(b.dateMillis, a.dateMillis);
            }
        });
        return out;
    }

    private static String str(Cursor c, int i) {
        String s = c.getString(i);
        return s == null ? "" : s;
    }
}
