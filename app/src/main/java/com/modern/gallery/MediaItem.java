package com.modern.gallery;

import android.net.Uri;

import java.io.File;

public class MediaItem {
    public long id;
    public Uri uri;
    public boolean video;
    public String name = "";
    public String mime = "";
    public String bucketName = "";
    public String path = "";
    public long bucketId;
    public long dateMillis;
    public long size;
    public long duration;
    public int width;
    public int height;
    /** Set for items that live in the private vault. */
    public File file;
    /** Millis when the item was deleted (private recycle bin only). */
    public long deletedAt;

    public String key() {
        return uri.toString();
    }
}
