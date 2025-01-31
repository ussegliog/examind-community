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

package org.constellation.wfs.ws.rs;

// J2SE dependencies

import java.io.IOException;

import org.apache.sis.xml.MarshallerPool;
import org.constellation.api.ServiceDef;
import org.constellation.api.ServiceDef.Specification;
import org.constellation.wfs.core.WFSWorker;
import org.constellation.ws.CstlServiceException;
import org.constellation.ws.UnauthorizedException;
import org.constellation.ws.WebServiceUtilities;
import org.constellation.ws.Worker;
import org.constellation.ws.rs.GridWebService;
import org.constellation.xml.PrefixMappingInvocationHandler;
import org.geotoolkit.client.RequestsUtilities;
import org.geotoolkit.nio.IOUtilities;
import org.geotoolkit.ogc.xml.FilterXmlFactory;
import org.geotoolkit.ogc.xml.SortBy;
import org.geotoolkit.ogc.xml.XMLFilter;
import org.geotoolkit.ows.xml.AcceptFormats;
import org.geotoolkit.ows.xml.AcceptVersions;
import org.geotoolkit.ows.xml.ExceptionResponse;
import org.geotoolkit.ows.xml.RequestBase;
import org.geotoolkit.ows.xml.Sections;
import org.geotoolkit.ows.xml.v100.SectionsType;
import org.geotoolkit.wfs.xml.AllSomeType;
import org.geotoolkit.wfs.xml.BaseRequest;
import org.geotoolkit.wfs.xml.CreateStoredQuery;
import org.geotoolkit.wfs.xml.DeleteElement;
import org.geotoolkit.wfs.xml.DescribeFeatureType;
import org.geotoolkit.wfs.xml.DescribeStoredQueries;
import org.geotoolkit.wfs.xml.DropStoredQuery;
import org.geotoolkit.wfs.xml.GetCapabilities;
import org.geotoolkit.wfs.xml.GetFeature;
import org.geotoolkit.wfs.xml.GetGmlObject;
import org.geotoolkit.wfs.xml.GetPropertyValue;
import org.geotoolkit.wfs.xml.ListStoredQueries;
import org.geotoolkit.wfs.xml.LockFeature;
import org.geotoolkit.wfs.xml.Parameter;
import org.geotoolkit.wfs.xml.ParameterExpression;
import org.geotoolkit.wfs.xml.Query;
import org.geotoolkit.wfs.xml.ResultTypeType;
import org.geotoolkit.wfs.xml.StoredQuery;
import org.geotoolkit.wfs.xml.Transaction;
import org.opengis.filter.sort.SortOrder;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.logging.Level;

import static org.constellation.api.QueryConstants.ACCEPT_FORMATS_PARAMETER;
import static org.constellation.api.QueryConstants.ACCEPT_VERSIONS_PARAMETER;
import static org.constellation.api.QueryConstants.REQUEST_PARAMETER;
import static org.constellation.api.QueryConstants.SECTIONS_PARAMETER;
import static org.constellation.api.QueryConstants.SERVICE_PARAMETER;
import static org.constellation.api.QueryConstants.UPDATESEQUENCE_PARAMETER;
import static org.constellation.api.QueryConstants.VERSION_PARAMETER;
import static org.constellation.wfs.core.WFSConstants.FILTER;
import static org.constellation.wfs.core.WFSConstants.GML_3_1_1_MIME;
import static org.constellation.wfs.core.WFSConstants.GML_3_2_1_MIME;
import org.constellation.wfs.core.WFSConstants.GetXSD;
import static org.constellation.wfs.core.WFSConstants.HANDLE;
import static org.constellation.wfs.core.WFSConstants.NAMESPACE;
import static org.constellation.wfs.core.WFSConstants.STR_CREATE_STORED_QUERY;
import static org.constellation.wfs.core.WFSConstants.STR_DESCRIBEFEATURETYPE;
import static org.constellation.wfs.core.WFSConstants.STR_DESCRIBE_STORED_QUERIES;
import static org.constellation.wfs.core.WFSConstants.STR_DROP_STORED_QUERY;
import static org.constellation.wfs.core.WFSConstants.STR_GETCAPABILITIES;
import static org.constellation.wfs.core.WFSConstants.STR_GETFEATURE;
import static org.constellation.wfs.core.WFSConstants.STR_GETGMLOBJECT;
import static org.constellation.wfs.core.WFSConstants.STR_GET_PROPERTY_VALUE;
import static org.constellation.wfs.core.WFSConstants.STR_LIST_STORED_QUERIES;
import static org.constellation.wfs.core.WFSConstants.STR_LOCKFEATURE;
import static org.constellation.wfs.core.WFSConstants.STR_TRANSACTION;
import static org.constellation.wfs.core.WFSConstants.STR_XSD;
import org.constellation.ws.rs.ResponseObject;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.MISSING_PARAMETER_VALUE;
import org.geotoolkit.wfs.xml.InsertElement;
import org.geotoolkit.wfs.xml.Property;
import org.geotoolkit.wfs.xml.ReplaceElement;
import org.geotoolkit.wfs.xml.StoredQueryDescription;
import org.geotoolkit.wfs.xml.UpdateElement;
import org.geotoolkit.wfs.xml.WFSXmlFactory;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildAcceptFormat;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildAcceptVersion;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildBBOXFilter;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildDecribeFeatureType;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildDeleteElement;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildDescribeStoredQueries;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildDropStoredQuery;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildGetCapabilities;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildGetFeature;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildGetGmlObject;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildGetPropertyValue;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildListStoredQueries;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildLockFeature;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildParameter;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildQuery;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildSections;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildSortBy;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildStoredQuery;
import static org.geotoolkit.wfs.xml.WFSXmlFactory.buildTransaction;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.w3c.dom.Node;

// JAXB dependencies
// jersey dependencies
// constellation dependencies
// Geotoolkit dependencies


/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@Controller
@RequestMapping("wfs/{serviceId:.+}")
public class WFSService extends GridWebService<WFSWorker> {

    static {
        System.setProperty("javax.xml.stream.XmlInputFactory", "com.ctc.wstx.stax.WstxInputFactory");
        System.setProperty("javax.xml.stream.XmlEventFactory", "com.ctc.wstx.stax.WstxEventFactory");
        System.setProperty("javax.xml.stream.XmlOutputFactory", "com.ctc.wstx.stax.WstxOutputFactory");
    }

