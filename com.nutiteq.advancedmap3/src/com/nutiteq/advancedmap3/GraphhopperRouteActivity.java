package com.nutiteq.advancedmap3;

import java.io.File;
import java.io.FileFilter;

import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Path;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.Helper;
import com.graphhopper.util.Instruction;
import com.graphhopper.util.InstructionList;
import com.graphhopper.util.PointList;
import com.graphhopper.util.StopWatch;
import com.nutiteq.advancedmap3.listener.RouteMapEventListener;
import com.nutiteq.core.MapPos;
import com.nutiteq.core.MapRange;
import com.nutiteq.datasources.LocalVectorDataSource;
import com.nutiteq.filepicker.FilePickerActivity;
import com.nutiteq.layers.VectorLayer;
import com.nutiteq.styles.BalloonPopupStyleBuilder;
import com.nutiteq.styles.LineJointType;
import com.nutiteq.styles.LineStyleBuilder;
import com.nutiteq.styles.MarkerStyle;
import com.nutiteq.styles.MarkerStyleBuilder;
import com.nutiteq.ui.MapView;
import com.nutiteq.utils.BitmapUtils;
import com.nutiteq.vectorelements.BalloonPopup;
import com.nutiteq.vectorelements.Line;
import com.nutiteq.vectorelements.Marker;
import com.nutiteq.wrappedcommons.MapPosVector;

/**
 * A sample demonstrating how to use Graphhopper library to calculate offline routes
 * 
 * Requires that user has downloaded Graphhopper data package to SDCARD.
 * 
 * See https://github.com/nutiteq/hellomap3d/wiki/Offline-routing for details
 * and downloads
 */
