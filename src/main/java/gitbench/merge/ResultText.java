package gitbench.merge;

import gitbench.diff.DiffText;

import java.util.List;

/**
 * The Result column of the merge window, with '\n' line separators. Every change must reach
 * {@link #beforeChange(int, int, String)} before it happens, as the IntelliJ MergeModelBase document listener: the
 * model then moves the blocks. A subclass over a text document calls it from its document listener, so typing moves
 * the blocks too.
 */
public abstract class ResultText {
  private MergeModel model;

  void attach(MergeModel mergeModel) {
    model = mergeModel;
  }

  public abstract String text();

  /** Replaces text; the subclass makes sure that {@link #beforeChange} runs first. */
  protected abstract void doReplace(int offset, int length, String newText);

  public final void replace(int offset, int length, String newText) {
    doReplace(offset, length, newText);
  }

  /**
   * Tells the model about a change, as DiffUtil.getAffectedLineRange and countLinesShift: the change touches lines
   * {@code [line1, line2)} and adds {@code shift} lines.
   */
  public final void beforeChange(int offset, int length, String newText) {
    if (model == null) {
      return;
    }
    DiffText current = DiffText.of(text());
    int line1 = current.lineOf(offset);
    int line2 = current.lineOf(offset + length) + 1;
    int shift = countNewLines(newText) - countNewLines(text().substring(offset, offset + length));
    model.beforeResultChange(line1, line2, shift);
  }

  public int lineCount() {
    return DiffText.of(text()).lineCount();
  }

  /** The text of lines {@code [start, end)}, joined with '\n', without the last separator. */
  public String lines(int start, int end) {
    if (start >= end) {
      return "";
    }
    DiffText current = DiffText.of(text());
    return text().substring(current.lineStart(start), current.lineEnd(end - 1));
  }

  /** Replaces lines {@code [line1, line2)} with {@code newLines}, with the line separator rules of DiffUtil.applyModification. */
  public void replaceLines(int line1, int line2, List<String> newLines) {
    if (line1 == line2 && newLines.isEmpty()) {
      return;
    }
    DiffText current = DiffText.of(text());
    int length = text().length();
    String content = String.join("\n", newLines);
    if (line1 == line2) {
      if (line1 == current.lineCount()) {
        replace(length, 0, "\n" + content);
      }
      else {
        replace(current.lineStart(line1), 0, content + "\n");
      }
      return;
    }
    int start = current.lineStart(line1);
    int end = current.lineEnd(line2 - 1);
    if (newLines.isEmpty()) {
      if (start > 0) {
        start--;
      }
      else if (end < length) {
        end++;
      }
      replace(start, end - start, "");
      return;
    }
    replace(start, end - start, content);
  }

  private static int countNewLines(String text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        count++;
      }
    }
    return count;
  }
}
