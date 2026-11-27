package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityFragmentBinding;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.fragment.ConfigFragment;
import com.fongmi.android.tv.utils.ConfigManager;

public class ConfigActivity extends BaseActivity {

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, ConfigActivity.class));
    }

    private ActivityFragmentBinding mBinding;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityFragmentBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null) getSupportFragmentManager().beginTransaction().add(R.id.container, ConfigFragment.newInstance(false)).commit();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 0.1.126：配置页拨开关只改标志不加载（瞬时）；离开页面时再真正加载一次，
        // 回首页生效。无待加载时什么都不做。
        ConfigManager.flushVodReloadPending();
    }
}
