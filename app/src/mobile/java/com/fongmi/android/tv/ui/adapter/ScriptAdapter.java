package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterFileBinding;
import com.fongmi.android.tv.databinding.AdapterScriptFolderBinding;
import com.fongmi.android.tv.utils.ScriptManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ScriptAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_FOLDER = 0;
    private static final int TYPE_SCRIPT = 1;

    public interface OnDeleteListener {
        void onDelete(String folder);
    }

    private final OnDeleteListener listener;
    private final List<Object> items = new ArrayList<>();

    public ScriptAdapter(OnDeleteListener listener) {
        this.listener = listener;
    }

    public void refresh() {
        items.clear();
        items.addAll(ScriptManager.get().getFolders());
        items.addAll(ScriptManager.get().scanFiles());
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position) instanceof String ? TYPE_FOLDER : TYPE_SCRIPT;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_FOLDER) return new FolderHolder(AdapterScriptFolderBinding.inflate(inflater, parent, false));
        return new ScriptHolder(AdapterFileBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof FolderHolder) {
            FolderHolder folderHolder = (FolderHolder) holder;
            String folder = (String) items.get(position);
            folderHolder.binding.name.setText(folder);
            folderHolder.binding.delete.setOnClickListener(v -> listener.onDelete(folder));
        } else if (holder instanceof ScriptHolder) {
            ScriptHolder scriptHolder = (ScriptHolder) holder;
            File file = (File) items.get(position);
            scriptHolder.binding.name.setText(file.getName());
            scriptHolder.binding.image.setImageResource(R.drawable.ic_file);
        }
    }

    public static class FolderHolder extends RecyclerView.ViewHolder {

        private final AdapterScriptFolderBinding binding;

        FolderHolder(@NonNull AdapterScriptFolderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    public static class ScriptHolder extends RecyclerView.ViewHolder {

        private final AdapterFileBinding binding;

        ScriptHolder(@NonNull AdapterFileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
