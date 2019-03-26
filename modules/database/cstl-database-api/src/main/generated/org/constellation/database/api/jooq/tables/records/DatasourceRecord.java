/**
 * This class is generated by jOOQ
 */
package org.constellation.database.api.jooq.tables.records;

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
public class DatasourceRecord extends org.jooq.impl.UpdatableRecordImpl<org.constellation.database.api.jooq.tables.records.DatasourceRecord> implements org.jooq.Record11<java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.Long, java.lang.String, java.lang.String, java.lang.Boolean> {

	private static final long serialVersionUID = 421760654;

	/**
	 * Setter for <code>admin.datasource.id</code>.
	 */
	public DatasourceRecord setId(java.lang.Integer value) {
		setValue(0, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.id</code>.
	 */
	@javax.validation.constraints.NotNull
	public java.lang.Integer getId() {
		return (java.lang.Integer) getValue(0);
	}

	/**
	 * Setter for <code>admin.datasource.type</code>.
	 */
	public DatasourceRecord setType(java.lang.String value) {
		setValue(1, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.type</code>.
	 */
	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 50)
	public java.lang.String getType() {
		return (java.lang.String) getValue(1);
	}

	/**
	 * Setter for <code>admin.datasource.url</code>.
	 */
	public DatasourceRecord setUrl(java.lang.String value) {
		setValue(2, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.url</code>.
	 */
	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 1000)
	public java.lang.String getUrl() {
		return (java.lang.String) getValue(2);
	}

	/**
	 * Setter for <code>admin.datasource.username</code>.
	 */
	public DatasourceRecord setUsername(java.lang.String value) {
		setValue(3, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.username</code>.
	 */
	@javax.validation.constraints.Size(max = 100)
	public java.lang.String getUsername() {
		return (java.lang.String) getValue(3);
	}

	/**
	 * Setter for <code>admin.datasource.pwd</code>.
	 */
	public DatasourceRecord setPwd(java.lang.String value) {
		setValue(4, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.pwd</code>.
	 */
	@javax.validation.constraints.Size(max = 500)
	public java.lang.String getPwd() {
		return (java.lang.String) getValue(4);
	}

	/**
	 * Setter for <code>admin.datasource.store_id</code>.
	 */
	public DatasourceRecord setStoreId(java.lang.String value) {
		setValue(5, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.store_id</code>.
	 */
	@javax.validation.constraints.Size(max = 100)
	public java.lang.String getStoreId() {
		return (java.lang.String) getValue(5);
	}

	/**
	 * Setter for <code>admin.datasource.read_from_remote</code>.
	 */
	public DatasourceRecord setReadFromRemote(java.lang.Boolean value) {
		setValue(6, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.read_from_remote</code>.
	 */
	@javax.validation.constraints.NotNull
	public java.lang.Boolean getReadFromRemote() {
		return (java.lang.Boolean) getValue(6);
	}

	/**
	 * Setter for <code>admin.datasource.date_creation</code>.
	 */
	public DatasourceRecord setDateCreation(java.lang.Long value) {
		setValue(7, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.date_creation</code>.
	 */
	public java.lang.Long getDateCreation() {
		return (java.lang.Long) getValue(7);
	}

	/**
	 * Setter for <code>admin.datasource.analysis_state</code>.
	 */
	public DatasourceRecord setAnalysisState(java.lang.String value) {
		setValue(8, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.analysis_state</code>.
	 */
	@javax.validation.constraints.Size(max = 50)
	public java.lang.String getAnalysisState() {
		return (java.lang.String) getValue(8);
	}

	/**
	 * Setter for <code>admin.datasource.format</code>.
	 */
	public DatasourceRecord setFormat(java.lang.String value) {
		setValue(9, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.format</code>.
	 */
	public java.lang.String getFormat() {
		return (java.lang.String) getValue(9);
	}

	/**
	 * Setter for <code>admin.datasource.permanent</code>.
	 */
	public DatasourceRecord setPermanent(java.lang.Boolean value) {
		setValue(10, value);
		return this;
	}

	/**
	 * Getter for <code>admin.datasource.permanent</code>.
	 */
	@javax.validation.constraints.NotNull
	public java.lang.Boolean getPermanent() {
		return (java.lang.Boolean) getValue(10);
	}

	// -------------------------------------------------------------------------
	// Primary key information
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Record1<java.lang.Integer> key() {
		return (org.jooq.Record1) super.key();
	}

	// -------------------------------------------------------------------------
	// Record11 type implementation
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row11<java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.Long, java.lang.String, java.lang.String, java.lang.Boolean> fieldsRow() {
		return (org.jooq.Row11) super.fieldsRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row11<java.lang.Integer, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.String, java.lang.Boolean, java.lang.Long, java.lang.String, java.lang.String, java.lang.Boolean> valuesRow() {
		return (org.jooq.Row11) super.valuesRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Integer> field1() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field2() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.TYPE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field3() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.URL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field4() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.USERNAME;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field5() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.PWD;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field6() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.STORE_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Boolean> field7() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.READ_FROM_REMOTE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Long> field8() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.DATE_CREATION;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field9() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.ANALYSIS_STATE;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.String> field10() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.FORMAT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Boolean> field11() {
		return org.constellation.database.api.jooq.tables.Datasource.DATASOURCE.PERMANENT;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Integer value1() {
		return getId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value2() {
		return getType();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value3() {
		return getUrl();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value4() {
		return getUsername();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value5() {
		return getPwd();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value6() {
		return getStoreId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Boolean value7() {
		return getReadFromRemote();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Long value8() {
		return getDateCreation();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value9() {
		return getAnalysisState();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.String value10() {
		return getFormat();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Boolean value11() {
		return getPermanent();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value1(java.lang.Integer value) {
		setId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value2(java.lang.String value) {
		setType(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value3(java.lang.String value) {
		setUrl(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value4(java.lang.String value) {
		setUsername(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value5(java.lang.String value) {
		setPwd(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value6(java.lang.String value) {
		setStoreId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value7(java.lang.Boolean value) {
		setReadFromRemote(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value8(java.lang.Long value) {
		setDateCreation(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value9(java.lang.String value) {
		setAnalysisState(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value10(java.lang.String value) {
		setFormat(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord value11(java.lang.Boolean value) {
		setPermanent(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public DatasourceRecord values(java.lang.Integer value1, java.lang.String value2, java.lang.String value3, java.lang.String value4, java.lang.String value5, java.lang.String value6, java.lang.Boolean value7, java.lang.Long value8, java.lang.String value9, java.lang.String value10, java.lang.Boolean value11) {
		return this;
	}

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a detached DatasourceRecord
	 */
	public DatasourceRecord() {
		super(org.constellation.database.api.jooq.tables.Datasource.DATASOURCE);
	}

	/**
	 * Create a detached, initialised DatasourceRecord
	 */
	public DatasourceRecord(java.lang.Integer id, java.lang.String type, java.lang.String url, java.lang.String username, java.lang.String pwd, java.lang.String storeId, java.lang.Boolean readFromRemote, java.lang.Long dateCreation, java.lang.String analysisState, java.lang.String format, java.lang.Boolean permanent) {
		super(org.constellation.database.api.jooq.tables.Datasource.DATASOURCE);

		setValue(0, id);
		setValue(1, type);
		setValue(2, url);
		setValue(3, username);
		setValue(4, pwd);
		setValue(5, storeId);
		setValue(6, readFromRemote);
		setValue(7, dateCreation);
		setValue(8, analysisState);
		setValue(9, format);
		setValue(10, permanent);
	}
}
