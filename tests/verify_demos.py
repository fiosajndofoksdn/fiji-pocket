#!/usr/bin/env python3
"""Independent verification of bundled sample fidelity and exported demo masks."""
from pathlib import Path
from collections import deque
import csv
import hashlib
import io
import json
import zipfile
import numpy as np
from PIL import Image
import tifffile

APP = Path(__file__).resolve().parents[1]
ASSETS = APP / 'assets/demos'
SOURCE = APP / 'vendor/demos'
OUTPUT = APP / 'tests/output/demos'

def read_zip(name):
    with zipfile.ZipFile(SOURCE / (name + '.zip')) as z:
        return tifffile.imread(io.BytesIO(z.read(name + '.tif')))

manifest = json.loads((ASSETS / 'provenance.json').read_text())
for directory, key in [(SOURCE, 'source_files'), (ASSETS, 'bundled_files')]:
    for name, entry in manifest[key].items():
        b = (directory / name).read_bytes()
        assert len(b) == entry['bytes'] and hashlib.sha256(b).hexdigest() == entry['sha256'], name
cells = tifffile.imread(ASSETS / 'cells.tif')
assert np.array_equal(cells, np.moveaxis(read_zip('FluorescentCells'), 0, -1))
brain = tifffile.imread(ASSETS / 'brain.tif')
assert np.array_equal(brain, read_zip('flybrain'))
for name in ['embryos', 'leaf']:
    assert np.array_equal(tifffile.imread(ASSETS / (name + '.tif')),
                          np.array(Image.open(SOURCE / (name + '.jpg')).convert('RGB')))
assert np.array_equal(tifffile.imread(OUTPUT / 'brain-result.tif'), brain[:, :, :, 1].max(axis=0))
print('PASS original sample pixels, all RGB channels, 57 brain slices, source checksums and independent green projection')

def components(mask, min_size, roi_height):
    # Independent flood-fill over the exported mask, excluding ROI-edge components.
    mask = mask[:roi_height].astype(bool)
    h, w = mask.shape
    seen = np.zeros(mask.shape, dtype=bool)
    result = []
    for y, x in zip(*np.nonzero(mask)):
        if seen[y, x]:
            continue
        queue = deque([(int(y), int(x))]); seen[y, x] = True
        count = 0; edge = False
        while queue:
            yy, xx = queue.popleft(); count += 1
            edge |= yy == 0 or yy == h - 1 or xx == 0 or xx == w - 1
            for dy in (-1, 0, 1):
                for dx in (-1, 0, 1):
                    ny, nx = yy + dy, xx + dx
                    if 0 <= ny < h and 0 <= nx < w and mask[ny, nx] and not seen[ny, nx]:
                        seen[ny, nx] = True; queue.append((ny, nx))
        if count >= min_size and not edge:
            result.append(count)
    return result

region_sizes = {}
for name, size, height, expected in [('cells', 100, 470, 6), ('embryos', 2000, 1200, 22)]:
    pixels = tifffile.imread(OUTPUT / (name + '-result.tif'))
    assert set(np.unique(pixels)) == {0, 255}
    if name == 'cells':
        assert not pixels[470:].any(), 'caption must not enter nuclei results'
    objects = components(pixels, size, height)
    region_sizes[name] = objects
    assert len(objects) == expected, (name, len(objects))
    print('PASS independent segmentation:', name, len(objects), 'regions')

for directory in sorted((APP / 'tests/output').glob('demos-emulator-*')):
    for path in directory.glob('biology-check-*.tif'):
        key = path.stem.replace('biology-check-', '')
        assert np.array_equal(tifffile.imread(path), tifffile.imread(OUTPUT / (key + '-result.tif')))
        if key == 'brain':
            with tifffile.TiffFile(path) as t:
                assert 'unit=um' in t.pages[0].description
                n, d = t.pages[0].tags['XResolution'].value
                assert abs(d / n - 1e6 / 373759) < 1e-6
        print('PASS Android SAF export:', directory.name, path.name)
    path = directory / 'biology-check-cells.csv'
    if path.exists():
        rows = list(csv.DictReader(path.open()))[-6:]
        assert len(rows) == 6 and all(r['Kind'] == 'DemoParticle' and int(r['Y']) < 470 for r in rows)
        assert sorted(int(r['Pixels']) for r in rows) == sorted(region_sizes['cells'])
        print('PASS Android CSV: all six region areas match independent flood fill:', directory.name)
