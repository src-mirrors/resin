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

package com.caucho.server.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.security.MessageDigest;

import com.caucho.network.listen.SocketLinkDuplexController;
import com.caucho.network.listen.SocketLinkDuplexListener;
import com.caucho.remote.websocket.WebSocketConstants;
import com.caucho.remote.websocket.WebSocketInputStream;
import com.caucho.remote.websocket.WebSocketOutputStream;
import com.caucho.remote.websocket.WebSocketPrintWriter;
import com.caucho.remote.websocket.WebSocketWriter;
import com.caucho.servlet.WebSocketContext;
import com.caucho.servlet.WebSocketListener;
import com.caucho.util.Hex;
import com.caucho.util.L10N;
import com.caucho.util.RandomUtil;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.TempBuffer;
import com.caucho.vfs.WriteStream;

/**
 * User facade for http requests.
 */
class WebSocketContextImpl
  implements WebSocketContext, WebSocketConstants, SocketLinkDuplexListener
{
  private static final L10N L = new L10N(WebSocketContextImpl.class);
  
  private final HttpServletRequestImpl _request;
  private final WebSocketListener _listener;

  private SocketLinkDuplexController _controller;
  private byte []_clientNonce;
  private byte []_serverNonce;
  
  private WebSocketOutputStream _binaryOut;
  private WebSocketInputStream _binaryIn;
  
  private WebSocketWriter _textOut;
  private PrintWriter _textWriter;

  WebSocketContextImpl(HttpServletRequestImpl request,
                       HttpServletResponseImpl response,
                       WebSocketListener listener,
                       byte []clientNonce)
  {
    _request = request;
    _listener = listener;

    _clientNonce = clientNonce;
    _serverNonce = new byte[8];
    
    setLong(_serverNonce, 0, RandomUtil.getRandomLong());
  }
  
  private void setLong(byte []buffer, int offset, long value)
  {
    _serverNonce[offset + 0] = (byte) (value >> 56);
    _serverNonce[offset + 1] = (byte) (value >> 48);
    _serverNonce[offset + 2] = (byte) (value >> 40);
    _serverNonce[offset + 3] = (byte) (value >> 32);
    _serverNonce[offset + 4] = (byte) (value >> 24);
    _serverNonce[offset + 5] = (byte) (value >> 16);
    _serverNonce[offset + 6] = (byte) (value >> 8);
    _serverNonce[offset + 7] = (byte) (value >> 0);
  }

  public void setController(SocketLinkDuplexController controller)
  {
    _controller = controller;
  }

  @Override
  public void setTimeout(long timeout)
  {
    _controller.setIdleTimeMax(timeout);
  }

  @Override
  public long getTimeout()
  {
    return _controller.getIdleTimeMax();
  }

  @Override
  public InputStream getInputStream()
  throws IOException
  {
    return _controller.getReadStream();
  }

  @Override
  public OutputStream startBinaryMessage()
  throws IOException
  {
    if (_binaryOut == null)
      _binaryOut = new WebSocketOutputStream(_controller.getWriteStream());
    
    _binaryOut.init();
    
    return _binaryOut;
  }

  @Override
  public PrintWriter startTextMessage()
    throws IOException
  {
    if (_textOut == null) {
      _textOut = new WebSocketWriter(_controller.getWriteStream(),
                                     TempBuffer.allocate().getBuffer());
      _textWriter = new WebSocketPrintWriter(_textOut);
    }
    
    _textOut.init();
    
    return _textWriter;
  }

  @Override
  public void complete()
  {
    _controller.complete();
  }
  
  //
  // duplex callbacks
  //
  
  void sendHello()
    throws IOException
  {
    WriteStream out = _controller.getWriteStream();
    
    byte []hash = calculateServerHash();
    
    out.write(FLAG_FIN | OP_HELLO);
    out.write(_serverNonce.length + hash.length);
    out.write(_serverNonce, 0, _serverNonce.length);
    out.write(hash, 0, hash.length);
  }
  
  private byte []calculateServerHash()
  {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      
      md.update(_clientNonce);
      
      String webSocket = "WebSocket";
      for (int i = 0; i < webSocket.length(); i++) {
        md.update((byte) webSocket.charAt(i));
      }
      
      md.update(_serverNonce);
      
      return md.digest();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  
  private byte []calculateClientHash()
  {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      
      md.update(_serverNonce);
      
      String webSocket = "WebSocket";
      for (int i = 0; i < webSocket.length(); i++) {
        md.update((byte) webSocket.charAt(i));
      }
      
      md.update(_clientNonce);
      
      return md.digest();
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
  
  void readHello()
    throws IOException
  {
    ReadStream is = _controller.getReadStream();

    int frame1 = is.read();
    int frame2 = is.read();

    boolean isFinal = (frame1 & FLAG_FIN) != 0;
    int opcode = frame1 & 0x0f;
    int len = frame2 & 0x7f;

    if (! isFinal) {
      throw new IllegalStateException(L.l("Invalid HELLO packet. Missing FIN."));
    }

    if (opcode != OP_HELLO) {
      throw new IllegalStateException(L.l("Invalid HELLO packet. OP=0x{0}.",
                                          Integer.toHexString(opcode)));
    }

    if (len != 16) {
      throw new IllegalStateException(L.l("Invalid HELLO packet. len={0}.",
                                          len));
    }

    byte []hash = new byte[len];
    is.readAll(hash, 0, len);

    byte []expectedHash = calculateClientHash();

    if (! isMatch(hash, expectedHash)) {
      throw new IllegalStateException(L.l("Invalid Hash. expected={0}, client={1}.",
                                          Hex.toHex(expectedHash),
                                          Hex.toHex(hash)));
    }
  }
  
  void onHandshakeComplete(boolean isValid)
    throws IOException
  {
    try {
      _listener.onHandshakeComplete(this, isValid);
    } finally {
      if (! isValid)
        complete();
    }
  }
  
  private boolean isMatch(byte []clientHash,
                          byte []expectedHash)
  {
    if (clientHash.length != expectedHash.length)
      return false;
    
    for (int i = clientHash.length - 1; i >= 0; i--) {
      if (clientHash[i] != expectedHash[i])
        return false;
    }
    
    return true;
  }
  
  void onStart()
    throws IOException
  {
    _listener.onStart(this);
  }
  
  void flush()
    throws IOException
  {
    WriteStream out = _controller.getWriteStream();
    
    out.flush();
  }

  @Override
  public void onStart(SocketLinkDuplexController context) 
    throws IOException
  {
  }

  public void onRead(SocketLinkDuplexController duplex)
  throws IOException
  {
    ReadStream is = _controller.getReadStream();
    
    do {
      int frame1 = is.read();
      int frame2 = is.read();
      
      boolean isFinal = (frame1 & FLAG_FIN) == FLAG_FIN;
      
      int opcode = frame1 & 0xf;
      
      long len = frame2 & 0x7f;
      
      if (len < 0x7e) {
      }
      else if (len == 0x7e) {
        len = (is.read() << 8) + is.read();
      }
      else {
        len = (((long) is.read() << 56)
              + ((long) is.read() << 48)
              + ((long) is.read() << 40)
              + ((long) is.read() << 32)
              + ((long) is.read() << 24)
              + ((long) is.read() << 16)
              + ((long) is.read() << 8)
              + ((long) is.read()));
      }
      
      switch (opcode) {
      case OP_BINARY:
        if (_binaryIn == null)
          _binaryIn = new WebSocketInputStream(is);
        
        _binaryIn.init(isFinal, len);
        
        _listener.onReadBinary(this, _binaryIn);
        break;
        
      default:
        // XXX:
        complete();
        break;
      }
    } while (_request.getAvailable() > 0);
  }

  public void onComplete(SocketLinkDuplexController duplex)
  throws IOException
  {
    _listener.onComplete(this);
  }

  public void onTimeout(SocketLinkDuplexController duplex)
  throws IOException
  {
    _listener.onTimeout(this);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _listener + "]";
  }
}
