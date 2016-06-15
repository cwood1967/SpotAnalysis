package org.stowers.microscopy.spotanalysis;

import ij.process.FloatProcessor;

import java.util.ArrayList;

/**
 * Created by cjw on 5/24/16.
 */
public class SpotPatch extends FloatProcessor {

    public SpotPatch(int width, int height, float[] pixels) {
        super(width, height, pixels);
    }

    public double[] getDoublePixels() {

        float[] pixels = (float[]) this.getPixels();
        double[] d = new double[pixels.length];
        for (int i = 0; i < pixels.length; i++) {

            d[i] = (double) pixels[i];
        }

        return d;
    }

    public ArrayList<int[]> getMaxCoords() {

        ArrayList<int[]> res = new ArrayList<int[]>();
        double max = getStatistics().max;
        double eps = max*1e-6;
        float[] pixels = (float[])getPixels();
        for (int i = 0; i < pixels.length; i++) {
            if ((max - pixels[i]) < eps) {
                int x = i % width;
                int y = i / width;
                int[] c = new int[] { x, y };
                res.add(c);
            }
        }
        return res;
    }

}
