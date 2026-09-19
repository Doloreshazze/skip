# Skip — quick trigger pause

Branch: `codex/quick-trigger-pause`. Based on the existing notification-control
work in PR #45, with Quick Settings, shared state, and resume validation added.

## Как пользоваться

1. Установите тестовый APK **Skip Пауза** (версия 12). Он устанавливается рядом с
   основной версией: пакет `com.playeverywhere999.skip.quickpause`.
2. Выключите службу старого Skip, если она включена. Откройте **Skip Пауза**,
   подтвердите согласие и включите его службу в специальных возможностях.
3. Укажите текст кнопки и включите автоклик.
4. Откройте шторку и нажмите **Пауза** в уведомлении. **Возобновить** включает
   триггер снова. На Android 13+ разрешите уведомления.
5. Для отдельной плитки раскройте быстрые настройки → изменение кнопок /
   карандаш → перетащите **Skip**. Нажатие переключает работу и паузу.

Пауза сохраняет текст и разрешение Accessibility, действует сразу и сохраняется
после перезапуска процесса. Состояние синхронизировано с главным экраном.
Плитка работает и при отключённых уведомлениях. Возобновление с плитки на
заблокированном устройстве требует разблокировки.

## Build and validation

JDK 17, Android SDK 36, Gradle wrapper included:

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug uses a separate
application ID and the Android debug signing key; release retains the original
application ID and needs the owner's release signing configuration.

Robolectric regression tests cover persistent pause, preserving Accessibility
and target text, repeated actions, consent withdrawal, disabled Accessibility,
empty targets, malformed broadcasts, and notification action labels on APIs 28
and 31. Device checks: add the tile, pause/resume while another app is open,
repeat using the notification, deny notifications and try the tile, then restart
the process while paused. These device checks must be done on a real device or
emulator; unit tests do not establish OEM-specific behaviour.

Notification receiver is not exported; the tile is protected by
`BIND_QUICK_SETTINGS_TILE`. The engine excludes its own UI and System UI so its
controls cannot trigger automatic clicks. Resume requires accepted disclosure,
an enabled Accessibility service, and non-blank target text.
