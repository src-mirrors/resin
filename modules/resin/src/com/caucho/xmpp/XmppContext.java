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
public class XmppContext
{
  private static final Logger log
    = Logger.getLogger(XmppBrokerStream.class.getName());

  // XXX: timeouts
  private long _requestId;
  private HashMap<Long,String> _idMap = new HashMap<Long,String>();
  
  private XmppMarshalFactory _marshalFactory;

  XmppContext()
  {
    this(new XmppMarshalFactory());
  }

  XmppContext(XmppMarshalFactory marshalFactory)
  {
    _marshalFactory = marshalFactory;
  }
  
  public XmppMarshalFactory getMarshalFactory()
  {
    return _marshalFactory;
  }

  public long addId(String id)
  {
    long bamId;
    
    synchronized (_idMap) {
      bamId = _requestId++;
      
      _idMap.put(bamId, id);
    }

    return bamId;
  }

  public String findId(long bamId)
  {
    synchronized (_idMap) {
      return _idMap.remove(bamId);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
