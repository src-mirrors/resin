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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.el;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.beans.*;
import java.lang.reflect.*;

import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.el.ELException;

import com.caucho.vfs.*;
import com.caucho.util.*;

/**
 * Represents an array reference:
 *
 * <pre>
 * a[b]
 * </pre>
 */
public class ArrayExpr extends Expr {
  private Expr _left;
  private Expr _right;

  // cached getter method
  private Class _lastClass;
  private String _lastField;
  private Method _lastMethod;

  /**
   * Creates a new array expression.
   *
   * @param left the object expression
   * @param right the index expression.
   */
  public ArrayExpr(Expr left, Expr right)
  {
    _left = left;
    _right = right;
  }

  /**
   * Creates a method for constant arrays.
   */
  public Expr createMethod(Expr []args)
  {
    if (! (_right instanceof StringLiteral))
      return null;

    StringLiteral literal = (StringLiteral) _right;

    return new MethodExpr(_left, literal.getValue(), args);
  }
  
  /**
   * Evaluate the expression as an object.
   *
   * @param env the variable environment
   *
   * @return the evaluated object
   */
  public Object evalObject(VariableResolver env)
    throws ELException
  {
    Object aObj = _left.evalObject(env);

    if (aObj == null)
      return null;

    Object fieldObj = _right.evalObject(env);
    if (fieldObj == null)
      return null;

    if (aObj instanceof Map) {
      return ((Map) aObj).get(fieldObj);
    }
    
    if (aObj instanceof List) {
      int ref = (int) toLong(fieldObj, env);

      try {
	List list = (List) aObj;

	if (ref < 0 || list.size() < ref)
	  return null;
	else
	  return list.get(ref);
      } catch (IndexOutOfBoundsException e) {
      } catch (Exception e) {
        return invocationError(e);
      }
    }

    Class aClass = aObj.getClass();
    
    if (aClass.isArray()) {
      int ref = (int) toLong(fieldObj, env);

      try {
        return Array.get(aObj, ref);
      } catch (IndexOutOfBoundsException e) {
      } catch (Exception e) {
        return error(e, env);
      }
    }

    String fieldName = toString(fieldObj, env);

    Method getMethod = null;
    try {
      synchronized (this) {
        if (_lastClass == aClass && _lastField.equals(fieldName))
          getMethod = _lastMethod;
        else {
	  // XXX: the Introspection is a memory hog
          // BeanInfo info = Introspector.getBeanInfo(aClass);
          getMethod = BeanUtil.getGetMethod(aClass, fieldName);
          _lastClass = aClass;
          _lastField = fieldName;
          _lastMethod = getMethod;
        }
      }

      if (getMethod != null)
        return getMethod.invoke(aObj, (Object []) null);
    } catch (Exception e) {
      return invocationError(e);
    }

    try {
      getMethod = aClass.getMethod("get", new Class[] { String.class });

      if (getMethod != null)
        return getMethod.invoke(aObj, new Object[] {fieldName});
    } catch (NoSuchMethodException e) {
      return null;
    } catch (Exception e) {
      return invocationError(e);
    }

    try {
      getMethod = aClass.getMethod("get", new Class[] { Object.class });

      if (getMethod != null)
        return getMethod.invoke(aObj, new Object[] {fieldObj});
    } catch (Exception e) {
      return invocationError(e);
    }

    ELException e = new ELException(L.l("no get method {0} for class {1}",
                                        fieldName, aClass.getName()));

    error(e, env);
    
    return null;
  }

  /**
   * Prints the code to create an LongLiteral.
   *
   * @param os stream to the generated *.java code
   */
  public void printCreate(WriteStream os)
    throws IOException
  {
    os.print("new com.caucho.el.ArrayExpr(");
    _left.printCreate(os);
    os.print(", ");
    _right.printCreate(os);
    os.print(")");
  }

  /**
   * Returns true for equal strings.
   */
  public boolean equals(Object o)
  {
    if (! (o instanceof ArrayExpr))
      return false;

    ArrayExpr expr = (ArrayExpr) o;

    return (_left.equals(expr._left) && _right.equals(expr._right));
  }

  /**
   * Returns a readable representation of the expr.
   */
  public String toString()
  {
    return _left + "[" + _right + "]";
  }
}
