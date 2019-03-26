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
public class UserXRole extends org.jooq.impl.TableImpl<org.constellation.database.api.jooq.tables.records.UserXRoleRecord> {

	private static final long serialVersionUID = -529240365;

	/**
	 * The reference instance of <code>admin.user_x_role</code>
	 */
	public static final org.constellation.database.api.jooq.tables.UserXRole USER_X_ROLE = new org.constellation.database.api.jooq.tables.UserXRole();

	/**
	 * The class holding records for this type
	 */
	@Override
	public java.lang.Class<org.constellation.database.api.jooq.tables.records.UserXRoleRecord> getRecordType() {
		return org.constellation.database.api.jooq.tables.records.UserXRoleRecord.class;
	}

	/**
	 * The column <code>admin.user_x_role.user_id</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.UserXRoleRecord, java.lang.Integer> USER_ID = createField("user_id", org.jooq.impl.SQLDataType.INTEGER.nullable(false), this, "");

	/**
	 * The column <code>admin.user_x_role.role</code>.
	 */
	public final org.jooq.TableField<org.constellation.database.api.jooq.tables.records.UserXRoleRecord, java.lang.String> ROLE = createField("role", org.jooq.impl.SQLDataType.VARCHAR.length(32).nullable(false), this, "");

	/**
	 * Create a <code>admin.user_x_role</code> table reference
	 */
	public UserXRole() {
		this("user_x_role", null);
	}

	/**
	 * Create an aliased <code>admin.user_x_role</code> table reference
	 */
	public UserXRole(java.lang.String alias) {
		this(alias, org.constellation.database.api.jooq.tables.UserXRole.USER_X_ROLE);
	}

	private UserXRole(java.lang.String alias, org.jooq.Table<org.constellation.database.api.jooq.tables.records.UserXRoleRecord> aliased) {
		this(alias, aliased, null);
	}

	private UserXRole(java.lang.String alias, org.jooq.Table<org.constellation.database.api.jooq.tables.records.UserXRoleRecord> aliased, org.jooq.Field<?>[] parameters) {
		super(alias, org.constellation.database.api.jooq.Admin.ADMIN, aliased, parameters, "");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.UserXRoleRecord> getPrimaryKey() {
		return org.constellation.database.api.jooq.Keys.USER_X_ROLE_PK;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.UserXRoleRecord>> getKeys() {
		return java.util.Arrays.<org.jooq.UniqueKey<org.constellation.database.api.jooq.tables.records.UserXRoleRecord>>asList(org.constellation.database.api.jooq.Keys.USER_X_ROLE_PK, org.constellation.database.api.jooq.Keys.USER_X_ROLE_USER_ID_KEY);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public java.util.List<org.jooq.ForeignKey<org.constellation.database.api.jooq.tables.records.UserXRoleRecord, ?>> getReferences() {
		return java.util.Arrays.<org.jooq.ForeignKey<org.constellation.database.api.jooq.tables.records.UserXRoleRecord, ?>>asList(org.constellation.database.api.jooq.Keys.USER_X_ROLE__USER_X_ROLE_USER_ID_FK, org.constellation.database.api.jooq.Keys.USER_X_ROLE__USER_X_ROLE_ROLE_FK);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public org.constellation.database.api.jooq.tables.UserXRole as(java.lang.String alias) {
		return new org.constellation.database.api.jooq.tables.UserXRole(alias, this);
	}

	/**
	 * Rename this table
	 */
	public org.constellation.database.api.jooq.tables.UserXRole rename(java.lang.String name) {
		return new org.constellation.database.api.jooq.tables.UserXRole(name, null);
	}
}
