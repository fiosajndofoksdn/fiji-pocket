# Fiji Pocket for Android

An independent, native Android port of a selected Fiji / ImageJ processing core.
The app works offline and is delivered as one signed, non-debuggable APK.
It is not the complete Fiji desktop distribution and does not run desktop plugins.

## Install and use

Download the signed [Fiji Pocket APK](https://github.com/fiosajndofoksdn/fiji-pocket/releases/latest/download/fiji-pocket.apk)
or browse the [releases](https://github.com/fiosajndofoksdn/fiji-pocket/releases)
for source archives and SHA-256 checksums. All four biology demos are included.

Install `dist/fiji-pocket.apk` on Android 8.0 or newer. Android may ask you to
allow APK installation from the browser or file manager you use. No account,
server, Orca connection, desktop runtime, storage permission, or internet
connection is required after downloading the APK.

The initial image is a **synthetic** three-slice, 16-bit stack containing 18
separate nuclei per slice, calibrated to 0.5 µm/pixel. It is a deterministic
test image, not microscopy data. Use **Open** to choose your own image.

Version **0.3.0** adds a **Light / Dark** button in the header. It shows the theme
you can switch to and remembers your choice after closing the app. Switching
preserves the current image, display range, viewport, ROI, results, and undo.
The workspace, gallery, controls, and dialogs follow the selected theme.

The **Demos** button opens four real biology samples from
[ImageJ's official Open Samples collection](https://imagej.net/ij/images/).
All images and thumbnail previews are bundled in the single APK (about 4 MB).
Open a card, read **Guide**, and tap **Run demo**:

| Demo | Runnable example |
| --- | --- |
| Fluorescent cells | Extract blue DAPI, Gaussian σ=1, Otsu, and label regions ≥100 px; excludes the embedded caption and ROI-edge objects. |
| Fruit-fly brain | Browse all 57 RGB confocal slices, then create a maximum projection of the green nc82 channel. |
| Embryos | Grayscale, Gaussian σ=2, dark Otsu mask, and label regions ≥2,000 px with edge exclusion. |
| Leaf structure | Grayscale, Gaussian σ=1, and Sobel edges to explore leaf boundaries and veins. |

**Reset** restores the bundled original; **Undo** returns to the image before
the last demo run or reset. Each run begins from the bundled original and adds
segmentation rows to the existing measurement table. **Export** saves the
current image or measurement CSV. The demo identity persists with the private
working image. Particle overlays and the temporary ROI do not persist after
process termination; run the demo again to recreate them.

These recipes are teaching examples, not verified cell/embryo counts. Touching
objects can merge, and min-area/edge rules intentionally exclude some objects.
For the bundled images, the fixed recipes produce 6 fluorescent-cell regions
and 22 embryo regions. No physical scale is inferred from a drawn scale bar;
only the brain sample supplies retained numerical XY calibration. The brain
recipe makes a 2D projection; desktop 3D Viewer is not included.

Credits, source hashes and preparation details are in
[`assets/demos/CREDITS.txt`](assets/demos/CREDITS.txt) and
[`assets/demos/provenance.json`](assets/demos/provenance.json). Analysis images
are never resized. `vendor/demos/` holds the original downloaded files; run
`prepare_demos.py` with the test virtual environment to regenerate assets.

- **Process:** Gaussian smoothing, median 3×3, Sobel edges, invert, RGB luminance
  conversion, RGB channel extraction, ImageJ automatic/manual threshold, binary erosion and dilation.
- **Analyze:** draw a rectangular ROI, measure the current slice, set pixel
  calibration, and count 8-connected white particles with minimum-size and
  edge-exclusion filters. Clear the ROI to analyze the whole image.
- **Image:** display-only contrast and color maps, histogram, rotate, flip,
  crop to ROI, maximum or mean stack projection.
- **Export:** full-precision TIFF stack or slice, the displayed slice as an
  8-bit PNG, or measurement rows as CSV. A source file is never overwritten
  automatically. Export does not burn ROI or particle labels into the image.
- Pinch to zoom, drag to pan. In ROI mode, drag to select a rectangle.
- Processing edits apply to **all slices**. Measurements and particle counts
  apply to the **current slice**. There is one level of undo/redo.

The current pixel stack and calibration are saved in the app's private storage,
along with measurement CSV rows. Rotation retains the working state. Display
contrast, color map, ROI, particle overlays and undo history are temporary;
they are not restored after the process closes. Loading a new image retains
the measurement table; **View results → Clear** resets it. Uninstalling removes
private working data; exported files remain in the location you chose.
An export is staged in private storage before Android's document picker opens,
so it can finish after Android recreates the app process while that picker is open.

## Image formats and scientific data

The ImageJ TIFF reader/writer is used directly, with an Android adaptation layer.
Supported TIFF pixels are unsigned 8-bit, unsigned 16-bit, float32, and RGB8;
strip-based uncompressed, LZW, Deflate, and PackBits files are supported where
handled by the original codec. Standard multipage and uncompressed contiguous
ImageJ stacks are supported. Both byte orders can be read; export is little
endian. Pixel width, height and physical units are retained in TIFF.

PNG, JPEG and WebP use Android's bitmap decoder and are imported as RGB8.
For numerical precision above 8 bits, use TIFF. PNG export encodes the selected
display range and color map at the full slice dimensions. TIFF export preserves
the numerical pixel values independently of display settings.

8/16-bit automatic thresholds use full integer histograms. Float thresholds use
256 bins spanning finite values; the histogram dialog also displays 256 bins.
Gaussian kernels come from ImageJ and use sigma in pixels; this port uses direct
separable convolution rather than ImageJ's large-sigma downsampling optimization.
Other filters use replicated edge pixels. Integer filter output is rounded and
clipped to its original integer range. Float data remain float. RGB conversion
uses 0.299 R + 0.587 G + 0.114 B.

ROI statistics include finite-pixel count, calibrated rectangular area, mean,
sample standard deviation, minimum, maximum and uncalibrated integrated pixel
intensity. NaN/infinite values are excluded from intensity statistics. Particle
areas count white pixels; centroids use pixel centers, scaled from the top-left
image origin. CSV stores the image name, slice and ROI geometry for each row.
Particle segmentation is a basic connected-component analysis, not a trained
cell detector or Fiji's full Analyze Particles feature set.

Limits: **8,388,608 pixels per plane, 32 MiB decoded stack, 512 slices, 128 MiB
input file, 10,000 particles per analysis**. Oversized and unsupported inputs
produce an error; the app does not silently downsample scientific input.

Not included: desktop AWT/Swing UI, updater, macros, general plugin loading,
Bio-Formats, OME channel/time interpretation, tiled TIFF, BigTIFF, JPEG-compressed
TIFF, floating-point TIFF predictor, palette TIFF, signed integer TIFF, desktop
ROI/overlay files, embedded private TIFF metadata, or 3D visualization. TIFF
pages are presented as an ordered slice stack. EXIF camera orientation is not
applied to JPEG imports in this version. Export a correctly oriented image first.

## Build on this Mac

```sh
python3 fiji-android/build.py
python3 fiji-android/build.py --install tablet
```

The build uses JDK 17, Android platform 36, and Build Tools 36.0.0 already installed
under the parent workspace's `.mobile/` directory. There are no Gradle, Kotlin,
Maven, WebView or native-library dependencies. The app compiles Java to DEX and
packages native Android views and the selected ImageJ classes.
Every build checks the four demo TIFFs and thumbnails against their provenance
hashes, requires their credits, and verifies the complete files inside the signed
APK. Missing or altered demo assets stop the build. No download step is needed
when opening demos.

On another SDK installation, set `JAVA_HOME` and `ANDROID_HOME` and run
`python3 build.py` from this directory. Install Android platform 36 and Build
Tools 36.0.0 first. `--install tablet` additionally uses the workspace's `android`
launcher; alternatively use `adb install -r dist/fiji-pocket.apk`.

The persistent local signing key is `../.mobile/fiji-pocket.keystore` (alias
`fijipocket`). Keep that file to produce compatible updates. It is excluded from
the source archive. This local distribution key has a development password;
use your own protected release key before wider distribution. The APK itself
is non-debuggable and uses APK v2/v3 signatures.

## Upstream provenance and licensing

Fiji overview reviewed at commit `ddc9a45d6042ed664d33ad95847096318c365aa4`:
https://github.com/fiji/fiji

ImageJ sources pinned at `4c4975d6df7cf89334e7bc7bb56c48f7b204f244`:
https://github.com/imagej/ImageJ

The Android port directly reuses ImageJ's automatic threshold algorithms and
TIFF codecs, plus its Gaussian kernel/convolution routines. The new native UI,
image model, remaining operations and analysis code implement the compact
Android workflow. The pristine reused files are in `vendor/imagej/`. Run
`python3 port_imagej.py` to regenerate the adapted classes from those originals.
Do not manually edit generated `src/ij/` classes or `GaussianKernel.java`.

See `THIRD_PARTY_NOTICES.txt`, `vendor/imagej/LICENSE.txt`, and `LICENSE`.
The new wrapper is GPL-3.0-or-later; upstream ImageJ's public-domain notice and
individual contributor comments are retained. The APK includes license notices;
`dist/fiji-pocket-source.zip` supplies corresponding source and build/test scripts.

## Reproduce tests

```sh
python3 tests/run_host.py
python3 tests/build_device.py
adb install -r build/device-tests/device-tests.apk
adb shell am instrument -w org.fijipocket.tests/org.fijipocket.tests.DeviceChecks
ANDROID_SERIAL=emulator-5556 python3 tests/ui_smoke.py
ANDROID_SERIAL=emulator-5556 python3 tests/ui_demos.py
ANDROID_SERIAL=emulator-5556 build/test-venv/bin/python tests/ui_theme.py
```

`CoreChecks` runs the same numeric, geometry, segmentation, precision, TIFF and
invalid-input tests on the Mac JVM and Android. The test APK is separate and
is not needed to use the distributed app.

For independent compressed TIFF fixtures, install the test-only packages
from `tests/requirements.txt` into a virtual environment, then run
`tests/generate_fixtures.py`. `tests/run_host.py` automatically checks these
fixtures if present. They are not app dependencies.
`tests/verify_demos.py` independently compares every bundled pixel against the
upstream images, checks the green maximum projection with NumPy, and counts
connected regions in exported demo masks using an independent flood fill.
`DemoChecks` runs the real sample recipes on both the host JVM and Android.
`tests/ui_theme.py` checks both themes, persistence after process restart,
image/viewport/ROI preservation using screenshot pixels, undo, demo loading,
and header controls after rotation. It uses Pillow from the test environment.

`tests/ui_files.py` tests Android's actual document picker and recreates the
app process while a save dialog is open. This optional process-death test
requires a disposable AOSP emulator with `adb root`; it is not intended for
personal physical devices. Push `tests/output/fixtures/color.png` as
`/sdcard/Download/fiji-color.png`, `tiff_lzw-16.tif` as `fiji-lzw16.tif`, and
`tests/output/sample-16bit-stack.tif` as `fiji-stack.tif` first. Run
`tests/verify_exports.py` to compare the resulting files using independent
Pillow/tifffile decoders. Restore `adb unroot` after that test.

For reference comparisons, compile the pinned pristine ImageJ repository's
`ij/**/*.java` into `build/reference` using JDK 17 `javac --release 8`.
`tests/run_host.py` will generate golden outputs in a separate JVM containing
only original ImageJ, then compare the port's results in another JVM. This avoids
accidentally comparing the port against itself. See `tests/ImageJReference.java`.

The build targets API 36 and uses APIs available from API 26 onward. Runtime
verification covers the local Android 14 and Android 16 ARM64 tablet emulators;
minimum-version and physical-device testing are separate from those checks.
