package com.fongmi.android.tv.utils;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import okhttp3.Call;

import java.io.IOException;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 配置管理：订阅列表（文件夹 / 单个源 / 在线接口 / 本地包 / 直播源）。
 * 订阅只存在本地（Prefers "config_subs"），不依赖任何内置源。
 */
public class ConfigManager {

    public static final int TAB_VOD = 0;
    public static final int TAB_LIVE = 1;

    public static final String KIND_FOLDER = "folder";
    public static final String KIND_FILE = "file";
    public static final String KIND_URL = "url";
    public static final String KIND_JSON = "json";
    public static final String KIND_LIVE_URL = "live_url";
    public static final String KIND_LIVE_FILE = "live_file";

    private static final String KEY_SUBS = "config_subs";
    private static final String KEY_ACTIVE = "config_active_iface";

    public static class Sub {
        public String id;
        public int tab;
        public String kind;
        public String name;
        public String link;
        public boolean enabled;

        public boolean isFolder() {
            return KIND_FOLDER.equals(kind);
        }

        public boolean isFile() {
            return KIND_FILE.equals(kind);
        }

        public boolean isUrl() {
            return KIND_URL.equals(kind);
        }

        public boolean isJson() {
            return KIND_JSON.equals(kind);
        }

        public boolean isInterface() {
            return isUrl() || isJson();
        }

        public boolean isLiveUrl() {
            return KIND_LIVE_URL.equals(kind);
        }

        public boolean isLiveFile() {
            return KIND_LIVE_FILE.equals(kind);
        }

        /** 加载这个订阅要用的配置地址（在线链接原样，本地文件拼 file://）。 */
        public String getConfigUrl() {
            if (isUrl() || isLiveUrl()) return link;
            return "file://" + link;
        }

        /** 列表里显示的链接（文件夹显示成 folderpack://…，和其他的一样短）。 */
        public String getDisplayLink() {
            if (isFolder()) return "folderpack://" + Crypto.md5(link).substring(0, 12);
            return link;
        }
    }

