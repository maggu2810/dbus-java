/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus.bin;

import static org.freedesktop.dbus.Gettext.localize;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import org.freedesktop.DBus;
import org.freedesktop.dbus.AbstractConnection;
import org.freedesktop.dbus.BusAddress;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.DirectConnection;
import org.freedesktop.dbus.Error;
import org.freedesktop.dbus.Marshalling;
import org.freedesktop.dbus.Message;
import org.freedesktop.dbus.MessageReader;
import org.freedesktop.dbus.MessageWriter;
import org.freedesktop.dbus.MethodCall;
import org.freedesktop.dbus.MethodReturn;
import org.freedesktop.dbus.Transport;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.FatalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cx.ath.matthew.unix.UnixServerSocket;
import cx.ath.matthew.unix.UnixSocket;
import cx.ath.matthew.unix.UnixSocketAddress;

/**
 * A replacement DBusDaemon
 */
public class DBusDaemon extends Thread {
    private static final Logger LOGGER = LoggerFactory.getLogger(DBusDaemon.class);

    public static final int QUEUE_POLL_WAIT = 500;

    static class Connstruct {
        public UnixSocket usock;
        public Socket tsock;
        public MessageReader min;
        public MessageWriter mout;
        public String unique;

        public Connstruct(final UnixSocket sock) {
            this.usock = sock;
            min = new MessageReader(sock.getInputStream());
            mout = new MessageWriter(sock.getOutputStream());
        }

        public Connstruct(final Socket sock) throws IOException {
            this.tsock = sock;
            min = new MessageReader(sock.getInputStream());
            mout = new MessageWriter(sock.getOutputStream());
        }

        @Override
        public String toString() {
            return null == unique ? ":?-?" : unique;
        }
    }

    static class MagicMap<A, B> {
        private final Map<A, LinkedList<B>> m;
        private final LinkedList<A> q;
        private final String name;

        public MagicMap(final String name) {
            m = new HashMap<A, LinkedList<B>>();
            q = new LinkedList<A>();
            this.name = name;
        }

        public A head() {
            return q.getFirst();
        }

        public void putFirst(final A a, final B b) {
            LOGGER.info("<" + name + "> Queueing {" + a + " => " + b + "}");
            if (m.containsKey(a)) {
                m.get(a).add(b);
            } else {
                final LinkedList<B> l = new LinkedList<B>();
                l.add(b);
                m.put(a, l);
            }
            q.addFirst(a);
        }

        public void putLast(final A a, final B b) {
            LOGGER.info("<" + name + "> Queueing {" + a + " => " + b + "}");
            if (m.containsKey(a)) {
                m.get(a).add(b);
            } else {
                final LinkedList<B> l = new LinkedList<B>();
                l.add(b);
                m.put(a, l);
            }
            q.addLast(a);
        }

        public List<B> remove(final A a) {
            LOGGER.info("<" + name + "> Removing {" + a + "}");
            q.remove(a);
            return m.remove(a);
        }

        public int size() {
            return q.size();
        }
    }

    public class DBusServer extends Thread implements DBus, DBus.Introspectable, DBus.Peer {
        public DBusServer() {
            setName("Server");
        }

        public Connstruct c;
        public Message m;

        public boolean isRemote() {
            return false;
        }

