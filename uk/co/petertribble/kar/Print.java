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

import java.io.IOException;
import uk.co.petertribble.jkstat.api.*;
import uk.co.petertribble.jkstat.parse.*;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;

/**
 * Print kstats matching a pattern from kar data.
 *
 * @author Peter Tribble
 */
public class Print {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private Set <String> kstatPatterns;

    // usage 1 is [-M module] [-I instance] [-N name] [-S statistic]
    private boolean mflag;
    private String showmodule;
    private boolean iflag;
    private String showinstance;
    private boolean nflag;
    private String showname;
    private boolean sflag;
    private String showstatistic;
    // -T d | u for pretty time or raw seconds
    private String ttype;

    /**
     * Display sar output.
     *
     * @param args  The command line arguments
     */
    public Print(String[] args) {
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
		    filename = args[i];
		} else if ("e".equals(flag)) {
		    i++;
		    etime = args[i];
		} else if ("s".equals(flag)) {
		    i++;
		    stime = args[i];
		} else if ("M".equals(flag)) {
		    if (i+1 < args.length) {
			mflag = true;
			i++;
			showmodule = args[i];
		    } else {
			usage("Error: missing argument to -M flag");
		    }
		} else if ("I".equals(flag)) {
		    if (i+1 < args.length) {
			iflag = true;
			i++;
			showinstance = args[i];
		    } else {
			usage("Error: missing argument to -I flag");
		    }
		} else if ("N".equals(flag)) {
		    if (i+1 < args.length) {
			nflag = true;
			i++;
			showname = args[i];
		    } else {
			usage("Error: missing argument to -N flag");
		    }
		} else if ("S".equals(flag)) {
		    if (i+1 < args.length) {
			sflag = true;
			i++;
			showstatistic = args[i];
		    } else {
			usage("Error: missing argument to -S flag");
		    }
		} else if ("T".equals(flag)) {
		    if (i+1 < args.length) {
			i++;
			ttype = args[i];
			if (!"u".equals(ttype) && !"d".equals(ttype)) {
			    usage("Error: time format must be d or u");
			}
		    } else {
			usage("Error: missing argument to -T flag");
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
    private void accumulate(SequencedJKstat sjkstat) {
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
	    for (Kstat ks : new TreeSet <Kstat>(ksf.getKstats())) {
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
     * Print a line of output.
     */
    private void printOut(long t, Kstat ks, String statistic) {
	if ("u".equals(ttype)) {
	    if (ks.isNumeric(statistic)) {
		System.out.printf("%d\t%s:%s\t%8d\n",
			t/1000,
			ks.getTriplet(), statistic,
			ks.getData(statistic));
	    } else {
		System.out.printf("%d\t%s:%s\t%s\n",
			t/1000,
			ks.getTriplet(), statistic,
			ks.getData(statistic));
	    }
	} else {
	    if (ks.isNumeric(statistic)) {
		System.out.printf("%tT\t%s:%s\t%8d\n",
			new Date(t),
			ks.getTriplet(), statistic,
			ks.getData(statistic));
	    } else {
		System.out.printf("%tT\t%s:%s\t%s\n",
			new Date(t),
			ks.getTriplet(), statistic,
			ks.getData(statistic));
	    }
	}
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: print [-e time] "
			+ "[-f filename] [-s time] [-T d | u]");
	System.err.println("         [-M module] [-I instance] "
			+ "[-N name] [-S statistic] pattern [...]");
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
     * Display sar output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new Print(args);
    }
}
