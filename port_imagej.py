#!/usr/bin/env python3
"""Reproduce the small Android-compatible ImageJ source subset from pinned originals."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent

def method(text, signature):
    start = text.index(signature)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return start, end

def remove(text, signature):
    a,b = method(text, signature)
    return text[:a]+text[b:]

for source in (ROOT/'vendor/imagej').rglob('*.java'):
    name = str(source.relative_to(ROOT/'vendor/imagej'))
    text = source.read_text()
    if name.endswith('GaussianBlur.java'):
        a,b = method(text, 'final static private void convolveLine(')
        c,d = method(text, 'public float[][] makeGaussianKernel(')
        text = '''package org.fijipocket.core;
// Kernels from ImageJ GaussianBlur, Michael Schmid / Stephan Saalfeld.
// Android adapter: raw float planes, no desktop plugin or AWT dependency.
public final class GaussianKernel {
    public static void blur(float[] pixels, int width, int height, double sigma) {
        if (!Double.isFinite(sigma) || sigma <= 0 || sigma > 20)
            throw new IllegalArgumentException("Sigma must be between 0 and 20 pixels");
        float[] line = new float[width];
        float[][] kernel = makeGaussianKernel(sigma, 0.0002, Math.max(width,height));
        for (int y=0;y<height;y++) {
            System.arraycopy(pixels,y*width,line,0,width);
            convolveLine(line,pixels,kernel,0,width,0,width,y*width,1);
        }
        line = new float[height];
        for (int x=0;x<width;x++) {
            for (int y=0;y<height;y++) line[y]=pixels[y*width+x];
            convolveLine(line,pixels,kernel,0,height,0,height,x,width);
        }
    }
''' + text[a:b] + '\n' + text[c:d].replace('public float[][]','public static float[][]') + '\n}\n'
        name='org/fijipocket/core/GaussianKernel.java'
    elif name.endswith('FileInfo.java'):
        text=text.replace('import ij.VirtualStack;','').replace('public VirtualStack virtualStack;','// Virtual stacks are not part of this bounded in-memory port.')
    elif name.endswith('TiffEncoder.java'):
        text=text.replace('ij.Prefs.intelByteOrder','true')
    elif name.endswith('TiffDecoder.java'):
        text=re.sub(r'case META_DATA_BYTE_COUNTS:\s*long saveLoc.*?break;', 'case META_DATA_BYTE_COUNTS: break; // Desktop private metadata omitted.', text, flags=re.S)
        text=text.replace('getMetaData(value, fi);','// Desktop overlays and private metadata are omitted.')
    elif name.endswith('ImageWriter.java'):
        text=text.replace('import ij.process.ImageProcessor;','')
        for kind in ['8Bit','16Bit','Float','RGB']:
            text=remove(text, 'void write'+kind+'VirtualStack(')
            text=re.sub(r'if \(fi.nImages>1 && fi.virtualStack!=null\)\s*write'+kind+r'VirtualStack\(out, fi.virtualStack\);\s*else ', '', text)
        text=text.replace(' && fi.virtualStack==null','')
    elif name.endswith('ImageReader.java'):
        text=text.replace('import java.awt.image.BufferedImage;','').replace('import javax.imageio.ImageIO;','')
        a,b=method(text,'int[] readJPEG(')
        text=text[:a]+'''int[] readJPEG(InputStream in) throws IOException {
            throw new IOException("JPEG-compressed TIFF is unsupported; export as lossless TIFF or PNG first.");
        }'''+text[b:]
        text=remove(text,'public Object readPixels(String url)')
        text=text.replace('imageCount++;\n\t\t\treturn pixels;', 'imageCount++;\n\t\t\tif (eofErrorCount>0) return null;\n\t\t\treturn pixels;')
        a,b=method(text,'public byte[] zipUncompress(')
        text=text[:a]+'''public byte[] zipUncompress(byte[] input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            Inflater inflater = new Inflater(); inflater.setInput(input);
            try {
                while (!inflater.finished()) {
                    int n=inflater.inflate(buffer);
                    if (n==0 && !inflater.finished()) throw new IllegalArgumentException("Truncated Deflate strip");
                    if (output.size()+(long)n>32L*1024*1024) throw new IllegalArgumentException("TIFF strip exceeds memory limit");
                    output.write(buffer,0,n);
                }
                return output.toByteArray();
            } catch (DataFormatException e) { throw new IllegalArgumentException("Invalid Deflate data",e); }
            finally { inflater.end(); }
        }'''+text[b:]
        text=text.replace('public void add(byte x) {','public void add(byte x) {\n        if (size>=32*1024*1024) throw new IllegalArgumentException("TIFF strip exceeds memory limit");')
        text=text.replace('public void add(byte[] array) {','public void add(byte[] array) {\n        if (size+(long)array.length>32L*1024*1024) throw new IllegalArgumentException("TIFF strip exceeds memory limit");')
    target=ROOT/'src'/name
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_text('// Android port: see THIRD_PARTY_NOTICES.txt and port_imagej.py.\n'+text)
