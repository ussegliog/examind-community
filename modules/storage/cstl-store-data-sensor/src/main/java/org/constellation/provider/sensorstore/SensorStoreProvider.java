/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2016 Geomatys.
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
package org.constellation.provider.sensorstore;

import java.nio.file.Path;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.apache.sis.storage.DataStoreException;
import org.constellation.api.DataType;
import org.constellation.provider.AbstractDataProvider;
import org.constellation.provider.Data;
import org.constellation.provider.DataProviderFactory;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.util.NamesExt;
import org.opengis.parameter.GeneralParameterValue;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.util.GenericName;
import org.apache.sis.internal.storage.ResourceOnFileSystem;
import org.apache.sis.storage.DataStore;
import org.apache.sis.util.ArraysExt;
import org.constellation.exception.ConstellationException;
import org.constellation.exception.ConstellationStoreException;
import org.constellation.provider.SensorProvider;
import org.geotoolkit.sensor.AbstractSensorStore;
import org.geotoolkit.sml.xml.AbstractSensorML;
import org.geotoolkit.storage.ResourceType;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class SensorStoreProvider extends AbstractDataProvider implements SensorProvider {


    private final Set<GenericName> index = new LinkedHashSet<>();
    private AbstractSensorStore store;


    public SensorStoreProvider(String providerId, DataProviderFactory service, ParameterValueGroup param) throws DataStoreException{
        super(providerId,service,param);
        visit();
    }

    @Override
    public DataType getDataType() {
        final org.apache.sis.storage.DataStoreProvider provider = getMainStore().getProvider();
        final ResourceType[] resourceTypes = DataStores.getResourceTypes(provider);
        if (ArraysExt.contains(resourceTypes, ResourceType.COVERAGE)
             || ArraysExt.contains(resourceTypes, ResourceType.GRID)
             || ArraysExt.contains(resourceTypes, ResourceType.PYRAMID)) {
            return DataType.COVERAGE;
        } else if (ArraysExt.contains(resourceTypes, ResourceType.VECTOR)) {
            return DataType.VECTOR;
        } else if (ArraysExt.contains(resourceTypes, ResourceType.SENSOR)) {
            return DataType.SENSOR;
        } else if (ArraysExt.contains(resourceTypes, ResourceType.METADATA)) {
            return DataType.METADATA;
        } else {
            return DataType.VECTOR; // unknown
        }
    }

    /**
     * @return the datastore this provider encapsulate.
     */
    @Override
    public synchronized AbstractSensorStore getMainStore(){
        if(store==null){
            store = createBaseStore();
        }
        return store;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public synchronized Set<GenericName> getKeys() {
        return Collections.unmodifiableSet(index);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Data get(final GenericName key) {
        return get(key, null);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Data get(GenericName key, Date version) {
        key = fullyQualified(key);
        if(!contains(key)){
            return null;
        }

        final AbstractSensorStore store = getMainStore();
        try {
            final AbstractSensorML metadata = store.getSensorML(key.toString());
            return new DefaultSensorData(key, store, metadata);

        } catch (DataStoreException ex) {
            getLogger().log(Level.WARNING, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    protected synchronized void visit() {
        store = createBaseStore();

        try {

            for (final String sensor : store.getSensorNames()) {
                GenericName name = NamesExt.create(sensor);
                if (!index.contains(name)) {
                    index.add(name);
                }
            }

        } catch (DataStoreException ex) {
            //Looks like we failed to retrieve the list of featuretypes,
            //the layers won't be indexed and the getCapability
            //won't be able to find thoses layers.
            getLogger().log(Level.SEVERE, "Failed to retrive list of available sensor names.", ex);
        }


        super.visit();
    }

    protected AbstractSensorStore createBaseStore() {
        //parameter is a choice of different types
        //extract the first one
        ParameterValueGroup param = getSource();
        param = param.groups("choice").get(0);
        ParameterValueGroup factoryconfig = null;
        for(GeneralParameterValue val : param.values()){
            if(val instanceof ParameterValueGroup){
                factoryconfig = (ParameterValueGroup) val;
                break;
            }
        }

        if(factoryconfig == null){
            getLogger().log(Level.WARNING, "No configuration for feature store source.");
            return null;
        }
        try {
            //create the store
            org.apache.sis.storage.DataStoreProvider provider = DataStores.getProviderById(factoryconfig.getDescriptor().getName().getCode());
            org.apache.sis.storage.DataStore tmpStore = provider.open(factoryconfig);
            if (tmpStore == null) {
                throw new DataStoreException("Could not create sensor store for parameters : "+factoryconfig);
            } else if (!(tmpStore instanceof AbstractSensorStore)) {
                throw new DataStoreException("Could not create sensor store for parameters : "+factoryconfig + " (not a sensor store)");
            }
            return (AbstractSensorStore) tmpStore;
        } catch (Exception ex) {
            getLogger().log(Level.WARNING, ex.getMessage(), ex);
        }
        return null;
    }

    @Override
    public synchronized boolean remove(GenericName key) {
        final AbstractSensorStore store = getMainStore();
        boolean result = false;
        try {
            result = store.deleteSensor(key.toString());
            reload();
        } catch (DataStoreException ex) {
            getLogger().log(Level.INFO, "Unable to remove " + key.toString() + " from provider.", ex);
        }
        return result;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public synchronized void reload() {
        dispose();
        visit();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public synchronized void dispose() {
        if(store != null){
           store.close();
        }
        index.clear();
    }

    @Override
    public boolean isSensorAffectable() {
        return false;
    }

    @Override
    public Path[] getFiles() throws ConstellationException {
        DataStore currentStore = store;
        if (!(currentStore instanceof ResourceOnFileSystem)) {
            throw new ConstellationException("Store is not made of files.");
        }

        final ResourceOnFileSystem fileStore = (ResourceOnFileSystem)currentStore;
        try {
            return fileStore.getComponentFiles();
        } catch (DataStoreException ex) {
            throw new ConstellationException(ex);
        }
    }

    @Override
    public String getNewSensorId() throws ConstellationStoreException {
        final AbstractSensorStore store = getMainStore();
        try {
            return store.getNewSensorId();
        } catch (DataStoreException ex) {
            getLogger().log(Level.INFO, "Unable to acquire a new sensorID from provider.", ex);
        }
        return null;
    }

    @Override
    public boolean writeSensor(String id, Object sensor) throws ConstellationStoreException {
        final AbstractSensorStore store = getMainStore();
        boolean result = false;
        try {
            result = store.writeSensor(id, sensor);
            reload();
        } catch (DataStoreException ex) {
            getLogger().log(Level.INFO, "Unable to insert a new sensor in provider:" + id, ex);
        }
        return result;
    }

    @Override
    public Map<String, List<String>> getAcceptedSensorMLFormats() {
        final AbstractSensorStore store = getMainStore();
        return store.getAcceptedSensorMLFormats();
    }
}
