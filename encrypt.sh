#!/bin/bash

KEY=$(cat ./.secret/password)
shopt -s dotglob

for f in .secret/*
do
    if ! [[ "$f" == *sh ]] ; then
	echo .secret-enc/$(basename $f)
        cat $f | openssl aes-256-cbc -a -md sha256 -k "$KEY" > .secret-enc/$(basename $f)
    fi
done

echo "How to decrypt:"
echo "cat FILENAME | openssl aes-256-cbc -a -d -md sha256 -k \"$KEY\""
