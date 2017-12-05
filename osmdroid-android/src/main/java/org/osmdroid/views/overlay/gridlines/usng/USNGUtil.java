package org.osmdroid.views.overlay.gridlines.usng;

import android.graphics.Color;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Jason on 11/13/2017.
 */

public class USNGUtil {

    /**
     * Constants
     */
    private static final double FOURTHPI = Math.PI / 4;
    private static final double DEG_2_RAD = Math.PI / 180;
    private static final double RAD_2_DEG = Math.PI / 180;
    // size of square identifier (within grid zone designation), (meters)
    private static final int BLOCK_SIZE = 100000;

    // if false, assumes NAD27 datum
    public static boolean IS_NAD83_DATUM = true;

    // For diagram of zone sets, please see the "United States National Grid" white paper.
    private static final int GRIDSQUARE_SET_COL_SIZE = 8;  // column width of grid square set
    private static final int GRIDSQUARE_SET_ROW_SIZE = 20; // row height of grid square set

    // UTM offsets
    private static final double EASTING_OFFSET  = 500000.0;   // (meters)
    private static final double NORTHING_OFFSET = 10000000.0; // (meters)

    // scale factor of central meridian
    private static final double k0 = 0.9996;

    private static final double EQUATORIAL_RADIUS;
    private static final double ECC_PRIME_SQUARED;
    private static final double ECC_SQUARED;
    private static final double E1;

    static {
        // check for NAD83
        if (IS_NAD83_DATUM) {
            EQUATORIAL_RADIUS = 6378137.0; // GRS80 ellipsoid (meters)
            ECC_SQUARED = 0.006694380023;
        }
        // else NAD27 datum is assumed
        else {
            EQUATORIAL_RADIUS = 6378206.4;  // Clarke 1866 ellipsoid (meters)
            ECC_SQUARED = 0.006768658;
        }

        ECC_PRIME_SQUARED = ECC_SQUARED / (1 - ECC_SQUARED);

        // variable used in inverse formulas (UTMtoLL function)
        E1 = (1 - Math.sqrt(1 - ECC_SQUARED)) / (1 + Math.sqrt(1 - ECC_SQUARED));
    }

    // Number of digits to display for x,y coords
    //  One digit:    10 km precision      eg. "18S UJ 2 1"
    //  Two digits:   1 km precision       eg. "18S UJ 23 06"
    //  Three digits: 100 meters precision eg. "18S UJ 234 064"
    //  Four digits:  10 meters precision  eg. "18S UJ 2348 0647"
    //  Five digits:  1 meter precision    eg. "18S UJ 23480 06470"

    /************* retrieve zone number from latitude, longitude *************

     Zone number ranges from 1 - 60 over the range [-180 to +180]. Each
     range is 6 degrees wide. Special cases for points outside normal
     [-80 to +84] latitude zone.

     Returns null if the latitude or longitude are not valid

     *************************************************************************/

    public static Integer getZoneNumber(double lat, double lon) {

        // sanity check on input
        if (lon > 360 || lon < -180 || lat > 84 || lat < -80) {
            return null;
        }

        int lonTemp = (int)((lon + 180) - ((lon + 180) / 360) * 360 - 180);
        int zoneNumber = (int)((lonTemp + 180) / 6) + 1;

        // Handle special case of west coast of Norway
        if ( lat >= 56.0 && lat < 64.0 && lonTemp >= 3.0 && lonTemp < 12.0 ) {
            zoneNumber = 32;
        }

        // Special zones for Svalbard
        if ( lat >= 72.0 && lat < 84.0 ) {
            if ( lonTemp >= 0.0  && lonTemp <  9.0 ) {
                zoneNumber = 31;
            }
            else if ( lonTemp >= 9.0  && lonTemp < 21.0 ) {
                zoneNumber = 33;
            }
            else if ( lonTemp >= 21.0 && lonTemp < 33.0 ) {
                zoneNumber = 35;
            }
            else if ( lonTemp >= 33.0 && lonTemp < 42.0 ) {
                zoneNumber = 37;
            }
        }
        return zoneNumber;
    }

    /************** retrieve grid zone designator letter **********************

     This routine determines the correct UTM letter designator for the given
     latitude returns 'Z' if latitude is outside the UTM limits of 84N to 80S

     Returns letter designator for a given latitude.
     Letters range from C (-80 lat) to X (+84 lat), with each zone spanning
     8 degrees of latitude.

     ***************************************************************************/

