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

package com.caucho.quercus.env;

import java.io.IOException;

import java.util.Collection;
import java.util.IdentityHashMap;

import com.caucho.util.L10N;

import com.caucho.vfs.WriteStream;

import com.caucho.quercus.QuercusRuntimeException;

import com.caucho.quercus.program.AbstractFunction;

import com.caucho.quercus.gen.PhpWriter;

import com.caucho.quercus.expr.Expr;

/**
 * Represents a PHP expression value.
 */
abstract public class Value {
  protected static final L10N L = new L10N(Value.class);

  public static final StringValue SCALAR_V = new StringValue("scalar");

  public static final Value []NULL_ARGS = new Value[0];

  /**
   * Returns true for a long-value.
   */
  public boolean isLong()
  {
    return false;
  }

  /**
   * Returns true for a double-value.
   */
  public boolean isDouble()
  {
    return false;
  }

  /**
   * Returns true for a number.
   */
  public boolean isNumber()
  {
    return isLong() || isDouble();
  }

  /**
   * Returns true for an array.
   */
  public boolean isArray()
  {
    return false;
  }

  /**
   * Returns true for an object.
   */
  public boolean isObject()
  {
    return false;
  }

  /**
   * Returns true for a null.
   */
  public boolean isNull()
  {
    return false;
  }
  
  /**
   * Converts to a boolean.
   */
  public boolean toBoolean()
  {
    return true;
  }
  
  /**
   * Converts to a long.
   */
  public long toLong()
  {
    return toBoolean() ? 1 : 0;
  }

  /**
   * Converts to an int
   */
  public int toInt()
  {
    return (int) toLong();
  }

  /**
   * Converts to a double.
   */
  public double toDouble()
  {
    return 0;
  }

  /**
   * Converts to a char
   */
  public char toChar()
  {
    String s = toString();

    if (s == null || s.length() < 1)
      return 0;
    else
      return s.charAt(0);
  }
  
  /**
   * Converts to a string.
   *
   * @param env
   */
  public String toString(Env env)
    throws Throwable
  {
    return toString();
  }
  
  /**
   * Converts to a string value.
   * @param env
   */
  public Value toStringValue(Env env)
    throws Throwable
  {
    Value value = toValue();

    if (value instanceof StringValue)
      return value;
    else
      return new StringValue(value.toString(env));
  }
  
  /**
   * Converts to an object.
   */
  public Value toObject(Env env)
  {
    return env.createObject();
  }
  
  /**
   * Converts to a java object.
   */
  public Object toJavaObject()
  {
    return null;
  }
  
  /**
   * Converts to a raw value.
   */
  public Value toValue()
  {
    return this;
  }

  /**
   * Converts to a key.
   */
  public Value toKey()
  {
    throw new QuercusRuntimeException(L.l("{0} is not a valid key", this));
  }
  
  /**
   * Copy for assignment.
   */
  public Value copy()
  {
    return this;
  }
  
  /**
   * Copy as an array item
   */
  public Value copyArrayItem()
  {
    return copy();
  }
  
  /**
   * Copy as a return value
   */
  public Value copyReturn()
  {
    return copy();
  }
  
  /**
   * Copy for serialization
   */
  public final Value copy(Env env)
  {
    return copy(env, new IdentityHashMap<Value,Value>());
  }
  
  /**
   * Copy for serialization
   */
  public Value copy(Env env, IdentityHashMap<Value,Value> map)
  {
    return this;
  }
  
  /**
   * Convert to a ref.
   */
  public Value toRef()
  {
    return this;
  }
  
  /**
   * Convert to a function argument value, e.g. for
   *
   * function foo($a)
   *
   * where $a is never assigned in the function
   */
  public Value toArgValue()
  {
    return toValue();
  }
  
  /**
   * Convert to a function argument reference value, e.g. for
   *
   * function foo(&$a)
   *
   * where $a is never assigned in the function
   */
  public Value toRefValue()
  {
    return toValue();
  }
  
