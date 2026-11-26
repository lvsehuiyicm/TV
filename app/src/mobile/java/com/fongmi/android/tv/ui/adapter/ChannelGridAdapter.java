package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.databinding.AdapterChannelGridBinding;

import java.util.ArrayList;
import java.util.List;

/**
 * 竖屏直播频道网格（4 列，只显示频道名，当前频道琥珀实底 + 黑字）。
 * 与侧边列表的 ChannelAdapter 共用同一批 Channel 对象和点击回调。
 * 选中态由本适配器自己记的 selectedPosition 驱动——不能只靠 Channel.isSelected()：
 * 点台时侧边列表先改 flag，网格再调 setSelected 会因 flag 已是目标值而直接 return、
 * 不发 notifyItemChanged，网格就永远不重绘、选中态看不到。
 * <p>
 * 0.1.100：选中态不再靠 View.setSelected() + selector 画出来——0.1.99 就是这么做的，
 * 但在真机上网格胶囊从来不变色（同页的分组 tab 用同一个 shape_live_grid selector
 * 却能变琥珀色，说明 drawable 没问题，问题出在网格这条 selected 状态传到 view 的链路上）。
 * 所以这里直接按 position == selectedPosition 给胶囊换底色、换字色（非 selector 的
 * shape + 显式颜色值），不经过 selected 状态；通知也不带 payload，永远走完整绑定。
 * setSelected 还照常调（无害，保持 view 自身状态同步），但外观不再依赖它。
 */
public class ChannelGridAdapter extends RecyclerView.Adapter<ChannelGridAdapter.ViewHolder> {

    private final ChannelAdapter.OnClickListener listener;
    private final List<Channel> mItems;
    private int selectedPosition = -1;
    private int textColorSelected;
    private int textColorNormal;
    private boolean colorsReady;

    public ChannelGridAdapter(ChannelAdapter.OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public void clear() {
        mItems.clear();
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void addAll(List<Channel> items) {
        mItems.clear();
        mItems.addAll(items);
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    public void remove(Channel item) {
        int position = mItems.indexOf(item);
        if (position == -1) return;
        mItems.remove(position);
        if (position == selectedPosition) selectedPosition = -1;
        else if (position < selectedPosition) selectedPosition--;
        notifyItemRemoved(position);
    }

    public void setSelected(int position) {
        if (position == -1 || position >= mItems.size()) return;
        int old = selectedPosition;
        selectedPosition = position;
        // 不管 Channel 对象的 flag 是什么，都通知新旧两项重绘——flag 是和侧边列表共用的，
        // 侧边先改过就和目标值一样了，靠 flag 判断会漏通知。
        // 0.1.100：不带 payload 通知，永远走完整绑定（0.1.99 的 payload 局部绑定在真机上没起作用）。
        if (old != -1 && old != position && old < mItems.size()) {
            mItems.get(old).setSelected(false);
            notifyItemChanged(old);
        }
        mItems.get(position).setSelected(true);
        if (old != position) notifyItemChanged(position);
    }

    public int setSelected(Channel channel) {
        int position = mItems.indexOf(channel);
        setSelected(position);
        return position;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ChannelGridAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterChannelGridBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ChannelGridAdapter.ViewHolder holder, int position) {
        Channel item = mItems.get(position);
        boolean selected = position == selectedPosition;
        ensureColors(holder.itemView.getContext());
        holder.binding.name.setText(item.getShow());
        // 外观直接按 selectedPosition 定，不经过 selector / selected 状态——0.1.99 那条链路在真机上没起作用。
        holder.binding.getRoot().setBackgroundResource(selected ? R.drawable.shape_live_grid_selected : R.drawable.shape_live_grid_normal_dark);
        holder.binding.name.setTextColor(selected ? textColorSelected : textColorNormal);
        holder.binding.getRoot().setSelected(selected);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(view -> listener.onLongClick(item));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        // 0.1.100：不走 payload 局部绑定，永远完整绑定（0.1.99 的 payload 分支在真机上没让胶囊变色）。
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

        private final AdapterChannelGridBinding binding;

        ViewHolder(@NonNull AdapterChannelGridBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
