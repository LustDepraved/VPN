# FreeVPN Android App (MVP)

MVP Android-клиент на Kotlin + Jetpack Compose.

## Что сейчас есть
- Выбор региона и отображение latency/load.
- Переключение Connect/Disconnect (UI-симуляция).
- Переключение протокола (WireGuard/Hysteria2, MVP UI).
- Флаг Auto reconnect.
- Блок live-метрик с packet loss/jitter (mock).

## Сборка APK
```bash
cd apps/mobile-android
./gradlew assembleDebug
```

Результат: `app/build/outputs/apk/debug/app-debug.apk`
