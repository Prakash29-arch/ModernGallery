package com.modern.gallery;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Date-grouped grid used for Photos, Favorites, Videos, a single Album and the Recycle bin. */
public class MediaGridFragment extends Fragment implements TimelineAdapter.Listener {

    public static final String MODE_ALL = "all";
    public static final String MODE_FAVORITES = "favorites";
    public static final String MODE_VIDEOS = "videos";
    public static final String MODE_ALBUM = "album";
    public static final String MODE_TRASH = "trash";

    public static MediaGridFragment newInstance(String mode, long bucketId, String title, boolean showBack) {
        MediaGridFragment f = new MediaGridFragment();
        Bundle b = new Bundle();
        b.putString("mode", mode);
        b.putLong("bucket", bucketId);
        b.putString("title", title);
        b.putBoolean("back", showBack);
        f.setArguments(b);
        return f;
    }

    private String mode = MODE_ALL;
    private long bucketId;
    private String title = "";
    private boolean showBack;

    private MaterialToolbar toolbar;
    private RecyclerView recycler;
    private View progress;
    private TextView empty;
    private TimelineAdapter adapter;
    private List<MediaItem> all = new ArrayList<>();
    private String query = "";
    private boolean ignoreQuery;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private ActivityResultLauncher<IntentSenderRequest> senderLauncher;
    private Runnable pendingOk;
    private Runnable pendingCancel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle a = getArguments();
        if (a != null) {
            mode = a.getString("mode", MODE_ALL);
            bucketId = a.getLong("bucket");
            title = a.getString("title", "");
            showBack = a.getBoolean("back");
        }
        senderLauncher = registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        Runnable r = result.getResultCode() == android.app.Activity.RESULT_OK ? pendingOk : pendingCancel;
                        pendingOk = null;
                        pendingCancel = null;
                        if (r != null) r.run();
                        if (adapter != null) adapter.clearSelection();
                        refreshToolbar();
                        reload();
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_grid, container, false);
        toolbar = v.findViewById(R.id.toolbar);
        recycler = v.findViewById(R.id.recycler);
        progress = v.findViewById(R.id.progress);
        empty = v.findViewById(R.id.empty);

        int span = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 6 : 3;
        adapter = new TimelineAdapter(requireContext(), true, this);
        GridLayoutManager lm = new GridLayoutManager(requireContext(), span);
        lm.setSpanSizeLookup(adapter.spanLookup(span));
        recycler.setLayoutManager(lm);
        recycler.setAdapter(adapter);
        if (MODE_TRASH.equals(mode)) empty.setText("Recycle bin is empty");
        else if (MODE_FAVORITES.equals(mode)) empty.setText("No favorites yet");
        refreshToolbar();
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        toolbar = null;
        recycler = null;
    }

    // ---- loading ----

    private void reload() {
        if (recycler == null) return;
        final Context app = requireContext().getApplicationContext();
        if (all.isEmpty()) progress.setVisibility(View.VISIBLE);
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> list = MediaRepository.load(app, MODE_TRASH.equals(mode));
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded() || recycler == null) return;
                        all = list;
                        progress.setVisibility(View.GONE);
                        applyFilter();
                    }
                });
            }
        });
    }

    private void applyFilter() {
        if (!isAdded() || adapter == null) return;
        Set<String> favs = Prefs.favorites(requireContext());
        String q = query.trim().toLowerCase(Locale.getDefault());
        List<MediaItem> out = new ArrayList<>();
        for (MediaItem m : all) {
            if (MODE_FAVORITES.equals(mode) && !favs.contains(m.key())) continue;
            if (MODE_VIDEOS.equals(mode) && !m.video) continue;
            if (MODE_ALBUM.equals(mode) && m.bucketId != bucketId) continue;
            if (!q.isEmpty()) {
                String hay = (m.name + " " + m.bucketName + " " + Util.dayLabel(requireContext(), m.dateMillis)
                        + " " + (m.video ? "video" : "photo")).toLowerCase(Locale.getDefault());
                if (!hay.contains(q)) continue;
            }
            out.add(m);
        }
        adapter.setItems(out, favs);
        empty.setVisibility(out.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ---- toolbar ----

    private void refreshToolbar() {
        if (toolbar == null) return;
        toolbar.getMenu().clear();
        toolbar.setOnMenuItemClickListener(null);
        if (adapter != null && adapter.isSelecting()) {
            toolbar.setNavigationIcon(R.drawable.ic_close);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    adapter.clearSelection();
                    refreshToolbar();
                }
            });
            toolbar.setTitle(adapter.selectedCount() + " selected");
            toolbar.inflateMenu(MODE_TRASH.equals(mode) ? R.menu.menu_selection_trash : R.menu.menu_selection);
            toolbar.setOnMenuItemClickListener(new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return onSelectionAction(item.getItemId());
                }
            });
            return;
        }
        toolbar.setTitle(title);
        if (showBack) {
            toolbar.setNavigationIcon(R.drawable.ic_back);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    requireActivity().finish();
                }
            });
        } else {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
        }
        toolbar.inflateMenu(R.menu.menu_grid);
        final MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        SearchView sv = (SearchView) searchItem.getActionView();
        if (sv != null) {
            sv.setQueryHint("Search by name, album or date");
            sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String s) {
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String s) {
                    if (!ignoreQuery) {
                        query = s == null ? "" : s;
                        applyFilter();
                    }
                    return true;
                }
            });
            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    if (!ignoreQuery) {
                        query = "";
                        applyFilter();
                    }
                    return true;
                }
            });
            if (!query.isEmpty()) {
                ignoreQuery = true;
                searchItem.expandActionView();
                sv.setQuery(query, false);
                ignoreQuery = false;
            }
        }
    }

    // ---- adapter callbacks ----

    @Override
    public void onItemClick(MediaItem item, int index) {
        if (adapter.isSelecting()) {
            adapter.toggle(item);
            if (adapter.selectedCount() == 0) adapter.clearSelection();
            refreshToolbar();
            return;
        }
        ViewerSession.items = new ArrayList<>(adapter.getItems());
        Intent i = new Intent(requireContext(), ViewerActivity.class);
        i.putExtra(ViewerActivity.EXTRA_POS, index);
        i.putExtra(ViewerActivity.EXTRA_MODE, MODE_TRASH.equals(mode) ? ViewerActivity.MODE_TRASH : ViewerActivity.MODE_NORMAL);
        startActivity(i);
    }

    @Override
    public void onItemLongClick(MediaItem item) {
        if (!adapter.isSelecting()) adapter.startSelecting();
        adapter.toggle(item);
        if (adapter.selectedCount() == 0) adapter.clearSelection();
        refreshToolbar();
    }

    // ---- actions ----

    private boolean onSelectionAction(int id) {
        final List<MediaItem> sel = adapter.selectedItems();
        if (sel.isEmpty()) return true;
        final Context ctx = requireContext();
        if (id == R.id.action_share) {
            Intent share = Util.shareIntent(sel);
            if (share != null) startActivity(share);
            return true;
        }
        if (id == R.id.action_favorite) {
            boolean allFav = true;
            Set<String> favs = Prefs.favorites(ctx);
            for (MediaItem m : sel) if (!favs.contains(m.key())) allFav = false;
            Prefs.setFavorite(ctx, sel, !allFav);
            adapter.clearSelection();
            refreshToolbar();
            applyFilter();
            return true;
        }
        if (id == R.id.action_delete) {
            send(MediaStore.createTrashRequest(ctx.getContentResolver(), uris(sel), true),
                    toast("Moved to Recycle bin"), null);
            return true;
        }
        if (id == R.id.action_restore) {
            send(MediaStore.createTrashRequest(ctx.getContentResolver(), uris(sel), false),
                    toast("Restored"), null);
            return true;
        }
        if (id == R.id.action_delete_forever) {
            send(MediaStore.createDeleteRequest(ctx.getContentResolver(), uris(sel)),
                    toast("Deleted permanently"), null);
            return true;
        }
        if (id == R.id.action_hide) {
            hide(sel);
            return true;
        }
        return false;
    }

    private void hide(final List<MediaItem> sel) {
        final Context app = requireContext().getApplicationContext();
        if (!Prefs.hasPassword(app)) {
            Toast.makeText(app, "Set a password for Private space first", Toast.LENGTH_LONG).show();
            startActivity(new Intent(requireContext(), PrivateActivity.class));
            return;
        }
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> done = new ArrayList<>();
                for (MediaItem m : sel) if (Vault.importItem(app, m)) done.add(m);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded()) return;
                        if (done.isEmpty()) {
                            Toast.makeText(app, "Couldn't move items", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        PendingIntent pi = MediaStore.createDeleteRequest(app.getContentResolver(), uris(done));
                        final int n = done.size();
                        send(pi, new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(app, n + " moved to Private space", Toast.LENGTH_SHORT).show();
                            }
                        }, new Runnable() {
                            @Override
                            public void run() {
                                // User declined to remove the originals: drop the private copies again.
                                io.execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        for (MediaItem m : done) Vault.removeCopy(app, m);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    private Runnable toast(final String text) {
        final Context app = requireContext().getApplicationContext();
        return new Runnable() {
            @Override
            public void run() {
                Toast.makeText(app, text, Toast.LENGTH_SHORT).show();
            }
        };
    }

    private void send(PendingIntent pi, Runnable ok, Runnable cancel) {
        pendingOk = ok;
        pendingCancel = cancel;
        senderLauncher.launch(new IntentSenderRequest.Builder(pi.getIntentSender()).build());
    }

    private static List<Uri> uris(List<MediaItem> items) {
        List<Uri> out = new ArrayList<>();
        for (MediaItem m : items) out.add(m.uri);
        return out;
    }
}
