package gitbench.diff;

/**
 * The {@code >>} and {@code <<} actions of a diff block: copy the block lines of the source side to the other side.
 * Repeats SimpleDiffViewer.replaceChange and appendChange with DiffUtil.applyModification.
 */
public final class BlockApplier {
  private BlockApplier() {
  }

  /** Replaces the block lines of the target side with the block lines of the source side, or returns null. */
  public static Edit replace(DiffText source, DiffText target, DiffChange change, Side sourceSide) {
    Side targetSide = sourceSide.other();
    return modification(target, change.startLine(targetSide), change.endLine(targetSide),
                        source, change.startLine(sourceSide), change.endLine(sourceSide));
  }

  /** Inserts the block lines of the source side after the block lines of the target side, or returns null. */
  public static Edit append(DiffText source, DiffText target, DiffChange change, Side sourceSide) {
    if (change.startLine(sourceSide) == change.endLine(sourceSide)) {
      return null;
    }
    int end = change.endLine(sourceSide.other());
    return modification(target, end, end, source, change.startLine(sourceSide), change.endLine(sourceSide));
  }

  private static Edit modification(DiffText target, int line1, int line2, DiffText source, int sourceLine1, int sourceLine2) {
    if (line1 == line2 && sourceLine1 == sourceLine2) {
      return null;
    }
    int length = target.text().length();
    if (line1 == line2) {
      String content = linesContent(source, sourceLine1, sourceLine2);
      if (line1 == target.lineCount()) {
        return at(target, length, length, "\n" + content);
      }
      return at(target, target.lineStart(line1), target.lineStart(line1), content + "\n");
    }
    int start = target.lineStart(line1);
    int end = target.lineEnd(line2 - 1);
    if (sourceLine1 == sourceLine2) {
      if (start > 0) {
        start--;
      }
      else if (end < length) {
        end++;
      }
      return at(target, start, end, "");
    }
    return at(target, start, end, linesContent(source, sourceLine1, sourceLine2));
  }

  private static String linesContent(DiffText text, int line1, int line2) {
    return text.text().substring(text.lineStart(line1), text.lineEnd(line2 - 1));
  }

  private static Edit at(DiffText target, int startOffset, int endOffset, String text) {
    int startLine = target.lineOf(startOffset);
    int endLine = target.lineOf(endOffset);
    return new Edit(startLine, startOffset - target.lineStart(startLine), endLine, endOffset - target.lineStart(endLine), text);
  }
}
