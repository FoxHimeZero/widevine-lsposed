package com.widevine.spoof;

import android.util.Log;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.Set;

public class TargetUidManager {

    private static final String TAG = "WidevineSpoof/Uid";
    private static final String CONFIG_FILE = "/data/local/tmp/widevine-spoof/target_uids.txt";
    private static Set<Integer> sTargetUids = null;
    private static long sLastLoad = 0;

    public static boolean isTarget(int uid) {
        if (sTargetUids == null || System.currentTimeMillis() - sLastLoad > 10000) {
            loadConfig();
        }
        return sTargetUids != null && sTargetUids.contains(uid);
    }

    private static void loadConfig() {
        Set<Integer> uids = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    uids.add(Integer.parseInt(line));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            Log.w(TAG, "读取 UID 配置失败: " + e.getMessage());
        }

        if (uids.isEmpty()) {
            uids.add(-1);
        }
        sTargetUids = uids;
        sLastLoad = System.currentTimeMillis();
        Log.i(TAG, "已加载 " + uids.size() + " 个目标 UID");
    }
}
