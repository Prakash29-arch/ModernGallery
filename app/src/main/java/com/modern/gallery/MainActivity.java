package com.modern.gallery;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView nav;
    private View permissionView;
    private ActivityResultLauncher<String[]> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        nav = findViewById(R.id.bottom_nav);
        permissionView = findViewById(R.id.permission_view);

        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                new ActivityResultCallback<Map<String, Boolean>>() {
                    @Override
                    public void onActivityResult(Map<String, Boolean> result) {
                        updateAccess();
                    }
                });
        findViewById(R.id.btn_grant).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                permissionLauncher.launch(requiredPermissions());
            }
        });

        nav.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                show(item.getItemId());
                return true;
            }
        });

        if (hasAccess()) {
            if (savedInstanceState == null) show(R.id.nav_photos);
        } else {
            permissionView.setVisibility(View.VISIBLE);
            permissionLauncher.launch(requiredPermissions());
        }
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO,
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
        }
        if (Build.VERSION.SDK_INT >= 33) {
            return new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO};
        }
        return new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
    }

    private boolean granted(String p) {
        return ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            return granted(Manifest.permission.READ_MEDIA_IMAGES)
                    || granted(Manifest.permission.READ_MEDIA_VIDEO)
                    || (Build.VERSION.SDK_INT >= 34 && granted("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"));
        }
        return granted(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void updateAccess() {
        if (hasAccess()) {
            permissionView.setVisibility(View.GONE);
            show(nav.getSelectedItemId());
        } else {
            permissionView.setVisibility(View.VISIBLE);
        }
    }

    private void show(int id) {
        Fragment f;
        if (id == R.id.nav_albums) {
            f = new AlbumsFragment();
        } else if (id == R.id.nav_favorites) {
            f = MediaGridFragment.newInstance(MediaGridFragment.MODE_FAVORITES, 0L, "Favorites", false);
        } else if (id == R.id.nav_library) {
            f = new LibraryFragment();
        } else {
            f = MediaGridFragment.newInstance(MediaGridFragment.MODE_ALL, 0L, "Photos", false);
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.container, f).commitAllowingStateLoss();
    }
}
