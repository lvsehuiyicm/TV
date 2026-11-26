package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.Product;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterSectionBinding;

import java.util.ArrayList;
import java.util.List;

public class SectionAdapter extends RecyclerView.Adapter<SectionAdapter.ViewHolder> {

    private static final int MAX_ITEMS = 6;

    private final OnClickListener listener;
    private final List<Section> mItems;
    private String siteKey;

    public SectionAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onSectionAllClick(Class type);

        void onSectionItemClick(Class type, Vod item);

        boolean onSectionItemLongClick(Class type, Vod item);
    }

    public static class Section {
        public final Class type;
        public List<Vod> items = new ArrayList<>();
        public boolean loaded;

        public Section(Class type) {
            this.type = type;
        }
    }

    public void setSections(String siteKey, List<Class> types) {
        this.siteKey = siteKey;
        mItems.clear();
        for (Class type : types) mItems.add(new Section(type));
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int getItemCount() {
        return mItems.size();
    }

    public Class get(int position) {
        return mItems.get(position).type;
    }

    public void setSectionItems(Class type, List<Vod> list) {
        int position = indexOf(type);
        if (position < 0) return;
        Section section = mItems.get(position);
        section.items = new ArrayList<>(list.size() > MAX_ITEMS ? list.subList(0, MAX_ITEMS) : list);
        section.loaded = true;
        notifyItemChanged(position);
    }

    public void removeSection(Class type) {
        int position = indexOf(type);
        if (position < 0) return;
        mItems.remove(position);
        notifyItemRemoved(position);
    }

    private int indexOf(Class type) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).type.getTypeId().equals(type.getTypeId())) return i;
        }
        return -1;
    }

    private Style getStyle(Class type) {
        Site site = VodConfig.get().getSite(siteKey);
        return type.isFolder() ? Style.list() : site.getStyle(type.getStyle());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSectionBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Section section = mItems.get(position);
        holder.binding.name.setText(section.type.getTypeName());
        holder.binding.all.setOnClickListener(v -> listener.onSectionAllClick(section.type));
        holder.binding.placeholder.setVisibility(section.loaded ? View.GONE : View.VISIBLE);
        holder.binding.recycler.setVisibility(section.loaded ? View.VISIBLE : View.GONE);
        if (!section.loaded) return;
        Style style = getStyle(section.type);
        holder.binding.recycler.setLayoutManager(style.isList() ? new LinearLayoutManager(holder.itemView.getContext()) : new GridLayoutManager(holder.itemView.getContext(), Product.getColumn(holder.itemView.getContext(), style)));
        VodAdapter adapter = new VodAdapter(new VodAdapter.OnClickListener() {
            @Override
            public void onItemClick(Vod item) {
                if (section.loaded) listener.onSectionItemClick(section.type, item);
            }

            @Override
            public boolean onLongClick(Vod item) {
                return section.loaded && listener.onSectionItemLongClick(section.type, item);
            }
        }, style, Product.getSpec(holder.itemView.getContext(), style));
        holder.binding.recycler.setAdapter(adapter);
        adapter.addAll(section.items);
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSectionBinding binding;

        ViewHolder(@NonNull AdapterSectionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
