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
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SitePickDialog extends BaseAlertDialog {

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
    }

    @Override
    protected void initEvent() {
        binding.cancel.setOnClickListener(v -> dismiss());
        binding.confirm.setOnClickListener(v -> {
            Site pending = adapter.getPending();
            if (pending != null && listener != null) listener.setSite(pending);
            dismiss();
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}
