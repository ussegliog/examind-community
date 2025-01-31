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
package org.constellation.metadata.index.elasticsearch;

// J2SE dependencies
import org.apache.sis.util.logging.Logging;

import org.constellation.metadata.CSWQueryable;
import org.constellation.api.PathType;
import org.constellation.test.utils.Order;
import org.constellation.test.utils.TestRunner;
import org.constellation.util.NodeUtilities;
import org.constellation.util.Util;
import org.geotoolkit.nio.IOUtilities;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.opengis.filter.FilterFactory2;

import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sis.internal.system.DefaultFactories;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.opengis.filter.FilterFactory;
import org.opengis.filter.Not;


/**
 * Test class for constellation lucene index
 *
 * @author Guilhem Legal (Geomatys)
 */
@RunWith(TestRunner.class)
public class ElasticSearchIndexTest {

    protected static final FilterFactory2 FF = (FilterFactory2) DefaultFactories.forBuildin(FilterFactory.class);


    private static final Logger LOGGER = Logging.getLogger("org.constellation.metadata");

    private static ElasticSearchIndexSearcher indexSearcher;

    private static ElasticSearchNodeIndexer indexer;

    private static final File configDirectory  = new File("GenericNodeIndexTest");

    private static String GEOM_PROP= "geoextent";

    private static boolean ES_SERVER_PRESENT = true;

