// Gitbench shim: a minimal replacement for the IntelliJ Platform class with the same name (Apache 2.0).
package com.intellij.util.containers;

import com.intellij.openapi.util.Condition;
import com.intellij.util.Function;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Only the methods the ported commit graph uses, with the IntelliJ semantics. */
public final class ContainerUtil {
  private ContainerUtil() {
  }

  public static <T> T getLastItem(List<? extends T> list) {
    return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
  }

  public static <T, V> List<V> map(Iterable<? extends T> iterable, Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<>();
    for (T t : iterable) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  public static <T, V> List<V> map(T[] array, Function<? super T, ? extends V> mapping) {
    return map(Arrays.asList(array), mapping);
  }

  public static <T, V> List<V> mapNotNull(Iterable<? extends T> iterable, Function<? super T, ? extends V> mapping) {
    List<V> result = new ArrayList<>();
    for (T t : iterable) {
      V v = mapping.fun(t);
      if (v != null) {
        result.add(v);
      }
    }
    return result;
  }

  public static <T, V> List<V> mapNotNull(T[] array, Function<? super T, ? extends V> mapping) {
    return mapNotNull(Arrays.asList(array), mapping);
  }

  public static <T, V> Set<V> map2Set(Collection<? extends T> collection, Function<? super T, ? extends V> mapping) {
    Set<V> result = new HashSet<>();
    for (T t : collection) {
      result.add(mapping.fun(t));
    }
    return result;
  }

  public static <T, V> Set<V> map2SetNotNull(Collection<? extends T> collection, Function<? super T, ? extends V> mapping) {
    Set<V> result = new HashSet<>();
    for (T t : collection) {
      V v = mapping.fun(t);
      if (v != null) {
        result.add(v);
      }
    }
    return result;
  }

  public static <T> List<T> filter(Collection<? extends T> collection, Condition<? super T> condition) {
    List<T> result = new ArrayList<>();
    for (T t : collection) {
      if (condition.value(t)) {
        result.add(t);
      }
    }
    return result;
  }

  public static <T> List<T> filter(T[] array, Condition<? super T> condition) {
    return filter(Arrays.asList(array), condition);
  }

  public static <T> void sort(List<T> list, Comparator<? super T> comparator) {
    list.sort(comparator);
  }

  public static <T extends Comparable<? super T>> List<T> sorted(Collection<? extends T> collection) {
    List<T> result = new ArrayList<>(collection);
    Collections.sort(result);
    return result;
  }

  public static <T> List<T> sorted(Collection<? extends T> collection, Comparator<? super T> comparator) {
    List<T> result = new ArrayList<>(collection);
    result.sort(comparator);
    return result;
  }

  /** Returns a reversed copy. IntelliJ returns a reversed view; the callers only read it. */
  public static <T> List<T> reverse(List<? extends T> list) {
    List<T> result = new ArrayList<>(list);
    Collections.reverse(result);
    return result;
  }

  public static <T> Set<T> union(Set<? extends T> a, Set<? extends T> b) {
    Set<T> result = new LinkedHashSet<>(a);
    result.addAll(b);
    return result;
  }

  public static <T> Collection<T> union(Collection<? extends T> a, Collection<? extends T> b) {
    List<T> result = new ArrayList<>(a);
    result.addAll(b);
    return result;
  }

  public static <T> Collection<T> intersection(Collection<? extends T> a, Collection<? extends T> b) {
    List<T> result = new ArrayList<>();
    for (T t : a) {
      if (b.contains(t)) {
        result.add(t);
      }
    }
    return result;
  }

  public static <T> List<T> concat(List<? extends T> a, List<? extends T> b) {
    List<T> result = new ArrayList<>(a.size() + b.size());
    result.addAll(a);
    result.addAll(b);
    return result;
  }

  @SafeVarargs
  public static <T> LinkedList<T> newLinkedList(T... elements) {
    return new LinkedList<>(Arrays.asList(elements));
  }

  public static <K, V> Map<V, K> reverseMap(Map<K, V> map) {
    Map<V, K> result = new HashMap<>();
    for (Map.Entry<K, V> entry : map.entrySet()) {
      result.put(entry.getValue(), entry.getKey());
    }
    return result;
  }
}
