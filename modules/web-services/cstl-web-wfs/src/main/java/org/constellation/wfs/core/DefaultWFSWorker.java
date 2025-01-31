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

package org.constellation.wfs.core;

import org.apache.sis.internal.xml.XmlUtilities;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.referencing.CRS;
import org.apache.sis.referencing.IdentifiedObjects;
import org.apache.sis.storage.DataStoreException;
import org.apache.sis.util.logging.Logging;
import org.apache.sis.util.Utilities;
import org.apache.sis.xml.MarshallerPool;
import org.constellation.api.ServiceDef;
import org.constellation.api.ServiceDef.Version;
import org.constellation.dto.service.config.wxs.FormatURL;
import org.constellation.dto.service.config.wxs.Layer;
import org.constellation.dto.contact.Details;
import org.constellation.provider.Data;
import org.constellation.provider.FeatureData;
import org.constellation.security.SecurityManagerHolder;
import org.constellation.util.QNameComparator;
import org.constellation.util.QnameLocalComparator;
import org.constellation.wfs.ws.rs.FeatureSetWrapper;
import org.constellation.wfs.ws.rs.ValueCollectionWrapper;
import org.constellation.ws.CstlServiceException;
import org.constellation.ws.LayerWorker;
import org.constellation.ws.UnauthorizedException;
import org.geotoolkit.data.FeatureCollection;
import org.geotoolkit.data.FeatureStore;
import org.geotoolkit.data.FeatureStoreUtilities;
import org.geotoolkit.data.query.QueryBuilder;
import org.geotoolkit.factory.Hints;
import org.geotoolkit.factory.HintsPending;
import org.geotoolkit.feature.xml.Utils;
import org.geotoolkit.feature.xml.jaxb.JAXBFeatureTypeWriter;
import org.geotoolkit.feature.xml.jaxp.JAXPStreamFeatureReader;
import org.geotoolkit.filter.binding.Binding;
import org.geotoolkit.filter.binding.Bindings;
import org.geotoolkit.filter.visitor.FillCrsVisitor;
import org.geotoolkit.filter.visitor.IsValidSpatialFilterVisitor;
import org.geotoolkit.filter.visitor.ListingPropertyVisitor;
import org.geotoolkit.geometry.jts.JTS;
import org.geotoolkit.gml.GeometrytoJTS;
import org.geotoolkit.gml.xml.AbstractGML;
import org.geotoolkit.gml.xml.DirectPosition;
import org.geotoolkit.gml.xml.v311.AbstractGeometryType;
import org.geotoolkit.gml.xml.v311.FeaturePropertyType;
import org.geotoolkit.ogc.xml.XMLFilter;
import org.geotoolkit.ogc.xml.XMLLiteral;
import org.geotoolkit.ogc.xml.v200.BBOXType;
import org.geotoolkit.ows.xml.AbstractCapabilitiesBase;
import org.geotoolkit.ows.xml.AbstractDomain;
import org.geotoolkit.ows.xml.AbstractOperationsMetadata;
import org.geotoolkit.ows.xml.AbstractServiceIdentification;
import org.geotoolkit.ows.xml.AbstractServiceProvider;
import org.geotoolkit.ows.xml.AcceptVersions;
import org.geotoolkit.ows.xml.RequestBase;
import org.geotoolkit.ows.xml.Sections;
import org.geotoolkit.sld.xml.StyleXmlIO;
import org.geotoolkit.wfs.xml.CreateStoredQuery;
import org.geotoolkit.wfs.xml.CreateStoredQueryResponse;
import org.geotoolkit.wfs.xml.DeleteElement;
import org.geotoolkit.wfs.xml.DescribeFeatureType;
import org.geotoolkit.wfs.xml.DescribeStoredQueries;
import org.geotoolkit.wfs.xml.DescribeStoredQueriesResponse;
import org.geotoolkit.wfs.xml.DropStoredQuery;
import org.geotoolkit.wfs.xml.DropStoredQueryResponse;
import org.geotoolkit.wfs.xml.FeatureRequest;
import org.geotoolkit.wfs.xml.FeatureTypeList;
import org.geotoolkit.wfs.xml.GetCapabilities;
import org.geotoolkit.wfs.xml.GetFeature;
import org.geotoolkit.wfs.xml.GetGmlObject;
import org.geotoolkit.wfs.xml.GetPropertyValue;
import org.geotoolkit.wfs.xml.IdentifierGenerationOptionType;
import org.geotoolkit.wfs.xml.InsertElement;
import org.geotoolkit.wfs.xml.ListStoredQueries;
import org.geotoolkit.wfs.xml.ListStoredQueriesResponse;
import org.geotoolkit.wfs.xml.LockFeature;
import org.geotoolkit.wfs.xml.LockFeatureResponse;
import org.geotoolkit.wfs.xml.Parameter;
import org.geotoolkit.wfs.xml.ParameterExpression;
import org.geotoolkit.wfs.xml.Property;
import org.geotoolkit.wfs.xml.Query;
import org.geotoolkit.wfs.xml.QueryExpressionText;
import org.geotoolkit.wfs.xml.ReplaceElement;
import org.geotoolkit.wfs.xml.ResultTypeType;
import org.geotoolkit.wfs.xml.StoredQueries;
import org.geotoolkit.wfs.xml.StoredQuery;
import org.geotoolkit.wfs.xml.StoredQueryDescription;
import org.geotoolkit.wfs.xml.Transaction;
import org.geotoolkit.wfs.xml.TransactionResponse;
import org.geotoolkit.wfs.xml.UpdateElement;
import org.geotoolkit.wfs.xml.WFSCapabilities;
import org.geotoolkit.wfs.xml.WFSMarshallerPool;
import org.geotoolkit.wfs.xml.WFSXmlFactory;
import org.geotoolkit.wfs.xml.v110.FeatureCollectionType;
import org.geotoolkit.wfs.xml.v200.ObjectFactory;
import org.geotoolkit.wfs.xml.v200.PropertyName;
import org.geotoolkit.wfs.xml.v200.QueryExpressionTextType;
import org.geotoolkit.wfs.xml.v200.QueryType;
import org.geotoolkit.wfs.xml.v200.StoredQueryDescriptionType;
import org.geotoolkit.xsd.xml.v2001.Schema;
import org.opengis.filter.BinaryComparisonOperator;
import org.opengis.filter.BinaryLogicOperator;
import org.opengis.filter.Filter;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.filter.identity.FeatureId;
import org.opengis.filter.sort.SortBy;
import org.opengis.filter.spatial.BinarySpatialOperator;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.CodeList;
import org.opengis.util.FactoryException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.inject.Named;
import javax.xml.bind.JAXBElement;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.geotoolkit.feature.FeatureExt;
import org.geotoolkit.feature.FeatureTypeExt;
import org.apache.sis.internal.feature.AttributeConvention;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.storage.DataStore;
import org.apache.sis.util.iso.Names;
import org.apache.sis.xml.Namespaces;
import org.constellation.business.IDataBusiness;
import org.constellation.dto.DataBrief;
import org.constellation.dto.NameInProvider;
import org.constellation.exception.ConstellationException;
import org.constellation.provider.DataProvider;
import org.constellation.provider.DataProviders;
import org.constellation.wfs.NameOverride;
import org.constellation.wfs.core.WFSConstants.GetXSD;
import static org.constellation.wfs.core.WFSConstants.IDENTIFIER_FILTER;
import static org.constellation.wfs.core.WFSConstants.IDENTIFIER_PARAM;
import static org.constellation.wfs.core.WFSConstants.OPERATIONS_METADATA_V110;
import static org.constellation.wfs.core.WFSConstants.OPERATIONS_METADATA_V200;
import static org.constellation.wfs.core.WFSConstants.TYPE_PARAM;
import static org.constellation.wfs.core.WFSConstants.UNKNOW_TYPENAME;
import org.apache.sis.storage.FeatureSet;
import org.apache.sis.storage.WritableFeatureSet;
import org.constellation.exception.ConstellationStoreException;
import org.geotoolkit.data.FeatureStoreRuntimeException;
import org.geotoolkit.data.FeatureStreams;
import org.geotoolkit.data.FeatureWriter;
import org.geotoolkit.data.memory.ExtendedFeatureStore;
import org.geotoolkit.data.session.Session;
import org.geotoolkit.util.NamesExt;
import org.geotoolkit.feature.xml.XSDFeatureStore;
import static org.geotoolkit.ows.xml.OWSExceptionCode.DUPLICATE_STORED_QUERY_ID_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.MISSING_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.NO_APPLICABLE_CODE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.OPERATION_NOT_SUPPORTED;
import static org.geotoolkit.ows.xml.OWSExceptionCode.VERSION_NEGOTIATION_FAILED;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildBBOX;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildCreateStoredQueryResponse;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildDescribeStoredQueriesResponse;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildDropStoredQueryResponse;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildFeatureCollection;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildFeatureType;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildFeatureTypeList;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildListStoredQueriesResponse;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildSections;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildTransactionResponse;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildValueCollection;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildWFSCapabilities;
import org.geotoolkit.xsd.xml.v2001.FormChoice;
import org.geotoolkit.xsd.xml.v2001.Import;
import org.opengis.feature.AttributeType;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureAssociationRole;
import org.opengis.feature.FeatureType;
import org.opengis.feature.PropertyNotFoundException;
import org.opengis.feature.PropertyType;
import org.opengis.util.GenericName;
import org.springframework.beans.factory.annotation.Autowired;


