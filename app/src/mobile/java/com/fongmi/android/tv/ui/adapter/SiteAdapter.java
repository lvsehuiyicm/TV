package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> all = new ArrayList<>();
    private final List<Site> items = new ArrayList<>();

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        for (Site site : VodConfig.get().getSites()) if (!site.isHide()) all.add(site);
        items.addAll(all);
    }

    public interface OnClickListener {

        void onTextClick(Site item);
    }

    public List<Site> getItems() {
        return items;
    }

    public void filter(String keyword) {
        items.clear();
        if (keyword == null || keyword.trim().isEmpty()) {
            items.addAll(all);
        } else {
            String key = keyword.toLowerCase(Locale.getDefault());
            for (Site site : all) if (site.getName().toLowerCase(Locale.getDefault()).contains(key)) items.add(site);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = items.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setOnClickListener(v -> listener.onTextClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
