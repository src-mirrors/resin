/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.caucho.admin.RemoteAdminService;
import com.caucho.cloud.network.NetworkListenSystem;
import com.caucho.cloud.topology.CloudCluster;
import com.caucho.cloud.topology.CloudPod;
import com.caucho.cloud.topology.CloudSystem;
import com.caucho.cloud.topology.TopologyService;
import com.caucho.config.Config;
import com.caucho.config.ConfigException;
import com.caucho.config.inject.BeanBuilder;
import com.caucho.config.inject.DefaultLiteral;
import com.caucho.config.inject.InjectManager;
import com.caucho.config.lib.ResinConfigLibrary;
import com.caucho.config.types.Period;
import com.caucho.env.service.ResinSystem;
import com.caucho.env.thread.ThreadPool;
import com.caucho.hemp.broker.HempBroker;
import com.caucho.jmx.Jmx;
import com.caucho.lifecycle.Lifecycle;
import com.caucho.loader.DependencyCheckInterval;
import com.caucho.loader.DynamicClassLoader;
import com.caucho.log.EnvironmentStream;
import com.caucho.log.LogHandlerConfig;
import com.caucho.log.RotateStream;
import com.caucho.network.listen.TcpSocketLinkListener;
import com.caucho.security.AdminAuthenticator;
import com.caucho.security.Authenticator;
import com.caucho.server.cluster.Server;
import com.caucho.server.http.HttpProtocol;
import com.caucho.server.resin.Resin;
import com.caucho.server.resin.ResinArgs;
import com.caucho.server.resin.ResinELContext;
import com.caucho.server.resin.ResinWatchdog;
import com.caucho.server.util.JniCauchoSystem;
import com.caucho.server.webbeans.ResinServerConfigLibrary;
import com.caucho.util.Alarm;
import com.caucho.util.AlarmListener;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;
import com.caucho.vfs.WriteStream;

/**
 * Process responsible for watching a backend watchdog.
 */
class WatchdogManager implements AlarmListener {
  private static L10N _L;
  private static Logger _log;

  private static WatchdogManager _watchdog;

  private Lifecycle _lifecycle = new Lifecycle();

  private WatchdogArgs _args;

  private int _watchdogPort;

  private String _adminCookie;
  private BootManagementConfig _management;
  private final ResinSystem _system;

  private Server _server;
  private TcpSocketLinkListener _httpPort;

  private HashMap<String,WatchdogChild> _watchdogMap
    = new HashMap<String,WatchdogChild>();

