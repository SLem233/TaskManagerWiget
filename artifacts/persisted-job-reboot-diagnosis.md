# Проверка чистой установки и фонового задания после reboot

Дата: 2026-10-01. Устройство: Realme RMX5303, Android API 36. Личные URI и Markdown в отчёт не записывались.

## Чистая установка

По разрешению владельца удалён только пакет `ru.slem.taskwidget`; `pm path` подтвердил его отсутствие. Затем `adb install` того же APK вернул `Success`, `MainActivity` запустилась без `AndroidRuntime:E`. Старых настроек не было. Владелец заново выбрал Vault через SAF и добавил виджет; задачи появились. После `am force-stop` и повторного запуска сохранённый URI остался; принудительный periodic scan обновил mtime индекса с `1790851606` до `1790851713`, `last_scan_error` пуст, job вернулся в `waiting`.

## Сбой после reboot до исправления

После первой перезагрузки сохранённый URI, индекс и экземпляр виджета остались, но `cmd jobscheduler get-job-state ru.slem.taskwidget 4102` вернул `unknown`: периодическое задание не пережило reboot. После первой разблокировки ручное сканирование обновило индекс до mtime `1790851891` и вновь зарегистрировало job. Значит, SAF-разрешение сохранилось, а потеряно было расписание.

[Документация Android](https://developer.android.com/reference/android/app/job/JobInfo.Builder#setPersisted(boolean)) указывает, что `setPersisted(true)` сохраняет job после reboot и требует `RECEIVE_BOOT_COMPLETED`.

## Исправление и проверка

До изменения кода добавлен `AndroidWidgetTest.periodicScanPersistsAcrossRebootAndReplacesOldSchedule`; он падал на `replacement.isPersisted`. В `VaultScanJobService.ensurePeriodic` добавлено persisted-задание и замена прежнего непостоянного job. В manifest добавлено только нормальное разрешение `android.permission.RECEIVE_BOOT_COMPLETED`; `INTERNET` нет.

После исправления тест прошёл на API 28/36. Полный прогон: 94 Android/JVM теста и 4 Python теста без ошибок; JaCoCo 927/1141 строк (81,2%), lint 0 errors. APK SHA-256 `2FBF705BC0DE2863ACA5786F6038C27D65417215608C692E4390310AA55025D9` установлен на Realme. Перед повторным reboot `dumpsys jobscheduler` показал `PERIODIC: interval=+30m0s0ms` и `PERSISTED`. После reboot, **до открытия приложения**, job `4102` остался в системном списке с `PERSISTED`. До первой разблокировки пользователь Android был `RUNNING_LOCKED`, `get-job-state` возвращал `no-component`; системный список уже содержал job с `PERSISTED`. После разблокировки владелец подтвердил, что на телефоне всё работает. При повторном ADB-подключении `get-job-state` показал `waiting`; принудительный запуск job после reboot обновил индекс с mtime `1790853547` до `1790854093`, `last_scan_error` пуст, job вернулся в `waiting` и сохранил `PERSISTED`. Точный момент самостоятельного 30-минутного запуска не измерялся.
