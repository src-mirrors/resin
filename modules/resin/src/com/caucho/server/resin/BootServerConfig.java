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

package com.caucho.server.resin;

import com.caucho.cloud.network.ClusterServerProgram;
import com.caucho.cloud.topology.CloudServer;
import com.caucho.config.Configurable;
import com.caucho.config.SchemaBean;
import com.caucho.config.program.ConfigProgram;
import com.caucho.config.program.ContainerProgram;

/**
 * The BootServerConfig is the first-pass configuration of the server.
 * 
 * It matches the &lt;server> tag in the resin.xml
 */
public class BootServerConfig implements SchemaBean
{
  private final BootPodConfig _pod;
  
  private String _id = "default";
  
  private String _address = "127.0.0.1";
  private int _port = -1;
  private boolean _isSecure;
  
  private boolean _isRequireExplicitId;
  
  // private boolean _isDynamic;

  private ContainerProgram _serverProgram
    = new ContainerProgram();

  /**
   * Creates a new resin server.
   */
  public BootServerConfig(BootPodConfig pod)
  {
    _pod = pod;
  }
  
  public BootPodConfig getPod()
  {
    return _pod;
  }

  /**
   * Returns the relax schema.
   */
  @Override
  public String getSchema()
  {
    return "com/caucho/server/resin/server.rnc";
  }
  
  /**
   * Returns the cluster's id
   */
  public String getId()
  {
    return _id;
  }
  
  /**
   * Sets the cluster's id
   */
  @Configurable
  public void setId(String id)
  {
    if (id == null || id.equals(""))
      id = "default";
    
    _id = id;
  }
  
  public String getAddress()
  {
    return _address;
  }
  
  @Configurable
  public void setAddress(String address)
  {
    _address = address;
  }
  
  public int getPort()
  {
    return _port;
  }
  
  @Configurable
  public void setPort(int port)
  {
    _port = port;
  }
  
  public boolean isSecure()
  {
    return _isSecure;
  }
  
  @Configurable
  public void setRequireExplicitId(boolean isRequire)
  {
    _isRequireExplicitId = isRequire;
  }
  
  @Configurable
  public boolean isRequireExplicitId()
  {
    return _isRequireExplicitId;
  }

  /**
   * Marks the server as a dynamic server
   */
  /*
  public void setDynamic(boolean isDynamic)
  {
    _isDynamic = isDynamic;
  }
  
  public boolean isDynamic()
  {
    return _isDynamic;
  }
  */
  
  public void addBuilderProgram(ConfigProgram program)
  {
    _serverProgram.addProgram(program);
  }
  
  public ConfigProgram getServerProgram()
  {
    return _serverProgram;
  }

  /*
  public CloudServer getCloudServer()
  {
    return _cloudServer;
  }
  */

  /*
  @PostConstruct
  public void init()
  {
    CloudPod pod = _pod.getCloudPod();
    
    if (pod.getServerLength() >= 64) {
      throw new ConfigException(L.l("The server cannot be added to the current pod because it would be more than 64 servers to the pod."));
    }

    if (isDynamic()) {
      _cloudServer = pod.findServer(getId());
      
      if (_cloudServer == null)
        throw new IllegalStateException(L.l("Dynamic server '{0}' is expected to exist here.",
                                            getId()));
    } else {
      _cloudServer = pod.createStaticServer(getId(), 
                                            getAddress(),
                                            getPort(),
                                            isSecure());
    }
    
    _cloudServer.putData(new ClusterServerProgram(_serverProgram));
  }
  */
  
  void initTopology(CloudServer cloudServer)
  {
    cloudServer.putData(new ClusterServerProgram(_serverProgram));
  }
  
  /*
  ClusterServer getClusterServer()
  {
    return _cloudServer.getData(ClusterServer.class);
  }
  */

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _id + "]";
  }

}
