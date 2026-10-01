# Независимое ревью Android-кандидата

Дата: 2026-09-30. Read-only reviewer проверил SAF-запись и Undo, SQLite-журнал, `RemoteViews`/`PendingIntent`, `JobScheduler`, manifest и API 26. Устройство и Android runtime в ревью не запускались.

| Находка | Исправление | Проверка |
|---|---|---|
| P1: backup частичного Undo не содержал поздние правки | Перед Undo журнал сохраняет текущие байты файла | `TaskWriterTest.failedUndoKeepsLatestUnrelatedEditsInRecoveryBackup` |
| P1: БД v1 падала при открытии как v2 | Явная SQL-миграция v1→v2; legacy recovery доступен для экспорта, legacy DONE не допускает Undo в неизвестном Vault | Python SQLite-тесты миграции и видимости legacy |
| P2: остановленный JobService продолжал сканирование и мог завершиться после stop | Scanner проверяет interruption; worker отменяется, сохранение индекса прерывается; завершение job сериализовано на main looper с callbacks | `VaultScannerCancellationTest`; статическое повторное ревью жизненного цикла |
| P2: полный backup каждой операции хранился без лимита | Обычная история ограничена 100 действиями на Vault; recovery сохраняется, есть подтверждаемая ручная очистка | Python SQLite-тесты retention и очистки |

После исправлений: 46 Kotlin unit-тестов и 4 Python SQLite-теста прошли, `assembleDebug` и `lintDebug` успешны. Повторное read-only ревью подтвердило исправления backup, retention и legacy recovery; выявленную гонку JobService затем устранили сериализацией на main looper. Финальный Android runtime-сценарий остаётся непроверенным без Realme.