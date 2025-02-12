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
import java.io.IOException;
import uk.co.petertribble.jumble.JumbleFile;
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import uk.co.petertribble.jkstat.gui.KstatPngImage;
import uk.co.petertribble.jkstat.gui.KstatAreaPngImage;

/**
 * Generate a set of kar graphs based on an input list read from a file.
 *
 * @author Peter Tribble
 */
public class Graphs {

    private String zfilename;
    private String sfilename;

    /**
     * Generate Kar graphs.
     *
     * @param args  The command line arguments
     */
    public Graphs(String[] args) {
	parseArgs(args);
	try {
	    makeGraphs(new ParseableJSONZipJKstat(zfilename, true));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * Argument parser. Usage is this form.
     *
     * graphs -f zipfile -s input-file
     *
     */
    private void parseArgs(String[] args) {
	for (int i = 0; i < args.length; i++) {
	    /*
	     * All flags start with a -, we pick out the arguments to any
	     * flags that have them as we parse that flag.
	     */
	    if (args[i].startsWith("-")) {
		String flag = args[i].substring(1);
		if ("f".equals(flag)) {
		    i++;
		    zfilename = args[i];
		} else if ("s".equals(flag)) {
		    i++;
		    sfilename = args[i];
		} else {
		    usage();
		}
	    } else {
		usage();
	    }
	}
    }

    /*
     * Actually build the graphs. If a graph fails, carry on with the next one.
     */
    private void makeGraphs(SequencedJKstat sjkstat) {
	for (String sline : JumbleFile.getLines(new File(sfilename))) {
	    try {
		makeGraph(sjkstat, sline);
	    } catch (Exception e) {
 		System.err.println("Failed graph: " + sline);
	    }
	}
    }

    /*
     * Create an individual graph. Each line in the input has a type, then
     * a filename, then a kstat specifier of arbitrary length.
     */
    private void makeGraph(SequencedJKstat sjkstat, String spec) {
	String[] sargs = spec.split("\\s+", 3);
	if ("Line".equals(sargs[0])) {
	    KstatPngImage.makeGraph(sargs[2].split("\\s+"), sjkstat,
				    new File(sargs[1]));
	} else if ("Area".equals(sargs[0])) {
	    KstatAreaPngImage.makeGraph(sargs[2].split("\\s+"), sjkstat,
				    new File(sargs[1]));
	} else if ("LineValue".equals(sargs[0])) {
	    KstatPngImage.makeGraph(sargs[2].split("\\s+"), sjkstat,
				    new File(sargs[1]), false);
	} else if ("AreaValue".equals(sargs[0])) {
	    KstatAreaPngImage.makeGraph(sargs[2].split("\\s+"), sjkstat,
				    new File(sargs[1]), false);
	}
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: graphs "
			+ "[-f zip_filename] [-s spec_filename]");
	System.exit(1);
    }

    /*
     * Print usage message and exit.
     */
    private void usage(String s) {
	System.err.println(s);
	usage();
    }

    /**
     * Generate Kar graphs.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new Graphs(args);
    }
}
