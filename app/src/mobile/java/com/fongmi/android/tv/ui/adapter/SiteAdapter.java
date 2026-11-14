package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.utils.SiteOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> all = new ArrayList<>();
    private final List<Site> items = new ArrayList<>();
    private String keyword = "";
    private int sort = SiteOrder.getSort();

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        for (Site site : VodConfig.get().getSites()) if (!site.isHide()) all.add(site);
        applySort();
        items.addAll(all);
    }

    public interface OnClickListener {

        void onTextClick(Site item);

        void onDeleteClick(Site item);
    }

    public List<Site> getItems() {
        return items;
    }

    public int getSort() {
        return sort;
    }

    public boolean isFiltering() {
        return keyword != null && !keyword.trim().isEmpty();
    }

    public void setSort(int sort) {
        this.sort = sort;
        SiteOrder.putSort(sort);
        applySort();
        filter(keyword);
    }

    private void applySort() {
        if (sort == SiteOrder.SORT_NAME) {
            SiteOrder.sortByName(all);
        } else if (sort == SiteOrder.SORT_TIME) {
            SiteOrder.sortByTime(all);
        } else {
            List<Site> ordered = SiteOrder.applyOrder(all);
            all.clear();
            all.addAll(ordered);
        }
    }

    public void filter(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
        items.clear();
        if (!isFiltering()) {
            items.addAll(all);
        } else {
            String key = this.keyword.toLowerCase(Locale.getDefault());
            for (Site site : all) if (site.getName().toLowerCase(Locale.getDefault()).contains(key)) items.add(site);
        }
        notifyDataSetChanged();
    }

    public boolean canDrag() {
        return sort == SiteOrder.SORT_DEFAULT && !isFiltering();
    }

    public void onMove(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        Collections.swap(items, from, to);
        int allFrom = all.indexOf(items.get(to));
        int allTo = all.indexOf(items.get(from));
        if (allFrom >= 0 && allTo >= 0) Collections.swap(all, allFrom, allTo);
        SiteOrder.putOrder(SiteOrder.keysOf(all));
        notifyItemMoved(from, to);
    }

    public void remove(Site item) {
        all.remove(item);
        int position = items.indexOf(item);
        if (position >= 0) {
            items.remove(position);
            notifyItemRemoved(position);
        }
        SiteOrder.putOrder(SiteOrder.keysOf(all));
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
        holder.binding.close.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSiteBinding binding;

        ViewHolder(@NonNull AdapterSiteBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
