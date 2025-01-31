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
package org.constellation.wfs.core;

import org.locationtech.jts.geom.Geometry;
import org.apache.sis.geometry.Envelopes;
import org.apache.sis.referencing.CRS;
import org.apache.sis.util.Utilities;
import org.geotoolkit.filter.visitor.DuplicatingFilterVisitor;
import org.geotoolkit.geometry.BoundingBox;
import org.geotoolkit.geometry.jts.JTS;
import org.opengis.filter.expression.Literal;
import org.opengis.geometry.Envelope;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.TransformException;
import org.opengis.util.FactoryException;

import java.util.logging.Level;
import org.apache.sis.util.logging.Logging;

/**
 *
 * @author Johann Sorel (Geomatys)
 */
public class CrsAdjustFilterVisitor extends DuplicatingFilterVisitor{

    private final CoordinateReferenceSystem baseCrs;
    private final CoordinateReferenceSystem replacementCrs;

    public CrsAdjustFilterVisitor(final CoordinateReferenceSystem baseCrs, final CoordinateReferenceSystem replacementCrs) {
        this.baseCrs = baseCrs;
        this.replacementCrs = replacementCrs;
    }

    @Override
    public Object visit(final Literal expression, final Object extraData) {
        Object obj = expression.getValue();
        try {
            if(obj instanceof BoundingBox){
                BoundingBox bbox = (BoundingBox) obj;
                if(Utilities.equalsIgnoreMetadata(bbox.getCoordinateReferenceSystem(), baseCrs)){
                    final Envelope e = Envelopes.transform(bbox, replacementCrs);
                    final BoundingBox rbbox = new BoundingBox(replacementCrs);
                    rbbox.setBounds(new BoundingBox(e));

                    obj = rbbox;
                }
            }else if(obj instanceof Geometry){

                Geometry geo = (Geometry) obj;
                geo = (Geometry) geo.clone();
                final CoordinateReferenceSystem geoCrs = JTS.findCoordinateReferenceSystem(geo);
                if(geoCrs == null){
                    JTS.setCRS(geo, replacementCrs);
                }else if(Utilities.equalsIgnoreMetadata(geoCrs, baseCrs)){
                    geo = JTS.transform(geo, CRS.findOperation(baseCrs, replacementCrs, null).getMathTransform());
                    JTS.setCRS(geo, replacementCrs);
                }
                obj = geo;

            }
        } catch (FactoryException | TransformException ex) {
            Logging.getLogger("org.constellation.wfs.ws").log(Level.SEVERE, null, ex);
        }

        return getFactory(extraData).literal(obj);
    }

}
