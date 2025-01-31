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
package org.constellation.wfs;

import org.apache.sis.util.logging.Logging;
import org.apache.sis.xml.MarshallerPool;
import org.constellation.admin.SpringHelper;
import org.constellation.api.ProviderType;
import org.constellation.business.IDataBusiness;
import org.constellation.business.ILayerBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.exception.ConfigurationException;
import org.constellation.dto.service.config.wxs.LayerContext;
import org.constellation.provider.DataProviders;
import org.constellation.provider.DataProviderFactory;
import org.constellation.test.utils.CstlDOMComparator;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.SpringTestRunner;
import org.constellation.test.utils.TestEnvironment;
import org.constellation.util.Util;
import org.constellation.wfs.core.DefaultWFSWorker;
import org.constellation.wfs.core.WFSWorker;
import org.constellation.wfs.ws.rs.FeatureSetWrapper;
import org.constellation.ws.CstlServiceException;
import org.geotoolkit.feature.xml.XmlFeatureWriter;
import org.geotoolkit.feature.xml.jaxp.JAXPStreamFeatureWriter;
import org.geotoolkit.internal.sql.DefaultDataSource;
import org.geotoolkit.internal.sql.DerbySqlScriptRunner;
import org.geotoolkit.nio.IOUtilities;
import org.geotoolkit.ogc.xml.v110.*;
import org.geotoolkit.ows.xml.v100.AcceptVersionsType;
import org.geotoolkit.ows.xml.v100.SectionsType;
import org.geotoolkit.referencing.CRS;
import org.geotoolkit.wfs.xml.*;
import org.geotoolkit.wfs.xml.v110.*;
import org.geotoolkit.wfs.xml.v200.ParameterExpressionType;
import org.geotoolkit.wfs.xml.v200.QueryExpressionTextType;
import org.geotoolkit.wfs.xml.v200.StoredQueryDescriptionType;
import org.geotoolkit.xsd.xml.v2001.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.opengis.parameter.ParameterValueGroup;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.*;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.constellation.provider.ProviderParameters;
import org.constellation.provider.datastore.DataStoreProviderService;

import static org.geotoolkit.ows.xml.OWSExceptionCode.*;
import static org.junit.Assert.*;

import org.geotoolkit.wfs.xml.v200.ObjectFactory;


