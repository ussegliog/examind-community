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
package org.constellation.metadata.core;

import com.codahale.metrics.annotation.Timed;
import org.apache.sis.util.logging.MonolineFormatter;
import org.apache.sis.xml.MarshallerPool;
import org.apache.sis.xml.Namespaces;
import org.apache.sis.internal.xml.LegacyNamespaces;
import org.constellation.api.ServiceDef;
import org.constellation.configuration.ConfigDirectory;
import org.constellation.exception.ConfigurationException;
import org.constellation.dto.contact.Details;
import org.constellation.filter.FilterParser;
import org.constellation.filter.FilterParserException;
import org.constellation.filter.SQLQuery;
import org.constellation.dto.service.config.generic.Automatic;
import org.constellation.metadata.harvest.CatalogueHarvester;
import org.geotoolkit.metadata.MetadataIoException;
import org.geotoolkit.metadata.MetadataType;
import org.constellation.metadata.security.MetadataSecurityFilter;
import org.constellation.metadata.utils.CSWUtils;
import org.constellation.security.SecurityManagerHolder;
import org.constellation.util.Util;
import org.constellation.ws.AbstractWorker;
import org.constellation.ws.CstlServiceException;
import org.constellation.ws.UnauthorizedException;
import org.geotoolkit.csw.xml.AbstractCapabilities;
import org.geotoolkit.csw.xml.Acknowledgement;
import org.geotoolkit.csw.xml.CSWMarshallerPool;
import org.geotoolkit.csw.xml.CswXmlFactory;
import org.geotoolkit.csw.xml.Delete;
import org.geotoolkit.csw.xml.DescribeRecord;
import org.geotoolkit.csw.xml.DescribeRecordResponse;
import org.geotoolkit.csw.xml.DistributedSearch;
import org.geotoolkit.csw.xml.DomainValues;
import org.geotoolkit.csw.xml.ElementSetName;
import org.geotoolkit.csw.xml.ElementSetType;
import org.geotoolkit.csw.xml.GetCapabilities;
import org.geotoolkit.csw.xml.GetDomain;
import org.geotoolkit.csw.xml.GetDomainResponse;
import org.geotoolkit.csw.xml.GetRecordById;
import org.geotoolkit.csw.xml.GetRecordByIdResponse;
import org.geotoolkit.csw.xml.GetRecordsRequest;
import org.geotoolkit.csw.xml.Harvest;
import org.geotoolkit.csw.xml.HarvestResponse;
import org.geotoolkit.csw.xml.Insert;
import org.geotoolkit.csw.xml.Query;
import org.geotoolkit.csw.xml.ResultType;
import org.geotoolkit.csw.xml.SchemaComponent;
import org.geotoolkit.csw.xml.SearchResults;
import org.geotoolkit.csw.xml.Transaction;
import org.geotoolkit.csw.xml.TransactionResponse;
import org.geotoolkit.csw.xml.TransactionSummary;
import org.geotoolkit.csw.xml.Update;
import org.geotoolkit.ebrim.xml.EBRIMMarshallerPool;
import org.geotoolkit.inspire.xml.InspireCapabilitiesType;
import org.geotoolkit.inspire.xml.MultiLingualCapabilities;
import org.geotoolkit.index.IndexingException;
import org.geotoolkit.index.SearchingException;
import org.geotoolkit.index.SpatialQuery;
import org.constellation.metadata.index.Indexer;
import org.constellation.metadata.index.IndexSearcher;
import org.geotoolkit.ogc.xml.SortBy;
import org.geotoolkit.ows.xml.AbstractCapabilitiesCore;
import org.geotoolkit.ows.xml.AbstractDomain;
import org.geotoolkit.ows.xml.AbstractOperation;
import org.geotoolkit.ows.xml.AbstractOperationsMetadata;
import org.geotoolkit.ows.xml.AbstractServiceIdentification;
import org.geotoolkit.ows.xml.AbstractServiceProvider;
import org.geotoolkit.ows.xml.Sections;
import org.geotoolkit.ows.xml.v100.SectionsType;
import org.apache.sis.storage.DataStore;
import org.geotoolkit.util.StringUtilities;
import org.geotoolkit.xml.AnchoredMarshallerPool;
import org.geotoolkit.xsd.xml.v2001.XSDMarshallerPool;
import org.opengis.filter.capability.FilterCapabilities;
import org.opengis.filter.sort.SortOrder;
import org.opengis.util.CodeList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.w3c.dom.Node;

import javax.inject.Named;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import org.apache.sis.storage.DataStoreException;

import static org.constellation.api.QueryConstants.SERVICE_PARAMETER;
import org.constellation.business.IClusterBusiness;
import org.constellation.business.IMetadataBusiness;
import static org.constellation.metadata.core.CSWConstants.ALL;
import static org.constellation.metadata.core.CSWConstants.CSW;
import static org.constellation.metadata.core.CSWConstants.CSW_FILTER_CAPABILITIES;
import static org.constellation.metadata.core.CSWConstants.EBRIM_25;
import static org.constellation.metadata.core.CSWConstants.EBRIM_30;
import static org.constellation.metadata.core.CSWConstants.FILTER_CAPABILITIES;
import static org.constellation.metadata.core.CSWConstants.OPERATIONS_METADATA;
import static org.constellation.metadata.core.CSWConstants.OUTPUT_SCHEMA;
import static org.constellation.metadata.core.CSWConstants.PARAMETERNAME;
import static org.constellation.metadata.core.CSWConstants.SOURCE;
import static org.constellation.metadata.core.CSWConstants.TRANSACTION_TYPE;
import static org.constellation.metadata.core.CSWConstants.TYPENAMES;
import static org.constellation.metadata.CSWQueryable.DUBLIN_CORE_QUERYABLE;
import static org.constellation.metadata.CSWQueryable.ISO_QUERYABLE;
import org.constellation.metadata.legacy.MetadataConfigurationUpgrade;
import org.constellation.metadata.utils.Utils;
import org.constellation.provider.DataProviders;
import org.constellation.ws.MimeType;
import org.geotoolkit.metadata.MetadataStore;
import org.constellation.ws.Refreshable;
import org.geotoolkit.csw.xml.FederatedSearchResultBase;
import org.geotoolkit.csw.xml.InsertResult;
import static org.geotoolkit.csw.xml.ResultType.HITS;
import static org.geotoolkit.metadata.TypeNames.EXTRINSIC_OBJECT_25_QNAME;
import static org.geotoolkit.metadata.TypeNames.EXTRINSIC_OBJECT_QNAME;
import static org.geotoolkit.metadata.TypeNames.METADATA_QNAME;
import static org.geotoolkit.metadata.TypeNames.containsOneOfEbrim25;
import static org.geotoolkit.metadata.TypeNames.containsOneOfEbrim30;
import static org.geotoolkit.ows.xml.OWSExceptionCode.INVALID_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.MISSING_PARAMETER_VALUE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.NO_APPLICABLE_CODE;
import static org.geotoolkit.ows.xml.OWSExceptionCode.OPERATION_NOT_SUPPORTED;
import static org.geotoolkit.ows.xml.OWSExceptionCode.VERSION_NEGOTIATION_FAILED;
import org.geotoolkit.ows.xml.RequestBase;
import org.springframework.beans.factory.annotation.Autowired;
import static org.geotoolkit.metadata.TypeNames.RECORD_202_QNAME;
import static org.geotoolkit.metadata.TypeNames.CAPABILITIES_202_QNAME;
import static org.geotoolkit.metadata.TypeNames.CAPABILITIES_300_QNAME;
import static org.geotoolkit.metadata.TypeNames.RECORD_300_QNAME;
import org.geotoolkit.metadata.RecordInfo;
import static org.geotoolkit.metadata.TypeNames.DIF_QNAME;
import org.geotoolkit.ops.xml.OpenSearchXmlFactory;
import org.w3._2005.atom.EntryType;
import org.w3._2005.atom.FeedType;


/**
 * The CSW (Catalog Service Web) engine.
 *
 * @author Guilhem Legal (Geomatys)
 */
