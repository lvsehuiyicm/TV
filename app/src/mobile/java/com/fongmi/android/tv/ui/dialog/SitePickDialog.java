package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogSitePickBinding;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.ui.adapter.SiteAdapter;
import com.fongmi.android.tv.ui.adapter.SitePickAdapter;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SitePickDialog extends BaseAlertDialog implements SiteListener, SitePickAdapter.OnItemLongClickListener {

    /** 0.1.128：切换片源双排 / 单排的选择，记住这次的选择，下次打开还是它。 */
    private static final String KEY_SITE_PICK_GRID = "site_pick_grid";

    private DialogSitePickBinding binding;
    private SiteListener listener;
    private SitePickAdapter adapter;
    private boolean grid;

    public static SitePickDialog create() {
        return new SitePickDialog();
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
        if (fragment instanceof SiteListener) listener = (SiteListener) fragment;
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogSitePickBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.site_switch).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        adapter = new SitePickAdapter(SiteAdapter.buildList(), VodConfig.get().getHome().getKey());
        grid = Prefers.getBoolean(KEY_SITE_PICK_GRID, false);
        // 0.1.131：把双排/单排告诉 adapter，双排时首页格不显示“当前使用”tag，
        // 不然 tag + 勾占掉约 84dp，名字只剩 20~40dp，只能显示成“...”。
        adapter.setGrid(grid);
        setLayoutManager();
        binding.recycler.setAdapter(adapter);
        binding.recycler.setItemAnimator(null);
        binding.recycler.setHasFixedSize(true);
        binding.keyword.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }
        });
    }

    @Override
    protected void initEvent() {
        adapter.setOnItemLongClickListener(this);
        // 0.1.127：点某一行就直接切到那个源并关弹窗，不用再点确定。
        adapter.setOnItemClickListener(site -> {
            if (listener != null) listener.setSite(site);
            dismiss();
        });
        binding.toggle.setOnClickListener(v -> {
            // 0.1.128：双排 / 单排切换。图标显示的是点一下会切到的布局（单排时显示田字格，
            // 双排时显示列表线），选完记下来，下次打开还是这次的样子。
            grid = !grid;
            Prefers.put(KEY_SITE_PICK_GRID, grid);
            setLayoutManager();
        });
        binding.cancel.setOnClickListener(v -> dismiss());
    }

    /** 0.1.128：按当前选择换布局管理器。双排是 2 列网格（源名 + 健康点都在格里，行布局不变，名字长了照常省略）；单排是原来的 1 列。 */
    private void setLayoutManager() {
        binding.recycler.setLayoutManager(grid ? new GridLayoutManager(requireContext(), 2) : new LinearLayoutManager(requireContext()));
        binding.toggle.setImageResource(grid ? R.drawable.ic_view_list : R.drawable.ic_view_grid);
        // 0.1.131：换排时同步告诉 adapter（双排藏 tag），setGrid 内部会重绑。
        if (adapter != null) adapter.setGrid(grid);
        binding.recycler.requestLayout();
    }

    @Override
    public void onItemLongClick(Site site) {
        // 0.1.118：弹窗里长按某一行，打开现有 SiteDialog 管这个源（改名/删除/排序）。
        SiteDialog.create().change().onDismiss(this::refreshList).show(this);
    }

    @Override
    public void setSite(Site site) {
        // SiteDialog 的转发：删首页源时靠它把 home 切到第一条（SiteDialog.deleteSite 的 wasHome 分支）。
        if (listener != null) listener.setSite(site);
    }

    /** 管理完刷新列表：重建列表与首页源；空表时关掉弹窗。 */
    private void refreshList() {
        if (adapter == null) return;
        binding.keyword.setText("");
        adapter.refresh(SiteAdapter.buildList(), VodConfig.get().getHome().getKey());
        if (adapter.getItemCount() == 0) dismiss();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (adapter.getItemCount() == 0) dismiss();
        else if (ResUtil.isLand(requireContext())) setWidth(0.5f);
    }
}
