package org.stowers.microscopy.spotanalysis;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.io.Opener;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.plugin.ChannelSplitter;
import ij.plugin.ZProjector;
import ij.plugin.filter.GaussianBlur;
import ij.plugin.frame.RoiManager;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.AutoThresholder;

import loci.common.DebugTools;
import loci.formats.FormatException;
import loci.plugins.BF;

import net.imagej.ImageJ;
//import net.imagej.table.DefaultResultsTable;
//import net.imagej.table.ResultsTable;
import ij.measure.ResultsTable;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.command.Previewable;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;



@Plugin(type = Command.class, menuPath="Plugins>Chris>Stack Fit Spots")
public class FitSpotsStackPlugin implements Command, Previewable {

    @Parameter(label="Pick a directory", style="directory")
    File inputDirectory;

    @Parameter(label="Pick a directory", style="directory")
    File outputDirectory;

    File[] imageFiles;

    @Parameter(label = "Size of fit region")
    int patchSize;

    @Parameter(label = "Noise threshold")
    double tol;

    public FitSpotsStackPlugin() {

    }

    String[] type = {"tif", "tiff"};
    int fitChannel = 2;
    int dapiChannel = 3;

    ArrayList<Roi> roiList;
    String outpath = "/Users/cjw/Desktop/Out/";

    BufferedWriter writer = null;
    AutoThresholder.Method method;

    ArrayList<Integer> inDapiList;

    @Override
    public void run() {

        outpath = outputDirectory.getAbsolutePath();
        method = AutoThresholder.Method.Huang;
        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("ERROR");

        Charset charset = Charset.forName("US-ASCII");
        Path outputFile = Paths.get(outpath + "data.csv");

        try {
            writer = Files.newBufferedWriter(outputFile, charset);
            writeheader();
            roiList = new ArrayList();
            getImageFiles();
            processImageList();
//            writeRoiZip(roiList, outpath + "rois.zip");
            writer.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            return;
        }

    }

