/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus.bin;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

class StructStruct {
    public static Map<StructStruct, Type[]> fillPackages(final Map<StructStruct, Type[]> structs, final String pack) {
        final Map<StructStruct, Type[]> newmap = new HashMap<StructStruct, Type[]>();
        for (final StructStruct ss : structs.keySet()) {
            final Type[] type = structs.get(ss);
            if (null == ss.pack) {
                ss.pack = pack;
            }
            newmap.put(ss, type);
        }
        return newmap;
    }

    public String name;
    public String pack;

    public StructStruct(final String name) {
        this.name = name;
    }

    public StructStruct(final String name, final String pack) {
        this.name = name;
        this.pack = pack;
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof StructStruct)) {
            return false;
        }
        if (!name.equals(((StructStruct) o).name)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "<" + name + ", " + pack + ">";
    }
}
