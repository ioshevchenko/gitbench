package gitbench.ui.diff;

import gitbench.diff.BlockApplier;
import gitbench.diff.DiffChange;
import gitbench.diff.DiffModel;
import gitbench.diff.DiffText;
import gitbench.diff.HighlightPolicy;
import gitbench.diff.IgnorePolicy;
import gitbench.diff.Side;
import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The >> of a CRLF working file: the edit is in LF lines and columns, the document keeps CRLF. */
class DocumentEditsTest {
  private static Document revertFirstBlock(String left, String rightDocument) throws Exception {
    Document document = new Document(rightDocument);
    DiffText leftText = DiffText.of(left);
    DiffText rightText = DiffText.of(rightDocument);
    DiffChange change = DiffModel.compute(leftText, rightText, IgnorePolicy.DEFAULT, HighlightPolicy.BY_WORD).get(0);
    DocumentEdits.apply(document, BlockApplier.replace(leftText, rightText, change, Side.LEFT));
    return document;
  }

  @Test
  void replaceInCrlfDocumentKeepsCrlf() throws Exception {
    assertThat(revertFirstBlock("a\nold\nc\n", "a\r\nnew\r\nmore\r\nc\r\n").get()).isEqualTo("a\r\nold\r\nc\r\n");
  }

  @Test
  void deleteInsertedBlockInCrlfDocument() throws Exception {
    assertThat(revertFirstBlock("a\nc\n", "a\r\nb\r\nc\r\n").get()).isEqualTo("a\r\nc\r\n");
  }

  @Test
  void insertDeletedBlockInCrlfDocument() throws Exception {
    assertThat(revertFirstBlock("a\nb\nc\n", "a\r\nc\r\n").get()).isEqualTo("a\r\nb\r\nc\r\n");
  }

  @Test
  void deleteLastLineWithoutSeparator() throws Exception {
    assertThat(revertFirstBlock("a\r\nb", "a\r\nb\r\nc").get()).isEqualTo("a\r\nb");
  }
}
