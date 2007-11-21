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

package com.caucho.webbeans.context;

import com.caucho.server.webapp.WebApp;
import com.caucho.webbeans.component.ComponentImpl;

import java.util.*;

import javax.servlet.*;
import javax.servlet.http.*;


/**
 * The application scope value
 */
public class DependentScope {
  private static final ThreadLocal<DependentScope> _threadScope
    = new ThreadLocal<DependentScope>();

  private ComponentImpl _owner;
  private Object _value;
  private ScopeContext _scope;

  private IdentityHashMap<ComponentImpl,Object> _map;

  public DependentScope()
  {
  }
  
  public DependentScope(ComponentImpl owner, Object value, ScopeContext scope)
  {
    _owner = owner;
    _value = value;
    
    _scope = scope;
  }
  
  /**
   * Returns the current dependent scope.
   */
  public static DependentScope getCurrent()
  {
    return _threadScope.get();
  }
  
  /**
   * Begins a new instanceof the dependent scope
   */
  public static DependentScope begin(ScopeContext ownerScope)
  {
    throw new UnsupportedOperationException();
    
    //DependentScope scope = new DependentScope(ownerScope);

    //_threadScope.set(scope);

    //return scope;
  }

  /**
   * Closes the scope
   */
  public static void end(DependentScope oldScope)
  {
    _threadScope.set(oldScope);
  }

  /**
   * Returns the object with the given name.
   */
  public Object get(ComponentImpl comp)
  {
    if (comp == _owner)
      return _value;
    else if (_map != null)
      return _map.get(comp);
    else
      return null;
  }

  /**
   * Sets the object with the given name.
   */
  public void put(ComponentImpl comp, Object value)
  {
    if (_map == null)
      _map = new IdentityHashMap<ComponentImpl,Object>(8);
    
    _map.put(comp, value);
  }

  public boolean canInject(ScopeContext scope)
  {
    if (scope == null)
      return true;
    else if (_scope == null)
      return false;
    else
      return _scope.canInject(scope);
  }

  /**
   * Adds a @PreDestroy destructor
   */
  public void addDestructor(ComponentImpl comp, Object value)
  {
    if (_scope != null)
      _scope.addDestructor(comp, value);
    else {
      // add to env?
    }
  }
}