    public static char UTMLetterDesignator(double lat) {
        char letterDesignator = 'Z';

        if ((84 >= lat) && (lat >= 72))
            letterDesignator = 'X';
        else if ((72 > lat) && (lat >= 64))
            letterDesignator = 'W';
        else if ((64 > lat) && (lat >= 56))
            letterDesignator = 'V';
        else if ((56 > lat) && (lat >= 48))
            letterDesignator = 'U';
        else if ((48 > lat) && (lat >= 40))
            letterDesignator = 'T';
        else if ((40 > lat) && (lat >= 32))
            letterDesignator = 'S';
        else if ((32 > lat) && (lat >= 24))
            letterDesignator = 'R';
        else if ((24 > lat) && (lat >= 16))
            letterDesignator = 'Q';
        else if ((16 > lat) && (lat >= 8))
            letterDesignator = 'P';
        else if (( 8 > lat) && (lat >= 0))
            letterDesignator = 'N';
        else if (( 0 > lat) && (lat >= -8))
            letterDesignator = 'M';
        else if ((-8> lat) && (lat >= -16))
            letterDesignator = 'L';
        else if ((-16 > lat) && (lat >= -24))
            letterDesignator = 'K';
        else if ((-24 > lat) && (lat >= -32))
            letterDesignator = 'J';
        else if ((-32 > lat) && (lat >= -40))
            letterDesignator = 'H';
        else if ((-40 > lat) && (lat >= -48))
            letterDesignator = 'G';
        else if ((-48 > lat) && (lat >= -56))
            letterDesignator = 'F';
        else if ((-56 > lat) && (lat >= -64))
            letterDesignator = 'E';
        else if ((-64 > lat) && (lat >= -72))
            letterDesignator = 'D';
        else if ((-72 > lat) && (lat >= -80))
            letterDesignator = 'C';
        else
            letterDesignator = 'Z'; // This is here as an error flag to show
        // that the latitude is outside the UTM limits
        return letterDesignator;
    }

    /****************** Find the set for a given zone. ************************

     There are six unique sets, corresponding to individual grid numbers in
     sets 1-6, 7-12, 13-18, etc. Set 1 is the same as sets 7, 13, ..; Set 2
     is the same as sets 8, 14, ..

     See p. 10 of the "United States National Grid" white paper.

     ***************************************************************************/
    private static int findSet (int zoneNum) {
        zoneNum = zoneNum % 6;

        switch (zoneNum) {
            case 0:
                return 6;
            case 1:
                return 1;
            case 2:
                return 2;
            case 3:
                return 3;
            case 4:
                return 4;
            case 5:
                return 5;
            default:
                return -1;
        }
    }

    /**************************************************************************
     Retrieve the square identification for a given coordinate pair & zone
     See "lettersHelper" function documentation for more details.

     ***************************************************************************/

    private static String findGridLetters(int zoneNum, double northing, double easting) {
        int row = 1;

        // northing coordinate to single-meter precision
        double north_1m = Math.round(northing);

        // Get the row position for the square identifier that contains the point
        while (north_1m >= BLOCK_SIZE) {
            north_1m = north_1m - BLOCK_SIZE;
            row++;
        }

        // cycle repeats (wraps) after 20 rows
        row = row % GRIDSQUARE_SET_ROW_SIZE;
        int col = 0;

        // easting coordinate to single-meter precision
        double east_1m = Math.round(easting);

        // Get the column position for the square identifier that contains the point
        while (east_1m >= BLOCK_SIZE){
            east_1m = east_1m - BLOCK_SIZE;
            col++;
        }

        // cycle repeats (wraps) after 8 columns
        col = col % GRIDSQUARE_SET_COL_SIZE;

        return lettersHelper(findSet(zoneNum), row, col);
    }

    /**************************************************************************
     Retrieve the Square Identification (two-character letter code), for the
     given row, column and set identifier (set refers to the zone set:
     zones 1-6 have a unique set of square identifiers; these identifiers are
     repeated for zones 7-12, etc.)

     See p. 10 of the "United States National Grid" white paper for a diagram
     of the zone sets.

     ***************************************************************************/

    private static String lettersHelper(int set, int row, int col) {

        // handle case of last row
        if (row == 0) {
            row = GRIDSQUARE_SET_ROW_SIZE - 1;
        }
        else {
            row--;
        }

        // handle case of last column
        if (col == 0) {
            col = GRIDSQUARE_SET_COL_SIZE - 1;
        }
        else {
            col--;
        }

        String columnIds = new String();
        String rowIds = new String();
        StringBuilder sb = new StringBuilder();

        switch(set) {
            case 1:
                columnIds="ABCDEFGH";              // column ids
                rowIds="ABCDEFGHJKLMNPQRSTUV";  // row ids
                break;
            case 2:
                columnIds="JKLMNPQR";
                rowIds="FGHJKLMNPQRSTUVABCDE";
                break;
            case 3:
                columnIds="STUVWXYZ";
                rowIds="ABCDEFGHJKLMNPQRSTUV";
                break;
            case 4:
                columnIds="ABCDEFGH";
                rowIds="FGHJKLMNPQRSTUVABCDE";
                break;
            case 5:
                columnIds="JKLMNPQR";
                rowIds="ABCDEFGHJKLMNPQRSTUV";
                break;
            case 6:
                columnIds="STUVWXYZ";
                rowIds="FGHJKLMNPQRSTUVABCDE";
                break;
        }

        return sb.append(columnIds.charAt(col)).append(rowIds.charAt(row)).toString();
    }

