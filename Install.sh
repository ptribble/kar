#!/bin/ksh
#
# SPDX-License-Identifier: CDDL-1.0
#
# install kar
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

if [ -f /usr/lib/ka/kadc ]; then
    bail "KAR already installed, please uninstall the old version first."
fi

/usr/bin/mkdir -m 0755 /usr/lib/ka
/usr/bin/cp kadc kaclean README /usr/lib/ka
/usr/bin/cp bin/$(/usr/bin/uname -p)/kar_collector /usr/lib/ka
/usr/bin/mkdir -m 0755 /var/adm/ka
/usr/bin/chown sys /var/adm/ka

if [ -d /usr/share/man/man1 ]; then
    /usr/bin/cp man1/kar.1 /usr/share/man/man1
fi
if [ -d /usr/share/man/man8 ]; then
    /usr/bin/cp man1/kadc.8 /usr/share/man/man8
fi

CTFILE=/tmp/ka.crontab.$$

if [ -f /var/spool/cron/crontabs/sys ]; then
    /usr/bin/cp /var/spool/cron/crontabs/sys ${CTFILE}
else
    /usr/bin/touch ${CTFILE}
fi

#
# add crontab entry. Note that (stupidly) /lib/svc/method/svc-sar
# will blindly delete the sys crontab if system/sar is disabled,
# which is almost certainly not what we want to happen.
#

#
# adding the comment below allows for uninstall by 'grep -v /usr/lib/ka/'
#
cat <<EOF >> ${CTFILE}
# See /usr/lib/ka/README
0,5,10,15,20,25,30,35,40,45,50,55 * * * * /usr/lib/ka/kadc
1 1 * * * /usr/lib/ka/kaclean
EOF

#
# this assumes sys is a valid account with a valid shell and that
# it hasn't been disabled in cron.deny/cron.allow
#
/usr/bin/chown sys ${CTFILE}
/usr/bin/su sys -c "crontab ${CTFILE}"
/usr/bin/rm ${CTFILE}
