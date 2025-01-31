package org.constellation.dto;

import java.io.Serializable;

/**
 * @author Fabien Bernard (Geomatys).
 */
public class SensorReference implements Serializable {

    private static final long serialVersionUID = 7557342300322707217L;


    protected Integer id;

    protected String identifier;


    public SensorReference() {

    }

    public SensorReference(Integer id, String identifier) {
        this.id = id;
        this.identifier = identifier;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
}
