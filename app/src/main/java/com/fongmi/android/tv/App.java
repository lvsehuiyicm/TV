package com.fongmi.android.tv;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.os.HandlerCompat;
import androidx.media3.mpvplayer.MpvLibrary;

import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.player.mpv.MpvUtil;
import com.fongmi.android.tv.receiver.RuntimeReceiver;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.SpiderDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.RuntimeInstaller;
import com.fongmi.hook.Hook;
import com.github.catvod.Runtime;
import com.github.catvod.utils.RuntimeModule;
import com.github.catvod.utils.Task;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class App extends Application implements Application.ActivityLifecycleCallbacks {

    private static volatile App instance;

    private final Handler handler;
    private final Gson gson;
    private final long time;

    private volatile Activity activity;
    private Hook hook;

    public App() {
        instance = this;
        gson = new Gson();
        time = System.currentTimeMillis();
        handler = HandlerCompat.createAsync(Looper.getMainLooper());
    }

    public static App get() {
        return instance;
    }

    public static Gson gson() {
        return get().gson;
    }

    public static long time() {
        return get().time;
    }

    public static Activity activity() {
        return get().activity;
    }

    public static void post(Runnable runnable) {
        get().handler.post(runnable);
    }

    public static void post(Runnable runnable, long delayMillis) {
        get().handler.removeCallbacks(runnable);
        if (delayMillis >= 0) get().handler.postDelayed(runnable, delayMillis);
    }

    public static void removeCallbacks(Runnable runnable) {
        get().handler.removeCallbacks(runnable);
    }

    public static void removeCallbacks(Runnable... runnable) {
        for (Runnable r : runnable) get().handler.removeCallbacks(r);
    }

    public void setHook(Hook hook) {
        this.hook = hook;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        Runtime.set(base);
        Runtime.setActivity(App::activity);
        // 0.1.134：Runtime.toast 是爬虫/运行时的错直接透传成 toast 的口子（AIGO、21:21 那句
        // IllegalArgumentException 都是这么出来的），完全绕过 SiteViewModel.handleError，
        // 所以没有 load-error 文件、toast 还被截断，查不到到底是哪个地址错了。
        // 改法：toast 里带 "Exception" 的（即异常原样透传），除了弹 toast，还在后台写一份
        // toast-error-*.txt（和 load-error 同目录），记下完整原文 + 时间。写文件失败静默，
        // 绝不影响 toast 本身。只认 "Exception" 是为了不给普通提示 toast 也写文件。
        Runtime.setToast(text -> App.post(() -> {
            Notify.show(text);
            if (text != null && text.contains("Exception")) Task.execute(() -> logToastError(text));
        }));
        Runtime.setDialog(SpiderDialog::show);
    }

    private static void logToastError(String text) {
        try {
            File dir = App.get().getExternalFilesDir(null);
            if (dir == null) return;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, "toast-error-" + stamp + ".txt");
            String head = "time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n"
                    + "source: Runtime.toast passthrough (bypasses SiteViewModel.handleError, no load-error file)\n\n";
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write((head + text).getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Setting.applyThemeMode();
        initRuntime();
        // 0.1.129：Python 预热挪到进程启动最早处——后台线程在 App.onCreate 里就开始起
        // Python 解释器（BaseLoader.warmupPython），到首页拉取时已经就绪；原来放在
        // HomeActivity.initConfig 里，和配置加载（约 4 秒）并行，配置完、首页请求开始时
        // Python 还差约 11 秒才起好，首页请求的 30 秒超时预算被 Python 启动吃掉，慢 VPN
        // 下直接超时变空白。预热失败静默（warmupPython 已 catch Throwable），不影响启动。
        Task.execute(() -> BaseLoader.get().warmupPython());
        Notify.createChannel();
        registerActivityLifecycleCallbacks(this);
    }

    private void initRuntime() {
        MpvLibrary.setLibraryLoader(MpvUtil::loadRuntime);
        RuntimeModule.refresh();
        cleanRuntime();
        RuntimeReceiver.register(this);
    }

    private void cleanRuntime() {
        if (!isMainProcess()) return;
        for (RuntimeModule module : RuntimeModule.values()) if (module != RuntimeModule.NODEJS) cleanRuntime(module);
    }

    private void cleanRuntime(RuntimeModule module) {
        try {
            module.clean();
        } catch (IOException e) {
            Log.w("RuntimeModule", "Unable to clean runtime cache", e);
        }
    }

    private boolean isMainProcess() {
        String name = getBaseContext().getPackageName();
        if (Build.VERSION.SDK_INT >= 28) return name.equals(Application.getProcessName());
        var processes = getSystemService(ActivityManager.class).getRunningAppProcesses();
        if (processes != null) for (var process : processes) if (process.pid == Process.myPid()) return name.equals(process.processName);
        return false;
    }

    @Override
    public PackageManager getPackageManager() {
        return hook != null ? hook : getBaseContext().getPackageManager();
    }

    @Override
    public String getPackageName() {
        return hook != null ? hook.getPackageName() : getBaseContext().getPackageName();
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (activity != activity()) this.activity = activity;
        RuntimeModule.refresh();
        RuntimeInstaller.resumeUpdate(activity);
        Updater.get().notifyModules();
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (activity == activity()) this.activity = null;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
    }
}
