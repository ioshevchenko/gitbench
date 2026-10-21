package gitbench.log;

/** The modes of "Reset Current Branch to Here", with the texts of the IntelliJ Git Reset dialog. */
public enum ResetMode {
  SOFT("Soft", "Files won't change, differences will be staged for commit."),
  MIXED("Mixed", "Files won't change, differences won't be staged."),
  HARD("Hard", "Files will be reverted to the state of the selected commit. Warning: any local changes will be lost."),
  KEEP("Keep", "Files will be reverted to the state of the selected commit, but local changes will be kept intact.");

  private final String title;
  private final String description;

  ResetMode(String title, String description) {
    this.title = title;
    this.description = description;
  }

  public String title() {
    return title;
  }

  public String description() {
    return description;
  }

  String option() {
    return "--" + name().toLowerCase(java.util.Locale.ROOT);
  }
}
