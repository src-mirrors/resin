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

package com.caucho.ejb;

import com.caucho.amber.entity.AmberEntityHome;
import com.caucho.amber.manager.AmberContainer;
import com.caucho.amber.manager.AmberPersistenceUnit;
import com.caucho.bytecode.JClassLoader;
import com.caucho.config.ConfigException;
import com.caucho.ejb.admin.EJBAdmin;
import com.caucho.ejb.manager.EjbContainer;
import com.caucho.ejb.cfg.EjbConfig;
import com.caucho.ejb.cfg.MessageDestination;
import com.caucho.ejb.entity.EntityKey;
import com.caucho.ejb.entity.EntityServer;
import com.caucho.ejb.entity.QEntityContext;
import com.caucho.ejb.protocol.EjbProtocolManager;
import com.caucho.ejb.protocol.ProtocolContainer;
import com.caucho.ejb.xa.EjbTransactionManager;
import com.caucho.java.WorkDir;
import com.caucho.lifecycle.*;
import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentClassLoader;
import com.caucho.loader.EnvironmentListener;
import com.caucho.loader.SimpleLoader;
import com.caucho.util.L10N;
import com.caucho.util.Log;
import com.caucho.util.LruCache;
import com.caucho.vfs.Path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the beans in an environment.
 */
public class EnvServerManager implements EnvironmentListener
{
  private static final L10N L = new L10N(EnvServerManager.class);
  protected static final Logger log
    = Logger.getLogger(EnvServerManager.class.getName());

  /*
    private static EnvironmentLocal<EnvServerManager> _localServerManager
    = new EnvironmentLocal<EnvServerManager>("caucho.env-server");
  */

  private EnvironmentClassLoader _classLoader;

  private Path _workPath;

  private ConfigException _initException;

  private EJBAdmin _ejbAdmin;

  private AmberContainer _amberContainer;
  private AmberPersistenceUnit _amberPersistenceUnit;

  private ArrayList<EjbConfig> _ejbConfigList = new ArrayList<EjbConfig>();

  private Hashtable<String,AbstractServer> _serverMap
    = new Hashtable<String,AbstractServer>();

  // handles remote stuff
  protected ProtocolContainer _protocolContainer;
  protected HashMap<String,ProtocolContainer> _protocolMap
    = new HashMap<String,ProtocolContainer>();

  private LruCache<EntityKey,QEntityContext> _entityCache;

  private EntityKey _entityKey = new EntityKey();

  private EjbContainer _ejbContainer;

  private final Lifecycle _lifecycle = new Lifecycle(log, "ejb-manager");

