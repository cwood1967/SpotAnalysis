package org.stowers.microscopy.spotanalysis;

import java.util.*;

/**
 * The counter class matches positions of spots found in images.
 *
 * @author Chris Wood
 * @version 1.0_SNAPSHOT
 * @since 12-01-2015
 */


public class Counter {

    IReader reader = null;
    double matchDistance;
    double pixelWidth;
    String desc;

    /**
     * Contructor for the class, it takes a TableReader object for metadata info. Does nothing but set the
     * instance reader object.
     * @param reader A TableReader object that has been used to reader a datafile with spot positions.
     */
    public Counter(IReader reader) {
        this.reader = reader;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * If the distance between two point is less than matchDistance, it is counted
     * @param d the distance in pixel units
     */
    public void setMatchDistance(double d) {
        matchDistance = d;
    }

    /**
     * The converstion factor to go from real unit to pixels
     * @param w The width of a pixel to get to pixel units
     */
    public void setPixelWidth(double w) {
        pixelWidth = w;
    }

    /**
     *
     * @param c1 The first channel number
     * @param c2 The second channel number
     * @return A HashMap with the slice number as the Integer key. The value is an array (length 2) of Spot
     * objects
     */
    public HashMap<Integer, ArrayList<Spot[]>> findMatches(int c1, int c2) {
        HashMap<Integer, ArrayList<Spot[]>> mapMatch = new HashMap<Integer, ArrayList<Spot[]>>();
        int[] slices =reader.getSlicesList();
        int[] channels = reader.getChannelsList();

        ArrayList<Spot[]> m = null;
        for (int i = 0; i < slices.length; i++) {
            m = findMatchesSlice(c1, c2, slices[i]);
            mapMatch.put(slices[i], m);
        }

        return mapMatch;
    }

    /**
     *
     * @param mapMatch2 The second channel (channel number from image)
     * @param c3 The third channel (channel number from image)
     * @return A HashMap with the slice number as the Integer key. The value is an array (length 3) of Spot
     * objects
     */
    public  HashMap<Integer, ArrayList<Spot[]>> findMatches3(
            HashMap<Integer, ArrayList<Spot[]>> mapMatch2, int c3) {

//        mapMatch2 = findMatches(c1, c2);

        double dtest =  (matchDistance + 0.)*pixelWidth;

        HashMap<Integer, ArrayList<Spot[]>> mapMatch123 = new HashMap<Integer, ArrayList<Spot[]>>();

        // iterate over the keys, the keys are the slice number
        for (Integer i : mapMatch2.keySet()) {
            //the spotlists for the current slice
            ArrayList<Spot[]> spotlist = mapMatch2.get(i);
            ArrayList<Spot> c3spots = reader.getSpotList(c3, i);

            //create a new arraylist for the matches of all three channels
            ArrayList<Spot[]> spotlist123 = new ArrayList<Spot[]>();

            //go ahead and add this list to the HashMap
            mapMatch123.put(i, spotlist123);
            //iterate over the c1-c2 Spot matches
            for (Spot[] sm : spotlist) {
                Spot sc1 = sm[0];
                Spot sc2 = sm[1];
                for (Spot sc3 : c3spots ) {
                    //check to see if all three are close
                    if ((sc3.distanceFrom(sc1) < dtest) || (sc3.distanceFrom(sc2) < dtest)) {
                        //if they are, add them to the list
                        spotlist123.add(new Spot[] {sc1, sc2, sc3 });
                    }
                }
            }
        }

        return mapMatch123;
    }

    public ArrayList<Spot[]> findMatchesSlice(int c1, int c2, int slice) {
        ArrayList<Spot[]> matches = new ArrayList<Spot[]>();

        ArrayList<Spot> spotlist1 = reader.getSpotList(c1, slice);
        int nc1 = spotlist1.size();

        ArrayList<Spot> spotlist2 = reader.getSpotList(c2, slice);
        int nc2 = spotlist2.size();

        double d;
        double dtest = matchDistance*pixelWidth;
        int nc = 0;
        for (int i = 0; i < nc1 ; i++) {
            Spot s = spotlist1.get(i);

            for (int j = 0; j < nc2; j++) {
                Spot g = spotlist2.get(j);
                d = s.distanceFrom(g);
                if (d < dtest) {
                    nc++;
                    matches.add(new Spot[]{s, g});
                    //String apath = "/Users/cjw/DataTemp/RNAFish/rois/roi";
                    //String fname = String.format("%s_%d_%d_%02d_%06d.roi", apath, c1, c2, slice, i);
                    //s.makeRoiFile(fname, pixelWidth);
                }
            }
        }

        String result = String.format(
                "%s\t%d\t%.2f\t%d", desc, slice, matchDistance, nc);
        System.out.println(result);
        return matches;
    }


}
