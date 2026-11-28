package com.fongmi.android.tv.ui.activity;

import android.app.PendingIntent;
import android.app.SearchManager;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.Updater;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.node.NodeRuntime;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityHomeBinding;
import com.fongmi.android.tv.db.BackupManager;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.ServerEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.playback.ExternalPlayback;
import com.fongmi.android.tv.receiver.ShortcutReceiver;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.custom.FragmentStateManager;
import com.fongmi.android.tv.ui.fragment.SettingDanmakuFragment;
import com.fongmi.android.tv.ui.fragment.SettingDecodeFragment;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.ui.fragment.SettingPlayerFragment;
import com.fongmi.android.tv.ui.fragment.SettingPreloadFragment;
import com.fongmi.android.tv.ui.fragment.VodFragment;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Prefers;
import com.github.catvod.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationBarView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

public class HomeActivity extends BaseActivity implements NavigationBarView.OnItemSelectedListener {

    private static final String STATE_ACTION_HANDLED = "action_handled";

    /** 0.1.127：第一次安装的“所有文件访问权限”提示弹过没有（只弹一次）。 */
    private static final String KEY_FILE_PERM_PROMPT = "file_perm_prompt_shown";

    private static volatile int instanceCount;

    public static boolean isRunning() {
        return instanceCount > 0;
    }

