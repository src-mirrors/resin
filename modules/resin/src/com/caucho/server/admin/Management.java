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

package com.caucho.server.admin;

import com.caucho.bam.BamBroker;
import com.caucho.hemp.broker.*;
import com.caucho.config.ConfigException;
import com.caucho.config.program.ContainerProgram;
import com.caucho.config.types.RawString;
import com.caucho.lifecycle.*;
import com.caucho.server.cluster.Cluster;
import com.caucho.server.cluster.DeployManagementService;
import com.caucho.server.cluster.Server;
import com.caucho.server.host.HostConfig;
import com.caucho.server.resin.*;
import com.caucho.security.*;
import com.caucho.server.security.*;
import com.caucho.webbeans.manager.*;
import com.caucho.util.L10N;
import com.caucho.vfs.*;

import javax.annotation.*;
import javax.resource.spi.ResourceAdapter;
import javax.webbeans.*;
import java.util.logging.Logger;

/**
 * Configuration for management.
 */
public class Management
{
  private static L10N L = new L10N(Management.class);
  private static Logger log = Logger.getLogger(Management.class.getName());

  public static final String HOST_NAME = "admin.caucho";

  private Cluster _cluster;
  private Resin _resin;
  private Server _server;
  private Path _path;
  
  private final BamBroker _adminBroker;

  private HostConfig _hostConfig;

  private ManagementAuthenticator _auth;

  protected TransactionManager _transactionManager;

  private Lifecycle _lifecycle = new Lifecycle();

  public Management()
  {
    _resin = Resin.getCurrent();

    HempBrokerManager brokerManager = HempBrokerManager.getCurrent();

    String serverId = _resin.getServerId();
    
    String brokerName;
    
    if (! "".equals(serverId))
      brokerName = serverId + ".resin.caucho";
    else
      brokerName = "default.resin.caucho";
    
    _adminBroker = new HempBroker(brokerName);

    brokerManager.addBroker(brokerName, _adminBroker);
    brokerManager.addBroker("resin.caucho", _adminBroker);
  }

  public static Path getCurrentPath()
  {
    Resin resin = Resin.getCurrent();

    Management management = resin.getManagement();

    return management.getPath();
  }

  public void setCluster(Cluster cluster)
  {
    _cluster = cluster;
  }
  
  public void setResin(Resin resin)
  {
    _resin = resin;
  }
  
  public void setServer(Server server)
  {
    _server = server;
  }

  public String getServerId()
  {
    return Cluster.getServerId();
  }

  /**
   * Sets the path for storing managment related logs and files,
   * default is "admin".
   */
  public void setPath(Path path)
  {
    _path = path;
  }
  
  public Path getPath()
  {
    Path path = _path;
    
    if (path == null)
      path = _resin.getRootDirectory().lookup("admin");

    if (path instanceof MemoryPath) { // QA
      path = Vfs.lookup("file:/tmp/caucho/qa/admin");
    }

    return path;
  }

  /**
   * Adds a user
   */
  public void addUser(User user)
  {
    if (_auth == null)
      _auth = new ManagementAuthenticator();

    _auth.addUser(user.getName(), user.getPasswordUser());
  }

  /**
   * Returns the management cookie.
   */
  public String getRemoteCookie()
  {
    if (_auth != null)
      return _auth.getHash();
    else
      return null;
  }

  /**
   * Returns the admin broker
   */
  public BamBroker getAdminBroker()
  {
    return _adminBroker;
  }

  /**
   * Create and configure the j2ee deploy service.
   */
  public Object createDeployService()
  {
    log.warning(L.l("deploy-service requires Resin Professional"));

    return new ContainerProgram();
  }

  /**
   * Create and configure the jmx service.
   */
  public Object createJmxService()
  {
    log.warning(L.l("jmx-service requires Resin Professional"));

    return new Object();
  }

  /**
   * Create and configure the persistent logger.
   */
  public Object createLogService()
  {
    log.warning(L.l("log-service requires Resin Professional"));

    return new Object();
  }

