package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterIfaceSheetBinding;
import com.fongmi.android.tv.utils.ConfigManager;

import java.util.ArrayList;
import java.util.List;

public class IfaceSheetAdapter extends RecyclerView.Adapter<IfaceSheetAdapter.ViewHolder> {

    /** sub == null 的行是首行“全部接口”。 */
    public static class Row {
        public final ConfigManager.Sub sub;
        public final String count;

        public Row(ConfigManager.Sub sub, String count) {
            this.sub = sub;
            this.count = count;
        }
    }

    private final OnClickListener listener;
    private final List<Row> rows = new ArrayList<>();
    private String activeId = "";

    public IfaceSheetAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(ConfigManager.Sub sub);

        void onAllClick();
    }

    public void refresh(List<Row> rows, String activeId) {
        this.rows.clear();
        this.rows.addAll(rows);
        this.activeId = activeId == null ? "" : activeId;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterIfaceSheetBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Row row = rows.get(position);
        boolean current;
        if (row.sub == null) {
            holder.binding.icon.setImageResource(R.drawable.ic_view_grid);
            holder.binding.name.setText(R.string.config_all_interfaces);
            holder.binding.count.setVisibility(View.GONE);
            current = activeId.isEmpty();
            holder.binding.getRoot().setOnClickListener(v -> listener.onAllClick());
        } else {
            ConfigManager.Sub sub = row.sub;
            holder.binding.icon.setImageResource(iconOf(sub));
            holder.binding.name.setText(sub.name);
            if (row.count == null) holder.binding.count.setVisibility(View.GONE);
            else {
                holder.binding.count.setVisibility(View.VISIBLE);
                holder.binding.count.setText(row.count);
            }
            current = sub.id.equals(activeId);
            holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(sub));
        }
        holder.binding.getRoot().setSelected(current);
        holder.binding.name.setSelected(current);
        holder.binding.count.setSelected(current);
        holder.binding.check.setVisibility(current ? View.VISIBLE : View.GONE);
    }

    private static int iconOf(ConfigManager.Sub sub) {
        if (sub.isFolder()) return R.drawable.ic_folder;
        if (sub.isUrl() || sub.isLiveUrl()) return R.drawable.ic_action_web;
        return R.drawable.ic_file;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterIfaceSheetBinding binding;

        ViewHolder(@NonNull AdapterIfaceSheetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
