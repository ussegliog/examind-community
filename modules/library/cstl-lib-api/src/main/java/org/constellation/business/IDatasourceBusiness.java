/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2018 Geomatys.
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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.constellation.dto.DataSource;
import org.constellation.dto.DataSourceSelectedPath;
import org.constellation.dto.importdata.DatasourceAnalysisV3;
import org.constellation.dto.importdata.FileBean;
import org.constellation.dto.ProviderConfiguration;
import org.constellation.dto.importdata.ResourceStoreAnalysisV3;
import org.constellation.exception.ConstellationException;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public interface IDatasourceBusiness {

    public static enum AnalysisState {
        PENDING,
        COMPLETED,
        NOT_STARTED,
        ERROR
    }

    public static enum PathStatus {
        PENDING,
        NO_DATA,
        ERROR,
        INTEGRATED,
        COMPLETED,
        REMOVED
    }

    /**
     * Store a new Datasource.
     *
     * @param ds the new datasource to store.
     * @return the assigned datasource id.
     */
    Integer create(DataSource ds);

    /**
     * Update a Datasource.
     *
     * @param ds the new datasource to update, id must not be null.
     */
    void update(DataSource ds) throws ConstellationException;

    /**
     * Close a datasource, identified by its id.
     *  - Close the current analysis, if its still going on.
     *  - if the datasource is of type local_files , the files are removed.
     *  - close the fileSystem.
     *
     * @param id the datasource identifier.
     */
    void close(int id);

    /**
     * Remove a datasource, identified by its id.
     * Call {@link IDatasourceBusiness#close()} before removing it.
     *
     * @param id the datasource identifier.
     */
    void delete(int id) throws ConstellationException;

    /**
     * Remove all the datasources.
     * Call {@link IDatasourceBusiness#close()}
     *
     */
    void deleteAll() throws ConstellationException;

    /**
     * Find a datasource by its id.
     *
     * @param id the searched datasource identifier.
     *
     * @return A Datasource or {@code null}
     */
    DataSource getDatasource(int id);

    /**
     * Find a datasource by its utl.
     *
     * @param url the searched datasource url.
     *
     * @return A Datasource or {@code null}
     */
    DataSource getByUrl(String url);

    /**
     * Test if the url pointed by the datasource is reachable.
     * The datasource object, does not have to be recorded in the system to test it.
     *
     * @param ds A datasource.
     *
     * @return Return the keyword "OK" if the datasource is reachable, else, return the exception message explaining the problem.
     */
    String testDatasource(DataSource ds);

    /**
     * If no path have been already selected, automaticaly select all the paths correspounding
     * to the datasource selected store and format.
     *
     * @param ds A datasource.
     */
    void recordSelectedPath(DataSource ds);

    /**
     * Remove a recorded datasource path from the system.
     *
     * @param ds A datasource.
     * @param path The path of a file.
     */
    void removePath(DataSource ds, String path);

    /**
     * Return the list of datasource path that have been selected to import by the user.
     *
     * @param ds A datasource.
     * @param limit maximum number of path returned.
     *
     * @return A list of selected datasource path.
     * @throws ConstellationException
     */
    List<DataSourceSelectedPath> getSelectedPath(DataSource ds, Integer limit) throws ConstellationException;

    /**
     * Return a datasource path that have been selected to import by the user with the specified path.
     *
     * @param ds A datasource.
     * @param path the searched path.
     *
     * @return A list of selected datasource path.
     * @throws ConstellationException
     */
    DataSourceSelectedPath getSelectedPath(DataSource ds, String path);

    /**
     * Return {@code true} if the specified path exist and has been selected for import by the user.
     *
     * @param dsId the datasource identifier.
     * @param path the searched path.
     *
     * @return
     */
    boolean existSelectedPath(final int dsId, String path);

    /**
     * Return a List of path informations for the specified sub path (not recursive).
     *
     * @param dsId the datasource identifier.
     * @param subPath the datasource sub path.
     *
     * @return A list of file informations.
     * @throws ConstellationException
     */
    List<FileBean> exploreDatasource(final Integer dsId, final String subPath) throws ConstellationException;

    /**
     * Treat all the selected datasource path, using the specified provider configuration.
     * Instanciate provider, data and metadata for each path, with an hidden flag.
     *
     * @param dsId The datasource identifier.
     * @param provConfig The provider configuration, containing various custom parameters for the datastore.
     *
     * @return A list of instancied store containing data.
     * @throws ConstellationException
     */
    DatasourceAnalysisV3 analyseDatasourceV3(final Integer dsId, ProviderConfiguration provConfig) throws ConstellationException;

    /**
     * Add a new datasource path in the selection for import.
     *
     * @param dsId The datasource identifier.
     * @param subPath The path to add at the selection.
     */
    void addSelectedPath(final int dsId,  String subPath);

    /**
     * Clear the datasource selection of path to import.
     *
     * @param id The datasource identifier.
     */
    void clearSelectedPaths(int id);

    /**
     * Remove all the datasource not permanent and older t han 24 hours.
     *
     * @throws ConstellationException
     */
    void removeOldDatasource() throws ConstellationException;

    /**
     * Perform an analysis on each file of the datasource (if deep is set to false, perform it only in the first level).
     * Then return a map of store / formats detected in the datasource.
     *
     * @param id The datasource identifier.
     * @param async if true, and if the datasource is not yet analyzed, it will return an empty result and perform the analysis on a new Thread.
     * @param deep if false, it will only analyse the first level of tha datasource.
     *
     * @return A map of store / formats detected in the datasource.
     * @throws ConstellationException
     */
    Map<String, Set<String>> computeDatasourceStores(int id, boolean async, boolean deep) throws ConstellationException;

    /**
     * Perform an analysis on each file of the datasource (if deep is set to false, perform it only in the first level).
     * if storeId is not null, try to analyse the files only with the specified store.
     * Then return a map of store / formats detected in the datasource.
     *
     * @param id The datasource identifier.
     * @param async if true, and if the datasource is not yet analyzed, it will return an empty result and perform the analysis on a new Thread.
     * @param storeId Allow to analyse the file only against one store.
     * @param deep if false, it will only analyse the first level of tha datasource.
     *
     * @return A map of store / formats detected in the datasource.
     * @throws ConstellationException
     */
    Map<String, Set<String>> computeDatasourceStores(int id, boolean async, String storeId, boolean deep) throws ConstellationException;

    /**
     * Return the current state of the datasource analysis going on (or already finished).
     * for the different possible values see {@link AnalysisState}
     *
     * @param id The datasource identifier.
     * @return the current state of the datasource analysis.
     */
    String getDatasourceAnalysisState(int id);

    /**
     * Update the current state of the datasource analysis.
     * for the different possible vlues see {@link AnalysisState}
     *
     * @param id The datasource identifier.
     * @return the current state of the datasource analysis.
     */
    void updateDatasourceAnalysisState(int dsId, String state);

    /**
     * Analyse and treat the specified datasource select path.
     *
     * Instanciate provider, data and metadata for the path, with the specified hidden flag.
     *
     * @param p The datasource path.
     * @param ds The datasource.
     * @param provConfig The provider configuration, containing various custom parameters for the datastore.
     * @param hidden A flag applied to the data and metadata created.
     * @param datasetId The dataset identifier in which the data will be inserted.
     * @param owner the owner of the created provider, data and metadata.
     *
     * @return informations about the generated objects.
     * @throws ConstellationException
     */
    ResourceStoreAnalysisV3 treatDataPath(DataSourceSelectedPath p, DataSource ds, ProviderConfiguration provConfig, boolean hidden, Integer datasetId, Integer owner) throws ConstellationException;

    /**
     * Update the status of the specified datasource path.
     * for the different possible vlues see {@link PathStatus}
     *
     * @param id The datasource identifier.
     * @param path the designed datasource path.
     * @param newStatus the new status of the path.
     */
    void updatePathStatus(int id, String path, String newStatus);
}
