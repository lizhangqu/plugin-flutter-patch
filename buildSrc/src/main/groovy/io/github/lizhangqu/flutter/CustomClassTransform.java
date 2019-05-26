package io.github.lizhangqu.flutter;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Files;

import org.gradle.api.Project;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 自定义类转换
 *
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-14 13:55
 */
public class CustomClassTransform extends Transform {

    @NonNull
    private final String name;

    @NonNull
    private final Project project;

    @NonNull
    private final Class<?> clazz;

    public CustomClassTransform(Project project, @NonNull Class<?> clazz) {
        this.project = project;
        this.name = clazz.getName().replaceAll("\\.", "_");
        this.clazz = clazz;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<QualifiedContent.ContentType> getOutputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @NonNull
    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        if (project.getPlugins().hasPlugin("com.android.application")) {
            return TransformManager.SCOPE_FULL_PROJECT;
        } else {
            return ImmutableSet.of(QualifiedContent.Scope.PROJECT);
        }
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(@NonNull TransformInvocation invocation)
            throws IOException {
        final TransformOutputProvider outputProvider = invocation.getOutputProvider();
        assert outputProvider != null;

        if (!invocation.isIncremental()) {
            outputProvider.deleteAll();
        }

        Consumer<InputStream, OutputStream> function = loadTransformFunction(clazz);
        String variantName = invocation.getContext().getVariantName();

        for (TransformInput ti : invocation.getInputs()) {
            for (JarInput jarInput : ti.getJarInputs()) {
                File inputJar = jarInput.getFile();
                File outputJar =
                        outputProvider.getContentLocation(
                                jarInput.getName(),
                                jarInput.getContentTypes(),
                                jarInput.getScopes(),
                                Format.JAR);

                if (invocation.isIncremental()) {
                    switch (jarInput.getStatus()) {
                        case NOTCHANGED:
                            break;
                        case ADDED:
                        case CHANGED:
                            transformJar(function, variantName, inputJar, outputJar);
                            break;
                        case REMOVED:
                            FileUtils.delete(outputJar);
                            break;
                    }
                } else {
                    transformJar(function, variantName, inputJar, outputJar);
                }
            }
            for (DirectoryInput di : ti.getDirectoryInputs()) {
                File inputDir = di.getFile();
                File outputDir =
                        outputProvider.getContentLocation(
                                di.getName(),
                                di.getContentTypes(),
                                di.getScopes(),
                                Format.DIRECTORY);
                if (invocation.isIncremental()) {
                    for (Map.Entry<File, Status> entry : di.getChangedFiles().entrySet()) {
                        File inputFile = entry.getKey();
                        switch (entry.getValue()) {
                            case NOTCHANGED:
                                break;
                            case ADDED:
                            case CHANGED:
                                if (!inputFile.isDirectory()
                                        && inputFile.getName()
                                        .endsWith(SdkConstants.DOT_CLASS)) {
                                    String filePath = FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir);
                                    File out = toOutputFile(outputDir, inputDir, inputFile);
                                    transformFile(function, variantName, filePath, inputFile, out);
                                }
                                break;
                            case REMOVED:
                                File outputFile = toOutputFile(outputDir, inputDir, inputFile);
                                FileUtils.deleteIfExists(outputFile);
                                break;
                        }
                    }
                } else {
                    for (File input : FileUtils.getAllFiles(inputDir)) {
                        if (input.getName().endsWith(SdkConstants.DOT_CLASS)) {
                            String filePath = FileUtils.relativePossiblyNonExistingPath(input, inputDir);
                            File out = toOutputFile(outputDir, inputDir, input);
                            transformFile(function, variantName, filePath, input, out);
                        }
                    }
                }
            }
        }
    }

    private Consumer<InputStream, OutputStream> loadTransformFunction(Class<?> clazz) {
        Consumer consumer = null;
        try {
            consumer = clazz.asSubclass(Consumer.class).getConstructor(Project.class).newInstance(project);
        } catch (Exception e) {
            try {
                consumer = clazz.asSubclass(Consumer.class).newInstance();
            } catch (Exception e1) {

            }
        }

        if (consumer == null) {
            throw new IllegalStateException(
                    "Custom transform does not provide a BiConsumer to apply");
        }

        Consumer uncheckedFunction = consumer;
        // Validate the generic arguments are valid:
        Type[] types = uncheckedFunction.getClass().getGenericInterfaces();
        for (Type type : types) {
            if (type instanceof ParameterizedType) {
                ParameterizedType generic = (ParameterizedType) type;
                Type[] args = generic.getActualTypeArguments();
                if (generic.getRawType().equals(Consumer.class)
                        && args.length == 2
                        && args[0].equals(InputStream.class)
                        && args[1].equals(OutputStream.class)) {
                    return (Consumer<InputStream, OutputStream>) uncheckedFunction;
                }
            }
        }
        throw new IllegalStateException(
                "Custom transform must provide a BiConsumer<InputStream, OutputStream>");
    }

    private void transformJar(
            Consumer<InputStream, OutputStream> function, String variantName, File inputJar, File outputJar)
            throws IOException {
        Files.createParentDirs(outputJar);
        try (FileInputStream fis = new FileInputStream(inputJar);
             ZipInputStream zis = new ZipInputStream(fis);
             FileOutputStream fos = new FileOutputStream(outputJar);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null && !entry.getName().contains("../")) {
                if (!entry.isDirectory() && entry.getName().endsWith(SdkConstants.DOT_CLASS)) {
                    zos.putNextEntry(new ZipEntry(entry.getName()));
                    apply(function, variantName, entry.getName(), zis, zos);
                } else {
                    // Do not copy resources
                }
                entry = zis.getNextEntry();
            }
        }
    }

    private void transformFile(
            Consumer<InputStream, OutputStream> function, String variantName, String path, File inputFile, File outputFile)
            throws IOException {
        Files.createParentDirs(outputFile);
        try (FileInputStream fis = new FileInputStream(inputFile);
             FileOutputStream fos = new FileOutputStream(outputFile)) {
            apply(function, variantName, path, fis, fos);
        }
    }

    @NonNull
    private static File toOutputFile(File outputDir, File inputDir, File inputFile) {
        return new File(outputDir, FileUtils.relativePossiblyNonExistingPath(inputFile, inputDir));
    }

    private void apply(
            Consumer<InputStream, OutputStream> function, String variantName, String path, InputStream input, OutputStream out)
            throws IOException {
        try {
            function.accept(variantName, path, input, out);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
