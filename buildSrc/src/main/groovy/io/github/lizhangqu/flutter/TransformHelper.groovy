package io.github.lizhangqu.flutter

import com.android.build.gradle.AppExtension
import com.android.build.gradle.api.BaseVariant
import javassist.ClassPool
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 *
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-14 14:11
 */
public class TransformHelper {

    public static String getAndroidGradlePluginVersionCompat() {
        String version = null
        try {
            Class versionModel = Class.forName("com.android.builder.model.Version")
            def versionFiled = versionModel.getDeclaredField("ANDROID_GRADLE_PLUGIN_VERSION")
            versionFiled.setAccessible(true)
            version = versionFiled.get(null)
        } catch (Exception e) {

        }
        return version
    }

    /**
     * 反射获取枚举类，静态方法
     */
    public static <T> T resolveEnumValue(String value, Class<T> type) {
        for (T constant : type.getEnumConstants()) {
            if (constant.toString().equalsIgnoreCase(value)) {
                return constant
            }
        }
        return null
    }


    public static def getCompileLibraries(Project project, String variantName) {

        BaseVariant foundVariant = null

        def variants = null;
        if (project.plugins.hasPlugin('com.android.application')) {
            variants = project.android.getApplicationVariants()
        } else if (project.plugins.hasPlugin('com.android.library')) {
            variants = project.android.getLibraryVariants()
        }

        variants?.all { BaseVariant variant ->
            if (variant.getName() == variantName) {
                foundVariant = variant
            }
        }
        if (foundVariant == null) {
            throw new GradleException("variant ${variantName} not found")
        }

        def variantData = foundVariant.getMetaClass().getProperty(foundVariant, 'variantData')

        if (getAndroidGradlePluginVersionCompat() >= '3.0.0') {
            Object compileClasspath = resolveEnumValue("COMPILE_CLASSPATH", Class.forName('com.android.build.gradle.internal.publishing.AndroidArtifacts$ConsumedConfigType'))
            Object allArtifactScope = resolveEnumValue("ALL", Class.forName('com.android.build.gradle.internal.publishing.AndroidArtifacts$ArtifactScope'))
            Object classes = resolveEnumValue("CLASSES", Class.forName('com.android.build.gradle.internal.publishing.AndroidArtifacts$ArtifactType'))
            return variantData.getScope().getArtifactFileCollection(compileClasspath, allArtifactScope, classes)
        } else {
            try {
                return foundVariant.getCompileLibraries()
            } catch (Exception e) {
                //maybe with com.android.library for aar
                return variantData.getScope().getGlobalScope().getAndroidBuilder().getCompileClasspath(variantData.getVariantConfiguration());
            }
        }
    }


    public static void updateClassPath(ClassPool classPool, Project project, String variantName) {
        getCompileLibraries(project, variantName)?.each {
            try {
                classPool.insertClassPath(it.absolutePath)
            } catch (Exception e) {
            }
        }
        try {
            def javacTask = project.tasks.findByName("compile${variantName.capitalize()}JavaWithJavac")
            classPool.insertClassPath(javacTask.getDestinationDir().getAbsolutePath())
        } catch (Exception e) {
        }
        AppExtension android = project.getExtensions().getByType(AppExtension.class)
        android?.getBootClasspath()?.each {
            try {
                classPool.insertClassPath(it.absolutePath)
            } catch (Exception e) {
            }
        }
    }

    public static void copy(InputStream inputStream, OutputStream outputStream) {
        int length = -1
        byte[] buffer = new byte[4096]
        while ((length = inputStream.read(buffer, 0, buffer.length)) != -1) {
            outputStream.write(buffer, 0, length)
            outputStream.flush()
        }
    }

}
