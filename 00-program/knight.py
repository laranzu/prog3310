#!/usr/bin/env python

"""
    COMP3310 Tute 0 programming warmup exercise
    Second of two programs. Run with
        python knight.py

    Written by H Fisher, ANU, 2024
    Creative Commons CC-BY-4.0 license
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
