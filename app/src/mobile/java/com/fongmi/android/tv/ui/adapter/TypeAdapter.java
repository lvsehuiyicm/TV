package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class TypeAdapter extends RecyclerView.Adapter<TypeAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Class> mItems;

    public TypeAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Class item);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public void addAll(Result result) {
        mItems.addAll(result.getTypes());
        mItems.removeIf(type -> type.isHome() || "推荐".equals(type.getTypeName()));
        if (mItems.isEmpty()) mItems.addAll(result.getTypes());
        if (!mItems.isEmpty()) mItems.get(0).setSelected(true);
        notifyDataSetChanged();
    }

    public void setSelected(int position) {
        for (Class item : mItems) item.setSelected(false);
        mItems.get(position).setSelected(true);
        notifyItemRangeChanged(0, mItems.size());
    }

    public Class get(int position) {
        return mItems.get(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Class item = mItems.get(position);
        // 0.1.119：默认壁纸时 chip 覆写深色（选中琥珀、未选中深灰药丸、白字），每次绑定现算开关值；
        // 切到别的壁纸时恢复原 shape 与原 selector。背景要在 setSelected 之前换，状态才跟得上。
        boolean dark = ResUtil.isDefaultWall();
        holder.binding.text.setBackground(ResUtil.getDrawable(dark ? R.drawable.shape_type_chip_dark : R.drawable.shape_type_chip));
        holder.binding.text.setTextColor(ContextCompat.getColorStateList(holder.itemView.getContext(), dark ? R.color.selector_iface_text_dark : R.color.selector_type_text));
        holder.binding.text.setText(item.getTypeName());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(position, item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
