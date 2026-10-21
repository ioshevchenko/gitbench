# Этап 5: маркеры изменений на полях и annotate — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Маркеры изменений относительно HEAD на поле текстовых редакторов, с popup как в IntelliJ, и annotate (git blame) в отдельной колонке поля.

**Architecture:** Модели без SWT в `gitbench.status` и `gitbench.annotate`, тесты на JUnit и `TempRepo`. Диапазоны изменений — уже перенесённое сравнение IntelliJ (`DiffModel.compute`, `BY_LINE`); тип диапазона — `DiffChange` этапа 3, откат блока — `BlockApplier` и `DocumentEdits`. Две колонки поля через extension point `org.eclipse.ui.workbench.texteditor.rulerColumns`: маркеры (включены по умолчанию) и annotate (включается командой). Рисование вынесено в painter-классы с интерфейсом геометрии строк, чтобы проверять пиксели в `uiTest` без редактора Eclipse.

**Tech Stack:** Java 17, Gradle, JUnit 5, AssertJ, Eclipse 4.33 (SWT, JFace text, `org.eclipse.ui.texteditor.rulers`). Новых зависимостей нет.

**Spec:** `D:\Projects\gitbench\docs\superpowers\specs\2026-10-21-gitbench-design.md`, раздел «5. Маркеры на полях и annotate».

**Commits:** пользователь разрешил коммитить в этом проекте. Один коммит на задачу, без `Co-Authored-By`. Ветка `stage5-annotate` от `main`.

## Global Constraints

- Всё из Global Constraints этапов 1–4: Eclipse 4.33+, Java 17, git CLI, долгие операции вне UI-потока, вывод git в консоль Gitbench, светлая и тёмная темы.
- Горячих клавиш по умолчанию нет. Команды Next Change, Previous Change и Rollback Lines есть в Preferences → Keys без привязки (спека: «Глобальных сочетаний нет»).
- Эталон IntelliJ:

  | Что | Значение |
  |---|---|
  | Цвет «добавлено» (`ADDED_LINES_COLOR`) | Light `7FC784`, Dark `549159` |
  | Цвет «изменено» (`MODIFIED_LINES_COLOR`) | Light `88ADF7`, Dark `375FAD` |
  | Цвет «удалено» (`DELETED_LINES_COLOR`) | Light `767A8A`, Dark `868A91` |
  | Маркер строк | ширина 4 px, отступ слева 3 px, сверху и снизу по 1 px, концы скруглены радиусом ширины (`RectanglePainter2D`, New UI) |
  | Маркер удаления | тот же прямоугольник высотой `2*5-2 = 8` px с центром на границе строк (`getTriangleHeight` = 5) |
  | Наведение | маркер шире на 3 px влево; блок «под мышью» с запасом `lineHeight/3` для удалений (`isBlockUnderY`) |
  | Кнопки popup по порядку | «Previous Change», «Next Change», «Rollback Lines», «Show Diff for Lines», «Copy» |
  | Иконки popup | `expui/general/up`, `expui/general/down`, `expui/vcs/revert` (= `rollback` у нас), `expui/vcs/diff`, `expui/general/copy` |
  | Пункт меню annotate | «Annotate with Git Blame»; закрытие «Close Annotations» |
  | Колонки annotate по умолчанию | дата, автор (ревизия скрыта) |
  | Дата | «Today», «Yesterday», иначе `dd.MM.yyyy` (`DateFormatUtil.formatPrettyDate`, формат даты как в `CommitTable`) |
  | Текст annotate (`ANNOTATIONS_COLOR`) | Light `5A5D6B`, Dark `8D9199`; строки последнего коммита (`ANNOTATIONS_LAST_COMMIT_COLOR`) Light `000000`, Dark `CED0D6` |
  | Фон по возрасту (`VCS_ANNOTATIONS_COLOR_1..5`) | Light `A1BEF7 C9DAFB DEE8FC EDF2FA F7F8FA`; Dark `2F5194 2D416B 283754 242D42 22252E`; между якорями по 4 линейных цвета (21 цвет); коммиты по дате от новых к старым, цвет `palette[size*index/count]`; меньше двух коммитов — без фона |

