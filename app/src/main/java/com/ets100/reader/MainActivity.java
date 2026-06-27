package com.ets100.reader;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 100;
    private static final String BASE_PATH =
            "/storage/emulated/0/Android/data/com.ets100.secondary/files";

    private TextView tvStatus;
    private TextView tvEmpty;
    private RecyclerView recyclerView;
    private Button btnScan;
    private EditText etManualPath;
    private ProgressBar progressBar;
    private ResultAdapter adapter;
    private String directPath;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Shizuku
    private boolean shizukuReady = false;
    private boolean shizukuBound = false;
    private IUserService shizukuService;

    private final Shizuku.OnRequestPermissionResultListener requestPermissionResultListener =
            this::onRequestPermissionsResult;

    private void onRequestPermissionsResult(int requestCode, int grantResult) {
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            tvStatus.setText("Shizuku 已授权，正在扫描...");
            tvStatus.setTextColor(Color.parseColor("#2E7D32"));
            startScan();
        }
    }

    private final ServiceConnection userServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            shizukuService = IUserService.Stub.asInterface(binder);
            shizukuBound = true;
            runOnUiThread(() -> {
                tvStatus.setText("Shizuku 已连接，点击扫描");
                tvStatus.setTextColor(Color.parseColor("#2E7D32"));
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            shizukuService = null;
            shizukuBound = false;
        }
    };

    private static final String PREFS_NAME = "ets100_prefs";
    private static final String KEY_CLEANED = "cache_cleaned";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupUI();
        adapter = new ResultAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener);

        btnScan.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_PERMISSION);
                return;
            }
            startScan();
        });
        etManualPath.setText(BASE_PATH);
        directPath = BASE_PATH;

        startScan();
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            shizukuReady = Shizuku.pingBinder();
        } catch (Exception e) {
            shizukuReady = false;
        }

        // Shizuku 可用且有权限，绑定 UserService
        if (shizukuReady && !shizukuBound) {
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    bindShizukuService();
                } else {
                    Shizuku.requestPermission(REQUEST_PERMISSION);
                }
            } catch (Exception e) {
                // Shizuku 不可用
            }
        }

        // 首次启动清理 ETS 缓存
        if (!getPrefs().getBoolean(KEY_CLEANED, false)) {
            cleanEtsCache();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener);
        if (shizukuBound) {
            try {
                Shizuku.unbindUserService(
                        new Shizuku.UserServiceArgs(new ComponentName(getPackageName(), MyUserService.class.getName())),
                        userServiceConnection, true);
            } catch (Exception ignored) {}
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PERMISSION) {
            startScan();
        }
    }

    private void bindShizukuService() {
        Shizuku.UserServiceArgs args = new Shizuku.UserServiceArgs(
                new ComponentName(getPackageName(), MyUserService.class.getName()))
                .daemon(false)
                .processNameSuffix("service");
        Shizuku.bindUserService(args, userServiceConnection);
    }

    private void startScan() {
        String scanPath = (directPath != null) ? directPath : BASE_PATH;

        String mode = shizukuBound ? "Shizuku" : "root";
        tvStatus.setText("扫描中 (" + mode + "): " + scanPath);
        tvStatus.setTextColor(Color.parseColor("#1565C0"));
        progressBar.setVisibility(View.VISIBLE);
        btnScan.setEnabled(false);
        tvEmpty.setVisibility(View.GONE);

        executor.execute(() -> {
            List<ScanResult> allResults = new ArrayList<>();
            boolean found = false;

            // 方案1: su root
            if (!found) found = trySuFind(scanPath, allResults);

            // 方案2: su 备用路径
            if (!found && scanPath.startsWith("/storage/emulated/0/")) {
                String altPath = "/data/media/0/" + scanPath.substring("/storage/emulated/0/".length());
                found = trySuFind(altPath, allResults);
            }

            // 方案3: Shizuku UserService
            if (!found && shizukuBound && shizukuService != null) {
                found = tryShizukuFind(scanPath, allResults);
                if (!found && scanPath.startsWith("/storage/emulated/0/")) {
                    String altPath = "/data/media/0/" + scanPath.substring("/storage/emulated/0/".length());
                    found = tryShizukuFind(altPath, allResults);
                }
            }

            // 方案4: File API 兜底
            if (!found) {
                tryFileApi(scanPath, allResults);
                if (!allResults.isEmpty()) found = true;
            }

            boolean finalFound = found;
            List<ScanResult> finalResults = new ArrayList<>(allResults);
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                btnScan.setEnabled(true);
                showResults(finalResults);
                // 只有完全没找到文件时才显示提示
                if (finalResults.isEmpty()) {
                    tvEmpty.setText("未找到文件\n\n请确保：\n• 已安装 Shizuku 并启动\n• 或设备已 root\n• 点击「扫描解析」授权");
                    tvEmpty.setVisibility(View.VISIBLE);
                }
            });
        });
    }

    private android.content.SharedPreferences getPrefs() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    // ========== 清理 ETS 缓存 ==========

    private void cleanEtsCache() {
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setMessage("正在清理E听说缓存...")
                .setCancelable(false)
                .create();
        dialog.show();

        String resDir = "/storage/emulated/0/Android/data/com.ets100.secondary/files/Download/ETS_secondary/resource";

        executor.execute(() -> {
            // 等 Shizuku 服务绑定
            for (int i = 0; i < 10 && !shizukuBound; i++) {
                try { Thread.sleep(300); } catch (Exception ignored) {}
            }
            boolean ok = false;
            // 先尝试 su
            try {
                Process p = Runtime.getRuntime().exec(new String[]{
                        "su", "-c",
                        "rm -rf " + resDir + " && mkdir -p " + resDir
                });
                p.waitFor();
                ok = (p.exitValue() == 0);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "su 失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
            // su 失败则尝试 Shizuku
            if (!ok && shizukuBound && shizukuService != null) {
                try {
                    shizukuService.exec("rm -rf " + resDir + " && mkdir -p " + resDir);
                    ok = true;
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Shizuku 失败: " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            }
            boolean finalOk = ok;
            runOnUiThread(() -> {
                dialog.dismiss();
                if (!finalOk) {
                    Toast.makeText(this, "清理失败，请手动授予 su 权限后重试", Toast.LENGTH_LONG).show();
                    return; // 不设 flag，下次还会重试
                }
                getPrefs().edit().putBoolean(KEY_CLEANED, true).apply();
                Toast.makeText(this, "清理完成，正在重启...", Toast.LENGTH_SHORT).show();
                android.content.Intent intent = getPackageManager()
                        .getLaunchIntentForPackage(getPackageName());
                if (intent != null) {
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                            | android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                }
            });
        });
    }

    // ========== su root 方案 ==========

    private boolean trySuFind(String path, List<ScanResult> allResults) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "find '" + path + "' -type f -name 'content.json' 2>&1"
            });
            BufferedReader finder = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder findOutput = new StringBuilder();
            String line;
            while ((line = finder.readLine()) != null) findOutput.append(line).append("\n");
            while ((line = errReader.readLine()) != null) {} // discard stderr
            finder.close();
            errReader.close();
            int exitCode = process.waitFor();

            String findStr = findOutput.toString().trim();

            if (exitCode == 0 && findStr.length() > 0) {
                for (String p : findStr.split("\n")) {
                    p = p.trim();
                    if (p.isEmpty()) continue;
                    try {
                        String json = readTextFileSu(p);
                        if (json != null && !json.isEmpty()) {
                            List<QuestionResult> results = JsonParser.parse(json);
                            allResults.add(new ScanResult(p, results));
                        } else {
                            allResults.add(new ScanResult(p, "读取失败(su)"));
                        }
                    } catch (Exception e) {
                        allResults.add(new ScanResult(p, "解析失败: " + e.getMessage()));
                    }
                }
                return true;
            }
        } catch (Exception e) {
            // su 不可用
        }
        return false;
    }

    private String readTextFileSu(String path) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{
                    "su", "-c", "cat '" + path + "'"
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            process.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ========== Shizuku UserService 方案 ==========

    private boolean tryShizukuFind(String path, List<ScanResult> allResults) {
        try {
            String findResult = shizukuService.exec(
                    "find '" + path + "' -type f -name 'content.json'");
            if (findResult == null || findResult.trim().isEmpty()) return false;

            for (String p : findResult.split("\n")) {
                p = p.trim();
                if (p.isEmpty()) continue;
                try {
                    String json = shizukuService.exec("cat '" + p + "'");
                    if (json != null && !json.isEmpty()) {
                        List<QuestionResult> results = JsonParser.parse(json);
                        allResults.add(new ScanResult(p, results));
                    } else {
                        allResults.add(new ScanResult(p, "读取失败(Shizuku)"));
                    }
                } catch (Exception e) {
                    allResults.add(new ScanResult(p, "解析失败: " + e.getMessage()));
                }
            }
            return true;
        } catch (Exception e) {
            // Shizuku 不可用
        }
        return false;
    }

    // ========== File API 兜底 ==========

    private void tryFileApi(String path, List<ScanResult> allResults) {
        try {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                scanDirRecursive(dir, allResults);
            }
        } catch (Exception ignored) {}
    }

    private void scanDirRecursive(File dir, List<ScanResult> results) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirRecursive(f, results);
            } else if ("content.json".equals(f.getName())) {
                try {
                    String json = readTextFile(f.getAbsolutePath());
                    if (json != null && !json.isEmpty()) {
                        List<QuestionResult> parsed = JsonParser.parse(json);
                        results.add(new ScanResult(f.getAbsolutePath(), parsed));
                    } else {
                        results.add(new ScanResult(f.getAbsolutePath(), "读取失败"));
                    }
                } catch (Exception e) {
                    results.add(new ScanResult(f.getAbsolutePath(), "解析失败: " + e.getMessage()));
                }
            }
        }
    }

    private String readTextFile(String path) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(new File(path))));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ========== 显示结果 ==========

    private void showResults(List<ScanResult> results) {
        if (results.isEmpty()) {
            tvEmpty.setText("未找到 content.json");
            tvEmpty.setVisibility(View.VISIBLE);
            tvStatus.setText("扫描完成：0 道题");
            tvStatus.setTextColor(Color.parseColor("#E65100"));
            return;
        }

        tvEmpty.setVisibility(View.GONE);

        int totalFiles = 0;
        int totalQuestions = 0;
        for (ScanResult sr : results) {
            if (sr.results != null) {
                totalFiles++;
                totalQuestions += sr.results.size();
            }
        }

        if (totalQuestions > 0) {
            tvStatus.setText(String.format("扫描完成：%d 个文件，%d 条内容", totalFiles, totalQuestions));
            tvStatus.setTextColor(Color.parseColor("#2E7D32"));
        } else {
            tvStatus.setText("扫描完成：" + results.size() + " 个结果，0 条内容");
            tvStatus.setTextColor(Color.parseColor("#E65100"));
        }

        // 构建带分组标记的列表
        List<QuestionResult> flat = new ArrayList<>();
        int groupId = 0;
        for (ScanResult sr : results) {
            groupId++;
            if (sr.results != null) {
                for (QuestionResult qr : sr.results) {
                    qr.setGroup(groupId, sr.path);
                    flat.add(qr);
                }
            }
            if (sr.error != null) {
                QuestionResult err = QuestionResult.fromError(sr.error);
                err.setGroup(groupId, sr.path);
                flat.add(err);
            }
        }
        adapter.setItems(flat);
        recyclerView.setVisibility(View.VISIBLE);
    }

    // ========== UI ==========

    private void setupUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#D0D8E0"));

        // 顶部栏 — 毛玻璃效果
        LinearLayout topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setPadding(28, 24, 28, 24);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(Color.parseColor("#801565C0")); // 50% 透明蓝
        topBg.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 32, 32});
        topBar.setBackground(topBg);
        topBar.setElevation(6);

        btnScan = new Button(this);
        btnScan.setText("🔍 扫描解析");
        btnScan.setTextColor(Color.WHITE);
        btnScan.setTextSize(15);
        btnScan.setTypeface(null, Typeface.BOLD);
        // 毛玻璃按钮：半透明背景 + 圆角
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(Color.parseColor("#33FFFFFF")); // 白色半透明
        btnBg.setCornerRadius(40);
        btnScan.setBackground(btnBg);
        btnScan.setPadding(32, 0, 32, 0);
        btnScan.setElevation(2);
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        btnScan.setLayoutParams(btnLp);
        topBar.addView(btnScan);
        root.addView(topBar);

        // 状态栏
        tvStatus = new TextView(this);
        tvStatus.setText("正在检查权限...");
        tvStatus.setTextColor(Color.parseColor("#666666"));
        tvStatus.setTextSize(12);
        tvStatus.setPadding(32, 14, 32, 14);
        tvStatus.setBackgroundColor(Color.parseColor("#F0F2F5"));
        root.addView(tvStatus);

        // 路径栏 — 毛玻璃效果
        LinearLayout pathBar = new LinearLayout(this);
        pathBar.setOrientation(LinearLayout.HORIZONTAL);
        pathBar.setPadding(20, 12, 20, 12);
        GradientDrawable pathBg = new GradientDrawable();
        pathBg.setColor(Color.parseColor("#99FFFFFF")); // 60% 透明白
        pathBg.setCornerRadius(20);
        pathBg.setStroke(1, Color.parseColor("#40FFFFFF")); // 白边更明显
        pathBar.setBackground(pathBg);
        pathBar.setElevation(8);
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pathLp.setMargins(16, 4, 16, 0);
        pathBar.setLayoutParams(pathLp);

        etManualPath = new EditText(this);
        etManualPath.setHint("搜索路径");
        etManualPath.setTextSize(11);
        etManualPath.setSingleLine(true);
        etManualPath.setTextColor(Color.parseColor("#333333"));
        etManualPath.setHintTextColor(Color.parseColor("#999999"));
        etManualPath.setBackgroundColor(Color.TRANSPARENT);
        etManualPath.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        pathBar.addView(etManualPath);
        root.addView(pathBar);

        // 进度条
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        progressLp.setMargins(20, 4, 20, 4);
        progressBar.setLayoutParams(progressLp);
        root.addView(progressBar);

        // 结果列表
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        recyclerView.setPadding(12, 8, 12, 12);
        recyclerView.setClipToPadding(false);
        root.addView(recyclerView);

        // 空状态
        tvEmpty = new TextView(this);
        tvEmpty.setText("正在扫描...");
        tvEmpty.setTextColor(Color.parseColor("#999999"));
        tvEmpty.setTextSize(15);
        tvEmpty.setGravity(Gravity.CENTER);
        tvEmpty.setVisibility(View.VISIBLE);
        tvEmpty.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(tvEmpty);

        setContentView(root);
    }

    // ========== 数据类 ==========

    static class ScanResult {
        final String path;
        final List<QuestionResult> results;
        final String error;

        ScanResult(String path, List<QuestionResult> results) {
            this.path = path;
            this.results = results;
            this.error = null;
        }

        ScanResult(String path, String error) {
            this.path = path;
            this.error = error;
            this.results = null;
        }
    }
}
