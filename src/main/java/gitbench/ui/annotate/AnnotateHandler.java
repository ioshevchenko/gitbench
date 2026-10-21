package gitbench.ui.annotate;

import gitbench.git.GitException;
import gitbench.git.GitResult;
import gitbench.git.GitRunner;
import gitbench.git.RepositoryLocator;
import gitbench.status.LineStatusBase;
import gitbench.ui.Activator;
import gitbench.ui.status.EditorFiles;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.ITextEditor;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Annotate with Git Blame: shows the annotate column in the active editor, or closes it when it shows. A file that git
 * does not track gets a message in the status line instead.
 */
public final class AnnotateHandler extends AbstractHandler {
  @Override
  public Object execute(ExecutionEvent event) {
    IEditorPart part = HandlerUtil.getActiveEditor(event);
    ITextEditor editor = part == null ? null : part.getAdapter(ITextEditor.class);
    if (editor == null) {
      return null;
    }
    if (AnnotateColumn.isVisible(editor)) {
      AnnotateColumn.setVisible(editor, false);
      return null;
    }
    String problem = problem(editor);
    if (problem != null) {
      editor.getEditorSite().getActionBars().getStatusLineManager().setErrorMessage(problem);
      return null;
    }
    AnnotateColumn.setVisible(editor, true);
    return null;
  }

  /** Why the file cannot be annotated, or null. */
  private static String problem(ITextEditor editor) {
    Optional<Path> file = EditorFiles.file(editor.getEditorInput());
    if (file.isEmpty()) {
      return "Cannot annotate: the file is not on disk";
    }
    GitRunner runner = Activator.probeRunner();
    try {
      Optional<LineStatusBase.Key> key = LineStatusBase.find(new RepositoryLocator(runner), file.get());
      if (key.isEmpty()) {
        return "Cannot annotate: the file is not under Git";
      }
      GitResult tracked = runner.run(key.get().repository().root(), List.of("ls-files", "--error-unmatch", "--", key.get().path()), null);
      return tracked.ok() ? null : "Cannot annotate: the file is not under Git";
    }
    catch (GitException e) {
      return "Cannot annotate: " + e.userMessage();
    }
  }
}
