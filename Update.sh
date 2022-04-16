#!/bin/ksh
#
# update kar, just the binaries, leaves the data and cron
# entries intact
#

if [ ! -f kadc ]; then
    cd `dirname $0`
fi

if [ ! -f kadc ]; then
    echo "Oops. Unable to find kar."
    exit 1
fi

if [ ! -f /usr/lib/ka/kadc ]; then
    echo "KAR not installed, please install rather than update."
    exit 1
fi

#
# We can't upgrade from 0.1 as the format changed. I hope nobody
# ever gets trapped here.
#
if [ ! -f /usr/lib/ka/kar_collector ]; then
    echo "Old incompatible version of kar found. Please uninstall"
    echo "the old version and install this version afresh."
    exit 1
fi

/usr/bin/mkdir -m 0755 /usr/lib/ka
/usr/bin/cp kadc kaclean README /usr/lib/ka
/usr/bin/cp bin/`/usr/bin/uname -p`/kar_collector /usr/lib/ka

if [ -d /usr/share/man/man1 ]; then
    /usr/bin/rm -f /usr/share/man/man1/kar.1
    /usr/bin/cp man1/kar.1 /usr/share/man/man1
fi
if [ -d /usr/share/man/man1m ]; then
    /usr/bin/rm -f /usr/share/man/man1m/kadc.1m
fi
if [ -d /usr/share/man/man8 ]; then
    /usr/bin/rm -f /usr/share/man/man8/kadc.8
    /usr/bin/cp man1/kadc.8 /usr/share/man/man8
fi
