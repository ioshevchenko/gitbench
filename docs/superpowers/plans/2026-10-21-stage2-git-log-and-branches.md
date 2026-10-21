# Этап 2: виды Git Log и Branches — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Два новых вида Gitbench: **Git Log** (граф коммитов, фильтры, детали и файлы коммита, операции над коммитом) и **Branches** (дерево HEAD / Local / Remote, операции над ветками). Оба выглядят и работают как Git tool window IntelliJ.

**Architecture:** Граф коммитов — это перенесённые без изменений модули IntelliJ `platform/vcs-log/graph-api` и `platform/vcs-log/graph` (Java + Kotlin, Apache 2.0) с маленькими shim-классами вместо платформы IntelliJ. Их оригинальные тесты переносятся тоже и доказывают, что порт точный. Новый Java-код:
- `gitbench.log`: загрузка refs, топологии и деталей коммитов, фильтры;
- `gitbench.branch`: модель дерева веток и операции;
- `gitbench.ui.log`, `gitbench.ui.branch`: SWT-виды.

Чистые пакеты тестируются на временных репозиториях. UI проверяет пользователь в CubeIDE.

**Tech Stack:** Java 17, Kotlin 2.x (только перенесённый граф), Gradle + Shadow (`com.gradleup.shadow`), JUnit 5 + JUnit Vintage (для тестов графа на JUnit 4), AssertJ, Eclipse 4.33 API.

**Spec:** `D:\Projects\gitbench\docs\superpowers\specs\2026-10-21-gitbench-design.md`, разделы «Части системы → 2. Виды Git Log и Branches», «Решения о технологиях → Граф коммитов».

**Commits:** пользователь разрешил коммитить в этом проекте. Один коммит на задачу, без `Co-Authored-By`.

## Global Constraints

- Всё из Global Constraints этапа 1 действует: Eclipse 4.33+, Java 17, git CLI, без глобальных горячих клавиш, долгие команды вне UI-потока, вывод git в консоль «Gitbench Git», цвета статусов New UI.
- Kotlin только в `third_party/intellij-graph/`. Свой код — Java.
- Перенесённые файлы IntelliJ не правятся. Если без правки нельзя, правка помечается комментарием `// Gitbench:` и записывается в ledger.
- Каждый перенесённый файл сохраняет свой копирайт-заголовок. `NOTICE` перечисляет перенесённые модули.
- Плагин ставится тем же путём: `deployDropins`, jar берётся из `shadowJar`.
- Иконки только из `intellij-community` (New UI, светлые и `_dark`), через `GitbenchIcons`.

## Review Focus

1. **Большая история.** Репозиторий с 20 000 коммитов: Git Log открывается за разумное время, прокрутка не тормозит. Тест в Task 3: `loadsTwentyThousandCommitsUnderThreeSeconds`.
2. **Пустой репозиторий и detached HEAD.** Пустой репозиторий: Git Log пустой, без исключений. Detached HEAD: в Branches узел HEAD показывает hash. Тесты в Task 3 (`emptyRepositoryHasNoCommits`) и Task 9 (`detachedHeadIsShownByHash`).
3. **Имена веток со слэшами и кириллицей.** `feature/uart/фикс` раскладывается по папкам `feature` → `uart` и checkout работает. Тест в Task 9 (`groupsSlashedNamesIntoFolders`) и Task 11 (`checksOutCyrillicBranch`).
4. **Merge или rebase с конфликтом.** Понятное сообщение «Conflicts during merge, resolve them in the Commit view». Commit-вид показывает файлы как CONFLICT, ничего не зависает. Тест в Task 11: `mergeWithConflictReportsConflict`.
5. **Checkout remote-ветки, если локальная с тем же именем уже есть.** Как в IntelliJ: checkout локальной ветки и предложение обновить её, а не ошибка git. Тест в Task 11: `checkoutRemoteWhenLocalExistsUsesLocal`.

---

## Файлы

```
third_party/intellij-graph/
  src/            перенесённые com/intellij/vcs/log/graph/** (graph-api + graph), без изменений
  shims/          com/intellij/** shim-классы (ContainerUtil, Logger, Pair, ...)
  test/           перенесённые тесты графа
  testShims/      shim-классы только для тестов (MultiMap, StringUtil, FileUtil, PathManagerEx)
  testData/       перенесённые тестовые данные
  README.md       откуда и какой ревизии взято, список правок `// Gitbench:`
src/main/java/gitbench/
  log/   GitRef.java RefType.java RefsLoader.java CommitTopology.java TopologyLoader.java
         CommitIndex.java CommitDetails.java DetailsLoader.java LogFilter.java FilterService.java
         LogModel.java LogRow.java HeadRefColors.java CommitOperations.java ResetMode.java
  branch/ BranchTreeNode.java BranchTreeBuilder.java Favorites.java BranchOperations.java
          AheadBehind.java
  ui/log/ GitLogView.java CommitTable.java GraphPainter.java GraphColors.java RefLabelPainter.java
          FilterBar.java CommitDetailsPanel.java LogContextMenu.java ResetDialog.java GoToRefDialog.java
  ui/branch/ BranchesView.java BranchesTreeViewer.java BranchesToolbar.java BranchContextMenu.java
             NewBranchDialog.java
  ui/RepositoryEvents.java    общая шина «репозиторий изменился» для всех видов
