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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Pattern;

import org.freedesktop.DBus;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.exceptions.FatalDBusException;
import org.freedesktop.dbus.exceptions.FatalException;
import org.freedesktop.dbus.exceptions.NotConnected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles a connection to DBus.
 */
public abstract class AbstractConnection {
    private final Logger logger = LoggerFactory.getLogger(AbstractConnection.class);

    protected class FallbackContainer {
        private final Map<String[], ExportedObject> fallbacks = new HashMap<>();

        public synchronized void add(final String path, final ExportedObject eo) {
            logger.debug("Adding fallback on {} of {}", path, eo);
            fallbacks.put(path.split("/"), eo);
        }

        public synchronized void remove(final String path) {
            logger.debug("Removing fallback on {}", path);
            fallbacks.remove(path.split("/"));
        }

        public synchronized ExportedObject get(final String path) {
            final int best = 0;
            int i = 0;
            ExportedObject bestobject = null;
            final String[] pathel = path.split("/");
            for (final String[] fbpath : fallbacks.keySet()) {
                logger.trace("Trying fallback path {} to match {}", fbpath, pathel);
                for (i = 0; i < pathel.length && i < fbpath.length; i++) {
                    if (!pathel[i].equals(fbpath[i])) {
                        break;
                    }
                }
                if (i > 0 && i == fbpath.length && i > best) {
                    bestobject = fallbacks.get(fbpath);
                }
                logger.trace("Matches {} bestobject now {}", i, bestobject);
            }
            logger.debug("Found fallback for {} of {}", path, bestobject);
            return bestobject;
        }
    }

    protected class _thread extends Thread {
        public _thread() {
            setName("DBusConnection");
        }

        @Override
        public void run() {
            try {
                Message m = null;
                while (_run) {
                    m = null;

                    // read from the wire
                    try {
                        // this blocks on outgoing being non-empty or a message being available.
                        m = readIncoming();
                        if (m != null) {
                            logger.trace("Got Incoming Message: {}", m);
                            synchronized (this) {
                                notifyAll();
                            }

                            if (m instanceof DBusSignal) {
                                handleMessage((DBusSignal) m);
                            } else if (m instanceof MethodCall) {
                                handleMessage((MethodCall) m);
                            } else if (m instanceof MethodReturn) {
                                handleMessage((MethodReturn) m);
                            } else if (m instanceof Error) {
                                handleMessage((Error) m);
                            }

                            m = null;
                        }
                    } catch (final Exception e) {
                        if (EXCEPTION_DEBUG) {
                            logger.error("Exception", e);
                        }
                        if (e instanceof FatalException) {
                            disconnect();
                        }
                    }

                }
                synchronized (this) {
                    notifyAll();
                }
            } catch (final Exception e) {
                if (EXCEPTION_DEBUG) {
                    logger.error("Exception", e);
                }
            }
        }
    }

    private class _globalhandler implements org.freedesktop.DBus.Peer, org.freedesktop.DBus.Introspectable {
        private final String objectpath;

        public _globalhandler() {
            this.objectpath = null;
        }

        public _globalhandler(final String objectpath) {
            this.objectpath = objectpath;
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        public void Ping() {
            return;
        }

        @Override
        public String Introspect() {
            String intro = objectTree.Introspect(objectpath);
            if (null == intro) {
                final ExportedObject eo = fallbackcontainer.get(objectpath);
                if (null != eo) {
                    intro = eo.introspectiondata;
                }
            }
            if (null == intro) {
                throw new DBus.Error.UnknownObject("Introspecting on non-existant object");
            } else {
                return "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\" "
                        + "\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">\n" + intro;
            }
        }
    }

    protected class _workerthread extends Thread {
        private boolean _run = true;

        public void halt() {
            _run = false;
        }

        @Override
        public void run() {
            while (_run) {
                Runnable r = null;
                synchronized (runnables) {
                    while (runnables.size() == 0 && _run) {
                        try {
                            runnables.wait();
                        } catch (final InterruptedException Ie) {
                        }
                    }
                    if (runnables.size() > 0) {
                        r = runnables.removeFirst();
                    }
                }
                if (null != r) {
                    r.run();
                }
            }
        }
    }

    private class _sender extends Thread {
        public _sender() {
            setName("Sender");
        }

