# MLAntiCheat

MLAntiCheat is a combat anticheat for Paper servers. It watches how players aim and fight, then checks that data with a model trained on your own server.

The plugin does not come with a ready model. Every server is different, so you need to train it before you turn on punishments.

[English](#english) · [Русский](#русский)

## English

### Main features

- learns from real fights on your server
- five scores per player: precision, dynamics, pattern, tracking and the final ML score
- four action levels (watch, alert, evidence, block) that you configure yourself
- built-in report system with a limit and punishment commands
- staff alerts, player cards and a history of detections
- admin menu with search, sorting and training buttons
- takes ping and server TPS into account and lowers the score when the server or the connection is bad
- warm-up rules, so a player is not judged by the first two hits
- saves evidence files for later review
- Shadow Mode keeps everything safe while the model is not ready
- can use PacketEvents for more accurate rotation data
- automatic training on data the model is already sure about
- physical dummy for safe combat testing
- floating tags above players, fully configurable in `config.yml`
- all text lives in `messages.yml` and `gui.yml`, the main command name is configurable too

### Requirements

- Paper 1.21.4 or newer
- Java 21 or newer
- PacketEvents 2.13.0 or newer is recommended

Newer Paper versions should work as long as their API stays compatible. Keep Paper and PacketEvents updated together.

### Download and installation

1. Download the latest jar from [GitHub Releases](../../releases/latest).
2. Put the jar into the server `plugins` folder.
3. Install PacketEvents if you want packet rotation tracking.
4. Start the server once so the files are created.
5. Keep Shadow Mode enabled while you train and test.

If a ready jar is not available in Releases, you can build the plugin with Maven.

If you upgrade from a very old version, the plugin moves your old `config.yml` to `config-legacy-<time>.yml` and writes a fresh one. Your old settings are not lost, but you have to move them over by hand.

### How the scores work

Every score is a number from 0.00 to 1.00. Higher means more suspicious.

| Score | Placeholder | What it looks at |
| --- | --- | --- |
| Precision | `%prec%` | how exactly the crosshair sits on the target |
| Dynamics | `%dyn%` | speed and shape of the mouse movement |
| Pattern | `%pat%` | repeating, machine-like movement |
| Tracking | `%trk%` | how the player follows a moving target |
| ML | `%ml%` | the final score, the average of the four above |

A score is only published when the fight is real: at least 4 hits inside the combat window, at least 900 ms from the first hit and at least 40 rotations in the buffer. All three numbers are in `config.yml` under `model`.

While the model is still collecting samples, `%ml%` stays at 0. That is normal.

### Training the model

Train with players whose behavior you know. Use normal players for `legit` data and known cheat clients for `cheat` data. The player has to be online.

```text
/mlac train legit <player>
/mlac train cheat <player>
/mlac train stop <player>
/mlac model
```

Start a label before the test fight and stop it when the test is over. Collect different play styles, sensitivities, weapons, ping values and arenas. A wrong label teaches the model the wrong behavior, so be careful.

The default target is 400 legit samples and 400 cheat samples. More good examples usually give better results.

Labelled players never trigger reports or punishment commands, so you can safely test cheats on your own account.

### Reading the model status

`/mlac model` shows how the training is going:

- **ready** — the model has enough samples of both classes and its score is used
- **training steps / pairs / surplus** — how much data was used and how many samples are still waiting for a pair
- **precision** — of all the players the model called cheaters, how many really were flagged correctly
- **recall** — how many of the real cheat samples the model catches
- **false positives** — the part of normal samples the model calls cheating; this is the number you care about most

Two extra commands:

- `/mlac model rebalance` — retrains the model from scratch on an equal number of legit and cheat samples
- `/mlac model reset` — clears the weights and the counters, your collected samples stay on disk

### Action levels

Everything the plugin does after an analysis is described in `config.yml` under `actions`. Each level has the same set of options, so you can build your own reaction.

| Level | Default score | Confirmations | What it does by default |
| --- | --- | --- | --- |
| `watch` | 0.65 | 3 | writes the detection into the player history, silently |
| `alert` | 0.55 | 4 | message to staff, line in the console, one anticheat report |
| `evidence` | 0.85 | 6 | saves an evidence file, cancels hits |
| `block` | 0.75 | 8 | saves evidence, cancels hits |

Options of a level:

- `enabled` — turns the level on or off
- `threshold` — the ML score from which the level works
- `confirmations` — how many analyses in a row must stay above the threshold
- `cooldown-ms` — pause between two runs of the same level
- `staff-alert`, `console`, `evidence` — where the detection goes
- `report` — gives the player one anticheat report
- `cancel-hit` — cancels the player's hits while the score is above the threshold
- `commands` — console commands, with `%player%`, `%player_name%`, `%uuid%`, `%score%`, `%raw_score%`, `%ping%`, `%tps%`

Commands and hit cancelling are ignored while Shadow Mode is on. Alerts, evidence and reports still work, so you can watch the plugin without any risk for your players.

### Reports

The plugin can collect reports instead of banning at once. Settings are in `config.yml` under `reports`.

- an action level with `report: true` gives one report when it triggers
- players can report by hand with `/mlac report <player>`
- when a player reaches `maximum` reports (15 by default), the commands from `reports.commands` run — but only if Shadow Mode is off
- `cooldown-seconds` limits how often the anticheat itself reports the same player; the manual command ignores this pause
- `reset-interval-minutes` clears all counters from time to time, so old suspicion does not add up forever
- `reset-after-punishment` starts the counter again after the limit is reached
- `notify-progress` sends every new report to staff with `mlac.report.notify`

Punishment commands support `%player%`, `%player_name%`, `%uuid%`, `%rule%`, `%count%` and `%max%`:

```yaml
reports:
  maximum: 15
  commands:
    - 'banip %player% 24d 2.2 | AI'
```

Staff commands: `/mlac reports <player>` shows the counter, `/mlac reports reset <player>` clears one player and `/mlac reports clear` clears everybody.

`/mlac report` needs the `mlac.report` permission, which is op-only by default. Give it to your normal players in your permission plugin if you want them to use it.

### Ping and server load

A player with 300 ms ping or a server at 15 TPS looks worse than they are. The `conditions` section lowers the score in that case:

- ping: the reduction starts at 150 ms and reaches its maximum at 300 ms, up to 15%
- TPS: the reduction starts at 18.5 and reaches its maximum at 15.0, up to 20%
- both together can never take away more than 30%

Staff alerts show both numbers: the corrected score and the raw one.

### Automatic training

The `auto-training` section lets the plugin keep learning after you stop labelling by hand.

Samples of quiet players with a low score become new `legit` data, samples of players you already punished become `cheat` data. New data waits in quarantine for a few days before it is used, old data is deleted after `retention-days`. Samples confirmed by staff get a higher weight than samples found only by a ban reason.

This works best when your manual dataset is already good. If the model is weak, automatic training will only make it more sure about its own mistakes, so keep `enabled: false` until `/mlac model` looks healthy.

### Admin menu

`/mlac` or `/mlac gui` opens the player list: every online and saved player, coloured by risk, with reports and alerts in the item description. You can sort the list, walk through pages and search with `/mlac gui <name>`.

Click a player to open the card: five scores, ping, TPS, risk level, reports and buttons to label the player as legit or cheat right from the menu. The card also opens the detection history, where every entry shows the rule, the score, the ping and how long ago it happened.

Every slot, material and line of text in the menu is configurable in `gui.yml`.

### Before enabling punishments

The plugin starts with `alerts.shadow-mode: true`. In this mode it shows alerts, saves evidence and counts reports, but it never cancels a hit and never runs a punishment command.

Keep Shadow Mode enabled until:

- both training classes have enough samples
- `/mlac model` shows a low false positive rate
- staff have checked alerts during real fights
- normal players are not being flagged

No model is perfect. Staff review is still important.

### Commands

| Command | What it does |
| --- | --- |
| `/mlac gui [name]` | Opens the player list |
| `/mlac inspect <player>` | Opens a player card |
| `/mlac stats <player>` | Shows player scores in chat |
| `/mlac report <player>` | Sends an anticheat report about a player |
| `/mlac reports <player>` | Shows the report counter |
| `/mlac reports reset <player>` | Clears reports of one player |
| `/mlac reports clear` | Clears reports of all players |
| `/mlac alerts` | Turns your alerts on or off |
| `/mlac tags` | Turns floating tags on or off |
| `/mlac dummy` | Spawns or removes a physical test dummy |
| `/mlac train <legit\|cheat\|stop> <player>` | Controls training |
| `/mlac model` | Shows model status |
| `/mlac model rebalance` | Retrains on balanced data |
| `/mlac model reset` | Clears the model weights |
| `/mlac reload` | Reloads plugin files |

The command name and its aliases are set in `config.yml` under `command`, so you can rename `/mlac` to anything you like.

### Permissions

| Permission | Access |
| --- | --- |
| `mlac.view` | Menu, player cards and model status |
| `mlac.alerts` | Staff alerts |
| `mlac.train` | Model training, rebalance and reset |
| `mlac.reload` | Plugin reload |
| `mlac.tags` | Floating tags |
| `mlac.dummy` | Physical test dummy |
| `mlac.report` | Sending reports with the command |
| `mlac.report.notify` | Messages about new reports |
| `mlac.report.manage` | Resetting and clearing reports |
| `mlac.report.bypass` | Never receives anticheat reports |
| `mlac.bypass` | Ignored during checks |

### Floating tags

Tag lines above players are configured in `config.yml` under `display.lines`. Each list entry is one line of the tag, top to bottom.

```yaml
display:
  lines:
    - '&7PREC %prec_color%%prec% &8· &7DYN %dyn_color%%dyn% &8· &7PAT %pat_color%%pat% &8· &7TRK %trk_color%%trk% &8· &7ML %ml_color%%ml%'
    - '&f%player_name%'
```

Available placeholders:

- `%player_name%` — player name
- `%prec%` `%dyn%` `%pat%` `%trk%` `%ml%` — score values
- `%prec_color%` `%dyn_color%` `%pat_color%` `%trk_color%` `%ml_color%` — dynamic color for each score

Color codes use `&`. The same section also sets the height, the text size, the update rate, whether a player sees their own tag and which permission is needed. Apply changes with `/mlac reload`.

### Files

- `config.yml` — main settings
- `messages.yml` — chat messages
- `gui.yml` — menu text, items and risk colors
- `reports.yml` — how many times each player reached the report limit
- `model/` — model weights and the collected dataset
- player statistics and evidence files are stored in `plugins/MLAntiCheat`

Back up the plugin folder after you train the model.

### License

This project uses the MIT License. You may use, change and share the code. The license text and copyright notice must stay with copied versions. The author is not responsible for damage caused by other people using the software.

---

## Русский

MLAntiCheat — боевой античит для Paper. Он следит за тем, как игроки целятся и сражаются, а затем проверяет эти данные моделью, обученной на вашем сервере.

Готовой модели в плагине нет. Серверы отличаются друг от друга, поэтому перед включением наказаний модель нужно обучить.

### Что умеет плагин

- учится на реальных боях вашего сервера
- считает пять оценок для игрока: точность, динамика, шаблон, отслеживание и итоговая оценка модели
- имеет четыре уровня реакции (watch, alert, evidence, block), которые вы настраиваете сами
- имеет собственную систему репортов с лимитом и командами наказания
- показывает администрации уведомления, карточки игроков и историю срабатываний
- имеет меню с поиском, сортировкой и кнопками обучения
- учитывает пинг и TPS сервера и снижает оценку, когда связь или сервер работают плохо
- ждёт разогрева боя, поэтому не судит игрока по первым двум ударам
- сохраняет файлы доказательств для проверки
- безопасно работает в Shadow Mode, пока модель не готова
- поддерживает PacketEvents для более точных данных о поворотах
- умеет обучаться автоматически на данных, в которых модель уже уверена
- имеет физический манекен для безопасной проверки боя
- полностью настраиваемые теги над игроками в `config.yml`
- весь текст находится в `messages.yml` и `gui.yml`, имя основной команды тоже меняется

### Требования

- Paper 1.21.4 или новее
- Java 21 или новее
- рекомендуется PacketEvents 2.13.0 или новее

Новые версии Paper должны работать, пока их API остаётся совместимым. Обновляйте Paper и PacketEvents вместе.

### Скачивание и установка

1. Скачайте последний jar в разделе [GitHub Releases](../../releases/latest).
2. Положите jar в папку `plugins` сервера.
3. Установите PacketEvents, если нужен сбор поворотов из пакетов.
4. Один раз запустите сервер, чтобы создались файлы.
5. Не выключайте Shadow Mode во время обучения и проверки.

Если готового jar в Releases пока нет, плагин можно собрать через Maven.

При переходе с очень старой версии плагин переносит прежний `config.yml` в файл `config-legacy-<время>.yml` и создаёт новый. Старые настройки не пропадают, но перенести их придётся вручную.

### Что означают оценки

Каждая оценка — число от 0.00 до 1.00. Чем больше, тем подозрительнее поведение.

| Оценка | Плейсхолдер | На что смотрит |
| --- | --- | --- |
| Точность | `%prec%` | насколько ровно прицел держится на цели |
| Динамика | `%dyn%` | скорость и форма движения мыши |
| Шаблон | `%pat%` | повторяющиеся, машинные движения |
| Отслеживание | `%trk%` | как игрок ведёт цель, которая двигается |
| Модель | `%ml%` | итоговая оценка, среднее четырёх предыдущих |

Оценка публикуется только тогда, когда бой действительно идёт: не меньше 4 ударов в боевом окне, не меньше 900 мс с первого удара и не меньше 40 поворотов в буфере. Все три значения меняются в `config.yml` в секции `model`.

Пока модель собирает примеры, `%ml%` остаётся равным нулю. Это нормально.

### Обучение модели

Для обучения нужны игроки, поведение которых вам известно. Обычные игроки дают данные `legit`, заранее известные чит-клиенты — данные `cheat`. Игрок должен быть в сети.

```text
/mlac train legit <игрок>
/mlac train cheat <игрок>
/mlac train stop <игрок>
/mlac model
```

Включите нужную метку перед тестовым боем и остановите её после теста. Собирайте разные стили игры, чувствительность, оружие, пинг и арены. Неверная метка научит модель неправильному поведению, поэтому будьте внимательны.

По умолчанию нужно 400 обычных и 400 читерских примеров. Чем больше хороших примеров, тем лучше результат.

Игрок с меткой обучения не получает репорты и не попадает под команды наказания, так что читы можно спокойно проверять на своём аккаунте.

### Как читать состояние модели

Команда `/mlac model` показывает, как идёт обучение:

- **готова** — примеров обоих классов хватает и оценка модели уже используется
- **шаги обучения / пары / без пары** — сколько данных ушло в обучение и сколько примеров ещё ждут пару
- **точность** — какая часть срабатываний модели действительно относится к читу
- **полнота** — какую часть читерских примеров модель находит
- **ложные срабатывания** — какую часть обычных примеров модель считает читом; это самое важное число

Ещё две команды:

- `/mlac model rebalance` — переобучает модель с нуля на равном числе обычных и читерских примеров
- `/mlac model reset` — сбрасывает веса и счётчики, собранные примеры остаются на диске

### Уровни реакции

Всё, что плагин делает после анализа, описано в `config.yml` в секции `actions`. У каждого уровня одинаковый набор настроек, поэтому реакцию можно собрать под свой сервер.

| Уровень | Оценка | Подтверждений | Что делает по умолчанию |
| --- | --- | --- | --- |
| `watch` | 0.65 | 3 | тихо записывает срабатывание в историю игрока |
| `alert` | 0.55 | 4 | сообщение администрации, строка в консоли, один репорт |
| `evidence` | 0.85 | 6 | сохраняет файл доказательств, отменяет удары |
| `block` | 0.75 | 8 | сохраняет доказательства, отменяет удары |

Настройки уровня:

- `enabled` — включает или выключает уровень
- `threshold` — оценка, начиная с которой уровень работает
- `confirmations` — сколько анализов подряд должны остаться выше порога
- `cooldown-ms` — пауза между двумя срабатываниями уровня
- `staff-alert`, `console`, `evidence` — куда попадает срабатывание
- `report` — выдаёт игроку один репорт от античита
- `cancel-hit` — отменяет удары игрока, пока оценка держится выше порога
- `commands` — команды консоли с плейсхолдерами `%player%`, `%player_name%`, `%uuid%`, `%score%`, `%raw_score%`, `%ping%`, `%tps%`

Команды и отмена ударов не работают, пока включён Shadow Mode. Уведомления, доказательства и репорты при этом работают, поэтому наблюдать за плагином можно без риска для игроков.

### Репорты

Плагин умеет собирать репорты вместо мгновенного бана. Настройки находятся в `config.yml` в секции `reports`.

- уровень реакции с `report: true` выдаёт один репорт при срабатывании
- игроки могут пожаловаться сами командой `/mlac report <игрок>`
- когда игрок набирает `maximum` репортов (по умолчанию 15), выполняются команды из `reports.commands`, но только при выключенном Shadow Mode
- `cooldown-seconds` ограничивает, как часто сам античит выдаёт репорты одному игроку; ручная команда эту паузу не учитывает
- `reset-interval-minutes` время от времени обнуляет счётчики, чтобы старые подозрения не накапливались
- `reset-after-punishment` начинает счёт заново после достижения лимита
- `notify-progress` отправляет каждый новый репорт администрации с правом `mlac.report.notify`

В командах наказания работают плейсхолдеры `%player%`, `%player_name%`, `%uuid%`, `%rule%`, `%count%` и `%max%`:

```yaml
reports:
  maximum: 15
  commands:
    - 'banip %player% 24d 2.2 | AI'
```

Команды администрации: `/mlac reports <игрок>` показывает счётчик, `/mlac reports reset <игрок>` очищает одного игрока, `/mlac reports clear` очищает всех.

Для `/mlac report` нужно право `mlac.report`, которое по умолчанию есть только у операторов. Выдайте его обычным игрокам в своём плагине прав, если хотите, чтобы команда была доступна всем.

### Пинг и нагрузка сервера

Игрок с пингом 300 мс или сервер на 15 TPS выглядят хуже, чем есть на самом деле. Секция `conditions` снижает оценку в таких условиях:

- пинг: снижение начинается со 150 мс и достигает предела на 300 мс, максимум 15%
- TPS: снижение начинается с 18.5 и достигает предела на 15.0, максимум 20%
- вместе они не могут забрать больше 30%

В уведомлениях администрации видно оба числа: исправленную оценку и исходную.

### Автоматическое обучение

Секция `auto-training` позволяет модели учиться дальше, когда вы перестали ставить метки руками.

Примеры спокойных игроков с низкой оценкой становятся новыми данными `legit`, примеры уже наказанных игроков — данными `cheat`. Новые данные несколько дней ждут в карантине, старые удаляются через `retention-days`. Примеры, подтверждённые администрацией, получают больший вес, чем найденные только по причине блокировки.

Это работает хорошо, когда ручная выборка уже качественная. Если модель слабая, автоматическое обучение только укрепит её собственные ошибки, поэтому держите `enabled: false`, пока `/mlac model` не покажет нормальный результат.

### Меню администрации

`/mlac` или `/mlac gui` открывает список игроков: все, кто в сети и кто сохранён в статистике, с цветом по уровню риска и с репортами и уведомлениями в описании. Список можно сортировать, листать по страницам и искать через `/mlac gui <имя>`.

Нажатие на игрока открывает карточку: пять оценок, пинг, TPS, уровень риска, репорты и кнопки, которыми игрока сразу можно отметить как обычного или как читера. Из карточки открывается история срабатываний, где у каждой записи видно правило, оценку, пинг и время.

Каждый слот, материал и строку текста в меню можно изменить в `gui.yml`.

### Перед включением наказаний

По умолчанию включён Shadow Mode через `alerts.shadow-mode: true`. В этом режиме плагин показывает уведомления, сохраняет доказательства и считает репорты, но не отменяет удары и не запускает команды наказания.

Не выключайте Shadow Mode, пока:

- не собрано достаточно примеров обоих типов
- команда `/mlac model` не покажет низкий уровень ложных срабатываний
- администрация не проверит уведомления в обычных боях
- обычные игроки не перестанут получать срабатывания

Идеальных моделей не бывает. Решение администрации всё равно остаётся важным.

### Команды

| Команда | Что делает |
| --- | --- |
| `/mlac gui [имя]` | Открывает список игроков |
| `/mlac inspect <игрок>` | Открывает карточку игрока |
| `/mlac stats <игрок>` | Показывает оценки игрока в чате |
| `/mlac report <игрок>` | Отправляет репорт на игрока |
| `/mlac reports <игрок>` | Показывает счётчик репортов |
| `/mlac reports reset <игрок>` | Сбрасывает репорты одного игрока |
| `/mlac reports clear` | Сбрасывает репорты всех игроков |
| `/mlac alerts` | Включает или выключает ваши уведомления |
| `/mlac tags` | Включает или выключает теги |
| `/mlac dummy` | Создаёт или убирает физический манекен |
| `/mlac train <legit\|cheat\|stop> <игрок>` | Управляет обучением |
| `/mlac model` | Показывает состояние модели |
| `/mlac model rebalance` | Переобучает модель на равной выборке |
| `/mlac model reset` | Сбрасывает веса модели |
| `/mlac reload` | Перезагружает файлы плагина |

Имя команды и её псевдонимы задаются в `config.yml` в секции `command`, поэтому `/mlac` можно переименовать во что угодно.

### Права

| Право | Доступ |
| --- | --- |
| `mlac.view` | Меню, карточки игроков и состояние модели |
| `mlac.alerts` | Уведомления администрации |
| `mlac.train` | Обучение, переобучение и сброс модели |
| `mlac.reload` | Перезагрузка плагина |
| `mlac.tags` | Теги над игроками |
| `mlac.dummy` | Физический манекен для проверки |
| `mlac.report` | Отправка репортов командой |
| `mlac.report.notify` | Сообщения о новых репортах |
| `mlac.report.manage` | Сброс и очистка репортов |
| `mlac.report.bypass` | Игрок никогда не получает репорты от античита |
| `mlac.bypass` | Игрок не проверяется |

### Теги над игроками

Строки тега настраиваются в `config.yml` в секции `display.lines`. Каждый элемент списка — одна строка тега сверху вниз.

```yaml
display:
  lines:
    - '&7PREC %prec_color%%prec% &8· &7DYN %dyn_color%%dyn% &8· &7PAT %pat_color%%pat% &8· &7TRK %trk_color%%trk% &8· &7ML %ml_color%%ml%'
    - '&f%player_name%'
```

Доступные плейсхолдеры:

- `%player_name%` — имя игрока
- `%prec%` `%dyn%` `%pat%` `%trk%` `%ml%` — значения оценок
- `%prec_color%` `%dyn_color%` `%pat_color%` `%trk_color%` `%ml_color%` — динамический цвет каждой оценки

Цветовые коды пишутся через `&`. В этой же секции задаются высота, размер текста, частота обновления, скрытие собственного тега и нужное право. Изменения применяются командой `/mlac reload`.

### Файлы

- `config.yml` — основные настройки
- `messages.yml` — сообщения чата
- `gui.yml` — текст меню, предметы и цвета риска
- `reports.yml` — сколько раз каждый игрок доходил до лимита репортов
- `model/` — веса модели и собранная выборка
- статистика игроков и файлы доказательств лежат в `plugins/MLAntiCheat`

После обучения сделайте резервную копию папки плагина.

### Лицензия

Плагин распространяется по лицензии MIT. Другие люди могут использовать, менять и публиковать исходный код, но должны сохранить текст лицензии и имя автора. Автор не отвечает за проблемы, которые появились при использовании чужих версий плагина.
