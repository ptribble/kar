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

import java.io.IOException;
import uk.co.petertribble.jkstat.api.Kstat;
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import java.util.Date;

/**
 * Print load averages.
 *
 * @author Peter Tribble
 */
public class Load {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;

    /**
     * Display load averages.
     *
     * @param args  The command line arguments
     */
    public Load(String[] args) {
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * Argument parser.
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
		}
	    } else {
		usage();
	    }
	}
    }

    /*
     * Go through the input reading all the entries, and accumulating
     * statistics.
     */
    private void accumulate(SequencedJKstat sjkstat) {
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
	Kstat ksb = sjkstat.getKstat("unix", 0, "system_misc");
	long boottime = 1000 * ksb.longData("boot_time");
	long firsttime = boottime > daystart ? boottime : daystart;
	System.out.printf("%tT%8s%8s%8s\n", new Date(firsttime),
			"1min", "5min", "15min");
	do {
	    Kstat ks = sjkstat.getKstat("unix", 0, "system_misc");
	    System.out.printf("%tT%8.2f%8.2f%8.2f\n",
				new Date(sjkstat.getTime()),
				ks.longData("avenrun_1min") / 256.0,
				ks.longData("avenrun_5min") / 256.0,
				ks.longData("avenrun_15min") / 256.0);
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: load [-e time] "
			+ "[-f filename] [-s time]");
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
	new Load(args);
    }
}
