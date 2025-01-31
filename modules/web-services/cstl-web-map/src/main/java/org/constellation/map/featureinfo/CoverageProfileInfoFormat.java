/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2019 Geomatys.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import java.awt.Rectangle;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.StreamSupport;
import javax.measure.Unit;
import javax.measure.UnitConverter;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.math.Statistics;
import org.apache.sis.measure.Units;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.referencing.crs.DefaultCompoundCRS;
import org.apache.sis.storage.DataStoreException;
import org.geotoolkit.coverage.io.CoverageStoreException;
import org.geotoolkit.display.PortrayalException;
import org.geotoolkit.display2d.service.CanvasDef;
import org.geotoolkit.display2d.service.SceneDef;
import org.geotoolkit.display2d.service.ViewDef;
import org.geotoolkit.geometry.jts.JTS;
import org.geotoolkit.geometry.jts.JTSEnvelope2D;
import org.geotoolkit.map.CoverageMapLayer;
import org.geotoolkit.map.MapLayer;
import org.geotoolkit.ows.xml.GetFeatureInfo;
import org.apache.sis.storage.GridCoverageResource;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.GridCoverage;
import org.opengis.coverage.grid.GridEnvelope;
import org.apache.sis.coverage.grid.GridGeometry;
import org.apache.sis.coverage.grid.GridRoundingMode;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.crs.TemporalCRS;
import org.opengis.referencing.crs.VerticalCRS;
import org.opengis.referencing.cs.AxisDirection;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.datum.PixelInCell;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
public class CoverageProfileInfoFormat extends AbstractFeatureInfoFormat {

    private static final String MIME = "application/json; subtype=profile";
    private static final String PARAM_PROFILE = "profile";
    private static final String PARAM_ALTITUDE = "alt";

    private static final Map<Unit,List<Unit>> UNIT_GROUPS = new HashMap<>();
    static {
        final List<Unit> tempUnits = new ArrayList<>();
        tempUnits.add(Units.CELSIUS);
        tempUnits.add(Units.FAHRENHEIT);
        tempUnits.add(Units.KELVIN);

        final List<Unit> pressUnits = new ArrayList<>();
        pressUnits.add(Units.BAR);
        pressUnits.add(Units.BAR.multiply(14.503773773));
        pressUnits.add(Units.PASCAL);

        final List<Unit> speedUnits = new ArrayList<>();
        speedUnits.add(Units.METRES_PER_SECOND);
        speedUnits.add(Units.METRES_PER_SECOND.divide(1000));

        for (Unit u : tempUnits) UNIT_GROUPS.put(u, tempUnits);
        for (Unit u : pressUnits) UNIT_GROUPS.put(u, pressUnits);
        for (Unit u : speedUnits) UNIT_GROUPS.put(u, speedUnits);
    }

    @Override
    public Object getFeatureInfo(SceneDef sdef, ViewDef vdef, CanvasDef cdef, Rectangle searchArea, GetFeatureInfo getFI) throws PortrayalException {

        //extract profile geometry
        String geomStr = null;
        if (getFI instanceof org.geotoolkit.wms.xml.GetFeatureInfo) {
            Object parameters = ((org.geotoolkit.wms.xml.GetFeatureInfo) getFI).getParameters();
            if (parameters instanceof Map) {
                Object cdt = ((Map) parameters).get(PARAM_PROFILE);
                if (cdt instanceof String){
                    geomStr = (String) cdt;
                } else if (cdt instanceof String[]) {
                    geomStr = ((String[]) cdt)[0];
                }
            }
        }

        if (geomStr == null) throw new PortrayalException("Missing PROFILE geometry parameter.");
        final WKTReader reader = new WKTReader();
        Geometry geom;
        try {
            geom = reader.read(geomStr);
        } catch (ParseException ex) {
            throw new PortrayalException(ex.getMessage(), ex);
        }
        if (!(geom instanceof LineString || geom instanceof Point)) {
            throw new PortrayalException("PROFILE geometry parameter must be a point or a LineString.");
        }

        //geometry is in view crs
        final CoordinateReferenceSystem geomCrs = CRS.getHorizontalComponent(vdef.getEnvelope().getCoordinateReferenceSystem());
        geom.setUserData(geomCrs);

        final Profile profil = new Profile();

        for (MapLayer layer : sdef.getContext().layers()) {
            if (layer instanceof CoverageMapLayer) {
                final GridCoverageResource ressource = ((CoverageMapLayer) layer).getResource();
                try {
                    final ProfilLayer l = extract(sdef, vdef, cdef, getFI, geom, ressource);
                    l.name = layer.getName();
                    if (l.name == null) {
                        if (ressource.getIdentifier().isPresent()) {
                            l.name = ressource.getIdentifier().get().tip().toString();
                        } else {
                            throw new PortrayalException("resource identifier not present");
                        }
                    }
                    profil.layers.add(l);
                } catch (TransformException | DataStoreException | FactoryException ex) {
                    throw new PortrayalException(ex.getMessage(), ex);
                }
            }
        }
        return profil;
    }

