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
package org.constellation.webservice.map.component;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.sis.internal.system.DefaultFactories;
import org.apache.sis.parameter.Parameters;

import org.apache.sis.util.logging.Logging;
import org.constellation.admin.SpringHelper;
import org.constellation.business.IClusterBusiness;
import org.constellation.business.IConfigurationBusiness;
import org.constellation.business.IDataBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IStyleBusiness;
import org.constellation.configuration.AppProperty;
import org.constellation.configuration.Application;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.exception.ConfigurationException;
import org.constellation.exception.ConfigurationRuntimeException;
import org.constellation.process.ExamindProcessFactory;
import org.constellation.process.provider.CreateProviderDescriptor;
import org.constellation.provider.DataProviders;
import org.constellation.provider.DataProviderFactory;
import org.geotoolkit.nio.IOUtilities;
import org.geotoolkit.nio.ZipUtilities;
import org.geotoolkit.process.ProcessDescriptor;
import org.geotoolkit.process.ProcessException;
import org.geotoolkit.process.ProcessFinder;
import org.geotoolkit.style.DefaultExternalGraphic;
import org.geotoolkit.style.DefaultGraphic;
import org.geotoolkit.style.DefaultOnlineResource;
import org.geotoolkit.style.DefaultPointSymbolizer;
import org.geotoolkit.style.MutableStyle;
import org.geotoolkit.style.MutableStyleFactory;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.style.GraphicalSymbol;
import org.opengis.util.NoSuchIdentifierException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;

import static org.geotoolkit.style.StyleConstants.DEFAULT_LINE_SYMBOLIZER;
import static org.geotoolkit.style.StyleConstants.DEFAULT_POINT_SYMBOLIZER;
import static org.geotoolkit.style.StyleConstants.DEFAULT_POLYGON_SYMBOLIZER;
import static org.geotoolkit.style.StyleConstants.DEFAULT_RASTER_SYMBOLIZER;
import org.opengis.style.StyleFactory;

/**
 * Specific setup for map service
 *
 * @author Guilhem Legal (Geomatys)
 * @author Alexis Manin (Geomatys)
 * @author Cédric Briançon (Geomatys)
 */
@Named
//@DependsOn("database-initer")
public class SetupBusiness {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.webservice.map.component");

    private static final String DEFAULT_RESOURCES = "org/constellation/map/setup.zip";

    @Inject
    private IStyleBusiness styleBusiness;

    @Inject
    private IClusterBusiness clusterBusiness;

    @Inject
    private IDataBusiness dataBusiness;

    @Inject
    private IProviderBusiness providerBusiness;

    @Inject
    private IConfigurationBusiness configurationBusiness;

    @PostConstruct
    public void contextInitialized() {
        LOGGER.log(Level.INFO, "=== Initialize Application ===");

        try {
            // Try to load postgresql driver for further use
            Class.forName("org.postgresql.ds.PGSimpleDataSource");
            LOGGER.log(Level.INFO, "postgresql loading success!");
        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.INFO, ex.getLocalizedMessage(), ex);
        }

        Lock lock = clusterBusiness.acquireLock("setup-default-resources");
        lock.lock();
        LOGGER.fine("LOCK Acquired on cluster: setup-default-resources");
        try {
            SpringHelper.executeInTransaction(new TransactionCallbackWithoutResult() {

                @Override
                protected void doInTransactionWithoutResult(TransactionStatus arg0) {

                    WithDefaultResources defaultResourcesDeployed = deployDefaultResources(ConfigDirectory.getDataDirectory());

                    LOGGER.log(Level.INFO, "initializing default styles ...");
                    defaultResourcesDeployed.initializeDefaultStyles();
                    LOGGER.log(Level.INFO, "initializing vector data ...");
                    defaultResourcesDeployed.initializeDefaultVectorData();
                    LOGGER.log(Level.INFO, "initializing raster data ...");
                    defaultResourcesDeployed.initializeDefaultRasterData();
                    LOGGER.log(Level.INFO, "initializing properties ...");
                    defaultResourcesDeployed.initializeDefaultProperties();

                }
            });
        } finally {
            LOGGER.fine("UNLOCK on cluster: setup-default-resources");
            lock.unlock();
        }

