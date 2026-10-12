#!/usr/bin/env python3
"""Check actual gallery control bounds while rotating an already-open dialog."""
import re
import struct
import time
import ui_smoke as u

u.click('Demos')
u.assert_text('Biology demos')
for rotation, name in [('0','portrait'),('1','landscape')]:
    u.adb('shell','settings','put','system','accelerometer_rotation','0')
    u.adb('shell','settings','put','system','user_rotation',rotation)
    time.sleep(1)
    nodes=u.nodes()
    screenshot=u.adb('exec-out','screencap','-p')
    width,height=struct.unpack('>II',screenshot[16:24])
    labels=['Close','Open Fluorescent cells','Open Fruit-fly brain','Open Embryos','Open Leaf structure']
    bounds=[]
    for label in labels:
        n=next(n for n in nodes if n.get('text')==label)
        b=list(map(int,re.findall(r'\d+',n.get('bounds'))))
        assert 0<=b[0]<b[2]<=width and 0<=b[1]<b[3]<=height,(label,b,width,height)
        assert b[2]-b[0]>70 and b[3]-b[1]>50,(label,b)
        bounds.append(b)
    # Catch partially clipped right-hand cards, even when accessibility still finds them.
    assert abs((bounds[1][2]-bounds[1][0])-(bounds[2][2]-bounds[2][0]))<=4,bounds
    assert abs((bounds[3][2]-bounds[3][0])-(bounds[4][2]-bounds[4][0]))<=4,bounds
    (u.OUT/('biology-gallery-'+name+'-fixed.png')).write_bytes(screenshot)
    print('PASS open-gallery resize:',name,'all cards and Close fully visible',flush=True)
u.click('Close');u.assert_text('Demos');u.click('Demos')
