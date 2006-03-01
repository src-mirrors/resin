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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.bytecode;

import java.io.*;

import java.util.*;

import java.util.logging.Logger;
import java.util.logging.Level;

import com.caucho.log.Log;

/**
 * Represents a constant pool entry.
 */
abstract public class ConstantPoolEntry {
  static private final Logger log = Log.open(ConstantPoolEntry.class);

  private ConstantPool _pool;
  private int _index;

  ConstantPoolEntry(ConstantPool pool, int index)
  {
    _pool = pool;
    _index = index;
  }

  /**
   * Returns the constant pool.
   */
  public ConstantPool getConstantPool()
  {
    return _pool;
  }

  /**
   * Returns the index.
   */
  public int getIndex()
  {
    return _index;
  }

  /**
   * Writes the contents of the pool.
   */
  abstract void write(ByteCodeWriter out)
    throws IOException;

  /**
   * Exports to the target pool.
   *
   * @return the index in the target pool.
   */
  abstract int export(ConstantPool target);
}
