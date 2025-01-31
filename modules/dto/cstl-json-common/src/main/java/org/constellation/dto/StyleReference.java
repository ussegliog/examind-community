package org.constellation.dto;

import java.io.Serializable;

/**
 * @author Fabien Bernard (Geomatys).
 */
public class StyleReference implements Serializable {

    private static final long serialVersionUID = -3049058751205624982L;


    protected Integer id;

    protected String name;

    protected Integer providerId;

    protected String providerIdentifier;

    public StyleReference() {

    }

    public StyleReference(Integer id, String name, Integer providerId, String providerIdentifier) {
        this.id = id;
        this.name = name;
        this.providerId = providerId;
        this.providerIdentifier = providerIdentifier;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getProviderId() {
        return providerId;
    }

    public void setProviderId(Integer providerId) {
        this.providerId = providerId;
    }

    public String getProviderIdentifier() {
        return providerIdentifier;
    }

    public void setProviderIdentifier(String providerIdentifier) {
        this.providerIdentifier = providerIdentifier;
    }
}