        public String Hello() {
            LOGGER.debug("enter");
            synchronized (c) {
                if (null != c.unique) {
                    throw new org.freedesktop.DBus.Error.AccessDenied(localize("Connection has already sent a Hello message"));
                }
                synchronized (unique_lock) {
                    c.unique = ":1." + (++next_unique);
                }
            }
            synchronized (names) {
                names.put(c.unique, c);
            }

            LOGGER.warn("Client " + c.unique + " registered");

            try {
                send(c, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                        "NameAcquired", "s", c.unique));
                final DBusSignal s = new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus",
                        "org.freedesktop.DBus", "NameOwnerChanged", "sss", c.unique, "", c.unique);
                send(null, s);
            } catch (final DBusException DBe) {
                if (AbstractConnection.EXCEPTION_DEBUG) {
                    LOGGER.error("Exception", DBe);
                }
            }
            LOGGER.debug("exit");
            return c.unique;
        }

        public String[] ListNames() {
            LOGGER.debug("enter");
            String[] ns;
            synchronized (names) {
                final Set<String> nss = names.keySet();
                ns = nss.toArray(new String[0]);
            }
            LOGGER.debug("exit");
            return ns;
        }

        public boolean NameHasOwner(final String name) {
            LOGGER.debug("enter");
            boolean rv;
            synchronized (names) {
                rv = names.containsKey(name);
            }
            LOGGER.debug("exit");
            return rv;
        }

        public String GetNameOwner(final String name) {
            LOGGER.debug("enter");
            final Connstruct owner = names.get(name);
            String o;
            if (null == owner) {
                o = "";
            } else {
                o = owner.unique;
            }
            LOGGER.debug("exit");
            return o;
        }

        public UInt32 GetConnectionUnixUser(final String connection_name) {
            LOGGER.debug("enter");
            LOGGER.debug("exit");
            return new UInt32(0);
        }

        public UInt32 StartServiceByName(final String name, final UInt32 flags) {
            LOGGER.debug("enter");
            LOGGER.debug("exit");
            return new UInt32(0);
        }

        public UInt32 RequestName(final String name, final UInt32 flags) {
            LOGGER.debug("enter");

            boolean exists = false;
            synchronized (names) {
                if (!(exists = names.containsKey(name))) {
                    names.put(name, c);
                }
            }

            int rv;
            if (exists) {
                rv = DBus.DBUS_REQUEST_NAME_REPLY_EXISTS;
            } else {
                LOGGER.warn("Client " + c.unique + " acquired name " + name);
                rv = DBus.DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER;
                try {
                    send(c, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                            "NameAcquired", "s", name));
                    send(null, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                            "NameOwnerChanged", "sss", name, "", c.unique));
                } catch (final DBusException DBe) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", DBe);
                    }
                }
            }

            LOGGER.debug("exit");
            return new UInt32(rv);
        }

        public UInt32 ReleaseName(final String name) {
            LOGGER.debug("enter");

            boolean exists = false;
            synchronized (names) {
                if (exists = names.containsKey(name) && names.get(name).equals(c)) {
                    names.remove(name);
                }
            }

            int rv;
            if (!exists) {
                rv = DBus.DBUS_RELEASE_NAME_REPLY_NON_EXISTANT;
            } else {
                LOGGER.warn("Client " + c.unique + " acquired name " + name);
                rv = DBus.DBUS_RELEASE_NAME_REPLY_RELEASED;
                try {
                    send(c, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                            "NameLost", "s", name));
                    send(null, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus", "org.freedesktop.DBus",
                            "NameOwnerChanged", "sss", name, c.unique, ""));
                } catch (final DBusException DBe) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", DBe);
                    }
                }
            }

            LOGGER.debug("exit");
            return new UInt32(rv);
        }

        public void AddMatch(final String matchrule) throws Error.MatchRuleInvalid {
            LOGGER.debug("enter");
            LOGGER.trace("Adding match rule: " + matchrule);
            synchronized (sigrecips) {
                if (!sigrecips.contains(c)) {
                    sigrecips.add(c);
                }
            }
            LOGGER.debug("exit");
            return;
        }

        public void RemoveMatch(final String matchrule) throws Error.MatchRuleInvalid {
            LOGGER.debug("enter");
            LOGGER.trace("Removing match rule: " + matchrule);
            LOGGER.debug("exit");
            return;
        }

        public String[] ListQueuedOwners(final String name) {
            LOGGER.debug("enter");
            LOGGER.debug("exit");
            return new String[0];
        }

        public UInt32 GetConnectionUnixProcessID(final String connection_name) {
            LOGGER.debug("enter");
            LOGGER.debug("exit");
            return new UInt32(0);
        }

        public Byte[] GetConnectionSELinuxSecurityContext(final String a) {
            LOGGER.debug("enter");
            LOGGER.debug("exit");
            return new Byte[0];
        }

        public void ReloadConfig() {
            LOGGER.debug("enter");
            LOGGER.debug("exit");
            return;
        }

        @SuppressWarnings("unchecked")
        private void handleMessage(final Connstruct c, final Message m) throws DBusException {
            LOGGER.debug("enter");
            LOGGER.trace("Handling message " + m + " from " + c.unique);
            if (!(m instanceof MethodCall)) {
                return;
            }
            final Object[] args = m.getParameters();

            final Class<? extends Object>[] cs = new Class[args.length];

            for (int i = 0; i < cs.length; i++) {
                cs[i] = args[i].getClass();
            }

            java.lang.reflect.Method meth = null;
            Object rv = null;

            try {
                meth = DBusServer.class.getMethod(m.getName(), cs);
                try {
                    this.c = c;
                    this.m = m;
                    rv = meth.invoke(dbus_server, args);
                    if (null == rv) {
                        send(c, new MethodReturn("org.freedesktop.DBus", (MethodCall) m, null), true);
                    } else {
                        final String sig = Marshalling.getDBusType(meth.getGenericReturnType())[0];
                        send(c, new MethodReturn("org.freedesktop.DBus", (MethodCall) m, sig, rv), true);
                    }
                } catch (final InvocationTargetException ITe) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", ITe);
                    }
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", ITe.getCause());
                    }
                    send(c, new org.freedesktop.dbus.Error("org.freedesktop.DBus", m, ITe.getCause()));
                } catch (final DBusExecutionException DBEe) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", DBEe);
                    }
                    send(c, new org.freedesktop.dbus.Error("org.freedesktop.DBus", m, DBEe));
                } catch (final Exception e) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", e);
                    }
                    send(c, new org.freedesktop.dbus.Error("org.freedesktop.DBus", c.unique,
                            "org.freedesktop.DBus.Error.GeneralError", m.getSerial(), "s",
                            localize("An error occurred while calling ") + m.getName()));
                }
            } catch (final NoSuchMethodException NSMe) {
                send(c, new org.freedesktop.dbus.Error("org.freedesktop.DBus", c.unique,
                        "org.freedesktop.DBus.Error.UnknownMethod", m.getSerial(), "s",
                        localize("This service does not support ") + m.getName()));
            }

            LOGGER.debug("exit");
        }

        public String Introspect() {
            return "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n"
                    + "\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n" + "<node>\n"
                    + "  <interface name=\"org.freedesktop.DBus.Introspectable\">\n"
                    + "    <method name=\"Introspect\">\n" + "      <arg name=\"data\" direction=\"out\" type=\"s\"/>\n"
                    + "    </method>\n" + "  </interface>\n" + "  <interface name=\"org.freedesktop.DBus\">\n"
                    + "    <method name=\"RequestName\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "      <arg direction=\"in\" type=\"u\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n"
                    + "    </method>\n" + "    <method name=\"ReleaseName\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n"
                    + "    </method>\n" + "    <method name=\"StartServiceByName\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"in\" type=\"u\"/>\n"
                    + "      <arg direction=\"out\" type=\"u\"/>\n" + "    </method>\n"
                    + "    <method name=\"Hello\">\n" + "      <arg direction=\"out\" type=\"s\"/>\n"
                    + "    </method>\n" + "    <method name=\"NameHasOwner\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"b\"/>\n"
                    + "    </method>\n" + "    <method name=\"ListNames\">\n"
                    + "      <arg direction=\"out\" type=\"as\"/>\n" + "    </method>\n"
                    + "    <method name=\"ListActivatableNames\">\n" + "      <arg direction=\"out\" type=\"as\"/>\n"
                    + "    </method>\n" + "    <method name=\"AddMatch\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "    </method>\n"
                    + "    <method name=\"RemoveMatch\">\n" + "      <arg direction=\"in\" type=\"s\"/>\n"
                    + "    </method>\n" + "    <method name=\"GetNameOwner\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"s\"/>\n"
                    + "    </method>\n" + "    <method name=\"ListQueuedOwners\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"as\"/>\n"
                    + "    </method>\n" + "    <method name=\"GetConnectionUnixUser\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n"
                    + "    </method>\n" + "    <method name=\"GetConnectionUnixProcessID\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"u\"/>\n"
                    + "    </method>\n" + "    <method name=\"GetConnectionSELinuxSecurityContext\">\n"
                    + "      <arg direction=\"in\" type=\"s\"/>\n" + "      <arg direction=\"out\" type=\"ay\"/>\n"
                    + "    </method>\n" + "    <method name=\"ReloadConfig\">\n" + "    </method>\n"
                    + "    <signal name=\"NameOwnerChanged\">\n" + "      <arg type=\"s\"/>\n"
                    + "      <arg type=\"s\"/>\n" + "      <arg type=\"s\"/>\n" + "    </signal>\n"
                    + "    <signal name=\"NameLost\">\n" + "      <arg type=\"s\"/>\n" + "    </signal>\n"
                    + "    <signal name=\"NameAcquired\">\n" + "      <arg type=\"s\"/>\n" + "    </signal>\n"
                    + "  </interface>\n" + "</node>";
        }

        public void Ping() {
        }

        @Override
        public void run() {
            LOGGER.debug("enter");
            while (_run) {
                Message m;
                List<WeakReference<Connstruct>> wcs;
                // block on outqueue
                synchronized (localqueue) {
                    while (localqueue.size() == 0) {
                        try {
                            localqueue.wait();
                        } catch (final InterruptedException Ie) {
                        }
                    }
                    m = localqueue.head();
                    wcs = localqueue.remove(m);
                }
                if (null != wcs) {
                    try {
                        for (final WeakReference<Connstruct> wc : wcs) {
                            final Connstruct c = wc.get();
                            if (null != c) {
                                LOGGER.trace("<localqueue> Got message " + m + " from " + c);
                                handleMessage(c, m);
                            }
                        }
                    } catch (final DBusException DBe) {
                        if (AbstractConnection.EXCEPTION_DEBUG) {
                            LOGGER.error("Exception", DBe);
                        }
                    }
                } else {
                    LOGGER.info("Discarding " + m + " connection reaped");
                }
            }
            LOGGER.debug("exit");
        }
    }

    public class Sender extends Thread {
        public Sender() {
            setName("Sender");
        }

        @Override
        public void run() {
            LOGGER.debug("enter");
            while (_run) {

                LOGGER.trace("Acquiring lock on outqueue and blocking for data");
                Message m = null;
                List<WeakReference<Connstruct>> wcs = null;
                // block on outqueue
                synchronized (outqueue) {
                    while (outqueue.size() == 0) {
                        try {
                            outqueue.wait();
                        } catch (final InterruptedException Ie) {
                        }
                    }

                    m = outqueue.head();
                    wcs = outqueue.remove(m);
                }
                if (null != wcs) {
                    for (final WeakReference<Connstruct> wc : wcs) {
                        final Connstruct c = wc.get();
                        if (null != c) {
                            LOGGER.trace("<outqueue> Got message " + m + " for " + c.unique);
                            LOGGER.info("Sending message " + m + " to " + c.unique);
                            try {
                                c.mout.writeMessage(m);
                            } catch (final IOException IOe) {
                                if (AbstractConnection.EXCEPTION_DEBUG) {
                                    LOGGER.error("Exception", IOe);
                                }
                                removeConnection(c);
                            }
                        }
                    }
                } else {
                    LOGGER.info("Discarding " + m + " connection reaped");
                }
            }
            LOGGER.debug("exit");
        }
    }

    public class Reader extends Thread {
        private Connstruct conn;
        private final WeakReference<Connstruct> weakconn;
        private boolean _lrun = true;

        public Reader(final Connstruct conn) {
            this.conn = conn;
            weakconn = new WeakReference<Connstruct>(conn);
            setName("Reader");
        }

        public void stopRunning() {
            _lrun = false;
        }

        @Override
        public void run() {
            LOGGER.debug("enter");
            while (_run && _lrun) {

                Message m = null;
                try {
                    m = conn.min.readMessage();
                } catch (final IOException IOe) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", IOe);
                    }
                    removeConnection(conn);
                } catch (final DBusException DBe) {
                    if (AbstractConnection.EXCEPTION_DEBUG) {
                        LOGGER.error("Exception", DBe);
                    }
                    if (DBe instanceof FatalException) {
                        removeConnection(conn);
                    }
                }

                if (null != m) {
                    LOGGER.info("Read " + m + " from " + conn.unique);
                    synchronized (inqueue) {
                        inqueue.putLast(m, weakconn);
                        inqueue.notifyAll();
                    }
                }
            }
            conn = null;
            LOGGER.debug("exit");
        }
    }

    private final Map<Connstruct, Reader> conns = new HashMap<Connstruct, Reader>();
    private final HashMap<String, Connstruct> names = new HashMap<String, Connstruct>();
    private final MagicMap<Message, WeakReference<Connstruct>> outqueue = new MagicMap<Message, WeakReference<Connstruct>>(
            "out");
    private final MagicMap<Message, WeakReference<Connstruct>> inqueue = new MagicMap<Message, WeakReference<Connstruct>>(
            "in");
    private final MagicMap<Message, WeakReference<Connstruct>> localqueue = new MagicMap<Message, WeakReference<Connstruct>>(
            "local");
    private final List<Connstruct> sigrecips = new Vector<Connstruct>();
    private boolean _run = true;
    private int next_unique = 0;
    private final Object unique_lock = new Object();
    DBusServer dbus_server = new DBusServer();
    Sender sender = new Sender();

    public DBusDaemon() {
        setName("Daemon");
        synchronized (names) {
            names.put("org.freedesktop.DBus", null);
        }
    }

    /*
     * Added temporary to fix "unreachable" code because the compiler "knows" that _run is never set to false.
     */
    void _stopRunning() {
        _run = false;
    }

    @SuppressWarnings("unchecked")
    private void send(final Connstruct c, final Message m) {
        send(c, m, false);
    }

    private void send(final Connstruct c, final Message m, final boolean head) {
        LOGGER.debug("enter");
        if (null == c) {
            LOGGER.trace("Queing message " + m + " for all connections");
        } else {
            LOGGER.trace("Queing message " + m + " for " + c.unique);
        }
        // send to all connections
        if (null == c) {
            synchronized (conns) {
                synchronized (outqueue) {
                    for (final Connstruct d : conns.keySet()) {
                        if (head) {
                            outqueue.putFirst(m, new WeakReference<Connstruct>(d));
                        } else {
                            outqueue.putLast(m, new WeakReference<Connstruct>(d));
                        }
                    }
                    outqueue.notifyAll();
                }
            }
        } else {
            synchronized (outqueue) {
                if (head) {
                    outqueue.putFirst(m, new WeakReference<Connstruct>(c));
                } else {
                    outqueue.putLast(m, new WeakReference<Connstruct>(c));
                }
                outqueue.notifyAll();
            }
        }
        LOGGER.debug("exit");
    }

    @SuppressWarnings("unchecked")
    private List<Connstruct> findSignalMatches(final DBusSignal sig) {
        LOGGER.debug("enter");
        List<Connstruct> l;
        synchronized (sigrecips) {
            l = new Vector<Connstruct>(sigrecips);
        }
        LOGGER.debug("exit");
        return l;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void run() {
        LOGGER.debug("enter");
        while (_run) {
            try {
                Message m;
                List<WeakReference<Connstruct>> wcs;
                synchronized (inqueue) {
                    while (0 == inqueue.size()) {
                        try {
                            inqueue.wait();
                        } catch (final InterruptedException Ie) {
                        }
                    }

                    m = inqueue.head();
                    wcs = inqueue.remove(m);
                }
                if (null != wcs) {
                    for (final WeakReference<Connstruct> wc : wcs) {
                        final Connstruct c = wc.get();
                        if (null != c) {
                            LOGGER.info("<inqueue> Got message " + m + " from " + c.unique);
                            // check if they have hello'd
                            if (null == c.unique
                                    && (!(m instanceof MethodCall) || !"org.freedesktop.DBus".equals(m.getDestination())
                                            || !"Hello".equals(m.getName()))) {
                                send(c, new Error("org.freedesktop.DBus", null,
                                        "org.freedesktop.DBus.Error.AccessDenied", m.getSerial(), "s",
                                        localize("You must send a Hello message")));
                            } else {
                                try {
                                    if (null != c.unique) {
                                        m.setSource(c.unique);
                                    }
                                } catch (final DBusException DBe) {
                                    if (AbstractConnection.EXCEPTION_DEBUG) {
                                        LOGGER.error("Exception", DBe);
                                    }
                                    send(c, new Error("org.freedesktop.DBus", null,
                                            "org.freedesktop.DBus.Error.GeneralError", m.getSerial(), "s",
                                            localize("Sending message failed")));
                                }

                                if ("org.freedesktop.DBus".equals(m.getDestination())) {
                                    synchronized (localqueue) {
                                        localqueue.putLast(m, wc);
                                        localqueue.notifyAll();
                                    }
                                } else {
                                    if (m instanceof DBusSignal) {
                                        final List<Connstruct> list = findSignalMatches((DBusSignal) m);
                                        for (final Connstruct d : list) {
                                            send(d, m);
                                        }
                                    } else {
                                        final Connstruct dest = names.get(m.getDestination());

                                        if (null == dest) {
                                            send(c, new Error("org.freedesktop.DBus", null,
                                                    "org.freedesktop.DBus.Error.ServiceUnknown", m.getSerial(), "s",
                                                    MessageFormat.format(localize("The name `{0}' does not exist"),
                                                            new Object[] { m.getDestination() })));
                                        } else {
                                            send(dest, m);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (final DBusException DBe) {
                if (AbstractConnection.EXCEPTION_DEBUG) {
                    LOGGER.error("Exception", DBe);
                }
            }
        }
        LOGGER.debug("exit");
    }

    private void removeConnection(final Connstruct c) {
        LOGGER.debug("enter");
        boolean exists;
        synchronized (conns) {
            if (exists = conns.containsKey(c)) {
                final Reader r = conns.get(c);
                r.stopRunning();
                conns.remove(c);
            }
        }
        if (exists) {
            try {
                if (null != c.usock) {
                    c.usock.close();
                }
                if (null != c.tsock) {
                    c.tsock.close();
                }
            } catch (final IOException IOe) {
            }
            synchronized (names) {
                final List<String> toRemove = new Vector<String>();
                for (final String name : names.keySet()) {
                    if (names.get(name) == c) {
                        toRemove.add(name);
                        try {
                            send(null, new DBusSignal("org.freedesktop.DBus", "/org/freedesktop/DBus",
                                    "org.freedesktop.DBus", "NameOwnerChanged", "sss", name, c.unique, ""));
                        } catch (final DBusException DBe) {
                            if (AbstractConnection.EXCEPTION_DEBUG) {
                                LOGGER.error("Exception", DBe);
                            }
                        }
                    }
                }
                for (final String name : toRemove) {
                    names.remove(name);
                }
            }
        }
        LOGGER.debug("exit");
    }

    public void addSock(final UnixSocket us) {
        LOGGER.debug("enter");
        LOGGER.warn("New Client");
        final Connstruct c = new Connstruct(us);
        final Reader r = new Reader(c);
        synchronized (conns) {
            conns.put(c, r);
        }
        r.start();
        LOGGER.debug("exit");
    }

    public void addSock(final Socket s) throws IOException {
        LOGGER.debug("enter");
        LOGGER.warn("New Client");
        final Connstruct c = new Connstruct(s);
        final Reader r = new Reader(c);
        synchronized (conns) {
            conns.put(c, r);
        }
        r.start();
        LOGGER.debug("exit");
    }

    public static void syntax() {
        System.out.println(
                "Syntax: DBusDaemon [--version] [-v] [--help] [-h] [--listen address] [-l address] [--print-address] [-r] [--pidfile file] [-p file] [--addressfile file] [-a file] [--unix] [-u] [--tcp] [-t] ");
        System.exit(1);
    }

    public static void version() {
        System.out.println("D-Bus Java Version: " + System.getProperty("Version"));
        System.exit(1);
    }

    public static void saveFile(final String data, final String file) throws IOException {
        final PrintWriter w = new PrintWriter(new FileOutputStream(file));
        w.println(data);
        w.close();
    }

    public static void main(final String args[]) throws Exception {
        LOGGER.debug("enter");

        String addr = null;
        String pidfile = null;
        String addrfile = null;
        boolean printaddress = false;
        boolean unix = true;
        boolean tcp = false;

        // parse options
        try {
            for (int i = 0; i < args.length; i++) {
                if ("--help".equals(args[i]) || "-h".equals(args[i])) {
                    syntax();
                } else if ("--version".equals(args[i]) || "-v".equals(args[i])) {
                    version();
                } else if ("--listen".equals(args[i]) || "-l".equals(args[i])) {
                    addr = args[++i];
                } else if ("--pidfile".equals(args[i]) || "-p".equals(args[i])) {
                    pidfile = args[++i];
                } else if ("--addressfile".equals(args[i]) || "-a".equals(args[i])) {
                    addrfile = args[++i];
                } else if ("--print-address".equals(args[i]) || "-r".equals(args[i])) {
                    printaddress = true;
                } else if ("--unix".equals(args[i]) || "-u".equals(args[i])) {
                    unix = true;
                    tcp = false;
                } else if ("--tcp".equals(args[i]) || "-t".equals(args[i])) {
                    tcp = true;
                    unix = false;
                } else {
                    syntax();
                }
            }
        } catch (final ArrayIndexOutOfBoundsException AIOOBe) {
            syntax();
        }

        // generate a random address if none specified
        if (null == addr && unix) {
            addr = DirectConnection.createDynamicSession();
        } else if (null == addr && tcp) {
            addr = DirectConnection.createDynamicTCPSession();
        }

        BusAddress address = new BusAddress(addr);
        if (null == address.getParameter("guid")) {
            addr += ",guid=" + Transport.genGUID();
            address = new BusAddress(addr);
        }

        // print address to stdout
        if (printaddress) {
            System.out.println(addr);
        }

        // print address to file
        if (null != addrfile) {
            saveFile(addr, addrfile);
        }

        // print PID to file
        if (null != pidfile) {
            saveFile(System.getProperty("Pid"), pidfile);
        }

        // start the daemon
        LOGGER.warn("Binding to " + addr);
        if ("unix".equals(address.getType())) {
            doUnix(address);
        } else if ("tcp".equals(address.getType())) {
            doTCP(address);
        } else {
            throw new Exception("Unknown address type: " + address.getType());
        }
        LOGGER.debug("exit");
    }

    private static void doUnix(final BusAddress address) throws IOException {
        LOGGER.debug("enter");
        UnixServerSocket uss;
        if (null != address.getParameter("abstract")) {
            uss = new UnixServerSocket(new UnixSocketAddress(address.getParameter("abstract"), true));
        } else {
            uss = new UnixServerSocket(new UnixSocketAddress(address.getParameter("path"), false));
        }
        final DBusDaemon d = new DBusDaemon();
        d.start();
        d.sender.start();
        d.dbus_server.start();

        // accept new connections
        while (d._run) {
            final UnixSocket s = uss.accept();
            if (new Transport.SASL().auth(Transport.SASL.MODE_SERVER, Transport.SASL.AUTH_EXTERNAL,
                    address.getParameter("guid"), s.getOutputStream(), s.getInputStream(), s)) {
                // s.setBlocking(false);
                d.addSock(s);
            } else {
                s.close();
            }
        }
        LOGGER.debug("exit");
    }

    private static void doTCP(final BusAddress address) throws IOException {
        LOGGER.debug("enter");
        final ServerSocket ss = new ServerSocket(Integer.parseInt(address.getParameter("port")), 10,
                InetAddress.getByName(address.getParameter("host")));
        final DBusDaemon d = new DBusDaemon();
        d.start();
        d.sender.start();
        d.dbus_server.start();

        // accept new connections
        while (d._run) {
            final Socket s = ss.accept();
            boolean authOK = false;
            try {
                authOK = new Transport.SASL().auth(Transport.SASL.MODE_SERVER, Transport.SASL.AUTH_EXTERNAL,
                        address.getParameter("guid"), s.getOutputStream(), s.getInputStream(), null);
            } catch (final Exception e) {
                LOGGER.debug("Exception", e);
            }
            if (authOK) {
                d.addSock(s);
            } else {
                s.close();
            }
        }
        LOGGER.debug("exit");
    }
}
