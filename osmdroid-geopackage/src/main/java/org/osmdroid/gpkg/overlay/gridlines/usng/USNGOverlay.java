package org.osmdroid.gpkg.overlay.gridlines.usng;

import android.content.Context;
import android.util.Log;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.FolderOverlay;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 11/13/2017.
 */

public class USNGOverlay {
    private static final String LOG_TAG = USNGOverlay.class.getSimpleName();


    public static FolderOverlay getUSNGGrid (Context mContext, MapView mapView) {

        BoundingBox boundingBox = mapView.getBoundingBox();
        int zoomLevel = mapView.getZoomLevel();
        FolderOverlay unsgGridLines = new FolderOverlay(mContext);

        USNGViewPort viewPort = new USNGViewPort(boundingBox);

        List<Integer> latlines = viewPort.getLatCoords();
        List<Integer> lnglines = viewPort.getLngCoords();
        List<USNGGeoRectangle> gzd_rectangles = viewPort.getGeoRectangles();

        if (zoomLevel > 2) {
            // makes zone line width semi-dynamic; gets wider as you zoom in
            double linewidth = zoomLevel * 1.5;

            // creates lines corresponding to zone lines using arrays of lat and lng points for the viewport
            // lines of latitude first
            List<GeoPoint> tempGeoPointsList;
            Polyline tempPolyline;

            for (int i= 0 ; i < latlines.size() ; i++) {

                tempGeoPointsList = new ArrayList();
                for (int j=0; j < lnglines.size() ; j++) {
                    // convert back to mercator coordinates, the coord system of the base map
                    tempGeoPointsList.add(new GeoPoint((double)latlines.get(i), (double)lnglines.get(j)));
                }

                unsgGridLines.add(USNGUtil.createPolyline(tempGeoPointsList));
            }  // for each latitude line

            for (int i = 0 ; i < lnglines.size() ; i++) {
                // insert code for Norway and Svalbard special cases here

                // normal case, not in Norway or Svalbard
                ////       else {
                tempGeoPointsList = new ArrayList();
                for (int j = 0 ; j < latlines.size() ; j++) {
                    tempGeoPointsList.add(new GeoPoint((double)latlines.get(j), (double)lnglines.get(i)));
                }

                unsgGridLines.add(USNGUtil.createPolyline(tempGeoPointsList));
                ////      }  // normal case else
            }  // for each longitude line
        }

        // display 100K grids
        if (zoomLevel > 5) {
            List<Polyline> grid100k = new ArrayList<>();
            try {
                 grid100k = USNGGrids.grid100k(viewPort, zoomLevel);
            } catch (Exception e) {
                Log.e(LOG_TAG,"display 100k grid",e);
            }
            if (grid100k != null) {
                for (Polyline p : grid100k) {
                    unsgGridLines.add(p);
                }
            }
            /*
            if (zoom > 6 ) {    // display 100K labels
                for (i in lines100k.labels) {
                    for (j in lines100k.labels[i]) {
                        gridLayer100k.addFeatures([lines100k.labels[i][j]]);  // 'lines' is an array of vectors belonging to zonelines, created by usngzonelines()
                    }
                }
            }
            */
        }

        return unsgGridLines;
    }


}
