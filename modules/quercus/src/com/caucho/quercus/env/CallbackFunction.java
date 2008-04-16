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

import com.caucho.quercus.program.AbstractFunction;

/**
 * Represents a call to a function.
 */
public class CallbackFunction extends Callback {
 // public static final CallbackFunction INVALID_CALLBACK = new CallbackFunction(null, "Invalid Callback");

  private Env _env;
  private String _funName;

  private AbstractFunction _fun;

 // private boolean _isInvalid = false;

  public CallbackFunction(Env env, String funName)
  {
    _env = env;
    _funName = funName;
  }

  public CallbackFunction(AbstractFunction fun)
  {
    _fun = fun;
  }

  public CallbackFunction(AbstractFunction fun, String funName)
  {
    _fun = fun;
    _funName = funName;
  }
/*
  public CallbackFunction(String funName, boolean isInvalid)
  {
    _funName = funName;
    _isInvalid = isInvalid;
  }
  */

  /**
   * Allow subclasses to set the abstract function directly.
   */
  protected void setFunction(AbstractFunction fun)
  {
    _fun = fun;
  }
  
  public boolean isValid()
  {
    if (_fun != null)
      return true;

    return _env.findFunction(_funName) != null;

    //return _isInvalid;
  }
  
  /**
   * Serializes the value.
   */
  public void serialize(StringBuilder sb)
  {
    String name;
    
    if (_fun != null)
      name = _fun.getName();
    else
      name = _funName;
    
    sb.append("S:");
    sb.append(name.length());
    sb.append(":\"");
    sb.append(name);
    sb.append("\";");
  }

  /**
   * Evaluates the callback with no arguments.
   *
   * @param env the calling environment
   */
  public Value call(Env env)
  {
    return getFunction().call(env);
  }

  /**
   * Evaluates the callback with 1 argument.
   *
   * @param env the calling environment
   */
  public Value call(Env env, Value a1)
  {
    return getFunction().call(env, a1);
  }

  /**
   * Evaluates the callback with 2 arguments.
   *
   * @param env the calling environment
   */
  public Value call(Env env, Value a1, Value a2)
  {
    return getFunction().call(env, a1, a2);
  }

  /**
   * Evaluates the callback with 3 arguments.
   *
   * @param env the calling environment
   */
  public Value call(Env env, Value a1, Value a2, Value a3)
  {
    return getFunction().call(env, a1, a2, a3);
  }

  /**
   * Evaluates the callback with 3 arguments.
   *
   * @param env the calling environment
   */
  public Value call(Env env, Value a1, Value a2, Value a3,
			     Value a4)
  {
    return getFunction().call(env, a1, a2, a3, a4);
  }

  /**
   * Evaluates the callback with 3 arguments.
   *
   * @param env the calling environment
   */
  public Value call(Env env, Value a1, Value a2, Value a3,
		    Value a4, Value a5)
  {
    return getFunction().call(env, a1, a2, a3, a4, a5);
  }

  public Value call(Env env, Value []args)
  {
    return getFunction().call(env, args);
  }

  public String getFunctionName()
  {
    return _funName;
  }

  public String getCallbackName()
  {
    return _funName;
  }

  public AbstractFunction getFunction()
  {
    if (_fun == null)
      _fun = _env.getFunction(_funName);

    return _fun;
  }

  public String toString()
  {
    return getClass().getName() + '[' + _funName + ']';
  }

  public boolean isInternal()
  {
    return getFunction() instanceof JavaInvoker;
  }
}
