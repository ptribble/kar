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

/**
 * Print information on a kar archive.
 *
 * @author Peter Tribble
 */
public class KarInfo {

    private Map <String, Kstat> firstKstats;
    private Map <String, Kstat> lastKstats;

    /**
     * Create an new KarInfo object.
     *
     * @param sjkstat a SequencedJKstat
     */
    public KarInfo(SequencedJKstat sjkstat) {
	firstKstats = new HashMap <String, Kstat> ();
	lastKstats = new HashMap <String, Kstat> ();
	readAll(sjkstat);
	countStats();
    }

    /*
     * Scan through the SequencedJKstat, getting the kstats from each time
     * interval. The first time we see a Kstat, we add it to the firstKstats
     * map. We add all kstats to the lastKstats map. As they're constantly
     * replaced, it ends up containing the kstat from the last time we saw it.
     */
    private void readAll(SequencedJKstat sjkstat) {
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
	int num_string = 0;
	int num_num = 0;
	int num_zero = 0;
	int num_changed = 0;
	int num_ks_changed = 0;
	for (String s : firstKstats.keySet()) {
	    Kstat ks1 = firstKstats.get(s);
	    Kstat ks2 = lastKstats.get(s);
	    int ks_changed = 0;
	    for (String stat : ks1.statistics()) {
		if (ks1.isNumeric(stat)) {
		    num_num++;
		    long l1 = ks1.longData(stat);
		    long l2 = ks2.longData(stat);
		    if (l2 == 0) {
			num_zero++;
		    }
		    if (l2 != l1) {
			ks_changed = 1;
			num_changed++;
		    }
		} else {
		    num_string++;
		}
	    }
	    num_ks_changed += ks_changed;
	}
	int num_total = num_num + num_string;
	System.out.println("Total kstats: " + lastKstats.size());
	System.out.println("Total statistics: " + num_total);
	System.out.println("Numeric statistics: " + num_num);
	System.out.println("String statistics: " + num_string);
	System.out.println("Statistics zero: " + num_zero);
	System.out.println("Statistics Changed: " + num_changed);
	System.out.println("Kstats changed: " + num_ks_changed);
    }

    private static void usage(String message) {
	System.err.println("ERROR: " + message);
	System.err.println("Usage: info -f zipfile");
	System.exit(1);
    }

    /**
     * Display information on the file or directory given on the command line.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
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