/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@RunWith(SpringTestRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class,DirtiesContextTestExecutionListener.class})
@DirtiesContext(hierarchyMode = DirtiesContext.HierarchyMode.EXHAUSTIVE,classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@ContextConfiguration(inheritInitializers = false, locations={"classpath:/cstl/spring/test-context.xml"})
public class WFSWorkerTest {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.wfs");

    @Inject
    private IServiceBusiness serviceBusiness;
    @Inject
    protected ILayerBusiness layerBusiness;
    @Inject
    protected IProviderBusiness providerBusiness;
    @Inject
    protected IDataBusiness dataBusiness;

    private static MarshallerPool pool;
    private static WFSWorker worker ;

    private static DefaultDataSource ds = null;

    private static DefaultDataSource ds2 = null;

    private XmlFeatureWriter featureWriter;

    private static String EPSG_VERSION;

    private static boolean initialized = false;

    private static Path shapefiles;
    private static Path geojsons;

    @BeforeClass
    public static void initTestDir() throws IOException, URISyntaxException {
        File workspace = ConfigDirectory.setupTestEnvironement("WFSWorkerTest").toFile();
        shapefiles = TestEnvironment.initWorkspaceData(workspace.toPath(), TestEnvironment.TestResources.WMS111_SHAPEFILES);
        geojsons   = TestEnvironment.initWorkspaceData(workspace.toPath(), TestEnvironment.TestResources.JSON);
    }

    @PostConstruct
    public void setUpClass() {
        if (!initialized) {
            try {

                layerBusiness.removeAll();
                serviceBusiness.deleteAll();
                dataBusiness.deleteAll();
                providerBusiness.removeAll();

                final DataProviderFactory featfactory = DataProviders.getFactory("data-store");

                /**
                 * SHAPEFILE DATA
                 */
                final ParameterValueGroup sourcef = featfactory.getProviderDescriptor().createValue();
                sourcef.parameter("id").setValue("shapeSrc");

                final ParameterValueGroup choice2 = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourcef);
                final ParameterValueGroup shpconfig = choice2.addGroup("ShapefileParametersFolder");
                shpconfig.parameter("path").setValue(shapefiles.toUri());

                providerBusiness.storeProvider("shapeSrc", null, ProviderType.LAYER, "data-store", sourcef);

                dataBusiness.create(new QName("http://www.opengis.net/gml", "BuildingCenters"), "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "BasicPolygons"),   "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "Bridges"),         "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "Streams"),         "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "Lakes"),           "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "NamedPlaces"),     "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "Buildings"),       "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "RoadSegments"),    "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "DividedRoutes"),   "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "Forests"),         "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "MapNeatline"),     "shapeSrc", "VECTOR", false, true, null, null);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "Ponds"),           "shapeSrc", "VECTOR", false, true, null, null);

                /**
                 * SOS DB DATA
                 */
                final String url = "jdbc:derby:memory:TestWFSWorkerOM";
                final DefaultDataSource ds = new DefaultDataSource(url + ";create=true");
                Connection con = ds.getConnection();
                DerbySqlScriptRunner sr = new DerbySqlScriptRunner(con);
                String sql = IOUtilities.toString(Util.getResourceAsStream("org/constellation/om2/structure_observations.sql"));
                sql = sql.replace("$SCHEMA", "");
                sr.run(sql);
                sr.run(Util.getResourceAsStream("org/constellation/sql/sos-data-om2.sql"));
                con.close();
                ds.shutdown();

                final ParameterValueGroup sourceOM = featfactory.getProviderDescriptor().createValue();
                sourceOM.parameter("id").setValue("omSrc");

                final ParameterValueGroup choiceOM = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourceOM);
                final ParameterValueGroup omconfig = choiceOM.addGroup(" SOSDBParameters");
                omconfig.parameter("sgbdtype").setValue("derby");
                omconfig.parameter("derbyurl").setValue(url);

                providerBusiness.storeProvider("omSrc", null, ProviderType.LAYER, "data-store", sourceOM);
                dataBusiness.create(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint"), "omSrc", "VECTOR", false, true, null, null);

                /**
                 * GEOJSON DATA
                 */
                final ParameterValueGroup sourcegjs = featfactory.getProviderDescriptor().createValue();
                sourcegjs.parameter("id").setValue("geojsonSrc");
                final ParameterValueGroup choice3 = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourcegjs);
                final ParameterValueGroup gjsconfig = choice3.addGroup("GeoJSONParameters");
                gjsconfig.parameter("path").setValue(geojsons.resolve("feature.json").toUri());

                providerBusiness.storeProvider("geojsonSrc", null, ProviderType.LAYER, "data-store", sourcegjs);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "feature"), "geojsonSrc", "VECTOR", false, true, null, null);

                final ParameterValueGroup sourcegjs2 = featfactory.getProviderDescriptor().createValue();
                sourcegjs2.parameter("id").setValue("geojsonSrc2");
                final ParameterValueGroup choice4 = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourcegjs2);
                final ParameterValueGroup gjsconfig2 = choice4.addGroup("GeoJSONParameters");
                gjsconfig2.parameter("path").setValue(geojsons.resolve("featureCollection.json").toUri());

                providerBusiness.storeProvider("geojsonSrc2", null, ProviderType.LAYER, "data-store", sourcegjs2);
                dataBusiness.create(new QName("http://www.opengis.net/gml", "featureCollection"), "geojsonSrc2", "VECTOR", false, true, null, null);


                final LayerContext config = new LayerContext();
                config.getCustomParameters().put("transactionSecurized", "false");
                config.getCustomParameters().put("transactional", "true");

                serviceBusiness.create("wfs", "default", config, null, null);

                layerBusiness.add("SamplingPoint",       "http://www.opengis.net/sampling/1.0",  "omSrc",      null, "default", "wfs", null);
                layerBusiness.add("BuildingCenters",     "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("BasicPolygons",       "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("Bridges",             "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("Streams",             "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("Lakes",               "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("NamedPlaces",         "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("Buildings",           "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("RoadSegments",        "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("DividedRoutes",       "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("Forests",             "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("MapNeatline",         "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("Ponds",               "http://www.opengis.net/gml",       "shapeSrc",       null, "default", "wfs", null);
                layerBusiness.add("feature",             "http://www.opengis.net/gml",       "geojsonSrc",     null, "default", "wfs", null);
                layerBusiness.add("featureCollection",   "http://www.opengis.net/gml",       "geojsonSrc2",    null, "default", "wfs", null);

                serviceBusiness.create("wfs", "test", config, null, null);

                layerBusiness.add("SamplingPoint",       "http://www.opengis.net/sampling/1.0",  "omSrc",      null, "test", "wfs", null);
                layerBusiness.add("BuildingCenters",     "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("BasicPolygons",       "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("Bridges",             "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("Streams",             "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("Lakes",               "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("NamedPlaces",         "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("Buildings",           "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("RoadSegments",        "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("DividedRoutes",       "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("Forests",             "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("MapNeatline",         "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);
                layerBusiness.add("Ponds",               "http://www.opengis.net/gml",       "shapeSrc",   null, "test", "wfs", null);


                final LayerContext config2 = new LayerContext();
                config2.getCustomParameters().put("transactionSecurized", "false");
                config2.getCustomParameters().put("transactional", "true");

                serviceBusiness.create("wfs", "test1", config, null, null);
                layerBusiness.add("SamplingPoint",       "http://www.opengis.net/sampling/1.0",  "omSrc",      null, "test1", "wfs", null);
                layerBusiness.add("BuildingCenters",     "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("BasicPolygons",       "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("Bridges",             "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("Streams",             "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("Lakes",               "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("NamedPlaces",         "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("Buildings",           "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("RoadSegments",        "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("DividedRoutes",       "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("Forests",             "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("MapNeatline",         "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("Ponds",               "http://www.opengis.net/gml",       "shapeSrc",   null, "test1", "wfs", null);
                layerBusiness.add("feature",             "http://www.opengis.net/gml",       "geojsonSrc",     null, "test1", "wfs", null);
                layerBusiness.add("featureCollection",   "http://www.opengis.net/gml",       "geojsonSrc2",    null, "test1", "wfs", null);

                pool = WFSMarshallerPool.getInstance();

                final List<StoredQueryDescription> descriptions = new ArrayList<>();
                final ParameterExpressionType param = new ParameterExpressionType("name", "name Parameter", "A parameter on the name of the feature", new QName("http://www.w3.org/2001/XMLSchema", "string", "xs"));
                final List<QName> types = Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint"));
                final org.geotoolkit.ogc.xml.v200.PropertyIsEqualToType pis = new org.geotoolkit.ogc.xml.v200.PropertyIsEqualToType(new org.geotoolkit.ogc.xml.v200.LiteralType("$name"), "name", true);
                final org.geotoolkit.ogc.xml.v200.FilterType filter = new org.geotoolkit.ogc.xml.v200.FilterType(pis);
                final org.geotoolkit.wfs.xml.v200.QueryType query = new org.geotoolkit.wfs.xml.v200.QueryType(filter, types, "2.0.0");
                final QueryExpressionTextType queryEx = new QueryExpressionTextType("urn:ogc:def:queryLanguage:OGC-WFS::WFS_QueryExpression", null, types);
                final ObjectFactory factory = new ObjectFactory();
                queryEx.getContent().add(factory.createQuery(query));
                final StoredQueryDescriptionType des1 = new StoredQueryDescriptionType("nameQuery", "Name query" , "filter on name for samplingPoint", param, queryEx);
                descriptions.add(des1);
                final StoredQueries queries = new StoredQueries(descriptions);
                serviceBusiness.setExtraConfiguration("wfs", "test1", "StoredQueries.xml", queries, pool);

                EPSG_VERSION = CRS.getVersion("EPSG").toString();
                worker = new DefaultWFSWorker("test1");
                worker.setServiceUrl("http://geomatys.com/constellation/WS/");
                initialized = true;
            } catch (Exception ex) {
                Logger.getAnonymousLogger().log(Level.SEVERE, "error while initializing test", ex);
            }
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try {
            final ILayerBusiness layerBean = SpringHelper.getBean(ILayerBusiness.class);
            if (layerBean != null) {
                layerBean.removeAll();
            }
            final IServiceBusiness service = SpringHelper.getBean(IServiceBusiness.class);
            if (service != null) {
                service.deleteAll();
            }
            final IDataBusiness dataBean = SpringHelper.getBean(IDataBusiness.class);
            if (dataBean != null) {
                dataBean.deleteAll();
            }
            final IProviderBusiness provider = SpringHelper.getBean(IProviderBusiness.class);
            if (provider != null) {
                provider.removeAll();
            }
        } catch (ConfigurationException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, ex.getMessage());
        }
        ConfigDirectory.shutdownTestEnvironement("WFSWorkerTest");
        if (ds != null) {
            ds.shutdown();
        }
        if (ds2 != null) {
            ds2.shutdown();
        }

        if (worker != null) {
            worker.destroy();
        }

        File derbyLog = new File("derby.log");
        if (derbyLog.exists()) {
            derbyLog.delete();
        }
    }

    @Before
    public void setUp() throws Exception {
        featureWriter = new JAXPStreamFeatureWriter();
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * test the feature marshall
     *
     */
    @Test
    @Order(order=1)
    public void getCapabilitiesTest() throws Exception {
        final Marshaller marshaller = pool.acquireMarshaller();

        AcceptVersionsType acceptVersion = new AcceptVersionsType("1.1.0");
        SectionsType sections       = new SectionsType("featureTypeList");
        GetCapabilitiesType request = new GetCapabilitiesType(acceptVersion, sections, null, null, "WFS");

        WFSCapabilities result = worker.getCapabilities(request);


        StringWriter sw = new StringWriter();
        marshaller.marshal(result, sw);

        // try to fix an issue with variant generated prefix
        String resultCapa   = sw.toString();
        String gmlPrefix    = getGmlPrefix(resultCapa);
        String expectedCapa = getResourceString("org.constellation.wfs.xml.WFSCapabilities1-1-0-ftl.xml");
        expectedCapa = expectedCapa.replace("{gmlPrefix}", gmlPrefix);
        domCompare(sw.toString(), expectedCapa);

        request = new GetCapabilitiesType("WFS");
        result = worker.getCapabilities(request);

        sw = new StringWriter();
        marshaller.marshal(result, sw);

        resultCapa   = sw.toString();
        gmlPrefix    = getGmlPrefix(resultCapa);
        expectedCapa = getResourceString("org.constellation.wfs.xml.WFSCapabilities1-1-0.xml");
        expectedCapa = expectedCapa.replace("{gmlPrefix}", gmlPrefix);
        domCompare(sw.toString(),expectedCapa);

        acceptVersion = new AcceptVersionsType("2.3.0");
        request = new GetCapabilitiesType(acceptVersion, null, null, null, "WFS");

        try {
            worker.getCapabilities(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), VERSION_NEGOTIATION_FAILED);
            assertEquals(ex.getLocator(), "version");
        }

        request = new GetCapabilitiesType(acceptVersion, null, null, null, "WPS");

        try {
            worker.getCapabilities(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
            assertEquals(ex.getLocator(), "service");
        }

        request = new GetCapabilitiesType(null);

        try {
            worker.getCapabilities(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), MISSING_PARAMETER_VALUE);
            assertEquals(ex.getLocator(), "service");
        }

        acceptVersion = new AcceptVersionsType("1.1.0");
        sections      = new SectionsType("operationsMetadata");
        request       = new GetCapabilitiesType(acceptVersion, sections, null, null, "WFS");

        result = worker.getCapabilities(request);


        sw = new StringWriter();
        marshaller.marshal(result, sw);
        resultCapa   = sw.toString();
        gmlPrefix    = getGmlPrefix(resultCapa);
        expectedCapa   = getResourceString("org.constellation.wfs.xml.WFSCapabilities1-1-0-om.xml");
        expectedCapa = expectedCapa.replace("{gmlPrefix}", gmlPrefix);
        domCompare(sw.toString(),expectedCapa);

        acceptVersion = new AcceptVersionsType("1.1.0");
        sections      = new SectionsType("serviceIdentification");
        request       = new GetCapabilitiesType(acceptVersion, sections, null, null, "WFS");

        result = worker.getCapabilities(request);


        sw = new StringWriter();
        marshaller.marshal(result, sw);
        resultCapa   = sw.toString();
        gmlPrefix    = getGmlPrefix(resultCapa);
        expectedCapa = getResourceString("org.constellation.wfs.xml.WFSCapabilities1-1-0-si.xml");
        expectedCapa = expectedCapa.replace("{gmlPrefix}", gmlPrefix);
        domCompare(sw.toString(),expectedCapa);

        acceptVersion = new AcceptVersionsType("1.1.0");
        sections      = new SectionsType("serviceProvider");
        request       = new GetCapabilitiesType(acceptVersion, sections, null, null, "WFS");

        result = worker.getCapabilities(request);


        sw = new StringWriter();
        marshaller.marshal(result, sw);
        resultCapa   = sw.toString();
        gmlPrefix    = getGmlPrefix(resultCapa);
        expectedCapa = getResourceString("org.constellation.wfs.xml.WFSCapabilities1-1-0-sp.xml");
        expectedCapa = expectedCapa.replace("{gmlPrefix}", gmlPrefix);
        domCompare(sw.toString(),expectedCapa);

        pool.recycle(marshaller);
    }

    /**
     * test the Getfeature operations with bad parameter causing exception return
     *
     */
    @Test
    @Order(order=2)
    public void getFeatureErrorTest() throws Exception {
        /**
         * Test 1 : empty query => error
         */
        GetFeatureType request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, null, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object result = null;
        try {
            result = worker.getFeature(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            //ok
        }

        /**
         * Test 2 : bad version => error
         */
        request = new GetFeatureType("WFS", "1.2.0", null, Integer.MAX_VALUE, null, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        try {
            result = worker.getFeature(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
            assertEquals(ex.getLocator(), "version");
        }
    }

     /**
     * test the feature marshall
     *
     */
    @Test
    @Order(order=3)
    public void getFeatureOMTest() throws Exception {

        /**
         * Test 1 : query on typeName samplingPoint
         */
        List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null));
        GetFeatureType request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        StringWriter writer = new StringWriter();
        featureWriter.write(result,writer);

        String expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-3.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        String sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);

        /**
         * Test 2 : query on typeName samplingPoint whith HITS result type
         */
        queries = new ArrayList<>();
        QueryType query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null);
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.HITS, "text/gml; subtype=\"gml/3.1.1\"");

        FeatureCollectionType resultHits = (FeatureCollectionType) worker.getFeature(request);

        assertTrue(resultHits.getNumberOfFeatures() == 6);


        /**
         * Test 3 : query on typeName samplingPoint with propertyName = {gml:name}
         */
        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null);
        query.getPropertyNameOrXlinkPropertyNameOrFunction().add("gml:name");
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-5.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);


        /**
         * Test 4 : query on typeName samplingPoint whith a filter name = 10972X0137-PONT
         */
        queries = new ArrayList<>();
        ComparisonOpsType pe = new PropertyIsEqualToType(new LiteralType("10972X0137-PONT"), new PropertyNameType("name"), Boolean.TRUE);
        FilterType filter = new FilterType(pe);
        queries.add(new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-4.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);

        /**
         * Test 5 : query on typeName samplingPoint whith a filter xpath //gml:name = 10972X0137-PONT
         */
        queries = new ArrayList<>();
        pe = new PropertyIsEqualToType(new LiteralType("10972X0137-PONT"), new PropertyNameType("//{http://www.opengis.net/gml}name"), Boolean.TRUE);
        filter = new FilterType(pe);
        queries.add(new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-4.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);

        /**
         * Test 6 : query on typeName samplingPoint whith a spatial filter BBOX
         */
        queries = new ArrayList<>();
        SpatialOpsType bbox = new BBOXType("{http://www.opengis.net/sampling/1.0}position", 65300.0, 1731360.0, 65500.0, 1731400.0, "urn:ogc:def:crs:epsg:7.6:27582");
        filter = new FilterType(bbox);
        queries.add(new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

       result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-8.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);

        /**
         * Test 7 : query on typeName samplingPoint whith a spatial filter BBOX () with no namespace
         */
        queries = new ArrayList<>();
        bbox = new BBOXType("position", 65300.0, 1731360.0, 65500.0, 1731400.0, "urn:ogc:def:crs:epsg:7.6:27582");
        filter = new FilterType(bbox);
        queries.add(new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-8.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);


        /**
         * Test 8 : query on typeName samplingPoint with sort on gml:name
         */

        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null);
        query.setSortBy(new SortByType(Arrays.asList(new SortPropertyType("http://www.opengis.net/gml:name", SortOrderType.ASC))));
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-6.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);


        /**
         * Test 9 : query on typeName samplingPoint with sort on gml:name
         */
        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null);
        query.setSortBy(new SortByType(Arrays.asList(new SortPropertyType("http://www.opengis.net/gml:name", SortOrderType.DESC))));
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        expectedResult = getResourceString("org.constellation.wfs.xml.samplingPointCollection-7.xml");
        expectedResult = expectedResult.replace("EPSG_VERSION", EPSG_VERSION);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,expectedResult);

        /**
         * Test 10 : query on typeName samplingPoint whith HITS result type
         */
        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null);
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.HITS, "text/gml; subtype=\"gml/3.1.1\"");

        resultHits = (FeatureCollectionType) worker.getFeature(request);

        assertTrue(resultHits.getNumberOfFeatures() == 6);


        /**
         * Test 11 : query on typeName samplingPoint whith a filter with unexpected property
         */

        queries = new ArrayList<>();
        pe = new PropertyIsEqualToType(new LiteralType("whatever"), new PropertyNameType("wrongProperty"), Boolean.TRUE);
        filter = new FilterType(pe);
        queries.add(new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        try {
            worker.getFeature(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            //ok
        }

        /**
         * Test 12 : query on typeName samplingPoint whith a an unexpected property in propertyNames
         */

        queries = new ArrayList<>();
        query = new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint")), null);
        query.getPropertyNameOrXlinkPropertyNameOrFunction().add("wrongProperty");
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        try {
            worker.getFeature(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
        }
    }

    /**
     * test the feature marshall
     *
     */
    @Test
    @Order(order=5)
    public void getFeatureShapeFileTest() throws Exception {

        /**
         * Test 1 : query on typeName bridges
         */

        List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "Bridges")), null));
        GetFeatureType request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);


        StringWriter writer = new StringWriter();
        featureWriter.write(result,writer);


        String sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.bridgeCollection.xml")
                );

        /**
         * Test 2 : query on typeName bridges with propertyName = {FID}
         */
        queries = new ArrayList<>();
        QueryType query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "Bridges")), null);
        query.getPropertyNameOrXlinkPropertyNameOrFunction().add("FID");
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.bridgeCollection-2.xml")
                );

        /**
         * Test 3 : query on typeName NamedPlaces
         */

        queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.namedPlacesCollection-1.xml")
                );

        /**
         * Test 4 : query on typeName NamedPlaces with resultType = HITS
         */

        queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.HITS, "text/gml; subtype=\"gml/3.1.1\"");

        FeatureCollectionType resultHits = (FeatureCollectionType) worker.getFeature(request);worker.getFeature(request);

        assertTrue(resultHits.getNumberOfFeatures() == 2);

        /**
         * Test 5 : query on typeName NamedPlaces with srsName = EPSG:27582
         */

        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null);
        query.setSrsName("EPSG:27582");
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.namedPlacesCollection-1_reproj.xml")
                );

        /**
         * Test 6 : query on typeName NamedPlaces with DESC sortBy on NAME property (not supported)
         */

        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null);
        query.setSortBy(new SortByType(Arrays.asList(new SortPropertyType("NAME", SortOrderType.DESC))));
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);
        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.namedPlacesCollection-5.xml")
                );


        /**
         * Test 7 : query on typeName NamedPlaces with ASC sortBy on NAME property (not supported)
         */
        queries = new ArrayList<>();
        query = new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null);
        query.setSortBy(new SortByType(Arrays.asList(new SortPropertyType("NAME", SortOrderType.ASC))));
        queries.add(query);
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);
        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);

        writer = new StringWriter();
        featureWriter.write(result,writer);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.namedPlacesCollection-1.xml")
                );


    }

    @Test
    @Order(order=6)
    public void getFeatureMixedTest() throws Exception {
        /**
         * Test 1 : query on typeName bridges and NamedPlaces
         */

        List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "Bridges"), new QName("http://www.opengis.net/gml", "NamedPlaces")), null));
        GetFeatureType request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet();


        StringWriter writer = new StringWriter();
        featureWriter.write(result,writer);


        String sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.mixedCollection.xml")
                );
    }

    /**
     *
     *
     */
    @Test
    @Order(order=7)
    public void DescribeFeatureTest() throws Exception {
        Unmarshaller unmarshaller = XSDMarshallerPool.getInstance().acquireUnmarshaller();

        /**
         * Test 1 : describe Feature type bridges
         */
        List<QName> typeNames = new ArrayList<>();
        typeNames.add(new QName("http://www.opengis.net/gml", "Bridges"));
        DescribeFeatureTypeType request = new DescribeFeatureTypeType("WFS", "1.1.0", null, typeNames, "text/gml; subtype=\"gml/3.1.1\"");

        Schema result = (Schema) worker.describeFeatureType(request);

        Schema expResult = (Schema) unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/wfs/xsd/bridge.xsd"));

        // fix for equlity on empty list / null list
        for (ComplexType type : expResult.getComplexTypes()) {
            type.getAttributeOrAttributeGroup();
        }
        assertEquals(expResult, result);

        /**
         * Test 2 : describe Feature type Sampling point
         */
        typeNames = new ArrayList<>();
        typeNames.add(new QName("http://www.opengis.net/sampling/1.0", "SamplingPoint"));
        request = new DescribeFeatureTypeType("WFS", "1.1.0", null, typeNames, "text/gml; subtype=\"gml/3.1.1\"");

        result = (Schema) worker.describeFeatureType(request);

        expResult = (Schema) unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/wfs/xsd/sampling.xsd"));
        // fix for equlity on empty list / null list
        for (ComplexType type : expResult.getComplexTypes()) {
            type.getAttributeOrAttributeGroup();
        }

        assertEquals(expResult, result);

        XSDMarshallerPool.getInstance().recycle(unmarshaller);
    }

    /**
     *
     *
     */
    @Test
    @Order(order=8)
    public void TransactionUpdateTest() throws Exception {

        /**
         * Test 1 : transaction update for Feature type bridges with a bad inputFormat
         */

        QName typeName = new QName("http://www.opengis.net/gml", "Bridges");
        List<PropertyType> properties = new ArrayList<>();
        UpdateElementType update = new UpdateElementType(null, properties, null, typeName, null);
        update.setInputFormat("bad inputFormat");
        TransactionType request = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, update);


        try {
            worker.transaction(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
            assertEquals(ex.getLocator(), "inputFormat");
        }


        /**
         * Test 2 : transaction update for Feature type bridges with a bad property
         */

        typeName = new QName("http://www.opengis.net/gml", "Bridges");
        properties = new ArrayList<>();
        properties.add(new PropertyType(new QName("whatever"), new ValueType("someValue")));
        request = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, new UpdateElementType(null, properties, null, typeName, null));


        try {
            worker.transaction(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_VALUE);
            assertEquals(ex.getMessage(), "The feature Type {http://www.opengis.net/gml}Bridges has no such property: whatever");
        }


        /**
         * Test 3 : transaction update for Feature type bridges with a bad property in filter
         */

        typeName = new QName("http://www.opengis.net/gml", "Bridges");
        properties = new ArrayList<>();
        properties.add(new PropertyType(new QName("NAME"), new ValueType("someValue")));
        ComparisonOpsType pe     = new PropertyIsEqualToType(new LiteralType("10972X0137-PONT"), new PropertyNameType("bad"), Boolean.TRUE);
        FilterType filter        = new FilterType(pe);
        request = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, new UpdateElementType(null, properties, filter, typeName, null));


        try {
            worker.transaction(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
            assertEquals(ex.getMessage(), "The feature Type {http://www.opengis.net/gml}Bridges has no such property: bad");
        }

        /**
         * Test 4 : transaction update for Feature type NamedPlaces with a property in filter
         */

        typeName = new QName("http://www.opengis.net/gml", "NamedPlaces");
        properties = new ArrayList<>();
        properties.add(new PropertyType(new QName("FID"), new ValueType("999")));
        pe     = new PropertyIsEqualToType(new LiteralType("Ashton"), new PropertyNameType("NAME"), Boolean.TRUE);
        filter = new FilterType(pe);
        request = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, new UpdateElementType(null, properties, filter, typeName, null));


        TransactionResponse result = worker.transaction(request);

        TransactionSummaryType sum = new TransactionSummaryType(0, 1, 0);
        TransactionResponseType ExpResult = new TransactionResponseType(sum, null, null, "1.1.0");

        assertEquals(ExpResult, result);

        /**
         * we verify that the feature have been updated
         */
         List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null));
        GetFeatureType requestGF = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object resultGF = worker.getFeature(requestGF);

        assertTrue(resultGF instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) resultGF;
        resultGF = wrapper.getFeatureSet().get(0);

        StringWriter writer = new StringWriter();
        featureWriter.write(resultGF,writer);

        String sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.namedPlacesCollection-3.xml")
                );

    }

    @Test
    @Order(order=9)
    public void TransactionDeleteTest() throws Exception {

        /**
         * Test 1 : transaction delete for Feature type bridges with a bad property in filter
         */
        QName typeName           = new QName("http://www.opengis.net/gml", "Bridges");
        ComparisonOpsType pe     = new PropertyIsEqualToType(new LiteralType("10972X0137-PONT"), new PropertyNameType("bad"), Boolean.TRUE);
        FilterType filter        = new FilterType(pe);
        DeleteElementType delete = new DeleteElementType(filter, null, typeName);
        TransactionType request  = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, delete);

        try {
            worker.transaction(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
            assertEquals(ex.getMessage(), "The feature Type {http://www.opengis.net/gml}Bridges has no such property: bad");
        }


        /**
         * Test 2 : transaction delete for Feature type NamedPlaces with a property in filter
         */
        typeName = new QName("http://www.opengis.net/gml", "NamedPlaces");
        pe       = new PropertyIsEqualToType(new LiteralType("Ashton"), new PropertyNameType("NAME"), Boolean.TRUE);
        filter   = new FilterType(pe);
        delete   = new DeleteElementType(filter, null, typeName);
        request  = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, delete);

        TransactionResponse result = worker.transaction(request);

        TransactionSummaryType sum = new TransactionSummaryType(0, 0, 1);
        TransactionResponseType expresult = new TransactionResponseType(sum, null, null, "1.1.0");

        assertEquals(expresult, result);

        /**
         * we verify that the feature have been deleted
         */
        List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null));
        GetFeatureType requestGF = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object resultGF = worker.getFeature(requestGF);

        assertTrue(resultGF instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) resultGF;
        resultGF = wrapper.getFeatureSet().get(0);

        StringWriter writer = new StringWriter();
        featureWriter.write(resultGF,writer);

        String sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(sresult,
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.namedPlacesCollection-2.xml")
                );
    }
    /**
     *
     *
     */
    @Test
    @Order(order=10)
    public void TransactionInsertTest() throws Exception {

        /**
         * Test 1 : transaction insert for Feature type bridges with a bad inputFormat
         */

        final QName typeName = new QName("http://www.opengis.net/gml", "Bridges");
        final InsertElementType insert = new InsertElementType();
        insert.setInputFormat("bad inputFormat");
        final TransactionType request = new TransactionType("WFS", "1.1.0", null, AllSomeType.ALL, insert);

        try {
            worker.transaction(request);
            fail("Should have raised an error.");
        } catch (CstlServiceException ex) {
            assertEquals(ex.getExceptionCode(), INVALID_PARAMETER_VALUE);
            assertEquals(ex.getLocator(), "inputFormat");
        }
    }

    @Test
    @Order(order=11)
    public void schemaLocationTest() throws Exception {
        List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "NamedPlaces")), null));
        GetFeatureType requestGF = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object resultGF = worker.getFeature(requestGF);

        assertTrue(resultGF instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) resultGF;

        final Map<String, String> expResult = new HashMap<>();
        expResult.put("http://www.opengis.net/gml", "http://geomatys.com/constellation/WS/wfs/test1?request=DescribeFeatureType&version=1.1.0&service=WFS&namespace=xmlns(ns1=http://www.opengis.net/gml)&typename=ns1:NamedPlaces");
        assertEquals(wrapper.getSchemaLocations(), expResult);

    }

    @Test
    @Order(order=12)
    public void getFeatureGJsonTest() throws Exception {

        /*
         * Test 1 : query on typeName feature
         */
        List<QueryType> queries = new ArrayList<>();
        queries.add(new QueryType(null, Arrays.asList(new QName("http://www.opengis.net/gml", "feature")), null));
        GetFeatureType request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        Object result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        FeatureSetWrapper wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);
        assertEquals("3.1.1", wrapper.getGmlVersion());

        StringWriter writer = new StringWriter();
        featureWriter.write(result,writer);

        String sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(IOUtilities.getResourceAsPath("org.constellation.wfs.xml.feature-1.xml"),
                sresult);

        /*
         * Test 2 : query on typeName featureCollection with propertyName = {FID}
         */
        queries = new ArrayList<>();
        ComparisonOpsType pe = new PropertyIsEqualToType(new LiteralType("DOUBLE OAKS CENTER"), new PropertyNameType("name"), Boolean.TRUE);
        FilterType filter = new FilterType(pe);
        queries.add(new QueryType(filter, Arrays.asList(new QName("http://www.opengis.net/gml", "featureCollection")), null));
        request = new GetFeatureType("WFS", "1.1.0", null, Integer.MAX_VALUE, queries, ResultTypeType.RESULTS, "text/gml; subtype=\"gml/3.1.1\"");

        result = worker.getFeature(request);

        assertTrue(result instanceof FeatureSetWrapper);
        wrapper = (FeatureSetWrapper) result;
        result = wrapper.getFeatureSet().get(0);
        assertEquals("3.1.1", wrapper.getGmlVersion());

        writer = new StringWriter();
        featureWriter.write(result,writer);

        sresult = writer.toString();
        sresult = sresult.replaceAll("timeStamp=\"[^\"]*\" ", "timeStamp=\"\" ");

        domCompare(
                IOUtilities.getResourceAsPath("org.constellation.wfs.xml.featureCollection-1.xml"),
                sresult);

    }

    public static void domCompare(final Object actual, final Object expected) throws Exception {

        String expectedStr;
        if (expected instanceof Path) {
            expectedStr = IOUtilities.toString((Path)expected);
        } else {
            expectedStr = (String) expected;
        }
        expectedStr = expectedStr.replace("EPSG_VERSION", EPSG_VERSION);

        final CstlDOMComparator comparator = new CstlDOMComparator(expectedStr, actual);
        comparator.ignoredAttributes.add("http://www.w3.org/2000/xmlns:*");
        comparator.ignoredAttributes.add("http://www.w3.org/2001/XMLSchema-instance:schemaLocation");
        comparator.compare();
    }

    private static String getGmlPrefix(final String xml) {
        Pattern p = Pattern.compile("xmlns:([^=]+)=\"http://www.opengis.net/gml\"");
        Matcher matcher = p.matcher(xml);
        if (matcher.find())
        {
            return matcher.group(1);
        }
        return null;
    }

    private String getResourceString(String resource) throws IOException, URISyntaxException {
        return IOUtilities.toString(IOUtilities.getResourceAsPath(resource));
    }
}