## Review Focus

1. **CRLF в рабочем файле, LF в HEAD (autocrlf).** Неизменённые строки без маркеров. Тест в Task 1: `crlfDocumentAgainstLfHeadHasNoRanges`.
2. **Файл не под git.** Неотслеживаемый файл или файл вне репозитория: нет маркеров, нет ошибки. Тесты в Task 1: `untrackedFileHasNoBase`, `fileOutsideRepositoryHasNoBase`.
3. **Последняя строка без перевода строки.** Rollback изменённой последней строки возвращает текст HEAD точно. Тест в Task 1: `rollbackRestoresLastLineWithoutNewline`.
4. **Быстрый ввод.** Результат сравнения для старого текста не показывается поверх нового. Тест в Task 3: `rangesFollowTheLatestText`.
5. **Не-UTF-8 файл (windows-1251).** База и blame читают и пишут текст в кодировке файла. Тесты: Task 1 `baseUsesFileCharset`, Task 5 `blameEncodesContentsInFileCharset`.

---

## Файлы

```
src/main/java/gitbench/status/      LineStatus.java LineStatusBase.java
src/main/java/gitbench/annotate/    BlameCommit.java Blame.java BlameParser.java BlameService.java AnnotationColors.java PrettyDate.java
src/main/java/gitbench/ui/status/   LineGeometry.java ChangeMarkerColors.java ChangeMarkerPainter.java LineStatusTracker.java
                                    ChangeMarkerColumn.java ChangeMarkerPopup.java ChangeMarkerHandlers.java
src/main/java/gitbench/ui/annotate/ AnnotatePainter.java AnnotateColumn.java AnnotateHandler.java
src/main/java/gitbench/ui/log/      LogLocator.java (+ правки LogPanel)
```

---

### Task 1: Модель изменений строк

**Files:**
- Create: `gitbench/status/{LineStatus,LineStatusBase}.java`
- Test: `src/test/java/gitbench/status/{LineStatusTest,LineStatusBaseTest}.java`

**Interfaces:**
- Consumes: `DiffModel.compute`, `DiffText`, `DiffChange` (left = HEAD, right = документ), `BlockApplier.replace`, `Edit`, `DiffContentLoader.load`, `GitRunner`, `RepositoryLocator`.
- Produces:
  - `LineStatus.compute(String base, String current) -> List<DiffChange>`: `IgnorePolicy.DEFAULT`, `HighlightPolicy.BY_LINE`. При `DiffTooBigException` — один `MODIFIED` на весь текст.
  - `LineStatus.next(List<DiffChange>, int line) -> int` и `previous(...)`: индекс первого диапазона с `startLine2 > line` / последнего с `startLine2 < line`, иначе `-1`.
  - `LineStatus.rollback(String base, String current, DiffChange) -> Edit`: `BlockApplier.replace(DiffText.of(base), DiffText.of(current), change, Side.LEFT)`.
  - `record LineStatusBase.Key(GitRepository repository, String path)`; `LineStatusBase.find(RepositoryLocator, Path file) -> Optional<Key>`: пусто вне репозитория.
  - `LineStatusBase.load(GitRunner, Key, Charset) -> Optional<String>`: текст `HEAD:path` в кодировке файла. Файл не отслеживается (`git ls-files --error-unmatch` не проходит) → пусто. Отслеживается, но нет в HEAD (добавлен) → `""`. Нет коммитов (unborn HEAD) → `""` для отслеживаемого файла.
