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
import java.util.Map;

import org.freedesktop.DBus.Description;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusMemberName;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * A sample signal with two parameters
 */
@Description("Test interface containing signals")
public interface TestSignalInterface extends DBusInterface {
    @Description("Test basic signal")
    public static class TestSignal extends DBusSignal {
        public final String value;
        public final UInt32 number;

        /**
         * Create a signal.
         */
        public TestSignal(final String path, final String value, final UInt32 number) throws DBusException {
            super(path, value, number);
            this.value = value;
            this.number = number;
        }
    }

    public static class StringSignal extends DBusSignal {
        public final String aoeu;

        public StringSignal(final String path, final String aoeu) throws DBusException {
            super(path, aoeu);
            this.aoeu = aoeu;
        }
    }

    public static class EmptySignal extends DBusSignal {
        public EmptySignal(final String path) throws DBusException {
            super(path);
        }
    }

    @Description("Test signal with arrays")
    public static class TestArraySignal extends DBusSignal {
        public final List<TestStruct2> v;
        public final Map<UInt32, TestStruct2> m;

        public TestArraySignal(final String path, final List<TestStruct2> v, final Map<UInt32, TestStruct2> m)
                throws DBusException {
            super(path, v, m);
            this.v = v;
            this.m = m;
        }
    }

    @Description("Test signal sending an object path")
    @DBusMemberName("TestSignalObject")
    public static class TestObjectSignal extends DBusSignal {
        public final DBusInterface otherpath;

        public TestObjectSignal(final String path, final DBusInterface otherpath) throws DBusException {
            super(path, otherpath);
            this.otherpath = otherpath;
        }
    }

    public static class TestPathSignal extends DBusSignal {
        public final Path otherpath;
        public final List<Path> pathlist;
        public final Map<Path, Path> pathmap;

        public TestPathSignal(final String path, final Path otherpath, final List<Path> pathlist,
                final Map<Path, Path> pathmap) throws DBusException {
            super(path, otherpath, pathlist, pathmap);
            this.otherpath = otherpath;
            this.pathlist = pathlist;
            this.pathmap = pathmap;
        }
    }
}
