/**
 * This class is generated by jOOQ
 */
package org.constellation.database.api.jooq.tables;

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
public class DatasourcePath extends org.jooq.impl.TableImpl<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord> {

	private static final long serialVersionUID = 724592362;

	/**
	 * The reference instance of <code>admin.datasource_path</code>
	 */
	public static final org.constellation.database.api.jooq.tables.DatasourcePath DATASOURCE_PATH = new org.constellation.database.api.jooq.tables.DatasourcePath();

	/**
	 * The class holding records for this type
	 */
	@Override
	public java.lang.Class<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord> getRecordType() {
		return org.constellation.database.api.jooq.tables.records.DatasourcePathRecord.class;
	}

	/**
	 * The column <code>admin.datasource_path.datasource_id</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, java.lang.Integer> DATASOURCE_ID = createField("datasource_id", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

	/**
	 * The column <code>admin.datasource_path.path</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, java.lang.String> PATH = createField("path", org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "");

	/**
	 * The column <code>admin.datasource_path.name</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, java.lang.String> NAME = createField("name", org.jooq.impl.SQLDataType.CLOB.nullable(false), this, "");

	/**
	 * The column <code>admin.datasource_path.folder</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, java.lang.Boolean> FOLDER = createField("folder", org.jooq.impl.SQLDataType.BOOLEAN.nullable(false), this, "");

	/**
	 * The column <code>admin.datasource_path.parent_path</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, java.lang.String> PARENT_PATH = createField("parent_path", org.jooq.impl.SQLDataType.CLOB, this, "");

	/**
	 * The column <code>admin.datasource_path.size</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, java.lang.Integer> SIZE = createField("size", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

	/**
	 * Create a <code>admin.datasource_path</code> table reference
	 */
	public DatasourcePath() {
		this("datasource_path", null);
	}

	/**
	 * Create an aliased <code>admin.datasource_path</code> table reference
	 */
	public DatasourcePath(java.lang.String alias) {
		this(alias, org.constellation.database.api.jooq.tables.DatasourcePath.DATASOURCE_PATH);
	}

	private DatasourcePath(java.lang.String alias, org.jooq.Table<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord> aliased) {
		this(alias, aliased, null);
	}

	private DatasourcePath(java.lang.String alias, org.jooq.Table<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord> aliased, org.jooq.Field<?>[] parameters) {
		super(alias, org.constellation.database.api.jooq.Admin.ADMIN, aliased, parameters, "");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord> getPrimaryKey() {
		return org.constellation.database.api.jooq.Keys.DATASOURCE_PATH_PK;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord>> getKeys() {
		return java.util.Arrays.<org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord>>asList(org.constellation.database.api.jooq.Keys.DATASOURCE_PATH_PK);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.ForeignKey<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, ?>> getReferences() {
		return java.util.Arrays.<org.jooq.ForeignKey<org.constellation.database.api.jooq.tables.records.DatasourcePathRecord, ?>>asList(org.constellation.database.api.jooq.Keys.DATASOURCE_PATH__DATASOURCE_PATH_DATASOURCE_ID_FK);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.constellation.database.api.jooq.tables.DatasourcePath as(java.lang.String alias) {
		return new org.constellation.database.api.jooq.tables.DatasourcePath(alias, this);
	}

	/**
	 * Rename this table
	 */
	public org.constellation.database.api.jooq.tables.DatasourcePath rename(java.lang.String name) {
		return new org.constellation.database.api.jooq.tables.DatasourcePath(name, null);
	}
}
