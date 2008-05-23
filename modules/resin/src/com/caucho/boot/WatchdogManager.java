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

package com.caucho.boot;

import com.caucho.config.Config;
import com.caucho.config.ConfigException;
import com.caucho.config.types.RawString;
import com.caucho.loader.*;
import com.caucho.log.EnvironmentStream;
import com.caucho.log.LogConfig;
import com.caucho.log.RotateStream;
import com.caucho.server.cluster.Cluster;
import com.caucho.server.cluster.ClusterServer;
import com.caucho.server.cluster.Server;
import com.caucho.server.dispatch.ServletMapping;
import com.caucho.server.resin.ResinELContext;
import com.caucho.server.host.Host;
import com.caucho.server.host.HostConfig;
import com.caucho.server.port.ProtocolDispatchServer;
import com.caucho.server.webapp.WebApp;
import com.caucho.server.webapp.WebAppConfig;
import com.caucho.util.*;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;
import com.caucho.vfs.WriteStream;
import com.caucho.webbeans.manager.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Process responsible for watching a backend watchdog.
 */
class WatchdogManager extends ProtocolDispatchServer {
  private static L10N _L;
  private static Logger _log;

  private static WatchdogManager _watchdog;

  private WatchdogArgs _args;

  private int _watchdogPort;

  private String _adminCookie;
  private BootManagementConfig _management;

  private Server _dispatchServer;
  
  private HashMap<String,Watchdog> _watchdogMap
    = new HashMap<String,Watchdog>();

  WatchdogManager(String []argv)
    throws Exception
  {
    _watchdog = this;

    _args = new WatchdogArgs(argv);

    Vfs.setPwd(_args.getRootDirectory());

    Path logPath = getLogDirectory().lookup("watchdog-manager.log");

    RotateStream logStream = RotateStream.create(logPath);
    logStream.init();
    WriteStream out = logStream.getStream();
    out.setDisableClose(true);

    EnvironmentStream.setStdout(out);
    EnvironmentStream.setStderr(out);

    LogConfig log = new LogConfig();
    log.setName("");
    log.setPath(logPath);
    log.init();

    ThreadPool.getThreadPool().setThreadIdleMin(1);
    ThreadPool.getThreadPool().setThreadIdleMax(5);

    ResinELContext elContext = _args.getELContext();
    
    WebBeansContainer webBeans = WebBeansContainer.create();
    webBeans.addSingletonByName(elContext.getResinHome(), "resinHome");
    webBeans.addSingletonByName(elContext.getJavaVar(), "java");
    webBeans.addSingletonByName(elContext.getResinVar(), "resin");
    webBeans.addSingletonByName(elContext.getServerVar(), "server");

    _watchdogPort = _args.getWatchdogPort();
    readConfig(_args);

    Watchdog server = _watchdogMap.get(_args.getServerId());

    if (server == null)
      throw new IllegalStateException(L().l("'{0}' is an unknown server",
					    _args.getServerId()));

    server.getConfig().logInit(logStream);
    
    Cluster cluster = new Cluster();
    ClusterServer clusterServer = new ClusterServer(cluster);

    if (_watchdogPort > 0)
      clusterServer.setPort(_watchdogPort);
    else
      clusterServer.setPort(server.getWatchdogPort());

    clusterServer.setAddress(server.getWatchdogAddress());

    clusterServer.getClusterPort().setMinSpareListen(1);
    clusterServer.getClusterPort().setMaxSpareListen(2);
      
    _dispatchServer = new Server(clusterServer);

    HostConfig hostConfig = new HostConfig();
    hostConfig.setId("resin-admin");

    hostConfig.init();
    
    _dispatchServer.addHost(hostConfig);
    _dispatchServer.init();
    _dispatchServer.start();

    Host host = _dispatchServer.getHost("resin-admin", 0);

    WebAppConfig webAppConfig = new WebAppConfig();
    webAppConfig.setId("");
    webAppConfig.setRootDirectory(new RawString("watchdog-manager"));

    host.addWebApp(webAppConfig);

    WebApp webApp = host.findWebAppByURI("/");

    ServletMapping servlet = webApp.createServletMapping();

    servlet.setServletName("watchdog");
    servlet.addURLPattern("/watchdog");
    servlet.setServletClass("com.caucho.boot.WatchdogServlet");
    servlet.init();

    webApp.addServletMapping(servlet);
    try {
      host.updateWebAppDeploy("/");
    } catch (Throwable e) {
      log().log(Level.WARNING, e.toString(), e);
    }

    webApp.start();
  }

  static WatchdogManager getWatchdog()
  {
    return _watchdog;
  }

  public void setAdminCookie(String cookie)
  {
    if (_adminCookie == null)
      _adminCookie = cookie;
  }

  public String getAdminCookie()
  {
    if (_adminCookie != null)
      return _adminCookie;
    else if (_management != null)
      return _management.getAdminCookie();
    else
      return null;    
  }

  Path getRootDirectory()
  {
    return _args.getRootDirectory();
  }
  
  Path getLogDirectory()
  {
    Path logDirectory = _args.getLogDirectory();

    if (logDirectory != null)
      return logDirectory;
    else
      return getRootDirectory().lookup("log");
  }

