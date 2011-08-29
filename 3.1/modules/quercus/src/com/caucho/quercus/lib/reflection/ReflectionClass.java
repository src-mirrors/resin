/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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
 * @author Nam Nguyen
 */

package com.caucho.quercus.lib.reflection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.caucho.quercus.UnimplementedException;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.ReturnNullAsFalse;
import com.caucho.quercus.env.ArrayValue;
import com.caucho.quercus.env.ArrayValueImpl;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.MethodMap;
import com.caucho.quercus.env.ObjectValue;
import com.caucho.quercus.env.QuercusClass;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.Var;
import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.program.AbstractFunction;
import com.caucho.quercus.program.ClassDef;
import com.caucho.util.L10N;

public class ReflectionClass
  implements Reflector
{
  private static final L10N L = new L10N(ReflectionClass.class);
  
  public static int IS_IMPLICIT_ABSTRACT = 16;
  public static int IS_EXPLICIT_ABSTRACT = 32;
  public static int IS_FINAL = 64;
  
  public String _name;
  private QuercusClass _cls;
  
  protected ReflectionClass(QuercusClass cls)
  {
    _cls = cls;
    _name = cls.getName();
  }
  
  protected ReflectionClass(Env env, String name)
  {
    _cls = env.findClass(name);
    _name = name;
  }
  
  protected QuercusClass getQuercusClass()
  {
    return _cls;
  }
  
  final private ReflectionClass __clone()
  {
    return new ReflectionClass(_cls);
  }
  
  public static ReflectionClass __construct(Env env, Value obj)
  {
    QuercusClass cls;
    
    if (obj.isObject())
      cls = ((ObjectValue) obj).getQuercusClass();
    else
      cls = env.findClass(obj.toString());

    return new ReflectionClass(cls);
  }
  
  public String __toString()
  {
    return null;
  }
  
  public static String export(Env env,
                              Value cls,
                              @Optional boolean isReturn)
  {
    return null;
  }
  
  public String getName()
  {
    return _name;
  }
  
  public boolean isInternal()
  {
    throw new UnimplementedException("ReflectionClass->isInternal()");
  }
  
  public boolean isUserDefined()
  {
    throw new UnimplementedException("ReflectionClass->isUserDefined()");
  }
  
  public boolean isInstantiable()
  {
    return ! _cls.isInterface();
  }
  
  public boolean hasConstant(String name)
  {
    return _cls.hasConstant(name);
  }

  public String getFileName()
  {
    return null;
  }
  
  public int getStartLine()
  {
    return -1;
  }
  
  public int getEndLine()
  {
    return -1;
  }
  
  public String getDocComment()
  {
    return null;
  }
  
  public ReflectionMethod getConstructor()
  {
    AbstractFunction cons = _cls.getConstructor();
    
    if (cons != null)
      return new ReflectionMethod(_name, cons);
    else
      return null;
  }
  
  public boolean hasMethod(String name)
  {
    MethodMap<AbstractFunction> map = _cls.getMethodMap();
    
    return map.get(name) != null;
  }
  
  public ReflectionMethod getMethod(String name)
  {
    return new ReflectionMethod(_name, _cls.getFunction(name));
    
    /*
    MethodMap<AbstractFunction> map = _cls.getMethodMap();
    
    return new ReflectionMethod(_name, map.get(name));
    */
  }
  
  public ArrayValue getMethods(Env env)
  {
    ArrayValue array = new ArrayValueImpl();
    
    MethodMap<AbstractFunction> map = _cls.getMethodMap();
    
    for (AbstractFunction method : map.values()) {
      array.put(env.wrapJava(new ReflectionMethod(method)));
    }
    
    return array;
  }
  
  public boolean hasProperty(StringValue name)
  {
    return _cls.findFieldIndex(name) >= 0;
  }
  
  public ReflectionProperty getProperty(Env env, StringValue name)
  {
    return new ReflectionProperty(env, _cls, name);
  }
  
  public ArrayValue getProperties(Env env)
  {
    ArrayValue array = new ArrayValueImpl();
    
    ArrayList<StringValue> list = _cls.getFieldNames();
    
    int size = list.size();
    
    for (int i = 0; i < size; i++) {
      array.put(env.wrapJava(new ReflectionProperty(env, _cls, list.get(i))));
    }
    
    return array;
  }
  
  public ArrayValue getConstants(Env env)
  {
    ArrayValue array = new ArrayValueImpl();
    
    HashMap<String, Expr> _constMap = _cls.getConstantMap();
    
    for (Map.Entry<String, Expr> entry : _constMap.entrySet()) {
      Value name = StringValue.create(entry.getKey());
      
      array.put(name, entry.getValue().eval(env));
    }

    return array;
  }
  
  public Value getConstant(Env env, String name)
  {
    if (hasConstant(name))
      return _cls.getConstant(env, name);
    else
      return BooleanValue.FALSE;
  }
  
  public ArrayValue getInterfaces(Env env)
  {
    ArrayValue array = new ArrayValueImpl();
    
    findInterfaces(env, array, _cls);
    
    return array;
  }

  private void findInterfaces(Env env, ArrayValue array, QuercusClass cls)
  {
    if (cls.isInterface()) {
      array.put(StringValue.create(cls.getName()),
                env.wrapJava(new ReflectionClass(cls)));
    }
    else {
      ClassDef []defList = cls.getClassDefList();
      
      for (int i = 0; i < defList.length; i++) {
        findInterfaces(env, array, defList[i]);
      }
    }
  }
  
  private void findInterfaces(Env env, ArrayValue array, ClassDef def)
  {
    String name = def.getName();
    
    if (def.isInterface()) {
      addInterface(env, array, name);
    }
    else {
      String []defList = def.getInterfaces();
      
      for (int i = 0; i < defList.length; i++) {
        QuercusClass cls = env.findClass(defList[i]);
        
        findInterfaces(env, array, cls);
      }
    }
  }
  
  private void addInterface(Env env, ArrayValue array, String name)
  {
    QuercusClass cls = env.findClass(name);

    array.put(StringValue.create(name),
              env.wrapJava(new ReflectionClass(cls)));
  }
  
  public boolean isInterface()
  {
    return _cls.isInterface();
  }
  
  public boolean isAbstract()
  {
    return _cls.isAbstract();
  }
  
  public boolean isFinal()
  {
    return _cls.isFinal();
  }
  
  public int getModifiers()
  {
    int flag = 0;
    
    if (isFinal())
      flag |= IS_FINAL;
    
    return flag;
  }
  
  public boolean isInstance(ObjectValue obj)
  {
    return obj.getQuercusClass().getName().equals(_name);
  }
  
  public Value newInstance(Env env, @Optional Value []args)
  {
    return _cls.callNew(env, args);
  }
  
  public Value newInstanceArgs(Env env, @Optional ArrayValue args)
  {
    if (args == null)
      return _cls.callNew(env, new Value []{});
    else
      return _cls.callNew(env, args.getValueArray(env));
  }
  
  @ReturnNullAsFalse
  public ReflectionClass getParentClass()
  {
    QuercusClass parent = _cls.getParent();
    
    if (parent == null)
      return null;
    else
      return new ReflectionClass(parent);
  }
  
  public boolean isSubclassOf(ReflectionClass cls)
  {
    // php/520p
    if (_cls.getName().equals(cls.getName()))
      return false;
    
    return _cls.isA(cls.getName());
  }
  
  public ArrayValue getStaticProperties(Env env)
  {
    ArrayValue array = new ArrayValueImpl();
    
    addStaticFields(env, array, _cls);
    
    return array;
  }
  
  private void addStaticFields(Env env, ArrayValue array, QuercusClass cls)
  {
    if (cls == null)
      return;
    
    HashMap<String, Value> fieldMap = cls.getStaticFieldMap();
    
    for (Map.Entry<String, Value> entry : fieldMap.entrySet()) {
      String name = entry.getKey();
      
      Var field = cls.getStaticField(env, name);

      array.put(StringValue.create(name), field.toValue());
    }
    
    addStaticFields(env, array, cls.getParent());
  }
  
  public Value getStaticPropertyValue(Env env,
                                      String name,
                                      @Optional Value defaultV)
  {
    Var field = _cls.getStaticField(env, name);
    
    if (field == null) {
      if (! defaultV.isDefault())
        return defaultV;
      else {
        throw new ReflectionException(L.l("Class '{0}' does not have property named '{1}'", _name, name));
      }
    }

    return field.toValue();
  }
  
  public void setStaticPropertyValue(Env env, String name, Value value)
  {
    _cls.getStaticField(env, name).set(value);
  }
  
  public ArrayValue getDefaultProperties(Env env)
  {
    ArrayValue array = new ArrayValueImpl();
    
    addStaticFields(env, array, _cls);
    
    HashMap<StringValue, Expr> fieldMap = _cls.getClassVars();
    
    for (Map.Entry<StringValue, Expr> entry : fieldMap.entrySet()) {
      array.put(entry.getKey(), entry.getValue().eval(env));
    }
    
    return array;
  }
  
  public boolean isIterateable()
  {
    return _cls.getTraversableDelegate() != null;
  }
  
  public boolean implementsInterface(Env env, String name)
  {
    return _cls.implementsInterface(env, name);
  }
  
  public ReflectionExtension getExtension(Env env)
  {
    String extName = getExtensionName();
    
    if (extName != null)
      return new ReflectionExtension(env, extName);
    else
      return null;
  }
  
  public String getExtensionName()
  {
    return _cls.getExtension();
  }
  
  public String toString()
  {
    return "ReflectionClass[" + _name + "]";
  }
}
