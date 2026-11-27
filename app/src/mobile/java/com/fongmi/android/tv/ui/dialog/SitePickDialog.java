package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSitePickBinding;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.adapter.SitePickAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SitePickDialog extends BaseAlertDialog implements SiteListener, SitePickAdapter.OnItemLongClickListener {

    private DialogSitePickBinding binding;
    private SiteListener listener;
    private SitePickAdapter adapter;

    public static SitePickDialog create() {
        return new SitePickDialog();
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof SiteListener) listener = (SiteListener) fragment;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSitePickBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.site_switch).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SitePickAdapter(SiteAdapter.buildList(), VodConfig.get().getHome().getKey());
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });
    }

    @Override
    protected void initEvent() {
        adapter.setOnItemLongClickListener(this);
        // 0.1.127：点某一行就直接切到那个源并关弹窗，不用再点确定。
        adapter.setOnItemClickListener(site -> {
            if (listener != null) listener.setSite(site);
            dismiss();
        });
        binding.cancel.setOnClickListener(v -> dismiss());
    }

    @Override
    public void onItemLongClick(Site site) {
        // 0.1.118：弹窗里长按某一行，打开现有 SiteDialog 管这个源（改名/删除/排序）。
        SiteDialog.create().change().onDismiss(this::refreshList).show(this);
    }

    @Override
    public void setSite(Site site) {
        // SiteDialog 的转发：删首页源时靠它把 home 切到第一条（SiteDialog.deleteSite 的 wasHome 分支）。
        if (listener != null) listener.setSite(site);
    }

    /** 管理完刷新列表：重建列表与首页源；空表时关掉弹窗。 */
    private void refreshList() {
        if (adapter == null) return;
        binding.keyword.setText("");
        adapter.refresh(SiteAdapter.buildList(), VodConfig.get().getHome().getKey());
        if (adapter.getItemCount() == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}
