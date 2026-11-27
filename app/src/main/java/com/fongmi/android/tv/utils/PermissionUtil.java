package com.fongmi.android.tv.utils;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.impl.PermissionCallback;
import com.permissionx.guolindev.PermissionMediator;
import com.permissionx.guolindev.PermissionX;

import java.util.function.Consumer;

public class PermissionUtil {

    public static void requestAudio(FragmentActivity activity, Consumer<Boolean> callback) {
        if (isGranted(activity, Manifest.permission.RECORD_AUDIO)) callback.accept(true);
        else post(activity, () -> PermissionX.init(activity).permissions(Manifest.permission.RECORD_AUDIO).request(new PermissionCallback(callback)));
    }

    public static void requestLocalNetwork(FragmentActivity activity, Consumer<Boolean> callback) {
        if (Build.VERSION.SDK_INT < 37 || isGranted(activity, Manifest.permission.ACCESS_LOCAL_NETWORK)) callback.accept(true);
        else post(activity, () -> PermissionX.init(activity).permissions(Manifest.permission.ACCESS_LOCAL_NETWORK).request(new PermissionCallback(callback)));
    }

    public static void requestLocalNetwork(Fragment fragment, Consumer<Boolean> callback) {
        FragmentActivity activity = fragment.requireActivity();
        if (Build.VERSION.SDK_INT < 37 || isGranted(activity, Manifest.permission.ACCESS_LOCAL_NETWORK)) callback.accept(true);
        else post(fragment, () -> PermissionX.init(fragment).permissions(Manifest.permission.ACCESS_LOCAL_NETWORK).request(new PermissionCallback(callback)));
    }

    public static void requestFile(FragmentActivity activity, Consumer<Boolean> callback) {
        boolean requestAllFiles = canRequestAllFiles(activity);
        if (hasFilePermission(activity, requestAllFiles)) callback.accept(true);
        else post(activity, () -> requestFile(PermissionX.init(activity), requestAllFiles, callback));
    }

    public static void requestFile(Fragment fragment, Consumer<Boolean> callback) {
        FragmentActivity activity = fragment.requireActivity();
        boolean requestAllFiles = canRequestAllFiles(activity);
        if (hasFilePermission(activity, requestAllFiles)) callback.accept(true);
        else post(fragment, () -> requestFile(PermissionX.init(fragment), requestAllFiles, callback));
    }

    public static void requestMediaVideo(Fragment fragment, Consumer<Boolean> callback) {
        FragmentActivity activity = fragment.requireActivity();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || isGranted(activity, Manifest.permission.READ_MEDIA_VIDEO)) callback.accept(true);
        else post(fragment, () -> PermissionX.init(fragment).permissions(Manifest.permission.READ_MEDIA_VIDEO).request(new PermissionCallback(callback)));
    }

    public static void requestNotify(FragmentActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return;
        if (isGranted(activity, PermissionX.permission.POST_NOTIFICATIONS)) return;
        post(activity, () -> PermissionX.init(activity).permissions(PermissionX.permission.POST_NOTIFICATIONS).request(new PermissionCallback()));
    }

    private static void requestFile(PermissionMediator mediator, boolean requestAllFiles, Consumer<Boolean> callback) {
        PermissionCallback permissionCallback = new PermissionCallback(callback);
        if (requestAllFiles) mediator.permissions().requestManageExternalStoragePermissionNow(permissionCallback);
        else mediator.permissions(storagePermission()).request(permissionCallback);
    }

    /** 0.1.127：这台手机有没有“所有文件访问权限”。 */
    public static boolean hasAllFilesPermission() {
        return hasAllFiles();
    }

    /** 0.1.127：这台手机能不能打开“所有文件访问权限”设置页（Android 11+ 且系统有这个设置页）。 */
    public static boolean canManageAllFiles(FragmentActivity activity) {
        return canRequestAllFiles(activity);
    }

    /**
     * 0.1.127：直接打开系统“所有文件访问权限”设置页（带本 app 包名），点允许就行。
     * 带包名的页打不开时退到不带包名的总页；两个都打不开时什么都不做。
     */
    public static void openAllFilesSettings(FragmentActivity activity) {
        Uri uri = Uri.parse("package:" + activity.getPackageName());
        Intent app = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
        if (resolves(activity, app)) {
            activity.startActivity(app);
            return;
        }
        Intent all = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        if (resolves(activity, all)) activity.startActivity(all);
    }

    private static boolean canRequestAllFiles(FragmentActivity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        Uri uri = Uri.parse("package:" + activity.getPackageName());
        Intent app = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
        Intent all = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        return resolves(activity, app) || resolves(activity, all);
    }

    private static boolean hasAllFiles() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager();
    }

    private static boolean hasFilePermission(FragmentActivity activity, boolean requestAllFiles) {
        return hasAllFiles() || (!requestAllFiles && hasStoragePermission(activity));
    }

    private static boolean hasStoragePermission(FragmentActivity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || isGranted(activity, storagePermission());
    }

    private static String storagePermission() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q ? Manifest.permission.WRITE_EXTERNAL_STORAGE : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    private static boolean isGranted(FragmentActivity activity, String permission) {
        return PermissionX.isGranted(activity, permission);
    }

    private static boolean resolves(FragmentActivity activity, Intent intent) {
        return intent.resolveActivity(activity.getPackageManager()) != null;
    }

    private static void post(FragmentActivity activity, Runnable runnable) {
        post(activity, null, runnable);
    }

    private static void post(Fragment fragment, Runnable runnable) {
        post(fragment.requireActivity(), fragment, runnable);
    }

    private static void post(FragmentActivity activity, Fragment fragment, Runnable runnable) {
        activity.getWindow().getDecorView().post(() -> {
            if (isActive(activity, fragment)) runnable.run();
        });
    }

    private static boolean isActive(FragmentActivity activity, Fragment fragment) {
        return !activity.isFinishing() && !activity.isDestroyed() && (fragment == null || fragment.isAdded());
    }
}
