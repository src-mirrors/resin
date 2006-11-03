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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.util;

/**
 * Stub for java.util.concurrent.TimeUnit.
 */
public class TimeUnit {
  public static final TimeUnit NANOSECONDS = new TimeUnit(1L);
  public static final TimeUnit MICROSECONDS = new TimeUnit(1000L);
  public static final TimeUnit MILLISECONDS = new TimeUnit(1000000L);
  public static final TimeUnit SECONDS = new TimeUnit(1000000000L);

  private final long _nanos;

  private TimeUnit(long nanos)
  {
    _nanos = nanos;
  }

  public long convert(long duration, TimeUnit unit)
  {
    long sourceNanos = unit._nanos;

    if (sourceNanos <= _nanos)
      return duration * (_nanos / sourceNanos);
    else
      return duration / (sourceNanos / _nanos);
  }

  public long toMillis(long duration)
  {
    long msNanos = 1000000L;

    if (msNanos <= _nanos)
      return duration * (_nanos / msNanos);
    else
      return duration / (msNanos / _nanos);
  }
}
