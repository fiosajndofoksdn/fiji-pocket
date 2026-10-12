package org.fijipocket;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import org.fijipocket.core.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private int BG,PANEL,LINE,TEXT,MUTED,TEAL;
    private UiTheme colors;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Document doc,undo;
    private ImageCanvas canvas;
    private LinearLayout root,toolBody,demoBar;
    private TextView title,metadata,status,roiLabel,sliceLabel,resultLabel,demoLabel;
    private ProgressBar progress;
    private SeekBar sliceBar;
    private Dialog galleryDialog;
    private boolean busy;
    private int tab;
    private String result="No measurements yet",lastAction="Ready";
    private final StringBuilder csv=new StringBuilder("Kind,Image,Slice,X,Y,Width,Height,Pixels,Area,Unit,Mean,StdDev,Min,Max,IntegratedDensity,CentroidX,CentroidY\n");
    private final ArrayList<String> history=new ArrayList<>();
    private interface Work<T>{T run()throws Exception;}
    private interface Result<T>{void accept(T value);}
    private interface Transform{Plane apply(Plane p)throws Exception;}

    @Override public void onCreate(Bundle state){
        configureTheme();super.onCreate(state);
        doc=Document.sample();
        buildUi();
        worker.execute(()->{
            File file=new File(getFilesDir(),"results.csv");
            if(file.exists()&&file.length()<=8L*1024*1024){
                try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
                    String saved=out.toString("UTF-8");
                    if(saved.startsWith("Kind,Image,Slice,"))handler.post(()->{if(isDestroyed())return;csv.setLength(0);csv.append(saved);int rows=saved.split("\n").length-1;if(rows>0)result=rows+" saved result rows";fillTools();});
                }catch(IOException e){android.util.Log.w("FijiPocket","Results recovery failed",e);}
            }
        });
        Uri uri=getIntent().getData();
        run("Opening image…",()->uri==null?ImageFiles.restore(this):ImageFiles.read(this,uri),d->{doc=d;showImage(true);fillTools();save();if(state!=null&&state.getBoolean("galleryOpen",false))demos();});
    }
    @Override protected void onSaveInstanceState(Bundle state){state.putBoolean("galleryOpen",galleryDialog!=null&&galleryDialog.isShowing());super.onSaveInstanceState(state);}
    @Override public void onConfigurationChanged(Configuration c){boolean galleryOpen=galleryDialog!=null&&galleryDialog.isShowing();if(galleryOpen)galleryDialog.dismiss();super.onConfigurationChanged(c);buildUi();if(galleryOpen)demos();}
    @Override protected void onDestroy(){if(galleryDialog!=null)galleryDialog.dismiss();super.onDestroy();worker.shutdown();}
    private void configureTheme(){
        UiTheme.apply(this);colors=UiTheme.colors(this);BG=colors.background;PANEL=colors.panel;LINE=colors.line;TEXT=colors.text;MUTED=colors.muted;TEAL=colors.accent;
    }
    private void toggleTheme(){UiTheme.save(this,!UiTheme.isDark(this));configureTheme();buildUi();}
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientDrawable box(int color,int border){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(10));if(border!=0)g.setStroke(dp(1),border);return g;}
    private TextView text(String value,float sp,int color){TextView t=new TextView(this);t.setText(value);t.setTextSize(sp);t.setTextColor(color);t.setFontFeatureSettings("kern");return t;}
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private Button button(String label,Runnable action){
        Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(TEXT);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(dp(12),0,dp(12),0);b.setBackground(box(colors.button,LINE));b.setContentDescription(label);
        b.setOnClickListener(v->{if(!busy&&doc!=null)action.run();});return b;
    }
    private void addButton(LinearLayout parent,String label,Runnable action){Button b=button(label,action);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));p.bottomMargin=dp(8);parent.addView(b,p);}
    private void pair(LinearLayout parent,String a,Runnable one,String b,Runnable two){
        LinearLayout r=row();Button x=button(a,one),y=button(b,two);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(44),1);p.rightMargin=dp(8);r.addView(x,p);r.addView(y,new LinearLayout.LayoutParams(0,dp(44),1));LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(-1,-2);q.bottomMargin=dp(8);parent.addView(r,q);
    }
    private void section(String value){TextView t=text(value.toUpperCase(Locale.US),11,MUTED);t.setLetterSpacing(.1f);t.setPadding(0,dp(16),0,dp(10));toolBody.addView(t);}
    private void note(String value){TextView t=text(value,12,MUTED);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(3),0,dp(10));toolBody.addView(t);}
    private void buildUi(){
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        int lightBars=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        int flags=getWindow().getDecorView().getSystemUiVisibility();
        getWindow().getDecorView().setSystemUiVisibility(UiTheme.isDark(this)?flags&~lightBars:flags|lightBars);
        ImageCanvas previous=canvas;
        root=column();root.setBackgroundColor(BG);root.setPadding(dp(16),dp(8),dp(16),dp(8));
        root.setOnApplyWindowInsetsListener((v,insets)->{root.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(8)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(8)+insets.getSystemWindowInsetBottom());return insets.consumeSystemWindowInsets();});
        LinearLayout head=row();TextView logo=text("F",25,colors.onAccent);logo.setGravity(Gravity.CENTER);logo.setTypeface(null,1);logo.setBackground(box(TEAL,0));head.addView(logo,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout brand=column();brand.setPadding(dp(12),0,0,0);TextView name=text("Fiji Pocket",21,TEXT);name.setTypeface(null,1);brand.addView(name);
        head.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button demos=button("Demos",this::demos),open=button("Open",this::open),export=button("Export",this::exportMenu),more=button("•••",this::menu);more.setContentDescription("More options");
        Button theme=button(UiTheme.isDark(this)?"Light":"Dark",this::toggleTheme);theme.setContentDescription(UiTheme.isDark(this)?"Switch to light theme":"Switch to dark theme");
        demos.setTextColor(TEAL);
        for(Button b:new Button[]{theme,demos,open,export,more}){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));p.leftMargin=dp(8);head.addView(b,p);}export.setBackground(box(TEAL,0));export.setTextColor(colors.onAccent);
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout divider=new LinearLayout(this);divider.setBackgroundColor(LINE);root.addView(divider,new LinearLayout.LayoutParams(-1,dp(1)));
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setIndeterminate(true);progress.setVisibility(busy?View.VISIBLE:View.INVISIBLE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));
        demoBar=row();demoBar.setPadding(dp(12),dp(5),dp(8),dp(5));demoBar.setBackground(box(colors.demo,0));
        demoLabel=text("",12,TEAL);demoLabel.setMaxLines(2);demoBar.addView(demoLabel,new LinearLayout.LayoutParams(0,-2,1));
        for(Button b:new Button[]{button("Guide",this::demoGuide),button("Run demo",this::runDemo),button("Reset",()->loadDemo(BiologyDemos.find(doc.demoId)))}){
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38));p.leftMargin=dp(6);demoBar.addView(b,p);
        }
        root.addView(demoBar,new LinearLayout.LayoutParams(-1,-2));
        Configuration configuration=getResources().getConfiguration();
        boolean wide=configuration.screenWidthDp>=1000||(configuration.screenWidthDp>=720&&configuration.orientation==Configuration.ORIENTATION_LANDSCAPE);
        LinearLayout content=wide?row():column();root.addView(content,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout workspace=column();workspace.setPadding(0,dp(12),wide?dp(16):0,0);
        LinearLayout info=row();LinearLayout names=column();title=text("Opening workspace…",15,TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);names.addView(title);metadata=text("",11,MUTED);metadata.setPadding(0,dp(4),0,0);names.addView(metadata);info.addView(names,new LinearLayout.LayoutParams(0,-2,1));
        Button fit=button("Fit",()->canvas.fit());info.addView(fit,new LinearLayout.LayoutParams(dp(50),dp(38)));workspace.addView(info,new LinearLayout.LayoutParams(-1,dp(54)));
        canvas=new ImageCanvas(this);canvas.whiteIsZero=doc!=null&&doc.whiteIsZero;
        canvas.listener=new ImageCanvas.Listener(){public void changed(){updateRoi();}public void pixel(String value){status.setText(value);}};
        workspace.addView(canvas,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout sliceRow=row();sliceLabel=text("Slice 1 / 1",12,MUTED);sliceRow.addView(sliceLabel,new LinearLayout.LayoutParams(dp(95),-2));sliceBar=new SeekBar(this);sliceRow.addView(sliceBar,new LinearLayout.LayoutParams(0,dp(42),1));
        sliceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){if(doc!=null)save();}public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&!busy&&doc!=null){doc.slice=Math.min(doc.planes.size()-1,value);canvas.particles=Collections.emptyList();canvas.setPlane(doc.current(),false);updateLabels();}}});
        workspace.addView(sliceRow);
        if(wide)content.addView(workspace,new LinearLayout.LayoutParams(0,-1,1));else content.addView(workspace,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout inspector=column();inspector.setPadding(dp(14),dp(12),dp(14),dp(8));inspector.setBackground(box(PANEL,LINE));
        LinearLayout tabs=row();String[] labels={"Process","Analyze","Image"};
        for(int i=0;i<3;i++){final int n=i;Button b=button(labels[i],()->{tab=n;buildUi();});if(tab==i){b.setTextColor(TEAL);b.setBackground(box(colors.selected,0));}LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(42),1);if(i<2)p.rightMargin=dp(4);tabs.addView(b,p);}inspector.addView(tabs);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);toolBody=column();scroll.addView(toolBody);inspector.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(wide)content.addView(inspector,new LinearLayout.LayoutParams(dp(302),-1));else content.addView(inspector,new LinearLayout.LayoutParams(-1,dp(266)));
        status=text(lastAction,11,MUTED);status.setSingleLine(true);status.setEllipsize(android.text.TextUtils.TruncateAt.END);status.setGravity(Gravity.CENTER_VERTICAL);root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        setContentView(root);fillTools();
        if(doc!=null){showImage(previous==null);if(previous!=null){canvas.low=previous.low;canvas.high=previous.high;canvas.lut=previous.lut;canvas.roi=previous.roi;canvas.selectRoi=previous.selectRoi;canvas.particles=previous.particles;canvas.copyViewport(previous);canvas.refresh();updateRoi();}}
    }
    private void fillTools(){
        toolBody.removeAllViews();roiLabel=null;resultLabel=null;
        if(tab==0){
            section("Enhance");pair(toolBody,"Gaussian blur",this::blur,"Median 3 × 3",()->edit("Median filter",Operations::median));
            pair(toolBody,"Find edges",()->edit("Sobel edges",Operations::edges),"Invert",()->edit("Invert",Operations::invert));
            addButton(toolBody,"Convert to grayscale",()->edit("Grayscale",Operations::grayscale));
            addButton(toolBody,"Extract RGB channel…",()->{
                if(doc.current().type!=Plane.RGB){error("Open an RGB image to extract a color channel.");return;}
                new AlertDialog.Builder(this).setTitle("Extract RGB channel").setItems(new String[]{"Red","Green","Blue"},(d,c)->edit(new String[]{"Red","Green","Blue"}[c]+" channel",p->Operations.channel(p,c))).show();
            });
            section("Segment");Button t=button("Threshold…",this::thresholdDialog);t.setTextColor(TEAL);toolBody.addView(t,new LinearLayout.LayoutParams(-1,dp(46)));
            note("Preview foreground, choose an ImageJ method, then create a binary mask.");pair(toolBody,"Erode mask",()->edit("Erode",p->Operations.morph(p,false)),"Dilate mask",()->edit("Dilate",p->Operations.morph(p,true)));
            section("History");addButton(toolBody,"Undo last edit",this::undo);
            note(history.isEmpty()?"Your source file stays untouched. Export creates a new file.":String.join("\n",history.subList(Math.max(0,history.size()-4),history.size())));
            note("Edits apply to every slice in the open stack.");
        }else if(tab==1){
            section("Region of interest");pair(toolBody,"Select ROI",()->{canvas.selectRoi=true;lastAction="Drag a rectangle on the image to select an ROI";status.setText(lastAction);},"Clear ROI",()->{canvas.selectRoi=false;canvas.clearRoi();});
            roiLabel=text("Whole image selected",12,MUTED);toolBody.addView(roiLabel);section("Measure");addButton(toolBody,"Measure current slice",this::measure);
            addButton(toolBody,"Analyze particles…",this::particlesDialog);note("Particles use 8-connected white foreground. Create a binary mask with Threshold first.");
            section("Results");resultLabel=text(result,14,TEXT);resultLabel.setTextIsSelectable(true);resultLabel.setPadding(0,0,0,dp(12));toolBody.addView(resultLabel);
            pair(toolBody,"View results",this::results,"Export CSV",()->export("csv"));addButton(toolBody,"Set pixel scale…",this::scaleDialog);
        }else{
            section("Display");pair(toolBody,"Auto contrast",()->{canvas.autoRange();canvas.refresh();status.setText("Display range updated · pixel values unchanged");},"Display range…",this::displayDialog);
            addButton(toolBody,"Color map…",()->new AlertDialog.Builder(this).setTitle("Display color map").setItems(new String[]{"Gray","Green","Fire"},(d,which)->{canvas.lut=new String[]{"Gray","Green","Fire"}[which];canvas.refresh();}).show());note("Contrast and color maps affect the view and PNG export. TIFF retains pixel values.");
            addButton(toolBody,"Histogram",()->new AlertDialog.Builder(this).setTitle("Histogram · current slice").setView(new HistogramView(this,doc.current())).setPositiveButton("Done",null).show());
            section("Geometry");pair(toolBody,"Rotate 90°",this::rotate,"Flip horizontal",()->edit("Flip horizontal",Operations::flip));addButton(toolBody,"Crop to ROI",this::crop);
            section("Stack");pair(toolBody,"Maximum Z",()->project(true),"Mean Z",()->project(false));
            section("Workspace");addButton(toolBody,"Biology demos",this::demos);addButton(toolBody,"Load sample stack",this::sample);addButton(toolBody,"About / formats",this::about);
        }
        updateRoi();
    }
    private void updateLabels(){if(doc==null)return;Plane p=doc.current();title.setText(doc.name);metadata.setText(p.width+" × "+p.height+"  ·  "+p.label()+"  ·  "+doc.planes.size()+" slice"+(doc.planes.size()==1?"":"s")+"  ·  "+fmt(doc.pixelWidth)+" "+doc.unit+"/px");sliceLabel.setText("Slice "+(doc.slice+1)+" / "+doc.planes.size());sliceBar.setMax(Math.max(1,doc.planes.size()-1));sliceBar.setProgress(doc.slice);sliceBar.setEnabled(!busy&&doc.planes.size()>1);BiologyDemos.Demo demo=BiologyDemos.find(doc.demoId);demoBar.setVisibility(demo==null?View.GONE:View.VISIBLE);if(demo!=null)demoLabel.setText("BIOLOGY DEMO  ·  "+demo.title);}
    private void showImage(boolean reset){if(doc==null)return;canvas.whiteIsZero=doc.whiteIsZero;canvas.threshold=null;canvas.particles=Collections.emptyList();canvas.setPlane(doc.current(),reset);updateLabels();updateRoi();}
    private void updateRoi(){if(roiLabel==null||canvas==null)return;Operations.Region r=canvas.roi;roiLabel.setText(r==null?"Whole image selected":"ROI  "+r.w+" × "+r.h+" px  at ("+r.x+", "+r.y+")");}
    private <T> void run(String message,Work<T> work,Result<T> done){
        if(busy)return;busy=true;progress.setVisibility(View.VISIBLE);status.setText(message);if(sliceBar!=null)sliceBar.setEnabled(false);
        long started=System.nanoTime();
        worker.execute(()->{try{T value=work.run();handler.post(()->{if(isDestroyed())return;busy=false;progress.setVisibility(View.INVISIBLE);done.accept(value);lastAction=message.replace("…","")+" · "+((System.nanoTime()-started)/1_000_000)+" ms";status.setText(lastAction);updateLabels();});}
        catch(Exception|OutOfMemoryError error){handler.post(()->{if(isDestroyed())return;busy=false;progress.setVisibility(View.INVISIBLE);status.setText("Operation stopped · image unchanged");updateLabels();error(error instanceof OutOfMemoryError?"Not enough memory. Try a smaller image or stack.":error.getMessage());});}});
    }
    private void error(String message){new AlertDialog.Builder(this).setTitle("Could not complete action").setMessage(message==null?"Unknown image error.":message).setPositiveButton("OK",null).show();}
    private void save(){if(doc==null)return;Document saved=doc.snapshot();worker.execute(()->{try{ImageFiles.saveSession(this,saved);}catch(Exception e){android.util.Log.w("FijiPocket","Session save failed",e);handler.post(()->{if(!isDestroyed())status.setText("Working session could not be saved. Use Export to keep your image.");});}});}
    private void edit(String name,Transform transform){
        Document before=doc.snapshot();run(name+"…",()->{Document next=before.snapshot();next.planes.clear();for(Plane p:before.planes)next.planes.add(transform.apply(p));next.validate();if(name.startsWith("Threshold"))next.whiteIsZero=false;return next;},next->{undo=before;doc=next;history.add(name);showImage(true);fillTools();save();});
    }
    private void undo(){if(undo==null){status.setText("Nothing to undo yet");return;}Document before=doc;doc=undo;undo=before;history.add("Undo / redo");showImage(true);fillTools();save();status.setText("Last edit restored. Tap again to redo.");}
    private void open(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/tiff","application/octet-stream"});startActivityForResult(i,10);}
    private void sample(){new AlertDialog.Builder(this).setTitle("Open sample stack?").setMessage("A synthetic 16-bit stack with 18 nuclei per slice and 0.5 µm pixels. Your current image remains available through Undo.").setNegativeButton("Cancel",null).setPositiveButton("Load sample",(d,w)->{undo=doc;doc=Document.sample();history.clear();showImage(true);fillTools();save();}).show();}
    private void menu(){new AlertDialog.Builder(this).setTitle("Fiji Pocket").setItems(new String[]{"Biology demos","Load sample stack","Undo last edit","About / supported formats"},(d,i)->{if(i==0)demos();else if(i==1)sample();else if(i==2)undo();else about();}).show();}
    private void demos(){
        final Dialog dialog=new Dialog(this);galleryDialog=dialog;LinearLayout layout=column();layout.setPadding(dp(22),dp(16),dp(22),dp(12));layout.setBackground(box(BG,LINE));
        LinearLayout heading=row();TextView label=text("Biology demos",24,TEXT);label.setTypeface(null,1);heading.addView(label,new LinearLayout.LayoutParams(0,-2,1));heading.addView(button("Close",dialog::dismiss));layout.addView(heading);
        TextView intro=text("Real ImageJ samples · bundled for offline exploration",13,MUTED);intro.setPadding(0,dp(6),0,dp(16));layout.addView(intro);
        ScrollView scroll=new ScrollView(this);LinearLayout cards=column();scroll.addView(cards);layout.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        int columns=getResources().getConfiguration().screenWidthDp>=720?2:1;
        for(int i=0;i<BiologyDemos.ALL.length;i+=columns){
            LinearLayout line=row();line.setGravity(Gravity.TOP);
            for(int j=i;j<Math.min(i+columns,BiologyDemos.ALL.length);j++){
                BiologyDemos.Demo demo=BiologyDemos.ALL[j];LinearLayout card=column();card.setPadding(dp(14),dp(12),dp(14),dp(12));card.setBackground(box(PANEL,LINE));
                ImageView preview=new ImageView(this);preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackgroundColor(colors.workspace);preview.setContentDescription(demo.title+" sample preview");
                try(InputStream in=getAssets().open("demos/"+demo.id+"-thumb.jpg")){preview.setImageBitmap(BitmapFactory.decodeStream(in));}catch(IOException e){android.util.Log.w("FijiPocket","Demo preview unavailable",e);}
                card.addView(preview,new LinearLayout.LayoutParams(-1,dp(110)));
                TextView name=text(demo.title,18,TEXT);name.setTypeface(null,1);name.setPadding(0,dp(10),0,dp(4));card.addView(name);
                TextView sub=text(demo.subtitle,12,TEAL);sub.setPadding(0,0,0,dp(10));card.addView(sub);
                addButton(card,"Open "+demo.title,()->{dialog.dismiss();loadDemo(demo);});
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.bottomMargin=dp(12);if(j>i)p.leftMargin=dp(12);line.addView(card,p);
            }cards.addView(line);
        }
        TextView foot=text("Open a sample, then tap Run demo for a guided analysis. Guide includes methods and image credits.",12,MUTED);foot.setPadding(0,dp(8),0,0);layout.addView(foot);
        dialog.setContentView(layout);dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawableResource(android.R.color.transparent);window.setLayout(Math.min(dp(980),getResources().getDisplayMetrics().widthPixels-dp(32)),Math.min(dp(790),getResources().getDisplayMetrics().heightPixels-dp(80)));}
    }
    private void loadDemo(BiologyDemos.Demo demo){
        if(demo==null)return;
        run("Opening "+demo.title+"…",()->DemoFiles.read(this,demo),next->{undo=doc;doc=next;history.clear();canvas.lut="Gray";canvas.selectRoi=false;showImage(true);fillTools();save();});
    }
    private void demoGuide(){
        BiologyDemos.Demo demo=BiologyDemos.find(doc.demoId);if(demo==null)return;
        ScrollView scroll=new ScrollView(this);TextView body=text(demo.description+"\n\n"+demo.steps+"\n\nRun demo starts from the bundled original each time. Use Undo to return to your previous image. Results can be exported as TIFF, PNG or CSV.\n\n"+demo.credit+"\n\nhttps://imagej.net/ij/images/"+demo.source,14,TEXT);body.setPadding(dp(24),dp(12),dp(24),dp(12));body.setTextIsSelectable(true);body.setLineSpacing(dp(4),1);scroll.addView(body);
        new AlertDialog.Builder(this).setTitle(demo.title+" · guide").setView(scroll).setNegativeButton("Close",null).setPositiveButton("Run demo",(d,w)->runDemo()).setNeutralButton("Reset original",(d,w)->loadDemo(demo)).show();
    }
    private void runDemo(){
        BiologyDemos.Demo demo=BiologyDemos.find(doc.demoId);if(demo==null)return;
        Document before=doc.snapshot();
        run("Running "+demo.title+" demo…",()->BiologyDemos.analyze(DemoFiles.read(this,demo)),outcome->{
            undo=before;doc=outcome.document;history.add("Biology demo: "+demo.title);result=outcome.summary;
            for(Operations.Particle p:outcome.particles){double area=p.count*doc.pixelWidth*doc.pixelHeight;appendResult("DemoParticle",doc,new Operations.Region(p.left,p.top,p.right-p.left+1,p.bottom-p.top+1),p.count,area,255,0,255,255,p.count*255.0,p.cx*doc.pixelWidth,p.cy*doc.pixelHeight);}
            persistResults();tab=1;buildUi();canvas.lut="Gray";canvas.selectRoi=false;showImage(true);canvas.particles=outcome.particles;canvas.roi=outcome.region;updateRoi();canvas.invalidate();save();
            new AlertDialog.Builder(this).setTitle(demo.title+" · demo complete").setMessage(outcome.summary+"\n\n"+demo.steps).setPositiveButton("Explore result",null).setNeutralButton("View original",(d,w)->loadDemo(demo)).show();
        });
    }
    private void exportMenu(){new AlertDialog.Builder(this).setTitle("Export a new file").setItems(new String[]{"TIFF · all slices, full precision","TIFF · current slice, full precision","PNG · current display, 8-bit","CSV · measurement results"},(d,i)->export(new String[]{"tiff","slice","png","csv"}[i])).show();}
    private void export(String kind){
        if(kind.equals("csv")&&csv.toString().split("\n").length<2){error("Measure an image or analyze particles before exporting results.");return;}
        Document output=doc.snapshot();double lo=canvas.low,hi=canvas.high;String lut=canvas.lut,table=csv.toString();boolean inverse=canvas.whiteIsZero;
        String base=doc.name.replaceAll("\\.[^.]+$","").replaceAll("[^\\p{L}\\p{N} _-]","_");
        run("Preparing export…",()->{
            File staged=new File(getFilesDir(),"pending-export"),temp=new File(getFilesDir(),"pending-export.tmp");
            try{
                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(temp))){
                    if(kind.equals("csv"))out.write(table.getBytes("UTF-8"));
                    else if(kind.equals("png")){Plane p=output.current();Bitmap b=ImageCanvas.render(p,p.width,p.height,lo,hi,lut,inverse,null,false);try{if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG encoder failed.");}finally{b.recycle();}}
                    else TiffIO.write(output,out,kind.equals("tiff"));
                }
                if(!temp.renameTo(staged))throw new IOException("Could not stage the export.");
            }finally{temp.delete();}
            return true;
        },ok->{
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(kind.equals("png")?"image/png":kind.equals("csv")?"text/csv":"image/tiff");
            i.putExtra(Intent.EXTRA_TITLE,base+(kind.equals("csv")?"-results.csv":kind.equals("png")?"-view.png":kind.equals("slice")?"-slice.tif":"-processed.tif"));startActivityForResult(i,11);
        });
    }
    private void whenReady(Runnable action){if(isDestroyed())return;if(busy)handler.postDelayed(()->whenReady(action),80);else action.run();}
    @Override protected void onActivityResult(int request,int code,Intent data){super.onActivityResult(request,code,data);if(code!=RESULT_OK||data==null||data.getData()==null){if(request==11)new File(getFilesDir(),"pending-export").delete();return;}Uri uri=data.getData();
        if(request==10)whenReady(()->run("Importing image…",()->ImageFiles.read(this,uri),next->{undo=doc;doc=next;history.clear();showImage(true);fillTools();save();}));
        else if(request==11)whenReady(()->run("Saving export…",()->{
            File staged=new File(getFilesDir(),"pending-export");
            if(!staged.exists())throw new IOException("Prepared export is no longer available. Please export the image again.");
            try(InputStream in=new BufferedInputStream(new FileInputStream(staged));OutputStream out=getContentResolver().openOutputStream(uri,"w")){
                if(out==null)throw new IOException("Cannot write to the selected destination.");
                byte[] bytes=new byte[65536];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            }
            staged.delete();return true;
        },ok->new AlertDialog.Builder(this).setTitle("Export complete").setMessage("Saved "+ImageFiles.name(this,uri)).setPositiveButton("Done",null).show()));
    }
    private EditText number(String value){EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);return e;}
    private LinearLayout form(){LinearLayout l=column();l.setPadding(dp(24),dp(8),dp(24),dp(8));return l;}
    private double read(EditText e){double v=Double.parseDouble(e.getText().toString());if(!Double.isFinite(v))throw new IllegalArgumentException("Enter a finite number.");return v;}
    private void blur(){LinearLayout f=form();f.addView(text("Sigma in pixels (0.1–20)",14,MUTED));EditText input=number("2");input.setContentDescription("Gaussian sigma");f.addView(input);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Gaussian blur").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double sigma=read(input);if(sigma<.1||sigma>20)throw new IllegalArgumentException("Sigma must be 0.1–20.");dialog.dismiss();edit("Gaussian σ="+fmt(sigma),p->Operations.gaussian(p,sigma));}catch(Exception e){input.setError(e.getMessage());}}));dialog.show();}
    private void thresholdDialog(){
        if(doc.current().type==Plane.RGB){error("Convert to grayscale before thresholding.");return;}
        LinearLayout f=form();Spinner methods=new Spinner(this);String[] names={"Otsu","Default","Li","Triangle","Yen"};methods.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names));f.addView(methods);
        EditText value=number(fmt(Operations.autoThreshold(doc.current(),"Otsu")));value.setContentDescription("Threshold value");f.addView(value);
        CheckBox dark=new CheckBox(this);dark.setText("Dark foreground (values ≤ threshold)");f.addView(dark);
        TextView help=text("Selected foreground is teal in the preview. Applying creates an 8-bit mask for every slice.",12,MUTED);f.addView(help);
        Runnable preview=()->{try{canvas.threshold=read(value);canvas.dark=dark.isChecked();canvas.refresh();}catch(Exception ignored){}};
        value.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int t){}public void onTextChanged(CharSequence s,int a,int b,int c){preview.run();}public void afterTextChanged(android.text.Editable e){}});
        dark.setOnCheckedChangeListener((b,c)->preview.run());
        methods.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){value.setText(fmt(Operations.autoThreshold(doc.current(),names[pos])));}});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Threshold").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Apply mask",null).create();
        dialog.setOnDismissListener(d->{canvas.threshold=null;canvas.refresh();});
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double t=read(value);boolean black=dark.isChecked();dialog.dismiss();edit("Threshold "+fmt(t),p->Operations.threshold(p,t,black));}catch(Exception e){value.setError("Enter a valid threshold.");}}));dialog.show();preview.run();
    }
    private void displayDialog(){LinearLayout f=form();EditText low=number(fmt(canvas.low)),high=number(fmt(canvas.high));low.setHint("Minimum");high.setHint("Maximum");f.addView(text("Display minimum / maximum",14,MUTED));f.addView(low);f.addView(high);AlertDialog d=new AlertDialog.Builder(this).setTitle("Display range").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(b->{try{double a=read(low),z=read(high);if(z<=a)throw new IllegalArgumentException("Maximum must exceed minimum.");canvas.low=a;canvas.high=z;canvas.refresh();d.dismiss();}catch(Exception e){high.setError(e.getMessage());}}));d.show();}
    private void scaleDialog(){LinearLayout f=form();EditText x=number(fmt(doc.pixelWidth)),y=number(fmt(doc.pixelHeight)),unit=new EditText(this);unit.setSingleLine(true);unit.setText(doc.unit);unit.setContentDescription("Scale unit");f.addView(text("Pixel width, pixel height, and unit",14,MUTED));x.setContentDescription("Pixel width");y.setContentDescription("Pixel height");f.addView(x);f.addView(y);f.addView(unit);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Set pixel scale").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Apply",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double a=read(x),z=read(y);String u=unit.getText().toString().trim();if(a<=0||z<=0||a>1e9||z>1e9||u.isEmpty()||u.length()>16||u.contains("\n"))throw new IllegalArgumentException("Use positive pixel sizes and a short unit.");undo=doc.snapshot();doc.pixelWidth=a;doc.pixelHeight=z;doc.unit=u;updateLabels();save();dialog.dismiss();}catch(Exception e){x.setError(e.getMessage());}}));dialog.show();}
    private void measure(){Document d=doc.snapshot();Operations.Region r=Operations.region(d.current(),canvas.roi);run("Measuring ROI…",()->Operations.measure(d.current(),r,d.pixelWidth,d.pixelHeight),s->{result=String.format(Locale.US,"Area  %s %s²\nMean  %s    SD  %s\nMin  %s    Max  %s\n%d finite pixels",fmt(s.area),d.unit,fmt(s.mean),fmt(s.std),fmt(s.min),fmt(s.max),s.count);appendResult("ROI",d,r,s.count,s.area,s.mean,s.std,s.min,s.max,s.sum,Double.NaN,Double.NaN);persistResults();fillTools();});}
    private void appendResult(String kind,Document d,Operations.Region r,long count,double area,double mean,double std,double min,double max,double integrated,double cx,double cy){csv.append(String.format(Locale.US,"%s,%s,%d,%d,%d,%d,%d,%d,%.9g,%s,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g,%.9g\n",kind,quoted(d.name),d.slice+1,r.x,r.y,r.w,r.h,count,area,quoted(d.unit),mean,std,min,max,integrated,cx,cy));}
    private void persistResults(){String value=csv.toString();worker.execute(()->{try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"results.csv"))){out.write(value.getBytes("UTF-8"));}catch(Exception e){android.util.Log.w("FijiPocket","Results save failed",e);}});}
    private String quoted(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    private void particlesDialog(){if(!Operations.isBinary(doc.current())){error("Create a binary mask with Threshold before analyzing particles.");return;}LinearLayout f=form();f.addView(text("Minimum area in pixels",14,MUTED));EditText size=number("20");size.setContentDescription("Minimum particle area");f.addView(size);CheckBox edges=new CheckBox(this);edges.setText("Exclude objects touching ROI / image edges");f.addView(edges);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Analyze particles").setView(f).setNegativeButton("Cancel",null).setPositiveButton("Analyze",null).create();dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(b->{try{double n=read(size);if(n<1||n>8_388_608||n!=(int)n)throw new IllegalArgumentException("Enter a whole area of at least 1 pixel.");Document d=doc.snapshot();Operations.Region r=canvas.roi;boolean exclude=edges.isChecked();dialog.dismiss();run("Counting particles…",()->Operations.particles(d.current(),r,(int)n,exclude),list->{canvas.particles=list;canvas.invalidate();double area=0;for(Operations.Particle p:list){double a=p.count*d.pixelWidth*d.pixelHeight;area+=a;appendResult("Particle",d,new Operations.Region(p.left,p.top,p.right-p.left+1,p.bottom-p.top+1),p.count,a,255,0,255,255,p.count*255.0,p.cx*d.pixelWidth,p.cy*d.pixelHeight);}persistResults();result=list.size()+" particles\nTotal area  "+fmt(area)+" "+d.unit+"²\n8-connected · white foreground";fillTools();});}catch(Exception e){size.setError(e.getMessage());}}));dialog.show();}
    private void results(){ScrollView scroll=new ScrollView(this);TextView t=text(csv.toString(),12,TEXT);t.setTypeface(android.graphics.Typeface.MONOSPACE);t.setTextIsSelectable(true);t.setPadding(dp(16),dp(16),dp(16),dp(16));scroll.addView(t);new AlertDialog.Builder(this).setTitle("Measurement results").setView(scroll).setNegativeButton("Close",null).setPositiveButton("Export CSV",(d,w)->export("csv")).setNeutralButton("Clear",(d,w)->{csv.setLength(csv.indexOf("\n")+1);result="No measurements yet";persistResults();fillTools();}).show();}
    private void rotate(){Document before=doc.snapshot();run("Rotating stack…",()->{Document next=before.snapshot();next.planes.clear();for(Plane p:before.planes)next.planes.add(Operations.rotate(p));next.pixelWidth=before.pixelHeight;next.pixelHeight=before.pixelWidth;return next;},next->{undo=before;doc=next;history.add("Rotate 90°");showImage(true);save();fillTools();});}
    private void crop(){if(canvas.roi==null){error("Choose Analyze → Select ROI, then drag a rectangle on the image.");return;}Operations.Region r=canvas.roi;edit("Crop ROI",p->Operations.crop(p,r));}
    private void project(boolean maximum){if(doc.planes.size()<2){error("Open a stack with at least two slices first.");return;}Document before=doc.snapshot();run("Projecting stack…",()->{Plane p=Operations.projection(before,maximum);Document next=before.snapshot();next.planes.clear();next.planes.add(p);next.slice=0;next.name=(maximum?"MAX_":"MEAN_")+before.name;return next;},next->{undo=before;doc=next;history.add(maximum?"Maximum projection":"Mean projection");showImage(true);save();fillTools();});}
    private void about(){String message="A compact, independent Android port of selected Fiji / ImageJ functions.\n\nImageJ TIFF codecs, automatic threshold methods, and Gaussian kernels run directly on your device. No account or network access.\n\nImport: PNG, JPEG, WebP; strip-based TIFF (8-bit, unsigned 16-bit, float32, RGB), including supported LZW, Deflate and PackBits. Up to 8 MP per plane and 32 MB decoded per stack.\n\nExport: full-precision TIFF, displayed PNG, results CSV. TIFF calibration is preserved. PNG/JPEG imports use Android's 8-bit color decoder. Embedded desktop ROIs, overlays and private metadata are omitted.\n\nThis build does not run Fiji desktop plugins, macros, Bio-Formats, BigTIFF, tiled TIFF, or multidimensional channel metadata. TIFF pages appear as a slice stack.\n\nSource and license notices are supplied with this build. GPL-3.0-or-later for the Android wrapper; ImageJ notices retained. Independent project, not an official Fiji release.";new AlertDialog.Builder(this).setTitle("Fiji Pocket 0.3").setMessage(message).setPositiveButton("Done",null).setNeutralButton("Licenses",(d,w)->{try(InputStream in=getAssets().open("THIRD_PARTY_NOTICES.txt")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);new AlertDialog.Builder(this).setTitle("Open-source notices").setMessage(out.toString("UTF-8")).setPositiveButton("Done",null).show();}catch(Exception e){error(e.getMessage());}}).show();}
    private static String fmt(double value){if(!Double.isFinite(value))return "NaN";if(value==Math.rint(value)&&Math.abs(value)<1e9)return String.format(Locale.US,"%.0f",value);return String.format(Locale.US,"%.5g",value);}
}
