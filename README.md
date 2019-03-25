### flutter patch gradle plugin

文章链接

[Flutter 动态化探索](http://lizhangqu.github.io/2019/03/22/Flutter-%E5%8A%A8%E6%80%81%E5%8C%96%E6%8E%A2%E7%B4%A2/)

```
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath "io.github.lizhangqu:plugin-flutter-patch:1.0.1"
    }
}

apply plugin: 'flutter.patch'

```


生成patch

```
gradlew assembleReleaseFlutterPatch -PbaselineApk=/path/to/baseline.apk 
```

或者传递maven坐标

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