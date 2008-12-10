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

package com.caucho.quercus.program;

import com.caucho.quercus.env.*;
import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.function.AbstractFunction;
import com.caucho.quercus.Location;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents an interpreted PHP class definition.
 */
public class InterpretedClassDef extends ClassDef
  implements InstanceInitializer
{
  protected boolean _isAbstract;
  protected boolean _isInterface;
  protected boolean _isFinal;
  
  protected boolean _hasNonPublicMethods;
  
  protected final HashMap<String,AbstractFunction> _functionMap
    = new HashMap<String,AbstractFunction>();

  protected final HashMap<StringValue,FieldEntry> _fieldMap
    = new LinkedHashMap<StringValue,FieldEntry>();

  protected final HashMap<String,Expr> _staticFieldMap
    = new LinkedHashMap<String,Expr>();

  protected final HashMap<String,Expr> _constMap
    = new HashMap<String,Expr>();

  protected AbstractFunction _constructor;
  protected AbstractFunction _destructor;
  protected AbstractFunction _getField;
  protected AbstractFunction _setField;
  protected AbstractFunction _call;
  
  // the order that this class was parsed
  protected int _index;
  
  public InterpretedClassDef(Location location,
                             String name,
                             String parentName,
                             String []ifaceList,
                             int index)
  {
    super(location, name, parentName, ifaceList);
    
    _index = index;
  }

  public InterpretedClassDef(String name,
                             String parentName,
                             String []ifaceList)
  {
    this(null, name, parentName, ifaceList, 0);
  }

  /**
   * true for an abstract class.
   */
  public void setAbstract(boolean isAbstract)
  {
    _isAbstract = isAbstract;
  }

  /**
   * True for an abstract class.
   */
  public boolean isAbstract()
  {
    return _isAbstract;
  }

  /**
   * true for an interface class.
   */
  public void setInterface(boolean isInterface)
  {
    _isInterface = isInterface;
  }

  /**
   * True for an interface class.
   */
  public boolean isInterface()
  {
    return _isInterface;
  }
  
  /*
   * True for a final class.
   */
  public void setFinal(boolean isFinal)
  {
    _isFinal = isFinal;
  }
  
  /*
   * Returns true for a final class.
   */
  public boolean isFinal()
  {
    return _isFinal;
  }
  
  /*
   * Returns true if class has protected or private methods.
   */
  public boolean getHasNonPublicMethods()
  {
    return _hasNonPublicMethods;
  }
  
  /*
   * Unique name to use for compilation.
   */
  public String getCompilationName()
  {
    return getName() + "_" + _index;
  }

  /**
   * Initialize the quercus class.
   */
  public void initClass(QuercusClass cl)
  {
    if (_constructor != null) {
      cl.setConstructor(_constructor);

      cl.addMethod("__construct", _constructor);
    }
    
    if (_destructor != null) {
      cl.setDestructor(_destructor);
      cl.addMethod("__destruct", _destructor);
    }
    
    if (_getField != null)
      cl.setFieldGet(_getField);
    
    if (_setField != null)
      cl.setFieldSet(_setField);
    
    if (_call != null)
      cl.setCall(_call);
    
    cl.addInitializer(this);
    
    for (Map.Entry<String,AbstractFunction> entry : _functionMap.entrySet()) {
      cl.addMethod(entry.getKey(), entry.getValue());
    }
    
    for (Map.Entry<StringValue,FieldEntry> entry : _fieldMap.entrySet()) {
      FieldEntry fieldEntry = entry.getValue();
      
      cl.addField(entry.getKey(), 0, fieldEntry.getValue(),
		  fieldEntry.getVisibility());
    }

    String className = getName();
    for (Map.Entry<String,Expr> entry : _staticFieldMap.entrySet()) {
      cl.addStaticFieldExpr(className, entry.getKey(), entry.getValue());
    }

    for (Map.Entry<String,Expr> entry : _constMap.entrySet()) {
      cl.addConstant(entry.getKey(), entry.getValue());
    }
  }

  /**
   * Sets the constructor.
   */
  public void setConstructor(AbstractFunction fun)
  {
    _constructor = fun;
  }
  
  /**
   * Adds a function.
   */
  public void addFunction(String name, Function fun)
  {
    _functionMap.put(name.intern(), fun);
    
    if (! fun.isPublic()) {
      _hasNonPublicMethods = true;
    }

    if (name.equals("__construct"))
      _constructor = fun;
    else if (name.equals("__destruct"))
      _destructor = fun;
    else if (name.equals("__get"))
      _getField = fun;
    else if (name.equals("__set"))
      _setField = fun;
    else if (name.equals("__call"))
      _call = fun;
    else if (name.equalsIgnoreCase(getName()) && _constructor == null)
      _constructor = fun;
  }

  /**
   * Adds a static value.
   */
  public void addStaticValue(Value name, Expr value)
  {
    _staticFieldMap.put(name.toString(), value);
  }

  /**
   * Adds a const value.
   */
  public void addConstant(String name, Expr value)
  {
    _constMap.put(name.intern(), value);
  }

  /**
   * Return a const value.
   */
  public Expr findConstant(String name)
  {
    return _constMap.get(name);
  }

  /**
   * Adds a value.
   */
  public void addValue(Value name, Expr value, FieldVisibility visibility)
  {
    _fieldMap.put(name.toStringValue(), new FieldEntry(value, visibility));
  }

  /**
   * Adds a value.
   */
  public Expr get(Value name)
  {
    FieldEntry entry = _fieldMap.get(name.toStringValue());

    if (entry != null)
      return entry.getValue();
    else
      return null;
  }

  /**
   * Return true for a declared field.
   */
  public boolean isDeclaredField(StringValue name)
  {
    return _fieldMap.get(name) != null;
  }

  /**
   * Initialize the class.
   */
  public void init(Env env)
  {
    for (Map.Entry<String,Expr> var : _staticFieldMap.entrySet()) {
      String name = getName() + "::" + var.getKey();

      env.setGlobalValue(name.intern(), var.getValue().eval(env).copy());
    }
  }

  /**
   * Initialize the fields
   */
  public void initInstance(Env env, Value value)
  {
    ObjectValue object = (ObjectValue) value;
    
    for (Map.Entry<StringValue,FieldEntry> entry : _fieldMap.entrySet()) {
      FieldEntry fieldEntry = entry.getValue();

      object.initField(entry.getKey(),
		       fieldEntry.getValue().eval(env).copy(),
		       fieldEntry.getVisibility());
    }

    if (_destructor != null && value instanceof ObjectExtValue)
      env.addObjectCleanup((ObjectExtValue) object);
  }

  /**
   * Returns the constructor
   */
  public AbstractFunction findConstructor()
  {
    return _constructor;
  }

  public Set<Map.Entry<StringValue, FieldEntry>> fieldSet()
  {
    return _fieldMap.entrySet();
  }
  
  public Set<Map.Entry<String, AbstractFunction>> functionSet()
  {
    return _functionMap.entrySet();
  }
}

