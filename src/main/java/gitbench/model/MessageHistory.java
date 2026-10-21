package gitbench.model;

import java.util.ArrayList;
import java.util.List;

/** The recent commit messages, newest first, without duplicates. */
public final class MessageHistory {
  private final int max;
  private final List<String> messages = new ArrayList<>();

  public MessageHistory(int max) {
    this.max = max;
  }

  public void add(String message) {
    if (message == null || message.isBlank()) {
      return;
    }
    messages.remove(message);
    messages.add(0, message);
    while (messages.size() > max) {
      messages.remove(messages.size() - 1);
    }
  }

  public List<String> recent() {
    return List.copyOf(messages);
  }

  /** Writes one message per line. Backslashes and line breaks inside a message are escaped. */
  public String serialize() {
    StringBuilder out = new StringBuilder();
    for (String message : messages) {
      if (!out.isEmpty()) {
        out.append('\n');
      }
      out.append(message.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n"));
    }
    return out.toString();
  }

  public static MessageHistory deserialize(String text, int max) {
    MessageHistory history = new MessageHistory(max);
    if (text == null || text.isEmpty()) {
      return history;
    }
    String[] lines = text.split("\n");
    for (int i = lines.length - 1; i >= 0; i--) {
      history.add(unescape(lines[i]));
    }
    return history;
  }

  private static String unescape(String line) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '\\' && i + 1 < line.length()) {
        char next = line.charAt(++i);
        out.append(next == 'n' ? '\n' : next == 'r' ? '\r' : next);
      }
      else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
