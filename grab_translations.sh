#!/bin/bash

cd ../default/res

for d in values*
do
    source=$d/strings.xml
    dest_dir=../../free/res/$d

    if [ -e $source ]
    then
        if [ ! -d $dest_dir ]
        then
            echo "Creating res/$d"
            mkdir $dest_dir
        fi

        cp $source $dest_dir
    fi
done
