package org.fijipocket.core;
import ij.process.AutoThresholder;
import java.util.*;

public final class Operations {
    public static final class Region {
        public final int x,y,w,h;
        public Region(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;}
        public Region clip(Plane p){int l=Math.max(0,Math.min(x,p.width-1)),t=Math.max(0,Math.min(y,p.height-1));return new Region(l,t,Math.max(1,Math.min(w,p.width-l)),Math.max(1,Math.min(h,p.height-t)));}
    }
    public static Region region(Plane p,Region r){return r==null?new Region(0,0,p.width,p.height):r.clip(p);}
    public static Plane grayscale(Plane p) {
        if(p.type!=Plane.RGB)return p.copy();
        Plane q=Plane.empty(p.width,p.height,Plane.U8);for(int i=0;i<p.size();i++)q.set(i,p.value(i));return q;
    }
    /** Extract an RGB component without display normalization: 0 red, 1 green, 2 blue. */
    public static Plane channel(Plane p,int channel) {
        if(p.type!=Plane.RGB||channel<0||channel>2)
            throw new IllegalArgumentException("Choose a red, green, or blue channel from an RGB image.");
        Plane q=Plane.empty(p.width,p.height,Plane.U8);int shift=16-channel*8;
        for(int i=0;i<p.size();i++)((byte[])q.pixels)[i]=(byte)(((int[])p.pixels)[i]>>shift);
        return q;
    }
    public static Plane invert(Plane p) {
        Plane q=p.copy(); double[] range=p.range();double top=p.type==Plane.U8?255:p.type==Plane.U16?65535:range[0]+range[1];
        for(int i=0;i<p.size();i++)if(p.type==Plane.RGB)((int[])q.pixels)[i]=0xff000000|(~((int[])p.pixels)[i]&0xffffff);else q.set(i,top-p.value(i));
        return q;
    }
    public static Plane gaussian(Plane p,double sigma) {
        Plane q=Plane.empty(p.width,p.height,p.type);
        float[] f=new float[p.size()];
        for(int channel=0;channel<(p.type==Plane.RGB?3:1);channel++) {
            int shift=16-channel*8;
            for(int i=0;i<f.length;i++)f[i]=p.type==Plane.RGB?(((int[])p.pixels)[i]>>shift)&255:p.value(i);
            GaussianKernel.blur(f,p.width,p.height,sigma);
            for(int i=0;i<f.length;i++)if(p.type==Plane.RGB)((int[])q.pixels)[i]|=0xff000000|Math.max(0,Math.min(255,Math.round(f[i])))<<shift;else q.set(i,f[i]);
        }
        return q;
    }
    public static Plane median(Plane p) {
        Plane q=Plane.empty(p.width,p.height,p.type);float[] v=new float[9];
        for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++)for(int c=0;c<(p.type==Plane.RGB?3:1);c++) {
            int k=0,shift=16-c*8;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int i=Math.max(0,Math.min(p.height-1,y+dy))*p.width+Math.max(0,Math.min(p.width-1,x+dx));
                v[k++]=p.type==Plane.RGB?(((int[])p.pixels)[i]>>shift)&255:p.value(i);
            }
            Arrays.sort(v);
            if(p.type==Plane.RGB)((int[])q.pixels)[y*p.width+x]|=0xff000000|Math.round(v[4])<<shift;else q.set(y*p.width+x,v[4]);
        }
        return q;
    }
    public static Plane edges(Plane p) {
        Plane q=Plane.empty(p.width,p.height,p.type==Plane.RGB?Plane.U8:p.type);
        int[] kx={-1,0,1,-2,0,2,-1,0,1},ky={-1,-2,-1,0,0,0,1,2,1};
        for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++){
            double gx=0,gy=0;int k=0;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                float v=p.value(Math.max(0,Math.min(p.height-1,y+dy))*p.width+Math.max(0,Math.min(p.width-1,x+dx)));
                gx+=v*kx[k];gy+=v*ky[k++];
            }
            q.set(y*p.width+x,Math.hypot(gx,gy));
        }
        return q;
    }
    public static Plane crop(Plane p,Region region) {
        Region r=region(p,region);Plane q=Plane.empty(r.w,r.h,p.type);
        for(int y=0;y<r.h;y++)for(int x=0;x<r.w;x++)q.copyPixel(y*r.w+x,p,(y+r.y)*p.width+x+r.x);return q;
    }
    public static Plane rotate(Plane p) {
        Plane q=Plane.empty(p.height,p.width,p.type);
        for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++)q.copyPixel(x*q.width+p.height-1-y,p,y*p.width+x);return q;
    }
    public static Plane flip(Plane p) {
        Plane q=Plane.empty(p.width,p.height,p.type);
        for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++)q.copyPixel(y*p.width+p.width-1-x,p,y*p.width+x);return q;
    }
    public static double autoThreshold(Plane p,String method) {
        double[] range=p.range();int bins=p.type==Plane.U16?65536:256;
        int[] hist=new int[bins];
        for(int i=0;i<p.size();i++){
            float v=p.value(i);if(!Float.isFinite(v))continue;
            int b=p.type==Plane.F32?(int)((v-range[0])*255/Math.max(1e-30,range[1]-range[0])):Math.round(v);
            hist[Math.max(0,Math.min(bins-1,b))]++;
        }
        int t=new AutoThresholder().getThreshold(method,hist);
        return p.type==Plane.F32?range[0]+t*(range[1]-range[0])/255:t;
    }
    public static Plane threshold(Plane p,double t,boolean dark) {
        Plane q=Plane.empty(p.width,p.height,Plane.U8);
        for(int i=0;i<p.size();i++){float v=p.value(i);q.set(i,Float.isFinite(v)&&(dark?v<=t:v>t)?255:0);}return q;
    }
    public static boolean isBinary(Plane p) {
        if(p.type!=Plane.U8)return false;
        for(byte v:(byte[])p.pixels)if(v!=0&&v!=(byte)255)return false;return true;
    }
    public static Plane morph(Plane p,boolean dilate) {
        if(!isBinary(p))throw new IllegalArgumentException("Apply a threshold to create a binary mask first.");
        Plane q=Plane.empty(p.width,p.height,Plane.U8);
        for(int y=0;y<p.height;y++)for(int x=0;x<p.width;x++){
            boolean hit=!dilate;
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                int xx=x+dx,yy=y+dy;boolean v=xx>=0&&yy>=0&&xx<p.width&&yy<p.height&&p.value(yy*p.width+xx)>0;
                if(dilate)hit|=v;else hit&=v;
            }
            q.set(y*p.width+x,hit?255:0);
        }
        return q;
    }
    public static Plane projection(Document doc,boolean maximum) {
        Plane p=doc.current();if(p.type==Plane.RGB)throw new IllegalArgumentException("Convert the stack to grayscale before projection.");
        Plane q=Plane.empty(p.width,p.height,maximum?p.type:Plane.F32);
        for(int i=0;i<p.size();i++){
            double v=maximum?Double.NEGATIVE_INFINITY:0;int n=0;
            for(Plane s:doc.planes){float x=s.value(i);if(Float.isFinite(x)){v=maximum?Math.max(v,x):v+x;n++;}}
            q.set(i,n==0?Double.NaN:maximum?v:v/n);
        }return q;
    }
    public static final class Stats {
        public long count;public double mean,std,min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY,sum,area;
    }
    public static Stats measure(Plane p,Region region,double pixelWidth,double pixelHeight) {
        Region r=region(p,region);Stats s=new Stats();double m2=0;
        for(int y=r.y;y<r.y+r.h;y++)for(int x=r.x;x<r.x+r.w;x++){
            double v=p.value(y*p.width+x);if(!Double.isFinite(v))continue;
            s.count++;double delta=v-s.mean;s.mean+=delta/s.count;m2+=delta*(v-s.mean);s.sum+=v;s.min=Math.min(s.min,v);s.max=Math.max(s.max,v);
        }
        s.std=s.count>1?Math.sqrt(m2/(s.count-1)):0;s.area=r.w*(double)r.h*pixelWidth*pixelHeight;
        if(s.count==0){s.mean=s.min=s.max=s.std=Double.NaN;}return s;
    }
    public static final class Particle {
        public int count,left,top,right,bottom;public double cx,cy;
    }
    /** Eight-connected white foreground; areas in pixels, centers at pixel centers. */
    public static List<Particle> particles(Plane p,Region region,int minSize,boolean excludeEdges) {
        if(!isBinary(p))throw new IllegalArgumentException("Apply a threshold first. Particles are the white areas of a binary mask.");
        if(minSize<1)throw new IllegalArgumentException("Minimum particle area must be at least 1 pixel.");
        Region r=region(p,region);BitSet seen=new BitSet(p.size());ArrayList<Particle> result=new ArrayList<>();
        int[] queue=new int[r.w*r.h];
        for(int y=r.y;y<r.y+r.h;y++)for(int x=r.x;x<r.x+r.w;x++){
            int i=y*p.width+x;if(seen.get(i)||p.value(i)==0)continue;
            Particle a=new Particle();a.left=a.right=x;a.top=a.bottom=y;int head=0,tail=0;queue[tail++]=i;seen.set(i);boolean edge=false;
            while(head<tail){
                int index=queue[head++],xx=index%p.width,yy=index/p.width;
                a.count++;a.cx+=xx+.5;a.cy+=yy+.5;a.left=Math.min(a.left,xx);a.right=Math.max(a.right,xx);a.top=Math.min(a.top,yy);a.bottom=Math.max(a.bottom,yy);
                edge|=xx==r.x||yy==r.y||xx==r.x+r.w-1||yy==r.y+r.h-1;
                for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
                    int nx=xx+dx,ny=yy+dy;
                    if(nx<r.x||ny<r.y||nx>=r.x+r.w||ny>=r.y+r.h)continue;
                    int ni=ny*p.width+nx;if(!seen.get(ni)&&p.value(ni)>0){seen.set(ni);queue[tail++]=ni;}
                }
            }
            if(a.count>=minSize&&(!excludeEdges||!edge)){a.cx/=a.count;a.cy/=a.count;result.add(a);}
            if(result.size()>10000)throw new IllegalArgumentException("Over 10,000 particles. Increase the minimum area or select a smaller ROI.");
        }return result;
    }
}
