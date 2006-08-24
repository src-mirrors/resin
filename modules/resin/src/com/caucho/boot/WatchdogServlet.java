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

package com.caucho.boot;

import java.io.*;
import java.util.logging.*;

import javax.servlet.*;

import com.caucho.hessian.server.HessianServlet;

/**
 * Process responsible for watching a backend server.
 */
public class WatchdogServlet extends HessianServlet implements WatchdogAPI {
  private static final Logger log
    = Logger.getLogger(WatchdogServlet.class.getName());

  private WatchdogManager _watchdog;
  
  public void init()
  {
    _watchdog = WatchdogManager.getWatchdog();
  }
  public boolean start(String serverId, String []argv)
  {
    log.info("Watchdog start: " + serverId);

    return _watchdog.startServer(serverId);
  }
  
  public boolean restart(String serverId, String []argv)
  {
    log.info("Watchdog restart: " + serverId);
    
    return true;
  }
  
  public boolean stop(String serverId)
  {
    log.info("Watchdog stop: " + serverId);
    
    return _watchdog.stopServer(serverId);
  }
  
  public boolean shutdown()
  {
    log.info("Watchdog shutdown");

    System.exit(0);

    return true;
  }
}
