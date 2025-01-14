#!/usr/bin/python

# Python program to build COMP3310 Tutes zip
# Usage: python make.py


import glob, logging, os, sys, zipfile
from os import path
from glob import glob

skipDirs = (
	".hg",
	".git",
	"__pycache__",
)

skipFiles = (
	".zip",
	".log",
	".aux",
	# Not PDF, include for people who don't have LaTeX
	".pyc",
	".class",
	)

def keep(fName):
	ext = path.splitext(fName)[1]
	return ext not in skipFiles


ZipName = "comp3310-2025-tutes.zip"

root = "."
if len(sys.argv) > 1:
	root = sys.argv[0]


logging.basicConfig(level=logging.INFO, format="%(message)s")


##	Create ZIP file

def copy(archive, DIR, files=None):
	"""Copy directory and either listed files or all into archive"""
	if not (path.exists(DIR) and path.isdir(DIR)):
		raise RuntimeError("Expected {}/ subdir".format(DIR))
	if DIR in skipDirs:
		return
	if files:
		archive.write(DIR)
		logging.info(DIR)
		for f in files:
			name = path.join(DIR, f)
			if path.isdir(name):
				copy(archive, name)
			else:
				archive.write(name)
				logging.info(name)
	else:
		allFiles = [path.basename(match) for match in glob(path.join(DIR, "*"))]
		allFiles = filter(keep, allFiles)
		copy(archive, DIR, allFiles)

logging.info("Creating {} ...".format(ZipName))
archive = zipfile.ZipFile(ZipName, mode='w')

copy(archive, root, None)

archive.close()

##

print("Done.")
