/**
 * This class is generated by jOOQ
 */
package org.constellation.database.api.jooq.tables.daos;

/**
 * This class is generated by jOOQ.
 */
@javax.annotation.Generated(
	value = {
		"http://www.jooq.org",
		"jOOQ version:3.5.3"
	},
	comments = "This class is generated by jOOQ"
)
@java.lang.SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DatasourcePathDao extends org.jooq.impl.DAOImpl<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, org.constellation.database.api.jooq.tables.pojos.DatasourcePath, org.jooq.Record2<java.lang.Integer, java.lang.String>> {

	/**
	 * Create a new DatasourcePathDao without any configuration
	 */
	public DatasourcePathDao() {
		super(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH, org.constellation.database.api.jooq.tables.pojos.DatasourcePath.class);
	}

	/**
	 * Create a new DatasourcePathDao with an attached configuration
	 */
	public DatasourcePathDao(org.jooq.Configuration configuration) {
		super(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH, org.constellation.database.api.jooq.tables.pojos.DatasourcePath.class, configuration);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected org.jooq.Record2<java.lang.Integer, java.lang.String> getId(org.constellation.database.api.jooq.tables.pojos.DatasourcePath object) {
		return compositeKeyRecord(object.getDatasourceId(), object.getPath());
	}

	/**
	 * Fetch records that have <code>datasource_id IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.DatasourcePath> fetchByDatasourceId(java.lang.Integer... values) {
		return fetch(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH.DATASOURCE_ID, values);
	}

	/**
	 * Fetch records that have <code>path IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.DatasourcePath> fetchByPath(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH.PATH, values);
	}

	/**
	 * Fetch records that have <code>name IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.DatasourcePath> fetchByName(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH.NAME, values);
	}

	/**
	 * Fetch records that have <code>folder IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.DatasourcePath> fetchByFolder(java.lang.Boolean... values) {
		return fetch(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH.FOLDER, values);
	}

	/**
	 * Fetch records that have <code>parent_path IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.DatasourcePath> fetchByParentPath(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH.PARENT_PATH, values);
	}

	/**
	 * Fetch records that have <code>size IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.DatasourcePath> fetchBySize(java.lang.Integer... values) {
		return fetch(org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH.SIZE, values);
	}
}
