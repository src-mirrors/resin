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

package com.caucho.cloud.bam;

import com.caucho.bam.broker.Broker;
import com.caucho.bam.broker.ManagedBroker;
import com.caucho.env.service.AbstractResinService;
import com.caucho.env.service.ResinSystem;
import com.caucho.hemp.broker.DomainManager;
import com.caucho.hemp.broker.HempBroker;
import com.caucho.hemp.broker.HempBrokerManager;
import com.caucho.hemp.servlet.ServerAuthManager;
import com.caucho.util.L10N;


/**
 * The BAM service registered in the Resin network.
 */
public class BamService extends AbstractResinService
{
  public static final int START_PRIORITY = START_PRIORITY_NETWORK_CLUSTER;
  
  private String _jid;
  
  private final HempBrokerManager _brokerManager;
  private final HempBroker _broker;
  
  private ServerAuthManager _linkManager;
  
  public BamService(String jid)
  {
    _jid = jid;
    
    _brokerManager = new HempBrokerManager();

    _broker = new HempBroker(_brokerManager, getJid());

    if (getJid() != null)
      _brokerManager.addBroker(getJid(), _broker);
    
    _brokerManager.addBroker("resin.caucho", _broker);
  }
  
  public static BamService createAndAddService(String jid)
  {
    ResinSystem system = preCreate(BamService.class);
      
    BamService service = new BamService(jid);
    system.addService(service);
    
    return service;
  }
  
  public static BamService getCurrent()
  {
    return ResinSystem.getCurrentService(BamService.class);
  }
  
  public static ManagedBroker getCurrentBroker()
  {
    return getCurrent().getBroker();
  }
  
  public String getJid()
  {
    return _jid;
  }
  
  public ManagedBroker getBroker()
  {
    return _broker;
  }
  
  public void setDomainManager(DomainManager manager)
  {
    _broker.setDomainManager(manager);
  }
  
  public void setLinkManager(ServerAuthManager linkManager)
  {
    _linkManager = linkManager;
  }
  
  public ServerAuthManager getLinkManager()
  {
    return _linkManager;
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _jid + "]";
  }
}
