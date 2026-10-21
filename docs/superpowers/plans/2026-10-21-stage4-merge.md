# Этап 4: resolve в 3 колонки и окно конфликтов — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Окно **Conflicts** (список файлов с конфликтами, Accept Yours / Accept Theirs / Merge...) и окно **Merge** (Yours | Result | Theirs), как в IntelliJ.

**Architecture:** Сравнение трёх текстов — уже перенесённый модуль IntelliJ (`ComparisonManagerImpl.mergeLines`, `MergeRangeUtil.getLineMergeType`, `ComparisonMergeUtil.tryResolveConflict`). Модель merge (`gitbench.merge.MergeModel`) повторяет поведение `MergeConflictModel` и `MergeModelBase` IntelliJ на чистой Java:
- блоки и их строки в Result;
- состояние «resolved» по сторонам;
- `>>`, `<<`, `x`;
- автоприменение неконфликтных блоков и «магия»;
- сдвиг блоков при ручной правке Result.

Модель работает с Result через интерфейс `ResultText`, поэтому тестируется без Eclipse. Список конфликтов и стадии файлов берутся из git CLI. UI — два JFace-диалога, как модальные окна IntelliJ.

**Tech Stack:** Java 17, Gradle, JUnit 5, AssertJ, Eclipse 4.33 (SWT, JFace text). Новых зависимостей нет.

**Spec:** `D:\Projects\gitbench\docs\superpowers\specs\2026-10-21-gitbench-design.md`, раздел «4. Resolve в 3 колонки».

**Commits:** пользователь разрешил коммитить в этом проекте. Один коммит на задачу, без `Co-Authored-By`. Ветка `stage4-merge` от `main`.

## Global Constraints

- Всё из Global Constraints этапов 1–3 действует: Eclipse 4.33+, Java 17, git CLI, долгие операции вне UI-потока, вывод git в консоль Gitbench, горячие клавиши только через команды в своём контексте (как diff на этапе 3).
- Подписи из `DiffBundle.properties`, `VcsBundle.properties`, `GitBundle.properties` IntelliJ:

  | Где | Текст |
  |---|---|
  | Заголовок окна конфликтов | «Conflicts» |
  | Описание | «The following file has conflicts:» / «The following files have conflicts:» |
  | Описание merge | «Merging branch {1} into branch {3}» |
  | Описание cherry-pick | «Conflicts during cherry-picking commit {1}» |
  | Описание rebase | «Rebasing branch {1} onto {2}» |
  | Кнопки окна конфликтов | «Accept Yours», «Accept Theirs», «Merge...», «Close»; чекбокс «Group by directory» |
  | Колонки | «Name», «Yours», «Theirs»; статусы «Modified», «Deleted» |
  | Заголовок merge-окна | «Merge Revisions for {path}» |
  | Колонки merge | слева «Your version, branch {name}» (cherry-pick: «Local Changes»), в центре «Result», справа «Changes from {short hash or branch}» (cherry-pick: «Changes from cherry-pick {hash}») |
  | Статус | «{n} changes, {m} conflicts» по `merge.differences.status.text`: «No changes», «1 change», «No conflicts», «1 conflict» |
  | Кнопки merge | слева «Accept Left», «Accept Right»; справа «Apply», «Cancel» |
  | Тулбар merge | «Apply non-conflicting changes:» Left / All / Right, «Resolve simple conflicts» (магия) |
  | Всё решено | «All changes have been processed» + ссылка «Apply Changes» |
  | Выход без сохранения | «Are you sure you want to exit without applying changes?» |
  | Apply при остатке | «There are {n} changes and {m} conflicts left unprocessed. Save changes and mark the conflict resolved anyway?» (текст `merge.dialog.apply.partially.resolved.changes.confirmation.message`) |

- Цвета: блоки как на этапе 3, плюс CONFLICT — Light `FFD5CC`, Dark `45302B`. Решённый блок не подсвечивается.
- Rebase: «Yours» — твой коммит, который сейчас применяется (stage 3). «Theirs» — ветка, на которую идёт rebase (stage 2). Так делает IntelliJ (reversed roots). Для merge и cherry-pick Yours = stage 2, Theirs = stage 3.

