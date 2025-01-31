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
package org.constellation.dto.service.config.wxs;

import org.constellation.util.DataReference;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import org.constellation.dto.Filter;
import org.constellation.dto.Reference;
import org.constellation.dto.StringList;

/**
 *
 * @author Guilhem Legal (Geomatys)
 * @author Cédric Briançon (Geomatys)
 * @author Quentin Boileau (Geomatys)
 * @since 0.6
 */
@XmlRootElement(name="LayerConfig")
@XmlAccessorType(XmlAccessType.FIELD)
public class Layer {
    @XmlAttribute
    private Integer id;

    @XmlAttribute
    private Integer dataId;

    @XmlAttribute
    private QName name;

    @XmlAttribute
    private String alias;

    @XmlAttribute
    private Long version;

    @XmlElement(name="style")
    private List<DataReference> styles;

    @XmlElement(name="filter")
    private Filter filter;

    @Deprecated
    @XmlElement(name="title")
    private String title;

    @XmlElement(name="multiLangTitle")
    private Map<String, String> multiLangTitle;

    @Deprecated
    @XmlElement(name="abstract")
    private String abstrac;

    @XmlElement(name="multiLangAbstract")
    private Map<String, String> multiLangAbstract;

    @Deprecated
    @XmlElement(name="keyword")
    private List<String> keywords;

    @XmlElement(name="multiLangKeywords")
    private Map<String, StringList> multiLangKeywords;

    @XmlElement(name="metadataURL")
    private List<FormatURL> metadataURL;

    @XmlElement(name="dataURL")
    private FormatURL dataURL;

    @XmlElement(name="authorityURL")
    private FormatURL authorityURL;

    @XmlElement(name="identifier")
    private Reference identifier;

    @XmlElement(name="attribution")
    private AttributionType attribution;

    @XmlElement(name="opaque")
    private Boolean opaque;

    @XmlElement(name="crs")
    private List<String> crs;

    @XmlElement(name="dimension")
    private List<DimensionDefinition> dimensions;

    @XmlElement(name="Modified-Date")
    private Date date;

    @XmlElement(name="providerType")
    private String providerType;

    @XmlElement(name="providerID")
    private String providerID;

    @XmlElement(name = "owner")
    private Integer owner;

    @XmlElementWrapper(name="featureInfos")
    @XmlElement(name="FeatureInfo")
    private List<GetFeatureInfoCfg> getFeatureInfoCfgs;

    public Layer() {
    }

    public Layer(final QName name) {
        this.name = name;
    }

    public Layer(final Integer id, final QName name) {
        this.id = id;
        this.name = name;
    }

    public Layer(final Integer id, final QName name, final List<DataReference> styles) {
        this.id = id;
        this.name = name;
        this.styles = styles;
    }

    public Layer(final Integer id, final QName name, final String title, final String abstrac, final List<String> keywords, final FormatURL metadataURL,
            final FormatURL dataURL, final FormatURL authorityURL, final Reference identifier, final AttributionType attribution, final Boolean opaque,
            final List<String> crs)
    {
        this(id, name, null, null, null, title, abstrac, keywords, metadataURL, dataURL, authorityURL, identifier, attribution, opaque, crs);
    }

    public Layer(final Integer id, final QName name, final List<DataReference> styles, final Filter filter, final String alias, final String title, final String abstrac, final List<String> keywords, final FormatURL metadataURL,
            final FormatURL dataURL, final FormatURL authorityURL, final Reference identifier, final AttributionType attribution, final Boolean opaque,
            final List<String> crs)
    {
        this(id, name, styles, filter, alias, title, abstrac, keywords, metadataURL, dataURL, authorityURL, identifier, attribution, opaque, crs, null);
    }

    public Layer(final Integer id, final QName name, final List<DataReference> styles, final Filter filter, final String alias, final String title, final String abstrac, final List<String> keywords, final FormatURL metadataURL,
            final FormatURL dataURL, final FormatURL authorityURL, final Reference identifier, final AttributionType attribution, final Boolean opaque,
            final List<String> crs, final List<DimensionDefinition> dimensions) {
        this(id, name, styles, filter, alias, title, abstrac, keywords, metadataURL, dataURL, authorityURL, identifier, attribution, opaque, crs, dimensions, null);
    }

