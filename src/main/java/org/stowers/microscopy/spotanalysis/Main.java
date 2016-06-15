package org.stowers.microscopy.spotanalysis;

import ij.ImagePlus;
import ij.io.Opener;
import net.imagej.ImageJ;
import loci.plugins.BF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;


/**
 * Created by cjw on 5/24/16.
 */
public class Main {

    public static void main(String[] args) {

        //final ImageJ imagej = net.imagej.Main.launch(args);

        String file = "/Volumes/projects/omxworx/RUH/20160519/Hela_RBM3_27c_01_SIR_PRJ.dv";
        ImagePlus imp = null;
        try {
            ImagePlus[] imps = BF.openImagePlus(file);
            imp = imps[0];
        }
        catch (Exception e) {
            e.printStackTrace();
        }

//        imp.show();

//        Spot spot = new Spot(0, 487, 563, imp.getProcessor(), 11, 1, 1);

        SpotReader reader = new SpotReader(imp, 6000);
        reader.setPixelWidth(1);
        reader.analyze();
        HashMap<ArrayList<Integer>, ArrayList<Spot>> spotsMap = reader.getSpotsMap();

        System.out.println(spotsMap.size());

        int nn = 0;
        int nf = 0;
        int nq = 0;
        double s = 0;
        double a = 0;

        Spot spotx = reader.spotFromCoordinates(637, 279, 11, 1, 1);
        spotx.makePatch();
        double[] px = spotx.fitPatch();
        for (int i = 0; i < px.length; i++) {
            System.out.println(px[i]);
        }

        if (1 == 1) {
            return;
        }
        double t0 = System.currentTimeMillis();
        for (ArrayList<Integer> k : spotsMap.keySet()) {
            ArrayList<Spot> spots = spotsMap.get(k);
            for (Spot spot : spots) {
                spot.makePatch();
                if (spot.isFittable()) {
                    try {
                        double[] p = spot.fitPatch();
                        double dx = spot.getX() - spot.getPatchSize()/2;
                        double dy = spot.getY() - spot.getPatchSize()/2;
                        if ((p[3] > 0.1) && (p[3] < 10)) {
                            s += p[3];
                            a += p[0];
                            nf++;
//                            System.out.println(p[0] + " " + p[1] + " " + p[2] + " " + p[3] + " " + p[4]);
//                            System.out.println(spot.getX() + " " +  spot.getY());
//                            System.out.println((dx + p[1]) + " " +  (dy + p[2]));
//                            System.out.println(dx + " " +  dy);
//                            System.out.println("##");
                        }
                        else {
                            System.out.println(p[0] + " " + p[1] + " " + p[2] + " " + p[3] + " " + p[4]);
                            System.out.println(spot.getX() + " " +  spot.getY());
                            System.out.println((dx + p[1]) + " " +  (dy + p[2]));
                            System.out.println(dx + " " +  dy);
                            System.out.println("--");
                            nq++;
                        }
                    }
                    catch (Exception e) {
                        System.out.println("Couldn't fit " + spot.getX() + " " + spot.getY());
                        e.printStackTrace();
                        nn++;
                    }
                }
            }

        }
        System.out.println(nn);
        System.out.println(nq);
        System.out.println(nf);
        System.out.println(s/nf + " " + a/nf);
        System.out.println(System.currentTimeMillis() - t0);
    }
}
