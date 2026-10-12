package org.fijipocket;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.fijipocket.core.*;
import java.io.*;

public final class ImageFiles {
    public static String name(Context c,Uri uri){
        try(Cursor q=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){
            if(q!=null&&q.moveToFirst())return q.getString(0);
        }catch(Exception ignored){}
        return "Imported image";
    }
    public static Document read(Context c,Uri uri)throws IOException {
        String name=name(c,uri);File temp=File.createTempFile("import-",".image",c.getCacheDir());
        try{
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(temp)){
                if(in==null)throw new IOException("Unable to open the selected document.");
                byte[] b=new byte[65536];long total=0;int n;
                while((n=in.read(b))!=-1){total+=n;if(total>TiffIO.MAX_FILE)throw new IOException("File exceeds the 128 MB import limit.");out.write(b,0,n);}
            }
            byte[] magic=new byte[4];try(InputStream in=new FileInputStream(temp)){if(in.read(magic)<4)throw new IOException("File is empty or truncated.");}
            Document d;
            if((magic[0]=='I'&&magic[1]=='I')||(magic[0]=='M'&&magic[1]=='M'))d=TiffIO.read(temp);
            else{
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(temp.getPath(),options);
                if(options.outWidth<1||options.outHeight<1)throw new IOException("Unsupported image. Open PNG, JPEG, WebP, or a supported TIFF.");
                if((long)options.outWidth*options.outHeight>8_388_608)throw new IOException("Image exceeds 8 megapixels. Resize it before importing.");
                options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
                Bitmap bitmap=BitmapFactory.decodeFile(temp.getPath(),options);if(bitmap==null)throw new IOException("Image decoding failed.");
                int[] pixels=new int[bitmap.getWidth()*bitmap.getHeight()];bitmap.getPixels(pixels,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight());
                d=new Document();d.planes.add(new Plane(bitmap.getWidth(),bitmap.getHeight(),Plane.RGB,pixels));bitmap.recycle();
            }
            d.name=name;d.validate();return d;
        } finally{temp.delete();}
    }
    public static void saveSession(Context c,Document d)throws IOException {
        File target=new File(c.getFilesDir(),"session.tif"),temp=new File(c.getFilesDir(),"session.pending");
        try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){TiffIO.write(d,out,true);}
        if(!temp.renameTo(target))throw new IOException("Could not save the working session.");
        c.getSharedPreferences("session",0).edit().putString("name",d.name).putInt("slice",d.slice).putString("demo",d.demoId).apply();
    }
    public static Document restore(Context c)throws IOException {
        File file=new File(c.getFilesDir(),"session.tif");if(!file.exists())return Document.sample();
        Document d=TiffIO.read(file);SharedPreferences prefs=c.getSharedPreferences("session",0);
        d.name=prefs.getString("name","Recovered image");d.slice=Math.max(0,Math.min(d.planes.size()-1,prefs.getInt("slice",0)));d.demoId=prefs.getString("demo","");return d;
    }
}
