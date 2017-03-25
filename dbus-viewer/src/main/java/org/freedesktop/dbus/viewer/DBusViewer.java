/*
   D-Bus Java Viewer
   Copyright (c) 2006 Peter Cox

   This program is free software; you can redistribute it and/or modify it
   under the terms of either the GNU Lesser General Public License Version 2 or the
   Academic Free Licence Version 2.1.

   Full licence texts are included in the COPYING file with this program.
*/

package org.freedesktop.dbus.viewer;

import static org.freedesktop.dbus.Gettext.localize;

import java.awt.BorderLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.TableModel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.freedesktop.DBus;
import org.freedesktop.DBus.Introspectable;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * A viewer for DBus
 *
 * goal is to replicate the functionality of kdbus with Java smarts
 *
 * @author pete
 * @since 29/01/2006
 */
public class DBusViewer {
    private static final Map<String, Integer> CONNECTION_TYPES = new HashMap<>();

    static {
        CONNECTION_TYPES.put("System", DBusConnection.SYSTEM);
        CONNECTION_TYPES.put("Session", DBusConnection.SESSION);
    }

    /**
     * Create the DBusViewer
     *
     * @param connectionTypes The map of connection types
     */
    public DBusViewer(final Map<String, Integer> connectionTypes) {
        connections = new ArrayList<>(connectionTypes.size());

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            @SuppressWarnings("synthetic-access")
            public void run() {

                final JTabbedPane tabbedPane = new JTabbedPane();
                addTabs(tabbedPane, connectionTypes);
                final JFrame frame = new JFrame("Dbus Viewer");
                frame.setContentPane(tabbedPane);
                frame.setSize(600, 400);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosing(final WindowEvent e) {
                        frame.dispose();
                        for (final DBusConnection connection : connections) {
                            connection.disconnect();
                        }
                        System.exit(0);
                    }
                });
                frame.setVisible(true);
            }
        });
    }

    private final List<DBusConnection> connections;

    /**
     * @param args
     */
    public static void main(final String[] args) {
        new DBusViewer(CONNECTION_TYPES);
    }

    /**
     * Add tabs for each supplied connection type
     *
     * @param tabbedPane The tabbed pane
     * @param connectionTypes The connection
     */
    private void addTabs(final JTabbedPane tabbedPane, final Map<String, Integer> connectionTypes) {
        for (final String key : connectionTypes.keySet()) {
            final JLabel label = new JLabel(localize("Processing DBus for ") + key);
            tabbedPane.addTab(key, label);
        }
        final Runnable loader = new Runnable() {
            @Override
            @SuppressWarnings("synthetic-access")
            public void run() {
                final boolean users = true, owners = true;
                for (final String key : connectionTypes.keySet()) {
                    try {
                        final DBusConnection conn = DBusConnection.getConnection(connectionTypes.get(key));
                        connections.add(conn);

                        final TableModel tableModel = listDBusConnection(users, owners, conn);

                        SwingUtilities.invokeLater(() -> {
                            final int index = tabbedPane.indexOfTab(key);
                            final JTable table = new JTable(tableModel);

                            final JScrollPane scrollPane = new JScrollPane(table);

                            final JPanel tab = new JPanel(new BorderLayout());
                            tab.add(scrollPane, BorderLayout.CENTER);

                            final JPanel southPanel = new JPanel();
                            final JButton button = new JButton(new IntrospectAction(table));
                            southPanel.add(button);

                            tab.add(southPanel, BorderLayout.SOUTH);

                            tabbedPane.setComponentAt(index, tab);

                        });
                    } catch (final DBusException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            final int index = tabbedPane.indexOfTab(key);
                            final JLabel label = (JLabel) tabbedPane.getComponentAt(index);
                            label.setText(
                                    localize("Could not load Dbus information for ") + key + ":" + e.getMessage());
                        });
                    } catch (final DBusExecutionException e) {
                        e.printStackTrace();
                        SwingUtilities.invokeLater(() -> {
                            final int index = tabbedPane.indexOfTab(key);
                            final JLabel label = (JLabel) tabbedPane.getComponentAt(index);
                            label.setText(
                                    localize("Could not load Dbus information for ") + key + ":" + e.getMessage());
                        });
                    }
                }
            }
        };
        final Thread thread = new Thread(loader);
        thread.setName("DBus Loader");
        thread.start();
    }

    /* based on code from org.freedesktop.dbus.ListDBus */
    private DBusTableModel listDBusConnection(final boolean users, final boolean owners, final DBusConnection conn)
            throws DBusException {
        final DBusTableModel model = new DBusTableModel();

        final DBus dbus = conn.getRemoteObject("org.freedesktop.DBus", "/org/freedesktop/DBus", DBus.class);
        final String[] names = dbus.ListNames();

        final ParsingContext p = new ParsingContext(conn);

        for (final String name : names) {
            List<DBusEntry> results = new ArrayList<>();
            try {
                // String objectpath = '/' + name.replace('.', '/');

                p.visitNode(name, "/");
            } catch (final DBusException e) {
                e.printStackTrace();
            } catch (final DBusExecutionException e) {
                e.printStackTrace();
            } catch (final SAXException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            results = p.getResult();
            p.reset();

            if (results.size() > 0) {
                if (users) {
                    try {
                        final UInt32 user = dbus.GetConnectionUnixUser(name);
                        for (final DBusEntry entry : results) {
                            entry.setUser(user);
                        }
                    } catch (final DBusExecutionException DBEe) {
                    }
                }
                if (!name.startsWith(":") && owners) {
                    try {
                        final String owner = dbus.GetNameOwner(name);
                        for (final DBusEntry entry : results) {
                            entry.setOwner(owner);
                        }
                    } catch (final DBusExecutionException DBEe) {
                    }
                }
                for (final DBusEntry entry : results) {
                    model.add(entry);
                }
            }
        }
        return model;
    }

    private final static String DOC_TYPE = "<!DOCTYPE node PUBLIC \"-//freedesktop//DTD D-BUS Object Introspection 1.0//EN\"\n\"http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd\">";

    class ParsingContext {
        private final DBusConnection conn;
        private DocumentBuilder builder;
        private List<DBusEntry> result;

        ParsingContext(final DBusConnection conn) {
            this.conn = conn;
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            try {
                builder = factory.newDocumentBuilder();
            } catch (final ParserConfigurationException e1) {
                // TODO Auto-generated catch block
                throw new RuntimeException(localize("Error during parser init: ") + e1.getMessage(), e1);
            }
            reset();

        }

        DBusEntry addEntry(final String name, final String path) throws DBusException {
            final DBusEntry entry = new DBusEntry();
            entry.setName(name);
            entry.setPath(path);
            final Introspectable introspectable = conn.getRemoteObject(name, path, Introspectable.class);
            entry.setIntrospectable(introspectable);

            result.add(entry);

            return entry;
        }

        public void visitNode(final String name, final String path) throws DBusException, SAXException, IOException {
            System.out.println("visit " + name + ":" + path);
            if ("/org/freedesktop/DBus/Local".equals(path)) {
                // this will disconnects us.
                return;
            }
            final DBusEntry e = addEntry(name, path);
            final String introspectData = e.getIntrospectable().Introspect();

            final Document document = builder
                    .parse(new InputSource(new StringReader(introspectData.replace(DOC_TYPE, ""))));
            final Element root = document.getDocumentElement();

            final NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                final Node node = children.item(i);
                if (Node.ELEMENT_NODE != node.getNodeType()) {
                    continue;
                }
                if ("node".equals(node.getNodeName())) {
                    final Node nameNode = node.getAttributes().getNamedItem("name");
                    if (nameNode != null) {
                        try {
                            if (path.endsWith("/")) {
                                visitNode(name, path + nameNode.getNodeValue());
                            } else {
                                visitNode(name, path + '/' + nameNode.getNodeValue());
                            }
                        } catch (final DBusException ex) {
                            ex.printStackTrace();
                        }
                    }
                }

            }

        }

        public List<DBusEntry> getResult() {
            return result;
        }

        void reset() {
            result = new ArrayList<>();
        }

    }

}
