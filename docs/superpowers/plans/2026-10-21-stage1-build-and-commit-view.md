# Этап 1: каркас сборки и вид Commit — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Плагин Gitbench ставится в STM32CubeIDE 2.0.0 и даёт вид Commit, который выглядит и работает как окно Commit в IntelliJ, включая Commit and Push.

**Architecture:** Один OSGi bundle `gitbench`. Пакет `gitbench.git` — чистая Java без Eclipse: запуск git CLI, разбор вывода, операции. Пакет `gitbench.model` — чистая Java: дерево изменений и состояние чекбоксов. Пакет `gitbench.ui` — SWT/JFace-вид, действия, диалоги, консоль, иконки. Чистые пакеты покрыты unit-тестами на реальных временных git-репозиториях. UI проверяется вручную в CubeIDE по чек-листу.

**Tech Stack:** Java 17, Gradle (wrapper 8.x), JUnit 5, AssertJ, Apache Batik (только при сборке, для PNG), Eclipse 4.33 API из установленной CubeIDE.

**Spec:** `D:\Projects\gitbench\docs\superpowers\specs\2026-10-21-gitbench-design.md`

**Commits:** у пользователя правило «коммит только по явной просьбе». В плане нет шагов commit. Коммить, только когда пользователь попросит.

## Global Constraints

- Целевая платформа: Eclipse 4.33 (STM32CubeIDE 2.0.0) и новее, Java 17.
- Сборка — Gradle. Maven + Tycho только если Gradle не даёт рабочий плагин.
- Установка первой версии — jar в `dropins` у CubeIDE.
- Git-бэкенд — git CLI через `ProcessBuilder`. JGit не используем. Путь к `git` задан в Preferences, по умолчанию `git` из `PATH`.
- Плагин не ломает EGit и работает, когда EGit установлен.
- В имени и UI нет слов JetBrains, IntelliJ, ST, Cube.
- Иконки и код из IntelliJ под Apache 2.0: копирайт-комментарий в SVG сохраняется, в корне есть `NOTICE`.
- Глобальных горячих клавиш нет. Сочетания работают только внутри видов Gitbench.
- Долгие git-команды идут вне UI-потока, через Eclipse `Job`. Вывод каждой команды пишется в консоль «Gitbench Git».
- Цвета статусов файлов (из `expUI_lightScheme.xml` / `expUI_darkScheme.xml`):

  | Статус | Светлая | Тёмная |
  |---|---|---|
  | MODIFIED, RENAMED | `#0033B3` | `#70AEFF` |
  | ADDED | `#067D17` | `#73BD79` |
  | DELETED | `#6C707E` | `#6F737A` |
  | UNVERSIONED | `#B23247` | `#E88F89` |
  | CONFLICT | `#DE1B2E` | `#DE6A66` |

## Review Focus

1. **Имена файлов с пробелами, кириллицей и в кавычках.** Ожидание: файл виден в дереве с правильным именем и коммитится. Тест в Task 3 (`parsesUnicodeAndSpacesWithNulSeparators`) и Task 4 (`commitsFileWithSpaceAndCyrillicName`).
2. **Репозиторий без коммитов (свежий `git init`).** Ожидание: вид показывает файлы как Unversioned, первый коммит проходит, Amend выключен. Тест в Task 4 (`firstCommitInEmptyRepository`), Task 9 (`outgoingWithoutUpstreamListsAllCommits`).
3. **Git не найден или путь в Preferences неверный.** Ожидание: понятное сообщение в виде, без исключения в логе Eclipse. Тест в Task 2 (`missingGitExecutableGivesReadableError`).
4. **Переименованный файл отмечен для коммита.** Ожидание: в коммит попадают и старый, и новый путь, иначе git оставит половину переименования. Тест в Task 4 (`commitsRenameWithBothPaths`).
5. **Push отклонён (remote ушёл вперёд).** Ожидание: диалог показывает, что push отклонён, и предлагает сделать Update, а не молчит. Тест в Task 9 (`rejectedPushIsReported`).

---

## Файлы

