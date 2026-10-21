# Gitbench: Git-интерфейс в стиле IntelliJ для STM32CubeIDE

Дата: 2026-10-21. Статус: черновик дизайна, ждёт одобрения.

## Зачем

Пользователь работает с git в STM32CubeIDE через консоль и EGit.
Интерфейс EGit ему неудобен. Ему нравится Git-интерфейс IntelliJ IDEA:
окно Commit, Git Log с деревом веток, diff и resolve конфликтов.

Gitbench — Eclipse-плагин, который повторяет этот интерфейс.
Он повторяет структуру панелей, иконки и поведение IntelliJ.
Исходники IntelliJ лежат в `D:\Projects\intellij-community`.
Они служат эталоном вида и поведения.

Критерий успеха: пользователь делает ежедневную работу с git
в CubeIDE без консоли и без EGit. Работа должна ощущаться как в IntelliJ.

## Цели (порядок реализации)

Пользователь утвердил порядок. Каждый этап даёт рабочий результат,
пользователь смотрит его и только потом идёт следующий этап.

1. Вид **Commit**.
2. Вид **Git Log** и вид **Branches** (дерево веток).
3. **Split diff** и **unified diff**.
4. **Resolve конфликтов в 3 колонки** и окно списка конфликтов.
5. **Маркеры изменений на полях** редактора и **annotate (blame)**.
6. **История файла**.

## Не цели (первая версия)

- Shelf, Stash, Staging Area как отдельные виды.
- Combined diff, three-side diff для staging, binary, image и directory diff.
- Local History IDE.
- Create/Apply Patch.
- Popup веток в тулбаре (branch widget), interactive rebase.
- GitHub/GitLab pull requests.
- Готовая перспектива. Пользователь сам собирает перспективу из видов.

## Анти-цели

- Свой вид вместо вида IntelliJ. Если элемент есть в IntelliJ, он
  повторяет его место, иконку, подпись и порядок. Отступать можно, только
  если Eclipse это не позволяет. Каждое такое отступление записать.
- Конфликт с EGit. Плагин работает, когда EGit установлен, и не ломает его.
- Названия и логотипы JetBrains, IntelliJ или ST в имени и UI плагина.

## Целевая платформа

- STM32CubeIDE 2.0.0 и новее. Версия 2.0.0 построена на Eclipse 4.33
  (2024-09). Минимальная целевая платформа — Eclipse 4.33, Java 17.
- Windows — основная ОС пользователя. Linux и macOS не проверяем, но и не ломаем
  намеренно.
- Светлая и тёмная темы Eclipse. Цвета берём из светлой и тёмной тем IntelliJ.

## Решения о технологиях

Решения пользователя:
- **Сборка — Gradle.** Если Gradle не даёт собрать рабочий плагин, допустим
  Maven + Tycho.
- Виды отдельные. Commit и Git Log — два разных вида.

Решения Claude (пользователь может их поменять):
- **Java + SWT/JFace**, обычный Eclipse-плагин (OSGi bundle).
- **Gradle-сборка:** OSGi-манифест пишем вручную (`META-INF/MANIFEST.MF`).
  Зависимости Eclipse для компиляции берём из папки `plugins` установленной
  CubeIDE. Так версии точно совпадают с IDE. Kotlin — только в
  перенесённом коде графа; свой код пишем на Java.
- **Установка в первой версии:** jar в папку `dropins` у STM32CubeIDE.
  p2 update site — позже.
- **Git-бэкенд:** git CLI через `ProcessBuilder`, как в IntelliJ.
  Путь к `git.exe` задаётся в Preferences, по умолчанию берётся из `PATH`.
  JGit не используем, чтобы не делить состояние с EGit.
- **Граф коммитов:** копируем исходники `platform/vcs-log/graph` и
  `platform/vcs-log/graph-api` (около 6 тыс. строк, Java и Kotlin)
  в плагин без изменений. Kotlin-код оставляем на Kotlin, чтобы граф
  совпадал с IntelliJ точно и проходил оригинальные тесты модуля.
  Зависимости от платформы IntelliJ (`ContainerUtil`, `Logger`, `Pair`,
  `SmartList`, `SLRUMap` и т. п., около 15 классов) заменяем маленькими
  своими классами с теми же именами. kotlin-stdlib и fastutil встраиваем
  в bundle, лишние классы убирает shadow-плагин. Отрисовку
  `SimpleGraphCellPainter` (Graphics2D) переписываем на SWT `GC`. Цвета
  линий считаем по формуле `DefaultColorGenerator`. (Изменено на этапе 2:
  раньше план был перевести Kotlin на Java.)
