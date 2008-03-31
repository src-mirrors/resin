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

package com.caucho.hemp.service;

import java.io.Serializable;
import java.util.*;
import javax.webbeans.*;

/**
 * Configuration for a service
 */
public interface HempService {
  public Serializable onQuery(String fromJit, String toJid,
			      Serializable query);
  
  public void onMessage(String fromJid, String toJid, Serializable value);

  /**
   * General presence, for clients announcing availability
   */
  public void onPresence(String fromJit,
			 String toJid,
			 Serializable value);

  /**
   * General presence, for clients announcing unavailability
   */
  public void onPresenceUnavailable(String fromJit,
				    String toJid,
				    Serializable value);

  /**
   * Presence probe from the server to a client
   */
  public void onPresenceProbe(String fromJit,
			      String toJid,
			      Serializable value);

  /**
   * A subscription request from a client
   */
  public void onPresenceSubscribe(String fromJit,
				  String toJid,
				  Serializable value);

  /**
   * A subscription response to a client
   */
  public void onPresenceSubscribed(String fromJit,
				   String toJid,
				   Serializable value);

  /**
   * An unsubscription request from a client
   */
  public void onPresenceUnsubscribe(String fromJit,
				    String toJid,
				    Serializable value);

  /**
   * A unsubscription response to a client
   */
  public void onPresenceUnsubscribed(String fromJit,
				     String toJid,
				     Serializable value);
}
