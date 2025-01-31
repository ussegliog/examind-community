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

package org.constellation.metadata.index.analyzer;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.sis.geometry.GeneralEnvelope;
import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.referencing.CommonCRS;
import org.apache.sis.util.logging.Logging;
import org.constellation.util.Util;
import org.geotoolkit.csw.xml.CSWMarshallerPool;
import org.geotoolkit.index.LogicalFilterType;
import org.geotoolkit.lucene.filter.LuceneOGCFilter;
import org.geotoolkit.lucene.filter.SerialChainFilter;
import org.geotoolkit.lucene.filter.SpatialQuery;
import org.geotoolkit.lucene.index.LuceneIndexSearcher;
import org.opengis.filter.FilterFactory2;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sis.internal.system.DefaultFactories;
import org.constellation.test.utils.SpringTestRunner;

import static org.junit.Assert.assertEquals;
import org.junit.runner.RunWith;
import org.opengis.filter.FilterFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

/**
 *
* @author Guilhem Legal (Geomatys)
 */
@ActiveProfiles({"standard"})
@ContextConfiguration("classpath:/cstl/spring/test-no-hazelcast.xml")
@RunWith(SpringTestRunner.class)
public abstract class AbstractAnalyzerTest {

    protected static final FilterFactory2 FF = (FilterFactory2) DefaultFactories.forBuildin(FilterFactory.class);

    protected static final Logger logger = Logging.getLogger("org.constellation.metadata.index.generic");

    protected static LuceneIndexSearcher indexSearcher;

