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

import java.awt.Dimension;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import uk.co.petertribble.jkstat.api.Kstat;
import uk.co.petertribble.jkstat.api.KstatFilter;
import uk.co.petertribble.jkstat.api.KstatSet;
import uk.co.petertribble.jkstat.api.KstatType;
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;
import java.io.File;
import org.tc33.jheatchart.HeatChart;

/**
 * Create a heatmap of IO (disk) utilization.
 *
 * @author Peter Tribble
 */
public class IOHeat {

    private String stime;
    private long daystart;
    private String etime;
    private long dayend;
    private String filename;
    private String ofilename = "/tmp/test.png";
    private Map<String, Kstat> lastMap;
    private long oldsnaptime;
    private long lastboot;
    private double[][] accvals;

    /**
     * Create a IO utilization heatmap.
     *
     * @param args  The command line arguments
     */
    public IOHeat(String[] args) {
	lastMap = new HashMap<>();
	parseArgs(args);
	try {
	    accumulate(new ParseableJSONZipJKstat(filename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * ioheat [-e time] [-f filename] [-s time]
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
		} else if ("o".equals(flag)) {
		    i++;
		    ofilename = args[i];
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
	accvals = new double[20][sjkstat.size()];
	for (double[] sval : accvals) {
	    Arrays.fill(sval, 0.0);
	}
	int thisval = 0;
	try {
	    daystart = KarTime.getStartTimeInMillis(sjkstat, stime);
	    dayend = KarTime.getEndTimeInMillis(sjkstat, etime);
	} catch (IllegalArgumentException iae) {
	    usage(iae.getMessage());
	}
	KstatFilter ksf = new KstatFilter(sjkstat);
	ksf.setFilterType(KstatType.KSTAT_TYPE_IO);
	ksf.setFilterClass("disk");
	ksf.addNegativeFilter("md::");
	// skip forward to start time
	while (sjkstat.getTime() < daystart) {
	    sjkstat.next();
	}
	do {
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
	    for (Kstat ks : kss.getKstats()) {
		doPrint(ks, thisval);
	    }
	    thisval++;
	    lastMap.clear();
	    for (Kstat ks : kss.getKstats()) {
		lastMap.put(ks.getTriplet(), ks);
	    }
	} while (sjkstat.next() && sjkstat.getTime() < dayend);
	HeatChart hchart = new HeatChart(accvals);
	hchart.setCellSize(new Dimension(6, 6));
	hchart.setTitle("Disk utilization");
	hchart.setYValues(100, -5);
	hchart.setYAxisLabel("%util");
	hchart.setYAxisValuesFrequency(2);
	hchart.setXAxisLabel("Time");
	hchart.setXAxisValuesFrequency(5);
	try {
	    hchart.saveToFile(new File(ofilename));
	} catch (IOException ioe) {
	    System.err.println(ioe);
	}
    }

    private void doPrint(Kstat ks, int thisval) {

	// get the new values
	long nrtime = ks.longData("rtime");

	Kstat ksold = lastMap.get(ks.getTriplet());
	if (ksold == null) {
	    oldsnaptime = ks.getCrtime();
	} else {
	    oldsnaptime = ksold.getSnaptime();
	    nrtime -= ksold.longData("rtime");
	}
	long snapdelta = ks.getSnaptime() - oldsnaptime;

	/*
	 * We need output 0..19; while nidl is unlikely to be zero
	 * I've seen it happen far too often to rely on it.
	 *
	 * Invert so the graph is the right way up (graphs have 0 at the
	 * top, remember)
	 */
	// use %busy
	int offset = 19 - (int) Math.floor(19.99 * nrtime / snapdelta);
	if (offset > 19) {
	    offset = 19;
	}
	if (offset < 0) {
	    offset = 0;
	}
	// accumulate into array at index given by offset
	accvals[offset][thisval]++;
    }

    /*
     * Print usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: ioheat [-e time] [-f filename] "
			+ "[-o output_filename] [-s time]");
	System.exit(1);
    }

    /*
     * Print error, followed by usage message and exit.
     */
    private void usage(String s) {
	System.err.println(s);
	usage();
    }

    /**
     * Display ioheat output.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new IOHeat(args);
    }
}
