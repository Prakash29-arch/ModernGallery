package com.modern.gallery;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Password-protected private space with its own recycle bin. */
public class PrivateActivity extends AppCompatActivity implements TimelineAdapter.Listener {

    private View lockView, vaultView;
    private TextInputLayout inputLayout, confirmLayout;
    private TextInputEditText inputPassword, inputConfirm;
    private TextView empty;
    private MaterialToolbar toolbar;
    private RecyclerView recycler;
    private TimelineAdapter adapter;

    private boolean setupMode;
    private boolean unlocked;
    private boolean trashMode;
    private boolean leavingToViewer;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_private);

        lockView = findViewById(R.id.lock_view);
        vaultView = findViewById(R.id.vault_view);
        inputLayout = findViewById(R.id.input_layout);
        confirmLayout = findViewById(R.id.confirm_layout);
        inputPassword = findViewById(R.id.input_password);
        inputConfirm = findViewById(R.id.input_confirm);
        empty = findViewById(R.id.empty);
        toolbar = findViewById(R.id.toolbar);
        recycler = findViewById(R.id.recycler);

        setupMode = !Prefs.hasPassword(this);
        TextView hint = findViewById(R.id.lock_hint);
        com.google.android.material.button.MaterialButton button = findViewById(R.id.btn_unlock);
        if (setupMode) {
            ((TextView) findViewById(R.id.lock_title)).setText("Create a password");
            hint.setText("Photos and videos you move here are hidden from the gallery and other apps.\n"
                    + "If you forget the password they can't be recovered.");
            confirmLayout.setVisibility(View.VISIBLE);
            button.setText("Create");
        } else {
            hint.setText("Enter your password to open Private space.");
            button.setText("Unlock");
        }
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                submitPassword();
            }
        });

        adapter = new TimelineAdapter(this, true, this);
        recycler.setLayoutManager(makeLayoutManager());
        recycler.setAdapter(adapter);

        ((MaterialButtonToggleGroup) findViewById(R.id.toggle)).addOnButtonCheckedListener(
                new MaterialButtonToggleGroup.OnButtonCheckedListener() {
                    @Override
                    public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                        if (!isChecked) return;
                        trashMode = checkedId == R.id.seg_trash;
                        adapter.clearSelection();
                        refreshToolbar();
                        reload();
                    }
                });
        refreshToolbar();
    }

    private GridLayoutManager makeLayoutManager() {
        GridLayoutManager lm = new GridLayoutManager(this, 3);
        lm.setSpanSizeLookup(adapter.spanLookup(3));
        return lm;
    }

    private void submitPassword() {
        String pw = inputPassword.getText() == null ? "" : inputPassword.getText().toString();
        inputLayout.setError(null);
        confirmLayout.setError(null);
        if (setupMode) {
            String confirm = inputConfirm.getText() == null ? "" : inputConfirm.getText().toString();
            if (pw.length() < 4) {
                inputLayout.setError("Use at least 4 characters");
                return;
            }
            if (!pw.equals(confirm)) {
                confirmLayout.setError("Passwords don't match");
                return;
            }
            Prefs.setPassword(this, pw);
            unlock();
        } else if (Prefs.checkPassword(this, pw)) {
            unlock();
        } else {
            inputLayout.setError("Wrong password");
        }
    }

    private void unlock() {
        unlocked = true;
        inputPassword.setText("");
        inputConfirm.setText("");
        lockView.setVisibility(View.GONE);
        vaultView.setVisibility(View.VISIBLE);
        reload();
    }

    @Override
    protected void onResume() {
        super.onResume();
        leavingToViewer = false;
        if (unlocked) reload();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Lock again as soon as the user leaves (unless they are just opening a photo).
        if (!isChangingConfigurations() && !leavingToViewer) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }

    // ---- list ----

    private void reload() {
        final Context app = getApplicationContext();
        final boolean trash = trashMode;
        io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaItem> list = Vault.list(app, trash);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        adapter.setItems(list, new java.util.HashSet<String>());
                        empty.setText(trash
                                ? "Recycle bin is empty.\nItems are removed automatically after 30 days."
                                : "No private items.\nIn Photos, long-press items and choose 'Move to Private space'.");
                        empty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private void refreshToolbar() {
        toolbar.getMenu().clear();
        toolbar.setOnMenuItemClickListener(null);
        if (adapter.isSelecting()) {
            toolbar.setNavigationIcon(R.drawable.ic_close);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    adapter.clearSelection();
                    refreshToolbar();
                }
            });
            toolbar.setTitle(adapter.selectedCount() + " selected");
            toolbar.inflateMenu(trashMode ? R.menu.menu_selection_trash : R.menu.menu_selection_private);
            toolbar.setOnMenuItemClickListener(new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    return onAction(item.getItemId());
                }
            });
        } else {
            toolbar.setNavigationIcon(R.drawable.ic_back);
            toolbar.setNavigationOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
            toolbar.setTitle("Private space");
        }
    }

    @Override
    public void onItemClick(MediaItem item, int index) {
        if (adapter.isSelecting()) {
            adapter.toggle(item);
            if (adapter.selectedCount() == 0) adapter.clearSelection();
            refreshToolbar();
            return;
        }
        ViewerSession.items = new ArrayList<>(adapter.getItems());
        leavingToViewer = true;
        Intent i = new Intent(this, ViewerActivity.class);
        i.putExtra(ViewerActivity.EXTRA_POS, index);
        i.putExtra(ViewerActivity.EXTRA_MODE, trashMode ? ViewerActivity.MODE_PRIVATE_TRASH : ViewerActivity.MODE_PRIVATE);
        startActivity(i);
    }

    @Override
    public void onItemLongClick(MediaItem item) {
        if (!adapter.isSelecting()) adapter.startSelecting();
        adapter.toggle(item);
        if (adapter.selectedCount() == 0) adapter.clearSelection();
        refreshToolbar();
    }

    private boolean onAction(int id) {
        final List<MediaItem> sel = adapter.selectedItems();
        if (sel.isEmpty()) return true;
        final Context app = getApplicationContext();
        if (id == R.id.action_delete) { // private -> private recycle bin
            for (MediaItem m : sel) Vault.moveToTrash(app, m.file);
            Toast.makeText(app, "Moved to private Recycle bin", Toast.LENGTH_SHORT).show();
            finishAction();
            return true;
        }
        if (id == R.id.action_restore && trashMode) { // recycle bin -> private
            for (MediaItem m : sel) Vault.restoreFromTrash(app, m.file);
            finishAction();
            return true;
        }
        if (id == R.id.action_restore) { // private -> gallery
            io.execute(new Runnable() {
                @Override
                public void run() {
                    int n = 0;
                    for (MediaItem m : sel) if (Vault.exportToGallery(app, m.file)) n++;
                    final int moved = n;
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(app, moved + " moved to Pictures/Restored", Toast.LENGTH_SHORT).show();
                            finishAction();
                        }
                    });
                }
            });
            return true;
        }
        if (id == R.id.action_delete_forever) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Delete permanently?")
                    .setMessage("This can't be undone.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Delete", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int which) {
                            for (MediaItem m : sel) Vault.deleteForever(m.file);
                            finishAction();
                        }
                    }).show();
            return true;
        }
        return false;
    }

    private void finishAction() {
        adapter.clearSelection();
        refreshToolbar();
        reload();
    }
}
