package com.fongmi.android.tv.ui.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.FragmentSettingBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.activity.ConfigActivity;
import com.fongmi.android.tv.ui.activity.HistoryActivity;
import com.fongmi.android.tv.ui.activity.KeepActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.shape.ShapeAppearanceModel;

public class SettingFragment extends BaseFragment implements ConfigListener, SiteListener, LiveListener {

    private static final String STATE_TAB = "tab";

    private FragmentSettingBinding mBinding;
    private TextView[] mTabs;
    private TextView[] mTabsTop;
    private int mTab;

    private final NestedScrollView.OnScrollChangeListener mScrollListener = (v, scrollX, scrollY, oldScrollX, oldScrollY) -> updateStickyTab(scrollY);

    private final ViewTreeObserver.OnGlobalLayoutListener mLayoutListener = () -> {
        updateBottomSpace();
        disableInnerScrolls();
    };

    public static SettingFragment newInstance() {
        return new SettingFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) mTab = savedInstanceState.getInt(STATE_TAB, 0);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_TAB, mTab);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentSettingBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        mTabs = new TextView[]{mBinding.tabPlayer, mBinding.tabTheme, mBinding.tabData};
        mTabsTop = new TextView[]{mBinding.tabPlayerTop, mBinding.tabThemeTop, mBinding.tabDataTop};
        for (int i = 0; i < mTabs.length; i++) {
            int index = i;
            mTabs[i].setOnClickListener(v -> selectTab(index));
            mTabsTop[i].setOnClickListener(v -> selectTab(index));
        }
        setTabBackgrounds();
        mBinding.pageScroll.setOnScrollChangeListener(mScrollListener);
        mBinding.pageContent.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
        mBinding.mineStatKeep.setOnClickListener(v -> KeepActivity.start(requireActivity()));
        mBinding.mineStatHistory.setOnClickListener(v -> HistoryActivity.start(requireActivity()));
        mBinding.mineStatSite.setOnClickListener(v -> ConfigActivity.start(requireActivity()));
        if (mTab >= mTabs.length) mTab = mTabs.length - 1;
        refreshMine();
        selectTab(mTab);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshMine();
        refreshTab(mTab);
    }

    @Override
    public void onDestroyView() {
        if (mBinding != null) mBinding.pageContent.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
        super.onDestroyView();
    }

    public void refreshMine() {
        refreshStats();
    }

    private void refreshStats() {
        if (mBinding == null) return;
        mBinding.mineKeepNum.setText(String.valueOf(Keep.getVod().size() + Keep.getLive().size()));
        mBinding.mineHistoryNum.setText(String.valueOf(History.get().size()));
        mBinding.mineSiteNum.setText(String.valueOf(VodConfig.get().getSites().size()));
    }

    private void setTabBackgrounds() {
        for (int i = 0; i < mTabs.length; i++) {
            mTabs[i].setBackground(createTabBackground());
            mTabsTop[i].setBackground(createTabBackground());
        }
    }

    private Drawable createTabBackground() {
        float radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        ShapeAppearanceModel shape = new ShapeAppearanceModel.Builder().setAllCorners(CornerFamily.ROUNDED, radius).build();
        MaterialShapeDrawable selected = new MaterialShapeDrawable(shape);
        selected.setFillColor(ColorStateList.valueOf(Color.parseColor("#F1BB6C")));
        MaterialShapeDrawable normal = new MaterialShapeDrawable(shape);
        normal.setFillColor(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.card_bg)));
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_selected}, selected);
        bg.addState(StateSet.WILD_CARD, normal);
        return bg;
    }

    private void updateStickyTab(int scrollY) {
        if (mBinding == null) return;
        int headerH = mBinding.headerWrap.getHeight();
        boolean show = headerH > 0 && scrollY >= headerH;
        mBinding.tabRowTop.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateBottomSpace() {
        if (mBinding == null || !isAdded()) return;
        int headerH = mBinding.headerWrap.getHeight();
        int viewportH = mBinding.pageScroll.getHeight();
        int contentH = mBinding.pageContent.getHeight();
        if (headerH <= 0 || viewportH <= 0 || contentH <= 0) return;
        View space = mBinding.bottomSpace;
        int oldH = space.getLayoutParams().height;
        int total = contentH - oldH;
        int need = Math.max(0, headerH + viewportH - total);
        if (need != oldH) {
            ViewGroup.LayoutParams lp = space.getLayoutParams();
            lp.height = need;
            space.setLayoutParams(lp);
        }
    }

    private void disableInnerScrolls() {
        if (!isAdded()) return;
        FragmentManager fm = getChildFragmentManager();
        for (int i = 0; i < mTabs.length; i++) {
            Fragment f = fm.findFragmentByTag("tab" + i);
            if (f == null || f.getView() == null) continue;
            NestedScrollView scroll = f.getView().findViewById(R.id.tabScroll);
            if (scroll == null) continue;
            if (scroll.isNestedScrollingEnabled()) scroll.setNestedScrollingEnabled(false);
            ViewGroup.LayoutParams lp = scroll.getLayoutParams();
            if (lp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                scroll.setLayoutParams(lp);
            }
        }
    }

    private void selectTab(int index) {
        mTab = index;
        for (int i = 0; i < mTabs.length; i++) {
            mTabs[i].setSelected(i == index);
            mTabsTop[i].setSelected(i == index);
        }
        FragmentManager fm = getChildFragmentManager();
        FragmentTransaction ft = fm.beginTransaction();
        for (int i = 0; i < mTabs.length; i++) {
            Fragment f = fm.findFragmentByTag("tab" + i);
            if (i == index) {
                if (f == null) ft.add(R.id.container, createTab(i), "tab" + i);
                else ft.show(f);
            } else if (f != null) {
                ft.hide(f);
            }
        }
        ft.commit();
        mBinding.pageScroll.post(() -> {
            if (mBinding == null) return;
            mBinding.pageScroll.scrollTo(0, 0);
        });
        refreshTab(index);
    }

    private Fragment createTab(int index) {
        return switch (index) {
            case 0 -> SettingPlayerFragment.newInstance(true);
            case 1 -> ThemeFragment.newInstance(true);
            case 2 -> DataFragment.newInstance();
            default -> null;
        };
    }

    private void refreshTab(int index) {
        Fragment f = getChildFragmentManager().findFragmentByTag("tab" + index);
        if (f == null || !f.isAdded() || f.getView() == null) return;
        if (f instanceof SettingPlayerFragment) ((SettingPlayerFragment) f).refresh();
        else if (f instanceof DataFragment) ((DataFragment) f).refresh();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) return;
        refreshMine();
        refreshTab(mTab);
    }

    @Override
    public void setConfig(Config config) {
        if (config.getUrl().startsWith("file")) PermissionUtil.requestFile(this, allGranted -> load(config));
        else load(config);
    }

    private void load(Config config) {
        switch (config.getType()) {
            case 0:
                VodConfig.load(config, getCallback());
                break;
            case 1:
                LiveConfig.load(config, getCallback());
                break;
            case 2:
                Setting.putWall(0);
                WallConfig.load(config, getCallback());
                break;
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
            }
        };
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void setLive(Live item) {
        LiveConfig.get().setHome(item);
    }

    public void reloadVod() {
        VodConfig.load(VodConfig.get().getConfig(), getCallback());
    }
}
