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
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import uk.co.petertribble.kar.KarTime;

/**
 * Graphite sar output from kar input.
 *
 * @author Peter Tribble
 */
public class GraphiteSar {

    private String stime;
    private String etime;
    private String filename;

    /**
     * Display sar output.
     *
     * @param args the command line arguments
     */
    public GraphiteSar(final String[] args) {
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * Argument parser.
     *
     * sar [-e time] [-f filename] [-s time]
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
    private void accumulate(final SequencedJKstat sjkstat) {
	long daystart = 0;
	long dayend = 0;
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

	long okernel = 0;
	long oidle = 0;
	long ouser = 0;

	/*
	 * If we were already running then skip the first data point rather
	 * than average over the time since boot.
	 */
	if (boottime < daystart) {
	    for (Kstat ks : sjkstat.getKstats()) {
		if ("cpu".equals(ks.getModule())
				&& "sys".equals(ks.getName())) {
		    okernel += ks.longData("cpu_nsec_kernel");
		    ouser += ks.longData("cpu_nsec_user");
		    oidle += ks.longData("cpu_nsec_idle");
		}
	    }
	    sjkstat.next();
	}

	do {
	    long nkernel = 0;
	    long nidle = 0;
	    long nuser = 0;
	    for (Kstat ks : sjkstat.getKstats()) {
		if ("cpu".equals(ks.getModule())
				&& "sys".equals(ks.getName())) {
		    nkernel += ks.longData("cpu_nsec_kernel");
		    nuser += ks.longData("cpu_nsec_user");
		    nidle += ks.longData("cpu_nsec_idle");
		}
	    }
	    // reboot, reset the counters
	    if (nkernel < okernel || nuser < ouser || nidle < oidle) {
		okernel = 0;
		ouser = 0;
		oidle = 0;
	    }
	    long dkernel = nkernel - okernel;
	    long duser = nuser - ouser;
	    long didle = nidle - oidle;
	    long dtot = dkernel + duser + didle;
	    // FIXME slew to midpoint
	    long time = sjkstat.getTime() / 1000;
	    System.out.printf("%s %.2f %d%n", "user", 100.0 * duser / dtot,
			time);
	    System.out.printf("%s %.2f %d%n", "kernel", 100.0 * dkernel / dtot,
			time);
	    System.out.printf("%s %.2f %d%n", "idle", 100.0 * didle / dtot,
			time);
	    okernel = nkernel;
	    ouser = nuser;
	    oidle = nidle;
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: sar [-e time] [-f filename] [-s time]");
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
	new GraphiteSar(args);
    }
}
