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

package org.constellation.store.observation.db;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBReader;
import org.locationtech.jts.io.WKBWriter;
import org.apache.sis.referencing.CRS;
import org.apache.sis.storage.DataStoreException;
import org.geotoolkit.data.AbstractFeatureStore;
import org.geotoolkit.data.FeatureReader;
import org.geotoolkit.data.FeatureStoreRuntimeException;
import org.geotoolkit.data.FeatureWriter;
import org.geotoolkit.data.query.DefaultQueryCapabilities;
import org.geotoolkit.data.query.QueryCapabilities;
import org.geotoolkit.factory.Hints;
import org.geotoolkit.filter.identity.DefaultFeatureId;
import org.geotoolkit.jdbc.ManageableDataSource;
import org.opengis.filter.Filter;
import org.opengis.filter.identity.FeatureId;
import org.opengis.parameter.ParameterValueGroup;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.util.FactoryException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.geotoolkit.feature.FeatureExt;
import org.apache.sis.feature.builder.AttributeRole;
import org.apache.sis.feature.builder.AttributeTypeBuilder;
import org.apache.sis.feature.builder.FeatureTypeBuilder;
import org.apache.sis.internal.feature.AttributeConvention;
import org.apache.sis.storage.IllegalNameException;
import org.apache.sis.storage.Query;
import org.apache.sis.storage.UnsupportedQueryException;
import static org.constellation.store.observation.db.OM2FeatureStoreFactory.SCHEMA_PREFIX;
import static org.constellation.store.observation.db.OM2FeatureStoreFactory.SGBDTYPE;
import org.geotoolkit.data.FeatureStreams;
import org.geotoolkit.internal.data.GenericNameIndex;
import org.geotoolkit.storage.DataStoreFactory;
import org.geotoolkit.storage.DataStores;
import org.geotoolkit.util.NamesExt;
import org.opengis.feature.Feature;
import org.opengis.feature.FeatureType;
import org.opengis.util.GenericName;

/**
 * Feature store on the Examind OM2 database.
 *
 * @author Guilhem Legal (Geomatys)
 * @author Johann Sorel (Geomatys)
 *
 */
public class OM2FeatureStore extends AbstractFeatureStore {

    private static final String CSTL_NAMESPACE = "http://constellation.org/om2";
    private static final GenericName CSTL_TN_SENSOR = NamesExt.create(CSTL_NAMESPACE, "Sensor");
    protected static final GenericName ATT_ID = NamesExt.create(CSTL_NAMESPACE,  "id");
    protected static final GenericName ATT_POSITION = NamesExt.create(CSTL_NAMESPACE,  "position");

    private static final QueryCapabilities capabilities = new DefaultQueryCapabilities(false);

    private final GenericNameIndex<FeatureType> types = new GenericNameIndex<>();

    private final ManageableDataSource source;

    private final String sensorIdBase = "urn:ogc:object:sensor:GEOM:"; // TODO

    private final boolean isPostgres;

    protected final String schemaPrefix;

    public OM2FeatureStore(final ParameterValueGroup params, final ManageableDataSource source) {
        super(params);
        this.source = source;
        Object sgbdtype = parameters.getMandatoryValue(SGBDTYPE);
        isPostgres = !("derby".equals(sgbdtype));
        String sc = parameters.getValue(SCHEMA_PREFIX);
        if (sc != null) {
            schemaPrefix = sc;
        } else {
            schemaPrefix = "";
        }
        initTypes();
    }

    @Override
    public DataStoreFactory getProvider() {
        return DataStores.getFactoryById(OM2FeatureStoreFactory.NAME);
    }

    private Connection getConnection() throws SQLException{
        return source.getConnection();
    }

