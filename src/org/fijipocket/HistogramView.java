package org.fijipocket;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.fijipocket.core.Plane;
import java.util.Locale;

final class HistogramView extends View {
    private final long[] bins=new long[256];
    private long maximum=1,count;
    private final double low,high;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final UiTheme colors;
    HistogramView(Context c,Plane p){
        super(c);colors=UiTheme.colors(c);double[] range=p.range();low=range[0];high=range[1];
        for(int i=0;i<p.size();i++){double v=p.value(i);if(Double.isFinite(v)){int bin=(int)((v-low)*255/Math.max(1e-30,high-low));bins[Math.max(0,Math.min(255,bin))]++;count++;}}
        for(long n:bins)maximum=Math.max(maximum,n);
        setContentDescription("Histogram of "+count+" finite pixels; 256 bins from "+low+" to "+high);
        setMinimumHeight((int)(220*c.getResources().getDisplayMetrics().density));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float density=getResources().getDisplayMetrics().density,pad=20*density,bottom=getHeight()-42*density,top=30*density,width=getWidth()-2*pad;
        paint.setColor(colors.accent);for(int i=0;i<256;i++){float x=pad+i*width/256;canvas.drawRect(x,bottom-(bottom-top)*bins[i]/maximum,x+Math.max(1,width/256),bottom,paint);}
        paint.setColor(colors.muted);paint.setStrokeWidth(density);canvas.drawLine(pad,bottom,pad+width,bottom,paint);paint.setTextSize(12*density);
        canvas.drawText(String.format(Locale.US,"%,d finite pixels · 256 bins",count),pad,18*density,paint);
        canvas.drawText(String.format(Locale.US,"%.5g",low),pad,bottom+24*density,paint);String max=String.format(Locale.US,"%.5g",high);canvas.drawText(max,pad+width-paint.measureText(max),bottom+24*density,paint);
    }
}
