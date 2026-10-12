package org.fijipocket.core;

/** A pixel plane retains the source precision. Display normalization never edits it. */
public final class Plane {
    public static final int U8=8, U16=16, F32=32, RGB=24;
    public final int width, height, type;
    public final Object pixels;
    public Plane(int width, int height, int type, Object pixels) {
        if (width<1 || height<1 || (long)width*height>8_388_608)
            throw new IllegalArgumentException("Image exceeds the 8-megapixel plane limit.");
        this.width=width; this.height=height; this.type=type; this.pixels=pixels;
        if (java.lang.reflect.Array.getLength(pixels)!=width*height)
            throw new IllegalArgumentException("Pixel count does not match image dimensions.");
    }
    public static Plane empty(int w,int h,int type) {
        int n=Math.multiplyExact(w,h);
        return new Plane(w,h,type,type==U8?new byte[n]:type==U16?new short[n]:type==F32?new float[n]:new int[n]);
    }
    public int size() { return width*height; }
    public int bytes() { return size()*(type==U8?1:type==U16?2:4); }
    public float value(int i) {
        switch(type) {
            case U8: return ((byte[])pixels)[i]&255;
            case U16: return ((short[])pixels)[i]&65535;
            case F32: return ((float[])pixels)[i];
            default: int c=((int[])pixels)[i]; return (float)(.299*((c>>16)&255)+.587*((c>>8)&255)+.114*(c&255));
        }
    }
    public void set(int i,double value) {
        switch(type) {
            case U8: ((byte[])pixels)[i]=(byte)Math.max(0,Math.min(255,Math.round(value))); break;
            case U16: ((short[])pixels)[i]=(short)Math.max(0,Math.min(65535,Math.round(value))); break;
            case F32: ((float[])pixels)[i]=(float)value; break;
            default: int v=(int)Math.max(0,Math.min(255,Math.round(value))); ((int[])pixels)[i]=0xff000000|v<<16|v<<8|v;
        }
    }
    public void copyPixel(int to,Plane source,int from) {
        if(type==RGB) ((int[])pixels)[to]=((int[])source.pixels)[from];
        else set(to,source.value(from));
    }
    public Plane copy() {
        Object copy=type==U8?((byte[])pixels).clone():type==U16?((short[])pixels).clone():type==F32?((float[])pixels).clone():((int[])pixels).clone();
        return new Plane(width,height,type,copy);
    }
    public double[] range() {
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY;
        for(int i=0;i<size();i++) { float v=value(i); if(Float.isFinite(v)){lo=Math.min(lo,v);hi=Math.max(hi,v);} }
        return lo==Double.POSITIVE_INFINITY?new double[]{0,1}:new double[]{lo,hi};
    }
    public String label() { return type==RGB?"RGB":type+"-bit"+(type==F32?" float":""); }
}
