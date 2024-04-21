#!/usr/bin/env python

"""
    Show SSL certificates available on this computer.

    Run with
        python showCerts.py [ -v ]
    The -v option prints enormous amounts of detail.

    Most computers have some kind of utility program(s) that
    do the same thing but better. For teaching it is nice to
    have a short cross-platform example in a language more
    students are familiar with.
    Written by Hugh Fisher u9011925, ANU, 2024
    Demonstration code for COMP3310
"""


import sys
from pprint import pprint
import ssl


def showCertificates(verbose):
    """Print out what Python knows about SSL certificates"""
    ctx = ssl.create_default_context()
    ctx.load_default_certs()
    loadedCerts = ctx.get_ca_certs(False)
    print("Loaded {} certificates".format(len(loadedCerts)))
    for cert in loadedCerts:
        if verbose:
            # Easy, get Python to print everything
            pprint(cert)
            print()
        else:
            # A certificate is a deeply nested and inconsistently ordered
            # dict of tuples of names and values tuples. Converting tuple
            # to dict makes it easier to test if something exists or not
            subject = cert['subject']
            # Discovered that not all the tuples are key-value pairs.
            subject = dict([e[0] for e in subject if isinstance(e, tuple)])
            # Most certs have a common name, but not all of them
            for k in ('commonName', 'organizationName'):
                if k in subject:
                    print(subject[k])
                    break
    print()

##

if __name__ == "__main__":
    verbose = False
    if len(sys.argv) > 1 and sys.argv[1] == "-v":
        verbose = True
    showCertificates(verbose)