- [ ] **Step 1: Тесты.**
  - `LineStatusTest`: `identicalTextsHaveNoRanges`; `addedLinesAreInserted` (`a\nb\n` → `a\nx\nb\n`: один INSERTED, строки 1–2 справа); `changedLineIsModified`; `removedLinesAreDeleted` (`startLine2 == endLine2`); `crlfDocumentAgainstLfHeadHasNoRanges`; `nextAndPreviousSkipTheCaretLine`; `rollbackRestoresModifiedLines`; `rollbackRestoresDeletedLines`; `rollbackRestoresLastLineWithoutNewline` (`a\nb` → `a\nc`, rollback → `a\nb`).
  - `LineStatusBaseTest` на `TempRepo`: `loadsHeadContent`; `untrackedFileHasNoBase`; `addedFileHasEmptyBase`; `fileOutsideRepositoryHasNoBase`; `baseUsesFileCharset` (HEAD содержит `Привет` в windows-1251).
- [ ] **Step 2:** Run: `./gradlew.bat test --tests "gitbench.status.*"`. Expected: FAIL компиляции.
- [ ] **Step 3:** Реализовать.
- [ ] **Step 4:** Expected: PASS. Коммит «Compute line changes against HEAD».

---

### Task 2: Рисование маркеров

**Files:**
- Create: `ui/status/{LineGeometry,ChangeMarkerColors,ChangeMarkerPainter}.java`
- Test: `src/uiTest/java/gitbench/ui/status/ChangeMarkerPainterTest.java`

**Interfaces:**
- Produces:
  - `interface LineGeometry { int lineHeight(); int top(int modelLine); boolean visible(int modelLine); }`. `top` для строки за концом — низ последней строки. Колонка (Task 3) реализует его через `JFaceTextUtil.modelLineToWidgetLine` и `StyledText.getLinePixel`; свёрнутые строки невидимы.
  - `ChangeMarkerColors.color(BlockKind, boolean dark) -> Color` (таблица Global Constraints).
  - `ChangeMarkerPainter.paint(GC, List<DiffChange>, LineGeometry, int hovered, boolean dark)`; `hovered` — индекс диапазона под мышью или `-1`.
  - `ChangeMarkerPainter.rangeAt(List<DiffChange>, LineGeometry, int y) -> int` — индекс или `-1`, по правилу `isBlockUnderY`.
  - `ChangeMarkerPainter.WIDTH = 8` — ширина колонки.
- [ ] **Step 1: Тесты** (геометрия: строка = 16 px, `top(n) = 16*n`; рисование в `Image` 8×200 и чтение пикселей).
  - `insertedLinesAreGreenBetweenTheirTops`: пиксель (4, 16*1+8) = `7FC784`, пиксель (4, 16*1) = фон (отступ 1 px);
  - `markerKeepsLeftGap`: пиксель (1, середина) = фон;
  - `deletedRangeIsAShortBarOnTheBoundary`: 8 px с центром на `top(startLine2)`;
  - `hoveredMarkerIsWiderToTheLeft`: пиксель (1, середина) цветной при `hovered = 0`;
  - `rangeAtFindsTheBlockUnderTheMouse`; `rangeAtCatchesADeletionWithinAThirdOfALine`; `rangeAtMissesOutside`.
- [ ] **Step 2–4:** FAIL → реализовать → PASS (`./gradlew.bat uiTest --tests "gitbench.ui.status.*"`). Коммит «Paint change markers as IntelliJ».

---

### Task 3: Колонка маркеров в редакторах

**Files:**
- Create: `ui/status/{LineStatusTracker,ChangeMarkerColumn}.java`
- Modify: `plugin.xml`
- Test: `src/uiTest/java/gitbench/ui/status/LineStatusTrackerTest.java`

