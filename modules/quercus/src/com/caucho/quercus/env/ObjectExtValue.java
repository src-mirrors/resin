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

import com.caucho.quercus.Location;
import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.expr.StringLiteralExpr;
import com.caucho.quercus.program.AbstractFunction;
import com.caucho.vfs.WriteStream;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Represents a PHP object value.
 */
public class ObjectExtValue extends ObjectValue
  implements Serializable
{
  private static final StringValue TO_STRING
    = new StringBuilderValue("__toString");

  private static final int DEFAULT_SIZE = 16;

  private MethodMap<AbstractFunction> _methodMap;

  private Entry []_entries;
  private int _hashMask;

  private int _size;
  private boolean _isFieldInit;

  public ObjectExtValue(QuercusClass cl)
  {
    super(cl);

    _methodMap = cl.getMethodMap();

    _entries = new Entry[DEFAULT_SIZE];
    _hashMask = _entries.length - 1;
  }

  public ObjectExtValue(Env env, ObjectExtValue copy, CopyRoot root)
  {
    super(copy.getQuercusClass());

    _methodMap = copy._methodMap;

    _size = copy._size;
    _isFieldInit = copy._isFieldInit;
    
    Entry []copyEntries = copy._entries;
    
    _entries = new Entry[copyEntries.length];
    _hashMask = copy._hashMask;

    int len = copyEntries.length;
    for (int i = 0; i < len; i++) {
      Entry entry = copyEntries[i];

      for (; entry != null; entry = entry._next) {
	Entry entryCopy = entry.copyTree(env, root);

	entryCopy._next = _entries[i];
	if (_entries[i] != null)
	  _entries[i]._prev = entryCopy;

	_entries[i] = entryCopy;
      }
    }
  }

  private void init()
  {
    _entries = new Entry[DEFAULT_SIZE];
    _hashMask = _entries.length - 1;
  }
  
  @Override
  protected void setQuercusClass(QuercusClass cl)
  {
    super.setQuercusClass(cl);
    
    _methodMap = cl.getMethodMap();
  }

  /**
   * Returns the number of entries.
   */
  @Override
  public int getSize()
  {
    return _size;
  }

  /**
   * Gets a field value.
   */
  @Override
  public final Value getField(Env env, StringValue name)
  {
    int hash = name.hashCode() & _hashMask;

    for (Entry entry = _entries[hash];
	 entry != null;
	 entry = entry._next) {
      if (name.equals(entry._key)) {
	if (entry._visibility == FieldVisibility.PRIVATE) {
	  env.error(L.l("Can't access private field '{0}::{1}'",
			_quercusClass.getName(), name));
	}
	
        return entry._value.toValue();
      }
    }

    Value value = getFieldExt(env, name);

    if (value != null)
      return value;
    else
      return _quercusClass.getField(env, this, name);
  }

  /**
   * Gets a field value.
   */
  @Override
  public Value getThisField(Env env, StringValue name)
  {
    int hash = name.hashCode() & _hashMask;

    for (Entry entry = _entries[hash];
	 entry != null;
	 entry = entry._next) {
      if (name.equals(entry._key))
        return entry._value.toValue();
    }

    Value value = getFieldExt(env, name);

    if (value != null)
      return value;
    else
      return UnsetValue.UNSET;
  }

  /**
   * Returns fields not specified by the value.
   */
  protected Value getFieldExt(Env env, StringValue name)
  {
    return null;
  }

  /**
   * Returns the array ref.
   */
  @Override
  public Var getFieldRef(Env env, StringValue name)
  {
    Entry entry = createEntry(name, FieldVisibility.PUBLIC);

    Value value = entry._value;

    if (value instanceof Var)
      return (Var) value;

    Var var = new Var(value);

    entry.setValue(var);

    return var;
  }

  /**
   * Returns the array ref.
   */
  @Override
  public Var getThisFieldRef(Env env, StringValue name)
  {
    Entry entry = createEntry(name, FieldVisibility.PUBLIC);

    Value value = entry._value;

    if (value instanceof Var)
      return (Var) value;

    Var var = new Var(value);

    entry.setValue(var);

    return var;
  }

  /**
   * Returns the value as an argument which may be a reference.
   */
  @Override
  public Value getFieldArg(Env env, StringValue name)
  {
    Entry entry = getEntry(name);

    if (entry != null)
      return entry.toArg();
    else
      return new ArgGetFieldValue(env, this, name);
  }

  /**
   * Returns the value as an argument which may be a reference.
   */
  @Override
  public Value getThisFieldArg(Env env, StringValue name)
  {
    Entry entry = getEntry(name);

    if (entry != null)
      return entry.toArg();
    else
      return new ArgGetFieldValue(env, this, name);
  }

  /**
   * Returns the value as an argument which may be a reference.
   */
  @Override
  public Value getFieldArgRef(Env env, StringValue name)
  {
    Entry entry = getEntry(name);

    if (entry != null)
      return entry.toArg();
    else
      return new ArgGetFieldValue(env, this, name);
  }

  /**
   * Returns the value as an argument which may be a reference.
   */
  @Override
  public Value getThisFieldArgRef(Env env, StringValue name)
  {
    Entry entry = getEntry(name);

    if (entry != null)
      return entry.toArg();
    else
      return new ArgGetFieldValue(env, this, name);
  }

  /**
   * Adds a new value.
   */
  @Override
  public Value putField(Env env, StringValue name, Value value)
  {
    Entry entry = getEntry(name);

    if (entry == null) {
      Value oldValue = putFieldExt(env, name, value);

      if (oldValue != null)
        return oldValue;
      
      if (! _isFieldInit) {
        AbstractFunction fieldSet = _quercusClass.getFieldSet();

        if (fieldSet != null) {
          _isFieldInit = true;
          Value retVal = fieldSet.callMethod(env, this, name, value);
          _isFieldInit = false;
          
          return retVal;
        }
      }
    }
    
    entry = createEntry(name, FieldVisibility.PROTECTED);

    Value oldValue = entry._value;

    if (value instanceof Var) {
      Var var = (Var) value;

      // for function return optimization
      var.setReference();

      entry._value = var;
    }
    else if (oldValue instanceof Var) {
      oldValue.set(value);
    }
    else {
      entry._value = value;
    }

    return value;
  }

  /**
   * Sets/adds field to this object.
   */
  @Override
  public Value putThisField(Env env, StringValue name, Value value)
  {
    Entry entry = getEntry(name);

    if (entry == null) {
      Value oldValue = putFieldExt(env, name, value);

      if (oldValue != null)
        return oldValue;
      
      if (! _isFieldInit) {
        AbstractFunction fieldSet = _quercusClass.getFieldSet();
    
        if (fieldSet != null) {
          //php/09k7
          _isFieldInit = true;
          
          Value retVal = fieldSet.callMethod(env, this, name, value);
          
          _isFieldInit = false;
          return retVal;
        }
      }
    }
    
    entry = createEntry(name, FieldVisibility.PROTECTED);

    Value oldValue = entry._value;

    if (value instanceof Var) {
      Var var = (Var) value;

      // for function return optimization
      var.setReference();

      entry._value = var;
    }
    else if (oldValue instanceof Var) {
      oldValue.set(value);
    }
    else {
      entry._value = value;
    }

    return value;
  }
  
  protected Value putFieldExt(Env env, StringValue name, Value value)
  {
    return null;
  }

  /**
   * Adds a new value to the object.
   */
  @Override
  public void initField(StringValue key,
			Value value,
			FieldVisibility visibility)
  {
    Entry entry = createEntry(key, visibility);

    entry._value = value;
  }

  /**
   * Removes a value.
   */
  @Override
  public void unsetField(StringValue name)
  {
    int hash = name.hashCode() & _hashMask;

    for (Entry entry = _entries[hash];
	 entry != null;
	 entry = entry._next) {
      if (name.equals(entry.getKey())) {
	Entry prev = entry._prev;
	Entry next = entry._next;

	if (prev != null)
	  prev._next = next;
	else
	  _entries[hash] = next;

	if (next != null)
	  next._prev = prev;

	_size--;

        return;
      }
    }
  }

  /**
   * Gets a new value.
   */
  private Entry getEntry(StringValue name)
  {
    int hash = name.hashCode() & _hashMask;

    for (Entry entry = _entries[hash];
	 entry != null;
	 entry = entry._next) {
      if (name.equals(entry._key))
	return entry;
    }

    return null;
  }

  /**
   * Creates the entry for a key.
   */
  private Entry createEntry(StringValue name, FieldVisibility visibility)
  {
    int hash = name.hashCode() & _hashMask;

    for (Entry entry = _entries[hash];
	 entry != null;
	 entry = entry._next) {
      if (name.equals(entry._key))
        return entry;
    }
    
    _size++;

    Entry newEntry = new Entry(name, visibility);
    Entry next = _entries[hash];
    
    if (next != null) {
      newEntry._next = next;
      next._prev = newEntry;
    }

    _entries[hash] = newEntry;

    // XXX: possibly resize

    return newEntry;
  }

  //
  // array methods
  //

  /**
   * Returns the array value with the given key.
   */
  @Override
  public Value get(Value key)
  {
    ArrayDelegate delegate = _quercusClass.getArrayDelegate();

    // php/066q vs. php/0906
    //return getField(null, key.toString());

    if (delegate != null)
      return delegate.get(this, key);
    else
      return super.get(key);
  }

  /**
   * Sets the array value with the given key.
   */
  @Override
  public Value put(Value key, Value value)
  {
    // php/0d94
    ArrayDelegate delegate = _quercusClass.getArrayDelegate();

    if (delegate != null)
      return delegate.put(this, key, value);
    else
      return super.put(key, value);
  }

  /**
   * Appends a new array value
   */
  @Override
  public Value put(Value value)
  {
    // php/0d94
    ArrayDelegate delegate = _quercusClass.getArrayDelegate();

    if (delegate != null)
      return delegate.put(this, value);
    else
      return super.put(value);
  }

  /**
   * Unsets the array value
   */
  @Override
  public Value remove(Value key)
  {
    ArrayDelegate delegate = _quercusClass.getArrayDelegate();

    if (delegate != null)
      return delegate.unset(this, key);
    else
      return super.remove(key);
  }

  //
  // Foreach/Traversable functions
  //

  /**
   * Returns an iterator for the key => value pairs.
   */
  @Override
  public Iterator<Map.Entry<Value, Value>> getIterator(Env env)
  {
    TraversableDelegate delegate = _quercusClass.getTraversableDelegate();

    if (delegate != null)
      return delegate.getIterator(env, this);

    return new EntryIterator(_entries);
  }

  /**
   * Returns an iterator for the keys.
   */
  @Override
  public Iterator<Value> getKeyIterator(Env env)
  {
    TraversableDelegate delegate = _quercusClass.getTraversableDelegate();

    if (delegate != null)
      return delegate.getKeyIterator(env, this);

    return new KeyIterator(_entries);
  }

  /**
   * Returns an iterator for the values.
   */
  @Override
  public Iterator<Value> getValueIterator(Env env)
  {
    TraversableDelegate delegate = _quercusClass.getTraversableDelegate();

    if (delegate != null)
      return delegate.getValueIterator(env, this);

    return new ValueIterator(_entries);
  }

  //
  // method calls
  //

  /**
   * Finds the method name.
   */
  @Override
  public AbstractFunction findFunction(String methodName)
  {
    return _quercusClass.findFunction(methodName);
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethod(Env env, int hash, char []name, int nameLen,
                          Expr []args)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);
      
      if (fun != null)
        return fun.callMethod(env, this, args);
      else if (_quercusClass.getCall() != null) {
        Expr []newArgs = new Expr[args.length + 1];
        newArgs[0] = new StringLiteralExpr(toMethod(name, nameLen));
        System.arraycopy(args, 0, newArgs, 1, args.length);
        
        return _quercusClass.getCall().callMethod(env, this, newArgs);
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethod(Env env, int hash, char []name, int nameLen,
                          Value []args)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this, args);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl(args));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethod(Env env, int hash, char []name, int nameLen)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl());
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethod(Env env, int hash, char []name, int nameLen,
                          Value a1)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this, a1);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl()
                  .append(a1));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethod(Env env, int hash, char []name, int nameLen,
                          Value a1, Value a2)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this, a1, a2);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl()
                  .append(a1)
                  .append(a2));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * calls the function.
   */
  @Override
  public Value callMethod(Env env, 
                          int hash, char []name, int nameLen,
			  Value a1, Value a2, Value a3)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this, a1, a2, a3);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl()
                  .append(a1)
                  .append(a2)
                  .append(a3));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }  

  /**
   * calls the function.
   */
  @Override
  public Value callMethod(Env env, 
                          int hash, char []name, int nameLen,
			  Value a1, Value a2, Value a3, Value a4)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this, a1, a2, a3, a4);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl()
                  .append(a1)
                  .append(a2)
                  .append(a3)
                  .append(a4));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }  

  /**
   * calls the function.
   */
  @Override
  public Value callMethod(Env env,
                          int hash, char []name, int nameLen,
			  Value a1, Value a2, Value a3, Value a4, Value a5)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethod(env, this, a1, a2, a3, a4, a5);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethod(env,
                  this,
                  env.createString(name, nameLen),
                  new ArrayValueImpl()
                  .append(a1)
                  .append(a2)
                  .append(a3)
                  .append(a4)
                  .append(a5));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }  

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Expr []args)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      return _quercusClass.callMethodRef(env, this, hash, name, nameLen, args);
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Value []args)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this, args);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl(args));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl());
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Value a1)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this, a1);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl()
                     .append(a1));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Value a1, Value a2)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this, a1, a2);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl()
                     .append(a1)
                     .append(a2));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Value a1, Value a2, Value a3)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this, a1, a2, a3);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl()
                     .append(a1)
                     .append(a2)
                     .append(a3));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Value a1, Value a2, Value a3, Value a4)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this, a1, a2, a3, a4);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl()
                     .append(a1)
                     .append(a2)
                     .append(a3)
                     .append(a4));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callMethodRef(Env env, int hash, char []name, int nameLen,
                             Value a1, Value a2, Value a3, Value a4, Value a5)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _methodMap.get(hash, name, nameLen);

      if (fun != null)
        return fun.callMethodRef(env, this, a1, a2, a3, a4, a5);
      else if ((fun = _quercusClass.getCall()) != null) {
        return fun.callMethodRef(env,
                     this,
                     env.createString(name, nameLen),
                     new ArrayValueImpl()
                     .append(a1)
                     .append(a2)
                     .append(a3)
                     .append(a4)
                     .append(a5));
      }
      else
        return env.error(L.l("Call to undefined method {0}::{1}()",
                             getName(), toMethod(name, nameLen)));
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Evaluates a method.
   */
  @Override
  public Value callClassMethod(Env env, AbstractFunction fun, Value []args)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      return fun.callMethod(env, this, args);
    } finally {
      env.setCallingClassName(oldClassName);
    }
  }

  /**
   * Returns the value for the variable, creating an object if the var
   * is unset.
   */
  @Override
  public Value getObject(Env env)
  {
    return this;
  }

  @Override
  public Value getObject(Env env, Value index)
  {
    // php/3d92

    env.error(L.l("Can't use object '{0}' as array", getName()));

    return NullValue.NULL;
  }

  /**
   * Copy for assignment.
   */
  @Override
  public Value copy()
  {
    return this;
  }

  /**
   * Copy for serialization
   */
  @Override
  public Value copy(Env env, IdentityHashMap<Value,Value> map)
  {
    Value oldValue = map.get(this);

    if (oldValue != null)
      return oldValue;

    // XXX:
    // return new ObjectExtValue(env, map, _cl, getArray());

    return this;
  }

  /**
   * Copy for serialization
   */
  @Override
  public Value copyTree(Env env, CopyRoot root)
  {
    return new CopyObjectExtValue(env, this, root);
  }

  /**
   * Clone the object
   */
  @Override
  public Value clone()
  {
    ObjectExtValue newObject = new ObjectExtValue(_quercusClass);

    for (Map.Entry<Value,Value> entry : entrySet()) {
      newObject.putThisField(null,
			     (StringValue) entry.getKey(),
			     entry.getValue());
    }

    return newObject;
  }

  // XXX: need to check the other copy, e.g. for sessions

  /*
   * Serializes the value.
   * 
   * @param sb holds result of serialization
   * @param serializeMap holds reference indexes
   */
  @Override
  public void serialize(Env env,
                        StringBuilder sb, SerializeMap serializeMap)
  {
    sb.append("O:");
    sb.append(_className.length());
    sb.append(":\"");
    sb.append(_className);
    sb.append("\":");
    sb.append(getSize());
    sb.append(":{");
    
    serializeMap.put(this);
    serializeMap.incrementIndex();

    for (Map.Entry<Value,Value> entry : entrySet()) {
      Value key = entry.getKey();

      sb.append("s:");
      sb.append(key.length());
      sb.append(":\"");
      sb.append(key);
      sb.append("\";");

      Value value = ((Entry) entry).getRawValue();
      
      value.serialize(env, sb, serializeMap);
    }

    sb.append("}");
  }

  /**
   * Converts to a string.
   * @param env
   */
  @Override
  public StringValue toString(Env env)
  {
    String oldClassName = env.setCallingClassName(_className);
    
    try {
      AbstractFunction fun = _quercusClass.findFunction("__toString");

      if (fun != null)
        return fun.callMethod(env, this, new Expr[0]).toStringValue();
      else
        return env.createString(_className + "[]");
    } finally {
      env.setCallingClassName(oldClassName);
    }
    
  }

  /**
   * Converts to a string.
   * @param env
   */
  @Override
  public void print(Env env)
  {
    env.print(toString(env));
  }

  /**
   * Converts to an array.
   */
  @Override
  public Value toArray()
  {
    ArrayValue array = new ArrayValueImpl();

    for (Map.Entry<Value,Value> entry : entrySet()) {
      array.put(entry.getKey(), entry.getValue());
    }

    return array;
  }

  /**
   * Converts to an object.
   */
  @Override
  public Value toObject(Env env)
  {
    return this;
  }

  /**
   * Converts to an object.
   */
  @Override
  public Object toJavaObject()
  {
    return this;
  }

  @Override
  public Set<? extends Map.Entry<Value,Value>> entrySet()
  {
    return new EntrySet();
  }

  /**
   * Returns a Set of entries, sorted by key.
   */
  public Set<? extends Map.Entry<Value,Value>> sortedEntrySet()
  {
    return new TreeSet<Map.Entry<Value, Value>>(entrySet());
  }

  //
  // debugging
  //

  public void varDumpImpl(Env env,
                          WriteStream out,
                          int depth,
                          IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    // XXX: push up to super, and use varDumpObject
    out.println("object(" + getName() + ") (" + getSize() + ") {");

    for (Map.Entry<Value,Value> mapEntry : sortedEntrySet()) {
      ObjectExtValue.Entry entry = (ObjectExtValue.Entry) mapEntry;

      entry.varDumpImpl(env, out, depth + 1, valueSet);
    }

    printDepth(out, 2 * depth);

    out.print("}");
  }

  @Override
  protected void printRImpl(Env env,
                            WriteStream out,
                            int depth,
                            IdentityHashMap<Value, String> valueSet)
    throws IOException
  {
    out.print(getName());
    out.print(' ');
    out.println("Object");
    printDepth(out, 4 * depth);
    out.println("(");

    for (Map.Entry<Value,Value> mapEntry : sortedEntrySet()) {
      ObjectExtValue.Entry entry = (ObjectExtValue.Entry) mapEntry;

      entry.printRImpl(env, out, depth + 1, valueSet);
    }

    printDepth(out, 4 * depth);
    out.println(")");
  }

  //
  // Java Serialization
  //
  
  private void writeObject(ObjectOutputStream out)
    throws IOException
  {
    out.writeObject(_className);

    out.writeInt(_size);
    
    for (Map.Entry<Value,Value> entry : entrySet()) {      
      out.writeObject(entry.getKey());
      out.writeObject(entry.getValue());
    }
  }
  
  private void readObject(ObjectInputStream in)
    throws ClassNotFoundException, IOException
  { 
    Env env = Env.getInstance();
    String name = (String) in.readObject();

    QuercusClass cl = env.findClass(name);

    init();

    if (cl != null) {
      setQuercusClass(cl);
    }
    else {
      cl = env.getQuercus().getStdClass();

      setQuercusClass(cl);

      putThisField(env,
		   env.createString("__Quercus_Class_Definition_Not_Found"),
		   env.createString(name));
    }

    int size = in.readInt();
    
    for (int i = 0; i < size; i++) {
      putThisField(env,
		   (StringValue) in.readObject(),
		   (Value) in.readObject());
    }
  }

  private static String toMethod(char []key, int keyLength)
  {
    return new String(key, 0, keyLength);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "@" + System.identityHashCode(this) +  "[" + _className + "]";
  }
  
  public class EntrySet extends AbstractSet<Map.Entry<Value,Value>> {
    EntrySet()
    {
    }

    @Override
    public int size()
    {
      return ObjectExtValue.this.getSize();
    }

    @Override
    public Iterator<Map.Entry<Value,Value>> iterator()
    {
      return new EntryIterator(ObjectExtValue.this._entries);
    }
  }

  public static class EntryIterator
    implements Iterator<Map.Entry<Value,Value>>
  {
    private final Entry []_list;
    private int _index;
    private Entry _entry;

    EntryIterator(Entry []list)
    {
      _list = list;
    }

    public boolean hasNext()
    {
      if (_entry != null)
	return true;
      
      for (; _index < _list.length && _list[_index] == null; _index++) {
      }

      return _index < _list.length;
    }

    public Map.Entry<Value,Value> next()
    {
      if (_entry != null) {
	Entry entry = _entry;
	_entry = entry._next;

	return entry;
      }

      for (; _index < _list.length && _list[_index] == null; _index++) {
      }

      if (_list.length <= _index)
        return null;

      Entry entry = _list[_index++];
      _entry = entry._next;

      return entry;
    }

    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  public static class ValueIterator
    implements Iterator<Value>
  {
    private final Entry []_list;
    private int _index;
    private Entry _entry;

    ValueIterator(Entry []list)
    {
      _list = list;
    }

    public boolean hasNext()
    {
      if (_entry != null)
	return true;
      
      for (; _index < _list.length && _list[_index] == null; _index++) {
      }

      return _index < _list.length;
    }

    public Value next()
    {
      if (_entry != null) {
	Entry entry = _entry;
	_entry = entry._next;

	return entry._value;
      }

      for (; _index < _list.length && _list[_index] == null; _index++) {
      }

      if (_list.length <= _index)
        return null;

      Entry entry = _list[_index++];
      _entry = entry._next;

      return entry._value;
    }

    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  public static class KeyIterator
    implements Iterator<Value>
  {
    private final Entry []_list;
    private int _index;
    private Entry _entry;

    KeyIterator(Entry []list)
    {
      _list = list;
    }

    public boolean hasNext()
    {
      if (_entry != null)
	return true;
      
      for (; _index < _list.length && _list[_index] == null; _index++) {
      }

      return _index < _list.length;
    }

    public Value next()
    {
      if (_entry != null) {
	Entry entry = _entry;
	_entry = entry._next;

	return entry._key;
      }

      for (; _index < _list.length && _list[_index] == null; _index++) {
      }

      if (_list.length <= _index)
        return null;

      Entry entry = _list[_index++];
      _entry = entry._next;

      return entry._key;
    }

    public void remove()
    {
      throw new UnsupportedOperationException();
    }
  }

  public final static class Entry
    implements Map.Entry<Value,Value>,
               Comparable<Map.Entry<Value, Value>>
  {
    private final StringValue _key;
    private final FieldVisibility _visibility;
    private Value _value;

    Entry _prev;
    Entry _next;

    public Entry(StringValue key)
    {
      _key = key;
      _visibility = FieldVisibility.PUBLIC;
      _value = NullValue.NULL;
    }

    public Entry(StringValue key, FieldVisibility visibility)
    {
      _key = key;
      _visibility = visibility;
      _value = NullValue.NULL;
    }

    public Entry(StringValue key, Value value)
    {
      _key = key;
      _visibility = FieldVisibility.PUBLIC;
      _value = value;
    }

    public Value getValue()
    {
      return _value.toValue();
    }
    
    public Value getRawValue()
    {
      return _value;
    }

    public Value getKey()
    {
      return _key;
    }

    public final boolean isPrivate()
    {
      return _visibility == FieldVisibility.PRIVATE;
    }

    public Value toValue()
    {
      // The value may be a var
      // XXX: need test
      return _value.toValue();
    }

    /**
     * Argument used/declared as a ref.
     */
    public Var toRefVar()
    {
      Var var = _value.toRefVar();

      _value = var;
      
      return var;
    }

    /**
     * Converts to an argument value.
     */
    public Value toArgValue()
    {
      return _value.toValue();
    }

    public Value setValue(Value value)
    {
      Value oldValue = toValue();

      _value = value;

      return oldValue;
    }

    /**
     * Converts to a variable reference (for function arguments)
     */
    public Value toRef()
    {
      Value value = _value;

      if (value instanceof Var)
        return new RefVar((Var) value);
      else {
	Var var = new Var(_value);
	
	_value = var;
	
        return new RefVar(var);
      }
    }

    /**
     * Converts to a variable reference (for function  arguments)
     */
    public Value toArgRef()
    {
      Value value = _value;

      if (value instanceof Var)
        return new RefVar((Var) value);
      else {
	Var var = new Var(_value);
	
	_value = var;
	
        return new RefVar(var);
      }
    }

    public Value toArg()
    {
      Value value = _value;

      if (value instanceof Var)
        return value;
      else {
	Var var = new Var(_value);
	
	_value = var;
	
        return var;
      }
    }

    Entry copyTree(Env env, CopyRoot root)
    {
      return new Entry(_key, _value.copyTree(env, root));
    }

    public int compareTo(Map.Entry<Value, Value> other)
    {
      if (other == null)
        return 1;

      Value thisKey = getKey();
      Value otherKey = other.getKey();

      if (thisKey == null)
        return otherKey == null ? 0 : -1;

      if (otherKey == null)
        return 1;

      return thisKey.cmp(otherKey);
    }

    public void varDumpImpl(Env env,
                            WriteStream out,
                            int depth,
                            IdentityHashMap<Value, String> valueSet)
      throws IOException
    {
      printDepth(out, 2 * depth);
      out.println("[\"" + getKey() + "\"]=>");

      printDepth(out, 2 * depth);
      
      _value.varDump(env, out, depth, valueSet);
      
      out.println();
    }

    protected void printRImpl(Env env,
                              WriteStream out,
                              int depth,
                              IdentityHashMap<Value, String> valueSet)
      throws IOException
    {
      printDepth(out, 4 * depth);
      out.print("[" + getKey() + "] => ");
      
      _value.printR(env, out, depth + 1, valueSet);

      out.println();
    }

    private void printDepth(WriteStream out, int depth)
      throws java.io.IOException
    {
      for (int i = 0; i < depth; i++)
	out.print(' ');
    }

    @Override
    public String toString()
    {
      return "ObjectExtValue.Entry[" + getKey() + "]";
    }
  }
}

