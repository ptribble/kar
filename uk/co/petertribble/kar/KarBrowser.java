/*
 * SPDX-License-Identifier: CDDL-1.0
 *
 * CDDL HEADER START
 *
 * This file and its contents are supplied under the terms of the
 * Common Development and Distribution License ("CDDL"), version 1.0.
 * You may only use this file in accordance with the terms of version
 * 1.0 of the CDDL.
 *
 * A full copy of the text of the CDDL should have accompanied this
 * source. A copy of the CDDL is also available via the Internet at
 * http://www.illumos.org/license/CDDL.
 *
 * CDDL HEADER END
 *
 * Copyright 2025 Peter Tribble
 *
 */

package uk.co.petertribble.kar;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import uk.co.petertribble.jingle.JingleMultiFrame;
import uk.co.petertribble.jingle.JingleInfoFrame;
import uk.co.petertribble.jkstat.api.JKstat;
import uk.co.petertribble.jkstat.gui.KstatResources;
import uk.co.petertribble.jkstat.gui.ChartBuilderPanel;
import uk.co.petertribble.jkstat.browser.KstatTreePanel;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;

/**
 * Kstat Browser and Chart Builder reading kar output.
 *
 * @author Peter Tribble
 */
public final class KarBrowser extends JFrame implements ListSelectionListener,
	ActionListener {

    private static final long serialVersionUID = 1L;

    /**
     * A menu item to exit the application.
     */
    private JMenuItem exitItem;
    /**
     * A menu item to clone the current window.
     */
    private JMenuItem cloneItem;
    /**
     * A menu item to close the current window.
     */
    private JMenuItem closeItem;
    /**
     * A menu item to show the help.
     */
    private JMenuItem helpItem;
    /**
     * A menu item to show the license.
     */
    private JMenuItem licenseItem;

    /**
     * The directory this browser is browsing.
     */
    private File dir;
    /**
     * The main panel.
     */
    private JPanel kpanel;
    /**
     * The panel for the chart builder.
     */
    private JPanel cpanel;
    /**
     * The list of files being browsed.
     */
    private JList<File> flist;
    /**
     * The kstat tree.
     */
    private KstatTreePanel ktp;
    /**
     * The chart builder.
     */
    private ChartBuilderPanel cbp;

    /**
     * Create a new Kar output browser.
     *
     * @param indir The directory containing kar output files.
     */
    public KarBrowser(final File indir) {
	dir = indir;

	addWindowListener(new WindowExit());

	JMenuBar jm = new JMenuBar();

	JMenu jme = new JMenu(KstatResources.getString("FILE.TEXT"));
	jme.setMnemonic(KeyEvent.VK_F);

	cloneItem = new JMenuItem(
			KstatResources.getString("FILE.NEWBROWSER.TEXT"),
			KeyEvent.VK_B);
	cloneItem.addActionListener(this);
	jme.add(cloneItem);
	closeItem = new JMenuItem(
				KstatResources.getString("FILE.CLOSEWIN.TEXT"),
				KeyEvent.VK_W);
	closeItem.addActionListener(this);
	jme.add(closeItem);
	exitItem = new JMenuItem(KstatResources.getString("FILE.EXIT.TEXT"),
				KeyEvent.VK_X);
	exitItem.addActionListener(this);
	jme.add(exitItem);

	JingleMultiFrame.register(this, closeItem);

	jm.add(jme);

	JMenu jmh = new JMenu(KstatResources.getString("HELP.TEXT"));
	jmh.setMnemonic(KeyEvent.VK_H);
	helpItem = new JMenuItem(KstatResources.getString("HELP.ABOUT.TEXT")
				+ " karbrowser", KeyEvent.VK_A);
	helpItem.addActionListener(this);
	jmh.add(helpItem);
	licenseItem = new JMenuItem(
				KstatResources.getString("HELP.LICENSE.TEXT"),
				KeyEvent.VK_L);
	licenseItem.addActionListener(this);
	jmh.add(licenseItem);

	jm.add(jmh);

	setJMenuBar(jm);

	// These shenanigans are to sort the files most recent first
	List<File> files = Arrays.asList(dir.listFiles(new KarFileFilter()));
	Collections.reverse(files);
	flist = new JList<>(new Vector<>(files));
	flist.addListSelectionListener(this);
	flist.setCellRenderer(new KarListCellRenderer());

	kpanel = new JPanel(new BorderLayout());

	cpanel = new JPanel(new BorderLayout());

	JTabbedPane jtp = new JTabbedPane();
	jtp.add(kpanel, KstatResources.getString("BROWSERUI.NAME.TEXT"));
	jtp.add(cpanel, KstatResources.getString("CHART.BUILDER"));

	JSplitPane psplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
					new JScrollPane(flist), jtp);
	psplit.setOneTouchExpandable(true);
	psplit.setDividerLocation(96);
	add(psplit);

	setSize(720, 600);
	setVisible(true);
    }

    /*
     * Print usage message and exit.
     */
    private static void usage() {
	System.err.println("Usage: karbrowser dir_name");
	System.exit(1);
    }

    private static void usage(final String s) {
	System.err.println(s);
	usage();
    }

    class WindowExit extends WindowAdapter {
	@Override
	public void windowClosing(final WindowEvent we) {
	    JingleMultiFrame.unregister(KarBrowser.this);
	}
    }

    /*
     * Only show files that look like kar output files.
     */
    class KarFileFilter implements FilenameFilter {
	@Override
	public boolean accept(final File f, final String name) {
	    return name.startsWith("ka-") && name.endsWith(".zip");
	}
    }

    /*
     * Only show the date portion of the filename in the list.
     */
    class KarListCellRenderer extends DefaultListCellRenderer {
	private static final long serialVersionUID = 1L;
	@Override
	public Component getListCellRendererComponent(final JList list,
						final Object value,
						final int index,
						final boolean isSelected,
						final boolean cellHasFocus) {
	    if (value instanceof File) {
		String s = ((File) value).getName();
		// starts ka-, so string 3 off the front
		// ends .zip, so strip 4 off the end
		setText(s.substring(3, s.length() - 4));
	    } else {
		setText(value.toString());
	    }
	    return this;
	}
    }

    /**
     * Browse kar output. Expects a single argument naming a directory
     * that contains kar output files.
     *
     * @param args  The command line arguments
     */
    public static void main(final String[] args) {
	if (args.length == 1) {
	    File f = new File(args[0]);
	    if (!f.exists()) {
		usage("Error: no such directory");
	    }
	    if (!f.isDirectory()) {
		usage("Error: input location isn't a directory");
	    }
	    new KarBrowser(f);
	} else {
	    usage();
	}
    }

    /*
     * Show a file. Kill off anything we already have. Read the new file, and
     * create a new kstat browser and chart builder.
     */
    private void showFile(final File f) {
	Cursor c = getCursor();
	setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	try {
	    kpanel.removeAll();
	    if (ktp != null) {
		ktp.stopLoop();
	    }
	    cpanel.removeAll();
	    /*
	     * Explicit garbage collection, otherwise we grow endlessly as we
	     * open new archives, which could easily be 100M of data each.
	     */
	    System.gc();

	    JKstat jkstat = new ParseableJSONZipJKstat(f.getAbsolutePath(),
						true);
	    ktp = new KstatTreePanel(jkstat);
	    kpanel.add(ktp);
	    kpanel.validate();
	    cbp = new ChartBuilderPanel(jkstat);
	    cpanel.add(cbp);
	    cpanel.validate();
	} catch (Exception e) {
	    JOptionPane.showMessageDialog(this,
		"Unable to parse input file.",
		"Invalid Data", JOptionPane.ERROR_MESSAGE);

	}
	setCursor(c);
    }

    @Override
    public void valueChanged(final ListSelectionEvent e) {
        if (!e.getValueIsAdjusting() && flist.getSelectedIndex() != -1) {
	    showFile(flist.getSelectedValue());
        }
    }

    @Override
    public void actionPerformed(final ActionEvent e) {
	if (e.getSource() == cloneItem) {
	    new KarBrowser(dir);
	}
	if (e.getSource() == closeItem) {
	    JingleMultiFrame.unregister(this);
	}
	if (e.getSource() == exitItem) {
	    System.exit(0);
	}
	if (e.getSource() == helpItem) {
	    new JingleInfoFrame(this.getClass().getClassLoader(),
				"help/karbrowser.html", "text/html");
	}
	if (e.getSource() == licenseItem) {
	    new JingleInfoFrame(this.getClass().getClassLoader(),
				"help/CDDL.txt", "text/plain");
	}
    }
}
