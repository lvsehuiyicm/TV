package com.fongmi.android.tv.ui.dialog;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSiteBinding;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.SiteOrder;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        return this;
    }

    public SiteDialog change() {
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof SiteListener) listener = (SiteListener) fragment;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSiteBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SiteAdapter(this);
        binding.recycler.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.post(() -> binding.recycler.scrollToPosition(VodConfig.getHomeIndex()));
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });
        updateSortButtons();
        binding.sortName.setOnClickListener(v -> onSortClick(SiteOrder.SORT_NAME));
        binding.sortTime.setOnClickListener(v -> onSortClick(SiteOrder.SORT_TIME));
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return adapter.canDrag();
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                adapter.onMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        }).attachToRecyclerView(binding.recycler);
    }

    private void onSortClick(int sort) {
        adapter.setSort(adapter.getSort() == sort ? SiteOrder.SORT_DEFAULT : sort);
        updateSortButtons();
    }

    private void updateSortButtons() {
        binding.sortName.setSelected(adapter.getSort() == SiteOrder.SORT_NAME);
        binding.sortTime.setSelected(adapter.getSort() == SiteOrder.SORT_TIME);
    }

    @Override
    public void onTextClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onDeleteClick(Site item) {
        boolean local = SiteOrder.isLocalFile(item);
        String[] choices = local ? new String[]{"删除列表源", "删除列表源和本地源"} : new String[]{"删除列表源"};
        new MaterialAlertDialogBuilder(requireContext()).setTitle(item.getName()).setItems(choices, (dialog, which) -> {
            if (which == 1) confirmDeleteFile(item);
            else deleteSite(item, false);
        }).show();
    }

    private void confirmDeleteFile(Site item) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(item.getName()).setMessage("会同时删除手机上的源文件，确定吗？").setPositiveButton(com.fongmi.android.tv.R.string.dialog_positive, (dialog, which) -> deleteSite(item, true)).setNegativeButton(com.fongmi.android.tv.R.string.dialog_negative, null).show();
    }

    private void deleteSite(Site item, boolean deleteFile) {
        boolean wasHome = item.isSelected();
        SiteOrder.addRemoved(item.getKey());
        VodConfig.get().getSites().removeIf(site -> site.getKey().equals(item.getKey()));
        adapter.remove(item);
        if (deleteFile) {
            try {
                new File(SiteOrder.getLocalPath(item)).delete();
            } catch (Exception ignored) {
            }
        }
        Notify.show("已删除" + item.getName());
        if (adapter.getItemCount() == 0) {
            dismiss();
        } else if (wasHome && listener != null) {
            listener.setSite(adapter.getItems().get(0));
            dismiss();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}
