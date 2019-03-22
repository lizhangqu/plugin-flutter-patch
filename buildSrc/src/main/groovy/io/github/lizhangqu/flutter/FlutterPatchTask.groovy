package io.github.lizhangqu.flutter

import com.android.build.gradle.AndroidGradleOptions
import com.android.build.gradle.internal.dsl.SigningConfig
import com.android.ide.common.signing.CertificateInfo;
import com.android.ide.common.signing.KeystoreHelper
import com.android.tools.build.apkzlib.zfile.ApkCreator
import com.android.tools.build.apkzlib.zfile.ApkCreatorFactory
import com.android.tools.build.apkzlib.zfile.ApkZFileCreatorFactory
import com.android.tools.build.apkzlib.zfile.NativeLibrariesPackagingMode
import com.android.tools.build.apkzlib.zip.ZFileOptions
import com.android.tools.build.apkzlib.zip.compress.BestAndDefaultDeflateExecutorCompressor
import com.google.common.base.Preconditions;
import com.android.sdklib.BuildToolInfo
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils
import org.zeroturnaround.zip.ZipUtil

import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import java.util.regex.Matcher
import java.util.regex.Pattern
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * flutter patch 生成具体实现
 * @author lizhangqu
 * @version V1.0
 * @since 2019-03-19 13:52
 */
class FlutterPatchTask extends DefaultTask {

    def variant

    @InputFile
    File baseLineApk
    @InputFile
    File apkFile

    @OutputDirectory
    File patchDir
    @OutputFile
    File patchFile

    @TaskAction
    void doFullTaskAction() {
        ZipFile newApk = new ZipFile(apkFile)
        ZipFile oldApk = new ZipFile(baseLineApk)
        GFileUtils.deleteQuietly(patchDir)
        GFileUtils.mkdirs(patchDir)

        boolean ignoreChanges = false
        if (project.hasProperty("ignoreChanges")) {
            ignoreChanges = project.property("ignoreChanges")?.toBoolean()
        }

        newApk.entries().each { ZipEntry newFile ->
            if (newFile.isDirectory()) {
                return
            }
            // Ignore changes to signature manifests.
            if (newFile.getName().startsWith('META-INF/')) {
                return
            }

            ZipEntry oldFile = oldApk.getEntry(newFile.getName())
            if (oldFile != null && oldFile.crc == newFile.crc) {
                return
            }


            boolean usesAot = variant.getName() == 'profile' || variant.getName() == 'release'
            // Only allow certain changes.
            if (!newFile.getName().startsWith('assets/') &&
                    !(usesAot && newFile.getName().endsWith('.so'))) {
                if (ignoreChanges) {
                    return
                }
                throw new GradleException("Error: Dynamic patching doesn't support changes to ${newFile.getName()}.")
            }

            final String name = newFile.getName()
            if (name.contains("_snapshot_") || name.endsWith(".so")) {
                byte[] oldBytes = oldApk.getInputStream(new ZipEntry(name)).bytes
                byte[] newBytes = newApk.getInputStream(new ZipEntry(name)).bytes
                FileUtils.writeByteArrayToFile(new File(patchDir, name + '.bzdiff40'), BSDiff.bsdiff(oldBytes, newBytes))
            } else {
                FileUtils.writeByteArrayToFile(new File(patchDir, name), newApk.getInputStream(newFile).bytes)
            }

        }

        final List<String> checksumFiles = [
                'assets/isolate_snapshot_data',
                'assets/isolate_snapshot_instr',
                'assets/flutter_assets/isolate_snapshot_data',
        ]
        CRC32 checksum = new CRC32()
        for (String fn in checksumFiles) {
            final ZipEntry oldFile = oldApk.getEntry(fn)
            if (oldFile != null) {
                checksum.update(oldApk.getInputStream(oldFile).bytes)
            }
        }
        long baselineChecksum = checksum.getValue()


        def variantData = variant.getMetaClass().getProperty(variant, 'variantData')
        def buildTools = variantData.getScope().getGlobalScope().getAndroidBuilder().getTargetInfo().getBuildTools()
        def stdout = new ByteArrayOutputStream()
        project.exec {
            commandLine new File(buildTools.getPath(BuildToolInfo.PathId.AAPT)), "dump", "badging", baseLineApk.getAbsolutePath()
            standardOutput = stdout
        }

        Pattern versionCodePattern = Pattern.compile("versionCode='(.*?)'", Pattern.MULTILINE)
        Matcher matcher = versionCodePattern.matcher(stdout.toString())
        matcher.find()
        String versionCode = matcher.group(1)
        if (versionCode == null || versionCode.length() == 0) {
            throw new GradleException("versionCode can't find.")
        }

        Gson gson = new GsonBuilder().setPrettyPrinting().create()
        Map<String, Object> manifestValues = new HashMap<>()
        manifestValues.put("baselineChecksum", baselineChecksum)
        manifestValues.put("buildNumber", versionCode)
        manifestValues.put("patchNumber", System.currentTimeMillis())
        String manifestJson = gson.toJson(manifestValues)
        FileUtils.writeByteArrayToFile(new File(patchDir, 'manifest.json'), manifestJson.getBytes())
        ZipUtil.pack(patchDir, patchFile)

        SigningConfig signingConfig = project.tasks.findByName("package${variant.name.capitalize()}").signingConfig
        File signOut = new File(patchFile.getParentFile(), "signed_" + patchFile.getName())
        //sign patch
        signZip(project, patchFile, signingConfig, signOut)

        if (!signOut.exists()) {
            throw new GradleException("signed patch file is not exist: ${signOut.absolutePath}")
        }
    }

