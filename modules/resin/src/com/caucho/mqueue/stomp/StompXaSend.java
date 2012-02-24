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

import com.caucho.amqp.broker.AmqpBrokerSender;
import com.caucho.vfs.TempBuffer;

/**
 * xa item for a send
 */
class StompXaSend extends StompXaItem
{
  private AmqpBrokerSender _dest;
  
  private TempBuffer _tBuf;
  private int _length;
  
  StompXaSend(AmqpBrokerSender dest, TempBuffer tBuf, int length)
  {
    _dest = dest;
    _tBuf = tBuf;
    _length = length;
  }
  
  @Override
  boolean doCommand(StompConnection conn)
  {
    _dest.messageComplete(_tBuf, _length, null);
    
    return true;
  }
}
