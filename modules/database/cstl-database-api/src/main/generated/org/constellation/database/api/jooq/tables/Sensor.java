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
public class Sensor extends org.jooq.impl.TableImpl<org.constellation.database.api.jooq.tables.records.SensorRecord> {

	private static final long serialVersionUID = 1556891511;

	/**
	 * The reference instance of <code>admin.sensor</code>
	 */
	public static final org.constellation.database.api.jooq.tables.Sensor SENSOR = new org.constellation.database.api.jooq.tables.Sensor();

	/**
	 * The class holding records for this type
	 */
	@Override
	public java.lang.Class<org.constellation.database.api.jooq.tables.records.SensorRecord> getRecordType() {
		return org.constellation.database.api.jooq.tables.records.SensorRecord.class;
	}

	/**
	 * The column <code>admin.sensor.id</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.Integer> ID = createField("id", org.jooq.impl.SQLDataType.INTEGER.nullable(false).defaulted(true), this, "");

	/**
	 * The column <code>admin.sensor.identifier</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.String> IDENTIFIER = createField("identifier", org.jooq.impl.SQLDataType.VARCHAR.length(512).nullable(false), this, "");

	/**
	 * The column <code>admin.sensor.type</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.String> TYPE = createField("type", org.jooq.impl.SQLDataType.VARCHAR.length(64).nullable(false), this, "");

	/**
	 * The column <code>admin.sensor.parent</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.String> PARENT = createField("parent", org.jooq.impl.SQLDataType.VARCHAR.length(512), this, "");

	/**
	 * The column <code>admin.sensor.owner</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.Integer> OWNER = createField("owner", org.jooq.impl.SQLDataType.INTEGER, this, "");

	/**
	 * The column <code>admin.sensor.date</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.Long> DATE = createField("date", org.jooq.impl.SQLDataType.BIGINT, this, "");

	/**
	 * The column <code>admin.sensor.provider_id</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.Integer> PROVIDER_ID = createField("provider_id", org.jooq.impl.SQLDataType.INTEGER, this, "");

	/**
	 * The column <code>admin.sensor.profile</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.String> PROFILE = createField("profile", org.jooq.impl.SQLDataType.VARCHAR.length(255), this, "");

	/**
	 * Create a <code>admin.sensor</code> table reference
	 */
	public Sensor() {
		this("sensor", null);
	}

	/**
	 * Create an aliased <code>admin.sensor</code> table reference
	 */
	public Sensor(java.lang.String alias) {
		this(alias, org.constellation.database.api.jooq.tables.Sensor.SENSOR);
	}

	private Sensor(java.lang.String alias, org.jooq.Table<org.constellation.database.api.jooq.tables.records.SensorRecord> aliased) {
		this(alias, aliased, null);
	}

	private Sensor(java.lang.String alias, org.jooq.Table<org.constellation.database.api.jooq.tables.records.SensorRecord> aliased, org.jooq.Field<?>[] parameters) {
		super(alias, org.constellation.database.api.jooq.Admin.ADMIN, aliased, parameters, "");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Identity<org.constellation.database.api.jooq.tables.records.SensorRecord, java.lang.Integer> getIdentity() {
		return org.constellation.database.api.jooq.Keys.IDENTITY_SENSOR;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.SensorRecord> getPrimaryKey() {
		return org.constellation.database.api.jooq.Keys.SENSOR_PK;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.SensorRecord>> getKeys() {
		return java.util.Arrays.<org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.SensorRecord>>asList(org.constellation.database.api.jooq.Keys.SENSOR_PK, org.constellation.database.api.jooq.Keys.SENSOR_ID_UQ);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.ForeignKey<org.constellation.database.api.jooq.tables.records.SensorRecord, ?>> getReferences() {
		return java.util.Arrays.<org.jooq.ForeignKey<org.constellation.database.api.jooq.tables.records.SensorRecord, ?>>asList(org.constellation.database.api.jooq.Keys.SENSOR__SENSOR_PARENT_FK, org.constellation.database.api.jooq.Keys.SENSOR__SENSOR_OWNER_FK, org.constellation.database.api.jooq.Keys.SENSOR__SENSOR_PROVIDER_ID_FK);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.constellation.database.api.jooq.tables.Sensor as(java.lang.String alias) {
		return new org.constellation.database.api.jooq.tables.Sensor(alias, this);
	}

	/**
	 * Rename this table
	 */
	public org.constellation.database.api.jooq.tables.Sensor rename(java.lang.String name) {
		return new org.constellation.database.api.jooq.tables.Sensor(name, null);
	}
}
