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
 * @author Adam Megacz, Emil Ong
 */

package com.caucho.soa;

import java.util.ArrayList;

import java.util.logging.Logger;

import javax.annotation.*;

import com.caucho.config.types.InitProgram;

import com.caucho.soa.encoding.ServiceEncoding;
import com.caucho.soa.encoding.ServiceEncodingConfig;
import com.caucho.soa.rest.RestServletMapping;

import com.caucho.naming.Jndi;

import com.caucho.server.webapp.WebApp;

import com.caucho.server.util.CauchoSystem;

/**
 * A Web Service entry in web.xml (Caucho-specific)
 */
public class WebService {
  private static final Logger log 
    = Logger.getLogger(WebService.class.getName());

  private ArrayList<ServiceEncoding> _encodings
    = new ArrayList<ServiceEncoding>();
  private ArrayList<RestServletMapping> _restServlets
    = new ArrayList<RestServletMapping>();

  private InitProgram _init;
  private Object _service;
  private String _serviceClass;
  private String _jndiName;
  private WebApp _webApp;

  /**
   * Creates a new web service object.
   */
  public WebService(WebApp webApp)
  {
    _webApp = webApp;
  }

  public WebApp getWebApp()
  {
    return _webApp;
  }

  public void setJndiName(String jndiName)
  {
    _jndiName = jndiName;
  }

  public void setClass(String serviceClass)
  {
    _serviceClass = serviceClass;
  }

  public void setInit(InitProgram init)
  {
    _init = init;
  }

  public ServiceEncodingConfig createHessian()
    throws Throwable
  {
    ServiceEncodingConfig config = new ServiceEncodingConfig(this);
    config.setType("hessian");
    return config;
  }

  public void addHessian(ServiceEncoding encoding)
  {
    _encodings.add(encoding);
  }

  public ServiceEncodingConfig createSoap()
    throws Throwable
  {
    ServiceEncodingConfig config = new ServiceEncodingConfig(this);
    config.setType("soap");
    return config;
  }

  public void addSoap(ServiceEncoding encoding)
  {
    _encodings.add(encoding);
  }

  public RestServletMapping createRest()
    throws Throwable
  {
    return new RestServletMapping(this);
  }

  public void addRest(RestServletMapping restServlet)
    throws Throwable
  {
    _restServlets.add(restServlet);
  }

  @PostConstruct
  public void init()
    throws Throwable
  {
    for (ServiceEncoding encoding : _encodings) {
      encoding.setService(getServiceInstance());
      encoding.init();
    }

    for (RestServletMapping restServlet : _restServlets) {
      restServlet.setService(getServiceInstance());
      restServlet.init();
    }
  }

  Object getServiceInstance()
    throws Throwable
  {
    if (_service != null)
      return _service;

    if (_serviceClass == null)
      return null;

    Class cl = CauchoSystem.loadClass(_serviceClass);

    if (_init != null)
      _service = _init.create(cl);
    else
      _service = cl.newInstance();

    if (_jndiName != null)
      Jndi.bindDeepShort(_jndiName, _service);

    return _service;
  }
}
