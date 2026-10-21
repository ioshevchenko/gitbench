package gitbench.ui.merge;

import com.intellij.diff.util.ThreeSide;
import gitbench.diff.DiffText;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.Side;
import gitbench.git.GitCommandListener;
import gitbench.git.GitRepository;
import gitbench.git.GitRunner;
import gitbench.merge.ConflictFile;
import gitbench.merge.ConflictService;
import gitbench.merge.MergeChange;
import gitbench.merge.MergeContentLoader;
import gitbench.merge.MergeContents;
import gitbench.merge.MergeContext;
import gitbench.merge.MergeModel;
import gitbench.testutil.TempRepo;
import gitbench.ui.SwtTest;
import org.eclipse.jface.text.Document;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Shell;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** The merge columns and the merge window on real widgets. */
class MergeWindowTest extends SwtTest {
  private static final int ICON_SIZE = 14;

  private Document result;
  private MergeModel model;
  private MergeColumns columns;

  /** Builds the three columns with the model behind them, as the merge window does. */
  private void columns(String yours, String base, String theirs) {
    result = new Document(base);
    DocumentResultText resultText = new DocumentResultText(result, () -> {
      if (columns != null) {
        columns.refresh();
      }
    });
    model = MergeModel.create(DiffText.of(yours), DiffText.of(base), DiffText.of(theirs), IgnorePolicy.DEFAULT, resultText);
    columns = new MergeColumns(shell, false, Path.of("a.txt"), new Document(yours), result, new Document(theirs), "Yours", "Theirs",
                               new MergeColumns.Actions() {
                                 @Override
                                 public void replace(MergeChange change, Side side) {
                                   model.replaceChange(change, side);
                                   columns.refresh();
                                 }

                                 @Override
                                 public void ignore(MergeChange change, Side side) {
                                   model.ignoreChange(change, side);
                                   columns.refresh();
                                 }

                                 @Override
                                 public void magic(MergeChange change) {
                                   model.resolveAutomatically(change);
                                   columns.refresh();
                                 }

                                 @Override
                                 public boolean key(int keyCode, int stateMask) {
                                   return false;
                                 }
                               });
    columns.setModel(model);
    show();
  }

  /** Clicks the arrow (or the cross) of the block on a band, where the merge window paints it. */
  private void clickBlockButton(MergeChange change, boolean leftBand, boolean cross) {
    Canvas band = columns.divider(leftBand);
    paint(band);
    StyledText side = columns.text(leftBand ? ThreeSide.LEFT : ThreeSide.RIGHT);
    int y = gitbench.ui.diff.DividerPainter.lineToY(side, change.startLine(leftBand ? ThreeSide.LEFT : ThreeSide.RIGHT), band)
            + side.getLineHeight() / 2;
    int width = band.getClientArea().width;
    int x = leftBand ? (cross ? ICON_SIZE + 1 : 1) : (cross ? width - 2 * ICON_SIZE - 1 : width - ICON_SIZE - 1);
    click(band, x + ICON_SIZE / 2, y);
  }

  @Test
  void leftArrowPutsYoursIntoTheResult() {
    columns("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");

    clickBlockButton(model.changes().get(0), true, false);

    assertThat(result.get()).isEqualTo("a\nX\nc\n");
    assertThat(model.changes().get(0).isOnesideAppliedConflict()).isTrue();
  }

  @Test
  void secondArrowOfAConflictAppendsTheOtherSide() {
    columns("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");

    clickBlockButton(model.changes().get(0), true, false);
    clickBlockButton(model.changes().get(0), false, false);

    assertThat(result.get()).isEqualTo("a\nX\nY\nc\n");
    assertThat(model.isAllResolved()).isTrue();
  }

  @Test
  void crossIgnoresTheSide() {
    columns("a\nX\nc\n", "a\nb\nc\n", "a\nY\nc\n");

    clickBlockButton(model.changes().get(0), true, true);

    assertThat(result.get()).isEqualTo("a\nb\nc\n");
    assertThat(model.changes().get(0).isResolved(Side.LEFT)).isTrue();
  }

  @Test
  void typingAboveABlockKeepsItsButtonOnTheRightLines() throws Exception {
    columns("a\nb\nc\nX\ne\n", "a\nb\nc\nd\ne\n", "a\nb\nc\nd\ne\n");

    result.replace(1, 0, "\ntyped line");
    pump();
    clickBlockButton(model.changes().get(0), true, false);

    assertThat(result.get()).isEqualTo("a\ntyped line\nb\nc\nX\ne\n");
  }

  // The merge window on a real conflict

  private TempRepo repo;
  private GitRepository repository;
  private ConflictService service;

  private MergeDialog openDialog(boolean[] saved) throws Exception {
    repo = TempRepo.init();
    repo.write("a.c", "base\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "theirs\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "ours\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");
    repository = new GitRepository(repo.root());
    GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
    service = new ConflictService(runner);
    ConflictFile file = service.list(repository).get(0);
    MergeContext context = service.context(repository);
    MergeContents contents = MergeContentLoader.load(runner, repository, file, context, StandardCharsets.UTF_8);
    MergeDialog dialog = new MergeDialog(shell, repository, file, context, contents, StandardCharsets.UTF_8, service,
                                         () -> saved[0] = true);
    dialog.setBlockOnOpen(false);
    dialog.open();
    pump();
    return dialog;
  }

