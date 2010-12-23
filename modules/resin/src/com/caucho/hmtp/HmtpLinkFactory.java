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
import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.bam.ActorException;
import com.caucho.bam.RemoteConnectionFailedException;
import com.caucho.bam.actor.AbstractActorSender;
import com.caucho.bam.actor.Actor;
import com.caucho.bam.actor.SimpleActorSender;
import com.caucho.bam.broker.Broker;
import com.caucho.bam.client.LinkConnection;
import com.caucho.bam.client.LinkConnectionFactory;
import com.caucho.bam.stream.ActorStream;
import com.caucho.cloud.security.SecurityService;
import com.caucho.remote.websocket.WebSocketClient;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.websocket.WebSocketListener;

/**
 * HMTP client protocol
 */
class HmtpLinkFactory implements LinkConnectionFactory {
  private static final L10N L = new L10N(HmtpLinkFactory.class);
  
  private static final Logger log
    = Logger.getLogger(HmtpLinkFactory.class.getName());
  
  private String _url;
  private String _jid;
  private String _virtualHost;

  private WebSocketClient _webSocketClient;
  private WebSocketListener _webSocketHandler;
  
  private String _user;
  private String _password;
  private Serializable _credentials;
  
  private ActorException _connException;
  
  private ClientAuthManager _authManager = new ClientAuthManager();

  public HmtpLinkFactory()
  {
  }
  
  public void setUrl(String url)
  {
    _url = url;
  }

  public void setVirtualHost(String host)
  {
    _virtualHost = host;
  }

  public void setEncryptPassword(boolean isEncrypt)
  {
  }

  public void connect()
  {
  }

  public void connect(String user, String password)
  {
    _user = user;
    _password = password;
  }

  public void connect(String user, Serializable credentials)
  {
    _user = user;
    _credentials = credentials;
  }

  public LinkConnection open(Broker broker)
  {
    try {
      HmtpWebSocketListener webSocketHandler = new HmtpWebSocketListener(broker);
        
      _webSocketClient = new WebSocketClient(_url, webSocketHandler);
      
      if (_virtualHost != null)
        _webSocketClient.setVirtualHost(_virtualHost);
      
      _webSocketClient.connect();
      
      return new HmtpLinkConnection(_webSocketClient, webSocketHandler);
    } catch (RuntimeException e) {
      throw e;
    } catch (IOException e) {
      throw new ActorException(e);
    }
  }
      
  /**
   * Login to the server
   */
  protected void loginImpl(String uid, Serializable credentials)
  {
    try {
      if (uid == null)
        uid = "";
      
      if (credentials == null)
        credentials = "";
      
      if (credentials instanceof SignedCredentials) {
      }
      else if (credentials instanceof String) {
        String password = (String) credentials;
        
        String clientNonce = String.valueOf(Alarm.getCurrentTime());
        
        NonceQuery nonceQuery = new NonceQuery(uid, clientNonce);
        NonceQuery nonceResult = null;
        //          = (NonceQuery) query(null, nonceQuery);
        
        String serverNonce = nonceResult.getNonce();
        String serverSignature = nonceResult.getSignature();
        
        String testSignature = _authManager.sign(uid, clientNonce, password);
        
        if (! testSignature.equals(serverSignature) && "".equals(uid))
          throw new ActorException(L.l("{0} server signature does not match",
                                      this));

        String signature = _authManager.sign(uid, serverNonce, password);

        SecurityService security = SecurityService.getCurrent();
        
        if ("".equals(uid))
          credentials = new SignedCredentials(uid, serverNonce, signature);
        else
          credentials = security.createCredentials(uid, password, serverNonce);
      }

      AuthResult result = null;
      // result = (AuthResult) query(null, new AuthQuery(uid, credentials));

      _jid = result.getJid();

      if (log.isLoggable(Level.FINE))
        log.fine(this + " login");
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the jid
   */
  public String getJid()
  {
    return _jid;
  }

  /**
   * Returns the broker jid
   */
  public String getBrokerJid()
  {
    String jid = getJid();

    if (jid == null)
      return null;

    int p = jid.indexOf('@');
    int q = jid.indexOf('/');

    if (p >= 0 && q >= 0)
      return jid.substring(p + 1, q);
    else if (p >= 0)
      return jid.substring(p + 1);
    else if (q >= 0)
      return jid.substring(0, q);
    else
      return jid;
  }

  public void flush()
    throws IOException
  {
    /*
    ClientToLinkStream stream = _linkStream;

    if (stream != null)
      stream.flush();
      */
  }

  public void close()
  {
    if (log.isLoggable(Level.FINE))
      log.fine(this + " close");

    // super.close();
    
    _webSocketClient.close();
   }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _url + "]";
  }

  @Override
  protected void finalize()
  {
    close();
  }

  /* (non-Javadoc)
   * @see com.caucho.bam.client.LinkConnectionFactory#isClosed()
   */
  @Override
  public boolean isClosed()
  {
    // TODO Auto-generated method stub
    return false;
  }
}
