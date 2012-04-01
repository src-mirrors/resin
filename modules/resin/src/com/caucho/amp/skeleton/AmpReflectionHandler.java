/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.amp.skeleton;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;

import com.caucho.amp.AmpQueryCallback;
import com.caucho.amp.actor.ActorContextImpl;
import com.caucho.amp.actor.AmpQueryFuture;
import com.caucho.amp.router.AmpBroker;
import com.caucho.amp.stream.AmpStream;
import com.caucho.amp.stream.NullEncoder;

/**
 * Creates AMP skeletons and stubs.
 */
class AmpReflectionHandler implements InvocationHandler
{
  private HashMap<Method,Call> _callMap = new HashMap<Method,Call>();
  
  private String _to;
  private String _from;
  private AmpBroker _broker;
  private ActorContextImpl _queryManager;
  private long _timeout = 60000; 
    
  AmpReflectionHandler(Class<?> api, 
                       AmpBroker broker,
                       ActorContextImpl actorContext, 
                       String to,
                       String from)
  {
    _to = to;
    _from = from;
    _broker = broker;
    _queryManager = actorContext;
    
    if (broker == null) {
      throw new NullPointerException();
    }
    
    if (actorContext == null) {
      throw new NullPointerException();
    }
      
    for (Method m : api.getMethods()) {
      if (m.getDeclaringClass() == Object.class) {
        continue;
      }
      
      Call call = null;
      
      Class<?> []param = m.getParameterTypes();
      
      if (param.length > 0
          && AmpQueryCallback.class.isAssignableFrom(param[param.length - 1])) {
        call = new QueryCallbackCall(m.getName(), param.length - 1);
      }
      else if (void.class.equals(m.getReturnType())) {
        call = new MessageCall(m.getName());
      }
      else {
        call = new QueryCall(m.getName());
      }
      
      _callMap.put(m, call);
    }
  }
  
  @Override
  public Object invoke(Object proxy, Method method, Object[] args)
    throws Throwable
  {
    Call call = _callMap.get(method);

    if (call != null) {
      return call.invoke(_broker, _queryManager, _to, _from, args, _timeout);
    }
    
    String name = method.getName();
    
    int size = args != null ? args.length : 0;
    
    if ("toString".equals(name) && size == 0) {
      return "BamProxyHandler[" + _to + "]";
    }
    
    return null;
  }
  
  abstract static class Call {
    abstract Object invoke(AmpStream stream, 
                           ActorContextImpl queryManager,
                           String to,
                           String from,
                           Object []args,
                           long timeout);
  }
  
  static class QueryCall extends Call {
    private final String _name;
    
    QueryCall(String name)
    {
      _name = name;
    }
    
    @Override
    Object invoke(AmpStream stream,
                  ActorContextImpl queryManager, 
                  String to, 
                  String from,
                  Object []args,
                  long timeout)
    {
      AmpQueryFuture future = new AmpQueryFuture(timeout);
      
      queryManager.query(stream, to, from, _name, args, future, timeout);
      
      return future.get();
    }
  }
  
  static class QueryCallbackCall extends Call {
    private final String _name;
    private final int _paramLen;
    
    QueryCallbackCall(String name, int paramLen)
    {
      _name = name;
      _paramLen = paramLen;
    }
    
    @Override
    Object invoke(AmpStream stream,
                  ActorContextImpl queryManager,
                  String to, 
                  String from,
                  Object []args,
                  long timeout)
    {
      Object []param = new Object[args.length - 1];
      System.arraycopy(args, 0, param, 0, param.length);
      
      AmpQueryCallback cb = (AmpQueryCallback) args[_paramLen];
      
      queryManager.query(stream, to, from, _name, args, cb, timeout);
      
      return null;
    }
  }

  static class MessageCall extends Call {
    private final String _name;
    
    MessageCall(String name)
    {
      _name = name;
    }

    @Override
    Object invoke(AmpStream broker,
                  ActorContextImpl manager,
                  String to,
                  String from,
                  Object []args,
                  long timeout)
    {
      broker.send(to, from, null, NullEncoder.ENCODER, _name, args);
      
      return null;
    }
  }
}
