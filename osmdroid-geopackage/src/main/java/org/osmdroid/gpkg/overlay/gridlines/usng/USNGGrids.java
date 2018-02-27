package org.osmdroid.gpkg.overlay.gridlines.usng;

import org.osgeo.proj4j.ProjCoordinate;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 11/15/2017.
 */

public class USNGGrids {
    private double nLat;
    private double sLat;
    private double wLng;
    private double eLng;
    private double interval;

    private int zoomLevel;
    private ArrayList<Polyline> gridlines;

    double p_sLat;
    double p_wLng;
    double p_nLat;
    double p_eLng;

    public USNGGrids (USNGGeoRectangle geoRectangle, int interval, int zoomLevel) {
        this.sLat = geoRectangle.getsLat();
        this.wLng = geoRectangle.getwLng();
        this.nLat = geoRectangle.getnLat();
        this.eLng = geoRectangle.geteLng();

        this.interval = interval;

        //ProjCoordinate nw = USNGUtil.toEPSG3857fromEPSG4326(new ProjCoordinate(this.wLng, this.nLat));
        //ProjCoordinate se = USNGUtil.toEPSG3857fromEPSG4326(new ProjCoordinate(this.eLng, this.sLat));

        //PointL nw = USNGUtil.toEPSG3857fromEPSG4326(new GeoPoint(nLat, wLng));
        //PointL se = USNGUtil.toEPSG3857fromEPSG4326(new GeoPoint(sLat, eLng));
        //Point nw = Mercator.projectGeoPoint(nLat, wLng, zoomLevel, null);
        //Point se = Mercator.projectGeoPoint(sLat, eLng, zoomLevel, null);

        //Mercator.projectPoint(nw.x, nw.y, zoomLevel);

        //p_sLat = se.y;
        //p_wLng = nw.x;
        //p_nLat = nw.y;
        //p_eLng = se.x;

        this.zoomLevel = zoomLevel;

        gridlines = new ArrayList<>();
    }

    public static List<Polyline> grid100k (USNGViewPort viewPort, int zoomLevel) throws Exception {
        List<Polyline> polylines = new ArrayList<>();
        List<Polyline> polylines100k = new ArrayList<>();

        USNGZoneLines zoneLines = new USNGZoneLines(viewPort);
        polylines = zoneLines.getPolylines();

        List<USNGGeoRectangle> geoRectangles = viewPort.getGeoRectangles();

        if (geoRectangles.isEmpty()) {
            return null;
        }

        // for each geographic rectangle in this viewport, generate and store 100K gridlines
        for (int i=0 ; i < geoRectangles.size() ; i++) {
            USNGGrids onegzd = new USNGGrids(geoRectangles.get(i), 100000, zoomLevel);
            onegzd.computeOneCell(viewPort);
            polylines100k.addAll(onegzd.gridlines);
            //this.labels[i] = onegzd.labels;
        }

        return polylines100k;
    }

