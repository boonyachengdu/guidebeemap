//------------------------------------------------------------------------------
//                         COPYRIGHT 2010 GUIDEBEE
//                           
//                     
///////////////////////////////////// REVISIONS ////////////////////////////////
// Date       name                 Tracking #         Description
// ---------  -------------------  ----------         --------------------------
// 28DEC2010  James Shen                 	      Code review
////////////////////////////////////////////////////////////////////////////////
//--------------------------------- PACKAGE ------------------------------------
package com.mapdigit.gis.service.cloudmade;

//--------------------------------- IMPORTS ------------------------------------
import com.mapdigit.gis.geometry.GeoBounds;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import com.mapdigit.gis.MapPoint;
import com.mapdigit.gis.geometry.GeoLatLng;

import com.mapdigit.ajax.Arg;
import com.mapdigit.ajax.Request;
import com.mapdigit.ajax.IRequestListener;
import com.mapdigit.ajax.Response;
import com.mapdigit.ajax.Result;
import com.mapdigit.gis.service.DigitalMapService;
import com.mapdigit.gis.service.IGeocodingListener;
import com.mapdigit.gis.service.ILocalSearch;
import com.mapdigit.gis.service.SearchOptions;
import com.mapdigit.util.Log;
import com.mapdigit.util.Utils;
import com.mapdigit.network.HttpConnection;

//[------------------------------ MAIN CLASS ----------------------------------]
////////////////////////////////////////////////////////////////////////////////
//--------------------------------- REVISIONS ----------------------------------
// Date       Name                 Tracking #         Description
// --------   -------------------  -------------      --------------------------
// 28DEC2010  James Shen                 	      Code review
////////////////////////////////////////////////////////////////////////////////
/**
 * This class is used to communicate directly with cloudmade servers to obtain
 * geocodes for user specified addresses. In addition, a geocoder maintains
 * its own cache of addresses, which allows repeated queries to be answered
 * without a round trip to the server.
 * <hr><b>&copy; Copyright 2010 Guidebee Pty Ltd. </b>
 * @version     2.00, 28/12/10
 * @author      Guidebee Pty Ltd.
 */
public final class CLocalSearch implements ILocalSearch {

    ////////////////////////////////////////////////////////////////////////////
    //--------------------------------- REVISIONS ------------------------------
    // Date       Name                 Tracking #         Description
    // ---------  -------------------  -------------      ----------------------
    // 30DEC2010  James Shen                 	          Code review
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @inheritDoc
     */
    public void getLocations(String address, int start, GeoLatLng center,
            GeoBounds bound, IGeocodingListener listener) {
        getLocations(-1,address,start,center,bound,listener);
    }

    ////////////////////////////////////////////////////////////////////////////
    //--------------------------------- REVISIONS ------------------------------
    // Date       Name                 Tracking #         Description
    // ---------  -------------------  -------------      ----------------------
    // 30DEC2010  James Shen                 	          Code review
    ////////////////////////////////////////////////////////////////////////////
    /**
     * @inheritDoc
     */
    public void getLocations(int mapType, String address, int start,
            GeoLatLng center, GeoBounds bound, IGeocodingListener listener) {
        this.listener = listener;
        searchAddress = address;
        try {
            address = com.mapdigit.util.HTML2Text.encodeutf8(address.getBytes("utf-8"));
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace();
        }
        queryKey = CloudMadeMapService.getCloudMadeKey();
        MapPoint mapPoint = (MapPoint) addressCache.get(searchAddress);
        if (mapPoint == null) {

            String searchBase=replaceMetaString(SEARCH_BASE);
            Vector argList = new Vector();
            argList.addElement(new Arg("query", address));
            SearchOptions routeOptions = DigitalMapService.getSearchOptions();
            argList.addElement(new Arg("results", String.valueOf(routeOptions.NumberOfSearchResult)));
            argList.addElement(new Arg("bbox",bound.getMinY()+","+bound.getMinX()+","+bound.getMaxY()+","+bound.getMaxX()));
            argList.addElement(new Arg("return_location", "true"));
            argList.addElement(new Arg("skip", String.valueOf(start)));
            final Arg[] args = new Arg[argList.size() + 1];
            argList.copyInto(args);
            args[argList.size()] = null;
            Request.get(searchBase, args, null, addressQuery, this);


        } else {
            MapPoint[] mapPoints = new MapPoint[1];
            mapPoints[0] = mapPoint;
            listener.done(mapPoint.name, mapPoints);
        }
    }



