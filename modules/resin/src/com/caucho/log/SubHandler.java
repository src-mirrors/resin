/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

package com.caucho.log;

import java.io.IOException;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import com.caucho.vfs.WriteStream;

import com.caucho.util.L10N;

import com.caucho.config.ConfigException;

import com.caucho.loader.Environment;
import com.caucho.loader.EnvironmentLocal;

/**
 * Proxy for an underlying handler, e.g. to handle different
 * logging levels.
 */
public class SubHandler extends Handler {
  private static final L10N L = new L10N(SubHandler.class);
  
  private Handler _handler;

  SubHandler(Handler handler)
  {
    _handler = handler;
  }

  /**
   * Publishes the record.
   */
  public void publish(LogRecord record)
  {
    if (record.getLevel().intValue() < getLevel().intValue())
      return;

    if (_handler != null)
      _handler.publish(record);
  }

  /**
   * Flushes the buffer.
   */
  public void flush()
  {
    if (_handler != null)
      _handler.flush();
  }

  /**
   * Closes the handler.
   */
  public void close()
  {
    _handler.close();

    _handle = null;
  }

  /**
   * Returns the hash code.
   */
  public int hashCode()
  {
    if (_handler == null)
      return super.hashCode();
    else
    return _handler.hashCode();
  }

  /**
   * Test for equality.
   */
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (o == null)
      return false;

    if (_handler == null)
      return false;
    else
      return o.equals(_handler);
  }
}
