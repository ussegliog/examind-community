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

package org.constellation.api.rest;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.io.WKTWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBException;
import org.apache.commons.io.IOUtils;
import org.apache.sis.storage.DataStoreException;
import org.constellation.api.ServiceDef;
import org.constellation.ws.IWSEngine;
import org.constellation.business.IDataBusiness;
import org.constellation.business.ISensorBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.dto.Sensor;
import org.constellation.dto.AcknowlegementType;
import org.constellation.dto.ParameterValues;
import org.constellation.dto.service.config.sos.SensorMLTree;
import org.constellation.dto.SimpleValue;
import org.constellation.dto.StringList;
import org.constellation.dto.metadata.RootObj;
import org.constellation.exception.ConfigurationException;
import org.constellation.exception.NotRunningServiceException;
import org.constellation.json.metadata.Template;
import org.constellation.json.metadata.bean.TemplateResolver;
import org.constellation.provider.DataProvider;
import org.constellation.provider.DataProviders;
import org.constellation.sos.ws.SOSUtils;
import org.constellation.sos.ws.SensorMLGenerator;
import org.constellation.ws.ISOSConfigurer;
import org.geotoolkit.nio.IOUtilities;
import org.geotoolkit.observation.ObservationReader;
import org.geotoolkit.observation.ObservationStore;
import org.geotoolkit.sml.xml.AbstractSensorML;
import org.geotoolkit.sml.xml.SensorMLUtilities;
import org.geotoolkit.sos.netcdf.ExtractionResult;
import org.constellation.dto.service.Service;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.springframework.http.HttpStatus.*;
import org.springframework.http.MediaType;
import static org.springframework.http.MediaType.*;
import static org.springframework.web.bind.annotation.RequestMethod.*;
import org.springframework.web.bind.annotation.RequestParam;

/**
 *
 * @author Guilhem Legal (Geomatys)
 * @author Cédric Briançon (Geomatys)
 * @author Johann Sorel (Geomatys)
 */
@RestController
public class SensorRestAPI extends AbstractRestAPI {

    @Inject
    private ISensorBusiness sensorBusiness;

    @Inject
    protected IServiceBusiness serviceBusiness;

    @Inject
    protected IDataBusiness dataBusiness;

    @Inject
    private IWSEngine wsengine;

    @Inject
    private TemplateResolver templateResolver;

