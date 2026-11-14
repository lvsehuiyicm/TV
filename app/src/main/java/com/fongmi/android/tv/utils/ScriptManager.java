package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Site;
import com.github.catvod.utils.Prefers;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScriptManager {

    private static final String KEY = "script_folders";

    private static class Loader {
        static final ScriptManager INSTANCE = new ScriptManager();
    }

    private ScriptManager() {
    }

    public static ScriptManager get() {
        return Loader.INSTANCE;
    }

    public List<String> getFolders() {
        try {
            List<String> folders = App.gson().fromJson(Prefers.getString(KEY), new TypeToken<List<String>>() {
            }.getType());
            return folders == null ? new ArrayList<>() : folders;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public boolean addFolder(String path) {
        List<String> folders = getFolders();
        if (folders.contains(path)) return false;
        folders.add(path);
        save(folders);
        return true;
    }

    public void removeFolder(String path) {
        List<String> folders = getFolders();
        if (folders.remove(path)) save(folders);
    }

    private void save(List<String> folders) {
        Prefers.put(KEY, App.gson().toJson(folders));
    }

    public List<File> scanFiles() {
        List<File> files = new ArrayList<>();
        for (String folder : getFolders()) {
            File dir = new File(folder);
            File[] children = dir.isDirectory() ? dir.listFiles() : null;
            if (children == null) continue;
            for (File file : children) {
                if (!file.isFile()) continue;
                String lower = file.getName().toLowerCase();
                if (lower.endsWith(".py") || lower.endsWith(".js")) files.add(file);
            }
        }
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return files;
    }

    public List<Site> scan() {
        List<Site> sites = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (File file : scanFiles()) {
            String name = file.getName();
            String base = name.substring(0, name.lastIndexOf('.'));
            String key = "script_" + base;
            if (!keys.add(key)) continue;
            JsonObject object = new JsonObject();
            object.addProperty("key", key);
            object.addProperty("name", base + (name.toLowerCase().endsWith(".py") ? "[py]" : "[js]"));
            object.addProperty("type", 3);
            object.addProperty("api", "file://" + file.getAbsolutePath());
            sites.add(Site.objectFrom(object, ""));
        }
        return sites;
    }
}
