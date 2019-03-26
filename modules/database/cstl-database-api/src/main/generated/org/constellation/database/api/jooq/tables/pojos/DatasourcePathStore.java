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
public class DatasourcePathStore implements java.io.Serializable {

	private static final long serialVersionUID = 475230084;

	private java.lang.Integer datasourceId;
	private java.lang.String  path;
	private java.lang.String  store;
	private java.lang.String  type;

	public DatasourcePathStore() {}

	public DatasourcePathStore(
		java.lang.Integer datasourceId,
		java.lang.String  path,
		java.lang.String  store,
		java.lang.String  type
	) {
		this.datasourceId = datasourceId;
		this.path = path;
		this.store = store;
		this.type = type;
	}

	@javax.validation.constraints.NotNull
	public java.lang.Integer getDatasourceId() {
		return this.datasourceId;
	}

	public DatasourcePathStore setDatasourceId(java.lang.Integer datasourceId) {
		this.datasourceId = datasourceId;
		return this;
	}

	@javax.validation.constraints.NotNull
	public java.lang.String getPath() {
		return this.path;
	}

	public DatasourcePathStore setPath(java.lang.String path) {
		this.path = path;
		return this;
	}

	@javax.validation.constraints.NotNull
	@javax.validation.constraints.Size(max = 500)
	public java.lang.String getStore() {
		return this.store;
	}

	public DatasourcePathStore setStore(java.lang.String store) {
		this.store = store;
		return this;
	}

	@javax.validation.constraints.NotNull
	public java.lang.String getType() {
		return this.type;
	}

	public DatasourcePathStore setType(java.lang.String type) {
		this.type = type;
		return this;
	}
}
