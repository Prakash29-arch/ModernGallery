package com.modern.gallery;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Simple photo editor: rotate, flip, crop, brightness / contrast / saturation and filters. */
public class EditorActivity extends AppCompatActivity {

    private static final String[] CROP_NAMES = {"Original", "1:1", "4:3", "3:4", "16:9", "9:16", "3:2", "2:3"};
    private static final float[] CROP_RATIOS = {0f, 1f, 4f / 3f, 3f / 4f, 16f / 9f, 9f / 16f, 3f / 2f, 2f / 3f};
    private static final String[] FILTER_NAMES = {"None", "Mono", "Noir", "Sepia", "Vivid", "Cool", "Warm", "Fade"};

    private ImageView preview;
    private View panelCrop, panelAdjust, panelFilters;
    private Bitmap base;
    private Bitmap shown;
    private float cropRatio = 0f;
    private int brightness = 0;
    private int contrast = 0;
    private int saturation = 0;
    private int filterIndex = 0;
    private boolean saving;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_editor);
        final Uri uri = getIntent().getData();
        if (uri == null) {
            finish();
            return;
        }
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        MenuItem save = toolbar.getMenu().add(Menu.NONE, 1, Menu.NONE, "Save");
        save.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        toolbar.setOnMenuItemClickListener(new androidx.appcompat.widget.Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                save();
                return true;
            }
        });

        preview = findViewById(R.id.preview);
        panelCrop = findViewById(R.id.panel_crop);
        panelAdjust = findViewById(R.id.panel_adjust);
        panelFilters = findViewById(R.id.panel_filters);

        findViewById(R.id.tool_rotate).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (base == null) return;
                Matrix m = new Matrix();
                m.postRotate(90);
                base = Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true);
                updateImage();
            }
        });
        findViewById(R.id.tool_flip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (base == null) return;
                Matrix m = new Matrix();
                m.postScale(-1f, 1f);
                base = Bitmap.createBitmap(base, 0, 0, base.getWidth(), base.getHeight(), m, true);
                updateImage();
            }
        });
        findViewById(R.id.tool_crop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(panelCrop);
            }
        });
        findViewById(R.id.tool_adjust).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(panelAdjust);
            }
        });
        findViewById(R.id.tool_filters).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showPanel(panelFilters);
            }
        });

        buildChips((LinearLayout) findViewById(R.id.crop_chips), CROP_NAMES, true);
        buildChips((LinearLayout) findViewById(R.id.filter_chips), FILTER_NAMES, false);

        bindSeek(R.id.seek_brightness, 0);
        bindSeek(R.id.seek_contrast, 1);
        bindSeek(R.id.seek_saturation, 2);

        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final Bitmap bmp = ImageDecoder.decodeBitmap(
                            ImageDecoder.createSource(getContentResolver(), uri),
                            new ImageDecoder.OnHeaderDecodedListener() {
                                @Override
                                public void onHeaderDecoded(ImageDecoder decoder, ImageDecoder.ImageInfo info,
                                                            ImageDecoder.Source source) {
                                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                                    Size s = info.getSize();
                                    int max = Math.max(s.getWidth(), s.getHeight());
                                    if (max > 4096) {
                                        float f = 4096f / max;
                                        decoder.setTargetSize(Math.round(s.getWidth() * f), Math.round(s.getHeight() * f));
                                    }
                                }
                            });
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            base = bmp;
                            updateImage();
                        }
                    });
                } catch (Exception | OutOfMemoryError e) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(EditorActivity.this, "Couldn't open this image", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }
            }
        });
    }

    private void showPanel(View which) {
        boolean already = which.getVisibility() == View.VISIBLE;
        panelCrop.setVisibility(View.GONE);
        panelAdjust.setVisibility(View.GONE);
        panelFilters.setVisibility(View.GONE);
        if (!already) which.setVisibility(View.VISIBLE);
    }

    private void buildChips(LinearLayout row, String[] names, final boolean crop) {
        for (int i = 0; i < names.length; i++) {
            MaterialButton b = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(names[i]);
            final int index = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(8);
            b.setLayoutParams(lp);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (crop) {
                        cropRatio = CROP_RATIOS[index];
                        updateImage();
                    } else {
                        filterIndex = index;
                        updateFilter();
                    }
                }
            });
            row.addView(b);
        }
    }

    private void bindSeek(int id, final int which) {
        SeekBar sb = findViewById(id);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int v = progress - 100;
                if (which == 0) brightness = v;
                else if (which == 1) contrast = v;
                else saturation = v;
                updateFilter();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    // ---- rendering ----

    private static Bitmap crop(Bitmap b, float ratio) {
        if (ratio <= 0f) return b;
        int w = b.getWidth();
        int h = b.getHeight();
        float cur = (float) w / h;
        int nw = w;
        int nh = h;
        if (cur > ratio) nw = Math.round(h * ratio);
        else nh = Math.round(w / ratio);
        nw = Math.max(1, Math.min(nw, w));
        nh = Math.max(1, Math.min(nh, h));
        return Bitmap.createBitmap(b, (w - nw) / 2, (h - nh) / 2, nw, nh);
    }

    private void updateImage() {
        if (base == null) return;
        shown = crop(base, cropRatio);
        preview.setImageBitmap(shown);
        updateFilter();
    }

    private void updateFilter() {
        preview.setColorFilter(new ColorMatrixColorFilter(buildMatrix()));
    }

    private ColorMatrix buildMatrix() {
        ColorMatrix cm = filterMatrix(filterIndex);
        ColorMatrix sat = new ColorMatrix();
        sat.setSaturation(1f + saturation / 100f);
        cm.postConcat(sat);
        float c = 1f + contrast / 100f;
        float t = 128f * (1f - c) + brightness * 1.28f;
        cm.postConcat(new ColorMatrix(new float[]{
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0}));
        return cm;
    }

    private static ColorMatrix filterMatrix(int index) {
        ColorMatrix m = new ColorMatrix();
        switch (index) {
            case 1: // Mono
                m.setSaturation(0f);
                break;
            case 2: { // Noir
                m.setSaturation(0f);
                m.postConcat(new ColorMatrix(new float[]{
                        1.3f, 0, 0, 0, -38f,
                        0, 1.3f, 0, 0, -38f,
                        0, 0, 1.3f, 0, -38f,
                        0, 0, 0, 1, 0}));
                break;
            }
            case 3: // Sepia
                m.set(new float[]{
                        0.393f, 0.769f, 0.189f, 0, 0,
                        0.349f, 0.686f, 0.168f, 0, 0,
                        0.272f, 0.534f, 0.131f, 0, 0,
                        0, 0, 0, 1, 0});
                break;
            case 4: // Vivid
                m.setSaturation(1.5f);
                m.postConcat(new ColorMatrix(new float[]{
                        1.1f, 0, 0, 0, -13f,
                        0, 1.1f, 0, 0, -13f,
                        0, 0, 1.1f, 0, -13f,
                        0, 0, 0, 1, 0}));
                break;
            case 5: // Cool
                m.set(new float[]{
                        0.9f, 0, 0, 0, 0,
                        0, 1f, 0, 0, 0,
                        0, 0, 1.15f, 0, 0,
                        0, 0, 0, 1, 0});
                break;
            case 6: // Warm
                m.set(new float[]{
                        1.15f, 0, 0, 0, 0,
                        0, 1f, 0, 0, 0,
                        0, 0, 0.9f, 0, 0,
                        0, 0, 0, 1, 0});
                break;
            case 7: // Fade
                m.set(new float[]{
                        0.85f, 0, 0, 0, 30f,
                        0, 0.85f, 0, 0, 30f,
                        0, 0, 0.85f, 0, 30f,
                        0, 0, 0, 1, 0});
                break;
            default:
                break;
        }
        return m;
    }

    // ---- saving ----

    private void save() {
        if (base == null || saving) return;
        saving = true;
        final Bitmap src = crop(base, cropRatio);
        final ColorMatrix cm = buildMatrix();
        Toast.makeText(this, "Saving…", Toast.LENGTH_SHORT).show();
        io.execute(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(out);
                    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
                    paint.setColorFilter(new ColorMatrixColorFilter(cm));
                    canvas.drawBitmap(src, 0, 0, paint);

                    ContentResolver cr = getContentResolver();
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, "IMG_edited_" + System.currentTimeMillis() + ".jpg");
                    v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Edited");
                    v.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis());
                    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                    Uri uri = cr.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
                    if (uri != null) {
                        try (OutputStream os = cr.openOutputStream(uri)) {
                            ok = os != null && out.compress(Bitmap.CompressFormat.JPEG, 95, os);
                        }
                        if (ok) {
                            ContentValues done = new ContentValues();
                            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
                            cr.update(uri, done, null, null);
                        } else {
                            cr.delete(uri, null, null);
                        }
                    }
                } catch (Exception | OutOfMemoryError e) {
                    ok = false;
                }
                final boolean success = ok;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        saving = false;
                        Toast.makeText(EditorActivity.this,
                                success ? "Saved a copy to Pictures/Edited" : "Couldn't save", Toast.LENGTH_SHORT).show();
                        if (success) finish();
                    }
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdown();
    }
}
