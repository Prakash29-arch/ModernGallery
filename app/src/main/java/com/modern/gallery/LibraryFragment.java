package com.modern.gallery;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class LibraryFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_library, container, false);
        v.findViewById(R.id.row_videos).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(MediaGridFragment.MODE_VIDEOS, "Videos");
            }
        });
        v.findViewById(R.id.row_trash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                open(MediaGridFragment.MODE_TRASH, "Recycle bin");
            }
        });
        v.findViewById(R.id.row_private).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(requireContext(), PrivateActivity.class));
            }
        });
        return v;
    }

    private void open(String mode, String title) {
        Intent i = new Intent(requireContext(), GridActivity.class);
        i.putExtra(GridActivity.EXTRA_MODE, mode);
        i.putExtra(GridActivity.EXTRA_TITLE, title);
        startActivity(i);
    }
}