  /**
   * Creates the remote service
   */
  public Object createRemoteService()
  {
    log.warning(L.l("remote-service requires Resin Professional"));
    
    return new Object();
  }

  /**
   * Create and configure the stat service
   */
  public Object createStatService()
  {
    log.warning(L.l("stat-service requires Resin Professional"));
    
    return new Object();
  }

  /**
   * Create and configure the stat service
   */
  public ResourceAdapter createPing()
  {
    log.warning(L.l("'ping' requires Resin Professional"));
    
    return null;
  }

  /**
   * Create and configure the stat service
   */
  public void addPing(ResourceAdapter ping)
  {
    log.warning(L.l("'ping' requires Resin Professional"));
  }

  /**
   * Create and configure the transaction log.
   */
  public Object createXaLogService()
  {
    log.warning(L.l("xa-log-service requires Resin Professional"));
    
    return new Object();
  }

  /**
   * backwards compat
   */
  @Deprecated
  public void setManagementPath(Path managementPath)
  {
    if (_path == null)
      _path = managementPath;
  }

  /**
   * backwards compat
   */
  @Deprecated
  public TransactionManager createTransactionManager()
    throws ConfigException
  {
    if (_transactionManager == null)
      _transactionManager = new TransactionManager(this);

    return _transactionManager;
  }

  @PostConstruct
  public void init()
  {
    try {
      if (! _lifecycle.toInit())
	return;
      
      if (_auth != null) {
	_auth.init();
      
	WebBeansContainer webBeans = WebBeansContainer.create();

	webBeans.addSingleton(_auth, "resin-admin", Standard.class);
      }

      if (_transactionManager != null)
	_transactionManager.start();
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  /**
   * Starts the management server
   */
  public void start(Server server)
  {
    try {
      if (getPath() != null)
        getPath().mkdirs();
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  public HostConfig getHostConfig()
  {
    if (_hostConfig == null) {
      HostConfig hostConfig = new HostConfig();
      hostConfig.setId(HOST_NAME);
      /*
      if (_path != null) {
	hostConfig.setRootDirectory(new RawString(_path.getFullPath() + "/bogus-admin"));
      }
      else
	hostConfig.setRootDirectory(new RawString("/bogus-admin"));
      */
      hostConfig.setRootDirectory(new RawString("/bogus-admin"));
      
      hostConfig.setSkipDefaultConfig(true);

      hostConfig.init();

      try {
	if (_server == null)
	  _server = _resin.getServer();

	if (_server != null)
	  _server.addHost(hostConfig);
      } catch (RuntimeException e) {
	throw e;
      } catch (Exception e) {
	throw ConfigException.create(e);
      }

      _hostConfig = hostConfig;
    }

    return _hostConfig;
  }

  protected Cluster getCluster()
  {
    if (_cluster == null)
      _cluster = Cluster.getLocal();

    return _cluster;
  }

  public void dumpThreads()
  {
  }

  public void destroy()
  {
    TransactionManager transactionManager = _transactionManager;
    _transactionManager = null;

    if (transactionManager != null)
      transactionManager.destroy();
  }

  public static class User {
    private String _name;
    private String _password;
    private boolean _isDisabled;

    public void setName(String name)
    {
      _name = name;
    }

    public String getName()
    {
      return _name;
    }

    public void setPassword(String password)
    {
      _password = password;
    }

    public String getPassword()
    {
      return _password;
    }

    public void setDisable(boolean isDisabled)
    {
      _isDisabled = isDisabled;
    }

    public boolean isDisable()
    {
      return _isDisabled;
    }

    PasswordUser getPasswordUser()
    {
      if (_name == null)
	throw new ConfigException(L.l("management <user> requires a 'name' attribute"));
      
      boolean isAnonymous = false;
      
      return new PasswordUser(new BasicPrincipal(_name),
			      _password.toCharArray(),
			      _isDisabled, isAnonymous,
			      new String[] { "resin-admin" });
    }
  }
}
