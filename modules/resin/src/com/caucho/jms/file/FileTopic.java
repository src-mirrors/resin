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

package com.caucho.jms.file;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.*;

import javax.jms.*;

import com.caucho.jms.message.*;
import com.caucho.jms.queue.*;
import com.caucho.jms.memory.MemoryQueue;
import com.caucho.jms.connection.*;
import com.caucho.vfs.*;

/**
 * Implements a file topic.
 */
public class FileTopic extends AbstractTopic
{
  private static final Logger log
    = Logger.getLogger(FileTopic.class.getName());

  private final FileQueueStore _store;

  private HashMap<String,AbstractQueue> _durableSubscriptionMap
    = new HashMap<String,AbstractQueue>();
    
  private ArrayList<AbstractQueue> _subscriptionList
    = new ArrayList<AbstractQueue>();

  private int _id;

  public FileTopic()
  {
    _store = new FileQueueStore(_messageFactory);
  }

  //
  // Configuration
  //

  /**
   * Sets the path to the backing database
   */
  public void setPath(Path path)
  {
    _store.setPath(path);
  }

  //
  // JMX configuration attributes
  //

  /**
   * Returns the JMS configuration url.
   */
  public String getUrl()
  {
    return "file:name=" + getName() + ";path=" + _store.getPath().getURL();
  }

  public void init()
  {
  }

  @Override
  public AbstractQueue createSubscriber(JmsSession session,
                                        String name,
                                        boolean noLocal)
  {
    AbstractQueue queue;

    if (name != null) {
      queue = _durableSubscriptionMap.get(name);

      if (queue == null) {
	queue = new FileSubscriberQueue(this, session, noLocal);
	queue.setName(getName() + ":sub-" + name);

	_subscriptionList.add(queue);
	_durableSubscriptionMap.put(name, queue);
      }

      return queue;
    }
    else {
      queue = new FileSubscriberQueue(this, session, noLocal);
      queue.setName(getName() + ":sub-" + _id++);

      _subscriptionList.add(queue);
    }

    return queue;
  }

  @Override
  public void closeSubscriber(AbstractQueue queue)
  {
    if (! _durableSubscriptionMap.values().contains(queue))
      _subscriptionList.remove(queue);
  }

  @Override
  public void send(JmsSession session, MessageImpl msg, long timeout)
    throws JMSException
  {
    for (int i = 0; i < _subscriptionList.size(); i++) {
      _subscriptionList.get(i).send(session, msg, timeout);
    }
  }

  public String toString()
  {
    return "FileTopic[" + getTopicName() + "]";
  }
}

