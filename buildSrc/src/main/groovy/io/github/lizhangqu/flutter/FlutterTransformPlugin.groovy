package io.github.lizhangqu.flutter

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-22 12:52
 */
class FlutterTransformPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        BaseExtension android = project.getExtensions().getByType(BaseExtension.class)
        def transform = new CustomClassTransform(project, DynamicPatchRedirectTransform.class)
        android.registerTransform(transform)

        project.getExtensions().create("flutterTransform", FlutterTransformExtension.class)

    }
}