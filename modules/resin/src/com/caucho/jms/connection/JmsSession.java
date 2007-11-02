/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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

package com.caucho.jms.connection;

import com.caucho.jms2.JMSExceptionWrapper;
import com.caucho.jms.message.*;
import com.caucho.jms.queue.*;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.util.ThreadPool;
import com.caucho.util.ThreadTask;

import javax.jms.*;
import javax.jms.IllegalStateException;
import javax.naming.*;
import javax.transaction.*;
import javax.transaction.xa.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the JMS session.
 */
public class JmsSession implements XASession, ThreadTask, XAResource
{
  protected static final Logger log
    = Logger.getLogger(JmsSession.class.getName());
  protected static final L10N L = new L10N(JmsSession.class);

  private static final long SHUTDOWN_WAIT_TIME = 10000;

  private boolean _isXA;
  private TransactionManager _tm;
  
  private boolean _isTransacted;
  private int _acknowledgeMode;

  private ClassLoader _classLoader;
  
  private ConnectionImpl _connection;
  
  private final ArrayList<MessageConsumerImpl> _consumers
    = new ArrayList<MessageConsumerImpl>();

  private MessageFactory _messageFactory = new MessageFactory();
  private MessageListener _messageListener;
  private boolean _isAsynchronous;

  // 4.4.1 - client's responsibility
  private Thread _thread;

  // transacted messages
  private ArrayList<TransactedMessage> _transactedMessages;

  // true if the listener thread is running
  private volatile boolean _isRunning;
  
  private volatile boolean _isClosed;
  private volatile boolean _hasMessage;

