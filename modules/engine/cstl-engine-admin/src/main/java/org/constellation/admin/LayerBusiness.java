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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import javax.imageio.spi.ServiceRegistry;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import org.constellation.business.ClusterMessage;
import org.constellation.business.IClusterBusiness;
import org.constellation.business.IDataBusiness;
import org.constellation.business.ILayerBusiness;
import org.constellation.business.IServiceBusiness;
import org.constellation.business.IUserBusiness;
import org.constellation.dto.CstlUser;
import org.constellation.dto.Data;
import org.constellation.dto.DataBrief;
import org.constellation.dto.Layer;
import org.constellation.dto.NameInProvider;
import org.constellation.dto.ProviderBrief;
import org.constellation.dto.Style;
import org.constellation.dto.service.ServiceComplete;
import org.constellation.dto.service.config.DataSourceType;
import org.constellation.dto.service.config.wxs.AddLayer;
import org.constellation.dto.service.config.wxs.FilterAndDimension;
import org.constellation.dto.service.config.wxs.LayerContext;
import org.constellation.dto.service.config.wxs.LayerSummary;
import org.constellation.exception.ConfigurationException;
import org.constellation.exception.ConstellationException;
import org.constellation.exception.ConstellationPersistenceException;
import org.constellation.exception.TargetNotFoundException;
import org.constellation.generic.database.GenericDatabaseMarshallerPool;
import org.constellation.provider.DataProvider;
import org.constellation.provider.DataProviders;
import org.constellation.provider.ProviderParameters;
import org.constellation.repository.DataRepository;
import org.constellation.repository.LayerRepository;
import org.constellation.repository.ProviderRepository;
import org.constellation.repository.StyleRepository;
import org.constellation.util.DataReference;
import org.constellation.ws.LayerSecurityFilter;
import org.constellation.ws.MapFactory;
import org.geotoolkit.util.NamesExt;
import org.opengis.util.GenericName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import static org.constellation.business.ClusterMessageConstant.*;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@Component("cstlLayerBusiness")
@Primary
public class LayerBusiness implements ILayerBusiness {

    @Autowired
    protected IUserBusiness userBusiness;
    @Autowired
    protected StyleRepository styleRepository;
    @Autowired
    protected LayerRepository layerRepository;
    @Autowired
    protected DataRepository dataRepository;
    @Autowired
    private ProviderRepository providerRepository;
    @Autowired
    protected IServiceBusiness serviceBusiness;
    @Autowired
    protected org.constellation.security.SecurityManager securityManager;
    @Autowired
    protected IDataBusiness dataBusiness;
    @Autowired
    protected IClusterBusiness clusterBusiness;

    @Override
    @Transactional
    public Integer add(final AddLayer addLayerData) throws ConfigurationException {
        final String name        = addLayerData.getLayerId();
        // Prevents adding empty layer namespace, put null instead
        final String namespace   = (addLayerData.getLayerNamespace() != null && addLayerData.getLayerNamespace().isEmpty()) ? null : addLayerData.getLayerNamespace();
        final String providerId  = addLayerData.getProviderId();
        final String alias       = addLayerData.getLayerAlias();
        final String serviceId   = addLayerData.getServiceId();
        final String serviceType = addLayerData.getServiceType();
        return add(name, namespace, providerId, alias, serviceId, serviceType, null);
    }

    @Override
    @Transactional
    public Integer add(final String name, String namespace, final String providerId, final String alias,
            final String serviceId, final String serviceType, final org.constellation.dto.service.config.wxs.Layer config) throws ConfigurationException {

        final Integer service = serviceBusiness.getServiceIdByIdentifierAndType(serviceType.toLowerCase(), serviceId);

        if (service !=null) {

            if (namespace != null && namespace.isEmpty()) {
                // Prevents adding empty layer namespace, put null instead
                namespace = null;
            }

            // look for layer namespace
            if (namespace == null) {
                final Integer pvId = providerRepository.findIdForIdentifier(providerId);
                final DataProvider provider = DataProviders.getProvider(pvId);
                if (provider != null) {
                    namespace = ProviderParameters.getNamespace(provider);
                }
            }

            final Integer data = dataRepository.findIdFromProvider(namespace, name, providerId);
            if(data == null) {
                throw new TargetNotFoundException("Unable to find data for namespace:" + namespace+" name:"+name+" provider:"+providerId);
            }
            return add(data, alias, service, config);

        } else {
            throw new TargetNotFoundException("Unable to find a service:" + serviceId);
        }
    }