  /**
   * Create a server with the given prefix name.
   */
  EnvServerManager(AmberContainer amberContainer)
  {
    try {
      _classLoader = (EnvironmentClassLoader) Thread.currentThread().getContextClassLoader();
      _workPath = WorkDir.getLocalWorkDir(_classLoader).lookup("ejb");

      _classLoader.addLoader(new SimpleLoader(_workPath));

      _ejbContainer = EjbContainer.create();

      _ejbAdmin = new EJBAdmin(this);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private AmberPersistenceUnit getAmberPersistenceUnit()
  {
    if (_amberPersistenceUnit == null) {
      try {
	_amberPersistenceUnit = _amberContainer.createPersistenceUnit("resin-ejb");
	_amberPersistenceUnit.setBytecodeGenerator(false);
	_amberPersistenceUnit.initLoaders();
	// _amberPersistenceUnit.setTableCacheTimeout(_entityCacheTimeout);
      } catch (RuntimeException e) {
	throw e;
      } catch (Exception e) {
	throw new RuntimeException(e);
      }
    }

    return _amberPersistenceUnit;
  }

  /**
   * Gets the local server.
   */
  /*
    public static EnvServerManager getLocal()
    {
    return _localServerManager.get();
    }
  */

  /**
   * Creates the local server.
   */
  /*
    public static EnvServerManager createLocal()
    {
    synchronized (EnvServerManager.class) {
    EnvServerManager serverManager = _localServerManager.getLevel();

    if (serverManager == null) {
    serverManager = new EnvServerManager();
    _localServerManager.set(serverManager);
    }

    return serverManager;
    }
    }
  */

  /**
   * Returns the loader.
   */
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * Returns the protocol manager.
   */
  public EjbProtocolManager getProtocolManager()
  {
    return _ejbContainer.getProtocolManager();
  }

  /**
   * Returns the transaction manager.
   */
  public EjbTransactionManager getTransactionManager()
  {
    return _ejbContainer.getTransactionManager();
  }

  /**
   * Sets the Resin isolation.
   */
  public void setResinIsolation(int resinIsolation)
  {
    getTransactionManager().setResinIsolation(resinIsolation);
  }

  /**
   * Sets the Resin isolation for the container.
   */
  public int getResinIsolation()
  {
    return getTransactionManager().getResinIsolation();
  }

  /**
   * Sets the JDBC isolation.
   */
  public void setJDBCIsolation(int jdbcIsolation)
  {
    getTransactionManager().setJDBCIsolation(jdbcIsolation);
  }

  /**
   * Gets the JDBC isolation level.
   */
  public int getJDBCIsolation()
  {
    return getTransactionManager().getJDBCIsolation();
  }

  /**
   * Gets the transaction timeout
   */
  public long getTransactionTimeout()
  {
    return getTransactionManager().getTransactionTimeout();
  }

  /**
   * Sets the transaction timout.
   */
  public void setTransactionTimeout(long transactionTimeout)
  {
    getTransactionManager().setTransactionTimeout(transactionTimeout);
  }

  /**
   * Returns the work path.
   */
  public Path getWorkPath()
  {
    return _workPath;
  }

  /**
   * Sets the work path.
   */
  public void setWorkPath(Path workPath)
  {
    _workPath = workPath;
  }

  /**
   * Returns the admin class.
   */
  public EJBAdmin getAdmin()
  {
    return _ejbAdmin;
  }

  /**
   * Adds an ejb-config.
   */
  void addEjbConfig(EjbConfig ejbConfig)
  {
    _ejbConfigList.add(ejbConfig);
  }

  /**
   * interface callback.
   */
  public void init()
    throws Exception
  {
    build();

    Environment.addEnvironmentListener(this);
  }

  /**
   * Initialize the manager after all the configuration files have been read.
   */
  public void build()
    throws ConfigException
  {
    try {
      if (_amberPersistenceUnit != null)
	_amberPersistenceUnit.init();
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  public void start()
  {
    for (EjbConfig cfg : _ejbConfigList)
      cfg.deploy();

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    for (AbstractServer server : _serverMap.values()) {
      try {
        thread.setContextClassLoader(server.getClassLoader());

        log.fine(server + " starting");

        server.start();
      } catch (RuntimeException e) {
	throw e;
      } catch (Exception e) {
	throw new StartLifecycleException(e);
      } finally {
        thread.setContextClassLoader(oldLoader);
      }
    }
  }

  public AmberEntityHome getAmberEntityHome(String name)
  {
    return getAmberPersistenceUnit().getEntityHome(name);
  }

  public AmberPersistenceUnit getAmberManager()
  {
    return getAmberPersistenceUnit();
  }

  public JClassLoader getJClassLoader()
  {
    return getAmberManager().getJClassLoader();
  }

  /**
   * Invalidates the caches for all the beans.
   */
  public void invalidateCache()
  {
  }

  /**
   * Adds a server.
   */
  public void addServer(AbstractServer server)
  {
    String id = server.getId();

    _serverMap.put(id, server);

    try {
      getProtocolManager().addServer(server);
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }


  /**
   * Returns the server specified by the ejbName, or null if not found.
   */
  public AbstractServer getServer(String ejbName)
  {
    for  (AbstractServer server : _serverMap.values()) {
      if (server.getEJBName().equals(ejbName)) {
        return server;
      }
    }

    return null;
  }

  /**
   * Returns the server specified by the path and ejbName, or null if not found.
   */
  public AbstractServer getServer(Path path, String ejbName)
  {
    return _serverMap.get(path.getFullPath() + "#" + ejbName);
  }

  public MessageDestination getMessageDestination(Path path, String name)
  {
    // XXX:
    return null;
  }

  public MessageDestination getMessageDestination(String name)
  {
    for (EjbConfig ejbConfig : _ejbConfigList) {
      MessageDestination dest = ejbConfig.getMessageDestination(name);

      if (dest != null)
        return dest;
    }

    return null;
  }

  /**
   * Adds a new entity.
   */
  public QEntityContext getEntity(EntityServer server, Object key)
  {
    synchronized (_entityKey) {
      _entityKey.init(server, key);

      return _entityCache.get(_entityKey);
    }
  }

  public Object getLocalByInterface(Class type)
  {
    for (AbstractServer server : _serverMap.values()) {
      if (server.getLocalHomeClass() != null
          && type.isAssignableFrom(server.getLocalHomeClass())) {
        // ejb/0gb0

        return server.getEJBLocalHome();
      }

      ArrayList<Class> apiList = server.getLocalApiList();

      if (apiList != null) {
        for (int i = apiList.size() - 1; i >= 0; i--) {
          if (type.isAssignableFrom(apiList.get(i)))
            return server.getClientObject(type);
        }
      }
    }

    return null;
  }

  public Object getRemoteByInterface(Class type)
  {
    for (AbstractServer server : _serverMap.values()) {
      Object remote = server.getRemoteObject21();

      if (remote != null && type.isAssignableFrom(remote.getClass()))
        return remote;

      remote = server.getRemoteObject(type);

      if (remote != null && type.isAssignableFrom(remote.getClass()))
        return remote;
    }

    return null;
  }

  /**
   * Adds a new entity.
   */
  public QEntityContext putEntityIfNew(EntityServer server, Object key,
                                       QEntityContext context)
  {
    return _entityCache.putIfNew(new EntityKey(server, key), context);
  }

  /**
   * Adds a new entity.
   */
  public void removeEntity(EntityServer server, Object key)
  {
    synchronized (_entityKey) {
      _entityKey.init(server, key);
      _entityCache.remove(_entityKey);
    }
  }

  /**
   * Adds a new entity.
   */
  public void removeBeans(ArrayList<QEntityContext> beans, EntityServer server)
  {
    synchronized (_entityCache) {
      Iterator<LruCache.Entry<EntityKey,QEntityContext>> iter;

      iter = _entityCache.iterator();

      while (iter.hasNext()) {
        LruCache.Entry<EntityKey,QEntityContext> entry = iter.next();

        beans.add(entry.getValue());

        iter.remove();
      }
    }
  }

  /**
   * Handles the case where the environment is starting (after init).
   */
  public void environmentStart(EnvironmentClassLoader loader)
  {
    start();
  }

  /**
   * Handles the case where the environment is stopping
   */
  public void environmentStop(EnvironmentClassLoader loader)
  {
  }

  /**
   * Closes the container.
   */
  public void destroy()
  {
    if (! _lifecycle.toDestroy())
      return;

    try {
      ArrayList<AbstractServer> servers;
      servers = new ArrayList<AbstractServer>(_serverMap.values());
      _serverMap.clear();

      /*
        for (int i = 0; i < _serverNames.size(); i++)
        _staticServerMap.remove(_serverNames.get(i));
      */

      // only purpose of the sort is to make the qa order consistent
      Collections.sort(servers, new ServerCmp());

      for (AbstractServer server : servers) {
        try {
          getProtocolManager().removeServer(server);
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }

      for (AbstractServer server : servers) {
        try {
          server.destroy();
        } catch (Throwable e) {
          log.log(Level.WARNING, e.toString(), e);
        }
      }

      _serverMap = null;
      _amberPersistenceUnit = null;
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Sorts the servers so they can be destroyed in a consistent order.
   * (To make QA sane.)
   */
  static class ServerCmp implements Comparator<AbstractServer> {
    public int compare(AbstractServer a, AbstractServer b)
    {
      return a.getEJBName().compareTo(b.getEJBName());
    }
  }
}
