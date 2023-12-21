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
import java.io.IOException;
import uk.co.petertribble.jkstat.api.*;
import uk.co.petertribble.jkstat.parse.*;
import uk.co.petertribble.kar.KarTime;

/**
 * Main driver to emulate fsstat output given kar input.
 *
 * @author Peter Tribble
 */
public class GraphiteFSstat {

    private String stime;
    private String etime;
    private String filename;
    private Map <String, Kstat> lastMap;

    private long oldsnaptime;

    /**
     * Display fsstat output.
     *
     * @param args  The command line arguments
     */
    public GraphiteFSstat(String[] args) {
	lastMap = new HashMap <String, Kstat> ();
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * fsstat [-e time] [-f filename] [-s time]
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

	    KstatSet kss = new KstatSet(sjkstat, ksf);
	    // if past the start time, print output
	    if (!skipfirst && sjkstat.getTime() > daystart) {
		for (Kstat ks : kss.getKstats()) {
		    doPrint(sjkstat.getTime(), ks);
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
	if (ksold == null) {
	    // FIXME first time through we want to print nothing, this path
	    // is a no-op; any subsequent passes are after a restart and
	    // we do want to emit data
	    oldsnaptime = ks.getCrtime();
	} else {
	    oldsnaptime = ksold.getSnaptime();
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
	// this is the interval
	long snapdelta = snaptime - oldsnaptime;
	// and the midpoint is half the interval before the current time
	long midpoint = t - snapdelta/2000000;
	midpoint /= 1000;

	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".ncreate",
		ncreate*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nrename",
		nrename*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nremove",
		nremove*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".ngetattr",
		ngetattr*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nsetattr",
		nsetattr*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nlookup",
		nlookup*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nread",
		nread*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nreaddir",
		nreaddir*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".nwrite",
		nwrite*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".read_bytes",
		read_bytes*1000000000.0/snapdelta, midpoint);
	System.out.printf("%s %.2f %d\n",
		"fsstat." + ks.getName() + ".write_bytes",
		write_bytes*1000000000.0/snapdelta, midpoint);
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: fsstat [-e time] "
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
	new GraphiteFSstat(args);
    }
}
