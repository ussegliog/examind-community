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
public class UserXRoleDao extends org.jooq.impl.DAOImpl<org.constellation.database.api.jooq.tables.records.UserXRoleRecord, org.constellation.database.api.jooq.tables.pojos.UserXRole, org.jooq.Record2<java.lang.Integer, java.lang.String>> {

	/**
	 * Create a new UserXRoleDao without any configuration
	 */
	public UserXRoleDao() {
		super(org.constellation.database.api.jooq.tables.UserXRole.USER_X_ROLE, org.constellation.database.api.jooq.tables.pojos.UserXRole.class);
	}

	/**
	 * Create a new UserXRoleDao with an attached configuration
	 */
	public UserXRoleDao(org.jooq.Configuration configuration) {
		super(org.constellation.database.api.jooq.tables.UserXRole.USER_X_ROLE, org.constellation.database.api.jooq.tables.pojos.UserXRole.class, configuration);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected org.jooq.Record2<java.lang.Integer, java.lang.String> getId(org.constellation.database.api.jooq.tables.pojos.UserXRole object) {
		return compositeKeyRecord(object.getUserId(), object.getRole());
	}

	/**
	 * Fetch records that have <code>user_id IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.UserXRole> fetchByUserId(java.lang.Integer... values) {
		return fetch(org.constellation.database.api.jooq.tables.UserXRole.USER_X_ROLE.USER_ID, values);
	}

	/**
	 * Fetch a unique record that has <code>user_id = value</code>
	 */
	public org.constellation.database.api.jooq.tables.pojos.UserXRole fetchOneByUserId(java.lang.Integer value) {
		return fetchOne(org.constellation.database.api.jooq.tables.UserXRole.USER_X_ROLE.USER_ID, value);
	}

	/**
	 * Fetch records that have <code>role IN (values)</code>
	 */
	public java.util.List<org.constellation.database.api.jooq.tables.pojos.UserXRole> fetchByRole(java.lang.String... values) {
		return fetch(org.constellation.database.api.jooq.tables.UserXRole.USER_X_ROLE.ROLE, values);
	}
}
