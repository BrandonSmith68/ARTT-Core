#!/bin/bash

pip3 install scipy numpy jep

git submodule update
cd lib/jep
python3 setup.py install