    /**
     * Build a new Restful WFS service.
     */
    public WFSService() {
        super(Specification.WFS);
        try {
            final MarshallerPool pool = new MarshallerPool(JAXBContext.newInstance(
                           "org.geotoolkit.wfs.xml.v110"   +
            		  ":org.geotoolkit.ogc.xml.v110"  +
                          ":org.geotoolkit.wfs.xml.v200"  +
            		  ":org.geotoolkit.gml.xml.v311"  +
                          ":org.geotoolkit.gml.xml.v321"  +
                          ":org.geotoolkit.xsd.xml.v2001" +
                          ":org.apache.sis.internal.jaxb.geometry"), null);
            setXMLContext(pool);
            LOGGER.log(Level.INFO, "WFS REST service running");

        } catch (JAXBException ex){
            LOGGER.warning("The WFS REST service is not running.\ncause  : Error creating XML context.\n error  : " + ex.getMessage()  +
                           "\n details: " + ex.toString());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseObject treatIncomingRequest(final Object objectRequest, final WFSWorker worker) {

        ServiceDef version    = null;

        try {

            // if the request is not an xml request we fill the request parameter.
            final RequestBase request;
            if (objectRequest == null) {
                version = worker.getVersionFromNumber(getParameter(VERSION_PARAMETER, false)); // needed if exception is launch before request build
                request = adaptQuery(getParameter(REQUEST_PARAMETER, true), worker);
            } else if (objectRequest instanceof RequestBase) {
                request = (RequestBase) objectRequest;
            } else {
                throw new CstlServiceException("The operation " + objectRequest.getClass().getName() + " is not supported by the service",
                        INVALID_PARAMETER_VALUE, "request");
            }
            version = worker.getVersionFromNumber(request.getVersion());

            if (request instanceof GetCapabilities) {
                final GetCapabilities model = (GetCapabilities) request;
                String outputFormat = model.getFirstAcceptFormat();
                if (outputFormat == null) {
                    outputFormat = "application/xml";
                }
                return new ResponseObject(worker.getCapabilities(model), outputFormat);

            } else if (request instanceof DescribeFeatureType) {
                final DescribeFeatureType model = (DescribeFeatureType) request;
                String requestOutputFormat = model.getOutputFormat();
                final String outputFormat;
                if (requestOutputFormat == null || requestOutputFormat.equals("text/xml; subtype=\"gml/3.1.1\"")) {
                    outputFormat = GML_3_1_1_MIME;
                } else if (requestOutputFormat.equals("text/xml; subtype=\"gml/3.2.1\"") || requestOutputFormat.equals("text/xml; subtype=\"gml/3.2\"")||
                           requestOutputFormat.equals("application/gml+xml; version=3.2")) {
                    outputFormat = GML_3_2_1_MIME;
                } else {
                    outputFormat = requestOutputFormat;
                }
                LOGGER.log(Level.INFO, "outputFormat asked:{0}", requestOutputFormat);

                return new ResponseObject(worker.describeFeatureType(model), outputFormat);

            } else if (request instanceof GetFeature) {
                final GetFeature model = (GetFeature) request;
                String requestOutputFormat = model.getOutputFormat();
                final String outputFormat;
                if (requestOutputFormat == null || requestOutputFormat.equals("text/xml; subtype=\"gml/3.1.1\"")) {
                    outputFormat = GML_3_1_1_MIME;
                } else if (requestOutputFormat.equals("text/xml; subtype=\"gml/3.2.1\"") || requestOutputFormat.equals("text/xml; subtype=\"gml/3.2\"") ||
                           requestOutputFormat.equals("application/gml+xml; version=3.2")) {
                    outputFormat = GML_3_2_1_MIME;
                } else {
                    outputFormat = requestOutputFormat;
                }
                final Object response = worker.getFeature(model);
                return new ResponseObject(response, outputFormat);

            } else if (request instanceof GetPropertyValue) {
                final GetPropertyValue model = (GetPropertyValue) request;
                String requestOutputFormat = model.getOutputFormat();
                final String outputFormat;
                if (requestOutputFormat == null || requestOutputFormat.equals("text/xml; subtype=\"gml/3.1.1\"")) {
                    outputFormat = GML_3_1_1_MIME;
                } else if (requestOutputFormat.equals("text/xml; subtype=\"gml/3.2.1\"") || requestOutputFormat.equals("text/xml; subtype=\"gml/3.2\"") ||
                           requestOutputFormat.equals("application/gml+xml; version=3.2")) {
                    outputFormat = GML_3_2_1_MIME;
                } else {
                    outputFormat = requestOutputFormat;
                }
                final Object response = worker.getPropertyValue(model);
                return new ResponseObject(response, outputFormat);

            } else if (request instanceof CreateStoredQuery) {
                final CreateStoredQuery model = (CreateStoredQuery) request;
                final WFSResponseWrapper response = new WFSResponseWrapper(worker.createStoredQuery(model),version.version.toString());
                return new ResponseObject(response, MediaType.TEXT_XML);

            } else if (request instanceof DropStoredQuery) {
                final DropStoredQuery model = (DropStoredQuery) request;
                final WFSResponseWrapper response = new WFSResponseWrapper(worker.dropStoredQuery(model),version.version.toString());
                return new ResponseObject(response, MediaType.TEXT_XML);

            } else if (request instanceof ListStoredQueries) {
                final ListStoredQueries model = (ListStoredQueries) request;
                final WFSResponseWrapper response = new WFSResponseWrapper(worker.listStoredQueries(model),version.version.toString());
                return new ResponseObject(response, MediaType.TEXT_XML);

            } else if (request instanceof DescribeStoredQueries) {
                final DescribeStoredQueries model = (DescribeStoredQueries) request;
                final WFSResponseWrapper response = new WFSResponseWrapper(worker.describeStoredQueries(model),version.version.toString());
                return new ResponseObject(response, MediaType.TEXT_XML);

            } else if (request instanceof GetGmlObject) {
                final GetGmlObject model = (GetGmlObject) request;
                final WFSResponseWrapper response = new WFSResponseWrapper(worker.getGMLObject(model),version.version.toString());
                return new ResponseObject(response, MediaType.TEXT_XML);

            } else if (request instanceof LockFeature) {
                final LockFeature model = (LockFeature) request;
                return new ResponseObject(worker.lockFeature(model), MediaType.TEXT_XML);

            } else if (request instanceof Transaction) {
                final Transaction model = (Transaction) request;
                return new ResponseObject(worker.transaction(model), MediaType.TEXT_XML);

            }  else if (request instanceof GetXSD) {
                final GetXSD model = (GetXSD) request;
                return new ResponseObject(worker.getXsd(model), MediaType.TEXT_XML);
            }

            throw new CstlServiceException("The operation " + request.getClass().getName() + " is not supported by the service",
                                          INVALID_PARAMETER_VALUE, "request");

        } catch (CstlServiceException ex) {
            return processExceptionResponse(ex, version, worker);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ResponseObject processExceptionResponse(final CstlServiceException ex, ServiceDef serviceDef, final Worker worker) {
         // asking for authentication
        if (ex instanceof UnauthorizedException) {
            Map<String, String> headers = new HashMap<>();
            headers.put("WWW-Authenticate", " Basic");
            return new ResponseObject(HttpStatus.UNAUTHORIZED, headers);
        }
        logException(ex);

        if (serviceDef == null) {
            serviceDef = worker.getBestVersion(null);
        }
        final String version         = serviceDef.exceptionVersion.toString();
        final String exceptionCode   = getOWSExceptionCodeRepresentation(ex.getExceptionCode());
        final ExceptionResponse report;
        if (serviceDef.exceptionVersion.toString().equals("1.0.0")) {
            report = new org.geotoolkit.ows.xml.v100.ExceptionReport(ex.getMessage(), exceptionCode, ex.getLocator(), version);
            return new ResponseObject(report, "text/xml");
        } else {
            report = new org.geotoolkit.ows.xml.v110.ExceptionReport(ex.getMessage(), exceptionCode, ex.getLocator(), version);
            final int port = getHttpCodeFromErrorCode(exceptionCode);
            return new ResponseObject(report, "text/xml", port);
        }
    }

    private int getHttpCodeFromErrorCode(final String exceptionCode) {
        if (null ==exceptionCode) {
            return 200;
        } else switch (exceptionCode) {
            case "CannotLockAllFeatures":
            case "FeaturesNotLocked":
            case "InvalidLockId":
            case "InvalidValue":
            case "OperationParsingFailed":
            case "OperationNotSupported":
            case "MissingParameterValue":
            case "InvalidParameterValue":
            case "VersionNegotiationFailed":
            case "InvalidUpdateSequence":
            case "OptionNotSupported":
            case "NoApplicableCode":
                return 400;
            case "DuplicateStoredQueryIdValue":
            case "DuplicateStoredQueryParameterName":
                return 409;
            case "LockHasExpired":
            case "OperationProcessingFailed":
                return 403;
            default:
                return 200;
        }
    }


    /**
     * Override the parent method in order to extract namespace mapping
     * during the unmarshall.
     *
     * @param unmarshaller
     * @param is
     * @return
     * @throws JAXBException
     */
    @Override
    protected Object unmarshallRequest(final Unmarshaller unmarshaller, final InputStream is) throws JAXBException {
        final Map<String, String> prefixMapping = new LinkedHashMap<>();
        return unmarshallRequestWithMapping(unmarshaller, is, prefixMapping);
    }

    @Override
    protected Object unmarshallRequestWithMapping(final Unmarshaller unmarshaller, final InputStream is, final Map<String, String> prefixMapping) throws JAXBException {
        final JAXBEventHandler handler          = new JAXBEventHandler();
         unmarshaller.setEventHandler(handler);
        try {
            final XMLEventReader rootEventReader    = XMLInputFactory.newInstance().createXMLEventReader(is);
            final XMLEventReader eventReader        = (XMLEventReader) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{XMLEventReader.class}, new PrefixMappingInvocationHandler(rootEventReader, prefixMapping));

            Object request =  unmarshaller.unmarshal(eventReader);
            if (request instanceof JAXBElement) {
                request = ((JAXBElement)request).getValue();
            }
            if (request instanceof BaseRequest) {
                ((BaseRequest)request).setPrefixMapping(prefixMapping);
            }
            return request;
        } catch (XMLStreamException ex) {
            throw new JAXBException(ex);
        }
    }

    private RequestBase adaptQuery(final String request, final WFSWorker worker) throws CstlServiceException {
        if (STR_GETCAPABILITIES.equalsIgnoreCase(request)) {
            return createNewGetCapabilitiesRequest(worker);
        } else if (STR_DESCRIBEFEATURETYPE.equalsIgnoreCase(request)) {
            return createNewDescribeFeatureTypeRequest(worker);
        } else if (STR_GETFEATURE.equalsIgnoreCase(request)) {
            return createNewGetFeatureRequest(worker);
        } else if (STR_GETGMLOBJECT.equalsIgnoreCase(request)) {
            return createNewGetGmlObjectRequest(worker);
        } else if (STR_LOCKFEATURE.equalsIgnoreCase(request)) {
            return createNewLockFeatureRequest(worker);
        } else if (STR_TRANSACTION.equalsIgnoreCase(request)) {
            return createNewTransactionRequest(worker);
        } else if (STR_DESCRIBE_STORED_QUERIES.equalsIgnoreCase(request)) {
            return createNewDescribeStoredQueriesRequest(worker);
        } else if (STR_LIST_STORED_QUERIES.equalsIgnoreCase(request)) {
            return createNewListStoredQueriesRequest(worker);
        } else if (STR_GET_PROPERTY_VALUE.equalsIgnoreCase(request)) {
            return createNewGetPropertyValueRequest(worker);
        } else if (STR_CREATE_STORED_QUERY.equalsIgnoreCase(request)) {
            return createNewCreateStoredQueryRequest();
        } else if (STR_DROP_STORED_QUERY.equalsIgnoreCase(request)) {
            return createNewDropStoredQueryRequest(worker);
        } else if (STR_XSD.equalsIgnoreCase(request)) {
            return createNewXsdRequest(worker);
        }
        throw new CstlServiceException("The operation " + request + " is not supported by the service",
                        INVALID_PARAMETER_VALUE, "request");
    }

    private DescribeFeatureType createNewDescribeFeatureTypeRequest(final Worker w) throws CstlServiceException {
        String outputFormat   = getParameter("outputFormat", false);
        final String handle   = getParameter(HANDLE, false);
        final String service  = getParameter(SERVICE_PARAMETER, true);
        final String version  = getParameter(VERSION_PARAMETER, true);
        w.checkVersionSupported(version, false);

        final Map<String, String> mapping = extractNamespace(version);

        String typeName = getParameter("typeName", false);
        if (typeName == null) {
            // there is a confusion in WFS 2.0.0 standards
            typeName = getParameter("typeNames", false);
        }
        final List<QName> typeNames = extractTypeName(typeName, mapping);

        return buildDecribeFeatureType(version, service, handle, typeNames, outputFormat);
    }

    private GetCapabilities createNewGetCapabilitiesRequest(final Worker w) throws CstlServiceException {
        String version = getParameter(ACCEPT_VERSIONS_PARAMETER, false);

        String currentVersion = getParameter(VERSION_PARAMETER, false);
        if (currentVersion == null) {
            currentVersion = w.getBestVersion(null).version.toString();
        }
        w.checkVersionSupported(currentVersion, true);

        final List<String> versions = new ArrayList<>();
        if (version != null) {
            String[] vArray = version.split(",");
            versions.addAll(Arrays.asList(vArray));
        } else {
            versions.add(currentVersion);
        }
        final AcceptVersions acceptVersions = buildAcceptVersion(currentVersion, versions);

        final String updateSequence = getParameter(UPDATESEQUENCE_PARAMETER, false);

        final AcceptFormats formats = buildAcceptFormat(currentVersion, Arrays.asList(getParameter(ACCEPT_FORMATS_PARAMETER, false)));

        //We transform the String of sections in a list.
        //In the same time we verify that the requested sections are valid.
        final Sections sections;
        final String section = getParameter(SECTIONS_PARAMETER, false);
        if (section != null && !section.equalsIgnoreCase("All")) {
            final List<String> requestedSections = new ArrayList<>();
            final StringTokenizer tokens = new StringTokenizer(section, ",;");
            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken().trim();
                if (SectionsType.getExistingSections().contains(token)){
                    requestedSections.add(token);
                } else {
                    throw new CstlServiceException("The section " + token + " does not exist",
                                                  INVALID_PARAMETER_VALUE, "Sections");
                }
            }
            sections = buildSections(currentVersion, requestedSections);
        } else {
            sections = null;

        }

        return buildGetCapabilities(currentVersion,
                                    acceptVersions,
                                    sections,
                                    formats,
                                    updateSequence,
                                    getParameter(SERVICE_PARAMETER, true));

    }

    private GetFeature createNewGetFeatureRequest(final WFSWorker worker) throws CstlServiceException {
        Integer maxFeature = null;

        final String service = getParameter(SERVICE_PARAMETER, true);
        final String version = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle  = getParameter(HANDLE,  false);
        final String outputFormat  = getParameter("outputFormat", false);

        final String max;
        if (version.equals("2.0.0")) {
            max = getParameter("count", false);
        } else {
            max = getParameter("maxfeatures", false);
        }
        if (max != null) {
            try {
                maxFeature = Integer.parseInt(max);
            } catch (NumberFormatException ex) {
                throw new CstlServiceException("Unable to parse the integer maxfeatures parameter" + max,
                                                  INVALID_PARAMETER_VALUE, "MaxFeatures");
            }
        }

        final Integer startIndex = parseOptionalIntegerParam("StartIndex");

        final Map<String, String> mapping = extractNamespace(version);

        final String result = getParameter("resultType", false);
        ResultTypeType resultType = null;
        if (result != null) {
            resultType = ResultTypeType.fromValue(result.toLowerCase());
        }

        final String featureVersion = getParameter("featureVersion", false);

        List<String> featureId;
        if (version.equals("2.0.0")) {
            featureId = parseCommaSeparatedParameter("resourceid");
        } else {
            featureId = parseCommaSeparatedParameter("featureid");
        }
        boolean mandatory = true;
        if (!featureId.isEmpty()) {
            mandatory = false;
        }

        final String storedQuery = getParameter("storedquery_id", false);
        final List<Parameter> parameters;
        if (storedQuery != null) {
            mandatory = false;
            // extract stored query params
            parameters = extractParameters(worker, storedQuery, version, mapping);
        } else {
            parameters = new ArrayList<>();
        }
        final String typeName;
        if (version.equals("2.0.0")) {
            typeName = getParameter("typeNames", mandatory);
        } else {
            typeName = getParameter("typeName", mandatory);
        }
        final List<QName> typeNames = extractTypeName(typeName, mapping);

        final String srsName = getParameter("srsName", false);
        final SortBy sortBy = parseSortByParameter(version);

        final List<String> propertyNames = parseCommaSeparatedParameter("propertyName");

        if (!featureId.isEmpty()) {
            final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, featureId);
            final Query query = buildQuery(version, filter, typeNames, featureVersion, srsName, sortBy, propertyNames);
            return buildGetFeature(version, service, handle, startIndex, maxFeature, query, resultType, outputFormat);
        } else if (storedQuery != null) {
            final StoredQuery query = buildStoredQuery(version, storedQuery, handle, parameters);
            return buildGetFeature(version, service, handle, startIndex, maxFeature, query, resultType, outputFormat);
        }

        final Object xmlFilter  = getComplexParameter(FILTER, false);

        XMLFilter filter;
        final Map<String, String> prefixMapping;
        if (xmlFilter instanceof XMLFilter) {
            filter = (XMLFilter) xmlFilter;
            prefixMapping = filter.getPrefixMapping();
        } else {
            filter = null;
            prefixMapping = new HashMap<>();
        }

        final String bbox = getParameter("bbox", false);
        if (bbox != null) {
            final double[] coodinates = new double[4];

            final StringTokenizer tokens = new StringTokenizer(bbox, ",;");
            int index = 0;
            while (tokens.hasMoreTokens() && index < 4) {
                final double value = RequestsUtilities.toDouble(tokens.nextToken());
                coodinates[index] = value;
                index++;
            }
            String crs = null;
            if (tokens.hasMoreTokens()) {
                crs = tokens.nextToken();
            }

            if (coodinates != null) {
                if (filter == null) {
                    filter = buildBBOXFilter(version, "", coodinates[0], coodinates[1], coodinates[2], coodinates[3], crs);
                } else {
                    LOGGER.info("unexpected case --> filter + bbox TODO");
                }
            }
        }

        try {
            final Query query   = buildQuery(version, filter, typeNames, featureVersion, srsName, sortBy, propertyNames);
            final GetFeature gf = buildGetFeature(version, service, handle, startIndex, maxFeature, query, resultType, outputFormat);
            gf.setPrefixMapping(prefixMapping);
            return gf;
        } catch (IllegalArgumentException ex) {
            throw new CstlServiceException(ex);
        }
    }

    private Map<String,String> extractNamespace(String version) throws CstlServiceException {
        if ("2.0.0".equals(version)) {
            String namespace = getParameter("namespaces", false);
            return WebServiceUtilities.extractNamespace(namespace, ',');
        } else {
            String namespace = getParameter(NAMESPACE, false);
            return WebServiceUtilities.extractNamespace(namespace, '=');
        }
    }

    private List<Parameter> extractParameters(WFSWorker worker, final String storedQuery, final String version, Map<String,String> mapping) {
        final List<Parameter> parameters = new ArrayList<>();

        // extract stored query params
        final List<ParameterExpression> params = worker.getParameterForStoredQuery(storedQuery);
        for (ParameterExpression param : params) {
            final String paramValue = getSafeParameter(param.getName());
            if (paramValue != null) {
                // TODO handle different type
                final Object obj;
                if (param.getType().getLocalPart().equals("QName")) {
                    final int separator = paramValue.indexOf(':');
                    if (separator != -1) {
                        final String prefix    = paramValue.substring(0, separator);
                        final String namespac  = mapping.get(prefix);
                        final String localPart = paramValue.substring(separator + 1);
                        obj = new QName(namespac, localPart);
                    } else {
                        obj = new QName(paramValue);
                    }
                } else {
                    obj = paramValue;
                }
                parameters.add(buildParameter(version, param.getName(), obj));
            }
        }
        return parameters;
    }

    private SortBy parseSortByParameter(String version) {
        // TODO handle multiple properties and handle prefixed properties
        String sortByParam = getSafeParameter("sortBy");
        final SortBy sortBy;
        if (sortByParam != null) {
            if (sortByParam.indexOf(':') != -1) {
                sortByParam = sortByParam.substring(sortByParam.indexOf(':') + 1);
            }
            //we get the order
            final SortOrder order;
            if (sortByParam.indexOf(' ') != -1) {
                final char cOrder = sortByParam.charAt(sortByParam.length() -1);
                sortByParam = sortByParam.substring(0, sortByParam.indexOf(' '));
                if (cOrder == 'D') {
                    order = SortOrder.DESCENDING;
                } else {
                    order = SortOrder.ASCENDING;
                }
            } else {
                order = SortOrder.ASCENDING;
            }
            sortBy =  buildSortBy(version, sortByParam, order);
        } else {
            sortBy = null;
        }
        return sortBy;
    }

    private Integer parseOptionalIntegerParam(String paramName) throws CstlServiceException {
        Integer result = null;
        final String max = getParameter(paramName, false);
        if (max != null) {
            try {
                result = Integer.parseInt(max);
            } catch (NumberFormatException ex) {
                throw new CstlServiceException("Unable to parse the integer " + paramName + " parameter" + max,
                                                  INVALID_PARAMETER_VALUE, paramName);
            }

        }
        return result;
    }

    private List<String> parseCommaSeparatedParameter(String paramName) throws CstlServiceException {
        final String propertyNameParam = getParameter(paramName, false);
        final List<String> results = new ArrayList<>();
        if (propertyNameParam != null) {
            final StringTokenizer tokens = new StringTokenizer(propertyNameParam, ",;");
            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken().trim();
                results.add(token);
            }
        }
        return results;
    }

