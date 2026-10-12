package org.fijipocket.tests;
import org.fijipocket.core.*;
import java.io.*;
import java.util.*;

/** Runs unchanged on the Mac JVM and the tablet's Android runtime. */
public final class CoreChecks {
    private static int assertions;
    private static void check(boolean value,String message){assertions++;if(!value)throw new AssertionError(message);}
    private static void near(double actual,double expected,double tolerance,String message){check(Math.abs(actual-expected)<=tolerance,message+": "+actual+" != "+expected);}
    private static void equal(Plane a,Plane b){check(a.width==b.width&&a.height==b.height&&a.type==b.type,"Plane dimensions/type");for(int i=0;i<a.size();i++){if(a.type==Plane.RGB)check(((int[])a.pixels)[i]==((int[])b.pixels)[i],"RGB pixel "+i);else if(Float.isNaN(a.value(i)))check(Float.isNaN(b.value(i)),"NaN preserved");else near(b.value(i),a.value(i),0,"Pixel "+i);}}
    private static Document doc(Plane p){Document d=new Document();d.planes.add(p);return d;}
    private static Plane ramp(int w,int h,int type){Plane p=Plane.empty(w,h,type);for(int i=0;i<p.size();i++)p.set(i,i);return p;}
    private interface Action{void run()throws Exception;}
    private static void rejects(Action a,String label)throws Exception{boolean rejected=false;try{a.run();}catch(IOException|IllegalArgumentException e){rejected=true;}check(rejected,label);}
    public static String run(File directory)throws Exception {
        assertions=0;directory.mkdirs();long start=System.nanoTime();
        Plane ramp=ramp(4,3,Plane.U16);
        Operations.Stats stats=Operations.measure(ramp,null,.5,2);
        near(stats.mean,5.5,1e-12,"Mean");near(stats.area,12,0,"Calibrated area");near(stats.std,Math.sqrt(13),1e-12,"Sample standard deviation");near(stats.sum,66,0,"Integrated density");
        stats=Operations.measure(ramp,new Operations.Region(1,1,2,2),.5,.5);
        near(stats.mean,7.5,0,"ROI mean");near(stats.area,1,0,"ROI area");near(stats.count,4,0,"ROI pixel count");
        Plane cropped=Operations.crop(ramp,new Operations.Region(1,1,2,2));near(cropped.value(0),5,0,"Crop origin");near(cropped.value(3),10,0,"Crop last pixel");
        Plane rot=ramp;for(int i=0;i<4;i++)rot=Operations.rotate(rot);equal(ramp,rot);equal(ramp,Operations.flip(Operations.flip(ramp)));equal(ramp,Operations.invert(Operations.invert(ramp)));
        Plane color=new Plane(2,1,Plane.RGB,new int[]{0xffff0000,0xff00ff00});Plane gray=Operations.grayscale(color);near(gray.value(0),76,0,"Red luminance");near(gray.value(1),150,0,"Green luminance");equal(color,Operations.invert(Operations.invert(color)));
        Plane impulse=Plane.empty(5,5,Plane.U8);impulse.set(12,255);Plane median=Operations.median(impulse);near(median.value(12),0,0,"Median removes isolated impulse");
        Plane flat=Plane.empty(17,11,Plane.F32);Arrays.fill((float[])flat.pixels,1024.5f);Plane blurred=Operations.gaussian(flat,2);for(int i=0;i<flat.size();i++)near(blurred.value(i),1024.5,.001,"Gaussian preserves flat field and edges");
        Plane edge=Operations.edges(flat);for(int i=0;i<edge.size();i++)near(edge.value(i),0,1e-6,"Sobel flat field");
        Plane binary=Plane.empty(8,7,Plane.U8);for(int y=1;y<3;y++)for(int x=1;x<3;x++)binary.set(y*8+x,255);binary.set(5*8+6,255);binary.set(0,255);
        // The corner pixel touches the 2x2 object diagonally: 8-connectivity merges them.
        List<Operations.Particle> particles=Operations.particles(binary,null,1,false);check(particles.size()==2,"Eight-connected particles");check(particles.get(0).count==5,"Particle area");
        check(Operations.particles(binary,null,2,false).size()==1,"Minimum area filter");check(Operations.particles(binary,null,1,true).size()==1,"Exclude edge objects");
        check(Operations.particles(binary,new Operations.Region(1,1,2,2),1,false).get(0).count==4,"Particles honor ROI");
        Plane solid=Plane.empty(5,5,Plane.U8);for(int y=1;y<4;y++)for(int x=1;x<4;x++)solid.set(y*5+x,255);
        Plane eroded=Operations.morph(solid,false);near(Operations.measure(eroded,null,1,1).sum,255,0,"Binary erosion");Plane dilated=Operations.morph(eroded,true);equal(solid,dilated);
        rejects(()->Operations.morph(ramp,true),"Reject nonbinary morphology");
        Document sample=Document.sample();double t=Operations.autoThreshold(sample.current(),"Otsu");check(t>1500&&t<28000,"Otsu separates sample nuclei");Plane mask=Operations.threshold(sample.current(),t,false);check(Operations.particles(mask,null,20,false).size()==18,"18 known sample nuclei");
        Plane dark=Operations.threshold(sample.current(),t,true);for(int i=0;i<mask.size();i++)near(mask.value(i)+dark.value(i),255,0,"Complementary threshold");
        Document stack=doc(ramp(3,2,Plane.U16));Plane p=Plane.empty(3,2,Plane.U16);Arrays.fill((short[])p.pixels,(short)100);stack.planes.add(p);near(Operations.projection(stack,true).value(0),100,0,"Max Z");near(Operations.projection(stack,false).value(1),50.5,0,"Mean Z float precision");
        Plane floats=new Plane(3,2,Plane.F32,new float[]{-10.25f,0,1.125f,65536.5f,Float.NaN,Float.POSITIVE_INFINITY});stats=Operations.measure(floats,null,1,1);check(stats.count==4,"Ignore nonfinite measurements");near(stats.min,-10.25,0,"Float minimum");
        for(int type:new int[]{Plane.U8,Plane.U16,Plane.F32,Plane.RGB}){
            Plane original=Plane.empty(13,7,type);for(int i=0;i<original.size();i++){if(type==Plane.RGB)((int[])original.pixels)[i]=0xff000000|i*12345;else original.set(i,type==Plane.F32?i*.125-4:type==Plane.U16?i*700:i);}
            Document d=doc(original);d.planes.add(original.copy());d.pixelWidth=.25;d.pixelHeight=.75;d.unit="um";File f=new File(directory,"roundtrip-"+type+".tif");try(OutputStream out=new FileOutputStream(f)){TiffIO.write(d,out,true);}Document back=TiffIO.read(f);check(back.planes.size()==2,"Stack TIFF slice count");equal(original,back.planes.get(0));equal(original,back.planes.get(1));near(back.pixelWidth,.25,1e-6,"TIFF X calibration");near(back.pixelHeight,.75,1e-6,"TIFF Y calibration");check(back.unit.equals("um"),"TIFF unit");
        }
        File sampleFile=new File(directory,"sample-16bit-stack.tif");try(OutputStream out=new FileOutputStream(sampleFile)){TiffIO.write(sample,out,true);}Document recovered=TiffIO.read(sampleFile);check(recovered.planes.size()==3,"Sample TIFF stack roundtrip");equal(sample.current(),recovered.current());
        File broken=new File(directory,"truncated.tif");try(OutputStream out=new FileOutputStream(broken)){out.write(new byte[]{73,73,42,0,8,0,0,0,0});}rejects(()->TiffIO.read(broken),"Reject truncated TIFF");
        File cycle=new File(directory,"cycle.tif");try(RandomAccessFile f=new RandomAccessFile(cycle,"rw")){f.write(new byte[]{73,73,42,0,8,0,0,0,1,0,0,1,3,0,1,0,0,0,1,0,0,0,8,0,0,0});}rejects(()->TiffIO.read(cycle),"Reject cyclic IFD");
        rejects(()->Operations.gaussian(ramp,Double.NaN),"Reject invalid sigma");
        return "PASS "+assertions+" assertions; precision, ROI stats, crop, rotate, flip, invert, grayscale, Gaussian, median, Sobel, Otsu, morphology, particles, projections, TIFF stacks/calibration, malformed input; "+((System.nanoTime()-start)/1_000_000)+" ms";
    }
    public static void main(String[] args)throws Exception{System.out.println(run(new File(args.length==0?"tests/output":args[0])));}
}
