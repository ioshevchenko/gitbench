package gitbench.ui.log;

import gitbench.log.CommitDetails;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyleRange;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Composite;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The commit details under the file tree: the subject in bold, the message body, then hash, author, date,
 * the committer when it differs, and the branches that contain the commit.
 */
final class CommitDetailsPanel {
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());

  private final StyledText text;

  CommitDetailsPanel(Composite parent) {
    text = new StyledText(parent, SWT.READ_ONLY | SWT.WRAP | SWT.V_SCROLL);
    text.setMargins(8, 6, 8, 6);
  }

  StyledText control() {
    return text;
  }

  void clear() {
    text.setText("");
  }

  void showLoading() {
    text.setText("Loading...");
  }

  /** Shows one commit. {@code branches} is null while it loads. */
  void show(CommitDetails d, List<String> branches) {
    List<StyleRange> styles = new ArrayList<>();
    StringBuilder out = new StringBuilder();
    styles.add(bold(0, d.subject().length()));
    out.append(d.fullMessage()).append("\n\n");
    int meta = out.length();
    out.append(d.hash(), 0, Math.min(10, d.hash().length())).append("  ")
      .append(d.authorName()).append(" <").append(d.authorEmail()).append(">  on ").append(DATE.format(d.authorTime()));
    if (!d.committerName().equals(d.authorName())) {
      out.append("\ncommitted by ").append(d.committerName()).append(" on ").append(DATE.format(d.commitTime()));
    }
    out.append("\n\n");
    if (branches == null) {
      out.append("In branches: loading...");
    }
    else if (branches.isEmpty()) {
      out.append("Not in any branch");
    }
    else {
      out.append("In ").append(branches.size()).append(branches.size() == 1 ? " branch: " : " branches: ")
        .append(String.join(", ", branches));
    }
    text.setText(out.toString());
    StyleRange gray = new StyleRange(meta, out.length() - meta, text.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DISABLED_FOREGROUND), null);
    styles.add(gray);
    text.setStyleRanges(styles.toArray(StyleRange[]::new));
  }

  /** Shows a list of subjects for a multiple selection. */
  void showMany(List<CommitDetails> commits) {
    StringBuilder out = new StringBuilder(commits.size() + " commits selected\n\n");
    for (CommitDetails d : commits) {
      out.append(d.hash(), 0, Math.min(8, d.hash().length())).append("  ").append(d.subject()).append('\n');
    }
    text.setText(out.toString());
  }

  private static StyleRange bold(int start, int length) {
    StyleRange range = new StyleRange();
    range.start = start;
    range.length = length;
    range.fontStyle = SWT.BOLD;
    return range;
  }
}