  /**
   * Convert to a function argument value, e.g. for
   *
   * function foo($a)
   *
   * where $a is used as a variable in the function
   */
  public Var toVar()
  {
    return new Var(this);
  }
  
  /**
   * Convert to a function argument reference value, e.g. for
   *
   * function foo(&$a)
   *
   * where $a is used as a variable in the function
   */
  public Var toRefVar()
  {
    return new Var(this);
  }

  /**
   * Returns the type.
   */
  public String getType()
  {
    return "value";
  }

  /**
   * Returns true for a set type.
   */
  public boolean isset()
  {
    return true;
  }

  /**
   * Returns true if there are more elements.
   */
  public boolean hasCurrent()
  {
    return false;
  }

  /**
   * Returns the current key
   */
  public Value key()
  {
    return BooleanValue.FALSE;
  }

  /**
   * Returns the current value
   */
  public Value current()
  {
    return BooleanValue.FALSE;
  }

  /**
   * Returns the current value
   */
  public Value next()
  {
    return BooleanValue.FALSE;
  }

  /**
   * Finds the method name.
   */
  public AbstractFunction findFunction(String methodName)
  {
    return null;
  }

  /**
   * Evaluates a method.
   */
  public Value evalMethod(Env env, String methodName, Expr []args)
    throws Throwable
  {
    Value []value = new Value[args.length];

    for (int i = 0; i < args.length; i++) {
      value[i] = args[i].eval(env);
    }

    return evalMethod(env, methodName, value);
  }

  /**
   * Evaluates a method.
   */
  public Value evalMethod(Env env, String methodName, Value []args)
    throws Throwable
  {
    switch (args.length) {
    case 0:
      return evalMethod(env, methodName);
      
    case 1:
      return evalMethod(env, methodName, args[0]);
      
    case 2:
      return evalMethod(env, methodName, args[0], args[1]);
      
    case 3:
      return evalMethod(env, methodName, args[0], args[1], args[2]);
      
    case 4:
      return evalMethod(env, methodName, args[0], args[1], args[2],
			args[3]);
      
    case 5:
      return evalMethod(env, methodName, args[0], args[1], args[2],
			args[3], args[4]);
      
    default:
      return errorNoMethod(env, methodName);
    }
  }

  /**
   * Evaluates a method with 0 args.
   */
  public Value evalMethod(Env env, String methodName)
    throws Throwable
  {
    return errorNoMethod(env, methodName);
  }

  /**
   * Evaluates a method with 1 arg.
   */
  public Value evalMethod(Env env, String methodName, Value a0)
    throws Throwable
  {
    return errorNoMethod(env, methodName);
  }

  /**
   * Evaluates a method with 1 arg.
   */
  public Value evalMethod(Env env, String methodName, Value a0, Value a1)
    throws Throwable
  {
    return errorNoMethod(env, methodName);
  }

  /**
   * Evaluates a method with 3 args.
   */
  public Value evalMethod(Env env, String methodName,
			  Value a0, Value a1, Value a2)
    throws Throwable
  {
    return errorNoMethod(env, methodName);
  }

  /**
   * Evaluates a method with 4 args.
   */
  public Value evalMethod(Env env, String methodName,
			  Value a0, Value a1, Value a2, Value a3)
    throws Throwable
  {
    return errorNoMethod(env, methodName);
  }

  /**
   * Evaluates a method with 5 args.
   */
  public Value evalMethod(Env env, String methodName,
			  Value a0, Value a1, Value a2, Value a3, Value a5)
    throws Throwable
  {
    return errorNoMethod(env, methodName);
  }

  private Value errorNoMethod(Env env, String methodName)
  {
    return env.error(L.l("{0}: '{1}' is an unknown method.",
			 toString(), methodName));
  }

