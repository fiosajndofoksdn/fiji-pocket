#!/usr/bin/env python3
"""Exercises Android's real document picker against the release application."""
import ui_smoke as u
import time

def open_file(name):
    u.click('Open')
    if name not in [n.get('text') for n in u.nodes()]:
        u.click('Show roots');u.click('Downloads')
    u.click(name)

def export(option,kill_background=False):
    u.click('Export');u.click(option)
    if kill_background:
        u.assert_text('SAVE')
        time.sleep(2)  # Let the picker transition stop/save the source Activity.
        before=u.adb('shell','pidof','org.fijipocket.android').decode().strip()
        if u.adb('shell','id','-u').decode().strip()!='0':
            raise RuntimeError('Process-death test requires adb root on a test emulator; it must not run on a personal physical device.')
        u.adb('shell','kill','-9',before);time.sleep(.5)
        print('Requested background process eviction while save picker was open; previous pid',before,flush=True)
    u.click('Save');u.assert_text('Export complete');u.click('Done')
    if kill_background:
        after=u.adb('shell','pidof','org.fijipocket.android').decode().strip()
        assert before!=after,('Background process was not evicted',before,after)
        print('PASS staged export survived process recreation:',before,'→',after,flush=True)

def main():
    u.adb('shell','am','force-stop','com.android.documentsui')
    u.adb('shell','am','force-stop','org.fijipocket.android')
    u.adb('shell','am','start','-W','-f','0x10008000','-n','org.fijipocket.android/org.fijipocket.MainActivity')
    # These are output files created only by this test.
    for name in ['fiji-lzw16-processed.tif','fiji-color-view.png','fiji-stack-results.csv','fiji-stack-processed.tif']:
        u.adb('shell','rm','-f','/sdcard/Download/'+name)
    open_file('fiji-lzw16.tif');u.assert_text('16-bit');u.assert_text('32 × 24')
    export('TIFF · all slices, full precision')
    open_file('fiji-color.png');u.assert_text('RGB')
    export('PNG · current display, 8-bit',kill_background=True)
    open_file('fiji-stack.tif');u.assert_text('3 slices');u.click('Analyze');u.click('Measure current slice');u.assert_text('393216 finite pixels')
    export('CSV · measurement results');export('TIFF · all slices, full precision')
    u.adb('shell','am','force-stop','org.fijipocket.android')
    u.adb('shell','am','start','-W','-f','0x10008000','-n','org.fijipocket.android/org.fijipocket.MainActivity')
    u.assert_text('fiji-stack.tif');u.assert_text('16-bit');u.click('Analyze');u.assert_text('saved result rows')
    destination=u.OUT/('exports-'+u.SERIAL);destination.mkdir(exist_ok=True)
    for name in ['fiji-lzw16-processed.tif','fiji-color-view.png','fiji-stack-results.csv','fiji-stack-processed.tif']:
        (destination/name).write_bytes(u.adb('exec-out','cat','/sdcard/Download/'+name))
    print('PASS release SAF import/export: TIFF, PNG, CSV; full session/results recovery',flush=True)

if __name__=='__main__':main()
