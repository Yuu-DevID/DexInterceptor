package com.yuuxi.interceptor.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.yuuxi.interceptor.R;
import com.yuuxi.interceptor.util.Const;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class MainActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private EditText editTargetPackage;
    private EditText editTargetClasses;
    private EditText editTargetLibs;
    private Switch switchHookAll;
    private Switch switchEnableNative;
    private Button btnLaunchInterceptor;
    private Button btnPickApp;
    private Button btnAddClass;
    private Button btnAddLib;
    private TextView tvStatus;
    private TextView tvClassList;
    private TextView tvLibList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(Const.PREFS_NAME, MODE_WORLD_READABLE);

        initViews();
        loadSettings();
        updateStatus();
    }

    private void initViews() {
        editTargetPackage = findViewById(R.id.edit_target_package);
        editTargetClasses = findViewById(R.id.edit_target_classes);
        editTargetLibs = findViewById(R.id.edit_target_libs);
        switchHookAll = findViewById(R.id.switch_hook_all);
        switchEnableNative = findViewById(R.id.switch_enable_native);
        btnLaunchInterceptor = findViewById(R.id.btn_launch_interceptor);
        btnPickApp = findViewById(R.id.btn_pick_app);
        btnAddClass = findViewById(R.id.btn_add_class);
        btnAddLib = findViewById(R.id.btn_add_lib);
        tvStatus = findViewById(R.id.tv_status);
        tvClassList = findViewById(R.id.tv_class_list);
        tvLibList = findViewById(R.id.tv_lib_list);

        btnPickApp.setOnClickListener(v -> showAppPicker());

        btnAddClass.setOnClickListener(v -> {
            String cls = editTargetClasses.getText().toString().trim();
            if (!cls.isEmpty()) {
                addClassTarget(cls);
                editTargetClasses.setText("");
            }
        });

        btnAddLib.setOnClickListener(v -> {
            String lib = editTargetLibs.getText().toString().trim();
            if (!lib.isEmpty()) {
                addLibTarget(lib);
                editTargetLibs.setText("");
            }
        });

        btnLaunchInterceptor.setOnClickListener(v -> {
            saveSettings();
            Intent intent = new Intent(this, InterceptorActivity.class);
            startActivity(intent);
        });

        switchHookAll.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(Const.KEY_HOOK_ALL, checked).apply();
        });

        switchEnableNative.setOnCheckedChangeListener((btn, checked) -> {
            prefs.edit().putBoolean(Const.KEY_ENABLE_NATIVE, checked).apply();
        });
    }

    private void loadSettings() {
        String pkg = prefs.getString(Const.KEY_TARGET_PACKAGE, "");
        editTargetPackage.setText(pkg);
        switchHookAll.setChecked(prefs.getBoolean(Const.KEY_HOOK_ALL, false));
        switchEnableNative.setChecked(prefs.getBoolean(Const.KEY_ENABLE_NATIVE, true));
        refreshClassList();
        refreshLibList();
    }

    private void saveSettings() {
        prefs.edit()
                .putString(Const.KEY_TARGET_PACKAGE, editTargetPackage.getText().toString().trim())
                .putBoolean(Const.KEY_HOOK_ALL, switchHookAll.isChecked())
                .putBoolean(Const.KEY_ENABLE_NATIVE, switchEnableNative.isChecked())
                .apply();
    }

    private void addClassTarget(String className) {
        String stored = prefs.getString(Const.KEY_TARGET_CLASSES, "");
        Set<String> classes = new HashSet<>();
        if (!stored.isEmpty()) {
            Collections.addAll(classes, stored.split("\\|"));
        }
        classes.add(className);
        StringBuilder sb = new StringBuilder();
        for (String c : classes) {
            if (sb.length() > 0) sb.append("|");
            sb.append(c);
        }
        prefs.edit().putString(Const.KEY_TARGET_CLASSES, sb.toString()).apply();
        refreshClassList();
    }

    private void removeClassTarget(String className) {
        String stored = prefs.getString(Const.KEY_TARGET_CLASSES, "");
        Set<String> classes = new HashSet<>();
        if (!stored.isEmpty()) {
            Collections.addAll(classes, stored.split("\\|"));
        }
        classes.remove(className);
        StringBuilder sb = new StringBuilder();
        for (String c : classes) {
            if (sb.length() > 0) sb.append("|");
            sb.append(c);
        }
        prefs.edit().putString(Const.KEY_TARGET_CLASSES, sb.toString()).apply();
        refreshClassList();
    }

    private void addLibTarget(String libName) {
        String stored = prefs.getString(Const.KEY_TARGET_LIBS, "");
        Set<String> libs = new HashSet<>();
        if (!stored.isEmpty()) {
            Collections.addAll(libs, stored.split("\\|"));
        }
        libs.add(libName);
        StringBuilder sb = new StringBuilder();
        for (String l : libs) {
            if (sb.length() > 0) sb.append("|");
            sb.append(l);
        }
        prefs.edit().putString(Const.KEY_TARGET_LIBS, sb.toString()).apply();
        refreshLibList();
    }

    private void removeLibTarget(String libName) {
        String stored = prefs.getString(Const.KEY_TARGET_LIBS, "");
        Set<String> libs = new HashSet<>();
        if (!stored.isEmpty()) {
            Collections.addAll(libs, stored.split("\\|"));
        }
        libs.remove(libName);
        StringBuilder sb = new StringBuilder();
        for (String l : libs) {
            if (sb.length() > 0) sb.append("|");
            sb.append(l);
        }
        prefs.edit().putString(Const.KEY_TARGET_LIBS, sb.toString()).apply();
        refreshLibList();
    }

    private void refreshClassList() {
        String stored = prefs.getString(Const.KEY_TARGET_CLASSES, "");
        Set<String> classes = new HashSet<>();
        if (!stored.isEmpty()) {
            Collections.addAll(classes, stored.split("\\|"));
        }

        if (classes.isEmpty()) {
            tvClassList.setText("(no classes added)");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String c : classes) {
            sb.append("[X] ").append(c).append("\n");
        }
        tvClassList.setText(sb.toString().trim());
        tvClassList.setOnLongClickListener(v -> {
            showRemoveDialog("Remove class target", classes, selected -> {
                removeClassTarget(selected);
            });
            return true;
        });
    }

    private void refreshLibList() {
        String stored = prefs.getString(Const.KEY_TARGET_LIBS, "");
        Set<String> libs = new HashSet<>();
        if (!stored.isEmpty()) {
            Collections.addAll(libs, stored.split("\\|"));
        }

        if (libs.isEmpty()) {
            tvLibList.setText("(no libraries added)");
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (String l : libs) {
            sb.append("[X] ").append(l).append("\n");
        }
        tvLibList.setText(sb.toString().trim());
        tvLibList.setOnLongClickListener(v -> {
            showRemoveDialog("Remove library target", libs, selected -> {
                removeLibTarget(selected);
            });
            return true;
        });
    }

    private void showRemoveDialog(String title, Set<String> items, java.util.function.Consumer<String> onRemove) {
        String[] itemsArray = items.toArray(new String[0]);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(itemsArray, (dialog, which) -> {
                    onRemove.accept(itemsArray[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateStatus() {
        boolean hookingActive = !prefs.getString(Const.KEY_TARGET_PACKAGE, "").isEmpty();
        tvStatus.setText(hookingActive ? "Status: CONFIGURED" : "Status: NO TARGET");
        tvStatus.setTextColor(getResources().getColor(
                hookingActive ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));
    }

    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
        List<AppItem> appItems = new ArrayList<>();

        for (ApplicationInfo app : apps) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) == 0 || isImportantSystemApp(app)) {
                appItems.add(new AppItem(
                        app.packageName,
                        pm.getApplicationLabel(app).toString(),
                        pm.getApplicationIcon(app)
                ));
            }
        }

        Collections.sort(appItems, (a, b) -> a.label.compareToIgnoreCase(b.label));

        AppPickerAdapter adapter = new AppPickerAdapter(this, appItems);

        new AlertDialog.Builder(this)
                .setTitle("Select Target App")
                .setAdapter(adapter, (dialog, which) -> {
                    AppItem selected = appItems.get(which);
                    editTargetPackage.setText(selected.packageName);
                    saveSettings();
                    updateStatus();
                    Toast.makeText(this, "Target: " + selected.label, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isImportantSystemApp(ApplicationInfo app) {
        return (app.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
    }

    static class AppItem {
        String packageName;
        String label;
        Drawable icon;

        AppItem(String packageName, String label, Drawable icon) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    static class AppPickerAdapter extends BaseAdapter implements Filterable {
        private Context context;
        private List<AppItem> allApps;
        private List<AppItem> filteredApps;

        AppPickerAdapter(Context context, List<AppItem> apps) {
            this.context = context;
            this.allApps = apps;
            this.filteredApps = new ArrayList<>(apps);
        }

        @Override public int getCount() { return filteredApps.size(); }
        @Override public Object getItem(int pos) { return filteredApps.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            AppItem item = filteredApps.get(pos);
            ((TextView) convertView.findViewById(android.R.id.text1)).setText(item.label);
            ((TextView) convertView.findViewById(android.R.id.text2)).setText(item.packageName);
            return convertView;
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    if (constraint == null || constraint.length() == 0) {
                        results.values = new ArrayList<>(allApps);
                        results.count = allApps.size();
                    } else {
                        String query = constraint.toString().toLowerCase();
                        List<AppItem> filtered = new ArrayList<>();
                        for (AppItem app : allApps) {
                            if (app.label.toLowerCase().contains(query) || app.packageName.toLowerCase().contains(query)) {
                                filtered.add(app);
                            }
                        }
                        results.values = filtered;
                        results.count = filtered.size();
                    }
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredApps = (List<AppItem>) results.values;
                    notifyDataSetChanged();
                }
            };
        }
    }
}
