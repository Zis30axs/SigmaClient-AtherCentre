package com.elfmcys.yesstevemodel.util.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OrderedStringMap<K, V> extends LinkedHashMap<K, V> {
    public OrderedStringMap(K[] keys, V[] values) {
        for (int i = 0; i < keys.length && i < values.length; i++) {
            put(keys[i], values[i]);
        }
    }

    /**
     * Upstream builds these as {@code new OrderedStringMap<>(new Object2ObjectArrayMap<>(map))};
     * this port keeps LinkedHashMap as the backing store, so any insertion-ordered map works.
     */
    public OrderedStringMap(Map<? extends K, ? extends V> source) {
        super(source);
    }

    public OrderedStringMap() {
        super();
    }

    public List<K> getKeys() {
        return new ArrayList<>(keySet());
    }

    public List<V> getValuesList() {
        return new ArrayList<>(values());
    }

    public K getKeyAt(int index) {
        int i = 0;
        for (K key : keySet()) {
            if (i++ == index) return key;
        }
        throw new IndexOutOfBoundsException("Index: " + index);
    }

    public V getValueAt(int index) {
        int i = 0;
        for (V value : values()) {
            if (i++ == index) return value;
        }
        throw new IndexOutOfBoundsException("Index: " + index);
    }
}