    private GetPropertyValue createNewGetPropertyValueRequest(final Worker worker) throws CstlServiceException {
        final Integer maxFeature = parseOptionalIntegerParam("maxfeatures");
        final Integer startIndex = parseOptionalIntegerParam("StartIndex");
        final String service = getParameter(SERVICE_PARAMETER, true);
        final String version = getParameter(VERSION_PARAMETER, true);
        final String handle  = getParameter(HANDLE,  false);
        final String outputFormat  = getParameter("outputFormat", false);

        worker.checkVersionSupported(version, false);

        final Map<String, String> mapping = extractNamespace(version);

        final String result = getParameter("resultType", false);
        ResultTypeType resultType = null;
        if (result != null) {
            resultType = ResultTypeType.fromValue(result.toLowerCase());
        }

        final String valueReference = getParameter("valueReference", true);

        final String featureVersion = getParameter("featureVersion", false);

        String featureId;
        if (version.equals("2.0.0")) {
            featureId = getParameter("ressourceid", false);
        } else {
            featureId = getParameter("featureid", false);
        }
        boolean mandatory = true;
        if (featureId != null) {
            //cite test fix
            if (featureId.endsWith(",")) {
                featureId = featureId.substring(0, featureId.length() - 1);
            }
            mandatory = false;
        }

        final String typeName;
        if (version.equals("2.0.0")) {
            typeName = getParameter("typeNames", mandatory);
        } else {
            typeName = getParameter("typeName", mandatory);
        }
        final List<QName> typeNames = extractTypeName(typeName, mapping);

        final String srsName = getParameter("srsName", false);

        // TODO handle multiple properties and handle prefixed properties
        String sortByParam = getParameter("sortBy", false);
        final SortBy sortBy;
        if (sortByParam != null) {
            if (sortByParam.indexOf(':') != -1) {
                sortByParam = sortByParam.substring(sortByParam.indexOf(':') + 1);
            }
            //we get the order
            final SortOrder order;
            if (sortByParam.indexOf(' ') != -1) {
                final char cOrder = sortByParam.charAt(sortByParam.length() -1);
                sortByParam = sortByParam.substring(0, sortByParam.indexOf(' '));
                if (cOrder == 'D') {
                    order = SortOrder.DESCENDING;
                } else {
                    order = SortOrder.ASCENDING;
                }
            } else {
                order = SortOrder.ASCENDING;
            }
            sortBy =  buildSortBy(version, sortByParam, order);
        } else {
            sortBy = null;
        }

        final String propertyNameParam = getParameter("propertyName", false);
        final List<String> propertyNames = new ArrayList<>();
        if (propertyNameParam != null && !propertyNameParam.isEmpty()) {
            final StringTokenizer tokens = new StringTokenizer(propertyNameParam, ",;");
            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken().trim();
                propertyNames.add(token);
            }
        }

