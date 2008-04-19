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

package com.caucho.hmpp.spi;

import com.caucho.hmpp.*;

/**
 * Low-level callback to handle packet events.  Each method corresponds to
 * a packet class.
 */
public interface HmppResource
{
  /**
   * Returns the resource's preferred jid.
   */
  public String getJid();
  
  /**
   * Returns a subresource, e.g. if the resource is room@domain, then
   * it might return a resource for room@domain/nick
   */
  public HmppResource lookupResource(String jid);

  /**
   * Called when an instance logs in
   */
  public void onLogin(String jid);

  /**
   * Called when an instance logs out
   */
  public void onLogout(String jid);

  /**
   * Returns the resource's stream
   */
  public HmppStream getCallbackStream();
  
  /**
   * Returns a filter for inbound calls, i.e. from back from the
   * router.
   */
  public HmppStream getOutboundFilter(HmppStream stream);

  /**
   * Returns a filter for outbound calls, i.e. before going through the
   * router.
   */
  public HmppStream getInboundFilter(HmppStream stream);
}