## Review Focus

1. **CRLF.** Файл с CRLF в рабочем дереве: Result сохраняется с CRLF, без смеси разделителей. Тест в Task 5: `savesResultWithOriginalLineSeparators`.
2. **Файл удалён с одной стороны.** «deleted by us/them»: Merge... выключена, Accept Yours / Accept Theirs удаляют или оставляют файл и делают `git add`/`git rm`. Тест в Task 1: `acceptDeletedSideRemovesFile`.
3. **Оба добавили файл (нет stage 1).** Base пустой, merge открывается. Тест в Task 2: `bothAddedHasEmptyBase`.
4. **Ручная правка Result, потом `>>`.** Блоки после правки стоят на своих строках, `>>` заменяет нужные строки. Тест в Task 3: `manualEditAboveShiftsChanges`, `replaceAfterManualEditTargetsShiftedLines`.
5. **Бинарный файл в конфликте.** Merge... выключена, Accept работает. Тест в Task 2: `binaryStageIsDetected`.

---

## Файлы

```
src/main/java/gitbench/merge/
  ConflictType.java ConflictFile.java ConflictService.java MergeOperation.java MergeContext.java
  MergeContents.java MergeContentLoader.java
  ResultText.java MergeChange.java MergeModel.java MergeResultWriter.java
src/main/java/gitbench/ui/merge/
  ConflictsDialog.java MergeDialog.java MergeColumns.java MergeDividerPainter.java MergeToolbar.java MergeOpener.java
```

---

### Task 1: Список конфликтов и Accept Yours / Theirs

**Files:**
- Create: `gitbench/merge/{ConflictType,ConflictFile,ConflictService,MergeOperation,MergeContext}.java`
- Test: `src/test/java/gitbench/merge/ConflictServiceTest.java`
- Modify: `src/test/java/gitbench/testutil/TempRepo.java` (помощник `conflict(...)`, если нужен)

**Interfaces:**
- Produces:
  - `enum ConflictType { BOTH_MODIFIED, BOTH_ADDED, DELETED_BY_US, DELETED_BY_THEM, ADDED_BY_US, ADDED_BY_THEM, BOTH_DELETED }` по XY из `git status --porcelain=v2 -z` (`UU AA DU UD AU UA DD`).
  - `record ConflictFile(String path, ConflictType type)` с методами `yoursDeleted()`, `theirsDeleted()`, `canMerge()`. `canMerge()` ложь, если одна сторона удалена. Бинарность проверяет Task 2.
  - `enum MergeOperation { MERGE, REBASE, CHERRY_PICK, REVERT, NONE }`.
  - `record MergeContext(MergeOperation operation, String currentBranch, String theirsName, String theirsHash, boolean reversed)`. `reversed` истина для REBASE. Методы: `description()`, `yoursTitle()`, `theirsTitle()`, `yoursColumn()` («Yours» / «Yours ({branch})»), `theirsColumn()`.
  - `ConflictService(GitRunner)`:
    - `list(GitRepository) -> List<ConflictFile>`;
    - `context(GitRepository) -> MergeContext`: по `MERGE_HEAD`, `CHERRY_PICK_HEAD`, `REVERT_HEAD`, `rebase-merge/`, `rebase-apply/` в `git rev-parse --git-dir`; имя — `git name-rev --name-only` или короткий hash;
    - `acceptYours(repo, files, context)`, `acceptTheirs(repo, files, context)`. Сторона берётся с учётом `reversed`. Удалённая сторона → `git rm`, иначе `git checkout --ours|--theirs -- path` и `git add`;
    - `markResolved(repo, path)` → `git add`.
- [ ] **Step 1: Тесты на TempRepo с настоящими конфликтами.**
  - `listsBothModifiedConflict`;
  - `listsDeletedByThem`;
  - `mergeContextNamesBranches`: описание «Merging branch feature into branch master»;
  - `cherryPickContext`;
  - `rebaseContextIsReversed`;
  - `acceptYoursKeepsOurContentAndStages`;
  - `acceptTheirsInRebaseTakesUpstream`: при rebase Theirs = ветка onto;
  - `acceptDeletedSideRemovesFile`;
  - `noConflictsGivesEmptyList`.
