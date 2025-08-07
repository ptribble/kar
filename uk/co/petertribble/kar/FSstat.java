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
    private Map<String, Kstat> lastMap;

    private long lastboot;

    /**
     * Display fsstat output.
     *
     * @param args  The command line arguments
     */
    public FSstat(String[] args) {
	lastMap = new HashMap<>();
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
	    System.out.printf("%tT    %s%n", new Date(sjkstat.getTime()),
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
	long readbytes = ks.longData("read_bytes");
	long nwrite = ks.longData("nwrite");
	long writebytes = ks.longData("write_bytes");

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
	    readbytes -= ksold.longData("read_bytes");
	    nwrite -= ksold.longData("nwrite");
	    writebytes -= ksold.longData("write_bytes");
	}

	if (!(zerohide && ncreate == 0 && nrename == 0 && nremove == 0
		&& ngetattr == 0 && nsetattr == 0 && nlookup == 0
		&& nreaddir == 0 && nread == 0 && readbytes == 0
		&& nwrite == 0 && writebytes == 0)) {

	    System.out.printf(
		"%5s %5s %5s %5s %5s %5s %5s %5s %5s %5s %5s %s%n",
		PrettyFormat.memscale(ncreate), PrettyFormat.memscale(nrename),
		PrettyFormat.memscale(nremove), PrettyFormat.memscale(ngetattr),
		PrettyFormat.memscale(nsetattr), PrettyFormat.memscale(nlookup),
		PrettyFormat.memscale(nreaddir), PrettyFormat.memscale(nread),
		PrettyFormat.memscale(readbytes),
		PrettyFormat.memscale(nwrite),
		PrettyFormat.memscale(writebytes),
		ks.getName());
	}
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: fsstat [-z] [-e time] "
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
     * Display fsstat output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new FSstat(args);
    }
}
