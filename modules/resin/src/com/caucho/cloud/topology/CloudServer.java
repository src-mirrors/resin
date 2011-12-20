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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.cloud.topology;

import java.net.InetAddress;
import java.util.concurrent.ConcurrentHashMap;

import com.caucho.config.ConfigException;
import com.caucho.util.L10N;

/**
 * Defines a cloud server, a single Resin instance.
 * 
 * Each server has the following:
 * <ul>
 * <li>A unique id across the entire domain.
 * <li>A containing pod (contained within the server).
 * <li>A unique index within the pod. Servers 0-2 are special servers called
 * "triad" servers.
 * <li>An IP address and port.
 * </ul>
 *
 * Servers are organized into pods of up to 64 servers, contained in a cluster.
 * 
 * All the clusters are contained in a domain.
 */
public class CloudServer {
  private static final L10N L = new L10N(CloudServer.class);
  private static final int DECODE[];

  private final String _id;

  private final CloudPod _pod;
  private final int _index;
  
  private final ServerType _isStatic;
  
  // unique identifier for the server within the cluster
  private final String _uniqueClusterId;
  // unique identifier for the server within all Resin clusters
  private final String _uniqueDomainId;
  
  private final String _address;
  private final int _port;
  private final boolean _isSSL;
  
  private boolean _isSelf;
  
  private final ConcurrentHashMap<Class<?>,Object> _dataMap
    = new ConcurrentHashMap<Class<?>,Object>();
  
  private CloudServerState _state = CloudServerState.UNKNOWN;

  public CloudServer(String id,
                     CloudPod pod, 
                     int index,
                     String address,
                     int port,
                     boolean isSSL,
                     ServerType isStatic)
  {
    if (id.equals(""))
      throw new IllegalArgumentException();
    
    _id = id;

    _pod = pod;
    _index = index;
    
    if (index < 0 || index >= 64)
      throw new IllegalArgumentException(L.l("'{0}' is an invalid server index because it must be between 0 and 64",
                                            index));
    
    if (! isStatic.isStatic() && index == 0)
      throw new IllegalArgumentException(L.l("The first server must be static."));
    
    if (id == null)
      throw new NullPointerException();
    if (pod == null)
      throw new NullPointerException();
    
    _address = address;
    _port = port;
    _isSSL = isSSL;
    _isStatic = isStatic;

    // _clusterPort = new ClusterPort(this);
    // _ports.add(_clusterPort);

    StringBuilder sb = new StringBuilder();

    sb.append(convert(getIndex()));
    sb.append(convert(getPod().getIndex()));
    sb.append(convert(getPod().getIndex() / 64));

    _uniqueClusterId = sb.toString();

    String clusterId = pod.getCluster().getId();
    if (clusterId.equals(""))
      clusterId = "default";

    _uniqueDomainId = _uniqueClusterId + "." + clusterId.replace('.', '_');
    
    if (! isLocalAddress(getAddress()) && ! isExternal()) {
      throw new ConfigException(L.l("'{0}' is not a valid cluster IP address because it is not a private network IP address.",
                                    getAddress()));
    }
  }
  