**Interfaces:**
- Consumes: Task 1, Task 2, `RepositoryEvents`, `GitRunner` из `Activator`.
- Produces:
  - `LineStatusTracker(IDocument document, Supplier<Optional<String>> baseLoader, Display display)`: грузит базу в Job, сравнивает в Job через 200 мс после последней правки. Результат применяется в UI-потоке, только если номер поколения совпадает (поколение растёт при каждой правке и перезагрузке базы). Методы: `ranges() -> List<DiffChange>`, `base() -> Optional<String>`, `reloadBase()`, `addListener(Runnable)`, `dispose()`. Нет базы → пустой список.
  - `ChangeMarkerColumn extends AbstractContributedRulerColumn`: путь файла из `IURIEditorInput` (схема `file`), кодировка — из `IEncodingSupport` редактора, иначе UTF-8. Перезагружает базу по `RepositoryEvents` своего репозитория и при активации редактора. Рисует через `ChangeMarkerPainter` с двойной буферизацией, перерисовка при прокрутке (`IViewportListener`) и правках. Наведение мыши обновляет `hovered`. Клик по маркеру в этой задаче ничего не делает; popup подключает Task 4. Статический реестр `ChangeMarkerColumn.of(ITextEditor) -> Optional<ChangeMarkerColumn>` для команд.
  - `plugin.xml`: колонка `gitbench.columns.changes`, имя «Gitbench Changes», `enabled="true"`, `includeInMenu="true"`, `global="true"`, `<placement gravity="0.9"><after id="org.eclipse.ui.editors.columns.linenumbers"/></placement>`, `targetClass = org.eclipse.ui.texteditor.AbstractDecoratedTextEditor`.
- [ ] **Step 1: Тесты** (`Document` и база из лямбды, без редактора).
  - `computesRangesAfterLoadingTheBase`;
  - `rangesFollowTheLatestText`: две правки подряд быстрее 200 мс → `ranges()` описывает второй текст;
  - `reloadBaseRecomputes`: после смены базы на текущий текст — пусто;
  - `noBaseGivesNoRanges`.

  Тест ждёт результат циклом `display.readAndDispatch` с таймаутом 5 с.
- [ ] **Step 2–4:** FAIL → реализовать → PASS. Коммит «Show change markers in text editors».

---

### Task 4: Popup маркера и команды

**Files:**
- Create: `ui/status/{ChangeMarkerPopup,ChangeMarkerHandlers}.java`; `icons/svg/{up,down,copy}{,_dark}.svg` из `platform/icons/src/expui/general/`
- Modify: `GitbenchIcons.java` (`UP`, `DOWN`, `COPY`), `ui/status/ChangeMarkerColumn.java` (клик открывает popup), `ui/diff/{DiffOpener,DiffEditorInput,DiffEditor}.java`, `plugin.xml`
- Test: `src/uiTest/java/gitbench/ui/status/ChangeMarkerPopupTest.java`

**Interfaces:**
- Consumes: Task 1, Task 3, `DiffRequests.forLocalChange`, `DiffColors`, `DiffModel.compute` с `BY_WORD` для слов блока.
- Produces:
  - `ChangeMarkerPopup(Shell parent, IDocument document, LineStatusTracker tracker, int rangeIndex, Point location, Actions actions)`; `interface Actions { void showDiff(int documentLine); void reveal(int documentLine); }`. Окно без рамки под маркером. Сверху тулбар с пятью кнопками (порядок и иконки из Global Constraints). Ниже `StyledText` только для чтения с текстом HEAD блока: фон строк — `DiffColors.color(kind)`, изменённые слова — полный цвет, как в diff этапа 3. Для `INSERTED` текста HEAD нет, показывается только тулбар.
  - Previous/Next Change: popup переходит к соседнему диапазону и вызывает `reveal`; кнопка выключена на краю. Rollback Lines: `DocumentEdits.apply(document, LineStatus.rollback(...))` одной правкой (Undo в редакторе её отменяет), popup закрывается. Show Diff for Lines: `showDiff(startLine2)`. Copy: текст HEAD блока в буфер обмена; выключена для `INSERTED`.
  - Esc и потеря фокуса закрывают popup.
  - `DiffOpener.open(IWorkbenchPage, List<DiffRequest>, int index, int revealLine)`. `DiffEditorInput` хранит `revealLine` (`-1` по умолчанию; в `equals` не участвует). `DiffEditor` после первого сравнения ставит курсор на блок с этой строкой справа, иначе на следующий.
  - Команды `gitbench.changes.next` «Next Change», `gitbench.changes.previous` «Previous Change», `gitbench.changes.rollback` «Rollback Lines» без привязки клавиш. Обработчики берут активный `ITextEditor`, колонку через `ChangeMarkerColumn.of` и строку курсора.