```
D:\Projects\gitbench\
  settings.gradle, build.gradle, gradle.properties, gradlew(.bat), gradle/wrapper/*
  NOTICE
  buildSrc/build.gradle
  buildSrc/src/main/java/gitbench/build/RasterizeIconsTask.java
  icons/svg/*.svg                         исходные SVG из intellij-community (+ *_dark.svg)
  META-INF/MANIFEST.MF
  plugin.xml
  src/main/java/gitbench/
    git/GitExecutable.java  GitRunner.java  GitResult.java  GitException.java
    git/GitRepository.java  RepositoryLocator.java
    git/FileChange.java  ChangeKind.java  StatusParser.java  StatusService.java
    git/CommitService.java  RollbackService.java  IgnoreService.java
    git/OutgoingCommit.java  PushService.java  PushResult.java
    model/ChangesTreeNode.java  ChangesTreeBuilder.java  InclusionModel.java
    model/MessageHistory.java
    ui/Activator.java  Theme.java  GitbenchIcons.java  StatusColors.java
    ui/GitConsole.java  GitPreferencePage.java  RepositoryContext.java
    ui/commit/CommitView.java  ChangesTreeViewer.java  CommitPanel.java
    ui/commit/actions/*.java
    ui/push/PushDialog.java
  src/test/java/gitbench/...              тесты, пакеты как у кода
  src/test/java/gitbench/testutil/TempRepo.java
```

---

### Task 1: Каркас Gradle-сборки, установка в dropins

**Files:**
- Create: `settings.gradle`, `build.gradle`, `gradle.properties`, Gradle wrapper, `META-INF/MANIFEST.MF`, `plugin.xml`, `NOTICE`, `src/main/java/gitbench/ui/Activator.java`, `src/test/java/gitbench/BuildSmokeTest.java`

**Interfaces:**
- Produces: свойство Gradle `cubeIdeHome` (папка, где лежат `plugins/` и `dropins/` CubeIDE); задачи `build`, `test`, `deployDropins`; класс `gitbench.ui.Activator extends AbstractUIPlugin` с `static Activator getDefault()` и `static final String PLUGIN_ID = "gitbench"`.

- [ ] **Step 1: Найти Gradle и CubeIDE.** Выполнить `gradle --version`. Если Gradle нет, скачать дистрибутив 8.x в scratchpad и запустить `wrapper` оттуда. Спросить у пользователя путь к установке CubeIDE, если его нет в `gradle.properties`. Типичный путь: `C:\ST\STM32CubeIDE_2.0.0\STM32CubeIDE`. Проверка: в этой папке есть `plugins\org.eclipse.ui.workbench_*.jar`.

- [ ] **Step 2: Написать `build.gradle`.**
  - `java` с `toolchain` 17. `sourceSets.main.resources` не нужен: корневые `plugin.xml`, `META-INF/`, `icons/` и сгенерированные PNG кладутся в jar через `jar { from(...) }`.
  - `compileOnly fileTree("${cubeIdeHome}/plugins")` с include для jar-файлов этих bundle: `org.eclipse.swt*`, `org.eclipse.jface*`, `org.eclipse.ui.workbench_*`, `org.eclipse.ui_*`, `org.eclipse.ui.ide_*`, `org.eclipse.ui.console_*`, `org.eclipse.core.runtime_*`, `org.eclipse.core.resources_*`, `org.eclipse.core.jobs_*`, `org.eclipse.equinox.common_*`, `org.eclipse.equinox.preferences_*`, `org.eclipse.osgi_*`, `org.eclipse.core.commands_*`, `org.eclipse.e4.ui.*`. Некоторые bundle лежат папками. Для них брать `**/*.jar` внутри.
  - `testImplementation` JUnit 5 и AssertJ с Maven Central. Тесты не зависят от Eclipse-классов.
  - `jar.manifest.from('META-INF/MANIFEST.MF')`.
  - Задача `deployDropins` (Copy): jar в `${cubeIdeHome}/dropins/`, старые `gitbench_*.jar` там удалить.

- [ ] **Step 3: Написать `META-INF/MANIFEST.MF`.** Значения: `Bundle-SymbolicName: gitbench;singleton:=true`, `Bundle-Version: 0.1.0.qualifier` (qualifier Gradle заменяет на дату сборки), `Bundle-Activator: gitbench.ui.Activator`, `Bundle-RequiredExecutionEnvironment: JavaSE-17`, `Bundle-ActivationPolicy: lazy`, `Require-Bundle: org.eclipse.ui, org.eclipse.ui.ide, org.eclipse.ui.console, org.eclipse.core.runtime, org.eclipse.core.resources, org.eclipse.jface.text`.

- [ ] **Step 4: Написать `plugin.xml`.** Категория видов `gitbench.category` с именем «Gitbench». Вид `gitbench.views.commit`, имя «Commit», класс `gitbench.ui.commit.CommitView`, иконка `icons/png/light/commitToolWindow.png`. Пока класс вида — пустой `ViewPart` с `Label("Gitbench")`.

