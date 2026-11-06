package com.modern.gallery;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateUtils;
import android.text.format.Formatter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public final class Util {
    private Util() {
    }

    public static String formatDuration(long ms) {
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s);
        return String.format(Locale.getDefault(), "%d:%02d", m, s);
    }

    /** "Today", "Yesterday" or a readable date. */
    public static String dayLabel(Context c, long millis) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        if (sameDay(now, then)) return "Today";
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(now, then)) return "Yesterday";
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL;
        if (Calendar.getInstance().get(Calendar.YEAR) != then.get(Calendar.YEAR)) {
            flags |= DateUtils.FORMAT_SHOW_YEAR;
        }
        return DateUtils.formatDateTime(c, millis, flags);
    }

    public static String dateTime(Context c, long millis) {
        return DateUtils.formatDateTime(c, millis,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_YEAR
                        | DateUtils.FORMAT_ABBREV_MONTH);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    public static String fileSize(Context c, long bytes) {
        return Formatter.formatFileSize(c, bytes);
    }

    public static Intent shareIntent(List<MediaItem> items) {
        ArrayList<Uri> uris = new ArrayList<>();
        boolean allImages = true;
        boolean allVideos = true;
        for (MediaItem m : items) {
            if (m.file != null) continue; // private items are never shared
            uris.add(m.uri);
            if (m.video) allImages = false;
            else allVideos = false;
        }
        if (uris.isEmpty()) return null;
        Intent i;
        if (uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        i.setType(allImages ? "image/*" : allVideos ? "video/*" : "*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return Intent.createChooser(i, "Share");
    }

    public static String infoText(Context c, MediaItem m) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(m.name).append('\n');
        if (m.dateMillis > 0) sb.append("Date: ").append(dateTime(c, m.dateMillis)).append('\n');
        if (m.size > 0) sb.append("Size: ").append(fileSize(c, m.size)).append('\n');
        if (m.width > 0 && m.height > 0) {
            sb.append("Resolution: ").append(m.width).append(" x ").append(m.height).append('\n');
        }
        if (m.video && m.duration > 0) sb.append("Duration: ").append(formatDuration(m.duration)).append('\n');
        if (!m.mime.isEmpty()) sb.append("Type: ").append(m.mime).append('\n');
        if (m.file != null) sb.append("Location: Private space");
        else if (!m.path.isEmpty()) sb.append("Location: ").append(m.path);
        return sb.toString().trim();
    }
}
