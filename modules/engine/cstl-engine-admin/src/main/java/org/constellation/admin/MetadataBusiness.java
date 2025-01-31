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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.xml.bind.JAXBException;

import org.apache.sis.metadata.iso.DefaultMetadata;
import org.apache.sis.util.logging.Logging;
import org.constellation.admin.util.MetadataUtilities;
import org.constellation.business.IMetadataBusiness;
import org.constellation.exception.ConfigurationException;
import org.constellation.dto.CstlUser;
import org.constellation.dto.Data;
import org.constellation.dto.DataSet;
import org.constellation.dto.metadata.Metadata;
import org.constellation.dto.service.Service;
import org.constellation.repository.DataRepository;
import org.constellation.repository.DatasetRepository;
import org.constellation.repository.MetadataRepository;
import org.constellation.repository.ServiceRepository;
import org.constellation.json.metadata.Template;
import org.constellation.json.metadata.bean.TemplateResolver;
import org.geotoolkit.nio.IOUtilities;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import javax.annotation.PostConstruct;
import javax.xml.bind.Marshaller;
import javax.measure.format.ParserException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import org.apache.sis.storage.DataStoreProvider;
import org.apache.sis.util.Locales;
import org.apache.sis.util.iso.Types;
import static org.constellation.api.CommonConstants.CSW_CONFIG_ONLY_PUBLISHED;
import static org.constellation.api.CommonConstants.CSW_CONFIG_PARTIAL;
import org.constellation.api.ServiceDef;
import org.constellation.dto.metadata.GroupStatBrief;
import org.constellation.dto.metadata.OwnerStatBrief;
import org.constellation.dto.metadata.User;
import org.constellation.business.ClusterMessage;
import org.constellation.business.IClusterBusiness;
import org.constellation.business.IProviderBusiness;
import org.constellation.business.IUserBusiness;
import org.constellation.dto.metadata.MetadataLists;
import org.constellation.dto.metadata.MetadataComplete;
import org.constellation.dto.metadata.MetadataWithState;
import org.constellation.dto.metadata.Attachment;
import org.constellation.dto.metadata.MetadataBbox;
import org.constellation.repository.InternalMetadataRepository;
import org.constellation.repository.AttachmentRepository;
import org.constellation.repository.MapContextRepository;
import org.constellation.dto.MapContextDTO;
import org.constellation.dto.metadata.MetadataBrief;
import org.constellation.dto.metadata.MetadataLightBrief;
import org.constellation.dto.metadata.RootObj;
import org.constellation.exception.ConstellationException;
import org.constellation.dto.service.config.generic.Automatic;
import org.constellation.exception.ConstellationStoreException;
import org.constellation.generic.database.GenericDatabaseMarshallerPool;
import org.constellation.metadata.utils.Utils;
import org.constellation.provider.DataProvider;
import org.constellation.provider.DataProviders;
import org.constellation.provider.MetadataData;
import org.constellation.provider.MetadataProvider;
import org.constellation.util.NodeUtilities;
import org.constellation.ws.ICSWConfigurer;
import org.geotoolkit.ebrim.xml.EBRIMMarshallerPool;
import org.geotoolkit.metadata.dimap.DimapAccessor;
import org.geotoolkit.util.DomUtilities;
import org.opengis.metadata.citation.DateType;
import org.opengis.metadata.constraint.Classification;
import org.opengis.metadata.constraint.Restriction;
import org.opengis.metadata.content.CoverageContentType;
import org.opengis.metadata.content.ImagingCondition;
import org.opengis.metadata.identification.KeywordType;
import org.opengis.metadata.identification.TopicCategory;
import org.opengis.metadata.identification.Progress;
import org.opengis.metadata.maintenance.MaintenanceFrequency;
import org.opengis.metadata.maintenance.ScopeCode;
import org.opengis.metadata.spatial.CellGeometry;
import org.opengis.metadata.spatial.SpatialRepresentationType;
import org.opengis.metadata.spatial.DimensionNameType;
import org.opengis.metadata.spatial.GeometricObjectType;
import org.opengis.metadata.spatial.PixelOrientation;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import static org.constellation.util.NodeUtilities.getMetadataFromNode;
import static org.constellation.util.NodeUtilities.getNodeFromObject;
import org.constellation.util.Util;
import org.constellation.ws.IWSEngine;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.util.NamesExt;
import org.opengis.parameter.ParameterValueGroup;
import static org.constellation.business.ClusterMessageConstant.*;

/**
 * Business facade for metadata.
 *
 * @author Guilhem Legal (Geomatys)
 * @author Mehdi Sidhoum (Geomatys).
 * @since 0.9
 */
@Component("cstlMetadataBusiness")
@Primary
public class MetadataBusiness implements IMetadataBusiness {

    protected static final Logger LOGGER = Logging.getLogger("org.constellation.admin");

    /**
     * Injected data repository.
     */
    @Inject
    protected DataRepository dataRepository;
    /**
     * Injected dataset repository.
     */
    @Inject
    protected DatasetRepository datasetRepository;
    /**
     * Injected service repository.
     */
    @Inject
    protected ServiceRepository serviceRepository;
    /**
     * Injected metadata repository.
     */
    @Inject
    protected MetadataRepository metadataRepository;
    /**
     * Injected attachment repository.
     */
    @Inject
    protected AttachmentRepository attachmentRepository;
    /**
     * Injected mapContext repository.
     */
    @Inject
    protected MapContextRepository mapContextRepository;

    @Inject
    protected InternalMetadataRepository internalMetadataRepository;

    @Inject
    protected IUserBusiness userBusiness;

    @Inject
    private org.constellation.security.SecurityManager securityManager;

    @Inject
    private IClusterBusiness clusterBusiness;

    @Inject
    protected IProviderBusiness providerBusiness;

    @Inject
    private IWSEngine wsengine;

    @Inject
    private TemplateResolver templateResolver;

