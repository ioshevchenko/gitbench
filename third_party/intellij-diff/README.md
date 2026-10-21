# Text comparison from IntelliJ Community

Source: https://github.com/JetBrains/intellij-community, commit `55da02142d542fd039cbf699c88b7f01fae6d9a2`.
License: Apache License 2.0. Every file keeps its original copyright header.

| Folder | Taken from |
|---|---|
| `src` | `platform/util/diff/src` (all) |
| `src` | `platform/diff-api/src/.../diff/comparison/ComparisonManager.java`, `InnerFragmentsPolicy.java`, `.../diff/util/LineRange.java` |
| `src` | `platform/diff-impl/src/.../diff/comparison/ComparisonManagerImpl.java`, `ByLine.java`, `ByWord.java`, `DiffIterableUtilEx.java`, `IndicatorCancellationChecker.kt` |
| `src` | `platform/diff-impl/src/.../diff/tools/util/text/LineOffsetsUtil.java`, `LineOffsetsDocumentWrapper.java` |
| `src` | `platform/util/multiplatform/src/.../util/fastutil/**` |
| `src` | `platform/util/base/multiplatform/src`: `IntPair`, `PeekableIterator`, `PeekableIteratorWrapper`, `MergingCharSequence`, `LineTokenizer`, `StringsKmp`, `CharSequenceWithStringHash`, `CharArrayCharSequence`, `CharSequenceSubSequence`, `CharSequenceBackedByArray`, `CharArrayExternalizable`, `CharArrayUtilKmp`; `srcJvmMain/.../util/text/jvm.kt` |
| `src` | `platform/util/base/src/.../openapi/util/TextRange.java`, `Segment.java` |
| `test` | `platform/diff-impl/tests/testSrc/com/intellij/diff/comparison/*.kt` |
| `testShims` | `platform/diff-impl/tests/testSrc/com/intellij/diff/DiffTestCase.kt`, `platform/util-rt/src/.../openapi/util/Couple.java`; the rest is Gitbench |
| `shims` | Gitbench: minimal replacements of IntelliJ Platform classes |

Changes in ported files, each marked with `// Gitbench:`:
- `CharArrayUtilKmp.kt`: the two `linkToActual()` bodies call the JVM functions from `jvm.kt` directly. IntelliJ links them at build time.
- `DiffTestCase.kt`: `createFilePath` is removed, because it needs `LocalFilePath` and no comparison test uses it.

Tests not ported: `BlocksComparisonUtilTest` and `ComparisonUtilAutoTest`. They extend `HeavyDiffTestCase` and need the IntelliJ editor and line status tracker.

The Kotlin code needs `-Xjvm-default=all`, as in `intellij.platform.util.diff.iml`.
The shared shims `ContainerUtil` (main) and `StringUtil` (tests) live in `third_party/intellij-graph`.
