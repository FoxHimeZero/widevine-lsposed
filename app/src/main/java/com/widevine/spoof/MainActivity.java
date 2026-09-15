package com.widevine.spoof;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {

    private static final String ID_FILE = "/data/adb/widevine-spoof/id";
    private static final String UID_FILE = "/data/adb/widevine-spoof/target_uids.txt";

    private TextView tvCurrentId;
    private TextView tvStatus;
    private LinearLayout containerUids;
    private EditText etUid;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvCurrentId = findViewById(R.id.tvCurrentId);
        tvStatus = findViewById(R.id.tvStatus);
        containerUids = findViewById(R.id.containerUids);
        etUid = findViewById(R.id.etUid);

        findViewById(R.id.btnRandomize).setOnClickListener(v -> randomizeId());
        findViewById(R.id.btnAddUid).setOnClickListener(v -> addUid());

        refreshId();
        refreshUids();
    }

    /** 读取当前 ID 并显示为 hex */
    private void refreshId() {
        new Thread(() -> {
            String hex = readFileHex(ID_FILE);
            uiHandler.post(() -> {
                if (hex == null) {
                    tvCurrentId.setText("❌ 未找到 ID 文件\n请确认 Zygisk 模块已刷入并重启过");
                } else {
                    tvCurrentId.setText("0x" + hex);
                }
            });
        }).start();
    }

    /** 一键换 ID */
    private void randomizeId() {
        showStatus("正在生成新 ID...");
        new Thread(() -> {
            // 用 su 权限写文件并重启 DRM 服务
            boolean ok = execSu(
                "head -c 16 /dev/urandom > " + ID_FILE + " && " +
                "chmod 644 " + ID_FILE + " && " +
                "killall android.hardware.drm-service.widevine 2>/dev/null; " +
                "sleep 1"
            );

            uiHandler.post(() -> {
                if (ok) {
                    showStatus("✅ 新 ID 已生成，重启目标应用后生效");
                    Toast.makeText(this, "ID 已更换", Toast.LENGTH_SHORT).show();
                } else {
                    showStatus("❌ 生成失败，请确认已授予 Root 权限");
                }
                refreshId();
            });
        }).start();
    }

    /** 刷新 UID 列表 */
    private void refreshUids() {
        new Thread(() -> {
            List<String> uids = readUids();
            uiHandler.post(() -> {
                containerUids.removeAllViews();
                if (uids.isEmpty()) {
                    TextView tv = new TextView(this);
                    tv.setText("（空，当前对所有应用生效）");
                    tv.setTextSize(13);
                    tv.setTextColor(0xFF888888);
                    tv.setPadding(8, 8, 8, 8);
                    containerUids.addView(tv);
                    return;
                }
                LayoutInflater inflater = LayoutInflater.from(this);
                for (String uid : uids) {
                    View row = inflater.inflate(
                        android.R.layout.simple_list_item_1, containerUids, false);
                    TextView tv = row.findViewById(android.R.id.text1);
                    tv.setText("UID: " + uid);
                    tv.setTextSize(14);
                    row.setOnLongClickListener(v -> {
                        removeUid(uid);
                        return true;
                    });
                    containerUids.addView(row);
                }
            });
        }).start();
    }

    /** 添加 UID */
    private void addUid() {
        String uid = etUid.getText().toString().trim();
        if (uid.isEmpty()) {
            Toast.makeText(this, "请输入 UID", Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> uids = readUids();
        if (!uids.contains(uid)) {
            uids.add(uid);
            writeUids(uids);
            etUid.setText("");
            refreshUids();
            showStatus("✅ 已添加 UID " + uid);
        } else {
            Toast.makeText(this, "已存在", Toast.LENGTH_SHORT).show();
        }
    }

    /** 移除 UID */
    private void removeUid(String uid) {
        List<String> uids = readUids();
        uids.remove(uid);
        writeUids(uids);
        refreshUids();
        showStatus("已移除 UID " + uid);
    }

    // ============= 文件操作 =============

    private String readFileHex(String path) {
        try (FileInputStream fis = new FileInputStream(path)) {
            byte[] data = new byte[16];
            if (fis.read(data) != 16) return null;
            StringBuilder sb = new StringBuilder();
            for (byte b : data) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> readUids() {
        List<String> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(UID_FILE))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#")) list.add(line);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private void writeUids(List<String> uids) {
        new Thread(() -> {
            try {
                // 用 su 写，避免权限问题
                StringBuilder sb = new StringBuilder();
                for (String u : uids) sb.append(u).append("\n");
                String cmd = "mkdir -p /data/adb/widevine-spoof && " +
                             "echo '" + sb.toString().replace("'", "'\\''") + "' > " + UID_FILE + " && " +
                             "chmod 644 " + UID_FILE;
                execSu(cmd);
            } catch (Exception ignored) {}
        }).start();
    }

    /** 执行 su 命令 */
    private boolean execSu(String cmd) {
        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes(cmd + "\nexit\n");
            os.flush();
            int exit = p.waitFor();
            return exit == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void showStatus(String msg) {
        uiHandler.post(() -> tvStatus.setText(msg));
    }
}