        @Override
        public void run() {
            Message m = null;

            logger.info("Monitoring outbound queue");
            // block on the outbound queue and send from it
            while (_run) {
                if (null != outgoing) {
                    synchronized (outgoing) {
                        logger.trace("Blocking");
                        while (outgoing.size() == 0 && _run) {
                            try {
                                outgoing.wait();
                            } catch (final InterruptedException Ie) {
                            }
                        }
                        logger.trace("Notified");
                        if (outgoing.size() > 0) {
                            m = outgoing.remove();
                        }
                        logger.debug("Got message: {}", m);
                    }
                }
                if (null != m) {
                    sendMessage(m);
                }
                m = null;
            }

            logger.info("Flushing outbound queue and quitting");
            // flush the outbound queue before disconnect.
            if (null != outgoing) {
                do {
                    final EfficientQueue ogq = outgoing;
                    synchronized (ogq) {
                        outgoing = null;
                    }
                    if (!ogq.isEmpty()) {
                        m = ogq.remove();
                    } else {
                        m = null;
                    }
                    sendMessage(m);
                } while (null != m);
            }

            // close the underlying streams
        }
    }

    /**
     * Timeout in us on checking the BUS for incoming messages and sending outgoing messages
     */
    protected static final int TIMEOUT = 100000;
    /** Initial size of the pending calls map */
    private static final int PENDING_MAP_INITIAL_SIZE = 10;
    static final String BUSNAME_REGEX = "^[-_a-zA-Z][-_a-zA-Z0-9]*(\\.[-_a-zA-Z][-_a-zA-Z0-9]*)*$";
    static final String CONNID_REGEX = "^:[0-9]*\\.[0-9]*$";
    static final String OBJECT_REGEX = "^/([-_a-zA-Z0-9]+(/[-_a-zA-Z0-9]+)*)?$";
    static final byte THREADCOUNT = 4;
    static final int MAX_ARRAY_LENGTH = 67108864;
    static final int MAX_NAME_LENGTH = 255;
    protected Map<String, ExportedObject> exportedObjects;
    private final ObjectTree objectTree;
    private final _globalhandler _globalhandlerreference;
    protected Map<DBusInterface, RemoteObject> importedObjects;
    protected Map<SignalTuple, Vector<DBusSigHandler<? extends DBusSignal>>> handledSignals;
    protected EfficientMap pendingCalls;
    protected Map<MethodCall, CallbackHandler<? extends Object>> pendingCallbacks;
    protected Map<MethodCall, DBusAsyncReply<? extends Object>> pendingCallbackReplys;
    protected LinkedList<Runnable> runnables;
    protected LinkedList<_workerthread> workers;
    protected FallbackContainer fallbackcontainer;
    protected boolean _run;
    EfficientQueue outgoing;
    LinkedList<Error> pendingErrors;
    private static final Map<Thread, DBusCallInfo> infomap = new HashMap<>();
    protected _thread thread;
    protected _sender sender;
    protected Transport transport;
    protected String addr;
    protected boolean weakreferences = false;
    static final Pattern dollar_pattern = Pattern.compile("[$]");
    public static final boolean EXCEPTION_DEBUG;
    static final boolean FLOAT_SUPPORT;
    protected boolean connected = false;
    static {
        FLOAT_SUPPORT = null != System.getenv("DBUS_JAVA_FLOATS");
        EXCEPTION_DEBUG = null != System.getenv("DBUS_JAVA_EXCEPTION_DEBUG");
        if (EXCEPTION_DEBUG) {
            LoggerFactory.getLogger(AbstractConnection.class).info("Debugging of internal exceptions enabled");
        }
    }

    protected AbstractConnection(final String address) throws DBusException {
        exportedObjects = new HashMap<>();
        importedObjects = new HashMap<>();
        _globalhandlerreference = new _globalhandler();
        synchronized (exportedObjects) {
            exportedObjects.put(null, new ExportedObject(_globalhandlerreference, weakreferences));
        }
        handledSignals = new HashMap<>();
        pendingCalls = new EfficientMap(PENDING_MAP_INITIAL_SIZE);
        outgoing = new EfficientQueue(PENDING_MAP_INITIAL_SIZE);
        pendingCallbacks = new HashMap<>();
        pendingCallbackReplys = new HashMap<>();
        pendingErrors = new LinkedList<>();
        runnables = new LinkedList<>();
        workers = new LinkedList<>();
        objectTree = new ObjectTree();
        fallbackcontainer = new FallbackContainer();
        synchronized (workers) {
            for (int i = 0; i < THREADCOUNT; i++) {
                final _workerthread t = new _workerthread();
                t.start();
                workers.add(t);
            }
        }
        _run = true;
        addr = address;
    }

