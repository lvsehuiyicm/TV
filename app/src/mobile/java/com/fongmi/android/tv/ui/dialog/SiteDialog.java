package com.fongmi.android.tv.ui.dialog;

import android.content.DialogInterface;
import android.text.InputType;
import android.widget.EditText;

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
import com.fongmi.android.tv.utils.SiteCookieStore;
import com.fongmi.android.tv.utils.SiteOrder;
import com.fongmi.android.tv.utils.UrlUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;

public class SiteDialog extends BaseAlertDialog implements SiteAdapter.OnClickListener {

    private DialogSiteBinding binding;
    private SiteListener listener;
    private SiteAdapter adapter;
    private GridLayoutManager layoutManager;
    private SpaceItemDecoration decoration;
    private Runnable mOnDismiss;

    public static SiteDialog create() {
        return new SiteDialog();
    }

    public SiteDialog search() {
        return this;
    }

    public SiteDialog change() {
        return this;
    }

    /** 0.1.118：关掉后回调（SitePickDialog 用它刷新列表）。 */
    public SiteDialog onDismiss(Runnable runnable) {
        mOnDismiss = runnable;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof SiteListener) listener = (SiteListener) fragment;
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        super.onDismiss(dialog);
        if (mOnDismiss != null) mOnDismiss.run();
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
        layoutManager = new GridLayoutManager(requireContext(), SiteOrder.getLayout());
        binding.recycler.setLayoutManager(layoutManager);
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        decoration = new SpaceItemDecoration(SiteOrder.getLayout(), 8);
        binding.recycler.addItemDecoration(decoration);
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });
        updateLayoutButton();
        binding.layoutToggle.setOnClickListener(v -> onLayoutClick());
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

    // Single/double column toggle: icon button in the search row (list icon = single column, grid icon = double column).
    private void onLayoutClick() {
        int layout = SiteOrder.getLayout() == SiteOrder.LAYOUT_SINGLE ? SiteOrder.LAYOUT_DOUBLE : SiteOrder.LAYOUT_SINGLE;
        SiteOrder.putLayout(layout);
        layoutManager.setSpanCount(layout);
        binding.recycler.removeItemDecoration(decoration);
        decoration = new SpaceItemDecoration(layout, 8);
        binding.recycler.addItemDecoration(decoration);
        updateLayoutButton();
    }

    private void updateLayoutButton() {
        binding.layoutToggle.setImageResource(SiteOrder.getLayout() == SiteOrder.LAYOUT_SINGLE ? com.fongmi.android.tv.R.drawable.ic_view_list : com.fongmi.android.tv.R.drawable.ic_view_grid);
    }

    @Override
    public void onTextClick(Site item) {
        if (listener != null) listener.setSite(item);
        dismiss();
    }

    @Override
    public void onEditClick(Site item) {
        // 0.1.106：编辑菜单（原来点铅笔直接改名）。加“清除登录状态”：清掉这个源自己域名下的 Cookie，
        // 下次打开要重新登录；只清这一个源，不动别的源。TV 端没有编辑入口，这项只在手机端有。
        new MaterialAlertDialogBuilder(requireContext()).setTitle(item.getName()).setItems(new String[]{"修改源名称", "清除登录状态"}, (dialog, which) -> {
            if (which == 1) clearSiteLogin(item);
            else renameSiteDialog(item);
        }).show();
    }

    private void clearSiteLogin(Site item) {
        if (SiteCookieStore.clearSite(item)) Notify.show("已清除登录状态");
        else Notify.show("该源没有保存的登录状态");
    }

    private void renameSiteDialog(Site item) {
        String current = getEditableName(item);
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(current);
        input.setSelection(current.length());
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding / 2, padding, padding / 2);
        new MaterialAlertDialogBuilder(requireContext()).setTitle("修改源名称").setView(input).setPositiveButton(com.fongmi.android.tv.R.string.dialog_positive, (dialog, which) -> renameSite(item, input.getText().toString().trim())).setNegativeButton(com.fongmi.android.tv.R.string.dialog_negative, null).show();
    }

    // Script sources show "name[py]"/"name[js]"; the editable part is the name without the suffix.
    private String getEditableName(Site item) {
        String name = item.getName();
        if (SiteOrder.isLocalFile(item) && (name.endsWith("[py]") || name.endsWith("[js]"))) return name.substring(0, name.length() - 4);
        return name;
    }

    private void renameSite(Site item, String newName) {
        if (newName.isEmpty()) {
            Notify.show("名称不能为空");
            return;
        }
        if (newName.contains("/") || newName.contains("\\") || newName.contains(":") || newName.contains("*") || newName.contains("?") || newName.contains("\"") || newName.contains("<") || newName.contains(">") || newName.contains("|")) {
            Notify.show("名称不能包含特殊字符");
            return;
        }
        String path = SiteOrder.getLocalPath(item);
        if (path != null) renameLocalFile(item, path, newName);
        else {
            SiteOrder.putName(item.getKey(), newName);
            item.setName(newName);
            adapter.rename(item);
            Notify.show("已重命名");
        }
    }

    // Local script source: rename the file on the phone too, so the file name changes together with the source name.
    private void renameLocalFile(Site item, String path, String newBase) {
        File oldFile = new File(path);
        String name = oldFile.getName();
        int dot = name.lastIndexOf('.');
        String ext = dot > 0 ? name.substring(dot) : "";
        String suffix = name.toLowerCase().endsWith(".py") ? "[py]" : "[js]";
        File newFile = new File(oldFile.getParent(), newBase + ext);
        if (newFile.exists()) {
            Notify.show("已有同名源文件");
            return;
        }
        if (!oldFile.renameTo(newFile)) {
            Notify.show("重命名失败");
            return;
        }
        String oldKey = item.getKey();
        boolean wasHome = VodConfig.get().getHome() != null && oldKey.equals(VodConfig.get().getHome().getKey());
        item.setKey("script_" + newBase);
        item.setName(newBase + suffix);
        item.setApi(UrlUtil.convert("file://" + newFile.getAbsolutePath()));
        SiteOrder.renameKey(oldKey, item.getKey());
        if (wasHome) VodConfig.get().setHome(item);
        adapter.rename(item);
        Notify.show("已重命名");
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