    /**
     * 签名函数
     */
    private static void signZip(Project project, File inFile, SigningConfig signingConfig, File outFile) throws Exception {
        PrivateKey key;
        X509Certificate certificate;
        boolean v1SigningEnabled;
        boolean v2SigningEnabled;
        if (signingConfig != null && signingConfig.isSigningReady()) {
            CertificateInfo certificateInfo = KeystoreHelper.getCertificateInfo(
                    signingConfig.getStoreType(),
                    Preconditions.checkNotNull(signingConfig.getStoreFile()),
                    Preconditions.checkNotNull(signingConfig.getStorePassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyPassword()),
                    Preconditions.checkNotNull(signingConfig.getKeyAlias()));
            key = certificateInfo.getKey();
            certificate = certificateInfo.getCertificate();
            v1SigningEnabled = signingConfig.isV1SigningEnabled();
            v2SigningEnabled = signingConfig.isV2SigningEnabled();
        } else {
            key = null;
            certificate = null;
            v1SigningEnabled = false;
            v2SigningEnabled = false;
        }
        ApkCreatorFactory.CreationData creationData =
                new ApkCreatorFactory.CreationData(
                        outFile,
                        key,
                        certificate,
                        v1SigningEnabled,
                        v2SigningEnabled,
                        null,
                        null,
                        1,
                        NativeLibrariesPackagingMode.COMPRESSED,
                        new Predicate<String>() {
                            @Override
                            boolean test(String s) {
                                return false
                            }
                        });
        ApkCreator signedJarBuilder
        try {
            boolean keepTimestamps = AndroidGradleOptions.keepTimestampsInApk(project);
            ZFileOptions options = new ZFileOptions();
            options.setNoTimestamps(!keepTimestamps);
            options.setCoverEmptySpaceUsingExtraField(true);
            ThreadPoolExecutor compressionExecutor =
                    new ThreadPoolExecutor(
                            0, /* Number of always alive threads */
                            2,
                            100,
                            TimeUnit.MILLISECONDS,
                            new LinkedBlockingDeque<>());
            options.setCompressor(
                    new BestAndDefaultDeflateExecutorCompressor(
                            compressionExecutor,
                            options.getTracker(),
                            1.0));
            options.setAutoSortFiles(true);
            ApkZFileCreatorFactory factory = new ApkZFileCreatorFactory(options);
            signedJarBuilder = factory.make(creationData)
            signedJarBuilder.writeZip(
                    inFile,
                    null,
                    null
            );
        } finally {
            if (signedJarBuilder) {
                signedJarBuilder.close()
            }
        }
    }

    public static class ConfigAction extends TaskConfiguration<FlutterPatchTask> {
        private def variant

        ConfigAction(Project project, def variant) {
            super(project)
            this.variant = variant
        }

        @Override
        String getName() {
            return "assemble${this.variant.getName().capitalize()}FlutterPatch"
        }

        @Override
        void execute(FlutterPatchTask task) {
            task.setGroup("flutter")
            task.setDescription("generate flutter patch file")
            task.variant = this.variant
            task.dependsOn project.tasks.findByName("assemble${this.variant.getName().capitalize()}")

            File baseLineApk = null
            if (project.hasProperty("baselineApk")) {
                String baseline = project.property("baselineApk")
                if (baseline.contains(":")) {
                    try {
                        //maybe maven
                        def dependency = project.getDependencies().create(baseline)
                        def configuration = project.getConfigurations().detachedConfiguration(dependency)
                        configuration.setTransitive(false)
                        baseLineApk = configuration.getSingleFile()
                    } catch (Exception e) {
                        baseLineApk = new File(baseline)
                    }
                } else {
                    baseLineApk = new File(baseline)
                }
            }
            if (baseLineApk == null) {
                baseLineApk = new File(project.projectDir, "baseline.apk")
            }
            task.baseLineApk = baseLineApk
            task.apkFile = new File(project.buildDir, "outputs/apk/${variant.getDirName()}/${project.name}-${this.variant.getName()}.apk")
            task.patchDir = new File(task.apkFile.getParentFile(), "patch")
            task.patchFile = new File(task.apkFile.getParentFile(), "flutter_patch.zip")

        }
    }
}