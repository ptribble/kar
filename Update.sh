#!/bin/ksh
#
# update kar, just the binaries, leaves the data and cron
# entries intact
#

bail() {
    echo "ERROR: $1"
    exit 1
}

if [ ! -f kadc ]; then
    cd $(dirname "$0") || bail "cannot cd to find $0"
fi

if [ ! -f kadc ]; then
    bail "Oops. Unable to find kar."
fi

if [ ! -f /usr/lib/ka/kadc ]; then
    bail "KAR not installed, please install rather than update."
fi

/usr/bin/mkdir -m 0755 /usr/lib/ka
/usr/bin/cp kadc kaclean README /usr/lib/ka
/usr/bin/cp bin/$(/usr/bin/uname -p)/kar_collector /usr/lib/ka

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
