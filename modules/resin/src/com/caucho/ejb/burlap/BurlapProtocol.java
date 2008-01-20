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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.ejb.burlap;

import com.caucho.config.ConfigException;
import com.caucho.ejb.AbstractServer;
import com.caucho.ejb.message.MessageServer;
import com.caucho.ejb.protocol.HandleEncoder;
import com.caucho.ejb.protocol.ProtocolContainer;
import com.caucho.ejb.protocol.Skeleton;
import com.caucho.hessian.io.HessianRemoteResolver;
import com.caucho.util.L10N;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.*;
/**
 * Server containing all the EJBs for a given configuration.
 *
 * <p>Each protocol will extend the container to override Handle creation.
 */
public class BurlapProtocol extends ProtocolContainer {
  private static final L10N L = new L10N(BurlapProtocol.class);
  private static final Logger log
    = Logger.getLogger(BurlapProtocol.class.getName());

  private HashMap<String,AbstractServer> _serverMap =
    new HashMap<String,AbstractServer>();

  private WeakHashMap<Class,com.caucho.burlap.server.BurlapSkeleton> _skeletonMap
    = new WeakHashMap<Class,com.caucho.burlap.server.BurlapSkeleton>();

  private HessianRemoteResolver _resolver;

  /**
   * Create a server with the given prefix name.
   */
  public BurlapProtocol()
  {
    _resolver = new BurlapStubFactory();
  }

  public String getName()
  {
    return "burlap";
  }

  /**
   * Adds a server to the protocol.
   */
  public void addServer(AbstractServer server)
  {
    log.finer("Burlap[" + server + "] added");

    _serverMap.put(server.getProtocolId(), server);
  }

  /**
   * Removes a server from the protocol.
   */
  public void removeServer(AbstractServer server)
  {
    _serverMap.remove(server.getProtocolId());
  }

  @Override
  protected HandleEncoder createHandleEncoder(AbstractServer server,
                                              Class primaryKeyClass)
    throws ConfigException
  {
    return new BurlapHandleEncoder(server,
                                   getURLPrefix() + server.getProtocolId(),
                                   primaryKeyClass);
  }

  /**
   * Returns the skeleton
   */
  public Skeleton getSkeleton(String uri, String queryString)
    throws Exception
  {
    String serverId = uri;
    String objectId = null;

    // decode ?id=my-instance-id
    if (queryString != null) {
      int p = queryString.indexOf('=');

      if (p >= 0)
        objectId = queryString.substring(p + 1);
      else
        objectId = queryString;
    }

    AbstractServer server = getProtocolManager().getServerByEJBName(serverId);

    if (server == null) {
      ArrayList children = getProtocolManager().getRemoteChildren(serverId);

      if (children != null && children.size() > 0)
        return new NameContextSkeleton(this, serverId);
      else {
	log.fine(this + " can't find server for " + serverId);
	
        return null; // XXX: should return error skeleton
      }
      /*
        ArrayList children = getServerContainer().getRemoteChildren(serverId);

        if (children != null && children.size() > 0)
        return new NameContextSkeleton(this, serverId);
        else
        return null; // XXX: should return error skeleton
      */
    }
    else if (objectId != null) {
      Object key = server.getHandleEncoder("burlap").objectIdToKey(objectId);

      // ejb/0604 vs ejb/0500
      Object obj = server.getRemoteObject(key);

      Class api = server.getRemoteObjectClass();
      
      com.caucho.burlap.server.BurlapSkeleton skel = getSkeleton(api);

      return new BurlapEjbSkeleton(obj, skel, _resolver);
    }
    else if (server instanceof MessageServer) {
      return new MessageSkeleton((MessageServer) server);
    }
    else {
      Class api;
      
      api = server.getRemoteHomeClass();

      if (api != null) {
	Object remote = server.getRemoteObject(api);
	
        com.caucho.burlap.server.BurlapSkeleton skel = getSkeleton(api);

	return new BurlapEjbSkeleton(remote, skel, _resolver);
      }
      
      api = server.getRemoteObjectClass();

      if (api != null) {
	Object remote = server.getRemoteObject(api);
	
        com.caucho.burlap.server.BurlapSkeleton skel = getSkeleton(api);

	return new BurlapEjbSkeleton(remote, skel, _resolver);
      }
    }

    return null;
  }

  /**
   * Returns the skeleton to use to return configuration exceptions
   */
  @Override
  public Skeleton getExceptionSkeleton()
    throws Exception
  {
    return new ExceptionSkeleton();
  }

  /**
   * Returns the class for home skeletons.
   */
  protected com.caucho.burlap.server.BurlapSkeleton getSkeleton(Class api)
    throws Exception
  {
    com.caucho.burlap.server.BurlapSkeleton skel;

    synchronized (_skeletonMap) {
      skel = _skeletonMap.get(api);

      if (skel == null) {
	skel = new com.caucho.burlap.server.BurlapSkeleton(api);

	_skeletonMap.put(api, skel);
      }

      return skel;
    }
  }
}