  WatchdogManager(String []argv)
    throws Exception
  {
    _watchdog = this;

    _args = new WatchdogArgs(argv);
    
    String serverId = _args.getServerId();

    if (_args.isDynamicServer()) {
      serverId = _args.getDynamicServerId();
    }
    
    ResinArgs resinArgs = new ResinArgs();
    resinArgs.setRootDirectory(_args.getRootDirectory());
    resinArgs.setServerId(serverId);
    
    if (_args.getDataDirectory() != null)
      resinArgs.setDataDirectory(_args.getDataDirectory());
    
    Resin resin = new ResinWatchdog(resinArgs);
    
    _system = resin.getResinSystem();

    Vfs.setPwd(_args.getRootDirectory());
    
    boolean isLogDirectoryExists = getLogDirectory().exists();

    Path logPath = getLogDirectory().lookup("watchdog-manager.log");
    
    try {
      getLogDirectory().mkdirs();
    } catch (Exception e) {
      log().log(Level.ALL, e.toString(), e);
    }
    
    // #4333 - check watchdog-manager.log can be written
    WriteStream testOut = logPath.openAppend();
    testOut.close();
    
    if (! logPath.canWrite()) {
      throw new ConfigException("Cannot open " + logPath.getNativePath()
                                + " required for Resin start. Please check permissions");
    }

    RotateStream logStream = RotateStream.create(logPath);
    logStream.setRolloverSize(64L * 1024 * 1024);
    logStream.init();
    WriteStream out = logStream.getStream();
    out.setDisableClose(true);

    EnvironmentStream.setStdout(out);
    EnvironmentStream.setStderr(out);

    LogHandlerConfig log = new LogHandlerConfig();
    log.setName("");
    log.setPath(logPath);
    log.init();

    Thread thread = Thread.currentThread();
    thread.setContextClassLoader(_system.getClassLoader());

    ThreadPool.getThreadPool().setIdleMin(4);
    ThreadPool.getThreadPool().setPriorityIdleMin(4);

    ResinELContext elContext = _args.getELContext();
    
    resin.preConfigureInit();
    
    // XXX: needs to be config

    InjectManager cdiManager = InjectManager.create();

    Config.setProperty("resinHome", elContext.getResinHome());
    Config.setProperty("resin", elContext.getResinVar());
    Config.setProperty("server", elContext.getServerVar());
    Config.setProperty("java", elContext.getJavaVar());
    Config.setProperty("system", System.getProperties());
    Config.setProperty("getenv", System.getenv());

    ResinConfigLibrary.configure(cdiManager);
    ResinServerConfigLibrary.configure(cdiManager);

    _watchdogPort = _args.getWatchdogPort();
    
    readConfig(serverId, _args);
    
    WatchdogChild server = null;
    
    if (serverId == null)
      serverId = "default";
      
    server = _watchdogMap.get(serverId);
    System.out.println("SERVER: " + server);
    
    if (server == null)
      throw new IllegalStateException(L().l("'{0}' is an unknown server",
                                            serverId));
    
    JniBoot boot = new JniBoot();
    Path logDirectory = getLogDirectory();

    if (boot.isValid()) {
      if (! isLogDirectoryExists) {
        logDirectory.mkdirs();

        boot.chown(logDirectory, server.getUserName(), server.getGroupName());
      }
    }

    server.getConfig().logInit(logStream);

    resin.preConfigureInit();
    // resin.setConfigFile(_args.getResinConf().getNativePath());

    thread = Thread.currentThread();
    thread.setContextClassLoader(resin.getClassLoader());

    CloudSystem cloudSystem = TopologyService.getCurrent().getSystem();
    
    CloudCluster cluster = cloudSystem.createCluster("watchdog");
    CloudPod pod = cluster.createPod();
    pod.createStaticServer("default", "localhost", -1, false);

    _server = resin.createServer();
    
    thread.setContextClassLoader(_server.getClassLoader());
    
    
    NetworkListenSystem listenService 
      = _system.getService(NetworkListenSystem.class);
    
    _httpPort = new TcpSocketLinkListener();
    _httpPort.setProtocol(new HttpProtocol());

    if (_watchdogPort > 0)
      _httpPort.setPort(_watchdogPort);
    else
      _httpPort.setPort(server.getWatchdogPort());

    _httpPort.setAddress(server.getWatchdogAddress());

    _httpPort.setAcceptThreadMin(2);
    _httpPort.setAcceptThreadMax(3);

    _httpPort.init();

    listenService.addListener(_httpPort);
    
    ClassLoader oldLoader = thread.getContextClassLoader();

    try {
      thread.setContextClassLoader(_system.getClassLoader());

      cdiManager = InjectManager.create();
      AdminAuthenticator auth = null;

      if (_management != null)
        auth = _management.getAdminAuthenticator();

      if (auth != null) {
        BeanBuilder<Authenticator> factory = cdiManager.createBeanFactory(Authenticator.class);

        factory.type(Authenticator.class);
        factory.type(AdminAuthenticator.class);
        factory.qualifier(DefaultLiteral.DEFAULT);

        cdiManager.addBean(factory.singleton(auth));
      }

      DependencyCheckInterval depend = new DependencyCheckInterval();
      depend.setValue(new Period(-1));
      depend.init();

      RemoteAdminService adminService = new RemoteAdminService();
      adminService.setAuthenticationRequired(false);
      adminService.init();

      HempBroker broker = HempBroker.getCurrent();
      
      WatchdogService service
        = new WatchdogService(this, "watchdog@admin.resin.caucho", broker);


      /*
      broker.setAdmin(true);
      broker.setAllowNullAdminAuthenticator(true);
      */

      broker.createAgent(service.getActor());

      ResinSystem.getCurrent().start();

      _lifecycle.toActive();
      
      // valid checker
      new Alarm(this).queue(60000);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  ClassLoader getClassLoader()
  {
    return _system.getClassLoader();
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

  boolean isActive()
  {
    return _server.isActive() && _httpPort.isActive();
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
    else if  (password != null && password.equals(cookie))
      return true;
    else
      return false;
  }

  WatchdogChild findServer(String id)
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

    synchronized (_watchdogMap) {
      ArrayList<String> keys = new ArrayList<String>(_watchdogMap.keySet());
      Collections.sort(keys);
      
      sb.append("\nwatchdog:\n");
      sb.append("  watchdog-pid: " + getWatchdogPid());

      for (String key : keys) {
        WatchdogChild child = _watchdogMap.get(key);

        sb.append("\n\n");
        sb.append("server '" + child.getId() + "' : " + child.getState() + "\n");

        if (getAdminCookie() == null)
          sb.append("  password: missing\n");
        else
          sb.append("  password: ok\n");
        
        sb.append("  watchdog-user: " + System.getProperty("user.name") + "\n");

        if (child.getUserName() != null)
          sb.append("  user: " + child.getUserName());
        else
          sb.append("  user: " + System.getProperty("user.name"));

        if (child.getGroupName() != null)
          sb.append("(" + child.getGroupName() + ")");

        sb.append("\n");

        sb.append("  root: " + child.getResinRoot() + "\n");
        sb.append("  conf: " + child.getResinConf() + "\n");

        if (child.getPid() > 0)
          sb.append("  pid: " + child.getPid() + "\n");
        
        sb.append("  uptime: " + child.getUptimeString() + "\n");
      }
    }

    return sb.toString();
  }

  /**
   * Called from the Hessian API to start a server.
   *
   * @param argv the command-line arguments to start the server
   */
  void startServer(String serverId, String []argv)
    throws ConfigException
  {
    synchronized (_watchdogMap) {
      WatchdogArgs args = new WatchdogArgs(argv, false);

      Vfs.setPwd(_args.getRootDirectory());
      
      if (serverId == null)
        serverId = args.getServerId();

      try {
        readConfig(serverId, args);
      } catch (Exception e) {
        throw ConfigException.create(e);
      }

      if (serverId == null)
        serverId = args.getServerId();

      if (args.isDynamicServer())
        serverId = args.getDynamicServerId();
      
      if (serverId == null)
        serverId = "default";

      WatchdogChild watchdog = _watchdogMap.get(serverId);

      if (watchdog == null)
        throw new ConfigException(L().l("No matching <server> found for start -server '{0}' in '{1}'",
                                        serverId, _args.getResinConf()));

      watchdog.start();
    }
  }

  /**
   * Called from the hessian API to gracefully stop a Resin instance
   *
   * @param serverId the Resin instance to stop
   */
  void stopServer(String serverId)
  {
    synchronized (_watchdogMap) {
      WatchdogChild watchdog = getWatchdog(serverId);
      
            if (watchdog == null)
        throw new ConfigException(L().l("No matching <server> found for stop -server '{0}' in {1}",
                                        serverId, _args.getResinConf()));

      watchdog.stop();
    }
  }
  
  private WatchdogChild getWatchdog(String serverId)
  {
    WatchdogChild watchdog = _watchdogMap.get(serverId);
  
    if (watchdog == null 
        && (serverId == null || "".equals(serverId))
        && _watchdogMap.size() == 1) {
      watchdog = _watchdogMap.values().iterator().next();
    }
    
    return watchdog;
  }

  /**
   * Called from the hessian API to forcibly kill a Resin instance
   *
   * @param serverId the server id to kill
   */
  void killServer(String serverId)
  {
    // no synchronization because kill must avoid blocking

    WatchdogChild watchdog = getWatchdog(serverId);

    if (watchdog == null)
      throw new ConfigException(L().l("No matching <server> found for -server '{0}' in {1}",
                                      serverId, _args.getResinConf()));

    watchdog.kill();
  }
  
  /**
   * Checks to see if any watchdog is active.
   */
  boolean isEmpty()
  {
    synchronized (_watchdogMap) {
      for (WatchdogChild child : _watchdogMap.values()) {
        if (child.isActive())
          return false;
      }
    }
    
    return true;
  }

  /**
   * Called from the HMTP API to restart a Resin instance.
   *
   * @param serverId the server identifier to restart
   * @param argv the command-line arguments to apply to the start
   */
  void restartServer(String serverId, String []argv)
  {
    synchronized (_watchdogMap) {
      WatchdogChild server = _watchdogMap.get(serverId);

      if (server != null)
        server.restart();
      else
        startServer(serverId, argv);
    }
  }

  boolean isValid()
  {
    return _server != null && _server.isActive();
  }

  private WatchdogChild readConfig(String serverId, WatchdogArgs args)
    throws Exception
  {
    Config config = new Config();
    // ignore since we don't want to start databases
    config.setIgnoreEnvironment(true);

    Vfs.setPwd(args.getRootDirectory());
    BootResinConfig resin = new BootResinConfig(_system, args);

    config.configure(resin,
                     args.getResinConf(),
                     "com/caucho/server/resin/resin.rnc");

    if (_management == null)
      _management = resin.getManagement();

    /*
    // The configuration file has already been validated by ResinBoot, so
    // it doesn't need a second validation
    config.configure(resin,
                     args.getResinConf());
    */

    if (serverId == null)
      serverId = args.getServerId();
    
    WatchdogConfig serverConfig = null;

    if (args.isDynamicServer() || resin.isJoinCluster()) {
      String clusterId = resin.getJoinCluster();
      
      if (args.isDynamicServer())
        clusterId = args.getDynamicCluster();
      
      String address = args.getDynamicAddress();
      int port = args.getDynamicPort();

      BootClusterConfig cluster = resin.findCluster(clusterId);

      if (cluster == null) {
        throw new ConfigException(L().l("'{0}' is an unknown cluster",
                                      clusterId));
      }

      serverConfig = cluster.createServer();
      serverId = args.getDynamicServerId();
      serverConfig.setId(serverId);
      serverConfig.setAddress(address);
      serverConfig.setPort(port);
      cluster.addServer(serverConfig);
    }
    else {
      WatchdogClient client = resin.findClient(serverId, args); 
        
        //resin.findClient(serverId);

      if (client != null)
        serverConfig = client.getConfig();
      else
        serverConfig = resin.findServer(serverId);
    }

    WatchdogChild watchdog = _watchdogMap.get(serverId);

    if (watchdog != null) {
      if (watchdog.isActive()) {
        throw new ConfigException(L().l("server '{0}' cannot be started because a running instance already exists.  stop or restart the old server first.",
                                        serverId));
      }

      watchdog = _watchdogMap.remove(serverId);

      if (watchdog != null)
        watchdog.close();
    }
    
    if (serverConfig == null) {
      throw new ConfigException(L().l("server '{0}' cannot be started because no configuration has been found.",
                                      serverId));
    }

    watchdog = new WatchdogChild(_system, serverConfig);
    
    if (serverId == null)
      serverId = "default";

    _watchdogMap.put(serverId, watchdog);

    return watchdog;
  }
  
  private int getWatchdogPid()
  {
    try {
      MBeanServer server = Jmx.getGlobalMBeanServer();
      ObjectName objName = new ObjectName("java.lang:type=Runtime");
      
      String runtimeName = (String) server.getAttribute(objName, "Name");
      
      if (runtimeName == null) {
        return 0;
      }
      
      int p = runtimeName.indexOf('@');
      
      if (p > 0) {
        int pid = Integer.parseInt(runtimeName.substring(0, p));

        return pid;
      }
      
      return 0;
    } catch (Exception e) {
      log().log(Level.FINE, e.toString(), e);
      
      return 0;
    }

  }

  public void waitForExit()
  {
    while (_lifecycle.isActive()) {
      try {
        synchronized (this) {
          wait();
        }
      } catch (Exception e) {
      }
    }
  }

  public void handleAlarm(Alarm alarm)
  {
    try {
      if (! _args.getResinConf().canRead()) {
        log().severe(L().l("{0} exiting because '{1}' is no longer valid",
                           this, _args.getResinConf()));

        System.exit(1);
      }
    } finally {
      alarm.queue(60000);
    }
  }

  /**
   * The launching program for the watchdog manager, generally called
   * from ResinBoot.
   */
  public static void main(String []argv)
    throws Exception
  {
    boolean isValid = false;

    try {
      DynamicClassLoader.setJarCacheEnabled(false);

      JniCauchoSystem.create().initJniBackground();

      WatchdogManager manager = new WatchdogManager(argv);
      manager.startServer(null, argv);

      isValid = manager.isActive() && manager.isValid();

      if (isValid) {
        manager.waitForExit();
      }
    } catch (Exception e) {
      log().log(Level.WARNING, e.toString(), e);
      System.out.println(e);
      e.printStackTrace();
    } finally {
      System.exit(1);
    }
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
