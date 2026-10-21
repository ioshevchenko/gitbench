package gitbench.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageHistoryTest {
  @Test
  void newestFirst() {
    MessageHistory history = new MessageHistory(25);
    history.add("one");
    history.add("two");
    assertThat(history.recent()).containsExactly("two", "one");
  }

  @Test
  void duplicateMovesToTop() {
    MessageHistory history = new MessageHistory(25);
    history.add("one");
    history.add("two");
    history.add("one");
    assertThat(history.recent()).containsExactly("one", "two");
  }

  @Test
  void trimsToMax() {
    MessageHistory history = new MessageHistory(25);
    for (int i = 0; i < 30; i++) {
      history.add("m" + i);
    }
    assertThat(history.recent()).hasSize(25).first().isEqualTo("m29");
  }

  @Test
  void ignoresBlank() {
    MessageHistory history = new MessageHistory(25);
    history.add("  ");
    assertThat(history.recent()).isEmpty();
  }

  @Test
  void roundTripsMultilineMessages() {
    MessageHistory history = new MessageHistory(25);
    history.add("subject\n\nbody | with pipe");
    history.add("second \\ backslash");
    assertThat(MessageHistory.deserialize(history.serialize(), 25).recent())
      .containsExactly("second \\ backslash", "subject\n\nbody | with pipe");
  }

  @Test
  void deserializeEmptyGivesEmptyHistory() {
    assertThat(MessageHistory.deserialize("", 25).recent()).isEmpty();
  }
}
