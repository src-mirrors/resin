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
 * @author Scott Ferguson;
 */

package com.caucho.config.j2ee;

import com.caucho.config.ConfigException;
import com.caucho.util.L10N;
import com.caucho.webbeans.context.DependentScope;

import javax.rmi.PortableRemoteObject;
import java.lang.reflect.Field;
import java.util.logging.Logger;


public class FieldInject extends Inject
{
  private static final Logger log
    = Logger.getLogger(FieldInject.class.getName());
  private static final L10N L = new L10N(FieldInject.class);

  private Field _field;
  private ValueGenerator _gen;

  public FieldInject(Field field, ValueGenerator gen)
  {
    _field = field;
    _field.setAccessible(true);

    _gen = gen;
  }

  String getName()
  {
    return _field.getName();
  }

  Class getType()
  {
    return _field.getType();
  }

  Class getDeclaringClass()
  {
    return _field.getDeclaringClass();
  }

  public void inject(Object bean, DependentScope scope)
    throws ConfigException
  {
    try {
      Object value = _gen.create();
      
      // XXX TCK: ejb30/bb/session/stateless/sessioncontext/descriptor/getBusinessObjectLocal1, needs QA
      if (value != null
	  && ! _field.getType().isAssignableFrom(value.getClass())
	  && ! _field.getType().isPrimitive()) {
	value = PortableRemoteObject.narrow(value, _field.getType());
	  
      }

      _field.set(bean, value);
    } catch (ConfigException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.create(location(), e);
    }
  }

  private String location()
  {
    return _field.getDeclaringClass().getName() + "." + _field.getName() + ": ";
  }
}
