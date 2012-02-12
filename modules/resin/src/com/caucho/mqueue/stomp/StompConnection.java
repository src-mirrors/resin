/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.mqueue.stomp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.distcache.ClusterCache;
import com.caucho.distcache.ExtCacheEntry;
import com.caucho.memcached.MemcachedProtocol;
import com.caucho.network.listen.ProtocolConnection;
import com.caucho.network.listen.SocketLink;
import com.caucho.util.Alarm;
import com.caucho.util.CharBuffer;
import com.caucho.util.HashKey;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.TempStream;
import com.caucho.vfs.WriteStream;

/**
 * Custom serialization for the cache
 */
public class StompConnection implements ProtocolConnection
{
  private static final Logger log
    = Logger.getLogger(StompConnection.class.getName());
  
  private static final HashMap<CharBuffer,StompCommand> _commandMap
    = new HashMap<CharBuffer,StompCommand>();
  
  private static final CharBuffer CONTENT_LENGTH
    = new CharBuffer("content-length");
  
  private static final CharBuffer CONTENT_TYPE
    = new CharBuffer("content-type");
  
  private static final CharBuffer DESTINATION
    = new CharBuffer("destination");
  
  private static final CharBuffer ID
    = new CharBuffer("id");
  
  private static final CharBuffer MESSAGE_ID
    = new CharBuffer("message-id");
  
  private static final CharBuffer PERSISTENT
    = new CharBuffer("persistent");
  
  private static final CharBuffer RECEIPT
    = new CharBuffer("receipt");
  
  private static final CharBuffer SUBSCRIPTION
    = new CharBuffer("subscription");

  private static final CharBuffer TRANSACTION
    = new CharBuffer("transaction");

  private static final StompPublisher NULL_DESTINATION
    = new StompNullDestination();
  
  private StompProtocol _stomp;
  private SocketLink _link;
  
  private HashMap<String,StompPublisher> _destinationMap
    = new HashMap<String,StompPublisher>();
  
  private HashMap<String,StompSubscription> _subscriptionMap
    = new HashMap<String,StompSubscription>();
  
  private CharBuffer _method = new CharBuffer();
  private char []_headerBuffer = new char[4096];
  private int _headerOffset;
  
  private String _destinationName;
  private long _contentLength;
  private String _contentType;
  private String _id;
  private long _messageId;
  private String _receipt;
  private String _subscription;
  private String _transaction;
  
  private long _sessionId;
  private ArrayList<StompXaItem> _xaList;
  
  StompConnection(StompProtocol stomp, SocketLink link)
  {
    _stomp = stomp;
    _link = link;
  }
  
  @Override
  public String getProtocolRequestURL()
  {
    return "stomp:";
  }
  
  @Override
  public void init()
  {
  }
  
  SocketLink getLink()
  {
    return _link;
  }
  
  ReadStream getReadStream()
  {
    return _link.getReadStream();
  }
  
  WriteStream getWriteStream()
  {
    return _link.getWriteStream();
  }
  
  public long getSessionId()
  {
    return _sessionId;
  }
  
  public long getContentLength()
  {
    return _contentLength;
  }
  
  public long getMessageId()
  {
    return _messageId;
  }
  
  public String getSubscription()
  {
    return _subscription;
  }
  
  public String getContentType()
  {
    return _contentType;
  }
  
  public StompPublisher getDestination()
  {
    if (_destinationName == null)
      return null;
    
    StompPublisher dest = _destinationMap.get(_destinationName);
    
    if (dest == null) {
      dest = _stomp.createDestination(_destinationName);
      
      if (dest != null)
        _destinationMap.put(_destinationName, dest);
      else
        dest = NULL_DESTINATION;
    }
    
    return dest;
  }
  
  public String getId()
  {
    return _id;
  }
  
  public String getReceipt()
  {
    return _receipt;
  }
  
  public StompReceiptListener createReceiptCallback()
  {
    if (_receipt != null)
      return new ReceiptListener(this, _receipt);
    else
      return null;
  }
  
  public String getTransaction()
  {
    return _transaction;
  }
  
  public boolean subscribe()
    throws IOException
  {
    if (_id == null)
      throw new IOException("sub requires id");
    
    if (_destinationName == null)
      throw new IOException("sub requires destination");
    
    StompSubscription sub = _subscriptionMap.get(_id);
    
    if (sub != null)
      throw new IOException("sub exists");
    
    StompBroker broker = _stomp.getBroker();
    StompMessageListener listener = new MessageListener(this, _id, _destinationName);
    
    sub = broker.createSubscription(_destinationName, listener);
    
    _subscriptionMap.put(_id, sub);

    return true;
  }
  
