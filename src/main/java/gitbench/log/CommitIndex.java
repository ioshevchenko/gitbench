package gitbench.log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Gives every commit hash a small int id, in the order the hashes are added. The graph works on these ids. */
public final class CommitIndex {
  private final Map<String, Integer> indexes = new HashMap<>();
  private final List<String> hashes = new ArrayList<>();

  /** Returns the id of {@code hash}, and adds it when it is new. */
  int add(String hash) {
    Integer index = indexes.get(hash);
    if (index != null) {
      return index;
    }
    indexes.put(hash, hashes.size());
    hashes.add(hash);
    return hashes.size() - 1;
  }

  /** Returns the id, or -1 for an unknown hash. */
  public int indexOf(String hash) {
    return indexes.getOrDefault(hash, -1);
  }

  public String hashOf(int index) {
    return hashes.get(index);
  }

  public int size() {
    return hashes.size();
  }
}