- [ ] **Step 2:** Run: `./gradlew.bat test --tests "gitbench.merge.*"`. Expected: FAIL компиляции.
- [ ] **Step 3:** Реализовать. Пути на stdin через `--pathspec-from-file=- --pathspec-file-nul`, как в stage 1.
- [ ] **Step 4:** Expected: PASS. Коммит «List conflicts and accept a side».

---

### Task 2: Содержимое трёх сторон

**Files:**
- Create: `gitbench/merge/{MergeContents,MergeContentLoader}.java`
- Modify: `gitbench/diff/DiffContentLoader.java` (вынести `decode(byte[], Charset, String title)` в публичный метод)
- Test: `src/test/java/gitbench/merge/MergeContentLoaderTest.java`

**Interfaces:**
- Consumes: `DiffContentLoader.decode`, `ConflictFile`, `MergeContext`.
- Produces: `record MergeContents(LoadedContent yours, LoadedContent base, LoadedContent theirs, String lineSeparator)`, метод `binary()`. `MergeContentLoader.load(GitRunner, GitRepository, ConflictFile, MergeContext, Charset) -> MergeContents`. Стадии через `git cat-file blob :N:path`. Нет стадии → пустой текст. `reversed` меняет stage 2 и 3 местами. `lineSeparator` берётся из рабочего файла (в нём маркеры конфликта), иначе из yours.
- [ ] **Step 1: Тесты.**
  - `loadsThreeStages`;
  - `bothAddedHasEmptyBase`;
  - `rebaseSwapsSides`;
  - `binaryStageIsDetected`;
  - `crlfWorkingFileGivesCrlfSeparator`.
- [ ] **Step 2–4:** FAIL → реализовать → PASS. Коммит «Load the three sides of a conflict».

---

### Task 3: Модель merge

**Files:**
- Create: `gitbench/merge/{ResultText,MergeChange,MergeModel}.java`
- Test: `src/test/java/gitbench/merge/MergeModelTest.java` (с `ListResultText` в тесте — список строк)

**Interfaces:**
- Consumes: `ComparisonManagerImpl.mergeLines`, `MergeRangeUtil.getLineMergeType`, `ComparisonMergeUtil.tryResolveConflict`, `DiffText`, `IgnorePolicy`, `BlockKind`.
- Produces:
  - `interface ResultText`:
    - `int lineCount()`;
    - `String lines(int start, int end)` — строки через `\n`;
    - `void replaceLines(int start, int end, String text)` — семантика `DiffUtil.applyModification`, как `BlockApplier` этапа 3.

    Реализация обязана вызвать `MergeModel.beforeResultChange(start, end, shift)` до правки, как `MergeModelBase.DocumentListener`.
  - `final class MergeChange`:
    - `index()`;
    - `startLine(ThreeSide)`, `endLine(ThreeSide)` — для BASE это строки в Result;
    - `isConflict()`, `kind()` (BlockKind, для конфликта отдельный флаг);
    - `isChange(Side)`;
    - `isResolved()`, `isResolved(Side)`, `isOnesideAppliedConflict()`;
    - `canResolveAutomatically()`.
  - `MergeModel`:
    - `static MergeModel create(DiffText yours, DiffText base, DiffText theirs, IgnorePolicy, ResultText result)`. Result уже содержит base;
    - `changes()`, `changesCount()`, `conflictsCount()` — нерешённые, как `getChangesCount`/`getConflictsCount`;
    - `replaceChange(MergeChange, Side)` (`>>`/`<<`), `ignoreChange(MergeChange, Side)` (`x`);
    - `applyNonConflicting(ThreeSide side)` — LEFT/RIGHT/BASE, где BASE = «All»;
    - `resolveSimpleConflicts()` — магия;
    - `acceptAll(Side)` — для Accept Left/Right;
    - `beforeResultChange(int line1, int line2, int shift)`;
    - `isAllResolved()`;
    - `next(int line)`, `previous(int line)` — по нерешённым блокам в Result.

  Логика из `MergeConflictModel.kt` (`replaceChange`, `ignoreChange`, `resolveChangeAutomatically`, `canResolveChangeAutomatically`) и `MergeModelBase.kt` (`processDocumentChange` через порт `DiffUtil.updateRangeOnModification`, `replaceChange`, `appendChange`, `moveChangesAfterInsertion`).
