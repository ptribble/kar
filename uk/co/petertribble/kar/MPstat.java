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

import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import uk.co.petertribble.jkstat.api.Kstat;
import uk.co.petertribble.jkstat.api.KstatFilter;
import uk.co.petertribble.jkstat.api.KstatSet;
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import java.util.Date;

/**
 * Main driver to emulate mpstat output given kar input.
 *
 * @author Peter Tribble
 */
public class MPstat {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private Map<String, Kstat> lastMap;

    private long oldsnaptime;
    private long foldsnaptime;
    private long lastboot;

    private long dminf;
    private long dmjf;
    private long dxcal;
    private long dintr;
    private long dithr;
    private long dcsw;
    private long dicsw;
    private long dmigr;
    private long dsmtx;
    private long dsrw;
    private long dsyscl;
    private long dusr;
    private long dsys;
    private long didl;

    /**
     * Display MPstat output.
     *
     * @param args the command line arguments
     */
    public MPstat(final String[] args) {
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
	    System.out.printf("%tT    %s%n", new Date(sjkstat.getTime()),
				"processor statistics");
	    System.out.println("CPU minf mjf xcal  intr ithr  csw icsw migr "
				+ "smtx  srw syscl  usr sys idl");

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
	    for (Kstat ks : kss.getKstats(true)) {
		doPrint(ks, sjkstat.getKstat("cpu", ks.getInst(), "vm"));
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
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    private void doPrint(final Kstat ks, final Kstat ksf) {

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
	long snapdelta = ks.getSnaptime() - oldsnaptime;
	long fsnapdelta = ksf.getSnaptime() - foldsnaptime;

	dminf = Math.round(nminf * 1000000000.0 / fsnapdelta);
	dmjf = Math.round(nmjf * 1000000000.0 / fsnapdelta);
	dxcal = Math.round(nxcal * 1000000000.0 / snapdelta);
 	dintr = Math.round(nintr * 1000000000.0 / snapdelta);
	dithr = Math.round(nithr * 1000000000.0 / snapdelta);
	dcsw = Math.round(ncsw * 1000000000.0 / snapdelta);
	dicsw = Math.round(nicsw * 1000000000.0 / snapdelta);
	dmigr = Math.round(nmigr * 1000000000.0 / snapdelta);
	dsmtx = Math.round(nsmtx * 1000000000.0 / snapdelta);
	dsrw = Math.round(nsrw * 1000000000.0 / snapdelta);
 	dsyscl = Math.round(nsyscl * 1000000000.0 / snapdelta);
	dusr = Math.round(nusr * 100.0 / snapdelta);
	dsys = Math.round(nsys * 100.0 / snapdelta);
	didl = Math.round(nidl * 100.0 / snapdelta);

	System.out.printf(
	    "%3d %4d %3d %4d %5d %4d %4d %4d %4d %4d %4d %5d  %3d %3d %3d%n",
		ks.getInst(), dminf, dmjf, dxcal, dintr, dithr, dcsw, dicsw,
		dmigr, dsmtx, dsrw, dsyscl, dusr, dsys, didl);
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: mpstat [-e time] [-f filename] [-s time]");
	System.exit(1);
    }

    /*
     * Print usage message and exit.
     */
    private void usage(final String s) {
	System.err.println(s);
	usage();
    }

    /**
     * Display mpstat output.
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
	new MPstat(args);
    }
}
