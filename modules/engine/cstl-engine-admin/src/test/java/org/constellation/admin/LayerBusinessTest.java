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
package org.constellation.admin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.xml.namespace.QName;
import org.apache.sis.util.logging.Logging;
import static org.constellation.admin.DataBusinessTest.initDataDirectory;
import org.constellation.api.ProviderType;
import org.constellation.business.IDataBusiness;
import org.constellation.business.IDatasetBusiness;
import org.constellation.business.ILayerBusiness;
import org.constellation.business.IMapContextBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.DataBrief;
import org.constellation.dto.contact.AccessConstraint;
import org.constellation.dto.contact.Contact;
import org.constellation.dto.contact.Details;
import org.constellation.dto.service.config.wxs.LayerContext;
import org.constellation.exception.ConstellationException;
import org.constellation.provider.DataProviderFactory;
import org.constellation.provider.DataProviders;
import org.constellation.provider.ProviderParameters;
import org.constellation.provider.datastore.DataStoreProviderService;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.SpringTestRunner;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengis.parameter.ParameterValueGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@RunWith(SpringTestRunner.class)
@ContextConfiguration("classpath:/cstl/spring/test-context.xml")
public class LayerBusinessTest {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.admin");

    @Autowired
    private IDatasetBusiness datasetBusiness;

    @Autowired
    private IDataBusiness dataBusiness;

    @Autowired
    private ILayerBusiness layerBusiness;

    @Inject
    protected IProviderBusiness providerBusiness;

    @Autowired
    private IServiceBusiness serviceBusiness;

    private static boolean initialized = false;

    private static int coveragePID;
    private static int vectorPID;

    @PostConstruct
    public void init() {
        if (!initialized) {
            try {
                dataBusiness.deleteAll();
                providerBusiness.removeAll();
                datasetBusiness.removeAllDatasets();

                //Initialize geotoolkit
                ImageIO.scanForPlugins();
                org.geotoolkit.lang.Setup.initialize(null);



                // dataset
                int dsId = datasetBusiness.createDataset("DataBusinessTest", null, null);

                // coverage-file datastore
                final File rootDir = initDataDirectory();
                final DataProviderFactory covFilefactory = DataProviders.getFactory("data-store");
                final ParameterValueGroup sourceCF = covFilefactory.getProviderDescriptor().createValue();
                sourceCF.parameter("id").setValue("coverageTestSrc");
                final ParameterValueGroup choice3 = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourceCF);

                final ParameterValueGroup srcCFConfig = choice3.addGroup("FileCoverageStoreParameters");

                srcCFConfig.parameter("path").setValue(new URL("file:" + rootDir.getAbsolutePath() + "/org/constellation/data/SSTMDE200305.png"));
                srcCFConfig.parameter("type").setValue("AUTO");

                coveragePID = providerBusiness.storeProvider("coverageTestSrc", null, ProviderType.LAYER, "data-store", sourceCF);

                providerBusiness.createOrUpdateData(coveragePID, dsId, false);

                final DataProviderFactory ffactory = DataProviders.getFactory("data-store");
                final File outputDir = initDataDirectory();
                final ParameterValueGroup sourcef = ffactory.getProviderDescriptor().createValue();
                sourcef.parameter("id").setValue("shapeSrc");

                final ParameterValueGroup choice = ProviderParameters.getOrCreate(DataStoreProviderService.SOURCE_CONFIG_DESCRIPTOR, sourcef);
                final ParameterValueGroup shpconfig = choice.addGroup("ShapefileParametersFolder");
                shpconfig.parameter("path").setValue(URI.create("file:" + outputDir.getAbsolutePath() + "/org/constellation/ws/embedded/wms111/shapefiles"));

                vectorPID = providerBusiness.storeProvider("shapeSrc", null, ProviderType.LAYER, "data-store", sourcef);

                providerBusiness.createOrUpdateData(vectorPID, dsId, false);


                initialized = true;
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    @BeforeClass
    public static void initTestDir() throws IOException {
        ConfigDirectory.setupTestEnvironement("LayerBusinessTest");
    }

    @AfterClass
    public static void tearDown() {
        try {
            final ILayerBusiness dbus = SpringHelper.getBean(ILayerBusiness.class);
            if (dbus != null) {
                dbus.removeAll();
            }
            ConfigDirectory.shutdownTestEnvironement("LayerBusinessTest");
        } catch (ConstellationException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @Test
    @Order(order=1)
    public void createTest() throws Exception {

        QName dataName = new QName("SSTMDE200305");
        DataBrief db = dataBusiness.getDataBrief(dataName, coveragePID);
        Assert.assertNotNull(db);

        final Details frDetails = new Details("name", "identifier", Arrays.asList("keyword1", "keyword2"), "description", Arrays.asList("version1"), new Contact(), new AccessConstraint(), true, "FR");
        Integer sid = serviceBusiness.create("wms", "default", new LayerContext(), frDetails, 1);

        Integer lid = layerBusiness.add(db.getId(), "SSTM", sid, null);

        Assert.assertNotNull(lid);
    }

}
