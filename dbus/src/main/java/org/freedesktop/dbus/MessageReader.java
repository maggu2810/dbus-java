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

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.text.MessageFormat;

import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.MessageProtocolVersionException;
import org.freedesktop.dbus.exceptions.MessageTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cx.ath.matthew.utils.Hexdump;

public class MessageReader {
    private final Logger logger = LoggerFactory.getLogger(MessageReader.class);

    private final InputStream in;
    private byte[] buf = null;
    private byte[] tbuf = null;
    private byte[] header = null;
    private byte[] body = null;
    private final int[] len = new int[4];

    public MessageReader(final InputStream in) {
        this.in = new BufferedInputStream(in);
    }

    public Message readMessage() throws IOException, DBusException {
        int rv;
        /* Read the 12 byte fixed header, retrying as neccessary */
        if (null == buf) {
            buf = new byte[12];
            len[0] = 0;
        }
        if (len[0] < 12) {
            try {
                rv = in.read(buf, len[0], 12 - len[0]);
            } catch (final SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv) {
                throw new EOFException(localize("Underlying transport returned EOF"));
            }
            len[0] += rv;
        }
        if (len[0] == 0) {
            return null;
        }
        if (len[0] < 12) {
            logger.debug("Only got {} of 12 bytes of header", len[0]);
            return null;
        }

        /* Parse the details from the header */
        final byte endian = buf[0];
        final byte type = buf[1];
        final byte protover = buf[3];
        if (protover > Message.PROTOCOL) {
            buf = null;
            throw new MessageProtocolVersionException(
                    MessageFormat.format(localize("Protocol version {0} is unsupported"), new Object[] { protover }));
        }

        /* Read the length of the variable header */
        if (null == tbuf) {
            tbuf = new byte[4];
            len[1] = 0;
        }
        if (len[1] < 4) {
            try {
                rv = in.read(tbuf, len[1], 4 - len[1]);
            } catch (final SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv) {
                throw new EOFException(localize("Underlying transport returned EOF"));
            }
            len[1] += rv;
        }
        if (len[1] < 4) {
            logger.debug("Only got {} of 4 bytes of header", len[1]);
            return null;
        }

        /* Parse the variable header length */
        int headerlen = 0;
        if (null == header) {
            headerlen = (int) Message.demarshallint(tbuf, 0, endian, 4);
            if (0 != headerlen % 8) {
                headerlen += 8 - headerlen % 8;
            }
        } else {
            headerlen = header.length - 8;
        }

        /* Read the variable header */
        if (null == header) {
            header = new byte[headerlen + 8];
            System.arraycopy(tbuf, 0, header, 0, 4);
            len[2] = 0;
        }
        if (len[2] < headerlen) {
            try {
                rv = in.read(header, 8 + len[2], headerlen - len[2]);
            } catch (final SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv) {
                throw new EOFException(localize("Underlying transport returned EOF"));
            }
            len[2] += rv;
        }
        if (len[2] < headerlen) {
            logger.debug("Only got {} of {} bytes of header", len[2], headerlen);
            return null;
        }

        /* Read the body */
        int bodylen = 0;
        if (null == body) {
            bodylen = (int) Message.demarshallint(buf, 4, endian, 4);
        }
        if (null == body) {
            body = new byte[bodylen];
            len[3] = 0;
        }
        if (len[3] < body.length) {
            try {
                rv = in.read(body, len[3], body.length - len[3]);
            } catch (final SocketTimeoutException STe) {
                return null;
            }
            if (-1 == rv) {
                throw new EOFException(localize("Underlying transport returned EOF"));
            }
            len[3] += rv;
        }
        if (len[3] < body.length) {
            logger.debug("Only got {} of {} bytes of body", len[3], body.length);
            return null;
        }

        Message m;
        switch (type) {
            case Message.MessageType.METHOD_CALL:
                m = new MethodCall();
                break;
            case Message.MessageType.METHOD_RETURN:
                m = new MethodReturn();
                break;
            case Message.MessageType.SIGNAL:
                m = new DBusSignal();
                break;
            case Message.MessageType.ERROR:
                m = new Error();
                break;
            default:
                throw new MessageTypeException(
                        MessageFormat.format(localize("Message type {0} unsupported"), new Object[] { type }));
        }
        if (logger.isTraceEnabled()) {
            logger.trace("{}", Hexdump.format(buf));
            logger.trace("{}", Hexdump.format(tbuf));
            logger.trace("{}", Hexdump.format(header));
            logger.trace("{}", Hexdump.format(body));
        }
        try {
            m.populate(buf, header, body);
        } catch (final DBusException DBe) {
            if (AbstractConnection.EXCEPTION_DEBUG) {
                logger.error("Exception", DBe);
            }
            buf = null;
            tbuf = null;
            body = null;
            header = null;
            throw DBe;
        } catch (final RuntimeException Re) {
            if (AbstractConnection.EXCEPTION_DEBUG) {
                logger.error("Exception", Re);
            }
            buf = null;
            tbuf = null;
            body = null;
            header = null;
            throw Re;
        }
        logger.info("=> {}", m);
        buf = null;
        tbuf = null;
        body = null;
        header = null;
        return m;
    }

    public void close() throws IOException {
        logger.info("Closing Message Reader");
        in.close();
    }
}
