/*
   D-Bus Java Implementation
   Copyright (c) 2005-2006 Matthew Johnson

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus;

import static org.freedesktop.dbus.Gettext.localize;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import org.freedesktop.DBus;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a connection to DBus.
 * <p>
 * This is a Singleton class, only 1 connection to the SYSTEM or SESSION busses can be made.
 * Repeated calls to getConnection will return the same reference.
 * </p>
 * <p>
 * Signal Handlers and method calls from remote objects are run in their own threads, you MUST handle the concurrency
 * issues.
 * </p>
 */
public class DBusConnection extends AbstractConnection {
    private final Logger logger = LoggerFactory.getLogger(DBusConnection.class);
    private static final Logger LOGGER = LoggerFactory.getLogger(DBusConnection.class);

    /**
     * Add addresses of peers to a set which will watch for them to
     * disappear and automatically remove them from the set.
     */
    public class PeerSet implements Set<String>, DBusSigHandler<DBus.NameOwnerChanged> {
        private final Set<String> addresses;

        public PeerSet() {
            addresses = new TreeSet<>();
            try {
                addSigHandler(new DBusMatchRule(DBus.NameOwnerChanged.class, null, null), this);
            } catch (final DBusException DBe) {
                if (EXCEPTION_DEBUG) {
                    logger.error("Exception", DBe);
                }
            }
        }

        @Override
        public void handle(final DBus.NameOwnerChanged noc) {
            logger.debug("Received dbus signal: {}", noc);
            if ("".equals(noc.new_owner) && addresses.contains(noc.name)) {
                remove(noc.name);
            }
        }

        @Override
        public boolean add(final String address) {
            logger.debug("Adding {}", address);
            synchronized (addresses) {
                return addresses.add(address);
            }
        }

        @Override
        public boolean addAll(final Collection<? extends String> addresses) {
            synchronized (this.addresses) {
                return this.addresses.addAll(addresses);
            }
        }

        @Override
        public void clear() {
            synchronized (addresses) {
                addresses.clear();
            }
        }

        @Override
        public boolean contains(final Object o) {
            return addresses.contains(o);
        }

        @Override
        public boolean containsAll(final Collection<?> os) {
            return addresses.containsAll(os);
        }

        @Override
        public boolean equals(final Object o) {
            if (o instanceof PeerSet) {
                return ((PeerSet) o).addresses.equals(addresses);
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return addresses.hashCode();
        }

        @Override
        public boolean isEmpty() {
            return addresses.isEmpty();
        }

        @Override
        public Iterator<String> iterator() {
            return addresses.iterator();
        }

        @Override
        public boolean remove(final Object o) {
            logger.debug("Removing {}", o);
            synchronized (addresses) {
                return addresses.remove(o);
            }
        }

        @Override
        public boolean removeAll(final Collection<?> os) {
            synchronized (addresses) {
                return addresses.removeAll(os);
            }
        }

        @Override
        public boolean retainAll(final Collection<?> os) {
            synchronized (addresses) {
                return addresses.retainAll(os);
            }
        }

        @Override
        public int size() {
            return addresses.size();
        }

        @Override
        public Object[] toArray() {
            synchronized (addresses) {
                return addresses.toArray();
            }
        }

        @Override
        public <T> T[] toArray(final T[] a) {
            synchronized (addresses) {
                return addresses.toArray(a);
            }
        }
    }

    private class _sighandler implements DBusSigHandler<DBusSignal> {
        @Override
        public void handle(final DBusSignal s) {
            if (s instanceof org.freedesktop.DBus.Local.Disconnected) {
                logger.warn("Handling Disconnected signal from bus");
                try {
                    final Error err = new Error("org.freedesktop.DBus.Local", "org.freedesktop.DBus.Local.Disconnected",
                            0, "s", new Object[] { localize("Disconnected") });
                    if (null != pendingCalls) {
                        synchronized (pendingCalls) {
                            final long[] set = pendingCalls.getKeys();
                            for (final long l : set) {
                                if (-1 != l) {
                                    final MethodCall m = pendingCalls.remove(l);
                                    if (null != m) {
                                        m.setReply(err);
                                    }
                                }
                            }
                        }
                    }
                    synchronized (pendingErrors) {
                        pendingErrors.add(err);
                    }
                } catch (final DBusException DBe) {
                }
            } else if (s instanceof org.freedesktop.DBus.NameAcquired) {
                busnames.add(((org.freedesktop.DBus.NameAcquired) s).name);
            }
        }
    }

