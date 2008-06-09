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

package com.caucho.xmpp;

import com.caucho.xmpp.im.Text;
import com.caucho.xmpp.im.ImPresence;
import com.caucho.xmpp.im.ImMessage;
import com.caucho.bam.*;
import com.caucho.vfs.*;
import com.caucho.xml.stream.*;
import java.io.Serializable;
import java.util.*;
import java.util.logging.*;
import javax.xml.stream.*;

/**
 * xmpp client to broker
 */
class XmppClientBrokerStream extends AbstractBamStream
{
  private static final Logger log
    = Logger.getLogger(XmppClientBrokerStream.class.getName());

  private WriteStream _os;
  private XmppWriter _out;

  XmppClientBrokerStream(XmppClient client, XmppWriter out)
  {
    _out = out;
  }

  /**
   * Sends a message to the stream
   */
  @Override
  public void message(String to, String from, Serializable value)
  {
    _out.sendMessage(to, from, value);
  }

  /**
   * Sends a query get message to the stream
   */
  @Override
  public boolean queryGet(long id, String to, String from,
			  Serializable value)
  {
    _out.sendQuery(String.valueOf(id), to, from, value, "get");

    return true;
  }

  /**
   * Sends a query set message to the stream
   */
  @Override
  public boolean querySet(long id, String to, String from,
			  Serializable value)
  {
    _out.sendQuery(String.valueOf(id), to, from, value, "get");
    
    return true;
  }

  /**
   * Sends a query result message to the stream
   */
  @Override
  public void queryResult(long id, String to, String from,
			  Serializable value)
  {
    _out.sendQuery(String.valueOf(id), to, from, value, "result");
  }

  /**
   * Sends a presence message to the stream
   */
  @Override
  public void presence(String to, String from, Serializable value)
  {
    _out.sendPresence(to, from, value, null);
  }

  /**
   * Sends a presence probe to the stream
   */
  @Override
  public void presenceProbe(String to, String from, Serializable value)
  {
    _out.sendPresence(to, from, value, "probe");
  }

  /**
   * Sends a presence unavailable to the stream
   */
  @Override
  public void presenceUnavailable(String to,
				      String from,
				      Serializable value)
  {
    _out.sendPresence(to, from, value, "unavailable");
  }

  /**
   * Sends a presence subscribe to the stream
   */
  @Override
  public void presenceSubscribe(String to,
				    String from,
				    Serializable value)
  {
    _out.sendPresence(to, from, value, "subscribe");
  }

  /**
   * Sends a presence subscribed to the stream
   */
  @Override
  public void presenceSubscribed(String to,
				    String from,
				    Serializable value)
  {
    _out.sendPresence(to, from, value, "subscribed");
  }

  /**
   * Sends a presence unsubscribe to the stream
   */
  @Override
  public void presenceUnsubscribe(String to,
				      String from,
				      Serializable value)
  {
    _out.sendPresence(to, from, value, "unsubscribe");
  }

  /**
   * Sends a presence unsubscribed to the stream
   */
  @Override
  public void presenceUnsubscribed(String to,
				      String from,
				      Serializable value)
  {
    _out.sendPresence(to, from, value, "unsubscribed");
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + "]";
  }
}