    private void initTypes() {
        final FeatureTypeBuilder featureTypeBuilder = new FeatureTypeBuilder();
        featureTypeBuilder.setName(CSTL_TN_SENSOR);
        featureTypeBuilder.addAttribute(String.class).setName(ATT_ID).addRole(AttributeRole.IDENTIFIER_COMPONENT);
        featureTypeBuilder.addAttribute(Geometry.class).setName(ATT_POSITION).addRole(AttributeRole.DEFAULT_GEOMETRY);
        try {
            types.add(CSTL_TN_SENSOR, featureTypeBuilder.build());
        } catch (IllegalNameException ex) {
            //won't happen
            getLogger().log(Level.WARNING, ex.getMessage(), ex);
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public FeatureReader getFeatureReader(final Query query) throws DataStoreException {
        if (!(query instanceof org.geotoolkit.data.query.Query)) throw new UnsupportedQueryException();

        final org.geotoolkit.data.query.Query gquery = (org.geotoolkit.data.query.Query) query;
        final FeatureType sft = getFeatureType(gquery.getTypeName());
        try {
            return FeatureStreams.subset(new OMReader(sft), gquery);
        } catch (SQLException ex) {
            throw new DataStoreException(ex);
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public FeatureWriter getFeatureWriter(Query query) throws DataStoreException {
        if (!(query instanceof org.geotoolkit.data.query.Query)) throw new UnsupportedQueryException();

        final org.geotoolkit.data.query.Query gquery = (org.geotoolkit.data.query.Query) query;
        final FeatureType sft = getFeatureType(gquery.getTypeName());
        try {
            return FeatureStreams.filter((FeatureWriter)new OMWriter(sft), gquery.getFilter());
        } catch (SQLException ex) {
            throw new DataStoreException(ex);
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void close() throws DataStoreException {
        super.close();
        try {
            source.close();
        } catch (SQLException ex) {
            getLogger().info("SQL Exception while closing O&M2 datastore");
        }
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public Set<GenericName> getNames() throws DataStoreException {
        return types.getNames();
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public FeatureType getFeatureType(final String typeName) throws DataStoreException {
        return types.get(typeName);
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public QueryCapabilities getQueryCapabilities() {
        return capabilities;
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public List<FeatureId> addFeatures(final String groupName, final Collection<? extends Feature> newFeatures,
            final Hints hints) throws DataStoreException {
        final FeatureType featureType = getFeatureType(groupName); //raise an error if type doesn't exist
        final List<FeatureId> result = new ArrayList<>();


        Connection cnx = null;
        PreparedStatement stmtWrite = null;
        try {
            cnx = getConnection();
            stmtWrite = cnx.prepareStatement("INSERT INTO \"" + schemaPrefix + "om\".\"procedures\" VALUES(?,?,?)");

            for(final Feature feature : newFeatures) {
                FeatureId identifier = FeatureExt.getId(feature);
                if (identifier == null || identifier.getID().isEmpty()) {
                    identifier = getNewFeatureId();
                }


                stmtWrite.setString(1, identifier.getID());
                final Optional<Geometry> geometry = FeatureExt.getDefaultGeometryValue(feature)
                        .filter(Geometry.class::isInstance)
                        .map(Geometry.class::cast);
                if (geometry.isPresent()) {
                    final Geometry geom = geometry.get();
                    final WKBWriter writer = new WKBWriter();
                    final int SRID = geom.getSRID();
                    stmtWrite.setBytes(2, writer.write(geom));
                    stmtWrite.setInt(3, SRID);

                } else {
                    stmtWrite.setNull(2, Types.VARCHAR);
                    stmtWrite.setNull(3, Types.INTEGER);

                }
                stmtWrite.executeUpdate();
                result.add(identifier);
            }
        } catch (SQLException ex) {
            getLogger().log(Level.WARNING, "Error while writing procedure feature", ex);
        }finally{
            if(stmtWrite != null){
                try {
                    stmtWrite.close();
                } catch (SQLException ex) {
                    getLogger().log(Level.WARNING, null, ex);
                }
            }

            if(cnx != null){
                try {
                    cnx.close();
                } catch (SQLException ex) {
                    getLogger().log(Level.WARNING, null, ex);
                }
            }
        }

        return result;
    }

    public FeatureId getNewFeatureId() {
        Connection cnx = null;
        PreparedStatement stmtLastId = null;
        try {
            cnx = getConnection();
            stmtLastId = cnx.prepareStatement("SELECT \"id\" FROM \"" + schemaPrefix + "om\".\"procedures\" ORDER BY \"id\" ASC");
            try(final ResultSet result = stmtLastId.executeQuery()) {
                // keep the last
                String id = null;
                while (result.next()) {
                    id = result.getString(1);
                }
                if (id != null) {
                    try {
                        final int i = Integer.parseInt(id.substring(sensorIdBase.length()));
                        return new DefaultFeatureId(sensorIdBase + i);
                    } catch (NumberFormatException ex) {
                        getLogger().warning("a snesor ID is malformed in procedures tables");
                    }
                } else {
                    return new DefaultFeatureId(sensorIdBase + 1);
                }
            }

        } catch (SQLException ex) {
            getLogger().log(Level.WARNING, null, ex);
        }finally{
            if(stmtLastId != null){
                try {
                    stmtLastId.close();
                } catch (SQLException ex) {
                    getLogger().log(Level.WARNING, null, ex);
                }
            }

            if(cnx != null){
                try {
                    cnx.close();
                } catch (SQLException ex) {
                    getLogger().log(Level.WARNING, null, ex);
                }
            }
        }
        return null;
    }


    ////////////////////////////////////////////////////////////////////////////
    // No supported stuffs /////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    /**
     * {@inheritDoc }
     */
    @Override
    public void createFeatureType(final FeatureType featureType) throws DataStoreException {
        throw new DataStoreException("Not Supported.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void updateFeatureType(final FeatureType featureType) throws DataStoreException {
        throw new DataStoreException("Not Supported.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void deleteFeatureType(final String typeName) throws DataStoreException {
        throw new DataStoreException("Not Supported.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void updateFeatures(final String groupName, final Filter filter, final Map<String, ? extends Object> values) throws DataStoreException {
        throw new DataStoreException("Not supported.");
    }

    /**
     * {@inheritDoc }
     */
    @Override
    public void removeFeatures(final String groupName, final Filter filter) throws DataStoreException {
        handleRemoveWithFeatureWriter(groupName, filter);
    }


    ////////////////////////////////////////////////////////////////////////////
    // Feature Reader //////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////

    private class OMReader implements FeatureReader {

        protected final Connection cnx;
        private boolean firstCRS = true;
        protected FeatureType type;
        private final ResultSet result;
        protected Feature current = null;

        private OMReader(final FeatureType type) throws SQLException{
            this.type = type;
            cnx = getConnection();
            final PreparedStatement stmtAll;
            if (isPostgres) {
                stmtAll = cnx.prepareStatement("SELECT  \"id\", \"postgis\".st_asBinary(\"shape\"), \"crs\" FROM \"" + schemaPrefix + "om\".\"procedures\"");
            } else {
                stmtAll = cnx.prepareStatement("SELECT * FROM \"" + schemaPrefix + "om\".\"procedures\"");
            }
            result = stmtAll.executeQuery();
        }

        @Override
        public FeatureType getFeatureType() {
            return type;
        }

        @Override
        public Feature next() throws FeatureStoreRuntimeException {
            try {
                read();
            } catch (Exception ex) {
                throw new FeatureStoreRuntimeException(ex);
            }
            Feature candidate = current;
            current = null;
            return candidate;
        }

        @Override
        public boolean hasNext() throws FeatureStoreRuntimeException {
            try {
                read();
            } catch (Exception ex) {
                throw new FeatureStoreRuntimeException(ex);
            }
            return current != null;
        }

        protected void read() throws Exception{
            if(current != null) return;

            if(!result.next()){
                return;
            }


            final String crsStr = result.getString(3);
            Geometry geom = null;

            if (crsStr != null && !crsStr.isEmpty()) {
                if (firstCRS) {
                    try {
                        CoordinateReferenceSystem crs = CRS.forCode("EPSG:" + crsStr);
                        final FeatureTypeBuilder ftb = new FeatureTypeBuilder(type);
                        ((AttributeTypeBuilder)ftb.getProperty("position")).setCRS(crs);
                        type = ftb.build();
                        firstCRS = false;
                    } catch (NoSuchAuthorityCodeException ex) {
                        throw new IOException(ex);
                    } catch (FactoryException ex) {
                        throw new IOException(ex);
                    }
                }

                final byte[] b = result.getBytes(2);
                if (b != null) {
                    WKBReader reader = new WKBReader();
                    geom = reader.read(b);
                }
            }

            final String id = result.getString(1);
            current = type.newInstance();
            current.setPropertyValue(AttributeConvention.IDENTIFIER_PROPERTY.toString(), id);
            current.setPropertyValue(ATT_ID.toString(),id);
            current.setPropertyValue(ATT_POSITION.toString(),geom);
            //props.add(FF.createAttribute(result.getString("description"), (AttributeDescriptor) type.getDescriptor(ATT_DESC), null));
        }

        @Override
        public void close() {
            try {
                result.close();
                cnx.close();
            } catch (SQLException ex) {
                throw new FeatureStoreRuntimeException(ex);
            }
        }

        @Override
        public void remove() throws FeatureStoreRuntimeException{
            throw new FeatureStoreRuntimeException("Not supported.");
        }

    }

    private class OMWriter extends OMReader implements FeatureWriter {

        protected Feature candidate = null;

        private OMWriter(final FeatureType type) throws SQLException{
            super(type);
        }

        @Override
        public Feature next() throws FeatureStoreRuntimeException {
            try {
                read();
            } catch (Exception ex) {
                throw new FeatureStoreRuntimeException(ex);
            }
            candidate = current;
            current = null;
            return candidate;
        }

        @Override
        public void remove() throws FeatureStoreRuntimeException{

            if (candidate == null) {
                return;
            }

            try (PreparedStatement stmtDelete = cnx.prepareStatement("DELETE FROM \"" + schemaPrefix + "om\".\"procedures\" WHERE \"id\" = ?")){

                stmtDelete.setString(1, FeatureExt.getId(candidate).getID());
                stmtDelete.executeUpdate();

            } catch (SQLException ex) {
                getLogger().log(Level.WARNING, "Error while deleting procedure features", ex);
            }
        }

        @Override
        public void write() throws FeatureStoreRuntimeException {
            throw new FeatureStoreRuntimeException("Not supported.");
        }
    }

	@Override
	public void refreshMetaModel() {

	}
}
