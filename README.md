Kstat Activity Reporter - kar
=============================

Traditionally, sar - system activity reporter, has been used to save
and display a small subset of system activity.

Sar suffers from being incomplete and inextensible, and some of the
statistics it does report are no longer useful.

Kar is an effort to archive and display a more complete set of system
statistics.

The basic idea is really simple: rather than save the limited set of
statistics reported by sar, simply save everything tracked by the kstat
framework and do any aggregation or analysis when the user needs it.

The result is a system that is as complete as the kstat framework, and
is immediately extensible - new kstats get picked up automatically
rather than having to modify the framework. And because the raw data is
saved, it can be analysed and displayed in any way you see fit: you
aren't limited to the handful of results that the authors of sar
thought you might need.

Installation and usage
======================

The data collector can be run by any user, they just need write access
to the destination directory.

Assuming you wish to use the same 'sys' user as is traditionally used
for sar, then:

pfexec mkdir -p /usr/lib/ka
pfexec cp kadc kaclean /usr/lib/ka
pfexec cp bin/`/usr/bin/uname -p`/kar_collector /usr/lib/ka
pfexec mkdir /var/adm/ka
pfexec chown sys /var/adm/ka

and add something like the following to the sys user's crontab

0,5,10,15,20,25,30,35,40,45,50,55 * * * * /usr/lib/ka/kadc

which will accumulate data to /var/adm/ka every 5 minutes.

You should also create a system for automatically archiving or removing
old data. See the sample kaclean script which cleans up records over 30
days old, and which can be added to the sys crontab as follows:

1 1 * * * /usr/lib/ka/kaclean

Looking at the data
===================

In version 0.1, this was just kstat -p data in files contained within
a zip archive. As of 0.2, the kar_collector program generated similar
output, with some minor differences. As of 0.7, the data is saved in
JSON format which is more compact and easier to parse.

JKstat (version 0.50 or later) can read in the zip files and allow you
to browse the statistics and generate charts.

A sample sar client using jkstat is supplied. For example:

./kar sar -f /var/adm/ka/ka-2010-04-13.zip

The -f option will default to today as expected, the only other flags
implemented are -s and -e, and the only output available is the default
(cpu usage).

You can also try

./kar iostat -f /var/adm/ka/ka-2010-05-23.zip

The input file if unspecified will be today's, assumed to be found in
the /var/adm/ka directory. You can also generate fsstat and mpstat
output.

You can generate a set of predefined charts using the graphs
subcommand:

./kar graphs -o /my/graph/location

where you need to supply an output location to put the images.

Or you can browse all available data using the kstat browser and chart
builder, by means of:

./kar browser

Plotting graphs with ploticus
=============================

Ploticus is a simple graphing package

http://ploticus.sourceforge.net/

You can use ploticus in combination with kar to graph pretty much
anything.

You'll need to create an input file. The simplest way is to use the kar
print subcommand and then filter the output with grep. (It would be
nice to think that kar would have sophisticated filtering, but it
doesn't really.)

So, suppose I was interested in writes to disk device sd4. The
following would generate a data file that ploticus could use:

./kar print :::writes | grep sd4:writes > datafile

And then I could use the chron prefab in ploticus to graph that, with
the data=datafile argument. The important thing here is that field 1 is
a time (so you need to tell ploticus that it's a time with the
unittype=time parameter) and the value is in field 3

pl -prefab chron unittype=time title="Writes to sd4" xlbl="Time" ylbl="bytes/s" mode=line data=datafile x=1 y=3

Pushing data into Graphite
==========================

If you want performance metrics from a Solaris system, then one option is
to simply throw kstats into graphite and let it handle the graphing.

http://graphiteapp.org/

The graphite command here will extract the statistics out of kar archives
and transform it into the correct form for graphite to consume. It
has a subset of the normal kar subcommands - so you can generate the
right output for iostat, mpstat, and the like.

This isn't a complete solution. I'm expecting that you may wish to process
the data. In particular, you may wish to prefix each line to match your
graphite naming convention (adding the hostname would be a good idea).
And you may wish to trim the statistics.

If you just want *everything* raw, then try

graphite print :::

The simplest way to add a prefix is with sed:

graphite iostat | sed 's:^:myhost.:'


Licensing
=========

Kar is licensed under CDDL, just like the bulk of OpenSolaris - see
the file LICENSES/CDDL-1.0.txt

Kar incorporates JFreeChart, Copyright 2000-present by David Gilbert and
Contributors. JFreeChart is covered by the LGPL - see the file
LICENSES/LGPL-2.1+.txt, and can be obtained from
https://www.jfree.org/jfreechart/.

Kar incorporates openjson https://github.com/openjson/openjson
See the file LICENSES/Apache-2.0.txt for the details of the license
for openjson.
