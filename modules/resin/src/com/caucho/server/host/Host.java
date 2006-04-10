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

package com.caucho.server.host;

import java.lang.ref.SoftReference;
  
import java.util.*;
import java.util.logging.*;

import javax.management.ObjectName;
import javax.management.MBeanServer;

import javax.servlet.jsp.el.VariableResolver;

import com.caucho.util.L10N;

import com.caucho.vfs.Path;
import com.caucho.vfs.MergePath;

import com.caucho.log.Log;

import com.caucho.el.EL;
import com.caucho.el.MapVariableResolver;

import com.caucho.loader.EnvironmentClassLoader;
import com.caucho.loader.EnvironmentBean;

import com.caucho.loader.enhancer.EnhancingClassLoader;

import com.caucho.config.SchemaBean;
import com.caucho.config.ConfigException;

import com.caucho.relaxng.CompactVerifierFactoryImpl;

import com.caucho.make.Dependency;
import com.caucho.make.AlwaysModified;

import com.caucho.jmx.Jmx;

import com.caucho.naming.Jndi;

import com.caucho.lifecycle.Lifecycle;

import com.caucho.server.deploy.EnvironmentDeployInstance;

import com.caucho.server.dispatch.Invocation;
import com.caucho.server.dispatch.ExceptionFilterChain;

import com.caucho.server.cluster.Cluster;

import com.caucho.server.webapp.ApplicationContainer;

/**
 * Resin's virtual host implementation.
 */
