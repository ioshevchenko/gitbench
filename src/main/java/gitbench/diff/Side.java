package gitbench.diff;

/** A side of a two-side diff. */
public enum Side {
  LEFT, RIGHT;

  public Side other() {
    return this == LEFT ? RIGHT : LEFT;
  }

  public <T> T select(T left, T right) {
    return this == LEFT ? left : right;
  }

  public int select(int left, int right) {
    return this == LEFT ? left : right;
  }
}
