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
package org.constellation.coverage.core;

// J2SE dependencies
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.util.Utilities;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.xml.MarshallerPool;
import org.constellation.api.ServiceDef;
import org.constellation.api.QueryConstants;
import org.constellation.dto.service.config.wxs.Layer;
import org.constellation.dto.contact.Details;
import org.constellation.portrayal.PortrayalUtil;
import org.constellation.provider.CoverageData;
import org.constellation.provider.Data;
import org.constellation.util.DataReference;
import org.constellation.util.WCSUtils;
import org.constellation.ws.CstlServiceException;
import org.constellation.ws.LayerWorker;
import org.constellation.ws.MimeType;
import org.geotoolkit.display.PortrayalException;
import org.geotoolkit.display2d.service.CanvasDef;
import org.geotoolkit.display2d.service.SceneDef;
import org.geotoolkit.display2d.service.ViewDef;
import org.geotoolkit.geometry.jts.JTSEnvelope2D;
import org.geotoolkit.gml.xml.v311.DirectPositionType;
import org.geotoolkit.gml.xml.v311.EnvelopeType;
import org.geotoolkit.gml.xml.v311.GridType;
import org.geotoolkit.gml.xml.v311.RectifiedGridType;
import org.geotoolkit.image.io.metadata.SpatialMetadata;
import org.geotoolkit.map.MapContext;
import org.geotoolkit.ows.xml.AbstractCapabilitiesCore;
import org.geotoolkit.ows.xml.AbstractOperationsMetadata;
import org.geotoolkit.ows.xml.AbstractServiceIdentification;
import org.geotoolkit.ows.xml.AbstractServiceProvider;
import org.geotoolkit.ows.xml.AcceptFormats;
import org.geotoolkit.ows.xml.Sections;
import org.geotoolkit.ows.xml.v110.BoundingBoxType;
import org.geotoolkit.ows.xml.v110.SectionsType;
import org.geotoolkit.ows.xml.v110.WGS84BoundingBoxType;
import org.apache.sis.referencing.CommonCRS;
import org.geotoolkit.resources.Errors;
import org.geotoolkit.style.MutableStyle;
import org.geotoolkit.temporal.util.TimeParser;
import org.geotoolkit.wcs.xml.Content;
import org.geotoolkit.wcs.xml.CoverageInfo;
import org.geotoolkit.wcs.xml.DescribeCoverage;
import org.geotoolkit.wcs.xml.DescribeCoverageResponse;
import org.geotoolkit.wcs.xml.GetCapabilities;
import org.geotoolkit.wcs.xml.GetCapabilitiesResponse;
import org.geotoolkit.wcs.xml.GetCoverage;
import org.geotoolkit.wcs.xml.WCSMarshallerPool;
import org.geotoolkit.wcs.xml.WCSXmlFactory;
import org.geotoolkit.wcs.xml.v100.CoverageOfferingType;
import org.geotoolkit.wcs.xml.v100.DomainSetType;
import org.geotoolkit.wcs.xml.v100.InterpolationMethod;
import org.geotoolkit.wcs.xml.v100.LonLatEnvelopeType;
import org.geotoolkit.wcs.xml.v100.RangeSetType;
import org.geotoolkit.wcs.xml.v100.SupportedCRSsType;
import org.geotoolkit.wcs.xml.v100.SupportedFormatsType;
import org.geotoolkit.wcs.xml.v100.SupportedInterpolationsType;
import org.geotoolkit.wcs.xml.v111.CoverageDescriptionType;
import org.geotoolkit.wcs.xml.v111.CoverageDomainType;
import org.geotoolkit.wcs.xml.v111.FieldType;
import org.geotoolkit.wcs.xml.v111.GridCrsType;
import org.geotoolkit.wcs.xml.v111.InterpolationMethods;
import org.geotoolkit.wcs.xml.v111.RangeType;
import org.geotoolkit.swe.xml.v200.Field;
import org.opengis.coverage.grid.RectifiedGrid;
import org.opengis.geometry.Envelope;
import org.opengis.metadata.extent.GeographicBoundingBox;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.cs.CoordinateSystemAxis;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.logging.Level;
import javax.inject.Named;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import org.apache.sis.coverage.Category;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.GridCoverage;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.IdentifiedObjects;
import org.apache.sis.referencing.crs.DefaultTemporalCRS;

import org.constellation.api.DataType;
import static org.constellation.coverage.core.WCSConstant.ASCII_GRID;
import static org.constellation.coverage.core.WCSConstant.GEOTIFF;
import static org.constellation.coverage.core.WCSConstant.INTERPOLATION_V100;
import static org.constellation.coverage.core.WCSConstant.INTERPOLATION_V111;
import static org.constellation.coverage.core.WCSConstant.KEY_BBOX;
import static org.constellation.coverage.core.WCSConstant.KEY_COVERAGE;
import static org.constellation.coverage.core.WCSConstant.KEY_CRS;
import static org.constellation.coverage.core.WCSConstant.KEY_FORMAT;
import static org.constellation.coverage.core.WCSConstant.KEY_IDENTIFIER;
import static org.constellation.coverage.core.WCSConstant.KEY_INTERPOLATION;
import static org.constellation.coverage.core.WCSConstant.KEY_RESPONSE_CRS;
import static org.constellation.coverage.core.WCSConstant.KEY_SECTION;
import static org.constellation.coverage.core.WCSConstant.KEY_TIME;
import static org.constellation.coverage.core.WCSConstant.MATRIX;
import static org.constellation.coverage.core.WCSConstant.NETCDF;
import static org.constellation.coverage.core.WCSConstant.SUPPORTED_FORMATS_100;
import static org.constellation.coverage.core.WCSConstant.SUPPORTED_FORMATS_111;
import static org.constellation.coverage.core.WCSConstant.SUPPORTED_INTERPOLATIONS_V100;
import static org.constellation.coverage.core.WCSConstant.TIF;
import static org.constellation.coverage.core.WCSConstant.TIFF;
import static org.constellation.coverage.core.WCSConstant.getOperationMetadata;
import org.constellation.coverage.ws.rs.GeotiffResponse;
import org.constellation.coverage.ws.rs.GridCoverageNCWriter;
import org.constellation.coverage.ws.rs.GridCoverageWriter;
import org.constellation.exception.ConstellationStoreException;
import org.constellation.portrayal.CstlPortrayalService;
import org.constellation.ws.ExceptionCode;
import static org.constellation.ws.ExceptionCode.AXIS_LABEL_INVALID;
import static org.constellation.ws.ExceptionCode.INVALID_SUBSETTING;
import org.constellation.ws.rs.MultiPart;
import org.apache.sis.coverage.Category;
import org.apache.sis.coverage.SampleDimension;
import org.apache.sis.coverage.grid.GridGeometry;
import org.geotoolkit.util.NamesExt;
import org.geotoolkit.gml.xml.v321.AssociationRoleType;
import org.geotoolkit.gml.xml.v321.FileType;
import org.geotoolkit.gmlcov.geotiff.xml.v100.CompressionType;
import org.geotoolkit.gmlcov.geotiff.xml.v100.ParametersType;
import org.geotoolkit.gmlcov.xml.v100.AbstractDiscreteCoverageType;
import org.geotoolkit.gmlcov.xml.v100.ObjectFactory;
import org.geotoolkit.ows.xml.BoundingBox;
import static org.geotoolkit.ows.xml.OWSExceptionCode.CURRENT_UPDATE_SEQUENCE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_CRS;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_DIMENSION_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_FORMAT;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_UPDATE_SEQUENCE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.LAYER_NOT_DEFINED;
import static org.geotoolkit.ows.xml.OWSExceptionCode.LAYER_NOT_QUERYABLE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.MISSING_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.NO_APPLICABLE_CODE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.VERSION_NEGOTIATION_FAILED;
import org.apache.sis.storage.GridCoverageResource;
import org.geotoolkit.swe.xml.v200.AllowedValuesPropertyType;
import org.geotoolkit.swe.xml.v200.AllowedValuesType;
import org.geotoolkit.swe.xml.v200.DataRecordPropertyType;
import org.geotoolkit.swe.xml.v200.DataRecordType;
import org.geotoolkit.swe.xml.v200.QuantityType;
import org.geotoolkit.swe.xml.v200.UnitReference;
import org.geotoolkit.temporal.object.TemporalUtilities;
import org.geotoolkit.wcs.xml.DomainSubset;
import org.geotoolkit.wcs.xml.ServiceMetadata;
import org.geotoolkit.wcs.xml.v200.DimensionSliceType;
import org.geotoolkit.wcs.xml.v200.DimensionTrimType;
import org.geotoolkit.wcs.xml.v200.ExtensionType;
import org.geotoolkit.wcs.xml.v200.ServiceParametersType;
import org.opengis.referencing.crs.CompoundCRS;
import org.opengis.referencing.crs.TemporalCRS;
import org.opengis.util.GenericName;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;

