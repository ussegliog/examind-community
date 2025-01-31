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
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sis.storage.DataStoreProvider;
import org.apache.sis.util.logging.Logging;
import org.constellation.business.IDatasourceBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.DataCustomConfiguration;
import org.constellation.dto.DataSource;
import org.constellation.dto.DataSourceSelectedPath;
import org.constellation.dto.ProviderConfiguration;
import org.constellation.dto.importdata.ResourceStoreAnalysisV3;
import org.constellation.exception.ConstellationException;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.SpringTestRunner;
import org.constellation.test.utils.TestEnvironment;
import org.geotoolkit.storage.DataStores;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@RunWith(SpringTestRunner.class)
@ContextConfiguration("classpath:/cstl/spring/test-context.xml")
public class DatasourceBusinessTest {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.admin");

    @Autowired
    private IDatasourceBusiness datasourceBusiness;

    private static Path rootDir;

    @BeforeClass
    public static void initTestDir() throws IOException {
        ConfigDirectory.setupTestEnvironement("DatasourceBusinessTest");
        rootDir = initDataDirectory();
    }

    @AfterClass
    public static void tearDown() {
        try {
            final IDatasourceBusiness dbus = SpringHelper.getBean(IDatasourceBusiness.class);
            if (dbus != null) {
                dbus.deleteAll();
            }
            ConfigDirectory.shutdownTestEnvironement("DatasourceBusinessTest");
        } catch (ConstellationException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @Test
    @Order(order=1)
    public void createTest() throws Exception {
        LOGGER.info("TEST CREATE");
        DataSource ds = new DataSource();
        ds.setUrl(rootDir.toUri().toString());
        ds.setPermanent(false);
        ds.setDateCreation(System.currentTimeMillis());
        ds.setReadFromRemote(Boolean.TRUE);
        ds.setType("files");
        Integer dsId = datasourceBusiness.create(ds);
        Assert.assertNotNull(dsId);
    }

    @Test
    @Order(order=2)
    public void analyseTest() throws Exception {
        LOGGER.info("TEST ANALYSE");
        DataSource ds = datasourceBusiness.getByUrl(rootDir.toUri().toString());
        Assert.assertNotNull(ds);

        String state = datasourceBusiness.getDatasourceAnalysisState(ds.getId());
        Assert.assertEquals("NOT_STARTED", state);

        Map<String, Set<String>> analyse = datasourceBusiness.computeDatasourceStores(ds.getId(), false, true);
        Assert.assertNotNull(analyse);
        Assert.assertTrue(analyse.containsKey("shapefile"));
        Assert.assertTrue(analyse.containsKey("coverage-file"));

        state = datasourceBusiness.getDatasourceAnalysisState(ds.getId());
        Assert.assertEquals("COMPLETED", state);

    }

    @Test
    @Order(order=3)
    public void selectPathTest() throws Exception {
        LOGGER.info("TEST SELECT");
        DataSource ds = datasourceBusiness.getByUrl(rootDir.toUri().toString());
        Assert.assertNotNull(ds);

        ds.setStoreId("shapefile");
        ds.setFormat("application/x-shapefile");

        datasourceBusiness.update(ds);

        // select all
        datasourceBusiness.recordSelectedPath(ds);

        List<DataSourceSelectedPath> paths = datasourceBusiness.getSelectedPath(ds, Integer.MAX_VALUE);
        Assert.assertEquals(12, paths.size());
    }

    @Test
    @Order(order=4)
    public void treatSelectedPathTest() throws Exception {
        LOGGER.info("TEST TREAT PATH");
        DataSource ds = datasourceBusiness.getByUrl(rootDir.toUri().toString());
        Assert.assertNotNull(ds);

        DataSourceSelectedPath path = datasourceBusiness.getSelectedPath(ds, "/org/constellation/ws/embedded/wms111/shapefiles/BasicPolygons.shp");
        Assert.assertNotNull(path);

        DataStoreProvider factory = DataStores.getProviderById("shapefile");

        Assert.assertNotNull(factory);
        final DataCustomConfiguration.Type storeParams = buildDatastoreConfiguration(factory, "data-store", null);
        storeParams.setSelected(true);
        final ProviderConfiguration provConfig = new ProviderConfiguration(storeParams.getCategory(), storeParams.getId());
        storeParams.cleanupEmptyProperty();
        storeParams.propertyToMap(provConfig.getParameters());

        boolean hidden = true;
        ResourceStoreAnalysisV3 analysis = datasourceBusiness.treatDataPath(path, ds, provConfig, hidden, null, 1);
        Assert.assertEquals(1, analysis.getResources().size());
    }

    private static DataCustomConfiguration.Type buildDatastoreConfiguration(DataStoreProvider factory, String category, String tag) {
        final String id    = factory.getOpenParameters().getName().getCode();
        final String title = String.valueOf(factory.getShortName());
        String description = null;
        if (factory.getOpenParameters().getDescription() != null) {
            description = String.valueOf(factory.getOpenParameters().getDescription());
        }
        final  DataCustomConfiguration.Property property = toDataStorePojo(factory.getOpenParameters());
        return new DataCustomConfiguration.Type(id, title, category, tag, description, property);
    }

    private static DataCustomConfiguration.Property toDataStorePojo(GeneralParameterDescriptor desc){
        final DataCustomConfiguration.Property prop = new DataCustomConfiguration.Property();
        prop.setId(desc.getName().getCode());
        if(desc.getDescription()!=null) prop.setDescription(String.valueOf(desc.getDescription()));
        prop.setOptional(desc.getMinimumOccurs()==0);

        if(desc instanceof ParameterDescriptorGroup){
            final ParameterDescriptorGroup d = (ParameterDescriptorGroup)desc;
            for(GeneralParameterDescriptor child : d.descriptors()){
                prop.getProperties().add(toDataStorePojo(child));
            }
        }else if(desc instanceof ParameterDescriptor){
            final ParameterDescriptor d = (ParameterDescriptor)desc;
            final Object defaut = d.getDefaultValue();
            if(defaut!=null && DataCustomConfiguration.MARSHALLABLE.contains(defaut.getClass())){
                prop.setValue(defaut);
            }
            prop.setType(d.getValueClass().getSimpleName());
        }

        return prop;
    }

    public static Path initDataDirectory() throws IOException {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        File outputDir = new File(tmpDir, "Constellation");
        if (!outputDir.exists()) {
            outputDir.mkdir();
        }
        try {
            TestEnvironment.initWorkspaceData(outputDir.toPath());
        } catch (URISyntaxException e) {
            throw new IOException(e.getMessage(), e);
        }
        return outputDir.toPath();
    }
}
