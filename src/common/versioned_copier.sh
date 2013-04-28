#!/bin/sh
#
# moves files between <local path> and <backup path>. On server side
# it maintains multiple versions.

# usage:
#  1. Backing up
#     # versioned_copier sync <local path> <backup path>
#
#  2. Restore files 
#     # versioned_copier restore <backup path>[@<time-stamp>] <local path>

setup_bkdir() {
    bkdir=$1
    if [ -d $bkdir ]
    then
        return
    fi
    mkdir -p ${bkdir}
    mkdir ${bkdir}/.metadata
    mkdir ${bkdir}/.metadata/csum
    mkdir ${bkdir}/.metadata/index
}

# get last index file before this time-stamp
get_index_file () {
    bkdir=$1
    ts=$2
    min_delta=none
    use_ts=none
    for ff in `ls ${bkdir}/.metadata/index/file_index.* 2> /dev/null`
    do
        this_ts=`echo $ff|awk -F. '{print $NF}'`
        dts=`expr $ts - $this_ts`
        if [ $use_ts = "none" ]
        then
            use_ts=$this_ts
            min_delta=$dts
        else
            if [ $dts -lt $min_delta ]
            then
                use_ts=$this_ts
                min_delta=$dts
            fi
        fi
    done
    if [ $use_ts != "none" ]
    then
        echo -n ${bkdir}/.metadata/index/file_index.$use_ts
    fi
}

sanitize_fname() {
    fname=$1
    echo `echo -n $fname|sed -e 's#//\+#/#g' -e 's#/./#/#g'`
}

copy_files () {
    locdir=$1
    bkdir=$2
    cur_ts=$3
    use_index_file=${bkdir}/.metadata/index/file_index.$cur_ts
    use_dir=${bkdir}/data/$cur_ts
    mkdir -p ${use_dir}
    for ff in `find $locdir -type f`
    do
        dname=`dirname $ff`
        bdname=`sanitize_fname ${use_dir}/$dname`
        bfname=`sanitize_fname ${use_dir}/$ff`
        if [ ! -d $bdname ]
        then
            mkdir -p $bdname
        fi
        cp $ff $bfname
        echo $ff `md5sum $bfname` >> $use_index_file
        echo $ff
    done
}

lookup_file_entry() {
    index_file=$1
    # escape special characters
    fname=`echo $2|sed -e 's#\.#\\\.#g' -e 's#\+#\\\+#g' -e 's#\*#\\\*#g'`
    entry=`egrep "^$fname " $index_file`
    echo -n $entry
}

delta_copy_files() {
    locdir=$1
    bkdir=$2
    head_index_file=$3
    cur_ts=$4
    use_dir=${bkdir}/data/$cur_ts
    use_index_file=${bkdir}/.metadata/index/file_index.$cur_ts
    mkdir -p ${use_dir}
    for ff in `find $locdir -type f`
    do
        f_entry=`lookup_file_entry $head_index_file $ff`
        if [ ! -z "$f_entry" ]
        then
            bk_md5=`echo $f_entry|awk '{print $2}'`
            loc_md5=`md5sum $ff|awk '{print $1}'`
            if [ $loc_md5 = $bk_md5 ]
            then
                echo $f_entry >> $use_index_file
                continue
            fi
        fi
        bfname=`sanitize_fname ${use_dir}/$ff`
        dname=`dirname $ff`
        bdname=`sanitize_fname ${use_dir}/$dname`
        if [ ! -d $bdname ]
        then
            mkdir -p $bdname
        fi
        cp $ff $bfname
        echo $ff `md5sum $bfname` >> $use_index_file
        echo $ff
    done
}

backup_files() {
    locdir=$1
    bkdir=$2
    cur_ts=`date +%s`
    setup_bkdir $bkdir
    head_index_file=`get_index_file $bkdir $cur_ts`
    if [ -z "$head_index_file" ]
    then
        copy_files $locdir $bkdir $cur_ts
    else
        delta_copy_files $locdir $bkdir $head_index_file $cur_ts
    fi
}

usage() {
    cat << EOF
 usage:
  1. Backing up
     # versioned_copier sync <local path> <backup path>

  2. Restore files 
     # versioned_copier restore <backup path>[@<time-stamp>] <local path>
EOF
    exit 1
}

if [ $# -lt 2 ]
then
    usage
fi

cmd=$1

if [ $cmd = "sync" ]
then
    locdir=$2
    bkdir=$3
    if [ -z "$locdir" ] || [ -z "$bkdir" ]
    then
        usage
    fi
    backup_files $locdir $bkdir
fi

# vim: ts=4:et:sw=4
