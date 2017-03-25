/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus;

import static org.freedesktop.dbus.Gettext._;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import cx.ath.matthew.debug.Debug;

public class BusAddress {
    private final String type;
    private final Map<String, String> parameters;

    public BusAddress(final String address) throws ParseException {
        if (null == address || "".equals(address)) {
            throw new ParseException(_("Bus address is blank"), 0);
        }
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "Parsing bus address: " + address);
        }
        final String[] ss = address.split(":", 2);
        if (ss.length < 2) {
            throw new ParseException(_("Bus address is invalid: ") + address, 0);
        }
        type = ss[0];
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "Transport type: " + type);
        }
        final String[] ps = ss[1].split(",");
        parameters = new HashMap<String, String>();
        for (final String p : ps) {
            final String[] kv = p.split("=", 2);
            parameters.put(kv[0], kv[1]);
        }
        if (Debug.debug) {
            Debug.print(Debug.VERBOSE, "Transport options: " + parameters);
        }
    }

    public String getType() {
        return type;
    }

    public String getParameter(final String key) {
        return parameters.get(key);
    }

    @Override
    public String toString() {
        return type + ": " + parameters;
    }
}
