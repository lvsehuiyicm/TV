package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSitePickBinding;
import com.fongmi.android.tv.utils.CheckStore;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.SiteOrder;
import com.fongmi.android.tv.utils.SourceChecker;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SitePickAdapter extends RecyclerView.Adapter<SitePickAdapter.ViewHolder> {

    // 0.1.118：refresh() 要重写 items 与 homeKey，两个字段都不能是 final。
    private List<Site> items;
    // 0.1.120：搜索过滤用——all 是全量表，items 是当前显示表。
    private final List<Site> all = new ArrayList<>();
    private String keyword = "";
    private String homeKey;
    private String pendingKey;
    // 0.1.131：是不是双排网格。双排时每格只有约一半宽，首页格还要同时显示“当前使用”
    // tag（约 56dp）和勾（28dp），名字/副行只剩 20~40dp，只能显示成“...”。
    // 双排下 tag 不显示——琥珀底 + 勾已经说明这是当前源，tag 是多余的；单排宽度够，留着。
    private boolean grid;

    public SitePickAdapter(List<Site> items, String homeKey) {
        this.items = items;
        this.homeKey = homeKey;
        this.pendingKey = homeKey;
        all.addAll(items);
    }

    public Site getPending() {
        if (pendingKey == null) return null;
        for (Site site : items) if (pendingKey.equals(site.getKey())) return site;
        return null;
    }

    /** 0.1.131：切双排/单排时由 SitePickDialog 调，改完重绑一次（tag 显隐跟着变）。 */
    public void setGrid(boolean grid) {
        if (this.grid == grid) return;
        this.grid = grid;
        notifyDataSetChanged();
    }

    public interface OnItemLongClickListener {

        void onItemLongClick(Site site);
    }

    private OnItemLongClickListener longClickListener;

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        longClickListener = listener;
    }

    /** 0.1.127：点某一行就直接切（不用先选、再点确定）。 */
    public interface OnItemClickListener {

        void onItemClick(Site site);
    }

    private OnItemClickListener clickListener;

    public void setOnItemClickListener(OnItemClickListener listener) {
        clickListener = listener;
    }

    /** 管理后刷新列表：homeKey 与 pending 都回到当前首页源。 */
    public void refresh(List<Site> items, String homeKey) {
        all.clear();
        all.addAll(items);
        this.items = items;
        this.homeKey = homeKey;
        this.pendingKey = homeKey;
        notifyDataSetChanged();
    }

    public boolean isFiltering() {
        return keyword != null && !keyword.trim().isEmpty();
    }

    /** 0.1.120：按站名过滤，照 SiteAdapter.filter 抄。 */
    public void filter(String keyword) {
        this.keyword = keyword == null ? "" : keyword;
        items.clear();
        if (!isFiltering()) {
            items.addAll(all);
        } else {
            String key = this.keyword.toLowerCase(Locale.getDefault());
            for (Site site : all) if (site.getName().toLowerCase(Locale.getDefault()).contains(key)) items.add(site);
        }
        // 过滤后待选的源不在当前列表里时，待选回到当前首页源，否则点“确定”没反应。
        if (pendingKey != null && getPending() == null) pendingKey = homeKey;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSitePickBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = items.get(position);
        boolean home = homeKey != null && homeKey.equals(item.getKey());
        boolean pending = pendingKey != null && pendingKey.equals(item.getKey());
        holder.binding.name.setText(item.getName());
        holder.binding.sub.setText(holder.itemView.getContext().getString(R.string.site_interface, ifaceName(holder.itemView.getContext(), item)));
        // 健康点：与 SiteAdapter.onBindViewHolder 同款逻辑。
        int grade = CheckStore.getSiteGrade(item.getKey());
        if (grade == SourceChecker.GRADE_NONE) {
            String activeId = ConfigManager.getActiveId();
            if (activeId != null && !activeId.isEmpty()) grade = CheckStore.getSiteGrade(activeId + "|" + item.getKey());
        }
        holder.binding.dot.setVisibility(grade == SourceChecker.GRADE_NONE ? View.GONE : View.VISIBLE);
        if (grade != SourceChecker.GRADE_NONE) holder.binding.dot.setBackgroundTintList(ColorStateList.valueOf(SourceChecker.gradeColor(grade)));
        holder.binding.getRoot().setSelected(home);
        holder.binding.name.setSelected(home);
        holder.binding.sub.setSelected(home);
        // 0.1.131：双排时不显示“当前使用”tag（见 grid 字段注释），把宽度让给名字；
        // 单排宽度够，首页行照常显示。
        holder.binding.tag.setVisibility(home && !grid ? View.VISIBLE : View.GONE);
        holder.binding.check.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            int color = ContextCompat.getColor(holder.itemView.getContext(), home ? R.color.black : R.color.live_selected);
            holder.binding.check.setImageTintList(ColorStateList.valueOf(color));
        }
        holder.binding.getRoot().setOnClickListener(v -> {
            // 0.1.127：点了就直接切（对话框里已接上 listener）；没接上时退回老行为
            // （只把这一行标成待选），免得点一下没反应。
            if (clickListener != null) clickListener.onItemClick(item);
            else {
                pendingKey = item.getKey();
                notifyDataSetChanged();
            }
        });
        holder.binding.getRoot().setOnLongClickListener(v -> {
            if (longClickListener != null) longClickListener.onItemLongClick(item);
            return true;
        });
    }

    /**
     * 每行所属接口：本地脚本源（key 以 script_ 开头）标“本地”，其余标当前接口名；
     * 全部模式下当前接口名为空时退到当前配置名。不用 getLocalPath 判断——接口自带的
     * XBPQ/drpy2 文件源也会返回本地路径（SiteOrder 注释原话）。
     */
    private static String ifaceName(Context context, Site site) {
        if (SiteOrder.isLocalScript(site)) return context.getString(R.string.nav_local);
        String active = ConfigManager.getActiveName();
        if (active != null) return active;
        return VodConfig.get().getConfig().getDesc();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSitePickBinding binding;

        ViewHolder(@NonNull AdapterSitePickBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
