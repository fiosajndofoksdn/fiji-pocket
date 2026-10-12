package org.fijipocket.tests;
import java.io.*;
import org.fijipocket.core.*;
import ij.process.AutoThresholder;
public final class CompareReference {
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);
        try(DataInputStream in=new DataInputStream(new FileInputStream(new File(dir,"gaussian.bin")))){
            int w=in.readInt(),h=in.readInt();float[] values=new float[w*h];for(int i=0;i<values.length;i++)values[i]=in.readFloat();Plane q=Operations.gaussian(new Plane(w,h,Plane.F32,values),2);double error=0;for(int i=0;i<values.length;i++)error=Math.max(error,Math.abs(q.value(i)-in.readFloat()));if(error>.001)throw new AssertionError("Gaussian difference "+error);System.out.println("PASS ImageJ Gaussian: maximum absolute difference "+error);
        }
        try(DataInputStream in=new DataInputStream(new FileInputStream(new File(dir,"thresholds.bin")))){
            int[] hist=new int[256];for(int i=0;i<256;i++)hist[i]=in.readInt();for(String method:new String[]{"Otsu","Default","Li","Triangle","Yen"}){int expected=in.readInt(),actual=new AutoThresholder().getThreshold(method,hist.clone());if(expected!=actual)throw new AssertionError(method+" "+actual+" != "+expected);System.out.println("PASS ImageJ "+method+" threshold = "+actual);}
        }
        try(DataInputStream in=new DataInputStream(new FileInputStream(new File(dir,"filters.bin")))){
            int w=in.readInt(),h=in.readInt();byte[] raw=new byte[w*h],median=new byte[w*h],edge=new byte[w*h];in.readFully(raw);in.readFully(median);in.readFully(edge);Plane p=new Plane(w,h,Plane.U8,raw),m=Operations.median(p),e=Operations.edges(p);int medianDiff=0,edgeDiff=0;for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){int i=y*w+x;if(m.value(i)!=(median[i]&255))medianDiff++;if(Math.abs(e.value(i)-(edge[i]&255))>1)edgeDiff++;}if(medianDiff>0||edgeDiff>0)throw new AssertionError("Filter differences median="+medianDiff+", edges="+edgeDiff);System.out.println("PASS ImageJ median and Sobel interior pixels (Sobel tolerance 1 intensity unit)");
        }
    }
}
