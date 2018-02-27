package org.osmdroid.gpkg.overlay.gridlines.usng;

import android.graphics.Color;
import android.graphics.Point;

import org.osgeo.proj4j.CRSFactory;
import org.osgeo.proj4j.CoordinateReferenceSystem;
import org.osgeo.proj4j.CoordinateTransform;
import org.osgeo.proj4j.CoordinateTransformFactory;
import org.osgeo.proj4j.ProjCoordinate;
import org.osgeo.proj4j.datum.Ellipsoid;
import org.osgeo.proj4j.proj.Projection;
import org.osgeo.proj4j.util.CRSCache;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polyline;

import java.util.ArrayList;
import java.util.List;

import mil.nga.geopackage.core.srs.SpatialReferenceSystem;
import mil.nga.geopackage.projection.ProjectionFactory;

/**
 * Created by Jason on 11/13/2017.
 */

public class USNGUtil {

    /**
     * EPSG:4326 parameters
     * <p>
     * +proj=longlat
     * +datum=WGS84
     * +no_defs
     */
    private static final String[] EPSG_4326_PARAMETERS = {"+proj=longlat", "+datum=WGS84", "+no_defs"};

    /**
     * EPSG:3857 parameters
     * <p>
     * +proj=merc
     * +a=6378137
     * +b=6378137
     * +lat_ts=0.0
     * +lon_0=0.0
     * +x_0=0.0
     * +y_0=0
     * +k=1.0
     * +units=m
     * +nadgrids=@null
     * +wktext
     * +no_defs
     */
    private static final String[] EPSG_3857_PARAMETERS = {"+proj=merc", "+a=6378137", "+b=6378137",
            "+lat_ts=0.0", "+lon_0=0.0", "+x_0=0.0", "+y_0=0", "+k=1.0", "+units=m", "+nadgrids=@null",
            "+wktext", "+no_defs"};

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
    private static final double EASTING_OFFSET = 500000.0;   // (meters)
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

        int lonTemp = (int) ((lon + 180) - (int) ((lon + 180) / 360) * 360 - 180);
        int zoneNumber = (int) ((lonTemp + 180) / 6) + 1;

        // Handle special case of west coast of Norway
        if (lat >= 56.0 && lat < 64.0 && lonTemp >= 3.0 && lonTemp < 12.0) {
            zoneNumber = 32;
        }

