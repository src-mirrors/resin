/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.config.gen;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.Logger;

import javax.enterprise.inject.spi.InterceptionType;
import javax.enterprise.inject.spi.Interceptor;
import javax.interceptor.InvocationContext;

public class CandiInvocationContext implements InvocationContext {
  private static final Logger log
    = Logger.getLogger(CandiInvocationContext.class.getName());
  
  private final Object _target;
  private final Method _apiMethod;
  private final Method _implMethod;

  private final Interceptor []_chainMethods;
  private final Object []_chainObjects;
  private final int []_chainIndex;
  
  private final InterceptionType _type;

  private Object []_param;

  private int _index;
  private HashMap<String,Object> _map;

  public CandiInvocationContext(InterceptionType type,
                                Object target,
                                Method apiMethod,
                                Method implMethod,
                                Interceptor []chainMethods,
                                Object []chainObjects,
                                int []chainIndex,
                                Object []param)
  {
    _target = target;
    _type = type;
    
    _apiMethod = apiMethod;
    _implMethod = implMethod;
    _chainMethods = chainMethods;
    _chainObjects = chainObjects;
    _chainIndex = chainIndex;
    _param = param;
  }

  @Override
  public Object getTarget()
  {
    return _target;
  }

  @Override
  public Method getMethod()
  {
    return _apiMethod;
  }

  @Override
  public Object getTimer()
  {
    return null;
  }

  @Override
  public Object[] getParameters()
    throws IllegalStateException
  {
    return _param;
  }

  @Override
  public void setParameters(Object[] parameters)
    throws IllegalStateException
  {
    _param = parameters;
  }

  @Override
  public Map<String, Object> getContextData()
  {
    if (_map == null)
      _map = new HashMap<String,Object>();

    return _map;
  }

  @Override
  public Object proceed()
    throws Exception
  {
    try {
      // ioc/0c57
      if (_chainObjects != null && _index < _chainIndex.length) {
        int i = _index++;

        return _chainMethods[i].intercept(_type,
                                          _chainObjects[_chainIndex[i]], 
                                          this);
      }
      else {
        log.warning("INVOKE: " + _target.getClass() + " " + _implMethod);
        return _implMethod.invoke(_target, _param);
      }
    } catch (InterceptorException e) {
      Throwable cause = e.getCause();

      if (cause instanceof Exception)
        throw (Exception) cause;
      else
        throw e;
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();

      if (cause instanceof Exception)
        throw (Exception) cause;
      else
        throw e;
    }
  }
  
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _implMethod.getName() + "]";
  }
}