    private void getImageFiles() {
        // get all of the images in the folder - only those of type
        imageFiles = inputDirectory.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {

                boolean res = false;
                for (int i = 0; i < type.length; i++) {
                    if (name.endsWith(type[i])) {
                        res = true;
                        break;
                    }
                }
                return res;
            }
        });
    }

    private void processImageList() throws IOException {
        for (int i = 0; i < imageFiles.length; i++) {
            String filename = imageFiles[i].getAbsolutePath();
            IJ.log("Working on " + filename);
            if (!filename.endsWith("ome.tiff")) continue;
            processImage(filename);

        }
    }


    private void processImage(String filename) throws IOException {

        ImagePlus imp = openImage(filename);

        roiList = new ArrayList<>();

        ImagePlus dapiImp = getCellMask(imp);
//        dapiImp.show();
        ImageStack fitStack = getFitChannel(imp);

        ImagePlus gimp = new ImagePlus("Green");
        gimp.setStack(fitStack);
//        gimp.show();

        int sizeZ = fitStack.getSize();

        for (int i = 0; i < sizeZ; i++) {
            gimp.setSlice(i + 1);
            ImageProcessor ip = fitStack.getProcessor(i + 1).convertToFloatProcessor();
            ImagePlus simp = new ImagePlus();
            simp.setProcessor(ip);

            SpotReader reader = new SpotReader(simp, tol, patchSize, filename, i + 1);
            reader.setPixelWidth(1);
            reader.analyze();

            ArrayList<Spot> spots = reader.getSpotList();
            System.out.println(spots.size());

            List<Spot> fs = null;

            spots.parallelStream()
                    .forEach(sp -> sp.fitPatch());
            fs = spots.parallelStream()
                    .filter(sp -> sp.getFitResult() != null)
                    .filter(sp -> sp.getFitResult()[3] > .001)
                    .filter(sp -> sp.getFitResult()[3] < 1000.)
                    .collect(Collectors.toList());


            System.out.println("--N " + spots.size());
            System.out.println("--N " + fs.size());

            List<Spot> notfit = spots.parallelStream()
                    .filter(sp -> sp.getFitResult() == null)
                    .collect(Collectors.toList());

            System.out.println("No Fit: " + notfit.size());
            System.out.println("No Fit: " + spots.size());

            writeSpots(fs, filename, i, ip);

            markSpots(gimp, fs, i + 1);

            int lastDotPos = filename.lastIndexOf(".");
            int lastSpace = filename.lastIndexOf(" ");
            String numStr = filename.substring(lastSpace + 1, lastDotPos);
            writeRoiZip(roiList, outpath + numStr + "-rois.zip");
        }
    }

    private ImagePlus openImage(String filename) {

        ImagePlus imp;
        try {
            ImagePlus[] imps = BF.openImagePlus(filename);
            imp = imps[0];
        }
        catch (FormatException e) {
            IJ.log("Can't open: " + filename + ". Bio-formats Format Exception");
            return null;
        }
        catch (IOException e) {
            IJ.log("Can't open: " + filename + ". Java IO Exception");
            return null;
        }
        return imp;
    }


    private ImagePlus getCellMask(ImagePlus imp) {

        inDapiList = new ArrayList<>();
        ImageStack stack = ChannelSplitter.getChannel(imp, dapiChannel);
        ImagePlus dimp = new ImagePlus("Dapi");
        dimp.setStack(stack);
        ZProjector zp = new ZProjector(dimp);
        zp.setStartSlice(1);
        zp.setStopSlice(stack.getSize());
        zp.setMethod(zp.SUM_METHOD);
        zp.doHyperStackProjection(true);
        ImagePlus projectedImp = zp.getProjection();

        GaussianBlur blur = new GaussianBlur();
        blur.blurGaussian(projectedImp.getProcessor(), 4., 4., 0.0002);
        projectedImp.getProcessor().resetThreshold();
        projectedImp.getProcessor().setAutoThreshold(method, true);

        double thres = projectedImp.getProcessor().getMinThreshold();

        float[] pixels = (float[])projectedImp.getProcessor().getPixels();
        byte[] maskPixels = new byte[pixels.length];

        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] > thres) {
                maskPixels[i] = (byte)(255 & 0xFF);
                inDapiList.add(i);
            } else {
                maskPixels[i] = 0;
            }
        }

        ImageProcessor maskIp = new ByteProcessor(dimp.getWidth(), dimp.getHeight());
        maskIp.setPixels(maskPixels);
        ImagePlus mimp = new ImagePlus("SUM Projected Dapi Mask", maskIp);

        return mimp;
    }

    private ImageStack getFitChannel(ImagePlus imp) {

        ImageStack stack = ChannelSplitter.getChannel(imp, fitChannel);
        return stack;
    }

    private void writeheader() throws IOException {

        String header = String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s\n",
                                    "Filename", "Slice", "Baseline", "Amplitude","FitX",
                                    "FitY", "StdDev", "ImageFitX", "ImageFitY", "StartX", "StartY", "InDapi",
                                    "DapiSize", "CytoplasmSize");

        writer.write(header);

    }

    private void writeSpots(List<Spot> spots, String filename, int slice, ImageProcessor ip)
                            throws IOException {

        for (Spot spot : spots) {

            double[] p = spot.getFitResult();

            int x = spot.getXint();
            int y = spot.getYint();
            int w = ip.getWidth();
            int h = ip.getHeight();
            int index = y*w + x;

            int inDapi;
            if (inDapiList.contains(index)) {
                inDapi = 1;
            }
            else {
                inDapi = 0;
//                System.out.println("Cytoplasm");
            }
            String outString = String.format("%s, %5d, %10.2f, %10.2f, %10.2f, %10.2f, %10.4f, %10.2f, %10.2f, " +
                                                "%10.2f, %10.2f, %5d, %10d, %10d\n",
                                                filename, slice, p[4], p[0], p[1], p[2], p[3], spot.getImageFitX(),
                                                spot.getImageFitY(), spot.getX(), spot.getY(), inDapi,
                                                inDapiList.size(), w*h - inDapiList.size());

            writer.write(outString);

        }
    }

    protected void markSpots(ImagePlus mimp, List<Spot> spots, int slice) {
        RoiManager manager = RoiManager.getInstance();
        if (manager == null) {
            manager = new RoiManager();
        }
//        manager.reset();

        float[] xpoints = new float[spots.size()];
        float[] ypoints = new float[spots.size()];

        ArrayList<PointRoi> rois = new ArrayList<>();
        int roiIndex = 0;
        for (Spot s : spots) {
            double x = s.getX() + 0.0;
            double y = s.getY() + 0.0;
            xpoints[roiIndex] = (float)x;
            ypoints[roiIndex] = (float)y;
            roiIndex++;
        }

        PointRoi proi = new PointRoi(xpoints, ypoints);
        proi.setSize(1);
        proi.setPointType(3);
        proi.setPosition(slice);
        String strSlice = String.format("%03d", slice);
        String name = strSlice + "-Fit tol = " + tol;
        proi.setName(name);
        manager.add(mimp, proi, slice);
        roiList.add(proi);
    }

    private void writeRoiZip(ArrayList<Roi> rois, String path) {
        DataOutputStream out = null;
        try {
            ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(path)));
            out = new DataOutputStream(new BufferedOutputStream(zos));
            RoiEncoder encoder = new RoiEncoder(out);
            for (Roi roi: rois) {
                String label = roi.getName() + ".roi"; //" .roi";
                zos.putNextEntry(new ZipEntry(label));
                encoder.write(roi);
                out.flush();
            }
            out.close();
        }
        catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public void preview() {

    }

    @Override
    public void cancel() {

    }
}
