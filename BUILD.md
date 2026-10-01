# Сборка APK

Нужны JDK 21 и Android SDK с платформой API 36 и Build-Tools 36.0.0. Путь SDK задаётся через `ANDROID_HOME` или локальный `local.properties`; последний не включается в Git. Gradle Wrapper закреплён на 9.8.0.

Из корня проекта в PowerShell (включая SQLite-тесты на встроенном Python `sqlite3`):

```powershell
$env:JAVA_HOME = 'путь к JDK 21'
$env:ANDROID_HOME = 'путь к Android SDK'
$env:PYTHONUTF8 = '1'
python -m unittest discover -s scripts -p 'test_*.py'
.\gradlew.bat testDebugUnitTest createDebugUnitTestCoverageReport assembleDebug lintDebug
```

Выходной debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Проверенный кандидат скопирован в `artifacts/taskmanager-widget-candidate.apk` (versionCode 6, versionName `0.4.0-integrated-candidate`, minSdk 26, targetSdk 36). APK подписан локальным debug-ключом и предназначен для установки и приёмки на устройстве. APK игнорируется Git; при передаче репозитория его нужно приложить отдельно либо собрать из исходников.

Текущий APK собран 2026-10-01 с таймером статуса Refresh и цветами задач; SHA-256: `9E7B75ABA4A57304EE726E4D62CB5BA7C18698C322F3DECCEE25170807994867`. `aapt dump permissions` показал только имя пакета: Android-разрешений, включая INTERNET, нет. Доступ к Vault выдаётся пользователем через SAF. APK установлен на Realme/Android 16 и принят владельцем как текущий кандидат. При клонировании репозитория APK надо собрать заново или передать отдельно: `*.apk` игнорируется Git.