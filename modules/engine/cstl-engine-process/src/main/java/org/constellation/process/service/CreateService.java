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
package org.constellation.process.service;

import org.constellation.business.IServiceBusiness;
import org.constellation.exception.ConfigurationException;
import org.constellation.dto.contact.Details;
import org.constellation.process.AbstractCstlProcess;
import org.geotoolkit.process.ProcessDescriptor;
import org.geotoolkit.process.ProcessException;
import org.opengis.parameter.ParameterValueGroup;
import org.springframework.beans.factory.annotation.Autowired;

import static org.constellation.process.service.CreateServiceDescriptor.CONFIGURATION;
import static org.constellation.process.service.CreateServiceDescriptor.IDENTIFIER;
import static org.constellation.process.service.CreateServiceDescriptor.OUT_CONFIGURATION;
import static org.constellation.process.service.CreateServiceDescriptor.SERVICE_METADATA;
import static org.constellation.process.service.CreateServiceDescriptor.SERVICE_TYPE;
import static org.geotoolkit.parameter.Parameters.getOrCreate;

/**
 * Process that create a new instance configuration from the service name (WMS, WMTS, WCS or WFS) for a specified instance name.
 * If the instance directory is created but no configuration file exist, the process will create one.
 * Execution will throw ProcessExeption if the service name is different from WMS, WMTS of WFS (no matter of case) or if
 * a configuration file already exist fo this instance name.
 * @author Quentin Boileau (Geomatys).
 */
public class CreateService extends AbstractCstlProcess {
    @Autowired
    public IServiceBusiness serviceBusiness;

    public CreateService(final ProcessDescriptor desc, final ParameterValueGroup parameter) {
        super(desc, parameter);
    }

    /**
     * Create a new instance and configuration for a specified service and instance name.
     * @throws ProcessException in cases :
     * - if the service name is different from WMS, WMTS, WCS of WFS (no matter of case)
     * - if a configuration file already exist for this instance name.
     * - if error during file creation or marshalling phase.
     */
    @Override
    protected void execute() throws ProcessException {

        final String serviceType       = inputParameters.getValue(SERVICE_TYPE);
        final String identifier        = inputParameters.getValue(IDENTIFIER);
        Object configuration           = inputParameters.getValue(CONFIGURATION);
        final Details serviceMetadata  = inputParameters.getValue(SERVICE_METADATA);

        try {
            Integer sid = serviceBusiness.create(serviceType.toLowerCase(), identifier, configuration, serviceMetadata, null);
            configuration = serviceBusiness.getConfiguration(sid);

        } catch (ConfigurationException ex) {
            throw new ProcessException(ex.getMessage(), this, ex);
        }

        getOrCreate(OUT_CONFIGURATION, outputParameters).setValue(configuration);
    }
}
