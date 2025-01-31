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
package org.constellation.wmts.ws.rs;

import org.constellation.api.ServiceDef;
import org.constellation.api.ServiceDef.Specification;
import org.constellation.wmts.core.WMTSWorker;
import org.constellation.ws.CstlServiceException;
import org.constellation.ws.Worker;
import org.constellation.ws.rs.GridWebService;
import org.geotoolkit.image.io.XImageIO;
import org.geotoolkit.ows.xml.RequestBase;
import org.geotoolkit.ows.xml.v110.AcceptFormatsType;
import org.geotoolkit.ows.xml.v110.AcceptVersionsType;
import org.geotoolkit.ows.xml.v110.ExceptionReport;
import org.geotoolkit.ows.xml.v110.SectionsType;
import org.geotoolkit.wmts.xml.WMTSMarshallerPool;
import org.geotoolkit.wmts.xml.v100.DimensionNameValue;
import org.geotoolkit.wmts.xml.v100.GetCapabilities;
import org.geotoolkit.wmts.xml.v100.GetFeatureInfo;
import org.geotoolkit.wmts.xml.v100.GetTile;

import javax.imageio.IIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;

import static org.constellation.api.QueryConstants.ACCEPT_FORMATS_PARAMETER;
import static org.constellation.api.QueryConstants.ACCEPT_VERSIONS_PARAMETER;
import static org.constellation.api.QueryConstants.REQUEST_PARAMETER;
import static org.constellation.api.QueryConstants.SECTIONS_PARAMETER;
import static org.constellation.api.QueryConstants.SERVICE_PARAMETER;
import static org.constellation.api.QueryConstants.UPDATESEQUENCE_PARAMETER;
import static org.constellation.api.QueryConstants.VERSION_PARAMETER;
import static org.constellation.ws.ExceptionCode.INVALID_PARAMETER_VALUE;
import static org.constellation.ws.ExceptionCode.MISSING_PARAMETER_VALUE;
import static org.constellation.ws.ExceptionCode.NO_APPLICABLE_CODE;
import static org.constellation.ws.ExceptionCode.OPERATION_NOT_SUPPORTED;
import org.constellation.ws.rs.ResponseObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import javax.servlet.http.HttpServletResponse;

// Jersey dependencies

/**
 * The REST facade to an OGC Web Map Tile Service, implementing the 1.0.0 version.
 *
 * @version $Id$
 *
 * @author Cédric Briançon (Geomatys)
 * @author Guilhem Legal (Geomatys)
 * @since 0.3
 */
@Controller
@RequestMapping("wmts/{serviceId:.+}")
public class WMTSService extends GridWebService<WMTSWorker> {

