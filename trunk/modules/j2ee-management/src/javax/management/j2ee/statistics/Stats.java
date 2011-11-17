/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Sam
 */


package javax.management.j2ee.statistics;

/**
 * Base class interface for management objects that provide statistics.
 */
public interface Stats {
  /**
   * A list of the statistic providing attributes.
   * Each name in this list corresponds to the name of an attribute that returns
   * a type of {@link Statistic}
   */
  public String []getStatisticNames();

  /**
   * Returns the named statistic.
   */
  public Statistic getStatistic(String name);

  /**
   * Returns a list of all {@link Statistic} supported by this
   * management object.
   */
  public Statistic []getStatistics();
}