```

---

### Task 1: Kotlin и Shadow в сборке

**Files:**
- Modify: `build.gradle`, `META-INF/MANIFEST.MF`, `NOTICE`
- Test: `src/test/java/gitbench/BuildSmokeTest.java` (новый тест)

**Interfaces:**
- Produces: исходный набор `third_party/intellij-graph/{src,shims}` компилируется вместе с `src/main/java`. Тестовый набор `third_party/intellij-graph/{test,testShims}` идёт вместе с `src/test/java`. Задача `shadowJar` даёт итоговый jar. `deployDropins` и проверки jar используют `shadowJar`.

- [ ] **Step 1: Написать тест `BuildSmokeTest.shadowJarEmbedsKotlinAndFastutil`.** Тест открывает jar из `build/libs` (последний `gitbench_*.jar`) и проверяет:
  - есть `kotlin/collections/CollectionsKt.class`;
  - есть `it/unimi/dsi/fastutil/ints/IntArrayList.class`;
  - есть `plugin.xml`;
  - размер jar меньше 4 МБ.

  Тест зависит от `shadowJar` (`test.dependsOn shadowJar`).
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests gitbench.BuildSmokeTest`. Expected: FAIL (нет Kotlin-классов).
- [ ] **Step 3: Настроить сборку.**
  - Плагин `org.jetbrains.kotlin.jvm` 2.1.x, `jvmTarget = 17`. Kotlin-исходники лежат в `third_party/intellij-graph/src` и `shims`.
  - Зависимости:
    - `implementation`: `org.jetbrains.kotlin:kotlin-stdlib`, `it.unimi.dsi:fastutil-core:8.5.15`;
    - `compileOnly`: `org.jetbrains:annotations:26.0.2`;
    - тесты: `junit:junit:4.13.2` и `org.junit.vintage:junit-vintage-engine`.
  - `shadowJar`:
    - `minimize()`, но `exclude(project(...))` не нужен: свои классы не минимизируются;
    - манифест из `META-INF/MANIFEST.MF` с заменой `qualifier`, как у `jar`;
    - имя `gitbench_<version>.<qualifier>.jar`;
    - без relocation, потому что OSGi изолирует classloader bundle.
  - `jar` отключить (`enabled = false`), чтобы в `build/libs` был один jar.
- [ ] **Step 4: `NOTICE`.** Дописать: «Commit graph: IntelliJ Community modules platform/vcs-log/graph-api and platform/vcs-log/graph, Apache License 2.0. Bundles kotlin-stdlib (Apache 2.0) and fastutil (Apache 2.0).»
- [ ] **Step 5: Запустить.** Run: `./gradlew test`. Expected: PASS, все прежние 81 тест плюс новый.

---

### Task 2: Перенос графа IntelliJ и его тестов

**Files:**
- Create: `third_party/intellij-graph/**`, `third_party/intellij-graph/README.md`

**Interfaces:**
- Produces: исходный API IntelliJ в пакете `com.intellij.vcs.log.graph`, без изменений. Ключевые классы:
  - `PermanentGraphImpl.newInstance(List<GraphCommit<Id>>, GraphColorGetterFactory<Id>, Comparator<Id> headCommitsComparator, Set<Id> branchesCommitId)`;
  - `PermanentGraph.createVisibleGraph(Options, Set<Id> heads, Set<Id> matched)`;
  - `VisibleGraph.getVisibleCommitCount()`, `getRowInfo(int)`, `getVisibleRowIndex(Id)`, `getRecommendedWidth()`;
  - `RowInfo.getPrintElements()`;
  - `NodePrintElement`, `EdgePrintElement`;
  - `GraphColorManager`, `GraphColorGetterByHead`.

- [ ] **Step 1: Скопировать.** Источник: `D:\Projects\intellij-community\platform\vcs-log\graph-api\src`, `...\graph\src`, `...\graph\test`, `...\graph\testData`. Файлы не менять. В `README.md` записать hash коммита `intellij-community` (`git -C D:/Projects/intellij-community rev-parse HEAD`).
- [ ] **Step 2: Собрать.** Run: `./gradlew compileKotlin compileJava`. Expected: FAIL на отсутствующих `com.intellij.*`. Это список нужных shim.
- [ ] **Step 3: Написать shim-классы** в `third_party/intellij-graph/shims`. FQN и сигнатуры те же, но только используемые методы. Ожидаемый список:
  - `com.intellij.util.containers.ContainerUtil`, `SmartList`, `SLRUMap`, `ImmutableList`, `CollectionFactory`;
  - `com.intellij.openapi.diagnostic.Logger` (пишет в `System.Logger`);
  - `com.intellij.openapi.util.Pair`, `Ref`, `Condition`, `Comparing`;
  - `com.intellij.util.Function`, `com.intellij.util.ArrayUtilRt`;
  - `com.intellij.openapi.progress.ProcessCanceledException`;
  - `com.google.common.primitives.Ints` (только используемый метод).

  Поведение копировать из `intellij-community` (`platform/util/...`). Это тоже Apache 2.0.
