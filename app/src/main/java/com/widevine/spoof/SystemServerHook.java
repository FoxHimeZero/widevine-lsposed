package com.widevine.spoof;

import android.media.MediaDrm;
import android.os.Binder;
import android.util.Log;
import java.io.FileInputStream;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemServerHook implements IXposedHookLoadPackage {

    private static final String TAG = "WidevineSpoof/System";
    private static final String ID_FILE = "/data/local/tmp/widevine-spoof/id";
    private static byte[] sFakeId = null;

    private static byte[] loadFakeId() {
        if (sFakeId != null) return sFakeId;
        try (FileInputStream fis = new FileInputStream(ID_FILE)) {
            byte[] data = new byte[16];
            if (fis.read(data) == 16) {
                sFakeId = data;
                Log.i(TAG, "SystemServer 已加载伪造 ID");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载 ID 失败: " + e.getMessage());
        }
        return sFakeId;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) return;

        Log.i(TAG, "进入 system_server，开始 Hook");

        try {
            Class<?> mediaDrmClass = Class.forName("android.media.MediaDrm");

            XposedBridge.hookAllMethods(mediaDrmClass, "getPropertyByteArray",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        String prop = (String) param.args[0];
                        if (!MediaDrm.PROPERTY_DEVICE_UNIQUE_ID.equals(prop))
                            return;

                        int callingUid = Binder.getCallingUid();
                        if (TargetUidManager.isTarget(callingUid)) {
                            byte[] fakeId = loadFakeId();
                            if (fakeId != null) {
                                param.setResult(fakeId.clone());
                                Log.d(TAG, "已为 UID " + callingUid + " 替换 ID");
                            }
                        }
                    }
                });

            Log.i(TAG, "SystemServer Hook 安装成功");

        } catch (Throwable t) {
            Log.e(TAG, "Hook 失败: " + t.getMessage());
        }
    }
}
