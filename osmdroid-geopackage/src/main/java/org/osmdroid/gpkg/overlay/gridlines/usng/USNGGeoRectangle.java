package org.osmdroid.gpkg.overlay.gridlines.usng;

import org.osmdroid.util.GeoPoint;

/**
 * Created by Jason on 11/13/2017.
 */

public class USNGGeoRectangle {
    private double nLat;
    private double sLat;
    private double wLng;
    private double eLng;
    private double centerLat;
    private double centerLng;

    public USNGGeoRectangle() {
        nLat = 0;
        sLat = 0;
        wLng = 0;
        eLng = 0;
        centerLat = 0;
        centerLng = 0;
    }

    public void assignCorners (double sLat, double nLat, double wLng, double eLng) {
        this.nLat = nLat;
        this.sLat = sLat;

        // special case: Norway
        if (sLat==56 && wLng==0) {
            this.wLng = wLng;
            this.eLng = eLng-3;
        }
        else if (sLat==56 && wLng==6) {
            this.wLng = wLng-3;
            this.eLng = eLng;
        }
        // special case: Svalbard
        else if (sLat==72 && wLng==0) {
            this.wLng = wLng;
            this.eLng = eLng+3;
        }
        else if (sLat==72 && wLng==12) {
            this.wLng = wLng-3;
            this.eLng = eLng+3;
        }
        else if (sLat==72 && wLng==36) {
            this.wLng = wLng-3;
            this.eLng = eLng;
        }
        else {
            this.wLng = wLng;
            this.eLng = eLng;
        }
    }

    public void assignCenter () {
        centerLat = (nLat+sLat)/2;
        centerLng = (wLng+eLng)/2;
    }

    public GeoPoint getCenter() {
        return new GeoPoint(centerLat, centerLng);
    }

    public GeoPoint getNW() {
        return new GeoPoint(nLat, wLng);
    }

    public GeoPoint getSW() {
        return new GeoPoint(sLat, wLng);
    }

    public GeoPoint getSE() {
        return new GeoPoint(sLat, eLng);
    }

    public GeoPoint getNE() {
        return new GeoPoint(nLat, eLng);
    }

    public double getnLat() {
        return nLat;
    }

    public double getsLat() {
        return sLat;
    }

    public double getwLng() {
        return wLng;
    }

    public double geteLng() {
        return eLng;
    }
}
