#!/bin/bash

if [ $# -ne 1 ]; then
    echo "usage: encrypt.sh <password>"
    exit 1
fi

shopt -s dotglob

for f in .secret/*
do
    if ! [[ "$f" == *sh ]] ; then
        cat $f | openssl aes-256-cbc -a -k  "$1" > .secret-enc/$(basename $f)
    fi
done

echo "How to decrypt:"
echo "cat FILENAME | openssl aes-256-cbc -a -d -k  \"$1\""
