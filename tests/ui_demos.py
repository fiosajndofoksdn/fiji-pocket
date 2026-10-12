#!/usr/bin/env python3
"""Release UI checks for real, offline biology demos on a tablet emulator."""
import re
import time
import ui_smoke as u

COMPONENT='org.fijipocket.android/org.fijipocket.MainActivity'

def wait_text(value):
    for _ in range(12):
        content='\n'.join(n.get('text','') for n in u.nodes())
        if value in content:
            return
        time.sleep(.3)
    raise AssertionError((value,content))

def restart():
    u.adb('shell','am','force-stop','com.android.documentsui')
    u.adb('shell','am','force-stop','org.fijipocket.android')
    u.adb('shell','am','start','-W','-f','0x10008000','-n',COMPONENT)

def open_demo(title):
    u.click('Demos');u.click('Open '+title)
    wait_text(title+' · original.tif')

def export_as(option,name):
    # The filename prefix is reserved for these test outputs.
    assert re.fullmatch(r'biology-check-[a-z0-9-]+\.(tif|csv|png)',name)
    u.adb('shell','rm','-f','/sdcard/Download/'+name)
    u.click('Export');u.click(option);wait_text('SAVE')
    for n in u.nodes():
        if n.get('class')=='android.widget.EditText':
            box=list(map(int,re.findall(r'\d+',n.get('bounds'))))
            u.adb('shell','input','tap',str((box[0]+box[2])//2),str((box[1]+box[3])//2))
            u.adb('shell','input','keycombination','113','29')
            u.adb('shell','input','text',name)
            u.adb('shell','input','keyevent','4')
            break
    else:
        raise AssertionError('Save filename field missing')
    u.click('Save');wait_text('Export complete');u.click('Done')
    destination=u.OUT/('demos-'+u.SERIAL);destination.mkdir(exist_ok=True)
    (destination/name).write_bytes(u.adb('exec-out','cat','/sdcard/Download/'+name))

def main():
    restart()
    u.click('Demos');u.assert_text('Biology demos')
    for title in ['Fluorescent cells','Fruit-fly brain','Embryos','Leaf structure']:
        u.assert_text('Open '+title)
    u.shot('biology-gallery-'+u.SERIAL);u.click('Close')
    for key,title,summary in [('cells','Fluorescent cells','6 segmented regions'),
                              ('brain','Fruit-fly brain','across 57 slices'),
                              ('embryos','Embryos','22 segmented regions'),
                              ('leaf','Leaf structure','Sobel edges')]:
        open_demo(title);u.assert_text('RGB')
        u.click('Guide');u.assert_text('https://imagej.net/ij/images/');u.click('Close')
        if key=='brain':
            u.assert_text('57 slices')
            slider=next(n for n in u.nodes() if n.get('class')=='android.widget.SeekBar')
            b=list(map(int,re.findall(r'\d+',slider.get('bounds'))))
            u.adb('shell','input','tap',str(b[2]-5),str((b[1]+b[3])//2));u.assert_text('Slice 57 / 57')
        u.click('Run demo');wait_text(title+' · demo complete');u.assert_text(summary)
        u.click('Explore result');u.assert_text('8-bit');u.shot('biology-'+key+'-'+u.SERIAL)
        if key=='cells':
            export_as('TIFF · all slices, full precision','biology-check-cells.tif')
            export_as('CSV · measurement results','biology-check-cells.csv')
        if key=='brain':
            export_as('TIFF · all slices, full precision','biology-check-brain.tif')
        u.click('More options');u.click('Undo last edit');u.assert_text(title+' · original.tif');u.assert_text('RGB')
        u.click('Reset');u.assert_text(title+' · original.tif')
        print('PASS release demo UI:',key,'guide, recipe, result, undo, reset',flush=True)
    restart();wait_text('Leaf structure · original.tif');u.assert_text('BIOLOGY DEMO  ·  Leaf structure')
    open_demo('Fluorescent cells');u.click('Process');u.click('Extract RGB channel…');u.click('Blue');u.assert_text('8-bit')
    u.click('Reset');u.assert_text('RGB');u.shot('biology-final-'+u.SERIAL)
    print('PASS demo session recovery, manual RGB extraction and TIFF/CSV exports',flush=True)

if __name__=='__main__':main()