    protected void listen() {
        // start listening
        thread = new _thread();
        thread.start();
        sender = new _sender();
        sender.start();
    }

    /**
     * Change the number of worker threads to receive method calls and handle signals.
     * Default is 4 threads
     *
     * @param newcount The new number of worker Threads to use.
     */
    public void changeThreadCount(final byte newcount) {
        synchronized (workers) {
            if (workers.size() > newcount) {
                final int n = workers.size() - newcount;
                for (int i = 0; i < n; i++) {
                    final _workerthread t = workers.removeFirst();
                    t.halt();
                }
            } else if (workers.size() < newcount) {
                final int n = newcount - workers.size();
                for (int i = 0; i < n; i++) {
                    final _workerthread t = new _workerthread();
                    t.start();
                    workers.add(t);
                }
            }
        }
    }

    private void addRunnable(final Runnable r) {
        synchronized (runnables) {
            runnables.add(r);
            runnables.notifyAll();
        }
    }

    String getExportedObject(final DBusInterface i) throws DBusException {
        synchronized (exportedObjects) {
            for (final String s : exportedObjects.keySet()) {
                if (i.equals(exportedObjects.get(s).object.get())) {
                    return s;
                }
            }
        }

        final String s = importedObjects.get(i).objectpath;
        if (null != s) {
            return s;
        }

        throw new DBusException("Not an object exported or imported by this connection");
    }

    abstract DBusInterface getExportedObject(String source, String path) throws DBusException;

    /**
     * Returns a structure with information on the current method call.
     *
     * @return the DBusCallInfo for this method call, or null if we are not in a method call.
     */
    public static DBusCallInfo getCallInfo() {
        DBusCallInfo info;
        synchronized (infomap) {
            info = infomap.get(Thread.currentThread());
        }
        return info;
    }

    /**
     * If set to true the bus will not hold a strong reference to exported objects.
     * If they go out of scope they will automatically be unexported from the bus.
     * The default is to hold a strong reference, which means objects must be
     * explicitly unexported before they will be garbage collected.
     */
    public void setWeakReferences(final boolean weakreferences) {
        this.weakreferences = weakreferences;
    }

    /**
     * Export an object so that its methods can be called on DBus.
     *
     * @param objectpath The path to the object we are exposing. MUST be in slash-notation, like
     *            "/org/freedesktop/Local",
     *            and SHOULD end with a capitalised term. Only one object may be exposed on each path at any one time,
     *            but an object
     *            may be exposed on several paths at once.
     * @param object The object to export.
     * @throws DBusException If the objectpath is already exporting an object.
     *             or if objectpath is incorrectly formatted,
     */
    public void exportObject(final String objectpath, final DBusInterface object) throws DBusException {
        if (null == objectpath || "".equals(objectpath)) {
            throw new DBusException(localize("Must Specify an Object Path"));
        }
        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }
        synchronized (exportedObjects) {
            if (null != exportedObjects.get(objectpath)) {
                throw new DBusException(localize("Object already exported"));
            }
            final ExportedObject eo = new ExportedObject(object, weakreferences);
            exportedObjects.put(objectpath, eo);
            objectTree.add(objectpath, eo, eo.introspectiondata);
        }
    }

