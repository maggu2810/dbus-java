/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus;

import org.freedesktop.dbus.exceptions.DBusException;

class InternalSignal extends DBusSignal {
    public InternalSignal(final String source, final String objectpath, final String name, final String iface,
            final String sig, final long serial, final Object... parameters) throws DBusException {
        super(objectpath, iface, name, sig, parameters);
        this.serial = serial;
    }
}