- [ ] **Step 5: Написать `NOTICE`.** Текст: часть иконок и алгоритмов взята из IntelliJ Community (https://github.com/JetBrains/intellij-community), © JetBrains s.r.o. and contributors, Apache License 2.0.

- [ ] **Step 6: Написать smoke-тест `BuildSmokeTest.manifestHasBundleName`.** Тест читает `META-INF/MANIFEST.MF` из корня проекта и проверяет, что `Bundle-SymbolicName` начинается с `gitbench`.

- [ ] **Step 7: Проверить сборку.** Run: `./gradlew build`. Expected: `BUILD SUCCESSFUL`, в `build/libs/` один `gitbench-0.1.0.jar`. Проверить, что внутри jar есть `plugin.xml` и `META-INF/MANIFEST.MF` без строки `qualifier`.

- [ ] **Step 8: Проверить установку вручную.** Run: `./gradlew deployDropins`, запустить CubeIDE. Expected: Window → Show View → Other → Gitbench → Commit открывает вид с текстом «Gitbench». В Error Log нет ошибок `gitbench`. Это проверяет пользователь, попросить его подтвердить.

---

### Task 2: Запуск git, поиск репозитория, Preferences

**Files:**
- Create: `git/GitExecutable.java`, `GitRunner.java`, `GitResult.java`, `GitException.java`, `GitRepository.java`, `RepositoryLocator.java`, `ui/GitPreferencePage.java`, `ui/GitConsole.java`
- Test: `src/test/java/gitbench/git/GitRunnerTest.java`, `RepositoryLocatorTest.java`, `testutil/TempRepo.java`

**Interfaces:**
- Produces:
  - `record GitResult(int exitCode, byte[] stdout, String stderr)` с `String stdoutText()` (UTF-8) и `boolean ok()`.
  - `class GitException extends Exception` с `String userMessage()`.
  - `final class GitRunner { GitRunner(Supplier<String> gitPath, GitCommandListener listener); GitResult run(Path workDir, List<String> args, byte[] stdin) throws GitException; }`
  - `interface GitCommandListener { void onCommand(Path workDir, List<String> args, GitResult result); }`
  - `record GitRepository(Path root)`.
  - `final class RepositoryLocator { RepositoryLocator(GitRunner runner); Optional<GitRepository> find(Path anyPathInside) throws GitException; }`
  - `TempRepo` (test util): `static TempRepo init()`, `Path root()`, `void write(String rel, String text)`, `void git(String... args)`, `void commitAll(String msg)`.

- [ ] **Step 1: Написать тесты.**
  - `GitRunnerTest.runsGitVersion`: `run(tmp, List.of("--version"), null)` → `ok()` и `stdoutText()` начинается с `"git version"`.
  - `GitRunnerTest.missingGitExecutableGivesReadableError`: `gitPath` = `"C:/no/such/git.exe"` → `GitException`, `userMessage()` содержит `"Git executable not found"` и этот путь.
  - `GitRunnerTest.nonZeroExitIsResultNotException`: `run(tmp, List.of("rev-parse", "HEAD"), null)` в пустой папке → `exitCode() != 0`, `stderr()` не пустой.
  - `GitRunnerTest.notifiesListener`: listener получает те же `args`.
  - `RepositoryLocatorTest.findsRootFromNestedDir`: `TempRepo` с `Core/Src/main.c` → `find(root/Core/Src)` даёт `root` (сравнивать `toRealPath()`).
  - `RepositoryLocatorTest.emptyOutsideRepo` → `Optional.empty()`.

- [ ] **Step 2: Запустить тесты.** Run: `./gradlew test --tests "gitbench.git.*"`. Expected: FAIL, классов нет.

- [ ] **Step 3: Реализовать `GitRunner`.** Каждый вызов добавляет перед `args` параметры `-c core.quotepath=false -c color.ui=false`. Окружение: `LC_ALL=C`, `GIT_TERMINAL_PROMPT=0`. stdout и stderr читаются в двух потоках, чтобы не было deadlock. `IOException` при старте процесса превращается в `GitException("Git executable not found: <path>. Set the path in Preferences → Gitbench.")`.

- [ ] **Step 4: Реализовать `RepositoryLocator`** через `git rev-parse --show-toplevel`.

- [ ] **Step 5: Запустить тесты.** Run: `./gradlew test --tests "gitbench.git.*"`. Expected: PASS.

- [ ] **Step 6: Реализовать UI-часть.**
  - `GitPreferencePage`: страница Preferences → Gitbench, `FileFieldEditor` «Path to Git executable», ключ `gitPath`, по умолчанию `git`, кнопка «Test» показывает вывод `git --version`. Регистрация в `plugin.xml` (`org.eclipse.ui.preferencePages`).
  - `GitConsole implements GitCommandListener`: `MessageConsole` «Gitbench Git». Строка `> git <args>` и stderr при ошибке. Запись в консоль через `Display.asyncExec`.
  - `Activator` держит один `GitRunner`. `gitPath` читается из preference store при каждом вызове.

---

### Task 3: Разбор `git status`

**Files:**
- Create: `git/ChangeKind.java`, `FileChange.java`, `StatusParser.java`, `StatusService.java`
- Test: `src/test/java/gitbench/git/StatusParserTest.java`, `StatusServiceTest.java`

**Interfaces:**
- Consumes: `GitRunner`, `GitRepository`, `TempRepo` (Task 2).
- Produces:
  - `enum ChangeKind { MODIFIED, ADDED, DELETED, RENAMED, UNVERSIONED, CONFLICT }`
  - `record FileChange(String path, String origPath, ChangeKind kind)`. `path` идёт относительно корня репо с `/`. `origPath` равен `null`, если это не rename.
  - `final class StatusParser { static List<FileChange> parse(byte[] porcelainV2z); }`
  - `final class StatusService { StatusService(GitRunner r); List<FileChange> status(GitRepository repo) throws GitException; }`. Команда: `status --porcelain=v2 -z --untracked-files=all`.

- [ ] **Step 1: Написать тесты парсера** на байтовых фикстурах (записи разделены `\0`):
  - `parsesModifiedInWorktree`: `1 .M N... 100644 100644 100644 <h> <h> src/a.c` → `MODIFIED`.
  - `parsesStagedAdd`: `1 A. ...` → `ADDED`.
  - `parsesDeletedInWorktree`: `1 .D ...` → `DELETED`.
  - `parsesRenameWithOrigPath`: `2 R. N... 100644 100644 100644 <h> <h> R100 new.c\0old.c` → `RENAMED`, `path="new.c"`, `origPath="old.c"`.
  - `parsesUntracked`: `? notes.txt` → `UNVERSIONED`.
  - `parsesUnmerged`: `u UU ...` → `CONFLICT`.
  - `ignoresIgnoredEntries`: `! build/` → пустой список.
  - `parsesUnicodeAndSpacesWithNulSeparators`: путь `Core/Src/мой файл.c` в UTF-8 → тот же путь.
  - Правило для X и Y: если хоть одна сторона `D`, тип `DELETED`. Иначе если X = `A`, тип `ADDED`. Иначе `MODIFIED`.

- [ ] **Step 2: Запустить тесты.** Run: `./gradlew test --tests "gitbench.git.StatusParserTest"`. Expected: FAIL.

- [ ] **Step 3: Реализовать `StatusParser.parse`.** Строки `# ...` (headers) пропускать.

- [ ] **Step 4: Написать `StatusServiceTest.reportsRealRepoState`.** `TempRepo`: commit `a.c` и `b.c`, изменить `a.c`, удалить `b.c`, создать `c.c` → три записи нужных типов.

- [ ] **Step 5: Запустить тесты.** Run: `./gradlew test --tests "gitbench.git.Status*"`. Expected: PASS.

---

### Task 4: Commit, rollback, add, ignore

**Files:**
- Create: `git/CommitService.java`, `RollbackService.java`, `IgnoreService.java`
- Test: `src/test/java/gitbench/git/CommitServiceTest.java`, `RollbackServiceTest.java`, `IgnoreServiceTest.java`

**Interfaces:**
- Consumes: `GitRunner`, `GitRepository`, `FileChange`, `StatusService`.
- Produces:
  - `record CommitOptions(boolean amend, boolean signOff, String author)`. `author` равен `null` или имеет вид `"Name <mail>"`.
  - `CommitService`:
    - `String commit(GitRepository repo, String message, List<FileChange> selected, CommitOptions opts) throws GitException` возвращает короткий hash.
    - `String lastCommitMessage(GitRepository repo) throws GitException` возвращает `null`, если коммитов нет.
    - `boolean hasCommits(GitRepository repo)`.
  - `RollbackService.rollback(GitRepository repo, List<FileChange> changes, boolean deleteAddedFiles) throws GitException`.
  - `CommitService.addToVcs(GitRepository repo, List<String> paths) throws GitException`.
  - `IgnoreService.addToGitignore(GitRepository repo, List<String> paths) throws GitException`.

- [ ] **Step 1: Написать тесты коммита.**
  - `commitsOnlySelectedFiles`: изменены `a.c` и `b.c`, выбран `a.c` → после коммита `status` показывает только `b.c` как `MODIFIED`.
  - `ignoresManualStaging`: `b.c` добавлен через `git add`, выбран только `a.c` → `b.c` не в коммите (`git show --name-only HEAD` содержит только `a.c`).
  - `addsSelectedUnversionedFirst`: выбран `UNVERSIONED` `new.c` → он в коммите.
  - `commitsRenameWithBothPaths`: `git mv old.c new.c`, выбран rename → `git show --name-status HEAD` содержит `R100`.
  - `commitsDeletedFile`: выбран `DELETED` → файл удалён из HEAD.
  - `amendReplacesLastCommit`: количество коммитов не меняется, сообщение новое.
  - `firstCommitInEmptyRepository`: `hasCommits` = false до коммита, true после. `lastCommitMessage` = null до.
  - `commitsFileWithSpaceAndCyrillicName`.
  - `signOffAddsTrailer`: сообщение HEAD содержит `Signed-off-by:`.

- [ ] **Step 2: Запустить тесты.** Run: `./gradlew test --tests "gitbench.git.CommitServiceTest"`. Expected: FAIL.

- [ ] **Step 3: Реализовать `CommitService.commit`.** Две команды:
  1. `git add -A -- <все выбранные path и origPath>`. Это добавляет новые файлы и фиксирует удаление старого пути при rename. Это нужно и в пустом репозитории.
  2. `git commit --only [--amend] [--signoff] [--author=...] -F - -- <те же пути>`. Сообщение передаётся через stdin. `--only` берёт только эти пути и не трогает остальной индекс.

  Hash берётся из `git rev-parse --short HEAD`.

- [ ] **Step 4: Запустить тесты.** Expected: PASS.

- [ ] **Step 5: Написать тесты rollback и ignore.**
  - `rollbackRestoresModified` → содержимое как в HEAD.
  - `rollbackRestoresDeleted` → файл снова есть.
  - `rollbackUnstagesAddedAndKeepsFile`: `deleteAddedFiles=false` → файл `UNVERSIONED`.
  - `rollbackDeletesAddedWhenAsked`: `deleteAddedFiles=true` → файла нет.
  - `rollbackRename` → `old.c` на месте, `new.c` нет в индексе.
  - `addToGitignoreAppendsLine`: `.gitignore` получает строку `/build/out.bin`. Повторный вызов строку не дублирует.

- [ ] **Step 6: Реализовать.** Rollback: для MODIFIED/DELETED `git restore --source=HEAD --staged --worktree -- <paths>`. Для ADDED `git rm --cached -- <paths>`, потом удалить файлы, если `deleteAddedFiles`. Для RENAMED: rollback нового пути как ADDED и старого как DELETED. Ignore: путь от корня репо с ведущим `/`, файл `.gitignore` в корне.

- [ ] **Step 7: Запустить тесты.** Run: `./gradlew test --tests "gitbench.git.*"`. Expected: PASS.

---

### Task 5: Иконки: SVG → PNG при сборке, выбор темы

**Files:**
- Create: `buildSrc/build.gradle`, `buildSrc/src/main/java/gitbench/build/RasterizeIconsTask.java`, `icons/svg/*.svg`, `ui/Theme.java`, `ui/GitbenchIcons.java`, `ui/StatusColors.java`
- Modify: `build.gradle` (задача `rasterizeIcons`, jar включает её вывод)
- Test: `src/test/java/gitbench/BuildSmokeTest.java` (новый тест)

**Interfaces:**
- Produces:
  - `enum Icon { REFRESH, ROLLBACK, VIEW_OPTIONS, EXPAND_ALL, COLLAPSE_ALL, HISTORY, SETTINGS, COMMIT_TOOL_WINDOW, PUSH, FOLDER, UNVERSIONED_FILES }` в `GitbenchIcons`. Метод `static Image get(Icon icon)` берёт тему из `Theme.isDark()` и кэширует картинки в `ImageRegistry` плагина.
  - `Theme.isDark()`: читает preference node `org.eclipse.e4.ui.css.swt.theme`, ключ `themeid`. Результат true, если значение содержит `dark`.
  - `StatusColors.foreground(ChangeKind kind)` возвращает `Color` из таблицы Global Constraints для текущей темы.

- [ ] **Step 1: Скопировать SVG** из `D:\Projects\intellij-community` в `icons/svg/` (светлый файл и `_dark` рядом, копирайт-комментарий не трогать):

  | Icon | Источник |
  |---|---|
  | REFRESH | `platform/icons/src/expui/general/refresh.svg` |
  | ROLLBACK | `platform/icons/src/expui/vcs/revert.svg` |
  | VIEW_OPTIONS | `platform/icons/src/expui/general/show.svg` |
  | EXPAND_ALL / COLLAPSE_ALL | `platform/icons/src/expui/general/expandAll.svg` / `collapseAll.svg` |
  | HISTORY | `platform/icons/src/expui/general/history.svg` |
  | SETTINGS | `platform/icons/src/expui/general/settings.svg` |
  | COMMIT_TOOL_WINDOW | `platform/icons/src/expui/toolwindows/commit.svg` |
  | PUSH | `platform/icons/src/expui/vcs/push.svg` |
  | FOLDER | `platform/icons/src/expui/nodes/folder.svg` |
  | UNVERSIONED_FILES | `platform/icons/src/expui/vcs/showUnversionedFiles.svg` |

  Имя PNG = имя enum в lowerCamel (`commitToolWindow.png`).

- [ ] **Step 2: Написать `BuildSmokeTest.iconsRasterizedForBothThemes`.** Для каждого SVG из `icons/svg` (без `_dark`) в `build/generated/icons/png/light/` и `.../dark/` есть `<name>.png` 16×16 и `<name>@2x.png` 32×32. Размер читать через `javax.imageio.ImageIO`. Тест зависит от задачи `rasterizeIcons` (`test.dependsOn`).

- [ ] **Step 3: Реализовать `RasterizeIconsTask`** через Batik `PNGTranscoder` (`batik-transcoder` и `batik-codec` 1.17 в `buildSrc`). Если `_dark.svg` нет, тёмная тема берёт светлую. Если не хватает чего-то для Eclipse 4.33 `@2x`-разрешения, `GitbenchIcons` грузит `@2x` через `ImageDescriptor.createFromURL`, а Eclipse сам находит `@2x` рядом.

- [ ] **Step 4: Запустить.** Run: `./gradlew test --tests "gitbench.BuildSmokeTest"`. Expected: PASS.

---

### Task 6: Модель дерева изменений и чекбоксов

**Files:**
- Create: `model/ChangesTreeNode.java`, `ChangesTreeBuilder.java`, `InclusionModel.java`
- Test: `src/test/java/gitbench/model/ChangesTreeBuilderTest.java`, `InclusionModelTest.java`

**Interfaces:**
- Consumes: `FileChange`, `ChangeKind`.
- Produces:
  - `sealed interface ChangesTreeNode` с вариантами:
    - `record Root(String title, List<ChangesTreeNode> children, int fileCount, boolean unversioned)`, где `title` равен `"Changes"` или `"Unversioned Files"`;
    - `record Dir(String path, String label, List<ChangesTreeNode> children)`;
    - `record File(FileChange change)`.
  - `final class ChangesTreeBuilder { static List<Root> build(List<FileChange> changes, boolean groupByDirectory); }`
  - `final class InclusionModel { void setIncluded(Collection<FileChange> c, boolean on); boolean isIncluded(FileChange c); State stateOf(ChangesTreeNode n); List<FileChange> included(); void retainOnly(Collection<FileChange> current); }`. Здесь `enum State { ON, OFF, PARTIAL }`.

- [ ] **Step 1: Написать тесты.**
  - `alwaysHasChangesRoot`: пустой вход → один `Root("Changes", [], 0, false)`. Узла Unversioned нет.
  - `splitsUnversioned`: MODIFIED и UNVERSIONED → два корня, Changes первым.
  - `flatWhenNotGrouped`: `groupByDirectory=false` → файлы прямо под корнем, сортировка по имени файла без учёта регистра.
  - `compactsSingleChildDirs`: `Core/Src/a.c`, `Core/Src/b.c` → один `Dir` с `label="Core/Src"`, как в IntelliJ.
  - `dirsBeforeFiles`: папки идут раньше файлов на одном уровне.
  - `newChangesIncludedByDefault`: для MODIFIED/ADDED/DELETED/RENAMED `isIncluded` = true. Для UNVERSIONED false (как в IntelliJ).
  - `partialStateForMixedDir`: в `Dir` один файл включён, один нет → `PARTIAL`.
  - `retainOnlyDropsGoneFiles`, но сохраняет выбор пользователя для оставшихся.

- [ ] **Step 2: Запустить тесты.** Run: `./gradlew test --tests "gitbench.model.*"`. Expected: FAIL.

- [ ] **Step 3: Реализовать.** Ключ в `InclusionModel` — `path`. Новые пути получают значение по умолчанию из теста `newChangesIncludedByDefault`. Пользовательский выбор живёт, пока файл есть в статусе.

- [ ] **Step 4: Запустить тесты.** Expected: PASS.

---

### Task 7: Вид Commit (UI)

**Files:**
- Create: `ui/RepositoryContext.java`, `ui/commit/CommitView.java`, `ChangesTreeViewer.java`, `CommitPanel.java`, `ui/commit/actions/{RefreshAction,RollbackAction,ViewOptionsAction,ExpandAllAction,CollapseAllAction}.java`, `ui/commit/RollbackDialog.java`
- Modify: `plugin.xml`

**Interfaces:**
- Consumes: всё из Tasks 2–6.
- Produces:
  - `RepositoryContext`: `Optional<GitRepository> current()` и слушатели `addListener(Consumer<Optional<GitRepository>>)`. Репозиторий берётся из выделения в Project Explorer (`IResource` / `IAdaptable`) или из активного редактора. Если в workspace несколько репозиториев, вид показывает Combo с ними над деревом.
  - `CommitView.refresh()`: публичный метод, его вызывают действия других этапов.

Раскладка сверху вниз, как на скриншоте пользователя:
1. Тулбар вида (`IToolBarManager`): Refresh, Rollback, View Options (глаз, выпадающее меню «Group By → Directory» toggle, по умолчанию включено), Expand All, Collapse All. Подсказки: «Refresh», «Rollback...», «View Options», «Expand All», «Collapse All». Rollback неактивен, если в выделении нет версионных файлов.
2. `ChangesTreeViewer` на базе `CheckboxTreeViewer`:
   - Узел корня: `Changes` / `Unversioned Files` и счётчик серым: `1 file`, `N files`.
   - Файл: иконка типа файла из `IWorkbench.getEditorRegistry().getImageDescriptor(name)`, имя цветом `StatusColors`, путь папки серым, если группировка выключена.
   - Папка: иконка FOLDER.
   - Чекбоксы из `InclusionModel`, у `PARTIAL` вид grayed.
   - Двойной клик открывает файл в редакторе. На этапе 3 здесь будет diff.
3. `SashForm` (вертикальный) между деревом и `CommitPanel`.
4. `CommitPanel`: чекбокс «Amend» и кнопка HISTORY справа от него. Многострочный `StyledText` моноширинным шрифтом редактора. Кнопка «Commit» (default button, primary), кнопка «Commit and Push...», справа кнопка SETTINGS с меню: «Sign-off commit» (toggle), «Author...» (ввод `Name <mail>`).
   - Amend включён → если поле пустое, в него подставляется `lastCommitMessage`. Если в репо нет коммитов, Amend неактивен.
   - Commit неактивен, если ничего не отмечено (кроме Amend) или сообщение пустое. Подсказка при пустом сообщении: «Specify commit message».
   - После успешного коммита: сообщение уходит в `MessageHistory` (Task 8), поле очищается, Amend снимается, вид обновляется, внизу Eclipse видна строка статуса «N files committed: <первая строка>».
5. Обновление: при открытии вида, по Refresh, после своих операций и по `IResourceChangeListener` с задержкой 300 мс. `git status` идёт в `Job`, UI обновляется через `asyncExec`. Выделение и раскрытие узлов сохраняются.
6. Ошибки git показываются в `MessageDialog` с `GitException.userMessage()`. Полный вывод уходит в консоль.
7. Горячая клавиша внутри вида: Ctrl+Enter в поле сообщения = Commit (контекст `gitbench.context.commit`).

- [ ] **Step 1: Реализовать `RepositoryContext`, `ChangesTreeViewer`, `CommitPanel`, `CommitView`** по описанию выше.
- [ ] **Step 2: Реализовать `RollbackDialog`.** Заголовок «Rollback Changes», список файлов, чекбокс «Delete local copies of added files» (виден, если есть ADDED), кнопки «Rollback» / «Cancel».
- [ ] **Step 3: Собрать и поставить.** Run: `./gradlew build deployDropins`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Ручная проверка в CubeIDE (пользователь).** Чек-лист:
  - Вид в светлой и тёмной теме; иконки как в IntelliJ.
  - Изменённый, новый (Unversioned), удалённый, переименованный файл видны с правильными цветами.
  - Коммит одного из двух файлов. Amend. Ctrl+Enter.
  - Rollback изменённого и добавленного файла.
  - Два проекта из разных репозиториев: Combo переключает их.
  - Неверный путь к git в Preferences → понятное сообщение.

---

### Task 8: Контекстное меню и история сообщений

**Files:**
- Create: `model/MessageHistory.java`, `ui/commit/actions/{ShowDiffAction,JumpToSourceAction,CopyPathAction,AddToVcsAction,IgnoreAction,DeleteAction}.java`
- Test: `src/test/java/gitbench/model/MessageHistoryTest.java`

**Interfaces:**
- Consumes: `CommitService.addToVcs`, `IgnoreService.addToGitignore`, `RollbackService`, `CommitView.refresh()`.
- Produces: `final class MessageHistory { MessageHistory(int max); void add(String msg); List<String> recent(); String serialize(); static MessageHistory deserialize(String s, int max); }`, где `max = 25`.

- [ ] **Step 1: Написать тесты.**
  - `newestFirst`.
  - `duplicateMovesToTop`: дубликат не хранится дважды.
  - `trimsToMax`: 30 добавлений → 25.
  - `ignoresBlank`.
  - `roundTripsMultilineMessages`: сообщение с `\n` и `|` переживает `serialize`/`deserialize`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests "gitbench.model.MessageHistoryTest"`. Expected: FAIL. Потом реализовать (хранение в preference store плагина, ключ `messageHistory`) и снова запустить. Expected: PASS.
- [ ] **Step 3: Реализовать контекстное меню дерева**, порядок как `ChangesViewPopupMenu` в IntelliJ:
  1. Commit File...: отмечает только выделенные файлы и фокусирует поле сообщения.
  2. Rollback...
  3. Show Diff: неактивно до этапа 3.
  4. Jump to Source (F4).
  5. Copy Path.
  6. разделитель
  7. Delete
  8. Add to VCS (Ctrl+Alt+A): только для UNVERSIONED.
  9. Add to .gitignore: только для UNVERSIONED.
- [ ] **Step 4: Кнопка HISTORY** открывает popup-список `recent()`. Выбор подставляет сообщение в поле.
- [ ] **Step 5: Ручная проверка.** Каждый пункт меню на подходящем файле. История переживает перезапуск CubeIDE.

---

### Task 9: Commit and Push, диалог Push

**Files:**
- Create: `git/OutgoingCommit.java`, `PushService.java`, `PushResult.java`, `ui/push/PushDialog.java`
- Test: `src/test/java/gitbench/git/PushServiceTest.java`

**Interfaces:**
- Consumes: `GitRunner`, `GitRepository`, `TempRepo`, `CommitView`.
- Produces:
  - `record OutgoingCommit(String shortHash, String subject, String author, Instant date)`.
  - `record PushTarget(String remote, String branch, boolean hasUpstream)`.
  - `PushService`:
    - `PushTarget defaultTarget(GitRepository r)`;
    - `List<OutgoingCommit> outgoing(GitRepository r, PushTarget t)`;
    - `PushResult push(GitRepository r, PushTarget t)`.
  - `record PushResult(Status status, String message)` с `enum Status { OK, REJECTED, UP_TO_DATE, ERROR }`.

- [ ] **Step 1: Написать тесты.** Remote — bare-репо, созданное `TempRepo` через `git init --bare`.
  - `outgoingListsCommitsAheadOfUpstream`: 2 локальных коммита → 2 записи, новые сверху.
  - `outgoingWithoutUpstreamListsAllCommits`: ветка без upstream → все коммиты, которых нет ни в одной `refs/remotes/<remote>/*`. `hasUpstream=false`, `remote` = единственный remote или `origin`.
  - `pushSetsUpstreamOnFirstPush`: после push `git rev-parse --abbrev-ref @{u}` = `origin/<branch>`.
  - `rejectedPushIsReported`: второй клон пушит раньше → `Status.REJECTED`, `message` содержит `rejected`.
  - `upToDate` → `UP_TO_DATE`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests "gitbench.git.PushServiceTest"`. Expected: FAIL.
- [ ] **Step 3: Реализовать.**
  - `outgoing`: `git log --format=%h%x00%s%x00%an%x00%at%x00 <range>`. Range: `@{u}..HEAD` или `HEAD --not --remotes=<remote>`.
  - `push`: `git push --porcelain [-u] <remote> HEAD:refs/heads/<branch>`. Статус берётся из флага строки ref в porcelain-выводе: `!` = REJECTED, `=` = UP_TO_DATE.
- [ ] **Step 4: Запустить тесты.** Expected: PASS.
- [ ] **Step 5: Реализовать `PushDialog`.** Эталон: `VcsPushDialog`.
  - Заголовок «Push Commits to <repo name>».
  - Слева дерево: репозиторий → строка `<branch> → <remote> : <branch>` (target редактируемый, у новой ветки пометка `New`) → коммиты.
  - Справа список файлов выбранного коммита (`git show --name-status --format= <hash>`).
  - Кнопки «Push» (default) и «Cancel».
  - Push выполняется в `Job`. После него уведомление: OK → «Pushed N commits to <remote>/<branch>»; REJECTED → диалог «Push Rejected», текст «Remote has changes you don't have locally. Update your branch and push again.»; ERROR → `message`.
- [ ] **Step 6: Связать с Commit and Push...** Сначала коммит, после успеха открыть `PushDialog`. Если коммит упал, диалог не открывать.
- [ ] **Step 7: Ручная проверка в CubeIDE.** Commit and Push в репо с GitHub-remote. Первый push новой ветки. Отклонённый push.

---

## Что не входит в этап 1

- Diff, merge, annotate, маркеры, Git Log, Branches, история файла: это этапы 2–6. Они получат свои планы, когда пользователь посмотрит результат этапа 1.
- Shelve: кнопка скрыта по спецификации.
- Update Project (pull) на Push Rejected: появится вместе с Git Log.
