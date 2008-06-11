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

package com.caucho.hmtp;

import com.caucho.bam.BamStream;
import java.io.Serializable;

/**
 * Announces presence information
 */
public class Presence extends Packet {
  private final Serializable _data;

  /**
   * zero-arg constructor for Hessian
   */
  protected Presence()
  {
    _data = null;
  }

  /**
   * An undirected presence announcement to the server.
   *
   * @param data a collection of presence data
   */
  public Presence(Serializable data)
  {
    _data = data;
  }

  /**
   * A directed presence announcement to another client
   *
   * @param to the target client
   * @param data a collection of presence data
   */
  public Presence(String to, Serializable data)
  {
    super(to);
    
    _data = data;
  }

  /**
   * A directed presence announcement to another client
   *
   * @param to the target client
   * @param from the source
   * @param data a collection of presence data
   */
  public Presence(String to, String from, Serializable data)
  {
    super(to, from);
    
    _data = data;
  }

  /**
   * Returns the presence data
   */
  public Serializable getData()
  {
    return _data;
  }

  /**
   * SPI method to dispatch the packet to the proper handler
   */
  @Override
  public void dispatch(BamStream handler, BamStream toSource)
  {
    handler.presence(getTo(), getFrom(), _data);
  }

  @Override
  public String toString()
  {
    StringBuilder sb = new StringBuilder();

    sb.append(getClass().getSimpleName());
    sb.append("[");
    
    sb.append("to=");
    sb.append(getTo());
    
    if (getFrom() != null) {
      sb.append(",from=");
      sb.append(getFrom());
    }

    if (_data != null) {
      sb.append(",data=");
      sb.append(_data);
    }
    
    sb.append("]");
    
    return sb.toString();
  }
}