    /**
     * System Bus
     */
    public static final int SYSTEM = 0;
    /**
     * Session Bus
     */
    public static final int SESSION = 1;

    public static final String DEFAULT_SYSTEM_BUS_ADDRESS = "unix:path=/var/run/dbus/system_bus_socket";

    private final List<String> busnames;

    private static final Map<Object, DBusConnection> conn = new HashMap<>();
    private int _refcount = 0;
    private final Object _reflock = new Object();
    private final DBus _dbus;

    /**
     * Connect to the BUS. If a connection already exists to the specified Bus, a reference to it is returned.
     *
     * @param address The address of the bus to connect to
     * @throws DBusException If there is a problem connecting to the Bus.
     */
    public static DBusConnection getConnection(final String address) throws DBusException {
        synchronized (conn) {
            DBusConnection c = conn.get(address);
            if (null != c) {
                synchronized (c._reflock) {
                    c._refcount++;
                }
                return c;
            } else {
                c = new DBusConnection(address);
                conn.put(address, c);
                return c;
            }
        }
    }

    /**
     * Connect to the BUS. If a connection already exists to the specified Bus, a reference to it is returned.
     *
     * @param bustype The Bus to connect to.
     * @see #SYSTEM
     * @see #SESSION
     * @throws DBusException If there is a problem connecting to the Bus.
     */
    public static DBusConnection getConnection(final int bustype) throws DBusException {
        synchronized (conn) {
            String s = null;
            switch (bustype) {
                case SYSTEM:
                    s = System.getenv("DBUS_SYSTEM_BUS_ADDRESS");
                    if (null == s) {
                        s = DEFAULT_SYSTEM_BUS_ADDRESS;
                    }
                    break;
                case SESSION:
                    s = System.getenv("DBUS_SESSION_BUS_ADDRESS");
                    if (null == s) {
                        // address gets stashed in $HOME/.dbus/session-bus/`dbus-uuidgen --get`-`sed 's/:\(.\)\..*/\1/'
                        // <<< $DISPLAY`
                        final String display = System.getenv("DISPLAY");
                        if (null == display) {
                            throw new DBusException(localize("Cannot Resolve Session Bus Address"));
                        }
                        final File uuidfile = new File("/var/lib/dbus/machine-id");
                        if (!uuidfile.exists()) {
                            throw new DBusException(localize("Cannot Resolve Session Bus Address"));
                        }
                        try {
                            final String uuid;
                            try (BufferedReader readerUuidFile = new BufferedReader(new FileReader(uuidfile))) {
                                uuid = readerUuidFile.readLine();
                            }

                            final String homedir = System.getProperty("user.home");
                            final File addressfile = new File(homedir + "/.dbus/session-bus",
                                    uuid + "-" + display.replaceAll(":([0-9]*)\\..*", "$1"));
                            if (!addressfile.exists()) {
                                throw new DBusException(localize("Cannot Resolve Session Bus Address"));
                            }
                            try (BufferedReader readerAddressFile = new BufferedReader(new FileReader(addressfile))) {
                                String l;
                                while (null != (l = readerAddressFile.readLine())) {
                                    LOGGER.trace("Reading D-Bus session data: {}", l);
                                    if (l.matches("DBUS_SESSION_BUS_ADDRESS.*")) {
                                        s = l.replaceAll("^[^=]*=", "");
                                        LOGGER.trace("Parsing {} to {}", l, s);
                                    }
                                }
                            }
                            if (null == s || "".equals(s)) {
                                throw new DBusException(localize("Cannot Resolve Session Bus Address"));
                            }
                            LOGGER.info("Read bus address {} from file {}", s, addressfile);
                        } catch (final Exception e) {
                            if (EXCEPTION_DEBUG) {
                                LOGGER.error("Exception", e);
                            }
                            throw new DBusException(localize("Cannot Resolve Session Bus Address"));
                        }
                    }
                    break;
                default:
                    throw new DBusException(localize("Invalid Bus Type: ") + bustype);
            }
            DBusConnection c = conn.get(s);
            LOGGER.trace("Getting bus connection for {}: {}", s, c);
            if (null != c) {
                synchronized (c._reflock) {
                    c._refcount++;
                }
                return c;
            } else {
                LOGGER.debug("Creating new bus connection to: {}", s);
                c = new DBusConnection(s);
                conn.put(s, c);
                return c;
            }
        }
    }

