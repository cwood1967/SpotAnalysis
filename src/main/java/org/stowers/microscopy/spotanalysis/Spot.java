package org.stowers.microscopy.spotanalysis;

/**
 * Created by cjw on 5/24/16.
 */

import ij.ImagePlus;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import jalgs.jfit.gridfit;
import jalgs.jfit.gausfunc;
import jalgs.jfit.NLLSfitinterface_v2;

import org.apache.commons.math3.fitting.leastsquares.*;
import org.apache.commons.math3.linear.*;
import org.apache.commons.math3.util.Pair;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;

public class Spot  {

    int id;
    int x;
    int y;
    int xyindex;
    int patchSize;

    int theC;
    int theZ;

    double pixelWidth = 1.0;
    float[] pixels;
    ImageProcessor ip;

    SpotPatch patch = null;
    float[] patchPixels;

    double patchMin;
    double patchMax;
    double patchStdDev;

    double stdDevMin;
    double xcMin;
    double ycMin;

    double stdDevMax;
    double xcMax;
    double ycMax;

    double meanIntensity;
    double channel;
    double slice;

    double fitResult[] = null;

    boolean canfit = true;

    public Spot(int x, int y, ImageProcessor ip) {
        this(0, x, y, ip, 0, 1, 1);
    }

    public Spot(int id, int x, int y, ImageProcessor ip, int patchSize, int theC, int theZ) {
        //theZ and theC are one based for image j
        this.id = id;
        this.x = x;
        this.y = y;
        this.theC = theC;
        this.theZ = theZ;
        this.xyindex = y*ip.getWidth() + x;
        this.ip = ip;

        if ((x < patchSize/2 + 1) || (y < patchSize/2 + 1)) {
            canfit = false;
        }

        if ((ip.getWidth() - x < patchSize/2) || (ip.getHeight() - y  < patchSize/2)) {
            canfit = false;
        }
        //this.pixels = (float[])(ip.convertToFloatProcessor().getPixels());
        this.patchSize = patchSize;
//        if (patchSize > 0) {
//            makePatch();
//        } else {
//            patch = new SpotPatch(ip.getWidth(), ip.getHeight(), pixels);
//        }
        //showPatch();
//        fitPatch();
    }

    public void setPixelWidth(double pw) {
        pixelWidth = pw;
    }

    public boolean isFittable() {
        return canfit;
    }
    public void setConstrantsByImage() {
        xcMin = 0.2;
        xcMax = patch.getWidth()/2.0;
        ycMin = 0.2;
        ycMax = patch.getWidth()/2.0;
        stdDevMax = (xcMax + ycMax)/2.;
        stdDevMin = (xcMin + ycMin)/2.;
    }

    public void makePatch() {

        if (patchSize <= 0) {
            patch = new SpotPatch(ip.getWidth(), ip.getHeight(), pixels);
            return;
        }

        float[] ipixels =  (float[])(ip.convertToFloatProcessor().getPixels());

        int d = patchSize/2;

        int w = ip.getWidth();
        int h = ip.getHeight();

        int xmin = x - d;
        if (xmin < 0) xmin = 0;

        int xmax = x + d;
        if (xmax > w -1 ) {
            xmax = w -1;
            canfit = false;
        }

        int ymin = y - d;
        if (ymin < 0) ymin = 0;

        int ymax = y + d;
        if (ymax > h -1 ) {
            ymax = h -1;
            canfit = false;
        }

        int n = 2*d + 1;

        int index;
        patchPixels = new float[n*n];
        int pindex = 0;
        for (int j = ymin; j <= ymax; j++) {
            for (int i = xmin; i <= xmax; i++) {
                index = j*w + i;
                patchPixels[pindex] = ipixels[index];
                pindex++;
            }
        }

        patch = new SpotPatch(n, n, patchPixels);

    }

    public double[] fitPatch() {

        if (!canfit) {
            return null;
        }

        if (patch == null) {
            makePatch();
        }

        double base = patch.getStatistics().min;
        double amp = patch.getStatistics().max - base;
//        double amp = patch.get((int)patch.getWidth()/2, (int)patch.getHeight()/2);
        double s = patch.getWidth()/8.;
        double xp = Math.floor(patch.getWidth()/2.);
        double yp = Math.floor(patch.getHeight()/2.);

        double xc = patch.getStatistics().xCenterOfMass;
        double yc = patch.getStatistics().yCenterOfMass;

        ArrayList<int[]> maxpoints = patch.getMaxCoords();

        double dmax = 2000.0;
        int[] cmax = new int[2];
        for (int[] mp : maxpoints) {
            double x = mp[0];
            double y = mp[1];
            double d = Math.sqrt((x - xp)*(x - xp) + (y - yp)*(y - yp));
            if (d < dmax) {
                dmax = d;
                cmax = mp;
//                System.out.println("Sme distance " + d);
            }
        }

        double[] p = new double[5];
        p[0] = amp;
        p[1] = xp;
        p[2] = yp;
        p[3] = s;
        p[4] = base;

//        for (int i = 0; i < p.length; i++) {
//            System.out.print(p[i] + " ");
//        }
//        System.out.println();

        double[] data = patch.getDoublePixels();

        FuncGauss2D fit = new FuncGauss2D(data, patch.getWidth(), patch.getHeight());

        LeastSquaresProblem prob = new LeastSquaresBuilder()
                .start(p)
                .model(fit)
                .target(data)
                .lazyEvaluation(false)
                .maxEvaluations(10000)
                .maxIterations(10000)
                .build();


        double[] pres = null;
        LeastSquaresOptimizer.Optimum optimum;
        try {
            optimum = new LevenbergMarquardtOptimizer().optimize(prob);
            pres = optimum.getPoint().toArray();
        }
        catch (Exception e) {
            e.printStackTrace();
        }


        fitResult = pres;
        return pres;
    }

    public double[] getFitResult() {
        return fitResult;
    }

    public double getImageFitX() {
        double xfit = fitResult[1];
        double xcorner = x - Math.floor(patchSize/2.);
        return xcorner + xfit;
    }

    public double getImageFitY() {
        double yfit = fitResult[2];
        double ycorner = y - Math.floor(patchSize/2.);
        return ycorner + yfit;
    }

    public void showPatch() {
        ImagePlus imp = new ImagePlus();
        imp.setProcessor(patch);
        imp.show();

    }


    public double distanceFrom(Spot otherspot) {
        double x1 = otherspot.getX();
        double y1 = otherspot.getY();
        double dx = x1 - x;
        double dy = y1 - y;
        double d = FastMath.sqrt(dx*dx + dy*dy);
        return d;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public int getPatchSize() {
        return patchSize;
    }

    public void showresults(String results) {
        System.out.println(results);
    }

}
