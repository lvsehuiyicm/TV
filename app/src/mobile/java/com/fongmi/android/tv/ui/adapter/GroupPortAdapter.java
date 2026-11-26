package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.databinding.AdapterGroupPortBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖屏直播分组网格（4 列，全部的分全露出来）。
 * 与侧边列表的 GroupAdapter 共用同一批 Group 对象和点击回调。
 * <p>
 * 0.1.101：选中态不再靠 Group.isSelected() + selector 画出来——0.1.100 及之前就是这么做的，
 * 但在真机上分组 tab 的琥珀色永远卡在"央视频道"不跟随切组（run100/run101 已预警，
 * 0.1.100 录屏证实）。根因和 0.1.99 的旧网格一模一样：点 tab 时侧边列表先改了共用
 * Group 对象的 selected 标志，竖屏 tab 再调 setSelected 会因标志已是目标值而直接 return、
 * 不发 notifyItemChanged，tab 就永远不重绘、选中态看不到。
 * 所以照搬 0.1.100 网格的修法：适配器自己记 selectedPosition，完整绑定里按
 * position == selectedPosition 直接换底色、换字色（非 selector 的 shape + 显式颜色值），
 * 不经过 selected 状态；通知也不带 payload，永远走完整绑定。
 * setSelected 还照常调（无害，保持 view 自身状态同步），但外观不再依赖它。
 * <p>
 * 0.1.102：分组从横向一排改成 4 列网格（四方格图标拿掉），字比下边频道大一号且加黑。
 * 选中通知改成全量重绘——0.1.101 用 notifyItemChanged(旧)+notifyItemChanged(新)，
 * xu 的 0.1.101 录屏里 7 个分组全是琥珀底；全量重绘最稳（分组只有几个，全露在网格里不用滚动），
 * 旧项残留的琥珀底不可能留下来，没选中的分组一定是普通底色。
 */
public class GroupPortAdapter extends RecyclerView.Adapter<GroupPortAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Group> mItems;
    private int selectedPosition = -1;
    private int textColorSelected;
    private int textColorNormal;
    private boolean colorsReady;

    public GroupPortAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Group item);
    }

    public void clear() {
        mItems.clear();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void addAll(List<Group> items) {
        mItems.clear();
        mItems.addAll(items);
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void add(Group item) {
        mItems.add(item);
        notifyItemInserted(getItemCount() - 1);
    }

    public Group get(int position) {
        return mItems.get(position);
    }

    public int getPosition() {
        if (selectedPosition != -1) return selectedPosition;
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public int indexOf(Group group) {
        return mItems.indexOf(group);
    }

    public void setSelected(Group group) {
        setSelected(indexOf(group));
    }

    public void setSelected(int position) {
        if (position == -1 || position >= mItems.size()) return;
        int old = selectedPosition;
        selectedPosition = position;
        // 不管 Group 对象的 flag 是什么，都重绘——flag 是和侧边列表共用的，
        // 侧边先改过就和目标值一样了，靠 flag 判断会漏通知（0.1.100 之前就是这么漏的）。
        // 0.1.102：改成全量重绘（notifyDataSetChanged），不再只通知新旧两项——0.1.101 的两项通知在
        // xu 的录屏里没起作用，7 个分组全是琥珀底；分组只有几个、全露在网格里不用滚动，全量重绘最稳，
        // 每个格子都按 position == selectedPosition 重新定底色，没选中的一定回到普通底色。
        // 不带 payload 通知，永远走完整绑定（0.1.99 的 payload 局部绑定在真机上没起作用）。
        if (old != -1 && old != position && old < mItems.size()) mItems.get(old).setSelected(false);
        mItems.get(position).setSelected(true);
        if (old != position) notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterGroupPortBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Group item = mItems.get(position);
        boolean selected = position == selectedPosition;
        ensureColors(holder.itemView.getContext());
        holder.binding.name.setText(item.getName());
        // 外观直接按 selectedPosition 定，不经过 selector / selected 状态——0.1.100 之前那条链路在真机上没起作用。
        holder.binding.getRoot().setBackgroundResource(selected ? R.drawable.shape_live_grid_selected : R.drawable.shape_live_grid_normal_dark);
        holder.binding.name.setTextColor(selected ? textColorSelected : textColorNormal);
        holder.binding.getRoot().setSelected(selected);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        // 0.1.101：不走 payload 局部绑定，永远完整绑定（0.1.99 的 payload 分支在真机上没让 tab 变色）。
        onBindViewHolder(holder, position);
    }

    private void ensureColors(Context context) {
        if (colorsReady) return;
        textColorSelected = ContextCompat.getColor(context, R.color.black);
        // 0.1.118：竖屏固定深色底，平时态字直接用白色，不再取主题 colorOnSurface。
        textColorNormal = ContextCompat.getColor(context, R.color.white);
        colorsReady = true;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterGroupPortBinding binding;

        ViewHolder(@NonNull AdapterGroupPortBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
