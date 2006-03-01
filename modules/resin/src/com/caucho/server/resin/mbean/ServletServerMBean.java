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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.server.resin.mbean;

import javax.management.ObjectName;

import com.caucho.server.port.mbean.PortMBean;

import com.caucho.server.deploy.mbean.DeployControllerMBean;

import com.caucho.server.resin.ServletServer;

/**
 * Management interface for the server.
 */
public interface ServletServerMBean extends DeployControllerMBean {
  /**
   * Returns the invocation cache hit count.
   */
  public long getInvocationCacheHitCount();

  /**
   * Returns the invocation cache miss count.
   */
  public long getInvocationCacheMissCount();

  /**
   * Returns the proxy cache hit count.
   */
  public long getProxyCacheHitCount();

  /**
   * Returns the proxy cache miss count.
   */
  public long getProxyCacheMissCount();

  /**
   * Returns the array of ports.
   */
  public ObjectName []getPortObjectNames();
  
  /**
   * Returns the array of cluster.
   */
  public ObjectName []getClusterObjectNames();
  
  /**
   * Returns the array of hosts.
   */
  public ObjectName []getHostObjectNames();

  /**
   * Clears the cache.
   */
  public void clearCache();
  
  /**
   * Clears the cache by regexp patterns.
   *
   * @param hostRegexp the regexp to match the host.  Null matches all.
   * @param urlRegexp the regexp to match the url. Null matches all.
   */
  public void clearCacheByPattern(String hostRegexp, String urlRegexp);

}
