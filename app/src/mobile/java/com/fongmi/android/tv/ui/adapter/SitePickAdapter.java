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

import java.util.List;

public class SitePickAdapter extends RecyclerView.Adapter<SitePickAdapter.ViewHolder> {

    private final List<Site> items;
    private final String homeKey;
    private String pendingKey;

    public SitePickAdapter(List<Site> items, String homeKey) {
        this.items = items;
        this.homeKey = homeKey;
        this.pendingKey = homeKey;
    }

    public Site getPending() {
        if (pendingKey == null) return null;
        for (Site site : items) if (pendingKey.equals(site.getKey())) return site;
        return null;
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
        holder.binding.tag.setVisibility(home ? View.VISIBLE : View.GONE);
        holder.binding.check.setVisibility(pending ? View.VISIBLE : View.GONE);
        if (pending) {
            int color = ContextCompat.getColor(holder.itemView.getContext(), home ? R.color.black : R.color.live_selected);
            holder.binding.check.setImageTintList(ColorStateList.valueOf(color));
        }
        holder.binding.getRoot().setOnClickListener(v -> {
            pendingKey = item.getKey();
            notifyDataSetChanged();
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
