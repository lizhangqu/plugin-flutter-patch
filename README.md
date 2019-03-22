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

gradlew assembleReleaseFlutterPatch -Pbaseline=/path/to/baseline.apk 