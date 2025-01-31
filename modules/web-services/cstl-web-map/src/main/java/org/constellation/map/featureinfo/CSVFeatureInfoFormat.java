/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.constellation.map.featureinfo;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.measure.Unit;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.storage.DataStoreException;
import org.constellation.ws.MimeType;
import org.geotoolkit.display.PortrayalException;
import org.geotoolkit.display2d.canvas.RenderingContext2D;
import org.geotoolkit.display2d.primitive.ProjectedCoverage;
import org.geotoolkit.display2d.primitive.ProjectedFeature;
import org.geotoolkit.display2d.primitive.SearchAreaJ2D;
import org.geotoolkit.display2d.service.CanvasDef;
import org.geotoolkit.display2d.service.SceneDef;
import org.geotoolkit.display2d.service.ViewDef;
import org.geotoolkit.map.FeatureMapLayer;
import org.geotoolkit.ows.xml.GetFeatureInfo;
import org.opengis.feature.AttributeType;
import org.opengis.feature.Feature;
import org.opengis.feature.PropertyType;
import org.opengis.util.GenericName;
import org.opengis.util.InternationalString;

/**
 * A generic FeatureInfoFormat that produce CSV output for Features and Coverages.
 * Supported mimeTypes are :
 * <ul>
 *     <li>text/plain</li>
 * </ul>
 *
 * @author Quentin Boileau (Geomatys)
 * @author Johann Sorel (Geomatys)
 */
public class CSVFeatureInfoFormat extends AbstractTextFeatureInfoFormat {

    private static final class LayerResult{
        private String layerName;
        private String layerType;
        private final List<String> values = new ArrayList<>();
    }

    private final Map<String,LayerResult> results = new HashMap<>();

    public CSVFeatureInfoFormat() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void nextProjectedFeature(ProjectedFeature graphic, RenderingContext2D context, SearchAreaJ2D queryArea) {

        final FeatureMapLayer layer = graphic.getLayer();
        final String layerName = layer.getName();

        final Feature feature = graphic.getCandidate();
        final Collection<? extends PropertyType> descs = feature.getType().getProperties(true);

        LayerResult result = results.get(layerName);
        if(result==null){
            //first feature of this type
            result = new LayerResult();
            result.layerName = layerName;
            //feature type
            final StringBuilder typeBuilder = new StringBuilder();
            for(PropertyType pt : descs){
                final GenericName propName = pt.getName();
                typeBuilder.append(propName.toString());
                typeBuilder.append(':');
                if(pt instanceof AttributeType) {
                    typeBuilder.append(((AttributeType)pt).getValueClass().getSimpleName());
                } else {
                    typeBuilder.append("Operation");
                }
                typeBuilder.append(';');
            }
            result.layerType = typeBuilder.toString();
            results.put(layerName, result);
        }


        //the feature values
        final StringBuilder dataBuilder = new StringBuilder();
        for(PropertyType pt : descs){
            final Object value = feature.getPropertyValue(pt.getName().toString());
            if(value instanceof Feature){
                dataBuilder.append("...complex attribute, use GML or HTML output...");
            }else if(value !=null){
                dataBuilder.append(String.valueOf(value));
            }
            dataBuilder.append(';');
        }
        result.values.add(dataBuilder.toString());
    }

    @Override
    protected void nextProjectedCoverage(ProjectedCoverage graphic, RenderingContext2D context, SearchAreaJ2D queryArea) {
        final List<Map.Entry<SampleDimension,Object>> covResults = FeatureInfoUtilities.getCoverageValues(graphic, context, queryArea);

        if (covResults == null) {
            return;
        }

        final String layerName;
        try {
            if (graphic.getLayer().getResource().getIdentifier().isPresent()) {
                layerName = graphic.getLayer().getResource().getIdentifier().get().tip().toString();
            } else {
                throw new RuntimeException("resource identifier not present");
            }
        } catch (DataStoreException e) {
            throw new RuntimeException(e);      // TODO
        }

        LayerResult result = results.get(layerName);
        if(result==null){
            //first feature of this type
            result = new LayerResult();
            result.layerName = layerName;
            //coverage type
            final StringBuilder typeBuilder = new StringBuilder();
            for (final Map.Entry<SampleDimension,Object> entry : covResults) {
                final SampleDimension gsd = entry.getKey();

                final InternationalString title = gsd.getName().toInternationalString();
                if(title!=null){
                    typeBuilder.append(title);
                }
                final Unit unit = gsd.getUnits().orElse(null);
                if (unit!=null) {
                    typeBuilder.append(unit.toString());
                }
                typeBuilder.append(';');
            }
            result.layerType = typeBuilder.toString();
            results.put(layerName, result);
        }

        //the coverage values
        final StringBuilder dataBuilder = new StringBuilder();
        for(Map.Entry<SampleDimension,Object> entry : covResults){
            dataBuilder.append(String.valueOf(entry.getValue()));
            dataBuilder.append(';');
        }
        result.values.add(dataBuilder.toString());
    }



    /**
     * {@inheritDoc}
     */
    @Override
    public Object getFeatureInfo(SceneDef sdef, ViewDef vdef, CanvasDef cdef, Rectangle searchArea, GetFeatureInfo getFI) throws PortrayalException {

        //fill coverages and features maps
        getCandidates(sdef, vdef, cdef, searchArea, -1);

        final StringBuilder builder = new StringBuilder();

        // optimization move this filter to getCandidates
        Integer maxValue = getFeatureCount(getFI);
        if (maxValue == null) {
            maxValue = 1;
        }

        for(LayerResult result : results.values()){
            builder.append(result.layerName).append('\n');
            builder.append(result.layerType).append('\n');

            int cpt = 0;
            for (final String record : result.values) {
                builder.append(record).append('\n');
                cpt++;
                if (cpt >= maxValue) break;
            }
            builder.append('\n');
        }

        results.clear();
        return builder.toString();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSupportedMimeTypes() {
        return Collections.singletonList(MimeType.TEXT_PLAIN);
    }
}
