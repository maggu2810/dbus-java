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

public class ProfilerInstance implements Profiler {
    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public void array(final int[] v) {
        return;
    }

    @Override
    public void stringarray(final String[] v) {
        return;
    }

    @Override
    public void map(final Map<String, String> m) {
        return;
    }

    @Override
    public void list(final List<String> l) {
        return;
    }

    @Override
    public void bytes(final byte[] b) {
        return;
    }

    @Override
    public void struct(final ProfileStruct ps) {
        return;
    }

    @Override
    public void string(final String s) {
        return;
    }

    @Override
    public void NoReply() {
        return;
    }

    @Override
    public void Pong() {
        return;
    }
}
