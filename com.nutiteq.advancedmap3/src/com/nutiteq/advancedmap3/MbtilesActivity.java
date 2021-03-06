package com.nutiteq.advancedmap3;

import java.io.File;
import java.io.FileFilter;

import android.os.Bundle;

import com.nutiteq.core.MapRange;
import com.nutiteq.datasources.MBTilesTileDataSource;
import com.nutiteq.filepicker.FilePickerActivity;
import com.nutiteq.layers.RasterTileLayer;
import com.nutiteq.layers.VectorTileLayer;
import com.nutiteq.utils.AssetUtils;
import com.nutiteq.vectortiles.MBVectorTileDecoder;
import com.nutiteq.vectortiles.MBVectorTileStyleSet;
import com.nutiteq.vectortiles.VectorTileDecoder;
import com.nutiteq.wrappedcommons.UnsignedCharVector;

/**
 * A sample that uses a specified MBTiles file for the base layer.
 * The sample assumes that the file name is specified using the Intent "selectedFile" extra field.
 */
public class MbtilesActivity extends MapSampleBaseActivity implements
        FilePickerActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // read filename from extras
        Bundle b = getIntent().getExtras();
        String filePath = b.getString("selectedFile");

        // Create tile data source. Min/max zoom will be automatically detected.
        MBTilesTileDataSource tileDataSource = new MBTilesTileDataSource(filePath);
        
        // Now check if we need to use vector layer or raster layer, based on mbtiles metadata
        String format = tileDataSource.getMetaData().get("format");
        if ("mbvt".equals(format)) {
            UnsignedCharVector styleBytes = AssetUtils.LoadBytes("osmbright.zip");
            MBVectorTileStyleSet vectorTileStyleSet = new MBVectorTileStyleSet(styleBytes);
            VectorTileDecoder vectorTileDecoder = new MBVectorTileDecoder(vectorTileStyleSet);
        	baseLayer = new VectorTileLayer(tileDataSource, vectorTileDecoder);
        } else {
        	baseLayer = new RasterTileLayer(tileDataSource);
        }
        mapView.getLayers().add(baseLayer);

        mapView.getOptions().setZoomRange(new MapRange(0, 18));
        mapView.setZoom(3, 0);
    }

    @Override
    public String getFileSelectMessage() {
        return "Select MBTiles file (raster or vector)";
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
                    } else if (file.isFile()
                            && file.getName().endsWith(".mbtiles")) {
                        // accept files with given extension
                        return true;
                    }
                }
                return false;
            };
        };
    }

}
