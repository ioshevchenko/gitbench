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
  - syntax colors (see [Syntax colors](#syntax-colors));
  - a buffer shared with the C editor, so edits and `Ctrl+S` are common.

  The text comparison is also ported from IntelliJ.
- **Conflicts and Merge** — after a merge, rebase, cherry-pick or revert stops on conflicts, the Conflicts window lists the files with Accept Yours, Accept Theirs and Merge.... The Merge window shows Yours | Result | Theirs with `>>`, `<<`, `x`, the magic resolve of simple conflicts, and Apply non-conflicting changes.

Each window is a separate view (Window → Show View → Other → Gitbench), so you build your own perspective.

The IntelliJ shortcuts work only inside the Gitbench windows: `F7` / `Shift+F7`, `Alt+Shift+Left/Right`, `Ctrl+Alt+R`, `Ctrl+D`, `F4`.

## Requirements

- STM32CubeIDE 2.0.0 or later, or Eclipse 4.33 or later.
- Java 17.
- git in `PATH`. You can set another git executable in Window → Preferences → Gitbench.

## Install

In the IDE: **Help → Install New Software... → Add...**, and give the update site:

```
https://<owner>.github.io/gitbench/
```

Select **Gitbench**, finish the wizard and restart the IDE. Updates come through **Help → Check for Updates**.

Each [release](../../releases) also has the update site as a zip (`Add... → Archive...`) and the plugin jar.

## Syntax colors

Eclipse has no single API that colors a file the way its editor does: each editor keeps its configuration to itself. The diff and merge windows therefore look for colors in this order:

1. **CDT** for C, C++ and assembler (`.c`, `.h`, `.cpp`, `.s`, `.S`, ...). STM32CubeIDE always has CDT.
2. **The Generic Editor** of Eclipse: any plugin that registers a highlighter for the content type of the file. Install **TM4E** (Eclipse Marketplace; already in the Eclipse IDE packages) to get TextMate grammars for Makefile, CMake, Python, shell, YAML, JSON, XML, Markdown and many more.
3. Plain text.

## Build

The build needs only Java 17: the Eclipse API comes from Maven Central.

```
./gradlew build
```

| Task | What it does |
|---|---|
| `./gradlew test` | Unit tests; they run real git in temporary repositories, plus the original tests of the ported IntelliJ modules. |
| `./gradlew uiTest` | SWT tests of the diff and merge windows on the desktop, and a check of the CDT calls against real CDT jars. |
| `./gradlew shadowJar` | The plugin jar in `build/libs`. |
| `./gradlew updateSite -PeclipseHome=<Eclipse folder>` | The p2 update site in `build/update-site` and a zip in `build/distributions`. It runs the p2 publisher of that Eclipse installation; an STM32CubeIDE folder works too. |
| `./gradlew deployDropins -PcubeIdeHome=<IDE folder>` | Copies the plugin jar into the `dropins` folder of an IDE, for quick checks during development. |

`-Pversion=X.Y.Z` sets the version; the build time in UTC is the qualifier. Put `cubeIdeHome=...` into `~/.gradle/gradle.properties` to skip it on the command line.

STM32CubeIDE does not read its `dropins` folder by default. To turn it on for `deployDropins`:
1. Open `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info` in the IDE folder.
2. Find the line `org.eclipse.equinox.p2.reconciler.dropins`.
3. Change its last value from `false` to `true`.

Keep a copy of the file before the edit.

## Releases

GitHub Actions builds and tests every push on Windows. A tag `vX.Y.Z` builds that version, attaches the plugin and the update site zip to a GitHub release, and publishes the update site on GitHub Pages. In the repository settings, set **Pages → Source** to **GitHub Actions** once.

## Layout

| Folder | Contents |
|---|---|
| `src/main/java/gitbench/git` | git command line calls and output parsing |
| `src/main/java/gitbench/log`, `branch`, `diff`, `merge`, `model` | models without UI |
| `src/main/java/gitbench/ui` | views, editors and dialogs (SWT/JFace) |
| `src/test`, `src/uiTest` | unit tests, SWT UI tests |
| `site` | the feature and the category of the update site |
| `.github` | CI and release workflows |
| `third_party/intellij-graph` | the commit graph from IntelliJ Community |
| `third_party/intellij-diff` | the text comparison from IntelliJ Community |
| `docs/superpowers` | the design, the plans and the results of each stage (in Russian) |

## License

Gitbench is under the [MIT License](LICENSE).

The icons, the commit graph and the text comparison come from [IntelliJ Community](https://github.com/JetBrains/intellij-community) and keep the [Apache License 2.0](third_party/LICENSE-APACHE-2.0.txt). `NOTICE` lists these parts. The plugin jar contains both licenses and `NOTICE`.
