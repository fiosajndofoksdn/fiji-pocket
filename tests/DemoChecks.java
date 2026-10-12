package org.fijipocket.tests;
import org.fijipocket.core.*;
import java.io.*;
import java.util.*;

/** Real bundled data, source immutability and pixel-exact channel/projection checks. */
public final class DemoChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static long fingerprint(Document d){long h=17;for(Plane p:d.planes)for(int i=0;i<p.size();i++)h=h*31+(p.type==Plane.RGB?((int[])p.pixels)[i]:Float.floatToIntBits(p.value(i)));return h;}
    public static String run(File assets,File output)throws Exception {
        output.mkdirs();StringBuilder report=new StringBuilder();
        Plane colors=new Plane(3,1,Plane.RGB,new int[]{0xff123456,0xff80fe01,0xff00ff80});
        int[][] expected={{18,128,0},{52,254,255},{86,1,128}};
        for(int c=0;c<3;c++){Plane channel=Operations.channel(colors,c);for(int i=0;i<3;i++)check(channel.value(i)==expected[c][i],"RGB extraction "+c+"/"+i);}
        try{Operations.channel(colors,3);throw new AssertionError("invalid RGB component accepted");}catch(IllegalArgumentException expectedError){}
        try{Operations.channel(Plane.empty(1,1,Plane.U8),0);throw new AssertionError("non-RGB accepted");}catch(IllegalArgumentException expectedError){}
        for(BiologyDemos.Demo demo:BiologyDemos.ALL){
            Document original=TiffIO.read(new File(assets,demo.id+".tif"));original.demoId=demo.id;
            check(original.current().type==Plane.RGB,"sample RGB "+demo.id);
            check(original.snapshot().demoId.equals(demo.id),"demo context survives undo snapshot");
            long before=fingerprint(original);BiologyDemos.Outcome outcome=BiologyDemos.analyze(original);Document result=outcome.document;
            check(before==fingerprint(original),"original pixels changed: "+demo.id);
            check(result.planes.size()==1&&result.current().type==Plane.U8,"8-bit result "+demo.id);
            check(result.demoId.equals(demo.id),"result guide identity");
            check(result.pixelWidth==original.pixelWidth&&result.pixelHeight==original.pixelHeight,"XY calibration preserved");
            check(result.current().width==original.current().width&&result.current().height==original.current().height,"full resolution preserved");
            if(demo.id.equals("brain")){
                check(original.planes.size()==57,"full brain stack");
                check(Math.abs(original.pixelWidth-1e6/373759)<1e-8&&original.unit.equals("um"),"brain scale");
                for(int i=0;i<result.current().size();i++){
                    int max=0;for(Plane p:original.planes)max=Math.max(max,(((int[])p.pixels)[i]>>8)&255);
                    check(result.current().value(i)==max,"max green projection pixel "+i);
                }
            }else if(demo.id.equals("leaf")){
                check(original.current().width==507&&original.current().height==446,"leaf full resolution");
                check(result.current().range()[1]>0,"leaf edges nonempty");
            }else{
                check(Operations.isBinary(result.current()),"segmentation is binary");
                check(!outcome.particles.isEmpty(),"segmentation detects regions");
                check(outcome.particles.size()==(demo.id.equals("cells")?6:22),"independently verified demo region count");
                if(demo.id.equals("cells"))for(int i=470*512;i<result.current().size();i++)check(result.current().value(i)==0,"embedded caption excluded");
                for(Operations.Particle p:outcome.particles){
                    check(p.left>0&&p.top>0&&p.right<result.current().width-1&&p.bottom<result.current().height-1,"edge exclusion");
                    check(p.count>=(demo.id.equals("cells")?100:2000),"minimum area");
                }
            }
            try(OutputStream out=new FileOutputStream(new File(output,demo.id+"-result.tif"))){TiffIO.write(result,out,true);}
            Document roundtrip=TiffIO.read(new File(output,demo.id+"-result.tif"));
            check(fingerprint(roundtrip)==fingerprint(result),"demo export roundtrip "+demo.id);
            report.append("PASS ").append(demo.id).append(": ").append(outcome.summary).append('\n');
        }
        return report.toString();
    }
    public static void main(String[] args)throws Exception{System.out.print(run(new File(args[0]),new File(args[1])));}
}
