# Диагностика повторной записи индекса на API 36

Дата: 2026-10-01. Проверка: `AndroidPersistenceTest.localIndexRoundTripsAndRejectsDamagedBytes` и `SafIntegrationTest.scanCompleteAndUndoModifySyntheticSafDocument` под Robolectric 4.17, JDK 21.

## Наблюдение

На API 26 и 28 оба сценария проходят. На API 36 `LocalTaskIndex.save(populated)` завершается без исключения, но следующий `load()` возвращает прежний пустой snapshot. В файлах sandbox после второго сохранения:

- `task-index-v1.bin`: 30 байт, декодируется как старый пустой индекс;
- `task-index-v1.bin.new`: 100 байт, декодируется как новый индекс с одной задачей.

Из-за этого после успешной SAF-записи `VaultRepository.complete()` физически меняет Markdown, но `indexedTasks()` ещё видит старую задачу. Тест сохраняет точное утверждение и падает. `AtomicFile.finishWrite` должен продвигать `.new` в основной файл; почему этого не происходит в Robolectric API 36 на Windows, пока не установлено. Реальное устройство Android 16 не подключено, поэтому нельзя утверждать, что дефект воспроизводится на телефоне.

## Следующая диагностика

1. Изолировать повторный `AtomicFile.startWrite/finishWrite` без `IndexCodec` и SAF; проверить return/исключения `File.renameTo` и содержимое base/new после каждого шага.
2. Сверить поведение `AtomicFile` Android 16 и Robolectric 4.17 по первичным исходникам. Если это ограничение Robolectric, локализовать его в тестовой среде, не ослабляя проверку реального индекса. Если дефект в проектном коде, исправить код и оставить оба регрессионных теста.
3. Выполнить полный suite. Сейчас красны только API 36 варианты двух тестов; остальные 23 выбранных Robolectric-варианта прошли. Последний полный зелёный прогон до добавления API 36 был на API 26/28; JaCoCo тогда показал 855/1057 строк = 80,9%.

## Ограничение

Текущий `artifacts/taskmanager-widget-candidate.apk` собран до Robolectric-исправления API 26–28 и не соответствует нынешнему исходнику. Не отдавать его как финальный APK. Первый Git-коммит проекта ещё не создан.
## Причина и решение, 2026-10-01

Минимальный `atomicFileReplacesExistingBase` падал на Robolectric API 36 до адаптации: два вызова `AtomicFile.startWrite/finishWrite` с однобайтовым содержимым возвращали первый байт. Значит, `IndexCodec`, SAF и индекс не требуются для воспроизведения. AOSP `AtomicFile.finishWrite` вызывает `File.renameTo` из `.new` в существующий base и лишь логирует `false`; на Windows host такая замена не произошла. Наблюдаемые base и `.new` подтвердили это.

В тестах добавлен `WindowsAtomicFileShadow` только для API 30+ с `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`, чтобы воспроизвести предполагаемую Android-семантику замены на host. Production-код и APK не менялись. Изолированный тест, `LocalTaskIndex` и интеграционный SAF-сценарий прошли на API 26/28/36, полный suite — 74 теста и 80,9% покрытия. Работа на реальном Android 16 ещё не подтверждена.

Первоисточник: [AOSP AtomicFile.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/util/AtomicFile.java), методы `finishWrite` и `rename`; [Robolectric 4.17](https://github.com/robolectric/robolectric/releases/tag/robolectric-4.17).
