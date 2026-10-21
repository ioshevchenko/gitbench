# Commit graph from IntelliJ Community

Source: https://github.com/JetBrains/intellij-community, commit `55da02142d542fd039cbf699c88b7f01fae6d9a2`.
License: Apache License 2.0. Every file keeps its original copyright header.

| Folder | Taken from |
|---|---|
| `src` | `platform/vcs-log/graph-api/src` and `platform/vcs-log/graph/src` |
| `test` | `platform/vcs-log/graph/test` |
| `testData` | `platform/vcs-log/graph/testData` |
| `shims` | Gitbench: minimal replacements of IntelliJ Platform classes used by `src` |
| `testShims` | Gitbench: minimal replacements of IntelliJ Platform classes used by `test` |

The ported files are not changed. To update, copy the folders again and run `./gradlew test`.
