# apksig uses reflection-free code but ships its own JCA lookups; keep it intact.
-keep class com.android.apksig.** { *; }
-dontwarn com.android.apksig.**
# Optional-method lookups done by ApkSigning via reflection.
-keepclassmembers class com.android.apksig.ApkSigner$Builder { public *; }
-keepclassmembers class com.android.apksig.ApkVerifier$Result { public *; }
