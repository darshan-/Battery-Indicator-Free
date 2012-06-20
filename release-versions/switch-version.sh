#!/bin/bash

# cd current
# for each file in each dir in current, remove it from actual res/
# go back up
# swap current
# cd current
# for each file in each dir in current, copy to actual res/

wd=`dirname \`realpath $0\``
current=`basename \`realpath current\``
normal_dir='normal-icons-pre-v11'
blacksq_dir='black-square-icons-v11'

function swap_current {
    pushd $wd >/dev/null

    if [ $current = $normal_dir ]
    then
        ln -sfn $blacksq_dir current
    else
        ln -sfn $normal_dir current
    fi

    current=`basename \`realpath current\``

    popd >/dev/null
}


swap_current

cd current

for f in */*
do
    echo rm ../../res/$f
done

cd ..



# cd ../default/res

# for d in values*
# do
#     source=$d/strings.xml
#     dest_dir=../../free/res/$d

#     if [ -e $source ]
#     then
#         if [ ! -d $dest_dir ]
#         then
#             echo "Creating res/$d"
#             mkdir $dest_dir
#         fi

#         cp $source $dest_dir
#     fi
# done
