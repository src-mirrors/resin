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

package com.caucho.jms.file;

import java.lang.ref.*;

import javax.jms.*;

import com.caucho.jms.message.*;

/**
 * Entry in a file queue
 */
public class FileQueueEntry
{
  private final long _id;

  FileQueueEntry _prev;
  FileQueueEntry _next;

  private long _expiresTime;

  private MessageType _type;

  private SoftReference<MessageImpl> _msg;

  // True if the message has been read, but not yet committed
  private boolean _isRead;

  public FileQueueEntry(long id, long expiresTime)
  {
    _id = id;
    _expiresTime = expiresTime;
  }

  public FileQueueEntry(long id, long expiresTime, MessageType type)
  {
    _id = id;
    _expiresTime = expiresTime;
    _type = type;
  }

  public FileQueueEntry(long id, long expiresTime, MessageImpl msg)
  {
    _id = id;
    _expiresTime = expiresTime;
    _msg = new SoftReference<MessageImpl>(msg);
  }
  
  public long getId()
  {
    return _id;
  }

  public MessageType getType()
  {
    return _type;
  }

  public void setType(MessageType type)
  {
    _type = type;
  }

  public MessageImpl getMessage()
  {
    SoftReference<MessageImpl> ref = _msg;

    if (ref != null)
      return ref.get();
    else
      return null;
  }

  public void setMessage(MessageImpl msg)
  {
    _msg = new SoftReference<MessageImpl>(msg);
  }

  public boolean isRead()
  {
    return _isRead;
  }

  public void setRead(boolean isRead)
  {
    _isRead = isRead;
  }
}

