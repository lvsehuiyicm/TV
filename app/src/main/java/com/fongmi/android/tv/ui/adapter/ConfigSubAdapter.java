package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.databinding.AdapterConfigSubBinding;
import com.fongmi.android.tv.utils.CheckStore;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.SourceChecker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConfigSubAdapter extends RecyclerView.Adapter<ConfigSubAdapter.ViewHolder> {

    public interface OnClickListener {
        void onRowClick(ConfigManager.Sub item);

        void onToggle(ConfigManager.Sub item, boolean enabled);

        void onEdit(ConfigManager.Sub item);

        void onDelete(ConfigManager.Sub item);
    }

    /** 本地算出来的源数（免联网）：count < 0 表示不知道。 */
    public static class Count {
        public final int count;
        public final int countKind;

        public Count(int count, int countKind) {
            this.count = count;
            this.countKind = countKind;
        }
    }

    private final OnClickListener listener;
    private final List<ConfigManager.Sub> items = new ArrayList<>();
    private final Map<String, Status> statuses = new HashMap<>();
    private final Map<String, Count> counts = new HashMap<>();
    private ItemTouchHelper touchHelper;

    /** 检测结果（按源 id 存，refresh 换数据也不丢）。 */
    private static class Status {
        final String text;
        final int grade;
        final int count;
        final int countKind;

        Status(String text, int grade, int count, int countKind) {
            this.text = text;
            this.grade = grade;
            this.count = count;
            this.countKind = countKind;
        }
    }

    public ConfigSubAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setItemTouchHelper(ItemTouchHelper helper) {
        this.touchHelper = helper;
    }

    public List<ConfigManager.Sub> getItems() {
        return new ArrayList<>(items);
    }

    /** 拖动换位（0.1.118 首页 tab 同款：只换内存顺序，松手后由 fragment 一次存盘）。 */
    public void onMove(int from, int to) {
        Collections.swap(items, from, to);
        notifyItemMoved(from, to);
    }

    public void setStatus(String subId, String text, int grade, int count, int countKind) {
        statuses.put(subId, new Status(text, grade, count, countKind));
        notifyDataSetChanged();
    }

    public void clearStatus() {
        statuses.clear();
        notifyDataSetChanged();
    }

    public void setCounts(Map<String, Count> counts) {
        this.counts.clear();
        if (counts != null) this.counts.putAll(counts);
        notifyDataSetChanged();
    }

    /** 画健康点用的成绩：刚测的优先，没有就用上次存进 CheckStore 的。 */
    public int getGrade(String subId) {
        Status status = statuses.get(subId);
        if (status != null) return status.grade;
        return CheckStore.getSubGrade(subId);
    }

    public void refresh(List<ConfigManager.Sub> subs) {
        items.clear();
        items.addAll(subs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigSubBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConfigManager.Sub item = items.get(position);
        holder.binding.name.setText(item.name);
        holder.binding.subtitle.setText(subtitle(holder.itemView.getContext(), item));
        holder.binding.dot.setBackgroundTintList(ColorStateList.valueOf(SourceChecker.gradeColor(getGrade(item.id))));
        boolean active = isActive(item);
        holder.binding.active.setVisibility(active ? View.VISIBLE : View.GONE);
        if (active) holder.binding.active.setSelected(true);
        holder.binding.divider.setVisibility(position == getItemCount() - 1 ? View.GONE : View.VISIBLE);
        // 开关先清监听再设状态（复用 holder 时 setChecked 不触发 onToggle），拨动走 onToggle。
        holder.binding.enableSwitch.setOnCheckedChangeListener(null);
        holder.binding.enableSwitch.setChecked(item.enabled);
        holder.binding.enableSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> listener.onToggle(item, isChecked));
        holder.itemView.setOnClickListener(v -> listener.onRowClick(item));
        // 长按行不再弹菜单，只做拖动调顺序（触发方式和原来 6 个点长按一致）。
        holder.itemView.setOnLongClickListener(v -> {
            if (touchHelper != null) touchHelper.startDrag(holder);
            return true;
        });
        // 6 个点常显（当前使用行也一样，不然它没地方进编辑菜单）：点它出编辑菜单，长按拖动。
        holder.binding.dragHandle.setOnClickListener(v -> showMenu(holder.itemView, item));
        holder.binding.dragHandle.setOnLongClickListener(v -> {
            if (touchHelper != null) touchHelper.startDrag(holder);
            return true;
        });
    }

    /** 当前使用的接口：点播看 ConfigManager 的 activeId；直播看 LiveConfig 正在用的地址。 */
    private boolean isActive(ConfigManager.Sub item) {
        if (item.tab == ConfigManager.TAB_VOD) return item.id.equals(ConfigManager.getActiveId());
        if (!item.enabled) return false;
        try {
            return item.getConfigUrl().equals(LiveConfig.getUrl());
        } catch (Exception e) {
            return false;
        }
    }

    /** 点 6 个点出的编辑菜单：只剩改名/删除，启用/停用已交给行里的开关。 */
    private void showMenu(View anchor, ConfigManager.Sub item) {
        Context context = anchor.getContext();
        new MaterialAlertDialogBuilder(context)
                .setItems(new CharSequence[]{context.getString(R.string.config_edit), context.getString(R.string.config_delete)}, (dialog, which) -> {
                    if (which == 0) listener.onEdit(item);
                    else if (which == 1) listener.onDelete(item);
                })
                .show();
    }

    /**
     * 行下面那行小字：有检测成绩先看成绩——黄/红直接显示成绩文案（“较慢 · 74 个源”）；
     * 绿的只显示数量（“128 个源”）；没测过就用本地算出来的数，在线接口没数只写类型。
     */
    private String subtitle(Context context, ConfigManager.Sub item) {
        Status status = statuses.get(item.id);
        int count = -1;
        int countKind = SourceChecker.COUNT_NONE;
        if (status != null && status.countKind != SourceChecker.COUNT_NONE) {
            count = status.count;
            countKind = status.countKind;
        } else {
            Count local = counts.get(item.id);
            if (local != null) {
                count = local.count;
                countKind = local.countKind;
            }
        }
        if (status != null && (status.grade == SourceChecker.GRADE_YELLOW || status.grade == SourceChecker.GRADE_RED)) return status.text;
        if (item.isLiveFile() || item.isLiveUrl()) {
            if (countKind != SourceChecker.COUNT_CHANNELS || count < 0) return kindText(context, item);
            int resId = item.isLiveUrl() ? R.string.config_sub_live_online : R.string.config_sub_live_local;
            return context.getString(resId) + " · " + context.getString(R.string.config_channel_count, count);
        }
        if (item.isJson()) {
            if (countKind != SourceChecker.COUNT_SITES || count < 0) return kindText(context, item);
            return context.getString(R.string.config_sub_json) + " · " + context.getString(R.string.iface_source_count, count);
        }
        if (item.isFolder()) {
            if (countKind != SourceChecker.COUNT_SOURCES || count < 0) return kindText(context, item);
            return kindText(context, item) + " · " + context.getString(R.string.iface_source_count, count);
        }
        if (item.isFile()) return kindText(context, item);
        if (countKind == SourceChecker.COUNT_SITES && count >= 0) return context.getString(R.string.iface_source_count, count);
        return kindText(context, item);
    }

    private String kindText(Context context, ConfigManager.Sub item) {
        int resId;
        if (item.isFolder()) resId = R.string.config_kind_folder;
        else if (item.isFile()) resId = R.string.config_kind_file;
        else if (item.isUrl()) resId = R.string.config_kind_url;
        else if (item.isJson()) resId = R.string.config_kind_json;
        else if (item.isLiveUrl()) resId = R.string.config_kind_live_url;
        else resId = R.string.config_kind_live_file;
        return context.getString(resId);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterConfigSubBinding binding;

        public ViewHolder(@NonNull AdapterConfigSubBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
