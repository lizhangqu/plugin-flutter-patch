package io.github.lizhangqu.flutter

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Plugin

/**
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-22 12:52
 */
class FlutterPatchPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        if (!project.getPlugins().hasPlugin('com.android.application')) {
            throw new GradleException('apply plugin: \'com.android.application\' is required')
        }
        project.android.applicationVariants.all { def variant ->
            def taskConfiguration = new FlutterPatchTask.ConfigAction(project, variant)
            project.getTasks().create(taskConfiguration.getName(), taskConfiguration.getType(), taskConfiguration)
        }
    }
}