  private boolean isLocalAddress(String address)
  {
    try {
      InetAddress addr = InetAddress.getByName(address);
      
      byte []ipAddress = addr.getAddress();

      if (ipAddress.length == 16)
        return true;
      
      if (ipAddress.length != 4)
        return false;
      
      if (ipAddress[0] == 0)
        return true;
      
      if (ipAddress[0] == 127)
        return true;
      
      if (ipAddress[0] == 10)
        return true;
      
      if ((ipAddress[0] & 0xff) == 192
          && (ipAddress[1] & 0xff) == 168) {
        return true;
      }
      
      if ((ipAddress[0] & 0xff) == 169
          && (ipAddress[1] & 0xff) == 254) {
        return true;
      }
      
      if ((ipAddress[0] & 0xff) == 172
          && (ipAddress[1] & 0xf0) == 0x10) {
        return true;
      }
       
      return false;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Gets the unique server identifier.
   */
  public final String getId()
  {
    return _id;
  }

  public final String getDebugId()
  {
    if ("".equals(_id))
      return "default";
    else
      return _id;
  }

  /**
   * Returns the index within the pod.
   */
  public final int getIndex()
  {
    return _index;
  }

  /**
   * Returns the server's id within the cluster
   */
  public final String getIdWithinCluster()
  {
    return _uniqueClusterId;
  }

  /**
   * Returns the server's id within all Resin clusters
   */
  public final String getIdWithinDomain()
  {
    return _uniqueDomainId;
  }

  /**
   * True if this server is a triad.
   */
  public boolean isTriad()
  {
    return false;
  }

  /**
   * True for statically configured servers.
   */
  public boolean isStatic()
  {
    return _isStatic.isStatic();
  }

  /**
   * True for external-address configured servers.
   */
  public boolean isExternal()
  {
    return _isStatic.isExternal();
  }
  
  /**
   * True for the active server
   */
  public boolean isSelf()
  {
    return _isSelf;
  }
  
  /**
   * Sets true for the active server.
   */
  public void setSelf(boolean isSelf)
  {
    _isSelf = isSelf;
    
    _pod.setSelf(isSelf);
  }
  
  /**
   * Returns the servers current state.
   */
  public CloudServerState getState()
  {
    return _state;
  }
  
  //
  // topology attributes
  //

  /**
   * Returns the pod
   */
  public CloudPod getPod()
  {
    return _pod;
  }

  /**
   * Returns the cluster.
   */
  public CloudCluster getCluster()
  {
    return getPod().getCluster();
  }

  /**
   * Returns the system.
   */
  public CloudSystem getSystem()
  {
    return getCluster().getSystem();
  }

  /**
   * Returns the pod owner
   */
  public TriadOwner getTriadOwner()
  {
    return TriadOwner.getOwner(getIndex());
  }
  
  //
  // IP addresses
  //
  
  /**
   * Gets the address
   */
  public final String getAddress()
  {
    return _address;
  }
  
  /**
   * Gets the port
   */
  public final int getPort()
  {
    return _port;
  }
  
  public boolean isSSL()
  {
    return _isSSL;
  }
  
  //
  // state transitions
  //
  

  public void onHeartbeatStart()
  {
    CloudServerState oldState;
    
    synchronized (this) {
      oldState = _state;
      
      _state = oldState.onHeartbeatStart();
    }
    
    if (_state != oldState)
      updateState();
  }

  public void onHeartbeatStop()
  {
    CloudServerState oldState;
    
    synchronized (this) {
      oldState = _state;
      
      _state = oldState.onHeartbeatStop();
    }
    
    if (_state != oldState)
      updateState();
  }

  public void setState(CloudServerState state)
  {
    if (_pod.isSelf())
      throw new IllegalStateException();
    
    _state = state;
  }

  public void overrideState(CloudServerState state)
  {
    _state = state;

    updateState();
  }
  
  public void disable()
  {
    overrideState(CloudServerState.DISABLED);
  }
  
  public void disableSoft()
  {
    overrideState(CloudServerState.DISABLED_SOFT);
  }
  
  public void enable()
  {
    overrideState(CloudServerState.ACTIVE);
  }
  
  private void updateState()
  {
    _pod.onServerStateChange(this);
  }

  //
  // data
  //
  
  public void putData(Object value)
  {
    _dataMap.put(value.getClass(), value);
  }
  
  @SuppressWarnings("unchecked")
  public <T> T putDataIfAbsent(T value)
  {
    return (T) _dataMap.putIfAbsent(value.getClass(), value);
  }
  
  @SuppressWarnings("unchecked")
  public <T> T getData(Class<T> cl)
  {
    return (T) _dataMap.get(cl);
  }
  
  @SuppressWarnings("unchecked")
  public <T> T removeData(Class<T> cl)
  {
    return (T) _dataMap.remove(cl);
  }
  
  @Override
  public String toString()
  {
    return (getClass().getSimpleName() 
            + "[" + _id + "," + _index
            + "," + _address + ":" + _port
            + "]");
  }

  private static char convert(long code)
  {
    code = code & 0x3f;

    if (code < 26)
      return (char) ('a' + code);
    else if (code < 52)
      return (char) ('A' + code - 26);
    else if (code < 62)
      return (char) ('0' + code - 52);
    else if (code == 62)
      return '_';
    else
      return '-';
  }

  public static int decode(int code)
  {
    return DECODE[code & 0x7f];
  }
  
  public enum ServerType {
    STATIC {
      @Override
      public boolean isStatic() { return true; }
    },
    
    EXTERNAL {
      @Override
      public boolean isStatic() { return true; }
      
      @Override
      public boolean isExternal() { return true; }
    },
    
    DYNAMIC {
    };
    
    public boolean isStatic()
    {
      return false;
    }
    
    public boolean isExternal()
    {
      return false;
    }
  }

  static {
    DECODE = new int[128];
    for (int i = 0; i < 64; i++)
      DECODE[(int) convert(i)] = i;
  }
}