- [ ] **Step 4: Собрать тесты.** Shim для тестов в `testShims`: `MultiMap`, `StringUtil`, `FileUtil`. Путь к `testData` в `AbstractTestWithTextFile` и подобных берётся из `System.getProperty("gitbench.graphTestData")`. Это единственная правка перенесённого кода: пометка `// Gitbench:` и запись в README и ledger. Gradle передаёт свойство в `test`.
- [ ] **Step 5: Запустить тесты графа.** Run: `./gradlew test --tests "com.intellij.vcs.log.graph.*"`. Expected: PASS все. Если тест падает из-за shim, исправить shim, а не тест. Тест с `@Ignore` в оригинале остаётся пропущенным.
- [ ] **Step 6: Запустить всё.** Run: `./gradlew test`. Expected: PASS.

---

### Task 3: Загрузка refs, топологии и деталей коммитов

**Files:**
- Create: `log/RefType.java`, `GitRef.java`, `RefsLoader.java`, `CommitIndex.java`, `CommitTopology.java`, `TopologyLoader.java`, `CommitDetails.java`, `DetailsLoader.java`
- Test: `src/test/java/gitbench/log/RefsLoaderTest.java`, `TopologyLoaderTest.java`, `DetailsLoaderTest.java`

**Interfaces:**
- Consumes: `GitRunner`, `GitRepository`, `TempRepo` (этап 1).
- Produces:
  - `enum RefType { HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG }`.
  - `record GitRef(String name, String fullName, RefType type, String hash, String upstream, boolean current)`.
    - `name`: короткое имя (`main`, `origin/main`, `v1.0`);
    - `hash`: полный hash, для аннотированного тега — hash коммита;
    - `upstream`: имя upstream или `null`;
    - `current`: только у текущей локальной ветки.
  - `RefsLoader.load(GitRepository) → List<GitRef>`. Команда: `for-each-ref --format=%(objectname)%00%(*objectname)%00%(refname)%00%(upstream:short)%00%(HEAD) refs/heads refs/remotes refs/tags`. Плюс `rev-parse --verify -q HEAD` для HEAD. `refs/remotes/*/HEAD` пропускать.
  - `final class CommitIndex { int indexOf(String hash); String hashOf(int index); int size(); }`. Индексы растут от 0 в порядке добавления.
  - `record CommitTopology(CommitIndex index, List<GraphCommit<Integer>> commits)`. Порядок как у `git log`, родители — индексы.
  - `TopologyLoader.load(GitRepository, List<GitRef>) → CommitTopology`. Команда: `log --topo-order --format=%H %P %ct` + hash всех refs (через stdin, `--stdin`). Пустой репозиторий даёт пустую топологию.
  - `record CommitDetails(String hash, List<String> parents, String authorName, String authorEmail, Instant authorTime, String committerName, Instant commitTime, String subject, String fullMessage)`.
  - `DetailsLoader.load(GitRepository, List<String> hashes) → Map<String, CommitDetails>`. Команда: `log --no-walk=unsorted --stdin --format=%H%x00%P%x00%an%x00%ae%x00%at%x00%cn%x00%ct%x00%s%x00%B%x01`. Hashes идут через stdin.

