package org.stowers.microscopy.spotanalysis;

import java.util.ArrayList;

/**
 * Created by cjw on 1/21/16.
 */
public interface IReader {

    public ArrayList<Spot> getSpotList(int channel, int slice);

    public int[] getSlicesList();

    public int[] getChannelsList();
}
