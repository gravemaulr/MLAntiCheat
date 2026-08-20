# MLAntiCheat

MLAntiCheat is a combat anticheat for Paper servers. It watches how players aim and fight then checks that data with a model trained on your server.

The plugin does not come with a ready model. Every server is different so you need to train it before using punishments.

[English](#english) · [Русский](#русский)

## English

### Main features

- learns from fights on your server
- shows alerts and player history to staff
- includes a simple admin menu
- takes ping and server performance into account
- saves evidence for later review
- works safely in Shadow Mode before the model is ready
- can use PacketEvents for better rotation data
- supports automatic training from reviewed player data
- includes a physical dummy for safe combat testing

### Requirements

- Paper 1.21.4 or newer
- Java 21 or newer
- PacketEvents 2.13.0 or newer is recommended

Newer Paper versions should work as long as their API stays compatible. Keep Paper and PacketEvents updated together.

### Download and installation

1. Download the latest jar from [GitHub Releases](../../releases/latest).
2. Put the jar into the server `plugins` folder.
3. Install PacketEvents if you want packet rotation tracking.
4. Start the server once.
5. Keep Shadow Mode enabled while training and testing.

If a ready jar is not available in Releases you can build the plugin with Maven.

### Training the model

Train with players whose behavior you know. Use normal players for `legit` data and known cheat clients for `cheat` data.

```text
/mlac train legit <player>
/mlac train cheat <player>
/mlac train stop <player>
/mlac model
```

Start a label before the test fight and stop it when the test is over. Collect different play styles sensitivities weapons ping values and arenas. Bad labels will teach the model the wrong behavior.

The default target is 400 legit samples and 400 cheat samples. More good examples will usually give better results.

### Before enabling punishments

The plugin starts with `alerts.shadow-mode: true`. In this mode it can show alerts and save evidence but it will not cancel hits or run punishment commands.

Keep Shadow Mode enabled until:

- both training classes have enough samples
- `/mlac model` shows acceptable results
- staff have checked alerts during real fights
- normal players are not being flagged often

No model is perfect. Staff review is still important.

### Commands

| Command | What it does |
| --- | --- |
| `/mlac gui [name]` | Opens the player list |
| `/mlac inspect <player>` | Opens a player card |
| `/mlac stats <player>` | Shows player scores |
| `/mlac alerts` | Turns your alerts on or off |
| `/mlac tags` | Turns floating tags on or off |
| `/mlac dummy` | Spawns or removes a physical test dummy |
| `/mlac train <legit\|cheat\|stop> <player>` | Controls training |
| `/mlac model` | Shows model status |
| `/mlac reload` | Reloads plugin files |

### Permissions

| Permission | Access |
| --- | --- |
| `mlac.view` | Menu and player data |
| `mlac.alerts` | Staff alerts |
| `mlac.train` | Model training |
| `mlac.reload` | Plugin reload |
| `mlac.tags` | Floating tags |
| `mlac.dummy` | Physical test dummy |
| `mlac.bypass` | Ignore a player during checks |

### Files

- `config.yml` contains the main settings
- `messages.yml` contains chat messages
- `gui.yml` contains menu text and items
- plugin data models and evidence are stored in `plugins/MLAntiCheat`

Back up the plugin folder after training the model.

### License

This project uses the MIT License. You may use change and share the code. The license text and copyright notice must stay with copied versions. The author is not responsible for damage caused by other people using the software.

---

## Русский

### Что делает плагин

MLAntiCheat — боевой античит для Paper. Он следит за тем как игроки целятся и сражаются а затем проверяет эти данные с помощью модели обученной на вашем сервере.

Готовой модели в плагине нет. Серверы отличаются друг от друга поэтому перед наказаниями модель нужно обучить.

### Основные возможности

- учится на боях вашего сервера
- показывает администрации уведомления и историю игроков
- имеет простое меню управления
- учитывает пинг и нагрузку сервера
- сохраняет данные о срабатываниях
- безопасно работает в Shadow Mode пока модель не готова
- поддерживает PacketEvents для более точных данных о поворотах
- умеет автоматически обучаться на проверенных данных игроков
- имеет физический манекен для безопасной проверки боя

### Требования

- Paper 1.21.4 или новее
- Java 21 или новее
- рекомендуется PacketEvents 2.13.0 или новее

Новые версии Paper должны работать пока их API остается совместимым. Обновляйте Paper и PacketEvents вместе.

### Скачивание и установка

1. Скачайте последний jar в разделе [GitHub Releases](../../releases/latest).
2. Положите jar в папку `plugins` сервера.
3. Установите PacketEvents если нужен сбор поворотов из пакетов.
4. Один раз запустите сервер.
5. Не выключайте Shadow Mode во время обучения и проверки.

Если готового jar в Releases пока нет плагин можно собрать через Maven.

### Обучение модели

Для обучения нужны игроки поведение которых вам известно. Обычные игроки нужны для данных `legit` а заранее известные чит-клиенты — для данных `cheat`.

```text
/mlac train legit <игрок>
/mlac train cheat <игрок>
/mlac train stop <игрок>
/mlac model
```

Включите нужную метку перед тестовым боем и остановите ее после теста. Собирайте разные стили игры чувствительность оружие пинг и арены. Если поставить неверную метку модель запомнит неправильное поведение.

По умолчанию нужно 400 обычных и 400 читерских примеров. Чем больше хороших примеров тем лучше результат.

### Перед включением наказаний

По умолчанию включен Shadow Mode через `alerts.shadow-mode: true`. Плагин показывает уведомления и сохраняет данные но не отменяет удары и не запускает команды наказаний.

Не выключайте Shadow Mode пока:

- не собрано достаточно примеров обоих типов
- команда `/mlac model` не показывает нормальный результат
- администрация не проверила уведомления в обычных боях
- обычные игроки не перестали получать частые срабатывания

Идеальных моделей не бывает. Решение администрации все равно важно.

### Команды

| Команда | Что делает |
| --- | --- |
| `/mlac gui [имя]` | Открывает список игроков |
| `/mlac inspect <игрок>` | Открывает карточку игрока |
| `/mlac stats <игрок>` | Показывает оценки игрока |
| `/mlac alerts` | Включает или выключает ваши уведомления |
| `/mlac tags` | Включает или выключает теги |
| `/mlac dummy` | Создает или убирает физический манекен |
| `/mlac train <legit\|cheat\|stop> <игрок>` | Управляет обучением |
| `/mlac model` | Показывает состояние модели |
| `/mlac reload` | Перезагружает файлы плагина |

### Права

| Право | Доступ |
| --- | --- |
| `mlac.view` | Меню и данные игроков |
| `mlac.alerts` | Уведомления администрации |
| `mlac.train` | Обучение модели |
| `mlac.reload` | Перезагрузка плагина |
| `mlac.tags` | Теги над игроками |
| `mlac.dummy` | Физический манекен для проверки |
| `mlac.bypass` | Игнорирование игрока при проверках |

### Файлы

- в `config.yml` находятся основные настройки
- в `messages.yml` находятся сообщения чата
- в `gui.yml` находятся названия и предметы меню
- модель статистика и сохраненные данные находятся в `plugins/MLAntiCheat`

После обучения сделайте резервную копию папки плагина.

### Лицензия

Плагин распространяется по лицензии MIT. Другие люди могут использовать менять и публиковать исходный код но должны сохранить текст лицензии и имя автора. Автор не отвечает за проблемы которые появились при использовании чужих версий плагина.
