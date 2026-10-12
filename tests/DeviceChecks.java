package org.fijipocket.tests;
import android.app.Instrumentation;
import android.os.Bundle;
import java.io.*;
import org.fijipocket.core.BiologyDemos;

/** Separate test APK; no test entry point or debug flag in the distributed app. */
public final class DeviceChecks extends Instrumentation {
    private boolean cleanupDemoRows;
    @Override public void onCreate(Bundle args){super.onCreate(args);cleanupDemoRows=args!=null&&"true".equals(args.getString("cleanupDemoRows"));start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        // Explicit test cleanup leaves pre-existing ROI/Particle measurement rows intact.
        if(cleanupDemoRows){
            File file=new File(getTargetContext().getFilesDir(),"results.csv");int removed=0;
            if(file.exists()){
                StringBuilder kept=new StringBuilder();
                try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),"UTF-8"))){
                    String line;while((line=reader.readLine())!=null){
                        if(line.startsWith("DemoParticle,\"Fluorescent cells · mask.tif\",")||line.startsWith("DemoParticle,\"Embryos · mask.tif\","))removed++;
                        else kept.append(line).append('\n');
                    }
                }
                try(OutputStream out=new FileOutputStream(file)){out.write(kept.toString().getBytes("UTF-8"));}
            }
            result.putString("stream","Removed "+removed+" generated demo test rows; other measurements preserved.\n");finish(-1,result);return;
        }
        String report=CoreChecks.run(new File(getTargetContext().getCacheDir(),"core-checks"));
        File assets=new File(getTargetContext().getCacheDir(),"demo-checks");assets.mkdirs();
        for(BiologyDemos.Demo demo:BiologyDemos.ALL){
            try(InputStream in=getTargetContext().getAssets().open("demos/"+demo.id+".tif");OutputStream out=new FileOutputStream(new File(assets,demo.id+".tif"))){
                byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            }
        }
        report+="\n"+DemoChecks.run(assets,assets);
        result.putString("stream",report+"\n");finish(-1,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e));finish(0,result);}}
}
