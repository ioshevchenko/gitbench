# Этап 4: итоги (ветка stage4-merge)

## Сделано
- **Окно Conflicts**, как MultipleFileMergeDialog IntelliJ: файлы, колонки Yours и Theirs («Modified» / «Deleted»), кнопки Accept Yours, Accept Theirs, Merge..., Close, «Group by directory». Над списком строка вида «Merging branch feature into branch main».
- **Окно Merge**: Yours | Result | Theirs, полосы с кривыми между колонками. На полосах кнопки `>>` / `<<`, `x` и волшебная палочка для простого конфликта. Неконфликтные блоки применяются при открытии.
  - Тулбар: Previous/Next Difference (`F7` / `Shift+F7`), Apply non-conflicting changes (Left / All / Right), Resolve simple conflicts, статус «N changes, M conflicts».
  - Кнопки: Accept Left, Accept Right, Apply, Cancel. Apply сохраняет файл и делает `git add`. Если блоки остались, Apply спрашивает. Cancel спрашивает, если Result изменён.
  - Когда всё решено, сверху появляется «All changes have been processed» со ссылкой Apply Changes.
- **Модель merge** повторяет MergeConflictModel и MergeModelBase IntelliJ: вторая сторона конфликта дописывается, блоки следуют за ручной правкой Result.
- **Merge, rebase, cherry-pick, revert.** Rebase переставляет стороны, как IntelliJ: Yours — твой коммит.
- **Commit-вид**: узел «Merge Conflicts» над «Changes». Двойной клик по файлу открывает Merge, по узлу — окно Conflicts. В меню есть «Resolve Conflicts...».
- **Окно Conflicts открывается само** после merge, rebase, cherry-pick или revert с конфликтом.
- **Финальное ревью** (отдельный ревьюер) и исправления в сохранении:
  - при `core.autocrlf=true` CRLF-файл сохранялся с LF;
  - Enter в пустом или однострочном Result вставлял CRLF и сдвигал блоки;
  - при сохранении терялись BOM и кодировка.
- Тесты: 546, все зелёные, 1 пропущен (как в оригинале графа). Jar лежит в dropins CubeIDE.
- **Тесты UI** — отдельная Gradle-задача `./gradlew uiTest`, 17 тестов на настоящих виджетах SWT без запущенной Eclipse:
  - правка `>>` в документе с CRLF (4 теста);
  - клик по `>>` на полосе diff, синхронная прокрутка, Unified с «Ignore whitespaces and empty lines» (5);
  - `>>`, `<<`, `x` в колонках merge, ручная правка Result, окно Merge на настоящем конфликте: Accept Left, Accept Right, Apply, Cancel (8).

  Тесты открывают окна на экране, поэтому не входят в обычный `test`. Каждая из трёх нарочных поломок (место кнопки, сдвиг блоков, прокрутка) роняет свой тест.
  Не покрыто: привязки клавиш через команды Eclipse, вкладки редактора, общий буфер с C-редактором, тёмная тема. Это работает только в запущенной IDE.

## Отступления от IntelliJ
- Внутри блоков merge нет подсветки слов, только строки.
- Нет шестерёнки с Ignore в тулбаре merge.
- У узла «Merge Conflicts» нет ссылки «Resolve»: подпись дерева JFace не умеет ссылку. Вместо неё двойной клик по узлу и пункт меню.
- `F7` в окне Merge работает через обработчик клавиш окна, не через команды Eclipse: команды не работают в модальном окне.
- Нет Undo для ошибочного клика по `>>`, `<<`, `x`. Выход — Cancel и снова Merge.

## Не сделано
- Отложенные мелочи ревью:
  - удалённый блок, который пользователь стёр до нуля строк, не считается решённым;
  - кнопки на полосах пересчитывают Result при каждой перерисовке;
  - Accept в окне Conflicts может столкнуться с `index.lock`;
  - разбор записей rename в `git status`;
  - заголовок «Changes from » без имени, если операции нет;
  - remote-ветка в заголовке показана как hash;
  - нет сообщения, если файл уже не в конфликте;
  - повтор пути при выборе папки и файла в ней.
- Нет кнопки «Continue rebase / merge» после последнего конфликта.
- Ветка не слита в main: решение после ручной проверки.

## Что проверить руками
1. Merge двух веток с конфликтом в Branches: окно Conflicts открывается само.
2. Merge...: неконфликтные блоки применены, конфликт красный.
3. `>>`, `<<`, `x`, палочка, Accept Left/Right, Apply. После Apply файл ушёл из «Merge Conflicts».
4. Ручная правка Result, потом `>>` на блоке ниже.
5. Rebase с конфликтом: Yours — твой коммит.
6. Файл, удалённый с одной стороны: Accept Yours/Theirs.
7. CRLF-файл после Apply остаётся CRLF.
