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
 * Main driver to emulate mpstat output given kar input.
 *
 * @author Peter Tribble
 */
public class GraphiteMPstat {

    private String stime;
    private String etime;
    private String filename;
    private Map<String, Kstat> lastMap;

    private long oldsnaptime;
    private long foldsnaptime;

    /**
     * Display MPstat output.
     *
     * @param args  The command line arguments
     */
    public GraphiteMPstat(String[] args) {
	lastMap = new HashMap<>();
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * mpstat [-e time] [-f filename] [-s time]
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
	    ksf.addFilter("cpu::sys");

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
		for (Kstat ks : new TreeSet<Kstat>(kss.getKstats())) {
		    doPrint(sjkstat.getTime(), ks,
			sjkstat.getKstat("cpu", ks.getInst(), "vm"));
		}
	    }
	    lastMap.clear();
	    for (Kstat ks : kss.getKstats()) {
		lastMap.put(ks.getTriplet(), ks);
		/*
		 * Save the matching cpu::vm kstat
		 */
		Kstat ksv = sjkstat.getKstat("cpu", ks.getInst(), "vm");
		lastMap.put(ksv.getTriplet(), ksv);
	    }
	    skipfirst = false;
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    private void doPrint(long t, Kstat ks, Kstat ksf) {

	long snaptime = ks.getSnaptime();
	long fsnaptime = ksf.getSnaptime();

	// get the new values
	long nminf = ksf.longData("hat_fault") + ksf.longData("as_fault");
	long nmjf = ksf.longData("maj_fault");
	long nxcal = ks.longData("xcalls");
	long nintr = ks.longData("intr");
	long nithr = ks.longData("intrthread");
	long ncsw = ks.longData("pswitch");
	long nicsw = ks.longData("inv_swtch");
	long nmigr = ks.longData("cpumigrate");
	long nsmtx = ks.longData("mutex_adenters");
	long nsrw = ks.longData("rw_rdfails") + ks.longData("rw_wrfails");
	long nsyscl = ks.longData("syscall");
	long nusr = ks.longData("cpu_nsec_user");
	long nsys = ks.longData("cpu_nsec_kernel");
	long nidl = ks.longData("cpu_nsec_idle");

	Kstat ksold = lastMap.get(ks.getTriplet());
	Kstat ksfold = lastMap.get(ksf.getTriplet());
	if (ksold == null) {
	    // FIXME first time through we want to print nothing, this path
	    // is a no-op; any subsequent passes are after a restart and
	    // we do want to emit data
	    oldsnaptime = ks.getCrtime();
	    foldsnaptime = ksf.getCrtime();
	} else {
	    oldsnaptime = ksold.getSnaptime();
	    foldsnaptime = ksfold.getSnaptime();

	    nminf -=
		ksfold.longData("hat_fault") + ksfold.longData("as_fault");
	    nmjf -= ksf.longData("maj_fault");
	    nxcal -= ksold.longData("xcalls");
	    nintr -= ksold.longData("intr");
	    nithr -= ksold.longData("intrthread");
	    ncsw -= ksold.longData("pswitch");
	    nicsw -= ksold.longData("inv_swtch");
	    nmigr -= ksold.longData("cpumigrate");
	    nsmtx -= ksold.longData("mutex_adenters");
	    nsrw -=
		ksold.longData("rw_rdfails") + ksold.longData("rw_wrfails");
	    nsyscl -= ksold.longData("syscall");
	    nusr -= ksold.longData("cpu_nsec_user");
	    nsys -= ksold.longData("cpu_nsec_kernel");
	    nidl -= ksold.longData("cpu_nsec_idle");
	}
	long snapdelta = snaptime - oldsnaptime;
	long fsnapdelta = fsnaptime - foldsnaptime;
	// and the midpoint is half the interval before the current time
	long midpoint = t - snapdelta/2000000;
	midpoint /= 1000;

	long dminf = Math.round(nminf*1000000000.0/fsnapdelta);
	long dmjf = Math.round(nmjf*1000000000.0/fsnapdelta);
	long dxcal = Math.round(nxcal*1000000000.0/snapdelta);
 	long dintr = Math.round(nintr*1000000000.0/snapdelta);
	long dithr = Math.round(nithr*1000000000.0/snapdelta);
	long dcsw = Math.round(ncsw*1000000000.0/snapdelta);
	long dicsw = Math.round(nicsw*1000000000.0/snapdelta);
	long dmigr = Math.round(nmigr*1000000000.0/snapdelta);
	long dsmtx = Math.round(nsmtx*1000000000.0/snapdelta);
	long dsrw = Math.round(nsrw*1000000000.0/snapdelta);
 	long dsyscl = Math.round(nsyscl*1000000000.0/snapdelta);
	long dusr = Math.round(nusr*100.0/snapdelta);
	long dsys = Math.round(nsys*100.0/snapdelta);
	long didl = Math.round(nidl*100.0/snapdelta);

	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".minf",
		dminf, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".mjf",
		dmjf, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".xcal",
		dxcal, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".intr",
		dintr, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".ithr",
		dithr, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".csw",
		dcsw, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".icsw",
		dicsw, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".migr",
		dmigr, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".smtx",
		dsmtx, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".srw",
		dsrw, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".syscl",
		dsyscl, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".usr",
		dusr, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".sys",
		dsys, midpoint);
	System.out.printf("%s %d %d\n",
		"mpstat." + ks.getInstance() + ".idl",
		didl, midpoint);
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: mpstat [-e time] [-f filename] [-s time]");
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
     * Display mpstat output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new GraphiteMPstat(args);
    }
}
