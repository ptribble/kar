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
import java.io.IOException;
import uk.co.petertribble.jkstat.api.*;
import uk.co.petertribble.jkstat.parse.*;
import java.util.Date;

/**
 * Main driver to emulate fsstat output given kar input.
 *
 * @author Peter Tribble
 */
public class FSstat {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private boolean zerohide;
    private Map <String, Kstat> lastMap;

    private long lastboot;

    /**
     * Display fsstat output.
     *
     * @param args  The command line arguments
     */
    public FSstat(String[] args) {
	lastMap = new HashMap <String, Kstat> ();
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * fsstat [-z] [-e time] [-f filename] [-s time]
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
				"file system statistics");
	    KstatFilter ksf = new KstatFilter(sjkstat);
	    /*
	     * We can't look for the kstats, because fsstat doesn't define
	     * a sensible naming scheme, so look for something that
	     * identifies them.
	     */
	    ksf.addFilter(":::nsetsecattr");

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

	    System.out.println(" new  name   name  attr  attr lookup rddir "
			+ " read read  write write");
	    System.out.println(" file remov  chng   get   set    ops   ops "
			+ "  ops bytes   ops bytes");
	    KstatSet kss = new KstatSet(sjkstat, ksf);
	    for (Kstat ks : kss.getKstats()) {
		doPrint(ks);
	    }
	    lastMap.clear();
	    for (Kstat ks : kss.getKstats()) {
		lastMap.put(ks.getTriplet(), ks);
	    }
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
    }

    private void doPrint(Kstat ks) {
	long ncreate = ks.longData("ncreate");
	long nrename = ks.longData("nrename");
	long nremove = ks.longData("nremove");
	long ngetattr = ks.longData("ngetattr");
	long nsetattr = ks.longData("nsetattr");
	long nlookup = ks.longData("nlookup");
	long nreaddir = ks.longData("nreaddir");
	long nread = ks.longData("nread");
	long read_bytes = ks.longData("read_bytes");
	long nwrite = ks.longData("nwrite");
	long write_bytes = ks.longData("write_bytes");

	Kstat ksold = lastMap.get(ks.getTriplet());
	if (ksold != null) {
	    ncreate -= ksold.longData("ncreate");
	    nrename -= ksold.longData("nrename");
	    nremove -= ksold.longData("nremove");
	    ngetattr -= ksold.longData("ngetattr");
	    nsetattr -= ksold.longData("nsetattr");
	    nlookup -= ksold.longData("nlookup");
	    nreaddir -= ksold.longData("nreaddir");
	    nread -= ksold.longData("nread");
	    read_bytes -= ksold.longData("read_bytes");
	    nwrite -= ksold.longData("nwrite");
	    write_bytes -= ksold.longData("write_bytes");
	}

	if (!(zerohide && ncreate == 0 && nrename == 0 && nremove == 0
		&& ngetattr == 0 && nsetattr == 0 && nlookup == 0
		&& nreaddir == 0 && nread == 0 && read_bytes == 0
		&& nwrite == 0 && write_bytes == 0)) {

	    System.out.printf(
		"%5s %5s %5s %5s %5s %5s %5s %5s %5s %5s %5s %s\n",
		PrettyFormat.memscale(ncreate), PrettyFormat.memscale(nrename),
		PrettyFormat.memscale(nremove), PrettyFormat.memscale(ngetattr),
		PrettyFormat.memscale(nsetattr), PrettyFormat.memscale(nlookup),
		PrettyFormat.memscale(nreaddir), PrettyFormat.memscale(nread),
		PrettyFormat.memscale(read_bytes),
		PrettyFormat.memscale(nwrite),
		PrettyFormat.memscale(write_bytes),
		ks.getName());
	}
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: fsstat [-z] [-e time] "
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
     * Display fsstat output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new FSstat(args);
    }
}
