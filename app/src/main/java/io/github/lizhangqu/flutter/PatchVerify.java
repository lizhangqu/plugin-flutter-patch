package io.github.lizhangqu.flutter;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-22 14:04
 */
public class PatchVerify {
    /**
     * 验证签名
     */
    public static boolean verifySign(Context context, File path) {
        if (context == null || path == null || !path.exists()) {
            return false;
        }
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(path);
            JarEntry jarEntry = jarFile.getJarEntry("manifest.json");
            if (null == jarEntry) {// no code
                return false;
            }
            loadDigestes(jarFile, jarEntry);
            Certificate[] certs = jarEntry.getCertificates();
            if (certs == null) {
                return false;
            }
            return check(context, certs);
        } catch (Throwable e) {
            return false;
        } finally {
            try {
                if (jarFile != null) {
                    jarFile.close();
                }
            } catch (Throwable e) {
            }
        }
    }

    private static void loadDigestes(JarFile jarFile, JarEntry je) throws IOException {
        InputStream is = null;
        try {
            is = jarFile.getInputStream(je);
            byte[] bytes = new byte[8192];
            while (is.read(bytes) > 0) {
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * 用apk公钥去验证patch
     *
     * @param context
     * @param certs
     * @return
     * @throws Exception
     */
    private static boolean check(Context context, Certificate[] certs) throws Exception {
        PublicKey apkPublicKey = getApkPublicKey(context);
        if (apkPublicKey == null) {
            return false;
        }
        boolean result = true;
        if (certs != null && certs.length > 0) {
            for (int i = certs.length - 1; i >= 0; i--) {
                try {
                    certs[i].verify(apkPublicKey);
                } catch (Exception e) {
                    result = false;
                    e.printStackTrace();
                }
            }
        }
        return result;
    }

    /**
     * 获得当前安装apk的公钥
     *
     * @param context
     * @return
     */
    private static PublicKey getApkPublicKey(Context context) {
        if (context == null) {
            return null;
        }
        try {
            PackageManager pm = context.getPackageManager();
            String packageName = context.getPackageName();
            PackageInfo packageInfo = pm.getPackageInfo(packageName,
                    PackageManager.GET_SIGNATURES);
            CertificateFactory certFactory = CertificateFactory
                    .getInstance("X.509");
            ByteArrayInputStream stream = new ByteArrayInputStream(
                    packageInfo.signatures[0].toByteArray());
            X509Certificate cert = (X509Certificate) certFactory
                    .generateCertificate(stream);
            PublicKey publicKey = cert.getPublicKey();
            return publicKey;
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return null;
    }
}
