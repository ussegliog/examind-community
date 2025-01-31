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


package org.constellation.metadata;


import java.util.Arrays;
import org.constellation.metadata.core.CSWworker;
import java.util.logging.Level;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import org.constellation.admin.SpringHelper;
import org.constellation.business.IMetadataBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.service.config.generic.Automatic;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.SpringTestRunner;
import org.constellation.util.Util;
import org.geotoolkit.ebrim.xml.EBRIMMarshallerPool;
import org.geotoolkit.xml.AnchoredMarshallerPool;
import org.apache.sis.util.logging.Logging;
import org.constellation.dto.contact.AccessConstraint;
import org.constellation.dto.contact.Contact;
import org.constellation.dto.contact.Details;
import org.constellation.metadata.configuration.CSWConfigurer;
import org.constellation.util.NodeUtilities;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.w3c.dom.Node;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@RunWith(SpringTestRunner.class)
public class InternalCSWworker3Test extends CSWWorker3Test {

    @Inject
    private IServiceBusiness serviceBusiness;

    @Inject
    private IProviderBusiness providerBusiness;

    @Inject
    private IMetadataBusiness metadataBusiness;

    private static boolean initialized = false;

    @BeforeClass
    public static void initTestDir() {
        ConfigDirectory.setupTestEnvironement("InternalCSWWorkerTest");
    }

    @PostConstruct
    public void setUpClass() {
        try {
            if (!initialized) {
                serviceBusiness.deleteAll();
                providerBusiness.removeAll();
                metadataBusiness.deleteAllMetadata();

                int internalProviderID = metadataBusiness.getDefaultInternalProviderID();
                pool = EBRIMMarshallerPool.getInstance();
                fillPoolAnchor((AnchoredMarshallerPool) pool);

                //we write the data files
                writeMetadata("meta1.xml",         "42292_5p_19900609195600", internalProviderID);
                writeMetadata("meta2.xml",         "42292_9s_19900610041000", internalProviderID);
                writeMetadata("meta3.xml",         "39727_22_19750113062500", internalProviderID);
                writeMetadata("meta4.xml",         "11325_158_19640418141800", internalProviderID);
                writeMetadata("meta5.xml",         "40510_145_19930221211500", internalProviderID);
                writeMetadata("meta-19119.xml",    "mdweb_2_catalog_CSW Data Catalog_profile_inspire_core_service_4", internalProviderID);
                writeMetadata("imageMetadata.xml", "gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX", internalProviderID);
                writeMetadata("ebrim1.xml",        "000068C3-3B49-C671-89CF-10A39BB1B652", internalProviderID);
                writeMetadata("ebrim2.xml",        "urn:uuid:3e195454-42e8-11dd-8329-00e08157d076", internalProviderID);
                writeMetadata("ebrim3.xml",        "urn:motiive:csw-ebrim", internalProviderID);
                writeMetadata("meta13.xml",        "urn:uuid:1ef30a8b-876d-4828-9246-dcbbyyiioo", internalProviderID);

                // add DIF metadata
                writeMetadata("NO.009_L2-SST.xml", "L2-SST", internalProviderID);
                writeMetadata("NO.021_L2-LST.xml", "L2-LST", internalProviderID);

                writeMetadata("meta7.xml",         "MDWeb_FR_SY_couche_vecteur_258", internalProviderID, true);

                //we write the configuration file
                Automatic configuration = new Automatic();
                configuration.putParameter("transactionSecurized", "false");

                Details d = new Details("Constellation CSW Server", "default", Arrays.asList("CS-W"),
                                        "CS-W 2.0.2/AP ISO19115/19139 for service, datasets and applications",
                                        Arrays.asList("2.0.0", "2.0.2", "3.0.0"),
                                        new Contact(), new AccessConstraint(),
                                        true, "eng");
                serviceBusiness.create("csw", "default", configuration, d, null);
                serviceBusiness.linkCSWAndProvider("default", "default-internal-metadata");

                worker = new CSWworker("default");
                initialized = true;
            }
        } catch (Exception ex) {
            Logging.getLogger("org.constellation.metadata").log(Level.SEVERE, null, ex);
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (worker != null) {
            worker.destroy();
        }
        CSWConfigurer configurer = SpringHelper.getBean(CSWConfigurer.class);
        configurer.removeIndex("default");
        final IServiceBusiness service = SpringHelper.getBean(IServiceBusiness.class);
        if (service != null) {
            service.deleteAll();
        }
        final IProviderBusiness provider = SpringHelper.getBean(IProviderBusiness.class);
        if (provider != null) {
            provider.removeAll();
        }
        final IMetadataBusiness mdService = SpringHelper.getBean(IMetadataBusiness.class);
        if (mdService != null) {
            mdService.deleteAllMetadata();
        }
        ConfigDirectory.shutdownTestEnvironement("InternalCSWWorkerTest");
    }

    /**
     * Tests the getcapabilities method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=1)
    public void getCapabilitiesTest() throws Exception {
        super.getCapabilitiesTest();
    }

    /**
     * Tests the getcapabilities method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=2)
    public void getRecordByIdTest() throws Exception {
        super.getRecordByIdTest();
    }

    /**
     * Tests the getcapabilities method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=3)
    public void getRecordByIdErrorTest() throws Exception {
        super.getRecordByIdErrorTest();
    }

    /**
     * Tests the getRecords method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=4)
    public void getRecordsTest() throws Exception {
        super.getRecordsTest();
    }

    @Test
    @Override
    @Order(order=5)
    public void getRecordsSpatialTest() throws Exception {
        super.getRecordsSpatialTest();
    }

    @Test
    @Override
    @Order(order=6)
    public void getRecords191152Test() throws Exception {
        super.getRecords191152Test();
    }

    @Test
    @Override
    @Order(order=7)
    public void getRecordsDIFTest() throws Exception {
        super.getRecordsDIFTest();
    }

    /**
     * Tests the getRecords method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=8)
    public void getRecordsErrorTest() throws Exception {
        super.getRecordsErrorTest();
    }

    /**
     * Tests the getDomain method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=9)
    public void getDomainTest() throws Exception {
        super.getDomainTest();
    }

    /**
     * Tests the transaction method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=10)
    public void transactionDeleteInsertTest() throws Exception {
        super.transactionDeleteInsertTest();
    }

    /**
     * Tests the transaction method
     *
     * @throws java.lang.Exception
     */
    @Test
    @Override
    @Order(order=11)
    public void transactionUpdateTest() throws Exception {
        typeCheckUpdate = false;
        super.transactionUpdateTest();

    }

    public void writeMetadata(String resourceName, String identifier, Integer providerID) throws Exception {
        writeMetadata(resourceName, identifier, providerID, false);
    }

    public void writeMetadata(String resourceName, String identifier, Integer providerID, boolean hidden) throws Exception {
       Node node  = NodeUtilities.getNodeFromStream(Util.getResourceAsStream("org/constellation/xml/metadata/" + resourceName));
       metadataBusiness.updateMetadata(identifier, node, null, null, null, null, providerID, "DOC", null, hidden);
    }
}
