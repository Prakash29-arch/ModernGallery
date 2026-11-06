package com.modern.gallery;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AlbumsFragment extends Fragment {

    private static class Album {
        long bucketId;
        String name;
        MediaItem cover;
        int count;
    }

    private RecyclerView recycler;
    private View progress;
    private final List<Album> albums = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private RecyclerView.Adapter<AlbumHolder> adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_albums, container, false);
        recycler = v.findViewById(R.id.recycler);
        progress = v.findViewById(R.id.progress);
        recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        adapter = new RecyclerView.Adapter<AlbumHolder>() {
            @NonNull
            @Override
            public AlbumHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                return new AlbumHolder(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_album, parent, false));
            }

            @Override
            public void onBindViewHolder(@NonNull AlbumHolder h, int position) {
                final Album a = albums.get(position);
                h.name.setText(a.name);
                h.count.setText(String.valueOf(a.count));
                Glide.with(h.cover).load(a.cover.uri).centerCrop().into(h.cover);
                h.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent i = new Intent(requireContext(), GridActivity.class);
                        i.putExtra(GridActivity.EXTRA_MODE, MediaGridFragment.MODE_ALBUM);
                        i.putExtra(GridActivity.EXTRA_BUCKET, a.bucketId);
                        i.putExtra(GridActivity.EXTRA_TITLE, a.name);
                        startActivity(i);
                    }
                });
            }

            @Override
            public int getItemCount() {
                return albums.size();
            }
        };
        recycler.setAdapter(adapter);
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        final Context app = requireContext().getApplicationContext();
        io.execute(new Runnable() {
            @Override
            public void run() {
                List<MediaItem> items = MediaRepository.load(app, false);
                Map<Long, Album> map = new LinkedHashMap<>();
                for (MediaItem m : items) {
                    Album a = map.get(m.bucketId);
                    if (a == null) {
                        a = new Album();
                        a.bucketId = m.bucketId;
                        a.name = m.bucketName.isEmpty() ? "Other" : m.bucketName;
                        a.cover = m; // items are newest first
                        map.put(m.bucketId, a);
                    }
                    a.count++;
                }
                final List<Album> list = new ArrayList<>(map.values());
                Collections.sort(list, new Comparator<Album>() {
                    @Override
                    public int compare(Album x, Album y) {
                        return Integer.compare(y.count, x.count);
                    }
                });
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!isAdded() || recycler == null) return;
                        albums.clear();
                        albums.addAll(list);
                        progress.setVisibility(View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        recycler = null;
    }

    static class AlbumHolder extends RecyclerView.ViewHolder {
        final android.widget.ImageView cover;
        final TextView name;
        final TextView count;

        AlbumHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.cover);
            name = v.findViewById(R.id.name);
            count = v.findViewById(R.id.count);
        }
    }
}
