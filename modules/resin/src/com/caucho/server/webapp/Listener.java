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

package com.caucho.server.webapp;

import com.caucho.config.BuilderProgram;
import com.caucho.config.Config;
import com.caucho.config.ConfigException;
import com.caucho.config.NodeBuilderProgram;
import com.caucho.config.types.InitProgram;
import com.caucho.config.j2ee.DescriptionGroupConfig;
import com.caucho.util.L10N;

import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestListener;
import javax.servlet.http.HttpSessionActivationListener;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

/**
 * Configuration for the listener
 */
public class Listener extends DescriptionGroupConfig {
  static L10N L = new L10N(Listener.class);

  // The listener class
  private Class _listenerClass;

  // The listener object
  private Object _object;
  
  private InitProgram _init;

  /**
   * Sets the listener class.
   */
  public void setListenerClass(Class cl)
    throws ConfigException
  {
    Config.checkCanInstantiate(cl);
    
    if (ServletContextListener.class.isAssignableFrom(cl)) {
    }
    else if (ServletContextAttributeListener.class.isAssignableFrom(cl)) {
    }
    else if (ServletRequestListener.class.isAssignableFrom(cl)) {
    }
    else if (ServletRequestAttributeListener.class.isAssignableFrom(cl)) {
    }
    else if (HttpSessionListener.class.isAssignableFrom(cl)) {
    }
    else if (HttpSessionAttributeListener.class.isAssignableFrom(cl)) {
    }
    else if (HttpSessionActivationListener.class.isAssignableFrom(cl)) {
    }
    else
      throw new ConfigException(L.l("listener-class '{0}' does not implement any web-app listener interface.",
				    cl.getName()));
    
    _listenerClass = cl;
  }

  /**
   * Gets the listener class.
   */
  public Class getListenerClass()
  {
    return _listenerClass;
  }

  /**
   * Sets the init block
   */
  public void setInit(InitProgram init)
  {
    _init = init;
  }

  /**
   * Gets the init block
   */
  public InitProgram getInit()
  {
    return _init;
  }

  /**
   * Initialize.
   */
  public Object createListenerObject()
    throws Exception
  {
    if (_object != null)
      return _object;
    
      _object = _listenerClass.newInstance();
    
    InitProgram init = getInit();
    BuilderProgram program;

    if (init != null)
      program = init.getBuilderProgram();
    else
      program = NodeBuilderProgram.NULL;

    program.configure(_object);
    program.init(_object);

    return _object;
  }

  public String toString()
  {
    return "Listener[" + _listenerClass + "]";
  }
}