@Named("CSWWorker")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class CSWworker extends AbstractWorker implements Refreshable {

    @Autowired
    private IMetadataBusiness metadataBusiness;

    @Autowired
    private IClusterBusiness clusterBusiness;

    private Integer providerId;

    /**
     * A Database reader.
     */
    private MetadataStore mdStore;

    /**
     * A index searcher to make quick search on the metadatas.
     */
    private IndexSearcher indexSearcher;

    private Indexer indexer;

    /**
     * A filter parser which create index query from OGC filter
     */
    private FilterParser filterParser;

    /**
     * A filter parser which create SQL query from OGC filter (used for ebrim query)
     */
    private FilterParser sqlFilterParser;

    /**
     * A catalogue Harvester communicating with other CSW
     */
    private CatalogueHarvester catalogueHarvester;

    /**
     * A task scheduler for asynchronous harvest.
     */
    private HarvestTaskScheduler harvestTaskScheduler;

    /**
     * A list of the supported Type name
     */
    private List<QName> supportedTypeNames;

    /**
     * A list of the supported SchemaLanguage for describeRecord Operation
     */
    private List<String> supportedSchemaLanguage;

    /**
     * A map of QName - xsd schema object
     */
    private final Map<QName, Object> schemas = new HashMap<>();

    /**
     * A list of supported resource type.
     */
    private List<String> acceptedResourceType;

    /**
     * A list of known CSW server used in distributed search.
     */
    private List<String> cascadedCSWservers;

    public static final  int DISCOVERY    = 0;
    public static final int TRANSACTIONAL = 1;

    /**
     * A flag indicating if the service have to support Transactional operations.
     */
    private int profile;

    private MetadataSecurityFilter securityFilter;

    private Automatic configuration;

    @Autowired
    @Qualifier(value = "indexConfigHandler")
    protected IndexConfigHandler indexHandler;

    /**
     * Build a new CSW worker with the specified configuration directory
     *
     * @param serviceID The service identifier (used in multiple CSW context). default value is "".
     */
    public CSWworker(final String serviceID) {
        super(serviceID, ServiceDef.Specification.CSW);
        isStarted = true;
        try {
            //we look if the configuration have been specified
            final Object obj = serviceBusiness.getConfiguration("csw", serviceID);
            if (obj instanceof Automatic) {
                configuration = (Automatic) obj;
            } else {
                startError = " Configuration Object is not an Automatic Object";
                LOGGER.log(Level.WARNING, "\nThe CSW worker is not working!\nCause:{0}", startError);
                isStarted = false;
                return;
            }

            // legacy
            MetadataConfigurationUpgrade upgrader = new MetadataConfigurationUpgrade();
            upgrader.upgradeConfiguration(serviceID);

            // we initialize the filterParsers
            init();
            String suffix = "";
            if (profile == TRANSACTIONAL) {
                suffix = "-T";
            }
            applySupportedVersion();

            LOGGER.info("CSW" + suffix + " worker \"" + serviceID + "\" running\n");

        } catch (MetadataIoException | IndexingException e) {
            startError = e.getMessage();
            Throwable tr = e.getCause();
            while (tr != null) {
                startError = startError + "\n" + tr.getMessage();
                tr = tr.getCause();
            }
            LOGGER.log(Level.WARNING, "\nThe CSW worker is not working!\nCause:{0}\n", startError);
            isStarted = false;
        } catch (IllegalArgumentException | IllegalStateException | JAXBException e) {
            startError = e.getLocalizedMessage();
            LOGGER.log(Level.WARNING, "\nThe CSW worker is not working!\nCause: {0}\n", startError);
            LOGGER.log(Level.WARNING, e.getLocalizedMessage(), e);
            isStarted = false;
        } catch (CstlServiceException e) {
            startError = e.getLocalizedMessage();
            LOGGER.log(Level.WARNING, "\nThe CSW worker is not working!\nCause: CstlServiceException: {0}\n", startError);
            LOGGER.log(Level.FINER, e.getLocalizedMessage(), e);
            isStarted = false;
        } catch (ConfigurationException ex) {
            if (ex.getMessage() == null) {
                startError = "The configuration file has not been found";
            } else {
                startError = ex.getMessage();
            }
            LOGGER.log(Level.WARNING, "\nThe CSW worker( {0}) is not working!\nCause: " + startError, serviceID);
            isStarted = false;
        } catch (Throwable ex) {
            LOGGER.log(Level.WARNING, "\nThe CSW worker( {0}) is not working!\nCause: "+ex.getLocalizedMessage(), serviceID);
            LOGGER.log(Level.WARNING, ex.getLocalizedMessage(), ex);
            isStarted = false;
        }
    }

    /**
     * Initialize the readers and indexSearcher to the dataSource for the discovery profile.
     * If The transactional part is enabled, it also initialize Writer and catalog harvester.
     *
     * @throws MetadataIoException If an error occurs while querying the dataSource.
     * @throws IndexingException If an error occurs while initializing the indexation.
     */
    private void init() throws MetadataIoException, IndexingException, JAXBException, CstlServiceException, ConfigurationException {

        // we assign the configuration directory
        final Path configDir = ConfigDirectory.getInstanceDirectory("csw", getId());
        configuration.setConfigurationDirectory(configDir);

        //we initialize all the data retriever (reader/writer) and index worker
        providerId = serviceBusiness.getCSWLinkedProviders(getId());
        if (providerId == null) {
            throw new ConfigurationException("No linked metadata Provider");
        }
        final DataStore ds = DataProviders.getProvider(providerId).getMainStore();
        if (!(ds instanceof MetadataStore)) {
            throw new ConfigurationException("Linked metadata provider is not a Metadata store");
        }
        final MetadataStore originalStore = (MetadataStore) DataProviders.getProvider(providerId).getMainStore();
        mdStore = new MetadataStoreWrapper(getId(), originalStore, configuration.getCustomparameters(), providerId);

        profile = configuration.getProfile();
        Lock lock = clusterBusiness.acquireLock("csw-indexation-" + getId());
        lock.lock();
        LOGGER.fine("LOCK Acquired on cluster: csw-indexation-" + getId());
        try {
            indexer = indexHandler.getIndexer(configuration, mdStore, getId());
            if (indexer.needCreation()) {
                try {
                    indexer.createIndex();
                } catch (Exception ex) {
                    indexer.destroy();
                    throw ex;
                }
            }
        } finally {
            LOGGER.fine("UNLOCK on cluster: csw-indexation-" + getId());
            lock.unlock();
        }
        indexSearcher                 = indexHandler.getIndexSearcher(configuration, getId());
        filterParser                  = indexHandler.getFilterParser(configuration);
        sqlFilterParser               = indexHandler.getSQLFilterParser(configuration);
        securityFilter                = indexHandler.getSecurityFilter();
        if (profile == TRANSACTIONAL) {
            catalogueHarvester        = indexHandler.getCatalogueHarvester(configuration, mdStore);
            harvestTaskScheduler      = new HarvestTaskScheduler(configDir, catalogueHarvester);
        } else {
            indexer.destroy();
        }
        initializeSupportedMetadataTypes();
        initializeSupportedSchemaLanguage();
        initializeRecordSchema();
        initializeAnchorsMap();
        loadCascadedService();
    }

    /**
     * Initialize the supported type names in function of the reader capacity.
     */
    private void initializeSupportedMetadataTypes() {
        supportedTypeNames    = new ArrayList<>();
        acceptedResourceType = new ArrayList<>();

        final List<MetadataType> supportedDataTypes = mdStore.getSupportedDataTypes();
        for (MetadataType metaType : supportedDataTypes) {
            supportedTypeNames.addAll(metaType.typeNames);
            acceptedResourceType.add(metaType.namespace);
        }
    }

    /**
     * Initialize the supported outputSchema in function of the reader capacity.
     */
    private void initializeSupportedSchemaLanguage() {
        supportedSchemaLanguage = new ArrayList<>();
        supportedSchemaLanguage.add("http://www.w3.org/XML/Schema");
        supportedSchemaLanguage.add("XMLSCHEMA");
        supportedSchemaLanguage.add("http://www.w3.org/TR/xmlschema-1/");
    }

    /**
     * Load from the resource the XSD schemas used for the response of describeRecord.
     *
     * @throws CstlServiceException if there is a JAXBException while using the unmarshaller.
     */
    private void initializeRecordSchema() throws CstlServiceException {
        try {
            final Unmarshaller unmarshaller = XSDMarshallerPool.getInstance().acquireUnmarshaller();
            schemas.put(RECORD_202_QNAME,          unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/metadata/record.xsd")));
            schemas.put(METADATA_QNAME,            unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/metadata/metadata.xsd")));
            schemas.put(EXTRINSIC_OBJECT_QNAME,    unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/metadata/ebrim-3.0.xsd")));
            schemas.put(EXTRINSIC_OBJECT_25_QNAME, unmarshaller.unmarshal(Util.getResourceAsStream("org/constellation/metadata/ebrim-2.5.xsd")));
            XSDMarshallerPool.getInstance().recycle(unmarshaller);

        } catch (JAXBException ex) {
            throw new CstlServiceException("JAXB Exception when trying to parse xsd file", ex, NO_APPLICABLE_CODE);
        }
    }

    /**
     * Initialize the Anchors in function of the reader capacity.
     */
    private void initializeAnchorsMap() throws JAXBException {
        if (EBRIMMarshallerPool.getInstance() instanceof AnchoredMarshallerPool) {
            final AnchoredMarshallerPool pool = (AnchoredMarshallerPool) EBRIMMarshallerPool.getInstance();
            final Map<String, URI> concepts = mdStore.getConceptMap();
            int nbWord = 0;
            for (Entry<String, URI> entry: concepts.entrySet()) {
                pool.addAnchor(entry.getKey(),entry.getValue());
                nbWord ++;
            }
            if (nbWord > 0) {
                LOGGER.log(Level.INFO, "{0} words put in pool.", nbWord);
            }
        } else {
            LOGGER.severe("NOT an anchoredMarshaller Pool");
        }
    }

    /**
     * Load the federated CSW server from a properties file.
     */
    private void loadCascadedService() {
        cascadedCSWservers = configuration.getParameterList("CSWCascading");
        if (!cascadedCSWservers.isEmpty()) {
            final StringBuilder s = new StringBuilder("Cascaded Services:\n");
            for (String servURL : cascadedCSWservers) {
                s.append(servURL).append('\n');
            }
            LOGGER.info(s.toString());
        } else  {
            LOGGER.info("no cascaded CSW server found (optionnal)");
        }
    }

    public void setCascadedService(final List<String> urls) throws CstlServiceException {
        cascadedCSWservers = urls;
        final StringBuilder s = new StringBuilder("Cascaded Services:\n");
        for (String servURL: urls) {
            s.append(servURL).append('\n');
        }
        LOGGER.info(s.toString());
    }

    /**
     * Web service operation describing the service and its capabilities.
     *
     * @param request A document specifying the section you would obtain like :
     *      ServiceIdentification, ServiceProvider, Contents, operationMetadata.
     *
     * @return the capabilities document
     */
    @Timed
    public AbstractCapabilities getCapabilities(final GetCapabilities request) throws CstlServiceException {
        isWorking();
        LOGGER.log(Level.INFO, "getCapabilities request processing\n");
        final long startTime = System.currentTimeMillis();

        //we verify the base request attribute
        verifyBaseRequest(request, false, true);

        final String currentVersion = request.getVersion().toString();

        Sections sections = request.getSections();
        if (sections == null) {
            sections = new SectionsType(ALL);
        }
        //according to CITE test a GetCapabilities must always return Filter_Capabilities
        if (!sections.containsSection(FILTER_CAPABILITIES) || sections.containsSection(ALL)) {
            sections.add(FILTER_CAPABILITIES);
        }

        //set the current updateSequence parameter
        final boolean returnUS = returnUpdateSequenceDocument(request.getUpdateSequence());
        if (returnUS) {
            return CswXmlFactory.createCapabilities(currentVersion, getCurrentUpdateSequence());
        }

        final AbstractCapabilitiesCore cachedCapabilities = getCapabilitiesFromCache(currentVersion, null);
        if (cachedCapabilities != null) {
            return (AbstractCapabilities) cachedCapabilities.applySections(sections);
        }

        /*
         final AcceptFormats formats = requestCapabilities.getAcceptFormats();

         if (formats != null && formats.getOutputFormat().size() > 0 && !formats.getOutputFormat().contains(MimeType.TEXT_XML)) {

             * Acording to the CITE test this case does not return an exception
             throw new OWSWebServiceException("accepted format : text/xml",
                                             INVALID_PARAMETER_VALUE, "acceptFormats",
                                             version);
        }
        */

        // we load the skeleton capabilities
        final Details skeleton = getStaticCapabilitiesObject("csw", null);
        final AbstractCapabilities skeletonCapabilities = CSWConstants.createCapabilities(currentVersion, skeleton);

        // verification (should not happen in normal cases)
        if (!CSWConstants.OPERATIONS_METADATA.containsKey(currentVersion)) {
            throw new CstlServiceException("Missing operation metadata for version: " + currentVersion);
        }
        if (!CSWConstants.CSW_FILTER_CAPABILITIES.containsKey(currentVersion)) {
            throw new CstlServiceException("Missing filter capabilities for version: " + currentVersion);
        }

        //we prepare the response document
        final AbstractServiceIdentification si = skeletonCapabilities.getServiceIdentification();
        final AbstractServiceProvider       sp = skeletonCapabilities.getServiceProvider();
        final FilterCapabilities            fc = CSW_FILTER_CAPABILITIES.get(currentVersion);
        final AbstractOperationsMetadata   om  = CSWConstants.OPERATIONS_METADATA.get(currentVersion).clone();

        // we remove the operation not supported in this profile (transactional/discovery)
        if (profile == DISCOVERY) {
            om.removeOperation("Harvest");
            om.removeOperation("Transaction");
        }

        // we update the URL
        String serviceUrl = getServiceUrl();
        if (serviceUrl != null) {
            om.updateURL(serviceUrl);
            String osUrl = getOsUrl(serviceUrl);
            CSWUtils.updateOpensearchURL(om, osUrl);
        }

        // we add the cascaded services (if there is some)
        final AbstractDomain cascadedCSW  = om.getConstraint("FederatedCatalogues");
        if (cascadedCSW == null) {
            if (cascadedCSWservers != null && !cascadedCSWservers.isEmpty()) {
                final AbstractDomain fedCata = CswXmlFactory.createDomain(currentVersion,"FederatedCatalogues", cascadedCSWservers);
                om.addConstraint(fedCata);
            }
        } else {
            if (cascadedCSWservers != null && !cascadedCSWservers.isEmpty()) {
                cascadedCSW.setValue(cascadedCSWservers);
            } else {
                om.removeConstraint("FederatedCatalogues");
            }
        }

        // we update the operation parameters
        final AbstractOperation gr = om.getOperation("GetRecords");
        if (gr != null) {
            final AbstractDomain os = gr.getParameter(OUTPUT_SCHEMA);
            if (os != null) {
                os.setValue(acceptedResourceType);
            }
            final AbstractDomain tn = gr.getParameter(TYPENAMES);
            if (tn != null) {
                final List<String> values = new ArrayList<>();
                for (QName qn : supportedTypeNames) {
                    values.add(Namespaces.getPreferredPrefix(qn.getNamespaceURI(), "") + ':' + qn.getLocalPart());
                }
                tn.setValue(values);
            }

            //we update the ISO queryable elements :
            final AbstractDomain isoQueryable = gr.getConstraint("SupportedISOQueryables");
            if (isoQueryable != null) {
                final List<String> values = new ArrayList<>();
                for (String name : ISO_QUERYABLE.keySet() ) {
                    values.add("apiso:" + name);
                }
                isoQueryable.setValue(values);
            }
            //we update the DC queryable elements :
            final AbstractDomain dcQueryable = gr.getConstraint("SupportedDublinCoreQueryables");
            if (dcQueryable != null) {
                final List<String> values = new ArrayList<>();
                for (String name : DUBLIN_CORE_QUERYABLE.keySet() ) {
                    values.add("dc:" + name);
                }
                dcQueryable.setValue(values);
            }

            //we update the reader's additional queryable elements :
            final AbstractDomain additionalQueryable = gr.getConstraint("AdditionalQueryables");
            if (additionalQueryable != null) {
                final List<String> values = new ArrayList<>();
                for (QName name : mdStore.getAdditionalQueryableQName()) {
                    // allow to redefine the mapping in reader implementation
                    if (!ISO_QUERYABLE.containsKey(name.getLocalPart()) &&
                        !DUBLIN_CORE_QUERYABLE.containsKey(name.getLocalPart())) {
                        if (name.getPrefix() != null && !name.getPrefix().isEmpty()) {
                            values.add(name.getPrefix() + ':' + name.getLocalPart());
                        } else {
                            values.add(name.getLocalPart());
                        }
                    }
                }
                if (values.size() > 0) {
                    additionalQueryable.setValue(values);
                }
            }
        }

        final AbstractOperation grbi = om.getOperation("GetRecordById");
        if (grbi != null) {
            final AbstractDomain os = grbi.getParameter(OUTPUT_SCHEMA);
            if (os != null) {
                os.setValue(acceptedResourceType);
            }
        }

        final AbstractOperation dr = om.getOperation("DescribeRecord");
        if (dr != null) {
            final AbstractDomain tn = dr.getParameter("TypeName");
            if (tn != null) {
                final List<String> values = new ArrayList<>();
                for (QName qn : supportedTypeNames) {
                    values.add(Namespaces.getPreferredPrefix(qn.getNamespaceURI(), "") + ':' + qn.getLocalPart());
                }
                tn.setValue(values);
            }
            final AbstractDomain sl = dr.getParameter("SchemaLanguage");
            if (sl != null) {
                sl.setValue(supportedSchemaLanguage);
            }
        }

        final AbstractOperation hr = om.getOperation("Harvest");
        if (hr != null) {
            final AbstractDomain tn = hr.getParameter("ResourceType");
            if (tn != null) {
                tn.setValue(acceptedResourceType);
            }
        }

        final AbstractOperation tr = om.getOperation("Transaction");
        if (tr != null) {
            final AbstractDomain tn = tr.getParameter("ResourceType");
            if (tn != null) {
                tn.setValue(acceptedResourceType);
            }
        }

        //we add the INSPIRE extend capabilties
        final InspireCapabilitiesType inspireCapa = new InspireCapabilitiesType(Arrays.asList("FRA", "ENG"));
        final MultiLingualCapabilities m          = new MultiLingualCapabilities();
        m.setMultiLingualCapabilities(inspireCapa);
        om.setExtendedCapabilities(m);

        final AbstractCapabilities c = CswXmlFactory.createCapabilities(currentVersion, si, sp, om, null, fc);

        putCapabilitiesInCache(currentVersion, null, c);
        LOGGER.log(Level.INFO, "GetCapabilities request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return (AbstractCapabilities) c.applySections(sections);
    }

    private String getOsUrl(String serviceUrl) {
        return serviceUrl.substring(0, serviceUrl.length() - 1) + "/descriptionDocument.xml";
    }

    /**
     * Web service operation which permits to search the catalog to find records.
     *
     * @return A GetRecordsResponseType containing the result of the request or
     *         an AcknowledgementType if the resultType is set to VALIDATE.
     */
    public Object getRecords(final GetRecordsRequest request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "GetRecords request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);

        final String version   = request.getVersion().toString();
        final String id        = request.getRequestId();
        final String userLogin = getUserLogin();

        // verify the output format of the response
        final String outputFormat = CSWUtils.getOutputFormat(request);

        //we get the output schema and verify that we handle it
        final String outputSchema;
        if (request.getOutputSchema() != null) {
            outputSchema = request.getOutputSchema();
            if (!acceptedResourceType.contains(outputSchema)) {
                final StringBuilder supportedOutput = new StringBuilder();
                for (String s: acceptedResourceType) {
                    supportedOutput.append(s).append('\n');
                }
                throw new CstlServiceException("The server does not support this output schema: " + outputSchema + '\n' +
                                              " supported ones are: " + '\n' + supportedOutput,
                                              INVALID_PARAMETER_VALUE, OUTPUT_SCHEMA);
            }
        } else {
             outputSchema = LegacyNamespaces.CSW;
        }

        //We get the resultType
        ResultType resultType = ResultType.HITS;
        if (request.getResultType() != null) {
            resultType = request.getResultType();
        }

        //We initialize (and verify) the principal attribute of the query
        Query query;
        List<QName> typeNames;
        final Map<String, QName> variables = new HashMap<>();
        final Map<String, String> prefixs  = new HashMap<>();
        if (request.getAbstractQuery() != null) {
            query = (Query)request.getAbstractQuery();
            typeNames =  query.getTypeNames();
            if (typeNames == null || typeNames.isEmpty()) {
                throw new CstlServiceException("The query must specify at least typeName.",
                                              INVALID_PARAMETER_VALUE, TYPENAMES);
            } else {
                for (QName type : typeNames) {
                    if (type != null) {
                        prefixs.put(type.getPrefix(), type.getNamespaceURI());
                        //for ebrim mode the user can put variable after the Qname
                        if (type.getLocalPart().indexOf('_') != -1 && !(type.getLocalPart().startsWith("MD") || type.getLocalPart().startsWith("MI") || type.getLocalPart().startsWith("FC"))) {
                            final StringTokenizer tokenizer = new StringTokenizer(type.getLocalPart(), "_;");
                            type = new QName(type.getNamespaceURI(), tokenizer.nextToken());
                            while (tokenizer.hasMoreTokens()) {
                                variables.put(tokenizer.nextToken(), type);
                            }
                        }
                    } else {
                        throw new CstlServiceException("The service was unable to read a typeName:" +'\n' +
                                                       "supported one are:" + '\n' + supportedTypeNames(),
                                                       INVALID_PARAMETER_VALUE, TYPENAMES);
                    }
                    //we verify that the typeName is supported
                    if (!supportedTypeNames.contains(type)) {
                        throw new CstlServiceException("The typeName " + type.getLocalPart() + " is not supported by the service:" +'\n' +
                                                      "supported one are:" + '\n' + supportedTypeNames(),
                                                      INVALID_PARAMETER_VALUE, TYPENAMES);
                    }
                }
                /*
                 * debugging part
                 */
                final StringBuilder report = new StringBuilder("variables:").append('\n');
                for (Entry<String, QName> entry : variables.entrySet()) {
                    report.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
                }
                report.append("prefixs:").append('\n');
                for (Entry<String, String> entry : prefixs.entrySet()) {
                    report.append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
                }
                LOGGER.log(Level.FINER, report.toString());
            }

        } else {
            throw new CstlServiceException("The request must contains a query.",
                                          INVALID_PARAMETER_VALUE, "Query");
        }

        // we get the element set type (BRIEF, SUMMARY OR FULL) or the custom elementName
        final ElementSetName setName  = query.getElementSetName();
        ElementSetType set            = ElementSetType.SUMMARY;
        final List<QName> elementName = query.getElementName();
        if (setName != null) {
            set = setName.getValue();
        } else if (elementName != null && !elementName.isEmpty()){
            set = null;
        }

        //we get the maxRecords wanted and start position
        final Integer maxRecord = request.getMaxRecords();
        final Integer startPos  = request.getStartPosition();
        if (startPos <= 0) {
            throw new CstlServiceException("The start position must be > 0.",
                                          NO_APPLICABLE_CODE, "startPosition");
        }

        final String[] results;
        if (outputSchema.equals(EBRIM_30) || outputSchema.equals(EBRIM_25)) {

            // build the sql query from the specified filter
            final SQLQuery sqlQuery;
            try {
                sqlQuery = (SQLQuery) sqlFilterParser.getQuery(query.getConstraint(), variables, prefixs, getConvertibleTypeNames(typeNames));
            } catch (FilterParserException ex) {
                throw new CstlServiceException(ex.getMessage(), ex, ex.getExceptionCode(), ex.getLocator());
            }

           // TODO sort not yet implemented
           LOGGER.log(Level.INFO, "ebrim SQL query obtained:{0}", sqlQuery);
           try {
            // we try to execute the query
            results = securityFilter.filterResults(userLogin, mdStore.executeEbrimSQLQuery(sqlQuery.getQuery()));
           } catch (MetadataIoException ex) {
               CodeList execptionCode = ex.getExceptionCode();
               if (execptionCode == null) {
                   execptionCode = NO_APPLICABLE_CODE;
               }
               throw new CstlServiceException(ex, execptionCode);
           }

        } else {

            // build the index query from the specified filter
            final SpatialQuery indexQuery;
            try {
                indexQuery = (SpatialQuery) filterParser.getQuery(query.getConstraint(), variables, prefixs, getConvertibleTypeNames(typeNames));
            } catch (FilterParserException ex) {
                throw new CstlServiceException(ex.getMessage(), ex, ex.getExceptionCode(), ex.getLocator());
            }

            //we look for a sorting request (for now only one sort is used)
            final SortBy sortBy = query.getSortBy();
            if (sortBy != null && sortBy.getSortProperty().size() > 0) {
                final org.opengis.filter.sort.SortBy first = sortBy.getSortProperty().get(0);
                if (first.getPropertyName() == null || first.getPropertyName().getPropertyName() == null || first.getPropertyName().getPropertyName().isEmpty()) {
                    throw new CstlServiceException("A SortBy filter must specify a propertyName.",
                                                  NO_APPLICABLE_CODE);
                }

                final String propertyName = StringUtilities.removePrefix(first.getPropertyName().getPropertyName()) + "_sort";
                final boolean desc        = !first.getSortOrder().equals(SortOrder.ASCENDING);
                final Character fieldType =  indexSearcher.getNumericFields().get(propertyName);
                indexQuery.setSort(propertyName, desc, fieldType);
            }

            // we try to execute the query
            results = securityFilter.filterResults(userLogin, executeLuceneQuery(indexQuery));
        }
        final int nbResults = results.length;

        //we look for distributed queries
        List<FederatedSearchResultBase> distributedResults = new ArrayList<>();
        if (catalogueHarvester != null) {
            final DistributedSearch dSearch = request.getDistributedSearch();
            if (dSearch != null && dSearch.getHopCount() > 0) {
                int distributedStartPosition;
                int distributedMaxRecord;
                if (startPos > nbResults) {
                    distributedStartPosition = startPos - nbResults;
                    distributedMaxRecord     = maxRecord;
                } else {
                    distributedStartPosition = 1;
                    distributedMaxRecord     = maxRecord - nbResults;
                }
                //decrement the hopCount
                dSearch.setHopCount(dSearch.getHopCount() - 1);
                distributedResults = catalogueHarvester.transferGetRecordsRequest(request, cascadedCSWservers, distributedStartPosition, distributedMaxRecord);
            }
        }

        int nextRecord   = startPos + maxRecord;
        int totalMatched = nbResults;
        for (FederatedSearchResultBase distR : distributedResults) {
            totalMatched += distR.getMatched();
        }
        if (nextRecord > totalMatched) {
            nextRecord = 0;
        }

        int maxDistributed = 0;
        for (FederatedSearchResultBase distR : distributedResults) {
            maxDistributed += distR.getReturned();
        }

        int max = (startPos - 1) + maxRecord;

        if (max > nbResults) {
            max = nbResults;
        }
        LOGGER.log(Level.FINER, "local max = " + max + " distributed max = " + maxDistributed);

        final MetadataType mode = MetadataType.getFromNamespace(outputSchema);

        List<RecordInfo> records = new ArrayList<>();
        switch (resultType) {
            // we return only the number of result matching => no metadata reading
            case HITS: break;

            // we read a list of Record
            case RESULTS:

                try {
                    for (int i = startPos - 1; i < max; i++) {
                        final RecordInfo obj = mdStore.getMetadata(results[i], mode, cstlSet(set), elementName);
                        if (obj == null && (max + 1) < nbResults) {
                            max++;

                        } else if (obj != null) {
                            records.add(obj);
                        }
                    }
                } catch (MetadataIoException ex) {
                    CodeList execptionCode = ex.getExceptionCode();
                    if (execptionCode == null) {
                        execptionCode = NO_APPLICABLE_CODE;
                    }
                    throw new CstlServiceException(ex, execptionCode);
                }
                /*
                 * Additional distributed result are now merged in 2.0.2 in CswXmlFactory
                 * TODO see if max ditributed is needed.

                for (int i = 0; i < maxDistributed; i++) {

                    final Object additionalResult = distributedResults.additionalResults.get(i);
                    records.add(additionalResult);
                }
                */
                break;

            //we return an Acknowledgement if the request is valid.
            case VALIDATE :
                return CswXmlFactory.createAcknowledgement(version, id, request, System.currentTimeMillis());
        }

        // ATOM response
        Object response;
        if (MimeType.APP_ATOM.equals(outputFormat)) {
            String serviceUrl = getServiceUrl();
            final Details skeleton = getStaticCapabilitiesObject("csw", null);
            FeedType feed = CSWConstants.createFeed(serviceUrl, skeleton, getOsUrl(serviceUrl));
            for (RecordInfo record : records) {
                EntryType entry = CSWUtils.getEntryFromRecordInfo(serviceUrl, record);
                feed.addEntry(entry);
            }
            response = OpenSearchXmlFactory.completeFeed(feed, (long)totalMatched,(long)startPos, (long)maxRecord);

        // CSW response
        } else {
            SearchResults searchResults;
            if (resultType.equals(HITS)) {
                searchResults = CswXmlFactory.createSearchResults(version, id, set, totalMatched, nextRecord);
            } else {
                final List<Object> nodes = new ArrayList<>();
                for (final RecordInfo obj : records) {
                     nodes.add(obj.node);
                }
                searchResults = CswXmlFactory.createSearchResults(version,
                                                                  id,
                                                                  set,
                                                                  totalMatched,
                                                                  nodes,
                                                                  nodes.size(),
                                                                  nextRecord,
                                                                  distributedResults);
            }
            response = CswXmlFactory.createGetRecordsResponse(request.getVersion().toString(), id, System.currentTimeMillis(), searchResults);
        }
        LOGGER.log(Level.INFO, "GetRecords request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;
    }

    private org.geotoolkit.metadata.ElementSetType cstlSet(final ElementSetType set) {
        if (set != null) {
            return org.geotoolkit.metadata.ElementSetType.fromValue(set.value());
        }
        return null;
    }

    /**
     * Execute a Lucene spatial query and return the result as a List of form identifier (form_ID:CatalogCode)
     */
    private String[] executeLuceneQuery(final SpatialQuery query) throws CstlServiceException {
        LOGGER.log(Level.FINE, "Lucene query obtained:{0}", query);
        try {
            final Set<String> results = indexSearcher.doSearch(query);
            return results.toArray(new String[results.size()]);

        } catch (SearchingException ex) {
            throw new CstlServiceException("The service has throw an exception while making identifier lucene request", ex,
                                             NO_APPLICABLE_CODE);
        }
    }

    /**
     * Execute a Lucene spatial query and return the result as a database identifier.
     */
    private String executeIdentifierQuery(final String id) throws CstlServiceException {
        try {
            return indexSearcher.identifierQuery(id);

        } catch (SearchingException ex) {
            throw new CstlServiceException("The service has throw an exception while making identifier lucene request",
                                          NO_APPLICABLE_CODE);
        }
    }

    /**
     * Add the convertible typeName to the list.
     * Example : MD_Metadata can be converted to a csw:Record (2 or 3)
     *
     * TODO dynamic
     */
    private List<QName> getConvertibleTypeNames(final List<QName> typeNames) {
        final Set<QName> result = new HashSet<>();
        for (QName typeName : typeNames) {
            if ( (typeName.equals(RECORD_202_QNAME) || typeName.equals(RECORD_300_QNAME))) {
                result.add(METADATA_QNAME);
                result.add(DIF_QNAME);
            }
            result.add(typeName);
        }
        return new ArrayList<>(result);
    }

    /**
     * web service operation return one or more records specified by there identifier.
     *
     * @return A GetRecordByIdResponse containing a list of records.
     */
    public GetRecordByIdResponse getRecordById(final GetRecordById request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "GetRecordById request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);

        final String version   = request.getVersion().toString();
        final String userLogin = getUserLogin();

        // verify the output format of the response
        CSWUtils.getOutputFormat(request);

        // we get the level of the record to return (Brief, summary, full)
        ElementSetType set = ElementSetType.SUMMARY;
        if (request.getElementSetName() != null && request.getElementSetName().getValue() != null) {
            set = request.getElementSetName().getValue();
        }

        //we get the output schema and verify that we handle it
        final String outputSchema;
        if (request.getOutputSchema() != null) {
            outputSchema = request.getOutputSchema();
            if (!acceptedResourceType.contains(outputSchema)) {
                throw new CstlServiceException("The server does not support this output schema: " + outputSchema,
                                                  INVALID_PARAMETER_VALUE, OUTPUT_SCHEMA);
            }
        } else {
            if ("2.0.2".equals(version)) {
                outputSchema = LegacyNamespaces.CSW;
            } else {
                // 3.0.0
                outputSchema = Namespaces.CSW;
            }
        }

        if (request.getId().isEmpty()){
            throw new CstlServiceException("You must specify at least one identifier", MISSING_PARAMETER_VALUE, "id");
        }

        //we begin to build the result
        GetRecordByIdResponse response;
        final List<String> unexistingID = new ArrayList<>();
        final List<Object> records      = new ArrayList<>();

        final MetadataType mode = MetadataType.getFromNamespace(outputSchema);
        for (String id : request.getId()) {

            final String saved = id;
            id = executeIdentifierQuery(id);
            if (id == null || !securityFilter.allowed(userLogin, id)) {
                unexistingID.add(saved);
                LOGGER.log(Level.WARNING, "unexisting id:{0}", saved);
                continue;
            }

            //we get the metadata object
            try {
                final RecordInfo o = mdStore.getMetadata(id, mode, cstlSet(set), null);
                if (o != null) {
                    if (!o.actualFormat.equals(mode)) {
                        LOGGER.log(Level.WARNING, "The record {0} does not correspound to {1} object.", new Object[]{id, outputSchema});
                        continue;
                    }
                    records.add(o.node);
                } else {
                    LOGGER.log(Level.WARNING, "The record {0} has not be read is null.", id);
                }
            } catch (MetadataIoException ex) {
                CodeList exceptionCode = ex.getExceptionCode();
                if (exceptionCode == null) {
                    exceptionCode = NO_APPLICABLE_CODE;
                }
                throw new CstlServiceException(ex, exceptionCode);
            }
        }

        if (records.isEmpty()) {
            throwUnexistingIdentifierException(unexistingID);
        }

        response = CswXmlFactory.createGetRecordByIdResponse(version, records);
        LOGGER.log(Level.INFO, "GetRecordById request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;
    }

    /**
     * Launch a service exception with th specified list of unexisting ID.
     */
    private void throwUnexistingIdentifierException(final List<String> unexistingID) throws CstlServiceException {
        final StringBuilder identifiers = new StringBuilder();
        for (String s : unexistingID) {
            identifiers.append(s).append(',');
        }
        String value = identifiers.toString();
        if (value.lastIndexOf(',') != -1) {
            value = value.substring(0, identifiers.length() - 1);
        }
        if (value.isEmpty()) {
            throw new CstlServiceException("The record does not correspound to the specified outputSchema.",
                                             INVALID_PARAMETER_VALUE, OUTPUT_SCHEMA);
        } else {

            throw new CstlServiceException("The identifiers " + value + " does not exist",
                                             INVALID_PARAMETER_VALUE, "id");
        }
    }

    /**
     * Return one or more xsd schemas corresponding to the metadata records.
     */
    public DescribeRecordResponse describeRecord(final DescribeRecord request) throws CstlServiceException{
        LOGGER.log(Level.INFO, "DescribeRecords request processing\n");
        final long startTime = System.currentTimeMillis();

        verifyBaseRequest(request, true, false);

        // verify the output format of the response
        CSWUtils.getOutputFormat(request);

        final String version = request.getVersion().toString();

        // we initialize the type names
        List<QName> typeNames = (List<QName>)request.getTypeName();
        if (typeNames == null || typeNames.isEmpty()) {
            typeNames = supportedTypeNames;
        } else {
            for (QName typeName : typeNames) {
                if (typeName.getNamespaceURI() == null || typeName.getNamespaceURI().isEmpty()) {
                    throw new CstlServiceException("The typeName must be qualified: " + typeName,
                                          INVALID_PARAMETER_VALUE, "typeName");
                }
            }
        }

        // we initialize the schema language
        String schemaLanguage = request.getSchemaLanguage();
        if (schemaLanguage == null) {
            schemaLanguage = "http://www.w3.org/XML/Schema";

        } else if (!supportedSchemaLanguage.contains(schemaLanguage)){

            String supportedList = "";
            for (String s : supportedSchemaLanguage) {
                supportedList = s + '\n';
            }
            throw new CstlServiceException("The server does not support this schema language: " + schemaLanguage +
                                           "\nsupported ones are:\n" + supportedList,
                                          INVALID_PARAMETER_VALUE, "schemaLanguage");
        }
        final List<SchemaComponent> components   = new ArrayList<>();

        if (typeNames.contains(RECORD_202_QNAME)) {
            final Object object = schemas.get(RECORD_202_QNAME);
            final SchemaComponent component = CswXmlFactory.createSchemaComponent(version, LegacyNamespaces.CSW, schemaLanguage, object);
            components.add(component);
        }

        if (typeNames.contains(METADATA_QNAME)) {
            final Object object = schemas.get(METADATA_QNAME);
            final SchemaComponent component = CswXmlFactory.createSchemaComponent(version, LegacyNamespaces.GMD, schemaLanguage, object);
            components.add(component);
        }

        if (containsOneOfEbrim30(typeNames)) {
            final Object object = schemas.get(EXTRINSIC_OBJECT_QNAME);
            final SchemaComponent component = CswXmlFactory.createSchemaComponent(version, EBRIM_30, schemaLanguage, object);
            components.add(component);
        }

        if (containsOneOfEbrim25(typeNames)) {
            final Object object = schemas.get(EXTRINSIC_OBJECT_25_QNAME);
            final SchemaComponent component = CswXmlFactory.createSchemaComponent(version, EBRIM_25, schemaLanguage, object);
            components.add(component);
        }

        LOGGER.log(Level.INFO, "DescribeRecords request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return CswXmlFactory.createDescribeRecordResponse(version, components);
    }

    /**
     * Return a list / range of values for the specified property.
     * The property can be a parameter of the GetCapabilities document or
     * a property of the metadata.
     */
    public GetDomainResponse getDomain(final GetDomain request) throws CstlServiceException{
        LOGGER.log(Level.INFO, "GetDomain request processing\n");
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);
        final String currentVersion = request.getVersion().toString();

        // we prepare the response
       final  List<DomainValues> responseList;

        final String parameterName = request.getParameterName();
        final String propertyName  = request.getPropertyName();

        // if the two parameter have been filled we launch an exception
        if (parameterName != null && propertyName != null) {
            throw new CstlServiceException("One of propertyName or parameterName must be null",
                                             INVALID_PARAMETER_VALUE, PARAMETERNAME);
        }

        /*
         * "parameterName" return metadata about the service itself.
         */
        if (parameterName != null) {
            responseList = new ArrayList<>();
            final StringTokenizer tokens = new StringTokenizer(parameterName, ",");
            while (tokens.hasMoreTokens()) {
                final String token      = tokens.nextToken().trim();
                final int pointLocation = token.indexOf('.');
                if (pointLocation != -1) {

                    final String operationName = token.substring(0, pointLocation);
                    final String parameter     = token.substring(pointLocation + 1);
                    final AbstractOperation o  = OPERATIONS_METADATA.get(currentVersion).getOperation(operationName);
                    if (o != null) {
                        final AbstractDomain param = o.getParameterIgnoreCase(parameter);
                        QName type;
                        if ("GetCapabilities".equals(operationName)) {
                            if ("2.0.2".equals(currentVersion)) {
                                type = CAPABILITIES_202_QNAME;
                            } else {
                                type = CAPABILITIES_300_QNAME;
                            }
                        } else {
                            type = RECORD_202_QNAME;
                        }
                        if (param != null) {
                            final DomainValues value = CswXmlFactory.getDomainValues(currentVersion, token, null, param.getValue(), type);
                            responseList.add(value);
                        } else {
                            throw new CstlServiceException("The parameter " + parameter + " in the operation " + operationName + " does not exist",
                                                          INVALID_PARAMETER_VALUE, PARAMETERNAME);
                        }
                    } else {
                        throw new CstlServiceException("The operation " + operationName + " does not exist",
                                                      INVALID_PARAMETER_VALUE, PARAMETERNAME);
                    }
                } else {
                    throw new CstlServiceException("ParameterName must be formed like this Operation.parameterName",
                                                     INVALID_PARAMETER_VALUE, PARAMETERNAME);
                }
            }

        /*
         * "PropertyName" return a list of metadata for a specific field.
         */
        } else if (propertyName != null) {
            try {
                responseList = mdStore.getFieldDomainofValues(propertyName);
            } catch (MetadataIoException ex) {
                CodeList execptionCode = ex.getExceptionCode();
                if (execptionCode == null) {
                    execptionCode = NO_APPLICABLE_CODE;
                }
                throw new CstlServiceException(ex, execptionCode);
            }

        // if no parameter have been filled we launch an exception
        } else {
            throw new CstlServiceException("One of propertyName or parameterName must be filled",
                                          MISSING_PARAMETER_VALUE, "parameterName, propertyName");
        }
        LOGGER.log(Level.INFO, "GetDomain request processed in {0} ms", (System.currentTimeMillis() - startTime));

        return CswXmlFactory.getDomainResponse(currentVersion, responseList);
    }

    /**
     * A web service method allowing to Insert / update / delete record from the CSW.
     */
    public TransactionResponse transaction(final Transaction request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "Transaction request processing\n");

        if (profile == DISCOVERY) {
            throw new CstlServiceException("This method is not supported by this mode of CSW",
                                          OPERATION_NOT_SUPPORTED, "Request");
        }
        if (isTransactionSecurized() && !SecurityManagerHolder.getInstance().isAuthenticated()) {
            throw new UnauthorizedException("You must be authentified to perform a transaction request.");
        }
        final long startTime = System.currentTimeMillis();
        verifyBaseRequest(request, true, false);

        final String version = request.getVersion().toString();

        // we prepare the report
        int totalInserted       = 0;
        int totalUpdated        = 0;
        int totalDeleted        = 0;
        final String requestID  = request.getRequestId();

        final List<InsertResult> insertResults = new ArrayList<>();
        final List<Object> transactions = request.getInsertOrUpdateOrDelete();
        for (Object transaction: transactions) {
            if (transaction instanceof Insert) {
                final Insert insertRequest = (Insert)transaction;

                for (Object recordObj : insertRequest.getAny()) {

                    final Node record = CSWUtils.transformToNode(recordObj, indexHandler.getMarshallerPool());
                    try {
                        String metadataID = Utils.findIdentifierNode(record);
                        boolean update = mdStore.existMetadata(metadataID);
                        metadataBusiness.updateMetadata(metadataID, record, null, null, null, null, providerId, "DOC");

                        if (update) {
                            totalUpdated++;
                        } else {
                            totalInserted++;
                            MetadataType dcType;
                            if (version.equals("3.0.0")) {
                                dcType = MetadataType.DUBLINCORE_CSW300;
                            } else {
                                dcType = MetadataType.DUBLINCORE_CSW202;
                            }
                            RecordInfo brief = mdStore.getMetadata(metadataID, dcType, cstlSet(ElementSetType.BRIEF), null);
                            List<Object> objs = new ArrayList<>();
                            objs.add(brief.node);
                            insertResults.add(CswXmlFactory.createInsertResult(version, objs, null));
                        }

                    } catch (MetadataIoException | ConfigurationException ex) {
                        CodeList execptionCode = null;// ex.getExceptionCode(); TODO lost code
                        if (execptionCode == null) {
                            execptionCode = NO_APPLICABLE_CODE;
                        }
                        throw new CstlServiceException(ex, execptionCode);
                    }
                }

            } else if (transaction instanceof Delete) {
                if (mdStore.deleteSupported()) {
                    final Delete deleteRequest = (Delete)transaction;
                    if (deleteRequest.getConstraint() == null) {
                        throw new CstlServiceException("A constraint must be specified.",
                                                      MISSING_PARAMETER_VALUE, "constraint");
                    }
                    final List<QName> typeNames = new ArrayList<>();
                    final QName dataType = deleteRequest.getTypeName();
                    if (dataType != null) {
                        try {
                            typeNames.add(dataType);
                        } catch (IllegalArgumentException ex) {
                            throw new CstlServiceException("Unexpected value for typeName:" + dataType, INVALID_PARAMETER_VALUE, "typeName");
                        }
                    }
                    // build the lucene query from the specified filter
                    final SpatialQuery luceneQuery;
                    try {
                        luceneQuery = (SpatialQuery) filterParser.getQuery(deleteRequest.getConstraint(), null, null, getConvertibleTypeNames(typeNames));
                    } catch (FilterParserException ex) {
                        throw new CstlServiceException(ex.getMessage(), ex, ex.getExceptionCode(), ex.getLocator());
                    }
                    // we try to execute the query
                    final String[] results = executeLuceneQuery(luceneQuery);

                    try {
                        for (String metadataID : results) {
                            final boolean deleted = metadataBusiness.deleteMetadata(metadataID);
                            if (!deleted) {
                                throw new CstlServiceException("The service does not succeed to delete the metadata:" + metadataID,
                                                  NO_APPLICABLE_CODE);
                            } else {
                                totalDeleted++;
                            }
                        }
                    } catch (ConfigurationException ex) {
                        CodeList execptionCode = null; // ex.getExceptionCode(); TODO los exception code
                        if (execptionCode == null) {
                            execptionCode = NO_APPLICABLE_CODE;
                        }
                        throw new CstlServiceException(ex, execptionCode);
                    }
                } else {
                    throw new CstlServiceException("This kind of transaction (delete) is not supported by this Writer implementation.",
                                                  NO_APPLICABLE_CODE, TRANSACTION_TYPE);
                }


            } else if (transaction instanceof Update) {
                if (mdStore.updateSupported()) {
                    final Update updateRequest = (Update) transaction;
                    if (updateRequest.getConstraint() == null) {
                        throw new CstlServiceException("A constraint must be specified.",
                                MISSING_PARAMETER_VALUE, "constraint");
                    }
                    if (updateRequest.getAny() == null && updateRequest.getRecordProperty().isEmpty()) {
                        throw new CstlServiceException("The any part or a list od RecordProperty must be specified.",
                                MISSING_PARAMETER_VALUE, "MD_Metadata");
                    } else if (updateRequest.getAny() != null && !updateRequest.getRecordProperty().isEmpty()) {
                        throw new CstlServiceException("You must choose between the any part or a list of RecordProperty, you can't specify both.",
                                MISSING_PARAMETER_VALUE, "MD_Metadata");
                    }

                    final List<QName> typeNames = new ArrayList<>();
                    // build the lucene query from the specified filter
                    final SpatialQuery luceneQuery;
                    try {
                        luceneQuery = (SpatialQuery) filterParser.getQuery(updateRequest.getConstraint(), null, null, getConvertibleTypeNames(typeNames));
                    } catch (FilterParserException ex) {
                        throw new CstlServiceException(ex.getMessage(), ex, ex.getExceptionCode(), ex.getLocator());
                    }

                    // we try to execute the query
                    try {
                        final String[] results = executeLuceneQuery(luceneQuery);
                        for (String metadataID : results) {
                            boolean updated;
                            if (updateRequest.getAny() != null) {
                                final Node any = CSWUtils.transformToNode(updateRequest.getAny(), indexHandler.getMarshallerPool());
                                metadataBusiness.deleteMetadata(metadataID);
                                updated = metadataBusiness.updateMetadata(metadataID, any, null, null, null, null, providerId, "DOC") != null;
                            } else {
                                updated = metadataBusiness.updatePartialMetadata(metadataID, updateRequest.getRecordPropertyMap(), providerId);
                            }
                            if (!updated) {
                                throw new CstlServiceException("The service does not succeed to update the metadata:" + metadataID,
                                        NO_APPLICABLE_CODE);
                            } else {
                                totalUpdated++;
                            }
                        }
                    } catch (ConfigurationException ex) {
                        CodeList execptionCode = null; // ex.getExceptionCode(); TODO lost exception code
                        if (execptionCode == null) {
                            execptionCode = NO_APPLICABLE_CODE;
                        }
                        throw new CstlServiceException(ex, execptionCode);
                    }
                } else {
                    throw new CstlServiceException("This kind of transaction (update) is not supported by this Writer implementation.",
                            NO_APPLICABLE_CODE, TRANSACTION_TYPE);
                }
            } else {
                String className = " null object";
                if (transaction != null) {
                    className = transaction.getClass().getName();
                }
                throw new CstlServiceException("This kind of transaction is not supported by the service: " + className,
                                              INVALID_PARAMETER_VALUE, TRANSACTION_TYPE);
            }

        }
        if (totalDeleted > 0 || totalInserted > 0 || totalUpdated > 0) {
            try {
                indexSearcher.refresh();
            } catch (IndexingException ex) {
                throw new CstlServiceException("The service does not succeed to refresh the index after deleting documents:" + ex.getMessage(),
                        NO_APPLICABLE_CODE);
            }
        }
        final TransactionSummary summary = CswXmlFactory.createTransactionSummary(version, totalInserted, totalUpdated, totalDeleted, requestID);

        final TransactionResponse response = CswXmlFactory.createTransactionResponse(version, summary, insertResults);
        LOGGER.log(Level.INFO, "Transaction request processed in {0} ms", (System.currentTimeMillis() - startTime));
        return response;
    }

    /**
     * TODO
     */
    public HarvestResponse harvest(final Harvest request) throws CstlServiceException {
        LOGGER.log(Level.INFO, "Harvest request processing\n");
        if (profile == DISCOVERY) {
            throw new CstlServiceException("This method is not supported by this mode of CSW",
                                          OPERATION_NOT_SUPPORTED, "Request");
        }
        if (isTransactionSecurized() && !SecurityManagerHolder.getInstance().isAuthenticated()) {
            throw new UnauthorizedException("You must be authentified to perform a harvest request.");
        }
        verifyBaseRequest(request, true, false);
        final String version = request.getVersion().toString();

        HarvestResponse response;
        // we prepare the report
        final int totalInserted;
        final int totalUpdated;
        final int totalDeleted;

        //we verify the resource Type
        final String resourceType = request.getResourceType();
        if (resourceType == null) {
            throw new CstlServiceException("The resource type to harvest must be specified",
                                          MISSING_PARAMETER_VALUE, "resourceType");
        } else {
            if (!acceptedResourceType.contains(resourceType)) {
                throw new CstlServiceException("This resource type is not allowed. ",
                                             MISSING_PARAMETER_VALUE, "resourceType");
            }
        }
        final String sourceURL = request.getSource();
        if (sourceURL != null) {
            try {

                // TODO find a better to determine if the source is single or catalogue
                int mode = 1;
                if (sourceURL.endsWith("xml")) {
                    mode = 0;
                }

                //mode synchronous
                if (request.getResponseHandler().isEmpty()) {

                    // if the resource is a simple record
                    if (mode == 0) {
                        final int[] results = catalogueHarvester.harvestSingle(sourceURL, resourceType);
                        totalInserted = results[0];
                        totalUpdated  = results[1];
                        totalDeleted  = 0;

                    // if the resource is another CSW service we get all the data of this catalogue.
                    } else {
                        final int[] results = catalogueHarvester.harvestCatalogue(sourceURL);
                        totalInserted = results[0];
                        totalUpdated  = results[1];
                        totalDeleted  = results[2];
                    }
                    final TransactionSummary summary = CswXmlFactory.createTransactionSummary(version, totalInserted, totalUpdated, totalDeleted, null);
                    final TransactionResponse transactionResponse = CswXmlFactory.createTransactionResponse(version, summary, null);
                    response = CswXmlFactory.createHarvestResponse(version, transactionResponse);

                //mode asynchronous
                } else {

                    final Acknowledgement acknowledgement = CswXmlFactory.createAcknowledgement(version, null, request, System.currentTimeMillis());
                    response = CswXmlFactory.createHarvestResponse(version, acknowledgement);
                    long period = 0;
                    if (request.getHarvestInterval() != null) {
                        period = request.getHarvestInterval().getTimeInMillis(new Date(System.currentTimeMillis()));
                    }

                    harvestTaskScheduler.newAsynchronousHarvestTask(period, sourceURL, resourceType, mode, request.getResponseHandler());

                }

            } catch (SQLException ex) {
                throw new CstlServiceException("The service has throw an SQLException: " + ex.getMessage(),
                                              NO_APPLICABLE_CODE);
            } catch (JAXBException ex) {
                throw new CstlServiceException("The resource can not be parsed: " + ex.getMessage(),
                                              INVALID_PARAMETER_VALUE, SOURCE);
            } catch (MalformedURLException ex) {
                throw new CstlServiceException("The source URL is malformed",
                                              INVALID_PARAMETER_VALUE, SOURCE);
            } catch (IOException ex) {
                throw new CstlServiceException("The service can't open the connection to the source",
                                              INVALID_PARAMETER_VALUE, SOURCE);
            }

        } else {
            throw new CstlServiceException("you must specify a source",
                                              MISSING_PARAMETER_VALUE, SOURCE);
        }

        try {
            indexSearcher.refresh();
            mdStore.getReader().clearCache();
        } catch (IndexingException ex) {
            throw new CstlServiceException("The service does not succeed to refresh the index after deleting documents:" + ex.getMessage(),
                    NO_APPLICABLE_CODE);
        }

        LOGGER.log(Level.INFO, "Harvest operation finished");
        return response;
    }

    /**
     * Verify that the bases request attributes are correct.
     *
     * @param request an object request with the base attribute (all except GetCapabilities request);
     */
    private void verifyBaseRequest(final RequestBase request, final boolean versionMandatory, final boolean getCapabilities) throws CstlServiceException {
        isWorking();
        if (request != null) {
            if (request.getService() != null && !request.getService().isEmpty()) {
                if (!request.getService().equals(CSW))  {
                    throw new CstlServiceException("service must be \"CSW\"!", INVALID_PARAMETER_VALUE, SERVICE_PARAMETER);
                }
            } else {
                throw new CstlServiceException("service must be specified!", MISSING_PARAMETER_VALUE, SERVICE_PARAMETER);
            }
            if (request.getVersion()!= null && !request.getVersion().toString().isEmpty()) {

                if (isSupportedVersion(request.getVersion().toString())) {
                    request.setVersion(request.getVersion().toString());
                } else {
                    final CodeList code;
                    final String locator;
                    if (getCapabilities) {
                        code = VERSION_NEGOTIATION_FAILED;
                        locator = "acceptVersion";
                    } else {
                        code = INVALID_PARAMETER_VALUE;
                        locator = "version";
                    }
                    final StringBuilder sb = new StringBuilder();
                    supportedVersions.stream().forEach((v) -> {
                        sb.append("\"").append(v.version.toString()).append("\"");
                    });
                    throw new CstlServiceException("version must be " + sb.toString() + "!", code, locator);
                }
            } else {
                if (versionMandatory) {
                    throw new CstlServiceException("version must be specified!", MISSING_PARAMETER_VALUE, "version");
                } else {
                    request.setVersion(getBestVersion(null).version.toString());
                }
            }
         } else {
            throw new CstlServiceException("The request is null!", NO_APPLICABLE_CODE);
         }
    }

    /**
     * Return a string list of the supported TypeName
     */
    private String supportedTypeNames() {
        final StringBuilder result = new StringBuilder();
        for (QName qn: supportedTypeNames) {
            result.append(qn.getPrefix()).append(qn.getLocalPart()).append('\n');
        }
        return result.toString();
    }

    /**
     * Redirect the logs into the specified folder.
     * if the parameter ID is null or empty it create a file named "cstl-csw.log"
     * else the file is named "ID-cstl-csw.log"
     *
     * @param id The ID of the service in a case of multiple sos server.
     * @param filePath The path to the log folder.
     */
    private void initLogger(String id, String filePath) {
        try {
            if (id != null && !id.isEmpty()) {
                id = id + '-';
            }
            final FileHandler handler = new FileHandler(filePath + '/'+ id + "cstl-csw.log");
            handler.setFormatter(new MonolineFormatter(handler));
            LOGGER.addHandler(handler);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "IO exception while trying to separate CSW Logs:{0}", ex.getMessage());
        } catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE, "Security exception while trying to separate CSW Logs{0}", ex.getMessage());
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void destroy() {
        super.destroy();
        try {
            if (mdStore != null) {
                mdStore.close();
            }
            if (catalogueHarvester != null) {
                catalogueHarvester.destroy();
            }
        } catch (DataStoreException ex) {
            LOGGER.log(Level.WARNING, "Error while closing Metadata store", ex);
        }
        if (indexSearcher != null) {
            indexSearcher.destroy();
        }
        if (indexer != null) {
            indexer.destroy();
        }
        if (harvestTaskScheduler != null) {
            harvestTaskScheduler.destroy();
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    protected MarshallerPool getMarshallerPool() {
        return CSWMarshallerPool.getInstanceCswOnly();
    }

    @Override
    public void refresh() throws CstlServiceException {
        try {
            indexSearcher.refresh();
            mdStore.getReader().clearCache();
        } catch (IndexingException ex) {
            throw new CstlServiceException("Error while refreshing cache", ex, NO_APPLICABLE_CODE);
        }
    }

   @Override
    protected String getProperty(final String key) {
        if (configuration != null) {
            return configuration.getParameter(key);
        }
        return null;
    }
}