    private Hashtable addressCache = new Hashtable();
    private String searchAddress = null;
    private IGeocodingListener listener = null;
    private LocalAddressQuery addressQuery = new LocalAddressQuery();
    private static final String SEARCH_BASE = "http://geocoding.cloudmade.com/{CLOUDMADE_KEY}/geocoding/v2/find.js";
    private String queryKey = "8ee2a50541944fb9bcedded5165f09d9";


     private String replaceMetaString(String input) {

        String[] pattern = new String[]{
            "{CLOUDMADE_KEY}",
            " "
        };

        String[] replace = new String[]{
            queryKey,
            "+"
        };

        String url = Utils.replace(pattern, replace, input);
        return url;
    }
    private class LocalAddressQuery implements IRequestListener {

        public void readProgress(Object context, int bytes, int total) {
            if (context instanceof CLocalSearch) {
                CLocalSearch geoCoder = (CLocalSearch) context;
                if (geoCoder.listener != null) {
                    geoCoder.listener.readProgress(bytes, total);
                }
            }
        }

        public void writeProgress(Object context, int bytes, int total) {
        }

        public void done(Object context, Response response) throws Exception {
            if (context instanceof CLocalSearch) {
                CLocalSearch geoCoder = (CLocalSearch) context;
                searchResponse(geoCoder, response);
            }
        }

        private void searchResponse(CLocalSearch geoCoder, final Response response) {
            MapPoint[] mapPoints = null;
            final Throwable ex = response.getException();
            if (ex != null || response.getCode() != HttpConnection.HTTP_OK) {
                if (ex instanceof OutOfMemoryError) {
                    Log.p("Dont have enough memory", Log.ERROR);
                    if (geoCoder.listener != null) {
                        geoCoder.listener.done(null, null);
                    }
                } else {
                    Log.p("Error connecting to search service", Log.ERROR);
                    if (geoCoder.listener != null) {
                        geoCoder.listener.done(geoCoder.searchAddress, null);
                    }
                }

                return;
            }
            try {
                final Result result = response.getResult();
                String prefix = "";

                final int resultCount = result.getSizeOfArray(prefix + "features");
                if (resultCount > 0) {

                    mapPoints = new MapPoint[resultCount];
                    for (int i = 0; i < resultCount; i++) {
                        mapPoints[i] = new MapPoint();
                        mapPoints[i].name = result.getAsString(prefix + "features[" + i + "].properties.name");
                        double []latLngArray=result.getAsDoubleArray(prefix + "features[" + i + "].centroid.coordinates");
                        String address=result.getAsString(prefix + "features[" + i + "].location.road");
                        address+=","+result.getAsString(prefix + "features[" + i + "].location.city");
                        address+=","+result.getAsString(prefix + "features[" + i + "].location.county");

                        mapPoints[i].setNote(Utils.replace("''''", address, "'")) ;
                        mapPoints[i].setPoint(new GeoLatLng(latLngArray[0],latLngArray[1]));

                    }

                    if (geoCoder.addressCache.size() > 24) {
                        for (int j = 0; j < 12; j++) {
                            Enumeration keys = geoCoder.addressCache.keys();
                            String key1 = (String) keys.nextElement();
                            geoCoder.addressCache.remove(key1);
                        }
                    }
                    geoCoder.addressCache.put(mapPoints[0].name, mapPoints[0]);

                }
            } catch (OutOfMemoryError ome) {
                System.gc();
                System.gc();
            } catch (Exception rex) {
                Log.p("Error extracting result information:" + rex.getMessage(), Log.ERROR);

            }
            if (geoCoder.listener != null) {
                geoCoder.listener.done(geoCoder.searchAddress, mapPoints);
            }

        }
    }
}

