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
package org.constellation.process.dynamic.pbs;

import java.util.ArrayList;
import java.util.List;
import org.apache.sis.parameter.ParameterBuilder;
import org.apache.sis.util.iso.SimpleInternationalString;
import org.constellation.process.AbstractCstlProcess;
import org.constellation.process.dynamic.AbstractDynamicDescriptor;
import org.opengis.parameter.GeneralParameterDescriptor;
import org.opengis.parameter.ParameterDescriptor;
import org.opengis.parameter.ParameterDescriptorGroup;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.util.InternationalString;

/**
 * ProcessDescriptor for running a PBS process.
 *
 * @author Gaelle Usseglio (Thales).
 *
 */
public class RunPBSDescriptor extends AbstractDynamicDescriptor {

    public static final String NAME = "pbs.run";
    public static final InternationalString ABSTRACT = new SimpleInternationalString("Run a pbs file process in examind");

    private static final ParameterBuilder BUILDER = new ParameterBuilder();

    public static final String PBS_FILE_NAME = "pbs.file";
    private static final String PBS_FILE_REMARKS = "The pbs file name";
    public static final ParameterDescriptor<String> PBS_FILE = BUILDER
            .addName(PBS_FILE_NAME)
            .setRemarks(PBS_FILE_REMARKS)
            .setRequired(false)
            .create(String.class, null);

    public static final String PBS_ERROR_DIR_NAME = "pbs.error.dir";
    private static final String PBS_ERROR_DIR_REMARKS = "Directory for PBS's error file.";
    public static final ParameterDescriptor<String> PBS_ERROR_DIR = BUILDER
            .addName(PBS_ERROR_DIR_NAME)
            .setRemarks(PBS_ERROR_DIR_REMARKS)
            .setRequired(false)
	.create(String.class, "./"); // current repository as default value 

    public static final String PBS_OUTPUT_DIR_NAME = "pbs.output.dir";
    private static final String PBS_OUTPUT_DIR_REMARKS = "Directory for PBS's output file.";
    public static final ParameterDescriptor<String> PBS_OUTPUT_DIR = BUILDER
	.addName(PBS_OUTPUT_DIR_NAME)
	.setRemarks(PBS_OUTPUT_DIR_REMARKS)
	.setRequired(false)
	.create(String.class, "./"); // current repository as default value 
    
    public static final String PBS_COMMAND_NAME = "pbs.command";
    private static final String PBS_COMMAND_REMARKS = "PBS command : qsub or qstat.";
    public static final ParameterDescriptor<String> PBS_COMMAND = BUILDER
	.addName(PBS_COMMAND_NAME)
	.setRemarks(PBS_COMMAND_REMARKS)
	.setRequired(false)
	.create(String.class, "qsub"); // qsub as default value 
    
    public static final String PBS_SELF_MONITORING_NAME = "pbs.self.monitoring";
    private static final String PBS_SELF_MONITORING_REMARKS = "PBS self monitoring : true or false.";
    public static final ParameterDescriptor<String> PBS_SELF_MONITORING = BUILDER
	.addName(PBS_SELF_MONITORING_NAME)
	.setRemarks(PBS_SELF_MONITORING_REMARKS)
	.setRequired(false)
	.create(String.class, "false"); // true as default value 


    /**
     * Public constructor use by the ServiceRegistry to find and instantiate all ProcessDescriptor.
     */
    public RunPBSDescriptor(String name) {
        super(name, ABSTRACT);
    }

    @Override
    public final AbstractCstlProcess createProcess(final ParameterValueGroup input) {
        return new RunPBS(this, input);
    }

    @Override
    public final ParameterDescriptorGroup getInputDescriptor() {
        List<GeneralParameterDescriptor> inputs = new ArrayList<>(dynamicInput);
        inputs.add(PBS_FILE);
        inputs.add(PBS_ERROR_DIR);
	inputs.add(PBS_OUTPUT_DIR);
	inputs.add(PBS_COMMAND);
	inputs.add(PBS_SELF_MONITORING);
        return BUILDER.addName("InputParameters").setRequired(true).createGroup(inputs.toArray(new GeneralParameterDescriptor[inputs.size()]));
    }

    @Override
    public final ParameterDescriptorGroup getOutputDescriptor() {
        return BUILDER.addName("OutputParameters").setRequired(true).createGroup(dynamicOutput.toArray(new GeneralParameterDescriptor[dynamicOutput.size()]));
    }
}
