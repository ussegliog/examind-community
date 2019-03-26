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
public class SensorXSosRecord extends org.jooq.impl.UpdatableRecordImpl<org.constellation.database.api.jooq.tables.records.SensorXSosRecord> implements org.jooq.Record2<java.lang.Integer, java.lang.Integer> {

	private static final long serialVersionUID = -2012334474;

	/**
	 * Setter for <code>admin.sensor_x_sos.sensor_id</code>.
	 */
	public SensorXSosRecord setSensorId(java.lang.Integer value) {
		setValue(0, value);
		return this;
	}

	/**
	 * Getter for <code>admin.sensor_x_sos.sensor_id</code>.
	 */
	@javax.validation.constraints.NotNull
	public java.lang.Integer getSensorId() {
		return (java.lang.Integer) getValue(0);
	}

	/**
	 * Setter for <code>admin.sensor_x_sos.sos_id</code>.
	 */
	public SensorXSosRecord setSosId(java.lang.Integer value) {
		setValue(1, value);
		return this;
	}

	/**
	 * Getter for <code>admin.sensor_x_sos.sos_id</code>.
	 */
	@javax.validation.constraints.NotNull
	public java.lang.Integer getSosId() {
		return (java.lang.Integer) getValue(1);
	}

	// -------------------------------------------------------------------------
	// Primary key information
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Record2<java.lang.Integer, java.lang.Integer> key() {
		return (org.jooq.Record2) super.key();
	}

	// -------------------------------------------------------------------------
	// Record2 type implementation
	// -------------------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row2<java.lang.Integer, java.lang.Integer> fieldsRow() {
		return (org.jooq.Row2) super.fieldsRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Row2<java.lang.Integer, java.lang.Integer> valuesRow() {
		return (org.jooq.Row2) super.valuesRow();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Integer> field1() {
		return org.constellation.database.api.jooq.tables.SensorXSos.SENSOR_X_SOS.SENSOR_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.Field<java.lang.Integer> field2() {
		return org.constellation.database.api.jooq.tables.SensorXSos.SENSOR_X_SOS.SOS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Integer value1() {
		return getSensorId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.lang.Integer value2() {
		return getSosId();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SensorXSosRecord value1(java.lang.Integer value) {
		setSensorId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SensorXSosRecord value2(java.lang.Integer value) {
		setSosId(value);
		return this;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public SensorXSosRecord values(java.lang.Integer value1, java.lang.Integer value2) {
		return this;
	}

	// -------------------------------------------------------------------------
	// Constructors
	// -------------------------------------------------------------------------

	/**
	 * Create a detached SensorXSosRecord
	 */
	public SensorXSosRecord() {
		super(org.constellation.database.api.jooq.tables.SensorXSos.SENSOR_X_SOS);
	}

	/**
	 * Create a detached, initialised SensorXSosRecord
	 */
	public SensorXSosRecord(java.lang.Integer sensorId, java.lang.Integer sosId) {
		super(org.constellation.database.api.jooq.tables.SensorXSos.SENSOR_X_SOS);

		setValue(0, sensorId);
		setValue(1, sosId);
	}
}
