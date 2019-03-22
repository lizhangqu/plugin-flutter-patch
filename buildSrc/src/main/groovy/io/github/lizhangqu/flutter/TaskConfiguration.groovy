package io.github.lizhangqu.flutter

import com.android.annotations.NonNull
import org.gradle.api.Action
import org.gradle.api.Project

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * @author lizhangqu
 * @version V1.0
 * @since 2019-02-28 20:08
 */


public abstract class TaskConfiguration<T> implements Action<T> {
    protected Project project

    public TaskConfiguration(Project project) {
        this.project = project
    }
    /**
     * Return the name of the task to be configured.
     */
    @NonNull
    abstract String getName();

    /**
     * Return the class type of the task to be configured.
     */
    Class<T> getType() {
        Type rawType = null;
        Type type = this.getClass().getGenericSuperclass();
        if (type instanceof ParameterizedType) {
            Type[] actualTypeArguments = ((ParameterizedType) type).getActualTypeArguments();
            if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                rawType = actualTypeArguments[0];
            }
        } else {
            Type[] genericInterfaces = this.getClass().getGenericInterfaces();
            if (genericInterfaces != null && genericInterfaces.length > 0) {
                Type[] actualTypeArguments = ((ParameterizedType) genericInterfaces[0]).getActualTypeArguments();
                if (actualTypeArguments != null && actualTypeArguments.length > 0) {
                    rawType = actualTypeArguments[0];
                }
            }
        }
        return rawType
    }

    /**
     * Configure the given newly-created task object.
     */
    @Override
    abstract void execute(@NonNull T task);
}