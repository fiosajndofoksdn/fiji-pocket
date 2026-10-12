#!/usr/bin/env python3
"""ADB UI helpers and a smoke flow through the installed release APK."""
import os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
ADB=ROOT/'.mobile/android-sdk/platform-tools/adb'
SERIAL=os.environ.get('ANDROID_SERIAL','emulator-5556')
OUT=Path(__file__).resolve().parent/'output'
OUT.mkdir(exist_ok=True)
def adb(*args,**kwargs):return subprocess.check_output([str(ADB),'-s',SERIAL,*args],**kwargs)
def tree():
    # A failed dump can leave yesterday's hierarchy behind; never click stale bounds.
    for _ in range(3):
        adb('shell','rm','-f','/sdcard/fiji-ui.xml')
        try:
            raw=adb('shell','uiautomator','dump','/sdcard/fiji-ui.xml')
            if b'dumped to:' not in raw:
                time.sleep(.5);continue
            return ET.fromstring(adb('exec-out','cat','/sdcard/fiji-ui.xml'))
        except (subprocess.CalledProcessError,ET.ParseError):
            time.sleep(.5)
    raise AssertionError('Could not capture a fresh UI hierarchy')
def nodes():return list(tree().iter('node'))
def click(label,contains=False):
    for n in nodes():
        text=n.get('text','');desc=n.get('content-desc','')
        if (label.casefold() in text.casefold() if contains else label.casefold()==text.casefold()) or label==desc:
            box=list(map(int,re.findall(r'\d+',n.get('bounds'))));x=(box[0]+box[2])//2;y=(box[1]+box[3])//2
            adb('shell','input','tap',str(x),str(y));time.sleep(.35);return
    raise AssertionError('UI element not found: '+label)
def assert_text(value):
    content='\n'.join(n.get('text','') for n in nodes())
    assert value in content,(value,content)
def shot(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out','screencap','-p'))
def main():
    adb('shell','am','force-stop','com.android.documentsui')
    adb('shell','am','force-stop','org.fijipocket.android')
    adb('shell','am','start','-W','-f','0x10008000','-n','org.fijipocket.android/org.fijipocket.MainActivity')
    click('More options');click('Load sample stack');click('Load sample');assert_text('16-bit')
    click('Gaussian blur');click('Apply');assert_text('Gaussian σ=2');click('More options');click('Undo last edit')
    click('Threshold…');click('Apply mask');assert_text('8-bit')
    click('Analyze');click('Analyze particles…');click('Analyze');assert_text('18 particles');shot('tablet-particles-'+SERIAL)
    click('Measure current slice');assert_text('393216 finite pixels')
    click('Image');click('Rotate 90°');assert_text('512 × 768')
    click('More options');click('Undo last edit');assert_text('768 × 512')
    # Return to the unchanged sample, exercising sample and undo paths.
    click('More options');click('Load sample stack');click('Load sample');click('Process');assert_text('16-bit')
    shot('tablet-workspace-'+SERIAL)
    print('PASS release UI: sample, Gaussian, undo, Otsu mask, 18 particles, measurement, rotation, restoration')
if __name__=='__main__':main()