    // instance of one utm cell
    private void computeOneCell (USNGViewPort viewport) throws Exception {

        double cellLngCenter = (this.wLng+this.eLng)/2;
        double cellLatCenter = (this.sLat+this.nLat)/2;

        Integer cellZone = USNGUtil.getZoneNumber(cellLatCenter, cellLngCenter);
        char cellLetter = USNGUtil.UTMLetterDesignator(cellLatCenter);

        int i,j,k,m,n,p,q;

        double wLng_temp = this.wLng;

        // calculate sw easting and northing
        ProjCoordinate utmCoords = USNGUtil.LLtoUTMpro4jProj(
                new ProjCoordinate(wLng_temp, this.sLat),
                cellZone,
                cellLetter);

        int sw_utm_e = (int)((Math.floor(utmCoords.x/this.interval)*this.interval)-this.interval);
        int sw_utm_n = (int)((Math.floor(utmCoords.y/this.interval)*this.interval)-this.interval);

        double eLng_temp = this.eLng;

        // calculate ne easting and northing
        utmCoords = USNGUtil.LLtoUTMpro4jProj(
                new ProjCoordinate(eLng_temp, this.nLat),
                cellZone,
                cellLetter);

        int ne_utm_e = (int)((Math.floor(utmCoords.x/this.interval+1)*this.interval)+this.interval);
        int ne_utm_n = (int)((Math.floor(utmCoords.y/this.interval+1)*this.interval)+this.interval);

        GeoPoint geocoords;
        ArrayList<Double> northings = new ArrayList<>();   // used to calculate 100K label positions
        ArrayList<Double> eastings = new ArrayList<>();    // used to calculate 100K label positions

        double precision;
        // set density of points on grid lines
        // case 1: zoomed out a long way; not very dense
        if (this.zoomLevel < 12 ) {
            precision = 10000;
        }
        // case 2: zoomed in a long way
        else if (this.zoomLevel > 15) {
            precision = 100;
        }
        // case 3: in between, zoom levels 12-15
        else {
            precision = 1000;
        }

        // for 1,000-meter grid and finer, don't want to label zone lines
        if (this.interval > 1000) { northings.add(0, this.sLat); }
        k = 1;

        // for each e-w line that covers the cell, with overedge
        for (i=sw_utm_n, j=0; i<ne_utm_n; i+=this.interval,j++) {
            //geocoords = new GeoPoint(0.0,0.0);

            // holds extended lines
            ArrayList<GeoPoint> temp = new ArrayList<>();
            // holds lines clipped to GZD bounds
            ArrayList<GeoPoint> gr100kCoord = new ArrayList<>();


            // collect coords to be used to place markers
            // '2*this.interval' is a fudge factor that approximately offsets grid line convergence
            /*USNGUtil.UTMtoLL(i,sw_utm_e+(2*this.interval),zone,geocoords);
            if ((geocoords.getLatitude() > this.sLat) && (geocoords.getLatitude() < this.nLat)) {
                northings.add(k++, geocoords.getLatitude());
            }*/

            // calculate  line segments of one e-w line
            for (m=sw_utm_e,n=0; m<=ne_utm_e; m+=precision,n++) {

                GeoPoint result = USNGUtil.UTMtoLLpro4jGeo(new ProjCoordinate(m, i, 0), cellZone, cellLetter);

                temp.add(n, result);
            }

            // clipping routine...clip e-w grid lines to GZD boundary
            // case of final point in the array is not covered
            for (p=0; p < temp.size()-1 ; p++) {
                if (clipToGZD(temp,p)) {
                    gr100kCoord.add(temp.get(p));
                }
            }

            this.gridlines.add(USNGUtil.createPolyline(gr100kCoord));  // array of e-w grid line segments
        }

        northings.add(k++, this.nLat);

        // for 1,000-meter grid and finer, don't label zone boundaries
        if (this.interval > 1000) { eastings.add(0, wLng_temp); }  // west bounary of viewport
        k=1;

        // for each n-s line that covers the cell, with overedge
        for (i=sw_utm_e; i<ne_utm_e; i+=this.interval,j++) {
            geocoords = new GeoPoint(0.0,0.0);
            ArrayList<ProjCoordinate> temp = new ArrayList<>();   // holds extended lines
            ArrayList<GeoPoint> gr100kCoord = new ArrayList<>();  // holds lines clipped to GZD bounds

            // collect coords to be used to place markers
            // '2*this.interval' is a fudge factor that approximately offsets grid line convergence
            //USNGUtil.UTMtoLL(sw_utm_n+(2*this.interval),i,zone,geocoords);

            if (geocoords.getLongitude() > wLng_temp && geocoords.getLongitude() < eLng_temp) {
                eastings.add(k++, geocoords.getLongitude());
            }

            for (m=sw_utm_n,n=0; m<=ne_utm_n; m+=precision,n++) {
                //USNGUtil.UTMtoLL(m,i,zone,geocoords);
                //temp.add(n, USNGUtil.toEPSG3857fromEPSG4326(new ProjCoordinate(geocoords.getLongitude(), geocoords.getLatitude())));
            }

            // clipping routine...clip n-s grid lines to GZD boundary
            for (p=0; p<temp.size()-1; p++) {
                //if (this.clipToGZD(temp,p)) {
                 //   gr100kCoord.add(new GeoPoint(temp.get(p).y, temp.get(p).x));
                //}
            }


            //this.gridlines.add(USNGUtil.createPolyline(gr100kCoord));  // array of n-s grid line segments
        }
        eastings.add(k, eLng_temp); //this.elng  // east boundary of viewport

////???? not sure if needed???
//    if (viewport.idl_model != 0) {
//        eastings[k] = fix_idl_lng(viewport.idl_model,elng_temp); //this.elng);
//    }
////???? not sure if needed???

        /*
        if (this.interval == 100000) {
            this.labels100k(eastings, northings);
        }
        else if (this.interval == 10000) {
            this.labels10k(eastings, northings);
        }
        else if (this.interval == 1000) {
            this.labels1k(eastings, northings);
        }
        */

    }  // end computeOneCell

