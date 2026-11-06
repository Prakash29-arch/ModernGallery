package com.modern.gallery;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ViewerActivity extends AppCompatActivity {
    public static final String EXTRA_POS = "pos";
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_NORMAL = "normal";
    public static final String MODE_TRASH = "trash";
    public static final String MODE_PRIVATE = "private";
    public static final String MODE_PRIVATE_TRASH = "private_trash";
    private static final String MODE_EXTERNAL = "external";

    private static final int MENU_INFO = 1;
    private static final int MENU_HIDE = 2;
    private static final int MENU_UNHIDE = 3;

    private List<MediaItem> items = new ArrayList<>();
    private String mode = MODE_NORMAL;
    private ViewPager2 pager;
    private MaterialToolbar topBar;
    private View bottomBar;
    private ImageButton btnShare, btnFav, btnEdit, btnDelete, btnMore;
    private PageAdapter adapter;
    private boolean barsVisible = true;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<IntentSenderRequest> sender;
    private Runnable pendingOk;
    private Runnable pendingCancel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String m = getIntent().getStringExtra(EXTRA_MODE);
        if (m != null) mode = m;
        boolean privateMode = MODE_PRIVATE.equals(mode) || MODE_PRIVATE_TRASH.equals(mode);
        if (privateMode) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        }
        setContentView(R.layout.activity_viewer);

        int start = getIntent().getIntExtra(EXTRA_POS, 0);
        Uri data = getIntent().getData();
        if (data != null) {
            mode = MODE_EXTERNAL;
            items = new ArrayList<>(Collections.singletonList(fromExternalUri(data)));
            start = 0;
        } else {
            items = new ArrayList<>(ViewerSession.items);
        }
        if (items.isEmpty()) {
            finish();
            return;
        }

        sender = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        Runnable r = result.getResultCode() == RESULT_OK ? pendingOk : pendingCancel;
                        pendingOk = null;
                        pendingCancel = null;
                        if (r != null) r.run();
                    }
                });

        pager = findViewById(R.id.pager);
        topBar = findViewById(R.id.top_bar);
        bottomBar = findViewById(R.id.bottom_bar);
        btnShare = findViewById(R.id.btn_share);
        btnFav = findViewById(R.id.btn_fav);
        btnEdit = findViewById(R.id.btn_edit);
        btnDelete = findViewById(R.id.btn_delete);
        btnMore = findViewById(R.id.btn_more);

        topBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        adapter = new PageAdapter();
        pager.setAdapter(adapter);
        pager.setCurrentItem(Math.max(0, Math.min(start, items.size() - 1)), false);
        pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateUi();
            }
        });

        configureButtons();
        updateUi();
    }

    private MediaItem fromExternalUri(Uri uri) {
        MediaItem m = new MediaItem();
        m.uri = uri;
        ContentResolver cr = getContentResolver();
        String type = getIntent().getType();
        if (type == null) type = cr.getType(uri);
        m.mime = type == null ? "image/*" : type;
        m.video = m.mime.startsWith("video/");
        m.name = uri.getLastPathSegment() == null ? "Image" : uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) {
            try (Cursor c = cr.query(uri, new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    if (c.getString(0) != null) m.name = c.getString(0);
                    m.size = c.getLong(1);
                }
            } catch (RuntimeException ignored) {
            }
        }
        return m;
    }

    private MediaItem current() {
        int p = pager.getCurrentItem();
        return items.get(Math.max(0, Math.min(p, items.size() - 1)));
    }

    // ---- UI state ----

    private void configureButtons() {
        boolean normal = MODE_NORMAL.equals(mode);
        boolean trash = MODE_TRASH.equals(mode);
        boolean priv = MODE_PRIVATE.equals(mode);
        boolean privTrash = MODE_PRIVATE_TRASH.equals(mode);
        boolean external = MODE_EXTERNAL.equals(mode);

        btnShare.setVisibility(normal || external ? View.VISIBLE : View.GONE);
        btnFav.setVisibility(normal || trash || privTrash ? View.VISIBLE : View.GONE);
        btnEdit.setVisibility(normal || external ? View.VISIBLE : View.GONE);
        btnDelete.setVisibility(external ? View.GONE : View.VISIBLE);
        btnMore.setVisibility(trash || privTrash ? View.GONE : View.VISIBLE);
        if (trash || privTrash) {
            btnFav.setImageResource(R.drawable.ic_restore);
            btnFav.setContentDescription("Restore");
        }

        btnShare.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = Util.shareIntent(Collections.singletonList(current()));
                if (i != null) startActivity(i);
            }
        });
        btnFav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onFavOrRestore();
            }
        });
        btnEdit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MediaItem m = current();
                if (m.video) {
                    Toast.makeText(ViewerActivity.this, "Editing is available for photos", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(ViewerActivity.this, EditorActivity.class);
                i.setData(m.uri);
                startActivity(i);
            }
        });
        btnDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDelete();
            }
        });
        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMore(v);
            }
        });
    }

    private void updateUi() {
        if (items.isEmpty()) return;
        MediaItem m = current();
        topBar.setTitle(m.dateMillis > 0 ? Util.dayLabel(this, m.dateMillis) : m.name);
        topBar.setSubtitle(m.dateMillis > 0 ? Util.dateTime(this, m.dateMillis) : "");
        if (MODE_NORMAL.equals(mode)) {
            boolean fav = Prefs.isFavorite(this, m);
            btnFav.setImageResource(fav ? R.drawable.ic_favorite : R.drawable.ic_favorite_border);
        }
    }

    private void toggleBars() {
        barsVisible = !barsVisible;
        int v = barsVisible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(v);
        bottomBar.setVisibility(v);
    }

    // ---- actions ----

    private void onFavOrRestore() {
        final MediaItem m = current();
        if (MODE_NORMAL.equals(mode)) {
            boolean fav = Prefs.isFavorite(this, m);
            Prefs.setFavorite(this, Collections.singletonList(m), !fav);
            updateUi();
        } else if (MODE_TRASH.equals(mode)) {
            send(MediaStore.createTrashRequest(getContentResolver(), Collections.singletonList(m.uri), false),
                    new Runnable() {
                        @Override
                        public void run() {
                            removeCurrent();
                        }
                    }, null);
        } else if (MODE_PRIVATE_TRASH.equals(mode)) {
            Vault.restoreFromTrash(this, m.file);
            removeCurrent();
        }
    }

    private void onDelete() {
        final MediaItem m = current();
        if (MODE_NORMAL.equals(mode)) {
            send(MediaStore.createTrashRequest(getContentResolver(), Collections.singletonList(m.uri), true),
                    new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(ViewerActivity.this, "Moved to Recycle bin", Toast.LENGTH_SHORT).show();
                            removeCurrent();
                        }
                    }, null);
        } else if (MODE_TRASH.equals(mode)) {
            send(MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(m.uri)),
                    new Runnable() {
                        @Override
                        public void run() {
                            removeCurrent();
                        }
                    }, null);
        } else if (MODE_PRIVATE.equals(mode)) {
            Vault.moveToTrash(this, m.file);
            Toast.makeText(this, "Moved to private Recycle bin", Toast.LENGTH_SHORT).show();
            removeCurrent();
        } else if (MODE_PRIVATE_TRASH.equals(mode)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete permanently?")
                    .setMessage("This can't be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int which) {
                            Vault.deleteForever(m.file);
                            removeCurrent();
                        }
                    }).show();
        }
    }

    private void showMore(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu menu = pm.getMenu();
        menu.add(0, MENU_INFO, 0, "Info");
        if (MODE_NORMAL.equals(mode)) menu.add(0, MENU_HIDE, 1, "Move to Private space");
        if (MODE_PRIVATE.equals(mode)) menu.add(0, MENU_UNHIDE, 1, "Move out of Private space");
        pm.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                switch (item.getItemId()) {
                    case MENU_INFO:
                        new MaterialAlertDialogBuilder(ViewerActivity.this)
                                .setTitle("Info")
                                .setMessage(Util.infoText(ViewerActivity.this, current()))
                                .setPositiveButton("OK", null)
                                .show();
                        return true;
                    case MENU_HIDE:
                        hideCurrent();
                        return true;
                    case MENU_UNHIDE:
                        unhideCurrent();
                        return true;
                    default:
                        return false;
                }
            }
        });
        pm.show();
    }

    private void hideCurrent() {
        final MediaItem m = current();
        if (!Prefs.hasPassword(this)) {
            Toast.makeText(this, "Set a password for Private space first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, PrivateActivity.class));
            return;
        }
        final android.content.Context app = getApplicationContext();
        io.execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = Vault.importItem(app, m);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (!ok) {
                            Toast.makeText(app, "Couldn't move item", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        send(MediaStore.createDeleteRequest(getContentResolver(), Collections.singletonList(m.uri)),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(app, "Moved to Private space", Toast.LENGTH_SHORT).show();
                                        removeCurrent();
                                    }
                                }, new Runnable() {
                                    @Override
                                    public void run() {
                                        io.execute(new Runnable() {
                                            @Override
                                            public void run() {
                                                Vault.removeCopy(app, m);
                                            }
                                        });
                                    }
                                });
                    }
                });
            }
        });
    }

    private void unhideCurrent() {
        final MediaItem m = current();
        final android.content.Context app = getApplicationContext();
        io.execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = Vault.exportToGallery(app, m.file);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        Toast.makeText(app, ok ? "Moved to Pictures/Restored" : "Couldn't move item",
                                Toast.LENGTH_SHORT).show();
                        if (ok) removeCurrent();
                    }
                });
            }
        });
    }

    private void send(PendingIntent pi, Runnable ok, Runnable cancel) {
        pendingOk = ok;
        pendingCancel = cancel;
        sender.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
    }

    private void removeCurrent() {
        int p = pager.getCurrentItem();
        if (p < 0 || p >= items.size()) return;
        items.remove(p);
        if (items.isEmpty()) {
            finish();
            return;
        }
        adapter.notifyItemRemoved(p);
        updateUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    // ---- pages ----

    private class PageAdapter extends RecyclerView.Adapter<PageHolder> {
        @NonNull
        @Override
        public PageHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext()).inflate(R.layout.page_media, parent, false);
            return new PageHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull PageHolder h, int position) {
            final MediaItem m = items.get(position);
            Glide.with(h.zoom).load(m.uri).override(2560, 2560).downsample(DownsampleStrategy.AT_MOST).into(h.zoom);
            h.play.setVisibility(m.video ? View.VISIBLE : View.GONE);
            h.play.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent i = new Intent(ViewerActivity.this, VideoActivity.class);
                    i.setData(m.uri);
                    startActivity(i);
                }
            });
            h.zoom.setTapListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleBars();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private static class PageHolder extends RecyclerView.ViewHolder {
        final ZoomImageView zoom;
        final ImageView play;

        PageHolder(View v) {
            super(v);
            zoom = v.findViewById(R.id.zoom);
            play = v.findViewById(R.id.play);
        }
    }
}