- **Иконки:** SVG из `intellij-community` (`platform/icons/src/expui/**`,
  `platform/dvcs-impl/shared/resources/icons/new/**` и др.), светлые и
  `_dark`. Eclipse 4.33 не умеет SVG, поэтому сборка растеризует их в PNG
  16 px и `@2x`. Тёмный вариант выбираем по активной теме Eclipse.
- **Лицензия:** исходный код и иконки IntelliJ — Apache 2.0. Сохраняем
  копирайты в файлах и добавляем `NOTICE`.
- **Несколько репозиториев:** виды показывают репозиторий проекта, который
  выбран в Project Explorer или открыт в редакторе. Если в workspace несколько
  репозиториев, в виде есть выбор репозитория, как в IntelliJ multi-root.
- **Где проект:** `D:\Projects\gitbench`, свой git-репозиторий.

## Части системы

### 1. Вид Commit

Эталон: tool window Commit (`platform/vcs-impl/.../vcs/commit/ChangesViewCommitPanel.kt`,
`platform/vcs-impl/shared/.../changes/ui/ChangesListView.java`).

Сверху вниз:
- Тулбар: Refresh, Rollback, Shelve, View Options (глаз: группировка по
  папкам), Expand All, Collapse All. Shelve в первой версии скрыт, потому что
  Shelf — не цель.
- Дерево: узел **Changes** с изменёнными файлами, узел **Unversioned Files**.
  Чекбоксы на узлах и файлах. Группировка по папкам. Счётчик файлов серым.
  Цвет имени файла по статусу (изменён, добавлен, удалён), как в IntelliJ.
- Двойной клик по файлу открывает split diff (часть 3).
- Контекстное меню: Show Diff, Rollback, Add to VCS, Ignore, Jump to Source,
  Copy path.
- Ниже: чекбокс **Amend**, кнопка истории сообщений (часы), поле сообщения
  коммита.
- Кнопки **Commit**, **Commit and Push...**, шестерёнка опций (Sign-off,
  author).
- Commit and Push открывает диалог Push: список исходящих коммитов, целевая
  ветка, кнопка Push. Эталон: `platform/dvcs-impl/.../push/ui/VcsPushDialog.java`.

### 2. Виды Git Log и Branches

Эталон: `platform/vcs-log/impl/.../ui/frame/MainFrame.java`,
`plugins/git4idea/backend/src/ui/branch/dashboard/*`.

**Branches** — отдельный вид:
- Вертикальный тулбар слева: New Branch, Update Selected, Delete, Compare
  with Current, Show My Branches, Favorites, Navigate Log to Branch, Settings.
- Поиск сверху. Дерево: HEAD (Current Branch), Local, Remote → remote →
  ветки. Иконки текущей ветки, обычной ветки, избранного, папки.
- Выбор ветки фильтрует Git Log. Двойной клик делает checkout.
- Контекстное меню ветки: Checkout, New Branch from, Compare with Current,
  Rebase Current onto, Merge into Current, Push, Rename, Delete.

**Git Log** — отдельный вид:
- Строка фильтров: поле Text or hash с regex и Match case, Branch, User,
  Date, Paths.
- Таблица: граф, сообщение, метки веток и тегов, автор, дата.
- Панель деталей коммита и дерево изменённых файлов коммита справа, как в
  IntelliJ. Двойной клик по файлу открывает diff.
- Контекстное меню коммита: Copy Revision Number, Create Patch (скрыт),
  Cherry-Pick, Checkout Revision, Reset Current Branch to Here, Revert
  Commit, New Branch, New Tag, Go to Parent/Child.
- Тулбар: Refresh, Fetch.

### 3. Split и unified diff

Эталон: `platform/diff-impl/.../tools/simple/SimpleDiffViewer.java`,
`.../tools/fragmented/UnifiedDiffViewer.java`.

