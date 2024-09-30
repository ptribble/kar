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

import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;
import java.io.IOException;
import uk.co.petertribble.jkstat.api.*;
import uk.co.petertribble.jkstat.parse.*;
import uk.co.petertribble.kar.KarTime;

/**
 * Main driver to emulate iostat output given kar input.
 *
 * @author Peter Tribble
 */
public class GraphiteIOstat {

    private String stime;
    private String etime;
    private String filename; //NOPMD
    private boolean diskhide;
    private boolean showpart;
    private Map <String, Kstat> lastMap;

    private long oldsnaptime;

    /**
     * Display iostat output.
     *
     * @param args  The command line arguments
     */
    public GraphiteIOstat(String[] args) {
	lastMap = new HashMap <String, Kstat> ();
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * iostat [-z] [-e time] [-f filename] [-s time]
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
		} else if ("p".equals(flag)) {
		    showpart = true;
		} else if ("P".equals(flag)) {
		    diskhide = true;
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
	long midnight = 0;
	try {
	    daystart = KarTime.getStartTimeInMillis(sjkstat, stime);
	    dayend = KarTime.getEndTimeInMillis(sjkstat, etime);
	    midnight = KarTime.getStartTimeInMillis(sjkstat, (String) null);
	} catch (IllegalArgumentException iae) {
	    usage(iae.getMessage());
	}
	long lastboot = sjkstat.getKstat("unix", 0, "system_misc")
				.longData("boot_time");
	/*
	 * If boot time was before today, then we will skip the first interval
	 * as there is no valid previous measurement.
	 */
	boolean skipfirst = 1000*lastboot < midnight;
	do {
	    KstatFilter ksf = new KstatFilter(sjkstat);
	    ksf.setFilterType(KstatType.KSTAT_TYPE_IO);
	    // ignore usba statistics
	    ksf.addNegativeFilter("usba:::");

	    /*
	     * If we've rebooted since the last measurement, clear all the
	     * saved measurements and the code will do the right thing.
	     */
	    long boottime = sjkstat.getKstat("unix", 0, "system_misc")
				.longData("boot_time");
	    if (boottime > lastboot) {
		lastMap.clear();
	    }
	    lastboot = boottime;

	    KstatSet kss = new KstatSet(sjkstat, ksf);
	    // if past the start time, print output
	    if (!skipfirst && sjkstat.getTime() > daystart) {
		for (Kstat ks : new TreeSet <Kstat> (kss.getKstats())) {
		    /*
		     * If -p, show everything. If -P, don't show disks.
		     * Otherwise, don't show partitions.
		     */
		    if (showpart) {
			doPrint(sjkstat.getTime(), ks);
		    } else if (diskhide) {
			if (!"disk".equals(ks.getKstatClass())) {
			    doPrint(sjkstat.getTime(), ks);
			}
		    } else {
			if (!"partition".equals(ks.getKstatClass())) {
			    doPrint(sjkstat.getTime(), ks);
			}
		    }
		}
	    }
	    lastMap.clear();
	    for (Kstat ks : kss.getKstats()) {
		lastMap.put(ks.getTriplet(), ks);
	    }
	    skipfirst = false;
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    private void doPrint(long t, Kstat ks) {
	long snaptime = ks.getSnaptime();
	long nr = ks.longData("reads");
	long nw = ks.longData("writes");
	long nkr = ks.longData("nread");
	long nkw = ks.longData("nwritten");
	long nrtime = ks.longData("rtime");
	long nwtime = ks.longData("wtime");
	long nrlentime = ks.longData("rlentime");
	long nwlentime = ks.longData("wlentime");

	Kstat ksold = lastMap.get(ks.getTriplet());
	if (ksold == null) {
	    // FIXME first time through we want to print nothing, this path
	    // is a no-op; any subsequent passes are after a restart and
	    // we do want to emit data
	    oldsnaptime = ks.getCrtime();
	} else {
	    oldsnaptime = ksold.getSnaptime();
	    nr -= ksold.longData("reads");
	    nw -= ksold.longData("writes");
	    nkr -= ksold.longData("nread");
	    nkw -= ksold.longData("nwritten");
	    nrtime -= ksold.longData("rtime");
	    nwtime -= ksold.longData("wtime");
	    nrlentime -= ksold.longData("rlentime");
	    nwlentime -= ksold.longData("wlentime");
	}
	long snapdelta = snaptime - oldsnaptime;
	// and the midpoint is half the interval before the current time
	long midpoint = t - snapdelta/2000000;
	midpoint /= 1000;

	double dr = nr*1000000000.0/snapdelta;
 	double dw = nw*1000000000.0/snapdelta;
	double dkr = nkr*1000000000.0/(snapdelta*1024.0);
 	double dkw = nkw*1000000000.0/(snapdelta*1024.0);
	double dwait = nwlentime/((double) snapdelta);
	double dactv = nrlentime/((double) snapdelta);
	double dwsvc = (nr+nw == 0) ? 0.0 : dwait/(1000.0*((double) nr+nw));
	double dasvc = (nr+nw == 0) ? 0.0 : dactv/(1000.0*((double) nr+nw));
	int dpw = (int) (0.5 + 100.0*nwtime/snapdelta);
	int dpb = (int) (0.5 + 100.0*nrtime/snapdelta);

	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".reads",
		dr, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".writes",
		dw, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".kread",
		dkr, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".kwrite",
		dkw, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".wait",
		dwait, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".actv",
		dactv, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".wsvc_t",
		dwsvc, midpoint);
	System.out.printf("%s %.2f %d\n",
		"iostat." + ks.getName() + ".asvc_t",
		dasvc, midpoint);
	System.out.printf("%s %d %d\n",
		"iostat." + ks.getName() + ".pcwait",
		dpw, midpoint);
	System.out.printf("%s %d %d\n",
		"iostat." + ks.getName() + ".pcbusy",
		dpb, midpoint);
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: iostat [-P] [-e time] "
			+ "[-f filename] [-s time]");
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
     * Display iostat output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new GraphiteIOstat(args);
    }
}
