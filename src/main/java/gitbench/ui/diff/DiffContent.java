package gitbench.ui.diff;

import gitbench.diff.DiffChange;
import gitbench.diff.DiffText;
import org.eclipse.jface.text.IDocument;

import java.nio.file.Path;
import java.util.List;

/**
 * What a diff viewer shows: the two documents with their column titles, and the blocks computed from the texts
 * {@code leftText} and {@code rightText}. The texts are snapshots of the documents taken for that computation.
 * {@code file} is the absolute path of the compared file in the working tree; CDT reads its project settings.
 */
record DiffContent(IDocument left, IDocument right, String leftTitle, String rightTitle, boolean rightEditable,
                   boolean localChanges, Path file, DiffText leftText, DiffText rightText, List<DiffChange> changes) {
  DiffContent withLeftTitle(String newLeftTitle) {
    return new DiffContent(left, right, newLeftTitle, rightTitle, rightEditable, localChanges, file, leftText, rightText, changes);
  }

  DiffContent withChanges(DiffText newLeft, DiffText newRight, List<DiffChange> newChanges) {
    return new DiffContent(left, right, leftTitle, rightTitle, rightEditable, localChanges, file, newLeft, newRight, newChanges);
  }
}
