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

import javax.servlet.jsp.el.VariableResolver;

import com.caucho.loader.EnvironmentMap;

/**
 * Creates a variable resolver based on the classloader.
 */
public class EnvironmentVariableResolver extends AbstractVariableResolver {
  private static EnvironmentMap _map = new EnvironmentMap();
  
  /**
   * Creates the resolver
   */
  public EnvironmentVariableResolver()
  {
  }
  
  /**
   * Creates the resolver
   */
  public EnvironmentVariableResolver(VariableResolver next)
  {
  }
  
  /**
   * Returns the named variable value.
   */
  public Object resolveVariable(String var)
  {
    Object value = _map.get(var);
    
    if (value != null)
      return value;
    else
      return super.resolveVariable(var);
  }
  
  /**
   * Sets the value for the named variable.
   */
  public Object put(String var, Object value)
  {
    return _map.put(var, value);
  }
}
