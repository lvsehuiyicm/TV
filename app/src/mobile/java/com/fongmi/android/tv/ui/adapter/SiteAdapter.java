package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteBinding;
import com.fongmi.android.tv.utils.CheckStore;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.SiteOrder;
import com.fongmi.android.tv.utils.SourceChecker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class SiteAdapter extends RecyclerView.Adapter<SiteAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Site> all = new ArrayList<>();
    private final List<Site> items = new ArrayList<>();
    private String keyword = "";

    public SiteAdapter(OnClickListener listener) {
        this.listener = listener;
        all.addAll(buildList());
        items.addAll(all);
    }

    /** 首页源列表：0.1.105 接口隔离＋SiteOrder 排序＋0.1.113 当前首页源置顶；源弹窗共用。 */
    public static List<Site> buildList() {
        List<Site> result = new ArrayList<>();
        // 0.1.105: 接口隔离——当前是 JSON/在线接口时，只显示接口自己的源；
        // 手机本地 py/js 脚本源（key 以 script_ 开头）是跨接口追加进来的，
        // 不在这里显示，它们仍在 本地 tab 和文件夹/单文件接口里。
        // 文件夹/单文件接口下 isSiteVisible 本来就只留本文件夹/文件的源，
        // 不动；没选接口时也不动。接口自己的本地文件源（XBPQ/drpy2，
        // key 不是 script_）照常显示。
        ConfigManager.Sub active = ConfigManager.getActiveSub();
        boolean isolate = active != null && active.isInterface();
        List<Site> visible = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isHide() && (!isolate || !SiteOrder.isLocalScript(site))) visible.add(site);
        }
        // 安全网：过滤后为空（接口配置没加载出来，只剩本地脚本源）时回退到
        // 不过滤，绝不让对话框因此变空白/自动消失。
        if (visible.isEmpty()) for (Site site : VodConfig.get().getSites()) if (!site.isHide()) visible.add(site);
        result.addAll(visible);
        List<Site> ordered = SiteOrder.applyOrder(result);
        result.clear();
        result.addAll(ordered);
        // 0.1.113: 当前首页源置顶——点源名弹出的列表第一个就是正在用的首页源，
        // 不再是配置文件顺序第一条。做在 all 上（applyOrder 之后），搜索过滤
        // filter() 从 all 重建 items 时也受益。只改打开时的显示顺序，不碰
        // 首页源选择本身；拖拽存的顺序不受影响，下次打开仍会置顶。
        Site home = VodConfig.get().getHome();
        if (home != null && home.getKey() != null) {
            for (int i = 0; i < result.size(); i++) {
                if (home.getKey().equals(result.get(i).getKey())) {
                    if (i > 0) result.add(0, result.remove(i));
                    break;
                }
            }
        }
        return result;
    }

    public interface OnClickListener {

        void onTextClick(Site item);

        void onEditClick(Site item);

        void onDeleteClick(Site item);
    }

    public List<Site> getItems() {
        return items;
    }

    public boolean isFiltering() {
        return keyword != null && !keyword.trim().isEmpty();
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

    // Drag is always allowed (unless filtering); dragging keeps the custom order.
    public boolean canDrag() {
        return !isFiltering();
    }

    public void onMove(int from, int to) {
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
        Collections.swap(items, from, to);
        int allFrom = all.indexOf(items.get(to));
        int allTo = all.indexOf(items.get(from));
        if (allFrom >= 0 && allTo >= 0) Collections.swap(all, allFrom, allTo);
        putOrder();
        notifyItemMoved(from, to);
    }

    // 0.1.105: 写回顺序时保留当前列表里没有的 key（接口隔离过滤掉的本地
    // 脚本源），不然它们的自定义顺序会从全局排序记录里丢掉。
    private void putOrder() {
        List<String> order = SiteOrder.keysOf(all);
        for (String key : SiteOrder.getOrder()) if (!order.contains(key)) order.add(key);
        SiteOrder.putOrder(order);
    }

    public void remove(Site item) {
        all.remove(item);
        int position = items.indexOf(item);
        if (position >= 0) {
            items.remove(position);
            notifyItemRemoved(position);
        }
        putOrder();
    }

    // The site object is updated in place by the rename; just refresh its row.
    public void rename(Site item) {
        int position = items.indexOf(item);
        if (position >= 0) notifyItemChanged(position);
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
        // Selected state drives the capsule fill, the text color and the icon tints (all use selector colors).
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.edit.setSelected(item.isSelected());
        holder.binding.close.setSelected(item.isSelected());
        // 检测色点：绿=优质，黄=慢或部分失效，红=失效；没测过的不显示。
        int grade = CheckStore.getSiteGrade(item.getKey());
        if (grade == SourceChecker.GRADE_NONE) {
            String activeId = ConfigManager.getActiveId();
            if (activeId != null && !activeId.isEmpty()) grade = CheckStore.getSiteGrade(activeId + "|" + item.getKey());
        }
        holder.binding.dot.setVisibility(grade == SourceChecker.GRADE_NONE ? View.GONE : View.VISIBLE);
        if (grade != SourceChecker.GRADE_NONE) holder.binding.dot.setBackgroundTintList(ColorStateList.valueOf(SourceChecker.gradeColor(grade)));
        holder.binding.getRoot().setOnClickListener(v -> listener.onTextClick(item));
        holder.binding.edit.setOnClickListener(v -> listener.onEditClick(item));
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
