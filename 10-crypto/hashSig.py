#!/usr/bin/env python

"""
    Calculate and print hash (signature) for a file,
    or show available hash algorithms.

    Run with
        python hashSig.py file [ algorithm ]
    or
        python hashSig.py -list

    Written by Hugh Fisher u9011925, ANU, 2024
    Demonstration code for COMP3310
"""


import sys
import hashlib

algo = "md5"

def hash(infile, algo):
    """Print hash signature for infile"""
    # Hash algorithm
    digest = hashlib.new(algo)
    # Data to sign
    src = open(infile, "rb")
    while True:
        block = src.read(1024)
        if len(block) <= 0:
            break
        digest.update(block)
    src.close()
    print(digest.hexdigest())

def available():
    """Print list of hash algorithms Python can find"""
    algos = hashlib.algorithms_available
    print("Algorithm names that work on this system")
    print(sorted(algos))

##

if __name__ == "__main__":
    if sys.argv[1] == "-list":
        available()
    else:
        infile = sys.argv[1]
        if len(sys.argv) > 2:
            algo = sys.argv[2]
        hash(infile, algo)
