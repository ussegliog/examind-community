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
package org.constellation.admin;

import org.constellation.exception.ConfigurationException;
import org.constellation.exception.TargetNotFoundException;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.dto.service.ServiceStatus;
import org.constellation.dto.service.Instance;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.lang3.StringUtils;
import org.apache.sis.xml.MarshallerPool;
import org.constellation.api.ServiceDef;
import org.constellation.exception.ConstellationException;
import org.constellation.business.IServiceBusiness;
import org.constellation.dto.contact.Details;
import org.constellation.exception.ConstellationPersistenceException;
import org.constellation.dto.CstlUser;
import org.constellation.dto.service.Service;
import org.constellation.dto.service.ServiceComplete;
import org.constellation.repository.DataRepository;
import org.constellation.repository.DatasetRepository;
import org.constellation.repository.LayerRepository;
import org.constellation.repository.ServiceRepository;
import org.constellation.repository.ProviderRepository;
import org.constellation.repository.ThesaurusRepository;
import org.constellation.generic.database.GenericDatabaseMarshallerPool;
import org.constellation.security.SecurityManager;
import org.constellation.util.Util;
import org.geotoolkit.nio.IOUtilities;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import org.constellation.business.ClusterMessage;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.sis.util.logging.Logging;
import org.constellation.business.IClusterBusiness;
import org.constellation.business.IProviderBusiness;
import static org.constellation.business.ClusterMessageConstant.*;
import org.constellation.business.IUserBusiness;
import org.constellation.dto.service.config.generic.Automatic;
import org.constellation.dto.service.config.sos.SOSConfiguration;
import org.constellation.dto.service.config.webdav.WebdavContext;
import org.constellation.dto.service.config.wps.ProcessContext;
import org.constellation.dto.service.config.wps.Processes;
import org.constellation.dto.service.config.wxs.GetFeatureInfoCfg;
import org.constellation.dto.service.config.wxs.LayerContext;
import org.constellation.ws.MimeType;

@Component
@Primary
public class ServiceBusiness implements IServiceBusiness {

    private static final Logger LOGGER = Logging.getLogger("org.constellation.admin");

    @Inject
    private SecurityManager securityManager;

    @Inject
    private IUserBusiness userBusiness;

    @Inject
    private ServiceRepository serviceRepository;

    @Inject
    private DataRepository dataRepository;

    @Inject
    private DatasetRepository datasetRepository;

    @Inject
    private LayerRepository layerRepository;

    @Inject
    private ProviderRepository providerRepository;

    @Inject
    private IProviderBusiness providerBusiness;

    @Inject
    private IClusterBusiness clusterBusiness;

