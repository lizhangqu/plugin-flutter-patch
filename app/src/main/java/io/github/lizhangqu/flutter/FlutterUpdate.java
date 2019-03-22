package io.github.lizhangqu.flutter;

import android.content.Context;
import android.support.annotation.Keep;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.math.BigInteger;
import java.security.MessageDigest;

/**
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-22 13:51
 */
@Keep
public class FlutterUpdate {

    @Keep
    public static class FlutterPatch {
        public String url;
        public String md5;
        public String downloadMode;
        public String installMode;

        @Override
        public String toString() {
            return "FlutterPatch{" +
                    "url='" + url + '\'' +
                    ", md5='" + md5 + '\'' +
                    ", downloadMode='" + downloadMode + '\'' +
                    ", installMode='" + installMode + '\'' +
                    '}';
        }
    }

    private static FlutterPatch flutterPatch = null;


    private static String getFileMD5(File file) {
        if (!file.exists()) {
            return null;
        }
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1024 * 1024];
            int numRead = 0;
            while ((numRead = fileInputStream.read(buffer)) > 0) {
                md5.update(buffer, 0, numRead);
            }
            return String.format("%032x", new BigInteger(1, md5.digest())).toLowerCase();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }


    private static String getInstalledPatchMd5(Context context) {
        File file = new File(context.getFilesDir().toString() + "/patch.zip");
        return getFileMD5(file);
    }

    private static void checkSign(Context context) {
        File file = new File(context.getFilesDir().toString() + "/patch.zip");
        if (!file.exists()) {
            return;
        }
        boolean success = PatchVerify.verifySign(context, file);
        if (!success) {

            //删除patch文件
            file.delete();

            //删除时间戳文件重新释放
            deleteFiles(context);
        }

    }

    private static String[] getExistingTimestamps(File dataDir) {
        return dataDir.list(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith("res_timestamp-");
            }
        });
    }

    private static void deleteFiles(Context context) {
        final File dataDir = new File(context.getDir("flutter", Context.MODE_PRIVATE).getPath(););
        final String[] existingTimestamps = getExistingTimestamps(dataDir);
        if (existingTimestamps == null) {
            return;
        }
        for (String timestamp : existingTimestamps) {
            new File(dataDir, timestamp).delete();
        }
    }


    private static void ensureConfig(Context context) {
        if (flutterPatch != null) {
            return;
        }
        //TODO 此处是自定义逻辑，根据本地配置持久化的远程配置，反序列化创建flutterPatch即可，这里mock一个
        FlutterPatch patch = new FlutterPatch();
        patch.url = "下载patch的url";
        patch.md5 = "下载patch的md5";
        patch.downloadMode = "patch的下载模式";
        patch.installMode = "patch的安装模式";

        flutterPatch = patch;

        Log.e("FlutterUpdate", "flutterPatch:" + flutterPatch);

    }

    @Keep
    public static String getDownloadURL(Context context) {
        ensureConfig(context);
        if (flutterPatch == null) {
            Log.e("FlutterUpdate", "flutterPatch == null");
            return null;
        }
        checkSign(context);
        String installedPatchMd5 = getInstalledPatchMd5(context);
        if (installedPatchMd5 != null && installedPatchMd5.equalsIgnoreCase(flutterPatch.md5)) {
            Log.e("FlutterUpdate", "md5 equals:" + flutterPatch.md5);
            return null;
        }

        return flutterPatch.url;
    }

    @Keep
    public static String getDownloadMode(Context context) {
        ensureConfig(context);
        if (flutterPatch == null) {
            Log.e("FlutterUpdate", "flutterPatch == null");
            return null;
        }
        return flutterPatch.downloadMode;
    }

    @Keep
    public static String getInstallMode(Context context) {
        ensureConfig(context);
        if (flutterPatch == null) {
            Log.e("FlutterUpdate", "flutterPatch == null");
            return null;
        }
        return flutterPatch.installMode;
    }

}
