package com.fongmi.android.tv.ui.fragment;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Class;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentVodBinding;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.event.StateEvent;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.impl.FilterListener;
import com.fongmi.android.tv.impl.SiteListener;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.ui.activity.ConfigActivity;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.activity.WebActivity;
import com.fongmi.android.tv.ui.adapter.IfaceTabAdapter;
import com.fongmi.android.tv.ui.adapter.SectionAdapter;
import com.fongmi.android.tv.ui.adapter.TypeAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.dialog.FilterDialog;
import com.fongmi.android.tv.ui.dialog.IfaceSheetDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SiteDialog;
import com.fongmi.android.tv.ui.dialog.SitePickDialog;
import com.fongmi.android.tv.utils.CheckStore;
import com.fongmi.android.tv.utils.ConfigManager;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.SourceChecker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VodFragment extends BaseFragment implements ConfigListener, SiteListener, FilterListener, TypeAdapter.OnClickListener,
        SectionAdapter.OnClickListener, IfaceTabAdapter.OnClickListener, IfaceSheetDialog.OnSelectListener, IfaceSheetDialog.OnOrderChangedListener,
        IfaceTabAdapter.OnTabOrderChangedListener {

    private FragmentVodBinding mBinding;
    private SiteViewModel mViewModel;
    private TypeAdapter mAdapter;
    private SectionAdapter mSectionAdapter;
    private IfaceTabAdapter mIfaceAdapter;
    private GridLayoutManager mIfaceLayoutManager;
    private Result mResult;
    private FolderFragment mFocused;
    private Class mFocusedType;
    private final Set<String> mObservedSections = new HashSet<>();
    private boolean mAutoFirst = true;

    public static VodFragment newInstance() {
        return new VodFragment();
    }

    private boolean isFocused() {
        return mFocused != null;
    }

    private Site getHome() {
        return VodConfig.get().getHome();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getHome().getKey());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentVodBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        EventBus.getDefault().register(this);
        setRecyclerView();
        setViewModel();
        showProgress();
        setSourceBar();
        refreshIfaceTabs();
    }

    @Override
    protected void initEvent() {
        mBinding.searchBtn.setOnClickListener(v -> SearchActivity.start(requireActivity()));
        mBinding.settingsBtn.setOnClickListener(v -> ConfigActivity.start(requireActivity()));
        mBinding.sourceBar.setOnClickListener(v -> SitePickDialog.create().show(this));

        // 源管理（改名/删除/拖拽排序）仍保留：长按源条打开旧的 SiteDialog。
        mBinding.sourceBar.setOnLongClickListener(v -> {
            SiteDialog.create().change().show(this);
            return true;
        });

        mBinding.typeMenu.setOnClickListener(this::onTypeMenu);
        mBinding.top.setOnClickListener(this::onTop);
        mBinding.filter.setOnClickListener(this::onFilter);
    }

    private void setRecyclerView() {
        mBinding.ifaceTabs.setHasFixedSize(true);
        mBinding.ifaceTabs.setItemAnimator(null);
        mIfaceLayoutManager = new GridLayoutManager(requireContext(), 1);
        mBinding.ifaceTabs.setLayoutManager(mIfaceLayoutManager);
        mBinding.ifaceTabs.setAdapter(mIfaceAdapter = new IfaceTabAdapter(this));
        mIfaceAdapter.setOnTabOrderChangedListener(this::onTabOrderChanged);

        // 0.1.118：长按接口 tab 拖动换位置（左右拖，全部位不可拖，松手才存一次）。
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT, 0) {

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                if (viewHolder.getAdapterPosition() == mIfaceAdapter.getItemCount() - 1) return 0;
                return super.getMovementFlags(recyclerView, viewHolder);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int from = viewHolder.getAdapterPosition();
                int to = target.getAdapterPosition();
                if (from == mIfaceAdapter.getItemCount() - 1 || to == mIfaceAdapter.getItemCount() - 1) return false;
                mIfaceAdapter.onMove(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                mIfaceAdapter.dispatchOrderChanged();
            }
        }).attachToRecyclerView(mBinding.ifaceTabs);

        mBinding.type.setHasFixedSize(true);
        mBinding.type.setItemAnimator(null);
        mBinding.type.setAdapter(mAdapter = new TypeAdapter(this));
        mBinding.sections.setHasFixedSize(false);
        mBinding.sections.setItemAnimator(null);
        mBinding.sections.setAdapter(mSectionAdapter = new SectionAdapter(this));
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observe(getViewLifecycleOwner(), this::setAdapter);
        mViewModel.getAction().observe(getViewLifecycleOwner(), result -> WebActivity.handleAction(requireActivity(), result));
    }

    private void setAdapter(Result result) {
        mAdapter.addAll(mResult = result);
        List<Class> types = new ArrayList<>();
        for (int i = 0; i < mAdapter.getItemCount(); i++) types.add(mAdapter.get(i));
        mSectionAdapter.setSections(getHome().getKey(), types);
        for (Class type : types) {
            if (type.isHome()) mSectionAdapter.setSectionItems(type, result.getList());
            else loadSection(type);
        }
        setFabVisible();
        hideProgress();
        showContent();
        setSourceBar();
        if (mAutoFirst && mAdapter.getItemCount() > 0 && !isFocused()) {
            mAutoFirst = false;
            onItemClick(0, mAdapter.get(0));
        }
    }

    private void loadSection(Class type) {
        SiteViewModel vm = new ViewModelProvider(this).get("section_" + type.getTypeId(), SiteViewModel.class);
        if (mObservedSections.add(type.getTypeId())) {
            vm.getResult().observe(getViewLifecycleOwner(), result -> onSectionResult(type, result));
        }
        vm.categoryContent(getHome().getKey(), type.getTypeId(), "1", true, getExtend(type));
    }

    private void onSectionResult(Class type, Result result) {
        if (result.getList().isEmpty()) mSectionAdapter.removeSection(type);
        else mSectionAdapter.setSectionItems(type, result.getList());
    }

    private HashMap<String, String> getExtend(Class type) {
        HashMap<String, String> extend = new HashMap<>();
        for (Filter filter : type.getFilters()) if (filter.getInit() != null) extend.put(filter.getKey(), filter.getInit());
        return extend;
    }

    private void setFabVisible() {
        mBinding.top.setVisibility(View.INVISIBLE);
        if (isFocused() && mFocusedType != null && !mFocusedType.getFilters().isEmpty()) mBinding.filter.show();
        else mBinding.filter.setVisibility(View.GONE);
    }

    private void setSourceBar() {
        // 0.1.117：片源条显示当前首页源名。加载中 home 为空时保留上一次显示，不闪空白；
        // 顶栏标题现在是静态“烛龙”，0.1.111 的“烛龙＋空白”问题已不存在。
        Site home = getHome();
        if (TextUtils.isEmpty(home.getName())) return;
        mBinding.sourceName.setText(getString(R.string.site_source, home.getName()));

        // 健康点：与 SiteAdapter.onBindViewHolder 同款逻辑；key 为空（加载中的空 Site）时不查。
        if (home.getKey() == null) {
            mBinding.sourceDot.setVisibility(View.GONE);
            return;
        }
        int grade = CheckStore.getSiteGrade(home.getKey());
        if (grade == SourceChecker.GRADE_NONE) {
            String activeId = ConfigManager.getActiveId();
            if (activeId != null && !activeId.isEmpty()) grade = CheckStore.getSiteGrade(activeId + "|" + home.getKey());
        }
        mBinding.sourceDot.setVisibility(grade == SourceChecker.GRADE_NONE ? View.GONE : View.VISIBLE);
        if (grade != SourceChecker.GRADE_NONE) mBinding.sourceDot.setBackgroundTintList(ColorStateList.valueOf(SourceChecker.gradeColor(grade)));
    }

    private void refreshIfaceTabs() {
        if (mIfaceAdapter == null) return;

        // 0.1.118：一行只放前 4 个接口＋全部（第 5 个固定不滚动）。
        List<ConfigManager.Sub> displayed = enabledVodSubs();
        if (displayed.size() > 4) displayed = displayed.subList(0, 4);
        if (mIfaceLayoutManager != null) mIfaceLayoutManager.setSpanCount(displayed.size() + 1);
        mIfaceAdapter.refresh(displayed, ConfigManager.getActiveId());
    }

    /** 启用的点播订阅（按订阅表里的保存顺序——用户拖拽后的顺序）：tab 行与接口面板共用。 */
    private static List<ConfigManager.Sub> enabledVodSubs() {
        List<ConfigManager.Sub> subs = ConfigManager.getSubs(ConfigManager.TAB_VOD);
        List<ConfigManager.Sub> enabled = new ArrayList<>();
        for (ConfigManager.Sub sub : subs) if (sub.enabled) enabled.add(sub);

        // 0.1.123：不再按在线在前、本地在后重排。0.1.96 的分组是为了找在线接口方便，
        // 但它每次读都重排：面板里跨组拖完的顺序一关再开就弹回去，tab 行与面板也对不上。
        // 现在显示顺序就是保存顺序（ConfigManager.saveVodOrder 存的顺序），拖完即所得；
        // 以前拖过存盘的顺序本身已是在线在前，老用户打开看到的顺序不变。
        return enabled;
    }

    @Override
    public void onIfaceClick(ConfigManager.Sub sub) {
        if (sub.id.equals(ConfigManager.getActiveId())) return;
        ConfigManager.activateVod(sub, getCallback());
    }

    @Override
    public void onAllClick() {
        IfaceSheetDialog.create().items(enabledVodSubs()).show(this);
    }

    @Override
    public void onIfaceSelected(ConfigManager.Sub sub) {
        if (sub.id.equals(ConfigManager.getActiveId())) return;
        ConfigManager.activateVod(sub, getCallback());
    }

    @Override
    public void onAllSelected() {
        if (TextUtils.isEmpty(ConfigManager.getActiveId())) return;
        ConfigManager.setActiveId("");
        ConfigManager.reloadVod(getCallback());
    }

    @Override
    public void onTabOrderChanged(List<ConfigManager.Sub> subs) {
        ConfigManager.saveVodOrder(subs);
    }

    @Override
    public void onIfaceOrderChanged(List<ConfigManager.Sub> subs) {
        ConfigManager.saveVodOrder(subs);

        // 0.1.119：面板里拖完的顺序可能改变 tab 行前 4，当场刷一次。
        refreshIfaceTabs();
    }

    private void onTypeMenu(View view) {
        int count = mAdapter.getItemCount();
        if (count == 0) return;
        String[] items = new String[count];
        for (int i = 0; i < count; i++) items[i] = mAdapter.get(i).getTypeName();
        new MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.home_all_types).setItems(items, (dialog, which) -> onItemClick(which, mAdapter.get(which))).show();
    }

    private Callback getCallback() {
        return new Callback() {
            @Override
            public void start() {
                mAutoFirst = true;
                showProgress();
                hideContent();
                setSourceBar();

                // 0.1.108：每次加载开始都把 tab 选中态刷成当前 activeId。自动换源/
                // 回退改了 activeId 后不刷，tab 会停在用户点的接口上，而实际已是
                // 另一个接口。
                refreshIfaceTabs();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();

                // 0.1.97：报错后把 tab 选中态刷回真实的 activeId。切换失败时
                // guarded 回调已把 activeId 恢复成旧接口，但 tab 还停在新接口 X
                // 上，不刷就会“tab 是 X、内容是旧接口”。
                refreshIfaceTabs();

                // 0.1.111：报错也是一个加载终态，片源条也要刷一次。静态 load()
                // 的失败分支在调 error() 之前已把快照恢复（vod.home = oldHome），
                // 此时 setSourceBar() 读到的是真实的旧首页源，不用等回退重载成功才纠正。
                setSourceBar();
            }
        };
    }

    private void enterFocused(Class type) {
        exitFocused();
        mFocusedType = type;
        mFocused = FolderFragment.newInstance(getHome().getKey(), type, 4);
        getChildFragmentManager().beginTransaction().replace(R.id.focusedContainer, mFocused).commit();
        mBinding.focusedContainer.setVisibility(View.VISIBLE);
        mBinding.sections.setVisibility(View.GONE);
        setFabVisible();
    }

    private void exitFocused() {
        if (mFocused == null) return;
        getChildFragmentManager().beginTransaction().remove(mFocused).commit();
        mFocused = null;
        mFocusedType = null;
        mBinding.focusedContainer.setVisibility(View.GONE);
        mBinding.sections.setVisibility(View.VISIBLE);
        setFabVisible();
    }

    private void onTop(View view) {
        if (isFocused()) mFocused.scrollToTop();
        else mBinding.sections.smoothScrollToPosition(0);
        mBinding.top.setVisibility(View.INVISIBLE);
        if (mBinding.filter.getVisibility() == View.INVISIBLE) mBinding.filter.show();
    }

    private void onFilter(View view) {
        if (isFocused() && mFocused.getFilters() != null && !mFocused.getFilters().isEmpty()) FilterDialog.create().filter(mFocused.getFilters()).show(this);
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
    }

    private void hideContent() {
        mBinding.type.setVisibility(View.INVISIBLE);
        mBinding.content.setVisibility(View.INVISIBLE);
    }

    private void showContent() {
        mBinding.type.setVisibility(View.VISIBLE);
        mBinding.content.setVisibility(View.VISIBLE);
    }

    private void homeContent() {
        exitFocused();
        showProgress();
        setFabVisible();
        mAdapter.clear();
        mSectionAdapter.clear();
        mAutoFirst = true;
        mViewModel.homeContent();
    }

    public Result getResult() {
        return mResult == null ? new Result() : mResult;
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        switch (event.getType()) {
            case HOME:
                setSourceBar();
                refreshIfaceTabs();
            case SIZE:
                homeContent();
                break;
            case CATEGORY:
                if (isFocused()) mFocused.onRefresh();
                else homeContent();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onStateEvent(StateEvent event) {
        switch (event.type()) {
            case EMPTY:
                hideProgress();
                break;
            case PROGRESS:
                showProgress();
                break;
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        ReceiveDialog.create().event(event).show(this);
    }

    @Override
    public void setConfig(Config config) {
        VodConfig.load(config, new Callback() {
            @Override
            public void start() {
                mAutoFirst = true;
                showProgress();
                hideContent();
                setSourceBar();
            }

            @Override
            public void error(String msg) {
                Notify.dismiss();
                Notify.show(msg);
                showContent();

                // 0.1.97：同上，报错后把 tab 选中态刷回真实的 activeId。
                refreshIfaceTabs();

                // 0.1.111：同 getCallback，报错后片源条也刷一次（快照已恢复）。
                setSourceBar();
            }
        });
    }

    @Override
    public void setSite(Site item) {
        VodConfig.get().setHome(item);
    }

    @Override
    public void onItemClick(int position, Class item) {
        onSectionAllClick(item);
        mAdapter.setSelected(position);
    }

    @Override
    public void setFilter(String key, Value value) {
        if (isFocused()) mFocused.setFilter(key, value);
    }

    @Override
    public void onSectionAllClick(Class type) {
        enterFocused(type);
    }

    @Override
    public void onSectionItemClick(Class type, Vod item) {
        if (item.isAction()) {
            mViewModel.action(getHome().getKey(), item.getAction());
        } else if (item.isFolder()) {
            enterFocused(type);
            mBinding.focusedContainer.post(() -> {
                if (mFocused != null) mFocused.openFolder(item.getId(), getExtend(type));
            });
        } else {
            if (getSite().isIndex()) SearchActivity.start(requireActivity(), item.getName());
            else VideoActivity.start(requireActivity(), getHome().getKey(), item, type.isFolder() ? item.getName() : null);
        }
    }

    @Override
    public boolean onSectionItemLongClick(Class type, Vod item) {
        if (item.isAction() || item.isFolder()) return false;
        SearchActivity.start(requireActivity(), item.getName());
        return true;
    }

    @Override
    public boolean canBack() {
        if (isFocused()) {
            if (mFocused.canBack()) {
                mFocused.goBack();
                return false;
            }
            exitFocused();
            return false;
        }
        return true;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        EventBus.getDefault().unregister(this);
    }
}
