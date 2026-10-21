// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util.containers;

import java.util.LinkedHashMap;
import java.util.Map;

/** A bounded LRU cache. IntelliJ splits it into a protected and a probational part; one LRU map is enough here. */
public class SLRUMap<K, V> {
  private final Map<K, V> map;

  public SLRUMap(int protectedQueueSize, int probationalQueueSize) {
    int capacity = protectedQueueSize + probationalQueueSize;
    map = new LinkedHashMap<>(16, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
      }
    };
  }

  public V get(K key) {
    return map.get(key);
  }

  public void put(K key, V value) {
    map.put(key, value);
  }

  public boolean remove(K key) {
    return map.remove(key) != null;
  }

  public void clear() {
    map.clear();
  }
}
