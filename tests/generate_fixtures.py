#!/usr/bin/env python3
"""Independent TIFF fixtures: pip install numpy pillow tifffile (test-only)."""
from PIL import Image
import numpy as np
import tifffile
from pathlib import Path
p=Path(__file__).resolve().parent/'output/fixtures'
p.mkdir(parents=True,exist_ok=True)
a=np.arange(32*24,dtype=np.uint16).reshape(24,32)*79
for compression in ['raw','tiff_lzw','tiff_adobe_deflate','packbits']:
    Image.fromarray(a).save(p/(compression+'-16.tif'),compression=compression)
tifffile.imwrite(p/'big-endian-16.tif',a,byteorder='>')
tifffile.imwrite(p/'float32.tif',a.astype('float32')/17-500)
tifffile.imwrite(p/'multipage16.tif',np.stack([a,a//2,a//4]),photometric='minisblack')
tifffile.imwrite(p/'imagej-private-metadata.tif',a,imagej=True,metadata={'Info':'Fixture test'})
tifffile.imwrite(p/'white-zero.tif',a,photometric='miniswhite')
rgb=np.zeros((24,32,3),dtype=np.uint8)
rgb[:,:,0]=np.arange(32)*8;rgb[:,:,1]=np.arange(24)[:,None]*10;rgb[:,:,2]=77
Image.fromarray(rgb).save(p/'color.png')
Image.fromarray(rgb).save(p/'color.jpg',quality=95)
tifffile.imwrite(p/'rgb.tif',rgb,photometric='rgb')
print('Created independent TIFF and PNG/JPEG fixtures')
