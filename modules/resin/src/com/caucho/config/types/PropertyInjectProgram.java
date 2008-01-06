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
 * @author Scott Ferguson;
 */

package com.caucho.config.types;

import com.caucho.config.*;
import com.caucho.util.L10N;

import java.lang.reflect.Method;
import java.util.logging.Logger;


public class PropertyInjectProgram extends BuilderProgram {
  private static final Logger log
    = Logger.getLogger(PropertyInjectProgram.class.getName());
  private static final L10N L = new L10N(PropertyInjectProgram.class);

  private Method _method;
  private Class _type;
  private Object _value;

  public PropertyInjectProgram(Method method, Object value)
  {
    _method = method;
    _value = value;

    Class []paramTypes = method.getParameterTypes();
    _type = paramTypes[0];
    
    _method.setAccessible(true);

    if (value != null && ! _type.isAssignableFrom(value.getClass())) {
      throw new ConfigException(L.l("Resource type {0} is not assignable to method '{1}' of type {2}.",
				    value.getClass().getName(),
				    _method.getName(),
				    _type.getName()));
    }
  }

  String getName()
  {
    return _method.getName();
  }

  Class getType()
  {
    return _type;
  }

  /**
   * Configures the object.
   */
  public void configureImpl(NodeBuilder builder, Object bean)
    throws ConfigException
  {
    try {
      _method.invoke(bean, _value);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }
}
