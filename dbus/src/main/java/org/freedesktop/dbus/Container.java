/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;

/**
 * This class is the super class of both Structs and Tuples
 * and holds common methods.
 */
abstract class Container {
    private static Map<Type, Type[]> typecache = new HashMap<Type, Type[]>();

    static void putTypeCache(final Type k, final Type[] v) {
        typecache.put(k, v);
    }

    static Type[] getTypeCache(final Type k) {
        return typecache.get(k);
    }

    private Object[] parameters = null;

    public Container() {
    }

    private void setup() {
        final Field[] fs = getClass().getDeclaredFields();
        final Object[] args = new Object[fs.length];

        int diff = 0;
        for (final Field f : fs) {
            final Position p = f.getAnnotation(Position.class);
            if (null == p) {
                diff++;
                continue;
            }
            try {
                args[p.value()] = f.get(this);
            } catch (final IllegalAccessException IAe) {
            }
        }

        this.parameters = new Object[args.length - diff];
        System.arraycopy(args, 0, parameters, 0, parameters.length);
    }

    /**
     * Returns the struct contents in order.
     *
     * @throws DBusException If there is a problem doing this.
     */
    public final Object[] getParameters() {
        if (null != parameters) {
            return parameters;
        }
        setup();
        return parameters;
    }

    /** Returns this struct as a string. */
    @Override
    public final String toString() {
        String s = getClass().getName() + "<";
        if (null == parameters) {
            setup();
        }
        if (0 == parameters.length) {
            return s + ">";
        }
        for (final Object o : parameters) {
            s += o + ", ";
        }
        return s.replaceAll(", $", ">");
    }

    @Override
    public final boolean equals(final Object other) {
        if (other instanceof Container) {
            final Container that = (Container) other;
            if (this.getClass().equals(that.getClass())) {
                return Arrays.equals(this.getParameters(), that.getParameters());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
