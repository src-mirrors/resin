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
import com.caucho.xmpp.im.XmppRosterQueryMarshal;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import javax.servlet.*;
import javax.xml.namespace.QName;
import javax.xml.stream.*;

/**
 * Protocol handler from the TCP/XMPP stream forwarding to the broker
 */
public class XmppReader
{
  private static final Logger log
    = Logger.getLogger(XmppReader.class.getName());

  private BamStream _callback;

  private ReadStream _is;
  private XMLStreamReader _in;

  private XmppMarshalFactory _marshalFactory;

  private boolean _isFinest;

  XmppReader(ReadStream is,
	     XMLStreamReader in,
	     BamStream callback)
  {
    _is = is;
    _in = in;
    _callback = callback;

    _isFinest = log.isLoggable(Level.FINEST);
  }

  void setCallback(BamStream callback)
  {
    _callback = callback;
  }

  /**
   * Processes a message
   */
  boolean handleMessage()
    throws IOException, XMLStreamException
  {
    String type = _in.getAttributeValue(null, "type");
    String id = _in.getAttributeValue(null, "id");
    String from = _in.getAttributeValue(null, "from");
    String to = _in.getAttributeValue(null, "to");

    if (type == null)
      type = "chat";

    int tag;

    ArrayList<Text> subjectList = new ArrayList<Text>();
    ArrayList<Text> bodyList = new ArrayList<Text>();
    String thread = null;
    
    while ((tag = _in.next()) > 0
	   && ! (tag == XMLStreamReader.END_ELEMENT
		 && "message".equals(_in.getLocalName()))) {
      if (_isFinest)
	debug(_in);
      
      if (tag != XMLStreamReader.START_ELEMENT)
	continue;

      if ("body".equals(_in.getLocalName())
	  && "jabber:client".equals(_in.getNamespaceURI())) {
	String lang = null;

	if (_in.getAttributeCount() > 0
	    && "lang".equals(_in.getAttributeLocalName(0))) {
	  lang = _in.getAttributeValue(0);
	}
	
	tag = _in.next();
	if (_isFinest)
	  debug(_in);

	String body = _in.getText();

	bodyList.add(new Text(body, lang));

	expectEnd("body");
      }
      else if ("subject".equals(_in.getLocalName())
	       && "jabber:client".equals(_in.getNamespaceURI())) {
	String lang = null;
	
	if (_in.getAttributeCount() > 0
	    && "lang".equals(_in.getAttributeLocalName(0)))
	  lang = _in.getAttributeValue(0);
	
	tag = _in.next();
	if (_isFinest)
	  debug(_in);

	String text = _in.getText();

	subjectList.add(new Text(text, lang));

	expectEnd("subject");
      }
      else if ("thread".equals(_in.getLocalName())
	       && "jabber:client".equals(_in.getNamespaceURI())) {
	tag = _in.next();
	if (_isFinest)
	  debug(_in);

	thread = _in.getText();

	expectEnd("thread");
      }
    }

    expectEnd("message", tag);

    Text []subjectArray = null;

    if (subjectList.size() > 0) {
      subjectArray = new Text[subjectList.size()];
      subjectList.toArray(subjectArray);
    }

    Text []bodyArray = null;

    if (bodyList.size() > 0) {
      bodyArray = new Text[bodyList.size()];
      bodyList.toArray(bodyArray);
    }

    Serializable []extra = null;

    ImMessage message = new ImMessage(to, from, type,
				      subjectArray, bodyArray, thread,
				      extra);

    if (_callback != null)
      _callback.sendMessage(to, from, message);

    return true;
  }

