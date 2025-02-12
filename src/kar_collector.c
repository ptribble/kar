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

#include <stdio.h>
#include <sys/types.h>
#include <string.h>
#include <fcntl.h>
#include <kstat.h>

/* for raw kstat support */
#include <nfs/nfs.h>
#include <nfs/nfs_clnt.h>
#include <sys/sysinfo.h>
#include <sys/var.h>
#include <sys/dnlc.h>

/* everything is cast to 64-bit types */
#define LOADUINT32(N,V)   printf("\"%s\":%llu", N, V);
#define LOADUINT64(N,V)   printf("\"%s\":%llu", N, V);
#define LOADINT32(N,V)   printf("\"%s\":%lld", N, V);
#define LOADINT64(N,V)   printf("\"%s\":%lld", N, V);
#define LOADUINT32PTR(N,V)  printf("\"%s\":%llu", #N, V->N);
#define LOADUINT64PTR(N,V)  printf("\"%s\":%llu", #N, V->N);
#define LOADINT32PTR(N,V)  printf("\"%s\":%lld", #N, V->N);
#define LOADINT64PTR(N,V)  printf("\"%s\":%lld", #N, V->N);
#define LOADSTRPTR(N,V)  printf("\"%s\":\"%s\"", #N, V->N);


static kstat_ctl_t *kc;

