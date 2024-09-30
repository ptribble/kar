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

import java.util.Map;
import java.util.HashMap;
import java.util.TreeSet;
import java.io.IOException;
import uk.co.petertribble.jkstat.api.*;
import uk.co.petertribble.jkstat.parse.*;
import java.util.Date;

/**
 * Main driver to emulate iostat output given kar input.
 *
 * @author Peter Tribble
 */
public class IOstat {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private boolean zerohide;
    private boolean diskhide;
    private boolean showpart;
    private boolean megabytes;
    private Map <String, Kstat> lastMap;

    private long oldsnaptime;
    private long lastboot;

    private double dr;
    private double dw;
    private double dkr;
    private double dkw;
    private double dwait;
    private double dactv;
    private double dwsvc;
    private double dasvc;
    private int dpw;
    private int dpb;

    /**
     * Display iostat output.
     *
     * @param args  The command line arguments
     */
    public IOstat(String[] args) {
	lastMap = new HashMap <String, Kstat> ();
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * iostat [-z] [-M] [-P] [-p] [-e time] [-f filename] [-s time]
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
		} else if ("z".equals(flag)) {
		    zerohide = true;
		} else if ("p".equals(flag)) {
		    showpart = true;
		} else if ("P".equals(flag)) {
		    diskhide = true;
		} else if ("M".equals(flag)) {
		    megabytes = true;
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
	while (sjkstat.getTime() < daystart) {
	    sjkstat.next();
	}
	do {
	    System.out.printf("%tT    %s\n", new Date(sjkstat.getTime()),
				"extended device statistics");
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

	    if (megabytes) {
		System.out.println("    r/s    w/s   Mr/s   Mw/s wait actv "
				+ "wsvc_t asvc_t  %w  %b device");
	    } else {
		System.out.println("    r/s    w/s   kr/s   kw/s wait actv "
				+ "wsvc_t asvc_t  %w  %b device");
	    }
	    KstatSet kss = new KstatSet(sjkstat, ksf);
	    for (Kstat ks : new TreeSet <Kstat> (kss.getKstats())) {
		/*
		 * If -p, show everything. If -P, don't show disks. Otherwise,
		 * don't show partitions.
		 */
		if (showpart) {
		    doPrint(ks);
		} else if (diskhide) {
		    if (!"disk".equals(ks.getKstatClass())) {
			doPrint(ks);
		    }
		} else {
		    if (!"partition".equals(ks.getKstatClass())) {
			doPrint(ks);
		    }
		}
	    }
	    lastMap.clear();
	    for (Kstat ks : kss.getKstats()) {
		lastMap.put(ks.getTriplet(), ks);
	    }
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    private void doPrint(Kstat ks) {
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
	long snapdelta = ks.getSnaptime() - oldsnaptime;

	if (!(zerohide && nr == 0 && nw == 0 && nkr == 0 && nkw == 0 &&
	    nrtime == 0 && nwtime == 0 && nrlentime == 0 && nwlentime == 0)) {
	dr = nr*1000000000.0/snapdelta;
 	dw = nw*1000000000.0/snapdelta;
	dkr = nkr*1000000000.0/(snapdelta*1024.0);
 	dkw = nkw*1000000000.0/(snapdelta*1024.0);
	if (megabytes) {
	    dkr /= 1024.0;
	    dkw /= 1024.0;
	}
	dwait = nwlentime/((double) snapdelta);
	dactv = nrlentime/((double) snapdelta);
	dwsvc = (nr+nw == 0) ? 0.0 : dwait/(1000.0*((double) nr+nw));
	dasvc = (nr+nw == 0) ? 0.0 : dactv/(1000.0*((double) nr+nw));
	dpw = (int) (0.5 + 100.0*nwtime/snapdelta);
	dpb = (int) (0.5 + 100.0*nrtime/snapdelta);
	System.out.printf("%7.1f %6.1f %6.1f %6.1f %4.1f %4.1f %6.1f %6.1f %3d %3d %s\n",
		dr, dw, dkr, dkw, dwait, dactv, dwsvc, dasvc, dpw, dpb,
		ks.getName());
	}
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: iostat [-z] [-P] [-M] [-e time] "
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
	new IOstat(args);
    }
}