    /**
     * Creates a polyline with the argument GeoPoints and applies the appearance defaults
     * of the line
     *
     * @param geoPoints a list of osmdroid GeoPoints
     * @return the built Polyline object
     */
    public static Polyline createPolyline (List<GeoPoint> geoPoints) {
        Polyline polyline = new Polyline();
        polyline.setWidth(2.0f);
        polyline.setColor(Color.BLACK);
        polyline.setPoints(geoPoints);
        return polyline;
    }

    /***************** convert latitude, longitude to UTM  *******************

     Converts lat/long to UTM coords.  Equations from USGS Bulletin 1532
     (or USGS Professional Paper 1395 "Map Projections - A Working Manual",
     by John P. Snyder, U.S. Government Printing Office, 1987.)

     East Longitudes are positive, West longitudes are negative.
     North latitudes are positive, South latitudes are negative
     lat and lon are in decimal degrees

     output is in the input array utmcoords
     utmcoords[0] = easting
     utmcoords[1] = northing (NEGATIVE value in southern hemisphere)
     utmcoords[2] = zone

     ***************************************************************************/
    public static final String UNDEFINED_STR = "undefined";
    public static void LLtoUTM(double lat, double lon, ArrayList<Object> utmcoords, Integer zone) throws Exception {
        // utmcoords is a 2-D array declared by the calling routine
        // note: input of lon = 180 or -180 with zone 60 not allowed; use 179.9999

        String latS = Double.toString(lat);
        String lonS = Double.toString(lon);

        // Constrain reporting USNG coords to the latitude range [80S .. 84N]
        /////////////////
        if (lat > 84.0 || lat < -80.0){
            throw new Exception("USNGUtil, LLtoUTM" + UNDEFINED_STR);
        }
        //////////////////////


        // sanity check on input - turned off when testing with Generic Viewer
        if (lon > 360 || lon < -180 || lat > 90 || lat < -90) {
            throw new Exception("USNGUtil, LLtoUTM, invalid input. lat: " + latS + " lon: " + lonS);
        }


        // Make sure the longitude is between -180.00 .. 179.99..
        // Convert values on 0-360 range to this range.
        double lonTemp = (lon + 180) - ((lon + 180) / 360) * 360 - 180;
        double latRad = lat     * DEG_2_RAD;
        double lonRad = lonTemp * DEG_2_RAD;

        Integer zoneNumber;

        // user-supplied zone number will force coordinates to be computed in a particular zone
        if (zone == null) {
            zoneNumber = getZoneNumber(lat, lon);
        }
        else {
            zoneNumber = zone;
        }

        double lonOrigin = (zoneNumber - 1) * 6 - 180 + 3;  // +3 puts origin in middle of zone
        double lonOriginRad = lonOrigin * DEG_2_RAD;

        // compute the UTM Zone from the latitude and longitude
        String UTMZone = zoneNumber + "" + UTMLetterDesignator(lat) + " ";

        double N = EQUATORIAL_RADIUS / Math.sqrt(1 - ECC_SQUARED *
                Math.sin(latRad) * Math.sin(latRad));
        double T = Math.tan(latRad) * Math.tan(latRad);
        double C = ECC_PRIME_SQUARED * Math.cos(latRad) * Math.cos(latRad);
        double A = Math.cos(latRad) * (lonRad - lonOriginRad);

        // Note that the term Mo drops out of the "M" equation, because phi
        // (latitude crossing the central meridian, lambda0, at the origin of the
        //  x,y coordinates), is equal to zero for UTM.
        double M = EQUATORIAL_RADIUS * (( 1 - ECC_SQUARED / 4
                - 3 * (ECC_SQUARED * ECC_SQUARED) / 64
                - 5 * (ECC_SQUARED * ECC_SQUARED * ECC_SQUARED) / 256) * latRad
                - ( 3 * ECC_SQUARED / 8 + 3 * ECC_SQUARED * ECC_SQUARED / 32
                + 45 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 1024)
                * Math.sin(2 * latRad) + (15 * ECC_SQUARED * ECC_SQUARED / 256
                + 45 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 1024) * Math.sin(4 * latRad)
                - (35 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 3072) * Math.sin(6 * latRad));

        double UTMEasting = (k0 * N * (A + (1 - T + C) * (A * A * A) / 6
                + (5 - 18 * T + T * T + 72 * C - 58 * ECC_PRIME_SQUARED )
                * (A * A * A * A * A) / 120)
                + EASTING_OFFSET);

        double UTMNorthing = (k0 * (M + N * Math.tan(latRad) * ( (A * A) / 2 + (5 - T + 9
                * C + 4 * C * C ) * (A * A * A * A) / 24
                + (61 - 58 * T + T * T + 600 * C - 330 * ECC_PRIME_SQUARED )
                * (A * A * A * A * A * A) / 720)));

        utmcoords.add(0, UTMEasting);
        utmcoords.add(1, UTMNorthing);
        utmcoords.add(2, zoneNumber);
    }

