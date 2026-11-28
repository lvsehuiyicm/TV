package com.fongmi.android.tv.ui.activity;

import static com.github.catvod.utils.Util.substring;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.DiscMenuState;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.VideoViewModel;
import com.fongmi.android.tv.playback.ExternalPlayback;
import com.fongmi.android.tv.playback.PlaybackAction;
import com.fongmi.android.tv.playback.PlaybackIntent;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.playback.PlaybackReset;
import com.fongmi.android.tv.playback.PlaybackResult;
import com.fongmi.android.tv.playback.vod.VodDetailResult;
import com.fongmi.android.tv.playback.vod.VodPlayRequest;
import com.fongmi.android.tv.playback.vod.VodPlaybackController;
import com.fongmi.android.tv.playback.vod.VodPlaybackHost;
import com.fongmi.android.tv.playback.vod.VodPlaybackMedia;
import com.fongmi.android.tv.player.media.MediaItemFactory;
import com.fongmi.android.tv.player.media.PlaySpec;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.DisplaySetting;
import com.fongmi.android.tv.setting.PlayerButtonsSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.SpeedSetting;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeRangeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.PinchScaleFeedback;
import com.fongmi.android.tv.ui.custom.TouchInput;
import com.fongmi.android.tv.ui.custom.TapSeekFeedback;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ChapterDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DecoderSettingDialog;
import com.fongmi.android.tv.ui.dialog.EditionDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.ParseDialog;
import com.fongmi.android.tv.ui.dialog.PlayerEngineDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SpeedSettingDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.utils.UriUtil;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, TouchInput.Listener, ControlDialog.Listener, ParseDialog.Listener, VodPlaybackHost, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private ActivityVideoBinding mBinding;
    private VideoViewModel mViewModel;
    private VodPlaybackController mVod;
    private FlagAdapter mFlagAdapter;
    private EpisodeAdapter mEpisodeAdapter;
    private EpisodeRangeAdapter mRangeAdapter;
    private QualityAdapter mQualityAdapter;
    private QuickAdapter mQuickAdapter;
    private ViewGroup.LayoutParams mFrameParams;
    private ValueAnimator mAnimator;
    private TouchInput mInput;
    private PinchScaleFeedback mScaleFeedback;
    private TapSeekFeedback mSeekFeedback;
    private Clock mClock;
    private PiP mPiP;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mR5;
    // 0.1.110 C(b)：缓冲提示的采样基线（上一秒已缓冲的毫秒数与采样时间），
    // 用于估算缓冲增长速度 → 预计还需几秒。showProgress 时重置。
    private long mBufferAheadMs = -1;
    private long mBufferSampleMs = 0;
    private History mHistory;
    /** 0.1.128：详情页选集全量表（集数格子里只放当前范围的一组，对话框和上下集还用全量）。 */
    private List<Episode> mEpisodes = new ArrayList<>();
    /** 0.1.128：范围 chips 一组 40 集（1-40、41-80……），和 EpisodeGridDialog 的页大小同款。 */
    private static final int EPISODE_RANGE = 40;
    /** 0.1.128：当前显示的是第几组范围（-1 = 还没定）。 */
    private int mEpisodeRange = -1;
    private boolean fullscreen;
    private boolean webFullscreen;
    private boolean externalPlaybackCompleted;
    private boolean useParse;
    private boolean rotate;

    public static void push(FragmentActivity activity, String text) {
        if (TextUtils.isEmpty(text)) return;
        Uri uri = UriUtil.uri(text);
        if (FileChooser.isFileSource(uri)) file(activity, uri);
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, Uri fileUri) {
        if (fileUri == null || activity.isFinishing() || activity.isDestroyed()) return;
        start(activity, SiteApi.LOCAL, fileUri.toString(), FileUtil.getDisplayName(fileUri));
    }

    public static void cast(Activity activity, History history) {
        history = history.copy();
        history.setVodPic(ImgUtil.cache(history.getVodPic()));
        Intent intent = createIntent(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic());
        intent.putExtra("history", history.toString());
        activity.startActivity(intent);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void collect(Activity activity, String key, Vod item) {
        Intent intent = createIntent(activity, key, item);
        intent.putExtra("collect", true);
        activity.startActivity(intent);
    }

    public static void start(Activity activity, String key, Vod item, String mark) {
        Intent intent = createIntent(activity, key, item);
        intent.putExtra("mark", item.getPlayId().isEmpty() ? mark : null);
        activity.startActivity(intent);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        Intent intent = createIntent(activity, key, id, name, pic);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        activity.startActivity(intent);
    }

    private static Intent createIntent(Activity activity, String key, Vod item) {
        Intent intent = createIntent(activity, key, item.getId(), item.getName(), item.getPic());
        intent.putExtra("playId", item.getPlayId());
        return intent;
    }

    private static Intent createIntent(Activity activity, String key, String id, String name, String pic) {
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("name", name);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        putPic(intent, pic);
        return intent;
    }

    private static void putPic(Intent intent, String pic) {
        intent.removeExtra("pic");
        pic = ImgUtil.cache(pic);
        if (!TextUtils.isEmpty(pic)) intent.putExtra("pic", pic);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private boolean isSameVideo(Intent intent) {
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        return TextUtils.equals(key, getKey()) && TextUtils.equals(id, getId());
    }

    @Override
    public String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    @Override
    public History takeCastHistory() {
        Intent intent = getIntent();
        History history = History.objectFrom(intent.getStringExtra("history"));
        intent.removeExtra("history");
        return history;
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private int getScale() {
        return PlayerSetting.getScale();
    }

    private void setScale(int scale) {
        PlayerSetting.putScale(scale);
        mVod.setScale(scale);
        mBinding.player.setResizeMode(PlayerSetting.getScaleMode(scale));
        setPlayerAspectRatio(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setPlayerAspectRatio(int scale) {
        float ratio = PlayerSetting.getAspectRatio(scale);
        AspectRatioFrameLayout frame = mBinding.player.findViewById(androidx.media3.ui.R.id.exo_content_frame);
        if (frame != null) frame.setAspectRatio(ratio);
    }

    private void updateSpeedButton() {
        float speed = service() == null || player() == null ? PlayerSetting.getSpeed() : player().getSpeed();
        mBinding.control.action.speed.setText(Math.abs(speed - SpeedSetting.NORMAL) < 0.001f ? getString(R.string.play_speed) : SpeedSetting.format(speed));
    }

    /**
     * 按播放器按钮配置应用控制栏：显隐 + 顺序。
     */
    private void applyPlayerButtons() {
        Map<String, View> views = new HashMap<>();
        views.put("episodes", mBinding.control.action.episodes);
        views.put("parse", mBinding.control.action.parse);
        views.put("player", mBinding.control.action.player);
        views.put("decoder", mBinding.control.action.decoder);
        views.put("reset", mBinding.control.action.reset);
        views.put("replay", mBinding.control.action.replay);
        views.put("repeat", mBinding.control.action.repeat);
        views.put("speed", mBinding.control.action.speed);
        views.put("scale", mBinding.control.action.scale);
        views.put("text", mBinding.control.action.text);
        views.put("audio", mBinding.control.action.audio);
        views.put("video", mBinding.control.action.video);
        views.put("danmaku", mBinding.control.action.danmaku);
        views.put("edition", mBinding.control.action.edition);
        views.put("chapter", mBinding.control.action.chapter);
        views.put("disc_menu", mBinding.control.action.discMenu);
        views.put("opening", mBinding.control.action.opening);
        views.put("ending", mBinding.control.action.ending);
        Set<String> hidden = PlayerButtonsSetting.getHidden();
        for (Map.Entry<String, View> entry : views.entrySet()) {
            boolean visible = !hidden.contains(entry.getKey());
            if ("danmaku".equals(entry.getKey())) visible = visible && DanmakuSetting.isLoad();
            if ("parse".equals(entry.getKey())) visible = visible && isUseParse();
            entry.getValue().setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        ViewGroup container = mBinding.control.action.container;
        container.removeAllViews();
        for (String id : PlayerButtonsSetting.getOrder()) {
            View view = views.get(id);
            if (view != null) container.addView(view);
        }
    }

    /**
     * 按屏显设置应用时间文字与进度条。
     */
    private void applySeekDisplay() {
        View position = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_position);
        View duration = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_duration);
        View progress = mBinding.control.seek.findViewById(androidx.media3.ui.R.id.exo_progress);
        if (position != null) position.setVisibility(DisplaySetting.isTime() ? View.VISIBLE : View.GONE);
        if (duration != null) duration.setVisibility(DisplaySetting.isTime() ? View.VISIBLE : View.GONE);
        if (progress != null) progress.setVisibility(DisplaySetting.isProgress() ? View.VISIBLE : View.GONE);
    }

    /**
     * 分辨率角标：只在全屏顶栏标题下显示。
     */
    private void updateResolutionBadge() {
        int height = 0;
        if (service() != null && player() != null) height = player().getVideoHeight();
        String text = height >= 2160 ? "4K" : height >= 1440 ? "1440p" : height >= 1080 ? "1080p" : height >= 720 ? "720p" : height > 0 ? height + "p" : "";
        mBinding.control.resolution.setText(text);
        mBinding.control.resolution.setVisibility(DisplaySetting.isResolution() && !text.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * 常驻屏显：直接叠在画面上的信息层，不跟随控制栏一起隐藏。
     * 标题 / 分辨率在左上，时钟在右上，当前 / 总时长在左下，网速在右下。
     */
    private void updateOsd() {
        boolean hasVideo = service() != null && player() != null && !player().isEmpty();
        boolean controlsHidden = mBinding.control.getRoot().getVisibility() != View.VISIBLE;
        boolean show = hasVideo && controlsHidden;
        boolean title = show && DisplaySetting.isTitle() && !TextUtils.isEmpty(mBinding.osd.osdTitle.getText());
        boolean resolution = show && DisplaySetting.isResolution() && !TextUtils.isEmpty(mBinding.osd.osdResolution.getText());
        mBinding.osd.osdTitle.setVisibility(title ? View.VISIBLE : View.GONE);
        mBinding.osd.osdResolution.setVisibility(resolution ? View.VISIBLE : View.GONE);
        mBinding.osd.osdTitleBlock.setVisibility(title || resolution ? View.VISIBLE : View.GONE);
        mBinding.osd.osdClock.setVisibility(show && DisplaySetting.isTime() ? View.VISIBLE : View.GONE);
        mBinding.osd.osdTime.setVisibility(show && DisplaySetting.isTime() ? View.VISIBLE : View.GONE);
        mBinding.osd.osdSpeed.setVisibility(show && DisplaySetting.isSpeed() ? View.VISIBLE : View.GONE);
        boolean any = title || resolution || (show && (DisplaySetting.isTime() || DisplaySetting.isSpeed()));
        mBinding.osd.getRoot().setVisibility(any ? View.VISIBLE : View.GONE);
    }

    private void updateOsdTitle() {
        CharSequence title = mBinding.control.title.getText();
        CharSequence subtitle = mBinding.control.subtitle.getText();
        mBinding.osd.osdTitle.setText(TextUtils.isEmpty(subtitle) ? String.valueOf(title) : title + " " + subtitle);
    }

    private void updateOsdResolution() {
        int width = 0, height = 0;
        if (service() != null && player() != null) {
            width = player().getVideoWidth();
            height = player().getVideoHeight();
        }
        mBinding.osd.osdResolution.setText(width > 0 && height > 0 ? width + " x " + height : "");
    }

    private void updateOsdTime() {
        if (service() == null || player() == null || player().isEmpty()) return;
        mBinding.osd.osdClock.setText(DateFormat.format("HH:mm:ss", System.currentTimeMillis()));
        long position = getPlayerPosition(), duration = getPlayerDuration();
        if (position >= 0 && duration > 0) mBinding.osd.osdTime.setText(formatOsdTime(position) + " / " + formatOsdTime(duration));
    }

    private String formatOsdTime(long ms) {
        long totalSeconds = ms / 1000;
        long hours = totalSeconds / 3600, minutes = totalSeconds % 3600 / 60, seconds = totalSeconds % 60;
        return hours > 0 ? hours + ":" + String.format("%02d:%02d", minutes, seconds) : String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 迷你进度条：控制栏隐藏时显示在画面底部。
     */
    private void updateMiniProgressValue(long position, long duration) {
        if (!DisplaySetting.isMini()) {
            mBinding.miniProgress.setVisibility(View.GONE);
            return;
        }
        boolean hidden = mBinding.control.getRoot().getVisibility() != View.VISIBLE;
        mBinding.miniProgress.setVisibility(hidden && duration > 0 ? View.VISIBLE : View.GONE);
        if (duration > 0) mBinding.miniProgress.setProgress((int) (position * 1000 / duration));
    }

    @Override
    public boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
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
        mVod.onPlaybackServiceReady();
        checkLand();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (TextUtils.isEmpty(intent.getStringExtra("id")) || (isSameVideo(intent) && !intent.hasExtra("history") && TextUtils.isEmpty(intent.getStringExtra("playId")))) return;
        externalPlaybackCompleted = false;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory(true);
        mVod.reset();
        setIntent(intent);
        renderFallbackName(getName());
        updateNavigationKey();
        checkControl();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mInput = TouchInput.create(this, mBinding.player, this);
        mScaleFeedback = new PinchScaleFeedback(mBinding.widget.scale);
        mSeekFeedback = new TapSeekFeedback(mBinding.video, mInput::cancelSeekTaps);
        mFrameParams = mBinding.video.getLayoutParams();
        mBinding.progressLayout.showProgress();
        mBinding.swipeLayout.setEnabled(false);
        mClock = Clock.create();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mR5 = this::updateOsdTick;
        App.post(mR5, 0);
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        updateOsd();
        setViewModel();
        showProgress();
        setAnimator();
        renderFallbackName(getName());
        checkId();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.director.setOnClickListener(view -> onDirector());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.fullscreen.setOnClickListener(view -> onFullscreenClick());
        mBinding.control.heading.setOnLongClickListener(view -> onChange());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.replay.setOnClickListener(view -> onReplay());
        mBinding.control.action.parse.setOnClickListener(view -> onParse());
        mBinding.control.action.player.setOnClickListener(view -> onPlayer());
        mBinding.control.action.decoder.setOnClickListener(view -> onDecoder());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.edition.setOnClickListener(view -> onEdition());
        mBinding.control.action.chapter.setOnClickListener(view -> onChapter());
        mBinding.control.action.discMenu.setOnClickListener(this::onDiscMenu);
        mBinding.control.action.discMenu.setOnLongClickListener(this::onDiscMenuLong);
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> dispatchDiscMenuTouch(event) || mInput.onTouchEvent(event));
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = top;
        mBinding.statusBar.setLayoutParams(lp);
        return insets;
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        // 0.1.128：选集从横向一条改成 4 列网格（布局里已是 GridLayoutManager span 4，
        // 下面是范围 chips）。格子本身带 6dp 边距，横向那条 8dp 间隔不要了；高度随组
        // 内集数变（1 集 1 行、40 集 10 行），hasFixedSize 必须关，不然换组后高度不变。
        mBinding.episode.setHasFixedSize(false);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        mBinding.episodeRange.setHasFixedSize(true);
        mBinding.episodeRange.setItemAnimator(null);
        mBinding.episodeRange.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mBinding.episodeRange.setAdapter(mRangeAdapter = new EpisodeRangeAdapter(this::showEpisodeRange));
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
    }

    private void setVideoView() {
        PlayerEngineDialog.setText(mBinding.control.action.player);
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> mPiP.update(this, view));
    }

    private void setVideoView(boolean expanded) {
        if (mAnimator.isRunning()) mAnimator.cancel();
        if (expanded) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
            if (isNativePlaybackCurrent()) changeHeight(player().getPlayer().getVideoSize());
        }
    }

    private void setAnimator() {
        mAnimator = new ValueAnimator();
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            if (isLand() || isFullscreen() || isInPictureInPictureMode()) return;
            mFrameParams.height = (int) animation.getAnimatedValue();
            mBinding.video.setLayoutParams(mFrameParams);
        });
    }

    private void setPlaybackMode() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setPlaybackMode(player(), mBinding.control.action.player);
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(VideoViewModel.class);
        mVod = mViewModel.createPlaybackController(this);
        observeWhenServiceReady(mViewModel.getDetail(), this::onDetailObserved);
        observeWhenServiceReady(mViewModel.getSearch(), this::onSearchObserved);
        observeWhenServiceReady(mViewModel.getPreload(), this::onPreloadObserved);
        observeWhenServiceReady(mViewModel.getPlayback(), this::onPlaybackObserved);
    }

    private void onDetailObserved(VodDetailResult result) {
        ExternalPlayback.applyTitle(result.result(), getIntent());
        mVod.onDetailResult(result);
    }

    private void onPlaybackObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPlaybackResult(result);
    }

    private void onPreloadObserved(PlaybackResult<VodPlayRequest> result) {
        mVod.onPreloadResult(result);
    }

    private void onSearchObserved(Result result) {
        mVod.onSearchResult(result);
    }

    @Override
    public String getVodKey() {
        return getKey();
    }

    @Override
    public String getVodId() {
        return getId();
    }

    @Override
    public void setVodId(String id) {
        String oldKey = getHistoryKey();
        boolean owned = isPlaybackCurrent();
        getIntent().putExtra("id", id);
        if (owned) player().updateKey(oldKey, getHistoryKey());
    }

    @Override
    public String getVodName() {
        return getName();
    }

    @Override
    public String getVodPic() {
        return getPic();
    }

    @Override
    public String getVodMark() {
        return getMark();
    }

    @Override
    public String getVodPlayId() {
        return Objects.toString(getIntent().getStringExtra("playId"), "");
    }

    @Override
    public void clearVodPlayId() {
        getIntent().removeExtra("playId");
    }

    @Override
    public boolean isSiteChangeable() {
        return getSite().isChangeable();
    }

    @Override
    public boolean isHostFinishing() {
        return isFinishing() || isDestroyed();
    }

    @Override
    public boolean isPlayerEmpty() {
        return !isWebPlaybackActive() && player().isEmpty();
    }

    @Override
    public boolean hasPlaybackSession() {
        return hasActivePlaybackSession();
    }

    @Override
    public boolean isLivePlayback() {
        return service() != null && isOwner() && player().isLive();
    }

    @Override
    public boolean canTrackPlaybackProgress() {
        return isWebPlaybackActive() ? canTrackActivePlaybackProgress() : isPlaybackCurrent() && player().isVod() && !isIsoNavigationPlayback();
    }

    @Override
    public boolean isIsoNavigationPlayback() {
        return super.isIsoNavigationPlayback();
    }

    @Override
    public boolean canPreloadNext() {
        return isPlaybackCurrent() && !ExternalPlayback.returnsResult(getIntent()) && player().canPreloadNext();
    }

    @Override
    public boolean canPreloadNextUrl() {
        return isPlaybackCurrent() && !ExternalPlayback.returnsResult(getIntent()) && player().canPreloadNextUrl();
    }

    @Override
    public boolean isPlayerPlaying() {
        return isPlaybackCurrent() && !isWebPlaybackActive() && player().isPlaying();
    }

    @Override
    public long getPlayerPosition() {
        return getActivePlaybackPosition();
    }

    @Override
    public long getPlayerDuration() {
        return getActivePlaybackDuration();
    }

    @Override
    public void usePushId(String id) {
        getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", id);
    }

    @Override
    public void onPlaybackRequested() {
        showProgress();
    }

    @Override
    public void prepareSource(Vod item) {
        ExternalPlayback.clearOptions(getIntent());
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("id", item.getId());
        getIntent().putExtra("playId", item.getPlayId());
        if (!item.getPlayId().isEmpty()) getIntent().removeExtra("mark");
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        putPic(getIntent(), item.getPic());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        player().reset();
        player().stop();
        checkControl();
    }

    @Override
    public void stopPlaybackForRefresh() {
        if (!isBindingOwner()) return;
        stopWebPlayback();
        service().stopPlayback(getNavigationCallback());
        player().clear();
        mClock.setCallback(null);
        checkControl();
    }

    @Override
    public void resetPlaybackForError(String msg) {
        mBinding.swipeLayout.setEnabled(true);
        PlaybackReset.afterError(player(), () -> mClock.setCallback(null));
        showError(msg);
    }

    @Override
    public void replay(long position) {
        if (replayWebPlayback(position)) return;
        player().replay(position);
    }

    @Override
    public void seekPlayback(long position) {
        long current = getPlayerPosition();
        if (current >= 0) seekTo(position - current);
    }

    @Override
    protected long endingDurationMs() {
        return mHistory == null ? 0 : mHistory.getEnding();
    }

    @Override
    public void setPlaybackEnding(long endingMs) {
        if (!isWebPlaybackActive() && isPlaybackCurrent()) player().setEnding(endingMs);
    }

    @Override
    public boolean isPlaybackEndingManaged() {
        return !isWebPlaybackActive() && isPlaybackCurrent() && player().isEndingManaged();
    }

    @Override
    public void startPlayback(Result result, boolean useParse, long startPositionMs, MediaMetadata metadata) {
        ExternalPlayback.apply(result, getIntent());
        startPositionMs = ExternalPlayback.takePosition(getIntent(), startPositionMs);
        if (startWebPlayback(mBinding.video, result)) return;
        startPlayer(getHistoryKey(), result, useParse, getSite().getTimeout(), startPositionMs, metadata);
    }

    @Override
    public boolean preloadPlayback(Result result, long startPositionMs, MediaMetadata metadata, VodPlayRequest request) {
        // 0.1.104：MPV 挂不了媒体，只把解析好的地址缓存在 PreloadEntry 里（点下一集时直接播，省掉重新解析）。
        if (PlayerSetting.isMpv()) return !isWebPlayback(result);
        return !isWebPlayback(result) && player().preload(PlaySpec.from(result, getHistoryKey(), metadata).setEnding(endingDurationMs()), startPositionMs, request);
    }

    @Override
    public boolean seekToPreloaded(VodPlayRequest request) {
        return isBindingOwner() && player().seekToPreloaded(request);
    }

    @Override
    public void clearPreload() {
        if (service() != null && isOwner() && isBindingOwner()) player().clearPreload();
    }

    @Override
    public void loadDanmaku(Result result, History history, Episode episode) {
        VodPlaybackMedia.searchDanmaku(result, history, episode, () -> isNativePlaybackCurrent() && mVod.isCurrentResult(result), player()::setDanmaku, player()::addDanmaku);
    }

    @Override
    public void renderDetail(Vod item, History history) {
        mHistory = history;
        mBinding.swipeLayout.setRefreshing(false);
        mBinding.progressLayout.showContent();
        renderFallbackName(item.getName());
        App.removeCallbacks(mR4);
        setArtwork(item.getPic());
        checkKeepImg();
        setText(item);
        updateKeep();
    }

    @Override
    public void renderVodUpdate(Vod item) {
        if (!item.getId().isEmpty()) updateNavigationKey();
        renderVodMetadata(item.getName(), item.getPic());
        setText(item);
    }

    private void renderVodMetadata(String name, String pic) {
        if (name.isEmpty() && pic.isEmpty()) return;
        if (!name.isEmpty()) renderFallbackName(name);
        if (!pic.isEmpty()) setArtwork();
        updateKeep();
    }

    @Override
    public void renderEmptyDetail() {
        showEmpty();
    }

    @Override
    public void renderFallbackName(String name) {
        getIntent().putExtra("name", name);
        mBinding.name.setText(name);
    }

    @Override
    public void renderFlags(List<Flag> items) {
        mBinding.flag.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mFlagAdapter.addAll(items);
    }

    @Override
    public void renderEpisodes(List<Episode> items) {
        setEpisodeAdapter(items);
    }

    @Override
    public void renderFlagSelection(Flag item) {
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
    }

    @Override
    public void renderEpisodeSelection(Episode item) {
        showEpisodeRangeFor(item);
        notifyItemChanged(mBinding.episode, mEpisodeAdapter);
        scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderReverseEpisodes(List<Episode> items, boolean scroll) {
        setEpisodeAdapter(items);
        if (scroll) scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    @Override
    public void renderQuality(Result result, boolean visible) {
        mBinding.swipeLayout.setRefreshing(false);
        mQualityAdapter.addAll(result);
        setQualityVisible(visible);
    }

    @Override
    public void renderQualityVisible(boolean visible) {
        setQualityVisible(visible);
    }

    @Override
    public void renderSources(List<Vod> items) {
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(mQuickAdapter.isEmpty() ? View.GONE : View.VISIBLE);
    }

    @Override
    public void renderHistory(History history) {
        mHistory = history;
        mBinding.control.action.opening.setText(openingText(history.getOpening()));
        mBinding.control.action.ending.setText(endingText(history.getEnding()));
        setScale(getScale());
    }

    @Override
    public void renderUseParse(boolean useParse) {
        setUseParse(useParse);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void renderArtwork(String url) {
        setArtwork(url);
    }

    @Override
    public void renderDescription(String desc) {
        setText(mBinding.content, 0, desc);
    }

    @Override
    public void renderPlaybackMetadata(MediaMetadata metadata) {
        if (isPlaybackCurrent()) player().setMetadata(metadata);
        mBinding.control.title.setText(MediaItemFactory.getDisplayTitle(metadata));
        mBinding.control.title.setSelected(true);
        mBinding.control.subtitle.setText(metadata.subtitle);
        mBinding.control.subtitle.setVisibility(TextUtils.isEmpty(metadata.subtitle) ? View.GONE : View.VISIBLE);
        updateOsdTitle();
        updateOsd();
    }

    @Override
    public void onDetailFallbackScheduled() {
        App.post(mR4, 10000);
    }

    @Override
    public void onDetailFallbackCancelled() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void onSearchStarted(String keyword) {
        mQuickAdapter.clear();
    }

    @Override
    public void onSearchResult() {
        App.removeCallbacks(mR4);
    }

    @Override
    public void showDetailMessage(String msg) {
        Notify.show(msg);
    }

    @Override
    public void showSwitchLine(Flag flag) {
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
    }

    @Override
    public void showSwitchSource(Vod item) {
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
    }

    @Override
    public void showNoNext(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_prev : R.string.error_play_next);
    }

    @Override
    public void showNoPrev(boolean reversed) {
        Notify.show(reversed ? R.string.error_play_next : R.string.error_play_prev);
    }

    @Override
    public void finishVod() {
        finish();
    }

    private void checkId() {
        mVod.checkId();
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setText(Vod item) {
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.content, 0, item.getContent());
        setText(mBinding.remark, 0, item.getRemarks());
        setOther(mBinding.other, item);
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view == mBinding.content) setContentVisible();
        view.setLinkTextColor(Color.WHITE);
        CustomMovement.bind(view);
    }

    private void setContentVisible() {
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getYear())).append("  ");
        if (!item.getArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(substring(sb.toString(), 2));
    }

    @Override
    public void onItemClick(Flag item) {
        mVod.selectFlag(item);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        mVod.selectEpisode(item);
    }

    @Override
    public void onItemClick(Result result) {
        mVod.selectQuality(result);
    }

    @Override
    public void onItemClick(Vod item) {
        mVod.selectSource(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.next.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(items.size() < 10 ? View.GONE : View.VISIBLE);
        // 0.1.128：全量表留着（对话框和上下集用它），格子里只放当前范围的一组；
        // 范围 chips 按当前集所在组选中，集数少就只有一组。拷一份，免得调用方
        // 之后改那个表把这里的全量表也改了（原来 adapter.addAll 也是拷一份）。
        mEpisodes = items == null ? new ArrayList<>() : new ArrayList<>(items);
        int group = getSelectedRange();
        setEpisodeRanges(group);
        showEpisodeRange(group);
    }

    /** 0.1.128：当前集在第几组范围里（没选中的集时回第 0 组）。 */
    private int getSelectedRange() {
        for (int i = 0; i < mEpisodes.size(); i++) if (mEpisodes.get(i).isSelected()) return i / EPISODE_RANGE;
        return 0;
    }

    /** 0.1.128：按 40 集一组拼范围 chips（1-40、41-80……；最后一组只剩 1 集就只写那集，如 201）。倒序时按 EpisodeGridDialog 同款标成倒着的（201-162）。 */
    private void setEpisodeRanges(int selected) {
        List<String> labels = new ArrayList<>();
        boolean reverse = mHistory != null && mHistory.isRevSort();
        for (int start = 0; start < mEpisodes.size(); start += EPISODE_RANGE) {
            labels.add(rangeLabel(start, Math.min(start + EPISODE_RANGE, mEpisodes.size()), reverse));
        }
        mBinding.episodeRange.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
        mRangeAdapter.setItems(labels, selected);
    }

    private String rangeLabel(int start, int end, boolean reverse) {
        int first = reverse ? mEpisodes.size() - start : start + 1;
        int last = reverse ? mEpisodes.size() - end + 1 : end;
        return first == last ? String.valueOf(first) : first + "-" + last;
    }

    /** 0.1.128：点范围 chip 只显示那一组的集数；点集数照常播放（走 EpisodeAdapter 原来的点击）。 */
    private void showEpisodeRange(int group) {
        if (mEpisodes.isEmpty()) {
            // 没集数时把格子清干净（别留着上一个剧的集数，虽然这时格子本身是 GONE 的）。
            mEpisodeAdapter.addAll(new ArrayList<>());
            return;
        }
        int groups = (mEpisodes.size() + EPISODE_RANGE - 1) / EPISODE_RANGE;
        group = Math.max(0, Math.min(group, groups - 1));
        mEpisodeRange = group;
        mRangeAdapter.setSelected(group);
        int start = group * EPISODE_RANGE;
        int end = Math.min(start + EPISODE_RANGE, mEpisodes.size());
        mEpisodeAdapter.addAll(mEpisodes.subList(start, end));
    }

    /** 0.1.128：当前集换组时（比如从 40 切到 41），先把范围 chip 切到那一组再刷新；还在同一组里就不动。 */
    private void showEpisodeRangeFor(Episode item) {
        if (mEpisodes.isEmpty() || item == null) return;
        int index = mEpisodes.indexOf(item);
        if (index >= 0 && index / EPISODE_RANGE != mEpisodeRange) showEpisodeRange(index / EPISODE_RANGE);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        mVod.search(name);
    }

    private void onMore() {
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(mEpisodes).show(this);
    }

    private void onActor() {
        mBinding.actor.setMaxLines(mBinding.actor.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onDirector() {
        mBinding.director.setMaxLines(mBinding.director.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onContent() {
        mBinding.content.setMaxLines(mBinding.content.getMaxLines() == 3 ? Integer.MAX_VALUE : 3);
    }

    private void onReverse() {
        mVod.setRevSort(!mHistory.isRevSort());
        mVod.reverseEpisode(false);
    }

    private boolean onChange() {
        mVod.manualSwitchSource();
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onBack() {
        if (isFullscreen()) exitFullscreen();
        else finish();
    }

    private void onCast() {
        CastDialog.create(player()).history(mHistory).show(this);
    }

    private void onInfo() {
        InfoDialog.create(player()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
        RefreshEvent.keep();
    }

    private void checkPlay() {
        setR1Callback();
        if (isPlayingForControls()) onPaused();
        else if (!isWebPlaybackActive() && player().isEmpty()) onRefresh();
        else onPlay();
    }

    private boolean isPlayingForControls() {
        if (isWebPlaybackActive()) return isWebPlaybackPlaying();
        return player().isPlaying() || ((isDiscMenuActive() || (isEnded() && isIsoPlaybackOngoing())) && controller().getPlayWhenReady());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        mVod.nextEpisode(notify);
    }

    private void checkPrev() {
        setR1Callback();
        mVod.prevEpisode(true);
    }

    private void onSetting() {
        ControlDialog.create().parent(mBinding).player(player()).show(this);
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

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).show(this);
        hideControl();
    }

    private void onEdition() {
        EditionDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onChapter() {
        ChapterDialog.create().show(this);
        hideControl();
    }

    private void onDiscMenu(View view) {
        openDiscMenu();
        hideControl();
    }

    private boolean onDiscMenuLong(View view) {
        if (openDiscPopupMenu()) hideControl();
        return true;
    }

    private void onDanmaku() {
        DanmakuDialog.create().show(this);
        hideControl();
    }

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuImg();
        syncDanmakuEnabled();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void onScale() {
        if (mInput.isScaled()) mInput.resetScale();
        else setScale((getScale() + 1) % ResUtil.getStringArray(R.array.select_scale).length);
        setR1Callback();
    }

    private void onSpeed() {
        SpeedSettingDialog.create().show(this);
        hideControl();
    }

    private boolean onSpeedLong() {
        PlaybackAction.toggleSpeed(player(), mBinding.widget.message);
        updateSpeedButton();
        setR1Callback();
        return true;
    }

    private void onReset() {
        onRefresh();
    }

    private void onParse() {
        ParseDialog.create().show(this);
        hideControl();
    }

    private void onReplay() {
        mVod.replay();
    }

    @Override
    protected boolean onRefresh() {
        mVod.refresh();
        return true;
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        setEnding(History.AUTO_SKIP);
        return true;
    }

    private void setEnding(long ending) {
        mVod.setEnding(ending);
        mBinding.control.action.ending.setText(endingText(ending));
    }

    private String endingText(long ending) {
        return ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(ending);
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(History.AUTO_SKIP);
        return true;
    }

    private void setOpening(long opening) {
        mVod.setOpening(opening);
        mBinding.control.action.opening.setText(openingText(opening));
    }

    private String openingText(long opening) {
        return opening <= 0 ? getString(R.string.play_op) : Util.timeMs(opening);
    }

    private void onEpisodes() {
        EpisodeListDialog.create().episodes(mEpisodes).show(this);
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
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) mVod.requestDetail();
        else mVod.refresh();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        setFullscreen(true);
        if (isLand() && !player().isPortrait()) setTransition();
        setVideoView(true);
        setRequestedOrientation(PlaybackOrientation.getEnterFullscreenOrientation(player().isPortrait()));
        mBinding.control.heading.setVisibility(DisplaySetting.isTitle() ? View.VISIBLE : View.INVISIBLE);
        setRotate(player().isPortrait());
        mInput.resetScale();
        App.post(mR3, 2000);
        hideControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        setFullscreen(false);
        mInput.cancelSeekTaps();
        mSeekFeedback.clear();
        if (isLand() && !player().isPortrait()) setTransition();
        setRequestedOrientation(PlaybackOrientation.getExitFullscreenOrientation(isPort()));
        mBinding.episode.postDelayed(() -> mBinding.episode.scrollToPosition(mEpisodeAdapter.getPosition()), 100);
        mBinding.control.heading.setVisibility(View.INVISIBLE);
        setVideoView(false);
        mInput.resetScale();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
    }

    private void onFullscreenClick() {
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
    }

    private void updateFullscreenIcon() {
        mBinding.control.fullscreen.setImageResource(isFullscreen() ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    private void setTransition() {
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate(), isPort() && isAutoRotate());
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        // 0.1.110 C(b)：新一轮缓冲开始，重置缓冲采样基线。
        mBufferAheadMs = -1;
        mBufferSampleMs = 0;
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

    private void syncDanmakuEnabled() {
        player().setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void hideDanmaku() {
        player().setDanmakuEnabled(false);
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        if (isDiscMenuActive()) updatePlayControl(isPlayingForControls());
        updateDiscMenuVisibility();
        mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.action.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        updateFullscreenIcon();
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        applyPlayerButtons();
        updateSpeedButton();
        applySeekDisplay();
        updateResolutionBadge();
        updateOsd();
        updateMiniProgressValue(getPlayerPosition(), getPlayerDuration());
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        updateMiniProgressValue(getPlayerPosition(), getPlayerDuration());
        updateOsd();
        App.removeCallbacks(mR1);
    }

    @Override
    protected void onDiscMenuLongPress() {
        showControl();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (dispatchDiscMenuKey(event)) {
            hideControl();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void dismissDialogs() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) if (fragment instanceof DialogFragment dialog) dialog.dismiss();
    }

    // 0.1.110 C(b)：缓冲时（progress 视图在显示，mR2 每秒跑一次）不再只转圈，
    // 在转圈下方的 traffic 文本行显示“正在缓冲，已缓冲 X 秒，预计还需 Y 秒”
    // （网速开关开着时再拼上当前网速）。已缓冲 = 已缓冲位置 − 当前位置；
    // 预计还需 = 还差多少才够起播门槛（forPlayback 2.5 秒 × 缓冲倍数）÷ 最近
    // 一秒的缓冲增长速度；增长为 0（卡住）或已够门槛时不给预计时间。
    // 注意：缓冲提示不看网速开关——开关只决定拼不拼网速那一段。
    private void setTraffic() {
        mBinding.progress.traffic.setText(bufferingText());
        mBinding.progress.traffic.setVisibility(View.VISIBLE);
        App.post(mR2, 1000);
    }

    private String bufferingText() {
        // 0.1.111 热修复：player() 内部直接调 mService.player()，service 未绑定时
        // mService 为 null，player() 自己先抛 NPE，根本到不了后面的判空。播放页刚创建、
        // 每秒刷新的流量循环就可能撞上它，导致一开视频就闪退。先按本文件一贯的写法
        // 判 service() == null，短路，不再往下走。
        if (service() == null || player() == null) return null;
        String speed = DisplaySetting.isSpeed() ? Traffic.speedText() : null;
        String suffix = speed == null ? "" : " · " + speed;
        Player player = player().getPlayer();
        if (player == null) return "正在缓冲…" + suffix;
        long buffered = player.getBufferedPosition();
        long current = player.getCurrentPosition();
        if (buffered == C.TIME_UNSET || buffered < 0) return "正在缓冲…" + suffix;
        long aheadMs = Math.max(0, buffered - Math.max(0, current));
        long aheadSec = aheadMs / 1000;
        long now = System.currentTimeMillis();
        String eta = "";
        if (mBufferAheadMs >= 0 && mBufferSampleMs > 0 && now > mBufferSampleMs) {
            long growthMs = aheadMs - mBufferAheadMs;
            double growthPerSec = growthMs * 1000.0 / (now - mBufferSampleMs);
            long targetMs = 2500L * PlayerSetting.getBuffer();
            long remainMs = targetMs - aheadMs;
            if (growthPerSec > 0 && remainMs > 0) {
                long etaSec = Math.max(1, Math.round(remainMs / growthPerSec));
                eta = "，预计还需 " + etaSec + " 秒";
            }
        }
        mBufferAheadMs = aheadMs;
        mBufferSampleMs = now;
        return "正在缓冲，已缓冲 " + aheadSec + " 秒" + eta + suffix;
    }

    /**
     * 常驻屏显的心跳：每秒刷新网速、时钟、当前 / 总时长和可见性。
     * 不能复用 setTraffic 的循环——hideProgress() 会在播放开始时停掉它。
     */
    private void updateOsdTick() {
        boolean hasVideo = service() != null && player() != null && !player().isEmpty();
        if (hasVideo && DisplaySetting.isSpeed()) Traffic.setSpeed(mBinding.osd.osdSpeed);
        else mBinding.osd.osdSpeed.setVisibility(View.GONE);
        updateOsdTime();
        updateOsd();
        if (hasVideo && isVisible(mBinding.control.getRoot())) updateSpeedButton();
        App.post(mR5, 1000);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getPortAutoRotateOrientation());
        if (isLand() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getLandAutoRotateOrientation());
    }

    private void setR1Callback() {
        if (isScrubbing()) return;
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    @Override
    protected void onScrubbingChanged(boolean scrubbing) {
        if (scrubbing) App.removeCallbacks(mR1);
        else if (isVisible(mBinding.control.getRoot())) setR1Callback();
    }

    private void setArtwork(String url) {
        mHistory.setVodPic(url);
        setArtwork();
    }

    private void setArtwork() {
        ImgUtil.load(this, mHistory.getVodPic(), mArtworkTarget);
    }

    private final CustomTarget<Drawable> mArtworkTarget = new CustomTarget<>() {
        @Override
        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
            mBinding.player.setDefaultArtwork(resource);
        }

        @Override
        public void onLoadFailed(@Nullable Drawable errorDrawable) {
            mBinding.player.setDefaultArtwork(errorDrawable);
        }

        @Override
        public void onLoadCleared(@Nullable Drawable placeholder) {
            mBinding.player.setDefaultArtwork(null);
        }
    };

    private void saveHistory(boolean exit) {
        if (!isWebPlaybackActive() && !isPlaybackCurrent()) return;
        long position = getActivePlaybackPosition();
        long duration = getActivePlaybackDuration();
        if (mVod != null) mVod.saveHistory(exit, System.currentTimeMillis(), position, duration);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        mBinding.control.keep.setImageResource(Keep.find(getHistoryKey()) == null ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on);
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(DanmakuSetting.isShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {

        @Override
        public Boolean isKept() {
            return mHistory == null ? null : Keep.find(getHistoryKey()) != null;
        }

        @Override
        public boolean onKeep() {
            if (mHistory == null) return false;
            VideoActivity.this.onKeep();
            return true;
        }

        @Override
        public void onSaveProgress() {
            saveHistory(true);
        }

        @Override
        public boolean onPush(String url) {
            if (isFinishing() || isDestroyed()) return false;
            mVod.redirectPush(url);
            return true;
        }

        @Override
        public boolean isExternalPlaybackActive() {
            return isWebPlaybackActive();
        }

        @Override
        public void onPlay() {
            VideoActivity.this.onPlay();
        }

        @Override
        public void onPause() {
            VideoActivity.this.onPaused();
        }

        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }

        @Override
        public void onAudio() {
            Util.moveToBackground(VideoActivity.this);
            setAudioOnly(true);
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

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
    protected void onMediaOptionsChanged() {
        setMediaOptionVisible();
    }

    @Override
    protected void onError(String msg) {
        mVod.playbackError(msg);
    }

    @Override
    protected void onReclaim() {
        mVod.refresh();
    }

    @Override
    public void onDiscMenuStateChanged(@NonNull DiscMenuState state) {
        if (!isNativePlaybackCurrent()) return;
        super.onDiscMenuStateChanged(state);
        updateDiscMenuVisibility();
        if (isDiscMenuTransition()) hideProgress();
    }

    private void updateDiscMenuVisibility() {
        mBinding.control.action.discMenu.setVisibility(hasDiscMenu() ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                if (isDiscMenuTransition()) hideProgress();
                else showProgress();
                mClock.setCallback(null);
                break;
            case Player.STATE_READY:
                hideProgress();
                checkControl();
                player().reset();
                mClock.setCallback(this);
                break;
            case Player.STATE_ENDED:
                hideProgress();
                mClock.setCallback(null);
                if (isWebPlaybackActive()) onPlaybackEnded();
                break;
        }
    }

    @Override
    protected void onPreloadError(Object tag) {
        if (tag instanceof VodPlayRequest request) mVod.onPreloadError(request);
    }

    @Override
    protected void onPreloadTransition(Object tag, long oldPositionMs, long oldDurationMs) {
        if (tag instanceof VodPlayRequest request) mVod.onPreloadTransition(request, oldPositionMs, oldDurationMs);
    }

    @Override
    protected void onPlaybackEnded() {
        if (ExternalPlayback.returnsResult(getIntent())) {
            externalPlaybackCompleted = true;
            finish();
            return;
        }
        setR1Callback();
        mVod.playbackEnded();
        updatePlayControl(false);
    }

    @Override
    public void finish() {
        if (ExternalPlayback.returnsResult(getIntent())) ExternalPlayback.setResult(this, externalPlaybackCompleted ? getActivePlaybackDuration() : getActivePlaybackPosition(), externalPlaybackCompleted);
        super.finish();
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        mVod.onPlayingChanged(isPlaying && isPlayerPlaying());
        boolean playingForControls = isPlaying || (!isWebPlaybackActive() && (isDiscMenuActive() || (isEnded() && isIsoPlaybackOngoing())) && controller().getPlayWhenReady());
        if (playingForControls || isPaused() || isWebPlaybackActive()) updatePlayControl(playingForControls);
    }

    @Override
    public void onPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        if (isPlaybackCurrent() && oldPosition.mediaItemIndex == newPosition.mediaItemIndex && (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT)) mVod.onPlaybackSeek();
    }

    private void updatePlayControl(boolean isPlaying) {
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mPiP.update(this, isPlaying);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        changeHeight(size);
        checkOrientation(size);
        updateResolutionBadge();
        updateOsdResolution();
        updateOsd();
    }

    @Override
    public void onTimeChanged(long time) {
        if (!canTrackPlaybackProgress()) return;
        long position = getPlayerPosition();
        long duration = getPlayerDuration();
        if (position < 0) return;
        mVod.onTimeChanged(time, position, duration);
        updateMiniProgressValue(position, duration);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) mVod.requestDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) mVod.refresh();
        else if (event.getType() == RefreshEvent.Type.KEEP) checkKeepImg();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().setDanmaku(Danmaku.from(event.getPath()));
    }

    @Override
    protected long startPositionMs() {
        return mVod == null ? C.TIME_UNSET : mVod.startPositionMs();
    }

    private void checkOrientation(VideoSize size) {
        if (isFullscreen() && !isRotate() && size.height > size.width) {
            setRequestedOrientation(PlaybackOrientation.getPortraitVideoSizeOrientation());
            setRotate(true);
        } else if (isFullscreen() && isRotate() && size.width > size.height) {
            setRequestedOrientation(PlaybackOrientation.getLandscapeVideoSizeOrientation());
            setRotate(false);
        }
    }

    private void changeHeight(VideoSize size) {
        if (isLand() || isFullscreen() || isInPictureInPictureMode()) return;
        int videoWidth = size.width;
        int videoHeight = size.height;
        if (videoWidth == 0 || videoHeight == 0) return;
        int screenWidth = ResUtil.getScreenWidth();
        int screenHeight = ResUtil.getScreenHeight();
        int viewWidth = Math.min(screenWidth, screenHeight);
        int minHeight = ResUtil.dp2px(150);
        int maxHeight = Math.max(screenWidth, screenHeight) / 2;
        float aspectRatio = (videoWidth * size.pixelWidthHeightRatio) / videoHeight;
        int calculated = (int) (viewWidth / aspectRatio);
        int finalHeight = Math.clamp(calculated, minHeight, Math.max(minHeight, maxHeight));
        if (mAnimator.isRunning()) mAnimator.cancel();
        if (finalHeight == mFrameParams.height) return;
        mAnimator.setIntValues(mFrameParams.height, finalHeight);
        mAnimator.setDuration(300);
        mAnimator.start();
    }

    @Override
    protected boolean supportsWebSeek() {
        return true;
    }

    @Override
    protected void onWebProgressChanged(boolean visible) {
        if (visible) showProgress();
        else hideProgress();
    }

    @Override
    protected void onWebPlaybackChanged(boolean active) {
        PlaybackAction.setWebPlaybackMode(active, mBinding.control.action.player, mBinding.control.action.decoder, mBinding.control.action.reset, mBinding.control.action.replay, mBinding.control.action.repeat, mBinding.control.action.speed, mBinding.control.action.scale, mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video, mBinding.control.action.danmaku, mBinding.control.action.edition, mBinding.control.action.chapter, mBinding.control.action.opening, mBinding.control.action.ending);
        if (!active) {
            setPlaybackMode();
            setTrackVisible();
            setMediaOptionVisible();
            mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onWebFullscreenChanged(boolean fullscreen) {
        if (fullscreen) {
            webFullscreen = !isFullscreen();
            if (webFullscreen) enterFullscreen();
        } else if (webFullscreen) {
            webFullscreen = false;
            if (isFullscreen()) exitFullscreen();
        }
    }

    private void setTrackVisible() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setTracks(player(), mBinding.control.action.text, mBinding.control.action.audio, mBinding.control.action.video);
    }

    private void setMediaOptionVisible() {
        if (isWebPlaybackActive()) return;
        PlaybackAction.setMediaOptions(player(), mBinding.control.action.edition, mBinding.control.action.chapter);
    }

    private void onPaused() {
        if (pauseWebPlayback()) return;
        controller().pause();
        if (isDiscMenuActive() || (isEnded() && isIsoPlaybackOngoing())) updatePlayControl(isPlayingForControls());
    }

    private void onPlay() {
        if (resumeWebPlayback()) return;
        if (mHistory != null && isEnded() && !isDiscMenuActive() && !isIsoPlaybackOngoing()) controller().seekTo(mVod.openingPositionMs());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        if (isEnded() && isIsoPlaybackOngoing()) controller().setPlayWhenReady(true);
        else controller().play();
        if (isDiscMenuActive() || (isEnded() && isIsoPlaybackOngoing())) updatePlayControl(isPlayingForControls());
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        else noPadding(mBinding.control.getRoot());
    }

    private void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        player().stop();
    }

    @Override
    public void onParse(Parse item) {
        mVod.selectParse(item);
    }

    @Override
    public void onScaleChanged() {
        setScale(PlayerSetting.getScale());
    }

    @Override
    public void onSpeedChanged() {
        updateSpeedButton();
    }

    @Override
    public boolean onSpeedPressStart() {
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
        return isFullscreen() && !isInPictureInPictureMode();
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
        // 0.1.128：按全量集数判（格子里现在只有当前组的一组，最后一组只剩 1 集时也不能当成只有 1 集）。
        if (mEpisodes.size() == 1) onRefresh();
        else mVod.nextEpisode(true);
    }

    @Override
    public void onFlingDown() {
        if (mEpisodes.size() == 1) onRefresh();
        else mVod.prevEpisode(true);
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.statusIcon.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        if (!isFullscreen()) {
            enterFullscreen();
        } else if (isPlayingForControls()) {
            showControl();
            onPaused();
        } else {
            hideControl();
            onPlay();
        }
    }

    @Override
    public boolean isSeekTapArea(float x) {
        return isFullscreen() && !isLock() && mSeekFeedback.directionAt(x) != 0;
    }

    @Override
    public boolean onSeekTap(float x) {
        if (!isSeekTapArea(x) || !canTrackActivePlaybackProgress() || isDiscMenuActive()) return false;
        hideControl();
        long delta = mSeekFeedback.show(x);
        seekTo(delta);
        return true;
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
        PlaybackAction.hideSpeedHint(mBinding.widget.message);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlaybackIntent.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
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
    public void onPictureInPictureModeChanged(boolean inPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPictureInPictureMode, newConfig);
        if (!isBindingOwner()) return;
        if (inPictureInPictureMode) onEnterPictureInPicture();
        else onExitPictureInPicture();
    }

    private void onEnterPictureInPicture() {
        if (!isFullscreen()) setVideoView(true);
        dismissDialogs();
        hideControl();
        hideDanmaku();
    }

    private void onExitPictureInPicture() {
        if (!isFullscreen()) setVideoView(false);
        syncDanmakuEnabled();
        if (isStop()) finish();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isWebPlaybackActive()) return;
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && !isRotate() && !isLock()) exitFullscreen();
        if (isAutoRotate() && isPort() && newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) enterFullscreen();
        if (isFullscreen()) Util.hideSystemUI(this);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus && !Util.isFullscreen(this)) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        setAudioOnly(false);
        setStop(false);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mInput.cancelSeekTaps();
        mSeekFeedback.clear();
        saveHistory(false);
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (handleWebViewNavigation()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (handleDiscMenuBack()) {
            return;
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            mViewModel.stopSearch();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void onDestroy() {
        mClock.release();
        saveHistory(true);
        Timer.get().reset();
        if (isBindingOwner()) DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mR5);
        super.onDestroy();
    }
}
