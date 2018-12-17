#!/bin/bash

for arg in "$@"; do
    case $arg in
        -d|--drop)
            drop=true
            ;;
        *)
            echo "unrecognized argument $arg" 1>&2
	    echo "usage: `basename $0` [--drop]" 1>&2

            exit 1
    esac
done

jar_dir="`dirname $0`/../../../build/sdk/target"

if [ ! -d "$jar_dir" ]; then
    echo "must build the sdk first"

    exit 1
fi

arguments="--script"

if [ "$drop" == "true" ]; then
    arguments="$arguments --drop"
fi

java -cp "$jar_dir"/sdk-*-jar-with-dependencies.jar -Dlog4j.configuration=sdk_log4j.xml org.ngbw.utils.CreateTables $arguments > create_cipres_tables.sql
