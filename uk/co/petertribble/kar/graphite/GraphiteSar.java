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
    private String filename; //NOPMD

    /**
     * Display sar output.
     *
     * @param args  The command line arguments
     */
    public GraphiteSar(String[] args) {
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
	long boottime = 1000*ksb.longData("boot_time");

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
	    long time = sjkstat.getTime()/1000;
	    System.out.printf("%s %.2f %d\n", "user", 100.0*duser/dtot, time);
	    System.out.printf("%s %.2f %d\n", "kernel", 100.0*dkernel/dtot,
			time);
	    System.out.printf("%s %.2f %d\n", "idle", 100.0*didle/dtot, time);
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
	new GraphiteSar(args);
    }
}
