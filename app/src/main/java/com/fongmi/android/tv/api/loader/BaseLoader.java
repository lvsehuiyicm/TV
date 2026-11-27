package com.fongmi.android.tv.api.loader;

import android.net.Uri;
import android.text.TextUtils;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.Proxy;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.RuntimeModule;
import com.github.catvod.utils.Task;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import dalvik.system.DexClassLoader;

public class BaseLoader {

    private final List<SpiderLoader> retiredLoaders;
    private volatile JarLoader jarLoader;
    private volatile NodeLoader nodeLoader;
    private volatile PyLoader pyLoader;
    private volatile JsLoader jsLoader;
    // 0.1.104：网页嗅探引擎。
    private volatile WebLoader webLoader;

    private BaseLoader() {
        jarLoader = new JarLoader();
        nodeLoader = new NodeLoader();
        retiredLoaders = new ArrayList<>();
    }

    public static BaseLoader get() {
        return Loader.INSTANCE;
    }

    private static boolean isJs(String api) {
        return api.contains(".js");
    }

    private static boolean isPy(String api) {
        return api.contains(".py");
    }

    private static boolean isNode(String api) {
        return api.startsWith("node:");
    }

    // 0.1.104：网页嗅探源，api 以 web: 开头。
    private static boolean isWeb(String api) {
        return api.startsWith("web:");
    }

    private static boolean isCsp(String api) {
        return api.startsWith("csp_");
    }

    private synchronized JsLoader getJsLoader() {
        if (jsLoader == null) jsLoader = new JsLoader();
        return jsLoader;
    }

    /**
     * 0.1.127：Python 预热——在后台线程提前创建 PyLoader，触发 Python.start()
     * + app 模块导入，和配置加载并行；首页第一次建爬虫时 Python 已经是热的。
     * 创建的就是后面真正用的那个 PyLoader 实例（getPyLoader 懒加载、只建一次，
     * 0.1.126 起切换也不换实例），不是一次性的。预热失败静默（解释器没起来时
     * 第一次建爬虫会再启动一次），不影响正常流程。只能在后台线程调（真正的建
     * 爬虫本来就发生在后台线程，构造 PyLoader 走的是同一条路）。
     */
    public void warmupPython() {
        try {
            getPyLoader();
        } catch (Throwable ignored) {
        }
    }

    private synchronized PyLoader getPyLoader() {
        if (pyLoader == null) pyLoader = new PyLoader();
        return pyLoader;
    }

    private synchronized WebLoader getWebLoader() {
        if (webLoader == null) webLoader = new WebLoader();
        return webLoader;
    }

    public void clear() {
        clear(false);
    }

    public void destroy() {
        clear(true);
    }

    private void clear(boolean exiting) {
        List<SpiderLoader> previous;
        List<SpiderLoader> retired;
        synchronized (this) {
            // 0.1.108：非退出时保留 JarLoader（及其 md5 缓存的 DexClassLoader 与
            // spider 实例），不要每次切接口都换一个空的。以前每次 VodConfig.load()
            // 都调 clear() 换掉 JarLoader，parseJar 缓存永远是空的，每次切换都要
            // 重新 DexClassLoader + 跑 Init.init() + 重启 Go（“Go 启动成功”反复弹），
            // 切换要 5–10 秒。退出时仍全部销毁。
            // 0.1.126：非退出时同样保留 PyLoader / JsLoader / WebLoader / NodeLoader
            //（及其 spiders 缓存）。以前每次切接口都换新实例，新实例的 spiders map
            // 是空的，py 爬虫要现场重建（影视py 切换 17 秒的主因之一）。爬虫按 key
            // 缓存，本地 py/js 文件跨接口是同一个文件，复用安全（NodeLoader 的 key
            // 里本来就带 api）；jar 站 0.1.108 起已有保留先例。退出时仍全部销毁。
            // 注意：改过内容的 .py/.js 文件，复用的爬虫还是旧代码，改完要重启 app
            // 才生效（和 jar 站 0.1.108 起的行为一致）；新导入的文件不受影响（新 key）。
            List<SpiderLoader> toClear = exiting
                    ? Stream.of(nodeLoader, jarLoader, pyLoader, jsLoader, webLoader).filter(Objects::nonNull).toList()
                    : List.of();
            previous = toClear;
            retiredLoaders.addAll(previous);
            retired = exiting ? new ArrayList<>(retiredLoaders) : List.of();
            if (exiting) retiredLoaders.clear();
            if (exiting) {
                jarLoader = new JarLoader();
                nodeLoader = new NodeLoader();
                pyLoader = null;
                jsLoader = null;
                webLoader = null;
            }
        }
        Task.execute(() -> clearLoaders(previous, retired));
    }

