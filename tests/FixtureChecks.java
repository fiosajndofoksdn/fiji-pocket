package org.fijipocket.tests;
import org.fijipocket.core.*;
import java.io.*;
public final class FixtureChecks {
    public static void main(String[] args)throws Exception {
        File dir=new File(args[0]);
        for(String name:new String[]{"raw-16.tif","tiff_lzw-16.tif","tiff_adobe_deflate-16.tif","packbits-16.tif","big-endian-16.tif","imagej-private-metadata.tif","white-zero.tif"}){
            Document d=TiffIO.read(new File(dir,name));Plane p=d.current();
            if(p.type!=Plane.U16||p.width!=32||p.height!=24)throw new AssertionError(name+" shape/type");
            for(int i=0;i<p.size();i++)if(p.value(i)!=i*79)throw new AssertionError(name+" pixel "+i+" = "+p.value(i));
            if(name.equals("white-zero.tif")&&!d.whiteIsZero)throw new AssertionError("WhiteIsZero LUT not preserved");
            System.out.println("PASS independent TIFF fixture: "+name);
        }
        Document d=TiffIO.read(new File(dir,"multipage16.tif"));
        if(d.planes.size()!=3)throw new AssertionError("multipage frame count");
        for(int z=0;z<3;z++)for(int i=0;i<768;i++)if(d.planes.get(z).value(i)!=(i*79)/(1<<z))throw new AssertionError("multipage pixel");
        System.out.println("PASS independent multipage stack pixels");
        d=TiffIO.read(new File(dir,"float32.tif"));for(int i=0;i<768;i++)if(Math.abs(d.current().value(i)-(i*79/17f-500))>.001)throw new AssertionError("float precision");
        System.out.println("PASS independent floating-point TIFF");
        d=TiffIO.read(new File(dir,"rgb.tif"));for(int y=0;y<24;y++)for(int x=0;x<32;x++)if(((int[])d.current().pixels)[y*32+x]!=(0xff000000|(x*8)<<16|(y*10)<<8|77))throw new AssertionError("RGB TIFF");
        System.out.println("PASS independent RGB TIFF");
    }
}
