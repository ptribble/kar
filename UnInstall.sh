#!/bin/ksh
#
# SPDX-License-Identifier: CDDL-1.0
#
# uninstall kar
#

#
# just exit silently if we have nothing to do
#
if [ ! -d /usr/lib/ka ]; then
    exit 0
fi

#
# remove cron entry
#
CTFILE=/tmp/ka.crontab.$$
if [ -f /var/spool/cron/crontabs/sys ]; then
    /usr/bin/grep -v /usr/lib/ka /var/spool/cron/crontabs/sys > ${CTFILE}
    /usr/bin/su sys -c "crontab ${CTFILE}"
    /usr/bin/rm ${CTFILE}
fi

#
# remove binaries
#
/usr/bin/rm -fr /usr/lib/ka

#
# remove data. Should this be optional?
#
/usr/bin/rm -fr /var/adm/ka

#
# remove manpages
#
/usr/bin/rm -f /usr/share/man/man1/kar.1
/usr/bin/rm -f /usr/share/man/man8/kadc.8
# this was the old location before the rename
/usr/bin/rm -f /usr/share/man/man1m/kadc.1m
