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

package com.caucho.jms.queue;

import java.util.*;
import java.util.logging.*;
import java.io.Serializable;

import javax.annotation.*;
import javax.jms.*;

import com.caucho.jms.JmsRuntimeException;
import com.caucho.jms.message.*;
import com.caucho.jms.connection.*;

import com.caucho.util.*;

/**
 * Implements an abstract queue.
 */
abstract public class AbstractQueue extends AbstractDestination
  implements javax.jms.Queue
{
  private static final L10N L = new L10N(AbstractQueue.class);
  private static final Logger log
    = Logger.getLogger(AbstractQueue.class.getName());

  private QueueAdmin _admin;

  private ArrayList<MessageConsumerImpl> _messageConsumerList
    = new ArrayList<MessageConsumerImpl>();

  private int _roundRobin;
  
  private int _enqueueCount;

  // stats
  private long _listenerFailCount;
  private long _listenerFailLastTime;
  
  // queue api
  private ConnectionFactoryImpl _connectionFactory;
  private Connection _conn;

  private JmsSession _session;

  protected AbstractQueue()
  {
  }

  public void setQueueName(String name)
  {
    setName(name);
  }

  //
  // JMX statistics
  //

  /**
   * Returns the number of active message consumers
   */
  public int getConsumerCount()
  {
    return _messageConsumerList.size();
  }

  /**
   * Returns the queue size
   */
  public int getQueueSize()
  {
    return -1;
  }

  /**
   * Returns the number of listener failures.
   */
  public long getListenerFailCountTotal()
  {
    return _listenerFailCount;
  }

  /**
   * Returns the number of listener failures.
   */
  public long getListenerFailLastTime()
  {
    return _listenerFailLastTime;
  }

  public void init()
  {
  }

  @PostConstruct
  public void postConstruct()
  {
    init();

    _admin = new QueueAdmin(this);
    _admin.register();
  }
  
  public void addConsumer(MessageConsumerImpl consumer)
  {
    synchronized (_messageConsumerList) {
      if (! _messageConsumerList.contains(consumer))
	_messageConsumerList.add(consumer);

      startPoll();
    }
  }
  
  public void removeConsumer(MessageConsumerImpl consumer)
  {
    synchronized (_messageConsumerList) {
      _messageConsumerList.remove(consumer);

      // force a poll to avoid missing messages
      for (int i = 0; i < _messageConsumerList.size(); i++) {
	_messageConsumerList.get(i).notifyMessageAvailable();
      }

      if (_messageConsumerList.size() == 0)
        stopPoll();
    }
  }

  protected void notifyMessageAvailable()
  {
    synchronized (_messageConsumerList) {
      if (_messageConsumerList.size() > 0) {
	MessageConsumerImpl consumer;
	int count = _messageConsumerList.size();

	// notify until one of the consumers signals readiness to read
	do {
	  int roundRobin = _roundRobin++ % _messageConsumerList.size();
	  
	  consumer = _messageConsumerList.get(roundRobin);
	} while (! consumer.notifyMessageAvailable() && count-- > 0);
      }
    }
  }

  protected void startPoll()
  {
  }

  protected void stopPoll()
  {
  }

  /**
   * Called when a listener throws an excepton
   */
  public void addListenerException(Exception e)
  {
    synchronized (this) {
      _listenerFailCount++;
      _listenerFailLastTime = Alarm.getCurrentTime();
    }
  }

  @PreDestroy
  public void close()
  {
    stopPoll();
    
    super.close();
  }
  
  /**
   * Creates a QueueBrowser to browse messages in the queue.
   *
   * @param queue the queue to send messages to.
   */
  public QueueBrowser createBrowser(JmsSession session,
				    String messageSelector)
    throws JMSException
  {
    return new MessageBrowserImpl(this, messageSelector);
  }

  //
  // BlockingQueue api
  //

  public boolean add(Object value)
  {
    try {
      synchronized (this) {
	JmsSession session = getSession();

	Message msg = session.createObjectMessage((Serializable) value);
	
	session.send(this, msg, 0, 0, Integer.MAX_VALUE);

	return true;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new JmsRuntimeException(e);
    }
  }

  public boolean offer(Object value)
  {
    return add(value);
  }

  public boolean put(Object value)
  {
    return add(value);
  }

  public int remainingCapacity()
  {
    return Integer.MAX_VALUE;
  }

  private JmsSession getSession()
    throws JMSException
  {
    if (_conn == null) {
      _connectionFactory = new ConnectionFactoryImpl();
      _conn = _connectionFactory.createConnection();
      _conn.start();
    }
    
    if (_session == null) {
      _session =
	(JmsSession) _conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
    }

    return _session;
  }

  public String toString()
  {
    String className = getClass().getName();

    int p = className.lastIndexOf('.');
    
    return className.substring(p + 1) + "[" + getName() + "]";
  }
}

