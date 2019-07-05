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

package org.constellation.json.binding;

import java.io.Serializable;

/**
 * This model used to pass object from client to server in post method,
 * it contains the style object and all necessary parameters to generate
 * automatically a list of rules for palette.
 *
 * @author Mehdi Sidhoum (Geomatys).
 * @version 0.9
 * @since 0.9
 */
public class WrapperInterval implements Serializable {

    private int dataId;

    private String layerName;

    private String namespace;

    private String dataProvider;

    private Style style;

    private AutoIntervalValues intervalValues;

    private AutoUniqueValues uniqueValues;

    public WrapperInterval(){

    }

    public int getDataId() {
        return dataId;
    }

    public void setDataId(int dataId) {
        this.dataId = dataId;
    }

    public String getLayerName() {
        return layerName;
    }

    public void setLayerName(String layerName) {
        this.layerName = layerName;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getDataProvider() {
        return dataProvider;
    }

    public void setDataProvider(String dataProvider) {
        this.dataProvider = dataProvider;
    }

    public Style getStyle() {
        return style;
    }

    public void setStyle(Style style) {
        this.style = style;
    }

    public AutoIntervalValues getIntervalValues() {
        return intervalValues;
    }

    public void setIntervalValues(AutoIntervalValues intervalValues) {
        this.intervalValues = intervalValues;
    }

    public AutoUniqueValues getUniqueValues() {
        return uniqueValues;
    }

    public void setUniqueValues(AutoUniqueValues uniqueValues) {
        this.uniqueValues = uniqueValues;
    }
}
