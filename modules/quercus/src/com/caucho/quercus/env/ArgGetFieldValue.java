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

package com.caucho.quercus.env;

/**
 * Represents an field-get argument which might be a call to a reference.
 */
public class ArgGetFieldValue extends Value {
  private final Env _env;
  private final Value _obj;
  private final StringValue _name;

  public ArgGetFieldValue(Env env, Value obj, StringValue name)
  {
    _env = env;
    _obj = obj;
    _name = name;
  }

  /**
   * Creates an argument which may create the given field.
   */
  public Value getArg(Value name)
  {
    // php/3d1q
    return new ArgGetValue(this, name);
  }

  /**
   * Creates an argument which may create the given field.
   */
  @Override
  public Value getFieldArg(Env env, StringValue name)
  {
    // php/3d2q
    return new ArgGetFieldValue(env, this, name);
  }

  /**
   * Converts to a reference variable.
   */
  @Override
  public Var toRefVar()
  {
    // php/3d2t
    return _obj.getFieldRef(_env, _name).toRefVar();
  }

  /**
   * Converts to a value.
   */
  @Override
  public Value toValue()
  {
    return _obj.getField(_env, _name);
  }

  /**
   * Converts to a read-only function argument.
   */
  @Override
  public Value toArgValueReadOnly()
  {
    return toValue();
  }

  /**
   * Converts to a function argument.
   */
  @Override
  public Value toArgValue()
  {
    return toValue();
  }

  /**
   * Converts to a reference variable.
   */
  @Override
  public Value toRefValue()
  {
    return _obj.getFieldRef(_env, _name);
  }

  /**
   * Converts to a variable.
   */
  public Var toVar()
  {
    return new Var(toValue());
  }

  /**
   * Converts to a reference variable.
   */
  @Override
  public Value getRef(Value index)
  {
    return _obj.getFieldArray(_env, _name).getRef(index);
  }

  /**
   * Converts to a reference variable.
   */
  @Override
  public Value getFieldRef(Env env, StringValue name)
  {
    // php/3d2q
    return _obj.getFieldObject(_env, _name).getFieldRef(_env, name);
  }

  public String toString()
  {
    return "Arg[" + _obj + "->" + _name + "]";
  }
}

