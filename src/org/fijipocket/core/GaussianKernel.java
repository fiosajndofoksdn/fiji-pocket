// Android port: see THIRD_PARTY_NOTICES.txt and port_imagej.py.
package org.fijipocket.core;
// Kernels from ImageJ GaussianBlur, Michael Schmid / Stephan Saalfeld.
// Android adapter: raw float planes, no desktop plugin or AWT dependency.
public final class GaussianKernel {
    public static void blur(float[] pixels, int width, int height, double sigma) {
        if (!Double.isFinite(sigma) || sigma <= 0 || sigma > 20)
            throw new IllegalArgumentException("Sigma must be between 0 and 20 pixels");
        float[] line = new float[width];
        float[][] kernel = makeGaussianKernel(sigma, 0.0002, Math.max(width,height));
        for (int y=0;y<height;y++) {
            System.arraycopy(pixels,y*width,line,0,width);
            convolveLine(line,pixels,kernel,0,width,0,width,y*width,1);
        }
        line = new float[height];
        for (int x=0;x<width;x++) {
            for (int y=0;y<height;y++) line[y]=pixels[y*width+x];
            convolveLine(line,pixels,kernel,0,height,0,height,x,width);
        }
    }
final static private void convolveLine( final float[] input, final float[] pixels, final float[][] kernel, final int readFrom,
            final int readTo, final int writeFrom, final int writeTo, final int point0, final int pointInc) {
        final int length = input.length;
        final float first = input[0];                 //out-of-edge pixels are replaced by nearest edge pixels
        final float last = input[length-1];
        final float[] kern = kernel[0];               //the kernel itself
        final float kern0 = kern[0];
        final float[] kernSum = kernel[1];            //the running sum over the kernel
        final int kRadius = kern.length;
        final int firstPart = kRadius < length ? kRadius : length;
        int p = point0 + writeFrom*pointInc;
        int i = writeFrom;
        for (; i<firstPart; i++,p+=pointInc) {  //while the sum would include pixels < 0
            float result = input[i]*kern0;
            result += kernSum[i]*first;
            if (i+kRadius>length) result += kernSum[length-i-1]*last;
            for (int k=1; k<kRadius; k++) {
                float v = 0;
                if (i-k >= 0) v += input[i-k];
                if (i+k<length) v+= input[i+k];
                result += kern[k] * v;
            }
            pixels[p] = result;
        }
        final int iEndInside = length-kRadius<writeTo ? length-kRadius : writeTo;
        for (;i<iEndInside;i++,p+=pointInc) {   //while only pixels within the line are be addressed (the easy case)
            float result = input[i]*kern0;
            for (int k=1; k<kRadius; k++)
                result += kern[k] * (input[i-k] + input[i+k]);
            pixels[p] = result;
        }
        for (; i<writeTo; i++,p+=pointInc) {    //while the sum would include pixels >= length 
            float result = input[i]*kern0;
            if (i<kRadius) result += kernSum[i]*first;
            if (i+kRadius>=length) result += kernSum[length-i-1]*last;
            for (int k=1; k<kRadius; k++) {
                float v = 0;
                if (i-k >= 0) v += input[i-k];
                if (i+k<length) v+= input[i+k];
                result += kern[k] * v;
            }
            pixels[p] = result;
        }
    }
public static float[][] makeGaussianKernel(final double sigma, final double accuracy, int maxRadius) {
        int kRadius = (int)Math.ceil(sigma*Math.sqrt(-2*Math.log(accuracy)))+1;
        if (maxRadius < 50) maxRadius = 50;         // too small maxRadius would result in inaccurate sum.
        if (kRadius > maxRadius) kRadius = maxRadius;
        float[][] kernel = new float[2][kRadius];
        for (int i=0; i<kRadius; i++)               // Gaussian function
            kernel[0][i] = (float)(Math.exp(-0.5*i*i/sigma/sigma));
        if (kRadius < maxRadius && kRadius > 3) {   // edge correction
            double sqrtSlope = Double.MAX_VALUE;
            int r = kRadius;
            while (r > kRadius/2) {
                r--;
                double a = Math.sqrt(kernel[0][r])/(kRadius-r);
                if (a < sqrtSlope)
                    sqrtSlope = a;
                else
                    break;
            }
            for (int r1 = r+2; r1 < kRadius; r1++)
                kernel[0][r1] = (float)((kRadius-r1)*(kRadius-r1)*sqrtSlope*sqrtSlope);
        }
        double sum;                                 // sum over all kernel elements for normalization
        if (kRadius < maxRadius) {
            sum = kernel[0][0];
            for (int i=1; i<kRadius; i++)
                sum += 2*kernel[0][i];
        } else
            sum = sigma * Math.sqrt(2*Math.PI);
        
        double rsum = 0.5 + 0.5*kernel[0][0]/sum;
        for (int i=0; i<kRadius; i++) {
            double v = (kernel[0][i]/sum);
            kernel[0][i] = (float)v;
            rsum -= v;
            kernel[1][i] = (float)rsum;
            //IJ.log("k["+i+"]="+(float)v+" sum="+(float)rsum);
        }
        return kernel;
    }
}