  public boolean unsubscribe(String id)
  {
    StompSubscription sub = _subscriptionMap.remove(id);
    
    if (sub != null) {
      sub.close();
      return true;
    }
    else {
      return false;
    }
  }
  
  public boolean ack(String sid, long mid)
  {
    StompSubscription sub = _subscriptionMap.get(sid);
    
    if (sub != null) {
      sub.ack(mid);
      return true;
    }
    else {
      return false;
    }
  }
  
  public boolean nack(String sid, long mid)
  {
    StompSubscription sub = _subscriptionMap.get(sid);
    
    if (sub != null) {
      sub.nack(mid);
      return true;
    }
    else {
      return false;
    }
  }
  
  public boolean begin(String tid)
  {
    _xaList = new ArrayList<StompXaItem>();
    
    return true;
  }
  
  public boolean commit(String tid)
  {
    ArrayList<StompXaItem> xaList = _xaList;
    _xaList = null;
    
    for (StompXaItem xaItem : xaList) {
      xaItem.doCommand(this);
    }
    
    return true;
  }
  
  public boolean abort(String tid)
  {
    _xaList = null;
    
    return true;
  }
  
  void addXaItem(StompXaItem xaItem)
  {
    _xaList.add(xaItem);
  }

  @Override
  public boolean handleRequest() throws IOException
  {
    ReadStream is = _link.getReadStream();
    
    if (! readMethod(is)) {
      return false;
    }
    StompCommand cmd = _commandMap.get(_method);
    
    if (cmd == null)
      throw new IOException("unknown command: " + _method);
    
    clearHeaders();
    
    while (readHeader(is)) {
    }
    
    WriteStream os = _link.getWriteStream();
    System.out.println("CMD: " + cmd + " " + os);
    return cmd.doCommand(this, is, os);
  }
  
  private void clearHeaders()
  {
    _contentLength = -1;
    _contentType = null;
    _destinationName = null;
    _id = null;
    _receipt = null;
    _messageId = -1;
    _transaction = null;
  }
  
  private boolean readMethod(ReadStream is)
    throws IOException
  {
    CharBuffer method = _method;
    method.clear();
    
    int ch;
    
    for (ch = is.read(); 'A' <= ch && ch <= 'Z'; ch = is.read()) {
      method.append((char) ch);
    }
    
    return (ch == '\n');
  }
  
  private boolean readHeader(ReadStream is)
    throws IOException
  {
    int ch;
    
    char []buffer = _headerBuffer;
    int keyHead = 0;
    int keyTail = 0;
    int valueHead = 0;
    int valueTail = 0;
    
    for (ch = is.read(); ch > 0 && ch != ':' && ch != '\n'; ch = is.read()) {
      buffer[keyTail++] = (char) ch;
    }

    if (ch == '\n')
      return false;
    
    if (ch != ':')
      throw new IOException("bad protocol");
    
    buffer[keyTail] = ':';
    valueHead = keyTail + 1;
    
    valueTail = valueHead;
    for (ch = is.read(); ch > 0 && ch != '\n'; ch = is.read()) {
      buffer[valueTail++] = (char) ch;
    }
    
    if (ch != '\n')
      throw new IOException("bad protocol2");
    
    buffer[valueTail] = '\n';
    
    handleHeader(buffer, keyHead, keyTail - keyHead, 
                 buffer, valueHead, valueTail - valueHead);
    
    return true;
  }
  
