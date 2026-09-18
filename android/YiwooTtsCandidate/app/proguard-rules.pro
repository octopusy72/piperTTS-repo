-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class ai.onnxruntime.** { *; }
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
