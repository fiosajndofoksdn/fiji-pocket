#!/usr/bin/env python3
"""Independently decode the files exported through Android's document picker."""
from pathlib import Path
from PIL import Image
import numpy as np
import tifffile
import csv,sys
base=Path(__file__).resolve().parent/'output'
export=base/(sys.argv[1] if len(sys.argv)>1 else 'exports-emulator-5556')
np.testing.assert_array_equal(tifffile.imread(export/'fiji-lzw16-processed.tif'),np.asarray(Image.open(base/'fixtures/tiff_lzw-16.tif')))
np.testing.assert_array_equal(np.asarray(Image.open(export/'fiji-color-view.png').convert('RGB')),np.asarray(Image.open(base/'fixtures/color.png')))
np.testing.assert_array_equal(tifffile.imread(export/'fiji-stack-processed.tif'),tifffile.imread(base/'sample-16bit-stack.tif'))
with tifffile.TiffFile(export/'fiji-stack-processed.tif') as tf:
    x=tf.pages[0].tags['XResolution'].value
    y=tf.pages[0].tags['YResolution'].value
    assert x[0]/x[1]==2 and y[0]/y[1]==2, (x,y)
    assert tf.imagej_metadata['unit']=='um',tf.imagej_metadata
with (export/'fiji-stack-results.csv').open() as f:rows=list(csv.DictReader(f))
last=rows[-1]
assert last['Kind']=='ROI' and last['Image']=='fiji-stack.tif',last
assert int(last['Pixels'])==393216 and float(last['Area'])==98304,last
print('PASS exported PNG exact RGB pixels; TIFF exact 16-bit pixels, 3-slice stack and calibration; CSV pixel count and calibrated area')
