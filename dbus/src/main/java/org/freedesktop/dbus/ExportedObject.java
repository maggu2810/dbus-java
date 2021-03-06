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

import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

class ExportedObject {
    private String getAnnotations(final AnnotatedElement c) {
        String ans = "";
        for (final Annotation a : c.getDeclaredAnnotations()) {
            final Class<? extends Annotation> t = a.annotationType();
            String value = "";
            try {
                final Method m = t.getMethod("value");
                value = m.invoke(a).toString();
            } catch (final NoSuchMethodException NSMe) {
            } catch (final InvocationTargetException ITe) {
            } catch (final IllegalAccessException IAe) {
            }

            ans += "  <annotation name=\"" + AbstractConnection.dollar_pattern.matcher(t.getName()).replaceAll(".")
                    + "\" value=\"" + value + "\" />\n";
        }
        return ans;
    }

    private Map<MethodTuple, Method> getExportedMethods(final Class<?> c) throws DBusException {
        if (DBusInterface.class.equals(c)) {
            return new HashMap<>();
        }
        final Map<MethodTuple, Method> m = new HashMap<>();
        for (final Class<?> i : c.getInterfaces()) {
            if (DBusInterface.class.equals(i)) {
                // add this class's public methods
                if (null != c.getAnnotation(DBusInterfaceName.class)) {
                    final String name = c.getAnnotation(DBusInterfaceName.class).value();
                    introspectiondata += " <interface name=\"" + name + "\">\n";
                    DBusSignal.addInterfaceMap(c.getName(), name);
                } else {
                    // don't let people export things which don't have a
                    // valid D-Bus interface name
                    if (c.getName().equals(c.getSimpleName())) {
                        throw new DBusException(localize("DBusInterfaces cannot be declared outside a package"));
                    }
                    if (c.getName().length() > DBusConnection.MAX_NAME_LENGTH) {
                        throw new DBusException(localize(
                                "Introspected interface name exceeds 255 characters. Cannot export objects of type ")
                                + c.getName());
                    } else {
                        introspectiondata += " <interface name=\""
                                + AbstractConnection.dollar_pattern.matcher(c.getName()).replaceAll(".") + "\">\n";
                    }
                }
                introspectiondata += getAnnotations(c);
                for (final Method meth : c.getDeclaredMethods()) {
                    if (Modifier.isPublic(meth.getModifiers())) {
                        String ms = "";
                        String name;
                        if (meth.isAnnotationPresent(DBusMemberName.class)) {
                            name = meth.getAnnotation(DBusMemberName.class).value();
                        } else {
                            name = meth.getName();
                        }
                        if (name.length() > DBusConnection.MAX_NAME_LENGTH) {
                            throw new DBusException(localize(
                                    "Introspected method name exceeds 255 characters. Cannot export objects with method ")
                                    + name);
                        }
                        introspectiondata += "  <method name=\"" + name + "\" >\n";
                        introspectiondata += getAnnotations(meth);
                        for (final Class<?> ex : meth.getExceptionTypes()) {
                            if (DBusExecutionException.class.isAssignableFrom(ex)) {
                                introspectiondata += "   <annotation name=\"org.freedesktop.DBus.Method.Error\" value=\""
                                        + AbstractConnection.dollar_pattern.matcher(ex.getName()).replaceAll(".")
                                        + "\" />\n";
                            }
                        }
                        for (final Type pt : meth.getGenericParameterTypes()) {
                            for (final String s : Marshalling.getDBusType(pt)) {
                                introspectiondata += "   <arg type=\"" + s + "\" direction=\"in\"/>\n";
                                ms += s;
                            }
                        }
                        if (!Void.TYPE.equals(meth.getGenericReturnType())) {
                            if (Tuple.class.isAssignableFrom(meth.getReturnType())) {
                                final ParameterizedType tc = (ParameterizedType) meth.getGenericReturnType();
                                final Type[] ts = tc.getActualTypeArguments();

                                for (final Type t : ts) {
                                    if (t != null) {
                                        for (final String s : Marshalling.getDBusType(t)) {
                                            introspectiondata += "   <arg type=\"" + s + "\" direction=\"out\"/>\n";
                                        }
                                    }
                                }
                            } else if (Object[].class.equals(meth.getGenericReturnType())) {
                                throw new DBusException(
                                        localize("Return type of Object[] cannot be introspected properly"));
                            } else {
                                for (final String s : Marshalling.getDBusType(meth.getGenericReturnType())) {
                                    introspectiondata += "   <arg type=\"" + s + "\" direction=\"out\"/>\n";
                                }
                            }
                        }
                        introspectiondata += "  </method>\n";
                        m.put(new MethodTuple(name, ms), meth);
                    }
                }
                for (final Class<?> sig : c.getDeclaredClasses()) {
                    if (DBusSignal.class.isAssignableFrom(sig)) {
                        String name;
                        if (sig.isAnnotationPresent(DBusMemberName.class)) {
                            name = sig.getAnnotation(DBusMemberName.class).value();
                            DBusSignal.addSignalMap(sig.getSimpleName(), name);
                        } else {
                            name = sig.getSimpleName();
                        }
                        if (name.length() > DBusConnection.MAX_NAME_LENGTH) {
                            throw new DBusException(localize(
                                    "Introspected signal name exceeds 255 characters. Cannot export objects with signals of type ")
                                    + name);
                        }
                        introspectiondata += "  <signal name=\"" + name + "\">\n";
                        final Constructor<?> con = sig.getConstructors()[0];
                        final Type[] ts = con.getGenericParameterTypes();
                        for (int j = 1; j < ts.length; j++) {
                            for (final String s : Marshalling.getDBusType(ts[j])) {
                                introspectiondata += "   <arg type=\"" + s + "\" direction=\"out\" />\n";
                            }
                        }
                        introspectiondata += getAnnotations(sig);
                        introspectiondata += "  </signal>\n";

                    }
                }
                introspectiondata += " </interface>\n";
            } else {
                // recurse
                m.putAll(getExportedMethods(i));
            }
        }
        return m;
    }

    Map<MethodTuple, Method> methods;
    Reference<DBusInterface> object;
    String introspectiondata;

    public ExportedObject(final DBusInterface object, final boolean weakreferences) throws DBusException {
        if (weakreferences) {
            this.object = new WeakReference<>(object);
        } else {
            this.object = new StrongReference<>(object);
        }
        introspectiondata = "";
        methods = getExportedMethods(object.getClass());
        introspectiondata += " <interface name=\"org.freedesktop.DBus.Introspectable\">\n"
                + "  <method name=\"Introspect\">\n" + "   <arg type=\"s\" direction=\"out\"/>\n" + "  </method>\n"
                + " </interface>\n";
        introspectiondata += " <interface name=\"org.freedesktop.DBus.Peer\">\n" + "  <method name=\"Ping\">\n"
                + "  </method>\n" + " </interface>\n";
    }
}