  /**
   * Evaluates a method.
   */
  public Value evalMethodRef(Env env, String methodName, Expr []args)
    throws Throwable
  {
    Value []value = new Value[args.length];

    for (int i = 0; i < args.length; i++) {
      value[i] = args[i].eval(env);
    }

    return evalMethodRef(env, methodName, value);
  }

  /**
   * Evaluates a method.
   */
  public Value evalMethodRef(Env env, String methodName, Value []args)
    throws Throwable
  {
    switch (args.length) {
    case 0:
      return evalMethodRef(env, methodName);
      
    case 1:
      return evalMethodRef(env, methodName, args[0]);
      
    case 2:
      return evalMethodRef(env, methodName, args[0], args[1]);
      
    case 3:
      return evalMethodRef(env, methodName, args[0], args[1], args[2]);
      
    case 4:
      return evalMethodRef(env, methodName, args[0], args[1], args[2],
			args[3]);
      
    case 5:
      return evalMethodRef(env, methodName, args[0], args[1], args[2],
			args[3], args[4]);
      
    default:
      return env.error(L.l("'{0}' is an unknown method in '{1}'.",
			   methodName, this));
    }
  }

  /**
   * Evaluates a method with 0 args.
   */
  public Value evalMethodRef(Env env, String methodName)
    throws Throwable
  {
    return env.error(L.l("'{0}' is an unknown method in  {1}.",
			 methodName, this));
  }

  /**
   * Evaluates a method with 1 arg.
   */
  public Value evalMethodRef(Env env, String methodName, Value a0)
    throws Throwable
  {
    return env.error(L.l("'{0}' is an unknown method.", methodName));
  }

  /**
   * Evaluates a method with 1 arg.
   */
  public Value evalMethodRef(Env env, String methodName, Value a0, Value a1)
    throws Throwable
  {
    return env.error(L.l("{0}: '{1}' is an unknown method.",
			 toString(), methodName));
  }

  /**
   * Evaluates a method with 3 args.
   */
  public Value evalMethodRef(Env env, String methodName,
			  Value a0, Value a1, Value a2)
    throws Throwable
  {
    return env.error(L.l("{0}: '{1}' is an unknown method.",
			 toString(), methodName));
  }

  /**
   * Evaluates a method with 4 args.
   */
  public Value evalMethodRef(Env env, String methodName,
			  Value a0, Value a1, Value a2, Value a3)
    throws Throwable
  {
    return env.error(L.l("{0}: '{1}' is an unknown method.",
			 toString(), methodName));
  }

  /**
   * Evaluates a method with 5 args.
   */
  public Value evalMethodRef(Env env, String methodName,
			  Value a0, Value a1, Value a2, Value a3, Value a5)
    throws Throwable
  {
    return env.error(L.l("{0}: '{1}' is an unknown method.",
			 toString(), methodName));
  }

  /**
   * Evaluates a method.
   */
  public Value evalClassMethod(Env env, AbstractFunction fun, Value []args)
    throws Throwable
  {
    return NullValue.NULL;
  }
 
  /**
   * Returns true for equality
   */
  public Value eqValue(Value rValue)
    throws Throwable
  {
    return eq(rValue) ? BooleanValue.TRUE : BooleanValue.FALSE;
  }

  /**
   * Returns true for equality
   */
  public boolean eq(Value rValue)
  {
    if (isLong() && rValue.isLong())
      return toLong() == rValue.toLong();
    else if (isNumber() || rValue.isNumber())
      return toDouble() == rValue.toDouble();
    else
      return toString().equals(rValue.toString());
  }

  /**
   * Returns true for equality
   */
  public boolean eql(Value rValue)
    throws Throwable
  {
    return this == rValue.toValue();
  }

  /**
   * Returns true for less than
   */
  public boolean lt(Value rValue, Env env)
    throws Throwable
  {
    if (isLong() && rValue.isLong())
      return toLong() < rValue.toLong();
    else if (isNumber() || rValue.isNumber())
      return toDouble() < rValue.toDouble();
    else
      return toString(env).compareTo(rValue.toString(env)) < 0;
  }

