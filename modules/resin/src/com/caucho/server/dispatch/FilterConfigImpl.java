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

package com.caucho.server.dispatch;

import com.caucho.config.Config;
import com.caucho.config.ConfigException;
import com.caucho.config.program.ContainerProgram;
import com.caucho.config.types.InitParam;
import com.caucho.server.util.CauchoSystem;
import com.caucho.util.L10N;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for a filter.
 */
public class FilterConfigImpl implements FilterConfig {
  private static final L10N L = new L10N(FilterConfigImpl.class);
  
  private String _filterName;
  private String _filterClassName;
  private Class _filterClass;
  private String _displayName;
  private HashMap<String,String> _initParams = new HashMap<String,String>();

  private ContainerProgram _init;

  private ServletContext _servletContext;
  
  /**
   * Creates a new filter configuration object.
   */
  public FilterConfigImpl()
  {
  }
  
  public void setId(String id)
  {
  }

  /**
   * Sets the filter name.
   */
  public void setFilterName(String name)
  {
    _filterName = name;
  }

  /**
   * Gets the filter name.
   */
  public String getFilterName()
  {
    return _filterName;
  }

  /**
   * Sets the filter class.
   */
  public void setFilterClass(String filterClassName)
    throws ConfigException, ClassNotFoundException
  {
    _filterClassName = filterClassName;
    
    _filterClass = CauchoSystem.loadClass(filterClassName);

    Config.validate(_filterClass, Filter.class);
  }

  /**
   * Gets the filter name.
   */
  public Class getFilterClass()
  {
    return _filterClass;
  }

  /**
   * Gets the filter name.
   */
  public String getFilterClassName()
  {
    return _filterClassName;
  }

  /**
   * Sets an init-param
   */
  public void setInitParam(String param, String value)
  {
    _initParams.put(param, value);
  }

  /**
   * Sets an init-param
   */
  public void setInitParam(InitParam initParam)
  {
    _initParams.putAll(initParam.getParameters());
  }

  /**
   * Gets the init params
   */
  public Map getInitParamMap()
  {
    return _initParams;
  }

  /**
   * Gets the init params
   */
  public String getInitParameter(String name)
  {
    return _initParams.get(name);
  }

  /**
   * Gets the init params
   */
  public Enumeration getInitParameterNames()
  {
    return Collections.enumeration(_initParams.keySet());
  }

  /**
   * Returns the servlet context.
   */
  public ServletContext getServletContext()
  {
    return _servletContext;
  }

  /**
   * Sets the servlet context.
   */
  public void setServletContext(ServletContext app)
  {
    _servletContext = app;
  }

  /**
   * Sets the init block
   */
  public void setInit(ContainerProgram init)
  {
    _init = init;
  }

  /**
   * Gets the init block
   */
  public ContainerProgram getInit()
  {
    return _init;
  }

  /**
   * Sets the display name
   */
  public void setDisplayName(String displayName)
  {
    _displayName = displayName;
  }

  /**
   * Gets the display name
   */
  public String getDisplayName()
  {
    return _displayName;
  }

  /**
   * Sets the description
   */
  public void setDescription(String description)
  {
  }

  /**
   * Sets the icon
   */
  public void setIcon(String icon)
  {
  }

  /**
   * Returns a printable representation of the filter config object.
   */
  public String toString()
  {
    return "FilterConfigImpl[name=" + _filterName + ",class=" + _filterClass + "]";
  }
}
