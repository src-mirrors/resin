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

package com.caucho.server.webapp;

import com.caucho.jca.UserTransactionProxy;
import com.caucho.jca.UserTransactionImpl;
import com.caucho.log.Log;
import com.caucho.servlet.comet.CometFilterChain;
import com.caucho.server.connection.CauchoResponse;
import com.caucho.server.connection.AbstractHttpRequest;
import com.caucho.server.connection.AbstractHttpResponse;
import com.caucho.server.connection.HttpServletResponseImpl;
import com.caucho.server.dispatch.AbstractFilterChain;
import com.caucho.server.log.AbstractAccessLog;
import com.caucho.transaction.TransactionManagerImpl;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the next filter in a filter chain.  The final filter will
 * be the servlet itself.
 */
public class WebAppFilterChain extends AbstractFilterChain {
  private static final Logger log
    = Logger.getLogger(WebAppFilterChain.class.getName());
  
  // Next filter chain
  private FilterChain _next;

  // app
  private WebApp _app;
  // transaction manager
  private TransactionManagerImpl _tm;
  // transaction proxy
  private UserTransactionProxy _utm;
  // error page manager
  private ErrorPageManager _errorPageManager;

  private ServletRequestListener []_requestListeners;

  private HashMap<String,String> _securityRoleMap;

  private ThreadLocal<ServletRequest> _requestThreadLocal;

  private AbstractAccessLog _accessLog;

  // true it's the top
  private boolean _isTop = true;

  /**
   * Creates a new FilterChainFilter.
   *
   * @param next the next filterChain
   * @param filter the user's filter
   */
  public WebAppFilterChain(FilterChain next, WebApp app)
  {
    this(next, app, true);
  }

  /**
   * Creates a new FilterChainFilter.
   *
   * @param next the next filterChain
   * @param filter the user's filter
   */
  public WebAppFilterChain(FilterChain next, WebApp app, boolean isTop)
  {
    _next = next;
    _app = app;
    _errorPageManager = app.getErrorPageManager();
    _isTop = isTop;
    _requestListeners = app.getRequestListeners();

    _requestThreadLocal = app.getRequestThreadLocal();

    if (_isTop)
      _accessLog = app.getAccessLog();

    try {
      if (_isTop) {
	_tm = TransactionManagerImpl.getInstance();
	_utm = UserTransactionProxy.getInstance();
      }
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  /**
   * Sets the security map.
   */
  public void setSecurityRoleMap(HashMap<String,String> map)
  {
    _securityRoleMap = map;
  }

  /**
   * Returns true if cacheable.
   */
  public FilterChain getNext()
  {
    return _next;
  }
  
  /**
   * Invokes the next filter in the chain or the final servlet at
   * the end of the chain.
   *
   * @param request the servlet request
   * @param response the servlet response
   * @since Servlet 2.3
   */
  public void doFilter(ServletRequest request,
                       ServletResponse response)
    throws ServletException, IOException
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    WebApp app = _app;
    
    UserTransactionImpl ut = null;
    if (_isTop)
      ut = _utm.getUserTransaction();
    
    try {
      thread.setContextClassLoader(app.getClassLoader());

      if (! app.enterWebApp() && app.getConfigException() == null) {
	if (response instanceof HttpServletResponse) {
	  HttpServletResponse res = (HttpServletResponse) response;

	  res.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
	}
	
	return;
      }

      /*
      if (_securityRoleMap != null && request instanceof AbstractHttpRequest)
	((AbstractHttpRequest) request).setRoleMap(_securityRoleMap);
      */

      for (int i = 0; i < _requestListeners.length; i++) {
	ServletRequestEvent event = new ServletRequestEvent(_app, request);
	
	_requestListeners[i].requestInitialized(event);
      }

      _next.doFilter(request, response);
    } catch (Throwable e) {
      _errorPageManager.sendServletError(e, request, response);
    } finally {
      app.exitWebApp();

      for (int i = _requestListeners.length - 1; i >= 0; i--) {
	try {
	  ServletRequestEvent event = new ServletRequestEvent(_app, request);
	
	  _requestListeners[i].requestDestroyed(event);
	} catch (Throwable e) {
	  log.log(Level.WARNING, e.toString(), e);
	}
      }

      if (_isTop) {
	((CauchoResponse) response).close();
	
	try {
	  if (ut != null)
	    ut.abortTransaction();
	} catch (Throwable e) {
	  log.log(Level.WARNING, e.toString(), e);
	}
      }

      // put finish() before access log so the session isn't tied up while
      // logging

      // needed for things like closing the session
      if (request instanceof AbstractHttpRequest)
        ((AbstractHttpRequest) request).finishInvocation();
        
      try {
	if (_accessLog != null) {
	  _accessLog.log((HttpServletRequest) request,
			 (HttpServletResponse) response,
			 _app);
	}
      } catch (Throwable e) {
	log.log(Level.FINE, e.toString(), e);
      }
      
      thread.setContextClassLoader(oldLoader);
    }
  }
  
  /**
   * Resumes the request for comet-style.
   *
   * @param request the servlet request
   * @param response the servlet response
   * @since Resin 3.1.3
   */
  @Override
  public boolean doResume(ServletRequest request,
			  ServletResponse response)
    throws ServletException, IOException
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();

    WebApp app = _app;
    
    try {
      thread.setContextClassLoader(app.getClassLoader());

      if (! app.enterWebApp())
	return false;

      if (_next instanceof CometFilterChain) {
	CometFilterChain next = (CometFilterChain) _next;

	return next.doResume(request, response);
      }
      else
	return false;
    } catch (Throwable e) {
      _errorPageManager.sendServletError(e, request, response);

      return false;
    } finally {
      app.exitWebApp();

      if (_isTop) {
	((HttpServletResponseImpl) response).close();
	
	try {
	  _utm.abortTransaction();
	} catch (Throwable e) {
	  log.log(Level.WARNING, e.toString(), e);
	}
      }

      // put finish() before access log so the session isn't tied up while
      // logging

      // needed for things like closing the session
      if (request instanceof AbstractHttpRequest)
        ((AbstractHttpRequest) request).finishInvocation();

      /*
      try {
	if (_accessLog != null) {
	  _accessLog.log((HttpServletRequest) request,
			 (HttpServletResponse) response,
			 _app);
	}
      } catch (Throwable e) {
	log.log(Level.FINE, e.toString(), e);
      }
      */
      
      thread.setContextClassLoader(oldLoader);
    }
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _app.getURL() + ", next=" + _next + "]";
  }
}