- [ ] **Step 1: Тесты.**
  - `showsHeadTextOfTheBlock`;
  - `rollbackReplacesTheBlockAndCloses`;
  - `nextMovesToTheFollowingRange` и `nextIsDisabledOnTheLastRange`;
  - `copyIsDisabledForInsertedLines`;
  - `escapeClosesThePopup`.
- [ ] **Step 2–4:** FAIL → реализовать → PASS. Коммит «Add the change marker popup and commands».

---

### Task 5: Модель blame

**Files:**
- Create: `gitbench/annotate/{BlameCommit,Blame,BlameParser,BlameService,AnnotationColors,PrettyDate}.java`
- Test: `src/test/java/gitbench/annotate/{BlameParserTest,BlameServiceTest,AnnotationColorsTest,PrettyDateTest}.java`

**Interfaces:**
- Produces:
  - `record BlameCommit(String hash, String author, String authorMail, Instant authorTime, String summary, String filename, String previousHash, String previousFilename)`. `previous*` — null у первого коммита файла.
  - `record Blame(List<String> lineHashes, Map<String, BlameCommit> commits)`: `commitAt(int line) -> Optional<BlameCommit>`; пусто для незакоммиченной строки (hash из нулей).
  - `BlameParser.parse(String porcelain) -> Blame` для `git blame --porcelain`. Заголовок коммита приходит только при первом появлении hash.
  - `BlameService(GitRunner)`: `blame(GitRepository, String path, String contents, Charset) -> Blame` — `git blame --porcelain --contents - -- path`, stdin — `contents` в кодировке файла.
  - `AnnotationColors.palette(boolean dark) -> List<RGB>` (21 цвет); `AnnotationColors.byCommit(Blame, boolean dark) -> Map<String, RGB>` — по правилу из Global Constraints, пусто при < 2 коммитах. `RGB` из SWT: класс без дисплея.
  - `PrettyDate.format(Instant, Clock) -> String`.
- [ ] **Step 1: Тесты.**
  - `BlameParserTest`: `parsesHeadersOnceAndLinesForEach`; `zeroHashLineIsUncommitted`; `readsPreviousAndFilename`; `summaryWithTabsAndUnicode`.
  - `BlameServiceTest` на `TempRepo`: `blamesCommittedLines` (два коммита двух авторов); `blameWithDirtyContentsMarksNewLinesUncommitted`; `blameEncodesContentsInFileCharset` (windows-1251, строка с кириллицей не помечается как изменённая).
  - `AnnotationColorsTest`: `paletteHas21ColorsStartingAndEndingAtAnchors`; `newestCommitGetsTheFirstColor`; `singleCommitHasNoColors`.
  - `PrettyDateTest`: `today`, `yesterday`, `yesterdayAcrossNewYear`, `olderDateIsDayMonthYear`.
- [ ] **Step 2–4:** FAIL → реализовать → PASS (`./gradlew.bat test --tests "gitbench.annotate.*"`). Коммит «Add the blame model».

---

### Task 6: Колонка annotate

**Files:**
- Create: `ui/annotate/{AnnotatePainter,AnnotateColumn,AnnotateHandler}.java`, `ui/log/LogLocator.java`
- Modify: `ui/log/LogPanel.java`, `ui/GitView.java` (`public LogPanel log()`), `plugin.xml`
- Test: `src/uiTest/java/gitbench/ui/annotate/AnnotatePainterTest.java`