/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@Named("WFSWorker")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class DefaultWFSWorker extends LayerWorker implements WFSWorker {

    /**
     * Base known CRS.
     */
    private final static List<String> DEFAULT_CRS;
    static {
        final String[] codes = {"EPSG:4326", "EPSG:3395", "CRS:84"};
        final List<String> tmpUrns = new ArrayList<>();
        for (final String code : codes) {
            try {
                final CoordinateReferenceSystem crs = CRS.forCode(code);
                String urn = IdentifiedObjects.lookupURN(crs, null);
                if (urn != null) {
                    tmpUrns.add(urn);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Cannot initialize default coordinate reference system", e);
            }
        }
        DEFAULT_CRS = Collections.unmodifiableList(tmpUrns);
    }

    private List<StoredQueryDescription> storedQueries = new ArrayList<>();

    private final boolean isTransactionnal;

    @Autowired
    private IDataBusiness dataBuz;

    public DefaultWFSWorker(final String id) {
        super(id, ServiceDef.Specification.WFS);
        if (isStarted) {
            LOGGER.log(Level.INFO, "WFS worker {0} running", id);
        }

        final String isTransactionnalProp = getProperty("transactional");
        if (isTransactionnalProp != null) {
            isTransactionnal = Boolean.parseBoolean(isTransactionnalProp);
        } else {
            boolean t = false;
            try {
                final Details details = serviceBusiness.getInstanceDetails("wfs", id, null);
                t = details.isTransactional();
            } catch (ConstellationException ex) {
                LOGGER.log(Level.WARNING, null, ex);
            }
            isTransactionnal = t;
        }

        // loading stored queries
       loadStoredQueries();
    }

    private void loadStoredQueries() {
        try {
            final Object obj = serviceBusiness.getExtraConfiguration("WFS", getId(), "StoredQueries.xml", getMarshallerPool());
            if (obj instanceof StoredQueries) {
                StoredQueries candidate = (StoredQueries) obj;
                this.storedQueries = candidate.getStoredQuery();
            } else {
                LOGGER.log(Level.WARNING, "The storedQueries File does not contains proper object");
            }
        } catch (ConstellationException ex) {
            LOGGER.log(Level.WARNING, "ConfigurationException while unmarshalling the stored queries File", ex);
        }

        // we verify if the identifier query is loaded (if not we load it)
       boolean foundID = false;
       for (StoredQueryDescription squery : storedQueries) {
           if ("urn:ogc:def:query:OGC-WFS::GetFeatureById".equals(squery.getId())) {
               foundID = true;
               break;
           }
       }
       if (!foundID) {
           final List<QName> typeNames = getConfigurationTypeNames(null);
           Collections.sort(typeNames, new QNameComparator());
           final QueryType query = new QueryType(IDENTIFIER_FILTER, typeNames, "2.0.0");
           final QueryExpressionTextType queryEx = new QueryExpressionTextType("urn:ogc:def:queryLanguage:OGC-WFS::WFS_QueryExpression", null, typeNames);
           final ObjectFactory factory = new ObjectFactory();
           queryEx.getContent().add(factory.createQuery(query));
           final StoredQueryDescriptionType idQ = new StoredQueryDescriptionType("urn:ogc:def:query:OGC-WFS::GetFeatureById", "Identifier query" , "filter on feature identifier", IDENTIFIER_PARAM, queryEx);
           storedQueries.add(idQ);
       }

        // we verify if the type query is loaded (if not we load it)
       boolean foundT = false;
       for (StoredQueryDescription squery : storedQueries) {
           if ("urn:ogc:def:storedQuery:OGC-WFS::GetFeatureByType".equals(squery.getId())) {
               foundT = true;
               break;
           }
       }
       if (!foundT) {
           final List<QName> returnTypeNames = new ArrayList<>();
           returnTypeNames.add(new QName("http://www.opengis.net/gml/3.2", "AbstractFeatureType"));
           final List<QName> typeNames = new ArrayList<>();
           typeNames.add(new QName("$typeName"));
           final QueryType query = new QueryType(null, typeNames, "2.0.0");
           final QueryExpressionTextType queryEx = new QueryExpressionTextType("urn:ogc:def:queryLanguage:OGC-WFS::WFS_QueryExpression", null, returnTypeNames);
           final ObjectFactory factory = new ObjectFactory();
           queryEx.getContent().add(factory.createQuery(query));
           final StoredQueryDescriptionType idQ = new StoredQueryDescriptionType("urn:ogc:def:storedQuery:OGC-WFS::GetFeatureByType", "By type query" , "filter on feature type", TYPE_PARAM, queryEx);
           storedQueries.add(idQ);
       }

    }

    private void storedQueries() {
        serviceBusiness.setExtraConfiguration("WFS", getId(), "StoredQueries.xml", new StoredQueries(storedQueries), getMarshallerPool());
    }

    @Override
    protected MarshallerPool getMarshallerPool() {
        return WFSMarshallerPool.getInstance();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public WFSCapabilities getCapabilities(final GetCapabilities request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "GetCapabilities request proccesing");
        final long start = System.currentTimeMillis();

        final String userLogin  = getUserLogin();
        //choose the best version from acceptVersion
        final AcceptVersions versions = request.getAcceptVersions();
        if (versions != null) {
            Version max = null;
            for (String v : versions.getVersion()) {
                final Version vv = new Version(v);
                if (isSupportedVersion(v)) {
                    if (max == null || vv.compareTo(max) > 1) {
                        max = vv;
                    }
                }
            }
            if (max != null) {
                request.setVersion(max.toString());
            }
        }

        // we verify the base attribute
        verifyBaseRequest(request, false, true);
        final String currentVersion = request.getVersion().toString();

        //set the current updateSequence parameter
        final boolean returnUS = returnUpdateSequenceDocument(request.getUpdateSequence());
        if (returnUS) {
            return buildWFSCapabilities(currentVersion, getCurrentUpdateSequence());
        }

        Sections sections = request.getSections();
        if (sections == null) {
            sections = buildSections(currentVersion, Arrays.asList("All"));
        }

        final AbstractCapabilitiesBase cachedCapabilities = (WFSCapabilities) getCapabilitiesFromCache(currentVersion, null);
        if (cachedCapabilities != null) {
            return (WFSCapabilities) cachedCapabilities.applySections(sections);
        }

        final Details skeleton = getStaticCapabilitiesObject("WFS", null);
        final WFSCapabilities inCapabilities = WFSConstants.createCapabilities(currentVersion, skeleton);

        final FeatureTypeList ftl = buildFeatureTypeList(currentVersion);
        /*
         *  layer providers
         */
        final List<Layer> layers    = getConfigurationLayers(userLogin);
        final QnameLocalComparator qNameComparator = new QnameLocalComparator();
        Collections.sort(layers, (l1, l2) -> qNameComparator.compare(l1.getName(), l2.getName()));
        for (final Layer configLayer : layers) {
            final QName layerName = configLayer.getName();
            final Data layer = getLayerReference(userLogin, layerName);

            if (layer instanceof FeatureData) {
                final FeatureData fld = (FeatureData) layer;
                final FeatureType type;
                try {
                    type  = fld.getType();
                } catch (ConstellationStoreException ex) {
                    LOGGER.log(Level.WARNING, "Error while getting featureType for:{0}\ncause:{1}", new Object[]{fld.getName(), ex.getMessage()});
                    continue;
                }
                final org.geotoolkit.wfs.xml.FeatureType ftt;
                try {

                    final String defaultCRS = getCRSCode(type);
                    final String title;
                    if (configLayer.getTitle() != null) {
                        title = configLayer.getTitle();
                    } else if (configLayer.getAlias() != null) {
                        title = configLayer.getAlias();
                    } else {
                        title = layerName.getLocalPart();
                    }

                    List<String> others = DEFAULT_CRS;
                    if (DEFAULT_CRS.contains(defaultCRS)) {
                        others = new ArrayList<>(DEFAULT_CRS);
                        others.remove(defaultCRS);
                    }
                    ftt = buildFeatureType(
                            currentVersion,
                            layerName,
                            title,
                            defaultCRS,
                            others,
                            toBBox(fld, currentVersion));

                    /*
                     * we apply the layer customization
                     */
                    ftt.setAbstract(configLayer.getAbstrac());
                    if (!configLayer.getKeywords().isEmpty()) {
                        ftt.addKeywords(configLayer.getKeywords());
                    }
                    List<FormatURL> metadataURLs = configLayer.getMetadataURL();
                    if (metadataURLs != null) {
                        for (FormatURL metadataURL : metadataURLs) {
                            ftt.addMetadataURL(metadataURL.getOnlineResource().getValue(),
                                               metadataURL.getType(),
                                               metadataURL.getFormat());
                        }
                    }
                    if (!configLayer.getCrs().isEmpty()) {
                        ftt.setOtherCRS(configLayer.getCrs());
                    }

                    // we add the feature type description to the list
                    ftl.addFeatureType(ftt);
                } catch (FactoryException ex) {
                    Logging.unexpectedException(LOGGER,DefaultWFSWorker.class,"getCapabilities",ex);
                }

            } else {
                LOGGER.log(Level.WARNING, "The layer:{0} is not a feature layer", layerName);
            }
        }

        final AbstractOperationsMetadata om;
        if (currentVersion.equals("2.0.0")) {
            om = OPERATIONS_METADATA_V200.clone();
        } else {
            om = OPERATIONS_METADATA_V110.clone();
        }
        om.updateURL(getServiceUrl());

        if (!isTransactionnal) {
            om.removeOperation("Transaction");
            final AbstractDomain cst = om.getConstraint("ImplementsTransactionalWFS");
            if (cst != null) {
                cst.setDefaultValue("FALSE");
            }
        }

        final AbstractServiceProvider sp       = inCapabilities.getServiceProvider();
        final AbstractServiceIdentification si = inCapabilities.getServiceIdentification();
        final FilterCapabilities fc;
        if (currentVersion.equals("2.0.0")) {
            fc = WFSConstants.FILTER_CAPABILITIES_V200;
        } else {
            fc = WFSConstants.FILTER_CAPABILITIES_V110;
        }
        final WFSCapabilities result = buildWFSCapabilities(currentVersion, si, sp, om, ftl, fc);
        putCapabilitiesInCache(currentVersion, null, result);
        LOGGER.log(Level.INFO, "GetCapabilities treated in {0}ms", (System.currentTimeMillis() - start));
        return (WFSCapabilities) result.applySections(sections);
    }

    private String getCRSCode(FeatureType type) throws FactoryException {
        final CoordinateReferenceSystem crs = getCRS(type);

        String defaultCRS = IdentifiedObjects.lookupURN(crs, null);
        if (defaultCRS == null) {
            defaultCRS = IdentifiedObjects.toURN(crs.getClass(), IdentifiedObjects.getIdentifier(crs, null));
        }

        if (defaultCRS == null) {
            /* If we reach here, we're in a very frightening situation : SIS did
             * not even succeed to give an URN for the most basic CRS. It means
             * that a big problem lies between SIS and EPSG database access.
             * Worst, in this case, SIS should have thrown an error already.
             */
            LOGGER.log(Level.WARNING, "No URN can be created for given CRS : "+crs);
            return IdentifiedObjects.lookupURN(CommonCRS.defaultGeographic(), null);
        }

        return defaultCRS;
    }


    private CoordinateReferenceSystem getCRS(FeatureType type) throws FactoryException {
        PropertyType geomAtt = null;
        try {
            geomAtt = FeatureExt.getDefaultGeometry(type);
        } catch (PropertyNotFoundException|IllegalStateException e) {
            LOGGER.log(Level.FINE, "Cannot determine a primary geometry in given feature type.", e);
        }

        if (geomAtt != null) {
            final CoordinateReferenceSystem crs = FeatureExt.getCRS(geomAtt);
            if (crs!=null) return crs;
        }

        // We fallback on CRS:84
        return CommonCRS.defaultGeographic();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Object describeFeatureType(final DescribeFeatureType request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "DecribeFeatureType request proccesing");
        final long start = System.currentTimeMillis();

        // we verify the base attribute
        verifyBaseRequest(request, false, false);
        final String currentVersion = request.getVersion().toString();
        final String userLogin      = getUserLogin();

        final String gmlVersion;
        if ("2.0.0".equals(currentVersion)) {
            gmlVersion = "3.2.1";
        } else {
            gmlVersion = "3.1.1";
        }
        final JAXBFeatureTypeWriter writer  = new JAXBFeatureTypeWriter(gmlVersion);
        final List<QName> names             = request.getTypeName();
        final List<FeatureType> types       = new ArrayList<>();
        final Map<String, String> locations = new HashMap<>();

        //XSDFeatureStore may provide the xsd themselves
        final Map<FeatureType,Schema> declaredSchema = new HashMap<>();
        if (names.isEmpty()) {
            //search all types
            for (final NameInProvider name : getConfigurationLayerNames(userLogin)) {
                final Data layer = getLayerReference(userLogin, name.name);
                if (!(layer instanceof FeatureData)) {continue;}

                try {
                    FeatureData fLayer = (FeatureData)layer;
                    DataStore store    = fLayer.getStore();
                    FeatureType ftType = fLayer.getType();

                    if (store instanceof ExtendedFeatureStore) {
                        store = (DataStore) ((ExtendedFeatureStore) store).getWrapped();
                    }
                    if (store instanceof XSDFeatureStore) {
                        final Map params = (Map) ((XSDFeatureStore) store).getSchema(layer.getName());
                        if (params.size() == 1 && params.get(params.keySet().iterator().next()) instanceof Schema) {
                            final FeatureType ft = NameOverride.wrap(ftType, name.name);
                            declaredSchema.put(ft, (Schema) params.get(params.keySet().iterator().next()));
                            types.add(ft);
                        } else {
                            locations.putAll(params);
                        }
                    } else {
                        types.add(NameOverride.wrap(ftType, name.name));
                    }
                } catch (ConstellationStoreException | DataStoreException ex) {
                    LOGGER.log(Level.WARNING, "error while getting featureType for:{0}", layer.getName());
                }
            }
        } else {

            //search only the given list
            for (final QName name : names) {
                if (name == null) {continue;}

                final Layer confLayer = getConfigurationLayer(name, userLogin);
                if (confLayer == null) {
                    throw new CstlServiceException(UNKNOW_TYPENAME + name, INVALID_PARAMETER_VALUE, "typenames");
                }
                final GenericName layerName = NamesExt.create(confLayer.getName());
                final Data layer = getLayerReference(userLogin, layerName);

                if(!(layer instanceof FeatureData)) {
                    throw new CstlServiceException(UNKNOW_TYPENAME + name, INVALID_PARAMETER_VALUE, "typenames");
                }

                try {
                    FeatureData fLayer = (FeatureData)layer;
                    DataStore store    = fLayer.getStore();
                    FeatureType ftType = fLayer.getType();

                    if(store instanceof ExtendedFeatureStore) store = (DataStore)((ExtendedFeatureStore)store).getWrapped();
                    if (store instanceof XSDFeatureStore) {
                        final Map params = (Map)((XSDFeatureStore)store).getSchema(layerName);
                        if(params.size()==1 && params.get(params.keySet().iterator().next()) instanceof Schema){
                            declaredSchema.put(NameOverride.wrap(ftType, layerName), (Schema) params.get(params.keySet().iterator().next()));
                            types.add(ftType);
                        }else{
                            locations.putAll(params);
                        }
                    } else {
                        types.add(NameOverride.wrap(ftType, layerName));
                    }
                } catch (ConstellationStoreException | DataStoreException ex) {
                    LOGGER.log(Level.WARNING, "error while getting featureType for:{0}", layer.getName());
                }
            }
        }

        final int size = types.size();

        if (request.getOutputFormat().equals("application/schema+json")) {
            return new org.constellation.wfs.ws.rs.FeatureTypeList(types);
        }

        /*
         * Most simple case. we have only one feature type
         */
        final Schema schema;
        if (size == 1) {
            final FeatureType type = types.get(0);
            final String tn        = type.getName().tip().toString();
            final String tnmsp     = NamesExt.getNamespace(type.getName());
            if(declaredSchema.containsKey(type)){
                schema = declaredSchema.get(type);
            }else{
                schema = writer.getSchemaFromFeatureType(type);
                final Set<String> nmsps = Utils.listAllNamespaces(type);
                nmsps.remove(tnmsp);
                nmsps.remove(Namespaces.GML);
                nmsps.remove("http://www.opengis.net/gml");
                for (String nmsp : nmsps) {
                    schema.addImport(new Import(nmsp, getServiceUrl() + "request=xsd&version=" + currentVersion + "&targetNamespace=" + nmsp + "&typename=ns:" + tn + "&namespace=xmlns(ns=" + tnmsp + ")"));
                }
            }
        /*
         * Second case. we have many feature type in the same namespace
         */
        } else if (AllInSameNamespace(types)) {
            schema = writer.getSchemaFromFeatureType(types);
            final Set<String> nmsps = new HashSet<>();
            for (FeatureType type : types) {
                nmsps.addAll(Utils.listAllNamespaces(type));
            }
            nmsps.remove(NamesExt.getNamespace(types.get(0).getName()));
            nmsps.remove(Namespaces.GML);
            nmsps.remove("http://www.opengis.net/gml");
            for (String nmsp : nmsps) {
                schema.addImport(new Import(nmsp, getServiceUrl() + "request=xsd&version=" + currentVersion + "&targetNamespace=" + nmsp));
            }

        /*
         * third case. we have many feature type in the many namespace.
         * send an xsd pointing on various describeFeatureRequest
         */
        } else {
            Map<String, List<FeatureType>> typeMap = splitByNamespace(types);
            if (typeMap.containsKey(null)) {
                final List<FeatureType> fts = typeMap.get(null);
                schema = writer.getSchemaFromFeatureType(fts);
                typeMap.remove(null);
            } else {
                schema = new Schema(FormChoice.QUALIFIED, null);
            }
            for (String nmsp : typeMap.keySet()) {
                final List<FeatureType> fts = typeMap.get(nmsp);
                StringBuilder sb = new StringBuilder();
                for (FeatureType ft : fts) {
                    sb.append("ns:").append(ft.getName().tip().toString()).append(',');
                }
                sb.delete(sb.length() -1, sb.length());
                final String schemaLocation = getServiceUrl() + "request=DescribeFeatureType&service=WFS&version=" + currentVersion + "&typename=" + sb.toString() + "&namespace=xmlns(ns=" + nmsp + ")";
                schema.addImport(new Import(nmsp, schemaLocation));
            }
        }

        for (Entry<String, String> location : locations.entrySet()) {
            schema.addImport(new Import(location.getKey(), location.getValue()));
        }

        LOGGER.log(Level.INFO, "DescribeFeatureType treated in {0}ms", (System.currentTimeMillis() - start));
        return schema;
    }

    private boolean AllInSameNamespace(final List<FeatureType> types) {
        if (types == null || types.isEmpty()) {
            return false;
        }

        final String firstNmsp = NamesExt.getNamespace(types.get(0).getName());
        for (int i = 1; i < types.size(); i++) {
            FeatureType type = types.get(i);
            final String currentNmsp = NamesExt.getNamespace(type.getName());
            // Objects.equals checks null pointers.
            if (!Objects.equals(firstNmsp, currentNmsp)) {
                return false;
            }
        }

        return true;
    }

    private Map<String, List<FeatureType>> splitByNamespace(final List<FeatureType> types) {
        Map<String, List<FeatureType>> results = new HashMap<>();
        for (FeatureType type : types) {
            final String nmsp = NamesExt.getNamespace(type.getName());
            if (results.containsKey(nmsp)) {
                results.get(nmsp).add(type);
            } else {
                final List<FeatureType> ft = new ArrayList<>();
                ft.add(type);
                results.put(nmsp, ft);
            }
        }
        return results;
    }

    @Override
    public Schema getXsd(final GetXSD request) throws CstlServiceException {
        final String userLogin = getUserLogin();

        final String gmlVersion;
        if ("2.0.0".equals(request.version)) {
            gmlVersion = "3.2.1";
        } else {
            gmlVersion = "3.1.1";
        }
        final JAXBFeatureTypeWriter writer  = new JAXBFeatureTypeWriter(gmlVersion);
        final List<FeatureType> types = new ArrayList<>();
        final String suffix;
        if (request.featureType == null) {
            //search all types
            for (final NameInProvider name : getConfigurationLayerNames(userLogin)) {
                final Data layer = getLayerReference(userLogin, name.name);
                if (!(layer instanceof FeatureData)) {continue;}

                try {
                    types.add(NameOverride.wrap(((FeatureData)layer).getType(), name.name));
                } catch (ConstellationStoreException ex) {
                    LOGGER.log(Level.WARNING, "error while getting featureType for:{0}", layer.getName());
                }
            }
            suffix = "";
        } else {
            final GenericName n = Utils.getNameFromQname(request.featureType);
            if (getFullLayerName(userLogin, n) == null) {
                throw new CstlServiceException(UNKNOW_TYPENAME + request.featureType, INVALID_PARAMETER_VALUE, "typenames");
            }
            final Data layer = getLayerReference(userLogin, n);

            if(!(layer instanceof FeatureData)) {
                throw new CstlServiceException(UNKNOW_TYPENAME + request.featureType, INVALID_PARAMETER_VALUE, "typenames");
            }

            try {
                types.add(NameOverride.wrap(((FeatureData)layer).getType(), n));
            } catch (ConstellationStoreException ex) {
                LOGGER.log(Level.WARNING, "error while getting featureType for:"+ layer.getName(), ex);
            }
            suffix = "&typename=ns:" + request.featureType.getLocalPart() + "&namespace=xmlns(ns=" + request.featureType.getNamespaceURI() + ")";
        }
        Schema schema = writer.getExternalSchemaFromFeatureType(request.namespace, types);
        final Set<String> nmsps = new HashSet<>();
        for (FeatureType type : types) {
            nmsps.addAll(Utils.listAllSubNamespaces(type, request.namespace));
        }
        nmsps.remove(request.namespace);
        nmsps.remove(Namespaces.GML);
        nmsps.remove("http://www.opengis.net/gml");

        for (String nmsp : nmsps) {
            schema.addImport(new Import(nmsp, getServiceUrl() + "request=xsd&version=" + request.version + "&targetNamespace=" + nmsp + suffix));
        }
        return schema;

    }

    private LinkedHashMap<String,? extends Query> extractStoredQueries(final FeatureRequest request) throws CstlServiceException {
        final List<? extends Query> queries = request.getQuery();
        final LinkedHashMap<String,Query> result = new LinkedHashMap<>();
        for(int i=0,n=queries.size();i<n;i++){
            result.put(""+i, queries.get(i));
        }

        for (StoredQuery storedQuery : request.getStoredQuery()) {
            StoredQueryDescription description = null;
            final List<? extends Parameter> parameters = storedQuery.getParameter();
            for (StoredQueryDescription desc : storedQueries) {
                if (desc.getId().equals(storedQuery.getId())) {
                    description = desc;
                    break;
                }
            }
            if (description == null) {
                throw new CstlServiceException("Unknow stored query: " + storedQuery.getId(), INVALID_PARAMETER_VALUE, "storedQuery");
            } else {
                for (QueryExpressionText queryEx : description.getQueryExpressionText()) {
                    for (Object content : queryEx.getContent()) {
                        if (content instanceof JAXBElement) {
                            content = ((JAXBElement)content).getValue();
                        }
                        if (content instanceof Query) {
                            final Query query = WFSXmlFactory.cloneQuery((Query)content);
                            applyParameterOnQuery(query, parameters);
                            result.put(description.getId(), query);
                        } else {
                            throw new CstlServiceException("unexpected query object: " + content, INVALID_PARAMETER_VALUE, "storedQuery");
                        }
                    }
                }
            }
        }
        return result;
    }

    private List<String> extractPropertyNames(final List<Object> properties) {
        final List<String> requestPropNames = new ArrayList<>();
        for (Object obj : properties) {
            if (obj instanceof JAXBElement) {
                obj = ((JAXBElement) obj).getValue();
            }
            if (obj instanceof String) {
                String pName = (String) obj;
                final int pos = pName.lastIndexOf(':');
                if (pos != -1) {
                    pName = pName.substring(pos + 1);
                }
                requestPropNames.add(pName);
            } else if (obj instanceof PropertyName) {
                final PropertyName pName = (PropertyName) obj;
                if (pName.getValue() != null) {
                    requestPropNames.add(pName.getValue().getLocalPart());
                }
            }
        }
        return requestPropNames;
    }

    private void putSchemaLocation(final QName typeName, final Map<String, String> schemaLocations, final String version) {
        final String namespace = typeName.getNamespaceURI();
        if (schemaLocations.containsKey(namespace)) {
            LOGGER.severe("TODO multiple typeName schemaLocation");

        } else {
            String prefix = typeName.getPrefix();
            if (prefix == null || prefix.isEmpty()) {
                prefix = "ns1";
            }
            final String url    = getServiceUrl();
            if (url != null) {
                String describeRequest = url + "request=DescribeFeatureType&version=" + version + "&service=WFS";
                describeRequest        = describeRequest + "&namespace=xmlns(" + prefix + "=" + namespace + ")";
                final String tnParameter;
                if (version.equals("2.0.0")) {
                    tnParameter = "typenames";
                } else {
                    tnParameter = "typename";
                }
                describeRequest        = describeRequest + "&" + tnParameter + "=" + prefix + ':' + typeName.getLocalPart();
                schemaLocations.put(namespace, describeRequest);
            }
        }
    }

    private String[] verifyPropertyNames(final QName typeName, final FeatureType ft, final List<String> requestPropNames) throws CstlServiceException {

        if (!requestPropNames.isEmpty()) {

            final Set<GenericName> selected = new LinkedHashSet<>();
            //select mandatory properties
            for (PropertyType pdesc : ft.getProperties(true)) {
                final GenericName propName = pdesc.getName();
                if ((pdesc instanceof AttributeType && ((AttributeType)pdesc).getMinimumOccurs() > 0) ||
                   (AttributeConvention.IDENTIFIER_PROPERTY.equals(propName))) {
                    selected.add(propName);
                }

                //check requested properties
                for (int i=requestPropNames.size()-1;i>=0;i--) {
                    final String cdt = requestPropNames.get(i);
                    try {
                        if (ft.getProperty(cdt)==pdesc) {
                            selected.add(propName);
                            requestPropNames.remove(cdt);
                        }
                    } catch (PropertyNotFoundException ex) {
                        throw new CstlServiceException("The feature Type " + typeName + " has no such property:" + cdt, INVALID_PARAMETER_VALUE);
                    }
                }
            }

            final String[] array = new String[selected.size()];
            final Iterator<GenericName> ite = selected.iterator();
            for (int i=0;i<array.length;i++) array[i] = ite.next().toString();
            return array;

        } else {
            return null;
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Object getFeature(final GetFeature request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "GetFeature request proccesing");
        final long start = System.currentTimeMillis();

        // we verify the base attribute
        verifyBaseRequest(request, false, false);

        long nbMatched                             = 0;
        final String userLogin                     = getUserLogin();
        final String currentVersion                = request.getVersion().toString();
        final int maxFeatures                      = request.getCount();
        final Integer startIndex                   = request.getStartIndex();
        final List<FeatureSet> collections         = new ArrayList<>();
        final Map<String, String> schemaLocations  = new HashMap<>();
        final Map<String, String> namespaceMapping = request.getPrefixMapping();

        //complete mapping by adding sis
        if (namespaceMapping != null) {
            namespaceMapping.put("sis", "sis");
        }

        if ((request.getQuery() == null || request.getQuery().isEmpty()) && (request.getStoredQuery() == null || request.getStoredQuery().isEmpty())) {
            throw new CstlServiceException("You must specify a query!", MISSING_PARAMETER_VALUE);
        }
        final LinkedHashMap<String, ? extends Query> queries = extractStoredQueries(request);

        final Map<QName, Layer> confLayers = getConfigurationLayers(userLogin).stream()
                .collect(
                        Collectors.toMap(
                                Layer::getName,
                                Function.identity(),
                                (Layer l1, Layer l2) -> {
                                    throw new IllegalStateException(
                                            String.format("There's 2 layers with the same name on service %s.%nFirst :%s%nSecond:%s", getId(), l1, l2)
                                    );
                                }
                        ));

        for (final Query query : queries.values()) {
            final Map<String, QName> aliases = new HashMap<>();
            final List<QName> typeNames = new ArrayList<>();
            if (isAllFeatureTypes(query.getTypeNames())) {
                typeNames.addAll(confLayers.keySet());
            } else {
                final List<QName> queryNames = query.getTypeNames();
                for (int i = 0; i < queryNames.size(); i++) {
                    final QName queryName = queryNames.get(i);
                    QName typeName = null;
                    if (confLayers.containsKey(queryName)) {
                        typeName = queryName;
                    } else if (queryName.getNamespaceURI() == null || queryName.getNamespaceURI().trim().isEmpty()) {
                        for (final QName n : confLayers.keySet()) {
                            if (n.getLocalPart().equals(queryName.getLocalPart())) {
                                typeName = n;
                                break;
                            }
                        }
                    }

                    if (typeName == null) {
                        throw new CstlServiceException(UNKNOW_TYPENAME + queryName, INVALID_PARAMETER_VALUE, "typenames");
                    }

                    typeNames.add(typeName);
                    if (query.getAliases().size() > i) {
                        aliases.put(query.getAliases().get(i), typeName);
                    }
                }
            }

            //decode filter-----------------------------------------------------
            final Filter filter = extractJAXBFilter(query.getFilter(), Filter.INCLUDE, namespaceMapping, currentVersion);

            //decode crs--------------------------------------------------------
            final CoordinateReferenceSystem queryCRS = extractCRS(query.getSrsName());

            //decode property names---------------------------------------------
            final List<String> requestPropNames = extractPropertyNames(query.getPropertyNames());

            //decode sort by----------------------------------------------------
            final List<SortBy> sortBys = visitJaxbSortBy(query.getSortBy(), namespaceMapping, currentVersion);


            final QueryBuilder queryBuilder = new QueryBuilder();
            queryBuilder.setCRS(queryCRS);

            if (!sortBys.isEmpty()) {
                queryBuilder.setSortBy(sortBys.toArray(new SortBy[sortBys.size()]));
            }
            if (startIndex != 0){
                queryBuilder.setStartIndex(startIndex);
            }

            for (QName typeName : typeNames) {
                final Layer confLayer = confLayers.get(typeName);

                final Data layerD;
                try {
                    final DataBrief data = dataBuz.getDataBrief(confLayer.getDataId());
                    DataProvider provider = DataProviders.getProvider(data.getProviderId());
                    provider.get(Names.createLocalName(data.getNamespace(), ":", data.getName()));
                    layerD = getLayerReference(userLogin, typeName);
                } catch (CstlServiceException e) {
                    throw e;
                } catch (ConstellationException e) {
                    throw new CstlServiceException("Error while accessing layer data : "+typeName, e);
                }

                if (!(layerD instanceof FeatureData)) {
                    LOGGER.warning(() -> String.format("Invalid layer for name %s in service %s : %s", typeName, getId(), layerD == null? "null" : layerD.getClass()));
                    continue;
                }

                final FeatureData layer = (FeatureData) layerD;

                final FeatureType ft;
                try {
                    ft = layer.getType();
                } catch (ConstellationStoreException ex) {
                    throw new CstlServiceException(ex);
                }

                // we ensure that the property names are contained in the feature type and add the mandatory attribute to the list
                queryBuilder.setProperties(verifyPropertyNames(typeName, ft, requestPropNames));
                queryBuilder.setTypeName(layer.getName());
                queryBuilder.setHints(new Hints(HintsPending.FEATURE_HIDE_ID_PROPERTY, Boolean.TRUE));
                final Filter cleanFilter = processFilter(ft, filter, aliases);
                queryBuilder.setFilter(cleanFilter);

                // we verify that all the properties contained in the filter are known by the feature type.
                final GenericName layerName = NamesExt.create(typeName);
                verifyFilterProperty(NameOverride.wrap(ft, layerName), cleanFilter, aliases);

                if (maxFeatures != 0){
                    queryBuilder.setMaxFeatures(maxFeatures);
                }
                final org.geotoolkit.data.query.Query qb = queryBuilder.buildQuery();

                FeatureCollection collection;
                if (layer.getStore() instanceof FeatureStore) {
                    final Session session = ((FeatureStore)layer.getStore()).createSession(false);
                    collection = session.getFeatureCollection(qb);
                } else if (layer.getOrigin() instanceof FeatureSet) {
                    try {
                        collection = new org.geotoolkit.data.FeatureSetWrapper((FeatureSet) layer.getOrigin(), layer.getStore()).subset(qb);
                    } catch (DataStoreException ex) {
                        throw new CstlServiceException("Error while querying the featureSet", ex);
                    }
                } else {
                    throw new CstlServiceException("Unexpected store configuration");
                }
                int colSize = 0;

                // look for matching count
                try {
                    colSize = collection.size();
                    nbMatched = nbMatched +  colSize;
                } catch (FeatureStoreRuntimeException ex) {
                    throw new CstlServiceException(ex);
                }

                if (colSize>0) {
                    if(queryCRS == null){
                        try {
                            //ensure axes are in the declared order, since we use urn epsg, we must comply
                            //to proper epsg axis order
                            final String defaultCRS = getCRSCode(ft);
                            final CoordinateReferenceSystem rcrs = CRS.forCode(defaultCRS);
                            final CoordinateReferenceSystem dataCrs = FeatureExt.getCRS(ft);
                            if(!Utilities.equalsIgnoreMetadata(rcrs, dataCrs)){
                                collection = FeatureStreams.reproject(collection, CRS.forCode(defaultCRS));
                            }
                        } catch (FactoryException|PropertyNotFoundException|IllegalStateException ex) {
                            // If we cannot extract coordinate system information, we send back brut data.
                            LOGGER.log(Level.WARNING, ex.getMessage(), ex);
                        }
                    }

                    // Ensure exposed name is compliant with service capabilities
                    if (!layerName.equals(ft.getName())) {
                        collection = NameOverride.wrap(collection, layerName);
                    }

                    collections.add(collection);
                    // we write The SchemaLocation
                    putSchemaLocation(typeName, schemaLocations, currentVersion);
                }
            }
        }
        final String gmlVersion;
        if ("text/xml; subtype=\"gml/3.1.1\"".equals(request.getOutputFormat()) ||
            "text/gml; subtype=\"gml/3.1.1\"".equals(request.getOutputFormat())) {
            gmlVersion = "3.1.1";
        } else if ("text/xml; subtype=\"gml/3.2.1\"".equals(request.getOutputFormat()) ||
                   "text/xml; subtype=\"gml/3.2\"".equals(request.getOutputFormat())   ||
                   "application/gml+xml; version=3.2".equals(request.getOutputFormat())) {
            gmlVersion = "3.2.1";
        } else if ("application/json".equals(request.getOutputFormat())) {
            gmlVersion = null;
        } else {
            throw new CstlServiceException("invalid outputFormat:" + request.getOutputFormat(), INVALID_PARAMETER_VALUE, "outputFormat");
        }


        /**
         * 3 possibilities here :
         *    1) return a collection of collection.
         *    2) return an ampty collection
         *    3) if there is only one feature we return (change the return type in object)
         *
         * result TODO find an id and a member type
         */
	if (collections.isEmpty()) {
            collections.add(FeatureStoreUtilities.collection("collection-1", null));
        }
        if (request.getResultType() == ResultTypeType.HITS) {
            final XMLGregorianCalendar calendar;
            try {
                calendar = XmlUtilities.toXML(null, new Date());
            } catch (DatatypeConfigurationException e) {
                throw new CstlServiceException("Unable to create XMLGregorianCalendar from Date.");
            }
            return buildFeatureCollection(currentVersion, "collection-1", (int)nbMatched, calendar);
        }
        LOGGER.log(Level.INFO, "GetFeature treated in {0}ms", (System.currentTimeMillis() - start));

        if(queries.size()==1 && queries.containsKey("urn:ogc:def:query:OGC-WFS::GetFeatureById")){
            return new FeatureSetWrapper(collections, schemaLocations, gmlVersion, currentVersion, (int)nbMatched,true);
        }else{
            return new FeatureSetWrapper(collections, schemaLocations, gmlVersion, currentVersion, (int)nbMatched,false);
        }

    }

    private boolean isAllFeatureTypes(List<QName> typeNames) {
        if (typeNames.isEmpty()) {
            return true;
        } else if (typeNames.size() == 1 && "http://www.opengis.net/gml/3.2".equals(typeNames.get(0).getNamespaceURI())
                                         && "AbstractFeatureType".equals(typeNames.get(0).getLocalPart())) {
            return true;

        }
        return false;
    }

    @Override
    public Object getPropertyValue(final GetPropertyValue request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "GetPropertyValue request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);

        final String valueReference                = request.getValueReference();
        if (valueReference == null) {
            throw new CstlServiceException("ValueReference must be specified", MISSING_PARAMETER_VALUE, "valueReference");
        } else if (valueReference.isEmpty()) {
            throw new CstlServiceException("ValueReference must not be empty", INVALID_PARAMETER_VALUE, "valueReference");
        }

        final String userLogin                     = getUserLogin();
        final Map<String, String> namespaceMapping = request.getPrefixMapping();
        final String currentVersion                = request.getVersion().toString();
        final Collection<? extends Query> queries  = extractStoredQueries(request).values();
        final Integer maxFeatures                  = request.getCount();
        final Map<String, String> schemaLocations  = new HashMap<>();
        final List<FeatureCollection> collections  = new ArrayList<>();

        for (final Query query : queries) {

            final List<QName> typeNames;
            final Map<String, QName> aliases = new HashMap<>();
            if (query.getTypeNames().isEmpty()) {
                typeNames = getConfigurationTypeNames(userLogin);
            } else {
                typeNames = query.getTypeNames();
                if (!query.getAliases().isEmpty()) {
                    for (int i = 0; i < typeNames.size() && i < query.getAliases().size(); i++) {
                        aliases.put(query.getAliases().get(i), typeNames.get(i));
                    }
                }
            }

            //decode filter-----------------------------------------------------
            final Filter filter = extractJAXBFilter(query.getFilter(), Filter.INCLUDE, namespaceMapping, currentVersion);

            //decode crs--------------------------------------------------------
            final CoordinateReferenceSystem crs = extractCRS(query.getSrsName());

            //decode property names---------------------------------------------
            final List<String> requestPropNames = extractPropertyNames(query.getPropertyNames());

            //decode sort by----------------------------------------------------
             final List<SortBy> sortBys = visitJaxbSortBy(query.getSortBy(), namespaceMapping, currentVersion);


            final QueryBuilder queryBuilder = new QueryBuilder();
            queryBuilder.setCRS(crs);

            if (!sortBys.isEmpty()) {
                queryBuilder.setSortBy(sortBys.toArray(new SortBy[sortBys.size()]));
            }
            if (maxFeatures != 0){
                queryBuilder.setMaxFeatures(maxFeatures);
            }

            for (QName typeName : typeNames) {

                final GenericName fullTypeName = Utils.getNameFromQname(typeName);
                if (getFullLayerName(userLogin, fullTypeName) == null) {
                    throw new CstlServiceException(UNKNOW_TYPENAME + typeName, INVALID_PARAMETER_VALUE, "typenames");
                }
                final Data layerD = getLayerReference(userLogin, fullTypeName);

                if (!(layerD instanceof FeatureData)) {continue;}

                final FeatureData layer = (FeatureData) layerD;

                final FeatureType ft;
                try {
                    ft = layer.getType();
                } catch (ConstellationStoreException ex) {
                    throw new CstlServiceException(ex);
                }
                final Filter cleanFilter = processFilter(ft, filter, aliases);

                // we ensure that the property names are contained in the feature type and add the mandatory attribute to the list
                queryBuilder.setProperties(verifyPropertyNames(typeName, ft, requestPropNames));
                queryBuilder.setFilter(cleanFilter);

                queryBuilder.setTypeName(layer.getName());
                queryBuilder.setHints(new Hints(HintsPending.FEATURE_HIDE_ID_PROPERTY, Boolean.TRUE));

                // we verify that all the properties contained in the filter are known by the feature type.
                verifyFilterProperty(NameOverride.wrap(ft, fullTypeName), cleanFilter, aliases);

                FeatureCollection col = ((FeatureStore)layer.getStore()).createSession(false)
                        .getFeatureCollection(queryBuilder.buildQuery());
                NameOverride.wrap(col, fullTypeName);
                collections.add(col);

                // we write The SchemaLocation
                putSchemaLocation(typeName, schemaLocations, currentVersion);
            }
        }

        /**
         * 3 possibility here :
         *    1) merge the collections
         *    2) return a collection of collection.
         *    3) if there is only one feature we return (change the return type in object)
         *
         * result TODO find an id and a member type
         */
        final FeatureCollection featureCollection;
	if (collections.size() > 1) {
            featureCollection = FeatureStreams.sequence("collection-1", collections.toArray(new FeatureCollection[collections.size()]));
        } else if (collections.size() == 1) {
            featureCollection = collections.get(0);
        } else {
            featureCollection = FeatureStoreUtilities.collection("collection-1", null);
        }

        LOGGER.log(Level.INFO, "GetPropertyValue request processed in {0} ms", (System.currentTimeMillis() - startTime));
        if (request.getResultType() == ResultTypeType.HITS) {
            final XMLGregorianCalendar calendar;
            try {
                calendar = XmlUtilities.toXML(null, new Date());
            } catch (DatatypeConfigurationException e) {
                throw new CstlServiceException("Unable to create XMLGregorianCalendar from Date.");
            }
            return buildValueCollection(currentVersion, featureCollection.size(), calendar);
        }
        return new ValueCollectionWrapper(featureCollection, request.getValueReference(), "3.2.1");
    }

    private List<SortBy> visitJaxbSortBy(final org.geotoolkit.ogc.xml.SortBy jaxbSortby,final Map<String, String> namespaceMapping, final String version) {
        if (jaxbSortby != null) {
            final StyleXmlIO util = new StyleXmlIO();
            if ("2.0.0".equals(version)) {
                return util.getTransformer200(namespaceMapping).visitSortBy((org.geotoolkit.ogc.xml.v200.SortByType)jaxbSortby);
            } else {
                return util.getTransformer110(namespaceMapping).visitSortBy((org.geotoolkit.ogc.xml.v110.SortByType)jaxbSortby);
            }
        }
        return new ArrayList<>();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public AbstractGML getGMLObject(final GetGmlObject grbi) throws CstlServiceException {
        throw new CstlServiceException("WFS get GML Object is not supported on this Constellation version.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public LockFeatureResponse lockFeature(final LockFeature gr) throws CstlServiceException {
        throw new CstlServiceException("WFS Lock is not supported on this Constellation version.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public TransactionResponse transaction(final Transaction request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "Transaction request processing\n");
        final long startTime = System.currentTimeMillis();
        if (!isTransactionnal) {
            throw new CstlServiceException("This method is not supported by this mode of WFS", OPERATION_NOT_SUPPORTED, "Request");
        }
        if (isTransactionSecurized() && !SecurityManagerHolder.getInstance().isAuthenticated()) {
            throw new UnauthorizedException("You must be authentified to perform an transaction request.");
        }
        verifyBaseRequest(request, true, false);

        // we prepare the report
        final String userLogin                      = getUserLogin();
        final String currentVersion                 = request.getVersion().toString();
        int totalInserted                           = 0;
        int totalUpdated                            = 0;
        int totalDeleted                            = 0;
        int totalReplaced                           = 0;
        final List<Object> transactions             = request.getTransactionAction();
        final Map<String, String> inserted          = new LinkedHashMap<>();
        final Map<String, String> replaced          = new LinkedHashMap<>();
        final Map<String, String> namespaceMapping  = request.getPrefixMapping();
        final JAXPStreamFeatureReader featureReader = new JAXPStreamFeatureReader(getFeatureTypes(userLogin));
        featureReader.getProperties().put(JAXPStreamFeatureReader.BINDING_PACKAGE, "GML");

        for (Object transaction: transactions) {

            /**
             * Features insertion.
             */
            if (transaction instanceof InsertElement) {
                final InsertElement insertRequest = (InsertElement)transaction;

                final String handle = insertRequest.getHandle();

                // we verify the input format
                if (insertRequest.getInputFormat() != null && !(insertRequest.getInputFormat().equals("text/xml; subtype=\"gml/3.1.1\"")
                                                           ||   insertRequest.getInputFormat().equals("application/gml+xml; version=3.2"))) {
                    throw new CstlServiceException("This only input format supported are: text/xml; subtype=\"gml/3.1.1\" and application/gml+xml; version=3.2",
                            INVALID_PARAMETER_VALUE, "inputFormat");
                }

                // what to do with the CRS ?
                final CoordinateReferenceSystem insertCRS = extractCRS(insertRequest.getSrsName());

                // what to do with that, which ones are supported ??
                final IdentifierGenerationOptionType idGen = insertRequest.getIdgen();

                for (Object featureObject : insertRequest.getFeature()) {
                    if (featureObject instanceof JAXBElement) {
                        featureObject = ((JAXBElement)featureObject).getValue();
                    }

                    FeatureType ft = null;
                    try {
                        if (featureObject instanceof Node) {
                            featureObject = featureReader.read(featureObject);
                        } else if (featureObject instanceof FeatureCollectionType) {
                            final FeatureCollectionType xmlCollection = (FeatureCollectionType) featureObject;
                            final String id = xmlCollection.getId();
                            final List<Feature> features = new ArrayList<>();
                            for (FeaturePropertyType fprop : xmlCollection.getFeatureMember()) {
                                Feature feat = (Feature)featureReader.read(fprop.getUnknowFeature());
                                ft = feat.getType();
                                features.add(feat);
                            }
                            featureObject = features;
                        }
                    } catch (IllegalArgumentException ex) {
                        throw new CstlServiceException(ex.getMessage(), ex, INVALID_VALUE);
                    } catch (IOException | XMLStreamException ex) {
                        throw new CstlServiceException(ex);
                    }
                    Collection<Feature> featureCollection;

                    if (featureObject instanceof Feature) {
                        final Feature feature = (Feature) featureObject;
                        ft = feature.getType();
                        featureCollection = Arrays.asList(feature);
                    } else if (featureObject instanceof List) {
                        featureCollection = (List) featureObject;
                    } else if (featureObject instanceof FeatureCollection) {
                        featureCollection = (Collection) featureObject;
                        ft = ((FeatureCollection)featureCollection).getType();
                    } else if (featureObject instanceof FeatureSet) {
                        try {
                            featureCollection = ((FeatureSet) featureObject).features(false).collect(Collectors.toList());
                            ft = ((FeatureSet) featureObject).getType();
                        } catch (DataStoreException ex) {
                            throw new CstlServiceException(ex);
                        }

                    } else {
                        final String featureType;
                        if (featureObject == null) {
                            featureType = "null";
                        } else {
                            if (featureObject instanceof JAXBElement) {
                                featureType = "JAXBElement<" + ((JAXBElement)featureObject).getValue().getClass().getName() + ">";
                            } else {
                                featureType = featureObject.getClass().getName();
                            }
                        }
                        throw new CstlServiceException("Unexpected Object to insert:" + featureType);
                    }
                    GenericName typeName = ft.getName();

                    if (getFullLayerName(userLogin, typeName) == null) {
                        throw new CstlServiceException(UNKNOW_TYPENAME + typeName);
                    }
                    final FeatureData layer = (FeatureData) getLayerReference(userLogin, typeName);
                    try {
                        final FeatureType type = layer.getType();
                        final CoordinateReferenceSystem trueCrs = FeatureExt.getCRS(type);
                        if(trueCrs != null && !Utilities.equalsIgnoreMetadata(trueCrs, FeatureExt.getCRS(ft))){
                            final FeatureCollection collection = FeatureStoreUtilities.collection(ft,featureCollection);
                            featureCollection = FeatureStreams.reproject(collection, trueCrs);
                        }

                        final List<FeatureId> features = ((FeatureStore)layer.getStore()).addFeatures(typeName.toString(), featureCollection);

                        for (FeatureId fid : features) {
                            inserted.put(fid.getID(), handle);// get the id of the inserted feature
                            totalInserted++;
                            LOGGER.log(Level.FINER, "fid inserted: {0} total:{1}", new Object[]{fid, totalInserted});
                        }
                    } catch (ConstellationStoreException | DataStoreException ex) {
                        Logging.unexpectedException(LOGGER,DefaultWFSWorker.class,"transaction", ex);
                    } catch (ClassCastException ex) {
                        Logging.unexpectedException(LOGGER,DefaultWFSWorker.class,"transaction", ex);
                        throw new CstlServiceException("The specified Datastore does not suport the write operations.");
                    }
                }

            /**
             * Features remove.
             */
            } else if (transaction instanceof DeleteElement) {

                final DeleteElement deleteRequest = (DeleteElement) transaction;

                //decode filter-----------------------------------------------------
                if (deleteRequest.getFilter() == null) {
                    throw new CstlServiceException("The filter must be specified.", MISSING_PARAMETER_VALUE, "filter");
                }
                final Filter filter = extractJAXBFilter(deleteRequest.getFilter(), Filter.EXCLUDE, namespaceMapping, currentVersion);

                final GenericName typeName = Utils.getNameFromQname(deleteRequest.getTypeName());
                if (getFullLayerName(userLogin, typeName) == null) {
                    throw new CstlServiceException(UNKNOW_TYPENAME + typeName, INVALID_PARAMETER_VALUE, "typename");
                }
                final FeatureData layer = (FeatureData) getLayerReference(userLogin, typeName);
                try {
                    final FeatureType ft = layer.getType();
                    final Filter cleanFilter = processFilter(ft, filter, null);
                    FeatureSet fs = (FeatureSet) layer.getOrigin();

                    // we verify that all the properties contained in the filter are known by the feature type.
                    verifyFilterProperty(NameOverride.wrap(ft, typeName), cleanFilter, null);

                    // we extract the number of feature deleted
                    final QueryBuilder queryBuilder = new QueryBuilder(layer.getName().toString());
                    queryBuilder.setFilter(cleanFilter);

                    totalDeleted = totalDeleted + (int) FeatureStoreUtilities.getCount(fs.subset(queryBuilder.buildQuery())).intValue();

                    if (fs instanceof WritableFeatureSet) {
                        ((WritableFeatureSet) fs).removeIf((f)-> filter.evaluate(f));
                    } else {
                        throw new CstlServiceException("This feature set is not Writable");
                    }
                } catch (ConstellationStoreException | DataStoreException ex) {
                    throw new CstlServiceException(ex);
                } catch (ClassCastException ex) {
                    Logging.unexpectedException(LOGGER,DefaultWFSWorker.class,"transaction", ex);
                    throw new CstlServiceException("The specified Datastore does not suport the delete operations.");
                }

            /**
             * Features updates.
             */
            } else if (transaction instanceof UpdateElement) {

                final UpdateElement updateRequest = (UpdateElement) transaction;

                // we verify the input format
                if (updateRequest.getInputFormat() != null && !(updateRequest.getInputFormat().equals("text/xml; subtype=\"gml/3.1.1\"")
                                                           ||   updateRequest.getInputFormat().equals("application/gml+xml; version=3.2"))) {
                    throw new CstlServiceException("This only input format supported are: text/xml; subtype=\"gml/3.1.1\" and application/gml+xml; version=3.2",
                            INVALID_PARAMETER_VALUE, "inputFormat");
                }

                //decode filter-----------------------------------------------------
                final Filter filter = extractJAXBFilter(updateRequest.getFilter(),Filter.EXCLUDE, namespaceMapping, currentVersion);

                //decode crs--------------------------------------------------------
                final CoordinateReferenceSystem crs = extractCRS(updateRequest.getSrsName());

                final GenericName typeName = Utils.getNameFromQname(updateRequest.getTypeName());
                if (getFullLayerName(userLogin, typeName) == null) {
                    throw new CstlServiceException(UNKNOW_TYPENAME + typeName, INVALID_PARAMETER_VALUE, "typename");
                }
                final FeatureData layer = (FeatureData) getLayerReference(userLogin, typeName);
                try {
                    final FeatureType ft = layer.getType();
                    if (ft == null) {
                        throw new CstlServiceException("Unable to find the featuretype:" + layer.getName());
                    }
                    final FeatureSet fs = (FeatureSet) layer.getOrigin();

                    final Map<String,Object> values = new HashMap<>();

                    // we verify that the update property are contained in the feature type
                    for (final Property updateProperty : updateRequest.getProperty()) {
                        String updatePropertyName = updateProperty.getLocalName();
                        Binding pa = Bindings.getBinding(FeatureType.class, updatePropertyName);
                        if (pa == null || pa.get(ft, updatePropertyName, null) == null) {
                            throw new CstlServiceException("The feature Type " + updateRequest.getTypeName() + " has no such property: " + updatePropertyName, INVALID_VALUE);
                        }
                        PropertyType propertyType = (PropertyType) pa.get(ft, updatePropertyName, null);
                        if (propertyType instanceof FeatureAssociationRole && updateProperty.getValue() != null) {
                            FeatureAssociationRole ct = (FeatureAssociationRole) propertyType;
                            try {
                                ct.getValueType().getProperty("_value");
                                updatePropertyName = "/" + updatePropertyName + "/_value";
                                propertyType = ct.getValueType().getProperty("_value");
                            } catch(PropertyNotFoundException ex) {
                                //do nothing
                            }
                        }

                        Object value;
                        if (updateProperty.getValue() instanceof Element) {
                            final String strValue = getXMLFromElementNSImpl((Element)updateProperty.getValue());
                            value = null;
                            LOGGER.log(Level.FINER, ">> updating : {0}   => {1}", new Object[]{updatePropertyName, strValue});
                        } else {
                            value = updateProperty.getValue();
                            if (value instanceof AbstractGeometryType) {
                                try {
                                    final String defaultCRS = getCRSCode(ft);
                                    final CoordinateReferenceSystem exposedCrs = CRS.forCode(defaultCRS);
                                    final CoordinateReferenceSystem trueCrs = FeatureExt.getCRS(propertyType);

                                    value = GeometrytoJTS.toJTS((AbstractGeometryType) value);
                                    if(trueCrs != null && !Utilities.equalsIgnoreMetadata(exposedCrs, trueCrs)){
                                        value = JTS.transform((Geometry)value, CRS.findOperation(exposedCrs, trueCrs, null).getMathTransform());
                                    }

                                } catch (TransformException | FactoryException ex) {
                                    Logging.unexpectedException(LOGGER,DefaultWFSWorker.class,"transaction", ex);
                                } catch (IllegalArgumentException ex) {
                                    throw new CstlServiceException(ex);
                                }
                            } else if (value instanceof DirectPosition) {
                                final DirectPosition dp = (DirectPosition) value;
                                value = new GeometryFactory().createPoint(new Coordinate(dp.getOrdinate(0), dp.getOrdinate(1)));
                            } else if (value instanceof String) {
                                value = featureReader.readValue((String) value, (AttributeType) propertyType);
                            }
                            LOGGER.log(Level.FINER, ">> updating : {0} => {1}", new Object[]{updatePropertyName, value});
                            if (value != null) {
                                LOGGER.log(Level.FINER, "type : {0}", value.getClass());
                            }
                        }
                        values.put(updatePropertyName, value);

                    }

                    final Filter cleanFilter = processFilter(ft, filter, null);
                    // we verify that all the properties contained in the filter are known by the feature type.
                    verifyFilterProperty(NameOverride.wrap(ft, typeName), cleanFilter, null);

                    // we extract the number of feature update
                    final QueryBuilder queryBuilder = new QueryBuilder(layer.getName().toString());
                    queryBuilder.setFilter(cleanFilter);
                    totalUpdated = totalUpdated + (int) FeatureStoreUtilities.getCount(fs.subset(queryBuilder.buildQuery())).intValue();

                    FeatureWriter fw = ((FeatureStore)layer.getStore()).getFeatureWriter(QueryBuilder.filtered(layer.getName().toString(), filter));
                    try {
                        while (fw.hasNext()) {
                            Feature feat = fw.next();
                            for (Entry<String, Object> entry : values.entrySet()) {
                                Binding pa = Bindings.getBinding(Feature.class, entry.getKey());
                                pa.set(feat, entry.getKey(), entry.getValue());
                            }
                            fw.write();
                        }
                    } finally {
                        fw.close();
                    }
                } catch (ConstellationStoreException | DataStoreException ex) {
                    throw new CstlServiceException(ex);
                }


            } else if (transaction instanceof ReplaceElement) {

                final ReplaceElement replaceRequest = (ReplaceElement) transaction;
                final String handle = replaceRequest.getHandle();

                // we verify the input format
                if (replaceRequest.getInputFormat() != null && !(replaceRequest.getInputFormat().equals("text/xml; subtype=\"gml/3.1.1\"")
                                                            ||   replaceRequest.getInputFormat().equals("application/gml+xml; version=3.2"))) {
                    throw new CstlServiceException("This only input format supported are: text/xml; subtype=\"gml/3.1.1\" and application/gml+xml; version=3.2",
                            INVALID_PARAMETER_VALUE, "inputFormat");
                }

                //decode filter-----------------------------------------------------
                final Filter filter = extractJAXBFilter(replaceRequest.getFilter(),Filter.EXCLUDE, namespaceMapping, currentVersion);

                //decode crs--------------------------------------------------------
                final CoordinateReferenceSystem crs = extractCRS(replaceRequest.getSrsName());

                // extract replacement feature
                Object featureObject = replaceRequest.getFeature();
                if (featureObject instanceof JAXBElement) {
                    featureObject = ((JAXBElement) featureObject).getValue();
                }
                try {
                    if (featureObject instanceof Node) {

                        featureObject = featureReader.read(featureObject);

                    } else if (featureObject instanceof FeatureCollectionType) {
                        final FeatureCollectionType xmlCollection = (FeatureCollectionType) featureObject;
                        final String id = xmlCollection.getId();
                        final List<Feature> features = new ArrayList<>();
                        FeatureType ft = null;
                        for (FeaturePropertyType fprop : xmlCollection.getFeatureMember()) {
                            Feature feat = (Feature) featureReader.read(fprop.getUnknowFeature());
                            ft = feat.getType();
                            features.add(feat);
                        }
                        final FeatureCollection collection = FeatureStoreUtilities.collection(id, ft);
                        collection.addAll(features);
                        featureObject = collection;
                    }
                } catch (IllegalArgumentException ex) {
                    throw new CstlServiceException(ex.getMessage(), ex, INVALID_PARAMETER_VALUE);
                } catch (IOException | XMLStreamException ex) {
                    throw new CstlServiceException(ex);
                }
                final GenericName typeName;
                FeatureCollection featureCollection;

                if (featureObject instanceof Feature) {
                    final Feature feature = (Feature) featureObject;
                    typeName = feature.getType().getName();
                    featureCollection = FeatureStoreUtilities.collection(feature);
                } else if (featureObject instanceof FeatureCollection) {
                    featureCollection = (FeatureCollection) featureObject;
                    typeName = ((FeatureCollection) featureCollection).getType().getName();
                } else if (featureObject instanceof FeatureSet) {
                    try {
                        typeName = ((FeatureSet) featureObject).getType().getName();
                        if (getFullLayerName(userLogin, typeName) == null) {
                            throw new CstlServiceException(UNKNOW_TYPENAME + typeName);
                        }
                        final FeatureData layer = (FeatureData) getLayerReference(userLogin, typeName);
                        featureCollection = new org.geotoolkit.data.FeatureSetWrapper((FeatureSet) featureObject, layer.getStore());
                    } catch (DataStoreException ex) {
                        throw new CstlServiceException(ex);
                    }

                } else {
                    final String featureType;
                    if (featureObject == null) {
                        featureType = "null";
                    } else {
                        if (featureObject instanceof JAXBElement) {
                            featureType = "JAXBElement<" + ((JAXBElement) featureObject).getValue().getClass().getName() + ">";
                        } else {
                            featureType = featureObject.getClass().getName();
                        }
                    }
                    throw new CstlServiceException("Unexpected replacement object:" + featureType);
                }

                if (getFullLayerName(userLogin, typeName) == null) {
                    throw new CstlServiceException(UNKNOW_TYPENAME + typeName);
                }

                try {
                    final FeatureData layer = (FeatureData) getLayerReference(userLogin, typeName);
                    final FeatureType ft    = layer.getType();
                    final FeatureSet fs     = (FeatureSet) layer.getOrigin();
                    final String layerName  = layer.getName().toString();

                    // we extract the number of feature to replace
                    final QueryBuilder queryBuilder = new QueryBuilder(layerName);
                    queryBuilder.setFilter(processFilter(ft, filter, null));
                    totalReplaced = totalReplaced + (int) FeatureStoreUtilities.getCount(fs.subset(queryBuilder.buildQuery())).intValue();

                    // first remove the feature to replace
                    ((FeatureStore)layer.getStore()).removeFeatures(layerName, filter);

                    // then add the new one
                    final CoordinateReferenceSystem trueCrs = FeatureExt.getCRS(ft);
                    if(trueCrs != null && !Utilities.equalsIgnoreMetadata(trueCrs, FeatureExt.getCRS(featureCollection.getType()))){
                        featureCollection = FeatureStreams.reproject(featureCollection, trueCrs);
                    }

                    final List<FeatureId> features = ((FeatureStore)layer.getStore()).addFeatures(layerName, featureCollection);

                    for (FeatureId fid : features) {
                        replaced.put(fid.getID(), handle);// get the id of the replaced feature
                        LOGGER.log(Level.FINER, "fid inserted: {0} total:{1}", new Object[]{fid, totalInserted});
                    }

                } catch (ConstellationStoreException | DataStoreException ex) {
                    throw new CstlServiceException(ex);
                }

            } else {
                String className = " null object";
                if (transaction != null) {
                    className = transaction.getClass().getName();
                }
                throw new CstlServiceException("This kind of transaction is not supported by the service: " + className,
                                              INVALID_PARAMETER_VALUE, "transaction");
            }

        }

        final TransactionResponse response = buildTransactionResponse(currentVersion,
                                                                      totalInserted,
                                                                      totalUpdated,
                                                                      totalDeleted,
                                                                      totalReplaced,
                                                                      inserted,
                                                                      replaced);
        LOGGER.log(Level.INFO, "Transaction request processed in {0} ms", (System.currentTimeMillis() - startTime));

        return response;
    }

    /**
     * Extract the a XML string from a W3C Element.
     *
     * @param elt An W3c Xml Element.
     *
     * @return a string containing the xml representation.
     */
    private  String getXMLFromElementNSImpl(final Element elt) {
        final StringBuilder s = new StringBuilder();
        s.append('<').append(elt.getLocalName()).append('>');
        final Node node = elt.getFirstChild();
        s.append(getXMLFromNode(node));

        s.append("</").append(elt.getLocalName()).append('>');
        return s.toString();
    }

    /**
     * Extract the a XML string from a W3C node.
     *
     * @param node An W3c Xml node.
     *
     * @return a string builder containing the xml.
     */
    private  StringBuilder getXMLFromNode(final Node node) {
        final StringBuilder temp = new StringBuilder();
        if (!node.getNodeName().equals("#text")){
            temp.append("<").append(node.getNodeName());
            final NamedNodeMap attrs = node.getAttributes();
            for(int i=0;i<attrs.getLength();i++){
                temp.append(" ").append(attrs.item(i).getNodeName()).append("=\"").append(attrs.item(i).getTextContent()).append("\" ");
            }
            temp.append(">");
        }
        if (node.hasChildNodes()) {
            final NodeList nodes = node.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                temp.append(getXMLFromNode(nodes.item(i)));
            }
        }
        else{
            temp.append(node.getTextContent());
        }
        if (!node.getNodeName().equals("#text")) {temp.append("</").append(node.getNodeName()).append(">");}
        return temp;
    }

    /**
     * Extract an OGC filter usable by the dataStore from the request filter
     * unmarshalled by JAXB.
     *
     * @param jaxbFilter an OGC JAXB filter.
     * @return An OGC filter
     * @throws CstlServiceException
     */
    private Filter extractJAXBFilter(final Filter jaxbFilter, final Filter defaultFilter, final Map<String, String> namespaceMapping, final String currentVersion) throws CstlServiceException {
        final StyleXmlIO util = new StyleXmlIO();
        final Filter filter;
        try {
            if (jaxbFilter != null) {
                if ("2.0.0".equals(currentVersion)) {
                    filter = util.getTransformer200(namespaceMapping).visitFilter((org.geotoolkit.ogc.xml.v200.FilterType)jaxbFilter);
                } else {
                    filter = util.getTransformer110(namespaceMapping).visitFilter((org.geotoolkit.ogc.xml.v110.FilterType)jaxbFilter);
                }
            } else {
                filter = defaultFilter;
            }
        } catch (Exception ex) {
            throw new CstlServiceException(ex, INVALID_PARAMETER_VALUE);
        }
        return filter;
    }

    /**
     * Return a coordinate reference system from an identifier.
     *
     * @param srsName a CRS identifier.
     * @return
     * @throws CstlServiceException
     */
    private CoordinateReferenceSystem extractCRS(final String srsName) throws CstlServiceException {
        final CoordinateReferenceSystem crs;
        if (srsName != null) {
            try {
                crs = CRS.forCode(srsName);
                //todo use other properties to filter properly
            } catch (NoSuchAuthorityCodeException ex) {
                throw new CstlServiceException(ex, INVALID_PARAMETER_VALUE, "srsName");
            } catch (FactoryException ex) {
                throw new CstlServiceException(ex, INVALID_PARAMETER_VALUE, "srsName");
            }
        } else {
            crs = null;
        }
        return crs;
    }

    /**
     * Verify that all the property contained in the filter are known by the featureType
     *
     * @param ft A featureType.
     * @param filter An OGC filter.
     *
     * @throws CstlServiceException if one of the propertyName in the filter is not present in the featureType.
     */
    private void verifyFilterProperty(final FeatureType ft, final Filter filter, final Map<String, QName> aliases) throws CstlServiceException {
        final Collection<String> filterProperties = (Collection<String>) filter.accept(ListingPropertyVisitor.VISITOR, null);
        if (filterProperties != null) {
            for (String filterProperty : filterProperties) {

                if (filterProperty.startsWith("@")){
                    //this property in an id property, we won't find it in the feature type
                    //but it always exist on the features
                    continue;
                }

                // look to remove featureType prefix
                String ftName = "";
                if (NamesExt.getNamespace(ft.getName()) != null) {
                    ftName = "{" + NamesExt.getNamespace(ft.getName()) + "}";
                }
                ftName = ftName + ft.getName().tip().toString();
                if (filterProperty.startsWith(ftName)) {
                    filterProperty = filterProperty.substring(ftName.length());
                }
                if (aliases != null) {
                    for (String entry : aliases.keySet()) {
                        if (filterProperty.startsWith(entry + "/")) {
                            filterProperty =  filterProperty.substring(entry.length());
                        }
                    }
                }
                final Binding pa = Bindings.getBinding(FeatureType.class, filterProperty);
                if (pa == null || pa.get(ft, filterProperty, null) == null) {
                    String s = "";
                    if (NamesExt.getNamespace(ft.getName()) != null) {
                        s = "{" + NamesExt.getNamespace(ft.getName()) + "}";
                    }
                    s = s + ft.getName().tip().toString();
                    throw new CstlServiceException("The feature Type " + s + " has no such property: " + filterProperty, INVALID_PARAMETER_VALUE, "filter");
                }
            }
        }

        if (!((Boolean)filter.accept(new IsValidSpatialFilterVisitor(ft), null))) {
            throw new CstlServiceException("The filter try to apply spatial operators on non-spatial property", INVALID_PARAMETER_VALUE, "filter");
        }
    }

    /**
     * Ensure crs is set on all geometric elements and with correct crs.
     * replace Aliases by correct feature type names.
     * remove feature type name prefixing propertyName.
     */
    private Filter processFilter(final FeatureType ft, Filter filter, final Map<String, QName> aliases) {
        try {
            filter = (Filter) filter.accept(new AliasFilterVisitor(aliases), null);
            filter = (Filter) filter.accept(new UnprefixerFilterVisitor(ft), null);
            filter = (Filter) filter.accept(new DefaultGeomPropertyVisitor(ft), null);
            filter = (Filter) filter.accept(new GMLNamespaceVisitor(), null);
            filter = (Filter) filter.accept(new BooleanVisitor(ft), null);

            final String defaultCRS = getCRSCode(ft);
            final CoordinateReferenceSystem exposedCrs = CRS.forCode(defaultCRS);
            final CoordinateReferenceSystem trueCrs = getCRS(ft);
            if (exposedCrs!=null && trueCrs!=null && !Utilities.equalsIgnoreMetadata(trueCrs, exposedCrs)) {
                filter = (Filter) filter.accept(FillCrsVisitor.VISITOR, exposedCrs);
                filter = (Filter) filter.accept(new CrsAdjustFilterVisitor(exposedCrs, trueCrs), null);
            }

        } catch (FactoryException|PropertyNotFoundException|IllegalStateException ex) {
            /* In case we cannot analyze CRS (no geometric property, or multiple
             * ones, or if a problem occurs with referencing engine), we simply
             * ignore the filter.
             */
            LOGGER.log(Level.WARNING, ex.getMessage(), ex);
        }
        return filter;
    }

    /**
     * Extract the WGS84 BBOx from a featureSource.
     * what ? may not be wgs84 exactly ? why is there a CRS attribute on a wgs84 bbox ?
     */
    private static Object toBBox(final FeatureData fld, final String version) throws CstlServiceException{
        try {
            Envelope env = fld.getEnvelope();
            if (env != null) {
                final CoordinateReferenceSystem epsg4326 = CRS.forCode("urn:ogc:def:crs:OGC:2:84");
                if (!Utilities.equalsIgnoreMetadata(env.getCoordinateReferenceSystem(), epsg4326)) {
                    env = Envelopes.transform(env, epsg4326);
                }
                return buildBBOX(version,
                       "urn:ogc:def:crs:OGC:2:84",
                       env.getMinimum(0),
                       env.getMinimum(1),
                       env.getMaximum(0),
                       env.getMaximum(1));
            }
        } catch (ConstellationStoreException | TransformException | FactoryException ex) {
            throw new CstlServiceException(ex);
        }
        // return default full BBOX
        return buildBBOX(version,"urn:ogc:def:crs:OGC:2:84", -180, -90, 180, 90);
    }

    private static void applyParameterOnQuery(final Query query, final List<? extends Parameter> parameters) throws CstlServiceException {
        applyParameterOnFilter(query.getFilter(), parameters);
        final List<QName> toRemove = new ArrayList<>();
        final List<QName> toAdd    = new ArrayList<>();
        if (query.getTypeNames().isEmpty()) {
            for (Parameter param : parameters) {
                if (!param.getContent().isEmpty() && param.getContent().get(0) instanceof QName && param.getName().equalsIgnoreCase("typeName")) {
                    toAdd.add((QName)param.getContent().get(0));
                }
            }
        } else {
            for (QName q : query.getTypeNames()) {
                for (Parameter param : parameters) {
                    if (q.getLocalPart().contains("$" +  param.getName())) {
                        toRemove.add(q);
                        if (!param.getContent().isEmpty() && param.getContent().get(0) instanceof QName) {
                            toAdd.add((QName)param.getContent().get(0));
                        } else {
                            LOGGER.warning("bad type or empty parameter content");
                        }
                    }
                }
            }
        }
        query.getTypeNames().removeAll(toRemove);
        query.getTypeNames().addAll(toAdd);
    }

    private static void applyParameterOnFilter(final Filter filter, final List<? extends Parameter> parameters) throws CstlServiceException {
        final Object filterObject;
        if (filter instanceof XMLFilter) {
            filterObject = ((XMLFilter)filter).getFilterObject();
        } else {
            filterObject = filter;
        }

        if (filterObject instanceof BBOXType) {
           final BBOXType bb = (BBOXType) filterObject;
           if (bb.getAny() != null && bb.getAny() instanceof String) {
               String s = (String)bb.getAny();
               for (Parameter param : parameters) {
                   if (s.contains("${" + param.getName() + '}')) {
                       bb.setAny(param.getContent().get(0));
                   }
               }
           }

       } else if (filterObject instanceof BinarySpatialOperator) {
           final BinarySpatialOperator binary = (BinarySpatialOperator) filterObject;
           if (binary.getExpression2() != null && binary.getExpression2() instanceof XMLLiteral) {
               final XMLLiteral lit = (XMLLiteral) binary.getExpression2();
               if (lit.getValue() instanceof String) {
                   String s = (String)lit.getValue();
                   for (Parameter param : parameters) {
                       if (s.contains("${" + param.getName() + '}')) {
                           s = s.replace("${" + param.getName()+ '}', (String)param.getContent().get(0));
                       }
                   }
                   lit.getContent().clear();
                   lit.setContent(s);
               }
           }

       } else if (filterObject instanceof BinaryComparisonOperator) {
           final BinaryComparisonOperator binary = (BinaryComparisonOperator) filterObject;
           if (binary.getExpression2() != null && binary.getExpression2() instanceof XMLLiteral) {
               final XMLLiteral lit = (XMLLiteral) binary.getExpression2();
               if (lit.getValue() instanceof String) {
                   String s = (String)lit.getValue();
                   for (Parameter param : parameters) {
                       if (s.contains("${"  + param.getName()+ '}')) {
                           s = s.replace("${"  + param.getName()+ '}', (String)param.getContent().get(0));
                       }
                   }
                   lit.getContent().clear();
                   lit.setContent(s);
               }
           }

       } else if (filterObject instanceof BinaryLogicOperator) {
           final BinaryLogicOperator binary = (BinaryLogicOperator) filterObject;
           for (Filter child : binary.getChildren()) {
               applyParameterOnFilter(child, parameters);
           }
       } else  if (filter != null) {
           throw new CstlServiceException("Unimplemented filter implementation:" + filterObject.getClass().getName(), NO_APPLICABLE_CODE);
       }
    }

    /**
     * Verify that the bases request attributes are correct.
     *
     * @param request an object request with the base attribute (all except GetCapabilities request);
     */
    private void verifyBaseRequest(final RequestBase request, final boolean versionMandatory, final boolean getCapabilities) throws CstlServiceException {
        isWorking();
        if (request != null) {
            if (request.getService() != null) {
                if (request.getService().isEmpty()) {
                  // we let pass (CITE test)
                } else if (!request.getService().equalsIgnoreCase("WFS"))  {
                    throw new CstlServiceException("service must be \"WFS\"!",
                                                  INVALID_PARAMETER_VALUE, "service");
                }
            } else {
                throw new CstlServiceException("service must be specified!",
                                              MISSING_PARAMETER_VALUE, "service");
            }
            if (request.getVersion() != null) {
                if (isSupportedVersion(request.getVersion().toString())) {
                    request.setVersion(request.getVersion().toString());

                // for the CITE test
                } else if (request.getVersion().toString().isEmpty()) {
                    request.setVersion(ServiceDef.WFS_1_1_0.version.toString());

                } else {
                    final CodeList code;
                    if (getCapabilities) {
                        code = VERSION_NEGOTIATION_FAILED;
                    } else {
                        code = INVALID_PARAMETER_VALUE;
                    }
                    throw new CstlServiceException("version must be \"1.1.0\" or \"2.0.0\"!", code, "version");
                }
            } else {
                if (versionMandatory) {
                    throw new CstlServiceException("version must be specified!", MISSING_PARAMETER_VALUE, "version");
                } else {
                    request.setVersion(ServiceDef.WFS_1_1_0.version.toString());
                }
            }
         } else {
            throw new CstlServiceException("The request is null!", NO_APPLICABLE_CODE);
         }
    }

    private List<FeatureType> getFeatureTypes(final String userLogin) throws CstlServiceException {
        final List<FeatureType> types = new ArrayList<>();

        //search all types
        for (final NameInProvider name : getConfigurationLayerNames(userLogin)) {
            final Data layer = getLayerReference(userLogin, name.name);
            if (!(layer instanceof FeatureData)) {continue;}
            try {
                //fix feature type to define the exposed crs : true EPSG axis order
                final FeatureType baseType = ((FeatureData)layer).getType();
                final String crsCode = getCRSCode(baseType);
                final CoordinateReferenceSystem exposedCrs = CRS.forCode(crsCode);
                final FeatureType exposedType = FeatureTypeExt.createSubType(baseType, null, exposedCrs);
                types.add(exposedType);
            } catch (Exception ex) {
                LOGGER.severe("DataStore exception while getting featureType");
            }
        }
        return types;
    }

    @Override
    public ListStoredQueriesResponse listStoredQueries(final ListStoredQueries request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "ListStoredQueries request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);

        final String currentVersion = request.getVersion().toString();

        final ListStoredQueriesResponse response = buildListStoredQueriesResponse(currentVersion, storedQueries);
        LOGGER.log(Level.INFO, "ListStoredQueries request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;

    }

    @Override
    public DescribeStoredQueriesResponse describeStoredQueries(final DescribeStoredQueries request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "DescribeStoredQueries request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);
        final String currentVersion = request.getVersion().toString();

        final List<StoredQueryDescription> storedQueryList;
        if (request.getStoredQueryId() != null && !request.getStoredQueryId().isEmpty()) {
            storedQueryList = new ArrayList<>();
            for (String id : request.getStoredQueryId()) {
                StoredQueryDescription description = getStoredQueryById(id);
                if (description != null) {
                    storedQueryList.add(description);
                }
            }
        } else {
            storedQueryList = storedQueries;
        }
        final DescribeStoredQueriesResponse response = buildDescribeStoredQueriesResponse(currentVersion, storedQueryList);
        LOGGER.log(Level.INFO, "DescribeStoredQueries request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;
    }

    private StoredQueryDescription getStoredQueryById(String id) {
        for (StoredQueryDescription description : storedQueries) {
            if (description.getId().equals(id)) {
                return description;
            }
        }
        return null;
    }

    @Override
    public CreateStoredQueryResponse createStoredQuery(final CreateStoredQuery request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "CreateStoredQuery request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);
        final String currentVersion  = request.getVersion().toString();

        for (StoredQueryDescription query : request.getStoredQueryDefinition()) {
            if (getStoredQueryById(query.getId()) != null) {
                throw new CstlServiceException("Stored query:" + query.getId() + " already exist",
                            DUPLICATE_STORED_QUERY_ID_VALUE, query.getId());
            }
            for (QueryExpressionText qet :query.getQueryExpressionText()) {
                if (!"urn:ogc:def:queryLanguage:OGC-WFS::WFS_QueryExpression".equals(qet.getLanguage()) &&
                    !"urn:ogc:def:queryLanguage:OGC-WFS::WFSQueryExpression".equals(qet.getLanguage())) { // error in CITE test
                    throw new CstlServiceException("Invalid language query. Accepted values are:{urn:ogc:def:queryLanguage:OGC-WFS::WFS_QueryExpression, urn:ogc:def:queryLanguage:OGC-WFS::WFSQueryExpression}",
                            INVALID_PARAMETER_VALUE, "language");
                }
            }
        }

        storedQueries.addAll(request.getStoredQueryDefinition());
        storedQueries();

        final CreateStoredQueryResponse response = buildCreateStoredQueryResponse(currentVersion, "OK");
        LOGGER.log(Level.INFO, "CreateStoredQuery request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;
    }

    @Override
    public DropStoredQueryResponse dropStoredQuery(final DropStoredQuery request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "dropStoredQuery request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);
        final String currentVersion  = request.getVersion().toString();

        StoredQueryDescription candidate = null;
        for (StoredQueryDescription sq : storedQueries) {
            if (sq.getId().equals(request.getId())) {
                candidate = sq;
            }
        }
        if (candidate == null) {
            throw new CstlServiceException("Unexisting Stored query: " + request.getId(), INVALID_PARAMETER_VALUE, "id");
        } else  {
            storedQueries.remove(candidate);
        }
        storedQueries();

        final DropStoredQueryResponse response = buildDropStoredQueryResponse(currentVersion, "OK");
        LOGGER.log(Level.INFO, "dropStoredQuery request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;
    }

    @Override
    public List<ParameterExpression> getParameterForStoredQuery(final String queryId) {
        final List<ParameterExpression> results = new ArrayList<>();
        for (StoredQueryDescription description : storedQueries) {
            if (description.getId().equals(queryId)) {
                results.addAll(description.getParameter());
            }
        }
        return results;
    }

    private List<QName> getConfigurationTypeNames(String login) {
        List<QName> results = new ArrayList<>();
        for (NameInProvider nop : getConfigurationLayerNames(login)) {
            results.add(Utils.getQnameFromName(nop.name));
        }
        return results;
    }
}
