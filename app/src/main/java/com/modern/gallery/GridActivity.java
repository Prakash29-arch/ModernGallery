package com.modern.gallery;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class GridActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_BUCKET = "bucket";
    public static final String EXTRA_TITLE = "title";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_grid);
        if (savedInstanceState == null) {
            String mode = getIntent().getStringExtra(EXTRA_MODE);
            if (mode == null) mode = MediaGridFragment.MODE_ALL;
            String title = getIntent().getStringExtra(EXTRA_TITLE);
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.grid_container, MediaGridFragment.newInstance(
                            mode, getIntent().getLongExtra(EXTRA_BUCKET, 0L), title == null ? "" : title, true))
                    .commit();
        }
    }
}
