package org.stowers.microscopy.spotanalysis;

import ij.ImagePlus;
import ij.gui.PointRoi;
import ij.io.Opener;
import ij.gui.Roi;
import ij.plugin.frame.RoiManager;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by cjw on 6/14/16.
 */




@Plugin(type = Command.class, menuPath="Plugins>Chris>FitSpots")
public class FitSpotsPlugin implements Command, Previewable {

    @Parameter(type = ItemIO.INPUT)
    private ImagePlus imp;

    @Parameter(label = "Size of fit region")
    int patchSize;

    @Parameter(label = "Noise threshold")
    double tol;

    @Override
    public void run() {


        Roi roi = imp.getRoi();
        if (roi != null) {
            imp.saveRoi();
        }
        SpotReader reader = new SpotReader(imp, tol, patchSize);
        reader.setPixelWidth(1);
        reader.analyze();
        ArrayList<Spot> spots = reader.getSpotList();

        System.out.println(spots.size());

        int nn = 0;
        int nf = 0;
        int nq = 0;
        double s = 0;
        double a = 0;

        double t0 = System.currentTimeMillis();

        List<Spot> fs = null;
//        for (Integer k : spotList) {
//            ArrayList<Spot> spots = spotsMap.get(k);
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
//        }

        ResultsTable table = makeTable(fs);
        table.show("Gaussian Fit Results");
        markSpots(fs);
        RoiManager.getInstance().setVisible(true);
        System.out.println(System.currentTimeMillis() - t0);
    }

    protected void markSpots(List<Spot> spots) {
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
        String name = "Fit tol = " + tol;
        proi.setName(name);
        manager.add(imp, proi, 0);

    }

    protected ResultsTable makeTable(List<Spot> spots) {
        ResultsTable table = new ResultsTable();

        for (Spot s: spots) {

            table.incrementCounter();
            double[] p = s.getFitResult();
            table.addValue("Baseline", p[4]);
            table.addValue("Amplitude", p[0]);
            table.addValue("x fit", p[1]);
            table.addValue("y fit", p[2]);
            table.addValue("StdDev", p[3]);
            table.addValue("x fit image coord", s.getImageFitX());
            table.addValue("y fit image coord", s.getImageFitY());
            table.addValue("x start point", s.getX());
            table.addValue("y start point", s.getY());

        }

        return table;
    }
    public static void main(final String... args) throws Exception {

        final ImageJ imagej = net.imagej.Main.launch(args);

//        String name = "/Volumes/projects/omxworx/RUH/20160519/Hela_RBM3_27c_01_SIR_PRJ.dv";
//        ImagePlus imp = null;
//        try {
//            ImagePlus[] imps = BF.openImagePlus(name);
//            imp = imps[0];
//        }
//        catch (Exception e) {
//            e.printStackTrace();
//        }
//
////        Object i1 = imagej.io().open(name);
//        Opener o = new Opener();
////        ImagePlus i1 = o.openImage(name);
//        imp.show();
//        imagej.ui().show(i1);
        //imagej.command().run(RnaFishPlugin.class, true);

    }

    @Override
    public void preview() {

    }

    @Override
    public void cancel() {

    }
}
