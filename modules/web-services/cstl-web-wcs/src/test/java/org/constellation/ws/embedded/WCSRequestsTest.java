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
package org.constellation.ws.embedded;

// J2SE dependencies
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.spi.ImageWriterSpi;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import org.constellation.admin.SpringHelper;
import org.constellation.exception.ConstellationException;
import org.constellation.api.ProviderType;
import org.constellation.business.IDataBusiness;
import org.constellation.business.ILayerBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.service.config.wxs.LayerContext;
import org.constellation.provider.DataProviders;
import org.constellation.provider.DataProviderFactory;
import org.constellation.provider.ProviderParameters;
import org.constellation.provider.datastore.DataStoreProviderService;
import org.constellation.test.ImageTesting;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.TestRunner;
import org.geotoolkit.util.NamesExt;
import org.geotoolkit.image.io.plugin.WorldFileImageReader;
import org.geotoolkit.image.jai.Registry;
import org.geotoolkit.ogc.xml.exception.ServiceExceptionReport;
import org.geotoolkit.ows.xml.v110.ExceptionReport;
import org.geotoolkit.wcs.xml.WCSMarshallerPool;
import org.geotoolkit.wcs.xml.v100.CoverageDescription;
import org.geotoolkit.wcs.xml.v100.CoverageOfferingBriefType;
import org.geotoolkit.wcs.xml.v100.CoverageOfferingType;
import org.geotoolkit.wcs.xml.v100.DCPTypeType.HTTP.Get;
import org.geotoolkit.wcs.xml.v100.LonLatEnvelopeType;
import org.geotoolkit.wcs.xml.v100.WCSCapabilitiesType;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.util.GenericName;

// JUnit dependencies

/**
 * A set of methods that request a Grizzly server which embeds a WCS service.
 *
 * @version $Id$
 *
 * @author Cédric Briançon (Geomatys)
 * @since 0.3
 */
@RunWith(TestRunner.class)
public class WCSRequestsTest extends AbstractGrizzlyServer {


    /**
     * The layer to test.
     */
    private static final GenericName LAYER_TEST = NamesExt.create("SSTMDE200305");

    /**
     * URLs which will be tested on the server.
     */
    private static final String WCS_FALSE_REQUEST ="request=SomethingElse";

    private static final String WCS_FALSE_REQUEST_100 ="request=GetCoverage&service=WCS&version=1.0.0&" +
                                      "format=image/png&width=1024&height=512&" +
                                      "crs=EPSG:4326&bbox=-180,-90,180,90&" +
                                      "coverage=wrongLayer";

    private static final String WCS_FALSE_REQUEST_111 ="request=GetCoverage&service=WCS&version=1.1.1&" +
                                      "format=image/png&width=1024&height=512&" +
                                      "crs=EPSG:4326&boundingbox=-180,-90,180,90,EPSG4326&" +
                                      "identifier=wrongLayer";

    private static final String WCS_GETCOVERAGE ="request=GetCoverage&service=WCS&version=1.0.0&" +
                                      "format=image/png&width=1024&height=512&" +
                                      "crs=EPSG:4326&bbox=-180,-90,180,90&" +
                                      "coverage="+ LAYER_TEST;

    private static final String WCS_GETCOVERAGE_MATRIX ="request=GetCoverage&service=WCS&version=1.0.0&" +
                                      "format=matrix&width=1024&height=512&" +
                                      "crs=EPSG:4326&bbox=-180,-90,180,90&" +
                                      "coverage="+ LAYER_TEST;

    private static final String WCS_GETCAPABILITIES ="request=GetCapabilities&service=WCS&version=1.0.0";

    private static final String WCS_GETCAPABILITIES2 ="request=GetCapabilities&service=WCS&version=1.0.0";

    private static final String WCS_DESCRIBECOVERAGE ="request=DescribeCoverage&coverage=SSTMDE200305&service=wcs&version=1.0.0";

    private static boolean initialized = false;

    @BeforeClass
    public static void initTestDir() {
        ConfigDirectory.setupTestEnvironement("WCSRequestsTest");
        controllerConfiguration = WCSControllerConfig.class;
    }