- [ ] **Step 1: Написать тесты.**
  - `RefsLoaderTest.loadsLocalRemoteTagsAndCurrent`: репо с `main`, `feature/x`, remote `origin` (bare + push), аннотированный тег `v1` → типы, `current` только у `main`, `upstream` у `main` = `origin/main`, hash тега = hash коммита.
  - `RefsLoaderTest.detachedHeadHasNoCurrentBranch`.
  - `TopologyLoaderTest.parentsAreIndexes`: merge-коммит имеет двух родителей в правильном порядке.
  - `TopologyLoaderTest.emptyRepositoryHasNoCommits`.
  - `TopologyLoaderTest.loadsTwentyThousandCommitsUnderThreeSeconds`: репо из 20 000 коммитов через `git fast-import` (генерировать поток в тесте) → `load` < 3 с.
  - `DetailsLoaderTest.loadsSubjectAuthorAndMultilineMessage`: тема, автор, сообщение с пустой строкой и кириллицей.
  - `DetailsLoaderTest.keepsRequestedHashesOnly`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests "gitbench.log.*"`. Expected: FAIL.
- [ ] **Step 3: Реализовать** по интерфейсам выше. Вывод `-z`/`%x00` разбирать по байтам UTF-8.
- [ ] **Step 4: Запустить.** Expected: PASS.

---

### Task 4: Модель лога на графе IntelliJ

**Files:**
- Create: `log/HeadRefColors.java`, `log/LogModel.java`, `log/LogRow.java`
- Test: `src/test/java/gitbench/log/LogModelTest.java`

**Interfaces:**
- Consumes: `CommitTopology`, `GitRef`, перенесённый граф (Task 2).
- Produces:
  - `HeadRefColors implements GraphColorManager<Integer>`. Логика `GraphColorManagerImpl` IntelliJ:
    - если `headFragmentIndex == fragmentIndex`, цвет = `name.hashCode()` лучшего ref головы;
    - иначе цвет = `fragmentIndex`;
    - без ref цвет = 0.

    Лучший ref: текущая ветка, потом локальная, потом remote, потом тег, потом по имени.
  - `final class LogModel`:
    - `static LogModel build(CommitTopology, List<GitRef>, boolean intelliSort)`;
    - `LogModel filtered(Set<Integer> visibleHeads /*null = все*/, Set<Integer> matched /*null = все*/)`;
    - `int rowCount()`;
    - `LogRow row(int)`;
    - `int rowOf(String hash)` (−1, если строки нет);
    - `int recommendedWidth()`.

    `intelliSort=true` → `PermanentGraph.SortType.Bek` (по умолчанию, как IntelliJ «Standard»). `false` → `Normal`. `headCommitsComparator` упорядочивает головы так же, как лучший ref.
  - `record LogRow(String hash, int commitIndex, Collection<? extends PrintElement> printElements, List<GitRef> refs)`.

- [ ] **Step 1: Написать тесты.**
  - `rowsFollowTopoOrder`: линейная история из 3 коммитов → строки от нового к старому.
  - `intelliSortShowsIncomingCommitsBelowMerge`: `main` и `feature` расходятся, merge → при `intelliSort=true` сразу под merge идут коммиты `feature`, а при `false` порядок по дате.
  - `rowCarriesRefs`: у коммита HEAD есть `main` (current) и `origin/main`.
  - `filterByBranchHidesOtherBranches`: `filtered({head of feature}, null)` не содержит коммитов только из `main`.
  - `filterByMatchedCommitsKeepsOnlyThem`.
  - `mergeRowHasTwoOutgoingEdges`: у строки merge среди `printElements` два `EdgePrintElement` вниз.
  - `colorOfHeadFragmentIsRefNameHash`: `HeadRefColors.getColor(head, 1, 1) == "main".hashCode()`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests gitbench.log.LogModelTest`. Expected: FAIL.
- [ ] **Step 3: Реализовать.** `GraphColorGetterFactory` — через `GraphColorGetterByHead` из перенесённого кода.
- [ ] **Step 4: Запустить.** Expected: PASS.

---

### Task 5: Фильтры лога

**Files:**
- Create: `log/LogFilter.java`, `log/FilterService.java`
- Test: `src/test/java/gitbench/log/FilterServiceTest.java`

**Interfaces:**
- Produces:
  - `record LogFilter(String text, boolean regex, boolean matchCase, List<String> branches, List<String> users, LocalDate since, LocalDate until, List<String> paths)`. Пустые списки и `null` означают «без фильтра». Методы:
    - `static LogFilter none()`;
    - `boolean isEmpty()`;
    - `boolean hasCommitFilter()`: всё, кроме `branches`.
  - `FilterService.matching(GitRepository, LogFilter) → Set<String>`. Возвращает hashes или `null`, если commit-фильтров нет. Команда `log --format=%H` с `--branches --remotes --tags HEAD` или с выбранными ветками и:
    - text: `--grep=<text>` (`-i` без matchCase, `-E` при regex, иначе `-F`); если text похож на hash (`[0-9a-f]{4,40}`), к результату добавить совпадения по префиксу hash;
    - users: `--author=<u>` на каждого (git объединяет их через ИЛИ);
    - since/until: `--since=<date> 00:00`, `--until=<date> 23:59:59`;
    - paths: `-- <paths>` через `--pathspec-from-file` нельзя у `log`, поэтому пути идут аргументами, но не больше 100 штук.

- [ ] **Step 1: Написать тесты.**
  - `textMatchesSubjectIgnoringCase`.
  - `matchCaseRespected`.
  - `regexMatches`.
  - `hashPrefixMatches`: текст `abc1` находит коммит с этим префиксом. Hash брать из репо, первые 7 символов.
  - `userFilter`.
  - `dateRange`: коммиты с `GIT_COMMITTER_DATE` / `GIT_AUTHOR_DATE` в окружении.
  - `pathFilter`.
  - `combinedFiltersIntersect`.
  - `noCommitFilterReturnsNull`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests gitbench.log.FilterServiceTest`. Expected: FAIL. Затем реализовать. Expected: PASS.

---

### Task 6: Отрисовка графа на SWT

**Files:**
- Create: `ui/log/GraphColors.java`, `ui/log/GraphPainter.java`
- Test: `src/test/java/gitbench/ui/log/GraphColorsTest.java`

**Interfaces:**
- Produces:
  - `GraphColors.rgb(int colorId, boolean dark) → int` (0xRRGGBB). Формула `DefaultColorGenerator.calcColor`: `r = id*200+30`, `g = id*130+50`, `b = id*90+100`, `rangeFix`, затем HSB с saturation 0.4 и brightness 0.65. Значения цвета 0 и обработку тёмной темы взять из `putDefaultColor` в `DefaultColorGenerator.kt`. Тест чистый, без SWT, на `java.awt.Color`.
  - `GraphPainter.paint(GC gc, Rectangle cell, Collection<? extends PrintElement> elements, boolean selectedRow, boolean dark)`. Порт `SimpleGraphCellPainter.kt`, `HeadNodePainter.kt` и `PaintParameters.java`: размеры от высоты строки, кружок узла, линии с антиалиасингом (`gc.setAntialias(SWT.ON)`), стрелки длинных рёбер, пунктир, выделенные рёбра толще.
  - `GraphPainter.width(int columns, int rowHeight)`.

- [ ] **Step 1: Написать `GraphColorsTest`.**
  - `matchesIntelliJFormulaForSeveralIds`: ожидания посчитать в тесте той же формулой из `DefaultColorGenerator`, через `Color.RGBtoHSB`/`HSBtoRGB`, для id 0, 1, 7, −5 и `"main".hashCode()`.
  - `colorIsStablePerId`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests gitbench.ui.log.GraphColorsTest`. Expected: FAIL. Затем реализовать. Expected: PASS.
