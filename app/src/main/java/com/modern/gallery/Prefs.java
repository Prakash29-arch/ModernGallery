package com.modern.gallery;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Favorites and the private-space password. */
public final class Prefs {
    private Prefs() {
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("gallery", Context.MODE_PRIVATE);
    }

    public static Set<String> favorites(Context c) {
        return new HashSet<>(sp(c).getStringSet("favorites", new HashSet<String>()));
    }

    public static void setFavorite(Context c, Collection<MediaItem> items, boolean fav) {
        Set<String> set = favorites(c);
        for (MediaItem m : items) {
            if (fav) set.add(m.key());
            else set.remove(m.key());
        }
        sp(c).edit().putStringSet("favorites", set).apply();
    }

    public static boolean isFavorite(Context c, MediaItem m) {
        return favorites(c).contains(m.key());
    }

    // ---- private space password ----

    public static boolean hasPassword(Context c) {
        return sp(c).getString("pw_hash", null) != null;
    }

    public static void setPassword(Context c, String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        String hash = hash(password, salt);
        sp(c).edit()
                .putString("pw_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("pw_hash", hash)
                .apply();
    }

    public static boolean checkPassword(Context c, String password) {
        String saltB64 = sp(c).getString("pw_salt", null);
        String stored = sp(c).getString("pw_hash", null);
        if (saltB64 == null || stored == null) return false;
        byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
        return stored.equals(hash(password, salt));
    }

    private static String hash(String password, byte[] salt) {
        try {
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] h = f.generateSecret(new PBEKeySpec(password.toCharArray(), salt, 60000, 256)).getEncoded();
            return Base64.encodeToString(h, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