    @Override
    @Transactional
    public Integer add(int dataId, String alias,
             int serviceId, org.constellation.dto.service.config.wxs.Layer config) throws ConfigurationException {

        final org.constellation.dto.service.ServiceComplete service = serviceBusiness.getServiceById(serviceId);

        if (service !=null) {

            final Data data = dataRepository.findById(dataId);
            if(data == null) {
                throw new TargetNotFoundException("Unable to find data for id:" + dataId);
            }

            String namespace = data.getNamespace();
            if (namespace.isEmpty()) {
                // Prevents adding empty layer namespace, put null instead
                namespace = null;
            }

            boolean update = true;
            Layer layer = layerRepository.findByServiceIdAndDataId(service.getId(), data.getId());
            if (layer == null) {
                update = false;
                layer = new Layer();
            }
            layer.setName(data.getName());
            layer.setNamespace(namespace);
            layer.setAlias(alias);
            layer.setService(service.getId());
            layer.setDataId(data.getId());
            layer.setDate(new Date(System.currentTimeMillis()));
            Optional<CstlUser> user = userBusiness.findOne(securityManager.getCurrentUserLogin());
            if(user.isPresent()) {
                layer.setOwnerId(user.get().getId());
            }
            final String configXml = writeLayerConfiguration(config);
            layer.setConfig(configXml);

            int layerID;
            if (!update) {
                layerID = layerRepository.create(layer);
            } else {
                layerRepository.update(layer);
                layerID = layer.getId();
            }

            for (int styleID : styleRepository.getStyleIdsForData(data.getId())) {
                styleRepository.linkStyleToLayer(styleID, layerID);
            }

            //clear cache event
            final ClusterMessage request = clusterBusiness.createRequest(SRV_MESSAGE_TYPE_ID,false);
            request.put(KEY_ACTION, SRV_VALUE_ACTION_CLEAR_CACHE);
            request.put(SRV_KEY_TYPE, service.getType());
            request.put(KEY_IDENTIFIER, service.getIdentifier());
            clusterBusiness.publish(request);

            return layerID;
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + serviceId);
        }
    }

    @Override
    @Transactional
    public void updateLayerTitle(int layerID, String newTitle) throws ConfigurationException {
        layerRepository.updateLayerTitle(layerID, newTitle);
    }

    @Override
    @Transactional
    public void remove(final String spec, final String serviceId, final String name, final String namespace) throws ConfigurationException {
        final Integer service = serviceBusiness.getServiceIdByIdentifierAndType(spec.toLowerCase(), serviceId);
        if (service != null) {
             Layer layer = (namespace != null && !namespace.isEmpty())?
                    layerRepository.findByServiceIdAndLayerName(service, name, namespace) :
                    layerRepository.findByServiceIdAndLayerName(service, name);
            if (layer != null) {
                removeLayer(layer.getId(), spec.toLowerCase(), serviceId);
            } else {
                throw new TargetNotFoundException("Unable to find a layer: {" + namespace + "}" + name);
            }
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + serviceId);
        }
    }

    @Override
    @Transactional
    public void removeForService(final String serviceType, final String serviceId) throws ConfigurationException {
        final Integer service = serviceBusiness.getServiceIdByIdentifierAndType(serviceType.toLowerCase(), serviceId);
        if (service != null) {
            final List<Layer> layers = layerRepository.findByServiceId(service);
            for (Layer layer : layers) {
                removeLayer(layer.getId(), serviceType.toLowerCase(), serviceId);
            }
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + serviceId);
        }
    }

    @Override
    @Transactional
    public void removeAll() throws ConfigurationException {
        final List<Layer> layers = layerRepository.findAll();
        for (Layer layer : layers) {
            // NOT OPTIMIZED => multiple event sent
            final ServiceComplete service = serviceBusiness.getServiceById(layer.getService());
            if (service != null) {
                removeLayer(layer.getId(), service.getType(), service.getIdentifier());
            }

        }
    }

    protected void removeLayer(int layerId, String serviceType, String serviceId) throws ConfigurationException {
        layerRepository.delete(layerId);

        //clear cache event
        final ClusterMessage request = clusterBusiness.createRequest(SRV_MESSAGE_TYPE_ID,false);
        request.put(KEY_ACTION, SRV_VALUE_ACTION_CLEAR_CACHE);
        request.put(SRV_KEY_TYPE, serviceType);
        request.put(KEY_IDENTIFIER, serviceId);
        clusterBusiness.publish(request);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LayerSummary> getLayerRefFromStyleId(final Integer styleId) {
        final List<LayerSummary> sumLayers = new ArrayList<>();
        final List<Layer> layers = layerRepository.getLayersRefsByLinkedStyle(styleId);
        for(final Layer lay : layers) {
            final LayerSummary layerSummary = new LayerSummary();
            layerSummary.setId(lay.getId());
            layerSummary.setName(lay.getName());
            layerSummary.setDataId(lay.getDataId());
            sumLayers.add(layerSummary);
        }
        return sumLayers;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<LayerSummary> getLayerSummaryFromStyleId(final Integer styleId) throws ConstellationException{
        final List<LayerSummary> sumLayers = new ArrayList<>();
        final List<Layer> layers = layerRepository.getLayersByLinkedStyle(styleId);
        for(final Layer lay : layers){
            final QName fullName = new QName(lay.getNamespace(), lay.getName());
            final Data data = dataRepository.findById(lay.getDataId());
            final DataBrief db = dataBusiness.getDataBrief(fullName, data.getProviderId());
            final LayerSummary layerSummary = new LayerSummary();
            layerSummary.setId(lay.getId());
            layerSummary.setName(data.getName());
            layerSummary.setNamespace(data.getNamespace());
            layerSummary.setAlias(lay.getAlias());
            layerSummary.setTitle(lay.getTitle());
            layerSummary.setType(db.getType());
            layerSummary.setSubtype(db.getSubtype());
            layerSummary.setDate(lay.getDate());
            layerSummary.setOwner(db.getOwner());
            layerSummary.setProvider(db.getProvider());
            layerSummary.setDataId(lay.getDataId());
            sumLayers.add(layerSummary);
        }
        return sumLayers;
    }

    @Override
    public List<org.constellation.dto.service.config.wxs.Layer> getLayers(final Integer serviceId, final String login) throws ConfigurationException {
        final List<org.constellation.dto.service.config.wxs.Layer> response = new ArrayList<>();
        if (serviceId != null) {
            final LayerSecurityFilter securityFilter = getSecurityFilter(serviceId);
            final List<Layer> layers   = layerRepository.findByServiceId(serviceId);
            for (Layer layer : layers) {
                if (securityFilter.allowed(login, layer.getId())) {
                    org.constellation.dto.service.config.wxs.Layer confLayer = toLayerConfig(layer);
                    if (confLayer != null) {
                        response.add(confLayer);
                    }
                }
            }
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + serviceId);
        }
        return response;
    }

    @Override
    public List<NameInProvider> getLayerNames(final String serviceType, final String serviceName, final String login) throws ConfigurationException {
        final List<NameInProvider> response = new ArrayList<>();
        final Integer service = serviceBusiness.getServiceIdByIdentifierAndType(serviceType.toLowerCase(), serviceName);

        if (service != null) {
            final LayerSecurityFilter securityFilter = getSecurityFilter(service);

            final List<Layer> layers   = layerRepository.findByServiceId(service);
            for (Layer layer : layers) {
                final GenericName name = NamesExt.create(layer.getNamespace(), layer.getName());
                final ProviderBrief provider  = providerRepository.findForData(layer.getDataId());
                 Date version = null;
                /* TODO how to get version?
                  if (layer.getVersion() != null) {
                    version = new Date(layer.getVersion());
                }*/
                if (securityFilter.allowed(login, layer.getId())) {
                    response.add(new NameInProvider(name, provider.getIdentifier(), version, layer.getAlias()));
                }
            }
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + serviceName);
        }
        return response;
    }

    @Override
    public List<Integer> getLayerIds(final Integer serviceId, final String login) throws ConfigurationException {
        final List<Integer> response = new ArrayList<>();
        serviceBusiness.ensureExistingInstance(serviceId);

        final LayerSecurityFilter securityFilter = getSecurityFilter(serviceId);

        final List<Integer> layers   = layerRepository.findIdByServiceId(serviceId);
        for (Integer layer : layers) {
            if (securityFilter.allowed(login, layer)) {
                response.add(layer);
            }
        }
        return response;
    }

    /**
     * Get a single layer from service spec and identifier and layer name and namespace.
     *
     * @param spec service type
     * @param identifier service identifier
     * @param name layer name
     * @param namespace layer namespace
     * @param login login for security check
     * @return org.constellation.dto.Layer
     * @throws ConfigurationException
     */
    @Override
    public org.constellation.dto.service.config.wxs.Layer getLayer(final String spec, final String identifier, final String nameOrAlias,
                                                          final String namespace, final String login) throws ConfigurationException {
        final Integer service = serviceBusiness.getServiceIdByIdentifierAndType(spec.toLowerCase(), identifier);

        if (service != null) {
            final LayerSecurityFilter securityFilter = getSecurityFilter(service);
            Layer layer;
            if (namespace != null && !namespace.isEmpty()) {
                //1. search by name and namespace
                layer = layerRepository.findByServiceIdAndLayerName(service, nameOrAlias, namespace);
            } else {
                //2. search by alias
                layer = layerRepository.findByServiceIdAndAlias(service, nameOrAlias);

                //3. search by single name
                if  (layer == null) {
                    layer = layerRepository.findByServiceIdAndLayerName(service, nameOrAlias);
                }
            }

            if (layer != null) {
                if (securityFilter.allowed(login, layer.getId())) {
                    return toLayerConfig(layer);
                } else {
                    throw new ConfigurationException("Not allowed to see this layer.");
                }
            } else {
                throw new TargetNotFoundException("Unable to find a layer:" + nameOrAlias);
            }
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + identifier);
        }
    }

    @Override
    public NameInProvider getFullLayerName(final Integer serviceId, final String nameOrAlias,
                                                          final String namespace, final String login) throws ConfigurationException {
        serviceBusiness.ensureExistingInstance(serviceId);

        final LayerSecurityFilter securityFilter = getSecurityFilter(serviceId);

        Layer layer;
        if (namespace != null && !namespace.isEmpty()) {
            //1. search by name and namespace
            layer = layerRepository.findByServiceIdAndLayerName(serviceId, nameOrAlias, namespace);
        } else {
            //2. search by alias
            layer = layerRepository.findByServiceIdAndAlias(serviceId, nameOrAlias);

            //3. search by single name
            if  (layer == null) {
                layer = layerRepository.findByServiceIdAndLayerName(serviceId, nameOrAlias);
            }
        }

        if (layer != null) {
            final GenericName layerName = NamesExt.create(layer.getNamespace(), layer.getName());
            if (securityFilter.allowed(login, layer.getId())) {
                final ProviderBrief provider  = providerRepository.findForData(layer.getDataId());
                Date version = null;
                /* TODO how to get version?
                  if (layer.getVersion() != null) {
                    version = new Date(layer.getVersion());
                }*/
                return new NameInProvider(layerName, provider.getIdentifier(), version, layer.getAlias());
            } else {
                throw new ConfigurationException("Not allowed to see this layer.");
            }
        } else {
            throw new TargetNotFoundException("Unable to find a layer:" + nameOrAlias);
        }

    }

    @Override
    public NameInProvider getFullLayerName(final Integer serviceId, final Integer layerId, final String login) throws ConfigurationException {
        serviceBusiness.ensureExistingInstance(serviceId);

        final LayerSecurityFilter securityFilter = getSecurityFilter(serviceId);

        Layer layer = layerRepository.findById(layerId);

        if (layer != null) {
            final GenericName layerName = NamesExt.create(layer.getNamespace(), layer.getName());
            if (securityFilter.allowed(login, layer.getId())) {
                final ProviderBrief provider  = providerRepository.findForData(layer.getDataId());
                Date version = null;
                /* TODO how to get version?
                  if (layer.getVersion() != null) {
                    version = new Date(layer.getVersion());
                }*/
                return new NameInProvider(layerName, provider.getIdentifier(), version, layer.getAlias());
            } else {
                throw new ConfigurationException("Not allowed to see this layer.");
            }
        } else {
            throw new TargetNotFoundException("Unable to find a layer:" + layerId);
        }

    }

    @Override
    public FilterAndDimension getLayerFilterDimension(final String spec, final String identifier, final String name,
                                                          final String namespace, final String login) throws ConfigurationException {
        final Integer service = serviceBusiness.getServiceIdByIdentifierAndType(spec.toLowerCase(), identifier);

        if (service != null) {
            final LayerSecurityFilter securityFilter = getSecurityFilter(service);
            Layer layer = (namespace != null && !namespace.isEmpty())?
                        layerRepository.findByServiceIdAndLayerName(service, name, namespace) :
                        layerRepository.findByServiceIdAndLayerName(service, name);

            if (layer != null) {
                if (securityFilter.allowed(login, layer.getId())) {
                    org.constellation.dto.service.config.wxs.Layer layerConfig = readLayerConfiguration(layer.getConfig());
                    if (layerConfig != null) {
                        return new FilterAndDimension(layerConfig.getFilter(), layerConfig.getDimensions());
                    }
                } else {
                    throw new ConfigurationException("Not allowed to see this layer.");
                }
            }
        } else {
            throw new TargetNotFoundException("Unable to find a service:" + identifier);
        }
        return new FilterAndDimension();
    }

    /**
     *
     * @param login
     * @param securityFilter
     * @param layer
     * @return
     * @throws ConfigurationException
     */
    private org.constellation.dto.service.config.wxs.Layer toLayerConfig(Layer layer) throws ConfigurationException {
        final ProviderBrief provider  = providerRepository.findForData(layer.getDataId());
        final QName name         = new QName(layer.getNamespace(), layer.getName());
        final List<Style> styles = styleRepository.findByLayer(layer.getId());
        org.constellation.dto.service.config.wxs.Layer layerConfig = readLayerConfiguration(layer.getConfig());
        if (layerConfig == null) {
            layerConfig = new org.constellation.dto.service.config.wxs.Layer(name);
            layerConfig.setTitle(layer.getTitle());
        }
        layerConfig.setId(layer.getId());

        // override with table values (TODO remove)
        layerConfig.setAlias(layer.getAlias());
        layerConfig.setDate(layer.getDate());
        layerConfig.setOwner(layer.getOwnerId());
        layerConfig.setProviderID(provider.getIdentifier());
        layerConfig.setProviderType(provider.getType());
        layerConfig.setDataId(layer.getDataId());

        // TODO layerDto.setAbstrac();
        // TODO layerDto.setAttribution(null);
        // TODO layerDto.setAuthorityURL(null);
        // TODO layerDto.setCrs(null);
        // TODO layerDto.setDataURL(null);
        // TODO layerDto.setDimensions(null);
        // TODO layerDto.setFilter(null);
        // TODO layerDto.setGetFeatureInfoCfgs(null);
        // TODO layerDto.setKeywords();
        // TODO layerDto.setMetadataURL(null);
        // TODO layerDto.setOpaque(Boolean.TRUE);


        for (Style style : styles) {
            DataReference styleProviderReference = DataReference.createProviderDataReference(DataReference.PROVIDER_STYLE_TYPE, "sld", style.getName());
            layerConfig.getStyles().add(styleProviderReference);
        }

         // TODO layerDto.setTitle(null);
         // TODO layerDto.setVersion();
        return layerConfig;
    }

    private LayerSecurityFilter getSecurityFilter(int  serviceId) throws ConfigurationException {
        final Object config = serviceBusiness.getConfiguration(serviceId);
        if (config instanceof LayerContext) {
            final LayerContext context = (LayerContext) config;
            final MapFactory mapfactory = getMapFactory(context.getImplementation());
            return mapfactory.getSecurityFilter();
        } else {
            throw new ConfigurationException("Trying to get a layer security filter on a non Layer service");
        }
    }

    private org.constellation.dto.service.config.wxs.Layer readLayerConfiguration(final String xml) throws ConfigurationException {
        try {
            if (xml != null) {
                final Unmarshaller u = GenericDatabaseMarshallerPool.getInstance().acquireUnmarshaller();
                final Object config = u.unmarshal(new StringReader(xml));
                GenericDatabaseMarshallerPool.getInstance().recycle(u);
                return (org.constellation.dto.service.config.wxs.Layer) config;
            }
            return null;
        } catch (JAXBException ex) {
            throw new ConfigurationException(ex);
        }
    }

    private String writeLayerConfiguration(final org.constellation.dto.service.config.wxs.Layer obj) {
        String config = null;
        if (obj != null) {
            try {
                final StringWriter sw = new StringWriter();
                final Marshaller m = GenericDatabaseMarshallerPool.getInstance().acquireMarshaller();
                m.marshal(obj, sw);
                GenericDatabaseMarshallerPool.getInstance().recycle(m);
                config = sw.toString();
            } catch (JAXBException e) {
                throw new ConstellationPersistenceException(e);
            }
        }
        return config;
    }

    /**
     * Select the good Map factory in the available ones in function of the dataSource type.
     *
     * @param type
     * @return
     */
    private MapFactory getMapFactory(final DataSourceType type) throws ConfigurationException {
        final Iterator<MapFactory> ite = ServiceRegistry.lookupProviders(MapFactory.class);
        while (ite.hasNext()) {
            MapFactory currentFactory = ite.next();
            if (currentFactory.factoryMatchType(type)) {
                return currentFactory;
            }
        }
        throw new ConfigurationException("No Map factory has been found for type:" + type);
    }
}
