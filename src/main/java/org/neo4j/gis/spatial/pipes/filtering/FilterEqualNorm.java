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
package org.neo4j.gis.spatial.pipes.filtering;

import org.neo4j.gis.spatial.pipes.AbstractFilterGeoPipe;
import org.neo4j.gis.spatial.pipes.GeoPipeFlow;
import com.vividsolutions.jts.geom.Geometry;


/**
 * Find geometries equal to the given geometry (with the same number of vertices, in the same
 * locations).<br>
 * <br>
 * See <a href="http://docs.geotools.org/latest/userguide/library/jts/equals.html">GeoTools
 * documentation</a>.
 */
public class FilterEqualNorm extends AbstractFilterGeoPipe {

  private Geometry other;
  private double tolerance;

  public FilterEqualNorm(Geometry other) {
    this(other, 0);
  }

  public FilterEqualNorm(Geometry other, double tolerance) {
    this.other = other.norm();
    this.tolerance = tolerance;
  }

  @Override
  protected boolean validate(GeoPipeFlow flow) {
    Geometry current = flow.getGeometry().norm();
    return other.equalsExact(current, tolerance);
  }
}
