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
package org.constellation.business;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import org.constellation.dto.DataSummary;
import org.constellation.exception.ConstellationException;
import org.constellation.exception.ConfigurationException;
import org.constellation.dto.DataBrief;
import org.constellation.dto.importdata.FileBean;
import org.constellation.dto.ParameterValues;
import org.constellation.dto.metadata.MetadataLightBrief;
import org.constellation.dto.process.DataProcessReference;

/**
 * @author Cédric Briançon (Geomatys)
 */
public interface IDataBusiness {

    Integer getDataProvider(Integer dataId);

    Integer getDataDataset(Integer dataId);

    /**
     * Delete data from database and delete data's dataset if it's empty.
     * This should be called when a provider update his layer list and
     * one layer was removed by external modification.
     * Because this method delete data entry in Data table, every link to
     * this data should be cascaded.
     *
     * Do not use this method to remove a data, use {@link #removeData(Integer, boolean)} instead.
     *
     * @param name given data name.
     * @param providerIdentifier given provider identifier.
     * @throws org.constellation.exception.ConfigurationException
     */
    void missingData(QName name, String providerIdentifier) throws ConstellationException;

    /**
     * Set {@code included} attribute to {@code false} in removed data and his children.
     * This may remove data/provider/dataset depending of the state of provider/dataset.
     *
     * @see #updateDataIncluded(int, boolean, boolean)
     * @param dataId Data identifier.
     * @param removeFiles Flag to indicate if you want to remove the datas from the data source (Files, database tables, ...)
     * @throws org.constellation.exception.ConfigurationException
     */
    void removeData(Integer dataId, final boolean removeFiles) throws ConstellationException;

    /**
     * Proceed to create a new data for given parameters.
     *
     * TODO seems only used by Junit tests, should not be public in DataBusiness API.
     *
     * @param name data name to create.
     * @param providerIdentifier provider identifier.
     * @param type data type.
     * @param sensorable flag that indicates if data is sensorable.
     * @param included flag that indicates if data is included.
     * @param subType data subType.
     * @param metadataXml metadata of data.
     * @deprecated seems only used by Junit tests use {@link #create(javax.xml.namespace.QName, String, String, boolean, boolean, Boolean, String, String)}
     * instead.
     */
    Integer create(QName name, String providerIdentifier, String type, boolean sensorable, boolean included, String subType, String metadataXml);

    /**
     * Proceed to create a new data for given parameters.
     * @param name data name to create.
     * @param providerIdentifier provider identifier.
     * @param type data type.
     * @param sensorable flag that indicates if data is sensorable.
     * @param included flag that indicates if data is included.
     * @param rendered flag that indicates if data is rendered (can be null).
     * @param subType data subType.
     * @param metadataXml metadata of data.
     */
    Integer create(QName name, String providerIdentifier, String type, boolean sensorable, boolean included, Boolean rendered, String subType, String metadataXml);

    /**
     * Proceed to create a new data for given parameters.
     * @param name data name to create.
     * @param providerIdentifier provider identifier.
     * @param type data type.
     * @param sensorable flag that indicates if data is sensorable.
     * @param included flag that indicates if data is included.
     * @param rendered flag that indicates if data is rendered (can be null).
     * @param subType data subType.
     * @param metadataXml metadata of data.
     * @param hidden flag that indicates if data is hidden.
     * @param owner the owner id, or {@code null} if you want to use the current logged user.
     */
    Integer create(QName name, String providerIdentifier, String type, boolean sensorable, boolean included, Boolean rendered, String subType, String metadataXml, boolean hidden, Integer owner);

    /**
     * Proceed to remove data for given provider.
     * Synchronized method.
     * @param providerId given provider identifier.
     * @throws org.constellation.exception.ConfigurationException
     */
    void removeDataFromProvider(Integer providerId) throws ConfigurationException ;

    /**
     * Returns {@link DataBrief} for given data name and provider id as integer.
     *
     * @param dataId data id.
     * @return {@link DataBrief}.
     * @throws ConstellationException is thrown if result fails.
     */
    DataBrief getDataBrief(int dataId) throws ConstellationException;

    /**
     * Returns {@link DataBrief} for given data name and provider id as integer.
     *
     * @param dataName given data name.
     * @param providerId given data provider as integer.
     * @return {@link DataBrief}.
     * @throws ConstellationException is thrown if result fails.
     */
    DataBrief getDataBrief(QName dataName, Integer providerId) throws ConstellationException;

