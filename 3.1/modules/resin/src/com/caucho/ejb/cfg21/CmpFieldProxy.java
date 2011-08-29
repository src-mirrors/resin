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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.ejb.cfg21;

import com.caucho.ejb.cfg.*;
import com.caucho.ejb.cfg21.CmpField;
import com.caucho.config.program.ConfigProgram;
import com.caucho.config.program.ContainerProgram;
import com.caucho.config.ConfigException;
import com.caucho.util.L10N;

import javax.annotation.PostConstruct;

/**
 * Proxy for an cmp-field configuration.  This proxy is needed to handle
 * the merging of ejb definitions.
 */
public class CmpFieldProxy {
  private static final L10N L = new L10N(CmpFieldProxy.class);

  private EjbEntityBean _bean;

  private String _name;
  
  // The configuration program
  private ContainerProgram _program = new ContainerProgram();
  
  /**
   * Creates a new entity bean configuration.
   */
  public CmpFieldProxy(EjbEntityBean bean)
  {
    _bean = bean;
  }

  /**
   * Sets the cmp-field name.
   */
  public void setFieldName(String name)
    throws ConfigException
  {
    _name = name;
  }

  /**
   * Adds to the builder program.
   */
  public void addBuilderProgram(ConfigProgram program)
  {
    _program.addProgram(program);
  }

  /**
   * Initializes and configures the entity bean.
   */
  @PostConstruct
  public void init()
    throws Throwable
  {
    CmpField field = _bean.addField(_name);

    _program.configure(field);
  }
}
