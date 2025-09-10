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
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;

/**
 * Print information on a kar archive.
 *
 * @author Peter Tribble
 */
public class KarInfo {

    private Map<String, Kstat> firstKstats;
    private Map<String, Kstat> lastKstats;

    /**
     * Create an new KarInfo object.
     *
     * @param sjkstat a SequencedJKstat
     */
    public KarInfo(final SequencedJKstat sjkstat) {
	firstKstats = new HashMap<>();
	lastKstats = new HashMap<>();
	readAll(sjkstat);
	countStats();
    }

    /*
     * Scan through the SequencedJKstat, getting the kstats from each time
     * interval. The first time we see a Kstat, we add it to the firstKstats
     * map. We add all kstats to the lastKstats map. As they're constantly
     * replaced, it ends up containing the kstat from the last time we saw it.
     */
    private void readAll(final SequencedJKstat sjkstat) {
	do {
	    for (Kstat ks : sjkstat.getKstats()) {
		String kst = ks.getTriplet();
		if (!firstKstats.containsKey(kst)) {
		    firstKstats.put(kst, ks);
		}
		lastKstats.put(kst, ks);
	    }
	} while (sjkstat.next());
    }

    /*
     * Count the number of statistics, and how many are numbers and strings.
     */
    private void countStats() {
	int numstring = 0;
	int numnumeric = 0;
	int numzero = 0;
	int numchanged = 0;
	int numkschanged = 0;
	for (String s : firstKstats.keySet()) {
	    Kstat ks1 = firstKstats.get(s);
	    Kstat ks2 = lastKstats.get(s);
	    int kschanged = 0;
	    for (String stat : ks1.statistics()) {
		if (ks1.isNumeric(stat)) {
		    numnumeric++;
		    long l1 = ks1.longData(stat);
		    long l2 = ks2.longData(stat);
		    if (l2 == 0) {
			numzero++;
		    }
		    if (l2 != l1) {
			kschanged = 1;
			numchanged++;
		    }
		} else {
		    numstring++;
		}
	    }
	    numkschanged += kschanged;
	}
	int numtotal = numnumeric + numstring;
	System.out.println("Total kstats: " + lastKstats.size());
	System.out.println("Total statistics: " + numtotal);
	System.out.println("Numeric statistics: " + numnumeric);
	System.out.println("String statistics: " + numstring);
	System.out.println("Statistics zero: " + numzero);
	System.out.println("Statistics Changed: " + numchanged);
	System.out.println("Kstats changed: " + numkschanged);
    }

    private static void usage(final String message) {
	System.err.println("ERROR: " + message);
	System.err.println("Usage: info -f zipfile");
	System.exit(1);
    }

    /**
     * Display information on the file or directory given on the command line.
     *
     * @param args the command line arguments
     */
    public static void main(final String[] args) {
	if (args.length != 2) {
	    usage("Missing arguments.");
	}
	if ("-f".equals(args[0])) {
	    try {
		new KarInfo(new ParseableJSONZipJKstat(args[1]));
	    } catch (IOException ioe) {
		usage("Invalid zip file");
	    }
	} else {
	    usage("Invalid arguments.");
	}
    }
}