    private boolean clipToGZD (List<GeoPoint> coordinates, int index) {
        double temp;
        double t;
        double u1 = coordinates.get(index).getLongitude();
        double v1 = coordinates.get(index).getLatitude();
        double u2 = coordinates.get(index+1).getLongitude();
        double v2 = coordinates.get(index+1).getLatitude();
        byte code1 = outCode(v1, u1);
        byte code2 = outCode(v2, u2);
        /* if initial point is completely out of the zone
        if (code1 == 6 || code1 == 5 || code1 == 9 || code1 == 10) {
            return false;
        }
        if (code2 == 6 || code2 == 5 || code2 == 9 || code2 == 10) {
            return false;
        }*/
        if ((code1 & code2) != 0) { // line segment outside window...don't draw it
            return false;
        }
        if ((code1 | code2) == 0) { // line segment completely inside window... draw it
            return true;
        }
        if (inside(v1,u1)) { // coordinates must be altered
            // swap coordinates
            temp = u1;
            u1 = u2;
            u2 = temp;
            temp = v1;
            v1 = v2;
            v2 = temp;
            byte tempByte;
            tempByte = code1;
            code1 = code2;
            code2 = tempByte;
        }
        if ((code1 & 8) == 8) {// clip along northern edge of polygon
            t = (this.nLat - v1)/(v2-v1);
            u1 += t*(u2-u1);
            v1 = this.nLat;
            //cp[p] = new GLatLng(v1,u1)
            coordinates.get(index).setLongitude(u1);
            coordinates.get(index).setLatitude(v1);
        }
        else if ((code1 & 4) == 4) { // clip along southern edge
            t = (this.sLat - v1)/(v2-v1);
            u1 += t*(u2-u1);
            v1 = this.sLat;
            //cp[p] = new GLatLng(v1,u1)
            coordinates.get(index).setLongitude(u1);
            coordinates.get(index).setLatitude(v1);
        }
        else if ((code1 & 1) == 1) { // clip along west edge
            t = (this.wLng - u1)/(u2-u1);
            v1 += t*(v2-v1);
            u1 = this.wLng;
            //cp[p] = new GLatLng(v1,u1)
            coordinates.get(index).setLongitude(u1);
            coordinates.get(index).setLatitude(v1);
        }
        else if ((code1 & 2) == 2) { // clip along east edge
            t = (this.eLng - u1)/(u2-u1);
            v1 += t*(v2-v1);
            u1 = this.eLng;
            //cp[p] = new GLatLng(v1,u1)
            coordinates.get(index).setLongitude(u1);
            coordinates.get(index).setLatitude(v1);
        }

        return true;
    }

    private byte outCode (double lat, double lng) {
        byte code = 0;

        if (lat < this.sLat) { code |= 4; }
        if (lat > this.nLat) { code |= 8; }
        if (lng < this.wLng) { code |= 1; }
        if (lng > this.eLng) { code |= 2; }

        return code;
    }

    private boolean inside(double lat, double lng) {
        if (lat < this.sLat || lat > this.nLat) {
            return false;
        }
        if (lng < this.wLng || lng > this.eLng) {
            return false;
        }
        return true;
    }

    private double fixIdlLng (int model, double longitude) {
        // viewport contains IDL and uses west hemisphere coordinate system
        if (model == 1) {
            if (longitude > 0) { longitude -= 360; }
        }
        // viewport contains IDL and uses east hemisphere coordinate system
        else if (model == 2) {
            if (longitude == -180) { longitude = 179.9999; }
            else if (longitude < 0) { longitude += 360; }
        }

        return longitude;
    }
}
