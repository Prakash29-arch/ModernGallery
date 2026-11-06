package com.modern.gallery;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Grid of thumbnails, optionally grouped under date headers, with multi-select. */
public class TimelineAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onItemClick(MediaItem item, int indexInItems);

        void onItemLongClick(MediaItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Context context;
    private final Listener listener;
    private final boolean grouped;
    private final List<Object> rows = new ArrayList<>();
    private final List<MediaItem> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private Set<String> favorites = new HashSet<>();
    private boolean selecting;

    public TimelineAdapter(Context context, boolean grouped, Listener listener) {
        this.context = context;
        this.grouped = grouped;
        this.listener = listener;
    }

    public void setItems(List<MediaItem> newItems, Set<String> favs) {
        items.clear();
        items.addAll(newItems);
        favorites = favs;
        rows.clear();
        String lastLabel = null;
        for (MediaItem m : items) {
            if (grouped) {
                String label = Util.dayLabel(context, m.dateMillis);
                if (!label.equals(lastLabel)) {
                    rows.add(label);
                    lastLabel = label;
                }
            }
            rows.add(m);
        }
        // Drop selections that no longer exist.
        Set<String> keys = new HashSet<>();
        for (MediaItem m : items) keys.add(m.key());
        selected.retainAll(keys);
        notifyDataSetChanged();
    }

    public List<MediaItem> getItems() {
        return items;
    }

    public GridLayoutManager.SpanSizeLookup spanLookup(final int spanCount) {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                return getItemViewType(position) == TYPE_HEADER ? spanCount : 1;
            }
        };
    }

    // ---- selection ----

    public boolean isSelecting() {
        return selecting;
    }

    public void startSelecting() {
        selecting = true;
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selected.clear();
        selecting = false;
        notifyDataSetChanged();
    }

    public void toggle(MediaItem m) {
        if (!selected.remove(m.key())) selected.add(m.key());
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (MediaItem m : items) selected.add(m.key());
        notifyDataSetChanged();
    }

    public int selectedCount() {
        return selected.size();
    }

    public List<MediaItem> selectedItems() {
        List<MediaItem> out = new ArrayList<>();
        for (MediaItem m : items) if (selected.contains(m.key())) out.add(m);
        return out;
    }

    // ---- adapter ----

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ITEM;
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inf.inflate(R.layout.item_header, parent, false));
        }
        return new ItemHolder(inf.inflate(R.layout.item_media, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).text.setText((String) row);
            return;
        }
        final MediaItem m = (MediaItem) row;
        ItemHolder h = (ItemHolder) holder;
        Glide.with(h.thumb).load(m.uri).centerCrop().into(h.thumb);
        h.videoBadge.setVisibility(m.video ? View.VISIBLE : View.GONE);
        if (m.video) h.duration.setText(m.duration > 0 ? Util.formatDuration(m.duration) : "");
        h.fav.setVisibility(favorites.contains(m.key()) ? View.VISIBLE : View.GONE);
        boolean sel = selected.contains(m.key());
        h.dim.setVisibility(sel ? View.VISIBLE : View.GONE);
        h.check.setVisibility(selecting && sel ? View.VISIBLE : View.GONE);
        h.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onItemClick(m, items.indexOf(m));
            }
        });
        h.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onItemLongClick(m);
                return true;
            }
        });
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View v) {
            super(v);
            text = (TextView) v;
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final View videoBadge;
        final TextView duration;
        final View fav;
        final View dim;
        final View check;

        ItemHolder(View v) {
            super(v);
            thumb = v.findViewById(R.id.thumb);
            videoBadge = v.findViewById(R.id.video_badge);
            duration = v.findViewById(R.id.duration);
            fav = v.findViewById(R.id.fav_badge);
            dim = v.findViewById(R.id.select_dim);
            check = v.findViewById(R.id.select_check);
        }
    }
}
