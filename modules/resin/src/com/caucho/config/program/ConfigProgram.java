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

package com.caucho.config.program;

import com.caucho.config.*;
import com.caucho.webbeans.context.DependentScope;

/**
 * A saved program for configuring an object.
 */
public abstract class ConfigProgram {
  /**
   * Configures the bean using the current program.
   * 
   * @param bean the bean to configure
   * @param env the Config environment
   */
  abstract public void inject(Object bean, ConfigContext env);

  public void addProgram(ConfigProgram program)
  {
    throw new UnsupportedOperationException("Cannot add a program to a BuilderProgram. You probably need a BuilderProgramContainer.");
  }

  /**
   * Configures the object.
   */
  final
  public void configure(Object bean)
    throws ConfigException
  {
    // server/23e7
    inject(bean, ConfigContext.create());
  }

  final
  public Object configure(Class type)
    throws ConfigException
  {
    return configure(type, ConfigContext.create());
  }


  /**
   * Configures a bean given a class to instantiate.
   */
  final
  protected Object configure(Class type, ConfigContext env)
    throws ConfigException
  {
    try {
      Object bean = type.newInstance();

      inject(bean, env);

      return bean;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  public void init(Object bean)
    throws ConfigException
  {
    Config.init(bean);
  }
}