    /**
     * Builds a new WMTS service REST (both REST Kvp and RESTFUL). This service only
     * provides the version 1.0.0 of OGC WMTS standard, for the moment.
     */
    public WMTSService() {
        super(Specification.WMTS);
        setXMLContext(WMTSMarshallerPool.getInstance());
        LOGGER.log(Level.INFO, "WMTS REST service running");
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public ResponseObject treatIncomingRequest(final Object objectRequest, final WMTSWorker worker) {
        ServiceDef serviceDef = null;
        try {

            // if the request is not an xml request we fill the request parameter.
            final RequestBase request;
            if (objectRequest == null) {
                request = adaptQuery(getParameter(REQUEST_PARAMETER, true));
            } else if (objectRequest instanceof RequestBase) {
                request = (RequestBase) objectRequest;
            } else {
                throw new CstlServiceException("The operation " + objectRequest.getClass().getName() + " is not supported by the service",
                        INVALID_PARAMETER_VALUE, "request");
            }
            serviceDef = worker.getVersionFromNumber(request.getVersion());

            if (request instanceof GetCapabilities) {
                final GetCapabilities gc = (GetCapabilities) request;
                return new ResponseObject(worker.getCapabilities(gc), MediaType.TEXT_XML);
            }
            if (request instanceof GetTile) {
                final GetTile gt = (GetTile) request;
                return new ResponseObject(worker.getTile(gt), gt.getFormat());
            }
            if (request instanceof GetFeatureInfo) {
                final GetFeatureInfo gf = (GetFeatureInfo) request;
                final Map.Entry<String, Object> result = worker.getFeatureInfo(gf);
                if (result != null) {
                    return new ResponseObject(result.getValue(), result.getKey());
                }
                //throw an exception if result of GetFeatureInfo visitor is null
                throw new CstlServiceException("An error occurred during GetFeatureInfo response building.");
            }

            throw new CstlServiceException("The operation " + request.getClass().getName() +
                    " is not supported by the service", OPERATION_NOT_SUPPORTED, "request");
        } catch (CstlServiceException ex) {
            return processExceptionResponse(ex, serviceDef, worker);
        }
    }

    /**
     * Build request object fom KVP parameters.
     *
     * @param request
     * @return
     * @throws CstlServiceException
     */
    private RequestBase adaptQuery(final String request) throws CstlServiceException {

        if ("GetCapabilities".equalsIgnoreCase(request)) {
            return createNewGetCapabilitiesRequest();
        } else if ("GetTile".equalsIgnoreCase(request)) {
            return createNewGetTileRequest();
        } else if ("GetFeatureInfo".equalsIgnoreCase(request)) {
            return createNewGetFeatureInfoRequest();
        }
        throw new CstlServiceException("The operation " + request + " is not supported by the service",
                        INVALID_PARAMETER_VALUE, "request");
    }

    /**
     * Builds a new {@link GetCapabilities} request from a REST Kvp request.
     *
     * @return The {@link GetCapabilities} request.
     * @throws CstlServiceException if a required parameter is not present in the request.
     */
    private GetCapabilities createNewGetCapabilitiesRequest() throws CstlServiceException {

        String version = getParameter(ACCEPT_VERSIONS_PARAMETER, false);
        AcceptVersionsType versions;
        if (version != null) {
            if (version.indexOf(',') != -1) {
                version = version.substring(0, version.indexOf(','));
            }
            versions = new AcceptVersionsType(version);
        } else {
             versions = new AcceptVersionsType("1.0.0");
        }

        final AcceptFormatsType formats = new AcceptFormatsType(getParameter(ACCEPT_FORMATS_PARAMETER, false));

        //We transform the String of sections in a list.
        //In the same time we verify that the requested sections are valid.
        final String section = getParameter(SECTIONS_PARAMETER, false);
        List<String> requestedSections = new ArrayList<>();
        if (section != null && !section.equalsIgnoreCase("All")) {
            final StringTokenizer tokens = new StringTokenizer(section, ",;");
            while (tokens.hasMoreTokens()) {
                final String token = tokens.nextToken().trim();
                if (SectionsType.getExistingSections("1.1.1").contains(token)){
                    requestedSections.add(token);
                } else {
                    throw new CstlServiceException("The section " + token + " does not exist",
                                                  INVALID_PARAMETER_VALUE, "Sections");
                }
            }
        } else {
            //if there is no requested Sections we add all the sections
            requestedSections = SectionsType.getExistingSections("1.1.1");
        }
        final SectionsType sections     = new SectionsType(requestedSections);
        final String updateSequence = getParameter(UPDATESEQUENCE_PARAMETER, false);
        return new GetCapabilities(versions,
                                   sections,
                                   formats,
                                   updateSequence,
                                   getParameter(SERVICE_PARAMETER, true));

    }

    /**
     * Builds a new {@link GetCapabilities} request from a RESTFUL request.
     *
     * @return The {@link GetCapabilities} request.
     * @throws CstlServiceException if a required parameter is not present in the request.
     */
    private GetCapabilities createNewGetCapabilitiesRequestRestful(final String version) throws CstlServiceException {
        final AcceptVersionsType versions;
        if (version != null) {
            versions = new AcceptVersionsType(version);
        } else {
            versions = new AcceptVersionsType("1.0.0");
        }
        return new GetCapabilities(versions, null, null, null, "WMTS");
    }

    /**
     * Builds a new {@link GetFeatureInfo} request from a REST Kvp request.
     *
     * @return The {@link GetFeatureInfo} request.
     * @throws CstlServiceException if a required parameter is not present in the request.
     */
    private GetFeatureInfo createNewGetFeatureInfoRequest() throws CstlServiceException {
        final GetFeatureInfo gfi = new GetFeatureInfo();
        gfi.setGetTile(createNewGetTileRequest());
        gfi.setI(Integer.valueOf(getParameter("I", true)));
        gfi.setJ(Integer.valueOf(getParameter("J", true)));
        gfi.setInfoFormat(getParameter("infoformat", true));
        gfi.setService(getParameter(SERVICE_PARAMETER, true));
        gfi.setVersion(getParameter(VERSION_PARAMETER, true));
        return gfi;
    }

    /**
     * Builds a new {@link GetTile} request from a REST Kvp request.
     *
     * @return The {@link GetTile} request.
     * @throws CstlServiceException if a required parameter is not present in the request.
     */
    private GetTile createNewGetTileRequest() throws CstlServiceException {
        final GetTile getTile = new GetTile();
        final Map<String, String[]> parameters = getParameters();
        parameters.remove(REQUEST_PARAMETER);
        // Mandatory parameters
        getTile.setFormat(getParameter("format", true));
        parameters.remove("format");
        getTile.setLayer(getParameter("layer", true));
        parameters.remove("layer");
        getTile.setService(getParameter(SERVICE_PARAMETER, true));
        parameters.remove(SERVICE_PARAMETER);
        getTile.setVersion(getParameter(VERSION_PARAMETER, true));
        parameters.remove(VERSION_PARAMETER);
        getTile.setTileCol(Integer.valueOf(getParameter("TileCol", true)));
        parameters.remove("TileCol");
        getTile.setTileRow(Integer.valueOf(getParameter("TileRow", true)));
        parameters.remove("TileRow");
        getTile.setTileMatrix(getParameter("TileMatrix", true));
        parameters.remove("TileMatrix");
        getTile.setTileMatrixSet(getParameter("TileMatrixSet", true));
        parameters.remove("TileMatrixSet");
        // Optional parameters
        getTile.setStyle(getParameter("style", false));
        parameters.remove("style");
        /*
         * HACK : Remaining parameters will be considered as extra dimension of the layer. We don't check layer
         * capabilities because it could be resource consuming operation. Filtering will be done by worker when it will
         * recompose a multi-dimensional envelope.
         */
        if (!parameters.isEmpty()) {
            final List<DimensionNameValue> dims = getTile.getDimensionNameValue();
            for (Map.Entry<String, String[]> entry : parameters.entrySet()) {
                if (entry.getValue() != null && entry.getValue().length != 0) {
                    final DimensionNameValue dnv = new DimensionNameValue();
                    dnv.setName(entry.getKey());
                    dnv.setValue(entry.getValue()[0]);
                    dims.add(dnv);
                }
            }
        }


        return getTile;
    }

    /**
     * Builds a new {@link GetTile} request from a RESTFUL request.
     *
     * @return The {@link GetTile} request.
     * @throws CstlServiceException if a required parameter is not present in the request.
     */
    private GetTile createNewGetTileRequestRestful(final String layer, final String tileMatrixSet,
                                                   final String tileMatrix, final String tileRow,
                                                   final String tileCol, final String format, final String style)
                                                   throws CstlServiceException
    {
        final GetTile getTile = new GetTile();
        // Mandatory parameters
        if (format == null) {
            throw new CstlServiceException("The parameter FORMAT must be specified",
                        MISSING_PARAMETER_VALUE);
        }
        getTile.setFormat(format);
        getTile.setLayer(layer);
        if (layer == null) {
            throw new CstlServiceException("The parameter LAYER must be specified",
                        MISSING_PARAMETER_VALUE);
        }
        getTile.setService("WMTS");
        getTile.setVersion("1.0.0");
        if (tileCol == null) {
            throw new CstlServiceException("The parameter TILECOL must be specified",
                        MISSING_PARAMETER_VALUE);
        }
        getTile.setTileCol(Integer.valueOf(tileCol));
        if (tileRow == null) {
            throw new CstlServiceException("The parameter TILEROW must be specified",
                        MISSING_PARAMETER_VALUE);
        }
        getTile.setTileRow(Integer.valueOf(tileRow));
        if (tileMatrix == null) {
            throw new CstlServiceException("The parameter TILEMATRIX must be specified",
                        MISSING_PARAMETER_VALUE);
        }
        getTile.setTileMatrix(tileMatrix);
        if (tileMatrixSet == null) {
            throw new CstlServiceException("The parameter TILEMATRIXSET must be specified",
                        MISSING_PARAMETER_VALUE);
        }
        getTile.setTileMatrixSet(tileMatrixSet);
        // Optionnal parameters
        getTile.setStyle(style);
        return getTile;
    }

    /**
     * Handle {@code GetCapabilities request} in RESTFUL mode.
     *
     * @param version The version of the GetCapabilities request.
     * @param resourcename The name of the resource file.
     *
     * @return The XML formatted response, for an OWS GetCapabilities of the WMTS standard.
     */
    @RequestMapping(path = "{version}/{caps}", method = RequestMethod.GET)
    public ResponseEntity processGetCapabilitiesRestful(@PathVariable("serviceId") String serviceId,
                                                        @PathVariable("version") final String version,
                                                        @PathVariable("caps") final String resourcename,
                                                        HttpServletResponse response) {
        putServiceIdParam(serviceId);
        try {
            final GetCapabilities gc = createNewGetCapabilitiesRequestRestful(version);
            return treatIncomingRequest(gc).getResponseEntity(response);
        } catch (CstlServiceException ex) {
            final Worker w = wsengine.getInstance("WMTS", getSafeParameter("serviceId"));
            return processExceptionResponse(ex, null, w).getResponseEntity(response);
        }
    }

    /**
     * Handle {@code GetTile request} in RESTFUL mode.
     *
     * @param layer The layer to request.
     * @param tileMatrixSet The matrix set of the tile.
     * @param tileMatrix The matrix tile.
     * @param tileRow The row of the tile in the matrix.
     * @param tileCol The column of the tile in the matrix.
     * @param format The format extension, like png.
     *
     * @return The response containing the tile.
     */
    @RequestMapping(path = "{layer}/{tileMatrixSet}/{tileMatrix}/{tileRow}/{tileCol}.{format}", method = RequestMethod.GET)
    public ResponseEntity processGetTileRestful(@PathVariable("serviceId") String serviceId,
                                          @PathVariable("layer") final String layer,
                                          @PathVariable("tileMatrixSet") final String tileMatrixSet,
                                          @PathVariable("tileMatrix") final String tileMatrix,
                                          @PathVariable("tileRow") final String tileRow,
                                          @PathVariable("tileCol") final String tileCol,
                                          @PathVariable("format") final String format,
                                          HttpServletResponse response) {
        putServiceIdParam(serviceId);
        try {
            final String mimeType;
            try {
                mimeType = XImageIO.formatNameToMimeType(format);
            } catch (IIOException ex) {
                throw new CstlServiceException(ex, NO_APPLICABLE_CODE);
            }
            final GetTile gt = createNewGetTileRequestRestful(layer, tileMatrixSet, tileMatrix, tileRow, tileCol, mimeType, null);
            return treatIncomingRequest(gt).getResponseEntity(response);
        } catch (CstlServiceException ex) {
            final Worker w = wsengine.getInstance("WMTS", getSafeParameter("serviceId"));
            return processExceptionResponse(ex, null, w).getResponseEntity(response);
        }
    }

    /**
     * Handle all exceptions returned by a web service operation in two ways:
     * <ul>
     *   <li>if the exception code indicates a mistake done by the user, just display a single
     *       line message in logs.</li>
     *   <li>otherwise logs the full stack trace in logs, because it is something interesting for
     *       a developer</li>
     * </ul>
     * In both ways, the exception is then marshalled and returned to the client.
     *
     * @param ex The exception that has been generated during the web-service operation requested.
     * @param worker The worker operating this exception.
     *
     * @return An XML representing the exception.
     *
     */
    @Override
    protected ResponseObject processExceptionResponse(final CstlServiceException ex, ServiceDef serviceDef, final Worker worker) {
        logException(ex);

        if (serviceDef == null) {
            serviceDef = worker.getBestVersion(null);
        }
        final String codeName = getOWSExceptionCodeRepresentation(ex.getExceptionCode());

        final ExceptionReport report = new ExceptionReport(ex.getMessage(), codeName,
                ex.getLocator(), serviceDef.exceptionVersion.toString());
        return new ResponseObject(report, MediaType.TEXT_XML);

    }
}
