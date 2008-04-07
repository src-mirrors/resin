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

import com.caucho.hmpp.HmppError;
import java.io.Serializable;

/**
 * low-level api for sending messages
 */
public interface HmppServer {
  //
  // message
  //
  
  /**
   * Sends a message
   */
  public void sendMessage(String to, String from, Serializable value);

  //
  // query
  //

  /**
   * Queries the service
   */
  public void queryGet(long id, String to, String from, Serializable query);

  /**
   * Queries the service
   */
  public void querySet(long id, String to, String from, Serializable query);

  /**
   * Sends a query response
   */
  public void queryResult(long id,
			  String to,
			  String from,
			  Serializable value);

  /**
   * Sends a query response
   */
  public void queryError(long id,
			 String to,
			 String from,
			 Serializable query,
			 HmppError error);
  

  //
  // presence
  //

  /**
   * Basic presence
   */
  public void presence(String to, String from, Serializable []data);

  /**
   * Presence callback on login
   */
  public void presenceProbe(String to, String from, Serializable []data);

  /**
   * Basic presence on logout
   */
  public void presenceUnavailable(String to, String from, Serializable []data);

  /**
   * Presence subscribe request
   */
  public void presenceSubscribe(String to, String from, Serializable []data);

  /**
   * Presence subscribed request
   */
  public void presenceSubscribed(String to, String from, Serializable []data);

  /**
   * Presence unsubscribe request
   */
  public void presenceUnsubscribe(String to, String from, Serializable []data);

  /**
   * Presence unsubscribed request
   */
  public void presenceUnsubscribed(String to, String from,
				   Serializable []data);

  /**
   * Presence error
   */
  public void presenceError(String to, String from,
			    Serializable []data,
			    HmppError error);
}