**Interfaces:**
- Consumes: Task 5, `LineGeometry` (Task 2), `DiffRequest`/`DiffSide.Revision`/`DiffOpener`.
- Produces:
  - `AnnotatePainter(Font)`: `width(Blame, Clock) -> int` — ширина самой длинной даты + 8 px + ширина самого длинного автора + 4 px по краям; `paint(GC, Blame, Map<String,RGB> colors, LineGeometry, boolean dark, Clock)` — фон строки по коммиту, дата и автор двумя выровненными колонками, цвет текста по Global Constraints; незакоммиченные строки пустые.
  - `AnnotateColumn extends AbstractContributedRulerColumn`: колонка `gitbench.columns.annotate`, имя «Git Blame», `enabled="false"`, `includeInMenu="false"`, `<placement gravity="0.8"><before id="org.eclipse.ui.editors.columns.linenumbers"/></placement>`, тот же `targetClass`. Пока видна, повторяет blame с текстом документа через 500 мс после правки и по `RepositoryEvents`. Меняет ширину через `getParentRuler().relayout()`. Подсказка строки: `{короткий hash} {автор} {дата dd.MM.yyyy HH:mm}`, пустая строка, `{summary}`. Клик по строке → `LogLocator.showCommit`. Своё меню по правой кнопке (событие `SWT.MenuDetect`, `doit = false`): «Show Diff», «Copy Revision Number», «Select in Git Log», разделитель, «Close Annotations».
  - Show Diff: `DiffRequest(repo, filename, previous == null ? Empty("File added") : Revision(previousHash, previousFilename), Revision(hash, filename))`.
  - `AnnotateHandler`: команда `gitbench.annotate` «Annotate with Git Blame» включает и выключает колонку через `IColumnSupport` активного редактора (`RulerColumnRegistry.getDefault().getColumnDescriptor("gitbench.columns.annotate")`). Пункт в `popup:#TextRulerContext`, `popup:#CEditorRulerContext`, `popup:#ASMEditorRulerContext`, `after=additions`. Нет репозитория или файл не отслеживается — сообщение в строке состояния, колонка не открывается.
  - `LogPanel.selectCommit(String)`: если коммита ещё нет в загруженном графе, hash запоминается и выбирается после следующей загрузки. Сбрасывается при смене репозитория.
  - `LogLocator.showCommit(IWorkbenchPage, String hash)`: открытый вид Git, иначе открытый вид Git Log, иначе открыть вид Git; затем `selectCommit`.
- [ ] **Step 1: Тесты** (`AnnotatePainter`, геометрия 16 px на строку).
  - `backgroundFollowsTheCommitColor`;
  - `uncommittedLineHasNoBackground`;
  - `widthFitsTheLongestDateAndAuthor`;
  - `lastCommitLinesUseTheLastCommitColor` (пиксель текста строки последнего коммита темнее остальных в светлой теме).
- [ ] **Step 2–4:** FAIL → реализовать → PASS. Коммит «Add Annotate with Git Blame».

---

### Task 7: Отчёт

- [ ] `docs/superpowers/reports/2026-10-22-stage5-results.md`: что сделано, отступления от IntelliJ, что проверить руками, что отложено. Отступления записать и в спеку, раздел 5. В README в Features добавить маркеры и annotate. Отметить Quick Diff Eclipse: он рисует свои цвета в колонке номеров строк; отключается в Preferences → General → Editors → Text Editors → Quick Diff. Коммит «Record the stage 5 results».

## Ручная проверка (пользователь)

1. Открыть `.c` файл, изменить, добавить и удалить строки: зелёный, синий и серый маркеры на поле справа от номеров строк.
2. Клик по маркеру: popup с текстом HEAD; Previous/Next Change; Rollback Lines (Ctrl+Z отменяет); Show Diff for Lines открывает diff на этом блоке; Copy.
3. Commit из вида Commit: маркеры исчезают.
4. Правая кнопка на поле → Annotate with Git Blame: дата и автор, фон по возрасту. Клик по строке выбирает коммит в Git Log. Меню колонки: Show Diff, Copy Revision Number, Select in Git Log, Close Annotations.
5. Правка при включённом annotate: новые строки пустые.
6. Тёмная тема: цвета маркеров и annotate.
