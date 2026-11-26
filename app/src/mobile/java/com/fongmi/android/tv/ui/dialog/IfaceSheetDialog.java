package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogIfaceSheetBinding;
import com.fongmi.android.tv.ui.activity.ConfigActivity;
import com.fongmi.android.tv.ui.adapter.IfaceSheetAdapter;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.SiteOrder;
import com.github.catvod.utils.Json;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class IfaceSheetDialog extends BaseBottomSheetDialog implements IfaceSheetAdapter.OnClickListener {

    private DialogIfaceSheetBinding binding;
    private OnSelectListener listener;
    private List<ConfigManager.Sub> subs;
    private IfaceSheetAdapter adapter;

    public static IfaceSheetDialog create() {
        return new IfaceSheetDialog();
    }

    public IfaceSheetDialog items(List<ConfigManager.Sub> subs) {
        this.subs = subs;
        return this;
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof OnSelectListener) listener = (OnSelectListener) fragment;
    }

    public interface OnSelectListener {

        void onIfaceSelected(ConfigManager.Sub sub);

        void onAllSelected();
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogIfaceSheetBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        List<IfaceSheetAdapter.Row> rows = new ArrayList<>();
        rows.add(new IfaceSheetAdapter.Row(null, null));
        if (subs != null) for (ConfigManager.Sub sub : subs) rows.add(new IfaceSheetAdapter.Row(sub, countText(sub)));
        adapter = new IfaceSheetAdapter(this);
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        adapter.refresh(rows, ConfigManager.getActiveId());
    }

    @Override
    protected void initEvent() {
        binding.manage.setOnClickListener(v -> {
            ConfigActivity.start(requireActivity());
            dismiss();
        });
    }

    @Override
    public void onItemClick(ConfigManager.Sub sub) {
        dismiss();
        if (listener != null) listener.onIfaceSelected(sub);
    }

    @Override
    public void onAllClick() {
        dismiss();
        if (listener != null) listener.onAllSelected();
    }

    /**
     * 每行源数。URL 接口没有现成数据（Config 表不存源数，CheckStore 只存等级），
     * 非当前行留空；能本地算出的才显示，见 NOTES.md 开放点 1。
     */
    private String countText(ConfigManager.Sub sub) {
        if (sub.isFolder()) return getString(R.string.iface_source_count, countFiles(new File(sub.link), 0));
        if (sub.isFile()) return getString(R.string.iface_source_count, 1);
        if (sub.isJson()) {
            Integer count = countJson(sub.link);
            return count == null ? null : getString(R.string.iface_source_count, count);
        }
        if (sub.isInterface() && sub.id.equals(ConfigManager.getActiveId())) {
            int count = 0;
            for (Site site : VodConfig.get().getSites()) if (!SiteOrder.isLocalScript(site)) count++;
            return getString(R.string.iface_source_count, count);
        }
        return null;
    }

    /** 本地包：读本地 JSON 文件，数 sites 数组长度；读不到／无 sites／Depot 式 urls 时返回 null。 */
    private static Integer countJson(String path) {
        try {
            String text = readFile(new File(path));
            if (text == null) return null;
            JsonElement element = Json.parse(text);
            if (element == null || !element.isJsonObject()) return null;
            JsonObject object = element.getAsJsonObject();
            JsonArray sites = object.getAsJsonArray("sites");
            return sites == null ? null : sites.size();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String readFile(File file) {
        if (file == null || !file.isFile()) return null;
        try (InputStream input = new FileInputStream(file); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = input.read(buffer)) >= 0) output.write(buffer, 0, n);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 文件夹：数 .py/.js 文件（逻辑照抄 ConfigFragment.countSources）。 */
    private static int countFiles(File dir, int depth) {
        int count = 0;
        File[] children = dir.listFiles();
        if (children == null) return 0;
        for (File file : children) {
            String lower = file.getName().toLowerCase();
            if (file.isFile() && (lower.endsWith(".py") || lower.endsWith(".js"))) count++;
            else if (file.isDirectory() && depth < 2 && !file.getName().startsWith(".")) count += countFiles(file, depth + 1);
        }
        return count;
    }
}