  /**
   * Returns true for less than or equal to
   */
  public boolean leq(Value rValue, Env env)
    throws Throwable
  {
    if (isLong() && rValue.isLong())
      return toLong() <= rValue.toLong();
    else if (isNumber() || rValue.isNumber())
      return toDouble() <= rValue.toDouble();
    else
      return toString(env).compareTo(rValue.toString(env)) <= 0;
  }

  /**
   * Returns true for greater than
   */
  public boolean gt(Value rValue, Env env)
    throws Throwable
  {
    if (isLong() && rValue.isLong())
      return toLong() > rValue.toLong();
    else if (isNumber() || rValue.isNumber())
      return toDouble() > rValue.toDouble();
    else
      return toString(env).compareTo(rValue.toString(env)) > 0;
  }

  /**
   * Returns true for greater than or equal to
   */
  public boolean geq(Value rValue, Env env)
    throws Throwable
  {
    if (isLong() && rValue.isLong())
      return toLong() >= rValue.toLong();
    else if (isNumber() || rValue.isNumber())
      return toDouble() >= rValue.toDouble();
    else
      return toString(env).compareTo(rValue.toString(env)) >= 0;
  }

  /**
   * Negates the value.
   */
  public Value neg()
    throws Throwable
  {
    return new LongValue(- toLong());
  }

  /**
   * Negates the value.
   */
  public Value pos()
    throws Throwable
  {
    return new LongValue(toLong());
  }

  /**
   * Adds to the following value.
   */
  public Value add(Value rValue)
    throws Throwable
  {
    long lLong = toLong();
    long rLong = rValue.toLong();
    
    return new LongValue(lLong + rLong);
  }

  /**
   * Multiplies to the following value.
   */
  public Value add(long lLong)
    throws Throwable
  {
    long rLong = toLong();
    
    return new LongValue(lLong + rLong);
  }

  /**
   * Pre-increment the following value.
   */
  public Value preincr(int incr)
    throws Throwable
  {
    long lValue = toLong();
    
    return new LongValue(lValue + incr);
  }

  /**
   * Post-increment the following value.
   */
  public Value postincr(int incr)
    throws Throwable
  {
    long lValue = toLong();
    
    return new LongValue(lValue + incr);
  }

  /**
   * Subtracts to the following value.
   */
  public Value sub(Value rValue)
    throws Throwable
  {
    long lLong = toLong();
    long rLong = rValue.toLong();
    
    return new LongValue(lLong - rLong);
  }

  /**
   * Subtracts
   */
  public Value sub(long rLong)
    throws Throwable
  {
    long lLong = toLong();
    
    return new LongValue(lLong - rLong);
  }

  /**
   * Substracts from the previous value.
   */
  public Value sub_rev(long lLong)
    throws Throwable
  {
    long rLong = toLong();
    
    return new LongValue(lLong - rLong);
  }

  /**
   * Multiplies to the following value.
   */
  public Value mul(Value rValue)
    throws Throwable
  {
    return rValue.mul(toLong());
  }

  /**
   * Multiplies to the following value.
   */
  public Value mul(long lLong)
    throws Throwable
  {
    long rLong = toLong();
    
    return new LongValue(lLong * rLong);
  }

  /**
   * Divides the following value.
   */
  public Value div(Value rValue)
    throws Throwable
  {
    double lDouble = toDouble();
    double rDouble = rValue.toDouble();
    
    return new DoubleValue(lDouble / rDouble);
  }

  /**
   * modulo the following value.
   */
  public Value mod(Value rValue)
    throws Throwable
  {
    double lDouble = toDouble();
    double rDouble = rValue.toDouble();

    return new DoubleValue(lDouble % rDouble);
  }

