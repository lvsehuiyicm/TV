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
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.impl.Callback;
import com.github.catvod.utils.Crypto;
import com.github.catvod.utils.Prefers;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    /** 0.1.127：每个本地接口上次选的首页源 key（iface_last_home_<subId> → site key），持久化，重启还在。 */
    private static final String KEY_LAST_HOME = "iface_last_home_";

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

    /** 拖拽排序后按给定顺序保存全部订阅（顺序本身就是数据，不另建模型）。 */
    public static void saveAllOrder(List<Sub> subs) {
        save(subs);
    }

    public static String getActiveId() {
        return Prefers.getString(KEY_ACTIVE, "");
    }

    /**
     * 0.1.127：记下当前本地接口上次选的首页源（用户亲手选源时调）。只对文件夹 / 单文件
     * 接口记，在线接口不记、不改在线接口的行为（在线接口切过去仍按 0.1.97 的 resetHome
     * 逻辑走第一个源）。没有当前接口、或当前不是本地接口时什么都不做。
     */
    public static void recordLastHomeSite(String siteKey) {
        Sub active = getActiveSub();
        if (active == null || !(active.isFolder() || active.isFile())) return;
        if (TextUtils.isEmpty(siteKey)) return;
        Prefers.put(KEY_LAST_HOME + active.id, siteKey);
    }

    /** 0.1.127：取这个本地接口上次选的首页源 key，没有记录返回 null。 */
    public static String getLastHomeSiteKey(String subId) {
        if (TextUtils.isEmpty(subId)) return null;
        String key = Prefers.getString(KEY_LAST_HOME + subId, "");
        return TextUtils.isEmpty(key) ? null : key;
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
        vodReloadPending = false;
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
        // 0.1.126：文件夹/单文件接口轻量切换：不调 VodConfig.load（不重拉上一个在线接口
        // 的配置），只换本地 sites/home，事件驱动首页刷新。
        else activateLocal(sub, guarded);
    }

    /**
     * 0.1.126：文件夹/单文件接口轻量切换。原来这里走 reloadVod = 重载上一个在线接口的
     * 配置，切本地文件夹也要先等一次在线拉取（约 7 秒），再加 py 首页拉取，才有 17 秒。
     * 现在不加载：先取消可能在飞的上一次加载（免得它后提交覆盖），再按文件夹/文件
     * 从本地脚本里拼 sites/home，发一次事件驱动首页刷新。
     */
    private static void activateLocal(Sub sub, Callback callback) {
        VodConfig.get().cancel();
        List<Site> sites = localSites(sub);
        // 0.1.127：首页用这个接口上次选的源（有记录、且那个源还在列表里才恢复），
        // 没有记录才用第一个。以前这里永远取第一个，切过去就重置。
        Site home = lastHomeSite(sub, sites);
        VodConfig.get().setLocalSites(sites, home);
        ConfigEvent.vod();
        callback.success();
    }

    /** 0.1.127：这个本地接口上次选的首页源（还在列表里才算）；没有记录或源已不在时回第一个。 */
    private static Site lastHomeSite(Sub sub, List<Site> sites) {
        if (sites.isEmpty()) return new Site();
        String key = getLastHomeSiteKey(sub.id);
        if (key != null) {
            for (Site site : sites) if (key.equals(site.getKey())) return site;
        }
        return sites.get(0);
    }

    /** 0.1.126：文件夹/单文件接口自己的源（与 VodConfig.initSite 里本地脚本那段同款过滤/重命名/同步）。 */
    private static List<Site> localSites(Sub sub) {
        List<Site> sites = new ArrayList<>();
        for (Site site : ScriptManager.get().scan()) {
            String path = localPath(site);
            if (path == null) continue;
            if (sub.isFolder() && !path.startsWith(sub.link + "/")) continue;
            if (sub.isFile() && !path.equals(sub.link)) continue;
            sites.add(site);
        }
        sites.removeIf(site -> SiteOrder.isRemoved(site.getKey()) || isScriptDisabled(site) || !isSiteVisible(site));
        sites.forEach(site -> { String name = SiteOrder.getName(site.getKey()); if (name != null) site.setName(name); });
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        sites.forEach(site -> site.sync(items.get(site.getKey())));
        return sites;
    }

    /**
     * 0.1.126：从脚本源的 api 反解本地路径。Site.objectFrom 会把 file:// api 转成本地
     * 服务器 http 地址（/file/ 后面是 URL 编码的路径），直接用 SiteOrder.getLocalPath
     * 会对每个源都重新扫一遍文件夹（131 个源就是 131 次扫盘），这里只做字符串解析；
     * 解析不到再回退到 getLocalPath。
     */
    private static String localPath(Site site) {
        String api = site.getApi();
        if (api != null) {
            int idx = api.indexOf("/file/");
            if (idx >= 0) {
                try {
                    String path = URLDecoder.decode(api.substring(idx + 6), "UTF-8");
                    if (new File(path).isFile()) return path;
                } catch (Exception ignored) {
                }
            }
            if (api.startsWith("file://")) {
                String path = api.substring(7);
                if (new File(path).isFile()) return path;
            }
        }
        return SiteOrder.getLocalPath(site);
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
                        // 0.1.126：自动换源重试时不再叠转圈——先关掉第一次加载的转圈，
                        // 重试静默在后台跑（只留“正在自动换源…”提示），成功/失败再一次性提示。
                        Notify.dismiss();
                        Notify.show("正在自动换源…");
                        setActiveId(next.id);
                        Callback nextGuarded = guardedCallback(next, prevId, tried, attempts + 1, callback);
                        Callback retry = silentStart(nextGuarded);
                        if (next.isInterface()) VodConfig.load(Config.find(next.getConfigUrl(), 0), retry);
                        else reloadVod(retry);
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
        vodReloadPending = false;
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
        vodReloadPending = false;
        VodConfig.load(VodConfig.get().getConfig(), callback);
    }

    /**
     * 0.1.127：开机按当前接口加载。以前开机永远 VodConfig.load(Config.vod())，
     * Config.vod() 是按 time DESC 取的最近成功加载过的在线配置；切文件夹不刷新
     * time，所以当前接口是文件夹时，开机仍先拉一次上一个在线配置的 JSON（超过
     * 10 分钟没用 app，磁盘缓存过期，就要多花 7–15 秒拉一个用不上的配置）。
     * 现在当前接口是文件夹 / 单文件时直接走轻量路径拼本地 sites，不调 VodConfig.load；
     * 是在线接口、没有当前接口、或文件夹被关掉时，走原来的全量加载。
     */
    public static void loadVodOnBoot(Callback callback) {
        Sub active = getActiveSub();
        if (active != null && active.enabled && (active.isFolder() || active.isFile())) {
            VodConfig.get().init();
            activateLocal(active, callback);
        } else {
            VodConfig.get().init().load(callback);
        }
    }

    // 0.1.126：拨开关攒下的待加载。开关只改标志不加载（瞬时），真正的加载推迟到
    // 离开配置页时由 ConfigActivity 一次做掉（回首页生效）。只放内存，进程死了就丢，
    // 开机本来就会按当前接口加载一次，不影响。
    private static boolean vodReloadPending;
    private static boolean liveReloadPending;

    /** 0.1.126：拨开关用的静默回调——不弹转圈（转圈只属于用户主动点的那次加载），失败只弹一句提示。 */
    private static final Callback SILENT = new Callback() {
        @Override
        public void error(String msg) {
            Notify.show(msg);
        }
    };

    /** 取走并清空点播待加载标志（取走一次就消费掉，免得重复加载）。 */
    public static boolean takeVodReloadPending() {
        boolean pending = vodReloadPending;
        vodReloadPending = false;
        return pending;
    }

    /** 取走并清空直播待加载标志。 */
    public static boolean takeLiveReloadPending() {
        boolean pending = liveReloadPending;
        liveReloadPending = false;
        return pending;
    }

    /**
     * 0.1.126：离开配置页时，把拨开关攒下的点播待加载一次做掉（回首页生效）。
     * 无待加载时什么都不做。走 activateVod（带自动换源/回退兜底），当前用的接口
     * 是文件夹时走轻量切换；没有当前接口时回到默认配置。
     */
    public static void flushVodReloadPending() {
        if (!takeVodReloadPending()) return;
        Sub active = getActiveSub();
        if (active != null) activateVod(active, SILENT);
        else VodConfig.load(Config.vod(), SILENT);
    }

    /**
     * 0.1.126：拨开关轻量版——只改 enabled/activeId，不加载（瞬时）。真正的加载
     * 推迟到离开配置页时（flushVodReloadPending）。语义与 activateVod 相同，
     * 只是把加载挪后：拨开一个接口就是切到它，拨开已是当前用的不变。
     */
    public static void activateVodLight(Sub sub) {
        sub.enabled = true;
        update(sub);
        boolean switching = !sub.id.equals(getActiveId());
        if (switching) VodConfig.get().setResetHome(true);
        setActiveId(sub.id);
        vodReloadPending = true;
    }

    /**
     * 0.1.126：拨开关轻量版——只改 enabled/activeId，不加载。关的是当前用的，
     * 就按 deactivateVod 同款逻辑选下一个还开着的接口（没有就回到只有本地源），
     * 加载同样推迟到离开配置页；关的不是当前用的，首页不受影响，不用加载。
     */
    public static void deactivateVodLight(Sub sub) {
        sub.enabled = false;
        update(sub);
        if (!sub.id.equals(getActiveId())) return;
        for (Sub item : getSubs(TAB_VOD)) {
            if (item.isInterface() && item.enabled) {
                setActiveId(item.id);
                vodReloadPending = true;
                return;
            }
        }
        setActiveId("");
        vodReloadPending = true;
    }

    /**
     * 0.1.126：拨开关轻量版（直播）——只改 enabled 和直播配置地址，不加载。
     * 地址先换过来，配置页的“当前使用”绿开关立刻就对；真正的加载推迟到进直播页时
     * （LiveActivity 消费 liveReloadPending）。
     */
    public static void activateLiveLight(Sub sub) {
        sub.enabled = true;
        update(sub);
        LiveConfig.get().config(Config.find(sub.getConfigUrl(), 1));
        liveReloadPending = true;
    }

    /**
     * 0.1.126：拨开关轻量版（直播）——只改 enabled，不加载。按 deactivateLive 同款
     * 逻辑选下一个还开着的直播源（没有就回到默认直播配置），地址先换过来，加载
     * 推迟到进直播页时。
     */
    public static void deactivateLiveLight(Sub sub) {
        sub.enabled = false;
        update(sub);
        for (Sub item : getSubs(TAB_LIVE)) {
            if (item.enabled) {
                LiveConfig.get().config(Config.find(item.getConfigUrl(), 1));
                liveReloadPending = true;
                return;
            }
        }
        LiveConfig.get().config(Config.live());
        liveReloadPending = true;
    }

    /**
     * 0.1.126：自动换源重试用的静默包装——start() 不弹转圈（转圈只盖第一次加载），
     * 其余原样转发。重试在后台跑，成功/失败再一次性提示。
     */
    private static Callback silentStart(Callback callback) {
        return new Callback() {
            @Override
            public void start() {
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
            public void error() {
                callback.error();
            }

            @Override
            public void error(String msg) {
                callback.error(msg);
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(call, e);
            }
        };
    }
}