    /**
     * Initialize the list of layers from the defined providers in Constellation's configuration.
     */
    public void initLayerList() {

        if (!initialized) {
            try {
                startServer(null);

                layerBusiness.removeAll();
                serviceBusiness.deleteAll();
                dataBusiness.deleteAll();
                providerBusiness.removeAll();

                final File outputDir = initDataDirectory();

                final DataProviderFactory covFilefactory = DataProviders.getFactory("data-store");
                final ParameterValueGroup sourceCF = covFilefactory.getProviderDescriptor().createValue();
                sourceCF.parameter("id").setValue("coverageTestSrc");
                final ParameterValueGroup choice3 = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourceCF);

                final ParameterValueGroup srcCFConfig = choice3.addGroup("FileCoverageStoreParameters");

                srcCFConfig.parameter("path").setValue(new URL("file:" + outputDir.getAbsolutePath() + "/org/constellation/data/SSTMDE200305.png"));
                srcCFConfig.parameter("type").setValue("AUTO");

                providerBusiness.storeProvider("coverageTestSrc", null, ProviderType.LAYER, "data-store", sourceCF);

                dataBusiness.create(new QName("SSTMDE200305"), "coverageTestSrc", "COVERAGE", false, true, null, null);

                final LayerContext config = new LayerContext();

                Integer defId = serviceBusiness.create("wcs", "default", config, null, null);
                layerBusiness.add("SSTMDE200305", null, "coverageTestSrc", null, "default", "wcs", null);

                Integer testId = serviceBusiness.create("wcs", "test", config, null, null);
                layerBusiness.add("SSTMDE200305", null, "coverageTestSrc", null, "test",    "wcs", null);


                pool = WCSMarshallerPool.getInstance();

                WorldFileImageReader.Spi.registerDefaults(null);

                //reset values, only allow pure java readers
                for(String jn : ImageIO.getReaderFormatNames()){
                    Registry.setNativeCodecAllowed(jn, ImageReaderSpi.class, false);
                }

                //reset values, only allow pure java writers
                for(String jn : ImageIO.getWriterFormatNames()){
                    Registry.setNativeCodecAllowed(jn, ImageWriterSpi.class, false);
                }

                serviceBusiness.start(defId);
                serviceBusiness.start(testId);
                waitForRestStart("wcs","default");
                waitForRestStart("wcs","test");

                initialized = true;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    @AfterClass
    public static void shutDown() throws JAXBException {
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
        } catch (ConstellationException ex) {
            Logger.getAnonymousLogger().log(Level.WARNING, ex.getMessage());
        }
        ConfigDirectory.shutdownTestEnvironement("WCSRequestsTest");
        stopServer();
    }

    /**
     * Ensure that a wrong value given in the request parameter for the WCS server
     * returned an error report for the user.
     */
    @Test
    @Order(order=1)
    public void testWCSWrongRequest() throws Exception {

        initLayerList();

        // Creates an intentional wrong url, regarding the WCS version 1.0.0 standard
        URL wrongUrl;
        try {
            wrongUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_FALSE_REQUEST);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a ServiceExceptionReport.
        Object obj = unmarshallResponse(wrongUrl);
        assertTrue(obj instanceof ServiceExceptionReport);

        try {
            wrongUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_FALSE_REQUEST_100);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a ServiceExceptionReport.
        obj = unmarshallResponse(wrongUrl);
        assertTrue(obj instanceof ServiceExceptionReport);

        try {
            wrongUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_FALSE_REQUEST_111);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a OWS ExceptionReport.
        obj = unmarshallResponse(wrongUrl);
        assertTrue("exception type:" + obj.getClass().getName(), obj instanceof ExceptionReport);
    }

    /**
     * Ensures that a valid GetCoverage request returns indeed a {@link BufferedImage}.
     */
    @Test
    @Order(order=2)
    public void testWCSGetCoverage() throws Exception {
        // Creates a valid GetCoverage url.
        final URL getCoverageUrl;
        try {
            getCoverageUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_GETCOVERAGE);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to get the coverage from the url.
        final BufferedImage image = getImageFromURL(getCoverageUrl, "image/png");

        // Test on the returned image.
        assertFalse (ImageTesting.isImageEmpty(image));
        assertEquals(1024, image.getWidth());
        assertEquals(512,  image.getHeight());
        assertTrue  (ImageTesting.getNumColors(image) > 8);
    }

    /**
     * Ensures a GetCoverage request with the output format matrix works fine.
     *
     * For now, this format is not well handled by the current Geotools. There are some
     * errors in the reading of this format, and they will be corrected in the next version
     * of Geotools.
     *
     * @TODO: do this test when moving of Geotools' version
     */
    @Ignore
    @Order(order=3)
    public void testWCSGetCoverageMatrixFormat() throws Exception {
        // Creates a valid GetCoverage url.
        final URL getCovMatrixUrl;
        try {
            getCovMatrixUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_GETCOVERAGE_MATRIX);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        final BufferedImage image = getImageFromURL(getCovMatrixUrl, "application/matrix");
        //assertEquals(Commons.checksum(image), ...);
    }

    /**
     * Ensures that a valid GetCapabilities request returns indeed a valid GetCapabilities
     * document representing the server capabilities in the WCS version 1.0.0 standard.
     */
    @Test
    @Order(order=4)
    public void testWCSGetCapabilities() throws Exception {
        // Creates a valid GetCapabilities url.
        URL getCapsUrl;
        try {
            getCapsUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_GETCAPABILITIES);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a WCSCapabilitiesType.
        Object obj = unmarshallResponse(getCapsUrl);
        assertTrue(obj.toString(), obj instanceof WCSCapabilitiesType);

        WCSCapabilitiesType responseCaps = (WCSCapabilitiesType)obj;
        final List<CoverageOfferingBriefType> coverages = responseCaps.getContentMetadata().getCoverageOfferingBrief();

        assertNotNull(coverages);
        assertFalse(coverages.isEmpty());
        boolean layerTestFound = false;
        for (CoverageOfferingBriefType coverage : coverages) {
            for (JAXBElement<String> elem : coverage.getRest()) {
                if (elem.getValue().equals(LAYER_TEST.tip().toString())) {
                    layerTestFound = true;
                    final LonLatEnvelopeType env = coverage.getLonLatEnvelope();
                    assertTrue(env.getPos().get(0).getValue().get(0) == -180d);
                    assertTrue(env.getPos().get(0).getValue().get(1) ==  -90d);
                    assertTrue(env.getPos().get(1).getValue().get(0) ==  180d);
                    assertTrue(env.getPos().get(1).getValue().get(1) ==   90d);
                }
            }
        }
        if (layerTestFound == false) {
            throw new AssertionError("The layer \""+ LAYER_TEST +"\" was not found in the returned GetCapabilities.");
        }

        Get get = (Get) responseCaps.getCapability().getRequest().getGetCapabilities().getDCP().get(0).getHTTP().getRealGetOrPost().get(0);
        assertEquals("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?", get.getOnlineResource().getHref());

        try {
            getCapsUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/test?SERVICE=WCS&" + WCS_GETCAPABILITIES2);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a WCSCapabilitiesType.
        obj = unmarshallResponse(getCapsUrl);
        assertTrue(obj instanceof WCSCapabilitiesType);

        responseCaps = (WCSCapabilitiesType)obj;

        get = (Get) responseCaps.getCapability().getRequest().getGetCapabilities().getDCP().get(0).getHTTP().getRealGetOrPost().get(0);
        assertEquals("http://localhost:"+ getCurrentPort() + "/WS/wcs/test?", get.getOnlineResource().getHref());


        try {
            getCapsUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_GETCAPABILITIES);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a WCSCapabilitiesType.
        obj = unmarshallResponse(getCapsUrl);
        assertTrue(obj instanceof WCSCapabilitiesType);

        responseCaps = (WCSCapabilitiesType)obj;

        get = (Get) responseCaps.getCapability().getRequest().getGetCapabilities().getDCP().get(0).getHTTP().getRealGetOrPost().get(0);
        assertEquals("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?", get.getOnlineResource().getHref());
    }

    /**
     * Ensures that a valid DescribeCoverage request returns indeed a valid document.
     */
    @Test
    @Order(order=5)
    public void testWCSDescribeCoverage() throws Exception {
        // Creates a valid DescribeCoverage url.
        final URL getCapsUrl;
        try {
            getCapsUrl = new URL("http://localhost:"+ getCurrentPort() + "/WS/wcs/default?SERVICE=WCS&" + WCS_DESCRIBECOVERAGE);
        } catch (MalformedURLException ex) {
            assumeNoException(ex);
            return;
        }

        // Try to marshall something from the response returned by the server.
        // The response should be a WCSCapabilitiesType.
        final Object obj = unmarshallResponse(getCapsUrl);
        assertTrue(obj instanceof CoverageDescription);

        final CoverageDescription responseDesc = (CoverageDescription)obj;
        assertNotNull(responseDesc);
        final List<CoverageOfferingType> coverageOffs = responseDesc.getCoverageOffering();
        assertFalse (coverageOffs.isEmpty());
        assertEquals(LAYER_TEST.tip().toString(), coverageOffs.get(0).getRest().get(1).getValue());
        // TODO: add more tests on returned XML doc
    }

}
