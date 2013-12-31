package com.nutiteq.layers.vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.geonames.BoundingBox;
import org.geonames.Toponym;
import org.geonames.ToponymSearchCriteria;
import org.geonames.ToponymSearchResult;
import org.geonames.WebService;

import com.nutiteq.components.Components;
import com.nutiteq.components.Envelope;
import com.nutiteq.components.MapPos;
import com.nutiteq.geometry.Point;
import com.nutiteq.layers.Layer;
import com.nutiteq.log.Log;
import com.nutiteq.projections.Projection;
import com.nutiteq.style.LabelStyle;
import com.nutiteq.style.PointStyle;
import com.nutiteq.style.StyleSet;
import com.nutiteq.tasks.Task;
import com.nutiteq.ui.DefaultLabel;
import com.nutiteq.vectorlayers.GeometryLayer;

public class GeonamesLayer extends GeometryLayer {

  private StyleSet<PointStyle> pointStyleSet;
  private float minZoom = Float.MAX_VALUE;
  private static String GEONAMES_USER = "nutiteq";
  private List<Toponym> visibleFeatures = new LinkedList<Toponym>();
  private LabelStyle labelStyle;

  // data loader task

  protected class LoadGeonamesDataTask implements Task {
    private final Envelope envelope;
    private final int zoom;

    LoadGeonamesDataTask(Envelope envelope, int zoom) {
      this.envelope = envelope;
      this.zoom = zoom;
    }

    @Override
    public void run() {

      List<com.nutiteq.geometry.Geometry> newVisibleElementsList = new LinkedList<com.nutiteq.geometry.Geometry>();
      Map<String, Toponym> newVisibleFeatureMap = new HashMap<String, Toponym>();

      WebService.setUserName(GEONAMES_USER); // add your username here
      
      ToponymSearchCriteria searchCriteria = new ToponymSearchCriteria();
      
      // bbox-based query to Geonames API
      MapPos minCorner = getProjection().toWgs84(envelope.minX, envelope.minY);
      MapPos maxCorner = getProjection().toWgs84(envelope.maxX, envelope.maxY);
      BoundingBox bbox = new BoundingBox(minCorner.x, maxCorner.x,  minCorner.y, maxCorner.y);
      Log.debug("bbox = " + bbox);
      searchCriteria.setBoundingBox(bbox);
      // name-based query to Geonames API
//      searchCriteria.setQ("zurich");
      searchCriteria.setMaxRows(100);
      try{
          
          ToponymSearchResult searchResult = WebService.search(searchCriteria);
          for (Toponym toponym : searchResult.getToponyms()) {
             Log.debug(toponym.getName()+" "+ toponym.getCountryName());
    
             com.nutiteq.geometry.Geometry newObject = null;
             DefaultLabel label = new DefaultLabel(toponym.getName(), ""+toponym.getCountryName()+" type:"+toponym.getFeatureCodeName(), labelStyle);
    
             MapPos mapPos = getProjection().fromWgs84(toponym.getLongitude(), toponym.getLatitude());
             newObject = new Point(mapPos, label, pointStyleSet, null);
    
             newObject.attachToLayer(GeonamesLayer.this);
             if (!newObject.getInternalState().envelope.intersects(envelope)) {
               continue;
             }
             newObject.setActiveStyle(zoom);
             newVisibleElementsList.add(newObject);
             newVisibleFeatureMap.put(String.valueOf(toponym.getGeoNameId()), toponym);
          }
          
          setVisibleElements(newVisibleElementsList);
          visibleFeatures = new ArrayList<Toponym>(newVisibleFeatureMap.values());
    
          Components components = getComponents();
          if (components != null) {
            for (Layer layer : components.layers.getVectorLayers()) {
              if (layer instanceof GeonamesTextLayer) {
                GeonamesTextLayer geonamesTextLayer = (GeonamesTextLayer) layer;
                if (geonamesTextLayer.getBaseLayer() == GeonamesLayer.this) {
                    geonamesTextLayer.calculateVisibleElements(visibleFeatures, zoom);
                }
              }
            }
          }
      }catch (Exception e){
          Log.error("search exception "+e.getLocalizedMessage());
      }
    }

    @Override
    public boolean isCancelable() {
      return true;
    }

    @Override
    public void cancel() {
    }
  }


  /**
   * Geonames connector, based on general query. Shows points and caches data for texts
   * Uses Geonames own Java library from http://www.geonames.org/source-code/
   * 
   * @param proj layer projection. NB! data must be in the same projection
   * @param pointStyleSet styleset for point objects
   */
  public GeonamesLayer(Projection proj, StyleSet<PointStyle> pointStyleSet, LabelStyle labelStyle) {
    super(proj);
    this.pointStyleSet = pointStyleSet;
    this.labelStyle = labelStyle;
    
    if (pointStyleSet != null) {
      minZoom = Math.min(minZoom, pointStyleSet.getFirstNonNullZoomStyleZoom());
    }
    Log.debug("GeonamesLayer minZoom = "+minZoom);
  }

  public List<Toponym> getVisibleFeatures() {
    return visibleFeatures;
  }

  @Override
  public void calculateVisibleElements(Envelope envelope, int zoom) {
    if (zoom < minZoom) {
      return;
    }
    executeVisibilityCalculationTask(new LoadGeonamesDataTask(envelope, zoom));
  }
}