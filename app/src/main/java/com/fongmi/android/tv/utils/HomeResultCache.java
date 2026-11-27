package com.fongmi.android.tv.utils;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Result;
import com.github.catvod.utils.Crypto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.1.127：首页结果磁盘缓存。SiteViewModel.homeCache 只放内存，冷启动必 miss，
 * 开机要空白转圈 30 秒。按首页缓存 key（activeId + "|" + homeKey，和内存缓存
 * 同一个 key）把上次的首页 Result（分类 + 推荐列表）存磁盘；开机先显示磁盘里
 * 的上次首页，后台再拉新的，到了再替换。key 不对就读不到（接口 / 源换了就
 * 不能拿旧的顶）。只缓存“拉成功”的结果，失败不写；读失败 / 解析失败返回 null。
 * <p>
 * 序列化走 Result 自带的 Gson 往返（toString() = gson.toJson，objectFrom() =
 * gson.fromJson），transient 字段（auth / direct 等）本来就不进首页结果，不影响。
 * 没有 TTL——首页本来就是“先显示旧的、后台一定再拉一次”，旧多久都由拉新来纠正。
 */
public class HomeResultCache {

    private static final ConcurrentHashMap<String, Result> MEMORY = new ConcurrentHashMap<>();

    /** 取上次的首页结果：内存没有就读磁盘；读不到、解析失败、或结果是空的返回 null。 */
    public static Result get(String key) {
        if (TextUtils.isEmpty(key)) return null;
        Result cached = MEMORY.get(key);
        if (cached != null) return cached;
        File file = file(key);
        if (file == null || !file.isFile()) return null;
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
            Result result = Result.objectFrom(json);
            if (result == null || (result.getTypes().isEmpty() && result.getList().isEmpty())) return null;
            MEMORY.put(key, result);
            return result;
        } catch (Exception e) {
            return null;
        }
    }

    /** 存首页结果：内存 + 磁盘都写。写失败静默（缓存只是加速，不影响加载）。 */
    public static void put(String key, Result result) {
        if (TextUtils.isEmpty(key) || result == null) return;
        MEMORY.put(key, result);
        File file = file(key);
        if (file == null) return;
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(result.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
        }
    }

    private static File file(String key) {
        try {
            File dir = new File(App.get().getCacheDir(), "home_result");
            return new File(dir, Crypto.md5(key) + ".json");
        } catch (Exception e) {
            return null;
        }
    }
}
