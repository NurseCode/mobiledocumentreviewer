-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends androidx.compose.runtime.**
-keepattributes *Annotation*
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.*
-dontwarn org.openjsse.**

# PDFBox Android
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.fontbox.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.fontbox.**
-dontwarn javax.activation.**
-dontwarn java.awt.**