public class GraphhopperRouteActivity extends VectorMapSampleBaseActivity implements
        FilePickerActivity {

    private GraphHopper gh;
    protected boolean errorLoading;
    protected boolean graphLoaded;
    protected boolean shortestPathRunning;
    private Marker startMarker;
    private Marker stopMarker;
    private MarkerStyle instructionUp;
    private MarkerStyle instructionLeft;
    private MarkerStyle instructionRight;
    private LocalVectorDataSource routeDataSource;
    private LocalVectorDataSource routeStartStopDataSource;
    private BalloonPopupStyleBuilder balloonPopupStyleBuilder;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // go to toronto
        mapView.setZoom(11,0);
        mapView.setFocusPos(baseProjection.fromWgs84(new MapPos(-79.3748,43.7155)), 0);
        
        // define layer and datasource for route start and stop markers
        routeStartStopDataSource = new LocalVectorDataSource(baseProjection);
        // Initialize a vector layer with the previous data source
        VectorLayer vectorLayer = new VectorLayer(routeStartStopDataSource);
        // Add the previous vector layer to the map
        mapView.getLayers().add(vectorLayer);
        // Set visible zoom range for the vector layer
        vectorLayer.setVisibleZoomRange(new MapRange(0, 20));

        // define layer and datasource for route line and instructions
        routeDataSource = new LocalVectorDataSource(baseProjection);
        VectorLayer routeLayer = new VectorLayer(routeDataSource);
        mapView.getLayers().add(routeLayer);
        
        // set route listener
        RouteMapEventListener mapListener = new RouteMapEventListener(this, mapView);
        mapView.setMapEventListener(mapListener);

        // read filename from extras
        Bundle b = getIntent().getExtras();
        String mapFilePath = b.getString("selectedFile");

        // open graph from folder. remove -gh and file name
        openGraph(mapFilePath.replace("-gh", "").substring(0,
                mapFilePath.replace("-gh", "").lastIndexOf("/")));

        // create markers for start & end, and a layer for them

        MarkerStyleBuilder markerStyleBuilder = new MarkerStyleBuilder();
        markerStyleBuilder.setBitmap(BitmapUtils
                .CreateBitmapFromAndroidBitmap(BitmapFactory.decodeResource(
                        getResources(), R.drawable.olmarker)));
        markerStyleBuilder.setHideIfOverlapped(false);
        markerStyleBuilder.setSize(30);

        markerStyleBuilder.setColor(new com.nutiteq.graphics.Color(Color.GREEN));

        startMarker = new Marker(new MapPos(0, 0), markerStyleBuilder.buildStyle());
        startMarker.setVisible(false);

        markerStyleBuilder.setColor(new com.nutiteq.graphics.Color(Color.RED));

        stopMarker = new Marker(new MapPos(0, 0), markerStyleBuilder.buildStyle());
        stopMarker.setVisible(false);
        
        routeStartStopDataSource.add(startMarker);
        routeStartStopDataSource.add(stopMarker);

        markerStyleBuilder.setColor(new com.nutiteq.graphics.Color(Color.WHITE));
        markerStyleBuilder.setBitmap(BitmapUtils
                .CreateBitmapFromAndroidBitmap(BitmapFactory.decodeResource(
                        getResources(), R.drawable.direction_up)));
        instructionUp = markerStyleBuilder.buildStyle();

        markerStyleBuilder.setBitmap(BitmapUtils
                .CreateBitmapFromAndroidBitmap(BitmapFactory.decodeResource(
                        getResources(), R.drawable.direction_upthenleft)));
        instructionLeft = markerStyleBuilder.buildStyle();

        markerStyleBuilder.setBitmap(BitmapUtils
                .CreateBitmapFromAndroidBitmap(BitmapFactory.decodeResource(
                        getResources(), R.drawable.direction_upthenright)));

        instructionRight = markerStyleBuilder.buildStyle();
        
        // style for 
        balloonPopupStyleBuilder = new BalloonPopupStyleBuilder();
        
    }

    public void showRoute(final MapPos startPos, final MapPos stopPos) {

        Log.d(Const.LOG_TAG, "calculating path " + startPos+" to "+stopPos);
        if (!graphLoaded) {
            Log.e(Const.LOG_TAG, "graph not loaded yet");
            Toast.makeText(getApplicationContext(),
                    "graph not loaded yet, cannot route", Toast.LENGTH_LONG)
                    .show();
            return;
        }

//        stopMarker.setPos(baseProjection.fromWgs84(stopPos));

        new AsyncTask<Void, Void, GHResponse>() {
            float time;

            protected GHResponse doInBackground(Void... v) {
                StopWatch sw = new StopWatch().start();
                GHRequest req = new GHRequest(startPos.getY(), startPos.getX(), stopPos.getY(), stopPos.getX())
                        .setAlgorithm("dijkstrabi")
                        .putHint("instructions", true)
                        .putHint("douglas.minprecision", 1);
                GHResponse resp = gh.route(req);
                time = sw.stop().getSeconds();
                return resp;
            }

            protected void onPostExecute(GHResponse res) {
                Log.d(Const.LOG_TAG, "from:" + startPos + " to:"
                        + stopPos + " found path with distance:"
                        + res.getDistance() / 1000f + ", nodes:"
                        + res.getPoints().getSize() + ", time:" + time + " "
                        + res.getDebugInfo());

                Toast.makeText(
                        getApplicationContext(),
                        "the route is " + (int) (res.getDistance() / 100) / 10f
                                + "km long, time:" + res.getMillis() / 60000f
                                + "min, calculation time:" + time,
                        Toast.LENGTH_LONG).show();

                routeDataSource.removeAll();

                routeDataSource.add(createPolyline(startMarker.getGeometry()
                        .getCenterPos(), stopMarker.getGeometry().getCenterPos(), res));

                // add instruction markers
                InstructionList instructions = res.getInstructions();
                for (Instruction instruction : instructions) {
                    Log.d(Const.LOG_TAG, "name: " + instruction.getName()
                            + " time: " + instruction.getTime() + " dist:"
                            + Helper.round(instruction.getDistance(), 3)
                            + " sign:" + instruction.getSign() + " message: "
                            + instruction.getAnnotation().getMessage()
                            + " importance:"
                            + instruction.getAnnotation().getImportance());
                    createRoutePoint(instruction
                            .getPoints().getLongitude(0), instruction
                            .getPoints().getLatitude(0), instruction.getName(),
                            instruction.getTime(), Helper.round(
                                    instruction.getDistance(), 3), instruction
                                    .getSign(),routeDataSource);
                }

                shortestPathRunning = false;
            }
        }.execute();
    }

    protected void createRoutePoint(double lon, double lat, String name,
            long time, double distance, int indicator, LocalVectorDataSource ds) {

        MarkerStyle style = instructionUp;
        String str = "";

        switch (indicator) {
        case Instruction.FINISH:
            str = "finish";
            break;
        case Instruction.TURN_SHARP_LEFT:
        case Instruction.TURN_LEFT:
            style = instructionLeft;
            str = "turn left";
            break;
        case Instruction.TURN_SHARP_RIGHT:
        case Instruction.TURN_RIGHT:
            style = instructionRight;
            str = "turn right";
            break;
        case Instruction.CONTINUE_ON_STREET:
            style = instructionUp;
            str = "continue";
            break;
        case Instruction.REACHED_VIA:
            style = instructionUp;
            str = "stopover";
            break;
        }

        if (!Helper.isEmpty(name)) {
            str += " to " + name;
        }

        Marker marker = new Marker(baseProjection.fromWgs84(new MapPos(lon, lat)), style);
        BalloonPopup popup2 = new BalloonPopup(marker, balloonPopupStyleBuilder.buildStyle(),
                str, "");
        ds.add(popup2);
        ds.add(marker);
    }

    // creates Nutiteq line from GraphHopper response
    protected Line createPolyline(MapPos start, MapPos end, GHResponse response) {

        LineStyleBuilder lineStyleBuilder = new LineStyleBuilder();
        lineStyleBuilder.setColor(new com.nutiteq.graphics.Color(Color.BLACK));
        lineStyleBuilder.setLineJointType(LineJointType.LINE_JOINT_TYPE_ROUND);
        lineStyleBuilder.setStretchFactor(2);
        lineStyleBuilder.setWidth(2);

        int points = response.getPoints().getSize();
        MapPosVector geoPoints = new MapPosVector();
        PointList tmp = response.getPoints();
        geoPoints.add(start);
        for (int i = 0; i < points; i++) {
            geoPoints.add(baseProjection.fromWgs84(new MapPos(tmp
                    .getLongitude(i), tmp.getLatitude(i))));
        }
        geoPoints.add(end);

        //String labelText = "" + (int) (response.getDistance() / 100) / 10f + "km, time:" + response.getMillis() / 60f + "min";

        return new Line(geoPoints,
                lineStyleBuilder.buildStyle());
    }

    // opens GraphHopper graph file
    void openGraph(final String graphFile) {
        Log.d(Const.LOG_TAG, "loading graph (" + graphFile + ") ... ");
        new AsyncTask<Void, Void, Path>() {
            protected Path doInBackground(Void... v) {
                try {
                    GraphHopper tmpHopp = new GraphHopper().forMobile();
                    tmpHopp.setCHShortcuts("fastest");
                    tmpHopp.load(graphFile);
                    Log.d(Const.LOG_TAG, "found graph with "
                            + tmpHopp.getGraph().getNodes() + " nodes");
                    gh = tmpHopp;
                    graphLoaded = true;
                } catch (Throwable t) {
                    Log.e(Const.LOG_TAG, t.getMessage());
                    errorLoading = true;
                    return null;
                }
                return null;
            }

            protected void onPostExecute(Path o) {
                if (graphLoaded)
                    Toast.makeText(
                            getApplicationContext(),
                            "graph loaded, click on map to set route start and end",
                            Toast.LENGTH_SHORT).show();
                else
                    Toast.makeText(getApplicationContext(),
                            "graph loading problem", Toast.LENGTH_SHORT).show();
            }
        }.execute();
    }

    public MapView getMapView() {
        return mapView;
    }

    @Override
    public String getFileSelectMessage() {
        return "Select .map file in graphhopper graph (<mapname>_gh folder)";
    }

    @Override
    public FileFilter getFileFilter() {
        return new FileFilter() {
            @Override
            public boolean accept(File file) {
                // accept only readable files
                if (file.canRead()) {
                    if (file.isDirectory()) {
                        // allow to select any directory
                        return true;
                    } else if (file.isFile() && file.getName().endsWith(".map")) {
                        // accept files with given extension
                        return true;
                    }
                }
                return false;
            };
        };
    }

    public void setStartMarker(MapPos startPos) {
        routeDataSource.removeAll();
        stopMarker.setVisible(false);
        startMarker.setPos(startPos);
        startMarker.setVisible(true);
    }

    public void setStopMarker(MapPos pos) {
        stopMarker.setPos(pos);
        stopMarker.setVisible(true);
    }

}
