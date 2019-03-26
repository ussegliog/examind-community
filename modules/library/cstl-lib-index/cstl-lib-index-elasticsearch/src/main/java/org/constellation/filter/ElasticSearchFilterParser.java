/*
 *    Constellation - An open source and standard compliant SDI
 *    http://www.constellation-sdi.org
 *
 * Copyright 2015 Geomatys.
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
package org.constellation.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;
import org.constellation.metadata.index.elasticsearch.SpatialFilterBuilder;
import org.constellation.metadata.index.elasticsearch.SpatialQuery;
import org.geotoolkit.csw.xml.QueryConstraint;
import org.geotoolkit.ogc.xml.v110.AbstractIdType;
import org.geotoolkit.ogc.xml.v110.AndType;
import org.geotoolkit.ogc.xml.v110.FilterType;
import org.geotoolkit.ogc.xml.v110.LiteralType;
import org.geotoolkit.ogc.xml.v110.OrType;
import org.geotoolkit.ogc.xml.v110.PropertyIsEqualToType;
import org.geotoolkit.ogc.xml.v110.PropertyNameType;
import org.opengis.filter.Filter;

/**
 *
 * @author Guilhem Legal (Geomatys)
 */
public class ElasticSearchFilterParser implements FilterParser {

    private static final String DEFAULT_FIELD = "metafile:doc";
    
    @Override
    public org.geotoolkit.index.SpatialQuery getQuery(final QueryConstraint constraint, final Map<String, QName> variables, final Map<String, String> prefixs, final List<QName> typeNames) throws FilterParserException {
        //if the constraint is null we make a null filter
        if (constraint == null)  {
            return new SpatialQuery(getTypeQuery(typeNames), null);
        } else {
            final FilterType filter = FilterParserUtils.getFilterFromConstraint(constraint);
            return getQuery(filter, variables, prefixs, typeNames);
        }
    }
    
    private String getTypeQuery(final List<QName> typeNames) {
        if (typeNames != null && !typeNames.isEmpty()) {
            StringBuilder query = new StringBuilder();
            for (QName typeName : typeNames) {
                query.append("objectType_sort:").append(typeName.getLocalPart()).append(" OR ");
            }
            final int length = query.length();
            query.delete(length -3, length);
            return query.toString();
        }
        return DEFAULT_FIELD;
    }
    
    private Filter getTypeFilter(final List<QName> typeNames) {
        if (typeNames != null && !typeNames.isEmpty()) {
            if (typeNames.size() == 1) {
                final QName typeName = typeNames.get(0);
                return new PropertyIsEqualToType(new LiteralType(typeName.getLocalPart()), new PropertyNameType("objectType_sort"), Boolean.FALSE);
            } else {
                final List<Object> operators = new ArrayList<>();
                for (QName typeName : typeNames) {
                    operators.add(new PropertyIsEqualToType(new LiteralType(typeName.getLocalPart()), new PropertyNameType("objectType_sort"), Boolean.FALSE));
                }
                return new OrType(operators.toArray());
            }
        }
        return null;
    }
    
    protected SpatialQuery getQuery(FilterType queryFilter, Map<String, QName> variables, Map<String, String> prefixs, List<QName> typeNames) throws FilterParserException {
        final Object typeFilter =  getTypeFilter(typeNames);
        final FilterType filter;
        if (typeFilter != null) {
            filter = new FilterType(new AndType(queryFilter, typeFilter));
        } else {
            filter = queryFilter;
        }
        SpatialQuery response = null;
        try {
            if (filter != null) {
                // we treat logical Operators like AND, OR, ...
                if (filter.getLogicOps() != null) {
                    response = new SpatialQuery(SpatialFilterBuilder.build(filter.getLogicOps().getValue()));

                // we treat directly comparison operator: PropertyIsLike, IsNull, IsBetween, ...
                } else if (filter.getComparisonOps() != null) {
                    response = new SpatialQuery(SpatialFilterBuilder.build(filter.getComparisonOps().getValue()));

                // we treat spatial constraint : BBOX, Beyond, Overlaps, ...
                } else if (filter.getSpatialOps() != null) {
                    response = new SpatialQuery(SpatialFilterBuilder.build(filter.getSpatialOps().getValue()));

                // we treat time operator: TimeAfter, TimeBefore, TimeDuring, ...
                } else if (filter.getTemporalOps()!= null) {
                    response = new SpatialQuery(SpatialFilterBuilder.build(filter.getTemporalOps().getValue()));

                } else if (filter.getId() != null && !filter.getId().isEmpty()) {
                    response = new SpatialQuery(treatIDOperator(filter.getId()), null);
                
                }  else {
                    throw new FilterParserException("Uncorrrect or empty filter specified.");
                }
            }
        } catch (IOException ex) {
            throw new FilterParserException(ex);
        }
        return response;
    }
    
    /**
     * Return a piece of query for An Id filter.
     *
     * @param jbIdsOps an Id filter
     * @return a piece of query.
     */
    protected String treatIDOperator(final List<JAXBElement<? extends AbstractIdType>> jbIdsOps) {
        //TODO
        if (true) {
            throw new UnsupportedOperationException("Not supported yet.");
        }
        return "";
    }
}
