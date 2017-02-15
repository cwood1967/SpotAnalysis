package org.stowers.microscopy.spotanalysis;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.PointRoi;
import ij.io.Opener;
import ij.gui.Roi;
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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

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

    @Override
    public void run() {

        DebugTools.enableIJLogging(false);
        DebugTools.enableLogging("ERROR");
        getImageFiles();
        processImageList();

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

    private void processImageList() {
        for (int i = 0; i < imageFiles.length; i++) {
            String filename = imageFiles[i].getAbsolutePath();
            IJ.log("Working on " + filename);
            processImage(filename);
            break;
        }
    }


    private void processImage(String filename) {

        ImagePlus imp = openImage(filename);
        ImageStack fitStack = getFitChannel(imp);

        ImagePlus gimp = new ImagePlus("Green");
        gimp.setStack(fitStack);
        gimp.show();

        int sizeZ = fitStack.getSize();

        for (int i = 0; i < sizeZ; i++) {
            ImageProcessor ip = fitStack.getProcessor(i + 1).convertToFloatProcessor();
            ImagePlus simp = new ImagePlus();
            simp.setProcessor(ip);

            SpotReader reader = new SpotReader(simp, tol, patchSize);
            reader.setPixelWidth(1);
            reader.analyze();

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




    @Override
    public void preview() {

    }

    @Override
    public void cancel() {

    }
}
