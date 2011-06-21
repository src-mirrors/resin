/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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
 * @author Alex Rojkov
 */

package com.caucho.server.admin;

import com.caucho.bam.RemoteConnectionFailedException;
import com.caucho.bam.ServiceUnavailableException;
import com.caucho.bam.actor.ActorSender;
import com.caucho.cloud.security.SecurityService;
import com.caucho.hmtp.HmtpClient;
import com.caucho.hmtp.NonceQuery;
import com.caucho.server.cluster.Server;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;

import java.io.IOException;
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deploy Client API
 */
public class ManagerClient
{
  private static final Logger log
    = Logger.getLogger(ManagerClient.class.getName());
  private static final L10N L = new L10N(ManagerClient.class);

  private static final long MANAGER_TIMEOUT = 600 * 1000L;

  private ActorSender _bamClient;
  private String _managerAddress;

  private String _url;

  public ManagerClient()
  {
    Server server = Server.getCurrent();

    if (server == null)
      throw new IllegalStateException(L.l("ManagerClient was not called in a Resin context. For external clients, use the ManagerClient constructor with host,port arguments."));

    _bamClient = server.createAdminClient(getClass().getSimpleName());

    _managerAddress = "manager@resin.caucho";
  }

  public ManagerClient(String serverId)
  {
    Server server = Server.getCurrent();

    if (server == null)
      throw new IllegalStateException(L.l("ManagerClient was not called in a Resin context. For external clients, use the ManagerClient constructor with host,port arguments."));

    _bamClient = server.createAdminClient(getClass().getSimpleName());

    _managerAddress = "manager@" + serverId + ".resin.caucho";
  }

  public ManagerClient(String host, int port,
                       String userName, String password)
  {
    String url = "http://" + host + ":" + port + "/hmtp";
    
    _url = url;
    
    HmtpClient client = new HmtpClient(url);
    try {
      client.setVirtualHost("admin.resin");

      client.connect(userName, password);

      _bamClient = client;
    
      _managerAddress = "manager@resin.caucho";
    } catch (RemoteConnectionFailedException e) {
      throw new RemoteConnectionFailedException(L.l("Connection to '{0}' failed for remote admin. Check the server and make sure <resin:RemoteAdminService> is enabled in the resin.xml.\n  {1}",
                                                    url, e.getMessage()),
                                                e);
    }
  }
  
  public String getUrl()
  {
    return _url;
  }

  public String addUser(String user, char []password, String []roles)
  {
    AddUserQuery query = new AddUserQuery(user, password, roles);

    return (String) query(query);
  }

  public String removeUser(String user)
  {
    RemoveUserQuery query = new RemoveUserQuery(user);

    return (String) query(query);
  }

  public String listUsers()
  {
    ListUsersQuery query = new ListUsersQuery();

    return (String) query(query);
  }

  public String doThreadDump()
  {
    ThreadDumpQuery query = new ThreadDumpQuery();

    return (String) query(query);
  }

  public String doHeapDump(boolean raw)
  {
    HeapDumpQuery query = new HeapDumpQuery(raw);

    return (String) query(query);
  }

  public String setLogLevel(String[] loggers, Level logLevel, long period)
  {
    LogLevelQuery query = new LogLevelQuery(loggers, logLevel, period);

    return (String) query(query);
  }

  public String listJmx(String pattern,
                        boolean isPrintAttributes,
                        boolean isPrintValues,
                        boolean isPrintOperations,
                        boolean isAll,
                        boolean isPlatform)
  {
    JmxListQuery query = new JmxListQuery(pattern,
                                          isPrintAttributes,
                                          isPrintValues,
                                          isPrintOperations,
                                          isAll,
                                          isPlatform);

    return (String) query(query);
  }

  public String setJmx(String pattern, String attribute, String value)
  {
    JmxSetQuery query = new JmxSetQuery(pattern, attribute, value);

    return (String) query(query);
  }

  public String callJmx(String pattern,
                        String operation,
                        int opIndex,
                        String []trailingArgs)
  {
    JmxCallQuery query = new JmxCallQuery(pattern,
                                          operation,
                                          opIndex,
                                          trailingArgs);

    return (String) query(query);
  }

  public String profile(long activeTime, long period, int depth) {
    ProfileQuery query = new ProfileQuery(activeTime, period, depth);

    return (String)query(query);
  }

  protected Serializable query(Serializable query)
  {
    try {
      return _bamClient.query(_managerAddress, query);
    } catch (ServiceUnavailableException e) {
      throw new ServiceUnavailableException("Manager service is not available, possibly because the resin.xml is missing a <resin:ManagerService> tag\n  " + e.getMessage(),
                                            e);
    }
  }

  public void close()
  {
    _bamClient.close();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _bamClient + "]";
  }
}

