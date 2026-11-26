package com.fongmi.android.tv.api;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.loader.BaseLoader;
import com.fongmi.android.tv.api.parser.EpgParser;
import com.fongmi.android.tv.api.parser.LiveParser;
import com.fongmi.android.tv.bean.Channel;
import com.fongmi.android.tv.bean.Epg;
import com.fongmi.android.tv.bean.EpgData;
import com.fongmi.android.tv.bean.Group;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.extractor.Source;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.net.OkHttp;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class LiveApi {

    /** 直播预检超时：每条线最多等 3 秒（死链一般立刻回 404，慢网也够回 1KB 播放列表）。 */
    private static final long PRECHECK_TIMEOUT_MS = 3000;
    private static final int PRECHECK_MAX_BYTES = 1024;

    public static void parse(@NonNull Live item) throws Exception {
        LiveParser.start(item.recent());
        item.getGroups().removeIf(Group::isEmpty);
        if (item.getGroups().isEmpty() || item.getGroups().get(0).isKeep()) return;
        item.getGroups().add(0, Group.create(R.string.keep));
        LiveConfig.get().applyKeepsToGroups(item.getGroups());
    }

    public static boolean parseXml(@NonNull Live item) {
        boolean parsed = false;
        for (String url : item.getEpgXml()) parsed |= startXml(item, url);
        return parsed;
    }

    @NonNull
    public static Epg getEpg(@NonNull Channel item, @NonNull ZoneId zoneId) {
        for (int offset = -1; offset <= 1; offset++) fetchEpgDay(item, zoneId, offset);
        return item.getData(zoneId).selected();
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item) throws Exception {
        Result result = resolveUrl(item);
        if (!result.hasMsg()) item.applyAuth(result);
        return result;
    }

    @NonNull
    public static Result getUrl(@NonNull Channel item, @NonNull EpgData data) throws Exception {
        Result result = resolveUrl(item);
        if (result.hasMsg()) return result;
        if (Source.isPush(result.getUrl().v())) return result;
        result.setTimeshift(null);
        result.setUrl(item.getCatchup().format(result.getRealUrl(), data));
        BaseLoader.get().requireProxy(result.getRealUrl());
        if (item.isRtsp()) result.getHeader().put("rtsp_range", data.getRange());
        item.applyAuth(result);
        return result;
    }

    /**
     * 逐条试当前频道的线路：直链先快速预检（404/超时/非视频直接跳下一条），
     * 需要解析的线原样放行（由 ParseJob 解析，解析遇 Cloudflare 验证页时直播直接判失败，见 ParseWebView），
     * 全部线路都死才返回一条带明确提示的错误结果，不交给播放器空转。
     * 循环最多试 total 条（从当前线路起，switchLine 会绕回），不会无限转圈。
     */
    private static Result resolveUrl(Channel item) throws Exception {
        Source.get().stop();
        int total = item.getUrls().size();
        for (int attempt = 0; attempt < total; attempt++) {
            Result result;
            try {
                result = item.result();
                result.setUrl(Source.get().fetch(result));
            } catch (Exception e) {
                if (Thread.currentThread().isInterrupted()) throw e;
                if (attempt + 1 < total) {
                    item.switchLine(true);
                    continue;
                }
                throw e;
            }
            if (TextUtils.isEmpty(result.getRealUrl())) {
                if (attempt + 1 < total) {
                    item.switchLine(true);
                    continue;
                }
                return Result.error(ResUtil.getString(R.string.error_live_lines));
            }
            if (result.needParse() || result.isUseParse()) return result;
            String realUrl = result.getRealUrl();
            if (isUnplayableScheme(realUrl) || isHttp(realUrl) && !isLineAlive(realUrl, result.getHeader())) {
                if (attempt + 1 < total) {
                    item.switchLine(true);
                    continue;
                }
                return Result.error(ResUtil.getString(R.string.error_live_lines));
            }
            return result;
        }
        return Result.error(ResUtil.getString(R.string.error_live_lines));
    }

    /** Exo 播不了的协议：rtmp/migu/htmlplay/udp，直接跳过（MPV 下再说）。rtsp 留着，Exo 有 RTSP 模块。 */
    private static boolean isUnplayableScheme(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("rtmp://") || lower.startsWith("migu://") || lower.startsWith("htmlplay://") || lower.startsWith("udp://");
    }

    private static boolean isHttp(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 直链快速预检：短超时 Range GET（最多读 1KB），跟随跳转（走 OkHttp 自带的 ProxyRedirectInterceptor）。
     * 2xx 且不是 HTML 错误页/CF 验证页才算活；404/403/超时/异常都算死。
     * 拿不准的类型（2xx、无特征）放行，让播放器自己试，不误杀。
     */
    private static boolean isLineAlive(String url, Map<String, String> headers) {
        Request.Builder builder = new Request.Builder().url(url).header("Range", "bytes=0-" + (PRECHECK_MAX_BYTES - 1));
        if (headers != null) for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;
            builder.header(entry.getKey(), entry.getValue());
        }
        try (Response res = OkHttp.client(PRECHECK_TIMEOUT_MS).newCall(builder.build()).execute()) {
            if (!res.isSuccessful()) return false;
            String contentType = res.header("Content-Type");
            if (contentType != null) {
                String type = contentType.toLowerCase(Locale.ROOT);
                if (type.contains("text/html")) return false;
                if (type.contains("mpegurl") || type.contains("m3u") || type.contains("video/") || type.contains("mp4") || type.contains("octet-stream")) return true;
            }
            try (ResponseBody body = res.peekBody(PRECHECK_MAX_BYTES)) {
                String head = body.string();
                if (head.startsWith("#EXTM3U")) return true;
                if (head.contains("Just a moment") || head.contains("_cf_chl_opt")) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean startXml(Live item, String url) {
        try {
            EpgParser.start(item, url);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void fetchEpgDay(@NonNull Channel item, @NonNull ZoneId zoneId, int offset) {
        String date = LocalDate.now(zoneId).plusDays(offset).format(Formatters.DATE);
        String url = item.getEpg().replace("{date}", date);
        boolean need = url.startsWith("http") && item.getDataList().stream().noneMatch(epg -> epg.equal(date));
        if (need) item.setData(Epg.objectFrom(OkHttp.string(url), item.getTvgId(), date, zoneId));
    }
}
