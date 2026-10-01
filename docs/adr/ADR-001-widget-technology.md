# ADR-001: RemoteViews для прокручиваемого виджета

Статус: принято 2026-09-30 для текущей реализации.

## Контекст

Основной продукт — домашний виджет Android с прокручиваемой agenda, checkbox без открытия Activity, Refresh и изменением размера. Минимальная версия — API 26. Макет ориентируется на локальный календарный виджет пользователя.

## Решение

Использовать `AppWidgetProvider` + `RemoteViews` + `RemoteViewsService` с `ListView`. Коллекция даёт вертикальный скролл внутри виджета; `PendingIntent` template и fill-in intent связывают строку с действием checkbox. Высота списка занимает доступное пространство при resize.

## Основание

Документация Android описывает и Glance `LazyColumn`, и `RemoteViews` collection widgets. В проекте проверен APK именно с `RemoteViews`: владелец подтвердил на Realme/Android 16 скролл, checkbox, Refresh и resize. Отдельный APK на Glance не собирался; утверждать, что Glance не может выполнить требования, оснований нет. Выбор закрепляет работающий на целевом устройстве вариант с небольшим числом зависимостей.

## Последствия и ограничения

- UI виджета строится из разрешённых `RemoteViews`/XML-компонентов; гибкость ниже обычного Activity UI.
- На API 26 для коллекции используется сервис. Современные API могут предлагать другие способы передачи коллекции; текущий способ компилируется и проверен владельцем на Android 16.
- `notifyAppWidgetViewDataChanged` и `setRemoteAdapter` помечены deprecated в SDK 36; их замену оценивать отдельно после проверки совместимости API 26.
- Начиная с APK-кандидата versionCode 6 коллекция получает задачи из локального индекса Vault. Новую интеграцию ещё требуется проверить на Realme; прежняя аппаратная проверка подтверждала только mock-прототип.

Источники: https://developer.android.com/develop/ui/views/appwidgets/collections ; https://developer.android.com/develop/ui/compose/glance/build-ui ; https://developer.android.com/develop/ui/compose/glance/user-interaction