    private DBusConnection(final String address) throws DBusException {
        super(address);
        busnames = new Vector<>();

        synchronized (_reflock) {
            _refcount = 1;
        }

        try {
            transport = new Transport(addr, AbstractConnection.TIMEOUT);
            connected = true;
        } catch (final IOException IOe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", IOe);
            }
            disconnect();
            throw new DBusException(localize("Failed to connect to bus ") + IOe.getMessage());
        } catch (final ParseException Pe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", Pe);
            }
            disconnect();
            throw new DBusException(localize("Failed to connect to bus ") + Pe.getMessage());
        }

        // start listening for calls
        listen();

        // register disconnect handlers
        final DBusSigHandler<DBusSignal> h = new _sighandler();
        addSigHandlerWithoutMatch(org.freedesktop.DBus.Local.Disconnected.class, h);
        addSigHandlerWithoutMatch(org.freedesktop.DBus.NameAcquired.class, h);

        // register ourselves
        _dbus = getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        try {
            busnames.add(_dbus.Hello());
        } catch (final DBusExecutionException DBEe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", DBEe);
            }
            throw new DBusException(DBEe.getMessage());
        }
    }

    DBusInterface dynamicProxy(final String source, final String path) throws DBusException {
        logger.info("Introspecting {} on {} for dynamic proxy creation", path, source);
        try {
            final DBus.Introspectable intro = getRemoteObject(source, path, DBus.Introspectable.class);
            final String data = intro.Introspect();
            logger.trace("Got introspection data: {}", data);
            final String[] tags = data.split("[<>]");
            final Vector<String> ifaces = new Vector<>();
            for (final String tag : tags) {
                if (tag.startsWith("interface")) {
                    ifaces.add(tag.replaceAll("^interface *name *= *['\"]([^'\"]*)['\"].*$", "$1"));
                }
            }
            final Vector<Class<? extends Object>> ifcs = new Vector<>();
            for (String iface : ifaces) {
                logger.debug("Trying interface {}", iface);
                int j = 0;
                while (j >= 0) {
                    try {
                        final Class<?> ifclass = Class.forName(iface);
                        if (!ifcs.contains(ifclass)) {
                            ifcs.add(ifclass);
                        }
                        break;
                    } catch (final Exception e) {
                    }
                    j = iface.lastIndexOf(".");
                    final char[] cs = iface.toCharArray();
                    if (j >= 0) {
                        cs[j] = '$';
                        iface = String.valueOf(cs);
                    }
                }
            }

            if (ifcs.size() == 0) {
                throw new DBusException(localize("Could not find an interface to cast to"));
            }

            final RemoteObject ro = new RemoteObject(source, path, null, false);
            final DBusInterface newi = (DBusInterface) Proxy.newProxyInstance(ifcs.get(0).getClassLoader(),
                    ifcs.toArray(new Class[0]), new RemoteInvocationHandler(this, ro));
            importedObjects.put(newi, ro);
            return newi;
        } catch (final Exception e) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", e);
            }
            throw new DBusException(
                    MessageFormat.format(localize("Failed to create proxy object for {0} exported by {1}. Reason: {2}"),
                            new Object[] { path, source, e.getMessage() }));
        }
    }

    @Override
    DBusInterface getExportedObject(final String source, final String path) throws DBusException {
        ExportedObject o = null;
        synchronized (exportedObjects) {
            o = exportedObjects.get(path);
        }
        if (null != o && null == o.object.get()) {
            unExportObject(path);
            o = null;
        }
        if (null != o) {
            return o.object.get();
        }
        if (null == source) {
            throw new DBusException(localize("Not an object exported by this connection and no remote specified"));
        }
        return dynamicProxy(source, path);
    }

    /**
     * Release a bus name.
     * Releases the name so that other people can use it
     *
     * @param busname The name to release. MUST be in dot-notation like "org.freedesktop.local"
     * @throws DBusException If the busname is incorrectly formatted.
     */
    public void releaseBusName(final String busname) throws DBusException {
        if (!busname.matches(BUSNAME_REGEX) || busname.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name"));
        }
        synchronized (this.busnames) {
            UInt32 rv;
            try {
                rv = _dbus.ReleaseName(busname);
                logger.trace("release bus name '{}' returned '{}'", busname, rv);
            } catch (final DBusExecutionException DBEe) {
                if (EXCEPTION_DEBUG) {
                    logger.error("Exception", DBEe);
                }
                throw new DBusException(DBEe.getMessage());
            }
            this.busnames.remove(busname);
        }
    }

    /**
     * Request a bus name.
     * Request the well known name that this should respond to on the Bus.
     *
     * @param busname The name to respond to. MUST be in dot-notation like "org.freedesktop.local"
     * @throws DBusException If the register name failed, or our name already exists on the bus.
     *             or if busname is incorrectly formatted.
     */
    public void requestBusName(final String busname) throws DBusException {
        if (!busname.matches(BUSNAME_REGEX) || busname.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name"));
        }
        synchronized (this.busnames) {
            UInt32 rv;
            try {
                rv = _dbus.RequestName(busname,
                        new UInt32(DBus.DBUS_NAME_FLAG_REPLACE_EXISTING | DBus.DBUS_NAME_FLAG_DO_NOT_QUEUE));
            } catch (final DBusExecutionException DBEe) {
                if (EXCEPTION_DEBUG) {
                    logger.error("Exception", DBEe);
                }
                throw new DBusException(DBEe.getMessage());
            }
            switch (rv.intValue()) {
                case DBus.DBUS_REQUEST_NAME_REPLY_PRIMARY_OWNER:
                    break;
                case DBus.DBUS_REQUEST_NAME_REPLY_IN_QUEUE:
                    throw new DBusException(localize("Failed to register bus name"));
                case DBus.DBUS_REQUEST_NAME_REPLY_EXISTS:
                    throw new DBusException(localize("Failed to register bus name"));
                case DBus.DBUS_REQUEST_NAME_REPLY_ALREADY_OWNER:
                    break;
                default:
                    break;
            }
            this.busnames.add(busname);
        }
    }

    /**
     * Returns the unique name of this connection.
     */
    public String getUniqueName() {
        return busnames.get(0);
    }

    /**
     * Returns all the names owned by this connection.
     */
    public String[] getNames() {
        final Set<String> names = new TreeSet<>();
        names.addAll(busnames);
        return names.toArray(new String[0]);
    }

    public <I extends DBusInterface> I getPeerRemoteObject(final String busname, final String objectpath,
            final Class<I> type) throws DBusException {
        return getPeerRemoteObject(busname, objectpath, type, true);
    }

    /**
     * Return a reference to a remote object.
     * This method will resolve the well known name (if given) to a unique bus name when you call it.
     * This means that if a well known name is released by one process and acquired by another calls to
     * objects gained from this method will continue to operate on the original process.
     *
     * This method will use bus introspection to determine the interfaces on a remote object and so
     * <b>may block</b> and <b>may fail</b>. The resulting proxy object will, however, be castable
     * to any interface it implements. It will also autostart the process if applicable. Also note
     * that the resulting proxy may fail to execute the correct method with overloaded methods
     * and that complex types may fail in interesting ways. Basically, if something odd happens,
     * try specifying the interface explicitly.
     *
     * @param busname The bus name to connect to. Usually a well known bus name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath The path on which the process is exporting the object.$
     * @return A reference to a remote object.
     * @throws ClassCastException If type is not a sub-type of DBusInterface
     * @throws DBusException If busname or objectpath are incorrectly formatted.
     */
    public DBusInterface getPeerRemoteObject(final String busname, final String objectpath) throws DBusException {
        if (null == busname) {
            throw new DBusException(localize("Invalid bus name: null"));
        }

        if (!busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) || busname.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + busname);
        }

        final String unique = _dbus.GetNameOwner(busname);

        return dynamicProxy(unique, objectpath);
    }

    /**
     * Return a reference to a remote object.
     * This method will always refer to the well known name (if given) rather than resolving it to a unique bus name.
     * In particular this means that if a process providing the well known name disappears and is taken over by another
     * process
     * proxy objects gained by this method will make calls on the new proccess.
     *
     * This method will use bus introspection to determine the interfaces on a remote object and so
     * <b>may block</b> and <b>may fail</b>. The resulting proxy object will, however, be castable
     * to any interface it implements. It will also autostart the process if applicable. Also note
     * that the resulting proxy may fail to execute the correct method with overloaded methods
     * and that complex types may fail in interesting ways. Basically, if something odd happens,
     * try specifying the interface explicitly.
     *
     * @param busname The bus name to connect to. Usually a well known bus name name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath The path on which the process is exporting the object.
     * @return A reference to a remote object.
     * @throws ClassCastException If type is not a sub-type of DBusInterface
     * @throws DBusException If busname or objectpath are incorrectly formatted.
     */
    public DBusInterface getRemoteObject(final String busname, final String objectpath) throws DBusException {
        if (null == busname) {
            throw new DBusException(localize("Invalid bus name: null"));
        }
        if (null == objectpath) {
            throw new DBusException(localize("Invalid object path: null"));
        }

        if (!busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) || busname.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + busname);
        }

        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }

        return dynamicProxy(busname, objectpath);
    }

    /**
     * Return a reference to a remote object.
     * This method will resolve the well known name (if given) to a unique bus name when you call it.
     * This means that if a well known name is released by one process and acquired by another calls to
     * objects gained from this method will continue to operate on the original process.
     *
     * @param busname The bus name to connect to. Usually a well known bus name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath The path on which the process is exporting the object.$
     * @param type The interface they are exporting it on. This type must have the same full class name and exposed
     *            method signatures
     *            as the interface the remote object is exporting.
     * @param autostart Disable/Enable auto-starting of services in response to calls on this object.
     *            Default is enabled; when calling a method with auto-start enabled, if the destination is a well-known
     *            name
     *            and is not owned the bus will attempt to start a process to take the name. When disabled an error is
     *            returned immediately.
     * @return A reference to a remote object.
     * @throws ClassCastException If type is not a sub-type of DBusInterface
     * @throws DBusException If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    public <I extends DBusInterface> I getPeerRemoteObject(final String busname, final String objectpath,
            final Class<I> type, final boolean autostart) throws DBusException {
        if (null == busname) {
            throw new DBusException(localize("Invalid bus name: null"));
        }

        if (!busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) || busname.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + busname);
        }

        final String unique = _dbus.GetNameOwner(busname);

        return getRemoteObject(unique, objectpath, type, autostart);
    }

    /**
     * Return a reference to a remote object.
     * This method will always refer to the well known name (if given) rather than resolving it to a unique bus name.
     * In particular this means that if a process providing the well known name disappears and is taken over by another
     * process
     * proxy objects gained by this method will make calls on the new proccess.
     *
     * @param busname The bus name to connect to. Usually a well known bus name name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath The path on which the process is exporting the object.
     * @param type The interface they are exporting it on. This type must have the same full class name and exposed
     *            method signatures
     *            as the interface the remote object is exporting.
     * @return A reference to a remote object.
     * @throws ClassCastException If type is not a sub-type of DBusInterface
     * @throws DBusException If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    public <I extends DBusInterface> I getRemoteObject(final String busname, final String objectpath,
            final Class<I> type) throws DBusException {
        return getRemoteObject(busname, objectpath, type, true);
    }

    /**
     * Return a reference to a remote object.
     * This method will always refer to the well known name (if given) rather than resolving it to a unique bus name.
     * In particular this means that if a process providing the well known name disappears and is taken over by another
     * process
     * proxy objects gained by this method will make calls on the new proccess.
     *
     * @param busname The bus name to connect to. Usually a well known bus name name in dot-notation (such as
     *            "org.freedesktop.local")
     *            or may be a DBus address such as ":1-16".
     * @param objectpath The path on which the process is exporting the object.
     * @param type The interface they are exporting it on. This type must have the same full class name and exposed
     *            method signatures
     *            as the interface the remote object is exporting.
     * @param autostart Disable/Enable auto-starting of services in response to calls on this object.
     *            Default is enabled; when calling a method with auto-start enabled, if the destination is a well-known
     *            name
     *            and is not owned the bus will attempt to start a process to take the name. When disabled an error is
     *            returned immediately.
     * @return A reference to a remote object.
     * @throws ClassCastException If type is not a sub-type of DBusInterface
     * @throws DBusException If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    @SuppressWarnings("unchecked")
    public <I extends DBusInterface> I getRemoteObject(final String busname, final String objectpath,
            final Class<I> type, final boolean autostart) throws DBusException {
        if (null == busname) {
            throw new DBusException(localize("Invalid bus name: null"));
        }
        if (null == objectpath) {
            throw new DBusException(localize("Invalid object path: null"));
        }
        if (null == type) {
            throw new ClassCastException(localize("Not A DBus Interface"));
        }

        if (!busname.matches(BUSNAME_REGEX) && !busname.matches(CONNID_REGEX) || busname.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + busname);
        }

        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }

        if (!DBusInterface.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Interface"));
        }

        // don't let people import things which don't have a
        // valid D-Bus interface name
        if (type.getName().equals(type.getSimpleName())) {
            throw new DBusException(localize("DBusInterfaces cannot be declared outside a package"));
        }

        final RemoteObject ro = new RemoteObject(busname, objectpath, type, autostart);
        final I i = (I) Proxy.newProxyInstance(type.getClassLoader(), new Class[] { type },
                new RemoteInvocationHandler(this, ro));
        importedObjects.put(i, ro);
        return i;
    }

    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     *
     * @param type The signal to watch for.
     * @param source The source of the signal.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler(final Class<T> type, final String source,
            final DBusSigHandler<T> handler) throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        if (source.matches(BUSNAME_REGEX)) {
            throw new DBusException(
                    localize("Cannot watch for signals based on well known bus name as source, only unique names."));
        }
        if (!source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + source);
        }
        removeSigHandler(new DBusMatchRule(type, source, null), handler);
    }

    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     *
     * @param type The signal to watch for.
     * @param source The source of the signal.
     * @param object The object emitting the signal.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler(final Class<T> type, final String source,
            final DBusInterface object, final DBusSigHandler<T> handler) throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        if (source.matches(BUSNAME_REGEX)) {
            throw new DBusException(
                    localize("Cannot watch for signals based on well known bus name as source, only unique names."));
        }
        if (!source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + source);
        }
        final String objectpath = importedObjects.get(object).objectpath;
        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }
        removeSigHandler(new DBusMatchRule(type, source, objectpath), handler);
    }

    @Override
    protected <T extends DBusSignal> void removeSigHandler(final DBusMatchRule rule, final DBusSigHandler<T> handler)
            throws DBusException {

        final SignalTuple key = new SignalTuple(rule.getInterface(), rule.getMember(), rule.getObject(),
                rule.getSource());
        synchronized (handledSignals) {
            final Vector<DBusSigHandler<? extends DBusSignal>> v = handledSignals.get(key);
            if (null != v) {
                v.remove(handler);
                if (0 == v.size()) {
                    handledSignals.remove(key);
                    try {
                        _dbus.RemoveMatch(rule.toString());
                    } catch (final NotConnected NC) {
                        if (EXCEPTION_DEBUG) {
                            logger.error("Exception", NC);
                        }
                    } catch (final DBusExecutionException DBEe) {
                        if (EXCEPTION_DEBUG) {
                            logger.error("Exception", DBEe);
                        }
                        throw new DBusException(DBEe.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type, name and source.
     *
     * @param type The signal to watch for.
     * @param source The process which will send the signal. This <b>MUST</b> be a unique bus name and not a well known
     *            name.
     * @param handler The handler to call when a signal is received.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void addSigHandler(final Class<T> type, final String source,
            final DBusSigHandler<T> handler) throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        if (source.matches(BUSNAME_REGEX)) {
            throw new DBusException(
                    localize("Cannot watch for signals based on well known bus name as source, only unique names."));
        }
        if (!source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + source);
        }
        addSigHandler(new DBusMatchRule(type, source, null), (DBusSigHandler<? extends DBusSignal>) handler);
    }

    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type, name, source and
     * object.
     *
     * @param type The signal to watch for.
     * @param source The process which will send the signal. This <b>MUST</b> be a unique bus name and not a well known
     *            name.
     * @param object The object from which the signal will be emitted
     * @param handler The handler to call when a signal is received.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void addSigHandler(final Class<T> type, final String source,
            final DBusInterface object, final DBusSigHandler<T> handler) throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        if (source.matches(BUSNAME_REGEX)) {
            throw new DBusException(
                    localize("Cannot watch for signals based on well known bus name as source, only unique names."));
        }
        if (!source.matches(CONNID_REGEX) || source.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid bus name: ") + source);
        }
        final String objectpath = importedObjects.get(object).objectpath;
        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }
        addSigHandler(new DBusMatchRule(type, source, objectpath), (DBusSigHandler<? extends DBusSignal>) handler);
    }

    @Override
    protected <T extends DBusSignal> void addSigHandler(final DBusMatchRule rule, final DBusSigHandler<T> handler)
            throws DBusException {
        try {
            _dbus.AddMatch(rule.toString());
        } catch (final DBusExecutionException DBEe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", DBEe);
            }
            throw new DBusException(DBEe.getMessage());
        }
        final SignalTuple key = new SignalTuple(rule.getInterface(), rule.getMember(), rule.getObject(),
                rule.getSource());
        synchronized (handledSignals) {
            Vector<DBusSigHandler<? extends DBusSignal>> v = handledSignals.get(key);
            if (null == v) {
                v = new Vector<>();
                v.add(handler);
                handledSignals.put(key, v);
            } else {
                v.add(handler);
            }
        }
    }

    /**
     * Disconnect from the Bus.
     * This only disconnects when the last reference to the bus has disconnect called on it
     * or has been destroyed.
     */
    @Override
    public void disconnect() {
        synchronized (conn) {
            synchronized (_reflock) {
                if (0 == --_refcount) {
                    logger.info("Disconnecting DBusConnection");
                    // Set all pending messages to have an error.
                    try {
                        final Error err = new Error("org.freedesktop.DBus.Local",
                                "org.freedesktop.DBus.Local.Disconnected", 0, "s",
                                new Object[] { localize("Disconnected") });
                        synchronized (pendingCalls) {
                            final long[] set = pendingCalls.getKeys();
                            for (final long l : set) {
                                if (-1 != l) {
                                    final MethodCall m = pendingCalls.remove(l);
                                    if (null != m) {
                                        m.setReply(err);
                                    }
                                }
                            }
                            pendingCalls = null;
                        }
                        synchronized (pendingErrors) {
                            pendingErrors.add(err);
                        }
                    } catch (final DBusException DBe) {
                    }

                    conn.remove(addr);
                    super.disconnect();
                }
            }
        }
    }
}
