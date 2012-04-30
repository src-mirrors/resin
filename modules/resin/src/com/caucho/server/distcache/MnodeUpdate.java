/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

/**
 * An entry in the cache map
 */
@SuppressWarnings("serial")
public class MnodeUpdate extends MnodeValue {
  public static final MnodeUpdate NULL
    = new MnodeUpdate(0, 0, 0, null, 0, 0, 0, -1, -1);
  
  private final int _leaseOwner;
  
  public MnodeUpdate(long valueHash,
                     long valueLength,
                     long version,
                     byte []cacheHash,
                     long flags,
                     long accessedExpireTime,
                     long modifiedExpireTime,
                     long leaseExpireTime,
                     int leaseOwner)
  {
    super(valueHash, valueLength, version,
          cacheHash, 
          flags, 
          accessedExpireTime, modifiedExpireTime, leaseExpireTime);
    
    _leaseOwner = leaseOwner;
  }
  
  public MnodeUpdate(long valueHash,
                     long valueLength,
                     long version)
  {
    super(valueHash, valueLength, version);
    
    _leaseOwner = -1;
  }
  
  public MnodeUpdate(MnodeUpdate update)
  {
    super(update);
    
    _leaseOwner = update._leaseOwner;
  }
  
  public MnodeUpdate(MnodeValue mnodeValue)
  {
    super(mnodeValue);
    
    _leaseOwner = -1;
  }
  
  public MnodeUpdate(MnodeValue mnodeValue,
                     int leaseOwner)
  {
    super(mnodeValue);
    
    _leaseOwner = leaseOwner;
  }

  public MnodeUpdate(long valueHash,
                     long valueLength,
                     long version,
                     CacheConfig config)
  {
    super(valueHash, valueLength, version, config);
    
    _leaseOwner = -1;
  }

  public MnodeUpdate(long valueHash,
                     long valueLength,
                     long version,
                     CacheConfig config,
                     int leaseOwner,
                     long leaseTimeout)
  {
    super(valueHash, valueLength, version, config);
    
    _leaseOwner = leaseOwner;
  }

  public MnodeUpdate(long valueHash,
                     long valueLength,
                     long version,
                     MnodeValue oldValue)
  {
    super(valueHash, valueLength, version, oldValue);
    
    _leaseOwner = -1;
  }

  public MnodeUpdate(long valueHash,
                     long valueLength,
                     long version,
                     MnodeValue oldValue,
                     int leaseOwner)
  {
    super(valueHash, valueLength, version, oldValue);
    
    _leaseOwner = leaseOwner;
  }
  
  public static MnodeUpdate createNull(long version, MnodeValue oldValue)
  {
    return new MnodeUpdate(0, 0, version, oldValue);
  }
  
  public static MnodeUpdate createNull(long version, CacheConfig config)
  {
    return new MnodeUpdate(0, 0, version, config);
  }
  
  /**
   * Create an update that removes the local information for sending to a
   * remote server.
   */
  public MnodeUpdate createRemote()
  {
    return new MnodeUpdate(getValueHash(),
                           getValueLength(),
                           getVersion(),
                           this,
                           getLeaseOwner());
  }
  
  public final int getLeaseOwner()
  {
    return _leaseOwner;
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
        + "[value=" + Long.toHexString(getValueHash())
        + ",len=" + getValueLength()
        + ",flags=" + Long.toHexString(getFlags())
        + ",version=" + getVersion()
        + ",lease=" + getLeaseOwner()
        + "]");
  }
}
