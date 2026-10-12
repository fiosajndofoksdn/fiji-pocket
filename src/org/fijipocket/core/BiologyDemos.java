package org.fijipocket.core;

import java.util.*;

/** Small, explicit examples built from the same operations exposed in the editor. */
public final class BiologyDemos {
    public static final class Demo {
        public final String id,title,subtitle,description,steps,credit,source;
        private Demo(String id,String title,String subtitle,String description,String steps,String credit,String source) {
            this.id=id;this.title=title;this.subtitle=subtitle;this.description=description;
            this.steps=steps;this.credit=credit;this.source=source;
        }
    }
    public static final Demo[] ALL={
        new Demo("cells","Fluorescent cells","Nuclei · actin · microtubules",
            "Bovine pulmonary artery endothelial cells: blue DAPI marks nuclei, green marks tubulin, and red marks F-actin.",
            "1. Use the blue (DAPI) channel above y=470 px, excluding the embedded caption.\n2. Smooth with Gaussian σ=1 px.\n3. Apply an Otsu threshold.\n4. Label regions ≥100 px; omit ROI-edge objects.",
            "ImageJ Open Samples · Molecular Probes demo slide. The three original 8-bit channels are packed into RGB with unchanged values.",
            "FluorescentCells.zip"),
        new Demo("brain","Fruit-fly brain","Confocal microscopy · 57 slices",
            "Explore a Drosophila brain through 57 optical sections. Green is nc82 antibody staining; red is the GAL4 driver line 78y expression pattern.",
            "1. Browse depth with the slice slider.\n2. Isolate the green channel.\n3. Take the maximum at every pixel across all 57 slices.",
            "Benjamin Schmid · Heisenberg lab, Würzburg, Germany, via ImageJ Open Samples. This upstream sample was already smoothed and reduced 4× per dimension; all its slices and XY calibration are retained here.",
            "flybrain.zip"),
        new Demo("embryos","Embryos","Bright-field image · segmentation",
            "Separate dark embryo regions from a light background and inspect the labeled objects. The image includes an original scale bar; this demo reports pixel areas.",
            "1. Convert to grayscale.\n2. Smooth with Gaussian σ=2 px.\n3. Use Otsu with dark foreground.\n4. Label regions ≥2,000 px; omit image-edge objects.",
            "ImageJ Open Samples · embryos.jpg. Full 1600 × 1200 image, decoded from the upstream JPEG without resizing. Creator not named in the sample listing.",
            "embryos.jpg"),
        new Demo("leaf","Leaf structure","Plant biology · edge detection",
            "Inspect a variegated leaf and its veins. Smoothing and edge detection highlight boundaries and internal texture. The original ruler remains part of the image.",
            "1. Convert to grayscale.\n2. Smooth with Gaussian σ=1 px.\n3. Apply the Sobel edge filter.\n4. Try an ROI measurement or export the result.",
            "ImageJ Open Samples · leaf.jpg. Full 507 × 446 image, decoded from the upstream JPEG without resizing. Creator not named in the sample listing.",
            "leaf.jpg")
    };
    public static Demo find(String id){for(Demo d:ALL)if(d.id.equals(id))return d;return null;}
    public static final class Outcome {
        public final Document document;
        public final List<Operations.Particle> particles;
        public final String summary;
        public final Operations.Region region;
        Outcome(Document d,List<Operations.Particle> p,String s,Operations.Region r){document=d;particles=p;summary=s;region=r;}
    }
    public static Outcome analyze(Document original) {
        Demo demo=find(original.demoId);
        if(demo==null)throw new IllegalArgumentException("Open a biology demo first.");
        Document result=original.snapshot();result.planes.clear();result.slice=0;result.whiteIsZero=false;
        List<Operations.Particle> particles=Collections.emptyList();String summary;Operations.Region region=null;
        if(demo.id.equals("brain")){
            Document channel=original.snapshot();channel.planes.clear();
            for(Plane p:original.planes)channel.planes.add(Operations.channel(p,1));
            result.planes.add(Operations.projection(channel,true));
            summary="Maximum projection of the green channel across "+original.planes.size()+" slices. Each pixel shows the brightest signal along depth. XY calibration is retained.";
        }else if(demo.id.equals("leaf")){
            result.planes.add(Operations.edges(Operations.gaussian(Operations.grayscale(original.current()),1)));
            summary="Sobel edges after Gaussian σ=1 px smoothing. Bright pixels mark rapid intensity changes, including veins, leaf outline, texture and the ruler.";
        }else{
            boolean cells=demo.id.equals("cells");
            Plane gray=cells?Operations.channel(original.current(),2):Operations.grayscale(original.current());
            int fullWidth=gray.width,fullHeight=gray.height;
            if(cells){region=new Operations.Region(0,0,fullWidth,470);gray=Operations.crop(gray,region);}
            Plane smooth=Operations.gaussian(gray,cells?1:2);
            double threshold=Operations.autoThreshold(smooth,"Otsu");
            Plane mask=Operations.threshold(smooth,threshold,!cells);
            if(cells){Plane full=Plane.empty(fullWidth,fullHeight,Plane.U8);System.arraycopy(mask.pixels,0,full.pixels,0,mask.size());mask=full;}
            result.planes.add(mask);
            particles=Operations.particles(mask,region,cells?100:2000,true);
            summary=particles.size()+" segmented regions · Otsu "+(int)threshold+"\n"+
                "Minimum area "+(cells?"100":"2,000")+" px; "+(cells?"caption below y=470 and ROI-edge objects excluded.":"image-edge objects excluded.")+" Touching objects may merge; these labels are an example segmentation, not a verified biological count.";
        }
        result.name=demo.title+" · "+(demo.id.equals("brain")?"maximum":demo.id.equals("leaf")?"edges":"mask")+".tif";
        result.validate();return new Outcome(result,particles,summary,region);
    }
}