/**
 * Worker for the WCS services in Constellation which services both the REST and
 * SOAP facades by issuing appropriate responses.
 * <p>
 * The classes implementing the REST or SOAP facades to this service will have
 * processed the requests sufficiently to ensure that all the information
 * conveyed by the HTTP request is in one of the fields of the object passed
 * to the worker methods as a parameter.
 * </p>
 *
 * @version 0.9
 *
 * @author Cédric Briançon (Geomatys)
 * @author Guilhem Legal (Geomatys)
 * @since 0.3
 */
@Named("WCSWorker")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public final class DefaultWCSWorker extends LayerWorker implements WCSWorker {

    public DefaultWCSWorker(final String id) {
        super(id, ServiceDef.Specification.WCS);
        if (isStarted) {
            LOGGER.log(Level.INFO, "WCS worker {0} running", id);
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    protected MarshallerPool getMarshallerPool() {
        return WCSMarshallerPool.getInstance();
    }

    /**
     * The DescribeCoverage operation returns an XML file, containing the
     * complete description of the specific coverages requested.
     * <p>
     * This method extends the definition of each coverage given in the
     * Capabilities document with supplementary information.
     * </p>
     *
     * @param request A {@linkplain DescribeCoverage request} with the
     *                parameters of the user message.
     * @return An XML document giving the full description of the requested coverages.
     * @throws CstlServiceException
     */
    @Override
    public DescribeCoverageResponse describeCoverage(final DescribeCoverage request) throws CstlServiceException {
        isWorking();
        final String version = request.getVersion().toString();
        final String userLogin = getUserLogin();
        if (version.isEmpty()) {
            throw new CstlServiceException("The parameter VERSION must be specified.",
                    MISSING_PARAMETER_VALUE, QueryConstants.VERSION_PARAMETER.toLowerCase());
        }

        if (request.getIdentifier().isEmpty()) {
            throw new CstlServiceException("The parameter IDENTIFIER must be specified",
                    MISSING_PARAMETER_VALUE, KEY_IDENTIFIER.toLowerCase());
        }

        if (!"WCS".equalsIgnoreCase(request.getService())) {
            throw new CstlServiceException("The parameter SERVICE must be specified as WCS",
                    MISSING_PARAMETER_VALUE, QueryConstants.SERVICE_PARAMETER.toLowerCase());
        }

        final List<CoverageInfo> coverageOfferings = new ArrayList<>();
        for (String coverage : request.getIdentifier()) {

            final GenericName tmpName = parseCoverageName(coverage);
            final Data layerRef = getLayerReference(userLogin, tmpName);
            if (layerRef.getDataType().equals(DataType.VECTOR)) {
                throw new CstlServiceException("The requested layer is vectorial. WCS is not able to handle it.",
                        LAYER_NOT_DEFINED, KEY_COVERAGE.toLowerCase());
            }
            if (!(layerRef instanceof CoverageData)) {
                // Should not occurs, since we have previously verified the type of layer.
                throw new CstlServiceException("The requested layer is not a coverage. WCS is not able to handle it.",
                        LAYER_NOT_DEFINED, KEY_COVERAGE.toLowerCase());
            }

            final CoverageData coverageRef = (CoverageData) layerRef;
            if (!coverageRef.isQueryable(ServiceDef.Query.WCS_ALL)) {
                throw new CstlServiceException("You are not allowed to request the layer \"" +
                        coverage + "\".", LAYER_NOT_QUERYABLE, KEY_COVERAGE.toLowerCase());
            }

            final Layer configLayer = getConfigurationLayer(tmpName, userLogin);
            final GenericName fullCoverageName = coverageRef.getName();
            final String coverageName;
            if (configLayer.getAlias() != null && !configLayer.getAlias().isEmpty()) {
                coverageName = configLayer.getAlias().trim().replaceAll(" ", "_");
            } else {
                if (NamesExt.getNamespace(fullCoverageName) != null && !NamesExt.getNamespace(fullCoverageName).isEmpty()) {
                    coverageName = NamesExt.getNamespace(fullCoverageName) + ':' + fullCoverageName.tip().toString();
                } else {
                    coverageName = fullCoverageName.tip().toString();
                }
            }
            if (version.equals("1.0.0")) {
                coverageOfferings.add(describeCoverage100(coverageName, coverageRef));
            } else if (version.equals("1.1.1")) {
                coverageOfferings.add(describeCoverage111(coverageName, coverageRef));
            } else if (version.equals("2.0.1")) {
                coverageOfferings.add(describeCoverage200(coverageName, coverageRef));
            } else {
                throw new CstlServiceException("The version number specified for this GetCoverage request " +
                        "is not handled.", NO_APPLICABLE_CODE, QueryConstants.VERSION_PARAMETER.toLowerCase());
            }
        }
        return WCSXmlFactory.createDescribeCoverageResponse(version, coverageOfferings);
    }

    /**
     * Returns the description of the coverage requested in version 1.0.0 of WCS standard.
     *
     * @param request a {@linkplain org.geotoolkit.wcs.xml.v100.DescribeCoverage describe coverage}
     *                request done by the user.
     * @return an XML document giving the full description of a coverage, in version 1.0.0.
     *
     * @throws CstlServiceException
     */
    private CoverageInfo describeCoverage100(final String coverageName, final CoverageData coverageRef) throws CstlServiceException {

        try {
            final GeographicBoundingBox inputGeoBox = coverageRef.getGeographicBoundingBox();

            final List<EnvelopeType> envelopes = new ArrayList<>();

            final LonLatEnvelopeType llenvelope;
            final EnvelopeType envelope;
            if (inputGeoBox != null) {
                final SortedSet<Number> elevations = coverageRef.getAvailableElevations();
                final List<DirectPositionType> pos = WCSUtils.buildPositions(inputGeoBox, elevations);
                llenvelope = new LonLatEnvelopeType(pos, "urn:ogc:def:crs:OGC:1.3:CRS84");
                envelope = new EnvelopeType(pos, "urn:ogc:def:crs:EPSG::4326");
                envelopes.add(envelope);
            } else {
                throw new CstlServiceException("The geographic bbox for the layer is null !",
                        NO_APPLICABLE_CODE);
            }
            final List<String> keywords = Arrays.asList("WCS", coverageName);

            /*
             * Spatial metadata
             */
            final EnvelopeType nativeEnvelope = getGMLEnvelope(coverageRef.getEnvelope());
            if (nativeEnvelope != null && !envelopes.contains(nativeEnvelope)) {
                envelopes.add(nativeEnvelope);
            }

            GridType grid = null;
            try {
                SpatialMetadata meta = coverageRef.getSpatialMetadata();
                if (meta != null) {
                    RectifiedGrid brutGrid = meta.getInstanceForType(RectifiedGrid.class);
                    if (brutGrid != null) {
                        grid = new RectifiedGridType(brutGrid);
                        /*
                         * UGLY PATCH : remove it when geotk will fill this data
                         */
                        if (grid.getDimension() == 0) {
                            int dimension = brutGrid.getOffsetVectors().size();
                            grid.setDimension(dimension);
                        }
                        if (grid.getAxisName().isEmpty()) {
                            if (grid.getDimension() == 2) {
                                grid.setAxisName(Arrays.asList("x", "y"));
                            } else if (grid.getDimension() == 3) {
                                grid.setAxisName(Arrays.asList("x", "y", "z"));
                            }
                        }
                    }
                }
            } catch (ConstellationStoreException ex) {
                LOGGER.log(Level.WARNING, "Unable to get coverage spatial metadata", ex);
            }

            final org.geotoolkit.wcs.xml.v100.SpatialDomainType spatialDomain =
                new org.geotoolkit.wcs.xml.v100.SpatialDomainType(envelopes, Arrays.asList(grid));

            // temporal metadata
            final List<Object> times = WCSUtils.formatDateList(coverageRef.getAvailableTimes());
            final DomainSetType domainSet = new DomainSetType(spatialDomain, times);
            //TODO complete
            final RangeSetType rangeSet = new RangeSetType(null, coverageName, coverageName, null, null, null, null);
            //supported CRS
            final SupportedCRSsType supCRS = new SupportedCRSsType("urn:ogc:def:crs:EPSG::4326");
            supCRS.addNativeCRSs(nativeEnvelope.getSrsName());

            // supported formats
            String nativeFormat = coverageRef.getImageFormat();
            if (nativeFormat == null || nativeFormat.isEmpty()) {
                nativeFormat = "unknown";
            }
            final SupportedFormatsType supForm = new SupportedFormatsType(nativeFormat, SUPPORTED_FORMATS_100);

            //supported interpolations
            final SupportedInterpolationsType supInt = INTERPOLATION_V100;

            //we build the coverage offering for this layer/coverage
            return new CoverageOfferingType(null, coverageName,
                    coverageName, "", llenvelope,
                    keywords, domainSet, rangeSet, supCRS, supForm, supInt);
        } catch (ConstellationStoreException ex) {
            throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
        }
    }

    /**
     * TODO remove when geotk > MC0028
     *
     * @param env
     * @return
     */
    private EnvelopeType getGMLEnvelope(final org.opengis.geometry.Envelope env) {
        if (env != null) {
            List<DirectPositionType> pos = new ArrayList<>();
            String srsName;
            pos.add(new DirectPositionType(env.getLowerCorner(), false));
            pos.add(new DirectPositionType(env.getUpperCorner(), false));
            final CoordinateReferenceSystem crs = env.getCoordinateReferenceSystem();
            if (crs != null) {
                try {
                    if (crs instanceof CompoundCRS) {
                        final StringBuilder sb = new StringBuilder();
                        final CompoundCRS compCrs = (CompoundCRS) crs;
                        // see OGC 07-092r3 7.5.2
                        sb.append("urn:ogc:def:crs,");
                        for (CoordinateReferenceSystem child : compCrs.getComponents()) {
                            String childSrs = IdentifiedObjects.lookupURN(child, null);
                            if (childSrs != null) {
                                if (childSrs.startsWith("urn:ogc:def:")) {
                                    childSrs = childSrs.substring(12);
                                }
                                sb.append(childSrs).append(',');
                            } else {
                                sb.append("crs:EPSG::unknow,");
                            }
                        }
                        sb.deleteCharAt(sb.length() - 1);
                        srsName = sb.toString();
                    } else {
                        srsName = IdentifiedObjects.lookupURN(crs, null);
                        if (srsName == null) {
                            srsName = "urn:ogc:def:crs:EPSG::unknow";
                        }
                    }
                    return new EnvelopeType(pos, srsName);
                } catch (FactoryException ex) {
                    LOGGER.log(Level.SEVERE, "Factory exception xhile creating GML envelope from opengis one", ex);
                }
            }
        }
        return null;
    }

    /**
     * Returns the description of the coverage requested in version 1.1.1 of WCS standard.
     *
     * @param request a {@linkplain org.geotoolkit.wcs.xml.v111.DescribeCoverage describe coverage}
     *                request done by the user.
     * @return an XML document giving the full description of a coverage, in version 1.1.1.
     *
     * @throws CstlServiceException
     */
    private CoverageInfo describeCoverage111(final String coverageName, final CoverageData coverageRef) throws CstlServiceException {
        try {
            final GeographicBoundingBox inputGeoBox = coverageRef.getGeographicBoundingBox();

            WGS84BoundingBoxType outputBBox = null;
            if (inputGeoBox != null) {
                outputBBox = new WGS84BoundingBoxType(inputGeoBox);
            }
            /*
             * Spatial metadata
             */
            final BoundingBoxType nativeEnvelope = new BoundingBoxType(coverageRef.getEnvelope());

            GridCrsType grid = null;
            try {
                SpatialMetadata meta = WCSUtils.adapt(
                        coverageRef.getSpatialMetadata(),
                        coverageRef.getGeometry(),
                        coverageRef.getSampleDimensions().toArray(new SampleDimension[0])
                );
                RectifiedGrid brutGrid = meta.getInstanceForType(RectifiedGrid.class);
                if (brutGrid != null) {
                    grid = new GridCrsType(brutGrid);
                }
            } catch (DataStoreException|ConstellationStoreException ex) {
                LOGGER.log(Level.WARNING, "Unable to get coverage spatial metadata", ex);
            }

            // spatial metadata
            final org.geotoolkit.wcs.xml.v111.SpatialDomainType spatial =
                    new org.geotoolkit.wcs.xml.v111.SpatialDomainType(outputBBox,nativeEnvelope, grid, null, null, null);

            //general metadata
            final String title = coverageName;
            final String abstractt = "";
            final List<String> keywords = Arrays.asList("WCS", coverageName);

            // temporal metadata
            final List<Object> times = WCSUtils.formatDateList(coverageRef.getAvailableTimes());
            final CoverageDomainType domain = new CoverageDomainType(spatial, times);

            //supported interpolations
            final InterpolationMethods interpolations = INTERPOLATION_V111;

            final String thematic = "";
            final RangeType range = new RangeType(new FieldType(thematic,
                    null, new org.geotoolkit.ows.xml.v110.CodeType("0.0"), interpolations));

            //supported CRS
            final List<String> supportedCRS = Arrays.asList("urn:ogc:def:crs:EPSG::4326");

            return new CoverageDescriptionType(title, abstractt,
                    keywords, coverageName, domain, range, supportedCRS, SUPPORTED_FORMATS_111);
        } catch (ConstellationStoreException ex) {
            throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
        }
    }

    /**
     * Returns the description of the coverage requested in version 2.0.1 of WCS standard.
     *
     * TODO : do not use metadata to build description. All can be set from grid geometry and sample dimensions.
     *
     * @param request a {@linkplain org.geotoolkit.wcs.xml.v200.DescribeCoverage describe coverage}
     *                request done by the user.
     * @return an XML document giving the full description of a coverage, in version 2.0.1.
     *
     * @throws CstlServiceException
     */
    private org.geotoolkit.wcs.xml.v200.CoverageDescriptionType describeCoverage200(final String coverageName, final CoverageData coverageRef) throws CstlServiceException {
        try {

            /*
             * Spatial metadata
             */
            final org.geotoolkit.gml.xml.v321.EnvelopeType nativeEnvelope = new org.geotoolkit.gml.xml.v321.EnvelopeType(coverageRef.getEnvelope());

            org.geotoolkit.gml.xml.v321.GridType grid = null;
            try {
                SpatialMetadata meta = WCSUtils.adapt(
                        coverageRef.getSpatialMetadata(),
                        coverageRef.getGeometry(),
                        coverageRef.getSampleDimensions().toArray(new SampleDimension[0])
                );

                RectifiedGrid brutGrid = meta.getInstanceForType(RectifiedGrid.class);
                if (brutGrid.getExtent() != null) {
                    CoordinateReferenceSystem crs = meta.getInstanceForType(CoordinateReferenceSystem.class);
                    grid = new org.geotoolkit.gml.xml.v321.RectifiedGridType(brutGrid, crs);
                    grid.setId("grid-" + coverageName);

                    // update native envelope axis labels
                    if (brutGrid.getAxisNames() != null) {
                        nativeEnvelope.setAxisLabels(brutGrid.getAxisNames());
                    } else if (crs != null) {
                        final List<String> axisNames = new ArrayList<>();
                        for (int i = 0; i < crs.getCoordinateSystem().getDimension(); i++) {
                            axisNames.add(crs.getCoordinateSystem().getAxis(i).getAbbreviation());
                        }
                        nativeEnvelope.setAxisLabels(axisNames);
                    }
                }

            } catch (DataStoreException|ConstellationStoreException ex) {
                LOGGER.log(Level.WARNING, "Unable to get coverage spatial metadata", ex);
            }

            // spatial metadata
            final org.geotoolkit.gml.xml.v321.DomainSetType domain = new org.geotoolkit.gml.xml.v321.DomainSetType(grid);

            final List<SampleDimension> bands = coverageRef.getSampleDimensions();
            final List<Field> fields = new ArrayList<>();
            if (bands != null) {
                for (SampleDimension band : bands) {
                    final QuantityType quantity = new QuantityType();
                    if (band.getUnits() != null) {
                        quantity.setUom(new UnitReference(band.getUnits().toString()));
                    }
                    // TODO select only one category => which one?
                    if (band.getCategories() != null) {
                        for (Category cat : band.getCategories()) {
                            final AllowedValuesType av = new AllowedValuesType();
                            if (cat.getName() != null) {
                                av.setId(cat.getName().toString());
                            }
                            if (cat.getMeasurementRange().orElse(null) != null) {
                                av.setMin(cat.getMeasurementRange().get().getMinDouble());
                                av.setMax(cat.getMeasurementRange().get().getMaxDouble());
                            }
                            quantity.setConstraint(new AllowedValuesPropertyType(av));
                        }
                    }
                    final Field f = new Field(band.getName().toString(), quantity);
                    fields.add(f);
                }
            }
            final DataRecordType dataRecord;
            if (!fields.isEmpty()) {
                dataRecord = new DataRecordType(null, null, false, fields);
            } else {
                dataRecord = null;
            }
            final DataRecordPropertyType rangeType = new DataRecordPropertyType(dataRecord);
            final ServiceParametersType serviceParametersType = new ServiceParametersType(new QName("GridCoverage"), coverageRef.getImageFormat());
            org.geotoolkit.wcs.xml.v200.CoverageDescriptionType result = new org.geotoolkit.wcs.xml.v200.CoverageDescriptionType(coverageName, nativeEnvelope, domain, rangeType, serviceParametersType);
            result.setId(coverageName);
            return result;
        } catch (ConstellationStoreException ex) {
            throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
        }
    }

    /**
     * Describe the capabilities and the layers available for the WCS service.
     *
     * @param request The request done by the user.
     * @return a WCSCapabilities XML document describing the capabilities of this service.
     *
     * @throws CstlServiceException
     */
    @Override
    public GetCapabilitiesResponse getCapabilities(final GetCapabilities request) throws CstlServiceException {
        isWorking();
        //we begin by extract the base attribute
        String version = request.getVersion().toString();
        final String userLogin = getUserLogin();
        if (version.isEmpty()) {
            // For the moment the only version that we really support is this one.
            version = "1.0.0";
        }

        if (!"WCS".equalsIgnoreCase(request.getService())) {
            throw new CstlServiceException("The parameter SERVICE must be specified as WCS",
                    MISSING_PARAMETER_VALUE, QueryConstants.SERVICE_PARAMETER.toLowerCase());
        }

        //set the current updateSequence parameter
        final boolean returnUS = returnUpdateSequenceDocument(request.getUpdateSequence(), version);
        if (returnUS) {
            return WCSXmlFactory.createCapabilitiesResponse(version, getCurrentUpdateSequence());
        }

        /*
         * In WCS 1.0.0 the user can request only one section
         * ( or all by omitting the parameter section)
         */
        final Sections sections = request.getSections();
        if (sections != null && !sections.getSection().isEmpty()) {
            for (String sec : sections.getSection()) {
                if (!SectionsType.getExistingSections(version).contains(sec)) {
                    throw new CstlServiceException("This sections " + sec + " is not allowed", INVALID_PARAMETER_VALUE, KEY_SECTION.toLowerCase());
                }
            }
        }

        // if the user have specified one format accepted (only one for now != spec)
        final String format;
        if (version.equals("1.1.1")) {
            final AcceptFormats formats = request.getAcceptFormats();
            if (formats == null || formats.getOutputFormat().isEmpty()) {
                format = MimeType.TEXT_XML;
            } else {
                format = formats.getOutputFormat().get(0);
                if (!format.equals(MimeType.TEXT_XML) && !format.equals(MimeType.APP_XML)) {
                    throw new CstlServiceException("This format " + format + " is not allowed",
                            INVALID_FORMAT, KEY_FORMAT.toLowerCase());
                }
            }
        }

        // If the getCapabilities response is in cache, we just return it.
        final AbstractCapabilitiesCore cachedCapabilities = getCapabilitiesFromCache(version, null);
        if (cachedCapabilities != null) {
            return (GetCapabilitiesResponse) cachedCapabilities.applySections(sections);
        }

        // We unmarshall the static capabilities document.
        final Details skeleton = getStaticCapabilitiesObject("WCS", null);
        final GetCapabilitiesResponse staticCapabilities = WCSConstant.createCapabilities(version, skeleton);
        final AbstractServiceIdentification si = staticCapabilities.getServiceIdentification();
        final AbstractServiceProvider sp = staticCapabilities.getServiceProvider();
        final AbstractOperationsMetadata om = getOperationMetadata(version);
        om.updateURL(getServiceUrl());

        WCSConstant.applyProfile(version, si);

        final List<CoverageInfo> offBrief = new ArrayList<>();
        final List<Layer> layers = getConfigurationLayers(userLogin);
        try {
            for (Layer configLayer : layers) {
                final Data layer = getLayerReference(configLayer);

                if (layer == null) {
                    throw new CstlServiceException("There is no existing layer named:" + configLayer.getName());
                }

                if (layer.getDataType().equals(DataType.VECTOR)) {
                    continue;
                }
                if (!layer.isQueryable(ServiceDef.Query.WCS_ALL)) {
                    continue;
                }
                if (layer.getGeographicBoundingBox() == null) {
                    // The coverage does not contain geometric information, we do not want this coverage
                    // in the capabilities response.
                    continue;
                }

                final CoverageInfo co;
                if (version.equals("1.0.0")) {
                    co = getCoverageInfo100(layer, configLayer);
                } else {
                    co = getCoverageInfo(version, layer, configLayer);
                }
                /*
                * coverage brief customisation
                 */
                if (configLayer.getTitle() != null) {
                    co.setTitle(configLayer.getTitle());
                }
                if (configLayer.getAbstrac() != null) {
                    co.setAbstract(configLayer.getAbstrac());
                }
                if (configLayer.getKeywords() != null && !configLayer.getKeywords().isEmpty()) {
                    co.setKeywordValues(configLayer.getKeywords());
                }
                if (configLayer.getMetadataURL() != null && !configLayer.getMetadataURL().isEmpty() && configLayer.getMetadataURL().get(0).getOnlineResource() != null) {
                    // TODO handle multiple metadata URL
                    co.setMetadata(configLayer.getMetadataURL().get(0).getOnlineResource().getValue());
                }
                offBrief.add(co);
            }
        } catch (ConstellationStoreException exception) {
            throw new CstlServiceException(exception, NO_APPLICABLE_CODE);
        }
        final Content contents = WCSXmlFactory.createContent(version, offBrief);
        final ServiceMetadata sm = WCSConstant.getServiceMetadata(version);
        final GetCapabilitiesResponse response = WCSXmlFactory.createCapabilitiesResponse(version, si, sp, om, contents, getCurrentUpdateSequence(), sm);
        putCapabilitiesInCache(version, null, response);
        return (GetCapabilitiesResponse) response.applySections(sections);
    }

    /**
     * Returns the {@linkplain GetCapabilitiesResponse GetCapabilities} response of the request
     * given by parameter, in version 1.0.0 of WCS.
     *
     * @param request The request done by the user, in version 1.0.0.
     * @return a WCSCapabilities XML document describing the capabilities of this service.
     *
     * @throws CstlServiceException
     * @throws JAXBException when unmarshalling the default GetCapabilities file.
     */
    private CoverageInfo getCoverageInfo100(final Data layer, final Layer configLayer) throws ConstellationStoreException {

        final GenericName fullLayerName = layer.getName();
        final String layerName;
        if (configLayer.getAlias() != null && !configLayer.getAlias().isEmpty()) {
            layerName = configLayer.getAlias().trim().replaceAll(" ", "_");
        } else {
            if (NamesExt.getNamespace(fullLayerName) != null && !NamesExt.getNamespace(fullLayerName).isEmpty()) {
                layerName = NamesExt.getNamespace(fullLayerName) + ':' + fullLayerName.tip().toString();
            } else {
                layerName = fullLayerName.tip().toString();
            }
        }
        final GeographicBoundingBox inputGeoBox = layer.getGeographicBoundingBox();
        final List<DirectPositionType> pos = WCSUtils.buildPositions(inputGeoBox, layer.getAvailableElevations());
        final LonLatEnvelopeType outputBBox = new LonLatEnvelopeType(pos, "urn:ogc:def:crs:OGC:1.3:CRS84");

        final SortedSet<Date> dates = layer.getAvailableTimes();
        if (dates != null && dates.size() >= 2) {
            /*
             * Adds the first and last date available, since in the WCS GetCapabilities,
             * it is a brief description of the capabilities.
             * To get the whole available values, the describeCoverage request has to be
             * done on a specific coverage.
             */
            final Date firstDate = dates.first();
            final Date lastDate = dates.last();
            synchronized (WCSUtils.FORMATTER) {
                outputBBox.addTimePosition(WCSUtils.FORMATTER.format(firstDate), WCSUtils.FORMATTER.format(lastDate));
            }
        }

        return WCSXmlFactory.createCoverageInfo("1.0.0", layerName, layerName, null, outputBBox, null);
    }

    /**
     * Returns the {@linkplain GetCapabilitiesResponse GetCapabilities} response of the request given
     * by parameter, in version 1.1.1 of WCS.
     *
     * @param request The request done by the user, in version 1.1.1.
     * @return a WCSCapabilities XML document describing the capabilities of this service.
     *
     * @throws CstlServiceException
     */
    private CoverageInfo getCoverageInfo(final String version, final Data layer, final Layer configLayer) throws ConstellationStoreException {

        final CoverageData coverageLayer = (CoverageData) layer;
        final String identifier;
        if (configLayer.getAlias() != null && !configLayer.getAlias().isEmpty()) {
            identifier = configLayer.getAlias().trim().replaceAll(" ", "_");
        } else {
            identifier = coverageLayer.getName().tip().toString();
        }

        final String title = coverageLayer.getName().tip().toString();
        final String remark = "";

        final GeographicBoundingBox inputGeoBox = coverageLayer.getGeographicBoundingBox();
        final BoundingBox outputBBox = WCSXmlFactory.buildWGS84BoundingBox(version, inputGeoBox);
        final String coverageSubType = "GridCoverage";
        return WCSXmlFactory.createCoverageInfo(version, identifier, title,
                remark, outputBBox, coverageSubType);
    }

    /**
     * Get the coverage values for a specific coverage specified.
     * According to the output format chosen, the response could be an
     * {@linkplain RenderedImage image} or data representation.
     *
     * @param request The request done by the user.
     * @return An {@linkplain RenderedImage image}, or a data representation.
     *
     * @throws CstlServiceException
     */
    @Override
    public Object getCoverage(final GetCoverage request) throws CstlServiceException {
        isWorking();
        final String inputVersion = request.getVersion().toString();
        final String userLogin = getUserLogin();
        if (inputVersion == null) {
            throw new CstlServiceException("The parameter version must be specified",
                    MISSING_PARAMETER_VALUE, QueryConstants.VERSION_PARAMETER.toLowerCase());
        } else if (!"1.0.0".equals(inputVersion) &&
                   !"2.0.1".equals(inputVersion) &&
                   !"1.1.1".equals(inputVersion)) {
            throw new CstlServiceException("The version number specified for this request " + inputVersion +
                    " is not handled.", VERSION_NEGOTIATION_FAILED, QueryConstants.VERSION_PARAMETER.toLowerCase());
        }

        if (!"WCS".equalsIgnoreCase(request.getService())) {
            throw new CstlServiceException("The parameter SERVICE must be specified as WCS",
                    MISSING_PARAMETER_VALUE, QueryConstants.SERVICE_PARAMETER.toLowerCase());
        }

        final String coverageName = request.getCoverage();
        if (coverageName == null) {
            throw new CstlServiceException("You must specify the parameter: COVERAGE", INVALID_PARAMETER_VALUE,
                    KEY_COVERAGE.toLowerCase());
        }
        final GenericName tmpName = parseCoverageName(request.getCoverage());
        final Data tmplayerRef = getLayerReference(userLogin, tmpName);
        if (!tmplayerRef.isQueryable(ServiceDef.Query.WCS_ALL) || tmplayerRef.getDataType().equals(DataType.VECTOR)) {
            throw new CstlServiceException("You are not allowed to request the layer \"" +
                    tmplayerRef.getName() + "\".", INVALID_PARAMETER_VALUE, KEY_COVERAGE.toLowerCase());
        }
        if (!(tmplayerRef instanceof CoverageData)) {
            // Should not occurs, since we have previously verified the type of layer.
            throw new CstlServiceException("The requested layer is not a coverage. WCS is not able to handle it.",
                    LAYER_NOT_DEFINED, KEY_COVERAGE.toLowerCase());
        }
        final CoverageData layerRef = (CoverageData) tmplayerRef;
        final Layer configLayer = getConfigurationLayer(tmpName, userLogin);

        // TODO : if no subsetting is done, and queried output format is the same as queried data, we could directly
        // send back original data file. It would be far less complex, and far more optimized.
        if ("2.0.1".equals(inputVersion)) {
            return getCoverage200(request, layerRef, configLayer);
        }

        Date date = null;
        try {
            date = TimeParser.toDate(request.getTime());
        } catch (ParseException ex) {
            throw new CstlServiceException("Parsing of the date failed. Please verify that the specified" +
                    " date is compliant with the ISO-8601 standard.", ex, INVALID_PARAMETER_VALUE,
                    KEY_TIME.toLowerCase());
        }

        // we verify the interpolation method even if we don't use it
        try {
            if (request.getInterpolationMethod() != null) {
                final InterpolationMethod interpolation = (InterpolationMethod) request.getInterpolationMethod();
                if (!SUPPORTED_INTERPOLATIONS_V100.contains(interpolation)) {
                    throw new CstlServiceException("Unsupported interpolation: " + request.getInterpolationMethod(), INVALID_PARAMETER_VALUE, KEY_INTERPOLATION.toLowerCase());
                }
            }
        } catch (IllegalArgumentException ex) {
            throw new CstlServiceException(ex.getMessage(), INVALID_PARAMETER_VALUE, KEY_INTERPOLATION.toLowerCase());
        }

        Envelope envelope;
        try {
            envelope = request.getEnvelope();
        } catch (FactoryException ex) {
            throw new CstlServiceException(ex, INVALID_PARAMETER_VALUE, KEY_BBOX.toLowerCase());
        }
        /*
         * Here the envelope can be null, if we have specified a TIME parameter. In this case we
         * do not have to test whether the bbox parameter are into the CRS axes definition.
         */
        if (envelope != null) {
            // Ensures the bbox specified is inside the range of the CRS.
            final CoordinateReferenceSystem objectiveCrs;
            try {
                objectiveCrs = request.getCRS();
            } catch (FactoryException ex) {
                throw new CstlServiceException(ex, INVALID_CRS, KEY_CRS.toLowerCase());
            }
            for (int i = 0; i < objectiveCrs.getCoordinateSystem().getDimension(); i++) {
                final CoordinateSystemAxis axis = objectiveCrs.getCoordinateSystem().getAxis(i);
                if (envelope.getMaximum(i) < axis.getMinimumValue() ||
                    envelope.getMinimum(i) > axis.getMaximumValue())
                {
                    throw new CstlServiceException(Errors.format(Errors.Keys.IllegalRange_2,
                            envelope.getMinimum(i), envelope.getMaximum(i)),
                            INVALID_DIMENSION_VALUE, KEY_BBOX.toLowerCase());
                }
            }
            // Ensures the requested envelope has, at least, a part that intersects the valid envelope
            // for the coverage.
            try {
                final GeographicBoundingBox geoBbox = layerRef.getGeographicBoundingBox();
                if (geoBbox == null) {
                    throw new CstlServiceException("The request coverage \""+ layerRef.getName() +"\" has" +
                                                   " no geometric information.", NO_APPLICABLE_CODE);
                }
                final GeneralEnvelope validGeoEnv = new GeneralEnvelope(geoBbox);
                Envelope requestGeoEnv = envelope;
                // We have to transform the objective envelope into an envelope that uses a geographic CRS,
                // in order to be able to verify the intersection between those two envelopes.
                if (!Utilities.equalsIgnoreMetadata(envelope.getCoordinateReferenceSystem(), CommonCRS.WGS84.normalizedGeographic())) {
                    try {
                        requestGeoEnv = Envelopes.transform(envelope, CommonCRS.WGS84.normalizedGeographic());
                    } catch (TransformException ex) {
                        throw new CstlServiceException(ex, NO_APPLICABLE_CODE, KEY_BBOX.toLowerCase());
                    }
                }
                if (!(validGeoEnv.intersects(requestGeoEnv, false))) {
                    throw new CstlServiceException("The requested bbox is outside the domain of validity " +
                            "for this coverage", NO_APPLICABLE_CODE, KEY_BBOX.toLowerCase());
                }
            } catch (ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE, KEY_BBOX.toLowerCase());
            }
        } else if (date == null) {

            throw new CstlServiceException("One of Time or Envelope has to be specified", MISSING_PARAMETER_VALUE);

        } else {
            // We take the envelope from the data provider. That envelope can be a little bit imprecise.
            try {
                final GeographicBoundingBox geoBbox = layerRef.getGeographicBoundingBox();
                if (geoBbox == null) {
                    throw new CstlServiceException("The request coverage \""+ layerRef.getName() +"\" has" +
                                                   " no geometric information.", NO_APPLICABLE_CODE);
                }
                envelope = new JTSEnvelope2D(geoBbox.getWestBoundLongitude(), geoBbox.getEastBoundLongitude(),
                        geoBbox.getSouthBoundLatitude(), geoBbox.getNorthBoundLatitude(),
                        CommonCRS.WGS84.normalizedGeographic());
            } catch (ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE, KEY_BBOX.toLowerCase());
            }
        }
        final JTSEnvelope2D refEnvel;
        try {
            final CoordinateReferenceSystem responseCRS = request.getResponseCRS();
            if (responseCRS != null && !Utilities.equalsIgnoreMetadata(responseCRS, envelope.getCoordinateReferenceSystem())) {
                final Envelope responseEnv = Envelopes.transform(envelope, responseCRS);
                refEnvel = new JTSEnvelope2D(responseEnv);
            } else {
                refEnvel = new JTSEnvelope2D(envelope);
            }
        } catch (FactoryException ex) {
            throw new CstlServiceException(ex, INVALID_CRS, KEY_CRS.toLowerCase());
        } catch (TransformException ex) {
            throw new CstlServiceException(ex, INVALID_CRS, KEY_RESPONSE_CRS.toLowerCase());
        }

        Dimension size = request.getSize();
        if (size == null) {
            // Try with resx/resy, those parameters should be filled.
            final List<Double> resolutions = request.getResolutions();
            if (resolutions == null || resolutions.isEmpty()) {
                // Should not occurs since it is already tested
                throw new CstlServiceException("If width/height are not specified, you have to give resx/resy");
            }
            final double resx = resolutions.get(0);
            final double resy = resolutions.get(1);
            final double envWidth = refEnvel.getSpan(0);
            final double envHeight = refEnvel.getSpan(1);
            // Assume that the resolution is in unit per px -> unit / (unit/pixel) -> px
            // For example to obtain an image whose width is 1024 pixels, representing 360 degrees,
            // the resolution on the x axis is 360 / 1024 = 0,3515625 degrees/pixels.
            // In our case, we want to know the image width using the size of the envelope and the
            // given resolution on that axis, so: image_width = envelope_width / resx
            final int newWidth = (int) Math.round(envWidth / resx);
            final int newHeight = (int) Math.round(envHeight / resy);
            size = new Dimension(newWidth, newHeight);
        }

        final Double elevation = (envelope.getDimension() > 2) ? envelope.getMedian(2) : null;

        /*
         * Generating the response.
         * It can be a text one (format MATRIX) or an image one (png, gif ...).
         */
        final String format = request.getFormat();
        if (format.equalsIgnoreCase(MATRIX) || format.equalsIgnoreCase(ASCII_GRID)) {

            //NOTE ADRIAN HACKED HERE
            final RenderedImage image;
            try {
                final GridCoverage gridCov = layerRef.getCoverage(refEnvel, size, elevation, date);
                image = gridCov.render(null);
            } catch (IOException | ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

            return image;

        } else if (format.equalsIgnoreCase(NETCDF)) {

            throw new CstlServiceException(new IllegalArgumentException(
                    "Constellation does not support netcdf writing."),
                    INVALID_FORMAT, KEY_FORMAT.toLowerCase());

        } else if (format.equalsIgnoreCase(GEOTIFF) || format.equalsIgnoreCase(TIFF) || format.equalsIgnoreCase(TIF)) {
            try {
                GeotiffResponse response = new GeotiffResponse();
                response.metadata = layerRef.getSpatialMetadata();
                response.coverage = layerRef.getCoverage(refEnvel, size, elevation, date);
                return response;
            } catch (IOException | ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

        } else {
            // We are in the case of an image format requested.
            //NOTE: ADRIAN HACKED HERE

            // SCENE
            final Map<String, Object> renderParameters = new HashMap<>();

            renderParameters.put(KEY_TIME, date);
            renderParameters.put("ELEVATION", elevation);
            final SceneDef sdef = new SceneDef();

            final List<DataReference> styles = configLayer.getStyles();
            final MutableStyle style;
            if (!styles.isEmpty()) {
                final DataReference styleName = styles.get(0);
                final MutableStyle incomingStyle = getStyle(styleName);
                style = WCSUtils.filterStyle(incomingStyle, request.getRangeSubset());
            } else {
                style = null;
            }
            try {
                final MapContext context = PortrayalUtil.createContext(layerRef, style, renderParameters);
                sdef.setContext(context);
            } catch (ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

            // VIEW
            final Double azimuth = 0.0; //HARD CODED SINCE PROTOCOL DOES NOT ALLOW
            final ViewDef vdef = new ViewDef(refEnvel, azimuth);

            // CANVAS
            Color background = null;
            if (MimeType.IMAGE_JPEG.equalsIgnoreCase(format)) {
                background = Color.WHITE;
            }
            final CanvasDef cdef = new CanvasDef(size, background);

            // IMAGE
            final BufferedImage img;
            try {
                img = CstlPortrayalService.getInstance().portray(sdef, vdef, cdef);
            } catch (PortrayalException ex) {
                /*
                 * TODO: the binding xml for WCS and GML do not support the exceptions format,
                 * consequently we can't extract the exception output mime-type information from
                 * the request. Maybe a more recent version of the GML 3 spec has fixed this bug ...
                 */
                //if (exceptions != null && exceptions.equalsIgnoreCase(EXCEPTIONS_INIMAGE)) {
                //    img = Cstl.Portrayal.writeInImage(ex, abstractRequest.getSize());
                //} else {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
                //}
            }

            return img;
        }
    }

    private Object getCoverage200(final GetCoverage request, final CoverageData layerRef, final Layer configLayer) throws CstlServiceException {
        boolean isMultiPart = false;
        if (request.getMediaType() != null) {
            if (request.getMediaType().equals("multipart/mixed")) {
                isMultiPart = true;
            } else {
                throw new CstlServiceException("Only multipart/mixed is supported for mediaType parameter", INVALID_PARAMETER_VALUE);
            }
        }

        final SpatialMetadata metadata;
        final GridCoverageResource ref = (GridCoverageResource) layerRef.getOrigin();
        final GridGeometry gridGeometry;

        final CoordinateReferenceSystem crs;
        try {
            gridGeometry = ref.getGridGeometry();
            crs = gridGeometry.getCoordinateReferenceSystem();
            metadata = layerRef.getSpatialMetadata();
        } catch (ConstellationStoreException ex) {
            throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
        } catch (DataStoreException ex) {
            throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
        }

        //combine envelope with domain subset
        final GeneralEnvelope readEnv = new GeneralEnvelope(gridGeometry.getEnvelope());
        if (!request.getDomainSubset().isEmpty()) {

            // trim / slice the envelope
            Set<String> usedDimension = new HashSet<>();
            for (DomainSubset subset : request.getDomainSubset()) {
                String dimension = null;
                if (subset instanceof DimensionTrimType) {
                    final DimensionTrimType trim = (DimensionTrimType) subset;
                    dimension = trim.getDimension();
                    final int dimIdx = dimensionIndex(trim.getDimension(), crs);
                    if (dimIdx == -1) {
                        throw new CstlServiceException("There is no such dimension: " + trim.getDimension(), AXIS_LABEL_INVALID);
                    } else {
                        double minVal = readEnv.getMinimum(dimIdx);
                        double maxVal = readEnv.getMaximum(dimIdx);
                        double low = toAxisValue(trim.getTrimLow(), crs, dimIdx, minVal);
                        double high = toAxisValue(trim.getTrimHigh(), crs, dimIdx, maxVal);

                        //verif that trim value does not overlap envelope
                        if (low < minVal || low > maxVal || high > maxVal || high < minVal) {
                            throw new CstlServiceException("Subsetting params overlap the envelope extent",
                                    INVALID_SUBSETTING);
                        }
                        //verif that trim value is correct (low < high)
                        if (low > high) {
                            throw new CstlServiceException("Subsetting params are invalid (low > high)",
                                    INVALID_SUBSETTING);
                        }

                        readEnv.setRange(dimIdx, low, high);
                    }

                } else if (subset instanceof DimensionSliceType) {
                    final DimensionSliceType slice = (DimensionSliceType) subset;
                    dimension = slice.getDimension();
                    final int dimIdx = dimensionIndex(slice.getDimension(), crs);
                    if (dimIdx == -1) {
                        throw new CstlServiceException("There is no such dimension: " + slice.getDimension(), AXIS_LABEL_INVALID);
                    } else {
                        double minVal = readEnv.getMinimum(dimIdx);
                        double maxVal = readEnv.getMaximum(dimIdx);
                        double pt = toAxisValue(slice.getSlicePoint(), crs, dimIdx, readEnv.getMedian(dimIdx));

                        //verif that trim value does not overlap envelope
                        if (pt < minVal || pt > maxVal) {
                            throw new CstlServiceException("Subsetting params overlap the envelope extent",
                                    INVALID_SUBSETTING);
                        }

                        readEnv.setRange(dimIdx, pt, pt);
                    }
                }
                // verify that multiple operations are not applied on the same dimension
                if (dimension != null) {
                    if (usedDimension.contains(dimension)) {
                        throw new CstlServiceException("A GetCoverage request shall contain at most one subsetting operation for each of the dimensions of the coverage addressed",
                                AXIS_LABEL_INVALID);
                    } else {
                        usedDimension.add(dimension);
                    }
                }
            }
        }

        /*
         * Generating the response.
         * It can be a text one (format MATRIX) or an image one (png, gif ...).
         */
        final String format = request.getFormat();
        if (format.equalsIgnoreCase(MATRIX) || format.equalsIgnoreCase(ASCII_GRID)) {

            //NOTE ADRIAN HACKED HERE
            final RenderedImage image;
            try {
                final GridCoverage coverage = layerRef.getCoverage(readEnv, null, null, null);
                image = coverage.render(null);
            } catch (IOException | ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

            return image;

        } else if (format.equalsIgnoreCase(MimeType.NETCDF)) {

            try {
                final GridCoverage coverage = layerRef.getCoverage(readEnv, null, null, null);
                final SimpleEntry response = new SimpleEntry(coverage, metadata);
                if (isMultiPart) {
                    final File img = File.createTempFile(layerRef.getName().tip().toString(), ".nc");
                    GridCoverageNCWriter.writeInStream(response, new FileOutputStream(img));
                    final String xml = buildXmlPart(describeCoverage200(layerRef.getName().tip().toString(), layerRef), format);
                    final MultiPart multiPart = new MultiPart();
                    multiPart.bodyPart("application/xml", xml)
                            .bodyPart(format, img);
                    return multiPart;
                } else {
                    return response;
                }

            } catch (IOException | JAXBException | ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

        } else if (format.equalsIgnoreCase(MimeType.IMAGE_TIFF)) {
            try {
                final GeotiffResponse response = new GeotiffResponse();
                response.coverage = layerRef.getCoverage(readEnv, null, null, null);
                response.metadata = metadata;
                if (request.getExtension() instanceof ExtensionType) {
                    final ExtensionType ext = (ExtensionType) request.getExtension();
                    final ParametersType geoExt = ext.getForClass(ParametersType.class);
                    if (geoExt != null) {
                        if (geoExt.getCompression() != null) {
                            if (geoExt.getCompression() == CompressionType.LZW ||
                                geoExt.getCompression() == CompressionType.PACK_BITS ||
                                geoExt.getCompression() == CompressionType.NONE) {
                                response.compression = geoExt.getCompression().value();
                            } else {
                                throw new CstlServiceException("Server does not support the requested compression.", ExceptionCode.COMPRESSION_NOT_SUPPORTED, geoExt.getCompression().value());
                            }
                        }
                        if (geoExt.getInterleave() != null) {
                            throw new CstlServiceException("Server does not support interleaving.", ExceptionCode.INTERLEAVING_NOT_SUPPORTED, geoExt.getInterleave().value());
                        }
                        if (geoExt.getPredictor() != null) {
                            throw new CstlServiceException("Server does not support predictor.", ExceptionCode.PREDICTOR_NOT_SUPPORTED, geoExt.getPredictor().value());
                        }
                        if (geoExt.isTiling()) {
                            if ("PackBits".equals(response.compression)) {
                                throw new CstlServiceException("Server does not support Tiling for packbit compression.", ExceptionCode.TILING_NOT_SUPPORTED, "tiling");
                            }
                            if (geoExt.getTileheight() != null && geoExt.getTilewidth() != null &&
                                geoExt.getTileheight() > 0 && geoExt.getTilewidth() > 0 &&
                                (geoExt.getTileheight() % 16 == 0) &&
                                (geoExt.getTilewidth() % 16 == 0)) {
                                response.tiling = true;
                                response.tileHeight = geoExt.getTileheight();
                                response.tileWidth = geoExt.getTilewidth();
                            } else {
                                throw new CstlServiceException("Server does not support predictor.", ExceptionCode.TILING_INVALID, geoExt.getPredictor().value());
                            }
                        }
                    }
                }
                if (isMultiPart) {
                    final File img = GridCoverageWriter.writeInFile(response);
                    final String xml = buildXmlPart(describeCoverage200(layerRef.getName().tip().toString(), layerRef), format);
                    final MultiPart multiPart = new MultiPart();
                    multiPart.bodyPart("application/xml", xml)
                            .bodyPart(format, img);
                    return multiPart;
                } else {
                    return response;
                }
            } catch (JAXBException | IOException | ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

        } else {
            // We are in the case of an image format requested.
            //NOTE: ADRIAN HACKED HERE

            // SCENE
            final SceneDef sdef = new SceneDef();

            final List<DataReference> styles = configLayer.getStyles();
            final MutableStyle style;
            if (!styles.isEmpty()) {
                final DataReference styleName = styles.get(0);
                final MutableStyle incomingStyle = getStyle(styleName);
                style = WCSUtils.filterStyle(incomingStyle, request.getRangeSubset());
            } else {
                style = null;
            }
            try {
                final MapContext context = PortrayalUtil.createContext(layerRef, style, new HashMap<>());
                sdef.setContext(context);
            } catch (ConstellationStoreException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }

            // VIEW
            final ViewDef vdef = new ViewDef(readEnv);

            // CANVAS
            Color background = null;
            if (MimeType.IMAGE_JPEG.equalsIgnoreCase(format)) {
                background = Color.WHITE;
            }
            final CanvasDef cdef = new CanvasDef(new Dimension(500, 500), background);

            // IMAGE
            final BufferedImage img;
            try {
                img = CstlPortrayalService.getInstance().portray(sdef, vdef, cdef);
            } catch (PortrayalException ex) {
                /*
                 * TODO: the binding xml for WCS and GML do not support the exceptions format,
                 * consequently we can't extract the exception output mime-type information from
                 * the request. Maybe a more recent version of the GML 3 spec has fixed this bug ...
                 */
                //if (exceptions != null && exceptions.equalsIgnoreCase(EXCEPTIONS_INIMAGE)) {
                //    img = Cstl.Portrayal.writeInImage(ex, abstractRequest.getSize());
                //} else {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
                //}
            }

            return img;
        }
    }

    private static int dimensionIndex(final String dimension, final CoordinateReferenceSystem crs) {
        for (int i = 0; i < crs.getCoordinateSystem().getDimension(); i++) {
            if (dimension.equals(crs.getCoordinateSystem().getAxis(i).getAbbreviation())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Convert string value to a double matching given axis unit.
     */
    private static double toAxisValue(String value, CoordinateReferenceSystem crs, int index, double fallback) throws CstlServiceException {
        if(value==null || value.isEmpty() || "*".equals(value)) return fallback;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            try {
                //check if it's a date
                final CoordinateReferenceSystem subcrs = CRS.getComponentAt(crs, index, index);
                if (subcrs instanceof TemporalCRS) {
                    final Calendar cal = TemporalUtilities.parseDateCal(value);
                    return DefaultTemporalCRS.castOrCopy((TemporalCRS) subcrs).toValue(cal.getTime());
                }
            } catch (Throwable ex1) {
                LOGGER.log(Level.FINE, ex1.getMessage(), ex1);
            }
            throw new CstlServiceException("Unsupported subset value " + value, INVALID_SUBSETTING);
        }
    }

    /**
     * Overriden from AbstractWorker because in version 1.0.0 the behaviour is different when the request updateSequence
     * is equal to the current.
     *
     * @param updateSequence
     * @param version
     * @return
     * @throws CstlServiceException
     */
    private boolean returnUpdateSequenceDocument(final String updateSequence, final String version) throws CstlServiceException {
        if (updateSequence == null) {
            return false;
        }
        if ("1.0.0".equals(version)) {
            try {
                final long sequenceNumber = Long.parseLong(updateSequence);
                final long currentUpdateSequence = Long.parseLong(getCurrentUpdateSequence());
                if (sequenceNumber == currentUpdateSequence) {
                    throw new CstlServiceException("The update sequence parameter is equal to the current", CURRENT_UPDATE_SEQUENCE, "updateSequence");
                } else if (sequenceNumber > currentUpdateSequence) {
                    throw new CstlServiceException("The update sequence parameter is invalid (higher value than the current)", INVALID_UPDATE_SEQUENCE, "updateSequence");
                }
                return false;
            } catch (NumberFormatException ex) {
                throw new CstlServiceException("The update sequence must be an integer", ex, INVALID_PARAMETER_VALUE, "updateSequence");
            }
        } else {
            return returnUpdateSequenceDocument(updateSequence);
        }
    }

    private String buildXmlPart(org.geotoolkit.wcs.xml.v200.CoverageDescriptionType describeCoverage200, String mime) throws JAXBException {
        final org.geotoolkit.gml.xml.v321.RangeSetType rangeSet = new org.geotoolkit.gml.xml.v321.RangeSetType();
        final FileType ft = new FileType();
        ft.setMimeType(mime);
        final String ext = WCSUtils.getExtension(mime);
        ft.setRangeParameters(new AssociationRoleType("cid:" + describeCoverage200.getCoverageId() + ext,
                "http://www.opengis.net/spec/GMLCOV_geotiff-coverages/1.0/conf/geotiff-coverage",
                "fileReference"));
        ft.setFileReference("cid:" + describeCoverage200.getCoverageId() + ext);
        rangeSet.setFile(ft);
        final AbstractDiscreteCoverageType cov = new AbstractDiscreteCoverageType(describeCoverage200, rangeSet);
        final ObjectFactory factory = new ObjectFactory();
        Object obj = factory.createGridCoverage(cov);

        Marshaller m = WCSMarshallerPool.getInstance().acquireMarshaller();
        final StringWriter sw = new StringWriter();
        m.marshal(obj, sw);
        WCSMarshallerPool.getInstance().recycle(m);
        return sw.toString();
    }
}