    @PostConstruct
    public void contextInitialized() {
        Lock lock = clusterBusiness.acquireLock("setup-default-metadata-internal-provider");
        lock.lock();
        LOGGER.fine("LOCK Acquired on cluster: setup-default-metadata-internal-provider");

        try {
            getDefaultInternalProviderID();
        } catch (ConfigurationException ex) {
            LOGGER.log(Level.WARNING, "Unable to generate default internal metadata provider : {0}", ex.getMessage());
        } finally {
            LOGGER.fine("UNLOCK on cluster: setup-default-metadata-internal-provider");
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataBrief searchFullMetadata(final String metadataId, final boolean includeService, final boolean onlyPublished, final Integer providerID)  {
        final Metadata metadata = metadataRepository.findByMetadataId(metadataId);
        if (metadata != null) {
            if (!includeService && metadata.getServiceId() != null) {
                return null;
            }
            if (onlyPublished && !metadata.getIsPublished()) {
                return null;
            }
            if (providerID != null && !metadata.getProviderId().equals(providerID)) {
                return null;
            }
            return convertToMetadataBrief(metadata);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public MetadataLightBrief updateMetadata(final String metadataId, final Object metadataObj, final Integer dataID, final Integer datasetID,
                                  final Integer mapContextID, final Integer owner, Integer providerId, String type) throws ConfigurationException  {
        return updateMetadata(metadataId, metadataObj, dataID, datasetID, mapContextID, owner, providerId, type, null, false);

    }
    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public MetadataLightBrief updateMetadata(final String metadataId, final Object metadataObj, final Integer dataID, final Integer datasetID,
                                  final Integer mapContextID, final Integer owner, Integer providerId, String type, String templateName, boolean hidden) throws ConfigurationException  {
        Metadata metadata          = metadataRepository.findByMetadataId(metadataId);
        final boolean update       = metadata != null;
        final Node   metaNode;
        final Object meta;
        try {
            if (metadataObj instanceof Node) {
                metaNode  = (Node) metadataObj;
                meta      = getMetadataFromNode(metaNode, EBRIMMarshallerPool.getInstance());
            } else {
                meta     = metadataObj;
                metaNode = getNodeFromObject(meta, EBRIMMarshallerPool.getInstance());
            }
        } catch (JAXBException | ParserConfigurationException | ParserException | IllegalArgumentException ex) {
            throw new ConfigurationException(ex);
        }

        final Long dateStamp   = MetadataUtilities.extractDatestamp(meta);
        final String title     = MetadataUtilities.extractTitle(meta);
        final String resume    = MetadataUtilities.extractResume(meta);
        Integer parentID       = null;
        final String parent    = MetadataUtilities.extractParent(meta);
        Metadata parentRecord  = metadataRepository.findByMetadataId(parent);
        if (parentRecord != null) {
            parentID = parentRecord.getId();
        }
        final List<MetadataBbox> bboxes = MetadataUtilities.extractBbox(meta);
        Integer userID = owner;
        if (userID == null) {
            final Optional<CstlUser> user = userBusiness.findOne(securityManager.getCurrentUserLogin());
            if (user.isPresent()) {
                userID = user.get().getId();
            }
        }
        Integer completion  = null;
        String level        = "NONE";
        final boolean previousPublishState;
        final boolean previousHiddenState;
        if (metadata != null) {
            if (templateName == null) {
                templateName = metadata.getProfile();
            }
            previousPublishState = metadata.getIsPublished();
            previousHiddenState  = metadata.getIsHidden();
        } else {
            metadata = new Metadata();
            metadata.setIsShared(false);
            previousPublishState = false;
            previousHiddenState  = hidden;
        }

        metadata.setOwner(userID);
        metadata.setDatestamp(dateStamp);
        metadata.setDateCreation(System.currentTimeMillis());
        metadata.setTitle(title);
        metadata.setResume(resume);
        metadata.setParentIdentifier(parentID);
        metadata.setIsPublished(false);
        metadata.setIsValidated(false);
        metadata.setIsHidden(hidden);

        // SET type if not null, else we keep the previous one (default to DOC in the database)
        if (type != null) {
            metadata.setType(type);
        }

        // if the metadata is not yet present look for empty metadata object
        final DataSet dataset;
        if (datasetID != null) {
            dataset = datasetRepository.findById(datasetID);
        } else {
            dataset = datasetRepository.findByIdentifierWithEmptyMetadata(metadataId);
        }
        final Data data;
        if (dataID != null) {
            data = dataRepository.findById(dataID);
        } else {
            // unsafe but no better way for now
            data = dataRepository.findByIdentifierWithEmptyMetadata(metadataId);
        }
        MapContextDTO mapContext = null;
        if (mapContextID != null) {
            mapContext = mapContextRepository.findById(mapContextID);
        }

        if (!update) {
            String dataType = null;
            if (dataset != null) {
                metadata.setDatasetId(dataset.getId());
                List<Data> datas = dataRepository.findByDatasetId(dataset.getId());
                if (!datas.isEmpty()) {
                    dataType = datas.get(0).getType();
                }
            } else if (data != null) {
                metadata.setDataId(data.getId());
                dataType = data.getType();
            } else if (mapContext != null) {
                metadata.setMapContextId(mapContext.getId());
                dataType = "mapContext";
            }

            Template template = templateResolver.resolveDefault(meta, dataType);
            if (template != null) {
                templateName = template.getIdentifier();
            }
        }

        if (templateName == null) {
            templateName = templateResolver.getFallbackTemplate().getIdentifier();
        }

        final String newMetaId; // in case of identifier change in object
        //paranoiac check
        if (templateName != null) {
            final Template template = templateResolver.getByName(templateName);
            newMetaId               = template.getMetadataIdentifier(meta);
            try {
                completion = template.calculateMDCompletion(meta);
                level      = template.getCompletion(meta);
            } catch (IOException | ClassCastException ex) {
                LOGGER.log(Level.WARNING, "Error while calculating metadata completion", ex);
            }
        } else {
            newMetaId = Utils.findIdentifier(meta);
            LOGGER.log(Level.WARNING, "Template name not defined for metadata "+metadataId+" in "+(update ? "update" : "create")+ " mode.");
        }

        metadata.setMetadataId(newMetaId);
        metadata.setProfile(templateName);
        metadata.setMdCompletion(completion);
        metadata.setLevel(level);

        if (update) {
            providerId = metadata.getProviderId();
            metadataRepository.update(new MetadataComplete(metadata, bboxes));
        } else {
            metadata.setProviderId(providerId);
            int id = metadataRepository.create(new MetadataComplete(metadata, bboxes));
            metadata.setId(id);
        }

        // update in store
        final DataProvider provider = DataProviders.getProvider(providerId);
        if (provider instanceof MetadataProvider) {
            final MetadataProvider mdProvider = (MetadataProvider) provider;
            try {
                if (update) {
                    mdProvider.replaceMetadata(metadataId, metaNode);
                } else {
                    mdProvider.storeMetadata(metaNode);
                }
            } catch (ConstellationStoreException ex) {
                throw new ConfigurationException(ex);
            }
        } else {
            throw new ConfigurationException("The provider " + providerId + " is not a metadata provider");
        }

        updateCSWIndex(Arrays.asList(new MetadataWithState(metadata, previousPublishState, previousHiddenState)), true);
        return convertToMetadataLightBrief(metadata);
    }

    @Override
    public boolean updatePartialMetadata(String metadataId, Map<String, Object> properties, Integer providerId) throws ConfigurationException {
        // update in store ? update metadata table properties?
        final DataProvider provider = DataProviders.getProvider(providerId);
        if (provider instanceof MetadataProvider) {
            final MetadataProvider mdProvider = (MetadataProvider) provider;
            Metadata metadata = metadataRepository.findByMetadataId(metadataId);
            try {
                boolean result = mdProvider.updateMetadata(metadataId, properties);
                if (result) {
                    updateCSWIndex(Arrays.asList(new MetadataWithState(metadata, metadata.getIsPublished(), metadata.getIsHidden())), true);
                }
                return result;
            } catch (ConstellationStoreException ex) {
                throw new ConfigurationException(ex);
            }
        } else {
            throw new ConfigurationException("The provider " + providerId + " is not a metadata provider");
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existInternalMetadata(final String metadataID, final boolean includeService, final boolean onlyPublished, final Integer providerID) {
        return metadataRepository.existInternalMetadata(metadataID, includeService, onlyPublished, providerID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean existMetadataTitle(final String title) {
        return metadataRepository.existMetadataTitle(title);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getMetadataIds(final boolean includeService, final boolean onlyPublished, final Integer providerID, final String type) {
        return metadataRepository.findMetadataID(includeService, onlyPublished, providerID, type);
    }

    @Override
    public int getMetadataCount(final boolean includeService, final boolean onlyPublished, final Integer providerID, final String type) {
        return metadataRepository.countMetadata(includeService, onlyPublished, providerID, type);
    }

    @Override
    public List<MetadataBrief> getByProviderId(int providerID, final String type) {
        return convertToMetadataBriefList(metadataRepository.findByProviderId(providerID, type));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getLinkedMetadataIDs(final String cswIdentifier, final boolean partial, final boolean includeService, final boolean onlyPublished, final String type) {
        final List<String> results = new ArrayList<>();
        final Integer service = serviceRepository.findIdByIdentifierAndType(cswIdentifier, "csw");
        if (service != null) {
            if (partial) {
                results.addAll(metadataRepository.findMetadataIDByCswId(service, includeService, onlyPublished, type, false));
            } else {
                Integer provider = serviceRepository.getLinkedMetadataProvider(service);
                if (provider != null) {
                    results.addAll(metadataRepository.findMetadataIDByProviderId(provider, includeService, onlyPublished, type, false));
                }
            }
        }
        return results;
    }

    @Override
    public int getLinkedMetadataCount(final String cswIdentifier, final boolean partial, final boolean includeService, final boolean onlyPublished, final String type) {
        int count = 0;
        final Integer service = serviceRepository.findIdByIdentifierAndType(cswIdentifier, "csw");
        if (service != null) {
            if (partial) {
                count = metadataRepository.countMetadataByCswId(service, includeService, onlyPublished, type, false);
            } else {
               Integer provider = serviceRepository.getLinkedMetadataProvider(service);
               if (provider != null) {
                    count = metadataRepository.countMetadataByProviderId(provider, includeService, onlyPublished, type, false);
               }
            }
        }
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLinkedMetadataToCSW(final int metadataID, final int cswID) {
        return metadataRepository.isLinkedMetadata(metadataID, cswID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isLinkedMetadataToCSW(final String metadataID, final String cswID) {
        return metadataRepository.isLinkedMetadata(metadataID, cswID);
    }

    @Override
    public boolean isLinkedMetadataToCSW(final String metadataID, final String cswID, final boolean partial, final boolean includeService, final boolean onlyPublished) {
        if (partial) {
            return metadataRepository.isLinkedMetadata(metadataID, cswID, includeService, onlyPublished);
        } else {
            final Integer service = serviceRepository.findIdByIdentifierAndType(cswID, "csw");
            if (service != null) {
                Integer provider = serviceRepository.getLinkedMetadataProvider(service);
                if (provider != null) {
                    return metadataRepository.isLinkedMetadata(metadataID, provider, includeService, onlyPublished);
                }
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void linkMetadataIDToCSW(final String metadataId, final String cswIdentifier)  throws ConfigurationException {
        final Service service = serviceRepository.findByIdentifierAndType(cswIdentifier, "csw");
        if (service != null) {
            final boolean partial;
            try {
                final Unmarshaller um = GenericDatabaseMarshallerPool.getInstance().acquireUnmarshaller();
                final Automatic config = (Automatic) um.unmarshal(new StringReader(service.getConfig()));
                partial = config.getBooleanParameter(CSW_CONFIG_PARTIAL, false);
                GenericDatabaseMarshallerPool.getInstance().recycle(um);
            } catch (JAXBException ex) {
                throw new ConfigurationException("Error while reading CSW configuration", ex);
            }
            if (partial) {
                metadataRepository.addMetadataToCSW(metadataId, service.getId());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void unlinkMetadataIDToCSW(final String metadataId, final String cswIdentifier) {
        final Integer service = serviceRepository.findIdByIdentifierAndType(cswIdentifier, "csw");
        if (service != null) {
            metadataRepository.removeDataFromCSW(metadataId, service);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getMetadata(final int id) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findById(id);
        if (metadata != null) {
            return getMetadata(metadata.getMetadataId());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getMetadata(final String metadataId) throws ConfigurationException {
        final Node n = getMetadataNode(metadataId);
        if (n != null) {
            return unmarshallMetadata(n);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataLightBrief getMetadataPojo(final String id) throws ConfigurationException {
        return convertToMetadataLightBrief(metadataRepository.findByMetadataId(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataBrief getMetadataPojo(final int id) throws ConfigurationException {
        return convertToMetadataBrief(metadataRepository.findById(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Node getMetadataNode(final String metadataId) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findByMetadataId(metadataId);
        if (metadata != null) {
            final DataProvider provider = DataProviders.getProvider(metadata.getProviderId());
            final MetadataData md = (MetadataData) provider.get(NamesExt.create(metadataId));
            return md.getMetadata();
        }
        return null;
    }

    @Override
    public List<MetadataLightBrief> getMetadataBriefForData(final int dataId) {
        final List<MetadataLightBrief> list = new ArrayList<>();
        final List<Metadata> metadatas = metadataRepository.findByDataId(dataId);

        for (final Metadata entry : metadatas) {
            list.add(new MetadataLightBrief(entry.getId(),
                                            entry.getTitle(),
                                            entry.getProfile()));
        }

        return list;
    }

    @Override
    public List<MetadataLightBrief> getMetadataBriefForDataset(final int datasetId) {
        final List<MetadataLightBrief> list = new ArrayList<>();
        final Map<String, Object> filter = Collections.singletonMap("dataset", datasetId);
        final List<Map<String,Object>> map = metadataRepository.filterAndGetWithoutPagination(filter);
        if (map != null) {
            for (final Map entry : map) {
                list.add(new MetadataLightBrief((Integer)entry.get("id"),
                                                (String)entry.get("title"),
                                                (String)entry.get("profile")));
            }
        }
        return list;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getIsoMetadataForData(final int dataId) throws ConfigurationException {
        final List<Metadata> metadatas = metadataRepository.findByDataId(dataId);
        for (Metadata metadata : metadatas) {
            Object obj = getMetadata(metadata.getId());

            // HACK for now we want only the ISO 19115 metadata
            if (obj instanceof DefaultMetadata) {
                return obj;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Object> getIsoMetadatasForData(final int dataId) throws ConfigurationException {
        List<Object> results = new ArrayList<>();
        final List<Metadata> metadatas = metadataRepository.findByDataId(dataId);
        for (Metadata metadata : metadatas) {
            results.add(getMetadata(metadata.getId()));
        }
        return results;
    }

    @Override
    public Object getIsoMetadataForService(final int serviceId) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findByServiceId(serviceId);
        if (metadata != null) {
            return getMetadata(metadata.getId());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getIsoMetadataForDataset(final int datasetId) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findByDatasetId(datasetId);
        if (metadata != null) {
            return getMetadata(metadata.getId());
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MetadataBrief getMetadataById(final int id) {
        Metadata m = metadataRepository.findById(id);
        if (m != null) {
            return convertToMetadataBrief(m);
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updatePublication(final int id, final boolean newStatus) throws ConfigurationException {
        updatePublication(Arrays.asList(id), newStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updatePublication(final List<Integer> ids, final boolean newStatus) throws ConfigurationException {
        final List<MetadataWithState> toUpdate = new ArrayList<>();
        for (Integer id : ids) {
            final Metadata metadata = metadataRepository.findById(id);
            if (metadata != null) {
                metadataRepository.changePublication(id, newStatus);
                final boolean prev = metadata.getIsPublished();
                metadata.setIsPublished(newStatus);
                toUpdate.add(new MetadataWithState(metadata, prev, metadata.getIsHidden()));
            }
        }
        updateCSWIndex(toUpdate, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateHidden(final int id, final boolean newStatus) throws ConfigurationException {
        updateHidden(Arrays.asList(id), newStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateHidden(final List<Integer> ids, final boolean newStatus) throws ConfigurationException {
        final List<MetadataWithState> toUpdate = new ArrayList<>();
        for (Integer id : ids) {
            final Metadata metadata = metadataRepository.findById(id);
            if (metadata != null) {
                metadataRepository.changeHidden(id, newStatus);
                final boolean prev = metadata.getIsHidden();
                metadata.setIsHidden(newStatus);
                toUpdate.add(new MetadataWithState(metadata, metadata.getIsPublished(), prev));
            }
        }
        updateCSWIndex(toUpdate, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateSharedProperty(final int id, final boolean shared) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findById(id);
        if (metadata != null) {
            metadataRepository.changeSharedProperty(id, shared);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateSharedProperty(final List<Integer> ids, final boolean shared) throws ConfigurationException {
        for (Integer id : ids) {
            updateSharedProperty(id, shared);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateProfile(final Integer id, final String newProfile) throws ConfigurationException {
        metadataRepository.changeProfile(id, newProfile);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateValidation(int id, boolean newStatus) {
        metadataRepository.changeValidation(id, newStatus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateOwner(int id, int newOwner) {
        metadataRepository.changeOwner(id, newOwner);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void updateOwner(List<Integer> ids, int newOwner) {
        for (final Integer id : ids) {
            metadataRepository.changeOwner(id, newOwner);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMetadata(int id) throws ConfigurationException {
        deleteMetadata(Arrays.asList(id));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public boolean deleteMetadata(String metadataID) throws ConfigurationException {
        final Metadata meta = metadataRepository.findByMetadataId(metadataID);
        if (meta != null) {
            deleteMetadata(Arrays.asList(meta.getId()));
            return true;
        }
        return false;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteDataMetadata(final int dataId) throws ConfigurationException {
        final List<Metadata> metas = metadataRepository.findByDataId(dataId);
        for (Metadata meta : metas) {
            deleteMetadata(meta.getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteDatasetMetadata(final int datasetId) throws ConfigurationException {
        final Metadata meta = metadataRepository.findByDatasetId(datasetId);
        if (meta != null) {
            deleteMetadata(meta.getId());
        }
    }

    @Override
    @Transactional
    public void deleteMapContextMetadata(int mapContextId) throws ConfigurationException {
        final Metadata meta = metadataRepository.findByMapContextId(mapContextId);
        if (meta != null) {
            deleteMetadata(meta.getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteMetadata(List<Integer> ids) throws ConfigurationException {
        // First we update the csw index
        final List<MetadataWithState> toDelete = new ArrayList<>();
        for (Integer id : ids) {
            final Metadata metadata = metadataRepository.findById(id);
            if (metadata != null) {
                final DataProvider provider = DataProviders.getProvider(metadata.getProviderId());
                if (provider instanceof MetadataProvider) {
                    final MetadataProvider mdStore = (MetadataProvider) provider;
                    try {
                        mdStore.deleteMetadata(metadata.getMetadataId());
                        toDelete.add(new MetadataWithState(metadata, metadata.getIsPublished(), metadata.getIsHidden()));
                    } catch (ConstellationStoreException ex) {
                        throw new ConfigurationException(ex);
                    }
                } else {
                    throw new ConfigurationException("The provider " + metadata.getProviderId() + " is not a metadata provider");
                }
            }
        }
        updateCSWIndex(toDelete, false);

        // then we remove the metadata from the database
        for (Integer id : ids) {
            attachmentRepository.deleteForMetadata(id);
            metadataRepository.delete(id);
        }
    }

    @Override
    @Transactional
    public void deleteFromProvider(int identifier) throws ConfigurationException {
        List<Metadata> metas = metadataRepository.findByProviderId(identifier, null);
        for (Metadata meta : metas) {
            deleteMetadata(meta.getId());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public void deleteAllMetadata() throws ConfigurationException {
        List<Integer> ids = metadataRepository.findAllIds();
        deleteMetadata(ids);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getCompletionForDataset(int datasetId) {
        final Metadata metadata = metadataRepository.findByDatasetId(datasetId);
        if (metadata != null) {
            return metadata.getMdCompletion();
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void updateCSWIndex(final List<MetadataWithState> metadatas, final boolean update) throws ConfigurationException {
        if (metadatas.isEmpty()) return;
        try {
            final List<Service> services = serviceRepository.findByType("csw");
            for (Service service : services) {

                final Unmarshaller um = GenericDatabaseMarshallerPool.getInstance().acquireUnmarshaller();
                // read config to determine CSW type
                final Automatic conf = (Automatic) um.unmarshal(new StringReader(service.getConfig()));
                GenericDatabaseMarshallerPool.getInstance().recycle(um);
                boolean partial       = conf.getBooleanParameter(CSW_CONFIG_PARTIAL, false);
                boolean onlyPublished = conf.getBooleanParameter(CSW_CONFIG_ONLY_PUBLISHED, false);

                final List<String> identifierToRemove = new ArrayList<>();
                final List<String> identifierToUpdate = new ArrayList<>();
                boolean needRefresh = false;

                for (MetadataWithState metadata : metadatas) {

                    if (serviceRepository.isLinkedMetadataProviderAndService(service.getId(), metadata.getProviderId())) {

                        // if the csw take all the metadata or if the metadata is linked to the service
                        if (!partial || (partial && isLinkedMetadataToCSW(metadata.getId(), service.getId()))) {


                            if ((!onlyPublished && !metadata.isPreviousHiddenState()) || (onlyPublished && metadata.isPreviousPublishState())) {
                                identifierToRemove.add(metadata.getMetadataId());
                                needRefresh = true;
                            }
                            if (update) {
                                if (!metadata.getIsHidden() && (!onlyPublished || (onlyPublished && metadata.getIsPublished()))){
                                    identifierToUpdate.add(metadata.getMetadataId());
                                    needRefresh = true;
                                }
                            }
                        }
                    }
                }
                if (needRefresh) {
                    ICSWConfigurer configurer = (ICSWConfigurer) wsengine.newInstance(ServiceDef.Specification.CSW);
                    configurer.removeFromIndex(service.getIdentifier(), identifierToRemove);
                    configurer.addToIndex(service.getIdentifier(), identifierToUpdate);

                    //send refresh message to services
                    final ClusterMessage message = clusterBusiness.createRequest(SRV_MESSAGE_TYPE_ID,false);
                    message.put(KEY_ACTION, SRV_VALUE_ACTION_REFRESH);
                    message.put(SRV_KEY_TYPE, "CSW");
                    message.put(KEY_IDENTIFIER, service.getIdentifier());
                    clusterBusiness.publish(message);
                }
            }
        } catch (JAXBException ex) {
            throw new ConfigurationException("Error while updating internal CSW index", ex);
        }
    }

    @Override
    public MetadataLists getMetadataCodeLists() {
        final MetadataLists mdList = new MetadataLists();

        //for role codes
        final List<String> roleCodes = new LinkedList<>();
        for (final org.opengis.metadata.citation.Role role : org.opengis.metadata.citation.Role.values()) {
            final String standardName = Types.getStandardName(role.getClass());
            final String code = role.identifier()!=null?role.identifier():role.name();
            final String codeListName = standardName+"."+code;
            roleCodes.add(codeListName);
        }
        Collections.sort(roleCodes);
        mdList.setRoleCodes(roleCodes);

        //for keyword type codes
        final List<String> keywordTypesCodes = new LinkedList<>();
        for (final KeywordType ktype : KeywordType.values()) {
            final String standardName = Types.getStandardName(ktype.getClass());
            final String code = ktype.identifier()!=null?ktype.identifier():ktype.name();
            final String codeListName = standardName+"."+code;
            keywordTypesCodes.add(codeListName);
        }
        Collections.sort(keywordTypesCodes);
        mdList.setKeywordTypeCodes(keywordTypesCodes);

        //for locale codes
        final List<String> localeCodes = new LinkedList<>();
        for (final Locale locale : Locales.ALL.getAvailableLanguages()) {
            localeCodes.add("LanguageCode."+locale.getISO3Language());
        }
        // add missing locale (FRE)
        localeCodes.add("LanguageCode.fre");
        Collections.sort(localeCodes);
        mdList.setLocaleCodes(localeCodes);

        //for topic category codes
        final List<String> topicCategoryCodes = new LinkedList<>();
        for (final TopicCategory tc : TopicCategory.values()) {
            final String standardName = Types.getStandardName(tc.getClass());
            final String code = tc.identifier()!=null? tc.identifier(): tc.name();
            final String codeListName = standardName+"."+code;
            topicCategoryCodes.add(codeListName);
        }
        Collections.sort(topicCategoryCodes);
        mdList.setTopicCategoryCodes(topicCategoryCodes);

        //for date type codes
        final List<String> dateTypeCodes = new LinkedList<>();
        for (final DateType dateType : DateType.values()) {
            final String standardName = Types.getStandardName(dateType.getClass());
            final String code = dateType.identifier()!=null? dateType.identifier(): dateType.name();
            final String codeListName = standardName+"."+code;
            dateTypeCodes.add(codeListName);
        }
        Collections.sort(dateTypeCodes);
        mdList.setDateTypeCodes(dateTypeCodes);

        //for maintenanceFrequency codes
        final List<String> maintenanceFrequencyCodes = new LinkedList<>();
        for (final MaintenanceFrequency cl : MaintenanceFrequency.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            maintenanceFrequencyCodes.add(codeListName);
        }
        Collections.sort(maintenanceFrequencyCodes);
        mdList.setMaintenanceFrequencyCodes(maintenanceFrequencyCodes);

        //for GeometricObjectType codes
        final List<String> geometricObjectTypeCodes = new LinkedList<>();
        for (final GeometricObjectType got : GeometricObjectType.values()) {
            final String standardName = Types.getStandardName(got.getClass());
            final String code = got.identifier()!=null? got.identifier(): got.name();
            final String codeListName = standardName+"."+code;
            geometricObjectTypeCodes.add(codeListName);
        }
        Collections.sort(geometricObjectTypeCodes);
        mdList.setGeometricObjectTypeCodes(geometricObjectTypeCodes);

        //for Classification codes
        final List<String> classificationCodes = new LinkedList<>();
        for (final Classification cl : Classification.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            classificationCodes.add(codeListName);
        }
        Collections.sort(classificationCodes);
        mdList.setClassificationCodes(classificationCodes);

        // for characterSet codes
        final List<String> characterSetCodes = new LinkedList<>();
        final Set<String> keys = Charset.availableCharsets().keySet();
        final List<String> keep = Arrays.asList("UTF-8","UTF-16","UTF-32",
                "ISO-8859-1","ISO-8859-13","ISO-8859-15",
                "ISO-8859-2","ISO-8859-3","ISO-8859-4",
                "ISO-8859-5","ISO-8859-6","ISO-8859-7",
                "ISO-8859-8","ISO-8859-9","Shift_JIS",
                "EUC-JP","EUC-KR","US-ASCII","Big5","GB2312");
        keep.retainAll(keys);
        for (final String c : keep) {
            characterSetCodes.add(c);
        }
        Collections.sort(characterSetCodes);
        mdList.setCharacterSetCodes(characterSetCodes);

        //for Restriction codes
        final List<String> restrictionCodes = new LinkedList<>();
        for (final Restriction cl : Restriction.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            restrictionCodes.add(codeListName);
        }
        Collections.sort(restrictionCodes);
        mdList.setRestrictionCodes(restrictionCodes);

        final List<String> dimensionNameTypeCodes = new LinkedList<>();
        for (final DimensionNameType cl : DimensionNameType.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            dimensionNameTypeCodes.add(codeListName);
        }
        Collections.sort(dimensionNameTypeCodes);
        mdList.setDimensionNameTypeCodes(dimensionNameTypeCodes);

        final List<String> coverageContentTypeCodes = new LinkedList<>();
        for (final CoverageContentType cl : CoverageContentType.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            coverageContentTypeCodes.add(codeListName);
        }
        Collections.sort(coverageContentTypeCodes);
        mdList.setCoverageContentTypeCodes(coverageContentTypeCodes);

        final List<String> imagingConditionCodes = new LinkedList<>();
        for (final ImagingCondition cl : ImagingCondition.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            imagingConditionCodes.add(codeListName);
        }
        Collections.sort(imagingConditionCodes);
        mdList.setImagingConditionCodes(imagingConditionCodes);

        final List<String> cellGeometryCodes = new LinkedList<>();
        for (final CellGeometry cl : CellGeometry.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            cellGeometryCodes.add(codeListName);
        }
        Collections.sort(cellGeometryCodes);
        mdList.setCellGeometryCodes(cellGeometryCodes);

        //for pixel orientation codes
        final List<String> pixelOrientationCodes = new LinkedList<>();
        for (final PixelOrientation cl : PixelOrientation.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            pixelOrientationCodes.add(codeListName);
        }
        Collections.sort(pixelOrientationCodes);
        mdList.setPixelOrientationCodes(pixelOrientationCodes);

        //for Scope codes
        final List<String> scopeCodes = new LinkedList<>();
        for (final ScopeCode cl : ScopeCode.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            scopeCodes.add(codeListName);
        }
        Collections.sort(scopeCodes);
        mdList.setScopeCodes(scopeCodes);

        //for Progress codes
        final List<String> progressCodes = new LinkedList<>();
        for (final Progress cl : Progress.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            progressCodes.add(codeListName);
        }
        Collections.sort(progressCodes);
        mdList.setProgressCodes(progressCodes);

        //for Progress codes
        final List<String> spatialRepresentationCodes = new LinkedList<>();
        for (final SpatialRepresentationType cl : SpatialRepresentationType.values()) {
            final String standardName = Types.getStandardName(cl.getClass());
            final String code = cl.identifier()!=null? cl.identifier(): cl.name();
            final String codeListName = standardName+"."+code;
            spatialRepresentationCodes.add(codeListName);
        }
        Collections.sort(spatialRepresentationCodes);
        mdList.setSpatialRepresentationCodes(spatialRepresentationCodes);

        return mdList;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object unmarshallMetadata(final String metadata) throws ConfigurationException {
        try {
            final Unmarshaller um = EBRIMMarshallerPool.getInstance().acquireUnmarshaller();
            Object obj = um.unmarshal(new StringReader(metadata));
            EBRIMMarshallerPool.getInstance().recycle(um);
            return obj;
        } catch (JAXBException ex) {
            throw new ConfigurationException("Unable to unmarshall metadata", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object unmarshallMetadata(final File metadata) throws ConfigurationException {
        try {
            final Unmarshaller um = EBRIMMarshallerPool.getInstance().acquireUnmarshaller();
            Object obj = um.unmarshal(metadata);
            EBRIMMarshallerPool.getInstance().recycle(um);
            return obj;
        } catch (JAXBException ex) {
            throw new ConfigurationException("Unable to unmarshall metadata", ex);
        }
    }

    private Object unmarshallMetadata(final Node n) throws ConfigurationException {
        try {
            // We would like to use um.unmarshal(metadataNode) directly, but it currently causes
            // UnsupportedOperationException: Cannot create XMLEventReader from a DOMSource.
            final Unmarshaller um = EBRIMMarshallerPool.getInstance().acquireUnmarshaller();
            Object obj = um.unmarshal(new StringReader(NodeUtilities.getStringFromNode(n)));
            EBRIMMarshallerPool.getInstance().recycle(um);
            return obj;
        } catch (JAXBException | TransformerException ex) {
            throw new ConfigurationException("Unable to unmarshall metadata", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object unmarshallMetadata(Path metadata) throws ConfigurationException {
        try (InputStream stream = Files.newInputStream(metadata)){
            final Unmarshaller um = EBRIMMarshallerPool.getInstance().acquireUnmarshaller();
            Object obj = um.unmarshal(stream);
            EBRIMMarshallerPool.getInstance().recycle(um);
            return obj;
        } catch (JAXBException ex) {
            throw new ConfigurationException("Unable to unmarshall metadata", ex);
        } catch (IOException ex) {
            throw new ConfigurationException("Unable to read metadata file", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String marshallMetadata(final Object metadata) throws ConfigurationException {
        try {
            final Marshaller m = EBRIMMarshallerPool.getInstance().acquireMarshaller();
            final StringWriter sw = new StringWriter();
            m.marshal(metadata, sw);
            EBRIMMarshallerPool.getInstance().recycle(m);
            return sw.toString();
        } catch (JAXBException ex) {
            throw new ConfigurationException("Unable to unmarshall metadata", ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public MetadataLightBrief duplicateMetadata(final int id, final String newTitle, final String newType) throws ConfigurationException {
        final Metadata meta = metadataRepository.findById(id);
        if (meta != null) {
            List<MetadataBbox> bboxes = metadataRepository.getBboxes(id);
            final DataProvider provider = DataProviders.getProvider(meta.getProviderId());
            if (provider instanceof MetadataProvider) {
                final MetadataProvider mdProvider = (MetadataProvider) provider;
                final MetadataData md = (MetadataData) provider.get(NamesExt.create(meta.getMetadataId()));

                final Node metaNode = md.getMetadata();
                final Object metaObj = unmarshallMetadata(metaNode);
                final Template template = templateResolver.getByName(meta.getProfile());

                final String newMetadataID = UUID.randomUUID().toString();
                final long dateStamp = System.currentTimeMillis();
                final String title;
                if (newTitle != null) {
                    title = newTitle;
                } else {
                    String oldTitle = template.getMetadataTitle(metaObj);
                    title = oldTitle + "(1)";
                }
                template.setMetadataTitle(title, metaObj);
                template.setMetadataIdentifier(newMetadataID, metaObj);

                // ISO 19115 case
                if (metaObj instanceof DefaultMetadata) {
                    DefaultMetadata isoMetadata = (DefaultMetadata) metaObj;
                    isoMetadata.setDateStamp(new Date(dateStamp));
                }

                final Optional<CstlUser> user = userBusiness.findOne(securityManager.getCurrentUserLogin());
                if (user.isPresent()) {
                    meta.setOwner(user.get().getId());
                }

                meta.setDateCreation(dateStamp);
                meta.setMetadataId(newMetadataID);
                meta.setTitle(title);
                meta.setIsPublished(false);
                meta.setIsValidated(false);
                meta.setValidationRequired("NONE");
                meta.setDataId(null);
                meta.setDatasetId(null);
                if (newType != null) {
                    meta.setType(newType);
                }

                final MetadataComplete duplicated = new MetadataComplete(meta, bboxes);
                final int newID = metadataRepository.create(duplicated);
                try {
                    Node finalNode = getNodeFromObject(metaObj, EBRIMMarshallerPool.getInstance());
                    mdProvider.storeMetadata(finalNode);
                } catch (ConstellationStoreException | JAXBException | ParserConfigurationException ex) {
                    throw new ConfigurationException(ex);
                }
                return convertToMetadataLightBrief(metadataRepository.findById(newID));
            } else {
                throw new ConfigurationException("The provider " + meta.getProviderId() + " is not a metadata provider");
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countTotal(final Map<String,Object> filterMap) {
        return metadataRepository.countTotalMetadata(filterMap);
    }

    @Override
    public int[] countInCompletionRange(final Map<String,Object> filterMap) {
        final int[] completionArray = new int[10];
        completionArray[0] = metadataRepository.countInCompletionRange(filterMap, 0,  10);
        completionArray[1] = metadataRepository.countInCompletionRange(filterMap, 11, 20);
        completionArray[2] = metadataRepository.countInCompletionRange(filterMap, 21, 30);
        completionArray[3] = metadataRepository.countInCompletionRange(filterMap, 31, 40);
        completionArray[4] = metadataRepository.countInCompletionRange(filterMap, 41, 50);
        completionArray[5] = metadataRepository.countInCompletionRange(filterMap, 51, 60);
        completionArray[6] = metadataRepository.countInCompletionRange(filterMap, 61, 70);
        completionArray[7] = metadataRepository.countInCompletionRange(filterMap, 71, 80);
        completionArray[8] = metadataRepository.countInCompletionRange(filterMap, 81, 90);
        completionArray[9] = metadataRepository.countInCompletionRange(filterMap, 91, 100);
        return completionArray;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countPublished(final boolean status,final Map<String,Object> filterMap) {
        return metadataRepository.countPublished(status,filterMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int countValidated(final boolean status,final Map<String,Object> filterMap) {
        return metadataRepository.countValidated(status, filterMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String,Integer> getProfilesCount(final Map<String,Object> filterMap) {
        return metadataRepository.getProfilesCount(filterMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<String,Integer> getProfilesCount(final Map<String,Object> filterMap, String dataType) throws ConfigurationException {
        if (dataType == null) {
            return getProfilesCount(filterMap);
        }
        List<String> names = getProfilesMatchingType(dataType);
        filterMap.put("name", names);
        return metadataRepository.getProfilesCount(filterMap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllProfiles() {
        final Set<String> names = templateResolver.getAvailableNames();
        final List<String> result = new ArrayList<>();
        for(final String name : names) {
            //add only iso 19115 profiles raster and vector
            if(name.startsWith("profile_default_")) {
                result.add(name);
            }
        }
        return result;
    }

    @Override
    public List<String> getProfilesMatchingType(final String dataType) throws ConfigurationException {
        if (dataType == null) {
            return getAllProfiles();
        }
        final Set<String> names = templateResolver.getAvailableNames();
        final List<String> result = new ArrayList<>();
        for(final String name : names) {
            Template t = templateResolver.getByName(name);
            if (t.matchDataType(dataType)) {
                result.add(name);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public void askForValidation(final int metadataID) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findById(metadataID);
        if (metadata != null) {
            final String validationState;
            if (metadata.getValidatedState() == null) {
                validationState = getMetadataXMLFromStore(metadata.getProviderId(), metadata.getMetadataId());
            } else {
                validationState = metadata.getValidatedState();
            }
            metadataRepository.setValidationRequired(metadataID, "REQUIRED", validationState);
        }
    }

    @Override
    @Transactional
    public void askForValidation(final List<Integer> ids, final String metadataLink, final boolean sendEmails) throws ConfigurationException {
        if(ids != null) {
            for(final Integer id : ids) {
                askForValidation(id);
            }
        }
    }

    @Override
    @Transactional
    public void denyValidation(final int metadataID, final String comment) {
        metadataRepository.denyValidation(metadataID, comment);
    }

    @Override
    @Transactional
    public void denyValidation(final MetadataBrief metadata, final String comment, final String metadataLink) {
        metadataRepository.denyValidation(metadata.getId(), comment);
    }

    @Override
    @Transactional
    public void acceptValidation(final int metadataID) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findById(metadataID);
        if (metadata != null) {
            metadataRepository.changeValidation(metadataID, true);
            metadataRepository.setValidationRequired(metadataID, "NONE", getMetadataXMLFromStore(metadata.getProviderId(), metadata.getMetadataId()));
        }
    }

    @Override
    @Transactional
    public void acceptValidation(final MetadataBrief metadata, final String metadataLink) throws ConfigurationException {
        if (metadata != null) {
            metadataRepository.changeValidation(metadata.getId(), true);
            metadataRepository.setValidationRequired(metadata.getId(), "NONE", getMetadataXMLFromStore(metadata.getProviderId(), metadata.getFileIdentifier()));
        }
    }

    @Override
    public Map.Entry<Integer, List<MetadataBrief>> filterAndGetBrief(final Map<String,Object> filterMap, final Map.Entry<String,String> sortEntry,final int pageNumber,final int rowsPerPage) {
        Map.Entry<Integer, List<Metadata>> entry = metadataRepository.filterAndGet(filterMap, sortEntry, pageNumber, rowsPerPage);
        List<MetadataBrief> results = new ArrayList<>();
        final List<Metadata> metadataList = entry.getValue();
        if (metadataList != null) {
            for (final Metadata md : metadataList) {
                results.add(convertToMetadataBrief(md));
            }
        }
        return new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), results);
    }

    @Override
    public List<MetadataLightBrief> filterAndGetWithoutPagination(final Map<String,Object> filterMap) {
        final List<MetadataLightBrief> list = new ArrayList<>();
        final List<Map<String,Object>> map = metadataRepository.filterAndGetWithoutPagination(filterMap);
        if (map != null) {
            for (final Map entry : map) {
                list.add(new MetadataLightBrief((Integer)entry.get("id"),
                                                (String)entry.get("title"),
                                                (String)entry.get("profile")));
            }
        }
        return list;
    }

    @Override
    public List<OwnerStatBrief> getOwnerStatBriefs(final Map<String, Object> filter) {
        final List<OwnerStatBrief> briefs = new ArrayList<>();
        for (CstlUser user : userBusiness.findAll()) {
            filter.put("owner", user.getId());
            final Map<String,Object> reqFilter = new HashMap<>();
            reqFilter.putAll(filter);
            reqFilter.put("validation_required","REQUIRED");
            final int toValidate = metadataRepository.countValidated(false, reqFilter);
            final Map<String,Object> toPublishFilter = new HashMap<>();
            toPublishFilter.putAll(filter);
            toPublishFilter.put("validated",Boolean.TRUE);
            final int toPublish  = metadataRepository.countPublished(false, toPublishFilter);
            final int published  = metadataRepository.countPublished(true, filter);
            final User userBrief = Util.copy(user, new User());
            briefs.add(new OwnerStatBrief(userBrief, toValidate, toPublish, published));
        }
        return briefs;
    }

    @Override
    public List<GroupStatBrief> getGroupStatBriefs(final Map<String, Object> filter) {
        return new ArrayList<>();
    }

    @Override
    public List<User> getUsers() {
        final List<User> results = new ArrayList<>();
        for (CstlUser u : userBusiness.findAll()) {
            results.add(Util.copy(u, new User()));
        }
        return results;
    }

    @Override
    public User getUser(final int id) {
        final Optional<CstlUser> optUser = userBusiness.findById(id);
        User owner = null;
        if (optUser != null && optUser.isPresent()) {
            final CstlUser user = optUser.get();
            if (user != null) {
                owner = Util.copy(user, new User());
            }
        }
        return owner;
    }

    @Override
    public Object getMetadataFromFile(Path metadataFile) throws ConfigurationException {
        Object obj = getMetadataFromSpecialFormat(metadataFile);
        if (obj == null) {
            obj = unmarshallMetadata(metadataFile);
        }
        return obj;
    }

    @Override
    public boolean isSpecialMetadataFormat(Path metadataFile) {
        return IOUtilities.extension(metadataFile).equalsIgnoreCase("dim");
    }

    @Override
    public Object getMetadataFromSpecialFormat(Path metadataFile) throws ConfigurationException {
        if (isSpecialMetadataFormat(metadataFile)) {
            try {
                Document doc = DomUtilities.read(metadataFile);
                final DefaultMetadata metadata = DimapAccessor.fillMetadata(doc.getDocumentElement(), null);
                return metadata;
            } catch (ParserConfigurationException | SAXException  | IOException ex) {
                throw new ConfigurationException("Error while parsing dimap file", ex);
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public org.constellation.dto.metadata.Attachment getMetadataAttachment(int attachmentID) {
        return attachmentRepository.findById(attachmentID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Attachment> getMetadataAttachmentByFileName(String fileName) {
        return attachmentRepository.findByFileName(fileName);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @Transactional
    public int createMetadataAttachment(final InputStream stream, final String fileName) throws ConfigurationException {
        if(stream==null) throw new ConfigurationException("Missing attachment file data.");

        //read image stream, reencode it in png
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] imgData;
        try {
            IOUtilities.copy(stream, out);
            imgData = out.toByteArray();
        } catch (IOException ex) {
            throw new ConfigurationException(ex.getMessage(),ex);
        }

        final Attachment att = new Attachment();
        att.setContent(imgData);
        att.setFilename(fileName);
        return attachmentRepository.create(att);
    }

    @Override
    @Transactional
    public void linkMetadataAtachment(int metadataID, int attchmentId) {
        attachmentRepository.linkMetadataAndAttachment(metadataID, attchmentId);
    }

    @Override
    @Transactional
    public void unlinkMetadataAtachment(int metadataID, int attchmentId) {
        attachmentRepository.unlinkMetadataAndAttachment(metadataID, attchmentId);
    }

    @Override
    @Transactional
    public int addMetadataAtachment(int metadataID, URI path, String fileName) {
        final Attachment att = new Attachment();
        att.setUri(path.toString());
        att.setFilename(fileName);
        final int attId = attachmentRepository.create(att);
        attachmentRepository.linkMetadataAndAttachment(metadataID, attId);
        return attId;
    }

    @Override
    @Transactional
    public int addMetadataAtachment(int metadataID, InputStream contentStream, String fileName) throws ConfigurationException {
        final Attachment att = new Attachment();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] dataContent;
        try {
            IOUtilities.copy(contentStream, out);
        } catch (IOException ex) {
            throw new ConfigurationException(ex.getMessage(),ex);
        }
        dataContent = out.toByteArray();
        att.setContent(dataContent);
        att.setFilename(fileName);
        final int attId = attachmentRepository.create(att);
        attachmentRepository.linkMetadataAndAttachment(metadataID, attId);
        return attId;
    }

    /**
     * Return true if the specified CSW implementation handle internal metadata.
     * @param implementation
     * @return
     */
    protected boolean isInternalCSW(String implementation) {
        return "internal".equals(implementation);
    }

    private String getMetadataXMLFromStore(final int providerId, final String metadataId) throws ConfigurationException {
        final DataProvider provider = DataProviders.getProvider(providerId);
        final org.constellation.provider.Data data = provider.get(NamesExt.create(metadataId));
        if (data instanceof MetadataData) {
            final MetadataData md = (MetadataData) data;
            try {
                final Node n = md.getMetadata();
                return NodeUtilities.getStringFromNode(n);
            } catch (TransformerException ex) {
                throw new ConfigurationException(ex);
            }
        } else {
            throw new ConfigurationException("The provider " + providerId + " is not a metadata provider");
        }
    }

    @Override
    public String getMetadataXml(int id) throws ConfigurationException {
        final Metadata metadata = metadataRepository.findById(id);
        if (metadata != null) {
            return getMetadataXMLFromStore(metadata.getProviderId(), metadata.getMetadataId());
        }
        return null;
    }

    @Override
     public Integer getDefaultInternalProviderID() throws ConfigurationException {
        Integer providerId = providerBusiness.getIDFromIdentifier("default-internal-metadata");
        if (providerId == null) {
            final DataStoreProvider factory = DataStores.getProviderById("InternalCstlmetadata");
            if (factory != null) {
                ParameterValueGroup params = factory.getOpenParameters().createValue();
                providerId = providerBusiness.create("default-internal-metadata", IProviderBusiness.SPI_NAMES.METADATA_SPI_NAME, params);
                if (providerId == null) {
                    LOGGER.warning("Fail to create default internal metadata provider");
                }
            } else {
                LOGGER.warning("Fail to create default internal metadata provider: no factory found");
            }
        }
        return providerId;
    }

    @Override
    public String getJsonDatasetMetadata(int datasetId, boolean prune, boolean override) throws ConstellationException {
        final Metadata metadata = metadataRepository.findByDatasetId(datasetId);
        if (metadata != null) {
            Object isoMetadata = getMetadata(metadata.getId());
            final Template template = resolveTemplateByName(metadata.getProfile());
            final StringWriter sw = new StringWriter();
            try {
                template.write(isoMetadata, sw, prune, override);
            } catch (IOException ex) {
                throw new ConstellationException(ex);
            }
            return sw.toString();
        }
        return null;
    }

    @Override
    public String getJsonDataMetadata(int dataId, boolean prune, boolean override) throws ConstellationException {
        List<Metadata> metadatas = metadataRepository.findByDataId(dataId);

        // try the dataset metadata if not present for data
        if (metadatas.isEmpty()) {
            Integer datasetId = dataRepository.getDatasetId(dataId);
            if (datasetId != null) {
                metadatas.add(metadataRepository.findByDatasetId(datasetId));
            }
        }

        Object isoMetadata = null;
        Metadata metadata  = null;
        for (Metadata m : metadatas) {
            Object o  = getMetadata(m.getId());
            // HACK for now we want only the ISO 19115 metadata
            if (o instanceof DefaultMetadata) {
                isoMetadata = o;
                metadata = m;
            }
        }

        if (metadata != null) {
            final Template template = resolveTemplateByName(metadata.getProfile());
            final StringWriter sw = new StringWriter();
            try {
                template.write(isoMetadata, sw, prune, override);
            } catch (IOException ex) {
                throw new ConstellationException(ex);
            }
            return sw.toString();
        }
        return null;
    }

    @Override
    @Transactional
    public void mergeDataMetadata(final int dataId, final RootObj metadataValues) throws ConstellationException {
        List<Metadata> metadatas = metadataRepository.findByDataId(dataId);

        Object isoMetadata = null;
        Metadata metadata  = null;

        for (Metadata m : metadatas) {
            Object o  = getMetadata(m.getId());
            // HACK for now we want only the ISO 19115 metadata
            if (o instanceof DefaultMetadata) {
                isoMetadata = o;
                metadata = m;
            }
        }

        if (metadata != null) {
            //get template name
            final Template template = resolveTemplateByName(metadata.getProfile());
            try{
                template.read(metadataValues, isoMetadata, false);
            }catch(IOException ex){
                LOGGER.log(Level.WARNING, "error while saving metadata.", ex);
                throw new ConstellationException(ex);
            }

            //update dateStamp for metadata
            if (isoMetadata instanceof DefaultMetadata) {
                ((DefaultMetadata)isoMetadata).setDateStamp(new Date());
            }
            final String metadataID = Utils.findIdentifier(isoMetadata);

            // save merged metadata
            updateMetadata(metadataID, isoMetadata, dataId, null, null, null, null, "DOC", null, metadata.getIsHidden());

        // look on dataset
        } else {
            Integer datasetId = dataRepository.getDatasetId(dataId);
            mergeDatasetMetadata(datasetId, metadataValues);
        }
    }

    @Override
    @Transactional
    public void mergeDatasetMetadata(final int datasetId, final RootObj metadataValues) throws ConstellationException {
        final Metadata metadata = metadataRepository.findByDatasetId(datasetId);
        if (metadata != null) {

            Object isoMetadata = getMetadata(metadata.getId());

            //get template name
            final Template template = resolveTemplateByName(metadata.getProfile());
            try{
                template.read(metadataValues, isoMetadata, false);
            }catch(IOException ex){
                LOGGER.log(Level.WARNING, "error while saving metadata.", ex);
                throw new ConstellationException(ex);
            }

            //update dateStamp for metadata
            if (isoMetadata instanceof DefaultMetadata) {
                ((DefaultMetadata)isoMetadata).setDateStamp(new Date());
            }
            final String metadataID = Utils.findIdentifier(isoMetadata);

            // save merged metadata
            updateMetadata(metadataID, isoMetadata, null, datasetId, null, null, null, null, null, metadata.getIsHidden());
        }
    }

    private Template resolveTemplateByName(String templateName) {
        try {
            return templateResolver.getByName(templateName);
        } catch (ConfigurationException e) {
            LOGGER.warning("Template named " + templateName + " not found, use fallback");
        }
        return templateResolver.getFallbackTemplate();
    }

     /**
     * Utility method to convert a metadata db model to ui model.
     *
     * @param md given metadata from data base model to convert.
     * @return {link MetadataBrief} converted pojo.
     */
    private MetadataBrief convertToMetadataBrief(final Metadata md) {
        if (md != null) {
            final MetadataBrief mdb = new MetadataBrief(md);
            if (md.getOwner() != null) {
                User owner = getUser(md.getOwner());
                mdb.setUser(owner);
            }
            if (md.getParentIdentifier()!= null) {
                Metadata parentMd = getMetadataById(md.getParentIdentifier());
                mdb.setParentFileIdentifier(parentMd.getMetadataId());
            }
            return mdb;
        }
        return null;
    }

    private MetadataLightBrief convertToMetadataLightBrief(final Metadata md) {
        if (md != null) {
            return new MetadataLightBrief(md.getId(), md.getTitle(), md.getProfile());
        }
        return null;
    }

    private List<MetadataBrief> convertToMetadataBriefList(final List<Metadata> mdList) {
        final List<MetadataBrief> mds = new ArrayList<>();
        for (Metadata md : mdList) {
            mds.add(convertToMetadataBrief(md));
        }
        return mds;
    }

    @Override
    public Map<String, Integer> getStats(Map<String, Object> filterMap) {
        Map<String,Integer> general = new HashMap<>();

        final int total             = countTotal(filterMap);
        final int validated         = countValidated(true,filterMap);
        final int notValid          = countValidated(false, filterMap);
        final Map<String,Object> filter = new HashMap<>();
        filter.putAll(filterMap);
        filter.put("validation_required","REQUIRED");
        final int waitingToValidate = countValidated(false, filter);
        final int notPublish        = countPublished(false, filterMap);
        final int published         = countPublished(true,filterMap);
        final Map<String, Object> filter2 = new HashMap<>();
        filter2.putAll(filterMap);
        filter2.put("validated",Boolean.TRUE);
        final int waitingToPublish  = countPublished(false,  filter2);

        general.put("total", total);
        general.put("validated", validated);
        general.put("notValid", notValid);
        general.put("waitingToValidate", waitingToValidate);
        general.put("notPublish", notPublish);
        general.put("published", published);
        general.put("waitingToPublish", waitingToPublish);

        return general;
    }

    @Override
    @Transactional
    public void linkMetadataData(int metadataID, int dataId) {
        metadataRepository.linkMetadataData(metadataID, dataId);
    }

    @Override
    @Transactional
    public void unlinkMetadataData(int metadataID, int dataId) {
        // does we need data ID ?
        metadataRepository.unlinkMetadataData(metadataID);
    }

    @Override
    @Transactional
    public void linkMetadataDataset(int metadataID, int datasetId) {
        metadataRepository.linkMetadataDataset(metadataID, datasetId);
    }

    @Override
    @Transactional
    public void unlinkMetadataDataset(int metadataID, int datasetId) {
        // does we need dataset ID ?
        metadataRepository.unlinkMetadataDataset(metadataID);
    }

    @Override
    @Transactional
    public void linkMetadataMapContext(int metadataID, int contextId) {
        metadataRepository.linkMetadataMapContext(metadataID, contextId);
    }

    @Override
    @Transactional
    public void unlinkMetadataMapContext(int metadataID, int contextId) {
        // does we need context ID ?
        metadataRepository.unlinkMetadataMapContext(metadataID);
    }
}