    /**************  convert UTM coords to decimal degrees *********************

     Equations from USGS Bulletin 1532 (or USGS Professional Paper 1395)
     East Longitudes are positive, West longitudes are negative.
     North latitudes are positive, South latitudes are negative.

     Expected Input args:
     UTMNorthing   : northing-m (numeric), eg. 432001.8
     southern hemisphere NEGATIVE from equator ('real' value - 10,000,000)
     UTMEasting    : easting-m  (numeric), eg. 4000000.0
     UTMZoneNumber : 6-deg longitudinal zone (numeric), eg. 18

     lat-lon coordinates are turned in the object 'ret' : ret.lat and ret.lon

     ***************************************************************************/

    public static void UTMtoLL(double UTMNorthing, double UTMEasting, Integer UTMZoneNumber, GeoPoint ret) {

        // remove 500,000 meter offset for longitude
        double xUTM = UTMEasting - EASTING_OFFSET;
        double yUTM = UTMNorthing;
        double zoneNumber = UTMZoneNumber;

        // origin longitude for the zone (+3 puts origin in zone center)
        double lonOrigin = (zoneNumber - 1) * 6 - 180 + 3;

        // M is the "true distance along the central meridian from the Equator to phi
        // (latitude)
        double M = yUTM / k0;
        double mu = M / ( EQUATORIAL_RADIUS * (1 - ECC_SQUARED / 4 - 3 * ECC_SQUARED *
                ECC_SQUARED / 64 - 5 * ECC_SQUARED * ECC_SQUARED * ECC_SQUARED / 256 ));

        // phi1 is the "footprint latitude" or the latitude at the central meridian which
        // has the same y coordinate as that of the point (phi (lat), lambda (lon) ).
        double phi1Rad = mu + (3 * E1 / 2 - 27 * E1 * E1 * E1 / 32 ) * Math.sin( 2 * mu)
                + ( 21 * E1 * E1 / 16 - 55 * E1 * E1 * E1 * E1 / 32) * Math.sin( 4 * mu)
                + (151 * E1 * E1 * E1 / 96) * Math.sin(6 * mu);
        double phi1 = phi1Rad * RAD_2_DEG;

        // Terms used in the conversion equations
        double N1 = EQUATORIAL_RADIUS / Math.sqrt( 1 - ECC_SQUARED * Math.sin(phi1Rad) *
                Math.sin(phi1Rad));
        double T1 = Math.tan(phi1Rad) * Math.tan(phi1Rad);
        double C1 = ECC_PRIME_SQUARED * Math.cos(phi1Rad) * Math.cos(phi1Rad);
        double R1 = EQUATORIAL_RADIUS * (1 - ECC_SQUARED) / Math.pow(1 - ECC_SQUARED *
                Math.sin(phi1Rad) * Math.sin(phi1Rad), 1.5);
        double D = xUTM / (N1 * k0);

        // Calculate latitude, in decimal degrees
        double lat = phi1Rad - ( N1 * Math.tan(phi1Rad) / R1) * (D * D / 2 - (5 + 3 * T1 + 10
                * C1 - 4 * C1 * C1 - 9 * ECC_PRIME_SQUARED) * D * D * D * D / 24 + (61 + 90 *
                T1 + 298 * C1 + 45 * T1 * T1 - 252 * ECC_PRIME_SQUARED - 3 * C1 * C1) * D * D *
                D * D * D * D / 720);
        lat = lat * RAD_2_DEG;

        // Calculate longitude, in decimal degrees
        double lon = (D - (1 + 2 * T1 + C1) * D * D * D / 6 + (5 - 2 * C1 + 28 * T1 - 3 *
                C1 * C1 + 8 * ECC_PRIME_SQUARED + 24 * T1 * T1) * D * D * D * D * D / 120) /
                Math.cos(phi1Rad);

        lon = lonOrigin + lon * RAD_2_DEG;
        ret.setLatitude(lat);
        ret.setLongitude(lon);
    }
}
