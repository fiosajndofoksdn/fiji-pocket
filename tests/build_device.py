#!/usr/bin/env python3
from pathlib import Path
import os,subprocess,zipfile
APP=Path(__file__).resolve().parent.parent
ROOT=APP.parent
JAVA=ROOT/'.mobile/jdk-17/Contents/Home'
SDK=ROOT/'.mobile/android-sdk'
TOOLS=SDK/'build-tools/36.0.0'
ANDROID=SDK/'platforms/android-36/android.jar'
OUT=APP/'build/device-tests'
OUT.mkdir(parents=True,exist_ok=True)
env=dict(os.environ,JAVA_HOME=str(JAVA),JAVA_OPTS='-Xmx512m')
env['PATH']=str(JAVA/'bin')+os.pathsep+env.get('PATH','')
def run(*args):subprocess.run([str(a) for a in args],check=True,env=env)
for d in ['classes','dex']:(OUT/d).mkdir(exist_ok=True)
run(TOOLS/'aapt2','link','--manifest',APP/'tests/AndroidManifest.xml','-I',ANDROID,'-o',OUT/'unsigned.apk')
run(JAVA/'bin/javac','-encoding','UTF-8','--release','8','-cp',str(ANDROID)+os.pathsep+str(APP/'build/tests'),'-d',OUT/'classes',APP/'tests/DeviceChecks.java',APP/'tests/CoreChecks.java',APP/'tests/DemoChecks.java')
run(TOOLS/'d8','--min-api','26','--lib',ANDROID,'--classpath',APP/'build/tests','--output',OUT/'dex',*sorted((OUT/'classes').rglob('*.class')))
with zipfile.ZipFile(OUT/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as apk:
    for f in (OUT/'dex').glob('*.dex'):apk.write(f,f.name)
run(TOOLS/'zipalign','-f','4',OUT/'unsigned.apk',OUT/'aligned.apk')
run(TOOLS/'apksigner','sign','--ks',ROOT/'.mobile/fiji-pocket.keystore','--ks-key-alias','fijipocket','--ks-pass','pass:android','--out',OUT/'device-tests.apk',OUT/'aligned.apk')
print(OUT/'device-tests.apk')