public class Host extends ApplicationContainer
  implements EnvironmentBean, Dependency, SchemaBean,
	     EnvironmentDeployInstance {
  static final Logger log = Log.open(Host.class);
  static final L10N L = new L10N(Host.class);
  
  private HostContainer _parent;

  // The Host entry
  private HostController _hostEntry;

  // The canonical host name.  The host name may include the port.
  private String _hostName = "";
  // The canonical URL
  private String _url;

  private String _serverName = "";
  private int _serverPort = 0;

  // The secure host
  private String _secureHostName;

  private boolean _isDefaultHost;

  // Alises
  private ArrayList<String> _aliasList = new ArrayList<String>();

  private Throwable _configException;
  
  private boolean _isRootDirSet;
  private boolean _isDocDirSet;

  private final Lifecycle _lifecycle;

  private String _configETag = null;
  
  /**
   * Creates the application with its environment loader.
   */
  public Host(HostContainer parent, HostController hostEntry, String hostName)
  {
    super(new EnvironmentClassLoader());

    try {
      _hostEntry = hostEntry;

      setParent(parent);
      setHostName(hostName);
    } catch (Throwable e) {
      _configException = e;
    } finally {
      _lifecycle = new Lifecycle(log, toString(), Level.INFO);
    }
  }

  /**
   * Sets the canonical host name.
   */
  private void setHostName(String name)
    throws ConfigException
  {
    _hostName = name;

    if (name.equals(""))
      _isDefaultHost = true;

    addHostAlias(name);

    getEnvironmentClassLoader().setId("host:" + name);
    
    // _jmxContext.put("J2EEServer", name);

    int p = name.indexOf("://");
    
    if (p >= 0)
      name = name.substring(p + 3);

    _serverName = name;

    p = name.lastIndexOf(':');
    if (p > 0) {
      _serverName = name.substring(0, p);
      
      boolean isPort = true;
      int port = 0;
      for (p++; p < name.length(); p++) {
	char ch = name.charAt(p);

	if ('0' <= ch && ch <= '9')
	  port = 10 * port + ch - '0';
	else
          isPort = false;
      }

      if (isPort)
	_serverPort = port;
    }
  }

  /**
   * Returns the entry name
   */
  public String getName()
  {
    return _hostEntry.getName();
  }

  /**
   * Returns the canonical host name.  The canonical host name may include
   * the port.
   */
  public String getHostName()
  {
    return _hostName;
  }

  /**
   * Returns the secure host name.  Used for redirects.
   */
  public String getSecureHostName()
  {
    return _secureHostName;
  }

  /**
   * Sets the secure host name.  Used for redirects.
   */
  public void setSecureHostName(String secureHostName)
  {
    _secureHostName = secureHostName;
  }

  /**
   * Returns the relax schema.
   */
  public String getSchema()
  {
    return "com/caucho/server/host/host.rnc";
  }
  
  /**
   * Returns the URL for the container.
   */
  public String getURL()
  {
    if (_url != null)
      return _url;
    else if (_hostName == null || _hostName.equals(""))
      return "";
    else if (_hostName.startsWith("http:") ||
             _hostName.startsWith("https:"))
      return _hostName;
    else
      return "http://" + _hostName;
  }

  /**
   * Adds an alias.
   */
  public void addHostAlias(String name)
  {
    name = name.toLowerCase();

    if (! _aliasList.contains(name))
      _aliasList.add(name);

    if (name.equals("") || name.equals("*"))
      _isDefaultHost = true;


    _hostEntry.addExtHostAlias(name);
  }

  /**
   * Gets the alias list.
   */
  public ArrayList<String> getAliasList()
  {
    return _aliasList;
  }

  /**
   * Returns true if matches the default host.
   */
  public boolean isDefaultHost()
  {
    return _isDefaultHost;
  }

  /**
   * Sets the parent container.
   */
  private void setParent(HostContainer parent)
  {
    _parent = parent;

    setDispatchServer(parent.getDispatchServer());

    if (! _isRootDirSet) {
      setRootDirectory(parent.getRootDirectory());
      _isRootDirSet = false;
    }
  }

  /**
   * Gets the environment class loader.
   */
  public EnvironmentClassLoader getEnvironmentClassLoader()
  {
    return (EnvironmentClassLoader) getClassLoader();
  }

  /**
   * Sets the root dir.
   */
  public void setRootDirectory(Path rootDir)
  {
    super.setRootDirectory(rootDir);
    _isRootDirSet = true;

    if (! _isDocDirSet) {
      setDocumentDirectory(rootDir);
      _isDocDirSet = false;
    }
  }

  /**
   * Sets the doc dir.
   */
  public void setDocumentDirectory(Path docDir)
  {
    super.setDocumentDirectory(docDir);
    _isDocDirSet = true;
  }

  /**
   * Sets the config exception.
   */
  public void setConfigException(Throwable e)
  {
    if (e != null) {
      // XXX:
      _configException = e;
      getEnvironmentClassLoader().addDependency(AlwaysModified.create());
    }
  }

  /**
   * Gets the config exception.
   */
  public Throwable getConfigException()
  {
    return _configException;
  }

  /**
   * Returns the current cluster.
   */
  public Cluster getCluster()
  {
    return Cluster.getCluster(getClassLoader());
  }

  /**
   * Returns the config etag.
   */
  public String getConfigETag()
  {
    return _configETag;
  }

  /**
   * Returns the config etag.
   */
  public void setConfigETag(String etag)
  {
    _configETag = etag;
  }

  /**
   * Starts the host.
   */
  public void start()
  {
    if (! _lifecycle.toStarting())
      return;
    
    if (getURL().equals("") && _parent != null) {
      _url = _parent.getURL();
    }

    EnvironmentClassLoader loader;
    loader = getEnvironmentClassLoader();
    
    loader.setId("host:" + getURL());
                       
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(loader);
      
      super.start();
      
      loader.start();

      if (_parent != null)
	_parent.clearCache();
    } finally {
      _lifecycle.toActive();
      
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Clears the cache
   */
  public void clearCache()
  {
    super.clearCache();

    setConfigETag(null);
  }

  /**
   * Builds the invocation for the host.
   */
  public void buildInvocation(Invocation invocation)
    throws Exception
  {
    invocation.setHostName(_serverName);
    invocation.setPort(_serverPort);

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(getClassLoader());

      if (_configException == null)
        super.buildInvocation(invocation);
      else {
        invocation.setFilterChain(new ExceptionFilterChain(_configException));
	invocation.setDependency(AlwaysModified.create());
      }
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Returns true if the host is modified.
   */
  public boolean isModified()
  {
    return (isDestroyed() || getEnvironmentClassLoader().isModified());
  }

  /**
   * Returns true if the host is modified.
   */
  public boolean isModifiedNow()
  {
    return (isDestroyed() || getEnvironmentClassLoader().isModifiedNow());
  }

  /**
   * Returns true if the host deploy was an error
   */
  public boolean isDeployError()
  {
    return _configException != null;
  }

  /**
   * Returns true if the host is idle
   */
  public boolean isDeployIdle()
  {
    return false;
  }
  
  /**
   * Stops the host.
   */
  public boolean stop()
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      EnvironmentClassLoader envLoader = getEnvironmentClassLoader();
      thread.setContextClassLoader(envLoader);

      if (! _lifecycle.toStopping())
	return false;
      
      super.stop();
      
      envLoader.stop();

      return true;
    } finally {
      _lifecycle.toStop();
      
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  /**
   * Closes the host.
   */
  public void destroy()
  {
    stop();
    
    if (isDestroyed())
      return;

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    EnvironmentClassLoader classLoader = getEnvironmentClassLoader();
    
    thread.setContextClassLoader(classLoader);
    
    try {
      super.destroy();
    } finally {
      thread.setContextClassLoader(oldLoader);

      classLoader.destroy();
    }
  }

  public String toString()
  {
    return "Host[" + getHostName() + "]";
  }
}
