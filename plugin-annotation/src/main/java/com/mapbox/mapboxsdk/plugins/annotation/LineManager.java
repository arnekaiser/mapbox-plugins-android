// This file is generated.

package com.mapbox.mapboxsdk.plugins.annotation;

import android.graphics.PointF;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.LongSparseArray;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.style.layers.PropertyValue;
import com.mapbox.mapboxsdk.style.layers.LineLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;
import com.mapbox.mapboxsdk.style.layers.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.*;
//import static com.mapbox.mapboxsdk.annotations.symbol.Symbol.Z_INDEX;

/**
 * The line manager allows to add lines to a map.
 */
public class LineManager extends AnnotationManager<Line, OnLineClickListener> {

  public static final String ID_GEOJSON_SOURCE = "mapbox-android-line-source";
  public static final String ID_GEOJSON_LAYER = "mapbox-android-line-layer";

  private LineLayer layer;
  private final MapClickResolver mapClickResolver;

  /**
   * Create a line manager, used to manage lines.
   *
   * @param mapboxMap the map object to add lines to
   */
  @UiThread
  public LineManager(@NonNull MapboxMap mapboxMap) {
    this(mapboxMap, new GeoJsonSource(ID_GEOJSON_SOURCE), new LineLayer(ID_GEOJSON_LAYER, ID_GEOJSON_SOURCE)
      .withProperties(
        getLayerDefinition()
      )
    );
  }

  /**
   * Create a line manager, used to manage lines.
   *
   * @param mapboxMap     the map object to add lines to
   * @param geoJsonSource the geojson source to add lines to
   * @param layer         the line layer to visualise Lines with
   */
  @VisibleForTesting
  public LineManager(MapboxMap mapboxMap, @NonNull GeoJsonSource geoJsonSource, LineLayer layer) {
    super(mapboxMap, geoJsonSource);
    this.layer = layer;
    mapboxMap.addLayer(layer);
    mapboxMap.addOnMapClickListener(mapClickResolver = new MapClickResolver(mapboxMap));
  }

  /**
   * Cleanup line manager, used to clear listeners
   */
  @UiThread
  public void onDestroy() {
    super.onDestroy();
    mapboxMap.removeOnMapClickListener(mapClickResolver);
  }

  /**
   * Create a line on the map from a LatLng coordinate.
   *
   * @param latLngs places to layout the line on the map
   * @return the newly created line
   */
  @UiThread
  public Line createLine(@NonNull List<LatLng> latLngs) {
    Line line = new Line(this, currentId);
    line.setLatLngs(latLngs);
    add(line);
    return line;
  }

  private static PropertyValue<?>[] getLayerDefinition() {
    return new PropertyValue[]{
      lineJoin(get("line-join")),
      lineOpacity(get("line-opacity")),
      lineColor(get("line-color")),
      lineWidth(get("line-width")),
      lineGapWidth(get("line-gap-width")),
      lineOffset(get("line-offset")),
      lineBlur(get("line-blur")),
    };
  }

  // Property accessors
  /**
   * Get the LineCap property
   *
   * @return property wrapper value around String
   */
  public String getLineCap() {
    return layer.getLineCap().value;
  }

  /**
   * Set the LineCap property
   *
   * @param value property wrapper value around String
   */
  public void setLineCap(@Property.LINE_CAP String value) {
    layer.setProperties(lineCap(value));
  }

  /**
   * Get the LineMiterLimit property
   *
   * @return property wrapper value around Float
   */
  public Float getLineMiterLimit() {
    return layer.getLineMiterLimit().value;
  }

  /**
   * Set the LineMiterLimit property
   *
   * @param value property wrapper value around Float
   */
  public void setLineMiterLimit( Float value) {
    layer.setProperties(lineMiterLimit(value));
  }

  /**
   * Get the LineRoundLimit property
   *
   * @return property wrapper value around Float
   */
  public Float getLineRoundLimit() {
    return layer.getLineRoundLimit().value;
  }

  /**
   * Set the LineRoundLimit property
   *
   * @param value property wrapper value around Float
   */
  public void setLineRoundLimit( Float value) {
    layer.setProperties(lineRoundLimit(value));
  }

  /**
   * Get the LineTranslate property
   *
   * @return property wrapper value around Float[]
   */
  public Float[] getLineTranslate() {
    return layer.getLineTranslate().value;
  }

  /**
   * Set the LineTranslate property
   *
   * @param value property wrapper value around Float[]
   */
  public void setLineTranslate( Float[] value) {
    layer.setProperties(lineTranslate(value));
  }

  /**
   * Get the LineTranslateAnchor property
   *
   * @return property wrapper value around String
   */
  public String getLineTranslateAnchor() {
    return layer.getLineTranslateAnchor().value;
  }

  /**
   * Set the LineTranslateAnchor property
   *
   * @param value property wrapper value around String
   */
  public void setLineTranslateAnchor(@Property.LINE_TRANSLATE_ANCHOR String value) {
    layer.setProperties(lineTranslateAnchor(value));
  }

  /**
   * Get the LineDasharray property
   *
   * @return property wrapper value around Float[]
   */
  public Float[] getLineDasharray() {
    return layer.getLineDasharray().value;
  }

  /**
   * Set the LineDasharray property
   *
   * @param value property wrapper value around Float[]
   */
  public void setLineDasharray( Float[] value) {
    layer.setProperties(lineDasharray(value));
  }

  /**
   * Inner class for transforming map click events into line clicks
   */
  private class MapClickResolver implements MapboxMap.OnMapClickListener {

    private MapboxMap mapboxMap;

    private MapClickResolver(MapboxMap mapboxMap) {
      this.mapboxMap = mapboxMap;
    }

    @Override
    public void onMapClick(@NonNull LatLng point) {
      if (clickListeners.isEmpty()) {
        return;
      }

      PointF screenLocation = mapboxMap.getProjection().toScreenLocation(point);
      List<Feature> features = mapboxMap.queryRenderedFeatures(screenLocation, ID_GEOJSON_LAYER);
      if (!features.isEmpty()) {
        long lineId = features.get(0).getProperty(Line.ID_KEY).getAsLong();
        Line line = annotations.get(lineId);
        if (line != null) {
          for (OnLineClickListener listener : clickListeners) {
            listener.onLineClick(line);
          }
        }
      }
    }
  }
}