        if (configurationBusiness != null) {
            //check if data analysis is required
            String propertyValue = Application.getProperty(AppProperty.DATA_AUTO_ANALYSE);
            boolean doAnalysis = propertyValue == null ? false : Boolean.valueOf(propertyValue);

            if (doAnalysis && dataBusiness != null) {
                LOGGER.log(Level.FINE, "Start data analysis");
                dataBusiness.computeEmptyDataStatistics(true);
            }
        }
    }

    /**
     * Invoked when the module needs to be shutdown.
     */
    @PreDestroy
    public void contextDestroyed() {
        DataProviders.dispose();
    }


    public static Path pathTransform(final Path dst, final Path zipath) {
        Path ret = dst;
        for (final Path component : zipath)
            ret = ret.resolve(component.getFileName().toString());
        return ret;
    }

    public WithDefaultResources deployDefaultResources(final Path path) {
        try {

            Path zipPath = IOUtilities.getResourceAsPath(DEFAULT_RESOURCES);

            Path tempDir = Files.createTempDirectory("");
            Path tempZip = tempDir.resolve(zipPath.getFileName().toString());
            Files.copy(zipPath, tempZip);

            ZipUtilities.unzipNIO(tempZip, path, false);

            IOUtilities.deleteRecursively(tempDir);

        } catch (IOException | URISyntaxException e) {
            throw new ConfigurationRuntimeException("Error while deploying default ressources", e);
        }

        return new WithDefaultResources();

    }

    private class WithDefaultResources {
        /**
         * Initialize default styles for generic data.
         */
        private void initializeDefaultStyles() {

            Path dataDirectory = ConfigDirectory.getDataDirectory();
            final Path dstImages = dataDirectory.resolve("images");
            final Path markerNormal = dstImages.resolve("marker_normal.png");
            final Path markerSelected = dstImages.resolve("marker_selected.png");
            try {
                if (!Files.exists(markerNormal)) {
                    IOUtilities.copyResource("org/constellation/map/setup/images", null, dataDirectory, false);
                }
            } catch (URISyntaxException | IOException ex) {
                LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            }

            // Fill default SLD provider.
            final MutableStyleFactory SF = (MutableStyleFactory) DefaultFactories.forBuildin(StyleFactory.class);


            try {
                //create sld and sld_temp providers

                if (!styleBusiness.existsStyle("sld","default-point")) {
                    final MutableStyle style = SF.style(DEFAULT_POINT_SYMBOLIZER);
                    style.setName("default-point");
                    style.featureTypeStyles().get(0).rules().get(0).setName("default-point");
                    styleBusiness.createStyle("sld", style);
                }

                createPointMarkerStyle(markerNormal, SF, "default-point-sensor");
                createPointMarkerStyle(markerSelected, SF, "default-point-sensor-selected");

                if (!styleBusiness.existsStyle("sld","default-line")) {
                    final MutableStyle style = SF.style(DEFAULT_LINE_SYMBOLIZER);
                    style.setName("default-line");
                    style.featureTypeStyles().get(0).rules().get(0).setName("default-line");
                    styleBusiness.createStyle("sld", style);
                }
                if (!styleBusiness.existsStyle("sld","default-polygon")) {
                    final MutableStyle style = SF.style(DEFAULT_POLYGON_SYMBOLIZER);
                    style.setName("default-polygon");
                    style.featureTypeStyles().get(0).rules().get(0).setName("default-polygon");
                    styleBusiness.createStyle("sld", style);
                }
                if (!styleBusiness.existsStyle("sld","default-raster")) {
                    final MutableStyle style = SF.style(DEFAULT_RASTER_SYMBOLIZER);
                    style.setName("default-raster");
                    style.featureTypeStyles().get(0).rules().get(0).setName("default-raster");
                    styleBusiness.createStyle("sld", style);
                }
            } catch (ConfigurationException ex) {
                LOGGER.log(Level.WARNING, "An error occurred when creating default styles for default SLD provider.", ex);
            }
        }

        private void createPointMarkerStyle(Path markerNormal, MutableStyleFactory SF, String pointNormalStyleName) throws ConfigurationException {
            if (!styleBusiness.existsStyle("sld", pointNormalStyleName)) {
                final MutableStyle style = SF.style(DEFAULT_POINT_SYMBOLIZER);
                style.setName(pointNormalStyleName);
                style.featureTypeStyles().get(0).rules().get(0).setName(pointNormalStyleName);

                // Marker
                String fileName = markerNormal.getFileName().toString();
                final DefaultOnlineResource onlineResource = new DefaultOnlineResource(markerNormal.toUri(), "", "", fileName,
                        null, null);
                final DefaultExternalGraphic graphSymb = (DefaultExternalGraphic) SF.externalGraphic(onlineResource, "png", null);
                final List<GraphicalSymbol> symbs = new ArrayList<>();
                symbs.add(graphSymb);
                final DefaultGraphic graphic = (DefaultGraphic) SF.graphic(symbs, null, null, null, null, null);
                final DefaultPointSymbolizer pointSymbolizer = (DefaultPointSymbolizer) SF.pointSymbolizer(pointNormalStyleName, "",
                        null, null, graphic);
                style.featureTypeStyles().get(0).rules().get(0).symbolizers().clear();
                style.featureTypeStyles().get(0).rules().get(0).symbolizers().add(pointSymbolizer);
                styleBusiness.createStyle("sld", style);
            }
        }

        /**
         * Initialize default vector data for displaying generic features in
         * data editors.
         */
        private void initializeDefaultVectorData() {
            final Path dst = ConfigDirectory.getDataDirectory().resolve("shapes");

                final String featureStoreStr = "data-store";
                final String shpProvName = "generic_shp";
                Integer provider = providerBusiness.getIDFromIdentifier(shpProvName);
                if (provider == null) {
                    // Acquire SHP provider service instance.
                    DataProviderFactory shpService = null;
                    for (final DataProviderFactory service : DataProviders.getFactories()) {
                        if (service.getName().equals(featureStoreStr)) {
                            shpService = service;
                            break;
                        }
                    }
                    if (shpService == null) {
                        LOGGER.log(Level.WARNING, "SHP provider service not found.");
                        return;
                    }

                final ParameterValueGroup source = Parameters.castOrWrap(shpService.getProviderDescriptor().createValue());
                source.parameter("id").setValue(shpProvName);
                source.parameter("providerType").setValue("vector");
                source.parameter("create_dataset").setValue(false);

                final List<ParameterValueGroup> choices = source.groups("choice");
                final ParameterValueGroup choice;
                if (choices.isEmpty()) {
                    choice = source.addGroup("choice");
                } else {
                    choice = choices.get(0);
                }

                final ParameterValueGroup shpConfig = choice.addGroup("ShapefileParametersFolder");
                shpConfig.parameter("path").setValue(dst.toUri());

                // Create SHP Folder provider.
                try {
                    final ProcessDescriptor desc = ProcessFinder.getProcessDescriptor(ExamindProcessFactory.NAME,
                            CreateProviderDescriptor.NAME);
                    final ParameterValueGroup inputs = desc.getInputDescriptor().createValue();
                    inputs.parameter(CreateProviderDescriptor.PROVIDER_TYPE_NAME).setValue(featureStoreStr);
                    inputs.parameter(CreateProviderDescriptor.SOURCE_NAME).setValue(source);
                    desc.createProcess(inputs).call();

                    // set hidden true for this data
                    final Integer p = providerBusiness.getIDFromIdentifier(shpProvName);
                    if (p != null) {
                        final List<Integer> datas = providerBusiness.getDataIdsFromProviderId(p);
                        for (final Integer dataId : datas) {
                            dataBusiness.updateDataHidden(dataId, true);
                        }
                    }

                } catch (NoSuchIdentifierException ignore) { // should never
                                                             // happen
                } catch (ProcessException ex) {
                    LOGGER.log(Level.WARNING, "An error occurred when creating default SHP provider.", ex);
                    return;
                }
            }
        }

        /**
         * Initialize default raster data for displaying generic features in
         * data editors.
         */
        private void initializeDefaultRasterData() {
            final Path dst = ConfigDirectory.getDataDirectory().resolve("raster");

                final String coverageFileStr = "data-store";
                final String tifProvName = "generic_world_tif";

                if (providerBusiness.getIDFromIdentifier(tifProvName) == null) {
                    // Acquire TIFF provider service instance.
                    DataProviderFactory tifService = null;
                    for (final DataProviderFactory service : DataProviders.getFactories()) {
                        if (service.getName().equals(coverageFileStr)) {
                            tifService = service;
                            break;
                        }
                    }
                    if (tifService == null) {
                        LOGGER.log(Level.WARNING, "TIFF provider service not found.");
                        return;
                    }

                final ParameterValueGroup source = tifService.getProviderDescriptor().createValue();
                source.parameter("id").setValue(tifProvName);
                source.parameter("providerType").setValue("raster");
                source.parameter("create_dataset").setValue(false);

                final List<ParameterValueGroup> choices = source.groups("choice");
                final ParameterValueGroup choice;
                if (choices.isEmpty()) {
                    choice = source.addGroup("choice");
                } else {
                    choice = choices.get(0);
                }

                final ParameterValueGroup tifConfig = choice.addGroup("FileCoverageStoreParameters");
                final Path dstTif = dst.resolve("cloudsgrey.tiff");
                tifConfig.parameter("path").setValue(dstTif.toUri());

                // Create SHP Folder provider.
                try {
                    final ProcessDescriptor desc = ProcessFinder.getProcessDescriptor(ExamindProcessFactory.NAME,
                            CreateProviderDescriptor.NAME);
                    final ParameterValueGroup inputs = desc.getInputDescriptor().createValue();
                    inputs.parameter(CreateProviderDescriptor.PROVIDER_TYPE_NAME).setValue(coverageFileStr);
                    inputs.parameter(CreateProviderDescriptor.SOURCE_NAME).setValue(source);
                    desc.createProcess(inputs).call();

                    // set hidden true for this data
                    final Integer p = providerBusiness.getIDFromIdentifier(tifProvName);
                    if (p != null) {
                        final List<Integer> datas = providerBusiness.getDataIdsFromProviderId(p);
                        for (final Integer dataId : datas) {
                            dataBusiness.updateDataHidden(dataId, true);
                        }
                    }

                } catch (NoSuchIdentifierException ignore) { // should never
                    // happen
                } catch (ProcessException ex) {
                    LOGGER.log(Level.WARNING, "An error occurred when creating default TIFF provider.", ex);
                    return;
                }
            }
        }

        /**
         * Initialize default properties values if not exist.
         */
        private void initializeDefaultProperties() {
            //nothing to do for now
        }
    }

}
