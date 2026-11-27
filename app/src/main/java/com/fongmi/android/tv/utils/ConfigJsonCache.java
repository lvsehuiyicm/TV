package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.github.catvod.utils.Crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.1.126：在线接口配置 JSON 缓存。切接口每次都重新拉配置 JSON，慢 VPN 上
 * 一个 100KB+ 的配置就要 7–15 秒，而且没有任何缓存。按 URL 缓存（内存 + 磁盘，
 * 10 分钟有效），命中直接走解析，不再重新拉；失效后重新拉。
 * <p>
 * 注意：只缓存“拉成功”的 JSON。拉失败不写缓存、也不读过期缓存，失败仍走原有
 * 兜底（0.1.103 加载失败留旧配置）；缓存 JSON 解析失败同样走失败兜底。
 */
public class ConfigJsonCache {

    /** 配置 JSON 缓存有效期：10 分钟。 */
    private static final long TTL = 10 * 60 * 1000L;

    private static final ConcurrentHashMap<String, Entry> MEMORY = new ConcurrentHashMap<>();

    private static class Entry {
        final String json;
        final long time;

        Entry(String json, long time) {
            this.json = json;
            this.time = time;
        }
    }

    /** 取缓存的配置 JSON：命中且未过期才返回，否则返回 null（调用方重新拉）。 */
    public static String get(String url) {
        if (TextUtils.isEmpty(url)) return null;
        long now = System.currentTimeMillis();
        Entry entry = MEMORY.get(url);
        if (entry != null && now - entry.time < TTL) return entry.json;
        if (entry != null) MEMORY.remove(url);
        File file = file(url);
        if (file == null || !file.isFile()) return null;
        if (now - file.lastModified() >= TTL) return null;
        try (FileInputStream in = new FileInputStream(file)) {
            byte[] bytes = new byte[(int) file.length()];
            int read = 0;
            while (read < bytes.length) {
                int n = in.read(bytes, read, bytes.length - read);
                if (n < 0) break;
                read += n;
            }
            String json = new String(bytes, 0, read, StandardCharsets.UTF_8);
            if (TextUtils.isEmpty(json)) return null;
            MEMORY.put(url, new Entry(json, now));
            return json;
        } catch (Exception e) {
            return null;
        }
    }

    /** 存缓存的配置 JSON：内存 + 磁盘都写。写失败静默（缓存只是加速，不影响加载）。 */
    public static void put(String url, String json) {
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(json)) return;
        MEMORY.put(url, new Entry(json, System.currentTimeMillis()));
        File file = file(url);
        if (file == null) return;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static File file(String url) {
        try {
            File dir = new File(App.get().getCacheDir(), "config_json");
            return new File(dir, Crypto.md5(url) + ".json");
        } catch (Exception e) {
            return null;
        }
    }
}