    public static List<Object> fillTestData() throws JAXBException {
        List<Object> result       = new ArrayList<>();
        Unmarshaller unmarshaller = CSWMarshallerPool.getInstance().acquireUnmarshaller();

        Object obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta1.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata");
        }

        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta2.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }

        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta3.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }

        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta4.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }

        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta5.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }

        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta6.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }
        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta7.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }

        obj = unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/xml/metadata/meta14.xml"));
        if (obj instanceof DefaultMetadata) {
            result.add((DefaultMetadata) obj);
        } else {
            throw new IllegalArgumentException("resource file must be DefaultMetadata:" + obj);
        }
        CSWMarshallerPool.getInstance().recycle(unmarshaller);
        return result;
    }

    protected void logResultReport(String reportName, Set<String> result) {
        String resultReport = "";
        for (String s: result) {
            resultReport = resultReport + s + '\n';
        }
        logger.log(Level.FINER, reportName + "\n{0}", resultReport);
    }

    /**
     * Test simple lucene search.
     *
     * @throws java.lang.Exception
     */
    public void simpleSearchTest() throws Exception {
        Filter nullFilter   = null;

        /**
         * Test 1 simple search: title = 90008411.ctd
         */
        SpatialQuery spatialQuery = new SpatialQuery("Title:\"90008411.ctd\"", nullFilter, LogicalFilterType.AND);
        Set<String> result = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 1:", result);

        // the result we want are this
        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");

        assertEquals(expectedResult, result);


        /**
         * Test 2 simple search: indentifier != 40510_145_19930221211500
         */
        spatialQuery = new SpatialQuery("metafile:doc NOT identifier:\"40510_145_19930221211500\"", nullFilter, LogicalFilterType.AND);
        result       = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 2:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1");

        assertEquals(expectedResult, result);

        /**
         * Test 3 simple search: originator = Donnees CTD NEDIPROD VI 120
         */
        spatialQuery = new SpatialQuery("abstract:\"Donnees CTD NEDIPROD VI 120\"", nullFilter, LogicalFilterType.AND);
        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 3:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");

        assertEquals(expectedResult, result);

        /**
         * Test 4 simple search: ID = World Geodetic System 84
         */
        spatialQuery = new SpatialQuery("ID:\"World Geodetic System 84\"", nullFilter, LogicalFilterType.AND);
        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 4:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);

        /**
         * Test 5 simple search: ID = 0UINDITENE
         */
        spatialQuery = new SpatialQuery("ID:\"0UINDITENE\"", nullFilter, LogicalFilterType.AND);
        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 5:", result);


        expectedResult = new LinkedHashSet<>();
        expectedResult.add("11325_158_19640418141800");

        assertEquals(expectedResult, result);


        /**
         * Test 6 range search: Title <= FRA
         */
        spatialQuery = new SpatialQuery("Title_raw:[0 TO FRA]", nullFilter, LogicalFilterType.AND);
        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 6:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("40510_145_19930221211500");

        assertEquals(expectedResult, result);

        /**
         * Test 7 range search: Title > FRA
         */
        spatialQuery = new SpatialQuery("Title_raw:[FRA TO z]", nullFilter, LogicalFilterType.AND);
        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("simpleSearch 7:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        //expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1"); no more null value => Spot5 ... has no title

        assertEquals(expectedResult, result);
    }


    /**
     * Test simple lucene search.
     *
     * @throws java.lang.Exception
     */
    public void wildCharUnderscoreSearchTest() throws Exception {
        Filter nullFilter   = null;

        /**
         * Test 1 simple search: title = title1
         */
        SpatialQuery spatialQuery = new SpatialQuery("identifier:*MDWeb_FR_SY*", nullFilter, LogicalFilterType.AND);
        Set<String> result = indexSearcher.doSearch(spatialQuery);
        logResultReport("wildCharUnderscoreSearch 1:", result);

        Set<String> expectedResult = new LinkedHashSet<>();
        //expectedResult.add("MDWeb_FR_SY_couche_vecteur_258"); error '_' is tokenized

        assertEquals(expectedResult, result);

        /**
         * Test 2 simple search: title = identifier:Spot5-Cyprus-THX-IMAGERY3_ortho*
         */
        spatialQuery = new SpatialQuery("identifier:Spot5-Cyprus-THX-IMAGERY3_ortho*", nullFilter, LogicalFilterType.AND);
        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("wildCharUnderscoreSearch 2:", result);

        expectedResult = new LinkedHashSet<>();
        //expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1"); // error

        assertEquals(expectedResult, result);
    }

    public void dateSearchTest() throws Exception {
        Filter nullFilter   = null;

        /**
         * Test 1 date search: date after 25/01/2009
         */
        SpatialQuery spatialQuery = new SpatialQuery("date:{20090125 30000101}", nullFilter, LogicalFilterType.AND);
        Set<String> result = indexSearcher.doSearch(spatialQuery);
        logResultReport("DateSearch 1:", result);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_9s_19900610041000");
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
    public void sortedSearchTest() throws Exception {

        Filter nullFilter   = null;

        /**
         * Test 1 sorted search: all orderBy identifier ASC
         */
        SpatialQuery spatialQuery = new SpatialQuery("metafile:doc", nullFilter, LogicalFilterType.AND);
        SortField sf = new SortField("identifier_sort", SortField.Type.STRING, false);
        spatialQuery.setSort(new Sort(sf));

        Set<String> result = indexSearcher.doSearch(spatialQuery);
        logResultReport("SortedSearch 1:", result);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1");

        assertEquals(expectedResult, result);

        /**
         * Test 2 sorted search: all orderBy identifier DSC
         */
        spatialQuery = new SpatialQuery("metafile:doc", nullFilter, LogicalFilterType.AND);
        sf = new SortField("identifier_sort", SortField.Type.STRING, true);
        spatialQuery.setSort(new Sort(sf));

        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("SortedSearch 2:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1");
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
        spatialQuery = new SpatialQuery("metafile:doc", nullFilter, LogicalFilterType.AND);
        sf = new SortField("Abstract_sort", SortField.Type.STRING, false);
        spatialQuery.setSort(new Sort(sf));

        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("SortedSearch 3:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("CTDF02");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1");

        assertEquals(expectedResult, result);

        /**
         * Test 4 sorted search: all orderBy Abstract DSC
         */
        spatialQuery = new SpatialQuery("metafile:doc", nullFilter, LogicalFilterType.AND);
        sf = new SortField("Abstract_sort", SortField.Type.STRING, true);
        spatialQuery.setSort(new Sort(sf));

        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("SortedSearch 4:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1");
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");

        assertEquals(expectedResult, result);
    }

    /**
     *
     * Test spatial lucene search.
     *
     * @throws java.lang.Exception
     */
    public void spatialSearchTest() throws Exception {

        /**
         * Test 1 spatial search: BBOX filter
         */
        double min1[] = {-20, -20};
        double max1[] = { 20,  20};
        GeneralEnvelope bbox = new GeneralEnvelope(min1, max1);
        CoordinateReferenceSystem crs = CommonCRS.defaultGeographic();
        bbox.setCoordinateReferenceSystem(crs);
        LuceneOGCFilter sf          = LuceneOGCFilter.wrap(FF.bbox(LuceneOGCFilter.GEOMETRY_PROPERTY, -20, -20, 20, 20, "EPSG:4326"));
        SpatialQuery spatialQuery = new SpatialQuery("metafile:doc", sf, LogicalFilterType.AND);

        Set<String> result = indexSearcher.doSearch(spatialQuery);
        logResultReport("spatialSearch 1:", result);

        Set<String> expectedResult = new LinkedHashSet<>();
        expectedResult.add("39727_22_19750113062500");
        expectedResult.add("11325_158_19640418141800");
        expectedResult.add("CTDF02");

        assertEquals(expectedResult, result);

        /**
         * Test 1 spatial search: NOT BBOX filter
         */
        List<Filter> lf = new ArrayList<>();
        //sf           = new BBOXFilter(bbox, "urn:x-ogc:def:crs:EPSG:6.11:4326");
        sf           = LuceneOGCFilter.wrap(FF.bbox(LuceneOGCFilter.GEOMETRY_PROPERTY, -20, -20, 20, 20, "EPSG:4326"));
        lf.add(sf);
        LogicalFilterType[] op = {LogicalFilterType.NOT};
        SerialChainFilter f = new SerialChainFilter(lf, op);
        spatialQuery = new SpatialQuery("metafile:doc", f, LogicalFilterType.AND);

        result = indexSearcher.doSearch(spatialQuery);
        logResultReport("spatialSearch 2:", result);

        expectedResult = new LinkedHashSet<>();
        expectedResult.add("42292_5p_19900609195600");
        expectedResult.add("42292_9s_19900610041000");
        expectedResult.add("40510_145_19930221211500");
        expectedResult.add("MDWeb_FR_SY_couche_vecteur_258");
        expectedResult.add("Spot5-Cyprus-THX-IMAGERY3_ortho1");

        assertEquals(expectedResult, result);
    }

    public void TermQueryTest() throws Exception {

        /**
         * Test 1
         */

        String identifier = "39727_22_19750113062500";
        String result = indexSearcher.identifierQuery(identifier);

        logger.log(Level.FINER, "identifier query 1:\n{0}", result);

        String expectedResult = "39727_22_19750113062500";

        assertEquals(expectedResult, result);

        /**
         * Test 2
         */

        identifier = "CTDF02";
        result = indexSearcher.identifierQuery(identifier);

        logger.log(Level.FINER, "identifier query 2:\n{0}", result);

        expectedResult = "CTDF02";

        assertEquals(expectedResult, result);
    }
}
