/**
 * This class is generated by jOOQ
 */
package org.constellation.database.api.jooq.tables.pojos;

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
public class Sensor implements java.io.Serializable {

	private static final long serialVersionUID = 208649053;

	private java.lang.Integer id;
	private java.lang.String  identifier;
	private java.lang.String  type;
	private java.lang.String  parent;
	private java.lang.Integer owner;
	private java.lang.Long    date;
	private java.lang.Integer providerId;
	private java.lang.String  profile;

	public Sensor() {}

	public Sensor(
		java.lang.Integer id,
		java.lang.String  identifier,
		java.lang.String  type,
		java.lang.String  parent,
		java.lang.Integer owner,
		java.lang.Long    date,
		java.lang.Integer providerId,
		java.lang.String  profile
	) {
		this.id = id;
		this.identifier = identifier;
		this.type = type;
		this.parent = parent;
		this.owner = owner;
		this.date = date;
		this.providerId = providerId;
		this.profile = profile;
	}

	@javax.validation.constraints.NotNull
	public java.lang.Integer getId() {
		return this.id;
	}

	public Sensor setId(java.lang.Integer id) {
		this.id = id;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 512)
	public java.lang.String getIdentifier() {
		return this.identifier;
	}

	public Sensor setIdentifier(java.lang.String identifier) {
		this.identifier = identifier;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 64)
	public java.lang.String getType() {
		return this.type;
	}

	public Sensor setType(java.lang.String type) {
		this.type = type;
		return this;
	}

	@javax.validation.constraints.Size(max = 512)
	public java.lang.String getParent() {
		return this.parent;
	}

	public Sensor setParent(java.lang.String parent) {
		this.parent = parent;
		return this;
	}

	public java.lang.Integer getOwner() {
		return this.owner;
	}

	public Sensor setOwner(java.lang.Integer owner) {
		this.owner = owner;
		return this;
	}

	public java.lang.Long getDate() {
		return this.date;
	}

	public Sensor setDate(java.lang.Long date) {
		this.date = date;
		return this;
	}

	public java.lang.Integer getProviderId() {
		return this.providerId;
	}

	public Sensor setProviderId(java.lang.Integer providerId) {
		this.providerId = providerId;
		return this;
	}

	@javax.validation.constraints.Size(max = 255)
	public java.lang.String getProfile() {
		return this.profile;
	}

	public Sensor setProfile(java.lang.String profile) {
		this.profile = profile;
		return this;
	}
}