- [ ] **Step 3: Реализовать `GraphPainter`.** Построчный перевод вызовов `Graphics2D` в `GC`: `drawLine`, `fillOval`, `drawPolyline`, `setLineWidth`, `setLineDash`, `setLineCap(SWT.CAP_ROUND)`. Без автотестов. Проверка визуальная в Task 7.

---

### Task 7: Вид Git Log

**Files:**
- Create: `ui/log/GitLogView.java`, `CommitTable.java`, `RefLabelPainter.java`, `FilterBar.java`, `CommitDetailsPanel.java`, `ui/RepositoryEvents.java`
- Modify: `plugin.xml`, `GitbenchIcons.java`, `icons/svg/*` (новые иконки)

**Interfaces:**
- Consumes: Tasks 3–6, `RepositoryContext`, `WorkspaceSync` (этап 1).
- Produces:
  - `RepositoryEvents`: `static void fire(GitRepository)` и `static void listen(Consumer<GitRepository>)` / `unlisten`. Его вызывают все операции, меняющие репозиторий: Commit-вид, Log, Branches. Commit-вид этапа 1 подписывается и вызывает `fire` после своих операций.
  - `GitLogView` (id `gitbench.views.log`, имя «Git Log», иконка `vcs` New UI):
    - `void showRefFilter(List<String> branches)`;
    - `void selectCommit(String hash)`;
    - `void showRange(String excludeRef, String includeRef)` — для Compare with Current;
    - `LogFilter currentFilter()`.

Раскладка (эталон: `MainFrame.java`, скриншот пользователя):
1. **Строка фильтров** (`FilterBar`). Поле «Text or hash» с иконкой поиска и переключателями regex (`.*`) и Match Case (`Cc`) справа внутри поля. За ним выпадающие кнопки «Branch: …», «User», «Date», «Paths» и кнопка IntelliSort (сортировка). У выбранного фильтра есть ×.
   - Branch: список refs с поиском.
   - User: «me» и авторы из загруженных деталей.
   - Date: Last 24 hours / Last 7 days / Select....
   - Paths: выбор папок проекта.
   - Ввод в поле применяется с задержкой 300 мс.
2. **Таблица** (`CommitTable`): `SWT.VIRTUAL` + owner draw, без линий сетки, высота строки 22 px при 100 %.
   - Колонка Subject: граф (`GraphPainter`), тема коммита; метки refs выровнены вправо (`RefLabelPainter`).
   - Колонки Author (жирный для своих коммитов, как в IntelliJ) и Date (`dd.MM.yyyy`).
   - Коммиты вне текущей ветки бледнее, как в IntelliJ.
   - Детали (тема, автор, дата) грузятся пачками по 1000 строк вокруг видимой области, вне UI-потока. Пока их нет, строка показывает `Loading...` серым.
3. **Метки refs** (`RefLabelPainter`, эталон `LabelPainter.kt`):
   - refs одного коммита группируются: иконки по типу, имена через « & »;
   - текущая ветка — иконка `currentBranchLabel` (жёлтая), остальные ветки — `branchLabel`;
   - текст метки серый, обрезается с «…», если не помещается.
4. **Справа** вертикальный `SashForm`: сверху дерево файлов коммита (тот же `ChangesTreeViewer`-стиль: группировка по папкам, цвета статусов, без чекбоксов), снизу `CommitDetailsPanel`:
   - тема жирным, тело сообщения;
   - hash, автор, дата, committer, если другой;
   - «In N branches: …», загружается лениво через `branch -a --contains`.

   При выборе нескольких коммитов панель показывает список тем.
5. **Тулбар вида**: Refresh, Fetch (`fetch --all --prune` в Job), View Options (глаз):
   - IntelliSort (toggle, по умолчанию включён);
   - Show Details (toggle);
   - Show Changed Files (toggle).
6. **Обновление**: при открытии, при активации вида, по `RepositoryEvents`, по Refresh. Позиция выбора сохраняется по hash.
7. **Горячие клавиши внутри вида**: Ctrl+F — фокус в поле фильтра, Ctrl+C — копировать hash, Enter — показать детали.

Новые иконки (New UI, из `D:\Projects\intellij-community`):

