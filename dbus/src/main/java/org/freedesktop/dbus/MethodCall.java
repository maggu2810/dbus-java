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

import java.util.Vector;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cx.ath.matthew.utils.Hexdump;

public class MethodCall extends Message {
    private final Logger logger = LoggerFactory.getLogger(MethodCall.class);

    MethodCall() {
    }

    public MethodCall(final String dest, final String path, final String iface, final String member, final byte flags,
            final String sig, final Object... args) throws DBusException {
        this(null, dest, path, iface, member, flags, sig, args);
    }

    public MethodCall(final String source, final String dest, final String path, final String iface,
            final String member, final byte flags, final String sig, final Object... args) throws DBusException {
        super(Message.Endian.BIG, Message.MessageType.METHOD_CALL, flags);

        if (null == member || null == path) {
            throw new MessageFormatException(
                    localize("Must specify destination, path and function name to MethodCalls."));
        }
        headers.put(Message.HeaderField.PATH, path);
        headers.put(Message.HeaderField.MEMBER, member);

        final Vector<Object> hargs = new Vector<>();

        hargs.add(new Object[] { Message.HeaderField.PATH, new Object[] { ArgumentType.OBJECT_PATH_STRING, path } });

        if (null != source) {
            headers.put(Message.HeaderField.SENDER, source);
            hargs.add(new Object[] { Message.HeaderField.SENDER, new Object[] { ArgumentType.STRING_STRING, source } });
        }

        if (null != dest) {
            headers.put(Message.HeaderField.DESTINATION, dest);
            hargs.add(new Object[] { Message.HeaderField.DESTINATION,
                    new Object[] { ArgumentType.STRING_STRING, dest } });
        }

        if (null != iface) {
            hargs.add(
                    new Object[] { Message.HeaderField.INTERFACE, new Object[] { ArgumentType.STRING_STRING, iface } });
            headers.put(Message.HeaderField.INTERFACE, iface);
        }

        hargs.add(new Object[] { Message.HeaderField.MEMBER, new Object[] { ArgumentType.STRING_STRING, member } });

        if (null != sig) {
            logger.debug("Appending arguments with signature: {}", sig);
            hargs.add(new Object[] { Message.HeaderField.SIGNATURE,
                    new Object[] { ArgumentType.SIGNATURE_STRING, sig } });
            headers.put(Message.HeaderField.SIGNATURE, sig);
            setArgs(args);
        }

        final byte[] blen = new byte[4];
        appendBytes(blen);
        append("ua(yv)", serial, hargs.toArray());
        pad((byte) 8);

        final long c = bytecounter;
        if (null != sig) {
            append(sig, args);
        }
        logger.debug("Appended body, type: {} start: {} end: {} size: {}", sig, c, bytecounter, +(bytecounter - c));
        marshallint(bytecounter - c, blen, 0, 4);
        logger.info("marshalled size ({}): {}", blen, Hexdump.format(blen));
    }

    private static long REPLY_WAIT_TIMEOUT = 20000;

    /**
     * Set the default timeout for method calls.
     * Default is 20s.
     *
     * @param timeout New timeout in ms.
     */
    public static void setDefaultTimeout(final long timeout) {
        REPLY_WAIT_TIMEOUT = timeout;
    }

    Message reply = null;

    public synchronized boolean hasReply() {
        return null != reply;
    }

    /**
     * Block (if neccessary) for a reply.
     *
     * @return The reply to this MethodCall, or null if a timeout happens.
     * @param timeout The length of time to block before timing out (ms).
     */
    public synchronized Message getReply(final long timeout) {
        logger.trace("Blocking on {}", this);
        if (null != reply) {
            return reply;
        }
        try {
            wait(timeout);
            return reply;
        } catch (final InterruptedException Ie) {
            return reply;
        }
    }

    /**
     * Block (if neccessary) for a reply.
     * Default timeout is 20s, or can be configured with setDefaultTimeout()
     *
     * @return The reply to this MethodCall, or null if a timeout happens.
     */
    public synchronized Message getReply() {
        logger.trace("Blocking on {}", this);
        if (null != reply) {
            return reply;
        }
        try {
            wait(REPLY_WAIT_TIMEOUT);
            return reply;
        } catch (final InterruptedException Ie) {
            return reply;
        }
    }

    protected synchronized void setReply(final Message reply) {
        logger.trace("Setting reply to {} to {}", this, reply);
        this.reply = reply;
        notifyAll();
    }

}
