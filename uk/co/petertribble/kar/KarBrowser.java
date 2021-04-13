/*
 * CDDL HEADER START
 *
 * The contents of this file are subject to the terms of the
 * Common Development and Distribution License, Version 1.0 only
 * (the "License").  You may not use this file except in compliance
 * with the License.
 *
 * You can obtain a copy of the license at usr/src/OPENSOLARIS.LICENSE
 * or http://www.opensolaris.org/os/licensing.
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL HEADER in each
 * file and include the License file at usr/src/OPENSOLARIS.LICENSE.
 * If applicable, add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your own identifying
 * information: Portions Copyright [yyyy] [name of copyright owner]
 *
 * CDDL HEADER END
 */

package uk.co.petertribble.kar;

import java.io.File;
import java.io.FilenameFilter;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;
import java.util.Vector;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
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
public class KarBrowser extends JFrame implements ListSelectionListener,
	ActionListener {

    private JMenuItem exitItem;
    private JMenuItem cloneItem;
    private JMenuItem closeItem;
    private JMenuItem helpItem;
    private JMenuItem licenseItem;

    private File dir;
    private JPanel kpanel;
    private JPanel cpanel;
    private JList <File> flist;
    private KstatTreePanel ktp;
    private ChartBuilderPanel cbp;

    /**
     * Create a new Kar output browser.
     *
     * @param dir The directory containing kar output files.
     */
    public KarBrowser(File dir) {
	this.dir = dir;

	addWindowListener(new winExit());

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
	List <File> files = Arrays.asList(dir.listFiles(new KarFileFilter()));
	Collections.reverse(files);
	flist = new JList <File> (new Vector <File> (files));
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
     * Emit usage message and exit.
     */
    private static void usage() {
	System.err.println("Usage: karbrowser dir_name");
	System.exit(1);
    }

    private static void usage(String s) {
	System.err.println(s);
	usage();
    }

    class winExit extends WindowAdapter {
	@Override
	public void windowClosing(WindowEvent we) {
	    JingleMultiFrame.unregister(KarBrowser.this);
	}
    }

    /*
     * Only show files that look like kar output files.
     */
    class KarFileFilter implements FilenameFilter {
	@Override
	public boolean accept(File f, String name) {
	    return name.startsWith("ka-") && name.endsWith(".zip");
	}
    }

    /*
     * Only show the date portion of the filename in the list.
     */
    class KarListCellRenderer extends DefaultListCellRenderer {
	@Override
	public Component getListCellRendererComponent(JList list,
							Object value,
							int index,
							boolean isSelected,
							boolean cellHasFocus) {
	    if (value instanceof File) {
		String s = ((File) value).getName();
		// starts ka-, so string 3 off the front
		// ends .zip, so strip 4 off the end
		setText(s.substring(3, s.length()-4));
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
    public static void main(String[] args) {
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
    private void showFile(File f) {
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
    public void valueChanged(ListSelectionEvent e) {
        if (!e.getValueIsAdjusting() && (flist.getSelectedIndex() != -1)) {
	    showFile(flist.getSelectedValue());
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
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
