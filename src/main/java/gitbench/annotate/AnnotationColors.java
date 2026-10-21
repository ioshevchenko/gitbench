package gitbench.annotate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The background of annotated lines by the age of their commit, as the IntelliJ AnnotationsSettings: the anchor colors
 * VCS_ANNOTATIONS_COLOR_1..5 with four linear colors between each two. A color is 0xRRGGBB, so the model needs no SWT.
 */
public final class AnnotationColors {
  private static final int COLORS_BETWEEN_ANCHORS = 4;
  private static final List<Integer> LIGHT_ANCHORS = List.of(0xA1BEF7, 0xC9DAFB, 0xDEE8FC, 0xEDF2FA, 0xF7F8FA);
  private static final List<Integer> DARK_ANCHORS = List.of(0x2F5194, 0x2D416B, 0x283754, 0x242D42, 0x22252E);

  private AnnotationColors() {
  }

  /** ColorGenerator.generateLinearColorSequence over the anchors: 21 colors from the newest to the oldest. */
  public static List<Integer> palette(boolean dark) {
    List<Integer> anchors = dark ? DARK_ANCHORS : LIGHT_ANCHORS;
    List<Integer> result = new ArrayList<>();
    result.add(anchors.get(0));
    for (int i = 0; i + 1 < anchors.size(); i++) {
      int from = anchors.get(i);
      int to = anchors.get(i + 1);
      for (int step = 1; step <= COLORS_BETWEEN_ANCHORS + 1; step++) {
        float ratio = (float)step / (COLORS_BETWEEN_ANCHORS + 1);
        result.add(mix(from >> 16, to >> 16, ratio) << 16
                   | mix(from >> 8 & 0xFF, to >> 8 & 0xFF, ratio) << 8
                   | mix(from & 0xFF, to & 0xFF, ratio));
      }
    }
    return result;
  }

  private static int mix(int from, int to, float ratio) {
    return Math.round(from + (to - from) * ratio);
  }

  /**
   * The color of each commit: the dates of the commits from the newest, commits of one date share a color, and the
   * index-th date gets {@code palette[size * index / count]}. Fewer than two commits get no colors.
   */
  public static Map<String, Integer> byCommit(Blame blame, boolean dark) {
    java.util.Set<String> shown = new java.util.HashSet<>(blame.lineHashes());
    List<BlameCommit> commits = blame.commits().values().stream()
      .filter(commit -> !Blame.UNCOMMITTED.equals(commit.hash()) && shown.contains(commit.hash()))
      .toList();
    if (commits.size() < 2) {
      return Map.of();
    }
    TreeMap<Instant, List<String>> byDate = new TreeMap<>(Comparator.reverseOrder());
    for (BlameCommit commit : commits) {
      byDate.computeIfAbsent(commit.authorTime(), date -> new ArrayList<>()).add(commit.hash());
    }
    List<Integer> palette = palette(dark);
    Map<String, Integer> result = new HashMap<>();
    int index = 0;
    for (List<String> hashes : byDate.values()) {
      int color = palette.get(palette.size() * index / byDate.size());
      hashes.forEach(hash -> result.put(hash, color));
      index++;
    }
    return result;
  }
}