| Icon | Источник |
|---|---|
| VCS (иконка вида и ветки) | `platform/icons/src/expui/general/vcs.svg` |
| FETCH | `platform/icons/src/expui/vcs/fetch.svg` |
| SEARCH | `platform/icons/src/expui/general/search.svg` |
| REGEX, MATCH_CASE | `platform/icons/src/expui/inline/regex.svg`, `matchCase.svg` |
| CURRENT_BRANCH_LABEL, BRANCH_LABEL, CURRENT_BRANCH_FAVORITE_LABEL | `platform/dvcs-impl/shared/resources/icons/new/currentBranchLabel.svg`, `branchLabel.svg`, `currentBranchFavoriteLabel.svg` |
| SORT | `platform/icons/src/expui/general/sortBy.svg` |

- [ ] **Step 1: Реализовать** `RepositoryEvents` и подписку Commit-вида (refresh по событию, `fire` после commit, rollback, add, ignore, delete).
- [ ] **Step 2: Реализовать** `CommitTable`, `RefLabelPainter`, `FilterBar`, `CommitDetailsPanel`, `GitLogView` по раскладке выше. Загрузка: Refs → Topology → `LogModel.build` в одном Job. Фильтр: `FilterService` в Job → `LogModel.filtered`.
- [ ] **Step 3: Собрать и поставить.** Run: `./gradlew build deployDropins`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 4: Ручная проверка (пользователь)**, сравнение со своим скриншотом IntelliJ:
  - граф и цвета линий, метки «master & with-terminal»;
  - фильтры: текст, hash, Branch, User, Date, Paths;
  - детали и файлы коммита;
  - IntelliSort вкл./выкл.;
  - светлая и тёмная тема;
  - большой репозиторий (например, клон STM32CubeF4).

---

### Task 8: Операции над коммитом в Git Log

**Files:**
- Create: `log/ResetMode.java`, `log/CommitOperations.java`, `ui/log/LogContextMenu.java`, `ui/log/ResetDialog.java`, `ui/log/GoToRefDialog.java`
- Test: `src/test/java/gitbench/log/CommitOperationsTest.java`

**Interfaces:**
- Produces:
  - `enum ResetMode { SOFT, MIXED, HARD, KEEP }`.
  - `CommitOperations` (все методы `throws GitException`, результат — `OperationResult`):
    - `cherryPick(GitRepository, String hash)`;
    - `revert(GitRepository, String hash)`;
    - `checkoutRevision(GitRepository, String hash)`;
    - `reset(GitRepository, String hash, ResetMode)`;
    - `createBranch(GitRepository, String name, String hash, boolean checkout)`;
    - `createTag(GitRepository, String name, String hash, String messageOrNull)`.
  - `record OperationResult(boolean ok, boolean conflicts, String message)`. Конфликт распознаётся по `CONFLICT` в выводе или по наличию `.git/CHERRY_PICK_HEAD` / `REVERT_HEAD`. Тогда `ok=false, conflicts=true` и текст «Conflicts during cherry-pick. Resolve them in the Commit view.».

Контекстное меню строки, порядок как в `Git.Log.ContextMenu` IntelliJ:
1. Copy Revision Number (Ctrl+C)
2. Create Patch... (скрыт, этап 3+)
3. Cherry-Pick
4. разделитель
5. Checkout Revision
6. Show Repository at Revision (скрыт)
7. Compare with Local (скрыт до этапа 3)
8. разделитель
9. Reset Current Branch to Here...
10. Revert Commit
11. Undo Commit (только для HEAD без push: `reset --soft HEAD~1`, сообщение возвращается в Commit-вид)
12. разделитель
13. New Branch...
14. New Tag...
15. разделитель
16. Go to Parent Commit / Go to Child Commit (←/→)

`ResetDialog`: заголовок «Git Reset», текст «This will reset the current branch head to the selected commit, and update the working tree and the index according to the selected mode:», радиокнопки Soft / Mixed / Hard / Keep с пояснениями IntelliJ, кнопки «Reset» / «Cancel».

`GoToRefDialog`: Ctrl+F в таблице. Поле «Enter hash or branch/tag name» с автодополнением по refs.