        // Special zones for Svalbard
        if (lat >= 72.0 && lat < 84.0) {
            if (lonTemp >= 0.0 && lonTemp < 9.0) {
                zoneNumber = 31;
            } else if (lonTemp >= 9.0 && lonTemp < 21.0) {
                zoneNumber = 33;
            } else if (lonTemp >= 21.0 && lonTemp < 33.0) {
                zoneNumber = 35;
            } else if (lonTemp >= 33.0 && lonTemp < 42.0) {
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
        else if ((8 > lat) && (lat >= 0))
            letterDesignator = 'N';
        else if ((0 > lat) && (lat >= -8))
            letterDesignator = 'M';
        else if ((-8 > lat) && (lat >= -16))
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
    private static int findSet(int zoneNum) {
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
        while (east_1m >= BLOCK_SIZE) {
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
        } else {
            row--;
        }

        // handle case of last column
        if (col == 0) {
            col = GRIDSQUARE_SET_COL_SIZE - 1;
        } else {
            col--;
        }

        String columnIds = new String();
        String rowIds = new String();
        StringBuilder sb = new StringBuilder();

        switch (set) {
            case 1:
                columnIds = "ABCDEFGH";              // column ids
                rowIds = "ABCDEFGHJKLMNPQRSTUV";  // row ids
                break;
            case 2:
                columnIds = "JKLMNPQR";
                rowIds = "FGHJKLMNPQRSTUVABCDE";
                break;
            case 3:
                columnIds = "STUVWXYZ";
                rowIds = "ABCDEFGHJKLMNPQRSTUV";
                break;
            case 4:
                columnIds = "ABCDEFGH";
                rowIds = "FGHJKLMNPQRSTUVABCDE";
                break;
            case 5:
                columnIds = "JKLMNPQR";
                rowIds = "ABCDEFGHJKLMNPQRSTUV";
                break;
            case 6:
                columnIds = "STUVWXYZ";
                rowIds = "FGHJKLMNPQRSTUVABCDE";
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
    public static Polyline createPolyline(List<GeoPoint> geoPoints) {
        Polyline polyline = new Polyline();
        polyline.setWidth(2.0f);
        polyline.setColor(Color.BLACK);
        polyline.setPoints(geoPoints);
        return polyline;
    }

    /**
     * From EPSG 900913/3857 To EPSG:4326
     */
    public static GeoPoint toEPSG4326fromEPSG3857GeoPoint(GeoPoint coord) {
        CRSCache cache = new CRSCache();
        CoordinateReferenceSystem EPSG_4326_COORD_REF = cache.createFromName("EPSG:4326");
        CoordinateReferenceSystem EPSG_3857_COORD_REF = cache.createFromName("EPSG:3857");

        CoordinateTransformFactory f = new CoordinateTransformFactory();
        CoordinateTransform t = f.createTransform(
                EPSG_3857_COORD_REF,
                EPSG_4326_COORD_REF);

        ProjCoordinate dest = new ProjCoordinate();
        t.transform(new ProjCoordinate(coord.getLongitude(), coord.getLatitude()), dest);

        return new GeoPoint(dest.y, dest.x);
    }

    /**
     * From EPSG:4326 To EPSG:900913/3857
     */
    public static ProjCoordinate toEPSG3857fromEPSG4326(ProjCoordinate source) {
        CRSCache cache = new CRSCache();
        CoordinateReferenceSystem EPSG_4326_COORD_REF = cache.createFromName("EPSG:4326");
        CoordinateReferenceSystem EPSG_3857_COORD_REF = cache.createFromName("EPSG:3857");

        CoordinateTransformFactory f = new CoordinateTransformFactory();
        CoordinateTransform t = f.createTransform(
                EPSG_4326_COORD_REF,
                EPSG_3857_COORD_REF);

        ProjCoordinate dest = new ProjCoordinate();
        t.transform(source, dest);

        return new ProjCoordinate((long) dest.x, (long) dest.y);
    }

    /**
     * //TODO preload the coordinate systems to be used
     * preload the CRS which will be needed
     */
    public static void initializeCRSCache() {
        CRSCache cache = new CRSCache();
        cache.createFromName("EPSG:4326");
        cache.createFromName("EPSG:3857");
    }


    /**
     *
     * @param projCoordinate
     * @param zone
     * @param letter
     */
    public static GeoPoint LLtoUTMpro4jGeo(ProjCoordinate projCoordinate, int zone, char letter) {

        // the epsg authority depends on the hemisphere and zone
        StringBuilder epsgAuthority = new StringBuilder();
        if (letter > 'N') {
            epsgAuthority.append("EPSG:326");
        } else {
            epsgAuthority.append("EPSG:327");
        }
        epsgAuthority.append(zone);

        // load the CRS for each system
        CRSCache crsCache = new CRSCache();
        CoordinateReferenceSystem epsg4326 = crsCache.createFromName("EPSG:4326");
        CoordinateReferenceSystem epsgUTM = crsCache.createFromName(epsgAuthority.toString());

        //
        CoordinateTransformFactory transform = new CoordinateTransformFactory();
        CoordinateTransform t = transform.createTransform(epsgUTM, epsg4326);

        ProjCoordinate dest = new ProjCoordinate();
        t.transform(projCoordinate, dest);

        return new GeoPoint(dest.y, dest.x);
    }

    public static GeoPoint UTMtoLLpro4jGeo(ProjCoordinate projCoordinate, int zone, char letter) {

        // the epsg authority depends on the hemisphere and zone
        StringBuilder epsgAuthority = new StringBuilder();
        if (letter > 'N') {
            epsgAuthority.append("EPSG:326");
        } else {
            epsgAuthority.append("EPSG:327");
        }
        epsgAuthority.append(zone);

        // load the CRS for each system
        CRSCache crsCache = new CRSCache();
        CoordinateReferenceSystem epsg4326 = crsCache.createFromName("EPSG:4326");
        CoordinateReferenceSystem epsgUTM = crsCache.createFromName(epsgAuthority.toString());

        //
        CoordinateTransformFactory transform = new CoordinateTransformFactory();
        CoordinateTransform t = transform.createTransform(epsgUTM, epsg4326);

        ProjCoordinate dest = new ProjCoordinate();
        t.transform(projCoordinate, dest);

        return new GeoPoint(dest.y, dest.x);
    }

    /**
     *
     * @param projCoordinate
     * @param zone
     * @param letter
     * @return
     */
    public static ProjCoordinate LLtoUTMpro4jProj(ProjCoordinate projCoordinate, int zone, char letter) {

        // the epsg authority depends on the hemisphere and zone
        StringBuilder epsgAuthority = new StringBuilder();
        if (letter > 'N') {
            epsgAuthority.append("EPSG:326");
        } else {
            epsgAuthority.append("EPSG:327");
        }
        epsgAuthority.append(zone);

        // load the CRS for each system
        CRSCache crsCache = new CRSCache();
        CoordinateReferenceSystem epsg4326 = crsCache.createFromName("EPSG:4326");
        CoordinateReferenceSystem epsgUTM = crsCache.createFromName(epsgAuthority.toString());

        //
        CoordinateTransformFactory transform = new CoordinateTransformFactory();
        CoordinateTransform t = transform.createTransform(epsg4326, epsgUTM);

        ProjCoordinate dest = new ProjCoordinate();
        t.transform(projCoordinate, dest);

        return dest;
    }
}