    public static List<Sub> getAll() {
        try {
            List<Sub> subs = App.gson().fromJson(Prefers.getString(KEY_SUBS), new TypeToken<List<Sub>>() {
            }.getType());
            return subs == null ? new ArrayList<>() : subs;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Sub> getSubs(int tab) {
        List<Sub> result = new ArrayList<>();
        for (Sub sub : getAll()) if (sub.tab == tab) result.add(sub);
        return result;
    }

    public static Sub find(String id) {
        if (id == null) return null;
        for (Sub sub : getAll()) if (id.equals(sub.id)) return sub;
        return null;
    }

    public static Sub add(int tab, String kind, String name, String link) {
        List<Sub> subs = getAll();
        Sub sub = new Sub();
        sub.id = UUID.randomUUID().toString();
        sub.tab = tab;
        sub.kind = kind;
        sub.name = name;
        sub.link = link;
        sub.enabled = true;
        subs.add(sub);
        save(subs);
        if (sub.isFolder()) ScriptManager.get().addFolder(link);
        if (sub.isFile()) ScriptManager.get().addFile(link);
        return sub;
    }

    public static void update(Sub sub) {
        List<Sub> subs = getAll();
        for (int i = 0; i < subs.size(); i++) if (subs.get(i).id.equals(sub.id)) subs.set(i, sub);
        save(subs);
    }

    /** 改本地订阅的地址：同时把 ScriptManager 里登记的旧地址换成新地址，不然源还从旧地址读。 */
    public static void updateLink(Sub sub, String oldLink) {
        update(sub);
        if (TextUtils.equals(oldLink, sub.link)) return;
        if (sub.isFolder()) {
            ScriptManager.get().removeFolder(oldLink);
            ScriptManager.get().addFolder(sub.link);
        } else if (sub.isFile()) {
            ScriptManager.get().removeFile(oldLink);
            ScriptManager.get().addFile(sub.link);
        }
    }

    public static void remove(Sub sub) {
        List<Sub> subs = getAll();
        subs.removeIf(item -> item.id.equals(sub.id));
        save(subs);
        if (sub.isFolder()) ScriptManager.get().removeFolder(sub.link);
        if (sub.isFile()) ScriptManager.get().removeFile(sub.link);
        if (sub.id.equals(getActiveId())) setActiveId("");
    }

    private static void save(List<Sub> subs) {
        Prefers.put(KEY_SUBS, App.gson().toJson(subs));
    }

    /** 0.1.118：首页 tab 拖拽换位置后，只重排 TAB_VOD 的槽位（拖后前 4＋其余 VOD 原顺序），其他 tab 的订阅原位不动。按 id 比对——Sub 没有 override equals，不能用 contains 比引用。 */
    public static void saveVodOrder(List<Sub> visible) {
        List<Sub> all = getAll();
        List<String> visibleIds = new ArrayList<>();
        for (Sub sub : visible) visibleIds.add(sub.id);
        List<Sub> rest = new ArrayList<>();
        for (Sub sub : all) if (sub.tab == TAB_VOD && !visibleIds.contains(sub.id)) rest.add(sub);
        List<Sub> result = new ArrayList<>();
        int index = 0;
        for (Sub sub : all) {
            if (sub.tab != TAB_VOD) result.add(sub);
            else if (index < visible.size()) result.add(visible.get(index++));
            else result.add(rest.remove(0));
        }
        save(result);
    }

    public static String getActiveId() {
        return Prefers.getString(KEY_ACTIVE, "");
    }

    public static void setActiveId(String id) {
        Prefers.put(KEY_ACTIVE, id == null ? "" : id);
    }

    public static Sub getActiveSub() {
        return find(getActiveId());
    }

    /** 这个脚本源是不是被关掉的订阅里的（文件夹或单文件被关掉时，它的源不显示）。 */
    public static boolean isScriptDisabled(Site site) {
        String path = SiteOrder.getLocalPath(site);
        if (path == null) return false;
        List<Sub> matches = new ArrayList<>();
        for (Sub sub : getSubs(TAB_VOD)) {
            if (sub.isFile() && sub.link.equals(path)) matches.add(sub);
            else if (sub.isFolder() && path.startsWith(sub.link + "/")) matches.add(sub);
        }
        if (matches.isEmpty()) return false;
        for (Sub sub : matches) if (sub.enabled) return false;
        return true;
    }

    /** 当前接口下，这个源要不要显示（""=全部接口，都显示）。本地脚本源永远显示：接口只增加它自己的源，不藏起用户自己的 py/js 源；否则导入一个在线接口后，本地源会从列表里消失。 */
    public static boolean isSiteVisible(Site site) {
        Sub active = getActiveSub();
        if (active == null || active.tab != TAB_VOD) return true;
        String path = SiteOrder.getLocalPath(site);
        if (active.isFolder()) return path != null && path.startsWith(active.link + "/");
        if (active.isFile()) return path != null && path.equals(active.link);
        if (active.isInterface()) return true;
        return true;
    }

    /** 当前接口的名字（主页上显示），没有选就是“全部接口”。 */
    public static String getActiveName() {
        Sub active = getActiveSub();
        return active == null ? null : active.name;
    }

    /** 打开一个点播订阅：在线接口/本地包会加载成当前配置，文件夹/单文件会切成当前接口；开关互不影响，多个接口可以同时开着。 */
    public static void activateVod(Sub sub, Callback callback) {
        sub.enabled = true;
        update(sub);
        // 0.1.96：先记下旧接口 id。之前这里先 setActiveId 再加载，新接口加载失败时
        // app 已经切到坏配置上（VodConfig 也被 clear 成新配置），只剩“配置信息初始化
        // 失败，请重启APP”，必须重启或手动切回去才能恢复。现在包一层回调：加载失败时
        // 把 activeId 恢复成旧接口、重新加载旧配置，用户留在原来能用的接口上。
        String prevId = getActiveId();
        // 0.1.97：真在切换接口（不是重复点当前接口）时，让加载完成后把首页重置到
        // 新接口自己的第一个源。不然首页源还是存着的旧首页（常是跨接口共享的本地
        // py 源），顶栏字变了、首页内容还是原来的——“切换后还是显示原来的接口”。
        boolean switching = !sub.id.equals(prevId);
        if (switching) VodConfig.get().setResetHome(true);
        setActiveId(sub.id);
        // 0.1.111：切接口诊断。时间看 logcat 自带时间戳；resetHome 与 switching 同值
        //（切换时置 true，重复点当前接口时不置）。
        Log.i("VodSwitch", "activate target=" + sub.name + " prevId=" + prevId + " switching=" + switching);
        Callback guarded = guardedCallback(sub, prevId, new java.util.HashSet<>(), 0, callback);
        if (sub.isInterface()) VodConfig.load(Config.find(sub.getConfigUrl(), 0), guarded);
        else reloadVod(guarded);
    }

    /**
     * 0.1.104：切接口的保护回调。加载失败时，先看失败类型：是域名解析失败、
     * 源拒绝访问、连接超时这三种，就自动试下一个开着的接口（最多 3 次，弹
     * “正在自动换源…”）；试完都不行、或失败不是这三种，就走原来的回退逻辑
     * （恢复旧接口、重载旧配置、拼报错）。已试过的接口记在 tried 里，防 A→B→A 循环。
     */
    private static Callback guardedCallback(Sub sub, String prevId, java.util.Set<String> tried, int attempts, Callback callback) {
        return new Callback() {
            @Override
            public void error(String msg) {
                // 0.1.111：切接口失败诊断（回退/自动换源前）。回退目标见下面 setActiveId(prevId)
                // 后的重载；自动换源时下面会再记一条 activate。
                String shortMsg = msg == null ? "" : msg.length() > 120 ? msg.substring(0, 120) : msg;
                Log.i("VodSwitch", "error target=" + sub.name + " prevId=" + prevId + " attempts=" + attempts + " msg=" + shortMsg);
                if (isAutoSwitchable(msg) && attempts < 3) {
                    Sub next = nextInterface(tried, sub);
                    if (next != null) {
                        tried.add(next.id);
                        Notify.show("正在自动换源…");
                        setActiveId(next.id);
                        Callback nextGuarded = guardedCallback(next, prevId, tried, attempts + 1, callback);
                        if (next.isInterface()) VodConfig.load(Config.find(next.getConfigUrl(), 0), nextGuarded);
                        else reloadVod(nextGuarded);
                        return;
                    }
                }
                setActiveId(prevId);
                Sub prev = find(prevId);
                if (prev != null && prev.isInterface()) VodConfig.load(Config.find(prev.getConfigUrl(), 0), callback);
                else VodConfig.load(Config.vod(), callback);
                // 0.1.97：失败要说清楚，不能只透传源里的原始报错（比如“配置信息初始
                // 化失败，请重启APP”）。点名新接口 X 和回退到的旧接口，让用户知道
                // 这次切换没成功、现在显示的是哪个接口；恢复旧接口的加载走上面那行，
                // 成功后首页事件链会把顶栏和内容都刷回旧接口。
                String reason = msg == null ? "" : msg;
                int nl = reason.indexOf('\n');
                if (nl >= 0) reason = reason.substring(nl + 1).trim();
                if (reason.length() > 80) reason = reason.substring(0, 80) + "…";
                String prevName = prev == null ? "" : prev.name;
                callback.error("切换到「" + sub.name + "」失败" + (reason.isEmpty() ? "" : "：" + reason) + (prevName.isEmpty() ? "" : "，仍显示「" + prevName + "」"));
            }

            @Override
            public void error() {
                error("");
            }

            @Override
            public void success() {
                callback.success();
            }

            @Override
            public void success(String result) {
                callback.success(result);
            }

            @Override
            public void start() {
                callback.start();
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(call, e);
            }
        };
    }

    /** 只认三种值得自动换源的失败：域名解析失败、源拒绝访问（403）、连接超时。 */
    private static boolean isAutoSwitchable(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase();
        return m.contains("unable to resolve host") || m.contains("unknownhost") || m.contains("域名解析失败")
                || m.contains("403") || m.contains("forbidden") || m.contains("拒绝访问")
                || m.contains("timeout") || m.contains("timed out") || m.contains("连接超时");
    }

    /** 找下一个开着的接口：是接口、已启用、不是刚失败的、没试过。按订阅列表顺序取第一个。 */
    private static Sub nextInterface(java.util.Set<String> tried, Sub failed) {
        for (Sub item : getAll()) {
            if (!item.isInterface() || !item.enabled) continue;
            if (item.id.equals(failed.id) || tried.contains(item.id)) continue;
            return item;
        }
        return null;
    }

    /** 关掉一个点播订阅：是在用的，就切到另一个还开着的接口；没有就回到只有本地源。 */
    public static void deactivateVod(Sub sub, Callback callback) {
        sub.enabled = false;
        update(sub);
        boolean wasActive = sub.id.equals(getActiveId());
        if (wasActive) {
            for (Sub item : getSubs(TAB_VOD)) {
                if (item.isInterface() && item.enabled) {
                    setActiveId(item.id);
                    VodConfig.load(Config.find(item.getConfigUrl(), 0), callback);
                    return;
                }
            }
            setActiveId("");
        }
        if (sub.isInterface() && wasActive) VodConfig.load(Config.vod(), callback);
        else reloadVod(callback);
    }

    /** 打开一个直播源；开关互不影响，多个可以同时开着（最后打开的是当前用的）。 */
    public static void activateLive(Sub sub, Callback callback) {
        sub.enabled = true;
        update(sub);
        LiveConfig.load(Config.find(sub.getConfigUrl(), 1), callback);
    }

    /** 关掉一个直播源：还有开着的就切过去，没有就回到默认直播配置。 */
    public static void deactivateLive(Sub sub, Callback callback) {
        sub.enabled = false;
        update(sub);
        for (Sub item : getSubs(TAB_LIVE)) {
            if (item.enabled) {
                LiveConfig.load(Config.find(item.getConfigUrl(), 1), callback);
                return;
            }
        }
        LiveConfig.load(Config.live(), callback);
    }

    /** 用当前配置重新加载一次（接口切换、开关文件夹后刷新主页）。 */
    public static void reloadVod(Callback callback) {
        VodConfig.load(VodConfig.get().getConfig(), callback);
    }
}
