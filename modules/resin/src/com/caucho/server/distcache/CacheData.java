/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.server.distcache;

import com.caucho.util.Alarm;

import java.lang.ref.SoftReference;

/**
 * Full data from the dat map
 */
public final class CacheData {
  private final HashKey _key;
  private final HashKey _value;
  private final long _version;
  private final long _accessTime;
  private final boolean _isRemoved;

  public CacheData(HashKey key,
		   HashKey value,
		   long version,
		   long accessTime,
		   boolean isRemoved)
  {
    _key = key;
    _value = value;
    _version = version;
    
    _accessTime = accessTime;
    _isRemoved = isRemoved;
  }

  public HashKey getKey()
  {
    return _key;
  }

  public HashKey getValue()
  {
    return _value;
  }

  public long getVersion()
  {
    return _version;
  }

  public long getAccessTime()
  {
    return _accessTime;
  }

  public boolean isRemoved()
  {
    return _isRemoved;
  }

  public String toString()
  {
    return (getClass().getSimpleName()
	    + "[key=" + _key
	    + ",value=" + _value
	    + ",version=" + _version
	    + "]");
  }
}
