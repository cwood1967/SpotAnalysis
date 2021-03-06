package org.stowers.microscopy.spotanalysis;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.filter.MaximumFinder;
import ij.process.AutoThresholder;
import ij.process.ByteProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by cjw on 1/21/16.
 */
public class SpotReader implements IReader {

    ImagePlus imp;
    Roi roi;
    ImageProcessor roiMask;
    int width;
    int height;
    int nc;
    int nz;
    int stackSize;
    double pixelWidth = 1;
    int patchSize = 11;
    int currentSlice;

    int theZ;
    String imageFile;
    AutoThresholder.Method method = null;

    double tol;  //the noise tolerance for the maximum finder
    HashMap<Integer, ArrayList<Spot>> spotsMap;

    ArrayList<Spot> spotList;
    public void setPixelWidth(double pw) {
        pixelWidth = pw;
    }

    public SpotReader(ImagePlus imp, double tol, int patchSize, String imageFile, int theZ) {

        this(imp, tol, patchSize);
        this.imageFile = imageFile;
        this.theZ = theZ;
        imp.setSlice(theZ);

    }

    public SpotReader(ImagePlus imp, double tol, int patchSize) {
        this.imp = imp;
        this.width = imp.getWidth();
        this.height = imp.getHeight();
        this.tol = tol;
        this.patchSize = patchSize;
        this.roi = imp.getRoi();
//        this.roiMask = imp.getMask();
        nz = imp.getNSlices();
        nc = imp.getNChannels();
        stackSize = imp.getStackSize();
        currentSlice = imp.getCurrentSlice();
    }

    public SpotReader(ImagePlus imp, AutoThresholder.Method method) {
        this.imp = imp;
        this.width = imp.getWidth();
        this.height = imp.getHeight();
        this.method = method;
        nz = imp.getNSlices();
        nc = imp.getNChannels();
        stackSize = imp.getStackSize();
    }

    public SpotReader(ImagePlus imp, double tol) {
        this(imp, tol, 11);
    }

    public void analyze() {
        MaximumFinder maxFinder = new MaximumFinder();

        ArrayList<Spot> spots = null;

        FloatProcessor bip = (FloatProcessor)imp.getProcessor().convertToFloatProcessor().duplicate();

        GaussianBlur blur = new GaussianBlur();
        blur.blurFloat(bip, .5, .5, 0.0002);

//        ImageProcessor oip = imp.getStack().getProcessor(currentSlice);
        int theC = imp.getChannel();
        int theZ = imp.getSlice();

        double stol = tol;
        if (method != null ) {
            bip.setAutoThreshold(method, true);
            stol = bip.getMinThreshold();
//                System.out.println(theC + " " + theZ + " " + stol);
        }

//        oip.setRoi(roi);
        bip.setMask(roiMask);
        ByteProcessor mfip = maxFinder.findMaxima(bip, stol, 0, false);
        spots = imageToSpots(mfip, 0, theC, theZ);

        spotList = spots;

    }

    public Spot spotFromCoordinates(int x, int y, int size, int theC, int theZ) {
        Spot spot= new Spot(0, x, y, imp.getProcessor(), size, theC, theZ);
        return spot;
    }

    private ArrayList<Spot> imageToSpots(ByteProcessor xip, int id, int theC, int theZ) {

        byte[] pixels = (byte[])xip.getPixels();
        int np = pixels.length;

        ArrayList<Spot> spots = new ArrayList();
        for (int i = 0; i < np; i++) {
            int x = i % width;
            int y = i / width;
            if (pixels[i] != 0) {
                Spot spot = new Spot(id, x, y, imp.getProcessor(), patchSize, theC, theZ);
                spot.setStack(imp.getStack());
                spot.setPixelWidth(pixelWidth);
                spots.add(spot);
            }
        }
        return spots;
    }

    public  HashMap<Integer, ArrayList<Spot>> getSpotsMap() {
        return spotsMap;
    }

    public ArrayList<Spot> getSpotList() {
        return spotList;
    }

    @Override
    public ArrayList<Spot> getSpotList(int channel, int slice) {

        ArrayList<Integer> key = new ArrayList<Integer>();
        key.add(channel);
        key.add(slice);
        return spotsMap.get(key);
    }

    @Override
    public int[] getSlicesList() {

        int[] list = new int[nz];
        for (int i = 0; i < nz; i++) {
            list[i] = i + 1;
        }
        return list;
    }

    @Override
    public int[] getChannelsList() {
        int[] list = new int[nc];
        for (int i = 0; i < nc; i++) {
            list[i] = i + 1;
        }
        return list;
    }
}