    /**
     * Return a list of all the registered sensors as a tree.
     *
     * @return A sensor tree.
     */
    @RequestMapping(value="/sensors",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getSensorList() {
        final SensorMLTree result = sensorBusiness.getFullSensorMLTree();
        return new ResponseEntity(result,OK);
    }

    /**
     * Remove a sensor from the data source.
     *
     * @param id Sensor identifier.
     * @return {@code true} if the operation succeed.
     */
    @RequestMapping(value="/sensors/{id}",method=DELETE,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity deleteSensor(@PathVariable("id") Integer id, @RequestParam(name = "removeData", required = false) Boolean removeData) {
        try {
            final Sensor sensor = sensorBusiness.getSensor(id);
            if (sensor != null) {
                List<Service> services = serviceBusiness.getSensorLinkedServices(id);
                for (Service service : services) {
                    getConfigurer().removeSensor(service.getIdentifier(), sensor.getIdentifier());
                }
                if (removeData != null && removeData) {
                    List<Integer> dataIds = sensorBusiness.getLinkedDataIds(id);
                    for (Integer dataId : dataIds) {
                        dataBusiness.removeData(dataId, false);
                    }
                }
                sensorBusiness.delete(id);
            }
            return new ResponseEntity(OK);
        } catch(Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Retrieve sensor metadata.
     *
     * @param id Sensor identifier.
     * @return The sensor metadata.
     */
    @RequestMapping(value="/sensors/{id}/metadata",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getSensorMetadata(@PathVariable("id") String id) {

        try {
            Object sml = sensorBusiness.getSensorMetadata(id);
            if (sml != null) {
                return new ResponseEntity(sml, OK);
            } else {
                return new ResponseEntity(NOT_FOUND);
            }
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "error while unmarshalling SensorML", ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Returns applied template for metadata sensorML for read mode only like metadata viewer.
     * for reference (consult) purposes only.
     *
     * @param id given sensor identifier.
     * @param type sensor type system or component.
     * @param prune flag that indicates if template result will clean empty children/block.
     * @return {@code Response}
     */
    @RequestMapping(value="/sensors/{id}/metadata/{type}/{prune}",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getSensorMetadataJson(
            final @PathVariable("id") Integer id,
            final @PathVariable("type") String type,
            final @PathVariable("prune") boolean prune) {

        try {
            final Sensor sensor = sensorBusiness.getSensor(id);
            final StringWriter buffer = new StringWriter();
            if (sensor != null) {
                final Object sml = sensorBusiness.getSensorMetadata(id);
                if (sml != null) {
                    final Template template = templateResolver.getByName(sensor.getProfile());
                    template.write(sml,buffer,prune, false);
                }
                return new ResponseEntity(buffer.toString(), OK);
            }  else {
                return new ErrorMessage().message("There is no sensor for id " + id).build();
            }

        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "error while writing metadata sensorML to json.", ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Proceed to save sensorML with given values from metadata editor.
     *
     * @param id the data provider identifier
     * @param type the data type.
     * @param metadataValues the values of metadata editor.
     * @return {@code Response}
     */
    @RequestMapping(value="/sensors/{id}/metadata/{type}",method=POST,consumes=APPLICATION_JSON_VALUE,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity saveMetadataJson(
            @PathVariable("id") final Integer id,
            @PathVariable("type")     final String type,
            @RequestBody              final RootObj metadataValues) {

        try {
            final Sensor sensor = sensorBusiness.getSensor(id);
            if (sensor != null) {
                final Object sml = sensorBusiness.getSensorMetadata(id);
                if (sml != null) {
                    final Template template = templateResolver.getByName(sensor.getProfile());
                    template.read(metadataValues,sml,false);

                    sensorBusiness.updateSensorMetadata(id, sml);
                }
                return new ResponseEntity(OK);
            } else {
                return new ErrorMessage().message("There is no sensor for id " + id).build();
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING,"Error while saving sensorML",ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Return as an attachment file the metadata of sensor in xml format.
     *
     * @param id given sensor identifier.
     * @return the xml file
     */
    @RequestMapping(value="/sensors/{id}/metadata/download",method=GET,produces=APPLICATION_XML_VALUE)
    public ResponseEntity downloadMetadataForSensor(
            @PathVariable("id") final Integer id, HttpServletResponse response) {

        try{
            final Object sml = sensorBusiness.getSensorMetadata(id);
            if (sml != null) {
                final String xml = sensorBusiness.marshallSensor(sml);
                response.addHeader("Content-Disposition","attachment; filename=" + id + ".xml");
                response.setContentType(MediaType.APPLICATION_XML.toString());
                response.flushBuffer();
                IOUtils.write(xml, response.getOutputStream());
                return new ResponseEntity(OK);
            } else {
                return new ErrorMessage().message("SensorML not found").build();
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to get xml metadata for sensor with identifier "+id,ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Generate automatically a SensorML for the specified data id
     * @param dataId The data identifier.
     *
     * @return An Acknowledgement describing how the operation went.
     */
    @RequestMapping(value="/sensors/generate/{dataId}",method=PUT,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity generateSensor(@PathVariable("dataId") final Integer dataId) {
        final DataProvider provider;
        try {
            final Integer providerId = dataBusiness.getDataProvider(dataId);
            provider = DataProviders.getProvider(providerId);
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "Error while accessing provider", ex);
            return new ErrorMessage(ex).message("Error while accessing provider.").build();
        }
        final List<ExtractionResult.ProcedureTree> procedures;
        try {
            final ObservationStore store = SOSUtils.getObservationStore(provider);
            if (store != null) {
                procedures = store.getProcedures();
            } else {
                return new ResponseEntity(new AcknowlegementType("Failure", "Available only on Observation provider (and netCDF coverage) for now"),OK);
            }
        } catch (DataStoreException ex) {
            LOGGER.log(Level.WARNING, "Error while reading netCDF", ex);
            return new ResponseEntity(new AcknowlegementType("Failure", "Error while reading netCDF"),OK);
        }

        // SensorML generation
        try {
            for (ExtractionResult.ProcedureTree process : procedures) {
                generateSensorML(dataId, process, null);
            }
        } catch (SQLException | ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "Error while writng sensorML", ex);
            return new ResponseEntity(new AcknowlegementType("Failure", "SQLException while writing sensorML"),OK);
        }
        return new ResponseEntity("The sensors has been succesfully generated",OK);
    }

    private void generateSensorML(final int dataID, final ExtractionResult.ProcedureTree process, final String parentID) throws SQLException, ConfigurationException {

        final Properties prop = new Properties();
        prop.put("id",         process.id);
        if (process.spatialBound.dateStart != null) {
            prop.put("beginTime",  process.spatialBound.dateStart);
        }
        if (process.spatialBound.dateEnd != null) {
            prop.put("endTime",    process.spatialBound.dateEnd);
        }
        if (process.spatialBound.minx != null) {
            prop.put("longitude",  process.spatialBound.minx);
        }
        if (process.spatialBound.miny != null) {
            prop.put("latitude",   process.spatialBound.miny);
        }
        prop.put("phenomenon", process.fields);

        Sensor sensor = sensorBusiness.getSensor(process.id);
        Integer sid;
        if (sensor == null) {
            Integer providerID = sensorBusiness.getDefaultInternalProviderID();
            sid = sensorBusiness.create(process.id, process.type, parentID, null, System.currentTimeMillis(), providerID);
        } else {
            sid = sensor.getId();
        }

        final List<String> component = new ArrayList<>();
        for (ExtractionResult.ProcedureTree child : process.children) {
            component.add(child.id);
            generateSensorML(dataID, child, process.id);
        }
        prop.put("component", component);
        final String sml = SensorMLGenerator.getTemplateSensorMLString(prop, process.type);

        // update sml
        sensorBusiness.updateSensorMetadata(sid, sml);
        sensorBusiness.linkDataToSensor(dataID, sid);
    }

    /**
     * Import a SensorML metadata file in the data source.
     *
     * @param fileIs
     * @return A {@link ResponseEntity} with 200 code if upload work, 500 if not work.
     */
    @RequestMapping(value="/sensors/upload",method=POST,consumes=MULTIPART_FORM_DATA_VALUE,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity uploadSensor(@RequestPart("data") MultipartFile fileIs) {

        final String fileName = fileIs.getOriginalFilename();
        final Path newFileData;
        try {
            final Path uploadDirectory = getUploadDirectory();
            newFileData = uploadDirectory.resolve(fileName);
            if (!fileIs.isEmpty()) {
                try (InputStream in = fileIs.getInputStream()) {
                    Files.copy(in, newFileData, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }

        //proceed to import sensor
        final List<Sensor> sensorsImported;
        try {
            sensorsImported = proceedToImportSensor(newFileData.toAbsolutePath().toUri().toString());
        }catch (IOException | JAXBException | ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "Error while reading sensorML file", ex);
            return new ErrorMessage(ex).message("fail to read sensorML file").build();
        }
        return new ResponseEntity(sensorsImported, OK);
    }

    /**
     * Import a server located sensor metadata file (can be a directory containing muliple files).
     * Then create the detected sensors in the datasource.
     *
     * @param pv The path to the file(s).
     *
     * @return A list of imported sensors.
     */
    @RequestMapping(value="/sensors/add",method=PUT,consumes=APPLICATION_JSON_VALUE,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity importSensor(@RequestBody final ParameterValues pv) {

        final String path = pv.get("path");
        final List<Sensor> sensorsImported;
        try{
            sensorsImported = proceedToImportSensor(path);
        } catch (IOException | JAXBException | ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "Error while reading sensorML file", ex);
            return new ErrorMessage(ex).message("fail to read sensorML file").build();
        }
        return new ResponseEntity(sensorsImported, OK);
    }

    /**
     * Import sensorML file and Returns a list of sensor described into this sensorML.
     *
     * @param path to the file sensorML
     * @return list of Sensor
     * @throws JAXBException
     */
    private List<Sensor> proceedToImportSensor(final String path) throws JAXBException, IOException, ConfigurationException {
        final List<Sensor> sensorsImported = new ArrayList<>();
        final Path imported  = IOUtilities.toPath(path);
        final Integer providerID           = sensorBusiness.getDefaultInternalProviderID();

        //@FIXME treat zip case
        if (Files.isDirectory(imported)) {
            final Map<String, List<String>> parents = new HashMap<>();

            FileVisitor<Path> importVisitor = new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    try {
                        final Object objsml = sensorBusiness.unmarshallSensor(file);
                        if (objsml instanceof AbstractSensorML) {
                            final AbstractSensorML sml = (AbstractSensorML) objsml;
                            final String type = SensorMLUtilities.getSensorMLType(sml);
                            final String sensorID = SensorMLUtilities.getSmlID(sml);
                            final List<String> children = SensorMLUtilities.getChildrenIdentifiers(sml);

                            final Integer sid = sensorBusiness.create(sensorID, type, null, sml, System.currentTimeMillis(), providerID);
                            sensorsImported.add(sensorBusiness.getSensor(sid));
                            parents.put(sensorID, children);
                        }
                    } catch (ConfigurationException | JAXBException e) {
                        throw new IOException(e.getMessage() , e);
                    }
                    return FileVisitResult.CONTINUE;
                }
            };

            Files.walkFileTree(imported, importVisitor);

            // update dependencies
            for (final Map.Entry<String, List<String>> entry : parents.entrySet()) {
                for (final String child : entry.getValue()) {
                    final Sensor childRecord = sensorBusiness.getSensor(child);
                    childRecord.setParent(entry.getKey());
                    sensorBusiness.update(childRecord);
                }
            }
        } else {
            final Object objsml = sensorBusiness.unmarshallSensor(imported);
            if (objsml instanceof AbstractSensorML) {
                final AbstractSensorML sml = (AbstractSensorML) objsml;
                final String type          = SensorMLUtilities.getSensorMLType(sml);
                final String sensorID      = SensorMLUtilities.getSmlID(sml);
                final Integer sid          = sensorBusiness.create(sensorID, type, null, sml, System.currentTimeMillis(), providerID);
                sensorsImported.add(sensorBusiness.getSensor(sid));
            }
        }
        return sensorsImported;
    }

    /**
     * Return all the observed properties (phenomenon) registered in the datasource.
     *
     * @return A list of phenomenon identifiers
     */
    @RequestMapping(value="/sensors/observedProperty",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getObservedPropertiesIds() {
        try {
            final Set<String> phenomenons = new HashSet<>();
            final List<Sensor> records = sensorBusiness.getAll();
            for (Sensor record : records) {
                final List<Integer> dataProviders = sensorBusiness.getLinkedDataProviderIds(record.getId());

                // look for provider ids (remove doublon)
                final Set<Integer> providerIDs = new HashSet<>();
                for (Integer dataProvider : dataProviders) {
                    providerIDs.add(dataProvider);
                }

                for (Integer providerId : providerIDs) {
                    final ObservationReader reader = getObservationReader(providerId);
                    phenomenons.addAll(reader.getPhenomenonNames());
                }
            }
            return new ResponseEntity(phenomenons,OK);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Return a list of sensor identifiers using the specified observed property (phenomenon).
     *
     * @param observedProperty An observed property identifier.
     *
     * @return A list of sensor identifiers.
     */
    @RequestMapping(value="/sensors/observedProperty/{observedProperty}",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getSensorIdsForObservedProperty(
            @PathVariable("observedProperty") final String observedProperty) {

        try {
            final Set<String> sensorIDS = new HashSet<>();
            final List<Sensor> records = sensorBusiness.getAll();
            for (Sensor record : records) {
                final List<Integer> dataProviders = sensorBusiness.getLinkedDataProviderIds(record.getId());

                // look for provider ids (remove doublon)
                final Set<Integer> providerIDs = new HashSet<>();
                for (Integer dataProvider : dataProviders) {
                    providerIDs.add(dataProvider);
                }

                for (Integer providerId : providerIDs) {
                    final ObservationReader reader = getObservationReader(providerId);
                    if (reader.existPhenomenon(observedProperty)) {
                        sensorIDS.addAll(reader.getProcedureNames());
                    }
                }
            }
            return new ResponseEntity(sensorIDS,OK);
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Return all the observed properties (phenomenon) for the specified sensor.
     *
     * @param id A sensor identifier.
     *
     * @return A list of phenomenon identifiers
     */
    @RequestMapping(value="/sensors/{id}/observedProperty",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getObservedPropertiesForSensor(@PathVariable("id") final Integer id) {
        try {
            final Sensor sensor = sensorBusiness.getSensor(id);
            if (sensor != null) {
                final Set<String> phenomenons = new HashSet<>();
                final List<Integer> dataProviders = sensorBusiness.getLinkedDataProviderIds(sensor.getId());

                // look for provider ids (remove doublon)
                final Set<Integer> providerIDs = new HashSet<>();
                for (Integer dataProvider : dataProviders) {
                    providerIDs.add(dataProvider);
                }

                for (Integer providerId : providerIDs) {
                    final ObservationReader reader = getObservationReader(providerId);
                    phenomenons.addAll(reader.getPhenomenonNames());
                }

                return new ResponseEntity(new StringList(phenomenons),OK);
            } else {
                return new ResponseEntity(NOT_FOUND);
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    /**
     * Return WKT location for the specified sensor.
     *
     * @param id The sensor identifier.
     *
     * @return A WKT location.
     */
    @RequestMapping(value="/sensors/{id}/location",method=GET,produces=APPLICATION_JSON_VALUE)
    public ResponseEntity getWKTSensorLocation(final @PathVariable("id") Integer id) {
        try {
            final Sensor sensor = sensorBusiness.getSensor(id);
            if (sensor != null) {
                final List<Integer> dataProviders = sensorBusiness.getLinkedDataProviderIds(sensor.getId());

                // look for provider ids (remove doublon)
                final Set<Integer> providerIDs = new HashSet<>();
                for (Integer dataProvider : dataProviders) {
                    providerIDs.add(dataProvider);
                }

                final List<Geometry> jtsGeometries = new ArrayList<>();
                final SensorMLTree root            = sensorBusiness.getFullSensorMLTree();
                final SensorMLTree current         = root.find(sensor.getIdentifier());
                for (Integer providerId : providerIDs) {
                    final ObservationReader reader = getObservationReader(providerId);
                    jtsGeometries.addAll(SOSUtils.getJTSGeometryFromSensor(current, reader));
                }

                if (jtsGeometries.size() == 1) {
                    final WKTWriter writer = new WKTWriter();
                    return new ResponseEntity(new SimpleValue(writer.write(jtsGeometries.get(0))),OK);
                } else if (!jtsGeometries.isEmpty()) {
                    final Geometry[] geometries   = jtsGeometries.toArray(new Geometry[jtsGeometries.size()]);
                    final GeometryCollection coll = new GeometryCollection(geometries, new GeometryFactory());
                    final WKTWriter writer        = new WKTWriter();
                    return new ResponseEntity(new SimpleValue(writer.write(coll)),OK);
                }
                return new ResponseEntity(new SimpleValue(""),OK);

            } else {
                return new ResponseEntity(NOT_FOUND);
            }
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            return new ErrorMessage(ex).build();
        }
    }

    private ObservationReader getObservationReader(final Integer providerID) throws ConfigurationException {
        final DataProvider provider = DataProviders.getProvider(providerID);
        final ObservationStore store = SOSUtils.getObservationStore(provider);
        if (store != null) {
            return store.getReader();
        } else {
            throw new ConfigurationException("Available only on Observation provider (and netCDF coverage) for now");
        }
    }

    private ISOSConfigurer getConfigurer() throws NotRunningServiceException {
        return (ISOSConfigurer) wsengine.newInstance(ServiceDef.Specification.SOS);
    }
}
