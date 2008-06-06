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

import com.caucho.server.port.*;
import com.caucho.vfs.*;

import com.caucho.webbeans.manager.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;
import javax.annotation.*;
import javax.jms.*;
import javax.xml.namespace.*;
import javax.webbeans.*;

/*
 * XMPP protocol server
 */
public class XmppMarshalFactory
{
  private static final Logger log
    = Logger.getLogger(XmppMarshalFactory.class.getName());
  
  private ClassLoader _loader;

  private HashMap<QName,XmppMarshal> _unserializeMap
    = new HashMap<QName,XmppMarshal>();

  private HashMap<String,XmppMarshal> _serializeMap
    = new HashMap<String,XmppMarshal>();

  private javax.jms.Connection _jmsConn;
  
  public XmppMarshalFactory()
  {
    _loader = Thread.currentThread().getContextClassLoader();

    init();
  }

  ClassLoader getClassLoader()
  {
    return _loader;
  }

  public void init()
  {
    String resource = "META-INF/caucho/com.caucho.xmpp.XmppMarshal";

    try {
      Enumeration<URL> iter = _loader.getResources(resource);
    
      while (iter.hasMoreElements()) {
	URL url = iter.nextElement();

	ReadStream is = null;
	try {
	  is = Vfs.lookup(url.toString()).openRead();
	  
	  loadMarshal(is);
	} catch (IOException e) {
	  log.log(Level.WARNING, e.toString(), e);
	} finally {
	  is.close();
	}
      }
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  private void loadMarshal(ReadStream is)
    throws IOException
  {
    String line;

    while ((line = is.readLine()) != null) {
      int p = line.indexOf('#');

      if (p > 0)
	line = line.substring(0, p);
      
      line = line.trim();

      if (line.length() == 0)
	continue;

      try {
	String marshalClassName = line;
	
	Class cl = Class.forName(marshalClassName, false, _loader);
	XmppMarshal marshal = (XmppMarshal) cl.newInstance();

	QName qName = new QName(marshal.getNamespaceURI(),
				marshal.getLocalName(), "");

	String className = marshal.getClassName();

	_serializeMap.put(className, marshal);
	_unserializeMap.put(qName, marshal);

	if (log.isLoggable(Level.FINEST))
	  log.finest(this + " marshal: " + marshal + " " + qName + " " + className);
      } catch (Exception e) {
	log.log(Level.WARNING, e.toString(), e);
      }
    }
  }

  XmppMarshal getUnserialize(QName name)
  {
    return _unserializeMap.get(name);
  }

  XmppMarshal getSerialize(String name)
  {
    return _serializeMap.get(name);
  }
}
