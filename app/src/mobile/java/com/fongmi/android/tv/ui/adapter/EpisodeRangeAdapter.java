package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterEpisodeRangeBinding;

import java.util.ArrayList;
import java.util.List;

/** 0.1.128：详情页选集上面的范围 chips（1-40、41-80……），和集数格子同款 chip 样式，选中那组是琥珀底。 */
public class EpisodeRangeAdapter extends RecyclerView.Adapter<EpisodeRangeAdapter.ViewHolder> {

    public interface OnClickListener {

        void onItemClick(int position);
    }

    private final OnClickListener listener;
    private final List<String> items = new ArrayList<>();
    private int selected;

    public EpisodeRangeAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItems(List<String> items, int selected) {
        this.items.clear();
        this.items.addAll(items);
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        if (position == selected || position < 0 || position >= items.size()) return;
        int old = selected;
        selected = position;
        notifyItemChanged(old);
        notifyItemChanged(selected);
    }

    public int getSelected() {
        return selected;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpisodeRangeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setText(items.get(position));
        holder.binding.text.setSelected(position == selected);
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(position));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpisodeRangeBinding binding;

        ViewHolder(@NonNull AdapterEpisodeRangeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
