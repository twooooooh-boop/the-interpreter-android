# R8 / ProGuard rules for The Interpreter (release builds are minified)

# Created through reflection by the ViewModel factory / the system
-keep class com.twooooooh.interpreter.InterpreterApp { *; }
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class * extends androidx.lifecycle.AndroidViewModel { <init>(android.app.Application); }

# Keep readable stack traces
-keepattributes SourceFile,LineNumberTable
