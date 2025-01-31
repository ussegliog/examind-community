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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sis.util.logging.Logging;
import org.constellation.business.IMapContextBusiness;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.MapContextLayersDTO;
import org.constellation.dto.MapContextStyledLayerDTO;
import org.constellation.dto.ParameterValues;
import org.constellation.exception.ConstellationException;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.SpringTestRunner;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@RunWith(SpringTestRunner.class)
@ContextConfiguration("classpath:/cstl/spring/test-context.xml")
public class MapContextBusinessTest {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.admin");

    @Autowired
    private IMapContextBusiness mpBusiness;

    @BeforeClass
    public static void initTestDir() throws IOException {
        ConfigDirectory.setupTestEnvironement("MapContextBusinessTest");
    }

    @AfterClass
    public static void tearDown() {
        try {
            final IMapContextBusiness dbus = SpringHelper.getBean(IMapContextBusiness.class);
            if (dbus != null) {
                dbus.deleteAll();
            }
            ConfigDirectory.shutdownTestEnvironement("MapContextBusinessTest");
        } catch (ConstellationException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    @Test
    @Order(order=1)
    public void createTest() throws Exception {
        MapContextLayersDTO mapContext = new MapContextLayersDTO();
        mapContext.setCrs("CRS:84");
        mapContext.setDescription("desc");
        mapContext.setKeywords("kw1,kw2");
        mapContext.setName("map-context-1");
        mapContext.setOwner(1);
        mapContext.setUserOwner("admin");

        mapContext.setEast(180.0);
        mapContext.setWest(-180.0);
        mapContext.setNorth(90.0);
        mapContext.setSouth(-90.0);

        List<MapContextStyledLayerDTO> layers = new ArrayList<>();
        mapContext.setLayers(layers);

        Integer mid = mpBusiness.create(mapContext);
        Assert.assertNotNull(mid);
        mapContext.setId(mid);

        MapContextLayersDTO result = mpBusiness.findMapContextLayers(mid);
        Assert.assertEquals(mapContext, result);
    }

    @Test
    @Order(order=2)
    public void getExtentTest() throws Exception {
        List<MapContextLayersDTO> mps = mpBusiness.findAllMapContextLayers();
        Assert.assertEquals(1, mps.size());

        int mid = mps.get(0).getId();
        ParameterValues pv = mpBusiness.getExtent(mid);
        Assert.assertEquals(5, pv.getValues().size());
        Assert.assertTrue(pv.getValues().containsKey("west"));
        Assert.assertEquals("-180.0", pv.getValues().get("west"));
    }
}
