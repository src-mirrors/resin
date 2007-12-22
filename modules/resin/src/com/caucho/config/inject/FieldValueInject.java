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

package com.caucho.config.inject;

import java.lang.reflect.*;

import com.caucho.config.*;
import com.caucho.util.*;
import com.caucho.webbeans.context.DependentScope;

/**
 * Injects a field with a constant value
 */
public class FieldValueInject extends NamedInject {
  private static final L10N L = new L10N(FieldComponentInject.class);
  
  private final Field _field;
  private final Object _value;

  public FieldValueInject(Field field, Object value)
  {
    _field = field;
    _value = value;

    _field.setAccessible(true);
  }
  
  /**
   * Returns the injection name.
   */
  public String getName()
  {
    return _field.getName();
  }
  
  /**
   * Injects the bean with the dependencies
   */
  public void inject(Object bean, DependentScope scope)
  {
    try {
      _field.set(bean, _value);
    } catch (IllegalArgumentException e) {
      throw new ConfigException(ConfigException.loc(_field) + L.l("Can't set field value '{0}'", _value), e);
    } catch (Exception e) {
      throw new ConfigException(ConfigException.loc(_field) + e.toString(), e);
    }
  }
}

