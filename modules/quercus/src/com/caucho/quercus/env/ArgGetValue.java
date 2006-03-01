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

package com.caucho.quercus.env;

import com.caucho.vfs.WriteStream;

import java.util.IdentityHashMap;

/**
 * Represents an array-get argument which might be a call to a reference.
 *
 * foo($a[0]), where is not known if foo is defined as foo($a) or foo(&amp;$a)
 */
public class ArgGetValue extends Value {
  private final Value _obj;
  private final Value _index;

  public ArgGetValue(Value obj, Value index)
  {
    _obj = obj;
    _index = index;
  }

  /**
   * Returns the arg object for a field reference, e.g.
   * foo($a[0][1])
   */
  public Value getArg(Value index)
  {
    return new ArgGetValue(this, index); // php/3d1p
  }

  /**
   * Returns the arg object for a field reference, e.g.
   * foo($a[0]->x)
   */
  public Value getFieldArg(Env env, String index)
  {
    return new ArgGetFieldValue(env, this, index); // php/3d2p
  }

  /**
   * Converts to a reference variable.
   */
  public Var toRefVar()
  {
    // php/3d55, php/3d49
    return _obj.getArgRef(_index).toRefVar();
  }

  /**
   * Converts to a reference variable.
   */
  public Value toRefValue()
  {
    // php/3a57
    return _obj.getArgRef(_index);
  }

  /**
   * Converts to a value.
   */
  public Value toValue()
  {
    return _obj.get(_index);
  }

  /**
   * Converts to a variable.
   */
  public Var toVar()
  {
    // quercus/3d56
    return new Var();
  }

  /**
   * Returns the reference.
   */
  public Value getArgRef(Value index)
  {
    return _obj.getArray(_index).getRef(index); // php/3d1p
  }

  /**
   * Converts to a reference variable.
   */
  public Value getFieldObject(Env env, String index)
  {
    return _obj.getObject(env, _index).getFieldObject(env, index);
  }

  /**
   * Converts to a reference variable.
   */
  public Value getFieldRef(Env env, String index)
  {
    // php/3d2p
    return _obj.getObject(env, _index).getFieldRef(env, index);
  }
}

