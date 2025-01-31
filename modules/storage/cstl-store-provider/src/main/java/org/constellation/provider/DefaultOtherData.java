/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.constellation.provider;

import java.util.Collections;
import java.util.List;
import org.apache.sis.measure.MeasurementRange;
import org.apache.sis.storage.DataStore;
import org.apache.sis.storage.Resource;
import org.constellation.api.DataType;
import org.constellation.dto.DataDescription;
import org.constellation.exception.ConstellationStoreException;
import org.geotoolkit.metadata.ImageStatistics;
import org.opengis.geometry.Envelope;
import org.opengis.util.GenericName;

/**
 *
 * @author guilhem
 */
public class DefaultOtherData extends AbstractData {

    private final Resource ref;
    protected final DataStore store;

    public DefaultOtherData(GenericName name, final Resource ref, final DataStore store) {
        super(name, Collections.EMPTY_LIST);
        this.ref = ref;
        this.store = store;

    }

    @Override
    public Resource getOrigin(){
        return ref;
    }

    @Override
    public Envelope getEnvelope() throws ConstellationStoreException {
        return null;
    }

    @Override
    public MeasurementRange<?>[] getSampleValueRanges() {
        return new MeasurementRange<?>[0];
    }

    @Override
    public DataStore getStore() {
        return store;
    }

    @Override
    public DataDescription getDataDescription(ImageStatistics stats) throws ConstellationStoreException {
        return null;
    }

    @Override
    public DataType getDataType() {
        return DataType.OTHER;
    }

    @Override
    public String getResourceCRSName() throws ConstellationStoreException {
        return null;
    }

}
