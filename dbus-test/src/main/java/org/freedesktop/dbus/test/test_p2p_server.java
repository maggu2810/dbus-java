/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus.test;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.freedesktop.dbus.DirectConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt16;

public class test_p2p_server implements TestRemoteInterface {
    @Override
    public int[][] teststructstruct(final TestStruct3 in) {
        final List<List<Integer>> lli = in.b;
        final int[][] out = new int[lli.size()][];
        for (int j = 0; j < out.length; j++) {
            out[j] = new int[lli.get(j).size()];
            for (int k = 0; k < out[j].length; k++) {
                out[j][k] = lli.get(j).get(k);
            }
        }
        return out;
    }

    @Override
    public String getNameAndThrow() {
        return getName();
    }

    @Override
    public String getName() {
        System.out.println("getName called");
        return "Peer2Peer Server";
    }

    @Override
    public <T> int frobnicate(final List<Long> n, final Map<String, Map<UInt16, Short>> m, final T v) {
        return 3;
    }

    @Override
    public void throwme() throws TestException {
        System.out.println("throwme called");
        throw new TestException("BOO");
    }

    @Override
    public void waitawhile() {
        return;
    }

    @Override
    public int overload() {
        return 1;
    }

    @Override
    public void sig(final Type[] s) {
    }

    @Override
    public void newpathtest(final Path p) {
    }

    @Override
    public void reg13291(final byte[] as, final byte[] bs) {
    }

    @Override
    public Path pathrv(final Path a) {
        return a;
    }

    @Override
    public List<Path> pathlistrv(final List<Path> a) {
        return a;
    }

    @Override
    public Map<Path, Path> pathmaprv(final Map<Path, Path> a) {
        return a;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public float testfloat(final float[] f) {
        System.out.println("got float: " + Arrays.toString(f));
        return f[0];
    }

    public static void main(final String[] args) throws Exception {
        final String address = DirectConnection.createDynamicSession();
        // String address = "tcp:host=localhost,port=12344,guid="+Transport.genGUID();
        final PrintWriter w = new PrintWriter(new FileOutputStream("address"));
        w.println(address);
        w.flush();
        w.close();
        final DirectConnection dc = new DirectConnection(address + ",listen=true");
        System.out.println("Connected");
        dc.exportObject("/Test", new test_p2p_server());
    }
}