- Открывается как редактор (editor tab), не как диалог.
- **Split:** две колонки, полоса-разделитель с кривыми связей между блоками,
  кнопки `>>` / `<<` (и `x`) для переноса блоков в рабочий файл, подсветка
  изменений внутри строки (слова).
- **Unified:** одна колонка, удалённые и добавленные строки.
- Тулбар: переключатель Split/Unified, предыдущее/следующее изменение,
  предыдущий/следующий файл, Ignore whitespace, Highlight words/lines.
- Правая сторона редактируемая, если это рабочий файл.
- Синтаксическая подсветка C/C++ из CDT, если CDT есть.

### 4. Resolve в 3 колонки

Эталон: `platform/diff-impl/.../merge/TextMergeViewer.kt`,
`MergeThreesideViewer.java`, `platform/vcs-impl/.../merge/MultipleFileMergeDialog.kt`.

- Окно списка конфликтов: файлы, кнопки Accept Yours, Accept Theirs, Merge...
- Окно merge: Yours | Result | Theirs. Result в центре и редактируется.
- Кнопки `>>` / `<<` / `x` на блоках, Apply All Non-Conflicting Changes
  (слева, справа, все), предыдущий/следующий конфликт, счётчик оставшихся.
- Открывается из Commit-вида (узел конфликтов) и после неудачного merge,
  rebase или cherry-pick.

### 5. Маркеры на полях и annotate

Эталон: `platform/diff-impl/.../openapi/vcs/ex/LineStatusMarkerRenderer.kt`,
`platform/vcs-impl/.../actions/AnnotateToggleAction.java`.

- Маркеры: свой ruler column в редакторах (extension point
  `org.eclipse.ui.texteditor.rulerColumns`). Цвета и формы как в IntelliJ:
  добавлено, изменено, удалено. Клик открывает popup: diff блока, Rollback,
  предыдущее/следующее изменение, Show Diff.
- Annotate: команда в контекстном меню поля. Колонка показывает дату и автора,
  цвет по возрасту коммита. Клик открывает коммит в Git Log. Есть Close
  Annotations.

### 6. История файла

Эталон: `Vcs.ShowTabbedFileHistory`, `platform/vcs-log/impl/.../ui/actions/history/*`.

- Открывается из контекстного меню файла: Git → Show History.
- Использует ту же таблицу, что Git Log, с фильтром по пути файла.
- Снизу или сбоку — diff выбранной ревизии с предыдущей.

## Ограничения

- Только git. Других VCS нет.
- Нет вызовов сети, кроме самого git (fetch, pull, push).
- Долгие git-команды выполняются вне UI-потока. Прогресс показывается через
  Eclipse Jobs.
- Вывод всех git-команд пишется в консоль Gitbench, как Git Console в IntelliJ.

## Уточнения (решения Claude после проверки документа)

- **Что коммитит Commit-вид.** Коммит берёт файлы, отмеченные чекбоксами, и
  игнорирует ручной staging. Так работает IntelliJ без Staging Area.
  Отмеченные Unversioned Files сначала добавляются в git.
- **Горячие клавиши.** Глобальных сочетаний нет, потому что Ctrl+K и Ctrl+D уже
  заняты в Eclipse. Сочетания IntelliJ (F7, Shift+F7, Ctrl+D и т. п.)
  работают только внутри видов и редакторов Gitbench.
- **Большие репозитории.** Git Log загружает первые 1000 коммитов и
  догружает следующие при прокрутке.
- **Цикл разработки.** Gradle-задача собирает jar и копирует его в
  `dropins` той CubeIDE, путь к которой задан Gradle-свойством. После этого
  нужен перезапуск IDE.

## Что решает строитель

- Внутреннее устройство пакетов и классов.
- Алгоритм diff. Подходит библиотека java-diff-utils (Apache 2.0) или свой
  порт сравнения из `platform/diff-impl/.../comparison`.
- Как сочетать SourceViewer и CDT для подсветки.
- Как растеризовать SVG при сборке (например, Apache Batik в Gradle task).
- Тесты: unit-тесты для разбора вывода git и для графа. UI проверяем вручную
  в STM32CubeIDE.