- [ ] **Step 1: Написать тесты.**
  - `cherryPickAppliesCommit`.
  - `cherryPickConflictReported`: `conflicts=true`, `status` показывает CONFLICT.
  - `revertCreatesRevertCommit`.
  - `resetSoftKeepsChangesStaged`.
  - `resetHardDropsChanges`.
  - `checkoutRevisionDetachesHead`.
  - `createBranchAtCommitAndCheckout`.
  - `createAnnotatedTag`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests gitbench.log.CommitOperationsTest`. Expected: FAIL. Реализовать. Expected: PASS.
- [ ] **Step 3: Реализовать меню и диалоги.** Каждая операция идёт в Job, затем `WorkspaceSync.refresh` и `RepositoryEvents.fire`. При `conflicts=true` показывается диалог с кнопкой «Open Commit View».

---

### Task 9: Модель дерева веток

**Files:**
- Create: `branch/BranchTreeNode.java`, `branch/BranchTreeBuilder.java`, `branch/Favorites.java`, `branch/AheadBehind.java`
- Test: `src/test/java/gitbench/branch/BranchTreeBuilderTest.java`, `FavoritesTest.java`, `AheadBehindTest.java`

**Interfaces:**
- Consumes: `GitRef`, `RefsLoader`.
- Produces:
  - `sealed interface BranchTreeNode` с вариантами:
    - `Head(String label)`: «HEAD (Current Branch)» или «HEAD (detached at abc1234)»;
    - `Group(String title, List<BranchTreeNode> children)`: «Local», «Remote»;
    - `Remote(String name, List<BranchTreeNode> children)`;
    - `Folder(String name, List<BranchTreeNode> children)`;
    - `Branch(GitRef ref, String label, boolean favorite, AheadBehind.Counts counts)`.
  - `BranchTreeBuilder.build(List<GitRef> refs, Set<String> favorites, Map<String, AheadBehind.Counts> counts, String filterText, boolean onlyMine, Set<String> myBranches) → List<BranchTreeNode>`.
    - Порядок: Head, Local, Remote.
    - Внутри: избранные сверху, потом по имени без учёта регистра.
    - Имена со `/` раскладываются по папкам.
    - `filterText` оставляет ветки, где имя содержит текст без учёта регистра, и их папки.
  - `Favorites`: хранение в preferences по ключу `favorites.<repo root>`. По умолчанию `main` и `master`, как в IntelliJ. Методы `toggle` и `contains`.
  - `AheadBehind.load(GitRepository) → Map<String, Counts>`, где `record Counts(int incoming, int outgoing)`. Команда: `for-each-ref --format=%(refname:short)%00%(upstream:track,nobracket) refs/heads`.
  - `BranchTreeBuilder.myBranches(GitRepository, List<GitRef>) → Set<String>`: ветки, где автор последнего коммита совпадает с `git config user.email`.

- [ ] **Step 1: Написать тесты.**
  - `headCurrentLocalRemoteOrder`.
  - `groupsSlashedNamesIntoFolders`: `feature/uart/фикс`.
  - `favoritesOnTop`.
  - `defaultFavoritesAreMainAndMaster`.
  - `filterKeepsMatchingBranchesAndTheirFolders`.
  - `detachedHeadIsShownByHash`.
  - `aheadBehindCounts`: 1 вперёд, 2 позади относительно `origin/main`.
  - `myBranchesByLastCommitAuthor`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests "gitbench.branch.*"`. Expected: FAIL. Реализовать. Expected: PASS.

---

### Task 10: Вид Branches и связь с Git Log

**Files:**
- Create: `ui/branch/BranchesView.java`, `BranchesTreeViewer.java`, `BranchesToolbar.java`
- Modify: `plugin.xml`, `GitbenchIcons.java`, `icons/svg/*`

**Interfaces:**
- Consumes: Task 9, `GitLogView.showRefFilter`, `RepositoryEvents`.
- Produces: вид `gitbench.views.branches`, имя «Branches», иконка `vcs`.

Раскладка (скриншот пользователя, `BranchesTreeComponent.kt`):
1. **Вертикальный тулбар слева**, сверху вниз:
   - New Branch (`add`);
   - Update Selected (`update`);
   - Delete (`delete`);
   - Compare with Current (`diff`);
   - Show My Branches (`search`, toggle);
   - Fetch (`fetch`);
   - Mark as Favorite (`star`);
   - Navigate Log to Selected Branch (`locate`);
   - разделитель;
   - Settings (`settings`): меню «On Selection: Filter Log / Navigate Log».
2. **Поле поиска** над деревом: speed search, фильтрует дерево (`filterText`).
3. **Дерево**:
   - HEAD (Current Branch);
   - Local:
     - текущая ветка — иконка `currentBranchLabel`, избранная текущая — `currentBranchFavoriteLabel`;
     - избранная — `star`, остальные — `vcs`;
   - Remote → имя remote (иконка `folder`) → ветки;
   - папки — иконка `folder`;
   - справа от имени ветки — стрелки входящих и исходящих коммитов с числами (`incomingUpdate.svg`, `outgoingPush.svg` из `dvcs-impl/.../icons/new`).
4. **Поведение:**
   - выбор ветки по настройке Settings либо фильтрует Git Log (`showRefFilter`, по умолчанию), либо переводит выбор к её голове;
   - двойной клик делает checkout (Task 11);
   - Enter делает то же.

Иконки:

| Icon | Источник |
|---|---|
| ADD, DELETE, LOCATE, SETTINGS, SEARCH | `platform/icons/src/expui/general/*.svg` |
| UPDATE | `platform/icons/src/expui/vcs/update.svg` |
| DIFF | `platform/icons/src/expui/vcs/diff.svg` |
| STAR | `platform/icons/src/expui/nodes/star.svg` |
| INCOMING, OUTGOING | `platform/dvcs-impl/shared/resources/icons/new/incomingUpdate.svg`, `outgoingPush.svg` |

