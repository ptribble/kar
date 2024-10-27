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

import java.text.DecimalFormat;
import java.text.DateFormat;
import java.util.Date;

/**
 * Utility methods to convert raw numbers into more aesthetically pleasing
 * human readable output.
 *
 * @author Peter Tribble
 */
public final class PrettyFormat {

    private static final double KSCALE = 1024.0;
    private static final double KMAX = 8000.0;
    /*
     * The first name is blank, and is for unconverted bytes, and I have to
     * use String because a char can't be empty.
     */
    private static final String[] NAMES = { "", "K", "M", "G", "T", "P", "E" };
    private static final DecimalFormat DF = new DecimalFormat("##0");
    private static final DecimalFormat DFS = new DecimalFormat("##0.0");
    private static final DecimalFormat DFT = new DecimalFormat("00");
    private static DateFormat dt = DateFormat.getDateTimeInstance();
    private static DateFormat dtt = DateFormat.getTimeInstance();

    /**
     * Hide the constructor.
     */
    private PrettyFormat() {
    }

    /**
     * Return a human readable version of the input number, with
     * an extra letter to denote K/M/G/T/P/E. The number is scaled
     * by 1024 as many times as necessary.
     *
     * @param l The input number to be scaled
     *
     * @return A scaled textual representation of the input value
     */
    public static String memscale(Long l) {
	return (l == null) ? memscale(0.0) : memscale(l.longValue());
    }

    /**
     * Return a human readable version of the input number, with
     * an extra letter to denote K/M/G/T/P/E. The number is scaled
     * by 1024 as many times as necessary.
     *
     * @param l The input number to be scaled
     *
     * @return A scaled textual representation of the input value
     */
    public static String memscale(long l) {
	return memscale((double) l);
    }

    /**
     * Return a human readable version of the input number, assumed to be in
     * bytes, with an extra letter to denote K/M/G/T/P/E. The number is scaled
     * by 1024 as many times as necessary.
     *
     * @param l The input number to be scaled
     *
     * @return A scaled textual representation of the input value
     */
    public static String memscale(double l) {
	double lvalue = l;
	int i = 0;
	while (lvalue > KMAX && i < 5) {
	    lvalue = lvalue/KSCALE;
	    i++;
	}
	return DF.format(lvalue) + NAMES[i];
    }

    /**
     * Return a human readable version of the elapsed time. Returns fractional
     * seconds, whole seconds, or minutes and seconds depending on the value.
     *
     * @param d The input time to be scaled
     *
     * @return A scaled textual representation of the input time
     */
    public static String timescale(Double d) {
	return (d == null) ? timescale(0.0) : timescale(d.doubleValue());
    }

    /**
     * Return a human readable version of the elapsed time. Returns fractional
     * seconds, whole seconds, or minutes and seconds depending on the value.
     *
     * @param d The input time to be scaled
     *
     * @return A scaled textual representation of the input time
     */
    public static String timescale(double d) {
	if (d < 10.0) {
	    return DFS.format(d);
	}
	if (d < 60.0) {
	    return DF.format(d);
	}
	long secs = (long) d;
	long ssecs = secs % 60;
	long mins = (secs - ssecs)/60;
	return DF.format(mins) + ":" + DFT.format(ssecs);
    }

    /**
     * Return a human readable version of the date. We pass the time in
     * seconds.
     *
     * @param l The date or time in seconds to be converted
     *
     * @return A scaled textual representation of the input date
     */
    public static String date(Long l) {
	return (l == null) ? "-" : date(l.longValue());
    }

    /**
     * Return a human readable version of the date. We pass the time in
     * seconds.
     *
     * @param l The date or time in seconds to be converted
     *
     * @return A scaled textual representation of the input date
     */
    public static String date(long l) {
	long now = System.currentTimeMillis();
	long then = l*1000;
	if ((now - then) < 3600000) {
	    return dtt.format(new Date(then));
	}
	return dt.format(new Date(then));
    }
}
