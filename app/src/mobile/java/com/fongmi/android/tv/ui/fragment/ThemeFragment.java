package com.fongmi.android.tv.ui.fragment;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivityThemeBinding;
import com.fongmi.android.tv.databinding.ItemThemeBgBinding;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseFragment;

public class ThemeFragment extends BaseFragment {

    public static ThemeFragment newInstance(boolean embedded) {
        ThemeFragment fragment = new ThemeFragment();
        Bundle args = new Bundle();
        args.putBoolean("embedded", embedded);
        fragment.setArguments(args);
        return fragment;
    }

    public static final int[] BGS = {R.drawable.wallpaper_0, R.drawable.wallpaper_1, R.drawable.wallpaper_2, R.drawable.wallpaper_3, R.drawable.wallpaper_4, R.drawable.wallpaper_5, R.drawable.wallpaper_6, R.drawable.wallpaper_7, R.drawable.wallpaper_8, R.drawable.wallpaper_9};
    public static final int[] BG_NAMES = {R.string.theme_bg_mint, R.string.theme_bg_1, R.string.theme_bg_2, R.string.theme_bg_3, R.string.theme_bg_4, R.string.theme_bg_5, R.string.theme_bg_6, R.string.theme_bg_7, R.string.theme_bg_8, R.string.theme_bg_9};
    /** 10 张壁纸的真实主色（与 BGS 同顺序），圆形壁纸格只画主色圆点。 */
    public static final String[] WALL_COLORS = {"#c7ebd2", "#69bda4", "#6964e7", "#89a9c0", "#424242", "#fcf0f9", "#fce7c0", "#e5bffb", "#584324", "#f8f8f8"};
    /** 琥珀描边/选中名：写死 #F1BB6C（见 colors.xml 注释，Material 动态取色会改掉 ?attr/colorPrimary）。 */
    private static final int AMBER = Color.parseColor("#F1BB6C");
    /** 未选中圆点的细边，只为让近白的月白在白卡上看得见。 */
    private static final int CIRCLE_STROKE = Color.parseColor("#E0E0E0");

    private ActivityThemeBinding mBinding;
    private View[] mModeViews;
    private ItemThemeBgBinding[] mBgItems;
    private int defaultNameColor;
    private boolean embedded;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) embedded = getArguments().getBoolean("embedded", false);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = ActivityThemeBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (embedded) mBinding.toolbar.setVisibility(View.GONE);
        else mBinding.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
        mModeViews = new View[]{mBinding.modeLight, mBinding.modeDark, mBinding.modeSystem};
        for (int i = 0; i < mModeViews.length; i++) {
            int mode = i;
            mModeViews[i].setOnClickListener(v -> setThemeMode(mode));
        }
        mBgItems = new ItemThemeBgBinding[BGS.length];
        for (int i = 0; i < BGS.length; i++) {
            ItemThemeBgBinding item = ItemThemeBgBinding.inflate(LayoutInflater.from(requireContext()), mBinding.bgGrid, false);
            item.name.setText(BG_NAMES[i]);
            item.image.setImageDrawable(new ColorDrawable(Color.parseColor(WALL_COLORS[i])));
            int index = i;
            item.getRoot().setOnClickListener(v -> selectBg(index));
            mBinding.bgGrid.addView(item.getRoot());
            mBgItems[i] = item;
        }
        defaultNameColor = mBgItems[0].name.getCurrentTextColor();
        mBinding.alphaSeek.setMax(100);
        mBinding.alphaSeek.setProgress(Setting.getWallAlpha());
        mBinding.alphaText.setText(getString(R.string.theme_bg_alpha_value, Setting.getWallAlpha()));
        mBinding.alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mBinding.alphaText.setText(getString(R.string.theme_bg_alpha_value, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Setting.putWallAlpha(seekBar.getProgress());
                ConfigEvent.wall();
            }
        });
        updateMode();
        updateBg();
        refreshPreview();
    }

    private void setThemeMode(int mode) {
        Setting.putThemeMode(mode);
        Setting.applyThemeMode();
        updateMode();
        refreshParentMine();
        RefreshEvent.theme();
    }

    private void updateMode() {
        int mode = Setting.getThemeMode();
        for (int i = 0; i < mModeViews.length; i++) mModeViews[i].setSelected(i == mode);
    }

    private void selectBg(int index) {
        Setting.putWall(index);
        Setting.putWallType(0);
        ConfigEvent.wall();
        updateBg();
        refreshPreview();
        refreshParentMine();
    }

    private void refreshParentMine() {
        Fragment parent = getParentFragment();
        if (parent instanceof SettingFragment) ((SettingFragment) parent).refreshMine();
    }

    private void updateBg() {
        int wall = Setting.getWall();
        for (int i = 0; i < mBgItems.length; i++) {
            boolean selected = i == wall;
            ItemThemeBgBinding item = mBgItems[i];
            item.image.setStrokeWidth(dp(selected ? 3 : 1));
            item.image.setStrokeColor(ColorStateList.valueOf(selected ? AMBER : CIRCLE_STROKE));
            item.check.setVisibility(selected ? View.VISIBLE : View.GONE);
            item.name.setTextColor(selected ? AMBER : defaultNameColor);
        }
    }

    /** 效果预览：用选中壁纸画一个缩小版首页。 */
    private void refreshPreview() {
        mBinding.previewBg.setImageResource(BGS[Setting.getWall()]);
    }

    private float dp(int dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }
}
