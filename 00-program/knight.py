#!/usr/bin/env python

"""
    COMP3310 Tute 0 programming warmup exercise
    Second of two programs. Run with
        python knight.py

    Written by H Fisher u9011925, ANU, 2024
    This code may be freely copied and modified
"""

import sys


def inputLoop():
    """Read input until EOF. Response depends on input"""
    while True:
        try:
            line = input()
        except EOFError:
            break
        chooseResponse(line)

def chooseResponse(line):
    """Decide what to print based on input line"""
    if line == "it":
        # Crash
        raise RuntimeError("Aaargh! You said it!")
    elif line == "ni":
        # Go into infinite loop
        while True:
            print("Ni! Ni! Ni!")
    else:
        print(line)


def processArgs(argv):
    """No command line arguments"""
    if len(argv) > 1:
        raise RuntimeError("No command line arguments")

##

if __name__ == "__main__":
    processArgs(sys.argv)
    inputLoop()
    print("Done.")
