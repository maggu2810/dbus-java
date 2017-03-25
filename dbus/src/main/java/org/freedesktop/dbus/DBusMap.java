/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

class DBusMap<K, V> implements Map<K, V> {
    Object[][] entries;

    public DBusMap(final Object[][] entries) {
        this.entries = entries;
    }

    class Entry implements Map.Entry<K, V>, Comparable<Entry> {
        private final int entry;

        public Entry(final int i) {
            this.entry = i;
        }

        @Override
        public boolean equals(final Object o) {
            if (null == o) {
                return false;
            }
            if (!(o instanceof DBusMap.Entry)) {
                return false;
            }
            return this.entry == ((Entry) o).entry;
        }

        @SuppressWarnings("unchecked")
        public K getKey() {
            return (K) entries[entry][0];
        }

        @SuppressWarnings("unchecked")
        public V getValue() {
            return (V) entries[entry][1];
        }

        @Override
        public int hashCode() {
            return entries[entry][0].hashCode();
        }

        public V setValue(final V value) {
            throw new UnsupportedOperationException();
        }

        public int compareTo(final Entry e) {
            return entry - e.entry;
        }
    }

    public void clear() {
        throw new UnsupportedOperationException();
    }

    public boolean containsKey(final Object key) {
        for (int i = 0; i < entries.length; i++) {
            if (key == entries[i][0] || key != null && key.equals(entries[i][0])) {
                return true;
            }
        }
        return false;
    }

    public boolean containsValue(final Object value) {
        for (int i = 0; i < entries.length; i++) {
            if (value == entries[i][1] || value != null && value.equals(entries[i][1])) {
                return true;
            }
        }
        return false;
    }

    public Set<Map.Entry<K, V>> entrySet() {
        final Set<Map.Entry<K, V>> s = new TreeSet<Map.Entry<K, V>>();
        for (int i = 0; i < entries.length; i++) {
            s.add(new Entry(i));
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    public V get(final Object key) {
        for (int i = 0; i < entries.length; i++) {
            if (key == entries[i][0] || key != null && key.equals(entries[i][0])) {
                return (V) entries[i][1];
            }
        }
        return null;
    }

    public boolean isEmpty() {
        return entries.length == 0;
    }

    @SuppressWarnings("unchecked")
    public Set<K> keySet() {
        final Set<K> s = new TreeSet<K>();
        for (final Object[] entry : entries) {
            s.add((K) entry[0]);
        }
        return s;
    }

    public V put(final K key, final V value) {
        throw new UnsupportedOperationException();
    }

    public void putAll(final Map<? extends K, ? extends V> t) {
        throw new UnsupportedOperationException();
    }

    public V remove(final Object key) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return entries.length;
    }

    @SuppressWarnings("unchecked")
    public Collection<V> values() {
        final List<V> l = new Vector<V>();
        for (final Object[] entry : entries) {
            l.add((V) entry[1]);
        }
        return l;
    }

    @Override
    public int hashCode() {
        return Arrays.deepHashCode(entries);
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(final Object o) {
        if (null == o) {
            return false;
        }
        if (!(o instanceof Map)) {
            return false;
        }
        return ((Map<K, V>) o).entrySet().equals(entrySet());
    }

    @Override
    public String toString() {
        String s = "{ ";
        for (int i = 0; i < entries.length; i++) {
            s += entries[i][0] + " => " + entries[i][1] + ",";
        }
        return s.replaceAll(".$", " }");
    }
}
