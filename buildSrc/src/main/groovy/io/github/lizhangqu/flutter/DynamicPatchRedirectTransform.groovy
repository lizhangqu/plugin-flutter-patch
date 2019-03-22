package io.github.lizhangqu.flutter

import javassist.ClassPool
import org.gradle.api.Project

/**
 * 转换dynamic patch下载url
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-14 14:06
 */
class DynamicPatchRedirectTransform implements Consumer<InputStream, OutputStream> {

    private Project project

    DynamicPatchRedirectTransform(Project project) {
        this.project = project
    }

    @Override
    void accept(String variantName, String path, InputStream inputStream, OutputStream outputStream) {
        if (path?.contains("io/flutter/view/ResourceUpdater.class") && project.getPlugins().hasPlugin("com.android.application")) {

            ClassPool classPool = new ClassPool(true)
            TransformHelper.updateClassPath(classPool, project, variantName)

            def ctClass = classPool.makeClass(inputStream, false)
            if (ctClass.isFrozen()) {
                ctClass.defrost()
            }



            FlutterTransformExtension flutterTransformExtension = project.getExtensions().findByType(FlutterTransformExtension.class)
            def downloadUrlctMethod = ctClass.getDeclaredMethod("buildUpdateDownloadURL")
            if (downloadUrlctMethod != null) {
                downloadUrlctMethod.setBody("""
{
return ${flutterTransformExtension.patchClass}.${flutterTransformExtension.downloadUrlMethod}(context);
}
""")
            }

            def downloadModectMethod = ctClass.getDeclaredMethod("getDownloadMode")
            if (downloadModectMethod != null) {
                downloadModectMethod.setBody("""
{
try {
    return io.flutter.view.ResourceUpdater.DownloadMode.valueOf(${
                    flutterTransformExtension.patchClass
                }.${
                    flutterTransformExtension.downloadModeMethod
                }(context));
} catch (Exception e) {
    return io.flutter.view.ResourceUpdater.DownloadMode.ON_RESTART;
}
}
""")
            }

            def installModectMethod = ctClass.getDeclaredMethod("getInstallMode")
            if (installModectMethod != null) {
                installModectMethod.setBody("""
{
try {
    return io.flutter.view.ResourceUpdater.InstallMode.valueOf(${
                    flutterTransformExtension.patchClass
                }.${
                    flutterTransformExtension.installModeMethod
                }(context));
} catch (Exception e) {
    return io.flutter.view.ResourceUpdater.InstallMode.ON_NEXT_RESTART;
}
}
""")
            }

            TransformHelper.copy(new ByteArrayInputStream(ctClass.toBytecode()), outputStream)
        } else {
            TransformHelper.copy(inputStream, outputStream)
        }
    }


}
