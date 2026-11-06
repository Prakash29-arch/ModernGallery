package com.modern.gallery;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.appcompat.widget.AppCompatImageView;

/** Image view with pinch-to-zoom, double-tap zoom and panning. */
public class ZoomImageView extends AppCompatImageView {
    private final Matrix matrix = new Matrix();
    private final float[] values = new float[9];
    private final RectF rect = new RectF();
    private float baseScale = 1f;
    private ScaleGestureDetector scaleDetector;
    private GestureDetector gestureDetector;
    private OnClickListener tapListener;

    public ZoomImageView(Context context) {
        super(context);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        setScaleType(ScaleType.MATRIX);
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float cur = currentScale();
                float target = cur * detector.getScaleFactor();
                float min = baseScale * 0.9f;
                float max = baseScale * 6f;
                if (target < min) target = min;
                if (target > max) target = max;
                float factor = target / cur;
                matrix.postScale(factor, factor, detector.getFocusX(), detector.getFocusY());
                clamp();
                setImageMatrix(matrix);
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                if (currentScale() < baseScale) {
                    fit();
                }
            }
        });
        gestureDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (getDrawable() == null) return true;
                if (currentScale() > baseScale * 1.05f) {
                    fit();
                } else {
                    float factor = (baseScale * 2.5f) / currentScale();
                    matrix.postScale(factor, factor, e.getX(), e.getY());
                    clamp();
                    setImageMatrix(matrix);
                }
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (isZoomed()) {
                    matrix.postTranslate(-dx, -dy);
                    clamp();
                    setImageMatrix(matrix);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (tapListener != null) {
                    tapListener.onClick(ZoomImageView.this);
                }
                return true;
            }
        });
    }

    public void setTapListener(OnClickListener l) {
        tapListener = l;
    }

    private boolean isZoomed() {
        return currentScale() > baseScale * 1.01f;
    }

    private float currentScale() {
        matrix.getValues(values);
        float s = values[Matrix.MSCALE_X];
        return s == 0f ? 1f : s;
    }

    @Override
    public void setImageDrawable(Drawable drawable) {
        super.setImageDrawable(drawable);
        fit();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fit();
    }

    private void fit() {
        Drawable d = getDrawable();
        if (d == null || getWidth() == 0 || getHeight() == 0) return;
        float dw = d.getIntrinsicWidth();
        float dh = d.getIntrinsicHeight();
        if (dw <= 0 || dh <= 0) return;
        baseScale = Math.min(getWidth() / dw, getHeight() / dh);
        matrix.reset();
        matrix.postScale(baseScale, baseScale);
        matrix.postTranslate((getWidth() - dw * baseScale) / 2f, (getHeight() - dh * baseScale) / 2f);
        setImageMatrix(matrix);
    }

    private void clamp() {
        Drawable d = getDrawable();
        if (d == null) return;
        rect.set(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        matrix.mapRect(rect);
        float dx = 0f;
        float dy = 0f;
        if (rect.width() <= getWidth()) {
            dx = (getWidth() - rect.width()) / 2f - rect.left;
        } else if (rect.left > 0) {
            dx = -rect.left;
        } else if (rect.right < getWidth()) {
            dx = getWidth() - rect.right;
        }
        if (rect.height() <= getHeight()) {
            dy = (getHeight() - rect.height()) / 2f - rect.top;
        } else if (rect.top > 0) {
            dy = -rect.top;
        } else if (rect.bottom < getHeight()) {
            dy = getHeight() - rect.bottom;
        }
        matrix.postTranslate(dx, dy);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        int action = event.getActionMasked();
        View parent = (View) getParent();
        if (parent != null) {
            boolean block = isZoomed() || scaleDetector.isInProgress() || event.getPointerCount() > 1;
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                block = false;
            }
            getParent().requestDisallowInterceptTouchEvent(block);
        }
        return true;
    }
}