  boolean authenticate(String password)
  {
    String cookie = getAdminCookie();
    
    if (password == null && cookie == null)
      return true;
    else if  (password != null && password.equals(getAdminCookie()))
      return true;
    else
      return false;
  }
  
  Watchdog findServer(String id)
  {
    return _watchdogMap.get(id);
  }
  
  /**
   * Called from the Hessian API to report the status of the watchdog
   * 
   * @return a human-readable description of the current status
   */
  String status()
  {
    StringBuilder sb = new StringBuilder();
    
    ArrayList<String> keys = new ArrayList<String>(_watchdogMap.keySet());
    Collections.sort(keys);
    
    for (String key : keys) {
      Watchdog watchdog = _watchdogMap.get(key);

      sb.append("\n");
      sb.append("server '" + key + "' : " + watchdog.getState() + "\n");

      if (getAdminCookie() == null)
	sb.append("  password: missing\n");
      else
	sb.append("  password: ok\n");
      
      sb.append("  user: " + System.getProperty("user.name"));
        
      if (watchdog.getGroupName() != null)
	sb.append("(" + watchdog.getGroupName() + ")");
        
      sb.append("\n");
      
      sb.append("  root: " + watchdog.getResinRoot() + "\n");
      sb.append("  conf: " + watchdog.getResinConf() + "\n");
    }
    
    return sb.toString();
  }

  /**
   * Called from the Hessian API to start a server.
   * 
   * @param argv the command-line arguments to start the server
   */
  void startServer(String []argv)
    throws ConfigException
  {
    WatchdogArgs args = new WatchdogArgs(argv);

    Vfs.setPwd(_args.getRootDirectory());

    try {
      readConfig(args);
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
    
    String serverId = args.getServerId();

    if (args.isDynamicServer())
      serverId = args.getDynamicAddress() + "-" + args.getDynamicPort();
    
    Watchdog watchdog = _watchdogMap.get(serverId);

    if (watchdog == null)
      throw new ConfigException(L().l("No matching <server> found for -server '{0}' in '{1}'",
				      serverId, _args.getResinConf()));

    watchdog.start();
  }

  /**
   * Called from the hessian API to gracefully stop a Resin instance
   * 
   * @param serverId the Resin instance to stop
   */
  void stopServer(String serverId)
  {
    Watchdog watchdog = _watchdogMap.get(serverId);
    
    if (watchdog == null)
      throw new ConfigException(L().l("No matching <server> found for -server '{0}' in {1}",
				      serverId, _args.getResinConf()));
    
    watchdog.stop();
  }

  /**
   * Called from the hessian API to forcibly kill a Resin instance
   * 
   * @param serverId the server id to kill
   */
  void killServer(String serverId)
  {
    Watchdog watchdog = _watchdogMap.get(serverId);
    
    if (watchdog == null)
      throw new ConfigException(L().l("No matching <server> found for -server '{0}' in {1}",
				      serverId, _args.getResinConf()));
    
    watchdog.kill();
  }

  /**
   * Called from the hessian API to restart a Resin instance.
   * 
   * @param serverId the server identifier to restart
   * @param argv the command-line arguments to apply to the start
   */
  void restartServer(String serverId, String []argv)
  {
    Watchdog server = _watchdogMap.get(serverId);
    
    if (server != null)
      server.stop();
    
    startServer(argv);
   }

  private void readConfig(WatchdogArgs args)
    throws Exception
  {
    Config config = new Config();
    // ignore since we don't want to start databases
    config.setIgnoreEnvironment(true);

    Vfs.setPwd(args.getRootDirectory());
    BootResinConfig resin = new BootResinConfig(args);

    config.configure(resin,
		     args.getResinConf(),
		     "com/caucho/server/resin/resin.rnc");

    if (args.isDynamicServer()) {
      String clusterId = args.getDynamicCluster();
      String address = args.getDynamicAddress();
      int port = args.getDynamicPort();

      BootClusterConfig cluster = resin.findCluster(clusterId);

      if (cluster == null) {
	throw new ConfigException(L().l("'{0}' is an unknown cluster",
				      clusterId));
      }
      
      WatchdogConfig server = cluster.createServer();
      server.setId(address + "-" + port);
      server.setAddress(address);
      server.setPort(port);
      cluster.addServer(server);
    }

    WatchdogConfig server = resin.findServer(args.getServerId());
      
    Watchdog watchdog = _watchdogMap.get(server.getId());
      
    if (watchdog == null)
      _watchdogMap.put(server.getId(), new Watchdog(server));
    else {
      watchdog.setConfig(server);
    }
  }

  /**
   * The launching program for the watchdog manager, generally called
   * from ResinBoot.
   */
  public static void main(String []argv)
    throws Exception
  {
    WatchdogManager manager = new WatchdogManager(argv);
    manager.startServer(argv);
  }

  private static L10N L()
  {
    if (_L == null)
      _L = new L10N(ResinBoot.class);

    return _L;
  }

  private static Logger log()
  {
    if (_log == null)
      _log = Logger.getLogger(ResinBoot.class.getName());

    return _log;
  }
}