  /**
   * Processes a query
   */
  boolean handleIq()
    throws IOException, XMLStreamException
  {
    String type = _in.getAttributeValue(null, "type");
    String id = _in.getAttributeValue(null, "id");
    String from = _in.getAttributeValue(null, "from");
    String to = _in.getAttributeValue(null, "to");

    int tag = _in.nextTag();

    if (_isFinest)
      debug(_in);

    String localName = _in.getLocalName();
    String uri = _in.getNamespaceURI();
    
    QName name = _in.getName();

    Serializable query = null;

    XmppMarshal marshal = _marshalFactory.getUnserialize(name);

    if (marshal != null)
      query = marshal.fromXml(_in);
    else
      query = readAsXmlString(_in);

    BamError error = null;

    /*
    if (to == null)
      to = _uid;
    */
      
    skipToEnd("iq");

    if ("get".equals(type)) {
      long bamId = Long.parseLong(id);

      if (_callback != null)
	_callback.sendQueryGet(bamId, to, from, query);
    }
    else if ("set".equals(type)) {
      long bamId = Long.parseLong(id);

      if (_callback != null)
	_callback.sendQuerySet(bamId, to, from, query);
    }
    else if ("result".equals(type)) {
      long bamId = Long.parseLong(id);

      if (_callback != null)
	_callback.sendQueryResult(bamId, to, from, query);
    }
    else if ("error".equals(type)) {
      long bamId = Long.parseLong(id);

      if (_callback != null)
	_callback.sendQueryError(bamId, to, from, query, error);
    }
    else {
      if (log.isLoggable(Level.FINE)) {
	log.fine(this + " <" + _in.getLocalName() + " xmlns="
		 + _in.getNamespaceURI() + "> unknown type");
      }
    }

    return true;
  }

  boolean handlePresence()
    throws IOException, XMLStreamException
  {
    String type = _in.getAttributeValue(null, "type");
    String id = _in.getAttributeValue(null, "id");
    String from = _in.getAttributeValue(null, "from");
    String to = _in.getAttributeValue(null, "to");

    if (type == null)
      type = "";

    int tag;

    String show = null;
    Text status = null;
    int priority = 0;
    ArrayList<Serializable> extraList = new ArrayList<Serializable>();
    BamError error = null;
    
    while ((tag = _in.nextTag()) > 0
	   && ! ("presence".equals(_in.getLocalName())
		 && tag == XMLStreamReader.END_ELEMENT)) {
      if (_isFinest)
	debug(_in);
      
      if (tag != XMLStreamReader.START_ELEMENT)
	continue;

      if ("status".equals(_in.getLocalName())) {
	tag = _in.next();
    
	if (_isFinest)
	  debug(_in);
	
	status = new Text(_in.getText());

	skipToEnd("status");
      }
      else if ("show".equals(_in.getLocalName())) {
	tag = _in.next();
    
	if (_isFinest)
	  debug(_in);
	
	show = _in.getText();

	skipToEnd("show");
      }
      else if ("priority".equals(_in.getLocalName())) {
	tag = _in.next();
    
	if (_isFinest)
	  debug(_in);
	
	priority = Integer.parseInt(_in.getText());

	skipToEnd("show");
      }
      else {
	
      }
    }
    
    if (_isFinest)
      debug(_in);

    expectEnd("presence", tag);

    /*
    from = _jid;

    if (to == null)
      to = _uid;
    */

    ImPresence presence = new ImPresence(to, from,
					 show, status, priority,
					 extraList);

    if (_callback != null) {
      if ("".equals(type))
	_callback.sendPresence(to, from, presence);
      else if ("probe".equals(type))
	_callback.sendPresenceProbe(to, from, presence);
      else if ("unavailable".equals(type))
	_callback.sendPresenceUnavailable(to, from, presence);
      else if ("subscribe".equals(type))
	_callback.sendPresenceSubscribe(to, from, presence);
      else if ("subscribed".equals(type))
	_callback.sendPresenceSubscribed(to, from, presence);
      else if ("unsubscribe".equals(type))
	_callback.sendPresenceUnsubscribe(to, from, presence);
      else if ("unsubscribed".equals(type))
	_callback.sendPresenceUnsubscribed(to, from, presence);
      else if ("error".equals(type))
	_callback.sendPresenceError(to, from, presence, error);
      else
	log.warning(this + " " + type + " is an unknown presence type");
    }

    return true;
  }

