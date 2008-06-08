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

import com.caucho.bam.BamStream;
import com.caucho.bam.BamError;
import com.caucho.bam.BamConnection;
import com.caucho.bam.im.*;
import com.caucho.bam.BamBroker;
import com.caucho.server.connection.*;
import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.xml.stream.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import javax.servlet.*;
import javax.xml.stream.*;

/**
 * Protocol handler from the TCP/XMPP stream forwarding to the broker
 */
public class XmppBrokerStream
  implements TcpDuplexHandler, BamStream
{
  private static final Logger log
    = Logger.getLogger(XmppBrokerStream.class.getName());

  private XmppRequest _request;
  private XmppProtocol _protocol;
  private XmppContext _xmppContext;
  
  private BamBroker _broker;
  private BamConnection _conn;
  private BamStream _toBroker;

  private BamStream _toClient;
  private BamStream _authHandler;

  private ReadStream _is;
  private WriteStream _os;
  
  private XmppStreamReader _in;
  private XmppStreamWriter _out;

  private XmppReader _reader;

  private String _jid;
  private long _requestId;

  private String _uid = "test@localhost";
  private boolean _isFinest;

  // XXX: needs timeout(?)
  private HashMap<Long,String> _idMap = new HashMap<Long,String>();

  XmppBrokerStream(XmppRequest request, BamBroker broker,
		   ReadStream is, XmppStreamReader in, WriteStream os)
  {
    _request = request;
    _protocol = request.getProtocol();
    _xmppContext = new XmppContext(_protocol.getMarshalFactory());
    _broker = broker;

    _in = in;
    _os = os;

    _uid = request.getUid();

    _out =  new XmppStreamWriterImpl(os, _protocol.getMarshalFactory());

    _toClient = new XmppAgentStream(this, _os);
    _authHandler = null;//new AuthBrokerStream(this, _callbackHandler);

    _reader = new XmppReader(_xmppContext,
			     is, _in, _toClient,
			     new XmppBindCallback(this));

    _reader.setUid(_uid);

    _isFinest = log.isLoggable(Level.FINEST);
  }

  public String getJid()
  {
    return _jid;
  }

  BamStream getAgentStream()
  {
    return _toClient;
  }

  XmppMarshalFactory getMarshalFactory()
  {
    return _protocol.getMarshalFactory();
  }

  XmppContext getXmppContext()
  {
    return _xmppContext;
  }
  
  public boolean serviceRead(ReadStream is,
			     TcpDuplexController controller)
    throws IOException
  {
    return _reader.readNext();
  }
  
  public boolean serviceWrite(WriteStream os,
			      TcpDuplexController controller)
    throws IOException
  {
    return false;
  }

  String login(String uid, Serializable credentials, String resource)
  {
    String password = (String) credentials;
    
    _uid = uid + "@localhost";
    
    _conn = _broker.getConnection(_uid, password);
    _conn.setStreamHandler(_toClient);

    _jid = _conn.getJid();
    
    _toBroker = _conn.getBrokerStream();

    return _jid;
  }

  String bind(String resource, String jid)
  {
    String password = null;
    
    _conn = _broker.getConnection(_uid, password, resource);
    _conn.setStreamHandler(_toClient);

    _jid = _conn.getJid();
    
    _toBroker = _conn.getBrokerStream();
    
    _reader.setJid(_jid);
    _reader.setHandler(_toBroker);
    
    return _jid;
  }

  String findId(long bamId)
  {
    synchronized (_idMap) {
      return _idMap.remove(bamId);
    }
  }

  void writeValue(Serializable value)
    throws IOException, XMLStreamException
  {
    if (value == null)
      return;
    
    XmppMarshalFactory marshalFactory = _protocol.getMarshalFactory();
    XmppMarshal marshal = marshalFactory.getSerialize(value.getClass().getName());

    if (marshal != null) {
      marshal.toXml(_out, value);
    }
  }
  
  /**
   * Handles a message
   */
  public void message(String to,
			  String from,
			  Serializable value)
  {
    _toBroker.message(to, _jid, value);
  }
  
  /**
   * Handles a message
   */
  public void messageError(String to,
			       String from,
			       Serializable value,
			       BamError error)
  {
    _toBroker.messageError(to, _jid, value, error);
  }
  
  /**
   * Handles a get query.
   *
   * The get handler must respond with either
   * a QueryResult or a QueryError 
   */
  public boolean queryGet(long id,
			      String to,
			      String from,
			      Serializable value)
  {
    _toBroker.queryGet(id, to, _jid, value);
    
    return true;
  }
  
  /**
   * Handles a set query.
   *
   * The set handler must respond with either
   * a QueryResult or a QueryError 
   */
  public boolean querySet(long id,
			      String to,
			      String from,
			      Serializable value)
  {
    _toBroker.querySet(id, to, _jid, value);
    
    return true;
  }
  
  /**
   * Handles a query result.
   *
   * The result id will match a pending get or set.
   */
  public void queryResult(long id,
			      String to,
			      String from,
			      Serializable value)
  {
    _toBroker.queryResult(id, to, _jid, value);
  }
  
  /**
   * Handles a query error.
   *
   * The result id will match a pending get or set.
   */
  public void queryError(long id,
			     String to,
			     String from,
			     Serializable value,
			     BamError error)
  {
    _toBroker.queryError(id, to, _jid, value, error);
  }
  
  /**
   * Handles a presence availability packet.
   *
   * If the handler deals with clients, the "from" value should be ignored
   * and replaced by the client's jid.
   */
  public void presence(String to,
			   String from,
			   Serializable data)

  {
    _toBroker.presence(to, _jid, data);
  }
  
  /**
   * Handles a presence unavailability packet.
   *
   * If the handler deals with clients, the "from" value should be ignored
   * and replaced by the client's jid.
   */
  public void presenceUnavailable(String to,
				      String from,
				      Serializable data)
  {
    _toBroker.presenceUnavailable(to, _jid, data);
  }
  
  /**
   * Handles a presence probe from another server
   */
  public void presenceProbe(String to,
			      String from,
			      Serializable data)
  {
    _toBroker.presenceProbe(to, _jid, data);
  }
  
  /**
   * Handles a presence subscribe request from a client
   */
  public void presenceSubscribe(String to,
				    String from,
				    Serializable data)
  {
    _toBroker.presenceSubscribe(to, _jid, data);
  }
  
  /**
   * Handles a presence subscribed result to a client
   */
  public void presenceSubscribed(String to,
				     String from,
				     Serializable data)
  {
    _toBroker.presenceSubscribed(to, _jid, data);
  }
  
  /**
   * Handles a presence unsubscribe request from a client
   */
  public void presenceUnsubscribe(String to,
				      String from,
				      Serializable data)
  {
    _toBroker.presenceUnsubscribe(to, _jid, data);
  }
  
  /**
   * Handles a presence unsubscribed result to a client
   */
  public void presenceUnsubscribed(String to,
				       String from,
				       Serializable data)
  {
    _toBroker.presenceUnsubscribed(to, _jid, data);
  }
  
  /**
   * Handles a presence unsubscribed result to a client
   */
  public void presenceError(String to,
			      String from,
			      Serializable data,
			      BamError error)
  {
    _toBroker.presenceError(to, _jid, data, error);
  }

  public void close()
  {
    XmppStreamReader in = _in;
    _in = null;

    if (in != null) {
      try { in.close(); } catch (Exception e) {}
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _conn + "]";
  }
}