    @Inject
    private ThesaurusRepository thesaurusRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public Integer create(final String serviceType, final String identifier, Object configuration, Details details, Integer owner)
            throws ConfigurationException {

        if (identifier == null || identifier.isEmpty()) {
            throw new ConfigurationException("Service instance identifier can't be null or empty.");
        }

        if (configuration == null) {
            configuration = getDefaultConfiguration(serviceType);
        }
        if (owner == null) {
            Optional<CstlUser> user = userBusiness.findOne(securityManager.getCurrentUserLogin());
            if (user.isPresent()) {
                owner = user.get().getId();
            }
        }

        final String config = getStringFromObject(configuration, GenericDatabaseMarshallerPool.getInstance());
        final Service service = new Service();
        service.setConfig(config);
        service.setDate(new Date());
        service.setType(ServiceDef.Specification.valueOf(serviceType.toUpperCase()).name().toLowerCase());
        if (owner != null) {
            service.setOwner(owner);
        }
        service.setIdentifier(identifier);
        service.setStatus(ServiceStatus.STOPPED.toString());
        // TODO metadata-Iso

        if (details == null) {
            final InputStream in = Util
                    .getResourceAsStream("org/constellation/xml/" + service.getType().toUpperCase() + "Capabilities.xml");
            if (in != null) {
                try {
                    final Unmarshaller u = GenericDatabaseMarshallerPool.getInstance().acquireUnmarshaller();
                    details = (Details) u.unmarshal(in);
                    details.setIdentifier(service.getIdentifier());
                    details.setLang("eng"); // default value
                    GenericDatabaseMarshallerPool.getInstance().recycle(u);
                    in.close();
                } catch (JAXBException | IOException ex) {
                    throw new ConfigurationException(ex);
                }
            } else {
                throw new ConfigurationException("Unable to find the capabilities skeleton from resource.");
            }
        } else if (details.getLang() == null) {
            details.setLang("eng");// default value
        }

        final String versions;
        if (details.getVersions() != null) {
            versions = StringUtils.join(details.getVersions(), "µ");
        } else {
            versions = "";
        }
        service.setVersions(versions);

        int serviceId = serviceRepository.create(service);
        setInstanceDetails(serviceType, identifier, details, details.getLang(), true);

        return serviceId;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void start(final Integer id) throws ConfigurationException {
        if (id == null) {
            throw new ConfigurationException("Service instance identifier can't be null.");
        }
        final Service service = serviceRepository.findById(id);
        if (service != null) {
            service.setStatus(ServiceStatus.STARTED.toString());
            serviceRepository.update(service);

            final ClusterMessage request = clusterBusiness.createRequest(SRV_MESSAGE_TYPE_ID,false);
            request.put(KEY_ACTION, SRV_VALUE_ACTION_START);
            request.put(SRV_KEY_TYPE, service.getType());
            request.put(KEY_IDENTIFIER, service.getIdentifier());
            clusterBusiness.publish(request);
        } else {
            throw new TargetNotFoundException("Service instance with identifier \"" + id
                    + "\" not found. There is not configuration in the database.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void stop(final Integer id) throws ConfigurationException {
        if (id == null) {
            throw new ConfigurationException("Service instance identifier can't be null.");
        }

        final Service service = serviceRepository.findById(id);
        if (service != null) {
            service.setStatus(ServiceStatus.STOPPED.toString());
            serviceRepository.update(service);

            final ClusterMessage request = clusterBusiness.createRequest(SRV_MESSAGE_TYPE_ID,false);
            request.put(KEY_ACTION, SRV_VALUE_ACTION_STOP);
            request.put(SRV_KEY_TYPE, service.getType());
            request.put(KEY_IDENTIFIER, service.getIdentifier());
            clusterBusiness.publish(request);
        } else {
            throw new TargetNotFoundException("Service instance with identifier \"" + id
                    + "\" not found. There is not configuration in the database.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void restart(final Integer id, final boolean closeFirst) throws ConfigurationException {
        if (id == null) {
            throw new ConfigurationException("Service instance identifier can't be null.");
        }
        stop(id);
        start(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void rename(final Integer id, final String newIdentifier) throws ConfigurationException {

        final Service service = serviceRepository.findById(id);
        if(service==null) throw new ConfigurationException("no existing instance:" + id);

        final Service newService = serviceRepository.findByIdentifierAndType(newIdentifier, service.getType());
        if(newService!=null) throw new ConfigurationException("already existing instance:" + newIdentifier);

        //stop service
        stop(id);

        //rename it
        service.setIdentifier(newIdentifier);
        serviceRepository.update(service);

        //start service
        start(id);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void delete(final Integer id) throws ConfigurationException {
        if (id == null) {
            throw new ConfigurationException("Service instance identifier can't be null.");
        }

        final Service service = serviceRepository.findById(id);
        if (service == null) throw new ConfigurationException("There is no instance:" + id + " to delete");

        // stop services
        stop(id);

        if (service.getType().equalsIgnoreCase("csw")) {
            dataRepository.removeAllDataFromCSW(id);
            datasetRepository.removeAllDatasetFromCSW(id);
            serviceRepository.removelinkedMetadataProvider(id);
        } else if (service.getType().equalsIgnoreCase("sos")) {
            List<Integer> linkedProviders = serviceRepository.getLinkedSensorProviders(id);
            serviceRepository.removelinkedSensorProviders(id);
            serviceRepository.removelinkedSensors(id);
            for (Integer linkedProviderID : linkedProviders) {
                providerBusiness.removeProvider(linkedProviderID);
            }
        }

        // delete from database
        serviceRepository.delete(id);
        // delete folder
        final Path instanceDir = ConfigDirectory.getInstanceDirectory(service.getType(), service.getIdentifier());
        if (Files.isDirectory(instanceDir)) {
            //FIXME use deleteRecursively instead and handle exception
            IOUtilities.deleteSilently(instanceDir);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteAll() throws ConfigurationException {
        final List<Service> services = serviceRepository.findAll();
        for (Service service : services) {
            delete(service.getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Transactional
    @Override
    public void configure(final String serviceType, final String identifier, Details details, Object configuration)
            throws ConstellationException {
        if (identifier == null || identifier.isEmpty()) {
            throw new ConfigurationException("Service instance identifier can't be null or empty.");
        }

        if (configuration == null) {
            configuration = getDefaultConfiguration(serviceType);
        }

        // write configuration file.
        final Service service = serviceRepository.findByIdentifierAndType(identifier, serviceType);
        if (service == null) throw new ConfigurationException("Service " + serviceType + ':' + identifier + " not found.");

        service.setConfig(getStringFromObject(configuration, GenericDatabaseMarshallerPool.getInstance()));
        if (details != null) {
            setInstanceDetails(serviceType, identifier, details, details.getLang(), true);
        } else {
            details = getInstanceDetails(service.getId(), null);
        }
        if (details != null) {
            service.setVersions(StringUtils.join(details.getVersions(), "µ"));
        }
        serviceRepository.update(service);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getConfiguration(final String serviceType, final String identifier) throws ConfigurationException {
        if (identifier == null || identifier.isEmpty()) {
            throw new ConfigurationException("Service instance identifier can't be null or empty.");
        }
        try {
            final Service service = serviceRepository.findByIdentifierAndType(identifier, serviceType);
            if (service != null) {
                final String confXml = service.getConfig();
                return getObjectFromString(confXml, GenericDatabaseMarshallerPool.getInstance());
            }
        } catch (JAXBException ex) {
            throw new ConfigurationException(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getConfiguration(final int id) throws ConfigurationException {
        try {
            final Service service = serviceRepository.findById(id);
            if (service != null) {
                final String confXml = service.getConfig();
                return getObjectFromString(confXml, GenericDatabaseMarshallerPool.getInstance());
            }
        } catch (JAXBException ex) {
            throw new ConfigurationException(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setConfiguration(final String serviceType, final String identifier, final Object config) throws ConfigurationException {
        if (identifier == null || identifier.isEmpty()) {
            throw new ConfigurationException("Service instance identifier can't be null or empty.");
        }
        try {
            final Service service = serviceRepository.findByIdentifierAndType(identifier, serviceType);
            if (service != null) {
                final String confXml = getStringFromObject(config, GenericDatabaseMarshallerPool.getInstance());
                service.setConfig(confXml);
                serviceRepository.update(service);
            }
        } catch (ConstellationPersistenceException ex) {
            throw new ConfigurationException(ex.getMessage(), ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getExtraConfiguration(final String serviceType, final String identifier, final String fileName)
            throws ConfigurationException {
        return getExtraConfiguration(serviceType, identifier, fileName, GenericDatabaseMarshallerPool.getInstance());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getExtraConfiguration(final String serviceType, final String identifier, final String fileName, final MarshallerPool pool)
            throws ConfigurationException {
        try {
            final Integer service = serviceRepository.findIdByIdentifierAndType(identifier, serviceType);
            if (service != null) {
                final String content = serviceRepository.getExtraConfig(service, fileName);
                if (content != null) {
                    return getObjectFromString(content, pool);
                }
            }
        } catch (JAXBException ex) {
            throw new ConfigurationException(ex.getMessage(), ex);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setExtraConfiguration(final String serviceType, final String identifier, final String fileName, final Object config,
            final MarshallerPool pool) {
        final Integer serviceId = serviceRepository.findIdByIdentifierAndType(identifier, serviceType);
        if (serviceId != null) {
            final String content = getStringFromObject(config, pool);
            serviceRepository.updateExtraFile(serviceId, fileName, content);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Integer, ServiceStatus> getStatus(final String spec) {
        final List<Service> services = serviceRepository.findByType(spec);
        final Map<Integer, ServiceStatus> status = new HashMap<>();
        for (Service service : services) {
            status.put(service.getId(), ServiceStatus.valueOf(service.getStatus()));
        }
        return status;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getServiceIdentifiers(final String spec) {
        return serviceRepository.findIdentifiersByType(spec);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceComplete getServiceById(int id) {
        final Service service = serviceRepository.findById(id);
        if (service != null) {
            Details details = null;
            try {
                details = getInstanceDetails(id, null);
            } catch (ConfigurationException ex) {
                LOGGER.log(Level.INFO, "Error while reading service details", ex);
            }
            return new ServiceComplete(service, details);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ServiceComplete getServiceByIdentifierAndType(String serviceType, String identifier) {
        final Service service = serviceRepository.findByIdentifierAndType(identifier, serviceType);
        if (service != null) {
            Details details = null;
            try {
                details = getInstanceDetails(service.getId(), null);
            } catch (ConfigurationException ex) {
                LOGGER.log(Level.INFO, "Error while reading service details", ex);
            }
            return new ServiceComplete(service, details);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getServiceIdByIdentifierAndType(String serviceType, String identifier) {
        return serviceRepository.findIdByIdentifierAndType(identifier, serviceType);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Details getInstanceDetails(final String serviceType, final String identifier, final String language)
            throws ConfigurationException {
        final Integer id = serviceRepository.findIdByIdentifierAndType(identifier, serviceType);
        if (id == null) {
            throw new TargetNotFoundException(serviceType + " service instance with identifier \"" + identifier
                    + "\" not found. There is not configuration in the database.");
        }
        return getInstanceDetails(id, language);
    }

    private Details getInstanceDetails(final int serviceId, String language) throws ConfigurationException {
        try {
            String details;
            if (language == null) {
                details = serviceRepository.getServiceDetailsForDefaultLang(serviceId);
            } else {
                // if the service description is not available in the requested language, we return the default one.
                if (serviceRepository.getServiceDefinedLanguage(serviceId).contains(language)) {
                    details = serviceRepository.getServiceDetails(serviceId, language);
                } else {
                    details = serviceRepository.getServiceDetailsForDefaultLang(serviceId);
                }
            }
            if (details != null) {
                return (Details) getObjectFromString(details, GenericDatabaseMarshallerPool.getInstance());
            }
            return null;
        } catch (JAXBException ex) {
            throw new ConfigurationException(ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void setInstanceDetails(final String serviceType, final String identifier, final Details details, final String language,
            final boolean default_) throws ConfigurationException {
        final Integer id = serviceRepository.findIdByIdentifierAndType(identifier, serviceType);
        if (id == null) {
            throw new TargetNotFoundException(serviceType + " service instance with identifier \"" + identifier
                    + "\" not found. There is not configuration in the database.");
        }
        final String xml = getStringFromObject(details, GenericDatabaseMarshallerPool.getInstance());
        serviceRepository.createOrUpdateServiceDetails(id, language, xml, default_);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ensureExistingInstance(final String spec, final String identifier) throws ConfigurationException {
        final Integer service = serviceRepository.findIdByIdentifierAndType(identifier, spec);
        if (service == null) {
            throw new TargetNotFoundException(spec + " service instance with identifier \"" + identifier
                    + "\" not found. There is not configuration in the database.");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void ensureExistingInstance(final Integer id) throws ConfigurationException {
        if (!serviceRepository.exist(id)) {
            throw new TargetNotFoundException(id + " service instance not found.");
        }
    }

    private static String getStringFromObject(final Object obj, final MarshallerPool pool) {
        String config = null;
        if (obj != null) {
            try {
                final StringWriter sw = new StringWriter();
                final Marshaller m = pool.acquireMarshaller();
                m.marshal(obj, sw);
                pool.recycle(m);
                config = sw.toString();
            } catch (JAXBException e) {
                throw new ConstellationPersistenceException(e);
            }
        }
        return config;
    }

    private static Object getObjectFromString(final String xml, final MarshallerPool pool) throws JAXBException {
        if (xml != null) {
            final Unmarshaller u = pool.acquireUnmarshaller();
            final Object config = u.unmarshal(new StringReader(xml));
            pool.recycle(u);
            return config;
        }
        return null;
    }



    @Override
    public List<ServiceComplete> getAllServices(String lang) throws ConstellationException {
        List<ServiceComplete> serviceDTOs = new ArrayList<>();
        List<Service> services = serviceRepository.findAll();
        for (Service service : services) {
            final Details details = getInstanceDetails(service.getId(), lang);
            final ServiceComplete serviceDTO = new ServiceComplete(service, details);
            serviceDTOs.add(serviceDTO);
        }
        return serviceDTOs;
    }

    @Override
    public List<ServiceComplete> getAllServicesByType(String lang, String type) throws ConstellationException {
        List<ServiceComplete> serviceDTOs = new ArrayList<>();
        List<Service> services = serviceRepository.findByType(type);
        for (Service service : services) {
            final Details details = getInstanceDetails(service.getId(), lang);
            final ServiceComplete serviceDTO = new ServiceComplete(service, details);
            serviceDTOs.add(serviceDTO);
        }
        return serviceDTOs;
    }

    @Override
    public Instance getI18nInstance(String serviceType, String identifier, String lang) throws ConstellationException{
        final Service service = serviceRepository.findByIdentifierAndType(identifier, serviceType);
        if (service != null) {
            final Details details = getInstanceDetails(service.getId(), lang);
            final ServiceComplete serviceDTO = new ServiceComplete(service, details);
            Instance instance = new Instance(serviceDTO);
            int layersNumber = layerRepository.findByServiceId(service.getId()).size();
            instance.setLayersNumber(layersNumber);
            return instance;
        }
        return null;
    }

    @Override
    public List<Integer> getSOSLinkedProviders(String integer) {
        final Integer serviceID = serviceRepository.findIdByIdentifierAndType(integer, "sos");
        if (serviceID != null) {
            return serviceRepository.getLinkedSensorProviders(serviceID);
        }
        return new ArrayList<>();
    }

    @Override
    @Transactional
    public void linkSOSAndProvider(String serviceID, String providerID) {
        final Integer service   = serviceRepository.findIdByIdentifierAndType(serviceID, "sos");
        final Integer provider = providerRepository.findIdForIdentifier(providerID);
        if (service != null && provider != null) {
            serviceRepository.linkSensorProvider(service, provider, true);
        } else if (service == null) {
            LOGGER.log(Level.WARNING, "Unexisting service:{0}", serviceID);
        } else if (provider == null) {
            LOGGER.log(Level.WARNING, "Unexisting provider:{0}", providerID);
        }
    }

    @Override
    public Integer getCSWLinkedProviders(String identifier) {
        final Integer serviceID = serviceRepository.findIdByIdentifierAndType(identifier, "csw");
        if (serviceID != null) {
            return serviceRepository.getLinkedMetadataProvider(serviceID);
        }
        return null;
    }

    @Override
    public List<Service> getSensorLinkedServices(Integer sensorID) throws ConfigurationException {
        return serviceRepository.getSensorLinkedServices(sensorID);
    }

    @Override
    @Transactional
    public void linkCSWAndProvider(String serviceID, String providerID) {
        final Integer service   = serviceRepository.findIdByIdentifierAndType(serviceID, "csw");
        final Integer provider = providerRepository.findIdForIdentifier(providerID);
        if (service != null && provider != null) {
            if (!serviceRepository.isLinkedMetadataProviderAndService(service, provider)) {
                serviceRepository.linkMetadataProvider(service, provider, true);
            }
        } else if (service == null) {
            LOGGER.log(Level.WARNING, "Unexisting service:{0}", serviceID);
        } else if (provider == null) {
            LOGGER.log(Level.WARNING, "Unexisting provider:{0}", providerID);
        }
    }

    @Override
    public List<String> getLinkedThesaurusUri(Integer serviceId) throws ConfigurationException {
        return thesaurusRepository.getLinkedThesaurusUri(serviceId);
    }

    @Override
    public Object getDefaultConfiguration(final String serviceType) {
        switch(serviceType.toLowerCase()) {
            case "csw": return new Automatic();
            case "wps": return new ProcessContext(new Processes(true));
            case "sos": return new SOSConfiguration();
            case "webdav": return new WebdavContext();
            // other case assume WXS
            default: final LayerContext configuration = new LayerContext();
                    configuration.setGetFeatureInfoCfgs(createGenericConfiguration());
                    return configuration;
        }
    }

    /**
     * Create the default {@link GetFeatureInfoCfg} list to configure a LayerContext.
     * This list is build from generic {@link FeatureInfoFormat} and there supported mimetype.
     * HTMLFeatureInfoFormat, CSVFeatureInfoFormat, GMLFeatureInfoFormat
     *
     * @return a list of {@link GetFeatureInfoCfg}
     */
    private static List<GetFeatureInfoCfg> createGenericConfiguration () {
        //Default featureInfo configuration
        final List<GetFeatureInfoCfg> featureInfos = new ArrayList<>();

        //HTML
        featureInfos.add(new GetFeatureInfoCfg(MimeType.TEXT_HTML, "org.constellation.map.featureinfo.HTMLFeatureInfoFormat"));

        //CSV
        featureInfos.add(new GetFeatureInfoCfg(MimeType.TEXT_PLAIN, "org.constellation.map.featureinfo.CSVFeatureInfoFormat"));

        //GML
        featureInfos.add(new GetFeatureInfoCfg(MimeType.APP_GML, "org.constellation.map.featureinfo.GMLFeatureInfoFormat"));//will return map server GML
        featureInfos.add(new GetFeatureInfoCfg(MimeType.APP_GML_XML, "org.constellation.map.featureinfo.GMLFeatureInfoFormat"));//will return GML 3

        //XML
        featureInfos.add(new GetFeatureInfoCfg(MimeType.APP_XML, "org.constellation.map.featureinfo.XMLFeatureInfoFormat"));
        featureInfos.add(new GetFeatureInfoCfg(MimeType.TEXT_XML, "org.constellation.map.featureinfo.XMLFeatureInfoFormat"));

        //JSON
        featureInfos.add(new GetFeatureInfoCfg(MimeType.APP_JSON, "org.constellation.map.featureinfo.JSONFeatureInfoFormat"));
        featureInfos.add(new GetFeatureInfoCfg(MimeType.APP_JSON_UTF8, "org.constellation.map.featureinfo.JSONFeatureInfoFormat"));

        //Examind specific for coverages
        featureInfos.add(new GetFeatureInfoCfg("application/json; subtype=profile", "org.constellation.map.featureinfo.CoverageProfileInfoFormat"));

        return featureInfos;
    }
}