    /**
     * Returns {@link DataBrief} for given data name and provider identifier as string.
     *
     * @param fullName given data name.
     * @param providerIdentifier given data provider identifier.
     * @return {@link DataBrief}
     * @throws ConstellationException is thrown if result fails.
     */
    DataBrief getDataBrief(QName fullName, String providerIdentifier) throws ConstellationException;

    /**
     * Returns The data identifier for given data name and provider identifier as string.
     *
     * @param fullName given data name.
     * @param providerId given data provider identifier.
     * @return {@link DataBrief}
     * @throws org.constellation.exception.ConstellationException
     */
    Integer getDataId(QName fullName, int providerId) throws ConstellationException;

    /**
     * Returns a metadata object for given providerId and data name.
     * @param providerId given data provider id.
     * @param name given data name.
     * @return A metadata object
     * @throws org.constellation.exception.ConfigurationException is thrown for UnsupportedEncodingException or JAXBException.
     */
    Object loadIsoDataMetadata(String providerId, QName name)  throws ConfigurationException;

    /**
     * Returns a metadata object for given data id.
     * @param dataID identifier of the data
     * @return A metadata object
     * @throws org.constellation.exception.ConfigurationException is thrown for UnsupportedEncodingException or JAXBException.
     */
    Object loadIsoDataMetadata(int dataID)  throws ConfigurationException;

    /**
     * Proceed to remove all data.
     * @throws org.constellation.exception.ConfigurationException
     */
    void deleteAll() throws ConstellationException ;

    /**
     * Update data {@code included} attribute.
     * If data {@code included} is set to {@code false}, all layers using this data are deleted,
     * data is removed from all CSW and reload them, delete data provider if all siblings data also have
     * their {@code included} attribute set as {@code false}.
     * This may also delete data's dataset if it's empty.
     *
     * @param dataId the given data Id.
     * @param included value to set
     * @param removeFiles Flag to indicate if you want to remove the datas from the data source (Files, database tables, ...)
     * @throws org.constellation.exception.ConfigurationException
     */
    void updateDataIncluded(int dataId, boolean included, final boolean removeFiles) throws ConstellationException;

    /**
     * Returns {@link DataBrief} for given layer identifier.
     *
     * @param layerId given layer identifier.
     *
     * @return {@link DataBrief}.
     * @throws ConstellationException is thrown if result fails.
     */
    DataBrief getDataLayer(int layerId) throws ConstellationException;

    /**
     * Returns a list of {@link DataBrief} for given metadata identifier.
     *
     * @param metadataId given metadata identifier.
     * @return list of {@link DataBrief}.
     */
    List<DataBrief> getDataBriefsFromMetadataId(String metadataId);

    /**
     * Returns a list of {@link DataBrief} for given dataSet id.
     *
     * @param datasetId the given dataSet id.
     * @return the list of {@link DataBrief}.
     */
    List<DataBrief> getDataBriefsFromDatasetId(Integer datasetId);

    List<DataSummary> getDataSummaryFromDatasetId(Integer datasetId) throws ConfigurationException;

    /**
     * Returns list of {@link DataBrief} for given dataSet id, with filter on the hidden and included flags.
     *
     * @param datasetId the given dataSet id.
     * @param included included flag filter
     * @param hidden hidden flag filter.
     * @param sensorable sensorable flag filter, can be {@code null}.
     * @param published service published flag filter, can be {@code null}.
     *
     * @return the list of {@link DataBrief}.
     * @throws org.constellation.exception.ConstellationException
     */
    List<DataBrief> getDataBriefsFromDatasetId(final Integer datasetId, boolean included, boolean hidden, Boolean sensorable, Boolean published) throws ConstellationException;

    /**
     * Returns list of {@link DataBrief} for given dataSet id, with filter on the hidden and included flags.
     *
     * @param providerId the given provider id.
     * @param datatype filter on data type, can be {@code null}.
     * @param included included flag filter
     * @param hidden hidden flag filter.
     * @param sensorable sensorable flag filter, can be {@code null}.
     * @param published service published flag filter, can be {@code null}.
     *
     * @return the list of {@link DataBrief}.
     * @throws org.constellation.exception.ConstellationException
     */
    List<DataBrief> getDataBriefsFromProviderId(final Integer providerId, String datatype, boolean included, boolean hidden, Boolean sensorable, Boolean published, Boolean fetchDataDescription) throws ConstellationException;

    /**
     * Returns a list of {@link DataBrief} for given style id.
     *
     * @param styleId the given style id.
     * @return the list of {@link DataBrief}.
     */
    List<DataBrief> getDataBriefsFromStyleId(final Integer styleId);

