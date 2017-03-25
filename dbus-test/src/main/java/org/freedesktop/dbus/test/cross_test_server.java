/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusSigHandler;
import org.freedesktop.dbus.UInt16;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

public class cross_test_server
        implements DBus.Binding.Tests, DBus.Binding.SingleTests, DBusSigHandler<DBus.Binding.TestClient.Trigger> {
    private final DBusConnection conn;
    boolean run = true;
    private final Set<String> done = new TreeSet<>();
    private final Set<String> notdone = new TreeSet<>();
    {
        notdone.add("org.freedesktop.DBus.Binding.Tests.Identity");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityByte");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityBool");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityInt16");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt16");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityInt32");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt32");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityInt64");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt64");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityDouble");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityString");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityArray");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityByteArray");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityBoolArray");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityInt16Array");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt16Array");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityInt32Array");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt32Array");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityInt64Array");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt64Array");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityDoubleArray");
        notdone.add("org.freedesktop.DBus.Binding.Tests.IdentityStringArray");
        notdone.add("org.freedesktop.DBus.Binding.Tests.Sum");
        notdone.add("org.freedesktop.DBus.Binding.SingleTests.Sum");
        notdone.add("org.freedesktop.DBus.Binding.Tests.InvertMapping");
        notdone.add("org.freedesktop.DBus.Binding.Tests.DeStruct");
        notdone.add("org.freedesktop.DBus.Binding.Tests.Primitize");
        notdone.add("org.freedesktop.DBus.Binding.Tests.Invert");
        notdone.add("org.freedesktop.DBus.Binding.Tests.Trigger");
        notdone.add("org.freedesktop.DBus.Binding.Tests.Exit");
        notdone.add("org.freedesktop.DBus.Binding.TestClient.Trigger");
    }

    public cross_test_server(final DBusConnection conn) {
        this.conn = conn;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    @DBus.Description("Returns whatever it is passed")
    public <T> Variant<T> Identity(final Variant<T> input) {
        done.add("org.freedesktop.DBus.Binding.Tests.Identity");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.Identity");
        return new Variant(input.getValue());
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public byte IdentityByte(final byte input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityByte");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityByte");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public boolean IdentityBool(final boolean input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityBool");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityBool");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public short IdentityInt16(final short input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityInt16");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityInt16");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public UInt16 IdentityUInt16(final UInt16 input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt16");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityUInt16");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public int IdentityInt32(final int input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityInt32");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityInt32");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public UInt32 IdentityUInt32(final UInt32 input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt32");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityUInt32");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public long IdentityInt64(final long input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityInt64");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityInt64");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public UInt64 IdentityUInt64(final UInt64 input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt64");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityUInt64");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public double IdentityDouble(final double input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityDouble");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityDouble");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public String IdentityString(final String input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityString");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityString");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public <T> Variant<T>[] IdentityArray(final Variant<T>[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityArray");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityArray");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public byte[] IdentityByteArray(final byte[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityByteArray");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityByteArray");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public boolean[] IdentityBoolArray(final boolean[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityBoolArray");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityBoolArray");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public short[] IdentityInt16Array(final short[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityInt16Array");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityInt16Array");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public UInt16[] IdentityUInt16Array(final UInt16[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt16Array");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityUInt16Array");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public int[] IdentityInt32Array(final int[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityInt32Array");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityInt32Array");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public UInt32[] IdentityUInt32Array(final UInt32[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt32Array");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityUInt32Array");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public long[] IdentityInt64Array(final long[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityInt64Array");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityInt64Array");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public UInt64[] IdentityUInt64Array(final UInt64[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityUInt64Array");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityUInt64Array");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public double[] IdentityDoubleArray(final double[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityDoubleArray");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityDoubleArray");
        return input;
    }

    @Override
    @DBus.Description("Returns whatever it is passed")
    public String[] IdentityStringArray(final String[] input) {
        done.add("org.freedesktop.DBus.Binding.Tests.IdentityStringArray");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.IdentityStringArray");
        return input;
    }

    @Override
    @DBus.Description("Returns the sum of the values in the input list")
    public long Sum(final int[] a) {
        done.add("org.freedesktop.DBus.Binding.Tests.Sum");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.Sum");
        long sum = 0;
        for (final int b : a) {
            sum += b;
        }
        return sum;
    }

    @Override
    @DBus.Description("Returns the sum of the values in the input list")
    public UInt32 Sum(final byte[] a) {
        done.add("org.freedesktop.DBus.Binding.SingleTests.Sum");
        notdone.remove("org.freedesktop.DBus.Binding.SingleTests.Sum");
        int sum = 0;
        for (final byte b : a) {
            sum += b < 0 ? b + 256 : b;
        }
        return new UInt32(sum % (UInt32.MAX_VALUE + 1));
    }

    @Override
    @DBus.Description("Given a map of A => B, should return a map of B => a list of all the As which mapped to B")
    public Map<String, List<String>> InvertMapping(final Map<String, String> a) {
        done.add("org.freedesktop.DBus.Binding.Tests.InvertMapping");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.InvertMapping");
        final HashMap<String, List<String>> m = new HashMap<>();
        for (final String s : a.keySet()) {
            final String b = a.get(s);
            List<String> l = m.get(b);
            if (null == l) {
                l = new Vector<>();
                m.put(b, l);
            }
            l.add(s);
        }
        return m;
    }

    @Override
    @DBus.Description("This method returns the contents of a struct as separate values")
    public DBus.Binding.Triplet<String, UInt32, Short> DeStruct(final DBus.Binding.TestStruct a) {
        done.add("org.freedesktop.DBus.Binding.Tests.DeStruct");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.DeStruct");
        return new DBus.Binding.Triplet<>(a.a, a.b, a.c);
    }

    @Override
    @DBus.Description("Given any compound type as a variant, return all the primitive types recursively contained within as an array of variants")
    @SuppressWarnings("unchecked")
    public List<Variant<Object>> Primitize(final Variant<Object> a) {
        done.add("org.freedesktop.DBus.Binding.Tests.Primitize");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.Primitize");
        return cross_test_client.PrimitizeRecurse(a.getValue(), a.getType());
    }

    @Override
    @DBus.Description("inverts it's input")
    public boolean Invert(final boolean a) {
        done.add("org.freedesktop.DBus.Binding.Tests.Invert");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.Invert");
        return !a;
    }

    @Override
    @DBus.Description("triggers sending of a signal from the supplied object with the given parameter")
    public void Trigger(final String a, final UInt64 b) {
        done.add("org.freedesktop.DBus.Binding.Tests.Trigger");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.Trigger");
        try {
            conn.sendSignal(new DBus.Binding.TestSignals.Triggered(a, b));
        } catch (final DBusException DBe) {
            throw new DBusExecutionException(DBe.getMessage());
        }
    }

    @Override
    public void Exit() {
        done.add("org.freedesktop.DBus.Binding.Tests.Exit");
        notdone.remove("org.freedesktop.DBus.Binding.Tests.Exit");
        run = false;
        synchronized (this) {
            notifyAll();
        }
    }

    @Override
    public void handle(final DBus.Binding.TestClient.Trigger t) {
        done.add("org.freedesktop.DBus.Binding.TestClient.Trigger");
        notdone.remove("org.freedesktop.DBus.Binding.TestClient.Trigger");
        try {
            final DBus.Binding.TestClient cb = conn.getRemoteObject(t.getSource(), "/Test",
                    DBus.Binding.TestClient.class);
            cb.Response(t.a, t.b);
        } catch (final DBusException DBe) {
            throw new DBusExecutionException(DBe.getMessage());
        }
    }

    public static void main(final String[] args) {
        try {
            final DBusConnection conn = DBusConnection.getConnection(DBusConnection.SESSION);
            conn.requestBusName("org.freedesktop.DBus.Binding.TestServer");
            final cross_test_server cts = new cross_test_server(conn);
            conn.addSigHandler(DBus.Binding.TestClient.Trigger.class, cts);
            conn.exportObject("/Test", cts);
            synchronized (cts) {
                while (cts.run) {
                    try {
                        cts.wait();
                    } catch (final InterruptedException Ie) {
                    }
                }
            }
            for (final String s : cts.done) {
                System.out.println(s + " ok");
            }
            for (final String s : cts.notdone) {
                System.out.println(s + " untested");
            }
            conn.disconnect();
            System.exit(0);
        } catch (final DBusException DBe) {
            DBe.printStackTrace();
            System.exit(1);
        }
    }
}
