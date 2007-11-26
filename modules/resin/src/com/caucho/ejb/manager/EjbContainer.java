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

package com.caucho.ejb.manager;

import java.util.*;
import java.util.logging.*;
import javax.jms.*;

import com.caucho.amber.manager.AmberContainer;
import com.caucho.amber.manager.AmberPersistenceUnit;
import com.caucho.config.*;
import com.caucho.ejb.AbstractServer;
import com.caucho.ejb.cfg.EjbConfigManager;
import com.caucho.ejb.cfg.EjbRootConfig;
import com.caucho.ejb.entity.EntityCache;
import com.caucho.ejb.protocol.EjbProtocolManager;
import com.caucho.ejb.xa.EjbTransactionManager;
import com.caucho.java.WorkDir;
import com.caucho.loader.*;
import com.caucho.loader.enhancer.ScanListener;
import com.caucho.util.*;
import com.caucho.vfs.*;

/**
 * Environment-based container.
 */
public class EjbContainer implements ScanListener, EnvironmentListener {
  private static final L10N L = new L10N(EjbContainer.class);
  private static final Logger log
    = Logger.getLogger(EjbContainer.class.getName());

  private static final EnvironmentLocal<EjbContainer> _localContainer
    = new EnvironmentLocal<EjbContainer>();

  private final EnvironmentClassLoader _classLoader;
  private final ClassLoader _tempClassLoader;

  private final EjbContainer _parentContainer;

  private final EjbConfigManager _configManager;
  private final EjbTransactionManager _transactionManager;
  private final EjbProtocolManager _protocolManager;
  private final EntityCache _entityCache;

  private AmberPersistenceUnit _ejbPersistenceUnit;

  private HashSet<String> _ejbUrls = new HashSet<String>();

  //
  // configuration
  //

  private boolean _isAutoCompile = true;
  private Path _workDir;

  private ConnectionFactory _jmsConnectionFactory;
  private int _messageConsumerMax = 5;

  //
  // active servers
  //

  private ArrayList<AbstractServer> _serverList
    = new ArrayList<AbstractServer>();

  private EjbContainer(ClassLoader loader)
  {
    _parentContainer = _localContainer.get(loader);

    _classLoader = Environment.getEnvironmentClassLoader(loader);

    _tempClassLoader = _classLoader.getNewTempClassLoader();

    _localContainer.set(this, _classLoader);

    if (_parentContainer != null)
      copyContainerDefaults(_parentContainer);

    _transactionManager = new EjbTransactionManager(this);

    // _ejbAdmin = new EJBAdmin(this);

    _protocolManager = new EjbProtocolManager(this);

    _configManager = new EjbConfigManager(this);

    _entityCache = new EntityCache();

    _workDir = WorkDir.getLocalWorkDir().lookup("ejb");

    _classLoader.addScanListener(this);

    Environment.addEnvironmentListener(this);
  }

