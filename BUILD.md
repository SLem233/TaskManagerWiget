# Сборка APK

Нужны JDK 21 и Android SDK с платформой API 36 и Build-Tools 36.0.0. Путь SDK задаётся через `ANDROID_HOME` или локальный `local.properties`; этот файл не включается в Git. Gradle Wrapper закреплён на 9.8.0.

Из корня проекта в PowerShell:

```powershell
$env:JAVA_HOME = 'путь к JDK 21'
$env:ANDROID_HOME = 'путь к Android SDK'
$env:PYTHONUTF8 = '1'
python -m unittest discover -s scripts -p 'test_*.py'
.\gradlew.bat testDebugUnitTest createDebugUnitTestCoverageReport assembleDebug lintDebug
```

Выходной debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Локальный проверенный кандидат: `artifacts/taskmanager-widget-candidate-v8.apk`, versionCode 8, versionName `0.5.1-candidate`, minSdk 26, targetSdk 36. Его SHA-256: `19E2C7A46D42F1A3E9375E38352385898D24CD885E72220CF3EE959860E13289`. APK игнорируется Git и при клонировании собирается заново. Debug-подпись локальная; для обновления установленной копии нужна та же подпись.

Приложение запрашивает `RECEIVE_BOOT_COMPLETED` для сохранения периодической job после перезагрузки и `POST_NOTIFICATIONS` для колокольчика на Android 13+. Доступ к Vault выдаётся через системный выбор папки SAF. Разрешение `INTERNET` не запрашивается.