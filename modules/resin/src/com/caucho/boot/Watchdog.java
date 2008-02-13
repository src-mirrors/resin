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

import com.caucho.config.program.ConfigProgram;
import com.caucho.config.ConfigException;
import com.caucho.lifecycle.Lifecycle;
import com.caucho.management.server.AbstractManagedObject;
import com.caucho.management.server.WatchdogMXBean;
import com.caucho.server.port.Port;
import com.caucho.util.*;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;

import java.lang.reflect.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Logger;

/**
 * Thread responsible for watching a backend server.
 */
public class Watchdog
{
  private static final L10N L = new L10N(Watchdog.class);
  private static final Logger log
    = Logger.getLogger(Watchdog.class.getName());
  
  private final String _id;

  private WatchdogConfig _config;
  
  private boolean _isSingle;
  private WatchdogTask _task;

  // statistics
  private Date _initialStartTime;
  private Date _lastStartTime;
  private int _startCount;
  private WatchdogAdmin _admin;

  Watchdog(String id, WatchdogArgs args)
  {
    _id = id;
    _config = new WatchdogConfig(args);

    _admin = new WatchdogAdmin();
  }

  Watchdog(WatchdogConfig config)
  {
    _id = config.getId();
    _config = config;
    
    _admin = new WatchdogAdmin();
  }

  /**
   * Returns the server id of the watchdog.
   */
  public String getId()
  {
    return _id;
  }

  /**
   * Returns the watchdog arguments.
   */
  WatchdogArgs getArgs()
  {
    return _config.getArgs();
  }
  
  /**
   * Returns the java startup args
   */
  String []getArgv()
  {
    return _config.getArgv();
  }

  /**
   * Returns the config state of the watchdog
   */
  public WatchdogConfig getConfig()
  {
    return _config;
  }

  /**
   * Sets the config state of the watchdog
   */
  public void setConfig(WatchdogConfig config)
  {
    _config = config;
  }

  /**
   * Returns the JAVA_HOME for the Resin instance
   */
  public Path getJavaHome()
  {
    return _config.getJavaHome();
  }
  
  /**
   * Returns the location of the java executable
   */
  public String getJavaExe()
  {
    return _config.getJavaExe();
  }

  /**
   * Returns the JVM arguments for the instance
   */
  public ArrayList<String> getJvmArgs()
  {
    return _config.getJvmArgs();
  }

  /**
   * Returns the setuid user name.
   */
  public String getUserName()
  {
    return _config.getUserName();
  }

  /**
   * Returns the setgid group name.
   */
  public String getGroupName()
  {
    return _config.getGroupName();
  }

  /**
   * Returns true for a standalone start.
   */
  public boolean isSingle()
  {
    return _isSingle;
  }

  /**
   * Returns the jvm-foo-log.log file path
   */
  public Path getLogPath()
  {
    return _config.getLogPath();
  }

  /**
   * Returns the maximum time to wait for a shutdown
   */
  public long getShutdownWaitTime()
  {
    return _config.getShutdownWaitTime();
  }

  /**
   * Returns the watchdog-port for this watchdog instance
   */
  public int getWatchdogPort()
  {
    return _config.getWatchdogPort();
  }

  Iterable<Port> getPorts()
  {
    return _config.getPorts();
  }

  Path getPwd()
  {
    return _config.getPwd();
  }

  Path getResinHome()
  {
    return _config.getResinHome();
  }

  Path getResinRoot()
  {
    return _config.getResinRoot();
  }
  
  Path getResinConf()
  {
    return _config.getResinConf();
  }

  boolean hasXmx()
  {
    return _config.hasXmx();
  }

  boolean hasXss()
  {
    return _config.hasXss();
  }

  boolean is64bit()
  {
    return _config.is64bit();
  }

  public String getState()
  {
    WatchdogTask task = _task;
    
    if (task == null)
      return "inactive";
    else
      return task.getState();
  }

  boolean isVerbose()
  {
    return _config.isVerbose();
  }

  public int startSingle()
  {
    if (_task != null)
      return -1;
    
    _isSingle = true;
    _task = new WatchdogTask(this);
    
    _task.start();

    return 1;
  }

  /**
   * Starts the watchdog instance.
   */
  public void start()
  {
    WatchdogTask task = null;
    
    synchronized (this) {
      if (_task != null)
	throw new IllegalStateException(L.l("Can't start new task because of old task '{0}'", _task));

      task = new WatchdogTask(this);
      _task = task;
    }

    task.start();
  }

  /**
   * Stops the watchdog instance
   */
  public void stop()
  {
    WatchdogTask task = _task;
    
    if (task != null)
      task.stop();
  }

  /**
   * Kills the watchdog instance
   */
  public void kill()
  {
    WatchdogTask task = _task;
    _task = null;
    
    if (task != null)
      task.kill();
  }

  void notifyTaskStarted()
  {
    _startCount++;
    _lastStartTime = new Date(Alarm.getExactTime());
    
    if (_initialStartTime == null)
      _initialStartTime = _lastStartTime; 
  }

  void completeTask(WatchdogTask task)
  {
    synchronized (this) {
      if (_task == task)
	_task = null;
    }
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + getId() + "]";
  }

  class WatchdogAdmin extends AbstractManagedObject implements WatchdogMXBean
  {
    WatchdogAdmin()
    {
      registerSelf();
    }

    public String getId()
    {
      return Watchdog.this.getId();
    }
    
    public String getName()
    {
      return getId();
    }

    @Override
    public String getType()
    {
      return "Watchdog";
    }

    public String getResinHome()
    {
      return Watchdog.this.getResinHome().getNativePath();
    }

    public String getResinRoot()
    {
      return Watchdog.this.getResinRoot().getNativePath();
    }

    public String getResinConf()
    {
      return Watchdog.this.getResinConf().getNativePath();
    }

    public String getUserName()
    {
      String userName = Watchdog.this.getUserName();

      if (userName != null)
	return userName;
      else
	return System.getProperty("user.name");
    }

    public String getState()
    {
      WatchdogTask task = _task;
    
      if (task == null)
	return "inactive";
      else
	return task.getState();
    }

    //
    // statistics
    //

    public Date getInitialStartTime()
    {
      return _initialStartTime;
    }

    public Date getStartTime()
    {
      return _lastStartTime;
    }

    public int getStartCount()
    {
      return _startCount;
    }

    //
    // operations
    //

    public void start()
    {
      Watchdog.this.start();
    }

    public void stop()
    {
      Watchdog.this.stop();
    }

    public void kill()
    {
      Watchdog.this.kill();
    }
  }
}
