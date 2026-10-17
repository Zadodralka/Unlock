# Ядро Xray (gomobile): Go вызывает Java-классы через JNI по именам —
# их нельзя переименовывать или выкидывать.
-keep class go.** { *; }
-keep class libXray.** { *; }
-keep class * implements libXray.** { *; }

# Сканер ZXing создаётся по имени класса из манифеста/Intent
-keep class com.journeyapps.barcodescanner.** { *; }
-dontwarn com.google.zxing.**
