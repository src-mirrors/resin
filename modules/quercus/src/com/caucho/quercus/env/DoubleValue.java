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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.IdentityHashMap;

/**
 * Represents a PHP double value.
 */
public class DoubleValue extends NumberValue
  implements Serializable
{
  public static final DoubleValue ZERO = new DoubleValue(0);

  private final double _value;

  public DoubleValue(double value)
  {
    _value = value;
  }

  public static DoubleValue create(double value)
  {
    return new DoubleValue(value);
  }

  public static Value create(Number value)
  {
    if (value == null) {
      // php/3c2d
      return NullValue.NULL;
    }
    else
      return new DoubleValue(value.doubleValue());
  }

  /**
   * Returns the type.
   */
  public String getType()
  {
    return "float";
  }

  /**
   * Returns true for a double.
   */
  public boolean isDoubleConvertible()
  {
    return true;
  }

  /**
   * Returns true for integer looking doubles.
   */
  public boolean isLongConvertible()
  {
    return _value == (double)((long)_value);
  }

  /**
   * Returns true for is_numeric
   */
  @Override
  public boolean isNumeric()
  {
    return true;
  }

  /**
   * Returns true for a scalar
   */
  public boolean isScalar()
  {
    return true;
  }

  /**
   * Converts to a boolean.
   */
  public boolean toBoolean()
  {
    return _value != 0;
  }

  /**
   * Converts to a long.
   */
  public long toLong()
  {
    return (long) _value;
  }

  /**
   * Converts to a double.
   */
  public double toDouble()
  {
    return _value;
  }

  /**
   * Converts to a double.
   */
  public DoubleValue toDoubleValue()
  {
    return this;
  }

  /**
   * Converts to a key.
   */
  public Value toKey()
  {
    return LongValue.create((long) _value);
  }

  /**
   * Converts to a java object.
   */
  public Object toJavaObject()
  {
    return new Double(_value);
  }

  /**
   * Negates the value.
   */
  public Value neg()
  {
    return new DoubleValue(- _value);
  }

  /**
   * Returns the value
   */
  public Value pos()
  {
    return this;
  }

  /**
   * Multiplies to the following value.
   */
  public Value add(Value rValue)
  {
    return new DoubleValue(_value + rValue.toDouble());
  }

  /**
   * Multiplies to the following value.
   */
  public Value add(long lValue)
  {
    return new DoubleValue(lValue + _value);
  }

  /**
   * Pre-increment the following value.
   */
  public Value preincr(int incr)
  {
    return new DoubleValue(_value + incr);
  }

  /**
   * Post-increment the following value.
   */
  public Value postincr(int incr)
  {
    return new DoubleValue(_value + incr);
  }

  /**
   * Multiplies to the following value.
   */
  public Value mul(Value rValue)
  {
    return new DoubleValue(_value * rValue.toDouble());
  }

  /**
   * Multiplies to the following value.
   */
  public Value mul(long lValue)
  {
    return new DoubleValue(lValue * _value);
  }

  /**
   * Returns true for equality
   */
  public boolean eql(Value rValue)
  {
    rValue = rValue.toValue();

    if (! (rValue instanceof DoubleValue))
      return false;

    double rDouble = ((DoubleValue) rValue)._value;

    return _value == rDouble;
  }

  /**
   * Converts to a string.
   * @param env
   */
  public String toString()
  {
    long longValue = (long) _value;

    if (longValue == _value)
      return String.valueOf(longValue);
    else
      return String.valueOf(_value);
  }

  /**
   * Converts to an object.
   */
  public Object toObject()
  {
    return toString();
  }

  /**
   * Prints the value.
   * @param env
   */
  public void print(Env env)
  {
    env.print(toString());
  }

  /**
   * Append to a string builder.
   */
  @Override
  public void appendTo(UnicodeBuilderValue sb)
  {
    sb.append(toString());
  }

  /**
   * Append to a binary builder.
   */
  @Override
  public void appendTo(BytesBuilderValue sb)
  {
    sb.append(toString());
  }

  /**
   * Serializes the value.
   */
  public void serialize(StringBuilder sb)
  {
    sb.append("d:");
    sb.append(_value);
    sb.append(";");
  }

  /**
   * Exports the value.
   */
  public void varExport(StringBuilder sb)
  {
    sb.append(toString());
  }

  //
  // Java generator code
  //

  /**
   * Generates code to recreate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PrintWriter out)
    throws IOException
  {
    if (_value == 0)
      out.print("DoubleValue.ZERO");
    else
      out.print("new DoubleValue(" + _value + ")");
  }

  /**
   * Returns the hash code
   */
  public int hashCode()
  {
    return (int) (37 + 65521 * _value);
  }

  /**
   * Compare for equality.
   */
  public boolean equals(Object o)
  {
    if (this == o)
      return true;
    else if (! (o instanceof DoubleValue))
      return false;

    DoubleValue value = (DoubleValue) o;

    return _value == value._value;
  }

  public void varDumpImpl(Env env,
                          WriteStream out,
                          int depth,
                          IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    out.print("float(" + toString() + ")");
  }
  
  //
  // Java Serialization
  //
  
  private Object readResolve()
  { 
    if (_value == 0)
      return ZERO;
    else
      return this;
  }

}