    @Override
    public List<String> getSupportedMimeTypes() {
        return Collections.singletonList(MIME);
    }

    private ProfilLayer extract(SceneDef sdef, ViewDef vdef, CanvasDef cdef,
            GetFeatureInfo getFI, Geometry geom, GridCoverageResource resource) throws TransformException, CoverageStoreException, FactoryException, DataStoreException {

        final ProfilLayer layer = new ProfilLayer();

        final ProfilData baseData;
        try {
            //build temporal and vertical slice
            final JTSEnvelope2D geomEnv = JTS.toEnvelope(geom);
            final Envelope venv = vdef.getEnvelope();
            final CoordinateReferenceSystem vcrs = venv.getCoordinateReferenceSystem();
            final TemporalCRS vtcrs = CRS.getTemporalComponent(vcrs);
            final VerticalCRS vacrs = CRS.getVerticalComponent(vcrs, true);

            final GridGeometry gridGeometry = resource.getGridGeometry();
            final CoordinateReferenceSystem ccrs = gridGeometry.getCoordinateReferenceSystem();
            final TemporalCRS ctcrs = CRS.getTemporalComponent(ccrs);
            final VerticalCRS cacrs = CRS.getVerticalComponent(ccrs, true);

            Double time = null;
            Double alti = null;
            TemporalCRS ftcrs = null;
            VerticalCRS facrs = null;
            if (vtcrs == null) {
                //pick first coverage temporal slice
                if (ctcrs != null) {
                    ftcrs = ctcrs;
                    final Envelope tenv = Envelopes.transform(gridGeometry.getEnvelope(), ctcrs);
                    time = tenv.getMaximum(0);
                }
            } else {
                //extract user requested time
                ftcrs = vtcrs;
                final Envelope tenv = Envelopes.transform(venv, vtcrs);
                time = tenv.getMedian(0);
            }

            if (geom instanceof LineString) {
                //extract altitude parameter
                String altStr = null;
                if (getFI instanceof org.geotoolkit.wms.xml.GetFeatureInfo) {
                    Object parameters = ((org.geotoolkit.wms.xml.GetFeatureInfo) getFI).getParameters();
                    if (parameters instanceof Map) {
                        Object cdt = ((Map) parameters).get(PARAM_ALTITUDE);
                        if (cdt instanceof String){
                            altStr = (String) cdt;
                        } else if (cdt instanceof String[]) {
                            altStr = ((String[]) cdt)[0];
                        }
                    }
                }

                if (altStr != null) {
                    if (cacrs != null) {
                        facrs = cacrs;
                        alti = Double.parseDouble(altStr);
                    }
                } else if (vacrs == null) {
                    //pick first coverage altitude slice
                    if (altStr != null) {
                        facrs = CommonCRS.Vertical.ELLIPSOIDAL.crs();
                        alti = Double.parseDouble(altStr);
                    } else if (cacrs != null) {
                        facrs = cacrs;
                        final Envelope aenv = Envelopes.transform(gridGeometry.getEnvelope(), cacrs);
                        alti = aenv.getMaximum(0);
                    }
                } else {
                    //extract user requested altitude
                    facrs = vacrs;
                    final Envelope aenv = Envelopes.transform(venv, vacrs);
                    alti = aenv.getMedian(0);
                }
            }

            final GeneralEnvelope workEnv;
            if (time == null && alti == null) {
                workEnv = new GeneralEnvelope(geomEnv);
            } else if (alti == null) {
                final Map props = new HashMap();
                props.put("name", "2d+t");
                final CoordinateReferenceSystem crs = new DefaultCompoundCRS(props, geomEnv.getCoordinateReferenceSystem(), ftcrs);
                workEnv = new GeneralEnvelope(crs);
                workEnv.setRange(0, geomEnv.getMinimum(0), geomEnv.getMaximum(0));
                workEnv.setRange(1, geomEnv.getMinimum(1), geomEnv.getMaximum(1));
                workEnv.setRange(2, time, time);
            } else if (time == null) {
                final Map props = new HashMap();
                props.put("name", "2d+a");
                final CoordinateReferenceSystem crs = new DefaultCompoundCRS(props, geomEnv.getCoordinateReferenceSystem(), facrs);
                workEnv = new GeneralEnvelope(crs);
                workEnv.setRange(0, geomEnv.getMinimum(0), geomEnv.getMaximum(0));
                workEnv.setRange(1, geomEnv.getMinimum(1), geomEnv.getMaximum(1));
                workEnv.setRange(2, alti, alti);
            } else {
                final Map props = new HashMap();
                props.put("name", "2d+t+a");
                final CoordinateReferenceSystem crs = new DefaultCompoundCRS(props, geomEnv.getCoordinateReferenceSystem(), ftcrs, facrs);
                workEnv = new GeneralEnvelope(crs);
                workEnv.setRange(0, geomEnv.getMinimum(0), geomEnv.getMaximum(0));
                workEnv.setRange(1, geomEnv.getMinimum(1), geomEnv.getMaximum(1));
                workEnv.setRange(2, time, time);
                workEnv.setRange(3, alti, alti);
            }

            final GridCoverage coverage = readCoverage(resource, workEnv, null);
            baseData = extractData(resource, coverage, geom);

        } catch (CoverageStoreException ex) {
            layer.message = ex.getMessage();
            return layer;
        }

        //convert data in different units
        final List<Unit> group = UNIT_GROUPS.get(baseData.realUnit);
        if (group != null) {
            for (Unit u : group) {
                if (u.equals(baseData.realUnit)) {
                    layer.data.add(baseData);
                    continue;
                }
                //create converted datas
                final ProfilData data = new ProfilData();
                final Statistics stats = new Statistics("");
                final UnitConverter converter = baseData.realUnit.getConverterTo(u);

                for (XY xy : baseData.points) {
                    final XY c = new XY(xy.x, xy.y);
                    if (geom instanceof Point) {
                        c.x = converter.convert(c.x);
                        stats.accept(c.x);
                    } else {
                        c.y = converter.convert(c.y);
                        stats.accept(c.y);
                    }
                    data.points.add(c);
                }

                data.unit = u.getSymbol();
                data.min = stats.minimum();
                data.max = stats.maximum();
                layer.data.add(data);
            }
        } else {
            layer.data.add(baseData);
        }


        return layer;
    }

