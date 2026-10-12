package org.fijipocket;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import org.fijipocket.core.*;
import java.util.*;

/** Native, zoomable canvas; all analysis refers to source pixels, never preview pixels. */
public final class ImageCanvas extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaling;
    private final UiTheme colors;
    private Plane plane; private Bitmap bitmap;
    public Operations.Region roi;
    public List<Operations.Particle> particles=Collections.emptyList();
    public boolean selectRoi,whiteIsZero;
    public String lut="Gray";
    public double low=0,high=255;
    public Double threshold;public boolean dark;
    public interface Listener{void changed();void pixel(String text);}
    public Listener listener;
    private float zoom=1,panX,panY,lastX,lastY,startX,startY;
    private boolean multiTouch;
    public ImageCanvas(Context context){
        super(context);colors=UiTheme.colors(context);setContentDescription("Image canvas. Pinch to zoom, drag to pan. Select ROI to draw a measurement rectangle.");setFocusable(true);
        scaling=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            public boolean onScale(ScaleGestureDetector d){zoom=Math.max(.5f,Math.min(16,zoom*d.getScaleFactor()));invalidate();return true;}
        });
    }
    public void setPlane(Plane p,boolean reset){plane=p;if(reset){roi=null;fit();autoRange();}refresh();}
    public Plane getPlane(){return plane;}
    public void autoRange(){if(plane==null)return;double[] r=plane.range();low=r[0];high=r[1]>r[0]?r[1]:r[0]+1;}
    public void fit(){zoom=1;panX=panY=0;invalidate();}
    void copyViewport(ImageCanvas previous){zoom=previous.zoom;panX=previous.panX;panY=previous.panY;}
    public void clearRoi(){roi=null;particles=Collections.emptyList();invalidate();if(listener!=null)listener.changed();}
    public void refresh(){
        if(plane==null)return;
        float factor=Math.min(1,1400f/Math.max(plane.width,plane.height));
        int w=Math.max(1,Math.round(plane.width*factor)),h=Math.max(1,Math.round(plane.height*factor));
        Bitmap next=render(plane,w,h,low,high,lut,whiteIsZero,threshold,dark);
        Bitmap old=bitmap;bitmap=next;if(old!=null)old.recycle();invalidate();
    }
    public static Bitmap render(Plane p,int w,int h,double lo,double hi,String lut,boolean inverse,Double threshold,boolean dark){
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int source=Math.min(p.height-1,(int)((long)y*p.height/h))*p.width+Math.min(p.width-1,(int)((long)x*p.width/w));
            float value=p.value(source);int color;
            if(p.type==Plane.RGB)color=0xff000000|((int[])p.pixels)[source];
            else if(!Float.isFinite(value))color=0xffd66baf;
            else{
                int v=(int)Math.max(0,Math.min(255,Math.round((value-lo)*255/Math.max(1e-30,hi-lo))));if(inverse)v=255-v;
                if(lut.equals("Green"))color=Color.rgb(v/5,v,v/2);
                else if(lut.equals("Fire"))color=Color.rgb(Math.min(255,v*3),Math.max(0,Math.min(255,(v-85)*3)),Math.max(0,(v-170)*3));
                else color=Color.rgb(v,v,v);
            }
            if(threshold!=null&&Float.isFinite(value)&&(dark?value<=threshold:value>threshold))color=0xff55dfc6;
            pixels[y*w+x]=color;
        }
        return Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
    }
    private float scale(){if(plane==null)return 1;return Math.min((getWidth()-48f)/plane.width,(getHeight()-48f)/plane.height)*zoom;}
    private float left(){return (getWidth()-plane.width*scale())/2+panX;}
    private float top(){return (getHeight()-plane.height*scale())/2+panY;}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);canvas.drawColor(colors.workspace);
        paint.setColor(colors.grid);paint.setStrokeWidth(1);
        for(int x=24;x<getWidth();x+=32)for(int y=24;y<getHeight();y+=32)canvas.drawCircle(x,y,1,paint);
        if(bitmap==null||plane==null)return;
        float s=scale(),l=left(),t=top();
        paint.setFilterBitmap(zoom<2);paint.setColor(Color.WHITE);
        RectF image=new RectF(l,t,l+plane.width*s,t+plane.height*s);
        canvas.drawBitmap(bitmap,null,image,paint);
        paint.setColor(colors.imageBorder);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(image,paint);
        if(roi!=null){
            paint.setColor(0xff56dac6);paint.setStrokeWidth(2);paint.setPathEffect(new DashPathEffect(new float[]{8,5},0));
            canvas.drawRect(l+roi.x*s,t+roi.y*s,l+(roi.x+roi.w)*s,t+(roi.y+roi.h)*s,paint);paint.setPathEffect(null);
        }
        paint.setStrokeWidth(1.5f);paint.setTextSize(18*getResources().getDisplayMetrics().density);
        int label=1;
        for(Operations.Particle p:particles){
            paint.setColor(0xfff8cd81);paint.setStyle(Paint.Style.STROKE);canvas.drawRect(l+p.left*s,t+p.top*s,l+(p.right+1)*s,t+(p.bottom+1)*s,paint);
            if(particles.size()<=200){paint.setStyle(Paint.Style.FILL);canvas.drawText(Integer.toString(label),l+(float)p.cx*s,t+(float)p.cy*s,paint);}label++;
        }
        paint.setStyle(Paint.Style.FILL);paint.setColor(colors.muted);paint.setTextSize(12*getResources().getDisplayMetrics().density);
        canvas.drawText(String.format(Locale.US,"%.0f%%  ·  Pinch to zoom",s*100),18,getHeight()-16,paint);
    }
    private float imageX(float x){return Math.max(0,Math.min(plane.width-1,(x-left())/scale()));}
    private float imageY(float y){return Math.max(0,Math.min(plane.height-1,(y-top())/scale()));}
    @Override public boolean onTouchEvent(MotionEvent e){
        if(plane==null)return true;scaling.onTouchEvent(e);
        if(e.getPointerCount()>1){multiTouch=true;return true;}
        float x=e.getX(),y=e.getY();
        switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:lastX=x;lastY=y;startX=imageX(x);startY=imageY(y);multiTouch=false;getParent().requestDisallowInterceptTouchEvent(true);return true;
            case MotionEvent.ACTION_MOVE:
                if(multiTouch)return true;
                if(selectRoi){int xx=(int)imageX(x),yy=(int)imageY(y);roi=new Operations.Region((int)Math.min(startX,xx),(int)Math.min(startY,yy),Math.abs(xx-(int)startX)+1,Math.abs(yy-(int)startY)+1);particles=Collections.emptyList();}
                else{panX+=x-lastX;panY+=y-lastY;}
                lastX=x;lastY=y;invalidate();return true;
            case MotionEvent.ACTION_UP:
                getParent().requestDisallowInterceptTouchEvent(false);
                if(!multiTouch&&listener!=null){int xx=(int)imageX(x),yy=(int)imageY(y);listener.pixel(String.format(Locale.US,"x %d  y %d  ·  value %.3f",xx,yy,plane.value(yy*plane.width+xx)));listener.changed();}
                performClick();return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
