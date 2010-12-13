/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

package com.caucho.hmtp;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;

import com.caucho.bam.ActorError;
import com.caucho.bam.ProtocolException;
import com.caucho.bam.broker.AbstractBroker;
import com.caucho.bam.stream.ActorStream;
import com.caucho.websocket.WebSocketContext;

/**
 * HmtpWriteStream writes HMTP packets to an OutputStream.
 */
public class HmtpWebSocketContextWriter extends AbstractBroker
{
  private String _jid;
  
  private WebSocketContext _ws;
  private HmtpWriter _hOut;

  public HmtpWebSocketContextWriter(WebSocketContext ws)
  {
    _ws = ws;
    _hOut = new HmtpWriter();
  }
  
  /**
   * The jid of the stream
   */
  @Override
  public String getJid()
  {
    return _jid;
  }
  
  /**
   * The jid of the stream
   */
  public void setJid(String jid)
  {
    _jid = jid;
  }

  //
  // message
  //

  /**
   * Sends a message to a given jid
   * 
   * @param to the jid of the target actor
   * @param from the jid of the source actor
   * @param payload the message payload
   */
  @Override
  public void message(String to, 
                      String from, 
                      Serializable payload)
  {
    try {
      OutputStream os = _ws.startBinaryMessage();
      
      _hOut.message(os, to, from, payload);
      
      os.close();
    } catch (IOException e) {
      throw new ProtocolException(e);
    }
  }

  /**
   * Sends a message error to a given jid
   * 
   * @param to the jid of the target actor
   * @param from the jid of the source actor
   * @param payload the message payload
   * @param error the message error
   */
  @Override
  public void messageError(String to, 
                           String from, 
                           Serializable payload,
                           ActorError error)
  {
    try {
      OutputStream os = _ws.startBinaryMessage();
      
      _hOut.messageError(os, to, from, payload, error);
      
      os.close();
    } catch (IOException e) {
      throw new ProtocolException(e);
    }
  }

  /**
   * Sends a queryGet to a given jid
   * 
   * @param id the query id
   * @param to the jid of the target actor
   * @param from the jid of the source actor
   * @param payload the message payload
   */
  @Override
  public void query(long id,
                    String to, 
                    String from, 
                    Serializable payload)
  {
    try {
      OutputStream os = _ws.startBinaryMessage();
      
      _hOut.query(os, id, to, from, payload);
      
      os.close();
    } catch (IOException e) {
      throw new ProtocolException(e);
    }
  }

  /**
   * Sends a queryResult to a given jid
   * 
   * @param id the query id
   * @param to the jid of the target actor
   * @param from the jid of the source actor
   * @param payload the message payload
   */
  @Override
  public void queryResult(long id,
                          String to, 
                          String from, 
                          Serializable payload)
  {
    try {
      OutputStream os = _ws.startBinaryMessage();
      
      _hOut.queryResult(os, id, to, from, payload);
      
      os.close();
    } catch (IOException e) {
      throw new ProtocolException(e);
    }
  }

  /**
   * Sends a query error to a given jid
   * 
   * @param id the query identifier
   * @param to the jid of the target actor
   * @param from the jid of the source actor
   * @param payload the message payload
   * @param error the message error
   */
  @Override
  public void queryError(long id,
                         String to, 
                         String from, 
                         Serializable payload,
                         ActorError error)
  {
    try {
      OutputStream os = _ws.startBinaryMessage();
      
      _hOut.queryError(os, id, to, from, payload, error);
      
      os.close();
    } catch (IOException e) {
      throw new ProtocolException(e);
    }
  }

  public void close()
  {
  }

  @Override
  public boolean isClosed()
  {
    return false;
  }
}
