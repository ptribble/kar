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

package uk.co.petertribble.kar.graphite;

import java.io.IOException;
import uk.co.petertribble.jkstat.api.Kstat;
import uk.co.petertribble.jkstat.api.KstatFilter;
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import uk.co.petertribble.kar.KarTime;
import java.util.Set;
import java.util.HashSet;

/**
 * Print kstats matching a pattern from kar data.
 *
 * @author Peter Tribble
 */
public class GraphitePrint {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private Set<String> kstatPatterns;

    // usage 1 is [-M module] [-I instance] [-N name] [-S statistic]
    private boolean mflag;
    private String showmodule;
    private boolean iflag;
    private String showinstance;
    private boolean nflag;
    private String showname;
    private boolean sflag;
    private String showstatistic;

    /**
     * Display sar output.
     *
     * @param args the command line arguments
     */
    public GraphitePrint(final String[] args) {
	kstatPatterns = new HashSet<>();
	parseArgs(args);
	if (kstatPatterns.isEmpty()) {
	    usage("Must supply a pattern.");
	}
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * Argument parser. Usage is this form:
     *
     * print [-e time] [-f filename] [-s time] pattern [...]
     *
     */
    private void parseArgs(final String[] args) {
	for (int i = 0; i < args.length; i++) {
	    /*
	     * All flags start with a -, we pick out the arguments to any
	     * flags that have them as we parse that flag.
	     */
	    if (args[i].startsWith("-")) {
		String flag = args[i].substring(1);
		if ("f".equals(flag)) {
		    i++;
		    filename = args[i];
		} else if ("e".equals(flag)) {
		    i++;
		    etime = args[i];
		} else if ("s".equals(flag)) {
		    i++;
		    stime = args[i];
		} else if ("M".equals(flag)) {
		    if (i + 1 < args.length) {
			mflag = true;
			i++;
			showmodule = args[i];
		    } else {
			usage("Error: missing argument to -M flag");
		    }
		} else if ("I".equals(flag)) {
		    if (i + 1 < args.length) {
			iflag = true;
			i++;
			showinstance = args[i];
		    } else {
			usage("Error: missing argument to -I flag");
		    }
		} else if ("N".equals(flag)) {
		    if (i + 1 < args.length) {
			nflag = true;
			i++;
			showname = args[i];
		    } else {
			usage("Error: missing argument to -N flag");
		    }
		} else if ("S".equals(flag)) {
		    if (i + 1 < args.length) {
			sflag = true;
			i++;
			showstatistic = args[i];
		    } else {
			usage("Error: missing argument to -S flag");
		    }
		}
	    } else {
		kstatPatterns.add(args[i]);
	    }
	}
	if (mflag || iflag || nflag || sflag) {
	    StringBuilder sb = new StringBuilder();
	    if (mflag) {
		sb.append(showmodule);
	    }
	    sb.append(':');
	    if (iflag) {
		sb.append(showinstance);
	    }
	    sb.append(':');
	    if (nflag) {
		sb.append(showname);
	    }
	    sb.append(':');
	    if (sflag) {
		sb.append(showstatistic);
	    }
	    kstatPatterns.add(sb.toString());
	}
    }

    /*
     * Go through the input reading all the entries, and accumulating
     * statistics.
     */
    private void accumulate(final SequencedJKstat sjkstat) {
	KstatFilter ksf = new KstatFilter(sjkstat);
	for (String s : kstatPatterns) {
	    ksf.addFilter(s);
	}
	try {
	    daystart = KarTime.getStartTimeInMillis(sjkstat, stime);
	    dayend = KarTime.getEndTimeInMillis(sjkstat, etime);
	} catch (IllegalArgumentException iae) {
	    usage(iae.getMessage());
	}
	// skip forward to start time
	while (sjkstat.getTime() < daystart) {
	    sjkstat.next();
	}
	do {
	    for (Kstat ks : ksf.getKstats(true)) {
		if (showstatistic == null) {
		    for (String s : ksf.filteredStatistics(ks)) {
			printOut(sjkstat.getTime(), ks, s);
		    }
		} else {
		    printOut(sjkstat.getTime(), ks, showstatistic);
		}
	    }
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    /*
     * Print a line of output. Silently ignore non-numeric statistics.
     */
    private void printOut(final long t, final Kstat ks,
			  final String statistic) {
	if (ks.isNumeric(statistic)) {
	    System.out.printf("%s:%s %d %d%n",
			ks.getTriplet(), statistic,
			ks.getData(statistic),
			t / 1000);
	}
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: print [-e time] [-f filename] [-s time]");
	System.err.println("         [-M module] [-I instance] "
			+ "[-N name] [-S statistic] pattern [...]");
	System.exit(1);
    }

    /*
     * Emit usage message and exit.
     */
    private void usage(final String s) {
	System.err.println(s);
	usage();
    }

    /**
     * Display sar output.
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
	new GraphitePrint(args);
    }
}
