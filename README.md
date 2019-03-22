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