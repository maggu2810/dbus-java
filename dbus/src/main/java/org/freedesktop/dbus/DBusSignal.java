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

import java.lang.reflect.Constructor;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DBusSignal extends Message {
    private final Logger logger = LoggerFactory.getLogger(DBusSignal.class);

    DBusSignal() {
    }

    public DBusSignal(final String source, final String path, final String iface, final String member, final String sig,
            final Object... args) throws DBusException {
        super(Message.Endian.BIG, Message.MessageType.SIGNAL, (byte) 0);

        if (null == path || null == member || null == iface) {
            throw new MessageFormatException(localize("Must specify object path, interface and signal name to Signals."));
        }
        headers.put(Message.HeaderField.PATH, path);
        headers.put(Message.HeaderField.MEMBER, member);
        headers.put(Message.HeaderField.INTERFACE, iface);

        final Vector<Object> hargs = new Vector<Object>();
        hargs.add(new Object[] { Message.HeaderField.PATH, new Object[] { ArgumentType.OBJECT_PATH_STRING, path } });
        hargs.add(new Object[] { Message.HeaderField.INTERFACE, new Object[] { ArgumentType.STRING_STRING, iface } });
        hargs.add(new Object[] { Message.HeaderField.MEMBER, new Object[] { ArgumentType.STRING_STRING, member } });

        if (null != source) {
            headers.put(Message.HeaderField.SENDER, source);
            hargs.add(new Object[] { Message.HeaderField.SENDER, new Object[] { ArgumentType.STRING_STRING, source } });
        }

        if (null != sig) {
            hargs.add(new Object[] { Message.HeaderField.SIGNATURE,
                    new Object[] { ArgumentType.SIGNATURE_STRING, sig } });
            headers.put(Message.HeaderField.SIGNATURE, sig);
            setArgs(args);
        }

        blen = new byte[4];
        appendBytes(blen);
        append("ua(yv)", ++serial, hargs.toArray());
        pad((byte) 8);

        final long c = bytecounter;
        if (null != sig) {
            append(sig, args);
        }
        marshallint(bytecounter - c, blen, 0, 4);
        bodydone = true;
    }

    static class internalsig extends DBusSignal {
        public internalsig(final String source, final String objectpath, final String type, final String name,
                final String sig, final Object[] parameters, final long serial) throws DBusException {
            super(source, objectpath, type, name, sig, parameters, serial);
        }
    }

    private static Map<Class<? extends DBusSignal>, Type[]> typeCache = new HashMap<Class<? extends DBusSignal>, Type[]>();
    private static Map<String, Class<? extends DBusSignal>> classCache = new HashMap<String, Class<? extends DBusSignal>>();
    private static Map<Class<? extends DBusSignal>, Constructor<? extends DBusSignal>> conCache = new HashMap<Class<? extends DBusSignal>, Constructor<? extends DBusSignal>>();
    private static Map<String, String> signames = new HashMap<String, String>();
    private static Map<String, String> intnames = new HashMap<String, String>();
    private Class<? extends DBusSignal> c;
    private boolean bodydone = false;
    private byte[] blen;

    static void addInterfaceMap(final String java, final String dbus) {
        intnames.put(dbus, java);
    }

    static void addSignalMap(final String java, final String dbus) {
        signames.put(dbus, java);
    }

    static DBusSignal createSignal(final Class<? extends DBusSignal> c, final String source, final String objectpath,
            final String sig, final long serial, final Object... parameters) throws DBusException {
        String type = "";
        if (null != c.getEnclosingClass()) {
            if (null != c.getEnclosingClass().getAnnotation(DBusInterfaceName.class)) {
                type = c.getEnclosingClass().getAnnotation(DBusInterfaceName.class).value();
            } else {
                type = AbstractConnection.dollar_pattern.matcher(c.getEnclosingClass().getName()).replaceAll(".");
            }

        } else {
            throw new DBusException(localize(
                    "Signals must be declared as a member of a class implementing DBusInterface which is the member of a package."));
        }
        final DBusSignal s = new internalsig(source, objectpath, type, c.getSimpleName(), sig, parameters, serial);
        s.c = c;
        return s;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends DBusSignal> createSignalClass(final String intname, final String signame)
            throws DBusException {
        String name = intname + '$' + signame;
        Class<? extends DBusSignal> c = classCache.get(name);
        if (null == c) {
            c = DBusMatchRule.getCachedSignalType(name);
        }
        if (null != c) {
            return c;
        }
        do {
            try {
                c = (Class<? extends DBusSignal>) Class.forName(name);
            } catch (final ClassNotFoundException CNFe) {
            }
            name = name.replaceAll("\\.([^\\.]*)$", "\\$$1");
        } while (null == c && name.matches(".*\\..*"));
        if (null == c) {
            throw new DBusException(localize("Could not create class from signal ") + intname + '.' + signame);
        }
        classCache.put(name, c);
        return c;
    }

    @SuppressWarnings("unchecked")
    DBusSignal createReal(final AbstractConnection conn) throws DBusException {
        String intname = intnames.get(getInterface());
        String signame = signames.get(getName());
        if (null == intname) {
            intname = getInterface();
        }
        if (null == signame) {
            signame = getName();
        }
        if (null == c) {
            c = createSignalClass(intname, signame);
        }
        logger.debug("Converting signal to type: {}", c);
        Type[] types = typeCache.get(c);
        Constructor<? extends DBusSignal> con = conCache.get(c);
        if (null == types) {
            con = (Constructor<? extends DBusSignal>) c.getDeclaredConstructors()[0];
            conCache.put(c, con);
            final Type[] ts = con.getGenericParameterTypes();
            types = new Type[ts.length - 1];
            for (int i = 1; i < ts.length; i++) {
                if (ts[i] instanceof TypeVariable) {
                    for (final Type b : ((TypeVariable<GenericDeclaration>) ts[i]).getBounds()) {
                        types[i - 1] = b;
                    }
                } else {
                    types[i - 1] = ts[i];
                }
            }
            typeCache.put(c, types);
        }

        try {
            DBusSignal s;
            final Object[] args = Marshalling.deSerializeParameters(getParameters(), types, conn);
            if (null == args) {
                s = con.newInstance(getPath());
            } else {
                final Object[] params = new Object[args.length + 1];
                params[0] = getPath();
                System.arraycopy(args, 0, params, 1, args.length);

                logger.debug("Creating signal of type {} with parameters {}", c, params);
                s = con.newInstance(params);
            }
            s.headers = headers;
            s.wiredata = wiredata;
            s.bytecounter = wiredata.length;
            return s;
        } catch (final Exception e) {
            if (AbstractConnection.EXCEPTION_DEBUG) {
                logger.error("Exception", e);
            }
            throw new DBusException(e.getMessage());
        }
    }

    /**
     * Create a new signal.
     * This contructor MUST be called by all sub classes.
     *
     * @param objectpath The path to the object this is emitted from.
     * @param args The parameters of the signal.
     * @throws DBusException This is thrown if the subclass is incorrectly defined.
     */
    @SuppressWarnings("unchecked")
    protected DBusSignal(final String objectpath, final Object... args) throws DBusException {
        super(Message.Endian.BIG, Message.MessageType.SIGNAL, (byte) 0);

        if (!objectpath.matches(AbstractConnection.OBJECT_REGEX)) {
            throw new DBusException(localize("Invalid object path: ") + objectpath);
        }

        final Class<? extends DBusSignal> tc = getClass();
        String member;
        if (tc.isAnnotationPresent(DBusMemberName.class)) {
            member = tc.getAnnotation(DBusMemberName.class).value();
        } else {
            member = tc.getSimpleName();
        }
        String iface = null;
        final Class<? extends Object> enc = tc.getEnclosingClass();
        if (null == enc || !DBusInterface.class.isAssignableFrom(enc) || enc.getName().equals(enc.getSimpleName())) {
            throw new DBusException(localize(
                    "Signals must be declared as a member of a class implementing DBusInterface which is the member of a package."));
        } else if (null != enc.getAnnotation(DBusInterfaceName.class)) {
            iface = enc.getAnnotation(DBusInterfaceName.class).value();
        } else {
            iface = AbstractConnection.dollar_pattern.matcher(enc.getName()).replaceAll(".");
        }

        headers.put(Message.HeaderField.PATH, objectpath);
        headers.put(Message.HeaderField.MEMBER, member);
        headers.put(Message.HeaderField.INTERFACE, iface);

        final Vector<Object> hargs = new Vector<Object>();
        hargs.add(new Object[] { Message.HeaderField.PATH,
                new Object[] { ArgumentType.OBJECT_PATH_STRING, objectpath } });
        hargs.add(new Object[] { Message.HeaderField.INTERFACE, new Object[] { ArgumentType.STRING_STRING, iface } });
        hargs.add(new Object[] { Message.HeaderField.MEMBER, new Object[] { ArgumentType.STRING_STRING, member } });

        String sig = null;
        if (0 < args.length) {
            try {
                Type[] types = typeCache.get(tc);
                if (null == types) {
                    final Constructor<? extends DBusSignal> con = (Constructor<? extends DBusSignal>) tc
                            .getDeclaredConstructors()[0];
                    conCache.put(tc, con);
                    final Type[] ts = con.getGenericParameterTypes();
                    types = new Type[ts.length - 1];
                    for (int i = 1; i <= types.length; i++) {
                        if (ts[i] instanceof TypeVariable) {
                            types[i - 1] = ((TypeVariable<GenericDeclaration>) ts[i]).getBounds()[0];
                        } else {
                            types[i - 1] = ts[i];
                        }
                    }
                    typeCache.put(tc, types);
                }
                sig = Marshalling.getDBusType(types);
                hargs.add(new Object[] { Message.HeaderField.SIGNATURE,
                        new Object[] { ArgumentType.SIGNATURE_STRING, sig } });
                headers.put(Message.HeaderField.SIGNATURE, sig);
                setArgs(args);
            } catch (final Exception e) {
                if (AbstractConnection.EXCEPTION_DEBUG) {
                    logger.error("Exception", e);
                }
                throw new DBusException(localize("Failed to add signal parameters: ") + e.getMessage());
            }
        }

        blen = new byte[4];
        appendBytes(blen);
        append("ua(yv)", ++serial, hargs.toArray());
        pad((byte) 8);
    }

    void appendbody(final AbstractConnection conn) throws DBusException {
        if (bodydone) {
            return;
        }

        final Type[] types = typeCache.get(getClass());
        final Object[] args = Marshalling.convertParameters(getParameters(), types, conn);
        setArgs(args);
        final String sig = getSig();

        final long c = bytecounter;
        if (null != args && 0 < args.length) {
            append(sig, args);
        }
        marshallint(bytecounter - c, blen, 0, 4);
        bodydone = true;
    }
}