void do_print(kstat_t *ks)
{
  kstat_named_t *kn;
  kstat_io_t *kiot;
  kstat_intr_t *kintrt;
  int itype;
  int n;
  int firsts;

  /*
   * Temporary area to handle KSTAT_DATA_CHAR
   */
  char dchar[17];
  char *dcharptr;
  dcharptr = dchar;

  /*
   * It would be nice if we could return something different at this point
   * as there are kstats that exist in the chain but have no data. Or perhaps
   * just return a new kstat with the type information filled in but no
   * data.
   */
  /* FIXME
  if (kstat_read(kc,ks,0) == -1) {
    return(VOID);
  }
  */
  kstat_read(kc,ks,0);

  itype = ks->ks_type;

  printf("{\"%s\":\"%s\",", "class", ks->ks_class);
  printf("\"%s\":%d,", "type", itype);
  printf("\"%s\":\"%s\",", "module", ks->ks_module);
  printf("\"%s\":%d,", "instance", ks->ks_instance);
  printf("\"%s\":\"%s\",", "name", ks->ks_name);
  printf("\"%s\":%llu,", "crtime", ks->ks_crtime);
  printf("\"%s\":%llu,", "snaptime", ks->ks_snaptime);

  printf("\"data\":{");

  if (itype == KSTAT_TYPE_NAMED) {
    firsts = TRUE;
    /*
     * Iterate through the data fields
     */
    for (n = ks->ks_ndata, kn = KSTAT_NAMED_PTR(ks); n > 0; n--, kn++) {
      /*
       * Some kstats have blank statistic names. Worse, they have multiple
       * such, leading to duplicate keys. Simply ignore any such statistics.
       */
      if (strcmp("", kn->name)) {
	if (firsts) {
	  firsts = FALSE;
	} else {
	  printf(",");
	}
	switch (kn->data_type) {
	case KSTAT_DATA_INT32:
	  printf("\"%s\":%lld", kn->name, (int64_t)kn->value.i32);
	  break;
	case KSTAT_DATA_UINT32:
	  printf("\"%s\":%llu", kn->name, (uint64_t)kn->value.ui32);
	  break;
	case KSTAT_DATA_INT64:
	  printf("\"%s\":%lld", kn->name, kn->value.i64);
	  break;
	case KSTAT_DATA_UINT64:
	  printf("\"%s\":%llu", kn->name, kn->value.ui64);
	  break;
	case KSTAT_DATA_STRING:
	  if (KSTAT_NAMED_STR_PTR(kn) == NULL) {
	    printf("\"%s\":\"%s\"", kn->name, "null");
	  } else {
	    printf("\"%s\":\"%s\"", kn->name, KSTAT_NAMED_STR_PTR(kn));
	  }
	  break;
	case KSTAT_DATA_CHAR:
	  strlcpy(dcharptr, kn->value.c, 16);
	  printf("\"%s\":\"%s\"", kn->name, dchar);
	  break;
	default:
	  printf("\"%s\":\"%s\"", kn->name, "junk");
	}
      }
    }
  }

  if (itype == KSTAT_TYPE_IO) {
    /*
     * Expose the kstat_io_t structure as a hash
     */
    /*
     * u_longlong_t nread,nwritten (bytes)
     * uint_t reads,writes (operations)
     * hrtime_t = int64_t times
     * the java method expects all values as long
     */
    kiot = KSTAT_IO_PTR(ks);
    LOADINT64("nread", (int64_t)kiot->nread);
    printf(",");
    LOADINT64("nwritten", (int64_t)kiot->nwritten);
    printf(",");
    LOADUINT64("reads", (uint64_t)kiot->reads);
    printf(",");
    LOADUINT64("writes", (uint64_t)kiot->writes);
    printf(",");
    LOADINT64("wtime", (int64_t)kiot->wtime);
    printf(",");
    LOADINT64("wlentime", (int64_t)kiot->wlentime);
    printf(",");
    LOADINT64("wlastupdate", (int64_t)kiot->wlastupdate);
    printf(",");
    LOADINT64("rtime", (int64_t)kiot->rtime);
    printf(",");
    LOADINT64("rlentime", (int64_t)kiot->rlentime);
    printf(",");
    LOADINT64("rlastupdate", (int64_t)kiot->rlastupdate);
    printf(",");
    LOADINT64("wcnt", (int64_t)kiot->wcnt);
    printf(",");
    LOADINT64("rcnt", (int64_t)kiot->rcnt);
  }

  if (itype == KSTAT_TYPE_INTR) {
    /*
     * early attempt at support. This is pretty easy as I just expose
     * the kstat_intr_t array as a hash. Names chosen to be the same as
     * those used by the perl kstat command.
     */
    kintrt = KSTAT_INTR_PTR(ks);
    LOADUINT64("hard", (uint64_t)kintrt->intrs[KSTAT_INTR_HARD]);
    printf(",");
    LOADUINT64("soft", (uint64_t)kintrt->intrs[KSTAT_INTR_SOFT]);
    printf(",");
    LOADUINT64("watchdog", (uint64_t)kintrt->intrs[KSTAT_INTR_WATCHDOG]);
    printf(",");
    LOADUINT64("spurious", (uint64_t)kintrt->intrs[KSTAT_INTR_SPURIOUS]);
    printf(",");
    LOADUINT64("multiple_service", (uint64_t)kintrt->intrs[KSTAT_INTR_MULTSVC]);
  }

  if (itype == KSTAT_TYPE_RAW) {
    /*
     * This is messy, as each raw kstat must be done manually.
     * The kstat browser shows the most popular kstats.
     *
     * The following are implemented:
     *
     * unix:*:var
     * nfs:*:mntinfo
     * cpustat::
     * unix:*:ncstats
     * unix:*:sysinfo
     * unix:*:vminfo
     * mm:*:phys_installed
     *
     * These kstats are ignored, generally for the same reason
     * as the perl implementation, namely that the data is bogus:
     *
     * unix:*:sfmmu_percpu_stat
     * ufs directio:*:UFS DirectIO Stats
     * sockfs:*:sock_unix_list
     *
     * These kstats are on the TODO list:
     *
     * unix:*:kstat_headers
     */
    if (!strcmp(ks->ks_module,"unix")) {
      if (!strcmp(ks->ks_name,"var")) {
	struct var *varp;
	varp = (struct var *)(ks->ks_data);
	LOADINT32PTR(v_buf, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_call, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_proc, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_maxupttl, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_nglobpris, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_maxsyspri, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_clist, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_maxup, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_hbuf, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_hmask, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_pbuf, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_sptmap, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_maxpmem, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_autoup, (uint64_t)varp);
	printf(",");
	LOADINT32PTR(v_bufhwm, (uint64_t)varp);
      }
      if (!strcmp(ks->ks_name,"ncstats")) {
	struct ncstats *ncstatsp;
	ncstatsp = (struct ncstats *)(ks->ks_data);
	LOADINT32PTR(hits, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(misses, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(enters, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(dbl_enters, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(long_enter, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(long_look, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(move_to_front, (uint64_t)ncstatsp);
	printf(",");
	LOADINT32PTR(purges, (uint64_t)ncstatsp);
      }
      if (!strcmp(ks->ks_name,"sysinfo")) {
	sysinfo_t *sysinfop;
	sysinfop = (sysinfo_t *)(ks->ks_data);
	LOADUINT32PTR(updates, (uint64_t)sysinfop);
	printf(",");
	LOADUINT32PTR(runque, (uint64_t)sysinfop);
	printf(",");
	LOADUINT32PTR(runocc, (uint64_t)sysinfop);
	printf(",");
	LOADUINT32PTR(swpque, (uint64_t)sysinfop);
	printf(",");
	LOADUINT32PTR(swpocc, (uint64_t)sysinfop);
	printf(",");
	LOADUINT32PTR(waiting, (uint64_t)sysinfop);
      }
      /*
       * this one requires 64-bit types
       */
      if (!strcmp(ks->ks_name,"vminfo")) {
	vminfo_t *vminfop;
	vminfop = (vminfo_t *)(ks->ks_data);
	LOADUINT64PTR(freemem, (uint64_t)vminfop);
	printf(",");
	LOADUINT64PTR(swap_resv, (uint64_t)vminfop);
	printf(",");
	LOADUINT64PTR(swap_alloc, (uint64_t)vminfop);
	printf(",");
	LOADUINT64PTR(swap_avail, (uint64_t)vminfop);
	printf(",");
	LOADUINT64PTR(swap_free, (uint64_t)vminfop);
	/*
	 * Requires later S10 update
	 * printf(",");
	 * LOADUINT64PTR(updates, (uint64_t)vminfop);
	 */
      }
    }
    if ((!strcmp(ks->ks_module,"nfs"))&&(!strcmp(ks->ks_name,"mntinfo"))) {
      struct mntinfo_kstat *mntinfop;
      mntinfop = (struct mntinfo_kstat *)(ks->ks_data);
      LOADSTRPTR(mik_proto, mntinfop);
      printf(",");
      LOADUINT32PTR(mik_vers, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_flags, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_secmod, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_curread, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_curwrite, (uint64_t)mntinfop);
      printf(",");
      LOADINT32PTR(mik_timeo, (int64_t)mntinfop);
      printf(",");
      LOADINT32PTR(mik_retrans, (int64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_acregmin, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_acregmax, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_acdirmin, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_acdirmax, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_noresponse, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_failover, (uint64_t)mntinfop);
      printf(",");
      LOADUINT32PTR(mik_remap, (uint64_t)mntinfop);
      printf(",");
      LOADSTRPTR(mik_curserver, mntinfop);
      printf(",");
      LOADUINT32("lookup_srtt", (uint64_t)mntinfop->mik_timers[0].srtt);
      printf(",");
      LOADUINT32("lookup_deviate", (uint64_t)mntinfop->mik_timers[0].deviate);
      printf(",");
      LOADUINT32("lookup_rtxcur", (uint64_t)mntinfop->mik_timers[0].rtxcur);
      printf(",");
      LOADUINT32("read_srtt", (uint64_t)mntinfop->mik_timers[1].srtt);
      printf(",");
      LOADUINT32("read_deviate", (uint64_t)mntinfop->mik_timers[1].deviate);
      printf(",");
      LOADUINT32("read_rtxcur", (uint64_t)mntinfop->mik_timers[1].rtxcur);
      printf(",");
      LOADUINT32("write_srtt", (uint64_t)mntinfop->mik_timers[2].srtt);
      printf(",");
      LOADUINT32("write_deviate", (uint64_t)mntinfop->mik_timers[2].deviate);
      printf(",");
      LOADUINT32("write_rtxcur", (uint64_t)mntinfop->mik_timers[2].rtxcur);
    }
    if (!strcmp(ks->ks_module,"cpu_stat")) {
      cpu_stat_t    *statp;
      cpu_sysinfo_t *sysinfop;
      cpu_syswait_t *syswaitp;
      cpu_vminfo_t  *vminfop;
      statp = (cpu_stat_t *)(ks->ks_data);
      sysinfop = &statp->cpu_sysinfo;
      syswaitp = &statp->cpu_syswait;
      vminfop  = &statp->cpu_vminfo;
      LOADUINT32("idle", (uint64_t)sysinfop->cpu[CPU_IDLE]);
      printf(",");
      LOADUINT32("user", (uint64_t)sysinfop->cpu[CPU_USER]);
      printf(",");
      LOADUINT32("kernel", (uint64_t)sysinfop->cpu[CPU_KERNEL]);
      printf(",");
      LOADUINT32("wait", (uint64_t)sysinfop->cpu[CPU_WAIT]);
      printf(",");
      LOADUINT32("wait_io", (uint64_t)sysinfop->wait[W_IO]);
      printf(",");
      LOADUINT32("wait_swap", (uint64_t)sysinfop->wait[W_SWAP]);
      printf(",");
      LOADUINT32("wait_pio", (uint64_t)sysinfop->wait[W_PIO]);
      printf(",");
      LOADUINT32PTR(bread, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(bwrite, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(lread, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(lwrite, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(phread, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(phwrite, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(pswitch, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(trap, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(intr, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(syscall, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(sysread, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(syswrite, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(sysfork, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(sysvfork, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(sysexec, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(readch, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(writech, (uint64_t)sysinfop);
      printf(",");
      /* 3 unused entries (rcvint, xmtint, mdmint) skipped */
      LOADUINT32PTR(rawch, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(canch, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(outch, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(msg, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(sema, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(namei, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(ufsiget, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(ufsdirblk, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(ufsipage, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(ufsinopage, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(inodeovf, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(fileovf, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(procovf, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(intrthread, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(intrblk, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(idlethread, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(inv_swtch, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(nthreads, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(cpumigrate, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(xcalls, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(mutex_adenters, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(rw_rdfails, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(rw_wrfails, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(modload, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(modunload, (uint64_t)sysinfop);
      printf(",");
      LOADUINT32PTR(bawrite, (uint64_t)sysinfop);
      printf(",");
      /* remaining entries skipped */
      LOADUINT32PTR(iowait, (uint64_t)syswaitp);
      printf(",");
      /* 2 unused entries (swap, physio) skipped */
      LOADUINT32PTR(pgrec, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgfrec, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgpgin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgpgout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(swapin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgswapin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(swapout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgswapout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(zfod, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(dfree, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(scan, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(rev, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(hat_fault, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(as_fault, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(maj_fault, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(cow_fault, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(prot_fault, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(softlock, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(kernel_asflt, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(pgrrun, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(execpgin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(execpgout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(execfree, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(anonpgin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(anonpgout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(anonfree, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(fspgin, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(fspgout, (uint64_t)vminfop);
      printf(",");
      LOADUINT32PTR(fsfree, (uint64_t)vminfop);
    }
  }

  printf("}}\n");
}


void do_enumerate()
{
  kstat_t *ks;
  int firstk = TRUE;
  printf("[\n");
  for (ks = kc->kc_chain; ks != 0; ks = ks->ks_next) {
    if (firstk) {
      firstk = FALSE;
    } else {
      printf(",\n");
    }
    do_print(ks);
  }
  printf("]\n");
}

int
main()
{
  kc = kstat_open();
  if (!kc) {
    perror("kstat_open");
  }
  do_enumerate();
}
