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
import uk.co.petertribble.jkstat.api.Kstat;
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import java.util.Date;

/**
 * Main driver to emulate sar output given kar input.
 *
 * @author Peter Tribble
 */
public class Sar {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;

    /**
     * Display sar output.
     *
     * @param args  The command line arguments
     */
    public Sar(String[] args) {
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * Argument parser. Usage is this form, from sar(1):
     *
     * sar [-aAbcdgkmpqruvwy] [-e time] [-f filename] [-s time]
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
	System.out.printf("%tT%8s%8s%8s%8s\n", new Date(firsttime),
			"%usr", "%sys", "%wio", "%idle");
	// times, both current and accumulated
	long okernel = 0;
	long oidle = 0;
	long ouser = 0;
	long tkernel = 0;
	long tidle = 0;
	long tuser = 0;
	if (boottime > daystart) {
	    System.out.printf("%tT        unix restarts\n",
			new Date(1000 * ksb.longData("boot_time")));
	} else {
	    // reset times based on first data
	    for (Kstat ks : sjkstat.getKstats()) {
		if ("cpu".equals(ks.getModule())
				&& "sys".equals(ks.getName())) {
		    okernel += ks.longData("cpu_nsec_kernel");
		    ouser += ks.longData("cpu_nsec_user");
		    oidle += ks.longData("cpu_nsec_idle");
		}
	    }
	    tkernel = -okernel;
	    tidle = -oidle;
	    tuser = -ouser;
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
	    if (nkernel < okernel || nuser < ouser || nidle < oidle) {
		tkernel += okernel;
		tuser += ouser;
		tidle += oidle;
		okernel = 0;
		ouser = 0;
		oidle = 0;
		ksb = sjkstat.getKstat("unix", 0, "system_misc");
		System.out.printf("%tT        unix restarts\n",
				new Date(1000 * ksb.longData("boot_time")));
	    }
	    long dkernel = nkernel - okernel;
	    long duser = nuser - ouser;
	    long didle = nidle - oidle;
	    long dtot = dkernel + duser + didle;
	    // add 0.5 so we round correctly
	    int fkernel = (int) (0.5 + 100.0 * dkernel / dtot);
	    int fuser = (int) (0.5 + 100.0 * duser / dtot);
	    int fidle = (int) (0.5 + 100.0 * didle / dtot);
	    System.out.printf("%tT%8d%8d%8d%8d\n", new Date(sjkstat.getTime()),
				fuser, fkernel, 0, fidle);
	    okernel = nkernel;
	    ouser = nuser;
	    oidle = nidle;
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
	tkernel += okernel;
	tuser += ouser;
	tidle += oidle;
	System.out.println();
	long ttot = tkernel + tuser + tidle;
	int fkernel = (int) (0.5 + 100.0 * tkernel / ttot);
	int fuser = (int) (0.5 + 100.0 * tuser / ttot);
	int fidle = (int) (0.5 + 100.0 * tidle / ttot);
	System.out.printf("Average %8d%8d%8d%8d\n",
				fuser, fkernel, 0, fidle);
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: sar [-aAbcdgkmpqruvwy] [-e time] "
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
	new Sar(args);
    }
}
