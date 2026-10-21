# Gitbench

Git-интерфейс в стиле IntelliJ IDEA для STM32CubeIDE и других IDE на Eclipse.

Плагин повторяет окна Git из IntelliJ: их структуру, иконки New UI, цвета и поведение. Работает через git CLI. EGit не нужен, и плагин ему не мешает.

## Что есть

- **Commit** — изменённые файлы с чекбоксами, группировка по папкам, Unversioned Files, Amend, история сообщений, Commit и Commit and Push..., Rollback, Add to VCS, Ignore.
- **Git Log** — граф коммитов (перенесён из IntelliJ без изменений), метки веток и тегов, фильтры Text or hash, Branch, User, Date, Paths, детали и файлы коммита. Операции: Cherry-Pick, Revert, Checkout Revision, Reset, New Branch, New Tag.
- **Branches** — дерево HEAD / Local / Remote, избранное, ahead/behind. Операции: Checkout, New Branch, Rename, Delete, Merge, Rebase, Compare with Current, Update.
- **Diff** — вкладка редактора в режимах Side-by-side и Unified, как в IntelliJ:
  - подсветка слов;
  - кривые между колонками;
  - `>>` для отката или принятия блока;
  - синхронная прокрутка;
  - подсветка C/C++ из CDT;
  - общий буфер с C-редактором: правки и `Ctrl+S` общие.

  Сравнение текстов тоже перенесено из IntelliJ.

Каждое окно — отдельный вид (Window → Show View → Other → Gitbench). Перспективу собираешь сам.

Горячие клавиши IntelliJ работают только внутри окон Gitbench: `F7` / `Shift+F7`, `Alt+Shift+←/→`, `Ctrl+Alt+R`, `Ctrl+D`, `F4`.

## Требования

- STM32CubeIDE 2.0.0 и новее, или Eclipse 4.33 и новее.
- Java 17.
- git в `PATH`. Путь к git можно задать в Window → Preferences → Gitbench.

## Сборка и установка

1. В `gradle.properties` задать `cubeIdeHome` — папку CubeIDE, в которой лежат `plugins` и `dropins`. Jar'ы Eclipse для компиляции берутся оттуда.
2. Собрать и скопировать плагин в `dropins`:

   ```
   ./gradlew deployDropins
   ```

3. Запустить CubeIDE с ключом `-clean`.

CubeIDE по умолчанию не читает папку `dropins`. Чтобы включить её, в `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info` в строке `org.eclipse.equinox.p2.reconciler.dropins` замените последнее `false` на `true`. Сохраните копию файла перед правкой.

## Тесты

```
./gradlew test
```

Тесты разбирают вывод настоящего git во временных репозиториях. Отдельно идут оригинальные тесты перенесённых модулей IntelliJ (граф коммитов и сравнение текстов). UI проверяется вручную в CubeIDE.

## Устройство

| Папка | Что внутри |
|---|---|
| `src/main/java/gitbench/git` | вызовы git CLI, разбор вывода |
| `src/main/java/gitbench/log`, `branch`, `diff`, `model` | модели без UI |
| `src/main/java/gitbench/ui` | виды и редакторы на SWT/JFace |
| `third_party/intellij-graph` | граф коммитов из IntelliJ Community |
| `third_party/intellij-diff` | сравнение текстов из IntelliJ Community |
| `docs/superpowers` | спецификация, планы и итоги этапов |

## Лицензия

Иконки, граф коммитов и сравнение текстов взяты из [IntelliJ Community](https://github.com/JetBrains/intellij-community) и распространяются по Apache License 2.0. Подробности в `NOTICE`.
