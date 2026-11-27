package com.fongmi.android.tv.ui.fragment;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.databinding.ActivityConfigBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.ui.activity.FileActivity;
import com.fongmi.android.tv.ui.adapter.ConfigSubAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.CheckStore;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScriptManager;
import com.fongmi.android.tv.utils.SourceChecker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConfigFragment extends BaseFragment implements ConfigSubAdapter.OnClickListener {

    public static ConfigFragment newInstance(boolean embedded) {
        ConfigFragment fragment = new ConfigFragment();
        Bundle args = new Bundle();
        args.putBoolean("embedded", embedded);
        fragment.setArguments(args);
        return fragment;
    }

    private static final int PICK_SINGLE = 0;
    private static final int PICK_LOCAL = 1;

    private boolean pickingEditPath;
    private EditText editLinkInput;

    private final ActivityResultLauncher<Intent> folderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        boolean edit = pickingEditPath;
        pickingEditPath = false;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = result.getData().getData().getPath();
        if (path == null) return;
        if (edit && editLinkInput != null) {
            editLinkInput.setText(path);
            return;
        }
        onFolderPicked(path);
    });

    private final ActivityResultLauncher<Intent> fileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getFileUri(result, this::onFilePicked));

    private final ActivityResultLauncher<Intent> editFileLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> FileChooser.getFileUri(result, uri -> {
        if (uri != null && uri.getPath() != null && editLinkInput != null) editLinkInput.setText(uri.getPath());
    }));

    private ActivityConfigBinding mBinding;
    private ConfigSubAdapter mAdapterOnline;
    private ConfigSubAdapter mAdapterLocal;
    private ConfigSubAdapter mAdapterLive;
    private int tab = ConfigManager.TAB_VOD;
    private int pickMode = PICK_SINGLE;
    private boolean embedded;
    private ExecutorService checkExecutor;
    private volatile boolean checkCancelled;
    private boolean checking;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressItem;
    private TextView progressCount;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) embedded = getArguments().getBoolean("embedded", false);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = ActivityConfigBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        if (embedded) mBinding.toolbar.setVisibility(View.GONE);
        else mBinding.toolbar.setNavigationOnClickListener(v -> requireActivity().finish());
        mBinding.checkAll.setSelected(true);
        mAdapterOnline = new ConfigSubAdapter(this);
        mAdapterLocal = new ConfigSubAdapter(this);
        mAdapterLive = new ConfigSubAdapter(this);
        bindSection(mBinding.recyclerOnline, mAdapterOnline);
        bindSection(mBinding.recyclerLocal, mAdapterLocal);
        bindSection(mBinding.recyclerLive, mAdapterLive);
        refresh();
    }

    /** 一组一个 RecyclerView；长按把手拖动换顺序（0.1.118 首页 tab 同款，松手才存一次）。 */
    private void bindSection(RecyclerView recycler, ConfigSubAdapter adapter) {
        recycler.setAdapter(adapter);
        recycler.setItemAnimator(null);
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                adapter.onMove(viewHolder.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                saveOrder();
            }
        });
        helper.attachToRecyclerView(recycler);
        adapter.setItemTouchHelper(helper);
    }

    @Override
    protected void initEvent() {
        mBinding.checkAll.setOnClickListener(v -> startCheckAll());
        mBinding.addOnline.setOnClickListener(v -> {
            setTab(ConfigManager.TAB_VOD);
            onAdd();
        });
        mBinding.addLocal.setOnClickListener(v -> showLocalAddDialog());
        mBinding.addLive.setOnClickListener(v -> showLiveAddDialog());
    }

    /** 本地接口组一种“添加”对应三种导入方式，点开选一种（沿用原来的三个导入入口）。 */
    private void showLocalAddDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(new CharSequence[]{getString(R.string.config_import_local_interface), getString(R.string.config_import_folder), getString(R.string.config_import_single)}, (dialog, which) -> {
                    if (which == 0) importLocalFile();
                    else if (which == 1) importFolder();
                    else importSingle();
                })
                .show();
    }

    /** 直播组一种“添加”对应两种导入方式，点开选一种（沿用原来的两个导入入口）。 */
    private void showLiveAddDialog() {
        new MaterialAlertDialogBuilder(requireContext())
                .setItems(new CharSequence[]{getString(R.string.config_import_live_local), getString(R.string.config_import_live_online)}, (dialog, which) -> {
                    if (which == 0) importLiveLocal();
                    else {
                        setTab(ConfigManager.TAB_LIVE);
                        onAdd();
                    }
                })
                .show();
    }

    private void importLocalFile() {
        setTab(ConfigManager.TAB_VOD);
        pickMode = PICK_LOCAL;
        FileChooser.from(fileLauncher).show();
    }

    private void importFolder() {
        setTab(ConfigManager.TAB_VOD);
        folderLauncher.launch(new Intent(requireActivity(), FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true));
    }

    private void importSingle() {
        setTab(ConfigManager.TAB_VOD);
        pickMode = PICK_SINGLE;
        FileChooser.from(fileLauncher).show();
    }

    private void importLiveLocal() {
        setTab(ConfigManager.TAB_LIVE);
        pickMode = PICK_LOCAL;
        FileChooser.from(fileLauncher).show();
    }

    /** 只记下当前类型（onAdd / onFilePicked 靠它定 kind）；列表不再按类型切页，3 组一直都在。 */
    private void setTab(int tab) {
        this.tab = tab;
    }

    /** 在线组 = KIND_URL；本地组 = KIND_JSON + KIND_FOLDER + KIND_FILE（保持订阅原顺序拼在一起）；直播组 = 两类直播。 */
    private void refresh() {
        List<ConfigManager.Sub> vod = ConfigManager.getSubs(ConfigManager.TAB_VOD);
        List<ConfigManager.Sub> live = ConfigManager.getSubs(ConfigManager.TAB_LIVE);
        List<ConfigManager.Sub> online = filter(vod, ConfigManager.KIND_URL);
        List<ConfigManager.Sub> local = new ArrayList<>();
        for (ConfigManager.Sub sub : vod) if (sub.isJson() || sub.isFolder() || sub.isFile()) local.add(sub);
        Map<String, ConfigSubAdapter.Count> counts = localCounts(vod, live);
        refreshSection(mBinding.recyclerOnline, mAdapterOnline, online, counts);
        refreshSection(mBinding.recyclerLocal, mAdapterLocal, local, counts);
        refreshSection(mBinding.recyclerLive, mAdapterLive, live, counts);
        mBinding.groupOnlineCount.setText(String.valueOf(online.size()));
        mBinding.groupLocalCount.setText(String.valueOf(local.size()));
        mBinding.groupLiveCount.setText(String.valueOf(live.size()));
        refreshTotal();
    }

    private void refreshSection(RecyclerView recycler, ConfigSubAdapter adapter, List<ConfigManager.Sub> subs, Map<String, ConfigSubAdapter.Count> counts) {
        adapter.refresh(subs);
        adapter.setCounts(counts);
        recycler.setVisibility(subs.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 总数卡：接口总数 + 健康点统计（只计测过成绩的，没测过的不算）。 */
    private void refreshTotal() {
        List<ConfigManager.Sub> vod = ConfigManager.getSubs(ConfigManager.TAB_VOD);
        List<ConfigManager.Sub> live = ConfigManager.getSubs(ConfigManager.TAB_LIVE);
        int green = 0;
        int yellow = 0;
        int red = 0;
        for (ConfigManager.Sub sub : vod) {
            int grade = gradeOf(sub);
            if (grade == SourceChecker.GRADE_GREEN) green++;
            else if (grade == SourceChecker.GRADE_YELLOW) yellow++;
            else if (grade == SourceChecker.GRADE_RED) red++;
        }
        for (ConfigManager.Sub sub : live) {
            int grade = gradeOf(sub);
            if (grade == SourceChecker.GRADE_GREEN) green++;
            else if (grade == SourceChecker.GRADE_YELLOW) yellow++;
            else if (grade == SourceChecker.GRADE_RED) red++;
        }
        mBinding.totalText.setText(getString(R.string.config_total, vod.size() + live.size()));
        mBinding.totalGreen.setText(String.valueOf(green));
        mBinding.totalYellow.setText(String.valueOf(yellow));
        mBinding.totalRed.setText(String.valueOf(red));
    }

    private int gradeOf(ConfigManager.Sub sub) {
        if (sub.isJson() || sub.isFolder() || sub.isFile()) return mAdapterLocal.getGrade(sub.id);
        if (sub.isUrl()) return mAdapterOnline.getGrade(sub.id);
        return mAdapterLive.getGrade(sub.id);
    }

    private List<ConfigManager.Sub> filter(List<ConfigManager.Sub> subs, String kind) {
        List<ConfigManager.Sub> result = new ArrayList<>();
        for (ConfigManager.Sub sub : subs) if (kind.equals(sub.kind)) result.add(sub);
        return result;
    }

    /**
     * 拖完的顺序存盘：只重排各组内部的相对顺序，各组在订阅总表里的槽位不动——
     * 这样首页接口 tab 的顺序不会因为配置页的一次拖动被打乱重组。
     */
    private void saveOrder() {
        List<ConfigManager.Sub> all = ConfigManager.getAll();
        List<ConfigManager.Sub> local = mAdapterLocal.getItems();
        List<ConfigManager.Sub> online = mAdapterOnline.getItems();
        List<ConfigManager.Sub> live = mAdapterLive.getItems();
        int localIndex = 0;
        int onlineIndex = 0;
        int liveIndex = 0;
        List<ConfigManager.Sub> result = new ArrayList<>();
        for (ConfigManager.Sub sub : all) {
            if (sub.tab == ConfigManager.TAB_VOD) {
                if (sub.isUrl()) result.add(pick(online, onlineIndex++, sub));
                else result.add(pick(local, localIndex++, sub));
            } else {
                result.add(pick(live, liveIndex++, sub));
            }
        }
        ConfigManager.saveAllOrder(result);
    }

    private ConfigManager.Sub pick(List<ConfigManager.Sub> items, int index, ConfigManager.Sub fallback) {
        return index < items.size() ? items.get(index) : fallback;
    }

    /** 本地能算的源数（免联网）：本地包数 sites、文件夹数 .py/.js、直播文件数频道；在线的数只能等检测。 */
    private Map<String, ConfigSubAdapter.Count> localCounts(List<ConfigManager.Sub> vod, List<ConfigManager.Sub> live) {
        Map<String, ConfigSubAdapter.Count> result = new HashMap<>();
        for (ConfigManager.Sub sub : vod) {
            if (sub.isJson()) {
                int count = countJsonSites(sub.link);
                if (count >= 0) result.put(sub.id, new ConfigSubAdapter.Count(count, SourceChecker.COUNT_SITES));
            } else if (sub.isFolder()) {
                File dir = new File(sub.link);
                if (dir.isDirectory()) result.put(sub.id, new ConfigSubAdapter.Count(countSources(dir), SourceChecker.COUNT_SOURCES));
            }
        }
        for (ConfigManager.Sub sub : live) {
            if (sub.isLiveFile()) {
                int count = countLiveChannels(sub.link);
                if (count >= 0) result.put(sub.id, new ConfigSubAdapter.Count(count, SourceChecker.COUNT_CHANNELS));
            }
        }
        return result;
    }

    private int countJsonSites(String path) {
        try {
            File file = new File(path);
            if (!file.isFile()) return -1;
            String text = readTextCapped(file);
            if (text == null) return -1;
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) return -1;
            JsonElement sites = parsed.getAsJsonObject().get("sites");
            if (sites == null || !sites.isJsonArray()) return -1;
            return sites.getAsJsonArray().size();
        } catch (Exception e) {
            return -1;
        }
    }

    private int countLiveChannels(String path) {
        try {
            File file = new File(path);
            if (!file.isFile()) return -1;
            String text = readTextCapped(file);
            if (text == null) return -1;
            String content = text.startsWith("\uFEFF") ? text.substring(1) : text;
            JsonElement parsed = null;
            try {
                parsed = JsonParser.parseString(content);
            } catch (Exception ignored) {
            }
            if (parsed != null && parsed.isJsonArray()) return parsed.getAsJsonArray().size();
            if (parsed != null && parsed.isJsonObject()) return -1;
            return countChannelLines(content);
        } catch (Exception e) {
            return -1;
        }
    }

    /** 和 SourceChecker.countChannels 同一套数法（它是 private 的，这里抄一份：m3u 数 #EXTINF，txt 数带逗号和 :// 的行）。 */
    private int countChannelLines(String text) {
        int count = 0;
        boolean m3u = text.contains("#EXTM3U");
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#genre#")) continue;
            if (m3u) {
                if (line.startsWith("#EXTINF")) count++;
            } else if (!line.startsWith("#")) {
                int comma = line.indexOf(',');
                if (comma > 0 && line.substring(comma + 1).contains("://")) count++;
            }
        }
        return count;
    }

    /** 读本地文件全文；超过 4MB 的不读（配单没那么大，读了也解析不动）。 */
    private String readTextCapped(File file) {
        if (file.length() > 4L * 1024 * 1024) return null;
        try (InputStream in = new FileInputStream(file); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public void onDestroyView() {
        checkCancelled = true;
        if (checkExecutor != null) checkExecutor.shutdownNow();
        dismissProgressDialog();
        super.onDestroyView();
    }

    /** 检测全部源：一个一个来（网慢，并行会拖死），每出一个结果就标在它下边；进度框里能看到正在测哪个、测到第几个。 */
    private void startCheckAll() {
        if (checking) return;
        checking = true;
        checkCancelled = false;
        List<ConfigManager.Sub> all = new ArrayList<>();
        all.addAll(ConfigManager.getSubs(ConfigManager.TAB_VOD));
        all.addAll(ConfigManager.getSubs(ConfigManager.TAB_LIVE));
        for (ConfigSubAdapter adapter : new ConfigSubAdapter[]{mAdapterOnline, mAdapterLocal, mAdapterLive}) adapter.clearStatus();
        mBinding.checkAll.setText(getString(R.string.config_checking, 0, all.size()));
        mBinding.checkAll.setEnabled(false);
        showProgressDialog();
        checkExecutor = Executors.newSingleThreadExecutor();
        checkExecutor.execute(() -> {
            int greenCount = 0;
            int yellowCount = 0;
            int redCount = 0;
            for (int i = 0; i < all.size(); i++) {
                if (checkCancelled) break;
                ConfigManager.Sub sub = all.get(i);
                int subIndex = i + 1;
                App.post(() -> {
                    if (getView() == null || progressDialog == null) return;
                    progressBar.setIndeterminate(true);
                    progressItem.setText(sub.name);
                    progressCount.setText(getString(R.string.config_check_progress_sub, subIndex, all.size()));
                });
                SourceChecker.Result result = SourceChecker.check(sub, new SourceChecker.ProgressListener() {
                    @Override
                    public void onItem(String itemName, int done, int total) {
                        App.post(() -> {
                            if (getView() == null || progressDialog == null) return;
                            progressBar.setIndeterminate(false);
                            progressBar.setMax(total);
                            progressBar.setProgress(done);
                            progressItem.setText(sub.name + " · " + itemName);
                            progressCount.setText(getString(R.string.config_check_progress, done, total, subIndex, all.size()));
                        });
                    }

                    @Override
                    public boolean isCancelled() {
                        return checkCancelled;
                    }
                });
                if (checkCancelled) break;
                if (result.grade == SourceChecker.GRADE_GREEN) greenCount++;
                else if (result.grade == SourceChecker.GRADE_YELLOW) yellowCount++;
                else redCount++;
                CheckStore.putSubGrade(sub.id, result.grade);
                CheckStore.putSiteGrades(result.siteGrades);
                int done = i + 1;
                App.post(() -> {
                    if (getView() == null) return;
                    setStatus(sub, result);
                    refreshTotal();
                    mBinding.checkAll.setText(getString(R.string.config_checking, done, all.size()));
                });
            }
            int green = greenCount;
            int yellow = yellowCount;
            int red = redCount;
            App.post(() -> {
                if (getView() == null) return;
                dismissProgressDialog();
                checking = false;
                mBinding.checkAll.setEnabled(true);
                mBinding.checkAll.setText(R.string.config_check_all_btn);
                refreshTotal();
                if (!checkCancelled) Notify.show(getString(R.string.config_check_done, green, yellow, red));
            });
        });
    }

    /** 检测进度框：当前源名 + 横向进度条 + 计数；取消按钮停掉检测。 */
    private void showProgressDialog() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, ResUtil.dp2px(8));
        progressItem = new TextView(requireContext());
        progressItem.setTextSize(16);
        progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = ResUtil.dp2px(12);
        progressBar.setLayoutParams(barParams);
        progressBar.setIndeterminate(true);
        progressCount = new TextView(requireContext());
        progressCount.setTextSize(14);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        countParams.topMargin = ResUtil.dp2px(8);
        progressCount.setLayoutParams(countParams);
        layout.addView(progressItem);
        layout.addView(progressBar);
        layout.addView(progressCount);
        progressDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_checking_title)
                .setView(layout)
                .setNegativeButton(R.string.config_check_cancel, (dialog, which) -> checkCancelled = true)
                .setCancelable(false)
                .create();
        progressDialog.show();
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) progressDialog.dismiss();
        progressDialog = null;
    }

    private void setStatus(ConfigManager.Sub sub, SourceChecker.Result result) {
        String text = statusText(result);
        if (sub.isJson() || sub.isFolder() || sub.isFile()) mAdapterLocal.setStatus(sub.id, text, result.grade, result.count, result.countKind);
        else if (sub.isUrl()) mAdapterOnline.setStatus(sub.id, text, result.grade, result.count, result.countKind);
        else if (sub.isLiveFile() || sub.isLiveUrl()) mAdapterLive.setStatus(sub.id, text, result.grade, result.count, result.countKind);
    }

    private String statusText(SourceChecker.Result result) {
        if (result.grade == SourceChecker.GRADE_RED) return getString(R.string.config_check_fail, getString(result.reason));
        if (result.grade == SourceChecker.GRADE_YELLOW) {
            switch (result.yellowKind) {
                case SourceChecker.YELLOW_SLOW:
                    switch (result.countKind) {
                        case SourceChecker.COUNT_SITES:
                            return getString(R.string.config_check_slow_sites, result.count);
                        case SourceChecker.COUNT_CHANNELS:
                            return getString(R.string.config_check_slow_channels, result.count);
                        case SourceChecker.COUNT_SOURCES:
                            return getString(R.string.config_check_slow_sources, result.count);
                        default:
                            return getString(R.string.config_check_slow);
                    }
                case SourceChecker.YELLOW_PARTIAL:
                    if (result.countKind == SourceChecker.COUNT_SITES) return getString(R.string.config_check_partial_sites, result.good, result.total);
                    return getString(R.string.config_check_partial_sources, result.good, result.total);
                case SourceChecker.YELLOW_UNVERIFIED:
                    return getString(R.string.config_check_unverified);
                default:
                    return getString(R.string.config_check_slow);
            }
        }
        switch (result.countKind) {
            case SourceChecker.COUNT_SITES:
                return getString(R.string.config_check_ok_sites, result.count);
            case SourceChecker.COUNT_CHANNELS:
                return getString(R.string.config_check_ok_channels, result.count);
            case SourceChecker.COUNT_SOURCES:
                return getString(R.string.config_check_ok_sources, result.count);
            default:
                return getString(R.string.config_check_ok);
        }
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                Notify.progress(requireActivity());
            }

            @Override
            public void success() {
                Notify.dismiss();
                refresh();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                refresh();
            }
        };
    }

    /** “添加接口”：名字 + 链接（在线接口或直播源地址）。名字不用填：输链接时自动取链接的文件名（mynz.json → mynz），想改就自己改；确定时名字还空着，也按链接自动取。 */
    private void onAdd() {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        EditText nameInput = new EditText(requireContext());
        nameInput.setHint(R.string.config_name);
        EditText linkInput = new EditText(requireContext());
        linkInput.setHint(R.string.config_link);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String[] autoName = {""};
        linkInput.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String current = nameInput.getText().toString();
                if (!current.isEmpty() && !current.equals(autoName[0])) return;
                autoName[0] = fileNameFromLink(s.toString());
                nameInput.setText(autoName[0]);
            }
        });
        layout.addView(nameInput);
        layout.addView(linkInput);
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_add_interface)
                .setView(layout)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String link = linkInput.getText().toString().trim();
                    if (link.isEmpty()) {
                        Notify.show(R.string.config_need_link);
                        return;
                    }
                    if (name.isEmpty()) {
                        name = fileNameFromLink(link);
                        if (name.isEmpty()) {
                            Notify.show(R.string.config_need_name);
                            return;
                        }
                    }
                    String kind = tab == ConfigManager.TAB_VOD ? ConfigManager.KIND_URL : ConfigManager.KIND_LIVE_URL;
                    ConfigManager.Sub sub = ConfigManager.add(tab, kind, name, link);
                    refresh();
                    if (tab == ConfigManager.TAB_VOD) ConfigManager.activateVod(sub, getCallback());
                    else ConfigManager.activateLive(sub, getCallback());
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    /** 从链接里取默认名字：最后一段路径的文件名，去掉扩展名（mynz.json → mynz）；没有文件名就用域名；都取不到返回 ""。 */
    private static String fileNameFromLink(String link) {
        if (link == null) return "";
        String clean = link.trim();
        if (clean.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(clean);
            String segment = uri.getLastPathSegment();
            if (segment != null && !segment.isEmpty()) {
                int dot = segment.lastIndexOf('.');
                if (dot > 0) segment = segment.substring(0, dot);
                if (!segment.isEmpty()) return segment;
            }
            String host = uri.getHost();
            return host == null ? "" : host;
        } catch (Exception ignored) {
            return "";
        }
    }

    /** 扫描文件夹：加成一个本地接口，里面的 .py/.js 都会变成源。 */
    private void onFolderPicked(String path) {
        File dir = new File(path);
        if (!dir.isDirectory()) return;
        if (ScriptManager.get().getFolders().contains(path)) {
            Notify.show(R.string.config_folder_exists);
            return;
        }
        int count = countSources(dir);
        ConfigManager.add(ConfigManager.TAB_VOD, ConfigManager.KIND_FOLDER, dir.getName(), path);
        Notify.show(getString(R.string.config_merged, count));
        ConfigManager.reloadVod(getCallback());
        refresh();
    }

    private int countSources(File dir) {
        return countSources(dir, 0);
    }

    private int countSources(File dir, int depth) {
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File file : children) {
            String lower = file.getName().toLowerCase();
            if (file.isFile() && (lower.endsWith(".py") || lower.endsWith(".js"))) count++;
            else if (file.isDirectory() && depth < 2 && !file.getName().startsWith(".")) count += countSources(file, depth + 1);
        }
        return count;
    }

    /** 单个源文件 / 本地包 / 直播文件。 */
    private void onFilePicked(Uri uri) {
        if (uri == null || uri.getPath() == null) return;
        String path = uri.getPath();
        String lower = path.toLowerCase();
        if (pickMode == PICK_SINGLE) {
            if (!lower.endsWith(".py") && !lower.endsWith(".js")) {
                Notify.show(R.string.config_bad_single);
                return;
            }
            if (ScriptManager.get().getFiles().contains(path)) {
                Notify.show(R.string.config_file_exists);
                return;
            }
            File file = new File(path);
            String base = file.getName().substring(0, file.getName().lastIndexOf('.'));
            ConfigManager.add(ConfigManager.TAB_VOD, ConfigManager.KIND_FILE, base, path);
            ConfigManager.reloadVod(getCallback());
        } else if (tab == ConfigManager.TAB_VOD) {
            if (!lower.endsWith(".json")) {
                Notify.show(R.string.config_bad_json);
                return;
            }
            ConfigManager.add(ConfigManager.TAB_VOD, ConfigManager.KIND_JSON, new File(path).getName(), path);
        } else {
            if (!lower.endsWith(".txt") && !lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) {
                Notify.show(R.string.config_bad_live);
                return;
            }
            ConfigManager.add(ConfigManager.TAB_LIVE, ConfigManager.KIND_LIVE_FILE, new File(path).getName(), path);
        }
        refresh();
    }

    /** 点一行切换接口（原来是拨开关，现在按设计图改成点行）：已经是当前用的就什么都不做。 */
    @Override
    public void onRowClick(ConfigManager.Sub item) {
        if (item.tab == ConfigManager.TAB_VOD) {
            if (item.id.equals(ConfigManager.getActiveId())) return;
            ConfigManager.activateVod(item, getCallback());
            refresh();
        } else {
            if (item.enabled && item.getConfigUrl().equals(LiveConfig.getUrl())) return;
            ConfigManager.activateLive(item, getCallback());
            refresh();
        }
    }

    /** 长按菜单里的“启用/停用”：原样恢复 0.1.121 的开关逻辑（开关在 0.1.122 重排版时拿掉了，入口改到长按菜单）。 */
    @Override
    public void onToggle(ConfigManager.Sub item, boolean enabled) {
        if (item.tab == ConfigManager.TAB_VOD) {
            if (!enabled && item.enabled && !item.id.equals(ConfigManager.getActiveId())) {
                // 点的是已开但不是当前用的：切成当前用的，不关掉。
                ConfigManager.activateVod(item, getCallback());
                refresh();
                return;
            }
            if (enabled) ConfigManager.activateVod(item, getCallback());
            else ConfigManager.deactivateVod(item, getCallback());
        } else {
            if (enabled) ConfigManager.activateLive(item, getCallback());
            else ConfigManager.deactivateLive(item, getCallback());
        }
    }

    @Override
    public void onEdit(ConfigManager.Sub item) {
        LinearLayout layout = new LinearLayout(requireContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = ResUtil.dp2px(20);
        layout.setPadding(padding, ResUtil.dp2px(8), padding, 0);
        EditText nameInput = new EditText(requireContext());
        nameInput.setText(item.name);
        EditText linkInput = new EditText(requireContext());
        linkInput.setText(item.link);
        linkInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        layout.addView(nameInput);
        layout.addView(linkInput);
        if (!item.isUrl() && !item.isLiveUrl()) {
            Button pickButton = new Button(requireContext());
            pickButton.setText(R.string.setting_choose);
            pickButton.setOnClickListener(v -> {
                editLinkInput = linkInput;
                if (item.isFolder()) {
                    pickingEditPath = true;
                    folderLauncher.launch(new Intent(requireActivity(), FileActivity.class).putExtra(FileActivity.EXTRA_PICK_DIR, true));
                } else {
                    FileChooser.from(editFileLauncher).show();
                }
            });
            layout.addView(pickButton);
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.config_edit_sub)
                .setView(layout)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    String name = nameInput.getText().toString().trim();
                    String link = linkInput.getText().toString().trim();
                    if (name.isEmpty()) {
                        Notify.show(R.string.config_need_name);
                        return;
                    }
                    if (link.isEmpty()) {
                        Notify.show(R.string.config_need_link);
                        return;
                    }
                    boolean wasActive = item.id.equals(ConfigManager.getActiveId());
                    String oldLink = item.link;
                    item.name = name;
                    item.link = link;
                    ConfigManager.updateLink(item, oldLink);
                    refresh();
                    if (wasActive) {
                        if (item.tab == ConfigManager.TAB_VOD) ConfigManager.activateVod(item, getCallback());
                        else ConfigManager.activateLive(item, getCallback());
                    }
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    @Override
    public void onDelete(ConfigManager.Sub item) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.config_delete_confirm, item.name))
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    boolean wasActive = item.id.equals(ConfigManager.getActiveId());
                    ConfigManager.remove(item);
                    refresh();
                    if (item.tab == ConfigManager.TAB_VOD) ConfigManager.reloadVod(getCallback());
                    else if (wasActive) LiveConfig.load(Config.live(), getCallback());
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }
}