    /**
     * Export an object as a fallback object.
     * This object will have it's methods invoked for all paths starting
     * with this object path.
     *
     * @param objectprefix The path below which the fallback handles calls.
     *            MUST be in slash-notation, like "/org/freedesktop/Local",
     * @param object The object to export.
     * @throws DBusException If the objectpath is incorrectly formatted,
     */
    public void addFallback(final String objectprefix, final DBusInterface object) throws DBusException {
        if (null == objectprefix || "".equals(objectprefix)) {
            throw new DBusException(localize("Must Specify an Object Path"));
        }
        if (!objectprefix.matches(OBJECT_REGEX) || objectprefix.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectprefix);
        }
        final ExportedObject eo = new ExportedObject(object, weakreferences);
        fallbackcontainer.add(objectprefix, eo);
    }

    /**
     * Remove a fallback
     *
     * @param objectprefix The prefix to remove the fallback for.
     */
    public void removeFallback(final String objectprefix) {
        fallbackcontainer.remove(objectprefix);
    }

    /**
     * Stop Exporting an object
     *
     * @param objectpath The objectpath to stop exporting.
     */
    public void unExportObject(final String objectpath) {
        synchronized (exportedObjects) {
            exportedObjects.remove(objectpath);
            objectTree.remove(objectpath);
        }
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
     * @return A reference to a remote object.
     * @throws ClassCastException If type is not a sub-type of DBusInterface
     * @throws DBusException If busname or objectpath are incorrectly formatted or type is not in a package.
     */
    /**
     * Send a signal.
     *
     * @param signal The signal to send.
     */
    public void sendSignal(final DBusSignal signal) {
        queueOutgoing(signal);
    }

    void queueOutgoing(final Message m) {
        synchronized (outgoing) {
            if (null == outgoing) {
                return;
            }
            outgoing.add(m);
            logger.debug("Notifying outgoing thread");
            outgoing.notifyAll();
        }
    }

    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     *
     * @param type The signal to watch for.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler(final Class<T> type, final DBusSigHandler<T> handler)
            throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        removeSigHandler(new DBusMatchRule(type), handler);
    }

    /**
     * Remove a Signal Handler.
     * Stops listening for this signal.
     *
     * @param type The signal to watch for.
     * @param object The object emitting the signal.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    public <T extends DBusSignal> void removeSigHandler(final Class<T> type, final DBusInterface object,
            final DBusSigHandler<T> handler) throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        final String objectpath = importedObjects.get(object).objectpath;
        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }
        removeSigHandler(new DBusMatchRule(type, null, objectpath), handler);
    }

    protected abstract <T extends DBusSignal> void removeSigHandler(DBusMatchRule rule, DBusSigHandler<T> handler)
            throws DBusException;

    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type and name.
     *
     * @param type The signal to watch for.
     * @param handler The handler to call when a signal is received.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    @SuppressWarnings("unchecked")
    public <T extends DBusSignal> void addSigHandler(final Class<T> type, final DBusSigHandler<T> handler)
            throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        addSigHandler(new DBusMatchRule(type), (DBusSigHandler<? extends DBusSignal>) handler);
    }

    /**
     * Add a Signal Handler.
     * Adds a signal handler to call when a signal is received which matches the specified type, name and object.
     *
     * @param type The signal to watch for.
     * @param object The object from which the signal will be emitted
     * @param handler The handler to call when a signal is received.
     * @throws DBusException If listening for the signal on the bus failed.
     * @throws ClassCastException If type is not a sub-type of DBusSignal.
     */
    @SuppressWarnings("unchecked")
    public <T extends DBusSignal> void addSigHandler(final Class<T> type, final DBusInterface object,
            final DBusSigHandler<T> handler) throws DBusException {
        if (!DBusSignal.class.isAssignableFrom(type)) {
            throw new ClassCastException(localize("Not A DBus Signal"));
        }
        final String objectpath = importedObjects.get(object).objectpath;
        if (!objectpath.matches(OBJECT_REGEX) || objectpath.length() > MAX_NAME_LENGTH) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }
        addSigHandler(new DBusMatchRule(type, null, objectpath), (DBusSigHandler<? extends DBusSignal>) handler);
    }

    protected abstract <T extends DBusSignal> void addSigHandler(DBusMatchRule rule, DBusSigHandler<T> handler)
            throws DBusException;

    protected <T extends DBusSignal> void addSigHandlerWithoutMatch(final Class<? extends DBusSignal> signal,
            final DBusSigHandler<T> handler) throws DBusException {
        final DBusMatchRule rule = new DBusMatchRule(signal);
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
     */
    public void disconnect() {
        connected = false;
        logger.info("Sending disconnected signal");
        try {
            handleMessage(new org.freedesktop.DBus.Local.Disconnected("/"));
        } catch (final Exception ee) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", ee);
            }
        }

        logger.info("Disconnecting Abstract Connection");
        // run all pending tasks.
        while (runnables.size() > 0) {
            synchronized (runnables) {
                runnables.notifyAll();
            }
        }

        // stop the main thread
        _run = false;

        // unblock the sending thread.
        synchronized (outgoing) {
            outgoing.notifyAll();
        }

        // disconnect from the trasport layer
        try {
            if (null != transport) {
                transport.disconnect();
                transport = null;
            }
        } catch (final IOException IOe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", IOe);
            }
        }

        // stop all the workers
        synchronized (workers) {
            for (final _workerthread t : workers) {
                t.halt();
            }
        }

        // make sure none are blocking on the runnables queue still
        synchronized (runnables) {
            runnables.notifyAll();
        }
    }

    @Override
    public void finalize() {
        disconnect();
    }

    /**
     * Return any DBus error which has been received.
     *
     * @return A DBusExecutionException, or null if no error is pending.
     */
    public DBusExecutionException getError() {
        synchronized (pendingErrors) {
            if (pendingErrors.size() == 0) {
                return null;
            } else {
                return pendingErrors.removeFirst().getException();
            }
        }
    }

    /**
     * Call a method asynchronously and set a callback.
     * This handler will be called in a separate thread.
     *
     * @param object The remote object on which to call the method.
     * @param m The name of the method on the interface to call.
     * @param callback The callback handler.
     * @param parameters The parameters to call the method with.
     */
    @SuppressWarnings("unchecked")
    public <A> void callWithCallback(final DBusInterface object, final String m, final CallbackHandler<A> callback,
            final Object... parameters) {
        logger.trace("callWithCallback({}, {}, {},...)", object, m, callback);
        final Class[] types = new Class[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            types[i] = parameters[i].getClass();
        }
        final RemoteObject ro = importedObjects.get(object);

        try {
            Method me;
            if (null == ro.iface) {
                me = object.getClass().getMethod(m, types);
            } else {
                me = ro.iface.getMethod(m, types);
            }
            RemoteInvocationHandler.executeRemoteMethod(ro, me, this, RemoteInvocationHandler.CALL_TYPE_CALLBACK,
                    callback, parameters);
        } catch (final DBusExecutionException DBEe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", DBEe);
            }
            throw DBEe;
        } catch (final Exception e) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", e);
            }
            throw new DBusExecutionException(e.getMessage());
        }
    }

    /**
     * Call a method asynchronously and get a handle with which to get the reply.
     *
     * @param object The remote object on which to call the method.
     * @param m The name of the method on the interface to call.
     * @param parameters The parameters to call the method with.
     * @return A handle to the call.
     */
    @SuppressWarnings("unchecked")
    public DBusAsyncReply callMethodAsync(final DBusInterface object, final String m, final Object... parameters) {
        final Class<?>[] types = new Class[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            types[i] = parameters[i].getClass();
        }
        final RemoteObject ro = importedObjects.get(object);

        try {
            Method me;
            if (null == ro.iface) {
                me = object.getClass().getMethod(m, types);
            } else {
                me = ro.iface.getMethod(m, types);
            }
            return (DBusAsyncReply) RemoteInvocationHandler.executeRemoteMethod(ro, me, this,
                    RemoteInvocationHandler.CALL_TYPE_ASYNC, null, parameters);
        } catch (final DBusExecutionException DBEe) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", DBEe);
            }
            throw DBEe;
        } catch (final Exception e) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", e);
            }
            throw new DBusExecutionException(e.getMessage());
        }
    }

    private void handleMessage(final MethodCall m) throws DBusException {
        logger.debug("Handling incoming method call: {}", m);

        ExportedObject eo = null;
        Method meth = null;
        Object o = null;

        if (null == m.getInterface() || m.getInterface().equals("org.freedesktop.DBus.Peer")
                || m.getInterface().equals("org.freedesktop.DBus.Introspectable")) {
            synchronized (exportedObjects) {
                eo = exportedObjects.get(null);
            }
            if (null != eo && null == eo.object.get()) {
                unExportObject(null);
                eo = null;
            }
            if (null != eo) {
                meth = eo.methods.get(new MethodTuple(m.getName(), m.getSig()));
            }
            if (null != meth) {
                o = new _globalhandler(m.getPath());
            } else {
                eo = null;
            }
        }
        if (null == o) {
            // now check for specific exported functions

            synchronized (exportedObjects) {
                eo = exportedObjects.get(m.getPath());
            }
            if (null != eo && null == eo.object.get()) {
                logger.info("Unexporting {} implicitly", m.getPath());
                unExportObject(m.getPath());
                eo = null;
            }

            if (null == eo) {
                eo = fallbackcontainer.get(m.getPath());
            }

            if (null == eo) {
                try {
                    queueOutgoing(new Error(m, new DBus.Error.UnknownObject(
                            m.getPath() + localize(" is not an object provided by this process."))));
                } catch (final DBusException DBe) {
                }
                return;
            }
            logger.trace("Searching for method {} with signature {}", m.getName(), m.getSig());
            logger.trace("List of methods on {}: {}", eo, eo.methods);
            meth = eo.methods.get(new MethodTuple(m.getName(), m.getSig()));
            if (null == meth) {
                try {
                    queueOutgoing(new Error(m,
                            new DBus.Error.UnknownMethod(MessageFormat.format(
                                    localize("The method `{0}.{1}' does not exist on this object."),
                                    new Object[] { m.getInterface(), m.getName() }))));
                } catch (final DBusException DBe) {
                }
                return;
            }
            o = eo.object.get();
        }

        // now execute it
        final Method me = meth;
        final Object ob = o;
        final boolean noreply = 1 == (m.getFlags() & Message.Flags.NO_REPLY_EXPECTED);
        final DBusCallInfo info = new DBusCallInfo(m);
        final AbstractConnection conn = this;
        logger.trace("Adding Runnable for method {}", meth);
        addRunnable(new Runnable() {
            private boolean run = false;

            @Override
            public synchronized void run() {
                if (run) {
                    return;
                }
                run = true;
                logger.debug("Running method {} for remote call", me);
                try {
                    assert me != null;
                    final Type[] ts = me.getGenericParameterTypes();
                    m.setArgs(Marshalling.deSerializeParameters(m.getParameters(), ts, conn));
                    logger.trace("Deserialised {} to types {}", m.getParameters(), ts);
                } catch (final Exception e) {
                    if (EXCEPTION_DEBUG) {
                        logger.error("Exception", e);
                    }
                    try {
                        conn.queueOutgoing(new Error(m,
                                new DBus.Error.UnknownMethod(localize("Failure in de-serializing message: ") + e)));
                    } catch (final DBusException DBe) {
                    }
                    return;
                }

                try {
                    synchronized (infomap) {
                        infomap.put(Thread.currentThread(), info);
                    }
                    final Object result;
                    try {
                        logger.trace("Invoking Method: {} on {} with parameters {}", me, ob, m.getParameters());
                        result = me.invoke(ob, m.getParameters());
                    } catch (final InvocationTargetException ITe) {
                        if (EXCEPTION_DEBUG) {
                            logger.error("Exception", ITe);
                        }
                        throw ITe.getCause();
                    }
                    synchronized (infomap) {
                        infomap.remove(Thread.currentThread());
                    }
                    if (!noreply) {
                        MethodReturn reply;
                        if (Void.TYPE.equals(me.getReturnType())) {
                            reply = new MethodReturn(m, null);
                        } else {
                            final StringBuffer sb = new StringBuffer();
                            for (final String s : Marshalling.getDBusType(me.getGenericReturnType())) {
                                sb.append(s);
                            }
                            final Object[] nr = Marshalling.convertParameters(new Object[] { result },
                                    new Type[] { me.getGenericReturnType() }, conn);

                            reply = new MethodReturn(m, sb.toString(), nr);
                        }
                        conn.queueOutgoing(reply);
                    }
                } catch (final DBusExecutionException DBEe) {
                    if (EXCEPTION_DEBUG) {
                        logger.error("Exception", DBEe);
                    }
                    try {
                        conn.queueOutgoing(new Error(m, DBEe));
                    } catch (final DBusException DBe) {
                    }
                } catch (final Throwable e) {
                    if (EXCEPTION_DEBUG) {
                        logger.error("Exception", e);
                    }
                    try {
                        conn.queueOutgoing(new Error(m,
                                new DBusExecutionException(
                                        MessageFormat.format(localize("Error Executing Method {0}.{1}: {2}"),
                                                new Object[] { m.getInterface(), m.getName(), e.getMessage() }))));
                    } catch (final DBusException DBe) {
                    }
                }
            }
        });
    }

    @SuppressWarnings({ "unchecked", "deprecation" })
    private void handleMessage(final DBusSignal s) {
        logger.debug("Handling incoming signal: {}", s);
        final Vector<DBusSigHandler<? extends DBusSignal>> v = new Vector<>();
        synchronized (handledSignals) {
            Vector<DBusSigHandler<? extends DBusSignal>> t;
            t = handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), null, null));
            if (null != t) {
                v.addAll(t);
            }
            t = handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), s.getPath(), null));
            if (null != t) {
                v.addAll(t);
            }
            t = handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), null, s.getSource()));
            if (null != t) {
                v.addAll(t);
            }
            t = handledSignals.get(new SignalTuple(s.getInterface(), s.getName(), s.getPath(), s.getSource()));
            if (null != t) {
                v.addAll(t);
            }
        }
        if (0 == v.size()) {
            return;
        }
        final AbstractConnection conn = this;
        for (final DBusSigHandler<? extends DBusSignal> h : v) {
            logger.trace("Adding Runnable for signal {} with handler {}", s, h);
            addRunnable(new Runnable() {
                private boolean run = false;

                @Override
                public synchronized void run() {
                    if (run) {
                        return;
                    }
                    run = true;
                    try {
                        DBusSignal rs;
                        if (s instanceof DBusSignal.internalsig || s.getClass().equals(DBusSignal.class)) {
                            rs = s.createReal(conn);
                        } else {
                            rs = s;
                        }
                        ((DBusSigHandler<DBusSignal>) h).handle(rs);
                    } catch (final DBusException DBe) {
                        if (EXCEPTION_DEBUG) {
                            logger.error("Exception", DBe);
                        }
                        try {
                            conn.queueOutgoing(new Error(s, new DBusExecutionException("Error handling signal "
                                    + s.getInterface() + "." + s.getName() + ": " + DBe.getMessage())));
                        } catch (final DBusException DBe2) {
                        }
                    }
                }
            });
        }
    }

    private void handleMessage(final Error err) {
        logger.debug("Handling incoming error: {}", err);
        MethodCall m = null;
        if (null == pendingCalls) {
            return;
        }
        synchronized (pendingCalls) {
            if (pendingCalls.contains(err.getReplySerial())) {
                m = pendingCalls.remove(err.getReplySerial());
            }
        }
        if (null != m) {
            m.setReply(err);
            CallbackHandler cbh = null;
            DBusAsyncReply asr = null;
            synchronized (pendingCallbacks) {
                cbh = pendingCallbacks.remove(m);
                logger.trace("{} = pendingCallbacks.remove({})", cbh, m);
                asr = pendingCallbackReplys.remove(m);
            }
            // queue callback for execution
            if (null != cbh) {
                final CallbackHandler fcbh = cbh;
                logger.trace("Adding Error Runnable with callback handler {}", fcbh);
                addRunnable(new Runnable() {
                    private boolean run = false;

                    @Override
                    public synchronized void run() {
                        if (run) {
                            return;
                        }
                        run = true;
                        try {
                            logger.trace("Running Error Callback for {}", err);
                            final DBusCallInfo info = new DBusCallInfo(err);
                            synchronized (infomap) {
                                infomap.put(Thread.currentThread(), info);
                            }

                            fcbh.handleError(err.getException());
                            synchronized (infomap) {
                                infomap.remove(Thread.currentThread());
                            }

                        } catch (final Exception e) {
                            if (EXCEPTION_DEBUG) {
                                logger.error("Exception", e);
                            }
                        }
                    }
                });
            }

        } else {
            synchronized (pendingErrors) {
                pendingErrors.addLast(err);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(final MethodReturn mr) {
        logger.debug("Handling incoming method return: {}", mr);
        MethodCall m = null;
        if (null == pendingCalls) {
            return;
        }
        synchronized (pendingCalls) {
            if (pendingCalls.contains(mr.getReplySerial())) {
                m = pendingCalls.remove(mr.getReplySerial());
            }
        }
        if (null != m) {
            m.setReply(mr);
            mr.setCall(m);
            CallbackHandler cbh = null;
            DBusAsyncReply asr = null;
            synchronized (pendingCallbacks) {
                cbh = pendingCallbacks.remove(m);
                logger.trace("{} = pendingCallbacks.remove({})", cbh, m);
                asr = pendingCallbackReplys.remove(m);
            }
            // queue callback for execution
            if (null != cbh) {
                final CallbackHandler fcbh = cbh;
                final DBusAsyncReply fasr = asr;
                logger.trace("Adding Runnable for method {} with callback handler {}", fasr.getMethod(), fcbh);
                addRunnable(new Runnable() {
                    private boolean run = false;

                    @Override
                    public synchronized void run() {
                        if (run) {
                            return;
                        }
                        run = true;
                        try {
                            logger.trace("Running Callback for {}", mr);
                            final DBusCallInfo info = new DBusCallInfo(mr);
                            synchronized (infomap) {
                                infomap.put(Thread.currentThread(), info);
                            }

                            fcbh.handle(RemoteInvocationHandler.convertRV(mr.getSig(), mr.getParameters(),
                                    fasr.getMethod(), fasr.getConnection()));
                            synchronized (infomap) {
                                infomap.remove(Thread.currentThread());
                            }

                        } catch (final Exception e) {
                            if (EXCEPTION_DEBUG) {
                                logger.error("Exception", e);
                            }
                        }
                    }
                });
            }

        } else {
            try {
                queueOutgoing(new Error(mr, new DBusExecutionException(
                        localize("Spurious reply. No message with the given serial id was awaiting a reply."))));
            } catch (final DBusException DBe) {
            }
        }
    }

    protected void sendMessage(final Message m) {
        try {
            if (!connected) {
                throw new NotConnected(localize("Disconnected"));
            }
            if (m instanceof DBusSignal) {
                ((DBusSignal) m).appendbody(this);
            }

            if (m instanceof MethodCall) {
                if (0 == (m.getFlags() & Message.Flags.NO_REPLY_EXPECTED)) {
                    if (null == pendingCalls) {
                        ((MethodCall) m).setReply(
                                new Error("org.freedesktop.DBus.Local", "org.freedesktop.DBus.Local.Disconnected", 0,
                                        "s", new Object[] { localize("Disconnected") }));
                    } else {
                        synchronized (pendingCalls) {
                            pendingCalls.put(m.getSerial(), (MethodCall) m);
                        }
                    }
                }
            }

            transport.mout.writeMessage(m);

        } catch (final Exception e) {
            if (EXCEPTION_DEBUG) {
                logger.error("Exception", e);
            }
            if (m instanceof MethodCall && e instanceof NotConnected) {
                try {
                    ((MethodCall) m)
                            .setReply(new Error("org.freedesktop.DBus.Local", "org.freedesktop.DBus.Local.Disconnected",
                                    0, "s", new Object[] { localize("Disconnected") }));
                } catch (final DBusException DBe) {
                }
            }
            if (m instanceof MethodCall && e instanceof DBusExecutionException) {
                try {
                    ((MethodCall) m).setReply(new Error(m, e));
                } catch (final DBusException DBe) {
                }
            } else if (m instanceof MethodCall) {
                try {
                    logger.info("Setting reply to {} as an error", m);
                    ((MethodCall) m).setReply(new Error(m,
                            new DBusExecutionException(localize("Message Failed to Send: ") + e.getMessage())));
                } catch (final DBusException DBe) {
                }
            } else if (m instanceof MethodReturn) {
                try {
                    transport.mout.writeMessage(new Error(m, e));
                } catch (final IOException IOe) {
                    if (EXCEPTION_DEBUG) {
                        logger.error("Exception", IOe);
                    }
                } catch (final DBusException DBe) {
                    if (EXCEPTION_DEBUG) {
                        logger.error("Exception", DBe);
                    }
                }
            }
            if (e instanceof IOException) {
                disconnect();
            }
        }
    }

    private Message readIncoming() throws DBusException {
        if (!connected) {
            throw new NotConnected(localize("No transport present"));
        }
        Message m = null;
        try {
            m = transport.min.readMessage();
        } catch (final IOException IOe) {
            throw new FatalDBusException(IOe.getMessage());
        }
        return m;
    }

    /**
     * Returns the address this connection is connected to.
     */
    public BusAddress getAddress() throws ParseException {
        return new BusAddress(addr);
    }
}
