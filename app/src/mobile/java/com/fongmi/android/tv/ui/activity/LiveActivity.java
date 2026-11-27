package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.databinding.ActivityLiveBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.impl.LiveListener;
import com.fongmi.android.tv.impl.PassListener;
import com.fongmi.android.tv.model.LiveViewModel;
import com.fongmi.android.tv.playback.PlaybackAction;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.playback.PlaybackReset;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.live.LivePlayRequest;
import com.fongmi.android.tv.playback.live.LivePlaybackController;
import com.fongmi.android.tv.playback.live.LivePlaybackHost;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DisplaySetting;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.adapter.ChannelAdapter;
import com.fongmi.android.tv.ui.adapter.ChannelGridAdapter;
import com.fongmi.android.tv.ui.adapter.EpgDataAdapter;
import com.fongmi.android.tv.ui.adapter.GroupAdapter;
import com.fongmi.android.tv.ui.adapter.GroupPortAdapter;
import com.fongmi.android.tv.ui.custom.PinchScaleFeedback;
import com.fongmi.android.tv.ui.custom.TouchInput;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.HistoryDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LiveDialog;
import com.fongmi.android.tv.ui.dialog.PassDialog;
import com.fongmi.android.tv.ui.dialog.PlayerEngineDialog;
import com.fongmi.android.tv.ui.dialog.DecoderSettingDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Biometric;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class LiveActivity extends PlaybackActivity implements TouchInput.Listener, Biometric.Callback, PassListener, ConfigListener, LiveListener, GroupAdapter.OnClickListener, GroupPortAdapter.OnClickListener, ChannelAdapter.OnClickListener, EpgDataAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, LivePlaybackHost {

    private ActivityLiveBinding mBinding;
    private LiveViewModel mViewModel;
    private LivePlaybackController mLive;
    private GroupAdapter mGroupAdapter;
    private ChannelAdapter mChannelAdapter;
    private EpgDataAdapter mEpgDataAdapter;
    private GroupPortAdapter mPortGroupAdapter;
    private ChannelGridAdapter mPortChannelAdapter;
    // 0.1.102：四方格图标拿掉了（用户 2026-11-24 17:33 定）——分组改成 4 列网格全露出来，不再展开/收回全部频道；
    // isAllChannels 和它那一套方法（onPortGridClick / refreshPortGrid / getAllChannels 等）随之删掉。
    // 频道网格始终 4 列（0.1.100 起），分组网格也是 4 列。
    private TouchInput mInput;
    private PinchScaleFeedback mScaleFeedback;
    private PiP mPiP;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    // 0.1.118：竖屏分组行固定 4 项（央视/卫视/地方/全部）；0.1.119：“全部”是展开开关——
    // 点一下展开显示全部可见分组，再点一下收回固定 4 项，频道网格永远只显示一个分组的台。
    private boolean mPortExpanded;
    private Group mAllGroup;
    private List<Group> mPortGroups = new ArrayList<>();
    private List<Group> mPort4 = new ArrayList<>();
    private Epg mEpg;
    private List<Group> mHides;
    private Group mGroup;
    private Channel mChannel;
    private String mPlaybackKey;
    private boolean rotate;
    private int count;

    public static void start(Context context) {
        context.startActivity(new Intent(context, LiveActivity.class).putExtra("empty", LiveConfig.isEmpty()));
    }

    private boolean isEmpty() {
        return getIntent().getBooleanExtra("empty", true);
    }

    private Group getKeep() {
        return mGroupAdapter.get(0);
    }

    private Live getHome() {
        return LiveConfig.get().getHome();
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityLiveBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected String getPlaybackKey() {
        return mPlaybackKey;
    }

    @Override
    protected PlayerView getPlayerView() {
        return mBinding.player;
    }

    @Override
    protected PlayerSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        mLive.onPlaybackServiceReady();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Util.hideSystemUI(this);
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mInput = TouchInput.create(this, mBinding.player, this);
        mScaleFeedback = new PinchScaleFeedback(mBinding.widget.scale);
        setPadding(mBinding.control.getRoot());
        setPadding(mBinding.recycler, true);
        mHides = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::hideInfo;
        mR4 = this::refreshPortProgram;
        mPiP = new PiP();
        setRecyclerView();
        setPortraitView();
        setVideoView();
        setViewModel();
        checkLive();
        layoutOrientation();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> nextChannel());
        mBinding.control.prev.setOnClickListener(view -> prevChannel());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate()); 
        mBinding.control.fullscreen.setOnClickListener(view -> onFullscreenClick());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.home.setOnClickListener(view -> onHome());
        mBinding.control.action.line.setOnClickListener(view -> onLine());
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.config.setOnClickListener(view -> onConfig());
        mBinding.control.action.invert.setOnClickListener(view -> onInvert());
        mBinding.control.action.across.setOnClickListener(view -> onAcross());
        mBinding.control.action.change.setOnClickListener(view -> onChange());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decoder.setOnClickListener(view -> onDecoder());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.video.setOnTouchListener((view, event) -> mInput.onTouchEvent(event));
        mBinding.portIcon.setOnClickListener(view -> checkPlay());
        // 0.1.101：5 个彩色按钮整行拿掉了；0.1.102：四方格图标也拿掉了（分组改成 4 列网格全露出来）。
        // 竖屏只剩视频右下角全屏图标（点一下进横屏；双击视频也是进全屏，见 onDoubleTap）。
        mBinding.btnPortFullscreen.setOnClickListener(view -> onFullscreenClick());
    }

    private void setPortraitView() {
        mBinding.groupPort.setItemAnimator(null);
        mBinding.channelPort.setItemAnimator(null);
        mBinding.groupPort.setAdapter(mPortGroupAdapter = new GroupPortAdapter(this));
        mBinding.channelPort.setAdapter(mPortChannelAdapter = new ChannelGridAdapter(this));
        // 0.1.102：分组网格和频道网格都是 4 列。分组网格全露出来（7 个分组占两行），点哪个分组就只显示那个分组的频道。
        mBinding.groupPort.setLayoutManager(new GridLayoutManager(this, 4));
        mBinding.channelPort.setLayoutManager(new GridLayoutManager(this, 4));
    }

    /** 横屏视频全屏，竖屏视频 16:9 在上、面板在下；只在横竖切换和初始化时调。 */
    private void layoutOrientation() {
        boolean land = ResUtil.isLand(this);
        ViewGroup.LayoutParams params = mBinding.video.getLayoutParams();
        params.height = land ? ViewGroup.LayoutParams.MATCH_PARENT : ResUtil.getScreenWidth() * 9 / 16;
        mBinding.video.setLayoutParams(params);
        mBinding.portrait.setVisibility(land ? View.GONE : View.VISIBLE);
        if (!land) hideUI();
    }

    private void updatePortInfo() {
        if (mChannel == null) return;
        mBinding.portNumber.setText(mChannel.getNumber());
        Live home = getHome();
        mBinding.portSource.setText(ResUtil.getString(R.string.live_source, home == null ? "" : home.getName()));
    }

    private void setRecyclerView() {
        mBinding.group.setItemAnimator(null);
        mBinding.channel.setItemAnimator(null);
        mBinding.epgData.setItemAnimator(null);
        mBinding.group.setAdapter(mGroupAdapter = new GroupAdapter(this));
        mBinding.channel.setAdapter(mChannelAdapter = new ChannelAdapter(this));
        mBinding.epgData.setAdapter(mEpgDataAdapter = new EpgDataAdapter(this));
    }

    private void setVideoView() {
        setScale(LiveSetting.getScale());
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
    }

    private void setPlaybackMode() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player);
    }

    private void setScale(int scale) {
        LiveSetting.putScale(scale);
        mBinding.player.setResizeMode(PlayerSetting.getScaleMode(scale));
        float ratio = PlayerSetting.getAspectRatio(scale);
        AspectRatioFrameLayout frame = mBinding.player.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (frame != null) frame.setAspectRatio(ratio);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(LiveViewModel.class);
        mLive = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.playback(), this::onPlaybackObserved);
        observeWhenServiceReady(mViewModel.error(), this::onLoadErrorObserved);
        observeForever(mViewModel.epg(), this::onEpgLoaded);
        mViewModel.live().observe(this, this::onLiveParsed);
        mViewModel.xml().observe(this, this::onXmlParsed);
    }

    private void onLiveParsed(Live live) {
        mViewModel.parseXml(live);
        setGroup(live);
        setWidth(live);
    }

    private void onPlaybackObserved(PlaybackResult<LivePlayRequest> result) {
        mLive.onPlaybackResult(result);
    }

    private void onLoadErrorObserved(String msg) {
        if (msg == null || msg.isEmpty()) return;
        resetPlaybackForError(msg);
    }

    private void checkLive() {
        // 0.1.126：配置页拨了直播开关时，地址已换好、加载推迟到这里。进直播页就把攒下
        // 的加载做掉；没攒时走原来的逻辑（空才加载，否则直接用现成的）。
        if (ConfigManager.takeLiveReloadPending()) LiveConfig.load(LiveConfig.get().getConfig(), getCallback());
        else if (isEmpty()) LiveConfig.get().init().load(getCallback());
        else getLive();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void success() {
                getLive();
            }

            @Override
            public void error(String msg) {
                Notify.show(msg);
            }
        };
    }

    private void getLive() {
        mBinding.control.action.home.setText(LiveConfig.isOnly() ? getString(R.string.live_refresh) : getHome().getName());
        mViewModel.parse(getHome());
        showProgress();
    }

    private void setGroup(Live live) {
        List<Group> items = new ArrayList<>();
        for (Group group : live.getGroups()) (group.isHidden() ? mHides : items).add(group);
        mGroupAdapter.addAll(items);
        // 0.1.118：竖屏分组行固定 4 项（央视/卫视/地方/全部），不再列出全部分组；
        // 横屏左侧分组栏不动（全部分组原顺序）。0.1.101 的过滤（竖屏不要"收藏"）随之作废——
        // 固定 4 项里本来就没有收藏。0.1.119：点"全部"展开显示全部可见分组（开关），再点收回。
        List<Group> port = filterPortGroups(items);
        mPortGroups = port;
        mPort4 = new ArrayList<>();
        mPort4.add(findTierGroup(port, 0));
        mPort4.add(findTierGroup(port, 1));
        mPort4.add(findTierGroup(port, 2));
        mAllGroup = new Group(ResUtil.getString(R.string.iface_all));
        mPort4.add(mAllGroup);
        mPortExpanded = false;
        mPortGroupAdapter.addAll(mPort4);
        if (mGroup != null) mPortGroupAdapter.setSelected(tierIndexOf(mGroup));
        setPosition(LiveConfig.get().findKeepPosition(items));
    }

    /** 按档找分组：0＝央视（央视/CCTV 开头）、1＝卫视、2＝地方（地方/省内开头），取源顺序第一个；找不到返回同名空 Group 占位（点它下面是空网格）。 */
    private Group findTierGroup(List<Group> items, int tier) {
        String name = tier == 0 ? "央视" : tier == 1 ? "卫视" : "地方";
        for (Group group : items) if (tierIndexOf(group) == tier) return group;
        return new Group(name);
    }

    /** 真实分组在固定 4 项里的下标（按名前缀定档），不在三档里返回 -1。 */
    private int tierIndexOf(Group group) {
        if (group == null) return -1;
        String name = group.getName();
        if (name.startsWith("央视") || name.toUpperCase().startsWith("CCTV")) return 0;
        if (name.startsWith("卫视")) return 1;
        if (name.startsWith("地方") || name.startsWith("省内")) return 2;
        return -1;
    }

    /** 竖屏分组 tab 的分组列表：去掉收藏分组。 */
    private List<Group> filterPortGroups(List<Group> items) {
        List<Group> port = new ArrayList<>();
        for (Group group : items) if (!group.isKeep()) port.add(group);
        return port;
    }

    private void setWidth(Live live) {
        int padding = ResUtil.dp2px(48);
        // 0.1.101：按 10sp 量（横屏分组项的字现在是 10sp，见 adapter_group.xml），每次都重算——
        // 之前只在 getWidth() == 0 时算一次，换台源后宽度还是旧台源的，分组名长的就被截成 "…"。
        int max = 0;
        for (Group item : live.getGroups()) max = Math.max(max, ResUtil.getTextWidth(item.getName(), 10));
        live.setWidth(max);
        int width = max == 0 ? 0 : Math.min(max + padding, ResUtil.getScreenWidth() / 4);
        setWidth(mBinding.group, width);
    }

    @Override
    public void setWidth(Group group) {
        int logo = ResUtil.dp2px(56);
        int padding = ResUtil.dp2px(60);
        // 0.1.101：按 10sp 量（横屏频道项的字现在是 10sp，见 adapter_channel.xml），每次都重算——
        // 之前只在 getWidth() == 0 时算一次，宽度是旧的，长频道名就被截成 "…"（如 "CCTV-5+体育…"）。
        int max = 0;
        for (Channel item : group.getChannel()) max = Math.max(max, (item.getLogo().isEmpty() ? 0 : logo) + ResUtil.getTextWidth(item.getNumber() + item.getName(), 10));
        group.setWidth(max);
        int width = max == 0 ? 0 : Math.min(max + padding, ResUtil.getScreenWidth() / 2);
        setWidth(mBinding.channel, width);
    }

    private void setWidth(Epg epg) {
        int padding = ResUtil.dp2px(48);
        if (epg.getList().isEmpty()) return;
        int minWidth = ResUtil.getTextWidth(epg.getList().get(0).getTime(), 12);
        if (epg.getWidth() == 0) for (EpgData item : epg.getList()) epg.setWidth(Math.max(epg.getWidth(), ResUtil.getTextWidth(item.getTitle(), 14)));
        int maxWidth = ResUtil.getScreenWidth() / 2;
        int minContentWidth = Math.min(minWidth + padding, maxWidth);
        int width = epg.getWidth() == 0 ? 0 : Math.clamp(epg.getWidth() + padding, minContentWidth, maxWidth);
        setWidth(mBinding.epgData, width);
    }

    private void setWidth(View view, int width) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params.width == width) return;
        params.width = width;
        view.setLayoutParams(params);
    }

    private void setPosition(int[] position) {
        if (position[0] == -1) return;
        int size = mGroupAdapter.getItemCount();
        if (size == 1 || position[0] >= size) return;
        mGroup = mGroupAdapter.get(position[0]);
        mGroup.setPosition(position[1]);
        mLive.selectGroup(mGroup);
        mLive.selectChannel(mGroup.current());
    }

    private void setPosition() {
        if (mChannel == null) return;
        mGroup = mChannel.getGroup();
        int position = mGroupAdapter.indexOf(mGroup);
        boolean change = mGroupAdapter.getPosition() != position;
        if (change) mGroupAdapter.setSelected(position);
        if (change) mChannelAdapter.addAll(mGroup.getChannel());
        if (change) mChannelAdapter.setSelected(mGroup.getPosition());
        scrollToPosition(mBinding.channel, mGroup.getPosition());
        scrollToPosition(mBinding.group, position);
    }

    private void onBack() {
        finish();
    }

    private void onCast() {
        CastDialog.createLive(player()).show(this);
    }

    private void onInfo() {
        InfoDialog.create(player()).show(this);
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mInput.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(PlaybackOrientation.getRotateOrientation(this));
    }

    private void onFullscreenClick() {
        onRotate();
    }

    private void updateFullscreenIcon() {
        int icon = ResUtil.isLand(this) ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen;
        mBinding.control.fullscreen.setImageResource(icon);
        // 0.1.101：视频右下角的全屏小图标也跟着横竖屏换（横屏时点它是退出全屏）。
        mBinding.btnPortFullscreen.setImageResource(icon);
    }

    private void checkPlay() {
        if (isWebPlaybackPlaying() || player().isPlaying()) onPaused();
        else onPlay();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onHome() {
        if (LiveConfig.isOnly()) setLive(getHome());
        else LiveDialog.show(this);
        hideControl();
    }

    private void onLine() {
        nextLine(false);
    }

    private void onScale() {
        if (mInput.isScaled()) mInput.resetScale();
        else setScale((LiveSetting.getScale() + 1) % ResUtil.getStringArray(R.array.select_scale).length);
        setR1Callback();
    }

    private void onSpeed() {
        SpeedSettingDialog.create().show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        setR1Callback();
        return true;
    }

    private void onConfig() {
        HistoryDialog.create().live().readOnly().show(this);
        hideControl();
    }

    private void onInvert() {
        setR1Callback();
        LiveSetting.putInvert(!LiveSetting.isInvert());
        mBinding.control.action.invert.setSelected(LiveSetting.isInvert());
    }

    private void onAcross() {
        setR1Callback();
        LiveSetting.putAcross(!LiveSetting.isAcross());
        mBinding.control.action.across.setSelected(LiveSetting.isAcross());
    }

    private void onChange() {
        setR1Callback();
        LiveSetting.putChange(!LiveSetting.isChange());
        mBinding.control.action.change.setSelected(LiveSetting.isChange());
    }

    private void onPlayer() {
        PlayerEngineDialog.show(this);
        hideControl();
    }

    private void onDecoder() {
        DecoderSettingDialog.create().show(this);
        hideControl();
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) setR1Callback();
        return false;
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate());
    }

    private void hideUI() {
        if (isGone(mBinding.recycler)) return;
        mBinding.recycler.setVisibility(View.GONE);
        setPosition();
    }

    private void showUI() {
        if (!ResUtil.isLand(this)) return;
        if (isVisible(mBinding.recycler) || mGroupAdapter.getItemCount() == 0) return;
        mBinding.recycler.setVisibility(View.VISIBLE);
        mBinding.channel.requestFocus();
        setPosition();
        hideEpg();
    }

    private void showEpg(Channel item) {
        if (mChannel == null || mChannel.getData(mViewModel.getZoneId()).getList().isEmpty() || mEpgDataAdapter.getItemCount() == 0 || !mChannel.equals(item) || !mChannel.getGroup().equals(mGroup)) return;
        scrollToPosition(mBinding.epgData, item.getData(mViewModel.getZoneId()).getSelected());
        mBinding.epgData.setVisibility(View.VISIBLE);
        mBinding.channel.setVisibility(View.GONE);
        mBinding.group.setVisibility(View.GONE);
    }

    private void hideEpg() {
        mBinding.channel.setVisibility(View.VISIBLE);
        mBinding.group.setVisibility(View.VISIBLE);
        mBinding.epgData.setVisibility(View.GONE);
    }

    @Override
    public void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
    }

    private void showError(String text) {
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        // 0.1.101：控制栏打开时，视频右下角的全屏小图标藏起来（控制栏底部本来就有全屏按钮，两个叠在一起会打架）。
        mBinding.btnPortFullscreen.setVisibility(View.GONE);
        updateFullscreenIcon();
        setR1Callback();
        hideInfo();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        // 0.1.101：控制栏收起时，视频右下角的全屏小图标再显示出来（控制栏藏着的时候它是唯一的全屏入口）。
        mBinding.btnPortFullscreen.setVisibility(View.VISIBLE);
        App.removeCallbacks(mR1);
    }

    private void showInfo() {
        mBinding.widget.infoPip.setVisibility(isInPictureInPictureMode() ? View.VISIBLE : View.GONE);
        mBinding.widget.info.setVisibility(isInPictureInPictureMode() ? View.GONE : View.VISIBLE);
        setR3Callback();
        hideControl();
        setInfo();
    }

    private void hideInfo() {
        mBinding.widget.infoPip.setVisibility(View.GONE);
        mBinding.widget.info.setVisibility(View.GONE);
        App.removeCallbacks(mR3);
    }

    private void setTraffic() {
        if (DisplaySetting.isSpeed()) Traffic.setSpeed(mBinding.progress.traffic);
        else mBinding.progress.traffic.setVisibility(View.GONE);
        App.post(mR2, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR3Callback() {
        App.post(mR3, Constant.INTERVAL_HIDE);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else if (isVisible(mBinding.recycler)) hideUI();
        else if (ResUtil.isLand(this)) showUI();
        else showControl();
        hideInfo();
    }

    private void resetPass() {
        this.count = 0;
    }

    private void setArtwork() {
        ImgUtil.load(this, mChannel.getLogo(), new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                mBinding.player.setDefaultArtwork(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                mBinding.player.setDefaultArtwork(errorDrawable);
            }
        });
    }

    @Override
    public void onItemClick(Group item) {
        // 0.1.119：点"全部"是开关——收起态展开显示全部可见分组，再点收回固定 4 项；
        // 频道网格不动（仍显示当前分组的台），不调 selectGroup。
        if (item == mAllGroup) {
            if (mPortExpanded) {
                mPortExpanded = false;
                mPortGroupAdapter.addAll(mPort4);
                mPortGroupAdapter.setSelected(tierIndexOf(mGroup));
            } else {
                mPortExpanded = true;
                List<Group> expanded = new ArrayList<>(mPortGroups);
                expanded.add(mAllGroup);
                mPortGroupAdapter.addAll(expanded);
                mPortGroupAdapter.setSelected(indexOfPortGroup(mGroup));
            }
            return;
        }
        // 0.1.102：点分组网格里的分组，就只显示那个分组的频道（四方格展开开关已经拿掉了，不用复位）。
        mLive.selectGroup(item);
        scrollToPosition(mBinding.channel, Math.max(item.getPosition(), 0));
        if (!item.isKeep() || ++count < 5 || mHides.isEmpty()) return;
        if (Biometric.enable()) Biometric.show(this);
        else PassDialog.create().show(this);
        resetPass();
    }

    @Override
    public void onItemClick(Channel item) {
        // 0.1.102：竖屏网格只显示当前分组的频道，点到的台一定在当前分组里，不用再找它属于哪个分组。
        // 只有点到的台就是正在播的台（同对象，或同名）才算"当前台"；同名但在别的分组里的台不算，点到就切过去。
        // （网格里只有当前分组的台，所以同名就是当前台。）
        boolean current = mChannel != null && (mChannel == item || mChannel.equals(item));
        if (!item.getData(mViewModel.getZoneId()).getList().isEmpty() && item.isSelected() && current) {
            if (ResUtil.isLand(this)) showEpg(item);
        } else if (mGroup != null && (!current || ResUtil.isLand(this))) {
            mLive.selectChannel(item.group(mGroup));
        }
    }

    @Override
    public boolean onLongClick(Channel item) {
        if (mGroup.isHidden()) return false;
        boolean exist = Keep.exist(item.getName());
        Notify.show(exist ? R.string.keep_del : R.string.keep_add);
        if (exist) delKeep(item);
        else addKeep(item);
        RefreshEvent.keep();
        return true;
    }

    @Override
    public void onItemClick(EpgData item) {
        mLive.selectEpg(item, player().getPosition());
    }

    private void addKeep(Channel item) {
        getKeep().add(item);
        Keep keep = new Keep();
        keep.setKey(item.getName());
        keep.setType(1);
        keep.save();
    }

    private void delKeep(Channel item) {
        if (mGroup.isKeep()) mChannelAdapter.remove(item);
        if (mGroup.isKeep()) mPortChannelAdapter.remove(item);
        getKeep().getChannel().remove(item);
        Keep.delete(item.getName());
    }

    private void setInfo() {
        mViewModel.getEpg(mChannel);
        mBinding.widget.play.setText("");
        mBinding.widget.name.setMaxEms(48);
        mChannel.loadLogo(mBinding.widget.logo);
        mBinding.widget.line.setText(mChannel.getLine());
        mBinding.widget.name.setText(mChannel.getShow());
        mBinding.widget.namePip.setText(mChannel.getShow());
        mBinding.widget.number.setText(mChannel.getNumber());
        mBinding.widget.numberPip.setText(mChannel.getNumber());
        mBinding.widget.line.setVisibility(mChannel.getLineVisible());
        mBinding.control.action.line.setText(mBinding.widget.line.getText());
        mBinding.control.action.line.setVisibility(mBinding.widget.line.getVisibility());
        updatePortInfo();
        renderPortProgram();
        App.post(mR4, 30000);
    }

    /** 0.1.118：竖屏节目卡——当前节目名＋进度＋接下来；无 EPG 时只显示频道名。 */
    private void renderPortProgram() {
        if (mChannel == null) return;
        Epg epg = mEpg != null ? mEpg : mChannel.getData(mViewModel.getZoneId());
        // selected() 按当前时间重算当前节目下标：新拼的合并 EPG 上这个标志不一定打过，直接调最稳（幂等）。
        epg.selected();
        EpgData data = epg.getEpgData();
        String title = data.getTitle();
        mBinding.portProgTitle.setText(title.isEmpty() ? mChannel.getShow() : title);
        long now = System.currentTimeMillis();
        long start = data.getStartTime();
        long end = data.getEndTime();
        if (title.isEmpty() || end <= start) {
            mBinding.portProgBar.setVisibility(View.GONE);
            mBinding.portProgNext.setVisibility(View.GONE);
            return;
        }
        int progress = (int) Math.clamp((now - start) * 100L / (end - start), 0, 100);
        mBinding.portProgBar.setVisibility(View.VISIBLE);
        mBinding.portProgBar.setProgress(progress);
        int selected = epg.getSelected();
        EpgData next = selected >= 0 && selected + 1 < epg.getList().size() ? epg.getList().get(selected + 1) : null;
        if (next == null || next.getTitle().isEmpty()) {
            mBinding.portProgNext.setVisibility(View.GONE);
        } else {
            String time = next.getStart();
            mBinding.portProgNext.setText(ResUtil.getString(R.string.live_next) + " " + (time.length() >= 5 ? time.substring(0, 5) : time) + " " + next.getTitle());
            mBinding.portProgNext.setVisibility(View.VISIBLE);
        }
    }

    /** 0.1.118：30 秒刷一次节目卡进度（节目名不重设）；节目切换边界最多晚 30 秒。 */
    private void refreshPortProgram() {
        renderPortProgram();
        App.post(mR4, 30000);
    }

    private void onEpgLoaded(Epg epg) {
        if (mChannel == null || !mChannel.getTvgId().equals(epg.getKey())) return;
        EpgData data = epg.getEpgData();
        mEpgDataAdapter.addAll(epg.getList());
        mLive.onEpgChanged(data);
        setWidth(epg);
        mEpg = epg;
        renderPortProgram();
        App.post(mR4, 30000);
    }

    private void onXmlParsed(boolean success) {
        if (mChannel != null && success) mViewModel.getEpg(mChannel);
    }

    private void stopPlayer() {
        player().clear();
        player().stop();
        checkControl();
    }

    @Override
    public int getGroupCount() {
        return mGroupAdapter.getItemCount();
    }

    @Override
    public int getGroupPosition() {
        return mGroupAdapter.getPosition();
    }

    @Override
    public Group getGroup(int position) {
        return mGroupAdapter.get(position);
    }

    @Override
    public boolean isPlayerLive() {
        return player().isLive();
    }

    @Override
    public boolean hasPlaybackSession() {
        return mPlaybackKey != null && hasActivePlaybackSession();
    }

    @Override
    public boolean isPlaybackServiceReady() {
        return service() != null;
    }

    @Override
    public void restorePlaybackKey(@Nullable String key) {
        if (key == null || !player().hasPlaySpec() || !key.equals(player().getKey())) return;
        updateNavigationKey(mPlaybackKey = key);
    }

    @Override
    public long getPlayerPosition() {
        return getActivePlaybackPosition();
    }

    @Override
    public ZoneId getZoneId() {
        return mViewModel.getZoneId();
    }

    @Override
    public void onCatchupRequested() {
        hideUI();
    }

    @Override
    public void preparePlaybackForRefresh() {
        player().prepareForReplacement();
    }

    @Override
    public void stopPlaybackForRefresh() {
        stopWebPlayback();
        stopPlayer();
    }

    @Override
    public void startPlayback(Result result, long position, MediaMetadata metadata) {
        mPlaybackKey = result.getRealUrl();
        if (startWebPlayback(result)) return;
        startPlayer(mPlaybackKey, result, false, getHome().getTimeout(), position, metadata);
    }

    private boolean startWebPlayback(Result result) {
        if (!startWebPlayback(mBinding.video, result, mInput::onTouchEvent)) return false;
        updateNavigationKey(mPlaybackKey);
        return true;
    }

    @Override
    public void resetPlaybackForError(String msg) {
        PlaybackReset.afterError(player());
        showError(msg);
    }

    @Override
    public void renderGroupSelection(Group group) {
        mGroupAdapter.setSelected(mGroup = group);
        // 0.1.119：竖屏分组行按当前数据集取下标——收起态按名前缀映射到固定 4 项里的档位，
        // 展开态在当前表里找该分组对象；不在表里（如 4K）不标选中。
        mPortGroupAdapter.setSelected(mPortExpanded ? indexOfPortGroup(group) : tierIndexOf(group));
    }

    /** 展开表里找分组对象的下标（同一批对象，== 比较），找不到为 -1。 */
    private int indexOfPortGroup(Group group) {
        if (group == null) return -1;
        for (int i = 0; i < mPortGroupAdapter.getItemCount(); i++) if (mPortGroupAdapter.get(i) == group) return i;
        return -1;
    }

    @Override
    public void renderGroupChannels(Group group) {
        mChannelAdapter.addAll(group.getChannel());
        mChannelAdapter.setSelected(group.getPosition());
        // 0.1.102：竖屏网格只显示当前分组的频道（四方格展开开关拿掉了），点哪个分组就换成那个分组的频道。
        mPortChannelAdapter.addAll(group.getChannel());
        mPortChannelAdapter.setSelected(group.getPosition());
    }

    @Override
    public void renderChannelSelection(Channel channel) {
        int position = mChannelAdapter.setSelected(channel.group(mGroup));
        if (mGroup != null) mGroup.setPosition(position);
        mChannel = channel;
        // 0.1.102：竖屏网格的选中位置就是当前台在分组内的下标。
        mPortChannelAdapter.setSelected(position);
        setArtwork();
        showInfo();
        hideUI();
    }

    @Override
    public void renderLineSelection(Channel channel, boolean show) {
        if (show) showInfo();
        else setInfo();
    }

    @Override
    public void renderEpgSelection(EpgData data) {
        mEpgDataAdapter.setSelected(data);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (service() != null && isOwner()) player().setMetadata(metadata);
        mBinding.widget.name.setMaxEms(TextUtils.isEmpty(metadata.subtitle) ? 48 : 12);
        mBinding.widget.play.setText(metadata.subtitle);
        mBinding.control.title.setText(metadata.displayTitle);
        mBinding.control.title.setSelected(true);
        mBinding.control.subtitle.setText(metadata.subtitle);
        mBinding.control.subtitle.setVisibility(TextUtils.isEmpty(metadata.subtitle) ? View.GONE : View.VISIBLE);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void resetAdapter() {
        mBinding.control.action.line.setVisibility(View.GONE);
        mBinding.control.title.setText("");
        mBinding.control.subtitle.setText("");
        mBinding.control.subtitle.setVisibility(View.GONE);
        mEpgDataAdapter.clear();
        mChannelAdapter.clear();
        mPortChannelAdapter.clear();
        mGroupAdapter.clear();
        mPortGroupAdapter.clear();
        mHides.clear();
        mChannel = null;
        mGroup = null;
        // 0.1.119：收起展开态，丢掉开关项（下次 setGroup 重建）；频道合成表已不存在。
        mPortExpanded = false;
        mAllGroup = null;
        mEpg = null;
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {

        @Override
        public boolean canSetSpeed() {
            return false;
        }

        @Override
        public Boolean isKept() {
            return mChannel == null || mGroup == null || mGroup.isHidden() ? null : Keep.exist(mChannel.getName());
        }

        @Override
        public boolean onKeep() {
            return isKept() != null && onLongClick(mChannel);
        }

        @Override
        public boolean onPush(String url) {
            if (isFinishing() || isDestroyed()) return false;
            mLive.redirectPush(url);
            return true;
        }

        @Override
        public boolean isExternalPlaybackActive() {
            return isWebPlaybackActive();
        }

        @Override
        public void onPlay() {
            LiveActivity.this.onPlay();
        }

        @Override
        public void onPause() {
            LiveActivity.this.onPaused();
        }

        @Override
        public void onNext() {
            mLive.nextChannel();
        }

        @Override
        public void onPrev() {
            mLive.prevChannel();
        }

        @Override
        public void onStop() {
            mLive.reset();
            finish();
        }

        @Override
        public void onAudio() {
            Util.moveToBackground(LiveActivity.this);
            setAudioOnly(true);
        }
    };

    @Override
    protected void onPrepare() {
        setPlaybackMode();
        player().setSpeed(PlayerSetting.getSpeed());
        checkControl();
    }

    @Override
    protected void onDecodeChanged() {
        setPlaybackMode();
    }

    @Override
    protected void onTracksChanged() {
        setTrackVisible();
    }

    @Override
    protected void onError(String msg) {
        mLive.playbackError(msg);
    }

    @Override
    protected boolean onRefresh() {
        mLive.refresh();
        return true;
    }

    @Override
    protected void onReclaim() {
        mLive.refresh();
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                hideProgress();
                checkControl();
                player().reset();
                break;
            case Player.STATE_ENDED:
                if (isWebPlaybackActive()) onPlaybackEnded();
                updatePlayControl(false);
                break;
        }
    }

    @Override
    protected void onPlaybackEnded() {
        mLive.playbackEnded();
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (isPlaying || isPaused() || isWebPlaybackActive()) updatePlayControl(isPlaying);
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mBinding.portIcon.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, isPlaying);
    }

    @Override
    public void setConfig(Config config) {
        Config current = LiveConfig.get().getConfig();
        LiveConfig.load(config, getCallback(current));
    }

    private Callback getCallback(Config config) {
        return new Callback() {
            @Override
            public void start() {
                showProgress();
            }

            @Override
            public void success() {
                setLive(getHome());
            }

            @Override
            public void error(String msg) {
                LiveConfig.load(config, new Callback());
                Notify.show(msg);
                hideProgress();
            }
        };
    }

    @Override
    public void setLive(Live item) {
        if (item.isSelected()) item.getGroups().clear();
        LiveConfig.get().setHome(item);
        player().reset();
        player().clear();
        player().stop();
        resetAdapter();
        hideControl();
        mLive.reset();
        getLive();
    }

    @Override
    public void setPass(String pass) {
        unlock(pass);
    }

    @Override
    public void onBiometricSuccess() {
        unlock(null);
    }

    private void unlock(String pass) {
        boolean first = true;
        Iterator<Group> iterator = mHides.iterator();
        while (iterator.hasNext()) {
            Group item = iterator.next();
            if (pass != null && !pass.equals(item.getPass())) continue;
            mGroupAdapter.add(item);
            mPortGroupAdapter.add(item);
            if (first) onItemClick(item);
            iterator.remove();
            first = false;
        }
    }

    private void refreshKeeps() {
        if (mGroupAdapter.getItemCount() == 0 || !getKeep().isKeep()) return;
        List<Group> groups = new ArrayList<>();
        for (int i = 0; i < mGroupAdapter.getItemCount(); i++) groups.add(mGroupAdapter.get(i));
        getKeep().getChannel().clear();
        LiveConfig.get().applyKeepsToGroups(groups);
        if (mGroup != null && mGroup.isKeep()) mChannelAdapter.addAll(getKeep().getChannel());
        if (mGroup != null && mGroup.isKeep()) mPortChannelAdapter.addAll(getKeep().getChannel());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case LIVE -> setLive(getHome());
            case KEEP -> refreshKeeps();
            case PLAYER -> mLive.refresh();
        }
    }

    @Override
    protected void onWebProgressChanged(boolean visible) {
        if (visible) showProgress();
        else hideProgress();
    }

    @Override
    protected void onWebPlaybackChanged(boolean active) {
        PlaybackAction.setWebPlaybackMode(active, mBinding.control.action.player, mBinding.control.action.decoder, mBinding.control.action.speed, mBinding.control.action.scale, mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
        if (!active) {
            setPlaybackMode();
            setTrackVisible();
        }
    }

    private void setTrackVisible() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video, mBinding.control.action.speed);
    }

    private void prevChannel() {
        mLive.prevChannel();
    }

    private void nextChannel() {
        mLive.nextChannel();
    }

    private void nextLine(boolean show) {
        mLive.nextLine(show);
    }

    private void onPaused() {
        if (pauseWebPlayback()) return;
        controller().pause();
    }

    private void onPlay() {
        if (resumeWebPlayback()) return;
        controller().play();
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (rotate) {
            noPadding(mBinding.recycler);
            noPadding(mBinding.control.getRoot());
        } else {
            setPadding(mBinding.recycler, true);
            setPadding(mBinding.control.getRoot());
        }
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public boolean onSpeedPressStart() {
        if (player().isLive()) return false;
        if (!player().isPlaying()) return false;
        return PlaybackAction.startSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onSpeedPressEnd() {
        PlaybackAction.endSpeedPress(player(), mBinding.widget.message);
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public boolean isScaleEnabled() {
        return !isInPictureInPictureMode();
    }

    @Override
    public void onPinchScale(float scaleX, float scaleY) {
        if (!mScaleFeedback.isActive()) PlaybackAction.hideSpeedHint(mBinding.widget.message);
        mScaleFeedback.show(scaleX, scaleY);
    }

    @Override
    public void onPinchScaleEnd() {
        mScaleFeedback.hide();
    }

    @Override
    public void onFlingUp() {
        if (LiveSetting.isInvert()) nextChannel();
        else prevChannel();
    }

    @Override
    public void onFlingDown() {
        if (LiveSetting.isInvert()) prevChannel();
        else nextChannel();
    }

    @Override
    public void onSeeking(long time) {
        if (!player().canSeek()) return;
        mBinding.widget.statusIcon.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        setSeekPreviewTime(mBinding.widget.time, time);
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (!player().canSeek()) return;
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        onToggle();
    }

    @Override
    public void onDoubleTap() {
        // 0.1.101：双击视频进全屏（用户 2026-11-24 13:09 定）。双击之前是开关控制栏，
        // 现在改成全屏——单击仍是开关控制栏（onSingleTap → onToggle），所以开关控制栏的功能没丢。
        // 单击和双击不冲突：手势识别用的是 onSingleTapConfirmed，双击不会先触发单击。
        onFullscreenClick();
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isRedirect()) return;
        if (isLock()) App.post(this::onLock, 500);
        if (service() == null) return;
        if (hasWebMediaTarget()) mPiP.enter(this, getWebVideoWidth(), getWebVideoHeight());
        else if (!isWebPlaybackActive() && player().haveTrack(C.TRACK_TYPE_VIDEO)) mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight());
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            hideControl();
            hideInfo();
            hideUI();
        } else {
            hideInfo();
            if (isStop()) finish();
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Util.hideSystemUI(this);
        updateFullscreenIcon();
        layoutOrientation();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 0.1.126：已打开的直播页回到前台时，把配置页拨开关攒下的直播加载做掉。
        // 新建时 checkLive() 已经消费过一次，这里取到的一定是 false，不会重复加载。
        if (ConfigManager.takeLiveReloadPending()) LiveConfig.load(LiveConfig.get().getConfig(), getCallback());
    }

    @Override
    protected void onStart() {
        super.onStart();
        setAudioOnly(false);
        setStop(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.info)) {
            hideInfo();
        } else if (isVisible(mBinding.recycler)) {
            hideUI();
        } else if (handleWebViewNavigation()) {
            return;
        } else if (ResUtil.isLand(this)) {
            onRotate();
        } else if (!isLock()) {
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        if (isBindingOwner() && isOwner()) Source.get().exit();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        super.onDestroy();
    }
}
