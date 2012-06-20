#!/bin/bash

# cd current
# for each file in each dir in current, remove it from actual res/
# go back up
# swap current
# cd current
# for each file in each dir in current, copy to actual res/

function swap_current {
    if [ -e `realpath` ]
    then
    fi
}

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