- [ ] **Step 1: Тесты.**
  - `nonConflictingLeftChangeIsChange`;
  - `bothSidesChangeSameLineIsConflict`;
  - `replaceLeftPutsYoursIntoResult`;
  - `secondSideOfConflictIsAppended`: после `>>` `<<` добавляет строки, как IntelliJ isOnesideAppliedConflict;
  - `ignoreMarksSideResolvedWithoutEdit`;
  - `applyNonConflictingAllLeavesOnlyConflicts`;
  - `simpleConflictIsResolvedByMagic`: правки в разных словах одной строки;
  - `manualEditAboveShiftsChanges`;
  - `replaceAfterManualEditTargetsShiftedLines`;
  - `manualEditInsideChangeKeepsItUnresolved`;
  - `countsMatchIntellijStatus`: «2 changes, 1 conflict».
- [ ] **Step 2–4:** FAIL → реализовать → PASS. Коммит «Add the merge model».

---

### Task 4: Иконки и цвета merge

**Files:** `icons/svg/*`, `GitbenchIcons.java`, `ui/diff/DiffColors.java` (CONFLICT)

- [ ] **Step 1:** Скопировать New UI SVG из `expui/diff`:
  - `applyNotConflictsLeft`, `applyNotConflicts`, `applyNotConflictsRight`;
  - `magicResolveToolbar`, `magicResolve@14x14`;
  - `remove@14x14` (x), `arrow@14x14` уже есть.

  Добавить в enum: `APPLY_NON_CONFLICTS_LEFT`, `APPLY_NON_CONFLICTS`, `APPLY_NON_CONFLICTS_RIGHT`, `MAGIC_RESOLVE_TOOLBAR`, `MAGIC_RESOLVE`, `DIFF_REMOVE`.
- [ ] **Step 2:** `DiffColors.conflict(boolean dark)`: `FFD5CC` / `45302B`.
- [ ] **Step 3:** Run: `./gradlew.bat test`. Expected: PASS (`GitbenchIconsTest`). Коммит «Add merge icons and the conflict color».

---

### Task 5: Окно Merge

**Files:**
- Create: `ui/merge/{MergeDialog,MergeColumns,MergeDividerPainter,MergeToolbar}.java`
- Test: `src/test/java/gitbench/merge/MergeSaveTest.java` (сохранение — чистый метод `MergeResultWriter.write(Path, String text, String separator, Charset)` в `gitbench.merge`)

**Interfaces:**
- Consumes: Tasks 1–4, `TextViewers`, `DividerPainter` (кривые этапа 3), `CdtHighlighting`.
- Produces: `MergeDialog.open(Shell, GitRepository, ConflictFile, MergeContext) -> boolean` (истина, если файл решён).

- [ ] **Step 1: Тест сохранения.** `savesResultWithOriginalLineSeparators`: `a\nb\n` + `\r\n` → на диске `a\r\nb\r\n`. `savesWithCharset`. FAIL → `MergeResultWriter` → PASS.
- [ ] **Step 2: Диалог.**
  - Модальный, с изменяемым размером. Заголовок «Merge Revisions for {path}».
  - Тулбар: Previous/Next Difference; «Apply non-conflicting changes» Left, All, Right; магия. Справа статус «N changes, M conflicts».
  - Три `SourceViewer`: Yours и Theirs только для чтения, Result редактируемый (`Document` с текстом base). Между ними две полосы с кривыми.
  - На левой полосе `>>` (Yours → Result) и `x` (ignore Yours). На правой `<<` и `x`. Для решённой стороны кнопки нет.
  - Для конфликта: после применения одной стороны кнопка другой — append (`DIFF_ARROW_*_DOWN`). Магия на блоке — `MAGIC_RESOLVE` на полосе, если `canResolveAutomatically`.
  - `ResultText` над `IDocument`: `IDocumentListener.documentAboutToBeChanged` вызывает `MergeModel.beforeResultChange`.
  - Внизу: «Accept Left», «Accept Right» слева; «Apply», «Cancel» справа.
  - Apply: если остались блоки, спросить (текст из Global Constraints). Записать Result через `MergeResultWriter` (разделитель — из `MergeContents`), `git add`, `WorkspaceSync.refresh`, `RepositoryEvents.fire`.
  - Cancel при изменённом Result: спросить «Are you sure you want to exit without applying changes?».
  - Когда всё решено: полоса «All changes have been processed» со ссылкой «Apply Changes».
  - При открытии автоматически применить неконфликтные блоки, как IntelliJ с включённым «Automatically apply non-conflicting changes».
  - Клавиши: F7/Shift+F7 — следующий/предыдущий блок. Делается через команды и контекст `gitbench.contexts.merge`, как diff.