    public Layer(final Integer id, final QName name, final List<DataReference> styles, final Filter filter, final String alias, final String title, final String abstrac, final List<String> keywords, final FormatURL metadataURL,
                 final FormatURL dataURL, final FormatURL authorityURL, final Reference identifier, final AttributionType attribution, final Boolean opaque,
                 final List<String> crs, final List<DimensionDefinition> dimensions, final Date version) {
        this.id           = id;
        this.name         = name;
        this.styles       = styles;
        this.filter       = filter;
        this.title        = title;
        this.abstrac      = abstrac;
        this.keywords     = keywords;
        this.metadataURL  = metadataURL != null ? Arrays.asList(metadataURL): null;
        this.dataURL      = dataURL;
        this.authorityURL = authorityURL;
        this.identifier   = identifier;
        this.attribution  = attribution;
        this.opaque       = opaque;
        this.crs          = crs;
        this.dimensions   = dimensions;
        this.version      = version != null ? Long.valueOf(version.getTime()): null;
        this.alias        = alias;
        this.getFeatureInfoCfgs = null;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getDataId() {
        return dataId;
    }

    public void setDataId(Integer dataId) {
        this.dataId = dataId;
    }

    /**
     * @return the name
     */
    public QName getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(final QName name) {
        this.name = name;
    }

    public List<DataReference> getStyles() {
        if (styles == null) {
            styles = new ArrayList<>();
        }
        return styles;
    }

    public void setStyles(final List<DataReference> styles) {
        this.styles = styles;
    }

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(final Filter filter) {
        this.filter = filter;
    }

    public String getAlias() {
        return alias;
    }

    public final void setAlias(String alias) {
        this.alias = alias;
    }

    /**
     * Get layer data version Date in timestamp
     * @return timestamp or null if not defined
     */
    public Long getVersion() {
        return version;
    }

    /**
     * Set layer data version timestamp date.
     * @param version timestamp
     */
    public void setVersion(Long version) {
        this.version = version;
    }

    @Deprecated
    public String getTitle() {
        return title;
    }

    @Deprecated
    public void setTitle(final String title) {
        this.title = title;
    }

    @Deprecated
    public String getAbstrac() {
        return abstrac;
    }

    @Deprecated
    public void setAbstrac(final String abstrac) {
        this.abstrac = abstrac;
    }

    @Deprecated
    public List<String> getKeywords() {
        if (keywords == null) {
            keywords = new ArrayList<>();
        }
        return keywords;
    }

    @Deprecated
    public void setKeywords(final List<String> keywords) {
        this.keywords = keywords;
    }

    public List<FormatURL> getMetadataURL() {
        return metadataURL;
    }

    public void setMetadataURL(final List<FormatURL> metadataURL) {
        this.metadataURL = metadataURL;
    }

    public FormatURL getDataURL() {
        return dataURL;
    }

    public void setDataURL(final FormatURL dataURL) {
        this.dataURL = dataURL;
    }

    public FormatURL getAuthorityURL() {
        return authorityURL;
    }

    public void setAuthorityURL(final FormatURL authorityURL) {
        this.authorityURL = authorityURL;
    }

    /**
     * @return the identifier
     */
    public Reference getIdentifier() {
        return identifier;
    }

    /**
     * @return the attribution
     */
    public AttributionType getAttribution() {
        return attribution;
    }

    /**
     * @param attribution the attribution to set
     */
    public void setAttribution(final AttributionType attribution) {
        this.attribution = attribution;
    }

    public Boolean getOpaque() {
        return opaque;
    }

    public void setOpaque(final Boolean opaque) {
        this.opaque = opaque;
    }

    public String getProviderID() {
        return providerID;
    }

    public void setProviderID(final String providerID) {
        this.providerID = providerID;
    }

    /**
     * @return the crs
     */
    public List<String> getCrs() {
        if (crs == null) {
            crs = new ArrayList<>();
        }
        return crs;
    }

    /**
     * @param crs the crs to set
     */
    public void setCrs(final List<String> crs) {
        this.crs = crs;
    }

    public List<DimensionDefinition> getDimensions() {
        if (dimensions == null) {
            dimensions = new ArrayList<>();
        }
        return dimensions;
    }

    public void setDimensions(final List<DimensionDefinition> dimensions) {
        this.dimensions = dimensions;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(final Date date) {
        this.date = date;
    }

    public String getProviderType() {
        return providerType;
    }

    public void setProviderType(final String providerType) {
        this.providerType = providerType;
    }

    public Integer getOwner() {
        return owner;
    }

    public void setOwner(final Integer owner) {
        this.owner = owner;
    }

    /**
     * Return custom getFeatureInfos
     * @return a list with GetFeatureInfoCfg, can be null.
     */
    public List<GetFeatureInfoCfg> getGetFeatureInfoCfgs() {
        if (getFeatureInfoCfgs == null) {
            getFeatureInfoCfgs = new ArrayList<>();
        }
        return getFeatureInfoCfgs;
    }

    public void setGetFeatureInfoCfgs(final List<GetFeatureInfoCfg> getFeatureInfoCfgs) {
        this.getFeatureInfoCfgs = getFeatureInfoCfgs;
    }

    /**
     * @return the multiLangTitle
     */
    public Map<String, String> getMultiLangTitle() {
        if (multiLangTitle == null) {
            multiLangTitle = new HashMap<>();
        }
        return multiLangTitle;
    }

    /**
     * @param multiLangTitle the multiLangTitle to set
     */
    public void setMultiLangTitle(Map<String, String> multiLangTitle) {
        this.multiLangTitle = multiLangTitle;
    }

    /**
     * @return the multiLangAbstract
     */
    public Map<String, String> getMultiLangAbstract() {
        if (multiLangAbstract == null) {
            multiLangAbstract = new HashMap<>();
        }
        return multiLangAbstract;
    }

    /**
     * @param multiLangAbstract the multiLangAbstract to set
     */
    public void setMultiLangAbstract(Map<String, String> multiLangAbstract) {
        this.multiLangAbstract = multiLangAbstract;
    }

    /**
     * @return the multiLangKeywords
     */
    public Map<String,StringList> getMultiLangKeywords() {
        if (multiLangKeywords == null) {
            multiLangKeywords = new HashMap<>();
        }
        return multiLangKeywords;
    }

    /**
     * @param multiLangKeywords the multiLangKeywords to set
     */
    public void setMultiLangKeywords(Map<String, StringList> multiLangKeywords) {
        this.multiLangKeywords = multiLangKeywords;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("[Layer]");
        if (id != null) {
            sb.append("id:\n").append(id).append('\n');
        }
        if (dataId != null) {
            sb.append("dataId:\n").append(dataId).append('\n');
        }
        if (name != null) {
            sb.append("name:\n").append(name).append('\n');
        }
        if (styles != null && !styles.isEmpty()) {
            for (DataReference style : styles) {
                sb.append("style:\n").append(style).append('\n');
            }
        }
        if (filter != null) {
            sb.append("filter:\n").append(filter).append('\n');
        }
        if (alias != null) {
            sb.append("alias:\n").append(alias).append('\n');
        }
        if (version != null) {
            sb.append("version:\n").append(version).append('\n');
        }
        if (abstrac != null) {
            sb.append("abstract=").append(abstrac).append('\n');
        }
        if (attribution != null) {
            sb.append("attribution:\n").append(attribution).append('\n');
        }
        if (authorityURL != null) {
            sb.append("authorityURL:\n").append(authorityURL).append('\n');
        }
        if (crs != null) {
            sb.append("crs:\n").append(crs).append('\n');
        }
        if (dataURL != null) {
            sb.append("dataURL:\n").append(dataURL).append('\n');
        }
        if (identifier != null) {
            sb.append("identifier:\n").append(identifier).append('\n');
        }
        if (keywords != null) {
            sb.append("keywords:\n").append(keywords).append('\n');
        }
        if (metadataURL != null) {
            sb.append("metadataURL:\n").append(metadataURL).append('\n');
        }
        if (opaque != null) {
            sb.append("opaque:\n").append(opaque).append('\n');
        }
        if (title != null) {
            sb.append("title:\n").append(title).append('\n');
        }
        if (multiLangTitle != null && !multiLangTitle.isEmpty()) {
            sb.append("multi language titles:\n").append('\n');
            for (final Entry<String, String> mtitle : multiLangTitle.entrySet()) {
                sb.append(mtitle.getKey()).append(":").append(mtitle.getValue()).append('\n');
            }
        }
        if (multiLangAbstract != null && !multiLangAbstract.isEmpty()) {
            sb.append("multi language abstracts:\n").append('\n');
            for (final Entry<String, String> mAbs : multiLangAbstract.entrySet()) {
                sb.append(mAbs.getKey()).append(":").append(mAbs.getValue()).append('\n');
            }
        }
        if (multiLangKeywords != null && !multiLangKeywords.isEmpty()) {
            sb.append("multi language keywords:\n").append('\n');
            for (final Entry<String, StringList> mKeys : multiLangKeywords.entrySet()) {
                sb.append(mKeys.getKey()).append(":").append(mKeys.getValue()).append('\n');
            }
        }
        if (dimensions != null && !dimensions.isEmpty()) {
            sb.append("dimensions:\n");
            for (final DimensionDefinition dimension : dimensions) {
                sb.append("dimension:\n").append(dimension).append('\n');
            }
        }
        if (getFeatureInfoCfgs != null && !getFeatureInfoCfgs.isEmpty()) {
            sb.append("featureInfos:\n").append('\n');
            for (final GetFeatureInfoCfg getFeatureInfoCfg : getFeatureInfoCfgs) {
                sb.append("featureInfos:\n").append(getFeatureInfoCfg).append('\n');
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof Layer) {
            final Layer that = (Layer) obj;
            return Objects.equals(this.abstrac,      that.abstrac) &&
                   Objects.equals(this.id,           that.id) &&
                   Objects.equals(this.dataId,       that.dataId) &&
                   Objects.equals(this.attribution,  that.attribution) &&
                   Objects.equals(this.authorityURL, that.authorityURL) &&
                   Objects.equals(this.crs,          that.crs) &&
                   Objects.equals(this.dataURL,      that.dataURL) &&
                   Objects.equals(this.filter,       that.filter) &&
                   Objects.equals(this.alias,        that.alias) &&
                   Objects.equals(this.version,      that.version) &&
                   Objects.equals(this.identifier,   that.identifier) &&
                   Objects.equals(this.keywords,     that.keywords) &&
                   Objects.equals(this.metadataURL,  that.metadataURL) &&
                   Objects.equals(this.name,         that.name) &&
                   Objects.equals(this.styles,       that.styles) &&
                   Objects.equals(this.opaque,       that.opaque) &&
                   Objects.equals(this.multiLangTitle,    that.multiLangTitle) &&
                   Objects.equals(this.multiLangAbstract, that.multiLangAbstract) &&
                   Objects.equals(this.multiLangKeywords, that.multiLangAbstract) &&
                   Objects.equals(this.title,        that.title) &&
                   Objects.equals(this.getFeatureInfoCfgs, that.getFeatureInfoCfgs);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 79 * hash + (this.id != null ? this.id.hashCode() : 0);
        hash = 79 * hash + (this.dataId != null ? this.dataId.hashCode() : 0);
        hash = 79 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 79 * hash + (this.styles != null ? this.styles.hashCode() : 0);
        hash = 79 * hash + (this.filter != null ? this.filter.hashCode() : 0);
        hash = 79 * hash + (this.alias != null ? this.alias.hashCode() : 0);
        hash = 79 * hash + (this.version != null ? this.version.hashCode() : 0);
        hash = 79 * hash + (this.title != null ? this.title.hashCode() : 0);
        hash = 79 * hash + (this.abstrac != null ? this.abstrac.hashCode() : 0);
        hash = 79 * hash + (this.keywords != null ? this.keywords.hashCode() : 0);
        hash = 79 * hash + (this.metadataURL != null ? this.metadataURL.hashCode() : 0);
        hash = 79 * hash + (this.dataURL != null ? this.dataURL.hashCode() : 0);
        hash = 79 * hash + (this.authorityURL != null ? this.authorityURL.hashCode() : 0);
        hash = 79 * hash + (this.identifier != null ? this.identifier.hashCode() : 0);
        hash = 79 * hash + (this.attribution != null ? this.attribution.hashCode() : 0);
        hash = 79 * hash + (this.opaque != null ? this.opaque.hashCode() : 0);
        hash = 79 * hash + (this.crs != null ? this.crs.hashCode() : 0);
        hash = 79 * hash + (this.getFeatureInfoCfgs != null ? this.getFeatureInfoCfgs.hashCode() : 0);
        hash = 79 * hash + (this.multiLangTitle != null ? this.multiLangTitle.hashCode() : 0);
        hash = 79 * hash + (this.multiLangAbstract != null ? this.multiLangAbstract.hashCode() : 0);
        hash = 79 * hash + (this.multiLangKeywords != null ? this.multiLangKeywords.hashCode() : 0);
        return hash;
    }
}