    @BeforeClass
    public static void setUpClass() throws Exception {

        final URL u = new URL("http://localhost:9200");
        HttpURLConnection conec = (HttpURLConnection) u.openConnection();
        try {
            conec.connect();
        } catch(IOException ex) {
            ES_SERVER_PRESENT = false;
            return;
        }
        int resCode = conec.getResponseCode();
        if (resCode == 404) {
            ES_SERVER_PRESENT = false;
            return;
        }

        Map<String, Object> infos = ElasticSearchClient.getServerInfo("http://localhost:9200");
        String clusterName = (String) infos.get("cluster_name");

        LOGGER.info("\n\n ELASTIC-SEARCH SERVER PRESENT \n\n");

        IOUtilities.deleteRecursively(configDirectory.toPath());
        List<Node> object         = fillTestData();
        boolean remoteES          = true;
        String indexName          = "GenericNodeIndexTest" + UUID.randomUUID().toString();
        indexer                   = new ElasticSearchNodeIndexer(object, "localhost", clusterName, indexName, new HashMap<String, PathType>(), true, remoteES);
        indexSearcher             = new ElasticSearchIndexSearcher("localhost", clusterName, indexName, remoteES);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        if (indexer != null) {
            indexer.removeIndex();
            indexer.destroy();
        }
        if (indexSearcher != null) {indexSearcher.destroy();}
        IOUtilities.deleteRecursively(configDirectory.toPath());
    }


    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test simple lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 1)
    public void simpleSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        XContentBuilder nullFilter   = null;
        String resultReport = "";

        /**
         * Test 1 simple search: title = 90008411.ctd
         */
        SpatialQuery spatialQuery = new SpatialQuery("Title:\"90008411.ctd\"", nullFilter);
        Set<String> result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 1:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");

        assertEquals(expectedResult, result);

         /**
         * Test 2 simple search: identifier != 40510_145_19930221211500
         */
        spatialQuery = new SpatialQuery("metafile:doc NOT identifier:\"40510_145_19930221211500\"", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 2:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");
        expectedResult.add("meta_NaN_id");


        assertEquals(expectedResult, result);

        /**
         * Test 3 simple search: originator = UNIVERSITE DE LA MEDITERRANNEE (U2) / COM - LAB. OCEANOG. BIOGEOCHIMIE - LUMINY
         */
        spatialQuery = new SpatialQuery("abstract:\"Donnees CTD NEDIPROD VI 120\"", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "simpleSearch 3:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");

        assertEquals(expectedResult, result);

        /**
         * Test 4 simple search: Title = 92005711.ctd
         */
        spatialQuery = new SpatialQuery("Title:\"92005711.ctd\"", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 4:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("40510_145_19930221211500");


        assertEquals(expectedResult, result);

        /**
         * Test 5 simple search: creator = IFREMER / IDM/SISMER
         */
        spatialQuery = new SpatialQuery("creator:\"IFREMER / IDM/SISMER\"", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 5:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("40510_145_19930221211500");


        assertEquals(expectedResult, result);

        /**
         * Test 6 simple search: identifier = 40510_145_19930221211500
         */
        spatialQuery = new SpatialQuery("identifier:\"40510_145_19930221211500\"", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 6:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);

         /**
         * Test 7 simple search: TopicCategory = oceans
         */
        spatialQuery = new SpatialQuery("TopicCategory:\"oceans\"", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 7:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("CTDF02");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");
        expectedResult.add("meta_NaN_id");

        assertEquals(expectedResult, result);

        /**
         * Test 8 simple search: TopicCategory = environment
         */
        spatialQuery = new SpatialQuery("TopicCategory:\"environment\"", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 8:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");

        assertEquals(expectedResult, result);

         /**
         * Test 9 simple search: TopicCategory = environment OR identifier = 40510_145_19930221211500
         */
         XContentBuilder filter = XContentFactory.jsonBuilder()
                .startObject()

                        .startArray("or")
                            .startObject()
                                .startObject("term")
                                        .field("TopicCategory", "environment")
                                .endObject()
                            .endObject()
                            .startObject()
                                .startObject("term")
                                        .field("identifier", "40510_145_19930221211500")
                                .endObject()
                            .endObject()
                        .endArray()

                .endObject();
         System.out.println(filter.string());
        spatialQuery = new SpatialQuery(filter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SimpleSearch 9:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);
    }

     /**
     * Test simple lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 2)
    public void wildCharSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        XContentBuilder nullFilter   = null;
        String resultReport = "";

        /**
         * Test 1 simple search: title = title1
         */
        SpatialQuery spatialQuery = new SpatialQuery("Title:90008411*", nullFilter);
        Set<String> result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wildCharSearch 1:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");

        assertEquals(expectedResult, result);

        /**
         * Test 2 wildChar search: originator LIKE *UNIVER....
         */
        spatialQuery = new SpatialQuery("abstract:*NEDIPROD*", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wildCharSearch 2:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");

        assertEquals(expectedResult, result);

        /**
         * Test 3 wildChar search: Title like *.ctd
         */
        resultReport = "";
        spatialQuery = new SpatialQuery("Title:*.ctd", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wilCharSearch 3:\n{0}", resultReport);

        assertTrue(result.contains("39727_22_19750113062500"));
        assertTrue(result.contains("40510_145_19930221211500"));
        assertTrue(result.contains("42292_5p_19900609195600"));
        assertTrue(result.contains("42292_9s_19900610041000"));

        assertEquals(4, result.size());

        /**
         * Test 4 wildChar search: title like *.ctd
         */
        resultReport = "";
        spatialQuery = new SpatialQuery("title:*.ctd", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wilCharSearch 4:\n{0}", resultReport);

        assertTrue(result.contains("39727_22_19750113062500"));
        assertTrue(result.contains("40510_145_19930221211500"));
        assertTrue(result.contains("42292_5p_19900609195600"));
        assertTrue(result.contains("42292_9s_19900610041000"));

        assertEquals(4, result.size());

         /**
         * Test 5 wildCharSearch: abstract LIKE *onnees CTD NEDIPROD VI 120
         */
         XContentBuilder filter = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("wildcard")
                                .field("abstract_sort", "*onnees CTD NEDIPROD VI 120")
                    .endObject()
                .endObject();

        spatialQuery = new SpatialQuery(null, filter);
        result = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wildCharSearch 5:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");

        //issues here it found
        assertEquals(expectedResult, result);

        /**
         * Test 6 wildCharSearch: identifier LIKE 40510_145_*
         */
        spatialQuery = new SpatialQuery("identifier:40510_145_*", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wildCharSearch 6:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);

        /**
         * Test 7 wildCharSearch: identifier LIKE *40510_145_*
         */
        spatialQuery = new SpatialQuery("identifier:*40510_145_*", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "wildCharSearch 7:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);

    }

    /**
     * Test simple lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 3)
    public void numericComparisonSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        XContentBuilder nullFilter   = null;
        String resultReport = "";

        /**
         * Test 1 numeric search: CloudCover <= 60
         */
        XContentBuilder filter = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("range")
                        .startObject("CloudCover")
                                .field("lte", 60.0)
                        .endObject()
                    .endObject()
                .endObject();
        SpatialQuery spatialQuery = new SpatialQuery(null, filter);
        Set<String> result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "numericComparisonSearch 1:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");

        assertEquals(expectedResult, result);

        /**
         * Test 2 numeric search: CloudCover <= 25
         */
        spatialQuery = new SpatialQuery("CloudCover:[-2147483648 TO 25]", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (Iterator<String> it = result.iterator(); it.hasNext();) {
            String s = it.next();
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "numericComparisonSearch 2:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_9s_19900610041000");


        assertEquals(expectedResult, result);

        /**
         * Test 3 numeric search: CloudCover => 25
         */
        resultReport = "";
        spatialQuery = new SpatialQuery("CloudCover:[25 TO 2147483648]", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "numericComparisonSearch 3:\n{0}", resultReport);

        assertTrue(result.contains("42292_5p_19900609195600"));
        assertTrue(result.contains("39727_22_19750113062500"));
        assertEquals(2, result.size());

        /**
         * Test 4 numeric search: CloudCover => 60
         */
        resultReport = "";
        spatialQuery = new SpatialQuery("CloudCover:[210 TO 2147483648]", nullFilter);
        result       = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "numericComparisonSearch 4:\n{0}", resultReport);

        assertEquals(0, result.size());

         /**
         * Test 5 numeric search: CloudCover => 50
         */
        spatialQuery = new SpatialQuery("CloudCover:[50.0 TO 2147483648]", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "numericComparisonSearch 5:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("39727_22_19750113062500");

        //issues here it found
        assertEquals(expectedResult, result);

    }

     /**
     * Test simple lucene date search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 4)
    public void dateSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        String resultReport = "";

        /**
         * Test 1 date search: date after 25/01/2009
         */
        XContentBuilder filter = XContentFactory.jsonBuilder()
        .startObject()
            .startObject("range")
                .startObject("date")
                        .field("gt", Util.LUCENE_DATE_FORMAT.parse("20090125000000"))
                .endObject()
            .endObject()
        .endObject();
        SpatialQuery spatialQuery = new SpatialQuery(null, filter);
        Set<String> result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 1:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");

        assertEquals(expectedResult, result);

        /**
         * Test 4 date search: date = 26/01/2009
         */
        filter = XContentFactory.jsonBuilder()
        .startObject()
            .startObject("term")
                        .field("date", Util.FULL_LUCENE_DATE_FORMAT.parse("20090126122224765"))
            .endObject()
        .endObject();
        spatialQuery = new SpatialQuery(null, filter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 4:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        //expectedResult.add("42292_9s_19900610041000"); exclude since date time is handled
        //expectedResult.add("39727_22_19750113062500"); exclude since date time is handled
        expectedResult.add("11325_158_19640418141800");

        assertEquals(expectedResult, result);

        /**
         * Test 5 date search: date LIKE 26/01/200*
         */
        filter = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("wildcard")
                                .field("date_sort", "200*-01-26*")
                    .endObject()
                .endObject();

        spatialQuery = new SpatialQuery(null, filter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 4:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);

        /**
         * Test 6 date search: CreationDate between 01/01/1800 and 01/01/2000
         */
        filter = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("range")
                        .startObject("CreationDate")
                                .field("gt", Util.LUCENE_DATE_FORMAT.parse("18000101000000"))
                                .field("lt", Util.LUCENE_DATE_FORMAT.parse("20000101000000"))
                        .endObject()
                    .endObject()
                .endObject();
        spatialQuery = new SpatialQuery(null, filter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 6:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");

        assertEquals(expectedResult, result);

        /**
         * Test 7 date time search: CreationDate after 1970-02-04T06:00:00
         */
        filter = XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("range")
                        .startObject("CreationDate")
                                .field("gt", Util.LUCENE_DATE_FORMAT.parse("19700204060000"))
                        .endObject()
                    .endObject()
                .endObject();
        spatialQuery = new SpatialQuery(null, filter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 7:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");

        assertEquals(expectedResult, result);
    }

    @Ignore
    public void problematicDateSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        XContentBuilder nullFilter   = null;

        /**
         * Test 3 date search: TempExtent_end after 01/01/1991
         */
        SpatialQuery spatialQuery = new SpatialQuery("TempExtent_end:{\"19910101\" 30000101}", nullFilter);
        Set<String> result = indexSearcher.doSearch(spatialQuery);

        String resultReport ="";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 3:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("CTDF02");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");

        assertEquals(expectedResult, result);

        /**
         * Test 2 date search: TempExtent_begin before 01/01/1985
         */
        spatialQuery = new SpatialQuery("TempExtent_begin:{00000101 \"19850101\"}", nullFilter);
        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "DateSearch 2:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");

        assertEquals(expectedResult, result);
    }
    /**
     * Test sorted lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 5)
    public void sortedSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        XContentBuilder nullFilter   = null;
        String resultReport = "";

        /**
         * Test 1 sorted search: all orderBy identifier ASC
         */
        SpatialQuery spatialQuery = new SpatialQuery(null, nullFilter);
        spatialQuery.setSort("identifier_sort", "ASC");

        Set<String> result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SortedSearch 1:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");
        expectedResult.add("meta_NaN_id");
        expectedResult.add("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd");
        assertEquals(expectedResult, result);

        /**
         * Test 2 sorted search: all orderBy identifier DSC
         */
        resultReport = "";
        spatialQuery = new SpatialQuery(null, nullFilter);
        spatialQuery.setSort("identifier_sort", "DESC");

        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SortedSearch 2:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd");
        expectedResult.add("meta_NaN_id");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("CTDF02");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");

        assertEquals(expectedResult, result);

        /**
         * Test 3 sorted search: all orderBy Abstract ASC
         */
        resultReport = "";
        spatialQuery = new SpatialQuery(null, nullFilter);
        spatialQuery.setSort("Abstract_sort", "ASC");

        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SortedSearch 3:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("CTDF02");
        expectedResult.add("meta_NaN_id");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");

        assertEquals(expectedResult, result);

        /**
         * Test 4 sorted search: all orderBy Abstract DSC
         */
        resultReport = "";
        spatialQuery = new SpatialQuery(null, nullFilter);
        spatialQuery.setSort("Abstract_sort", "DESC");

        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SortedSearch 4:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("meta_NaN_id");
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd");

        assertEquals(expectedResult, result);

        /**
         * Test 5 sorted search: orderBy CloudCover ASC with SortField.STRING => bad order
         */
        resultReport = "";
        spatialQuery = new SpatialQuery("CloudCover:[0 TO 2147483648]", nullFilter);
        spatialQuery.setSort("CloudCover_sort", "ASC");

        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SortedSearch 5:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();

        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");

        assertEquals(expectedResult, result);

        /**
         * Test 5 sorted search: orderBy CloudCover ASC with SortField.DOUBLE => good order
         */
        resultReport = "";
        spatialQuery = new SpatialQuery("CloudCover:[0 TO 2147483648]", nullFilter);
        spatialQuery.setSort("CloudCover_sort", "ASC");

        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "SortedSearch 5:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();

        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");


        assertEquals(expectedResult, result);
    }

    /**
     *
     * Test spatial lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 6)
    public void spatialSearchTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        String resultReport = "";

        /**
         * Test 1 spatial search: BBOX filter
         */
        XContentBuilder sf = SpatialFilterBuilder.build(FF.bbox(GEOM_PROP, -20, -20, 20, 20, "EPSG:4326"));
        SpatialQuery spatialQuery = new SpatialQuery(sf);

        Set<String> result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "spatialSearch 1:\n{0}", resultReport);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");

        assertEquals(expectedResult, result);

        /**
         * Test 1 spatial search: NOT BBOX filter
         */
        resultReport = "";

        Not f = FF.not(FF.bbox(GEOM_PROP, -20, -20, 20, 20, "CRS:84"));
        sf = SpatialFilterBuilder.build(f);
        spatialQuery = new SpatialQuery(null, sf);

        result = indexSearcher.doSearch(spatialQuery);

        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }

        LOGGER.log(Level.FINER, "spatialSearch 2:\n{0}", resultReport);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("urn:uuid:1ef30a8b-876d-4828-9246-c37ab4510bbd");
        expectedResult.add("gov.noaa.nodc.ncddc. MODXXYYYYJJJ.L3_Mosaic_NOAA_GMX or MODXXYYYYJJJHHMMSS.L3_NOAA_GMX");
        expectedResult.add("meta_NaN_id");
        assertEquals("CRS URN are not working", expectedResult, result);
    }

    /**
     *
     * Test spatial lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 7)
    public void TermQueryTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        /**
         * Test 1
         */

        String identifier = "39727_22_19750113062500";
        String result = indexSearcher.identifierQuery(identifier);

        LOGGER.log(Level.FINER, "identifier query 1:\n{0}", result);

        String expectedResult = "39727_22_19750113062500";

        assertEquals(expectedResult, result);

        /**
         * Test 2
         */

        identifier = "CTDF02";
        result = indexSearcher.identifierQuery(identifier);

        LOGGER.log(Level.FINER, "identifier query 2:\n{0}", result);

        expectedResult = "CTDF02";

        assertEquals(expectedResult, result);
    }

    /**
     *
     * Test spatial lucene search.
     *
     * @throws java.lang.Exception
     */
    @Test
    @Order(order = 8)
    public void DeleteDocumentTest() throws Exception {
        if (!ES_SERVER_PRESENT) return;

        indexer.removeDocument("CTDF02");

        indexSearcher.refresh();

        /**
         * Test 1
         */

        String identifier = "39727_22_19750113062500";
        String result = indexSearcher.identifierQuery(identifier);

        LOGGER.log(Level.FINER, "identifier query 1:\n{0}", result);

        String expectedResult = "39727_22_19750113062500";

        assertEquals(expectedResult, result);

        /**
         * Test 2
         */

        identifier = "CTDF02";
        result = indexSearcher.identifierQuery(identifier);

        LOGGER.log(Level.FINER, "identifier query 2:\n{0}", result);

        expectedResult = null;

        assertEquals(expectedResult, result);
    }


    @Test
    @Order(order = 9)
    public void extractValuesTest() throws Exception {
        Node n = getOriginalMetadata("org/constellation/xml/metadata/meta7.xml");
        List<Object> result = NodeUtilities.extractValues(n, CSWQueryable.ISO_QUERYABLE.get("CreationDate"));
        assertEquals(Arrays.asList("20060101000000"), result);

        n = getOriginalMetadata("org/constellation/xml/metadata/meta3.xml");
        result = NodeUtilities.extractValues(n, CSWQueryable.ISO_QUERYABLE.get("CreationDate"));
        assertEquals(Collections.emptyList(), result);

        n = getOriginalMetadata("org/constellation/xml/metadata/meta1.xml");

        result = NodeUtilities.extractValues(n, CSWQueryable.ISO_QUERYABLE.get("TempExtent_begin"));
        assertEquals(Arrays.asList("19900605000000"), result);


        result = NodeUtilities.extractValues(n, CSWQueryable.ISO_QUERYABLE.get("TempExtent_end"));
        assertEquals(Arrays.asList("19900702000000"), result);

    }

   @Test
    @Order(order = 10)
    public void extractValuesTest2() throws Exception {

        Node n = getOriginalMetadata("org/constellation/xml/metadata/meta8.xml");
        List<Object> result = NodeUtilities.extractValues(n, CSWQueryable.DUBLIN_CORE_QUERYABLE.get("WestBoundLongitude"));
        assertEquals(Arrays.asList(60.042), result);


       /* DefaultMetadata meta4 = new DefaultMetadata();
        DefaultDataIdentification ident4 = new DefaultDataIdentification();

        TimePeriodType tp1 = new TimePeriodType("id", "2008-11-01", "2008-12-01");
        tp1.setId("007-all");
        DefaultTemporalExtent tempExtent = new DefaultTemporalExtent();
        tempExtent.setExtent(tp1);

        DefaultExtent ext = new DefaultExtent();
        ext.setTemporalElements(Arrays.asList(tempExtent));
        ident4.setExtents(Arrays.asList(ext));

        meta4.setIdentificationInfo(Arrays.asList(ident4));
        List<Object> result = GenericIndexer.extractValues(meta4, Arrays.asList("ISO 19115:MD_Metadata:identificationInfo:extent:temporalElement:extent#id=[0-9]+-all:beginPosition"));
        assertEquals(Arrays.asList("20081101000000"), result);*/
    }

    @Test
    @Order(order = 11)
    public void extractValuesTest3() throws Exception {
        Node n = getOriginalMetadata("org/constellation/xml/metadata/meta7.xml");

        List<Object> result = NodeUtilities.extractValues(n, CSWQueryable.ISO_QUERYABLE.get("TopicCategory"));
        assertEquals(Arrays.asList("environment"), result);

    }

    public static List<Node> fillTestData() throws Exception {
        List<Node> result = new ArrayList<>();

        Node obj = getOriginalMetadata("org/constellation/xml/metadata/meta1.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta2.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta3.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta4.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta5.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta6.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta7.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/meta8.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/imageMetadata.xml");
        result.add(obj);

        obj = getOriginalMetadata("org/constellation/xml/metadata/metaNan.xml");
        result.add(obj);

        return result;
    }



    private static Node getOriginalMetadata(final String fileName) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        DocumentBuilder docBuilder = dbf.newDocumentBuilder();
        Document document = docBuilder.parse(Util.getResourceAsStream(fileName));

        return document.getDocumentElement();
    }
}

