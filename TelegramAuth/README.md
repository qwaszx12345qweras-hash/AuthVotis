# TelegramAuth - Minecraft Plugin

Плагин аутентификации через Telegram для серверов Paper 1.16.5.

## 📋 Описание

TelegramAuth позволяет привязывать аккаунты Minecraft к Telegram для безопасной авторизации на сервере. При входе игрок должен подтвердить вход через Telegram-бот.

## ⚙️ Возможности

- **Привязка аккаунта** — генерация 6-значного кода на сервере и отправка боту
- **Подтверждение входа** — inline-кнопки «Подтвердить» / «Отклонить» в Telegram
- **Полная заморозка** — блокировка всех действий неавторизованных игроков
- **Гибкое хранилище** — YAML, SQLite или MySQL
- **Настраиваемые сообщения** — все тексты в config.yml
- **Long-polling** — стабильная работа без webhook

## 📦 Сборка

### Требования
- Java 21 (JDK 21)
- Gradle 8.x

### Локальная сборка
```bash
gradle shadowJar
```

JAR-файл будет в `build/libs/TelegramAuth-1.0.0.jar`

### Сборка через GitHub Actions
При push в ветку `main` автоматически запускается сборка. Артефакт можно скачать во вкладке Actions.

## 🔧 Установка

1. Скачайте `TelegramAuth-1.0.0.jar`
2. Поместите в папку `plugins/` сервера
3. Перезапустите сервер
4. Отредактируйте `plugins/TelegramAuth/config.yml`:
   - Укажите `telegram.bot-token` (получите у @BotFather)
   - Укажите `telegram.bot-username`
   - Выберите тип хранилища
5. Перезагрузите плагин: `/telegramauth reload`

## 🎮 Использование

### Для игроков
1. Зайдите на сервер
2. Введите `/link` — получите 6-значный код
3. Откройте Telegram-бот и отправьте `/link <код>`
4. При следующем входе подтвердите вход в Telegram

### Для администраторов
- `/telegramauth reload` — перезагрузить конфиг
- `/telegramauth unlink <player>` — отвязать игрока
- `/telegramauth status` — статус плагина

## 📁 Структура проекта

```
TelegramAuth/
├── .github/workflows/build.yml
├── src/main/java/ru/example/telegramauth/
│   ├── TelegramAuthPlugin.java       — главный класс
│   ├── LinkCommand.java              — команда /link
│   ├── AdminCommand.java             — команда /telegramauth
│   ├── FreezeListener.java           — блокировка игроков
│   ├── TelegramBot.java              — Telegram long-polling бот
│   ├── BotCommandHandler.java        — обработка команд бота
│   ├── PlayerSession.java            — модель сессии
│   ├── PlayerSessionManager.java     — менеджер сессий
│   ├── Storage.java                  — интерфейс хранилища
│   ├── YamlStorage.java              — YAML реализация
│   ├── SqliteStorage.java            — SQLite реализация
│   └── MysqlStorage.java             — MySQL реализация
├── src/main/resources/
│   ├── plugin.yml
│   └── config.yml
├── build.gradle
├── settings.gradle
└── README.md
```

## 🔒 Блокировка (заморозка)

Во время заморозки блокируются:
- Движение, прыжки, полёт
- Чат и команды (кроме разрешённых)
- Взаимодействие с блоками
- Ломание/установка блоков
- Использование предметов
- Выброс/подбор предметов
- Открытие инвентаря
- Получение/нанесение урона
- Атака мобов
- Порталы, кровати, транспорт

Дополнительно: эффект слепоты и невидимости.

## ⚙️ Конфигурация

Все настройки находятся в `config.yml`. Основные секции:

- `telegram` — настройки бота
- `storage` — тип хранилища
- `security` — параметры безопасности (длина кода, время жизни, таймаут)
- `freeze` — флаги блокировки
- `allowed-commands` — разрешённые команды при заморозке
- `messages` — все сообщения плагина

## 📜 Лицензия

MIT License

## 👤 Автор

YourName
