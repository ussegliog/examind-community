/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2014 Geomatys.
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
package org.constellation.dto;

import java.util.List;
import java.util.Objects;

public class MapContextLayersDTO extends MapContextDTO {

    private List<MapContextStyledLayerDTO> layers;

    public MapContextLayersDTO() {
        super();
    }

    public MapContextLayersDTO(Integer id,
		String  name,
		Integer owner,
		String  description,
		String  crs,
		Double  west,
		Double  north,
		Double  east,
		Double  south,
		String  keywords,
                String userOwner,
                final List<MapContextStyledLayerDTO> layers) {
        super(id, name, owner, description, crs, west, north, east, south, keywords, userOwner);
        this.layers = layers;
    }

    public List<MapContextStyledLayerDTO> getLayers() {
        return layers;
    }

    public void setLayers(List<MapContextStyledLayerDTO> layers) {
        this.layers = layers;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        if (layers != null) {
            sb.append("layers:\n");
            for (MapContextStyledLayerDTO layer : layers) {
                sb.append(layer).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof MapContextLayersDTO && super.equals(obj)) {
            MapContextLayersDTO that = (MapContextLayersDTO) obj;
            return Objects.equals(this.layers, that.layers);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = super.hashCode();
        hash = 79 * hash + Objects.hashCode(this.layers);
        return hash;
    }
}
