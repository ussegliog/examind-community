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
package org.constellation.repository;

import org.constellation.dto.Data;

import java.util.List;
import java.util.Map;

public interface DataRepository {

    List<Data> findAll();

    List<Data> findAllByVisibility(boolean hidden);

    Integer countAll(boolean includeInvisibleData);

    Data findById(int dataId);

    Integer create(Data data);

    int delete(int id);

    int delete(String namespaceURI, String localPart, int providerId);

    Data findDataFromProvider(String namespaceURI, String localPart, String providerId);

    Integer findIdFromProvider(String namespaceURI, String localPart, String providerId);

    Data findByMetadataId(String metadataId);

    List<Data> findByProviderId(Integer id);

    List<Integer> findIdsByProviderId(Integer id);

    List<Data> findByProviderId(Integer id, String dataType, boolean included, boolean hidden);

    List<Integer> findIdsByProviderId(Integer id, String dataType, boolean included, boolean hidden);

    List<Data> findByDatasetId(Integer id);

    List<Data> findByDatasetId(Integer datasetId, boolean included, boolean hidden);

    List<Data> findAllByDatasetId(Integer id);

    List<Data> findByServiceId(Integer id);

    List<Data> findStatisticLess();

    Data findByNameAndNamespaceAndProviderId(String localPart, String namespaceURI, Integer providerId);

    void update(Data data);

    Data findByIdentifierWithEmptyMetadata(String localPart);

    List<Data> getCswLinkedData(final int cswId);

    void addDataToCSW(final int serviceID, final int dataID);

    void removeDataFromCSW(final int serviceID, final int dataID);

    void removeDataFromAllCSW(final int dataID);

    void removeAllDataFromCSW(final int serviceID);

    void linkDataToData(final int dataId, final int childId);

    List<Data> getDataLinkedData(final int dataId);

    /**
     * Remove all cross reference between a data and his children.
     * Children data are not removed, only cross references are.
     * @param dataId origin data id
     */
    void removeLinkedData(final int dataId);

    /**
     * Retrieve all Data linked to given style id.
     * Returned data objects are complete (view of Data table)
     *
     * @param styleId style id candidate
     * @return a list of full {@link Data}
     */
    List<Data> getFullDataByLinkedStyle(final int styleId);

    /**
     * Retrieve all Data linked to given style id.
     * Returned data objects are lighten view of Data table with only :
     * <ul>
     *     <li>id</li>
     *     <li>name</li>
     *     <li>namespace</li>
     *     <li>provider</li>
     *     <li>type</li>
     *     <li>subtype</li>
     * </ul>
     *
     * @param styleId style id candidate
     * @return a list of full {@link Data}
     */
    List<Data> getRefDataByLinkedStyle(final int styleId);

    boolean existsById(int dataId);

    Integer getDatasetId(int dataId);

    void updateOwner(int dataId, int newOwner);

    Map.Entry<Integer, List<Data>> filterAndGet(Map<String, Object> filterMap, Map.Entry<String, String> sortEntry, int pageNumber, int rowsPerPage);

     void updateStatistics(int dataId, String statsResult, String statsState);

}