    private void clearLoaders(List<SpiderLoader> previous, List<SpiderLoader> retired) {
        Throwable failure = null;
        for (SpiderLoader loader : previous) failure = clearLoader(loader, failure);
        for (SpiderLoader loader : retired) failure = destroyLoader(loader, failure);
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }

    private Throwable clearLoader(SpiderLoader loader, Throwable failure) {
        try {
            loader.clear();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    private Throwable destroyLoader(SpiderLoader loader, Throwable failure) {
        try {
            loader.destroy();
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else if (failure != error) failure.addSuppressed(error);
        }
        return failure;
    }

    public Spider getSpider(String key, String api, String ext, String jar) {
        // 0.1.104：web: 放第一位，防止 web URL 里含 .js 被误判成 JS 爬虫。
        if (isWeb(api)) {
            return getWebLoader().getSpider(key, api, ext);
        } else if (isNode(api)) {
            RuntimeModule.NODEJS.require();
            return nodeLoader.getSpider(key, api, ext);
        } else if (isPy(api)) {
            return getPyLoader().getSpider(key, api, ext);
        } else if (isJs(api)) {
            return getJsLoader().getSpider(key, api, ext, jar);
        } else if (isCsp(api)) {
            return jarLoader.getSpider(key, api, ext, jar);
        } else {
            return new SpiderNull();
        }
    }

    public Spider getSpider(String key) {
        if (SpiderTestSession.isKey(key)) return SpiderTestSession.require(key);
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        if (!site.isEmpty()) return site.spider();
        if (!live.isEmpty()) return live.spider();
        return new SpiderNull();
    }

    public void setRecent(String key, String api, String jar) {
        if (isNode(api)) return;
        if (isWeb(api)) getWebLoader().setRecent(key);
        else if (isJs(api)) getJsLoader().setRecent(key);
        else if (isPy(api)) getPyLoader().setRecent(key);
        else if (isCsp(api)) jarLoader.setRecent(Crypto.md5(jar));
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        if (params.containsKey("siteKey")) return getSpider(params.get("siteKey")).proxy(params);
        if ("js".equals(params.get("do"))) return getJsLoader().proxy(params);
        if ("py".equals(params.get("do"))) return getPyLoader().proxy(params);
        if ("web".equals(params.get("do"))) return getWebLoader().proxy(params);
        return jarLoader.proxy(params);
    }

    public void requireProxy(String url) {
        Uri uri = getProxyUri(url);
        if (uri == null) return;
        String key = uri.getQueryParameter("siteKey");
        if (key != null) requireSite(key);
    }

    private Uri getProxyUri(String url) {
        String prefix = Proxy.getUrl(true) + "?";
        if (url.startsWith("proxy://")) url = prefix + url.substring(8);
        return url.startsWith(prefix) ? Uri.parse(url) : null;
    }

    private void requireSite(String key) {
        if (SpiderTestSession.isKey(key)) {
            SpiderTestSession.require(key);
            return;
        }
        Site site = VodConfig.get().getSite(key);
        Live live = LiveConfig.get().getLive(key);
        requireRuntime(!site.isEmpty() ? site.getApi() : live.getApi());
    }

    private void requireRuntime(String api) {
        if (isNode(api)) RuntimeModule.NODEJS.require();
    }

    public void parseJar(String jar, boolean recent) {
        if (TextUtils.isEmpty(jar)) return;
        String key = Crypto.md5(jar);
        JarLoader loader = jarLoader;
        loader.parseJar(key, jar);
        if (recent) loader.setRecent(key);
    }

    public DexClassLoader dex(String jar) {
        return jarLoader.dex(jar);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) throws Throwable {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) throws Throwable {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    private static class Loader {
        static volatile BaseLoader INSTANCE = new BaseLoader();
    }
}
