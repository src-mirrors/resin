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

package com.caucho.ejb.session;

import com.caucho.ejb.AbstractContext;
import com.caucho.ejb.EJBExceptionWrapper;
import com.caucho.ejb.manager.EjbContainer;
import com.caucho.ejb.protocol.AbstractHandle;
import com.caucho.ejb.session.StatelessComponent;
import com.caucho.webbeans.component.*;

import javax.ejb.*;
import javax.webbeans.*;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server home container for a stateless session bean
 */
public class StatelessServer extends SessionServer {
  protected static Logger log
    = Logger.getLogger(StatelessServer.class.getName());

  private StatelessContext _homeContext;
  private StatelessProvider _remoteProvider;

  /**
   * Creates a new stateless server.
   *
   * @param urlPrefix the url prefix for any request to the server
   * @param allowJVMCall allows fast calls to the same JVM (with serialization)
   * @param config the session configuration from the ejb.xml
   */
  public StatelessServer(EjbContainer ejbContainer)
  {
    super(ejbContainer);
  }

  @Override
  protected String getType()
  {
    return "stateless:";
  }

  /**
   * Returns the JNDI proxy object to create instances of the
   * local interface.
   */
  @Override
  public Object getLocalProxy(Class api)
  {
    StatelessProvider provider = getStatelessContext().getProvider(api);

    return new StatelessProviderProxy(provider);
  }
  
  protected ComponentImpl createSessionComponent(Class api)
  {
    StatelessProvider provider = getStatelessContext().getProvider(api);

    return new StatelessComponent(provider);
  }

  /**
   * Returns the ejb home.
   */
  @Override
  public EJBHome getEJBHome()
  {
    return _remoteHomeView;
  }

  /**
   * Returns the 3.0 remote stub for the container
   */
  public Object getRemoteObject()
  {
    if (_remoteProvider != null)
      return _remoteProvider.__caucho_get();
    else
      return null;
  }

  /**
   * Returns the 3.0 remote stub for the container
   */
  public Object getRemoteObject(Class api)
  {
    StatelessProvider provider = getStatelessContext().getProvider(api);

    if (provider != null) {
      Object result = provider.__caucho_get();
      
      return result;
    }
    else {
      log.fine(this + " unknown api " + api.getName());
      return null;
    }
  }

  /**
   * Returns the remote object.
   */
  @Override
  public Object getRemoteObject(Object key)
  {
    if (_remoteProvider != null)
      return _remoteProvider.__caucho_get();
    else
      return null;
  }
  
  @Override
  public void init()
    throws Exception
  {
    super.init();

    ArrayList<Class> remoteApiList = getRemoteApiList();

    if (remoteApiList != null && remoteApiList.size() > 0) {
      Class api = remoteApiList.get(0);
      
      _remoteProvider = getStatelessContext().getProvider(api);
    }
  }
  
  /**
   * Finds the remote bean by its key.
   *
   * @param key the remote key
   *
   * @return the remote interface of the entity.
   */
  @Override
  public EJBObject getEJBObject(Object key)
    throws FinderException
  {
    return getStatelessContext().getEJBObject();
  }

  public AbstractContext getContext()
  {
    return getStatelessContext();
  }

  @Override
  public AbstractContext getContext(Object key, boolean forceLoad)
  {
    return getStatelessContext();
  }

  @Override
  public AbstractSessionContext getSessionContext()
  {
    return getStatelessContext();
  }

  private StatelessContext getStatelessContext()
  {
    synchronized (this) {
      if (_homeContext == null) {
        try {
          Class []param = new Class[] { StatelessServer.class };
          Constructor cons = _contextImplClass.getConstructor(param);

          _homeContext = (StatelessContext) cons.newInstance(this);
        } catch (Exception e) {
          throw new EJBExceptionWrapper(e);
        }
      }
    }

    return _homeContext;
  }

  /**
   * Creates a handle for a new session.
   */
  AbstractHandle createHandle(AbstractContext context)
  {
    String key = createSessionKey(context);

    return getHandleEncoder().createHandle(key);
  }

  /**
   * Creates a handle for a new session.
   */
  /*
    JVMObject createEJBObject(Object primaryKey)
    {
    try {
    JVMObject obj = (JVMObject) _remoteStubClass.newInstance();
    obj._init(this, createSessionKey(null));

    return obj;
    } catch (Exception e) {
    throw new EJBExceptionWrapper(e);
    }
    }
  */

  /**
   * Creates a handle for a new session.
   */
  String createSessionKey(AbstractContext context)
  {
    return "::ejb:stateless";
  }

  /**
   * Cleans up the entity server nicely.
   */
  @Override
  public void destroy()
  {
    if (_homeContext != null) {
      try {
        _homeContext.destroy();
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }

    super.destroy();
  }
}
