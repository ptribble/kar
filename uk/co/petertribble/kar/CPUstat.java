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

/**
 * Main driver to give cpu information from kar input.
 *
 * @author Peter Tribble
 */
public class CPUstat {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private KstatAggregate ksold;

    private long oldsnaptime;
    private long lastboot;

    private double dexec;
    private long dintr;
    private long dsyscl;
    private long dcsw;
    private long dusr;
    private long dsys;
    private long didl;

    /**
     * Display CPUstat output.
     *
     * @param args  The command line arguments
     */
    public CPUstat(String[] args) {
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
	    System.out.printf("%tT    %s\n", new Date(sjkstat.getTime()),
				"cpu statistics");
	    System.out.println("Load 1min   5min  15min   "
				+ "exec     in    sy    cs   us  sy  id");

	    KstatFilter ksf = new KstatFilter(sjkstat);
	    ksf.addFilter("cpu::sys");

	    /*
	     * If we've rebooted since the last measurement, clear all the
	     * saved measurements and the code will do the right thing.
	     */
	    long boottime = sjkstat.getKstat("unix", 0, "system_misc")
				.longData("boot_time");
	    if (boottime > lastboot) {
		ksold = null;
	    }
	    lastboot = boottime;

	    KstatSet kss = new KstatSet(sjkstat, ksf);

	    doPrint(sjkstat.getKstat("unix", 0, "system_misc"),
		new KstatAggregate(sjkstat, kss.getKstats()));

	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    private void doPrint(Kstat ksl, KstatAggregate ks) {

	// get the new values
	long nexec = ks.aggregate("sysexec");
	long nintr = ks.aggregate("intr");
	long nsyscl = ks.aggregate("syscall");
	long ncsw = ks.aggregate("pswitch") + ks.aggregate("inv_swtch");
	float nusr = ks.average("cpu_nsec_user");
	float nsys = ks.average("cpu_nsec_kernel");
	float nidl = ks.average("cpu_nsec_idle");

	if (ksold == null) {
	    oldsnaptime = ks.getCrtime();
	} else {
	    oldsnaptime = ksold.getSnaptime();

	    nexec -= ksold.aggregate("sysexec");
	    nintr -= ksold.aggregate("intr");
	    nsyscl -= ksold.aggregate("syscall");
	    ncsw -= ksold.aggregate("pswitch");
	    ncsw -= ksold.aggregate("inv_swtch");
	    nusr -= ksold.average("cpu_nsec_user");
	    nsys -= ksold.average("cpu_nsec_kernel");
	    nidl -= ksold.average("cpu_nsec_idle");
	}
	long snapdelta = ks.getSnaptime() - oldsnaptime;

 	dexec = nexec*1000000000.0/snapdelta;
 	dintr = Math.round(nintr*1000000000.0/snapdelta);
 	dsyscl = Math.round(nsyscl*1000000000.0/snapdelta);
	dcsw = Math.round(ncsw*1000000000.0/snapdelta);
	dusr = Math.round(nusr*100.0/snapdelta);
	dsys = Math.round(nsys*100.0/snapdelta);
	didl = Math.round(nidl*100.0/snapdelta);

	System.out.printf(
	    "   %6.2f %6.2f %6.2f   %4.1f  %5d %5d %5d  %3d %3d %3d\n",
		ksl.longData("avenrun_1min")/256.0,
		ksl.longData("avenrun_5min")/256.0,
		ksl.longData("avenrun_15min")/256.0,
		dexec, dintr, dsyscl, dcsw, dusr, dsys, didl);

	ksold = ks;
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: cpustat [-e time] [-f filename] [-s time]");
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
     * Display cpu output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new CPUstat(args);
    }
}
