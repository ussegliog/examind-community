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

package org.constellation.process.data;

import org.geotoolkit.process.ProcessException;
import org.opengis.parameter.ParameterValueGroup;
import static org.geotoolkit.parameter.Parameters.*;
import static org.constellation.process.data.ProviderToDataReferenceDescriptor.*;
import java.util.Date;
import org.constellation.util.DataReference;
import org.geotoolkit.processing.AbstractProcess;

/**
 *
 * @author Quentin Boileau (Geomatys)
 */
public class ProviderToDataReference extends AbstractProcess {

    public ProviderToDataReference( final ParameterValueGroup input) {
        super(ProviderToDataReferenceDescriptor.INSTANCE, input);
    }

    @Override
    protected void execute() throws ProcessException {
        final String providerID     = inputParameters.getValue(PROVIDER_ID);
        final String layerID        = inputParameters.getValue(LAYER_ID);
        final String providerType   = inputParameters.getValue(PROVIDER_TYPE);
        final Date version          = inputParameters.getValue(VERSION);

        String type;
        if ("layer".equals(providerType)) {
            type = DataReference.PROVIDER_LAYER_TYPE;
        } else {
            type = DataReference.PROVIDER_STYLE_TYPE;
        }

        final DataReference dataReference = 
                DataReference.createProviderDataReference(type, providerID, layerID, version);

        getOrCreate(DATA_REFERENCE, outputParameters).setValue(dataReference);
    }

}