  /**
   * Shifts left by the value.
   */
  public Value lshift(Value rValue)
    throws Throwable
  {
    long lLong = toLong();
    long rLong = rValue.toLong();
    
    return new LongValue(lLong << rLong);
  }

  /**
   * Shifts right by the value.
   */
  public Value rshift(Value rValue)
    throws Throwable
  {
    long lLong = toLong();
    long rLong = rValue.toLong();
    
    return new LongValue(lLong >> rLong);
  }

  /**
   * Returns the array size.
   */
  public int getSize()
  {
    return 0;
  }

  /**
   * Returns the field values.
   */
  public Collection<Value> getIndices()
  {
    return new java.util.ArrayList<Value>();
  }

  /**
   * Returns the array ref.
   */
  public Value get(Value index)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the array ref.
   */
  public Value getRef(Value index)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the array ref.
   */
  public Value getArg(Value index)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the array ref for an argument.
   */
  public Value getArgRef(Value index)
  {
    return getRef(index);
  }

  /**
   * Returns the value for a field, creating an array if the field
   * is unset.
   */
  public Value getArray()
  {
    return this;
  }

  /**
   * Returns the value for a field, creating an array if the field
   * is unset.
   */
  public Value getArgArray()
  {
    return this;
  }

  /**
   * Returns the value for a field, creating an array if the field
   * is unset.
   */
  public Value getArray(Value index)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the value for a field, creating an array if the field
   * is unset.
   */
  public Value getArgArray(Value index)
  {
    return getArray(index);
  }

  /**
   * Returns the value for the variable, creating an object if the var
   * is unset.
   */
  public Value getObject(Env env)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the value for a field, creating an object if the field
   * is unset.
   */
  public Value getObject(Env env, Value index)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the value for a field, creating an object if the field
   * is unset.
   */
  public Value getArgObject(Env env)
  {
    // quercus/399l
    return getObject(env);
  }

  /**
   * Returns the value for a field, creating an object if the field
   * is unset.
   */
  public Value getArgObject(Env env, Value index)
  {
    return getObject(env, index);
  }

  /**
   * Sets the value ref.
   */
  public Value set(Value value)
  {
    return value;
  }

  /**
   * Sets the array ref.
   */
  public Value put(Value index, Value value)
  {
    return value;
  }

  /**
   * Sets the array ref.
   */
  public Value put(Value value)
  {
    return value;
  }

  /**
   * Sets the array ref.
   */
  public Value putRef()
  {
    return NullValue.NULL;
  }

  /**
   * Appends the array
   */
  public Value putArray()
  {
    ArrayValue value = new ArrayValueImpl();

    put(value);
    
    return value;
  }

  /**
   * Appends a new object
   */
  public Value putObject(Env env)
  {
    Value value = env.createObject();

    put(value);
    
    return value;
  }

  /**
   * Return unset the value.
   */
  public Value remove(Value index)
  {
    return UnsetValue.UNSET;
  }

  /**
   * Returns the character at the named index.
   */
  public Value charAt(long index)
  {
    return NullValue.NULL;
  }

  /**
   * Sets the character at the named index.
   */
  public Value setCharAt(long index, String value)
  {
    return NullValue.NULL;
  }
  
  /**
   * Prints the value.
   * @param env
   */
  public void print(Env env)
    throws Throwable
  {
    env.getOut().print(toString(env));
  }

  /**
   * Serializes the value.
   */
  public void serialize(StringBuilder sb)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  /**
   * Exports the value.
   */
  public void varExport(StringBuilder sb)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  //
  // Java generator code
  //

  /**
   * Generates code to recreate the expression.
   *
   * @param out the writer to the Java source code.
   */
  public void generate(PhpWriter out)
    throws IOException
  {
    // XXX: remove when done
    System.out.println("Generate: " + getClass().getName());
    
    out.print("com.caucho.quercus.env.NullValue.NULL");
  }
}