  private void skipToEnd(String tagName)
    throws IOException, XMLStreamException
  {
    XMLStreamReader in = _in;
      
    if (in == null)
      return;

    int tag;
    while ((tag = in.next()) > 0) {
      if (_isFinest)
	debug(in);
	
      if (tag == XMLStreamReader.START_ELEMENT) {
      }
      else if (tag == XMLStreamReader.END_ELEMENT) {
	if (tagName.equals(in.getLocalName()))
	  return;
      }
    }
  }

  private void expectEnd(String tagName)
    throws IOException, XMLStreamException
  {
    expectEnd(tagName, _in.nextTag());
  }

  private void expectEnd(String tagName, int tag)
    throws IOException, XMLStreamException
  {
    if (tag != XMLStreamReader.END_ELEMENT)
      throw new IllegalStateException("expected </" + tagName + "> at <" + _in.getLocalName() + ">");

    else if (! tagName.equals(_in.getLocalName()))
      throw new IllegalStateException("expected </" + tagName + "> at </" + _in.getLocalName() + ">");
  }

  private String readAsXmlString(XMLStreamReader in)
    throws IOException, XMLStreamException
  {
    StringBuilder sb = new StringBuilder();
    int depth = 0;

    while (true) {
      if (XMLStreamReader.START_ELEMENT == in.getEventType()) {
	depth++;

	String prefix = in.getPrefix();
	
	sb.append("<");

	if (! "".equals(prefix)) {
	  sb.append(prefix);
	  sb.append(":");
	}
	
	sb.append(in.getLocalName());

	if (in.getNamespaceURI() != null) {
	  if ("".equals(prefix))
	    sb.append(" xmlns");
	  else
	    sb.append(" xmlns:").append(prefix);
	    
	  sb.append("=\"");
	  sb.append(in.getNamespaceURI()).append("\"");
	}

	for (int i = 0; i < in.getAttributeCount(); i++) {
	  sb.append(" ");
	  sb.append(in.getAttributeLocalName(i));
	  sb.append("=\"");
	  sb.append(in.getAttributeValue(i));
	  sb.append("\"");
	}
	sb.append(">");

	log.finest(this + " " + sb);
      }
      else if (XMLStreamReader.END_ELEMENT == in.getEventType()) {
	depth--;

	sb.append("</");

	String prefix = in.getPrefix();
	if (! "".equals(prefix))
	  sb.append(prefix).append(":");
	
	sb.append(in.getLocalName());
	sb.append(">");

	if (depth == 0)
	  return sb.toString();
      }
      else if (XMLStreamReader.CHARACTERS == in.getEventType()) {
	sb.append(in.getText());
      }
      else {
	log.finer(this + " tag=" + in.getEventType());

	return sb.toString();
      }

      if (in.next() < 0) {
	log.finer(this + " unexpected end of file");
	
	return sb.toString();
      }
    }
  }

  private void debug(XMLStreamReader in)
    throws IOException, XMLStreamException
  {
    if (XMLStreamReader.START_ELEMENT == in.getEventType()) {
      StringBuilder sb = new StringBuilder();
      sb.append("<").append(in.getLocalName());

      if (in.getNamespaceURI() != null)
	sb.append("{").append(in.getNamespaceURI()).append("}");

      for (int i = 0; i < in.getAttributeCount(); i++) {
	sb.append(" ");
	sb.append(in.getAttributeLocalName(i));
	sb.append("='");
	sb.append(in.getAttributeValue(i));
	sb.append("'");
      }
      sb.append(">");

      log.finest(this + " " + sb);
    }
    else if (XMLStreamReader.END_ELEMENT == in.getEventType()) {
      log.finest(this + " </" + in.getLocalName() + ">");
    }
    else if (XMLStreamReader.CHARACTERS == in.getEventType()) {
      String text = in.getText().trim();

      if (! "".equals(text))
	log.finest(this + " text='" + text + "'");
    }
    else
      log.finest(this + " tag=" + in.getEventType());
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
