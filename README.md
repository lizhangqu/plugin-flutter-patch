### flutter patch gradle plugin


```
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath "io.github.lizhangqu:plugin-flutter-patch:1.0.0" //未发布
    }
}

apply plugin: 'flutter.patch'

```


生成patch

```
gradlew assembleReleaseFlutterPatch -PbaselineApk=/path/to/baseline.apk 
```

or

```
gradlew assembleReleaseFlutterPatch -PbaselineApk=x:y:z
```

其中x:y:z为基线包maven坐标



自定义patch下载url，下载模式，安装模式

```
apply plugin: 'flutter.transform'
flutterTransform {
      patchClass = "io.github.lizhangqu.flutter.FlutterUpdate"
      downloadUrlMethod = "getDownloadURL"
      installModeMethod = "getInstallMode"
      downloadModeMethod = "getDownloadMode"
}
```


```
@Keep
public class FlutterUpdate {

    @Keep
    public static String getDownloadURL(Context context) {
       return null;
    }

    @Keep
    public static String getDownloadMode(Context context) {
        return null;
    }

    @Keep
    public static String getInstallMode(Context context) {
        return null;
    }
}

```