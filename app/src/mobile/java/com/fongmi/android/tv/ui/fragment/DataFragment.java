package com.fongmi.android.tv.ui.fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.databinding.FragmentDataBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.RestoreDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;

public class DataFragment extends BaseFragment {

    private FragmentDataBinding mBinding;

    public static DataFragment newInstance() {
        return new DataFragment();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentDataBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mBinding.versionText.setText(BuildConfig.VERSION_NAME);
        setCacheText();
    }

    @Override
    protected void initEvent() {
        mBinding.backupNow.setOnClickListener(v -> onBackup());
        mBinding.restoreNow.setOnClickListener(v -> onRestore());
        mBinding.clearCache.setSelected(true);
        mBinding.clearCache.setOnClickListener(this::onCache);
        mBinding.version.setOnClickListener(this::onVersion);
    }

    public void refresh() {
        setCacheText();
    }

    private void setCacheText() {
        if (mBinding == null) return;
        FileUtil.getCacheSize(new Callback() {
            @Override
            public void success(String result) {
                mBinding.cacheText.setText(result);
            }
        });
    }

    private void onBackup() {
        PermissionUtil.requestFile(this, allGranted -> BackupManager.backup(new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.backup_success);
            }

            @Override
            public void error() {
                Notify.show(R.string.backup_fail);
            }
        }));
    }

    private void onRestore() {
        PermissionUtil.requestFile(this, allGranted -> RestoreDialog.create().show(requireActivity(), new Callback() {
            @Override
            public void success() {
                Notify.show(R.string.restore_success);
                initConfig();
            }

            @Override
            public void error() {
                Notify.show(R.string.restore_fail);
            }
        }));
    }

    private void initConfig() {
        VodConfig.get().init().load(new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                setCacheText();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        });
        LiveConfig.get().init().load();
        WallConfig.get().init().load();
    }

    private void onVersion(View view) {
        Updater.get().start(requireActivity(), true);
    }

    private void onCache(View view) {
        FileUtil.clearCache(new Callback() {
            @Override
            public void success() {
                setCacheText();
            }
        });
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        setCacheText();
    }
}
