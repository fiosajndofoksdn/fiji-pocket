#!/usr/bin/env python3
from pathlib import Path
import subprocess
APP=Path(__file__).resolve().parent.parent
ROOT=APP.parent
JAVA=ROOT/'.mobile/jdk-17/Contents/Home/bin'
BUILD=APP/'build/tests'
BUILD.mkdir(parents=True,exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True,cwd=APP)
sources=list((APP/'src/ij').rglob('*.java'))+list((APP/'src/org/fijipocket/core').rglob('*.java'))
run(JAVA/'javac','-encoding','UTF-8','--release','8','-d',BUILD,*sources,APP/'tests/CoreChecks.java',APP/'tests/CompareReference.java',APP/'tests/FixtureChecks.java',APP/'tests/DemoChecks.java')
run(JAVA/'java','-cp',BUILD,'org.fijipocket.tests.CoreChecks',APP/'tests/output')
run(JAVA/'java','-cp',BUILD,'org.fijipocket.tests.DemoChecks',APP/'assets/demos',APP/'tests/output/demos')
if (APP/'tests/output/fixtures/raw-16.tif').exists():
    run(JAVA/'java','-cp',BUILD,'org.fijipocket.tests.FixtureChecks',APP/'tests/output/fixtures')
reference=APP/'build/reference'
if reference.exists() and (reference/'ij/ImagePlus.class').exists():
    run(JAVA/'javac','-encoding','UTF-8','--release','8','-cp',reference,'-d',reference,APP/'tests/ImageJReference.java')
    run(JAVA/'java','-Djava.awt.headless=true','-cp',reference,'org.fijipocket.tests.ImageJReference',APP/'tests/output/reference')
    run(JAVA/'java','-cp',BUILD,'org.fijipocket.tests.CompareReference',APP/'tests/output/reference')
else:print('Reference comparison skipped: compile pristine ImageJ into build/reference first.')
