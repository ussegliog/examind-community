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

import org.apache.sis.parameter.ParameterBuilder;
import org.apache.sis.util.iso.SimpleInternationalString;
import org.constellation.process.AbstractCstlProcess;
import org.constellation.process.AbstractCstlProcessDescriptor;
import org.constellation.process.ExamindProcessFactory;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.util.InternationalString;

/**
 * Restart an instance for the specified service identifier. Or all service instances if identifier is not specified.
 * @author Quentin Boileau (Geomatys).
 */
public class RestartServiceDescriptor  extends AbstractCstlProcessDescriptor {

    public static final String NAME = "service.restart";
    public static final InternationalString ABSTRACT = new SimpleInternationalString("Restart an instance for the specified service instance. "
            + "Or all service instances if identifier is not specified.");

    private static final ParameterBuilder BUILDER = new ParameterBuilder();

    public static final String SERVICE_TYPE_NAME = "service_type";
    private static final String SERVICE_TYPE_REMARKS = "The type of the service.";
    private static final String[] SERVICE_TYPE_VALID_VALUES = ServiceProcessCommon.servicesAvaible();
    public static final ParameterDescriptor<String> SERVICE_TYPE = BUILDER
            .addName(SERVICE_TYPE_NAME)
            .setRemarks(SERVICE_TYPE_REMARKS)
            .setRequired(true)
            .createEnumerated(String.class, SERVICE_TYPE_VALID_VALUES, null);

    public static final String IDENTIFIER_NAME = "identifier";
    private static final String IDENTIFIER_REMARKS = "Identifier of the service instance to restart. If empty, all service instance will be restarted.";
    public static final ParameterDescriptor<String> IDENTIFIER =BUILDER
            .addName(IDENTIFIER_NAME)
            .setRemarks(IDENTIFIER_REMARKS)
            .setRequired(false)
            .create(String.class, null);

    public static final String CLOSE_NAME = "close";
    private static final String CLOSE_REMARKS = "Close instance(s) before restart.";
    public static final ParameterDescriptor<Boolean> CLOSE = BUILDER
            .addName(CLOSE_NAME)
            .setRemarks(CLOSE_REMARKS)
            .setRequired(true)
            .create(Boolean.class, Boolean.TRUE);

    /**Input parameters */
    public static final ParameterDescriptorGroup INPUT_DESC = BUILDER.addName("InputParameters").setRequired(true)
            .createGroup(SERVICE_TYPE, IDENTIFIER, CLOSE);

    /**Output parameters */
    public static final ParameterDescriptorGroup OUTPUT_DESC = BUILDER.addName("OutputParameters").setRequired(true)
            .createGroup();


    /**
     * Public constructor use by the ServiceRegistry to find and instantiate all ProcessDescriptor.
     */
    public RestartServiceDescriptor() {
        super(NAME, ExamindProcessFactory.IDENTIFICATION, ABSTRACT, INPUT_DESC, OUTPUT_DESC);
    }

    @Override
    public AbstractCstlProcess buildProcess(final ParameterValueGroup input) {
        return new RestartService(this, input);
    }
}
