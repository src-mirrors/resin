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

import com.caucho.bam.*;
import com.caucho.bam.im.*;
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
public class XmppBindCallback extends AbstractBamStream
{
  private XmppBrokerStream _xmppBroker;

  XmppBindCallback(XmppBrokerStream broker)
  {
    _xmppBroker = broker;
  }

  @Override
  public boolean querySet(long id,
			      String to, String from,
			      Serializable value)
  {
    if (value instanceof ImBindQuery) {
      ImBindQuery bind = (ImBindQuery) value;

      String jid = _xmppBroker.bind(bind.getResource(), bind.getJid());

      ImBindQuery result = new ImBindQuery(null, jid);

      _xmppBroker.getAgentStream().queryResult(id, from, to, result);

      return true;
    }
    
    return false;
  }
}
