package org.opentripplanner.analyst;

import com.beust.jcommander.internal.Maps;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.opentripplanner.analyst.core.IsochroneData;
import org.opentripplanner.api.resource.LIsochrone;
import org.opentripplanner.api.resource.SurfaceResource;
import org.opentripplanner.profile.IsochroneGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class ResultSet implements Serializable{

    private static final long serialVersionUID = -6723127825189535112L;

    private static final Logger LOG = LoggerFactory.getLogger(ResultSet.class);

    /** An identifier consisting of the ids for the pointset and time surface that were combined. */
    public String id;
    
    /** One histogram for each */
    public Map<String,Histogram> histograms = Maps.newHashMap();

    /** Times to reach every feature, may be null */
    public int[] times;

    /** Isochrones from the origin, may be null */
    public IsochroneData[] isochrones;

    public ResultSet() {
        // TODO is this ever used?
    }

    /** Build a new ResultSet by evaluating the given TimeSurface at all the given sample points, not including times. */
    public ResultSet(SampleSet samples, TimeSurface surface){
        this(samples, surface, false, false);
    }
    
    /** Build a new ResultSet by evaluating the given TimeSurface at all the given sample points, optionally including times. */
    public ResultSet(SampleSet samples, TimeSurface surface, boolean includeTimes, boolean includeIsochrones){
        id = samples.pset.id + "_" + surface.id;

        PointSet targets = samples.pset;
        // Evaluate the surface at all points in the pointset
        int[] times = samples.eval(surface);
        buildHistograms(times, targets);

        if (includeTimes)
            this.times = times;
        
        if (includeIsochrones)
            buildIsochrones(surface);
    }
    
    private void buildIsochrones(TimeSurface surface) {
        List<IsochroneData> id = SurfaceResource.getIsochronesAccumulative(surface, 5, 24);
        
        this.isochrones = new IsochroneData[id.size()];
        id.toArray(this.isochrones);
    }

    /** Build a new ResultSet that contains only isochrones */
    public ResultSet (TimeSurface surface) {
        buildIsochrones(surface);
    }
    
    /** Build a new ResultSet directly from times at point features */
    public ResultSet(int[] times, PointSet targets, boolean includeTimes) {
        buildHistograms(times, targets);

        if (includeTimes)
            this.times = times;
    }

    /** 
     * Given an array of travel times to reach each point in the supplied pointset, make a histogram of 
     * travel times to reach each separate category of points (i.e. "property") within the pointset.
     * Each new histogram object will be stored as a part of this result set keyed on its property/category.
     */
    protected void buildHistograms(int[] times, PointSet targets) {
        for (Entry<String, int[]> entry : targets.properties.entrySet()) {
            String property = entry.getKey();
            int[] magnitudes = entry.getValue();
            this.histograms.put(property, new Histogram(times, magnitudes));
        }
    }

    /**
     * Sum the values of specified categories at all time limits within the
     * bounds of the search. If no categories are specified, sum all categories. 
     */
    public long sum (String... categories) {
        return sum((Integer) null, categories);
    }
    
    /**
     * Sum the values of the specified categories up to the time limit specified
     * (in seconds). If no categories are specified, sum all categories.
     */
    public long sum(Integer timeLimit, String... categories) {
        
        if (categories.length == 0)
            categories = histograms.keySet().toArray(new String[histograms.keySet().size()]);

        long value = 0l;

        int maxMinutes;

        if(timeLimit != null)
            maxMinutes = timeLimit / 60;
        else
            maxMinutes = Integer.MAX_VALUE;

        for(String k : categories) {
            int minute = 0;
            for(int v : histograms.get(k).sums) {
                if(minute < maxMinutes)
                    value += v;
                minute++;
            }
        }

        return value;
    }

    /**
     * Serialize this ResultSet to the given output stream as a JSON document, when the pointset is not available.
     * TODO: explain why and when that would happen 
     */
    public void writeJson(OutputStream output) {
        writeJson(output, null);
    }

    /** 
     * Serialize this ResultSet to the given output stream as a JSON document.
     * properties: a list of the names of all the pointSet properties for which we have histograms.
     * data: for each property, a histogram of arrival times.
     */
    public void writeJson(OutputStream output, PointSet ps) {
        try {
            JsonFactory jsonFactory = new JsonFactory(); 

            JsonGenerator jgen = jsonFactory.createGenerator(output);
            jgen.setCodec(new ObjectMapper());

            jgen.writeStartObject(); {	

                if(ps == null) {
                    jgen.writeObjectFieldStart("properties"); {
                        if (id != null)
                            jgen.writeStringField("id", id);
                    }
                    jgen.writeEndObject();
                }
                else {
                    ps.writeJsonProperties(jgen);
                }

                jgen.writeObjectFieldStart("data"); {
                    for(String propertyId : histograms.keySet()) {

                        jgen.writeObjectFieldStart(propertyId); {
                            histograms.get(propertyId).writeJson(jgen);
                        }
                        jgen.writeEndObject();

                    }
                }
                jgen.writeEndObject();
            }
            jgen.writeEndObject();

            jgen.close();
        } catch (IOException ioex) {
            LOG.info("IOException, connection may have been closed while streaming JSON.");
        }
    }

    /** Write the isochrones as GeoJSON */
    public void writeIsochrones(JsonGenerator jgen) throws IOException {
        if (this.isochrones == null)
            return;
        
        FeatureJSON fj = new FeatureJSON();
        FeatureCollection fc = LIsochrone.makeContourFeatures(Arrays.asList(isochrones));
        
        StringWriter sw = new StringWriter();
        fj.writeFeatureCollection(fc, sw);
        // TODO cludge
        String json = sw.toString();
        jgen.writeRaw(json.substring(1, json.length() - 1));
    }
    
    /** A set of result sets from profile routing: min, avg, max */;
    public static class RangeSet implements Serializable {
        public static final long serialVersionUID = 1L;

        public ResultSet min;
        public ResultSet avg;
        public ResultSet max;
    }
}