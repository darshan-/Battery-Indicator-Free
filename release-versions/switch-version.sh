#!/bin/bash

NORMAL_DIR='normal-icons-pre-v11'
V11_DIR='black-square-icons-v11'
NORMAL_ARG='normal'
V11_ARG='v11'
SWAP_ARG='swap'

function swap_current {
    pushd $wd >/dev/null

    if [ $current = $NORMAL_DIR ]
    then
        ln -sfn $V11_DIR current
    else
        ln -sfn $NORMAL_DIR current
    fi

    current=`basename \`realpath current\``
    popd >/dev/null
}

function set_cur_to_req {
    pushd $wd >/dev/null

    ln -sfn $req_dir current

    current=`basename \`realpath current\``
    popd >/dev/null
}

wd=`dirname \`realpath $0\``
cd $wd

current=`basename \`realpath current 2> /dev/null\` 2> /dev/null`

if [ $? -ne 0 ]
then
    current='none'
fi

if [ $# -gt 0 ]
then
    if [ $1 = $NORMAL_ARG ]
    then
        req_dir=$NORMAL_DIR
    elif [ $1 = $V11_ARG ]
    then
        req_dir=$V11_DIR
    else
        echo "Error: '$1' not valid; please choose '$NORMAL_ARG' or '$V11_ARG', or leave off to swap."
        exit
    fi
else
    if [ $current = 'none' ]
    then
        echo "Please choose '$NORMAL_ARG' or '$V11_ARG' to set initial version."
        exit
    fi

    req_dir=$SWAP_ARG
fi

if [ $req_dir = $current ]
then
    echo "Already set to $current"
    exit
fi

function rm_cur {
    cd current

    for f in */*
    do
        rm ../../res/$f
    done

    for d in *
    do
        rmdir --ignore-fail-on-non-empty $d
    done

    cd ..
}

function cp_cur {
    cd current

    for d in *
    do
        mkdir $d 2> /dev/null
    done

    for f in */*
    do
        cp $f ../../res/$f
    done

    cd ..
}

if [ $current = 'none' ]
then
    set_cur_to_req
else
    rm_cur
    swap_current
fi

cp_cur