  private void handleHeader(char []keyBuffer, int keyOffset, int keyLength,
                            char []valueBuffer, int valueOffset, int valueLength)
    throws IOException
  {
    int code = (keyLength << 16) + keyBuffer[keyOffset];
    System.out.println("CODE: " + Integer.toHexString(code));
    switch (code) {
    case 0xe0000 + 'c':
      if (CONTENT_LENGTH.equals(keyBuffer, keyOffset, keyLength)) {
        _contentLength = parseLong(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0xc0000 + 'c':
      if (CONTENT_TYPE.equals(keyBuffer, keyOffset, keyLength)) {
        _contentType = new String(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0xb0000 + 'd':
      if (DESTINATION.equals(keyBuffer, keyOffset, keyLength)) {
        _destinationName = new String(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0x20000 + 'i':
      if (ID.equals(keyBuffer, keyOffset, keyLength)) {
        _id = new String(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0xa0000 + 'm':
      if (MESSAGE_ID.equals(keyBuffer, keyOffset, keyLength)) {
        _messageId = parseLong(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0x70000 + 'r':
      if (RECEIPT.equals(keyBuffer, keyOffset, keyLength)) {
        _receipt = new String(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0xc0000 + 's':
      if (SUBSCRIPTION.equals(keyBuffer, keyOffset, keyLength)) {
        _subscription = new String(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    case 0xb0000 + 't':
      if (TRANSACTION.equals(keyBuffer, keyOffset, keyLength)) {
        _transaction = new String(valueBuffer, valueOffset, valueLength);
      }
      break;
      
    default:
      System.out.println("HH: "+ new String(keyBuffer, keyOffset, keyLength)
                         + " " + new String(valueBuffer, valueOffset, valueLength));
      break;
    }
  }

  private long parseLong(char []buffer, int offset, int length)
  {
    long value = 0;
    
    for (int i = 0; i < length; i++) {
      value = 10 * value + buffer[offset + i] - '0';
    }
    
    return value;
  }
  
  void receipt(String receipt)
  {
    try {
      WriteStream out = _link.getWriteStream();
      
      out.print("RECEIPT\nreceipt-id:");
      out.print(receipt);
      out.print("\n\n\0");
      out.flush();
    } catch (IOException e) {
      log.log(Level.FINER, e.toString(), e);
    }
  }
  
  void message(String subscription,
               String destination,
               long messageId,
               InputStream bodyIs,
               long contentLength)
    throws IOException
  {
    WriteStream out = _link.getWriteStream();
    
    out.print("MESSAGE");
    out.print("\nsubscription:");
    out.print(subscription);
    out.print("\ndestination:");
    out.print(destination);
    out.print("\nmessage-id:");
    out.print(messageId);

    /*
    if (contentType != null) {
      out.print("\ncontent-type:");
      out.print(contentType);
    }
    */
    
    if (contentLength >= 0) {
      out.print("\ncontent-length:");
      out.print(contentLength);
      out.print("\n\n");
      
      out.writeStream(bodyIs, (int) contentLength);
    }
    else {
      out.print("\n\n");
      out.writeStream(bodyIs);
    }
    
    out.print("\0");
    out.flush();
  }
  
  @Override
  public boolean handleResume() throws IOException
  {
    return false;
  }

  @Override
  public boolean isWaitForRead()
  {
    return false;
  }

  @Override
  public void onCloseConnection()
  {
    ArrayList<StompPublisher> destList
      = new ArrayList<StompPublisher>(_destinationMap.values());
  
    _destinationMap.clear();
    
    ArrayList<StompSubscription> subList
      = new ArrayList<StompSubscription>(_subscriptionMap.values());

    _destinationMap.clear();
    _subscriptionMap.clear();
    
    for (StompPublisher dest : destList) {
      dest.close();
    }
    
    for (StompSubscription sub : subList) {
      sub.close();
    }
    
    _xaList = null;
  }

  @Override
  public void onStartConnection()
  {
  }
  
  static class ReceiptListener implements StompReceiptListener {
    private StompConnection _conn;
    private String _receipt;
  
    ReceiptListener(StompConnection conn, String receipt)
    {
      _conn = conn;
      _receipt = receipt;
    }
    
    @Override
    public void onComplete()
    {
      _conn.receipt(_receipt);
    }
    
    public void onError(String msg)
    {
      
    }
  }
  
  static class MessageListener implements StompMessageListener {
    private StompConnection _conn;
    private String _subscription;
    private String _destination;
  
    MessageListener(StompConnection conn, 
                    String subscription,
                    String destination)
    {
      _conn = conn;
      _subscription = subscription;
      _destination = destination;
    }

    @Override
    public void onMessage(long messageId,
                          InputStream bodyIs,
                          long contentLength)
      throws IOException
    {
      _conn.message(_subscription, _destination,
                    messageId, bodyIs, contentLength);
    }
  }
  
  static {
    _commandMap.put(new CharBuffer("ABORT"), new StompAbortCommand());
    _commandMap.put(new CharBuffer("ACK"), new StompAckCommand());
    _commandMap.put(new CharBuffer("BEGIN"), new StompBeginCommand());
    _commandMap.put(new CharBuffer("COMMIT"), new StompCommitCommand());
    _commandMap.put(new CharBuffer("CONNECT"), new StompConnectCommand());
    _commandMap.put(new CharBuffer("DISCONNECT"), new StompDisconnectCommand());
    _commandMap.put(new CharBuffer("NACK"), new StompNackCommand());
    _commandMap.put(new CharBuffer("SEND"), new StompSendCommand());
    _commandMap.put(new CharBuffer("SUBSCRIBE"), new StompSubscribeCommand());
    _commandMap.put(new CharBuffer("UNSUBSCRIBE"), new StompUnsubscribeCommand());
  }
}
