package org.fijipocket.core;
import java.util.*;

public final class Document {
    public final ArrayList<Plane> planes=new ArrayList<>();
    public String name="Untitled",unit="px";
    public String demoId="";
    public double pixelWidth=1,pixelHeight=1;
    public boolean whiteIsZero;
    public int slice;
    public Plane current() { return planes.get(slice); }
    /** Shallow snapshots: processing always creates new pixel arrays. */
    public Document snapshot() {
        Document d=new Document();d.planes.addAll(planes);d.name=name;d.unit=unit;
        d.pixelWidth=pixelWidth;d.pixelHeight=pixelHeight;d.whiteIsZero=whiteIsZero;d.slice=slice;d.demoId=demoId;return d;
    }
    public long bytes(){long n=0;for(Plane p:planes)n+=p.bytes();return n;}
    public void validate() {
        if(planes.isEmpty()||planes.size()>512||bytes()>32L*1024*1024)
            throw new IllegalArgumentException("This mobile build supports up to 32 MB of decoded pixels and 512 slices.");
        Plane first=planes.get(0);
        for(Plane p:planes)if(p.width!=first.width||p.height!=first.height||p.type!=first.type)
            throw new IllegalArgumentException("Stack slices must have matching dimensions and pixel types.");
    }
    public static Document sample() {
        Document d=new Document(); d.name="Nuclei · synthetic.tif";d.unit="µm";d.pixelWidth=d.pixelHeight=.5;
        Random random=new Random(42);
        for(int z=0;z<3;z++) {
            Plane p=Plane.empty(768,512,Plane.U16);
            for(int i=0;i<p.size();i++)p.set(i,1300+random.nextInt(600));
            for(int cy=0;cy<3;cy++)for(int cx=0;cx<6;cx++) {
                int x0=70+cx*125+(cy%2)*8, y0=86+cy*166;
                int rx=24+(cx%3)*3+z,ry=30+(cy%2)*5+z;
                for(int y=y0-ry-3;y<=y0+ry+3;y++)for(int x=x0-rx-3;x<=x0+rx+3;x++){
                    double r=(x-x0)*(x-x0)/(double)(rx*rx)+(y-y0)*(y-y0)/(double)(ry*ry);
                    if(r<=1)p.set(y*p.width+x,26000+(1-r)*21000+random.nextInt(1400));
                }
            }
            d.planes.add(p);
        }
        return d;
    }
}
