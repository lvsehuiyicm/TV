package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogScriptBinding;
import com.fongmi.android.tv.ui.activity.FileActivity;
import com.fongmi.android.tv.ui.adapter.ScriptAdapter;
import com.fongmi.android.tv.ui.fragment.SettingFragment;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScriptManager;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ScriptDialog extends BaseAlertDialog {

    private DialogScriptBinding binding;
    private ScriptAdapter adapter;
    private boolean changed;

    public static ScriptDialog create() {
        return new ScriptDialog();
    }

    public void show(Fragment fragment) {
        show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogScriptBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.script_manage).setView(getBinding().getRoot()).setPositiveButton(R.string.dialog_positive, this::onPositive).setNegativeButton(R.string.dialog_negative, null);
    }

    @Override
    protected void initView() {
        adapter = new ScriptAdapter(folder -> {
            ScriptManager.get().removeFolder(folder);
            changed = true;
            refresh();
        });
        binding.recycler.setAdapter(adapter);
        binding.recycler.setHasFixedSize(true);
        binding.recycler.setItemAnimator(null);
        refresh();
    }

    private void refresh() {
        adapter.refresh();
        binding.count.setText(getString(R.string.script_count, ScriptManager.get().getFolders().size(), ScriptManager.get().scanFiles().size()));
    }

    @Override
    protected void initEvent() {
        binding.add.setOnClickListener(v -> pickFolder());
    }

    private void pickFolder() {
        Intent intent = new Intent(requireContext(), FileActivity.class);
        intent.putExtra(FileActivity.EXTRA_PICK_DIR, true);
        launcher.launch(intent);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        Uri uri = result.getData().getData();
        String path = uri.getPath();
        if (path == null || path.isEmpty()) return;
        if (!ScriptManager.get().addFolder(path)) {
            Notify.show(R.string.script_folder_exists);
            return;
        }
        changed = true;
        refresh();
    });

    private void onPositive(DialogInterface dialog, int which) {
        Fragment parent = requireParentFragment();
        if (changed && parent instanceof SettingFragment) ((SettingFragment) parent).reloadVod();
        dismiss();
    }
}
