package com.modern.gallery;

import java.util.ArrayList;
import java.util.List;

/** Hands the list currently shown in a grid over to the full-screen viewer. */
public final class ViewerSession {
    private ViewerSession() {
    }

    public static List<MediaItem> items = new ArrayList<>();
}