- [ ] **Step 3:** Run: `./gradlew.bat test deployDropins`. Expected: PASS. Коммит «Add the merge window».

---

### Task 6: Окно Conflicts

**Files:**
- Create: `ui/merge/{ConflictsDialog,MergeOpener}.java`

**Interfaces:**
- Produces: `MergeOpener.showConflicts(Shell, GitRepository)` — открывает окно, если конфликты есть. `MergeOpener.merge(Shell, GitRepository, String path)` — сразу merge одного файла.

- [ ] **Step 1:**
  - Модальный диалог «Conflicts». Сверху описание из `MergeContext.description()` и «The following files have conflicts:».
  - Таблица (или дерево при «Group by directory»): Name | Yours | Theirs, статус «Modified»/«Deleted». Множественный выбор.
  - Справа кнопки «Accept Yours», «Accept Theirs», «Merge...», «Close». Merge... выключена при нескольких выбранных, удалённой стороне или бинарном файле. Двойной клик = Merge...
  - После каждого действия список перечитывается. Пустой список закрывает окно.
  - Git-операции в Job с правилом репозитория (`OperationRunner`), затем refresh и `RepositoryEvents`.
- [ ] **Step 2:** Run: `./gradlew.bat test deployDropins`. Коммит «Add the conflicts window».

---

### Task 7: Точки входа

**Files:**
- Modify: `gitbench/model/ChangesTreeBuilder.java` (+ тест), `ui/commit/CommitView.java`, `ui/commit/CommitContextMenu.java`, `ui/OperationRunner.java`
- Test: `ChangesTreeBuilderTest.conflictsGetOwnRoot`

- [ ] **Step 1: Тест.** `conflictsGetOwnRoot`: файлы `CONFLICT` уходят в корень «Merge Conflicts» перед «Changes», как в IntelliJ. FAIL → реализовать → PASS.
- [ ] **Step 2: Commit-вид.**
  - Двойной клик по файлу в «Merge Conflicts» → `MergeOpener.merge`.
  - Контекстное меню: «Resolve Conflicts...» → `showConflicts`.
  - Узел «Merge Conflicts» показывает справа ссылку «Resolve», как IntelliJ. Если ссылку сделать нельзя, это пункт меню; записать ruling.
- [ ] **Step 3: OperationRunner.** Диалог конфликта после merge, rebase, cherry-pick, revert или checkout с конфликтом: вместо «Open Commit View» сразу открывается окно Conflicts, как IntelliJ.
- [ ] **Step 4:** Run: `./gradlew.bat test deployDropins`. Коммит «Open the conflicts window from the Commit view and after operations».

---

### Task 8: Отчёт

- [ ] `docs/superpowers/reports/2026-10-21-stage4-results.md`: сделано, отступления, что проверить руками, отложенное. Отступления записать и в спеку, раздел 4. Коммит «Record the stage 4 results».

## Ручная проверка (пользователь)

1. Merge ветки с конфликтом: сразу открывается окно Conflicts.
2. Merge...: Yours | Result | Theirs, неконфликтные блоки уже применены, конфликт красный.
3. `>>`, `<<`, `x`, магия, Accept Left/Right, Apply. Файл решён, `git status` чистый по нему.
4. Ручная правка Result, потом `>>` на блоке ниже.
5. Rebase с конфликтом: Yours — твой коммит.
6. Удалённый с одной стороны файл: Accept Yours/Theirs.
