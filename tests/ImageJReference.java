package org.fijipocket.tests;
import java.io.*;
import java.util.*;
import ij.process.*;
import ij.plugin.filter.GaussianBlur;

/** Run in an isolated JVM containing pristine upstream ImageJ, not the port. */
public final class ImageJReference {
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();Random random=new Random(321);
        int w=37,h=23;float[] floats=new float[w*h];for(int i=0;i<floats.length;i++)floats[i]=(float)(random.nextDouble()*1000-100);
        FloatProcessor fp=new FloatProcessor(w,h,floats.clone());new GaussianBlur().blurGaussian(fp,2,2,.0002);
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(dir,"gaussian.bin")))){out.writeInt(w);out.writeInt(h);for(float v:floats)out.writeFloat(v);for(float v:(float[])fp.getPixels())out.writeFloat(v);}
        int[] hist=new int[256];for(int i=0;i<hist.length;i++)hist[i]=random.nextInt(400);
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(dir,"thresholds.bin")))){for(int v:hist)out.writeInt(v);for(String m:new String[]{"Otsu","Default","Li","Triangle","Yen"})out.writeInt(new AutoThresholder().getThreshold(m,hist.clone()));}
        byte[] raw=new byte[37*23];random.nextBytes(raw);ByteProcessor original=new ByteProcessor(37,23,raw,null);ByteProcessor median=(ByteProcessor)original.duplicate();median.medianFilter();ByteProcessor edges=(ByteProcessor)original.duplicate();edges.findEdges();
        try(DataOutputStream out=new DataOutputStream(new FileOutputStream(new File(dir,"filters.bin")))){out.writeInt(37);out.writeInt(23);out.write(raw);out.write((byte[])median.getPixels());out.write((byte[])edges.getPixels());}
        System.out.println("Created golden outputs using pristine desktop ImageJ");
    }
}
