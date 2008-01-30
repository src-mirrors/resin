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

package com.caucho.ejb.session;

import com.caucho.ejb.AbstractContext;
import com.caucho.ejb.EJBExceptionWrapper;
import java.util.*;

import com.caucho.ejb.manager.EjbContainer;
import com.caucho.ejb.protocol.AbstractHandle;
import com.caucho.util.LruCache;
import com.caucho.webbeans.component.*;
import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.FinderException;
import javax.ejb.NoSuchEJBException;

/**
 * Server container for a session bean.
 */
public class StatefulServer extends SessionServer
{
  private static final Logger log
    = Logger.getLogger(StatefulServer.class.getName());
  
  private StatefulContext _homeContext;
  
  // XXX: need real lifecycle
  private LruCache<String,StatefulObject> _remoteSessions;

  public StatefulServer(EjbContainer ejbContainer)
  {
    super(ejbContainer);
  }

  @Override
  protected String getType()
  {
    return "stateful:";
  }

  @Override
  public AbstractSessionContext getSessionContext()
  {
    return getStatefulContext();
  }
    
  private StatefulContext getStatefulContext()
  {
    synchronized (this) {
      if (_homeContext == null) {
        try {
          Class []param = new Class[] { StatefulServer.class };
          Constructor cons = _contextImplClass.getConstructor(param);

          _homeContext = (StatefulContext) cons.newInstance(this);
        } catch (Exception e) {
          throw new EJBExceptionWrapper(e);
        }
      }
    }

    return _homeContext;
  }

  /**
   * Returns the JNDI proxy object to create instances of the
   * local interface.
   */
  @Override
  public Object getLocalProxy(Class api)
  {
    StatefulProvider provider = getStatefulContext().getProvider(api);

    return new StatefulProviderProxy(provider);
  }

  protected ComponentImpl createSessionComponent(Class api)
  {
    StatefulProvider provider = getStatefulContext().getProvider(api);

    return new StatefulComponent(provider);
  }
  
  /**
   * Creates a handle for a new session.
   */
  AbstractHandle createHandle(AbstractContext context)
  {
    throw new UnsupportedOperationException(getClass().getName());
    /*
    String key = ((StatelessContext) context).getPrimaryKey();

    return getHandleEncoder().createHandle(key);
     */
  }

  public void addSession(StatefulObject remoteObject)
  {
    createSessionKey(remoteObject);
  }

  /**
   * Finds the remote bean by its key.
   *
   * @param key the remote key
   *
   * @return the remote interface of the entity.
   */
  @Override
  public AbstractContext getContext(Object key, boolean forceLoad)
    throws FinderException
  {
    throw new NoSuchEJBException("no matching object:" + key);
    /*
    if (key == null)
      return null;

    StatefulContext cxt = _sessions.get(key);

    // ejb/0fe4
    if (cxt == null)
      throw new NoSuchEJBException("no matching object:" + key);
    // XXX ejb/0fe-: needs refactoring of 2.1/3.0 interfaces.
    // throw new FinderException("no matching object:" + key);

    return cxt;
    */
  }

  /**
   * Returns the remote object.
   */
  @Override
  public Object getRemoteObject(Object key)
  {
    StatefulObject remote = null;
    if (_remoteSessions != null) {
      remote = _remoteSessions.get(String.valueOf(key));
    }

    return remote;
  }

  /**
   * Creates a handle for a new session.
   */
  public String createSessionKey(StatefulObject remote)
  {
    String key = getHandleEncoder().createRandomStringKey();

    if (_remoteSessions == null)
      _remoteSessions = new LruCache<String,StatefulObject>(8192);
    
    _remoteSessions.put(key, remote);

    return key;
  }

  /**
   * Returns the remote stub for the container
   */
  @Override
  public Object getRemoteObject(Class api)
  {
    StatefulProvider provider = getStatefulContext().getProvider(api);

    if (provider != null) {
      Object value = provider.__caucho_createNew(null);
      
      return value;
    }
    else
      return null;
  }
  
  /**
   * Remove an object by its handle.
   */
  @Override
  public Object remove(AbstractHandle handle)
  {
    if (_remoteSessions != null)
      return _remoteSessions.remove(handle.getObjectId());
    else
      return null;
    // _ejbManager.remove(handle);
  }

  /**
   * Remove an object.
   */
  @Override
  public void remove(Object key)
  {
    if (_remoteSessions != null) {
      _remoteSessions.remove(String.valueOf(key));

      /*
      // ejb/0fe2
      if (cxt == null)
	throw new NoSuchEJBException("no matching object:" + key);
      */
    }
  }
  
  
  /**
   * Cleans up the entity server nicely.
   */
  @Override
  public void destroy()
  {
    super.destroy();

    ArrayList<StatefulObject> values = new ArrayList<StatefulObject>();
    
    if (_remoteSessions != null) {
      Iterator<StatefulObject> iter = _remoteSessions.values();
      while (iter.hasNext()) {
	values.add(iter.next());
      }
    }

    _remoteSessions = null;

    for (StatefulObject obj : values) {
      try {
        obj.remove();
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      }
    }
    
    log.fine(this + " closed");
  }
}
