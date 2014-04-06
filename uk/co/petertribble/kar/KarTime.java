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

import java.util.Calendar;
import uk.co.petertribble.jkstat.api.SequencedJKstat;

/**
 * A class to handle times for kar, specifically the start and end time
 * parsing for the -s and -e command line flags.
 *
 * @author Peter Tribble
 */
public class KarTime {

    private KarTime() {
    }

    /**
     * Calculate the start time in milliseconds since the epoch.
     * If no start time given, this would be midnight (00:00:00) of
     * the day whose data is being analysed.
     *
     * @param sjkstat A SequencedJKstat from which the current day is derived
     * @param stime A String containing the desired start time in hhmmss format
     *
     * @return The time in milliseconds since the epoch corresponding to the
     * desired start time.
     */
    public static long getStartTimeInMillis(SequencedJKstat sjkstat,
				String stime) {
	int startHour = 0;
	int startMin = 0;
	int startSec = 0;
	if (stime != null) {
	    // parse hh:mm:ss
	    String[] ds = stime.split(":");
	    if (ds.length == 3) {
		try {
		    startHour = Integer.parseInt(ds[0]);
		    startMin = Integer.parseInt(ds[1]);
		    startSec = Integer.parseInt(ds[2]);
		} catch (NumberFormatException nfe) {
		    throw new IllegalArgumentException("invalid start time.");
		}
	    } else if (ds.length == 2) {
		try {
		    startHour = Integer.parseInt(ds[0]);
		    startMin = Integer.parseInt(ds[1]);
		} catch (NumberFormatException nfe) {
		    throw new IllegalArgumentException("invalid start time.");
		}
	    } else if (ds.length == 1) {
		try {
		    startHour = Integer.parseInt(ds[0]);
		} catch (NumberFormatException nfe) {
		    throw new IllegalArgumentException("invalid start time.");
		}
	    } else {
		throw new IllegalArgumentException("invalid start time.");
	    }
	}
	Calendar cal = Calendar.getInstance();
	// set the base time to match the date we're analysing
	cal.setTimeInMillis(sjkstat.getTime());
	// and go back to 00:00:00
	cal.set(Calendar.SECOND, startSec);
	cal.set(Calendar.MINUTE, startMin);
	cal.set(Calendar.HOUR_OF_DAY, startHour);
	return cal.getTimeInMillis();
    }

    /**
     * Calculate the end time in milliseconds since the epoch.
     * If no end time given, this would be just before midnight (23:59:59) of
     * the day whose data is being analysed.
     *
     * @param sjkstat A SequencedJKstat from which the current day is derived
     * @param etime A String containing the desired end time in hhmmss format
     *
     * @return The time in milliseconds since the epoch corresponding to the
     * desired end time.
     */
    public static long getEndTimeInMillis(SequencedJKstat sjkstat,
				String etime) {
	int endHour = 23;
	int endMin = 59;
	int endSec = 59;
	if (etime != null) {
	    // parse hh:mm:ss
	    String[] ds = etime.split(":");
	    if (ds.length == 3) {
		try {
		    endHour = Integer.parseInt(ds[0]);
		    endMin = Integer.parseInt(ds[1]);
		    endSec = Integer.parseInt(ds[2]);
		} catch (NumberFormatException nfe) {
		    throw new IllegalArgumentException("invalid end time.");
		}
	    } else if (ds.length == 2) {
		try {
		    endHour = Integer.parseInt(ds[0]);
		    endMin = Integer.parseInt(ds[1]);
		} catch (NumberFormatException nfe) {
		    throw new IllegalArgumentException("invalid end time.");
		}
	    } else if (ds.length == 1) {
		try {
		    endHour = Integer.parseInt(ds[0]);
		} catch (NumberFormatException nfe) {
		    throw new IllegalArgumentException("invalid end time.");
		}
	    } else {
		throw new IllegalArgumentException("invalid end time.");
	    }
	}
	Calendar cal = Calendar.getInstance();
	// set the base time to match the date we're analysing
	cal.setTimeInMillis(sjkstat.getTime());
	// and go forward to 00:00:00
	cal.set(Calendar.SECOND, endSec);
	cal.set(Calendar.MINUTE, endMin);
	cal.set(Calendar.HOUR_OF_DAY, endHour);
	return cal.getTimeInMillis();
    }
}