    private ProfilData extractData(GridCoverageResource resource, GridCoverage coverage, Geometry geom) throws TransformException, FactoryException {

        final ProfilData pdata = new ProfilData();

        final CoordinateReferenceSystem crs = coverage.getCoordinateReferenceSystem();
        final CoordinateReferenceSystem crs2d = CRS.getHorizontalComponent(crs);
        final CoordinateReferenceSystem vcrs = CRS.getVerticalComponent(crs, true);
        final GridGeometry gridGeometry = coverage.getGridGeometry();
        final GridEnvelope extent = gridGeometry.getExtent();
        final MathTransform gridToCrs = gridGeometry.getGridToCRS(PixelInCell.CELL_CENTER);

        //build axes informations
        final int dim = crs.getCoordinateSystem().getDimension();
        long[] lowsI = extent.getLow().getCoordinateValues();
        double[] lowsD = new double[dim];
        for (int i=0;i<dim;i++) lowsD[i] = lowsI[i];
        final double[] gridPt = new double[extent.getDimension()];
        final double[] crsPt = new double[extent.getDimension()];
        final List<Axe> axes = new ArrayList<>();
        Integer altiIdx = null;
        for (int i=2;i<dim;i++) {
            final CoordinateSystemAxis axis = crs.getCoordinateSystem().getAxis(i);
            System.arraycopy(lowsD, 0, gridPt, 0, dim);
            final double[] range = new double[(int)extent.getSize(i)];
            for (int k=0,kg=(int) extent.getLow(k);k<range.length;k++,kg++) {
                gridPt[i] = kg;
                gridToCrs.transform(gridPt, 0, crsPt, 0, 1);
                range[k] = crsPt[i];
            }
            final Unit<?> unit = axis.getUnit();

            final Axe axe = new Axe();
            axe.name = axis.getName().toString();
            axe.direction = axis.getDirection().name();
            if (unit != null) axe.unit = unit.getSymbol();
            axe.range = range;
            axes.add(axe);

            if (axis.getDirection().equals(AxisDirection.UP) || axis.getDirection().equals(AxisDirection.DOWN)) {
                altiIdx = i-2;
            }
        }
        final int ALTIIDX = altiIdx == null ? -1 : altiIdx;

        //build sample dimension informations
        final int numSampleDimensions = coverage.getSampleDimensions().size();
        final Band[] bands = new Band[numSampleDimensions];
        for (int i=0;i<numSampleDimensions;i++) {
            SampleDimension sampleDimension = coverage.getSampleDimensions().get(0);
            Unit<?> unit = sampleDimension.getUnits().get();
            final Band band = new Band();
            band.realUnit = unit;
            band.name = String.valueOf(sampleDimension.getName());
            if (unit != null) band.unit = unit.getSymbol();
            bands[i] = band;

            //limit to first band, request by david/mehdi
            break;
        }

        final boolean isPoint = geom instanceof Point;
        if (isPoint) {
            final Object userData = geom.getUserData();
            final Coordinate[] coords = geom.getCoordinates();
            geom = new GeometryFactory().createLineString(new Coordinate[]{coords[0], coords[0]});
            geom.setUserData(userData);
        }

        final DataProfile dp = new DataProfile(coverage, (LineString) geom);

        final Statistics stats = new Statistics("");

        if (isPoint) {
            StreamSupport.stream(dp, false).forEach(new Consumer<DataProfile.DataPoint>() {
                @Override
                public void accept(DataProfile.DataPoint t) {
                    Object value = t.value;

                    // due to NPE if we click outside the data domain see jira issue SDMS-313
                    if (value == null) {
                        return;
                    }

                    //unpack first band
                    value = Array.get(value, 0);

                    final List<Double> values = new ArrayList<>();
                    //unpack dimensions
                    final int as = axes.size();

                    //lazy, we expect only time and alti dimensions.
                    if (as == 0) {
                        extractValues(value, values);
                    } else if (as == 1) {
                        if (ALTIIDX == 0) {
                            //pick all
                            extractValues(value, values);
                        } else {
                            //pick first
                            value = Array.get(value, 0);
                            extractValues(value, values);
                        }
                    } else if (as == 2) {
                        if (ALTIIDX == 0) {
                            //pick first - alti
                            for (int i=0,n=Array.getLength(value);i<n;i++) {
                                Object sub = Array.get(value, i);
                                //pick first - not alti
                                sub = Array.get(sub, 0);
                                extractValues(sub, values);
                            }
                        } else if (ALTIIDX == 1) {
                            //pick first - not alti
                            value = Array.get(value, 0);
                            //pick all - alti
                            extractValues(value, values);
                        } else {
                            //pick first - not alti
                            value = Array.get(value, 0);
                            //pick first - not alti
                            value = Array.get(value, 0);
                            extractValues(value, values);
                        }
                    }

                    for (Double d : values) {
                        stats.accept(d);
                    }

                    if (ALTIIDX >= 0) {
                        final Axe axe = axes.get(ALTIIDX);
                        for (int i=0,n=values.size();i<n;i++) {
                            pdata.points.add(new XY(values.get(i), axe.range[i]));
                        }
                    } else {
                        pdata.points.add(new XY(0, values.get(0)));
                    }
                }
            });

        } else {
            double[] d = new double[1];
            StreamSupport.stream(dp, false).forEach(new Consumer<DataProfile.DataPoint>() {
                @Override
                public void accept(DataProfile.DataPoint t) {
                    Object value = t.value;
                    d[0] += t.distanceFromPrevious / 1000.0;
                    if (value != null) {
                        final double distancekm = d[0];

                        //unpack first band
                        value = Array.get(value, 0);

                        //pick first value
                        while (value.getClass().isArray()) {
                            value = Array.get(value, 0);
                        }

                        double num = ((Number)value).doubleValue();
                        stats.accept(num);
                        pdata.points.add(new XY(distancekm, num));
                    }
                }
            });
        }

        pdata.realUnit = bands[0].realUnit;
        pdata.unit = bands[0].unit;
        pdata.min = stats.minimum();
        pdata.max = stats.maximum();
        return pdata;
    }

