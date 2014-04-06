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
import uk.co.petertribble.jkstat.api.SequencedJKstat;
import uk.co.petertribble.jkstat.api.Kstat;
import uk.co.petertribble.jkstat.api.KstatType;
import uk.co.petertribble.jkstat.parse.ParseableJSONZipJKstat;

/**
 * Generate a list of kar graphs.
 *
 * @author Peter Tribble
 */
public class GraphList {

    private String zfilename;

    /**
     * Generate Kar graphs.
     *
     * @param args  The command line arguments
     */
    public GraphList(String[] args) {
	parseArgs(args);
	try {
	    makeGraphs(new ParseableJSONZipJKstat(zfilename));
	} catch (IOException ioe) {
	    usage("Invalid zip file");
	}
    }

    /*
     * Argument parser. Usage is this form.
     *
     * graphs -f zipfile
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
		    zfilename = args[i];
		} else {
		    usage();
		}
	    } else {
		usage();
	    }
	}
    }

    /*
     * Actually build the graphs. If a graph fails, carry on with the next one.
     */
    private void makeGraphs(SequencedJKstat sjkstat) {
	System.out.println("Area XX_DIR/cpu.png cpu_stat:: kernel user idle");
	for (Kstat ks : sjkstat.getKstats()) {
	    if ("cpu_stat".equals(ks.getModule())) {
		System.out.println("Area XX_DIR/cpu." + ks.getInstance()
			+ ".png " + ks.getTriplet() + " kernel user idle");
	    }
	    if ("mac".equals(ks.getName())) {
		System.out.println("Line XX_DIR/net."
			+ ks.getModule() + ks.getInstance()
			+ ".png " + ks.getTriplet() + " rbytes64 obytes64");
	    }
	    if ((ks.getType() == KstatType.KSTAT_TYPE_IO)
			&& (!"usba".equals(ks.getModule()))) {
		System.out.println("Line XX_DIR/io." + ks.getName() + ".png "
			+ ks.getTriplet() + " nread nwritten");
	    }
	}
	System.out.println("Line XX_DIR/fsstat.zfs.rwops.png "
			+ "unix:0:vopstats_zfs nread nwrite");
	System.out.println("Line XX_DIR/fsstat.zfs.rwbytes.png "
			+ "unix:0:vopstats_zfs read_bytes write_bytes");
	System.out.println("Line XX_DIR/fsstat.ufs.rwops.png "
			+ "unix:0:vopstats_ufs nread nwrite");
	System.out.println("Line XX_DIR/fsstat.ufs.rwbytes.png "
			+ "unix:0:vopstats_ufs read_bytes write_bytes");
	System.out.println("Line XX_DIR/fsstat.tmpfs.rwops.png "
			+ "unix:0:vopstats_tmpfs nread nwrite");
	System.out.println("Line XX_DIR/fsstat.tmpfs.rwbytes.png "
			+ "unix:0:vopstats_tmpfs read_bytes write_bytes");
	System.out.println("Line XX_DIR/fsstat.nfs3.rwops.png "
			+ "unix:0:vopstats_nfs3 nread nwrite");
	System.out.println("Line XX_DIR/fsstat.nfs3.rwbytes.png "
			+ "unix:0:vopstats_nfs3 read_bytes write_bytes");
	System.out.println("Line XX_DIR/fsstat.nfs4.rwops.png "
			+ "unix:0:vopstats_nfs4 nread nwrite");
	System.out.println("Line XX_DIR/fsstat.nfs4.rwbytes.png "
			+ "unix:0:vopstats_nfs4 read_bytes write_bytes");
	System.out.println("Line XX_DIR/fsstat.lofs.rwops.png "
			+ "unix:0:vopstats_lofs nread nwrite");
	System.out.println("Line XX_DIR/fsstat.lofs.rwbytes.png "
			+ "unix:0:vopstats_lofs read_bytes write_bytes");
	System.out.println("AreaValue XX_DIR/zfs.size.png zfs:0:arcstats size");
	System.out.println("LineValue XX_DIR/loadave.png unix:0:system_misc "
			+ "avenrun_1min avenrun_5min avenrun_15min");
	System.out.println("LineValue XX_DIR/numproc.png unix:0:system_misc "
			+ "nproc");
	System.out.println("LineValue XX_DIR/freemem.png unix:0:system_pages "
			+ "lotsfree minfree pagesfree physmem");
    }

    /*
     * Emit usage message and exit.
     */
    private void usage() {
	System.err.println("Usage: graphlist [-f zip_filename]");
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
     * Generate Kar graphs.
     *
     * @param args  The command line arguments
     */
    public static void main(String[] args) {
	new GraphList(args);
    }
}
