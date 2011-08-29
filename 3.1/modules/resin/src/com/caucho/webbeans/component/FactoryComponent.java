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

package com.caucho.webbeans.component;

import com.caucho.config.ConfigContext;
import java.lang.annotation.*;
import javax.webbeans.*;

import com.caucho.webbeans.cfg.WbWebBeans;
import com.caucho.webbeans.context.*;
import com.caucho.webbeans.manager.*;

/**
 * Component for a singleton beans
 */
abstract public class FactoryComponent extends ComponentImpl {
  public FactoryComponent(Class targetType, String name)
  {
    super(WebBeansContainer.create().getWbWebBeans());

    setTargetType(targetType);
    setName(name);
    addNameBinding(name);
    setType(_webbeans.createComponentType(Component.class));
  }

  @Override
  public void setScope(ScopeContext scope)
  {
  }

  @Override
  public Object get()
  {
    return get(null);
  }

  @Override
  public Object get(ConfigContext env)
  {
    if (env != null) {
      Object value = env.get(this);

      if (value != null)
	return value;

      value = create();
      
      env.put(this, value);

      return value;
    }
    else
      return create();
  }

  abstract public Object create();
}
