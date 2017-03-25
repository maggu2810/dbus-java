/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus.test;

import java.util.List;

import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.exceptions.DBusException;

public final class TestStruct3 extends Struct {
    @Position(0)
    public final TestStruct2 a;
    @Position(1)
    public final List<List<Integer>> b;

    public TestStruct3(final TestStruct2 a, final List<List<Integer>> b) throws DBusException {
        this.a = a;
        this.b = b;
    }
}
