package org.fijipocket;

import android.content.Context;
import org.fijipocket.core.*;
import java.io.*;

/** APK assets only: demos never require network or storage permissions. */
public final class DemoFiles {
    public static Document read(Context context,BiologyDemos.Demo demo)throws IOException {
        File temp=File.createTempFile("biology-",".tif",context.getCacheDir());
        try {
            try(InputStream in=context.getAssets().open("demos/"+demo.id+".tif");OutputStream out=new FileOutputStream(temp)){
                byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            }
            Document d=TiffIO.read(temp);d.name=demo.title+" · original.tif";d.demoId=demo.id;
            d.slice=demo.id.equals("brain")?d.planes.size()/2:0;return d;
        }finally{temp.delete();}
    }
}
