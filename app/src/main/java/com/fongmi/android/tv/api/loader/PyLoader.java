package com.fongmi.android.tv.api.loader;

import android.net.Uri;

import com.fongmi.android.tv.App;
import com.fongmi.chaquo.RuntimeEntry;
import com.github.catvod.crawler.Spider;
import com.github.catvod.runtime.PythonRuntime;

import java.util.Locale;
import java.util.Map;

public class PyLoader extends SpiderLoader {

    private final PythonRuntime loader;
    private volatile String recent;

    public PyLoader() {
        this(new RuntimeEntry());
    }

    PyLoader(PyLoader previous) {
        this(previous.loader);
    }

    private PyLoader(PythonRuntime loader) {
        loader.initialize();
        this.loader = loader;
    }

    @Override
    protected void clearReferences() {
        recent = null;
    }

    public synchronized void setRecent(String recent) {
        checkOpen();
        this.recent = recent;
    }

    public Spider getSpider(String key, String api, String ext) {
        return load(key, App.get(), ext, () -> newSpider(key, api));
    }

    Spider newSpider(String key, String api) {
        return loader.spider(toLocalFile(api), key, JsHost.INSTANCE, JsHost.INSTANCE::getUserAgent);
    }

    /**
     * 0.1.131：本地 py 文件不走 localhost HTTP，直接读盘。UrlUtil.convert 会把 file://
     * 转成本机 http 地址（http://127.0.0.1:&lt;端口&gt;/file/&lt;编码路径&gt;），Python
     * read_source 见到 http 就走 read_remote 调 OkHttp 去取；服务器没起时端口是 -1，
     * 地址从一开始就是坏的（OkHttp 认不出 -1 端口，报 IllegalArgumentException →
     * "Invalid request options"，4K咕噜爬虫加载失败）。这里把本机 /file/ 地址解回 file://，
     * Python 见到 file:// 直接 open() 读盘，不管服务器起没起、端口是多少，本地 py 源
     * 都能加载。也符合 0.1.77 的共存要求（本地文件直接读盘、不走 localhost HTTP）。
     * 只认回环地址（127.0.0.1/localhost/::1）上的 /file/ 转发，远程 http 地址原样放过。
     */
    private static String toLocalFile(String api) {
        if (api == null) return null;
        String lower = api.toLowerCase(Locale.ROOT);
        boolean loopback = lower.startsWith("http://127.0.0.1:") || lower.startsWith("http://localhost:") || lower.startsWith("http://[::1]:");
        if (!loopback) return api;
        int fileIdx = api.indexOf("/file/");
        if (fileIdx < 0) return api;
        String encoded = api.substring(fileIdx + "/file/".length());
        if (encoded.isEmpty()) return api;
        String path = Uri.decode(encoded);
        if (!path.startsWith("/")) path = "/" + path;
        return "file://" + path;
    }

    public Object[] proxy(Map<String, String> params) throws Exception {
        Spider spider = findSpider(recent);
        return spider != null ? spider.proxy(params) : null;
    }
}
