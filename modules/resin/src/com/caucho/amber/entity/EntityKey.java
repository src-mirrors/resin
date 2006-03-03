/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.amber.entity;

import com.caucho.amber.type.EntityType;

/**
 * Key to handle the merged identity hash code.
 */
public class EntityKey {
  private EntityType _rootType;
  private Object _key;

  public EntityKey()
  {
  }

  public EntityKey(EntityType rootType, Object key)
  {
    _rootType = rootType;
    _key = key;
  }

  public void init(EntityType rootType, Object key)
  {
    _rootType = rootType;
    _key = key;
  }

  /**
   * Returns the home value.
   */
  public EntityType getEntityType()
  {
    return _rootType;
  }

  /**
   * Returns the key
   */
  public Object getKey()
  {
    return _key;
  }

  /**
   * Returns the hash.
   */
  public int hashCode()
  {
    return 65521 * System.identityHashCode(_rootType) + _key.hashCode();
  }

  /**
   * Returns equality.
   */
  public boolean equals(Object o)
  {
    if (this == o)
      return true;

    if (o == null || getClass() != o.getClass())
      return false;

    EntityKey key = (EntityKey) o;

    return _rootType == key._rootType && _key.equals(key._key);
  }
    
  public String toString()
  {
    return "EntityKey[" + _rootType + ", " + _key + "]";
  }
}
