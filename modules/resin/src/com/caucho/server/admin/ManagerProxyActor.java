/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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
 * @author Alex Rojkov
 */

package com.caucho.server.admin;

import com.caucho.server.cluster.ServletService;
import com.caucho.util.L10N;

public class ManagerProxyActor
{
  private static final L10N L = new L10N(ManagerProxyActor.class);
  
  public ManagerProxyActor()
  {
  }
  
  public String hello()
  {
    return "hello, world";
  }
  
  public String enable()
  {
    ServletService server = ServletService.getCurrent();
    
    server.setEnabled(true);
    
    return L.l("Server '{0}' is enabled.", server.getServerId());
  }
  
  public String disable()
  {
    ServletService server = ServletService.getCurrent();
    
    server.setEnabled(false);
    
    return L.l("Server '{0}' is disabled.", server.getServerId());
  }
}
