package com.fongmi.android.tv.model;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.exception.SpiderLoadException;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.HomeResultCache;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Task;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public class SiteViewModel extends ViewModel {

    private final MutableLiveData<Result> result;
    private final MutableLiveData<Result> search;
    private final MutableLiveData<Result> action;

    private final ViewModelTaskRunner<TaskType> tasks;
    private final ViewModelSearchRunner searches;

    /** 首页内存缓存：key = 接口身份（activeId）+ 首页源 key。只放内存，退出即丢；只用来先显示，后台一定再拉一次（见 homeContent）。 */
    // 0.1.127：换成 ConcurrentHashMap——内存缓存现在会被多个后台线程同时读写
    // （拉新任务、磁盘读任务），HashMap 在这里不安全。
    private final Map<String, Result> homeCache = new ConcurrentHashMap<>();

    // 0.1.110 B(a)：首页请求的世代号。每次发起新的首页请求或切到分类/详情/搜索
    // （execute）时 +1；只有与当前世代号一致、且取回的仍是当前首页源的请求，
    // 才把结果推给界面；过期的只进缓存（切回该站时秒出），不覆盖当前界面。
    private final AtomicInteger homeEpoch = new AtomicInteger();
    private final List<ListenableFuture<?>> homeFutures = new ArrayList<>();

    public SiteViewModel() {
        result = new MutableLiveData<>();
        search = new MutableLiveData<>();
        action = new MutableLiveData<>();
        tasks = new ViewModelTaskRunner<>(TaskType.class);
        searches = new ViewModelSearchRunner();
    }

    public LiveData<Result> getResult() {
        return result;
    }

    public LiveData<Result> getSearch() {
        return search;
    }

    public LiveData<Result> getAction() {
        return action;
    }

    public SiteViewModel init() {
        search.setValue(null);
        result.setValue(null);
        action.setValue(null);
        return this;
    }

    public void homeContent() {
        Site home = VodConfig.get().getHome();
        String key = homeCacheKey(home);
        Result cached = homeCache.get(key);
        // 先显示缓存（切回已打开过的接口秒出内容），同时一定再拉一次新的，回来后更新缓存并刷新界面。
        if (cached != null) result.postValue(cached);
        // 0.1.110 B(a)：首页请求不再走共享的 tasks runner（它每种类型只有一个槽，
        // 新任务会取消旧的；5 秒点一个新站、首页要 10–20 秒时，永远等不到完成）。
        // 这里直接用 Task.submit 起请求，各站的请求互不取消、先出哪个算哪个；
        // 世代号 + 当前首页源双重校验，只让“最新、且仍是当前站”的那次推界面。
        int epoch = homeEpoch.incrementAndGet();
        // 0.1.132（觅影 MY-4）：新首页请求先取消旧的未完成请求。旧请求跑完回来也会因世代号
        // 丢弃，但不取消会占着线程池、拖慢新请求；被取消的旧请求不写缓存（回调里先判
        // CancellationException 直接返回，见下面 error 分支）。只取消 homeFutures
        // （首页的磁盘读+拉新），分类/详情走 tasks runner 不受影响。
        synchronized (homeFutures) {
            for (ListenableFuture<?> old : homeFutures) old.cancel(true);
            homeFutures.clear();
        }
        // 0.1.127：内存没命中时读磁盘缓存（上次的首页）。磁盘读放后台线程，不卡主线程；
        // 读到先显示，后台拉新的到了再替换。只当内存里还没有新结果时才贴（putIfAbsent
        // 原子判断：拉新先到了就不贴旧的，免得新结果被旧的盖回去）。
        if (cached == null) {
            FluentFuture<Result> diskFuture = FluentFuture.from(Task.submit(() -> HomeResultCache.get(key), 5000));
            synchronized (homeFutures) {
                homeFutures.add(diskFuture);
            }
            diskFuture.addCallback(Task.callback(disk -> {
                removeHomeFuture(diskFuture);
                if (disk == null) return;
                if (epoch != homeEpoch.get() || !isCurrentHome(home)) return;
                if (homeCache.putIfAbsent(key, disk) == null) result.postValue(disk);
            }, error -> removeHomeFuture(diskFuture)), MoreExecutors.directExecutor());
        }
        FluentFuture<Result> future = FluentFuture.from(Task.submit(() -> {
            Result fresh = SiteApi.homeContent(home);
            if (fresh != null) {
                homeCache.put(key, fresh);
                // 0.1.127：拉新的成功也写一份到磁盘，下次冷启动先显示它。
                HomeResultCache.put(key, fresh);
            }
            return fresh;
        }, Constant.TIMEOUT_VOD));
        synchronized (homeFutures) {
            homeFutures.add(future);
        }
        future.addCallback(Task.callback(fresh -> {
            removeHomeFuture(future);
            if (epoch == homeEpoch.get() && isCurrentHome(home)) result.postValue(fresh);
        }, error -> {
            removeHomeFuture(future);
            if (error instanceof CancellationException) return;
            if (epoch == homeEpoch.get() && isCurrentHome(home)) {
                // 0.1.129：拉取失败/超时不清空首页——当前首页源有缓存（内存或磁盘）时
                // 留着缓存内容，只 toast 一句；只有内存和磁盘都没有当前源缓存时，
                // 才按原来那样 post error/empty。判断以当前首页源的 key 为准，不拿错源的缓存顶。
                if (hasHomeCache(key)) {
                    App.post(() -> Notify.show(ResUtil.getString(R.string.error_home_keep_cache)));
                    error.printStackTrace();
                } else {
                    handleError(TaskType.RESULT, result, error);
                }
            } else error.printStackTrace();
        }), MoreExecutors.directExecutor());
    }

    private void removeHomeFuture(ListenableFuture<?> future) {
        synchronized (homeFutures) {
            homeFutures.remove(future);
        }
    }

    private static boolean isCurrentHome(Site home) {
        Site current = VodConfig.get().getHome();
        return current != null && home != null && current.getKey().equals(home.getKey());
    }

    /**
     * 0.1.129：当前首页源在内存缓存或磁盘缓存里有没有内容。key 必须是当前首页源的
     * homeCacheKey（activeId + 首页源 key），不错源。拉取失败/超时时用它决定要不要
     * post error/empty——有缓存就留着缓存内容。磁盘读是同步的，只在后台线程的失败
     * 回调里调，不在主线程调。
     */
    private boolean hasHomeCache(String key) {
        if (homeCache.get(key) != null) return true;
        return HomeResultCache.get(key) != null;
    }

    /**
     * 0.1.129：当前首页源在内存缓存里有没有可立即显示的内容。
     * VodFragment.homeContent() 用它决定：新源没有内存缓存时，先清掉界面上旧源的内容
     * （只留转圈），免得错源海报一直留着；有内存缓存时不清，缓存秒出直接替换。
     * 只看内存——磁盘读是异步的，等它到了再贴，中间那一下旧内容不能留。
     */
    public boolean hasHomeMemoryCache() {
        Site home = VodConfig.get().getHome();
        return home != null && homeCache.get(homeCacheKey(home)) != null;
    }

    /**
     * 0.1.126：首页缓存 key 改用接口身份（activeId）+ 首页源 key，不再绑配置 URL。
     * 以前 key 里是配置 URL：切文件夹接口时配置 URL 是上一个在线接口的，换个在线
     * 接口再切回文件夹就 miss；现在只要是同一个接口 + 同一个首页源就命中，切回秒出。
     * 不同接口下同名首页源也不会串（activeId 不同就是两个 key）。
     */
    private static String homeCacheKey(Site home) {
        return ConfigManager.getActiveId() + "|" + (home == null ? "" : home.getKey());
    }

    public void categoryContent(String key, String tid, String page, boolean filter, HashMap<String, String> extend) {
        execute(TaskType.RESULT, result, () -> SiteApi.categoryContent(key, tid, page, filter, extend));
    }

    public void action(String key, String act) {
        execute(TaskType.ACTION, action, () -> SiteApi.action(key, act));
    }

    public void detailContent(String key, String id) {
        execute(TaskType.RESULT, result, () -> SiteApi.detailContent(key, id));
    }

    public void searchContent(Site site, String keyword, boolean quick, String page) {
        execute(TaskType.RESULT, result, new SearchTask(site, keyword, quick, page));
    }

    public void searchContent(List<Site> sites, String keyword, boolean quick) {
        searches.start(sites, site -> new SearchTask(site, keyword, quick, "1"), search::postValue);
    }

    private void execute(TaskType type, MutableLiveData<Result> liveData, Callable<Result> callable) {
        // 0.1.110 B(a)：切到分类/详情/动作/搜索时，正在飞的首页请求就算回来也不再推界面
        // （避免把首页内容盖到分类/详情上）；它仍会进缓存。
        homeEpoch.incrementAndGet();
        tasks.execute(type, Constant.TIMEOUT_VOD, callable, liveData::postValue, error -> handleError(type, liveData, error));
    }

    private void handleError(TaskType type, MutableLiveData<Result> liveData, Throwable error) {
        String message = Notify.getError(error);
        // 0.1.110 B(b)：超时原来在 Notify.getError 里没有映射，返回 null，
        // 界面只剩静默空白。超时改给明确提示，可重试。
        if (message == null && isTimeout(error)) message = ResUtil.getString(R.string.error_load_timeout);
        // 0.1.132：Notify.getError 只认 5 种错（模块缺失/Extract/JS 缺 Spider/JS 缺 cat.js/
        // 爬虫加载失败），别的错一律返回 null，以前就静默 post Result.empty()——界面只剩
        // “这里什么都没有”，不 toast、不存文件。0.1.131 的 4K咕噜就是这么静默失败的：
        // -1 端口错没了（不再报 Invalid request options），但 homeContent 里别的错
        // （比如 py 的 homeVideoContent 抛错）没人认，全被静默吞掉。现在认不出的错也写
        // 诊断文件（load-error-*.txt，和 spider-error-*.txt 同目录），并给一句可见的
        // 报错（异常类名+信息），不再静默空盒子。
        if (message == null) {
            String detail = error.getClass().getSimpleName();
            if (error.getMessage() != null && !error.getMessage().isEmpty()) detail += ": " + error.getMessage();
            if (detail.length() > 120) detail = detail.substring(0, 120) + "…";
            message = detail;
            logErrorFile("load-error", error);
        }
        // 0.1.110 A.2.3 断点二：JS 爬虫加载失败时只留“爬虫加载失败”一句，
        // 看不到是哪一行抛的。把完整异常栈写进诊断文件，保存成功就在 toast
        // 后追加一句，xu 下次失败时把文件发我，就能定准那一行。
        if (message != null && isSpiderLoadError(error) && logErrorFile("spider-error", error)) {
            message += "，" + ResUtil.getString(R.string.error_spider_saved_hint);
        }
        liveData.postValue(message != null ? Result.error(message) : Result.empty());
        // message 在上面被多次赋值，不是 effectively final，lambda 不能直接捕获；用 final 副本。
        final String toast = message;
        if (type == TaskType.RESULT && toast != null) App.post(() -> Notify.show(toast));
        error.printStackTrace();
    }

    private static boolean isTimeout(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof TimeoutException) return true;
        }
        return false;
    }

    private static boolean isSpiderLoadError(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof SpiderLoadException) return true;
        }
        return false;
    }

    // 0.1.110 A.2.3 断点二：只留痕，不修引擎、不动 Module 的 import 解析。
    // 文件写在 app 外置存储目录（Android/data/com.zhulong.tv/files/，
    // 文件管理器可进），文件名带时间，如 spider-error-20261125-213000.txt，
    // 内容是完整异常栈（含 cause 链）。写失败返回 false，toast 不追加提示。
    // 0.1.132：改成带前缀的通用写文件（spider-error 给爬虫加载失败，
    // load-error 给 Notify.getError 认不出的其它加载失败），不再静默。
    private static boolean logErrorFile(String prefix, Throwable error) {
        try {
            File dir = App.get().getExternalFilesDir(null);
            if (dir == null) return false;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(new Date());
            File file = new File(dir, prefix + "-" + stamp + ".txt");
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            error.printStackTrace(pw);
            pw.flush();
            String head = "time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) + "\n";
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write((head + sw.toString()).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void stopSearch() {
        searches.stop();
    }

    @Override
    protected void onCleared() {
        stopSearch();
        tasks.cancelAll();
        synchronized (homeFutures) {
            for (ListenableFuture<?> future : homeFutures) future.cancel(true);
            homeFutures.clear();
        }
    }

    private record SearchTask(Site site, String keyword, boolean quick, String page) implements Callable<Result> {

        @Override
        public Result call() throws Exception {
            if (quick && !site.isQuickSearch()) return Result.empty();
            return SiteApi.searchContent(site, keyword, quick, page);
        }
    }

    private enum TaskType {RESULT, ACTION}
}
