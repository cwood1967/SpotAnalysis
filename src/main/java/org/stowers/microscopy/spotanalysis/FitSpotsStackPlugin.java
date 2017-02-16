package org.stowers.microscopy.spotanalysis;

import com.sun.xml.internal.ws.policy.privateutil.PolicyUtils;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.io.Opener;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import ij.plugin.ChannelSplitter;
import ij.plugin.frame.RoiManager;
import ij.process.ImageProcessor;
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



    @Override
    public void run() {

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
            writeRoiZip(roiList, outpath + "rois.zip");
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
            if (!filename.endsWith("70.ome.tiff")) continue;
            processImage(filename);

        }
    }


    private void processImage(String filename) throws IOException {

        ImagePlus imp = openImage(filename);
        ImageStack fitStack = getFitChannel(imp);

        ImagePlus gimp = new ImagePlus("Green");
        gimp.setStack(fitStack);
        gimp.show();

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

            writeSpots(fs, filename, i);
            markSpots(gimp, fs, i + 1);

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

    private ImageStack getFitChannel(ImagePlus imp) {

        ImageStack stack = ChannelSplitter.getChannel(imp, fitChannel);
        return stack;
    }

    private void writeheader() throws IOException {

        String header = String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s\n",
                                    "Filename", "Slice", "Baseline", "Amplitude","FitX",
                                    "FitY", "StdDev", "ImageFitX", "ImageFitY", "StartX", "StartY");

        writer.write(header);

    }

    private void writeSpots(List<Spot> spots, String filename, int slice)
                            throws IOException {

        for (Spot spot : spots) {

            double[] p = spot.getFitResult();

            String outString = String.format("%s, %5d, %10.2f, %10.2f, %10.2f, %10.2f, %10.4f, %10.2f, %10.2f, %10.2f, %10.2f\n",
                                                filename, slice, p[4], p[0], p[1], p[2], p[3], spot.getImageFitX(),
                                                spot.getImageFitY(), spot.getX(), spot.getY());

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
