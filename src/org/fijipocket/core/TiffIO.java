package org.fijipocket.core;

import ij.io.*;
import java.io.*;
import java.util.*;

/** ImageJ's actual TIFF codec, with bounded mobile import and explicit format checks. */
public final class TiffIO {
    public static final long MAX_FILE=128L*1024*1024;
    public static Document read(File file) throws IOException {
        preflight(file);
        FileInfo[] infos=new TiffDecoder(file.getParent(),file.getName()).getTiffInfo();
        if(infos==null||infos.length==0)throw new IOException("No supported TIFF images found.");
        Document d=new Document();d.name=file.getName();
        FileInfo first=infos[0];
        d.whiteIsZero=first.whiteIsZero;
        d.pixelWidth=first.pixelWidth;d.pixelHeight=first.pixelHeight;
        d.unit=first.unit==null?"px":first.unit;
        if(first.description!=null){
            Properties props=new Properties();props.load(new StringReader(first.description));
            d.unit=props.getProperty("unit",d.unit);
        }
        if(!Double.isFinite(d.pixelWidth)||!Double.isFinite(d.pixelHeight)||d.pixelWidth<=0||d.pixelHeight<=0){d.pixelWidth=d.pixelHeight=1;d.unit="px";}
        long decoded=0;
        for(FileInfo fi:infos){
            int type;
            switch(fi.fileType){
                case FileInfo.GRAY8:type=Plane.U8;break;
                case FileInfo.GRAY16_UNSIGNED:type=Plane.U16;break;
                case FileInfo.GRAY32_FLOAT:type=Plane.F32;break;
                case FileInfo.RGB:case FileInfo.RGB_PLANAR:type=Plane.RGB;break;
                default:throw new IOException("Unsupported TIFF pixel type. Use 8-bit, unsigned 16-bit, 32-bit float, or RGB TIFF.");
            }
            long pixels=(long)fi.width*fi.height;
            if(fi.width<1||fi.height<1||pixels>8_388_608)throw new IOException("TIFF exceeds the 8-megapixel plane limit.");
            int count=Math.max(1,fi.nImages);
            decoded+=pixels*(type==Plane.U8?1:type==Plane.U16?2:4)*count;
            if(count>512||d.planes.size()+count>512||decoded>32L*1024*1024)throw new IOException("TIFF exceeds the mobile limit: 32 MB of decoded pixels / 512 slices.");
            if(count>1&&fi.compression>FileInfo.COMPRESSION_NONE)throw new IOException("Compressed contiguous ImageJ stacks are unsupported. Save as standard multipage TIFF.");
            for(int z=0;z<count;z++){
                FileInfo planeInfo=(FileInfo)fi.clone();
                if(z>0){planeInfo.longOffset=fi.getOffset()+z*(pixels*fi.getBytesPerPixel()+fi.getGap());planeInfo.stripOffsets=null;planeInfo.stripLengths=null;}
                if(fi.compression<=FileInfo.COMPRESSION_NONE&&planeInfo.getOffset()+pixels*fi.getBytesPerPixel()>file.length())throw new IOException("TIFF pixel data is truncated.");
                Object data;
                try(RandomAccessStream in=new RandomAccessStream(new RandomAccessFile(file,"r"))){data=new ImageReader(planeInfo).readPixels(in);}
                if(data==null)throw new IOException("Could not decode TIFF pixels; the file may be truncated or unsupported.");
                // WhiteIsZero is a display LUT. Keep the stored numerical values intact.
                d.planes.add(new Plane(fi.width,fi.height,type,data));
            }
        }
        try{d.validate();}catch(IllegalArgumentException e){throw new IOException(e.getMessage());}
        return d;
    }
    public static void write(Document d,OutputStream out,boolean allSlices) throws IOException {
        d.validate();Plane p=d.current();FileInfo fi=new FileInfo();
        fi.width=p.width;fi.height=p.height;fi.fileFormat=FileInfo.TIFF;
        fi.fileType=p.type==Plane.U8?FileInfo.GRAY8:p.type==Plane.U16?FileInfo.GRAY16_UNSIGNED:p.type==Plane.F32?FileInfo.GRAY32_FLOAT:FileInfo.RGB;
        fi.intelByteOrder=true;fi.pixelWidth=d.pixelWidth;fi.pixelHeight=d.pixelHeight;fi.unit=d.unit;
        fi.whiteIsZero=d.whiteIsZero;
        fi.nImages=allSlices?d.planes.size():1;
        if(fi.nImages==1)fi.pixels=p.pixels;
        else{Object[] pixels=new Object[fi.nImages];for(int i=0;i<pixels.length;i++)pixels[i]=d.planes.get(i).pixels;fi.pixels=pixels;}
        // ImageJ descriptions use Latin-1. ASCII "um" is portable across readers.
        fi.description="ImageJ=1.54\nimages="+fi.nImages+"\nslices="+fi.nImages+"\nunit="+d.unit.replace("µm","um")+"\nloop=false\n";
        new TiffEncoder(fi).write(out);
    }
    private static int u16(RandomAccessFile f,boolean little)throws IOException{int a=f.readUnsignedByte(),b=f.readUnsignedByte();return little?a|(b<<8):(a<<8)|b;}
    private static long u32(RandomAccessFile f,boolean little)throws IOException{long a=u16(f,little),b=u16(f,little);return little?a|(b<<16):(a<<16)|b;}
    /** Validate offsets, allocation sizes and the IFD chain before the upstream decoder runs. */
    public static void preflight(File file)throws IOException {
        if(file.length()<8||file.length()>MAX_FILE)throw new IOException("TIFF must be between 8 bytes and 128 MB.");
        try(RandomAccessFile f=new RandomAccessFile(file,"r")){
            int order=f.readUnsignedShort();boolean little=order==0x4949;
            if(!little&&order!=0x4d4d)throw new IOException("Not a TIFF file.");
            if(u16(f,little)!=42)throw new IOException("BigTIFF is unsupported. Save a smaller standard TIFF.");
            long offset=u32(f,little);Set<Long> seen=new HashSet<>();
            while(offset!=0){
                if(offset<8||offset>file.length()-6||!seen.add(offset)||seen.size()>512)throw new IOException("Invalid or oversized TIFF directory chain.");
                f.seek(offset);int n=u16(f,little);if(n<1||n>1000||offset+2+12L*n+4>file.length())throw new IOException("Invalid TIFF directory.");
                for(int i=0;i<n;i++){
                    int tag=u16(f,little),type=u16(f,little);long count=u32(f,little),field=f.getFilePointer(),value=u32(f,little),back=f.getFilePointer();
                    int size=type==1||type==2||type==6||type==7?1:type==3||type==8?2:type==4||type==9||type==11?4:type==5||type==10||type==12?8:0;
                    long length=count*size;
                    if(count>1_048_576||length>8L*1024*1024||(length>4&&(value>file.length()-length)))throw new IOException("Invalid or oversized TIFF metadata.");
                    if(type==3&&count==1){f.seek(field);value=u16(f,little);f.seek(back);}
                    if(tag==274&&value!=1)throw new IOException("TIFF orientation must be top-left. Reorient and save the file first.");
                    if(tag==322||tag==323)throw new IOException("Tiled TIFFs are unsupported. Export a strip-based TIFF first.");
                    if(tag==317&&value>2)throw new IOException("Floating-point TIFF predictor is unsupported. Save without predictor.");
                    if(tag==259&&value!=1&&value!=5&&value!=8&&value!=32946&&value!=32773)throw new IOException("Unsupported TIFF compression. Use uncompressed, LZW, Deflate, or PackBits.");
                }
                offset=u32(f,little);
            }
        }
    }
}