        if (featureId != null) {
            final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, featureId);
            final Query query = buildQuery(version, filter, typeNames, featureVersion, srsName, sortBy, propertyNames);
            return buildGetPropertyValue(version, service, handle, startIndex, maxFeature, query, resultType, outputFormat, valueReference);
        }

        final Object xmlFilter  = getComplexParameter(FILTER, false);

        XMLFilter filter;
        final Map<String, String> prefixMapping;
        if (xmlFilter instanceof XMLFilter) {
            filter = (XMLFilter) xmlFilter;
            prefixMapping = filter.getPrefixMapping();
        } else {
            filter = null;
            prefixMapping = new HashMap<>();
        }

        final String bbox = getParameter("bbox", false);
        if (bbox != null) {
            final double[] coodinates = new double[4];

            final StringTokenizer tokens = new StringTokenizer(bbox, ",;");
            int index = 0;
            while (tokens.hasMoreTokens() && index < 4) {
                final double value = RequestsUtilities.toDouble(tokens.nextToken());
                coodinates[index] = value;
                index++;
            }
            String crs = null;
            if (tokens.hasMoreTokens()) {
                crs = tokens.nextToken();
            }

            if (coodinates != null) {
                if (filter == null) {
                    filter = buildBBOXFilter(version, "", coodinates[0], coodinates[1], coodinates[2], coodinates[3], crs);
                } else {
                    LOGGER.info("unexpected case --> filter + bbox TODO");
                }
            }
        }

        final Query query   = buildQuery(version, filter, typeNames, featureVersion, srsName, sortBy, propertyNames);
        final GetPropertyValue gf = buildGetPropertyValue(version, service, handle, startIndex, maxFeature, query, resultType, outputFormat, valueReference);
        gf.setPrefixMapping(prefixMapping);
        return gf;
    }

    private GetGmlObject createNewGetGmlObjectRequest(final Worker worker) throws CstlServiceException {
        final String service      = getParameter(SERVICE_PARAMETER, true);
        final String version      = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle       = getParameter(HANDLE,  false);
        final String outputFormat = getParameter("outputFormat", false);
        final String id           = getParameter("gmlobjectid", true);

        return buildGetGmlObject(version, id, service, handle, outputFormat);
    }

    private LockFeature createNewLockFeatureRequest(final Worker worker) throws CstlServiceException {
        final String service  = getParameter(SERVICE_PARAMETER, true);
        final String version  = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle   = getParameter(HANDLE,  false);

        final String lockAct  = getParameter("lockAction",  false);
        AllSomeType lockAction = null;
        if (lockAct != null) {
            lockAction = AllSomeType.fromValue(lockAct);
        }
        final String exp   = getParameter("expiry",  false);
        Integer expiry     = null;
        if (exp != null) {
            try {
                expiry = Integer.parseInt(exp);
            } catch (NumberFormatException ex) {
                throw new CstlServiceException("The service was to parse the expiry value :" + exp,
                                                  INVALID_PARAMETER_VALUE, "expiry");
            }
        }

        final Map<String, String> mapping = extractNamespace(version);

        final String typeName       = getParameter("typeName", true);
        final List<QName> typeNames = extractTypeName(typeName, mapping);

        final Object xmlFilter  = getComplexParameter(FILTER, false);
        final XMLFilter filter;
        final Map<String, String> prefixMapping;
        if (xmlFilter instanceof XMLFilter) {
            filter = (XMLFilter) xmlFilter;
            prefixMapping = filter.getPrefixMapping();
        } else {
            filter = null;
            prefixMapping = new HashMap<>();
        }

        // TODO
        final QName typeNamee = typeNames.get(0);

        final LockFeature lf = buildLockFeature(version, service, handle, lockAction, filter, typeNamee, expiry);
        lf.setPrefixMapping(prefixMapping);
        return lf;
    }

    private Transaction createNewTransactionRequest(final Worker worker) throws CstlServiceException {
        final String service      = getParameter(SERVICE_PARAMETER, true);
        final String version      = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle       = getParameter(HANDLE,  false);
        final String relAct       = getParameter("releaseAction",  false);
        AllSomeType releaseAction = null;
        if (relAct != null) {
            releaseAction = AllSomeType.fromValue(relAct);
        }

        final Map<String, String> mapping = extractNamespace(version);

        final String typeName       = getParameter("typeName", true);
        final List<QName> typeNames = extractTypeName(typeName, mapping);

        final Object xmlFilter  = getComplexParameter(FILTER, false);
        final XMLFilter filter;
        final Map<String, String> prefixMapping;
        if (xmlFilter instanceof XMLFilter) {
            filter = (XMLFilter) xmlFilter;
            prefixMapping = filter.getPrefixMapping();
        } else {
            filter = null;
            prefixMapping = new HashMap<>();
        }

        // TODO
        final QName typeNamee = typeNames.get(0);
        final DeleteElement delete = buildDeleteElement(version, filter, handle, typeNamee);
        final Transaction t = buildTransaction(version, service, handle, releaseAction, delete);
        t.setPrefixMapping(prefixMapping);
        return t;
     }

    /**
     * Extract proper QName from a String list of typeName.
     * @param typeName A String with the pattern: ns1:type1,ns1:type2,ns2:type3
     * @param mapping A Map of  @{<prefix, namespace>}
     *
     * @return A list of QName.
     * @throws CstlServiceException if the pattern of the typeName parameter if wrong,
     *                              or if a prefix is not bounded to a namespace in the mapping map.
     */
    private List<QName> extractTypeName(final String typeName, final Map<String, String> mapping) throws CstlServiceException {
        final List<QName> typeNames = new ArrayList<>();
        if (typeName != null) {
            final StringTokenizer tokens = new StringTokenizer(typeName, ",;");
            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken().trim();
                if (token.indexOf(':') != -1) {
                    final String prefix    = token.substring(0, token.indexOf(':'));
                    final String localPart = token.substring(token.indexOf(':') + 1);
                    final String namesp    = mapping.get(prefix);
                    if (namesp != null) {
                        typeNames.add(new QName(namesp, localPart, prefix));
                    } else {
                        typeNames.add(new QName(prefix, localPart));
                        /*throw new CstlServiceException("The typeName parameter is malformed : the prefix [" + prefix + "] is not bounded with a namespace",
                                                  INVALID_PARAMETER_VALUE, "typeName");*/
                    }
                } else {
                    typeNames.add(new QName(token));
                    /*throw new CstlServiceException("The typeName parameter is malformed : [" + token + "] the good pattern is ns1:feature",
                                                  INVALID_PARAMETER_VALUE, "typeName");*/
                }
            }
        }
        return typeNames;
    }

    /**
     * {@inheritDoc}
     * overriden for extract namespace mapping.
     */
    @Override
    protected Object getComplexParameter(final String parameterName, final boolean mandatory) throws CstlServiceException {
        try {
            final Map<String, String[]> parameters = getHttpServletRequest().getParameterMap();
            String[] list = parameters.get(parameterName);
            if (list == null) {
                for(final String key : parameters.keySet()){
                    if(parameterName.equalsIgnoreCase(key)){
                        list = parameters.get(key);
                        break;
                    }
                }

                if (list == null) {
                    if (!mandatory) {
                        return null;
                    } else {
                        throw new CstlServiceException("The parameter " + parameterName + " must be specified",
                                       MISSING_PARAMETER_VALUE);
                    }
                }
            }
            final StringReader sr                   = new StringReader(list[0]);
            final Map<String, String> prefixMapping = new LinkedHashMap<>();
            final XMLEventReader rootEventReader    = XMLInputFactory.newInstance().createXMLEventReader(sr);
            final XMLEventReader eventReader        = (XMLEventReader) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{XMLEventReader.class}, new PrefixMappingInvocationHandler(rootEventReader, prefixMapping));
            final Unmarshaller unmarshaller = getMarshallerPool().acquireUnmarshaller();
            Object result = unmarshaller.unmarshal(eventReader);
            getMarshallerPool().recycle(unmarshaller);
            if (result instanceof JAXBElement) {
                result = ((JAXBElement)result).getValue();
            }
            if (result instanceof XMLFilter) {
                ((XMLFilter)result).setPrefixMapping(prefixMapping);
            }
            return result;
        } catch (JAXBException | XMLStreamException ex) {
             throw new CstlServiceException("The xml object for parameter " + parameterName + " is not well formed:" + '\n' +
                            ex, INVALID_PARAMETER_VALUE);
        }
    }

    private DescribeStoredQueries createNewDescribeStoredQueriesRequest(final Worker worker) throws CstlServiceException {
        final String service      = getParameter(SERVICE_PARAMETER, true);
        final String version      = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle       = getParameter(HANDLE,  false);

        final String storedQueryIdParam = getParameter("StoredQueryId", false);
        final List<String> storedQueryId = new ArrayList<>();
        if (storedQueryIdParam != null) {
            final StringTokenizer tokens = new StringTokenizer(storedQueryIdParam, ",;");
            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken().trim();
                storedQueryId.add(token);
            }
        }
        return buildDescribeStoredQueries(version, service, handle, storedQueryId);
    }

    private ListStoredQueries createNewListStoredQueriesRequest(final Worker worker) throws CstlServiceException {
        final String service      = getParameter(SERVICE_PARAMETER, true);
        final String version      = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle       = getParameter(HANDLE,  false);

        return buildListStoredQueries(version, service, handle);
    }

    private CreateStoredQuery createNewCreateStoredQueryRequest() throws CstlServiceException {
        throw new CstlServiceException("KVP encoding is not allowed for CreateStoredQuery request");
    }

    private DropStoredQuery createNewDropStoredQueryRequest(final Worker worker) throws CstlServiceException {
        final String service      = getParameter(SERVICE_PARAMETER, true);
        final String version      = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);
        final String handle       = getParameter(HANDLE,  false);
        final String id           = getParameter("id",  true);

        return buildDropStoredQuery(version, service, handle, id);
    }

    private GetXSD createNewXsdRequest(WFSWorker worker) throws CstlServiceException {
        final String version              = getParameter(VERSION_PARAMETER, true);
        worker.checkVersionSupported(version, false);

        final String targetNamespace      = getParameter("targetnamespace", true);
        final Map<String, String> mapping = extractNamespace(version);
        final String typeNameStr          = getParameter("typeName", true);
        final List<QName> typeNames       = extractTypeName(typeNameStr, mapping);
        QName typeName;
        if (typeNames.size() == 1) {
            typeName = typeNames.get(0);
        } else {
            typeName = null;
        }
        return new GetXSD(typeName, targetNamespace, version);
    }


    @RequestMapping(path = "{version:.+}", method = RequestMethod.GET)
    public ResponseEntity processGetCapabilitiesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version) throws CstlServiceException {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final Sections sections;
                final String section = getSafeParameter(SECTIONS_PARAMETER);
                if (section != null && !section.equalsIgnoreCase("All")) {
                    final List<String> requestedSections = new ArrayList<>();
                    final StringTokenizer tokens = new StringTokenizer(section, ",;");
                    while (tokens.hasMoreTokens()) {
                        final String token = tokens.nextToken().trim();
                        if (SectionsType.getExistingSections().contains(token)){
                            requestedSections.add(token);
                        } else {
                            throw new CstlServiceException("The section " + token + " does not exist",
                                                          INVALID_PARAMETER_VALUE, "Sections");
                        }
                    }
                    sections = buildSections(version, requestedSections);
                } else {
                    sections = null;

                }
                final List<String> versions = new ArrayList<>();
                versions.add(version);
                final AcceptVersions acceptVersions = buildAcceptVersion(version, versions);
                final GetCapabilities gc = WFSXmlFactory.buildGetCapabilities(version, acceptVersions, sections, null, null, "WFS");
                return treatIncomingRequest(gc).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/schema", method = RequestMethod.GET)
    public ResponseEntity processDescribeFeatureRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final DescribeFeatureType df = WFSXmlFactory.buildDecribeFeatureType(version, "WFS", null, null, null);
                return treatIncomingRequest(df).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}", method = RequestMethod.GET)
    public ResponseEntity processFeatureTypeRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final Object request;
                if (featureType.endsWith(".xsd") || featureType.endsWith(".jsd")) {
                    featureType = featureType.substring(0, featureType.length() - 4);
                    final List<QName> typeNames = Arrays.asList(new QName(featureType));
                    String outFormat = null;
                    if (featureType.endsWith(".jsd")) {
                        outFormat = "application/schema+json";
                    }

                    request = WFSXmlFactory.buildDecribeFeatureType(version, "WFS", null, typeNames, outFormat);
                } else {
                    // TODO namespace param
                    final SortBy sortBy = parseSortByParameter(version);
                    String srsName       = getSafeParameter("srsName");
                    String resultTypeStr = getSafeParameter("resultType");
                    ResultTypeType resultType = ResultTypeType.RESULTS;
                    if (resultTypeStr != null) {
                        resultType = ResultTypeType.fromValue(resultTypeStr);
                    }
                    final Integer maxFeature = parseOptionalIntegerParam("count");
                    final Integer startIndex = parseOptionalIntegerParam("startIndex");
                    final String outputFormat = getSafeParameter("outputFormat");
                    final List<String> propertyNames = parseCommaSeparatedParameter("propertyName");
                    final List<QName> typeNames = Arrays.asList(new QName(featureType));
                    final Object xmlFilter  = getComplexParameter(FILTER, false);
                    XMLFilter filter;
                    final Map<String, String> prefixMapping;
                    if (xmlFilter instanceof XMLFilter) {
                        filter = (XMLFilter) xmlFilter;
                        prefixMapping = filter.getPrefixMapping();
                    } else {
                        filter = null;
                        prefixMapping = new HashMap<>();
                    }

                    final Query query   = buildQuery(version, filter, typeNames, null, srsName, sortBy, propertyNames);
                    final GetFeature gf = WFSXmlFactory.buildGetFeature(version, "WFS", null, startIndex, maxFeature, query, resultType, outputFormat);
                    gf.setPrefixMapping(prefixMapping);
                    request = gf;
                }
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}", method = RequestMethod.POST)
    public ResponseEntity processTransactionInsertRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @RequestBody final Node in) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final String srsName = getSafeParameter("srsName");
                final InsertElement elem = WFSXmlFactory.buildInsertElement(version, null, srsName, in);
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, elem);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}", method = RequestMethod.PUT)
    public ResponseEntity processTransactionReplaceRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @RequestBody final Node in) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final String srsName = getSafeParameter("srsName");
                final Object xmlFilter  = getComplexParameter(FILTER, false);
                final XMLFilter filter;
                final Map<String, String> prefixMapping;
                if (xmlFilter instanceof XMLFilter) {
                    filter = (XMLFilter) xmlFilter;
                    prefixMapping = filter.getPrefixMapping();
                } else {
                    filter = null;
                    prefixMapping = new HashMap<>();
                }
                final ReplaceElement elem = WFSXmlFactory.buildReplaceElement(version, null, srsName, filter, in);
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, elem);
                request.setPrefixMapping(prefixMapping);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}", method = RequestMethod.DELETE)
    public ResponseEntity processTransactionDeleteRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final Object xmlFilter  = getComplexParameter(FILTER, false);
                final XMLFilter filter;
                final Map<String, String> prefixMapping;
                if (xmlFilter instanceof XMLFilter) {
                    filter = (XMLFilter) xmlFilter;
                    prefixMapping = filter.getPrefixMapping();
                } else {
                    filter = null;
                    prefixMapping = new HashMap<>();
                }
                final DeleteElement elem = WFSXmlFactory.buildDeleteElement(version, filter, null, new QName(featureType));
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, elem);
                request.setPrefixMapping(prefixMapping);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/{feature:.+}", method = RequestMethod.GET)
    public ResponseEntity processGetFeatureRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("feature") String feature) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, feature);
                // TODO namespace param
                final SortBy sortBy = parseSortByParameter(version);
                final String srsName = getSafeParameter("srsName");
                final String outputFormat = getSafeParameter("outputFormat");
                String resultTypeStr = getSafeParameter("resultType");
                ResultTypeType resultType = ResultTypeType.RESULTS;
                if (resultTypeStr != null) {
                    resultType = ResultTypeType.fromValue(resultTypeStr);
                }
                final Integer maxFeature = parseOptionalIntegerParam("count");
                final Integer startIndex = parseOptionalIntegerParam("startIndex");
                final List<String> propertyNames = parseCommaSeparatedParameter("propertyName");
                final List<QName> typeNames = Arrays.asList(new QName(featureType));
                final Query query = buildQuery(version, filter, typeNames, null, srsName, sortBy, propertyNames);
                final Object request = WFSXmlFactory.buildGetFeature(version, "WFS", null, startIndex, maxFeature, query, resultType, outputFormat);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/{feature:.+}", method = RequestMethod.PUT)
    public ResponseEntity processTransactionReplaceRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("feature") String feature, @RequestBody final Node in) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, feature);
                final String srsName = getSafeParameter("srsName");
                final ReplaceElement elem = WFSXmlFactory.buildReplaceElement(version, null, srsName, filter, in);
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, elem);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/{feature:.+}", method = RequestMethod.DELETE)
    public ResponseEntity processTransactionDeleteRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("feature") String feature) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, feature);
                final DeleteElement elem = WFSXmlFactory.buildDeleteElement(version, filter, null, new QName(featureType));
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, elem);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/property/{prop:.+}", method = RequestMethod.GET)
    public ResponseEntity processGetPropertyValueRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("prop") String prop) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final Object xmlFilter  = getComplexParameter(FILTER, false);
                final XMLFilter filter;
                final Map<String, String> prefixMapping;
                if (xmlFilter instanceof XMLFilter) {
                    filter = (XMLFilter) xmlFilter;
                    prefixMapping = filter.getPrefixMapping();
                } else {
                    filter = null;
                    prefixMapping = new HashMap<>();
                }
                // TODO namespace param
                final SortBy sortBy = parseSortByParameter(version);
                final String srsName = getSafeParameter("srsName");
                final String outputFormat = getSafeParameter("outputFormat");
                String resultTypeStr = getSafeParameter("resultType");
                ResultTypeType resultType = ResultTypeType.RESULTS;
                if (resultTypeStr != null) {
                    resultType = ResultTypeType.fromValue(resultTypeStr);
                }
                final Integer maxFeature = parseOptionalIntegerParam("count");
                final Integer startIndex = parseOptionalIntegerParam("startIndex");
                final List<QName> typeNames = Arrays.asList(new QName(featureType));
                final List<String> propNames = Arrays.asList(prop);
                final Query query = buildQuery(version, filter, typeNames, null, srsName, sortBy, propNames);
                final GetPropertyValue request = WFSXmlFactory.buildGetPropertyValue(version, "WFS", null, startIndex, maxFeature, query, resultType, outputFormat, prop);
                request.setPrefixMapping(prefixMapping);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/property/{prop:.+}", method = RequestMethod.PUT)
    public ResponseEntity processTransactionUpdateRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("prop") String prop, final InputStream in) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final Object xmlFilter  = getComplexParameter(FILTER, false);
                final XMLFilter filter;
                final Map<String, String> prefixMapping;
                if (xmlFilter instanceof XMLFilter) {
                    filter = (XMLFilter) xmlFilter;
                    prefixMapping = filter.getPrefixMapping();
                } else {
                    filter = null;
                    prefixMapping = new HashMap<>();
                }
                final String srsName = getSafeParameter("srsName");

                /**
                 * getting the value is a little bit tricky. use inputFormat?
                 */
                final Unmarshaller um = getMarshallerPool().acquireUnmarshaller();
                final Object obj = um.unmarshal(in);
                getMarshallerPool().recycle(um);

                final Property property = WFSXmlFactory.buildProperty(version, prop, obj);
                final UpdateElement update = WFSXmlFactory.buildUpdateElement(version, null, srsName, filter, new QName(featureType), Arrays.asList(property));
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, update);
                request.setPrefixMapping(prefixMapping);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException | JAXBException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/property/{prop:.+}", method = RequestMethod.DELETE)
    public ResponseEntity processTransactionUpdateNullRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("prop") String prop) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final Object xmlFilter  = getComplexParameter(FILTER, false);
                final XMLFilter filter;
                final Map<String, String> prefixMapping;
                if (xmlFilter instanceof XMLFilter) {
                    filter = (XMLFilter) xmlFilter;
                    prefixMapping = filter.getPrefixMapping();
                } else {
                    filter = null;
                    prefixMapping = new HashMap<>();
                }
                final String srsName = getSafeParameter("srsName");

                final Property property = WFSXmlFactory.buildProperty(version, prop, null);
                final UpdateElement update = WFSXmlFactory.buildUpdateElement(version, null, srsName, filter, new QName(featureType), Arrays.asList(property));
                final Transaction request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, update);
                request.setPrefixMapping(prefixMapping);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/{feature:.+}/{prop:.+}", method = RequestMethod.GET)
    public ResponseEntity processGetPropertyValueRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("feature") String feature, @PathVariable("prop") String prop) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, feature);
                // TODO namespace param
                final SortBy sortBy = parseSortByParameter(version);
                final String srsName = getSafeParameter("srsName");
                final String outputFormat = getSafeParameter("outputFormat");
                String resultTypeStr = getSafeParameter("resultType");
                ResultTypeType resultType = ResultTypeType.RESULTS;
                if (resultTypeStr != null) {
                    resultType = ResultTypeType.fromValue(resultTypeStr);
                }
                final Integer maxFeature = parseOptionalIntegerParam("count");
                final Integer startIndex = parseOptionalIntegerParam("startIndex");
                final List<QName> typeNames = Arrays.asList(new QName(featureType));
                final List<String> propNames = Arrays.asList(prop);
                final Query query = buildQuery(version, filter, typeNames, null, srsName, sortBy, propNames);
                final Object request = WFSXmlFactory.buildGetPropertyValue(version, "WFS", null, startIndex, maxFeature, query, resultType, outputFormat, prop);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/{feature:.+}/{prop:.+}", method = RequestMethod.PUT)
    public ResponseEntity processTransactionUpdateRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("feature") String feature, @PathVariable("prop") String prop, final InputStream in) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, feature);
                final String srsName = getSafeParameter("srsName");

                /**
                 * getting the value is a little bit tricky. use inputFormat?
                 */
                final Unmarshaller um = getMarshallerPool().acquireUnmarshaller();
                final Object obj = um.unmarshal(in);
                getMarshallerPool().recycle(um);

                final Property property = WFSXmlFactory.buildProperty(version, prop, obj);
                final UpdateElement update = WFSXmlFactory.buildUpdateElement(version, null, srsName, filter, new QName(featureType), Arrays.asList(property));
                final Object request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, update);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException | JAXBException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/{featureType:.+}/{feature:.+}/{prop:.+}", method = RequestMethod.DELETE)
    public ResponseEntity processTransactionUpdateNullRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("featureType") String featureType,
            @PathVariable("feature") String feature, @PathVariable("prop") String prop) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final XMLFilter filter = FilterXmlFactory.buildFeatureIDFilter(version, feature);
                final String srsName = getSafeParameter("srsName");

                final Property property = WFSXmlFactory.buildProperty(version, prop, null);
                final UpdateElement update = WFSXmlFactory.buildUpdateElement(version, null, srsName, filter, new QName(featureType), Arrays.asList(property));
                final Object request = WFSXmlFactory.buildTransaction(version, "WFS", null, AllSomeType.ALL, update);
                return treatIncomingRequest(request).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/query", method = RequestMethod.GET)
    public ResponseEntity processListStoredQueriesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final ListStoredQueries lsq = WFSXmlFactory.buildListStoredQueries(version, "WFS", null);
                return treatIncomingRequest(lsq).getResponseEntity();
            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/query", method = RequestMethod.POST)
    public ResponseEntity processCreateStoredQueriesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, final InputStream queryStream) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);

                final String queryString = IOUtilities.toString(queryStream);
                final Unmarshaller um    = getMarshallerPool().acquireUnmarshaller();
                Object obj               = um.unmarshal(new StringReader(queryString));
                getMarshallerPool().recycle(um);
                if (obj instanceof JAXBElement) {
                    obj = ((JAXBElement)obj).getValue();
                }

                if (obj instanceof Query) {
                    // adHocQuery
                    final String id = UUID.randomUUID().toString();
                    List<ParameterExpression> parameters = extractPram(version, queryString);
                    final StoredQueryDescription desc = WFSXmlFactory.buildStoredQueryDescription(version, id, (Query)obj, parameters);
                    final CreateStoredQuery lsq = WFSXmlFactory.buildCreateStoredQuery(version, "WFS", null, Arrays.asList(desc));
                    return treatIncomingRequest(lsq).getResponseEntity();
                } else {
                    throw new CstlServiceException("Unexpected content for query body");
                }
            } catch (IllegalArgumentException | JAXBException | IOException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    public static List<ParameterExpression> extractPram(String version, String s) {
        final List<ParameterExpression> results = new ArrayList<>();
        int pos = s.indexOf("${");
        while (pos != -1) {
            int endPos = s.indexOf('}');
            if (endPos != -1 && pos < endPos) {
                results.add(WFSXmlFactory.buildParameterDescription(version, s.substring(pos + 2, endPos), new QName("http://www.w3.org/2001/XMLSchema", "string", "xs")));
                s = s .substring(endPos + 1);
            } else if (endPos < pos){
                s = s .substring(endPos + 1);
            }
            pos = s.indexOf("${");
        }
        return results;
    }

    @RequestMapping(path = "{version:.+}/query/{queryId:.+}", method = RequestMethod.GET)
    public ResponseEntity processExecuteStoredQueriesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("queryId") final String queryId) {
        putServiceIdParam(serviceId);
        final WFSWorker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);

                final List<Parameter> params = extractParameters(worker, queryId, version, new HashMap<>());
                final StoredQuery query = WFSXmlFactory.buildStoredQuery(version, queryId, null, params);
                final GetFeature lsq = WFSXmlFactory.buildGetFeature(version, "WFS", null, null, null, query, null, null);
                return treatIncomingRequest(lsq).getResponseEntity();

            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/query/{queryId:.+}", method = RequestMethod.POST)
    public ResponseEntity processCreateStoredQueriesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("queryId") final String queryId, final InputStream queryStream) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final String queryString = IOUtilities.toString(queryStream);
                final Unmarshaller um    = getMarshallerPool().acquireUnmarshaller();
                Object obj               = um.unmarshal(new StringReader(queryString));
                getMarshallerPool().recycle(um);
                if (obj instanceof JAXBElement) {
                    obj = ((JAXBElement)obj).getValue();
                }

                if (obj instanceof Query) {
                    final String id = queryId;
                    List<ParameterExpression> parameters = extractPram(version, queryString);
                    final StoredQueryDescription desc = WFSXmlFactory.buildStoredQueryDescription(version, id, (Query)obj, parameters);
                    final CreateStoredQuery lsq = WFSXmlFactory.buildCreateStoredQuery(version, "WFS", null, Arrays.asList(desc));
                    return treatIncomingRequest(lsq).getResponseEntity();
                } else {
                    throw new CstlServiceException("Unexpected content for query body");
                }
            } catch (IllegalArgumentException | JAXBException | IOException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/query/{queryId:.+}", method = RequestMethod.PUT)
    public ResponseEntity processUpdateStoredQueriesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("queryId") final String queryId, final InputStream queryStream) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final String queryString = IOUtilities.toString(queryStream);
                final Unmarshaller um    = getMarshallerPool().acquireUnmarshaller();
                Object obj               = um.unmarshal(new StringReader(queryString));
                getMarshallerPool().recycle(um);
                if (obj instanceof JAXBElement) {
                    obj = ((JAXBElement)obj).getValue();
                }

                if (obj instanceof Query) {
                    // REMOVE then INSERT
                    final DropStoredQuery dsq = WFSXmlFactory.buildDropStoredQuery(version, "WFS", null, queryId);
                    treatIncomingRequest(dsq);

                    final String id = queryId;
                    List<ParameterExpression> parameters = extractPram(version, queryString);
                    final StoredQueryDescription desc = WFSXmlFactory.buildStoredQueryDescription(version, id, (Query)obj, parameters);
                    final CreateStoredQuery csq = WFSXmlFactory.buildCreateStoredQuery(version, "WFS", null, Arrays.asList(desc));
                    return treatIncomingRequest(csq).getResponseEntity();
                } else {
                    throw new CstlServiceException("Unexpected content for query body");
                }
            } catch (IllegalArgumentException | JAXBException | IOException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }

    @RequestMapping(path = "{version:.+}/query/{queryId:.+}", method = RequestMethod.DELETE)
    public ResponseEntity processDeleteStoredQueriesRestful(@PathVariable("serviceId") String serviceId, @PathVariable("version") final String version, @PathVariable("queryId") final String queryId) {
        putServiceIdParam(serviceId);
        final Worker worker = getWorker(serviceId);
        if (worker != null) {
            try {
                worker.checkVersionSupported(version, true);
                final DropStoredQuery lsq = WFSXmlFactory.buildDropStoredQuery(version, "WFS", null, queryId);
                return treatIncomingRequest(lsq).getResponseEntity();

            } catch (IllegalArgumentException ex) {
                return processExceptionResponse(new CstlServiceException(ex), null, worker).getResponseEntity();
            } catch (CstlServiceException ex) {
                return processExceptionResponse(ex, null, worker).getResponseEntity();
            }
        }
        return new ResponseEntity(HttpStatus.NOT_FOUND);
    }
}