    private static void extractValues(Object value, List<Double> values) {
        if (value instanceof Number) {
            values.add( ((Number)value).doubleValue() );
        } else {
            for (int i=0,n=Array.getLength(value);i<n;i++) {
                Object sub = Array.get(value, i);
                extractValues(sub, values);
            }
        }
    }

    private static GridCoverage readCoverage(GridCoverageResource resource, Envelope work, Boolean deferred)
            throws CoverageStoreException, TransformException, DataStoreException {

        //ensure envelope is no flat
        final GeneralEnvelope workEnv = new GeneralEnvelope(work);
        if (workEnv.isEmpty()) {
            if (workEnv.getSpan(0) <= 0.0) {
                double buffer = workEnv.getSpan(1) / 100.0;
                if (buffer <= 0.0) buffer = 0.00001;
                workEnv.setRange(0, workEnv.getLower(0)-buffer, workEnv.getLower(0)+buffer);
            }
            if (workEnv.getSpan(1) <= 0.0) {
                double buffer = workEnv.getSpan(0) / 100.0;
                if (buffer <= 0.0) buffer = 0.00001;
                workEnv.setRange(1, workEnv.getLower(1)-buffer, workEnv.getLower(1)+buffer);
            }
        }

        /*
        Removed because there is no deferred mecanism anymore
        if (deferred == null) {
        //detec if expected image will be larger then 10Mb to load all the data.
        deferred = true;
        final GridGeometry gridGeometry = resource.getGridGeometry();
        if (gridGeometry.isDefined(GeneralGridGeometry.GRID_TO_CRS) && gridGeometry.isDefined(GeneralGridGeometry.CRS)) {
        final CoordinateReferenceSystem crs = CRS.getHorizontalComponent(gridGeometry.getCoordinateReferenceSystem());
        MathTransform gridToCRS = gridGeometry.getGridToCRS(PixelInCell.CELL_CENTER);
        TransformSeparator ts = new TransformSeparator(gridToCRS);
        ts.addSourceDimensions(0,1);
        ts.addTargetDimensions(0,1);
        try {
        gridToCRS = ts.separate();
        } catch (FactoryException ex) {
        //we have try
        }
        final MathTransform crsToGrid = gridToCRS.inverse();
        final Envelope dataEnv = Envelopes.transform(workEnv, crs);
        final GeneralEnvelope imgEnv = Envelopes.transform(crsToGrid, dataEnv);
        final double nbPixel = imgEnv.getSpan(0) * imgEnv.getSpan(1);
        deferred = nbPixel > 5000000;
        }
        }
        final GridCoverageReadParam param = new GridCoverageReadParam();
        param.setDeferred(deferred);
        param.setEnvelope(workEnv);
        GridCoverage coverage = reader.read(resource.getImageIndex(), param);
        if (coverage instanceof GridCoverage2D) {
        coverage = ((GridCoverage2D) coverage).view(ViewType.GEOPHYSICS);
        }*/
        GridGeometry gg = resource.getGridGeometry().derive().rounding(GridRoundingMode.ENCLOSING).subgrid(workEnv).build();

        return resource.read(gg).forConvertedValues(true);
    }

