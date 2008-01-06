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

package com.caucho.webbeans.inject;

import com.caucho.config.*;
import com.caucho.config.j2ee.*;
import com.caucho.webbeans.component.*;
import com.caucho.webbeans.context.DependentScope;
import com.caucho.util.*;

import java.util.logging.*;
import java.lang.reflect.*;

public class ComponentInject extends Inject
{
  private static final L10N L = new L10N(ComponentInject.class);
  private static final Logger log
    = Logger.getLogger(ComponentInject.class.getName());

  private ComponentImpl _component;
  private Field _field;

  public ComponentInject(ComponentImpl component,
			 Field field)
  {
    _component = component;
    _field = field;

    field.setAccessible(true);
  }

  public void inject(Object bean, DependentScope scope)
  {
    Object value = null;
    try {
      value = _component.get(scope);

      _field.set(bean, value);
    } catch (IllegalArgumentException e) {
      throw new ConfigException(ConfigException.loc(_field) + L.l("Can't set field value '{0}'", value), e);
    } catch (Exception e) {
      throw new ConfigException(ConfigException.loc(_field) + e.toString(), e);
    }
  }
}
