/**
 * Copyright (c) 2010-2013 "Neo Technology," Network Engine for Objects in Lund AB
 * [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Affero General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.gis.spatial.pipes.processing;

import org.neo4j.gis.spatial.pipes.AbstractGeoPipe;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Unites item geometry with itself or with the given geometry. Item geometry is replaced by pipe
 * output unless an alternative property name is given in the constructor.
 */
public class Union extends AbstractGeoPipe {

  private Geometry other = null;

  public Union() {}

  /**
   * @param resultPropertyName property name to use for geometry output
   */
  public Union(String resultPropertyName) {
    super(resultPropertyName);
  }

  public Union(Geometry other) {
    this.other = other;
  }

  public Union(Geometry other, String resultPropertyName) {
    super(resultPropertyName);
    this.other = other;
  }

  @Override
  protected GeoPipeFlow process(GeoPipeFlow flow) {
    if (other == null) {
      setGeometry(flow, flow.getGeometry().union());
    } else {
      setGeometry(flow, flow.getGeometry().union(other));
    }
    return flow;
  }
}