  public JmsSession(ConnectionImpl connection,
		     boolean isTransacted, int ackMode,
                     boolean isXA)
    throws JMSException
  {
    _classLoader = Thread.currentThread().getContextClassLoader();
    
    _connection = connection;

    _isXA = isXA;

    if (isTransacted) {
      try {
        InitialContext ic = new InitialContext();
        
        _tm = (TransactionManager) ic.lookup("java:comp/TransactionManager");
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
    
    _isTransacted = isTransacted;
    _acknowledgeMode = ackMode;

    if (isTransacted)
      _acknowledgeMode = 0;
    else {
      switch (ackMode) {
      case CLIENT_ACKNOWLEDGE:
      case DUPS_OK_ACKNOWLEDGE:
      case AUTO_ACKNOWLEDGE:
	_acknowledgeMode = ackMode;
	break;
      default:
        // XXX: tck
        // throw new JMSException(L.l("{0} is an illegal acknowledge mode", ackMode));
	log.warning(L.l("JmsSession {0} is an illegal acknowledge mode",
                        ackMode));
        _acknowledgeMode = AUTO_ACKNOWLEDGE;
        break;
      }
    }
    
    _connection.addSession(this);
  }

  /**
   * Returns the connection.
   */
  ConnectionImpl getConnection()
  {
    return _connection;
  }

  /**
   * Returns the ClassLoader.
   */
  ClassLoader getClassLoader()
  {
    return _classLoader;
  }

  /**
   * Returns the connection's clientID
   */
  public String getClientID()
    throws JMSException
  {
    return _connection.getClientID();
  }

  /**
   * Returns true if the connection is active.
   */
  public boolean isActive()
  {
    return ! _isClosed && _connection.isActive();
  }

  /**
   * Returns true if the connection is active.
   */
  boolean isStopping()
  {
    return _connection.isStopping();
  }

  /**
   * Returns true if the session is in a transaction.
   */
  public boolean getTransacted()
    throws JMSException
  {
    checkOpen();
    
    return _isTransacted;
  }

  /**
   * Returns the acknowledge mode for the session.
   */
  public int getAcknowledgeMode()
    throws JMSException
  {
    checkOpen();
    
    return _acknowledgeMode;
  }

  /**
   * Returns the message listener
   */
  public MessageListener getMessageListener()
    throws JMSException
  {
    checkOpen();
    
    return _messageListener;
  }

  /**
   * Sets the message listener
   */
  public void setMessageListener(MessageListener listener)
    throws JMSException
  {
    checkOpen();
    
    _messageListener = listener;
    setAsynchronous();
  }

  /**
   * Set true for a synchronous session.
   */
  void setAsynchronous()
  {
    _isAsynchronous = true;

    notifyMessageAvailable();
  }

  /**
   * Set true for a synchronous session.
   */
  boolean isAsynchronous()
  {
    return _isAsynchronous;
  }

  /**
   * Creates a new byte[] message.
   */
  public BytesMessage createBytesMessage()
    throws JMSException
  {
    checkOpen();
    
    return new BytesMessageImpl();
  }

  /**
   * Creates a new map message.
   */
  public MapMessage createMapMessage()
    throws JMSException
  {
    checkOpen();
    
    return new MapMessageImpl();
  }

  /**
   * Creates a message.  Used when only header info is important.
   */
  public Message createMessage()
    throws JMSException
  {
    checkOpen();
    
    return new MessageImpl();
  }

  /**
   * Creates an object message.
   */
  public ObjectMessage createObjectMessage()
    throws JMSException
  {
    checkOpen();
    
    return new ObjectMessageImpl();
  }

  /**
   * Creates an object message.
   *
   * @param obj a serializable message.
   */
  public ObjectMessage createObjectMessage(Serializable obj)
    throws JMSException
  {
    checkOpen();
    
    ObjectMessage msg = createObjectMessage();

    msg.setObject(obj);

    return msg;
  }

  /**
   * Creates a stream message.
   */
  public StreamMessage createStreamMessage()
    throws JMSException
  {
    checkOpen();
    
    return new StreamMessageImpl();
  }

  /**
   * Creates a text message.
   */
  public TextMessage createTextMessage()
    throws JMSException
  {
    checkOpen();
    
    return new TextMessageImpl();
  }

  /**
   * Creates a text message.
   */
  public TextMessage createTextMessage(String message)
    throws JMSException
  {
    checkOpen();
    
    TextMessage msg = createTextMessage();

    msg.setText(message);

    return msg;
  }

  /**
   * Creates a consumer to receive messages.
   *
   * @param destination the destination to receive messages from.
   */
  public MessageConsumer createConsumer(Destination destination)
    throws JMSException
  {
    checkOpen();

    return createConsumer(destination, null, false);
  }

  /**
   * Creates a consumer to receive messages.
   *
   * @param destination the destination to receive messages from.
   * @param messageSelector query to restrict the messages.
   */
  public MessageConsumer createConsumer(Destination destination,
                                        String messageSelector)
    throws JMSException
  {
    checkOpen();
    
    return createConsumer(destination, messageSelector, false);
  }

  /**
   * Creates a consumer to receive messages.
   *
   * @param destination the destination to receive messages from.
   * @param messageSelector query to restrict the messages.
   */
  public MessageConsumer createConsumer(Destination destination,
                                        String messageSelector,
                                        boolean noLocal)
    throws JMSException
  {
    checkOpen();

    if (destination == null)
      throw new InvalidDestinationException(L.l("destination is null.  Destination may not be null for Session.createConsumer"));

    MessageConsumerImpl consumer;
    
    if (destination instanceof AbstractQueue) {
      AbstractQueue dest = (AbstractQueue) destination;

      consumer = new MessageConsumerImpl(this, dest, messageSelector, noLocal);
    }
    else if (destination instanceof AbstractTopic) {
      AbstractTopic dest = (AbstractTopic) destination;

      consumer = new TopicSubscriberImpl(this, dest, messageSelector, noLocal);
    }
    else
      throw new InvalidDestinationException(L.l("'{0}' is an unknown destination.  The destination must be a Resin JMS Destination.",
						destination));

    
    addConsumer(consumer);

    return consumer;
  }

  /**
   * Creates a producer to produce messages.
   *
   * @param destination the destination to send messages from.
   */
  public MessageProducer createProducer(Destination destination)
    throws JMSException
  {
    checkOpen();

    if (destination == null) {
      return new MessageProducerImpl(this, null);
    }
    
    if (! (destination instanceof AbstractDestination))
      throw new InvalidDestinationException(L.l("'{0}' is an unknown destination.  The destination must be a Resin JMS destination for Session.createProducer.",
						destination));

    AbstractDestination dest = (AbstractDestination) destination;

    return new MessageProducerImpl(this, dest);
  }

  /**
   * Creates a QueueBrowser to browse messages in the queue.
   *
   * @param queue the queue to send messages to.
   */
  public QueueBrowser createBrowser(Queue queue)
    throws JMSException
  {
    checkOpen();
    
    return createBrowser(queue, null);
  }

  /**
   * Creates a QueueBrowser to browse messages in the queue.
   *
   * @param queue the queue to send messages to.
   */
  public QueueBrowser createBrowser(Queue queue, String messageSelector)
    throws JMSException
  {
    checkOpen();

    if (queue == null)
      throw new InvalidDestinationException(L.l("queue is null.  Queue may not be null for Session.createBrowser"));
    
    if (! (queue instanceof AbstractQueue))
      throw new InvalidDestinationException(L.l("'{0}' is an unknown queue.  The queue must be a Resin JMS Queue for Session.createBrowser.",
						queue));
    
    return ((AbstractQueue) queue).createBrowser(this, messageSelector);
  }

  /**
   * Creates a new queue.
   */
  public Queue createQueue(String queueName)
    throws JMSException
  {
    checkOpen();

    return _connection.createQueue(queueName);
  }

  /**
   * Creates a temporary queue.
   */
  public TemporaryQueue createTemporaryQueue()
    throws JMSException
  {
    checkOpen();
    
    return new TemporaryQueueImpl(this);
  }

  /**
   * Creates a new topic.
   */
  public Topic createTopic(String topicName)
    throws JMSException
  {
    checkOpen();

    return _connection.createTopic(topicName);
  }

  /**
   * Creates a temporary topic.
   */
  public TemporaryTopic createTemporaryTopic()
    throws JMSException
  {
    checkOpen();
    
    return new TemporaryTopicImpl(this);
  }

  /**
   * Creates a durable subscriber to receive messages.
   *
   * @param topic the topic to receive messages from.
   */
  public TopicSubscriber createDurableSubscriber(Topic topic, String name)
    throws JMSException
  {
    checkOpen();
    
    if (getClientID() == null)
      throw new JMSException(L.l("connection may not create a durable subscriber because it does not have an assigned ClientID."));

    return createDurableSubscriber(topic, name, null, false);
  }

  /**
   * Creates a subscriber to receive messages.
   *
   * @param topic the topic to receive messages from.
   * @param messageSelector topic to restrict the messages.
   * @param noLocal if true, don't receive messages we've sent
   */
  public TopicSubscriber createDurableSubscriber(Topic topic,
                                                 String name,
                                                 String messageSelector,
                                                 boolean noLocal)
    throws JMSException
  {
    checkOpen();

    if (topic == null)
      throw new InvalidDestinationException(L.l("destination is null.  Destination may not be null for Session.createDurableSubscriber"));
    
    if (! (topic instanceof AbstractTopic))
      throw new InvalidDestinationException(L.l("'{0}' is an unknown destination.  The destination must be a Resin JMS Destination.",
						topic));
    
    AbstractTopic topicImpl = (AbstractTopic) topic;

    if (_connection.getDurableSubscriber(name) != null) {
      // jms/2130
      // unsubscribe(name);
      /*
      throw new JMSException(L.l("'{0}' is already an active durable subscriber",
				 name));
      */
    }

    AbstractQueue queue = topicImpl.createSubscriber(this, name, noLocal);

    TopicSubscriberImpl consumer;
    consumer = new TopicSubscriberImpl(this, topicImpl, queue,
				       messageSelector, noLocal);
    
    _connection.putDurableSubscriber(name, consumer);
    
    addConsumer(consumer);

    return consumer;
  }

  /**
   * Unsubscribe from a durable subscription.
   */
  public void unsubscribe(String name)
    throws JMSException
  {
    checkOpen();

    if (name == null)
      throw new InvalidDestinationException(L.l("destination is null.  Destination may not be null for Session.unsubscribe"));

    TopicSubscriber subscriber = _connection.removeDurableSubscriber(name);

    if (subscriber == null)
      throw new InvalidDestinationException(L.l("'{0}' is an unknown subscriber for Session.unsubscribe",
                                          name));

    subscriber.close();
  }

  /**
   * Starts the session.
   */
  void start()
  {
    if (log.isLoggable(Level.FINE))
      log.fine(toString() + " active");
    
    notifyMessageAvailable();
  }

  /**
   * Stops the session.
   */
  void stop()
  {
    if (log.isLoggable(Level.FINE))
      log.fine(toString() + " stopping");
    
    synchronized (_consumers) {
      long timeout = Alarm.getCurrentTime() + SHUTDOWN_WAIT_TIME;
      while (_isRunning && Alarm.getCurrentTime() < timeout) {
	try {
	  _consumers.wait(SHUTDOWN_WAIT_TIME);
	
	  if (Alarm.isTest()) {
	    return;
	  }
	} catch (Throwable e) {
	  log.log(Level.FINER, e.toString(), e);
	}
      }

      ArrayList<MessageConsumerImpl> consumers
	= new ArrayList<MessageConsumerImpl>(_consumers);
      
      for (MessageConsumerImpl consumer : consumers) {
	try {
	  // XXX: should be stop()?
	  
	  consumer.stop();
	} catch (Throwable e) {
	  log.log(Level.FINE, e.toString(), e);
	}
      }
    }
  }
  
  /**
   * Commits the messages.
   */
  public void commit()
    throws JMSException
  {
    commit(false);
  }
  
  /**
   * Commits the messages.
   */
  private void commit(boolean isXA)
    throws JMSException
  {
    if (! isXA)
      checkOpen();

    if (! _isTransacted)
      throw new IllegalStateException(L.l("commit() can only be called on a transacted session."));


    ArrayList<TransactedMessage> messages = _transactedMessages;
    if (messages != null) {
      try {
	for (int i = 0; i < messages.size(); i++) {
	  messages.get(i).commit();
	}
      } finally {
	messages.clear();
      }
    }

    if (! isXA)
      acknowledge();
  }
  
  /**
   * Acknowledge received
   */
  public void acknowledge()
    throws JMSException
  {
    checkOpen();

    if (_transactedMessages != null) {
      for (int i = _transactedMessages.size() - 1; i >= 0; i--) {
	TransactedMessage msg = _transactedMessages.get(i);

	if (msg instanceof ReceiveMessage) {
	  _transactedMessages.remove(i);

	  msg.commit();
	}
      }
    }
  }
  
  /**
   * Recovers the messages.
   */
  public void recover()
    throws JMSException
  {
    checkOpen();

    if (_isTransacted)
      throw new IllegalStateException(L.l("recover() may not be called on a transacted session."));

    if (_transactedMessages != null) {
      for (int i = _transactedMessages.size() - 1; i >= 0; i--) {
	TransactedMessage msg = _transactedMessages.get(i);

	if (msg instanceof ReceiveMessage) {
	  _transactedMessages.remove(i);

	  msg.rollback();
	}
      }
    }
  }
  
  /**
   * Rollsback the messages.
   */
  public void rollback()
    throws JMSException
  {
    checkOpen();

    if (! _isTransacted)
      throw new IllegalStateException(L.l("rollback() can only be called on a transacted session."));
    
    if (_transactedMessages != null) {
      for (int i = 0; i < _transactedMessages.size(); i++)
	_transactedMessages.get(i).rollback();

      _transactedMessages.clear();
    }
  }
  
  /**
   * Closes the session
   */
  public void close()
    throws JMSException
  {
    if (_isClosed)
      return;

    try {
      stop();
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    ArrayList<TransactedMessage> messages = _transactedMessages;
    if (messages != null) {
      try {
	for (int i = 0; i < messages.size(); i++) {
	  messages.get(i).close();
	}
      } catch (Exception e) {
	log.log(Level.WARNING, e.toString(), e);
      }
    }

    for (int i = 0; i < _consumers.size(); i++) {
      MessageConsumerImpl consumer = _consumers.get(i);

      try {
	consumer.close();
      } catch (Exception e) {
	log.log(Level.WARNING, e.toString(), e);
      }
    }

    try {
      _connection.removeSession(this);
    } finally {
      _isClosed = true;
    }

    _classLoader = null;
  }

  protected void addConsumer(MessageConsumerImpl consumer)
  {
    _consumers.add(consumer);

    notifyMessageAvailable();
  }

  protected void removeConsumer(MessageConsumerImpl consumer)
  {
    if (_consumers != null)
      _consumers.remove(consumer);
  }

  /**
   * Notifies the receiver.
   */
  boolean notifyMessageAvailable()
  {
    synchronized (_consumers) {
      _hasMessage = true;

      if (_isRunning || ! _isAsynchronous || ! isActive())
	return false;

      _isRunning = true;
    }

    ThreadPool.getThreadPool().schedule(this);
    // the yield is only needed for the regressions
    Thread.yield();

    return true;
  }

  /**
   * Adds a message to the session message queue.
   */
  public void send(AbstractDestination queue,
                   Message appMessage,
                   int deliveryMode,
                   int priority,
                   long timeout)
    throws JMSException
  {
    checkOpen();
    
    if (queue == null)
      throw new UnsupportedOperationException(L.l("empty queue is not allowed for this session."));
    
    MessageImpl message = _messageFactory.copy(appMessage);

    long now = Alarm.getExactTime();
    long expiration = now + timeout;

    message.setJMSMessageID(queue.generateMessageID());
    message.setJMSDestination(queue.getJMSDestination());
    message.setJMSDeliveryMode(deliveryMode);
    message.setJMSTimestamp(now);
    message.setJMSExpiration(expiration);
    message.setJMSPriority(priority);
    
    if (_isTransacted) {
      if (_transactedMessages == null)
	_transactedMessages = new ArrayList<TransactedMessage>();

      TransactedMessage transMsg = new SendMessage(queue, message);
      
      _transactedMessages.add(transMsg);

      if (_tm != null && _transactedMessages.size() == 1) {
        try {
          Transaction trans = _tm.getTransaction();

          if (trans != null)
            trans.enlistResource(this);
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    }
    else {
      if (log.isLoggable(Level.FINE))
        log.fine(queue + " sending " + message);
      
      queue.send(this, message, timeout);
    }
  }

  /**
   * Adds a message to the session message queue.
   */
  void addTransactedReceive(AbstractDestination queue,
			    MessageImpl message)
  {
    message.setSession(this);
    
    if (_transactedMessages == null)
      _transactedMessages = new ArrayList<TransactedMessage>();

    TransactedMessage transMsg = new ReceiveMessage(queue, message);
      
    _transactedMessages.add(transMsg);

    if (_tm != null && _transactedMessages.size() == 1) {
      try {
	Transaction trans = _tm.getTransaction();

	if (trans != null)
	  trans.enlistResource(this);
      } catch (Exception e) {
	throw new RuntimeException(e);
      }
    }
  }

  /**
   * Called to synchronously receive a message.
   */
  protected Message receive(MessageConsumerImpl consumer,
			    long timeout)
    throws JMSException
  {
    throw new UnsupportedOperationException();
    /*
    checkOpen();
    
    if (Long.MAX_VALUE / 2 < timeout || timeout < 0)
      timeout = Long.MAX_VALUE / 2;
    
    long now = Alarm.getCurrentTime();
    long failTime = Alarm.getCurrentTime() + timeout;
    
    Selector selector = consumer.getSelector();
    AbstractQueue queue;
    queue = (AbstractQueue) consumer.getDestination();

    // 4.4.1 user's reponsibility
    // checkThread();

    Thread oldThread = Thread.currentThread();
    try {
      // _thread = Thread.currentThread();
      
      while (! consumer.isClosed()) {
	if (isActive()) {
	  Message msg = queue.receive(selector);
	  if (msg != null)
	    return msg;
	  _hasMessage = false;
	}
      
	long delta = failTime - Alarm.getCurrentTime();

	if (delta <= 0 || _isClosed || Alarm.isTest())
	  return null;

	synchronized (_consumers) {
	  if (! _hasMessage || ! isActive()) {
	    try {
	      _consumers.wait(delta);
	    } catch (Throwable e) {
	    }
	  }
	}
      }
    } finally {
      // _thread = oldThread;
    }

    return null;
    */
  }

  //
  // XA
  //

  public Session getSession()
  {
    return this;
  }
  
  public XAResource getXAResource()
  {
    return this;
  }
  
  /**
   * Returns true if the specified resource has the same RM.
   */
  public boolean isSameRM(XAResource xa)
    throws XAException
  {
    return this == xa;
  }
  
  /**
   * Sets the transaction timeout in seconds.
   */
  public boolean setTransactionTimeout(int timeout)
    throws XAException
  {
    return true;
  }
  
  /**
   * Gets the transaction timeout in seconds.
   */
  public int getTransactionTimeout()
    throws XAException
  {
    return 0;
  }
  
  /**
   * Called when the resource is associated with a transaction.
   */
  public void start(Xid xid, int flags)
    throws XAException
  {
  }
  
  /**
   * Called when the resource is is done with a transaction.
   */
  public void end(Xid xid, int flags)
    throws XAException
  {
  }
  
  /**
   * Called to start the first phase of the commit.
   */
  public int prepare(Xid xid)
    throws XAException
  {
    return 0;
  }
  
  /**
   * Called to commit.
   */
  public void commit(Xid xid, boolean onePhase)
    throws XAException
  {
    try {
      commit(true);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Called to roll back.
   */
  public void rollback(Xid xid)
    throws XAException
  {
    try {
      rollback();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  /**
   * Called to forget an Xid that had a heuristic commit.
   */
  public void forget(Xid xid)
    throws XAException
  {
  }
  
  /**
   * Called to find Xid's that need recovery.
   */
  public Xid[] recover(int flag)
    throws XAException
  {
    return null;
  }

  /**
   * Called to synchronously receive messages
   */
  public void run()
  {
    boolean isValid = true;

    while (isValid) {
      isValid = false;
      _hasMessage = false;
	
      try {
	for (int i = 0; i < _consumers.size(); i++) {
	  MessageConsumerImpl consumer = _consumers.get(i);

	  while (isActive() && consumer.handleMessage(_messageListener)) {
	  }
	}

	isValid = isActive();
      } finally {
	synchronized (_consumers) {
	  if (! isValid)
	    _isRunning = false;
	  else if (! _hasMessage) {
	    _isRunning = false;
	    isValid = false;
	  }

	  // notification, e.g. for shutdown
	  _consumers.notifyAll();
	}
      }
    }
  }

  public boolean isClosed()
  {
    return _isClosed;
  }

  /**
   * Checks that the session is open.
   */
  public void checkOpen()
    throws javax.jms.IllegalStateException
  {
    if (_isClosed)
      throw new javax.jms.IllegalStateException(L.l("session is closed"));
  }

  /**
   * Verifies that multiple threads aren't using the session.
   *
   * 4.4.1 the client takes the responsibility.  There's no
   * validation check.
   */
  void checkThread()
    throws JMSException
  {
    Thread thread = _thread;
    
    if (thread != Thread.currentThread() && thread != null) {
      Exception e = new IllegalStateException(L.l("Can't use session from concurrent threads."));
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  public String toString()
  {
    String className = getClass().getName();
    int p = className.lastIndexOf('.');

    return className.substring(p + 1) + "[]";
  }

  abstract class TransactedMessage {
    abstract void commit()
      throws JMSException;
    
    abstract void rollback()
      throws JMSException;
    
    void close()
      throws JMSException
    {
    }
  }

  class SendMessage extends TransactedMessage {
    private final AbstractDestination _queue;
    private final MessageImpl _message;
    
    SendMessage(AbstractDestination queue, MessageImpl message)
    {
      _queue = queue;
      _message = message;
    }

    void commit()
      throws JMSException
    {
      _queue.send(JmsSession.this, _message, 0);
    }

    void rollback()
      throws JMSException
    {
    }
    
    void close()
      throws JMSException
    {
      commit();
    }
  }

  class ReceiveMessage extends TransactedMessage {
    private final AbstractDestination _queue;
    private final MessageImpl _message;
    
    ReceiveMessage(AbstractDestination queue, MessageImpl message)
    {
      _queue = queue;
      _message = message;
    }

    void commit()
      throws JMSException
    {
      _queue.acknowledge(_message.getJMSMessageID());
    }

    void rollback()
      throws JMSException
    {
      _queue.rollback(_message.getJMSMessageID());
    }
    
    void close()
      throws JMSException
    {
      rollback();
    }
  }
}