  /**
   * Returns the local container.
   */
  public static EjbContainer create()
  {
    return create(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns the local container.
   */
  public static EjbContainer create(ClassLoader loader)
  {
    synchronized (_localContainer) {
      EjbContainer container = _localContainer.getLevel(loader);

      if (container == null) {
        container = new EjbContainer(loader);

        _localContainer.set(container, loader);
      }

      return container;
    }
  }

  /**
   * Returns the local container.
   */
  public static EjbContainer getCurrent()
  {
    return getCurrent(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Returns the current environment container.
   */
  public static EjbContainer getCurrent(ClassLoader loader)
  {
    synchronized (_localContainer) {
      return _localContainer.get(loader);
    }
  }

  /**
   * Returns the parent loader
   */
  public EnvironmentClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * Returns the introspection class loader
   */
  public ClassLoader getIntrospectionClassLoader()
  {
    return _tempClassLoader;
  }

  /**
   * Returns the configuration manager.
   */
  public EjbConfigManager getConfigManager()
  {
    return _configManager;
  }

  /**
   * Returns the protocol manager.
   */
  public EjbProtocolManager getProtocolManager()
  {
    return _protocolManager;
  }

  /**
   * Returns the transaction manager.
   */
  public EjbTransactionManager getTransactionManager()
  {
    return _transactionManager;
  }

  /**
   * Returns the entity cache.
   */
  public EntityCache getEntityCache()
  {
    return _entityCache;
  }

  /**
   * Returns the amber persistence unit for ejb.
   */
  public AmberPersistenceUnit createEjbPersistenceUnit()
  {
    if (_ejbPersistenceUnit == null) {
      try {
        AmberContainer amber = AmberContainer.create(_classLoader);

        _ejbPersistenceUnit = amber.createPersistenceUnit("resin-ejb");
        _ejbPersistenceUnit.setBytecodeGenerator(false);
        _ejbPersistenceUnit.initLoaders();
        // _ejbPersistenceUnit.setTableCacheTimeout(_entityCacheTimeout);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) {
        throw new ConfigException(e);
      }
    }

    return _ejbPersistenceUnit;
  }

  //
  // configuration
  //

  /**
   * true if beans should be auto-compiled
   */
  public void setAutoCompile(boolean isAutoCompile)
  {
    _isAutoCompile = isAutoCompile;
  }

  /**
   * true if beans should be auto-compiled
   */
  public boolean isAutoCompile()
  {
    return _isAutoCompile;
  }

  /**
   * The work directory for EJB-generated files
   */
  public void setWorkDir(Path workDir)
  {
    _workDir = workDir;
  }

  /**
   * The work directory for EJB-generated files
   */
  public Path getWorkDir()
  {
    return _workDir;
  }

  /**
   * The JMS connection factory for the container.
   */
  public void setJmsConnectionFactory(ConnectionFactory factory)
  {
    _jmsConnectionFactory = factory;
  }

  /**
   * Sets the JMS connection factory for the container.
   */
  public ConnectionFactory getJmsConnectionFactory()
  {
    return _jmsConnectionFactory;
  }

  /**
   * Sets the consumer maximum for the container.
   */
  public void setMessageConsumerMax(int consumerMax)
  {
    _messageConsumerMax = consumerMax;
  }

  /**
   * The consumer maximum for the container.
   */
  public int getMessageConsumerMax()
  {
    return _messageConsumerMax;
  }

  /**
   * Copy defaults from the parent container when first created.
   */
  private void copyContainerDefaults(EjbContainer parent)
  {
    _isAutoCompile = parent._isAutoCompile;
    _jmsConnectionFactory = parent._jmsConnectionFactory;
    _messageConsumerMax = parent._messageConsumerMax;
  }

  //
  // AbstractServer management
  //


  /**
   * Adds a server.
   */
  public void addServer(AbstractServer server)
  {
    _serverList.add(server);

    getProtocolManager().addServer(server);
  }

  /**
   * Returns the server specified by the ejbName, or null if not found.
   */
  public AbstractServer getServer(String ejbName)
  {
    for  (AbstractServer server : _serverList) {
      if (server.getEJBName().equals(ejbName)) {
        return server;
      }
    }

    return null;
  }

  /**
   * Returns the server specified by the path and ejbName,
   * or null if not found.
   */
  public AbstractServer getServer(Path path, String ejbName)
  {
    String mappedName = path.getFullPath() + "#" + ejbName;

    for  (AbstractServer server : _serverList) {
      if (mappedName.equals(server.getId())) {
        return server;
      }
    }

    return null;
  }

  //
  // Deployment information
  //

  /**
   * Returns the information for a client remote configuration, e.g.
   * the <ejb-ref> needed for the client to properly connect.
   *
   * Only needed for the TCK.
   */
  public String getClientRemoteConfig()
  {
    StringBuilder sb = new StringBuilder();

    sb.append("<!-- test references -->");

    for (AbstractServer server : _serverList) {
      server.addClientRemoteConfig(sb);
    }

    return sb.toString();
  }

  //
  // ScanListener
  //

  /**
   * Adds a root URL
   */
  public void addRoot(Path root)
  {
    if (root.getURL().endsWith(".jar"))
      root = JarPath.create(root);

    // XXX: ejb/0fbn
    Path ejbJar = root.lookup("META-INF/ejb-jar.xml");
    if (ejbJar.canRead())
      getConfigManager().addEjbPath(ejbJar);

    _ejbUrls.add(root.getURL());
  }

  /**
   * Returns true if the root is a valid scannable root.
   */
  public boolean isRootScannable(Path root)
  {
    if (_ejbUrls.contains(root.getURL())) {
    }
    else if (! root.lookup("META-INF/ejb-jar.xml").canRead())
      return false;

    EjbRootConfig context = _configManager.createRootConfig(root);

    if (context.isScanComplete())
      return false;
    else {
      context.setScanComplete(true);
      return true;
    }
  }

  public boolean isScanMatch(CharBuffer annotationName)
  {
    if (annotationName.matches("javax.ejb.Stateless"))
      return true;
    else if (annotationName.matches("javax.ejb.Stateful"))
      return true;
    else if (annotationName.matches("javax.ejb.MessageDriven"))
      return true;
    else
      return false;
  }

  /**
   * Callback to note the class matches
   */
  public void classMatchEvent(EnvironmentClassLoader loader,
                              Path root,
                              String className)
  {
    EjbRootConfig config = _configManager.createRootConfig(root);

    config.addClassName(className);
  }

  //
  // lifecycle methods
  //

  public void init()
  {
  }

  public void start()
    throws ConfigException
  {
    try {
      AmberContainer.create().start();
      
      _configManager.start();

      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();

      for (AbstractServer server : _serverList) {
        try {
          thread.setContextClassLoader(server.getClassLoader());

          server.start();
        } finally {
          thread.setContextClassLoader(oldLoader);
        }
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e);
    }
  }

  /**
   * Closes the container.
   */
  public void destroy()
  {
    /*
      if (! _lifecycle.toDestroy())
        return;
    */

    try {
      ArrayList<AbstractServer> servers;
      servers = new ArrayList<AbstractServer>(_serverList);

      _serverList.clear();

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
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
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
    destroy();
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