- [ ] **Step 1: Реализовать вид и тулбар.** Загрузка `RefsLoader` + `AheadBehind` + `myBranches` в Job, обновление по `RepositoryEvents` и при активации вида. Раскрытые узлы сохраняются.
- [ ] **Step 2: Собрать и поставить.** Run: `./gradlew build deployDropins`. Expected: `BUILD SUCCESSFUL`.
- [ ] **Step 3: Ручная проверка (пользователь):** раскладка как на скриншоте, фильтр лога по выбору ветки, избранное, стрелки входящих и исходящих, поиск.

---

### Task 11: Операции над ветками

**Files:**
- Create: `branch/BranchOperations.java`, `ui/branch/BranchContextMenu.java`, `ui/branch/NewBranchDialog.java`
- Modify: `git/PushService.java` (push не только HEAD), `ui/push/PushDialog.java`
- Test: `src/test/java/gitbench/branch/BranchOperationsTest.java`, `src/test/java/gitbench/git/PushServiceTest.java` (новый тест)

**Interfaces:**
- Consumes: `OperationResult` (Task 8), `PushService`, `PushDialog`.
- Produces: `BranchOperations` (результат `OperationResult`):
  - `checkout(GitRepository, GitRef ref)`. Local: `switch`. Remote: если локальная с тем же коротким именем есть — `switch` на неё. Иначе `switch -c <name> --track <remote/name>`.
  - `newBranch(GitRepository, String name, String startPoint, boolean checkout)`.
  - `rename(GitRepository, String oldName, String newName)`.
  - `delete(GitRepository, GitRef ref, boolean force)`. Local: `branch -d`, при «not fully merged» результат `ok=false` с флагом `needsForce`. Remote: `push <remote> --delete <branch>`.
  - `mergeIntoCurrent(GitRepository, String ref)`.
  - `rebaseCurrentOnto(GitRepository, String ref)`.
  - `updateSelected(GitRepository, GitRef local)`. Текущая ветка: `pull --ff-only`. Нетекущая с upstream: `fetch <remote> <upstreamBranch>:<local>`.
  - `PushService.push(GitRepository, PushTarget, String sourceBranch)`: новая перегрузка, refspec `<source>:refs/heads/<target>`. Старая перегрузка вызывает её с `HEAD`.
  - `OperationResult` получает поле `boolean needsForce` (по умолчанию `false`).

Контекстное меню ветки, порядок `Git.Branch.Backend` / IntelliJ:
1. Checkout
2. New Branch from '<name>'...
3. Checkout and Rebase onto '<current>' (только для нетекущей)
4. разделитель
5. Compare with '<current>' → `GitLogView.showRange(current, ref)`
6. Show Diff with Working Tree (скрыт до этапа 3)
7. разделитель
8. Rebase '<current>' onto '<name>'
9. Merge '<name>' into '<current>'
10. разделитель
11. Update (Update Selected)
12. Push... (открывает `PushDialog` для этой ветки)
13. разделитель
14. Rename... (только локальные)
15. Delete

Пункты, которые не подходят к выбранной ветке (например, Merge в саму себя), скрыты. При удалении неслитой ветки: диалог «Branch '<name>' is not fully merged. Delete anyway?» → `delete(force=true)`.

`NewBranchDialog`: «Create New Branch from <ref>», поле имени с проверкой `git check-ref-format --branch`, чекбокс «Checkout branch» (включён), чекбокс «Overwrite existing branch» (виден, если имя занято).

- [ ] **Step 1: Написать тесты.**
  - `checkoutLocal`.
  - `checkoutRemoteCreatesTrackingBranch`.
  - `checkoutRemoteWhenLocalExistsUsesLocal`.
  - `checksOutCyrillicBranch`.
  - `newBranchFromRef`.
  - `renameBranch`.
  - `deleteMergedBranch`.
  - `deleteUnmergedNeedsForce`.
  - `deleteRemoteBranch`.
  - `mergeIntoCurrent`.
  - `mergeWithConflictReportsConflict`.
  - `rebaseCurrentOnto`.
  - `updateSelectedFastForwardsNonCurrent`.
  - `PushServiceTest.pushesNonCurrentBranch`.
- [ ] **Step 2: Запустить.** Run: `./gradlew test --tests "gitbench.branch.BranchOperationsTest" --tests "gitbench.git.PushServiceTest"`. Expected: FAIL. Реализовать. Expected: PASS.
- [ ] **Step 3: Реализовать меню, диалоги и действия тулбара** (New Branch, Update Selected, Delete, Compare with Current). Каждая операция идёт в Job, затем `WorkspaceSync.refresh` и `RepositoryEvents.fire`. Конфликт ведёт к диалогу с «Open Commit View».
- [ ] **Step 4: Ручная проверка (пользователь):** checkout local и remote, new, rename, delete (и неслитой), merge, rebase, update, push ветки, Compare with Current.

---

## Что не входит в этап 2

- Diff файлов (двойной клик по файлу коммита, Compare with Local, Show Diff with Working Tree): этап 3. До него двойной клик открывает файл рабочей копии.
- Окно разрешения конфликтов: этап 4. На этапе 2 конфликт показывается сообщением, а файлы видны в Commit-виде как CONFLICT.
- Interactive rebase, Create Patch, Show Repository at Revision, Tags в дереве Branches: позже или не планируются.
- p2 update site: отдельная задача после этапа 2.