    /**
     * Returns a list of light {@link DataBrief} for given style id.
     * Output DataBrief contain only :
     * <ul>
     *     <li>id</li>
     *     <li>name</li>
     *     <li>namespace</li>
     *     <li>provider</li>
     *     <li>type</li>
     *     <li>subtype</li>
     * </ul>
     *
     * @param styleId the given style id.
     * @return the list of light {@link DataBrief}.
     */
    List<DataBrief> getDataRefsFromStyleId(final Integer styleId);

    ParameterValues getVectorDataColumns(int id) throws ConfigurationException;

    /**
     * Update the data metadata
     *
     * @param dataId the given data id.
     * @param metadata metadata object
     * @param hidden flag to indicate if the metadata should be hidden for now.
     *
     */
    MetadataLightBrief updateMetadata(int dataId, Object metadata, boolean hidden) throws ConstellationException;

    /**
     * Give subfolder list from a server file path
     *
     * @param path server file path
     * @param filtered {@code True} if we want to keep only known files.
     * @param onlyXML flag to list only xml files used list metadata xml.
     * @return the file list
     */
    List<FileBean> getFilesFromPath(final String path, final boolean filtered, final boolean onlyXML) throws ConstellationException;

    /**
     * Returns list of all {@link DataProcessReference}.
     *
     * @return A list of {@link DataProcessReference} object.
     */
    List<DataProcessReference> findAllDataProcessReference();

    /**
     * Run {@link org.constellation.business.IDataCoverageJob#asyncUpdateDataStatistics(int)}
     * on each coverage type data without computed statistics.
     * @param isInit flag that define if it's a startup call.
     *               If true, statistic of all data in ERROR and PENDING will be also computed
     */
    void computeEmptyDataStatistics(boolean isInit);

    /**
     * Search for data without statistics and launch analysis on them.
     */
    void updateDataStatistics();

    /**
     * Remove old hidden data every hour.
     */
    void removeOldHiddenData() throws ConstellationException;

    /**
     * Update {@link org.constellation.dto.Data#getRendered()} attribute that define
     * if a data is Rendered or Geophysic.
     *
     * @param fullName data name
     * @param providerIdentifier provider identifier name
     * @param isRendered if true data is Rendered, otherwise it's Geophysic
     */
    void updateDataRendered(QName fullName, String providerIdentifier, boolean isRendered);

    /**
     * Update {@link org.constellation.dto.Data#getRendered()} attribute that define
     * if a data is Rendered or Geophysic.
     *
     * @param dataId data identifier
     * @param isRendered if true data is Rendered, otherwise it's Geophysic
     */
    void updateDataRendered(int dataId, boolean isRendered);

    /**
     * Update {@link org.constellation.dto.Data#getOwnerId()} attribute that define
     * The data owner.
     *
     * @param dataId data identifier
     * @param newOwner The new owner of the data.
     */
    void updateDataOwner(int dataId, int newOwner);

    /**
     * Update {@link org.constellation.dto.Data#datasetId} attribute.
     *
     * @param dataId data identifier
     * @param datasetId dataset Id value to set
     */
    void updateDataDataSetId(Integer dataId, Integer datasetId);

    /**
     * Update hidden for data
     * @param dataId
     * @param value
     */
    void updateDataHidden(final int dataId, boolean value);

    /**
     * Returns count of all data
     * @param includeInvisibleData flag that indicates if the count will includes hidden data.
     * @return int
     */
    Integer getCountAll(boolean includeInvisibleData);

    void linkDataToData(final int dataId, final int childId);

    void uploadCleaner();

    boolean existsById(int dataId);

    Map<String, Object> getDataAssociations(int dataId);

    /**
     * Return the Files to export for the selected data.
     *
     * TODO this method return ALL the file of the datastore.
     *
     * @param dataId identifier of the data.
     * @return
     * @throws org.constellation.exception.ConstellationException
     */
    Path[] exportData(int dataId) throws ConstellationException;

    /**
     * Proceed to extract metadata from reader and fill additional info
     * then save metadata in dataset.
     *
     * Warning metadata are actually for all the provider
     *
     * @param dataId given data identifier.
     * @param hidden flag to indicate if the metadata should be hidden for now.
     * @throws ConstellationException
     */
    MetadataLightBrief initDataMetadata(final int dataId, final boolean hidden) throws ConstellationException;

    Map.Entry<Integer, List<DataBrief>> filterAndGetBrief(Map<String, Object> filterMap, Map.Entry<String, String> sortEntry, int pageNumber, int rowsPerPage);

    DataBrief acceptData(int id, int owner, boolean hidden) throws ConstellationException;

    Map<String, List> acceptDatas(List<Integer> ids, int owner, boolean hidden) throws ConstellationException;

}