    public static class Band {
        @JsonIgnore
        private Unit realUnit;
        public String name;
        public String unit;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }
    }

    public static class Axe {
        public String name;
        public String direction;
        public String unit;
        public double[] range;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDirection() {
            return direction;
        }

        public void setDirection(String direction) {
            this.direction = direction;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public double[] getRange() {
            return range;
        }

        public void setRange(double[] range) {
            this.range = range;
        }
    }

    public static class SamplePoint {
        public double distanceToPrevious;
        public Object samples;

        public double getDistanceToPrevious() {
            return distanceToPrevious;
        }

        public void setDistanceToPrevious(double distanceToPrevious) {
            this.distanceToPrevious = distanceToPrevious;
        }

        public Object getSamples() {
            return samples;
        }

        public void setSamples(Object samples) {
            this.samples = samples;
        }
    }

    public static class Profile {
        public List<ProfilLayer> layers = new ArrayList<>();
    }

    public static class ProfilLayer {

        public String name;
        public List<ProfilData> data = new ArrayList<>();
        public String message;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public List<ProfilData> getData() {
            return data;
        }

        public void setData(List<ProfilData> data) {
            this.data = data;
        }
    }

    public static class ProfilData {
        @JsonIgnore
        private Unit realUnit;
        public String unit;
        public double min;
        public double max;
        public List<XY> points = new ArrayList<>();

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public double getMin() {
            return min;
        }

        public void setMin(double min) {
            this.min = min;
        }

        public double getMax() {
            return max;
        }

        public void setMax(double max) {
            this.max = max;
        }
    }

    public static class XY {
        public double x;
        public double y;

        public XY() {}

        public XY(double x, double y) {
            this.x = x;
            this.y = y;
        }

        public double getX() {
            return x;
        }

        public void setX(double x) {
            this.x = x;
        }

        public double getY() {
            return y;
        }

        public void setY(double y) {
            this.y = y;
        }
    }

}
