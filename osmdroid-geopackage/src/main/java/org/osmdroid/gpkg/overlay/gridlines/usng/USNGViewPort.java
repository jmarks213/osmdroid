package org.osmdroid.gpkg.overlay.gridlines.usng;

import org.osmdroid.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 11/13/2017.
 */

public class USNGViewPort {

    private static final int NUMBER_OF_LATITUDE_BANDS = 20;

    private int idlModel;
    private int wLng;
    private int nLat;
    private int eLng;
    private int sLat;
    private int x1;
    private int y1;
    private List<Integer> latCoords;
    private List<Integer> lngCoords;
    private List<USNGGeoRectangle> geoRectangles;

    public USNGViewPort(BoundingBox mapViewBounds, boolean temp) {
        wLng = (int) mapViewBounds.getLonWest();
        nLat = (int) mapViewBounds.getLatNorth();
        eLng = (int) mapViewBounds.getLonEast();
        sLat = (int) mapViewBounds.getLatSouth();

        // north latitude does not exceed 84
        if (nLat > 84) {
            nLat = 84;
        }
        // south latitude does not exceed -80
        if (sLat < -80) {
            sLat = -80;
        }

        latCoords = new ArrayList<>(NUMBER_OF_LATITUDE_BANDS);
        for (int tempSLat = sLat ; tempSLat < nLat ; tempSLat += 8 ) {
            latCoords.add(tempSLat);
        }
    }


    public USNGViewPort(BoundingBox mapViewBounds) {

        // push the corners of the view out so that lines extend off screen
        final int PUSH_BOUNDS_LAT = 0;
        final int PUSH_BOUNTS_LON = 0;
        // push the northwest corner further out so more lines are drawn
        wLng = mapViewBounds.getLonWest() - PUSH_BOUNTS_LON < -180 ? -180 : (int) mapViewBounds.getLonWest() - PUSH_BOUNTS_LON;
        nLat = mapViewBounds.getLatNorth() + PUSH_BOUNDS_LAT > 90 ? 90 : (int) mapViewBounds.getLatNorth() + PUSH_BOUNDS_LAT;

        eLng = mapViewBounds.getLonEast() + PUSH_BOUNDS_LAT > 180 ? 180 : (int) mapViewBounds.getLonEast() + PUSH_BOUNDS_LAT;
        sLat = mapViewBounds.getLatSouth() - PUSH_BOUNTS_LON < -90 ? -90 : (int) mapViewBounds.getLatSouth() - PUSH_BOUNTS_LON;

        if (wLng < -180 && eLng < 0 && eLng > -180) {
            idlModel = 1;  // viewport contains IDL and uses west hemisphere coordinate system
        }
        else if (wLng > 0 && wLng < 180 && eLng > 180) {
            idlModel = 2;  // viewport contains IDL and uses east hemisphere coordinate system
        }
        else {
            idlModel = 0;  // normal case, IDL not in viewport
        }

        // UTM is undefined beyond 84N or 80S, so this application defines viewport at those limits
        if (nLat > 84) {
            nLat=84;
        }

        // first zone intersection inside the southwest corner of the map window
        // longitude coordinate is straight-forward...
        x1 = (int)(Math.floor((wLng/6)+1)*6.0);

        // but latitude coordinate has three cases
        if (sLat < -80) {  // far southern zone; limit of UTM definition
            y1 = -80;
        }
        else {
            y1 = (int)(Math.floor((sLat/8)+1)*8.0);
        }

        lngCoords = new ArrayList<>();
        latCoords = new ArrayList<>();

        // compute the latitude coordinates that belong to this viewport
        if (sLat < -80) {
            // special case of southern limit
            latCoords.add(0,-80);
        }
        else {
            // normal case
            latCoords.add(0, sLat);
        }

        int lat = y1, j = 1;
        for ( ; lat < nLat; lat+=8, j++) {
            if (lat <= 72) {
                latCoords.add(j, lat);
            }
            else if (lat <= 80) {
                latCoords.add(j, 84);
            }
            else { j--; }
        }

        latCoords.add(j, nLat);

        // compute the longitude coordinates that belong to this viewport
        lngCoords.add(0, wLng);
        if (wLng < eLng) {   // normal case
            j=1;
            for (int lng=x1 ; lng < eLng; lng+=6, j++) {
                lngCoords.add(j,lng);
            }
        }

        lngCoords.add(j++, eLng);

        // store corners and center point for each geographic rectangle in the viewport
        // each rectangle may be a full UTM cell, but more commonly will have one or more
        //    edges bounded by the extent of the viewport
        // these geographic rectangles are stored in instances of the class 'usng_georectangle'
        USNGGeoRectangle tempGeo;
        geoRectangles = new ArrayList<>();

        for (int i=0; i< latCoords.size()-1 ; i++) {
            for (j=0; j < lngCoords.size()-1 ; j++) {
                if (latCoords.get(i) >= 72 && lngCoords.get(j) == 6) {  } // do nothing
                else if (latCoords.get(i) >= 72 && lngCoords.get(j) == 18) {  } // do nothing
                else if (latCoords.get(i) >= 72 && lngCoords.get(j) == 30) {  } // do nothing
                else {
                    tempGeo = new USNGGeoRectangle();
                    tempGeo.assignCorners(latCoords.get(i), latCoords.get(i+1), lngCoords.get(j), lngCoords.get(j+1));
                    if (latCoords.get(i) != latCoords.get(i+1)) {  // ignore special case of -80 deg latitude
                        tempGeo.assignCenter();
                    }
                    geoRectangles.add(tempGeo);
                }
            }
        }
    }

    public List<Integer> getLatCoords() {
        return latCoords;
    }

    public List<Integer> getLngCoords() {
        return lngCoords;
    }

    public List<USNGGeoRectangle> getGeoRectangles() {
        return geoRectangles;
    }
}
