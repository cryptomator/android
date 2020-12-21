-useuniqueclassmembernames

# greenDAO 3, http://greenrobot.org/greendao/documentation/technical-faq
-keepclassmembers class * extends org.greenrobot.greendao.AbstractDao {
    public static java.lang.String TABLENAME;
}
-keep class **$Properties {*;}
-dontwarn org.greenrobot.greendao.database.**
-dontwarn net.sqlcipher.database.**
-dontwarn rx.**

# RxJava, https://github.com/artem-zinnatullin/RxJavaProGuardRules/blob/master/rxjava-proguard-rules/proguard-rules.txt
-dontwarn sun.misc.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}

# Google API Client, https://github.com/google/google-api-java-client/blob/dev/google-api-client-assembly/proguard-google-api-client.txt
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class * {
    @com.google.api.client.util.Key <fields>;
}
-dontwarn com.google.api.client.extensions.android.**
-dontwarn com.google.api.client.googleapis.extensions.android.**
-dontwarn com.google.api.client.googleapis.testing.TestUtils
-dontwarn com.google.android.gms.**

# okhttp3
-dontwarn okhttp3.**
-dontwarn okio.**

# Others
-dontwarn org.slf4j.**
-dontwarn com.dropbox.core.**
-dontwarn com.fernandocejas.frodo.core.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.common.util.concurrent.FuturesGetChecked**
-keepclassmembers class com.microsoft.graph.http.GraphServiceException {
    int mResponseCode;
}
-keep class com.nulabinc.zxcvbn.**

# https://stackoverflow.com/a/47555897/1759462
-dontwarn afu.org.checkerframework.**
-dontwarn org.checkerframework.**

# https://github.com/microsoftgraph/msgraph-sdk-java/issues/258#issue-452030712
-keep class com.microsoft.** { *; }
-keep class com.microsoft.**
-keep interface com.microsoft.** { *; }
-keepclasseswithmembernames class com.microsoft.** { *; }

-keep class com.sun.** { *; }
-keep class com.sun.**
-keep interface com.sun.** { *; }

# https://github.com/jwtk/jjwt
-keepattributes InnerClasses

-keep class io.jsonwebtoken.** { *; }
-keepnames class io.jsonwebtoken.* { *; }
-keepnames interface io.jsonwebtoken.* { *; }

-keep class org.bouncycastle.** { *; }
-keepnames class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

-keep class android.net.http.** { *; }
-keep interface org.apache.** { *; }
-keep enum org.apache.** { *; }
-keep class org.apache.** { *; }
-keep class org.apache.commons.** { *; }
-keep class org.apache.http.** { *; }
-keep class org.apache.harmony.** {*;}
