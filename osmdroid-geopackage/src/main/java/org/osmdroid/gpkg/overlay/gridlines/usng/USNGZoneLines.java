package org.osmdroid.gpkg.overlay.gridlines.usng;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 11/15/2017.
 */

public class USNGZoneLines {
    private List<Integer> latCoords;
    private List<Integer> lngCoords;
    private List<USNGGeoRectangle> geoRectangles;
    private List<Polyline> polylines;

    public USNGZoneLines (USNGViewPort viewPort) {
        latCoords = viewPort.getLatCoords();
        lngCoords = viewPort.getLngCoords();
        geoRectangles = viewPort.getGeoRectangles();
        polylines = new ArrayList<>();

        // creates lines corresponding to zone lines using arrays of lat and lng points for the viewport
        // lines of latitude first
        List<GeoPoint> geoPoints;
        GeoPoint tempGeoPoint;

        for (int i=1; i < latCoords.size()-1 ; i++) {
            geoPoints = new ArrayList<>();
            for (int j=0; j < lngCoords.size() ; j++) {
                tempGeoPoint = new GeoPoint((double) latCoords.get(i), (double) lngCoords.get(j));
                geoPoints.add(tempGeoPoint);
            }

            polylines.add(USNGUtil.createPolyline(geoPoints));
        }  // for each latitude line

        for (int i=1; i < lngCoords.size()-1 ; i++) {

            // insert code for Norway and Svalbard special cases here

            // normal case, not in Norway or Svalbard
////       else {
            geoPoints = new ArrayList<>();
            for (int j = 0 ; j < latCoords.size() ; j++) {
                tempGeoPoint = new GeoPoint((double) latCoords.get(i), (double) lngCoords.get(j));
                geoPoints.add(tempGeoPoint);
            }

            polylines.add(USNGUtil.createPolyline(geoPoints));
////      }  // normal case else
        }  // for each longitude line
    }

    public List<Polyline> getPolylines() {
        return polylines;
    }
}
