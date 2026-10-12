#!/usr/bin/env python3
"""Release UI checks for themes, state preservation, and the bundled demo gallery.

Run with build/test-venv/bin/python (Pillow) on a disposable tablet emulator.
Leaves the app in light mode with the original fluorescent-cell sample open.
"""
import io
import re
import time
from PIL import Image, ImageChops
import ui_smoke as u
from ui_demos import restart, open_demo, wait_text


def bounds(node):
    return list(map(int, re.findall(r'\d+', node.get('bounds'))))


def screenshot(name):
    data = u.adb('exec-out', 'screencap', '-p')
    (u.OUT / (name + '-' + u.SERIAL + '.png')).write_bytes(data)
    return Image.open(io.BytesIO(data)).convert('RGB')


def assert_theme(light):
    nodes = u.nodes()
    action = 'Switch to dark theme' if light else 'Switch to light theme'
    assert any(n.get('content-desc') == action for n in nodes), action
    assert all('LOCAL IMAGE ANALYSIS' not in n.get('text', '').upper() for n in nodes)
    image = screenshot('theme-light' if light else 'theme-dark')
    # The outer app margin changes; the image's source pixels are tested separately.
    expected = (243, 246, 247) if light else (16, 24, 30)
    assert image.getpixel((10, image.height // 2)) == expected
    return image


def gallery(light):
    u.click('Demos')
    nodes = u.nodes()
    for title in ['Fluorescent cells', 'Fruit-fly brain', 'Embryos', 'Leaf structure']:
        assert any(n.get('text') == 'Open ' + title for n in nodes), title
    screenshot('gallery-light' if light else 'gallery-dark')
    u.click('Close')


def main():
    restart()
    if any(n.get('content-desc') == 'Switch to dark theme' for n in u.nodes()):
        u.click('Switch to dark theme')
    assert_theme(False)
    gallery(False)
    open_demo('Fluorescent cells')
    u.click('Process'); u.click('Convert to grayscale'); wait_text('8-bit')
    # Move the viewport, then draw a selection. Theme changes must retain both.
    canvas = next(n for n in u.nodes() if n.get('content-desc', '').startswith('Image canvas.'))
    x0, y0, x1, y1 = bounds(canvas)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    u.adb('shell', 'input', 'swipe', str(cx), str(cy), str(cx+65), str(cy+30), '300')
    u.click('Analyze'); u.click('Select ROI')
    u.adb('shell', 'input', 'swipe', str(cx-80), str(cy-80), str(cx+80), str(cy+80), '350')
    roi = next(n.get('text') for n in u.nodes() if n.get('text', '').startswith('ROI  '))
    dark = screenshot('image-state-dark')
    u.click('Switch to light theme')
    light = assert_theme(True)
    u.assert_text(roi); u.assert_text('8-bit'); u.assert_text('Fluorescent cells · original.tif')
    # A region inside the displayed image includes the ROI overlay. Exact equality
    # detects changes to pixels, contrast, image position, and the ROI on a theme switch.
    crop = (cx-150, cy-150, cx+150, cy+150)
    assert ImageChops.difference(dark.crop(crop), light.crop(crop)).getbbox() is None
    u.click('More options'); u.click('Undo last edit'); u.assert_text('RGB')
    print('PASS theme switch preserves displayed image, viewport, ROI, and undo', flush=True)
    gallery(True)
    u.click('Image'); u.click('Histogram'); u.assert_text('Histogram · current slice')
    screenshot('histogram-light'); u.click('Done')
    u.click('Process'); u.click('Gaussian blur')
    screenshot('dialog-light'); u.click('Cancel')
    restart(); wait_text('Fluorescent cells · original.tif'); assert_theme(True)
    u.click('Switch to dark theme'); assert_theme(False)
    u.click('Image'); u.click('Histogram'); screenshot('histogram-dark'); u.click('Done')
    restart(); wait_text('Fluorescent cells · original.tif'); assert_theme(False)
    u.click('Switch to light theme')
    for title in ['Fruit-fly brain', 'Embryos', 'Leaf structure', 'Fluorescent cells']:
        open_demo(title); u.assert_text('RGB')
        if title == 'Fruit-fly brain':
            u.assert_text('57 slices')
    print('PASS both theme preferences survive process restart; all four bundled demos load', flush=True)
    # Rotation must keep the chosen appearance and all header buttons reachable.
    for rotation in ('0', '1'):
        u.adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')
        u.adb('shell', 'settings', 'put', 'system', 'user_rotation', rotation)
        time.sleep(1)
        image = assert_theme(True)
        for label in ['Demos', 'Open', 'Export', 'Switch to dark theme']:
            node = next(n for n in u.nodes() if n.get('content-desc') == label)
            a, b, c, d = bounds(node)
            assert 0 <= a < c <= image.width and 0 <= b < d <= image.height
    print('PASS light theme and header controls survive portrait/landscape rotation', flush=True)


if __name__ == '__main__':
    main()
