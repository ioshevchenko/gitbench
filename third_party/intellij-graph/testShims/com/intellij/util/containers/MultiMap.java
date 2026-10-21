// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util.containers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MultiMap<K, V> {
  private final Map<K, List<V>> map = new LinkedHashMap<>();

  public static <K, V> MultiMap<K, V> create() {
    return new MultiMap<>();
  }

  public void putValue(K key, V value) {
    map.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
  }

  public Collection<V> get(K key) {
    List<V> values = map.get(key);
    return values == null ? Collections.emptyList() : values;
  }

  public boolean remove(K key, V value) {
    List<V> values = map.get(key);
    if (values == null || !values.remove(value)) {
      return false;
    }
    if (values.isEmpty()) {
      map.remove(key);
    }
    return true;
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }
}
