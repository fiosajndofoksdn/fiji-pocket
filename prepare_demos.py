#!/usr/bin/env python3
"""Prepare bundled ImageJ samples. Requires tests/requirements.txt; never runs on Android."""
from pathlib import Path
import hashlib
import io
import json
import zipfile
import numpy as np
from PIL import Image
import tifffile

APP = Path(__file__).resolve().parent
SOURCE = APP / 'vendor/demos'
OUT = APP / 'assets/demos'
OUT.mkdir(parents=True, exist_ok=True)

def zipped_tiff(name):
    with zipfile.ZipFile(SOURCE / (name + '.zip')) as archive:
        return tifffile.TiffFile(io.BytesIO(archive.read(name + '.tif')))

def write(name, pixels, rgb=True, pixel_size=None):
    options = dict(photometric='rgb' if rgb else 'minisblack', compression='deflate',
                   metadata=None, description='ImageJ=1.54\nunit=' + ('um' if pixel_size else 'px') + '\n')
    if pixel_size:
        options['resolution'] = (1 / pixel_size[0], 1 / pixel_size[1])
    tifffile.imwrite(OUT / (name + '.tif'), pixels, **options)
    # Thumbnails are display previews only. Full data above is never resized.
    preview = pixels.max(axis=0) if pixels.ndim == 4 else pixels
    im = Image.fromarray(preview)
    im.thumbnail((360, 220), Image.Resampling.LANCZOS)
    im.save(OUT / (name + '-thumb.jpg'), quality=85)

with zipped_tiff('FluorescentCells') as t:
    channels = t.asarray()
    assert channels.shape == (3, 512, 512)
    write('cells', np.moveaxis(channels, 0, -1))
with zipped_tiff('flybrain') as t:
    pixels = t.asarray()
    assert pixels.shape == (57, 256, 256, 3)
    tags = t.pages[0].tags
    x, y = tags['XResolution'].value, tags['YResolution'].value
    write('brain', pixels, pixel_size=(x[1] / x[0], y[1] / y[0]))
for name, source in [('embryos', 'embryos.jpg'), ('leaf', 'leaf.jpg')]:
    # Decode JPEG once during preparation so Android and host tests use identical pixels.
    write(name, np.array(Image.open(SOURCE / source).convert('RGB')))

manifest = {'source_base': 'https://imagej.net/ij/images/',
            'preparation': 'No resizing of analysis images. Fluorescent channels packed into RGB without changing their 8-bit values; flybrain full 57-slice RGB stack retained; JPEG samples decoded once to TIFF. TIFF data losslessly Deflate-compressed. Thumbnails resized separately.',
            'source_files': {}, 'bundled_files': {}}
for directory, key in [(SOURCE, 'source_files'), (OUT, 'bundled_files')]:
    for p in sorted(directory.iterdir()):
        if p.suffix in ('.tif', '.jpg', '.zip', '.txt') and p.name != 'CREDITS.txt':
            manifest[key][p.name] = {'bytes': p.stat().st_size,
                'sha256': hashlib.sha256(p.read_bytes()).hexdigest()}
(OUT / 'provenance.json').write_text(json.dumps(manifest, indent=2) + '\n')
print('Prepared biology demos:', sum(p.stat().st_size for p in OUT.iterdir()), 'bytes')
