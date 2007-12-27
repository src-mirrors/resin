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

package com.caucho.resin;

import java.io.*;

import com.caucho.config.*;
import com.caucho.config.types.*;
import com.caucho.lifecycle.*;
import com.caucho.server.cluster.*;
import com.caucho.server.host.*;
import com.caucho.server.port.*;
import com.caucho.server.resin.*;
import com.caucho.server.webapp.*;
import com.caucho.vfs.*;

/**
 * Embeddable version of the Resin server.
 */
public class ResinServer
{
  private static final String EMBED_CONF
    = "com/caucho/resin/resin-embed.xml";
  
  private Resin _resin = new Resin();
  private Cluster _cluster;
  private ClusterServer _clusterServer;
  private Host _host;
  private Server _server;

  private Lifecycle _lifecycle = new Lifecycle();
  
  /**
   * Creates a new resin server.
   */
  public ResinServer()
  {
    InputStream is = null;
    try {
      Config config = new Config();
      
      is = _resin.getClassLoader().getResourceAsStream(EMBED_CONF);

      config.configure(_resin, is);
    } catch (Exception e) {
      throw ConfigException.create(e);
    } finally {
      try {
	is.close();
      } catch (IOException e) {
      }
    }

    _cluster = _resin.findCluster("");
    _clusterServer = _cluster.findServer("");
  }

  public void setHttpPort(int port)
  {
    _clusterServer.createHttp().setPort(port);
  }

  public void addWebApp(String contextPath,
			String rootDirectory)
  {
    try {
      start();

      WebAppConfig config = new WebAppConfig();
      config.setContextPath(contextPath);
      config.setRootDirectory(new RawString(rootDirectory));

      _host.addWebApp(config);
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  public void start()
  {
    if (! _lifecycle.toActive())
      return;
      
    try {
      _resin.start();
      _server = _resin.getServer();
      HostConfig hostConfig = new HostConfig();
      _server.addHost(hostConfig);
      _host = _server.getHost("", 0);
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw ConfigException.create(e);
    }
  }

  public void stop()
  {
    if (! _lifecycle.toStop())
      return;
      
    try {
      _resin.stop();
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw ConfigException.create(e);
    }
  }

  public void destroy()
  {
    if (! _lifecycle.toDestroy())
      return;
      
    try {
      _resin.destroy();
    } catch (RuntimeException e) {
      throw e;
    } catch (Throwable e) {
      throw ConfigException.create(e);
    }
  }

  protected void finalize()
    throws Throwable
  {
    super.finalize();
    
    destroy();
  }
}