  private void press(MergeDialog dialog, String text) {
    Button button = find(dialog.getShell(), Button.class, text);
    assertThat(button).as(text).isNotNull();
    button.notifyListeners(SWT.Selection, new org.eclipse.swt.widgets.Event());
    pump();
  }

  @Test
  void acceptLeftSavesYoursAndResolvesTheFile() throws Exception {
    boolean[] saved = {false};
    MergeDialog dialog = openDialog(saved);

    press(dialog, "Accept Left");

    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("ours\n");
    assertThat(service.list(repository)).isEmpty();
    assertThat(dialog.isResolved()).isTrue();
    assertThat(saved[0]).isTrue();
  }

  @Test
  void acceptRightSavesTheirs() throws Exception {
    boolean[] saved = {false};
    MergeDialog dialog = openDialog(saved);
    Shell dialogShell = dialog.getShell();

    press(dialog, "Accept Right");

    assertThat(dialogShell.isDisposed()).isTrue();
    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("theirs\n");
    assertThat(service.list(repository)).isEmpty();
  }

  @Test
  void applyAfterTakingTheirsAndIgnoringYoursSavesTheResult() throws Exception {
    boolean[] saved = {false};
    MergeDialog dialog = openDialog(saved);
    columns = dialog.columns();
    model = dialog.model();
    MergeChange conflict = model.changes().get(0);

    clickBlockButton(conflict, false, false);
    clickBlockButton(conflict, true, true);
    assertThat(model.isAllResolved()).isTrue();
    press(dialog, "Apply");

    assertThat(Files.readString(repo.root().resolve("a.c"))).isEqualTo("theirs\n");
    assertThat(service.list(repository)).isEmpty();
    assertThat(saved[0]).isTrue();
  }

  @Test
  void cancelWithAnUnchangedResultClosesWithoutSaving() throws Exception {
    boolean[] saved = {false};
    MergeDialog dialog = openDialog(saved);
    Shell dialogShell = dialog.getShell();

    press(dialog, "Cancel");

    assertThat(dialogShell.isDisposed()).isTrue();
    assertThat(saved[0]).isFalse();
    assertThat(service.list(repository)).hasSize(1);
  }

  /** Builds a conflict whose sides change one word of the same line, and opens the merge window. */
  private MergeDialog openWordConflict() throws Exception {
    repo = TempRepo.init();
    repo.write("a.c", "int a = 0;\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "int a = 2;\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "int a = 1;\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");
    repository = new GitRepository(repo.root());
    GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
    service = new ConflictService(runner);
    ConflictFile file = service.list(repository).get(0);
    MergeContext context = service.context(repository);
    MergeContents contents = MergeContentLoader.load(runner, repository, file, context, StandardCharsets.UTF_8);
    MergeDialog dialog = new MergeDialog(shell, repository, file, context, contents, StandardCharsets.UTF_8, service, () -> { });
    dialog.setBlockOnOpen(false);
    dialog.open();
    pump();
    return dialog;
  }

  @Test
  void ctrlZTakesBackTheLastBlockAction() throws Exception {
    MergeDialog dialog = openWordConflict();
    columns = dialog.columns();
    model = dialog.model();
    MergeChange conflict = model.changes().get(0);
    StyledText resultText = columns.text(ThreeSide.BASE);
    String before = resultText.getText();

    clickBlockButton(conflict, true, false);
    assertThat(resultText.getText()).isNotEqualTo(before);
    org.eclipse.swt.widgets.Event key = new org.eclipse.swt.widgets.Event();
    key.keyCode = 'z';
    key.character = (char)26;
    key.stateMask = SWT.MOD1;
    resultText.notifyListeners(SWT.KeyDown, key);
    pump();

    assertThat(resultText.getText()).isEqualTo(before);
    assertThat(conflict.isResolved(Side.LEFT)).isFalse();
    assertThat(conflict.isOnesideAppliedConflict()).isFalse();
  }

  @Test
  void changedWordsOfAConflictHaveTheFullConflictColor() throws Exception {
    MergeDialog dialog = openWordConflict();
    StyledText resultText = dialog.columns().text(ThreeSide.BASE);

    // "int a = 0;": the changed word is the 0 at column 8.
    org.eclipse.swt.custom.StyleRange word = resultText.getStyleRangeAtOffset(8);
    assertThat(word).isNotNull();
    assertThat(word.background).isEqualTo(gitbench.ui.diff.DiffColors.conflict(false));
  }

