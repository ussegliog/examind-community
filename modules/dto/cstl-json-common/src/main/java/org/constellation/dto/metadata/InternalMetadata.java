/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2018 Geomatys.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.constellation.dto.metadata;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
@XmlRootElement
public class InternalMetadata implements Serializable {

    private Integer id;
    private String metadataId;
    private String metadataIso;

    public InternalMetadata() {
    }

    public InternalMetadata(Integer id, String metadataId, String metadataIso) {
        this.id = id;
        this.metadataId = metadataId;
        this.metadataIso = metadataIso;
    }

    /**
     * @return the id
     */
    public Integer getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * @return the metadataId
     */
    public String getMetadataId() {
        return metadataId;
    }

    /**
     * @param metadataId the metadataId to set
     */
    public void setMetadataId(String metadataId) {
        this.metadataId = metadataId;
    }

    /**
     * @return the metadataIso
     */
    public String getMetadataIso() {
        return metadataIso;
    }

    /**
     * @param metadataIso the metadataIso to set
     */
    public void setMetadataIso(String metadataIso) {
        this.metadataIso = metadataIso;
    }
}
