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

import com.caucho.quercus.QuercusException;
import com.caucho.quercus.annotation.*;
import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.expr.ExprFactory;
import com.caucho.quercus.function.Marshal;
import com.caucho.quercus.function.MarshalFactory;
import com.caucho.quercus.module.ModuleContext;
import com.caucho.quercus.parser.QuercusParser;
import com.caucho.util.L10N;

import java.lang.annotation.Annotation;

/**
 * Represents the introspected static function information.
 */
abstract public class JavaInvoker
  extends AbstractJavaMethod
{
  private static final L10N L = new L10N(JavaInvoker.class);

  private static final Object [] NULL_ARGS = new Object[0];
  private static final Value [] NULL_VALUES = new Value[0];

  private final ModuleContext _moduleContext;
  private final String _name;
  private final Class [] _param;
  private final Class _retType;
  private final Annotation [][] _paramAnn;
  private final Annotation []_methodAnn;

  private volatile boolean _isInit;
  
  private int _minArgumentLength;
  private int _maxArgumentLength;

  private boolean _hasEnv;
  private boolean _hasThis;
  private Expr [] _defaultExprs;
  private Marshal [] _marshalArgs;
  private boolean _hasRestArgs;
  private Marshal _unmarshalReturn;

  private boolean _isRestReference;

  private boolean _isCallUsesVariableArgs;
  private boolean _isCallUsesSymbolTable;


  /**
   * Creates the statically introspected function.
   */
  public JavaInvoker(ModuleContext moduleContext,
                     String name,
                     Class []param,
                     Annotation [][]paramAnn,
                     Annotation []methodAnn,
                     Class retType)
  {
    _moduleContext = moduleContext;
    _name = name;
    _param = param;
    _paramAnn = paramAnn;
    _methodAnn = methodAnn;
    _retType = retType;
    
    // init();
  }

  public synchronized void init()
  {
    if (_isInit)
      return;

    MarshalFactory marshalFactory = _moduleContext.getMarshalFactory();
    ExprFactory exprFactory = _moduleContext.getExprFactory();

    try {
      boolean callUsesVariableArgs = false;
      boolean callUsesSymbolTable = false;
      boolean returnNullAsFalse = false;

      for (Annotation ann : _methodAnn) {
        if (VariableArguments.class.isAssignableFrom(ann.annotationType()))
          callUsesVariableArgs = true;

        if (UsesSymbolTable.class.isAssignableFrom(ann.annotationType()))
          callUsesSymbolTable = true;

        if (ReturnNullAsFalse.class.isAssignableFrom(ann.annotationType()))
          returnNullAsFalse = true;
      }

      _isCallUsesVariableArgs = callUsesVariableArgs;
      _isCallUsesSymbolTable = callUsesSymbolTable;

      _hasEnv = _param.length > 0 && _param[0].equals(Env.class);
      int envOffset = _hasEnv ? 1 : 0;

      if (envOffset < _param.length)
        _hasThis = hasThis(_param[envOffset], _paramAnn[envOffset]);
      else
        _hasThis = false;

      if (_hasThis)
        envOffset++;

      boolean hasRestArgs = false;
      boolean isRestReference = false;

      if (_param.length > 0 &&
          (_param[_param.length - 1].equals(Value[].class) ||
	       _param[_param.length - 1].equals(Object[].class))) {
        hasRestArgs = true;

        for (Annotation ann : _paramAnn[_param.length - 1]) {
          if (Reference.class.isAssignableFrom(ann.annotationType()))
            isRestReference = true;
        }
      }

      _hasRestArgs = hasRestArgs;
      _isRestReference = isRestReference;

      int argLength = _param.length;

      if (_hasRestArgs)
        argLength -= 1;

      _defaultExprs = new Expr[argLength - envOffset];
      _marshalArgs = new Marshal[argLength - envOffset];

      _maxArgumentLength = argLength - envOffset;
      _minArgumentLength = _maxArgumentLength;

      for (int i = 0; i < argLength - envOffset; i++) {
        boolean isReference = false;
        boolean isNotNull = false;

        for (Annotation ann : _paramAnn[i + envOffset]) {
          if (Optional.class.isAssignableFrom(ann.annotationType())) {
            _minArgumentLength--;

            Optional opt = (Optional) ann;

            if (! opt.value().equals("")) {
              Expr expr = QuercusParser.parseDefault(opt.value());

              _defaultExprs[i] = expr;
            } else
              _defaultExprs[i] = exprFactory.createDefault();
          } else if (Reference.class.isAssignableFrom(ann.annotationType())) {
            isReference = true;
          } else if (NotNull.class.isAssignableFrom(ann.annotationType())) {
            isNotNull = true;
          }
        }

        Class argType = _param[i + envOffset];

        if (isReference) {
          _marshalArgs[i] = marshalFactory.createReference();

          if (! Value.class.equals(argType)
              && ! Var.class.equals(argType)) {
            throw new QuercusException(L.l("reference must be Value or Var for {0}",
                                           _name));
          }
        }
        else
          _marshalArgs[i] = marshalFactory.create(argType, isNotNull);

	/*
        if (getName().equals("test"))
          System.out.println("XXX:: MARSHALL " + getName() + " _marshalArgs for " + i + " " + argType + " is " + _marshalArgs[i]); // XXX::
	*/
      }

      _unmarshalReturn = marshalFactory.create(_retType,
                                               false,
                                               returnNullAsFalse);
    } finally {
      _isInit = true;
    }
  }

  /**
   * Returns the minimally required number of arguments.
   */
  @Override
  public int getMinArgLength()
  {
    if (! _isInit)
      init();
    return _minArgumentLength;
  }
  
  /**
   * Returns the maximum number of arguments allowed.
   */
  @Override
  public int getMaxArgLength()
  {
    if (! _isInit)
      init();
    return _maxArgumentLength;
  }

  /**
   * Returns true if the environment is an argument.
   */
  public boolean getHasEnv()
  {
    if (! _isInit)
      init();
    
    return _hasEnv;
  }

  /**
   * Returns true if the environment has rest-style arguments.
   */
  public boolean getHasRestArgs()
  {
    if (! _isInit)
      init();
    
    return _hasRestArgs;
  }

  /**
   * Returns true if the rest argument is a reference.
   */
  public boolean isRestReference()
  {
    if (! _isInit)
      init();
    
    return _isRestReference;
  }

  /**
   * Returns the unmarshaller for the return
   */
  public Marshal getUnmarshalReturn()
  {
    if (! _isInit)
      init();
    
    return _unmarshalReturn;
  }

  /**
   * Returns true if the call uses variable arguments.
   */
  @Override
  public boolean isCallUsesVariableArgs()
  {
    if (! _isInit)
      init();
    
    return _isCallUsesVariableArgs;
  }

  /**
   * Returns true if the call uses the symbol table
   */
  @Override
  public boolean isCallUsesSymbolTable()
  {
    if (! _isInit)
      init();
    
    return _isCallUsesSymbolTable;
  }

  /**
   * Returns true if the result is a boolean.
   */
  public boolean isBoolean()
  {
    if (! _isInit)
      init();
    
    return _unmarshalReturn.isBoolean();
  }

  /**
   * Returns true if the result is a string.
   */
  public boolean isString()
  {
    if (! _isInit)
      init();
    
    return _unmarshalReturn.isString();
  }

  /**
   * Returns true if the result is a long.
   */
  public boolean isLong()
  {
    if (! _isInit)
      init();
    
    return _unmarshalReturn.isLong();
  }

  /**
   * Returns true if the result is a double.
   */
  public boolean isDouble()
  {
    if (! _isInit)
      init();
    
    return _unmarshalReturn.isDouble();
  }

  public String getName()
  {
    return _name;
  }

  /**
   * Returns the marshal arguments.
   */
  protected Marshal []getMarshalArgs()
  {
    if (! _isInit)
      init();
    
    return _marshalArgs;
  }

  /**
   * Returns the parameter annotations.
   */
  protected Annotation [][]getParamAnn()
  {
    if (! _isInit)
      init();
    
    return _paramAnn;
  }

  /**
   * Returns the default expressions.
   */
  protected Expr []getDefaultExprs()
  {
    if (! _isInit)
      init();
    
    return _defaultExprs;
  }

  /**
   * Evaluates a function's argument, handling ref vs non-ref
   */
  @Override
  public Value []evalArguments(Env env, Expr fun, Expr []args)
  {
    if (! _isInit)
      init();
    
    Value []values = new Value[args.length];

    for (int i = 0; i < args.length; i++) {
      Marshal arg = null;

      if (i < _marshalArgs.length)
        arg = _marshalArgs[i];
      else if (_isRestReference) {
        values[i] = args[i].evalRef(env);
	continue;
      }
      else {
        values[i] = args[i].eval(env);
	continue;
      }

      if (arg == null)
        values[i] = args[i].eval(env).copy();
      else if (arg.isReference())
        values[i] = args[i].evalRef(env);
      else {
        // php/0d04
        values[i] = args[i].eval(env);
      }
    }

    return values;
  }

  /**
   * Returns the cost of marshaling for this method.
   */
  public int getMarshalingCost(Value []args)
  {
    if (! _isInit)
      init();

    if (_hasRestArgs) {
    }
    else if (args.length < _marshalArgs.length) {
      // not enough args
      return Integer.MAX_VALUE;
    }
    else if (args.length > _marshalArgs.length) {
      // too many args
      return Integer.MAX_VALUE;
    }

    int cost = 0;    
    int i = 0;
    
    for (; i < _marshalArgs.length; i++) {
      Marshal marshal = _marshalArgs[i];
      
      if (i < args.length && args[i] != null) {
        Value arg = args[i];

        cost = Math.max(marshal.getMarshalingCost(arg) + cost, cost);
	/*
        System.out.println("XXX:: MARSHALLING COST " + marshal + " " + arg.getClass() + " " + cost); // XXX::
	*/
      }
    }

    // consume all the REST args
    if (_hasRestArgs) {
      int restLen = args.length - _marshalArgs.length;
      
      if (restLen > 0)
        i += restLen;
    }

    // too many args passed in
    if (i != args.length)
      return Integer.MAX_VALUE;

    return cost;
  }
  
  public Value call(Env env, Value []value)
  {
    return call(env, env.getThis(), value);
  }

  /**
   * Evaluates the function, returning a copy.  Java methods don't
   * need the copy.
   */
  public Value callCopy(Env env, Value []args)
  {
    return call(env, args);
  }

  public Value call(Env env, Value obj, Value []value)
  {
    // php/0cl1
    return call(env, obj.toJavaObject(), value);
  }

  public Value call(Env env, Object obj, Expr []exprs)
  {
    if (! _isInit)
      init();
    
    int len = (_defaultExprs.length +
	       (_hasEnv ? 1 : 0) +
	       (_hasThis ? 1 : 0) +
	       (_hasRestArgs ? 1 : 0));

    Object []values = new Object[len];

    int k = 0;

    if (_hasEnv)
      values[k++] = env;
    if (_hasThis) {
      values[k++] = (ObjectValue) obj;
      obj = null;
    }

    for (int i = 0; i < _marshalArgs.length; i++) {
      Expr expr;

      if (i < exprs.length && exprs[i] != null)
        expr = exprs[i];
      else {
        expr = _defaultExprs[i];

        if (expr == null)
          expr = _moduleContext.getExprFactory().createRequired();
      }

      values[k] = _marshalArgs[i].marshal(env, expr, _param[k]);

      k++;
    }

    if (_hasRestArgs) {
      Value []rest;

      int restLen = exprs.length - _marshalArgs.length;

      if (restLen <= 0)
        rest = NULL_VALUES;
      else {
        rest = new Value[restLen];

        for (int i = _marshalArgs.length; i < exprs.length; i++) {
          if (_isRestReference)
            rest[i - _marshalArgs.length] = exprs[i].evalRef(env);
          else
            rest[i - _marshalArgs.length] = exprs[i].eval(env);
        }
      }

      values[values.length - 1] = rest;
    }

    Object result = invoke(obj, values);
    
    return _unmarshalReturn.unmarshal(env, result);
  }

  public Value call(Env env, Object obj, Value []args)
  {
    if (! _isInit)
      init();

    /*
    System.out.println("XXX:: JavaInvoker call " + this + " "); // XXX::
    for (int i = 0; i < args.length; i++) {
      Object arg = args[i];
      System.out.println("XXX::    arg " + i + " " + (arg == null ? "null" : arg.getClass()));
    }
    */

    int len = _param.length;

    Object []javaArgs = new Object[len];

    int k = 0;

    if (_hasEnv)
      javaArgs[k++] = env;

    if (_hasThis) {
      javaArgs[k++] = (ObjectValue) obj;
      obj = null;
    }

    for (int i = 0; i < _marshalArgs.length; i++) {

      if (i < args.length && args[i] != null)
        javaArgs[k] = _marshalArgs[i].marshal(env, args[i], _param[k]);
      else if (_defaultExprs[i] != null) {
        javaArgs[k] = _marshalArgs[i].marshal(env,
                                              _defaultExprs[i],
                                              _param[k]);
      } else {
        env.warning(L.l("function '{0}' has {1} required arguments, but only {2} were provided",
                        _name, _minArgumentLength, args.length));

        //return NullValue.NULL;

        javaArgs[k] = _marshalArgs[i].marshal(env, NullValue.NULL, _param[k]);
      }

      /*
      System.out.println("XXX::    arg " + k + " marhsal " + _marshalArgs[i].getClass() ); // XXX::
      */

      k++;
    }

    if (_hasRestArgs) {
      Value []rest;

      int restLen = args.length - _marshalArgs.length;

      if (restLen <= 0)
        rest = NULL_VALUES;
      else {
        rest = new Value[restLen];

        for (int i = _marshalArgs.length; i < args.length; i++) {
          if (_isRestReference)
            rest[i - _marshalArgs.length] = args[i];
          else
            rest[i - _marshalArgs.length] = args[i].toValue();
        }
      }

      javaArgs[k++] = rest;
    }

    /*
    System.out.println("XXX:: JavaInvoker call " + this + " "); // XXX::
    for (int i = 0; i < javaArgs.length; i++) {
      Object arg = javaArgs[i];
      System.out.println("XXX::    javaArg "  + i + " " + (arg == null ? "null" : arg.getClass()));
    }
    */

    Object result = invoke(obj, javaArgs);

    return _unmarshalReturn.unmarshal(env, result);
  }

  public Value call(Env env, Object obj)
  {
    return call(env, obj, new Value[0]);
  }

  public Value call(Env env, Object obj, Value a1)
  {
    return call(env, obj, new Value[] {a1});
  }

  public Value call(Env env, Object obj, Value a1, Value a2)
  {
    return call(env, obj, new Value[]{a1, a2});
  }

  public Value call(Env env, Object obj, Value a1, Value a2, Value a3)
  {
    return call(env, obj, new Value[]{a1, a2, a3});
  }

  public Value call(Env env, Object obj,
                    Value a1, Value a2, Value a3, Value a4)
  {
    return call(env, obj, new Value[]{a1, a2, a3, a4});
  }

  public Value call(Env env, Object obj,
                    Value a1, Value a2, Value a3, Value a4, Value a5)
  {
    return call(env, obj, new Value[]{a1, a2, a3, a4, a5});
  }

  abstract public Object invoke(Object obj, Object []args);

  //
  // Utility methods
  //
  private boolean hasThis(Class param, Annotation[]ann)
  {
    if (! param.isAssignableFrom(ObjectValue.class))
      return false;

    for (int i = 0; i < ann.length; i++) {
      if (This.class.isAssignableFrom(ann[i].annotationType()))
	return true;
    }

    return false;
  }
}
