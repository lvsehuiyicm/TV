package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterIfaceTabBinding;
import com.fongmi.android.tv.utils.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class IfaceTabAdapter extends RecyclerView.Adapter<IfaceTabAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<ConfigManager.Sub> items = new ArrayList<>();
    private String activeId = "";

    public IfaceTabAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onIfaceClick(ConfigManager.Sub sub);

        void onAllClick();
    }

    /** 刷新 tab 列表与选中态：activeId 为空＝全部接口，全部 tab 选中。 */
    public void refresh(List<ConfigManager.Sub> subs, String activeId) {
        items.clear();
        items.addAll(subs);
        this.activeId = activeId == null ? "" : activeId;
        notifyDataSetChanged();
    }

    /** 拖拽用的内部列表引用（不要重建 Sub 对象——ConfigManager.saveVodOrder 靠它们排顺序）。 */
    public List<ConfigManager.Sub> getSubs() {
        return items;
    }

    public void onMove(int from, int to) {
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    public interface OnTabOrderChangedListener {

        void onTabOrderChanged(List<ConfigManager.Sub> subs);
    }

    private OnTabOrderChangedListener orderListener;

    public void setOnTabOrderChangedListener(OnTabOrderChangedListener listener) {
        orderListener = listener;
    }

    /** 松手后调一次：把拖完的顺序交给 VodFragment 持久化。 */
    public void dispatchOrderChanged() {
        if (orderListener != null) orderListener.onTabOrderChanged(items);
    }

    @Override
    public int getItemCount() {
        return items.size() + 1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterIfaceTabBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < items.size()) {
            ConfigManager.Sub sub = items.get(position);
            holder.binding.text.setText(sub.name);
            holder.binding.text.setSelected(sub.id.equals(activeId));
            holder.binding.text.setOnClickListener(v -> listener.onIfaceClick(sub));
        } else {
            holder.binding.text.setText(R.string.iface_all);
            holder.binding.text.setSelected(activeId.isEmpty());
            holder.binding.text.setOnClickListener(v -> listener.onAllClick());
        }
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterIfaceTabBinding binding;

        ViewHolder(@NonNull AdapterIfaceTabBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
