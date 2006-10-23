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
 * @author Scott Ferguson
 */

package com.caucho.server.dispatch;

import java.util.*;
import java.util.logging.*;

import javax.annotation.*;

import javax.servlet.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

import com.caucho.jsp.QServlet;

import com.caucho.config.BuilderProgram;
import com.caucho.config.types.InitProgram;

import com.caucho.jmx.Jmx;

import com.caucho.log.Log;

/**
 * Manages the servlets.
 */
public class ServletManager {
  static final Logger log = Log.open(ServletManager.class);
  static final L10N L = new L10N(ServletManager.class);

  private HashMap<String,ServletConfigImpl> _servlets
    = new HashMap<String,ServletConfigImpl>();
  
  private ArrayList<ServletConfigImpl> _servletList
    = new ArrayList<ServletConfigImpl>();
  
  private ArrayList<ServletConfigImpl> _cronList
  = new ArrayList<ServletConfigImpl>();

  private boolean _isLazyValidate;

  /**
   * Sets true if validation is lazy.
   */
  public void setLazyValidate(boolean isLazy)
  {
    _isLazyValidate = isLazy;
  }

  /**
   * Adds a servlet to the servlet manager.
   */
  public void addServlet(ServletConfigImpl config)
    throws ServletException
  {
    if (config.getServletContext() == null)
      throw new NullPointerException();

    config.setServletManager(this);

    synchronized (_servlets) {
      if (_servlets.get(config.getServletName()) != null) {
	for (int i = _servletList.size() - 1; i >= 0; i--) {
	  ServletConfigImpl oldConfig = _servletList.get(i);

	  if (config.getServletName().equals(oldConfig.getServletName())) {
	    _servletList.remove(i);
	    break;
	  }
	}

	/* XXX: need something more sophisticated since the
	 * resin.conf needs to override the web.xml
	 * throw new ServletConfigException(L.l("'{0}' is a duplicate servlet-name.  Servlets must have a unique servlet-name.", config.getServletName()));
	 */
      }
    
      config.validateClass(! _isLazyValidate);
    
      _servlets.put(config.getServletName(), config);
      _servletList.add(config);
    }
  }

  /**
   * Adds a servlet to the servlet manager.
   */
  public ServletConfigImpl getServlet(String servletName)
  {
    return _servlets.get(servletName);
  }

  /**
   * Initialize servlets that need starting at server start.
   */
  @PostConstruct
  public void init()
    throws ServletException
  {
    ArrayList<ServletConfigImpl> loadOnStartup;
    loadOnStartup = new ArrayList<ServletConfigImpl>();
    
    for (int j = 0; j < _servletList.size(); j++) {
      ServletConfigImpl config = _servletList.get(j);

      if (config.getLoadOnStartup() == Integer.MIN_VALUE)
        continue;

      int i = 0;
      for (; i < loadOnStartup.size(); i++) {
        ServletConfigImpl config2 = loadOnStartup.get(i);

        if (config.getLoadOnStartup() < config2.getLoadOnStartup()) {
          loadOnStartup.add(i, config);
          break;
        }
      }
      
      if (i == loadOnStartup.size())
        loadOnStartup.add(config);

      if (config.getRunAt() != null)
        _cronList.add(config);
    }

    for (int i = 0; i < loadOnStartup.size(); i++) {
      ServletConfigImpl config = loadOnStartup.get(i);

      try {
	config.createServlet();
      } catch (ServletException e) {
        log.log(Level.WARNING, e.toString(), e);

	// XXX: should JSP failure also cause a system failure?
	if (config.getJspFile() == null)
	  throw e;
      }
    }
  }

  /**
   * Creates the servlet chain for the servlet.
   */
  public FilterChain createServletChain(String servletName)
    throws ServletException
  {
    ServletConfigImpl config = _servlets.get(servletName);

    if (config == null) {
      throw new ServletConfigException(L.l("'{0}' is not a known servlet.  Servlets must be defined by <servlet> before being used.", servletName));
    }

    return config.createServletChain();
  }

  /**
   * Instantiates a servlet given its configuration.
   *
   * @param servletName the servlet
   *
   * @return the initialized servlet.
   */
  public Servlet createServlet(String servletName)
    throws ServletException
  {
    ServletConfigImpl config = _servlets.get(servletName);

    if (config == null) {
      throw new ServletException(L.l("'{0}' is not a known servlet.  Servlets must be defined by <servlet> before being used.", servletName));
    }

    return (Servlet) config.createServlet();
  }

  /**
   * Returns the servlet config.
   */
  ServletConfigImpl getServletConfig(String servletName)
  {
    return _servlets.get(servletName);
  }

  public void destroy()
  {
    ArrayList<ServletConfigImpl> servletList;
    servletList = new ArrayList<ServletConfigImpl>();
    
    if (_servletList != null) {
      synchronized (_servletList) {
        servletList.addAll(_servletList);
      }
    }

    for (int i = 0; i < servletList.size(); i++) {
      ServletConfigImpl config = servletList.get(i);

      try {
	config.close();
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }
}