    private FragmentStateManager mManager;
    private ActivityHomeBinding mBinding;
    private Intent pendingAction;
    private boolean actionReady;
    private int orientation;

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityHomeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        pendingAction = intent;
        consumeAction();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != ExternalPlayback.REQUEST_CODE) return;
        setResult(resultCode, data);
        finish();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        instanceCount++;
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        if (savedInstanceState == null || !savedInstanceState.getBoolean(STATE_ACTION_HANDLED)) pendingAction = getIntent();
        orientation = getResources().getConfiguration().orientation;
        mBinding.navigation.setOnItemSelectedListener(this);
        setWindowInsets();
        initFragment(savedInstanceState);
        // 0.1.123：启动时不再自动检查应用版本、不弹升级提示；只保留一次性的运行模块更新检查。
        Updater.get().checkModulesOnce();
        checkPermission();
    }

    @Override
    protected void initEvent() {
        mBinding.navigation.findViewById(R.id.live).setOnLongClickListener(this::addShortcut);
    }

    private void checkAction(Intent intent) {
        if (Intent.ACTION_SEND.equals(intent.getAction()) || Intent.ACTION_VIEW.equals(intent.getAction())) {
            ExternalPlayback.open(this, intent, this::loadLive);
        } else if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String keyword = intent.getStringExtra(SearchManager.QUERY);
            if (!TextUtils.isEmpty(keyword)) SearchActivity.start(this, keyword);
        }
    }

    private void consumeAction() {
        if (!actionReady || pendingAction == null) return;
        Intent intent = pendingAction;
        pendingAction = null;
        checkAction(intent);
    }

    private void onConfigReady() {
        actionReady = true;
        consumeAction();
    }

    private void initFragment(Bundle savedInstanceState) {
        mManager = new FragmentStateManager(mBinding.container, getSupportFragmentManager(), this::createFragment);
        if (savedInstanceState == null) change(0);
    }

    private Fragment createFragment(int position) {
        return switch (position) {
            case 0 -> VodFragment.newInstance();
            case 1 -> SettingFragment.newInstance();
            case 2 -> SettingPlayerFragment.newInstance();
            case 3 -> SettingDanmakuFragment.newInstance();
            case 4 -> SettingPreloadFragment.newInstance();
            case 5 -> SettingDecodeFragment.newInstance();
            default -> null;
        };
    }

    /**
     * 0.1.128：底部导航自适应屏幕。原来只靠根布局 fitsSystemWindows 吃系统 insets，
     * 有的手机（手势导航、或 ROM 的 insets 分发不一样）底栏拿不到导航条高度，底部
     * 导航就贴着屏幕底边走，标签被手势条盖住一半；固定 48dp 高时字体放大的手机
     * 标签也会被截。现在显式按 systemBars insets 给根布局留安全区（上下左右都算，
     * 和 fitsSystemWindows 同款效果，只是走代码、不靠它分发），导航条高度跟着系统
     * 导航条走；布局里 fitsSystemWindows 已去掉，免得两处重复算。
     */
    private void setWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    private void checkPermission() {
        // 0.1.128：首次安装权限提示改到这里——第一次打开一定走到，不再挂在本地网络
        // 权限的回调里（见 onNetworkPermissionResult 注释）。只弹一次、已有权限不弹、
        // 打不开设置页不弹，条件和 0.1.127 一样，只换了触发点。
        maybeShowFilePermissionPrompt();
        PermissionUtil.requestLocalNetwork(this, this::onNetworkPermissionResult);
    }

    private void onNetworkPermissionResult(boolean allGranted) {
        // 0.1.128：首次安装权限提示不在这里调了——SDK 37+ 的手机上这个回调要等用户
        // 答完本地网络权限的系统弹窗才到，提示挂在这里会被拖住（弹窗没答完就不弹）；
        // 改在 checkPermission 里调，第一次打开一定走到。
        PermissionUtil.requestNotify(this);
        initConfig();
    }

    /**
     * 0.1.127：第一次打开 app 时弹一次，说明本地包 / 本地源要“所有文件访问权限”
     * 才能读到文件；点“去设置”直接跳到系统权限页，点允许就行。只弹一次（flag 记着），
     * 已有权限、或这台手机打不开那个设置页时不弹。提示不挡配置加载（initConfig 照常走）。
     */
    private void maybeShowFilePermissionPrompt() {
        if (Prefers.getBoolean(KEY_FILE_PERM_PROMPT, false)) return;
        if (!PermissionUtil.canManageAllFiles(this) || PermissionUtil.hasAllFilesPermission()) return;
        Prefers.put(KEY_FILE_PERM_PROMPT, true);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.file_perm_title)
                .setMessage(R.string.file_perm_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.file_perm_settings, (dialog, which) -> PermissionUtil.openAllFilesSettings(this))
                .show();
    }

    private void initConfig() {
        // 0.1.127：Python 预热与配置加载并行——后台线程先把 Python 解释器起起来
        // （BaseLoader.warmupPython），盖掉冷启动里 Python 启动那 ~15 秒里解释器
        // 启动的部分；预热失败静默，第一次建爬虫时再启动，不影响正常流程。
        Task.execute(() -> BaseLoader.get().warmupPython());
        // 0.1.127：开机按当前接口加载（文件夹接口走轻量路，不拉上一个在线配置）。
        ConfigManager.loadVodOnBoot(getCallback());
        LiveConfig.get().init().load();
        WallConfig.get().init();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                onConfigReady();
            }

            @Override
            public void error(String msg) {
                // 0.1.103：开机时保存的接口加载失败，别留空白首页。改加载一个空配置，
                // 只显示本地源（py/js），用户不用重启，进我的页切个能用的接口就行。
                if (VodConfig.get().getSites().isEmpty()) {
                    VodConfig.load(new Config(), new Callback() {
                        @Override
                        public void success() {
                            onConfigReady();
                            Notify.show(msg);
                        }

                        @Override
                        public void error(String errorMsg) {
                            onConfigReady();
                            StateEvent.empty();
                            Notify.show(msg);
                        }
                    });
                    return;
                }
                onConfigReady();
                StateEvent.empty();
                Notify.show(msg);
            }
        };
    }

    private void loadLive(String url) {
        if (isFinishing() || isDestroyed()) return;
        LiveConfig.load(Config.find(url, 1), new Callback() {
            @Override
            public void success() {
                if (getIntent().getBooleanExtra(ExternalPlayback.FORWARD_RESULT, false)) startActivityForResult(new Intent(HomeActivity.this, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()), ExternalPlayback.REQUEST_CODE);
                else openLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
                if (getIntent().getBooleanExtra(ExternalPlayback.FORWARD_RESULT, false)) finish();
            }
        });
    }

    private void setNavigation() {
        mBinding.navigation.getMenu().findItem(R.id.vod).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.setting).setVisible(true);
        mBinding.navigation.getMenu().findItem(R.id.live).setVisible(true);
    }

    private boolean openLive() {
        LiveActivity.start(this);
        return false;
    }

    private boolean addShortcut(View view) {
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(this, getString(R.string.nav_live)).setIcon(IconCompat.createWithResource(this, R.mipmap.ic_launcher)).setIntent(new Intent(Intent.ACTION_VIEW, null, this, LiveActivity.class)).setShortLabel(getString(R.string.nav_live)).build();
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(this, ShortcutReceiver.class).setAction(ShortcutReceiver.ACTION), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ShortcutManagerCompat.requestPinShortcut(this, info, pendingIntent.getIntentSender());
        return true;
    }

    public void change(int position) {
        if (position == 0) mBinding.navigation.setSelectedItemId(R.id.vod);
        else if (position == 1) mBinding.navigation.setSelectedItemId(R.id.setting);
        else mManager.change(position);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        switch (event.type()) {
            case VOD:
                RefreshEvent.home();
                break;
            case COMMON:
                setNavigation();
                break;
            case BOOT:
                LiveActivity.start(this);
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (event.getType() == RefreshEvent.Type.THEME) recreate();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerEvent(ServerEvent event) {
        if (event.type() == ServerEvent.Type.PUSH) VideoActivity.push(this, event.text());
        if (event.type() == ServerEvent.Type.SEARCH) SearchActivity.start(this, event.text());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.setting) return mManager.change(1);
        if (item.getItemId() == R.id.vod) return mManager.change(0);
        if (item.getItemId() == R.id.live) return openLive();
        return false;
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        App.post(() -> checkOrientation(newConfig), 100);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(STATE_ACTION_HANDLED, pendingAction == null);
        super.onSaveInstanceState(outState);
    }

    private void checkOrientation(Configuration newConfig) {
        if (orientation != newConfig.orientation) {
            orientation = newConfig.orientation;
            RefreshEvent.home();
        }
    }

    @Override
    protected void onBackInvoked() {
        if (!mBinding.navigation.getMenu().findItem(R.id.vod).isVisible()) {
            setNavigation();
        } else if (mManager.isVisible(4) || mManager.isVisible(5)) {
            change(2);
        } else if (mManager.isVisible(3) || mManager.isVisible(2) || mManager.isVisible(6)) {
            change(1);
        } else if (mManager.isVisible(1)) {
            change(0);
        } else if (mManager.canBack(0)) {
            if (PlaybackService.isRunning()) Util.moveToBackground(this);
            else super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        if (--instanceCount == 0 && !isChangingConfigurations()) {
            BackupManager.backup();
            releaseResources();
        }
        super.onDestroy();
    }

    public static void releaseResources() {
        if (isRunning() || PlaybackService.isRunning()) return;
        LiveConfig.get().clear();
        VodConfig.get().destroy();
        OkHttp.get().clear();
        Source.get().exit();
        Server.get().stop();
        NodeRuntime.get().releaseIfUnused();
    }
}