  @Test
  void typingAboveAConflictKeepsTheSyntaxColorsInBetween() throws Exception {
    StringBuilder base = new StringBuilder();
    for (int line = 0; line < 50; line++) {
      base.append("int x").append(line).append(";\n");
    }
    String yours = base.toString().replace("int x49;", "int a49;");
    String theirs = base.toString().replace("int x49;", "int b49;");
    columns(yours, base.toString(), theirs);
    // A highlighter like CDT: it colors the keyword "int" red and repairs only the damaged line.
    org.eclipse.jface.text.source.SourceViewer resultViewer = columns.viewer(ThreeSide.BASE);
    org.eclipse.jface.text.rules.RuleBasedScanner scanner = new org.eclipse.jface.text.rules.RuleBasedScanner();
    org.eclipse.jface.text.rules.WordRule keywords = new org.eclipse.jface.text.rules.WordRule(new org.eclipse.jface.text.rules.IWordDetector() {
      @Override
      public boolean isWordStart(char c) {
        return Character.isLetter(c);
      }

      @Override
      public boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c);
      }
    });
    keywords.addWord("int", new org.eclipse.jface.text.rules.Token(
      new org.eclipse.jface.text.TextAttribute(display.getSystemColor(SWT.COLOR_RED))));
    scanner.setRules(new org.eclipse.jface.text.rules.IRule[]{keywords});
    org.eclipse.jface.text.rules.DefaultDamagerRepairer repairer = new org.eclipse.jface.text.rules.DefaultDamagerRepairer(scanner);
    org.eclipse.jface.text.presentation.PresentationReconciler reconciler = new org.eclipse.jface.text.presentation.PresentationReconciler();
    reconciler.setDamager(repairer, org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE);
    reconciler.setRepairer(repairer, org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE);
    reconciler.install(resultViewer);
    resultViewer.invalidateTextPresentation();
    pump();
    StyledText text = columns.text(ThreeSide.BASE);
    int line25 = result.getLineOffset(25);
    assertThat(text.getStyleRangeAtOffset(line25)).isNotNull();
    assertThat(text.getStyleRangeAtOffset(line25).foreground).isEqualTo(display.getSystemColor(SWT.COLOR_RED));

    // Typing on line 0 repairs line 0; the changed words of the conflict on line 49 must not widen that repair.
    System.err.println("RANGES before " + text.getStyleRanges().length);
    result.replace(0, 0, "x");
    pump();

    int line25After = result.getLineOffset(25);
    System.err.println("RANGES after " + text.getStyleRanges().length + " first " + java.util.Arrays.toString(java.util.Arrays.copyOf(text.getStyleRanges(), 3)));
    assertThat(text.getStyleRangeAtOffset(line25After)).as("syntax color of line 25 after typing").isNotNull();
    assertThat(text.getStyleRangeAtOffset(line25After).foreground).isEqualTo(display.getSystemColor(SWT.COLOR_RED));
  }

  /** Ctrl+Z in the Result column, as the user presses it. */
  private void pressCtrlZ(StyledText text) {
    org.eclipse.swt.widgets.Event key = new org.eclipse.swt.widgets.Event();
    key.keyCode = 'z';
    key.character = (char)26;
    key.stateMask = SWT.MOD1;
    text.notifyListeners(SWT.KeyDown, key);
    pump();
  }

  @Test
  void ctrlZGoesBackStepByStepOverTypedText() throws Exception {
    repo = TempRepo.init();
    repo.write("a.c", "a\nb\nc\nd\ne\n");
    repo.commitAll("base");
    repo.git("checkout", "-q", "-b", "feature");
    repo.write("a.c", "a\nB2\nc\nD2\ne\n");
    repo.commitAll("theirs");
    repo.git("checkout", "-q", "main");
    repo.write("a.c", "a\nB1\nc\nD1\ne\n");
    repo.commitAll("ours");
    repo.gitMayFail("merge", "-q", "feature");
    repository = new GitRepository(repo.root());
    GitRunner runner = new GitRunner(() -> "git", GitCommandListener.NONE);
    service = new ConflictService(runner);
    ConflictFile file = service.list(repository).get(0);
    MergeContext context = service.context(repository);
    MergeContents contents = MergeContentLoader.load(runner, repository, file, context, StandardCharsets.UTF_8);
    MergeDialog dialog = new MergeDialog(shell, repository, file, context, contents, StandardCharsets.UTF_8, service, () -> { });
    dialog.setBlockOnOpen(false);
    dialog.open();
    pump();
    columns = dialog.columns();
    model = dialog.model();
    StyledText text = columns.text(ThreeSide.BASE);
    org.eclipse.jface.text.IDocument document = columns.viewer(ThreeSide.BASE).getDocument();
    String start = document.get();

    clickBlockButton(model.changes().get(0), true, false);
    String afterA = document.get();
    document.replace(0, 0, "typed\n");
    pump();
    String afterTyping = document.get();
    clickBlockButton(model.changes().get(1), true, false);

    pressCtrlZ(text);
    assertThat(document.get()).as("first Ctrl+Z takes back block B").isEqualTo(afterTyping);
    pressCtrlZ(text);
    assertThat(document.get()).as("second Ctrl+Z takes back the typing").isEqualTo(afterA);
    pressCtrlZ(text);
    assertThat(document.get()).as("third Ctrl+Z takes back block A").isEqualTo(start);
  }
}
