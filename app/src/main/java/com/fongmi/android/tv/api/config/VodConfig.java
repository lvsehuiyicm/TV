package com.fongmi.android.tv.api.config;

import android.text.TextUtils;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.node.NodeRuntime;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Depot;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Rule;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.utils.ConfigJsonCache;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ParseStore;
import com.fongmi.android.tv.utils.ScriptManager;
import com.fongmi.android.tv.utils.SiteOrder;
import com.fongmi.android.tv.utils.UrlUtil;
import com.github.catvod.bean.Doh;
import com.github.catvod.bean.Header;
import com.github.catvod.bean.Proxy;
import com.github.catvod.runtime.NodeRuntimeApi;
import com.github.catvod.utils.Decoder;
import com.github.catvod.utils.Json;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VodConfig extends BaseConfig {

    private static final String TAG = VodConfig.class.getSimpleName();

    private Site home;
    private String wall;
    private Parse parse;
    private List<Doh> doh;
    private List<Rule> rules;
    private List<Site> sites;
    private List<String> ads;
    private List<String> flags;
    private List<Parse> parses;
    private List<Parse> configParses;

    /**
     * 0.1.97：切换接口时把首页重置到新接口自己的第一个源（不是存着的旧首页）。
     * <p>
     * 根因（“切换后还是显示原来的接口”）：首页内容由首页源（home）决定，而 home 是按
     * 接口存着的（Config.getHome()）。用户常把本地 py 源（比如 4K河豚[py]）当首页源，
     * 这个 py 源在所有接口下都可见（0.1.91 起本地脚本源永不隐藏），所以切到 RRZY/
     * 影视py 后首页源还是同一个 py 源，首页内容一点没变——顶栏字变了，内容还是原来的。
     * 只有在真正“切换接口”（activateVod 切到另一个订阅）时才置位，加载完成后消费掉；
     * 平时重载/重启不置位，存着的首页源照常用，不打扰用户。
     */
    private boolean resetHome;

    /** 切换接口时调用：下一次加载完成后把首页重置到新接口自己的第一个源。 */
    public void setResetHome(boolean resetHome) {
        this.resetHome = resetHome;
    }

    /**
     * 0.1.108：一次加载算出的新状态，先放暂存里，确认是最新一次点击才提交。
     * 根因（“切换过去不是我点的接口”）：旧一次加载的 parse 直接往单例字段写，
     * 它跑完时新一次点击可能已经改了配置，新接口的数据会被旧接口的覆盖。
     * 暂存 + 提交时判新旧，保证最后显示的永远是最后一次点的接口。
     */
    private static class Staged {
        List<Site> sites;
        Site home;
        Parse parse;
        List<Parse> parses;
        List<Parse> configParses;
        String wall;
        List<Doh> doh;
        List<Rule> rules;
        List<String> flags;
        List<String> ads;
        boolean resetHomeUsed;
    }

    public static VodConfig get() {
        return Loader.INSTANCE;
    }

    public static int getCid() {
        return get().getConfig().getId();
    }

    public static String getUrl() {
        return get().getConfig().getUrl();
    }

    public static String getDesc() {
        return get().getConfig().getDesc();
    }

    public static int getHomeIndex() {
        return get().getSites().indexOf(get().getHome());
    }

    public static boolean hasParse() {
        return !get().getParses().isEmpty();
    }

    /**
     * 0.1.103：加载失败不再清空能用的配置。之前这里先 clear 再加载，新配置加载失败时
     * app 剩个空壳（源列表被清掉），只能重启。现在加载前先快照当前能用的状态，失败时
     * 恢复快照，app 留在旧接口上，首页照常显示，不用重启。
     */
    public static void load(Config config, Callback callback) {
        VodConfig vod = get();
        List<Site> oldSites = vod.sites;
        Site oldHome = vod.home;
        Parse oldParse = vod.parse;
        List<Parse> oldParses = vod.parses;
        List<Parse> oldConfigParses = vod.configParses;
        String oldWall = vod.wall;
        List<Doh> oldDoh = vod.doh;
        List<Rule> oldRules = vod.rules;
        List<String> oldAds = vod.ads;
        List<String> oldFlags = vod.flags;
        Config oldConfig = vod.getConfig();
        vod.clear().config(config).load(new Callback() {
            @Override
            public void error(String msg) {
                vod.sites = oldSites;
                vod.home = oldHome;
                vod.parse = oldParse;
                vod.parses = oldParses;
                vod.configParses = oldConfigParses;
                vod.wall = oldWall;
                vod.doh = oldDoh;
                vod.rules = oldRules;
                vod.ads = oldAds;
                vod.flags = oldFlags;
                vod.config(oldConfig);
                callback.error(msg);
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
        });
    }

    /**
     * 0.1.126：文件夹/单文件接口轻量切换用的本地 sites/home 替换。只换数据，不发事件
     *（事件由调用方发一次），不碰 parses/rules/doh 等在线配置的字段。resetHome 在这里
     * 消费掉（已经按新接口选好首页了），不留给下一次加载再重置一次。
     */
    public void setLocalSites(List<Site> sites, Site home) {
        this.sites = sites == null ? Collections.emptyList() : sites;
        resetHome = false;
        setHome(getConfig(), home == null ? new Site() : home, true);
    }

    public VodConfig init() {
        return config(Config.vod());
    }

    public VodConfig config(Config config) {
        synchronized (this) {
            this.config = config;
        }
        return this;
    }

    public VodConfig clear() {
        return clear(false);
    }

    public void destroy() {
        clear(true);
    }

    private VodConfig clear(boolean exiting) {
        ads = null;
        doh = null;
        home = null;
        wall = null;
        parse = null;
        sites = null;
        flags = null;
        rules = null;
        parses = null;
        configParses = null;
        if (exiting) BaseLoader.get().destroy();
        else BaseLoader.get().clear();
        RuleConfig.get().invalidate();
        return this;
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected Config defaultConfig() {
        return Config.vod();
    }

    @Override
    protected void cancelLoad() {
        NodeRuntime.get().cancelLoad();
    }

    @Override
    protected void postEvent() {
        super.postEvent();
        ConfigEvent.vod();
    }

    @Override
    protected void load(Config config) throws Throwable {
        if (config.isEmpty()) {
            commitIfCurrent(config, parseConfig(config, new JsonObject()));
            return;
        }
        boolean node = NodeRuntime.isConfig(config.getUrl());
        NodeRuntime runtime = NodeRuntime.get();
        if (!node) runtime.clear();
        NodeRuntimeApi.LoadedConfig loaded = null;
        try {
            if (node) loaded = runtime.loadConfig(config.getUrl());
            String json = node ? loaded.json() : isLocalFile(config.getUrl()) ? readLocalText(config.getUrl()) : getConfigJson(config.getUrl());
            runtime.prune(loaded, Config.getAll(VOD).stream().map(Config::getUrl).toList(), config.getUrl());
            Staged staged = checkJson(config, Json.parse(json).getAsJsonObject(), loaded);
            if (staged != null) commitIfCurrent(config, staged);
        } catch (Throwable e) {
            if (node) runtime.fail(loaded);
            throw e;
        }
    }

    /**
     * 0.1.126：在线接口的配置 JSON 先查缓存（内存 + 磁盘，10 分钟有效），命中直接用，
     * 不再重新拉；失效才联网拉，拉成功写缓存。拉失败抛异常，走原有失败兜底（0.1.103）。
     */
    private static String getConfigJson(String url) throws Throwable {
        String fetchUrl = UrlUtil.convert(url);
        String cached = ConfigJsonCache.get(fetchUrl);
        if (cached != null) return cached;
        String json = Decoder.getJson(fetchUrl, TAG);
        ConfigJsonCache.put(fetchUrl, json);
        return json;
    }

    /**
     * 0.1.108：同一个配置的不同对象（Room 每次查库都新建实例）要认作同一个接口，
     * 用 URL + 类型判等，不用引用相等，也不用 id（未入库的配置 id 都是 0 会误判）。
     */
    private static boolean isSameConfig(Config a, Config b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getType() == b.getType() && TextUtils.equals(a.getUrl(), b.getUrl());
    }

    /**
     * 0.1.108：只有本次加载的配置仍是当前配置（最后一次点击的那个）才提交；
     * 已有更新的点击时直接丢弃本次解析结果，不碰单例字段。判-提交与改配置互斥。
     */
    private void commitIfCurrent(Config config, Staged staged) {
        synchronized (this) {
            // 0.1.111：切接口诊断。提交时记最终首页源 key（selectHomeSite 刚算出来的），
            // 丢弃时记被谁顶掉（当前配置已是更新的点击）。时间看 logcat 自带时间戳。
            if (!isSameConfig(config, this.config)) {
                Log.i("VodSwitch", "discard url=" + config.getUrl() + " currentUrl=" + (this.config == null ? "" : this.config.getUrl()));
                return;
            }
            Log.i("VodSwitch", "commit url=" + config.getUrl() + " homeKey=" + (staged.home == null ? "null" : staged.home.getKey()) + " resetHomeUsed=" + staged.resetHomeUsed);
            commit(staged);
        }
    }

    private void commit(Staged staged) {
        sites = staged.sites;
        configParses = staged.configParses;
        parses = staged.parses;
        doh = staged.doh;
        rules = staged.rules;
        flags = staged.flags;
        ads = staged.ads;
        wall = staged.wall;
        RuleConfig.get().invalidate();
        if (staged.resetHomeUsed) resetHome = false;
        setHome(getConfig(), staged.home, false);
        setParse(getConfig(), staged.parse, false);
    }

    @Override
    protected boolean isLoaded() {
        return !getSites().isEmpty();
    }

    private Staged checkJson(Config config, JsonObject object, NodeRuntimeApi.LoadedConfig loaded) throws Throwable {
        if (object.has("msg")) throw new Exception(object.get("msg").getAsString());
        if (object.has("urls")) {
            List<Depot> items = Depot.arrayFrom(object.getAsJsonArray("urls").toString());
            if (items.isEmpty()) throw new Exception("Depot urls is empty");
            if (loaded != null) NodeRuntime.get().accept(loaded);
            parseDepot(config, items);
            return null;
        }
        Staged staged = parseConfig(config, object);
        if (loaded != null) NodeRuntime.get().accept(loaded);
        return staged;
    }

    private void parseDepot(Config config, List<Depot> items) throws Throwable {
        List<Config> configs = new ArrayList<>();
        for (Depot item : items) configs.add(Config.find(item, VOD));
        Config first = configs.get(0);
        config(first);
        load(first);
        Config.delete(config.getUrl());
    }

    private Staged parseConfig(Config config, JsonObject object) {
        Staged staged = new Staged();
        initList(object, staged);
        initLive(config, object);
        initWall(config, object, staged);
        initSite(config, object, staged);
        initParse(config, object, staged);
        config.setLogo(Json.safeString(object, "logo"));
        config.setAssrt(Json.safeString(object, "assrt"));
        config.setNotice(Json.safeString(object, "notice"));
        config.setDanmaku(Json.safeString(object, "danmaku"));
        return staged;
    }

    private void initList(JsonObject object, Staged staged) {
        setHeaders(Header.arrayFrom(fetchArray(object, "headers")));
        setProxy(Proxy.arrayFrom(fetchArray(object, "proxy")));
        staged.rules = Rule.arrayFrom(fetchArray(object, "rules"));
        staged.doh = Doh.arrayFrom(fetchArray(object, "doh"));
        staged.flags = Json.safeListString(object, "flags");
        setHosts(Json.safeListString(object, "hosts"));
        staged.ads = Json.safeListString(object, "ads");
    }

    private void initLive(Config config, JsonObject object) {
        if (Json.isEmpty(object, "lives")) return;
        Config temp = Config.find(config, LIVE).save();
        boolean sync = LiveConfig.get().needSync(config.getUrl());
        if (sync) LiveConfig.get().config(temp.update()).parse(object);
    }

    private void initWall(Config config, JsonObject object, Staged staged) {
        if (Json.isEmpty(object, "wallpaper")) return;
        staged.wall = Json.safeString(object, "wallpaper");
        Config temp = Config.find(staged.wall, config.getName(), WALL).save();
        boolean sync = WallConfig.get().needSync(staged.wall);
        if (sync) WallConfig.get().config(temp.update());
    }

    private void initSite(Config config, JsonObject object, Staged staged) {
        String base = getResolveBase(config);
        String spider = UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(base, Json.safeString(object, "spider")));
        BaseLoader.get().parseJar(spider, true);
        List<Site> sites = Json.safeListElement(object, "sites").stream().map(e -> Site.objectFrom(e, spider)).distinct().collect(Collectors.toCollection(ArrayList::new));
        for (Site site : sites) {
            site.setApi(UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(base, site.getApi())));
            site.setExt(UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(base, site.getExt())));
            // 0.1.110 A.1：站点 jar 也要相对配置地址解析（与 api/ext 同法）。
            // 原来只解析 api/ext，jar 字段从未解析，相对路径（./jar/xbpq.jar 等）
            // 原样进了 JarLoader，isLocalFile 永远 false，报“不支持的 JAR 位置”，
            // loader 留空 → 点站即“爬虫加载失败”。解析成 file:// 后自然走进
            // JarLoader 已有的 file 分支（copyLocalJar），加载逻辑本身不动。
            site.setJar(UrlUtil.resolveEncodedFile(UrlUtil.resolveRelative(base, site.getJar())));
        }
        Set<String> keys = sites.stream().map(Site::getKey).collect(Collectors.toSet());
        ScriptManager.get().scan().stream().filter(site -> keys.add(site.getKey())).forEach(site -> sites.add(site));
        sites.removeIf(site -> SiteOrder.isRemoved(site.getKey()) || ConfigManager.isScriptDisabled(site) || !ConfigManager.isSiteVisible(site));
        sites.forEach(site -> { String name = SiteOrder.getName(site.getKey()); if (name != null) site.setName(name); });
        Map<String, Site> items = Site.findAll().stream().collect(Collectors.toMap(Site::getKey, Function.identity()));
        sites.forEach(site -> site.sync(items.get(site.getKey())));
        staged.sites = sites;
        staged.home = selectHomeSite(config, sites, staged);
    }

    /**
     * 0.1.128：相对路径的解析基准。平时就是配置自己的地址；配置是缓存复制的
     * （导入时没权限、SAF 解析不到真实路径，wo.json 被拷到 app 缓存），且订阅
     * 上记着源目录时，改按源目录解析——缓存目录里没有 py/js/jar，按它解析
     * 全是 404，首页源就一直“爬虫加载失败”。源目录是导入时从挑文件的 Uri
     * 的 docId 里反推的（FileChooser.getSourceDir）；没记着、或不是缓存复制
     * 的配置时，走原来的路，一点不动。
     */
    private String getResolveBase(Config config) {
        ConfigManager.Sub sub = ConfigManager.findSubByUrl(config.getUrl());
        if (sub == null || TextUtils.isEmpty(sub.sourceDir)) return config.getUrl();
        String cache = FileChooser.getCacheDir().getAbsolutePath();
        if (!config.getUrl().startsWith("file://" + cache + "/")) return config.getUrl();
        return "file://" + sub.sourceDir + "/";
    }

    /**
     * 0.1.97：选首页源。平时（重载/重启）按存着的首页源恢复；切换接口时（resetHome），
     * 忽略存着的首页源（它常是跨接口共享的本地 py 源，恢复它等于没切），改用新接口
     * 自己的第一个源——接口自己的源排在前（config 的 sites 在先、脚本源后加），
     * 所以第一个“非本地脚本”源就是该接口自己的源；文件夹接口下过滤后只剩文件夹
     * 自己的源，直接取第一个。resetHome 一次性，选完就清掉。
     * 0.1.108：resetHome 只在提交时清（staged.resetHomeUsed），过期加载丢弃时
     * 不消费它，免得新一次切换拿不到重置标志。
     */
    private Site selectHomeSite(Config config, List<Site> sites, Staged staged) {
        if (sites.isEmpty()) return new Site();
        if (!resetHome) {
            return sites.stream().filter(item -> item.getKey().equals(config.getHome())).findFirst().orElse(sites.get(0));
        }
        staged.resetHomeUsed = true;
        for (Site site : sites) if (SiteOrder.getLocalPath(site) == null) return site;
        return sites.get(0);
    }

    private void initParse(Config config, JsonObject object, Staged staged) {
        staged.configParses = Json.safeListElement(object, "parses").stream().map(Parse::objectFrom).distinct().collect(Collectors.toCollection(ArrayList::new));
        List<Parse> items = buildParses(staged.configParses);
        if (!items.isEmpty()) items.add(0, Parse.god());
        staged.parses = items;
        staged.parse = staged.parses.isEmpty() ? new Parse() : staged.parses.stream().filter(item -> item.getName().equals(config.getParse())).findFirst().orElse(staged.parses.get(0));
    }

    private List<Parse> buildParses(List<Parse> configParses) {
        List<Parse> items = new ArrayList<>(configParses == null ? Collections.emptyList() : configParses);
        for (Parse item : ParseStore.getUserParses()) if (!items.contains(item)) items.add(item);
        // 0.1.104：接口没给 parses、用户也没加过时，补 4 条自带解析线兜底，
        // 新装的烛龙也能解析；给了就不用，不抢用户自己的配置。
        if (items.isEmpty()) items.addAll(Parse.builtin());
        return items;
    }

    private void rebuildParses() {
        setParses(buildParses(configParses));
    }

    public void updateParses() {
        rebuildParses();
        if (getConfig() == null) return;
        Parse current = getParse();
        Parse selected = getParses().isEmpty() ? new Parse() : getParses().stream().filter(item -> item.getName().equals(current.getName())).findFirst().orElse(getParses().get(0));
        setParse(getConfig(), selected, !selected.getName().equals(current.getName()));
    }

    public List<Site> getSites() {
        return sites == null ? Collections.emptyList() : sites;
    }

    public List<Parse> getParses() {
        return parses == null ? Collections.emptyList() : parses;
    }

    private void setParses(List<Parse> parses) {
        if (!parses.isEmpty()) parses.add(0, Parse.god());
        this.parses = parses;
    }

    public List<Doh> getDoh() {
        List<Doh> items = Doh.get(App.get());
        if (doh == null) return items;
        items.removeAll(doh);
        items.addAll(doh);
        return items;
    }

    public List<Rule> getRules() {
        return rules == null ? Collections.emptyList() : rules;
    }

    public List<Parse> getParses(int type) {
        return getParses().stream().filter(item -> item.getType() == type).toList();
    }

    public List<Parse> getParses(int type, String flag) {
        List<Parse> items = getParses(type);
        List<Parse> filter = items.stream().filter(item -> item.getExt().getFlag().contains(flag)).toList();
        return filter.isEmpty() ? items : filter;
    }

    public List<String> getFlags() {
        return flags == null ? Collections.emptyList() : flags;
    }

    public List<String> getAds() {
        return ads == null ? Collections.emptyList() : ads;
    }

    public Parse getParse() {
        return parse == null ? new Parse() : parse;
    }

    public void setParse(Parse parse) {
        setParse(getConfig(), parse, true);
    }

    public Site getHome() {
        return home == null ? new Site() : home;
    }

    public void setHome(Site site) {
        setHome(getConfig(), site, true);
        // 0.1.127：用户亲手选了首页源，记下来（只对本地接口记，见 ConfigManager）。
        // 走这个公开入口的都是用户主动选源（首页切源弹窗、设置页、删源后改首页源），
        // 加载内部走的是 setHome(Config, Site, boolean)，不经过这里，不会把加载时
        // 恢复的默认源误记成“上次选的”。
        ConfigManager.recordLastHomeSite(site == null ? null : site.getKey());
        RefreshEvent.home();
    }

    public String getWall() {
        return TextUtils.isEmpty(wall) ? "" : wall;
    }

    public Parse getParse(String name) {
        return getParses().stream().filter(item -> item.getName().equals(name)).findFirst().orElse(new Parse());
    }

    public Site getSite(String key) {
        return getSites().stream().filter(item -> item.getKey().equals(key)).findFirst().orElse(new Site());
    }

    private void setParse(Config config, Parse parse, boolean save) {
        this.parse = parse;
        this.parse.setSelected(true);
        config.setParse(parse.getName());
        getParses().forEach(item -> item.setSelected(parse));
        if (save) config.save();
    }

    private void setHome(Config config, Site site, boolean save) {
        home = site;
        home.setSelected(true);
        config.setHome(home.getKey());
        if (save) config.save();
        getSites().forEach(item -> item.setSelected(home));
    }

    private static class Loader {
        static volatile VodConfig INSTANCE = new VodConfig();
    }
}
