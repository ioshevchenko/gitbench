# Gitbench

An IntelliJ-style Git UI for STM32CubeIDE and other Eclipse-based IDEs.

Gitbench brings the Git tool windows of IntelliJ IDEA to Eclipse: the same layout, New UI icons, colors and behavior. It runs the git command line. It does not need EGit and does not interfere with it.

> Gitbench is an independent project. It is not affiliated with or endorsed by JetBrains or STMicroelectronics.

## Features

- **Commit** — changed files with check boxes, grouping by folder, Unversioned Files, Amend, message history, Commit and Commit and Push..., Rollback, Add to VCS, Ignore.
- **Git Log** — the commit graph (ported from IntelliJ unchanged), branch and tag labels, filters (text or hash, branch, user, date, paths), commit details and changed files. Operations: Cherry-Pick, Revert, Checkout Revision, Reset, New Branch, New Tag.
- **Branches** — a HEAD / Local / Remote tree with favorites and ahead/behind counters. Operations: Checkout, New Branch, Rename, Delete, Merge, Rebase, Compare with Current, Update.
- **Diff** — an editor tab with Side-by-side and Unified viewers, as in IntelliJ:
  - word highlighting;
  - curved bands between the columns;
  - `>>` to revert or accept a block;
  - synchronized scrolling;
  - C/C++ syntax colors from CDT;
  - a buffer shared with the C editor, so edits and `Ctrl+S` are common.

  The text comparison is also ported from IntelliJ.

Each window is a separate view (Window → Show View → Other → Gitbench), so you build your own perspective.

The IntelliJ shortcuts work only inside the Gitbench windows: `F7` / `Shift+F7`, `Alt+Shift+Left/Right`, `Ctrl+Alt+R`, `Ctrl+D`, `F4`.

## Requirements

- STM32CubeIDE 2.0.0 or later, or Eclipse 4.33 or later.
- Java 17.
- git in `PATH`. You can set another git executable in Window → Preferences → Gitbench.

## Build and install

1. Point the build to your IDE: the folder that holds `plugins` and `dropins`. Give it on the command line, or put the line `cubeIdeHome=<folder>` in `~/.gradle/gradle.properties`. The build compiles against the Eclipse jars of that IDE.

   ```
   ./gradlew deployDropins -PcubeIdeHome=C:/ST/STM32CubeIDE_2.2.0/STM32CubeIDE
   ```

   The task builds the plugin jar and copies it into the `dropins` folder.

2. Start the IDE once with `-clean`.

STM32CubeIDE does not read the `dropins` folder by default. To turn it on:
1. Open `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info` in the IDE folder.
2. Find the line `org.eclipse.equinox.p2.reconciler.dropins`.
3. Change its last value from `false` to `true`.

Keep a copy of the file before the edit.

## Tests

```
./gradlew test
```

The tests run real git in temporary repositories. They also include the original tests of the ported IntelliJ modules (the commit graph and the text comparison).

## Layout

| Folder | Contents |
|---|---|
| `src/main/java/gitbench/git` | git command line calls and output parsing |
| `src/main/java/gitbench/log`, `branch`, `diff`, `model` | models without UI |
| `src/main/java/gitbench/ui` | views and editors (SWT/JFace) |
| `third_party/intellij-graph` | the commit graph from IntelliJ Community |
| `third_party/intellij-diff` | the text comparison from IntelliJ Community |
| `docs/superpowers` | the design, the plans and the results of each stage (in Russian) |

## License

The icons, the commit graph and the text comparison come from [IntelliJ Community](https://github.com/JetBrains/intellij-community) under the Apache License 2.0. See `NOTICE`.
