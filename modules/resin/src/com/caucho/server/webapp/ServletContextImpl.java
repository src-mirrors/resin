/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.net.URL;

import javax.servlet.*;

import com.caucho.util.L10N;

import com.caucho.log.Log;

import com.caucho.vfs.WriteStream;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;

/**
 * Bare-bones servlet context implementation.
 */
public class ServletContextImpl implements ServletContext {
  static final Logger log = Log.open(ServletContextImpl.class);
  static final L10N L = new L10N(ServletContextImpl.class);

  private String _name;
  
  private HashMap<String,Object> _attributes = new HashMap<String,Object>();

  private ArrayList<ServletContextAttributeListener>
    _applicationAttributeListeners;

  private HashMap<String,String> _initParams = new HashMap<String,String>();

  public Path getAppDir()
  {
    return Vfs.lookup();
  }

  /**
   * Sets the servlet context name
   */
  public void setDisplayName(String name)
  {
    _name = name;
  }

  /**
   * Gets the servlet context name
   */
  public String getServletContextName()
  {
    return _name;
  }

  /**
   * Adds the listener.
   */
  protected void addAttributeListener(ServletContextAttributeListener listener)
  {
    if (_applicationAttributeListeners == null)
      _applicationAttributeListeners = new ArrayList<ServletContextAttributeListener>();
    
    _applicationAttributeListeners.add(listener);
  }

  /**
   * Returns the server information
   */
  public String getServerInfo()
  {
    return "Resin/" + com.caucho.Version.VERSION;
  }

  /**
   * Returns the servlet major version
   */
  public int getMajorVersion()
  {
    return 2;
  }

  /**
   * Returns the servlet minor version
   */
  public int getMinorVersion()
  {
    return 4;
  }

  /**
   * Sets an init param
   */
  protected void setInitParameter(String name, String value)
  {
    _initParams.put(name, value);
  }

  /**
   * Gets the init params
   */
  public String getInitParameter(String name)
  {
    return (String) _initParams.get(name);
  }

  /**
   * Gets the init params
   */
  public Enumeration getInitParameterNames()
  {
    return Collections.enumeration(_initParams.keySet());
  }

  /**
   * Returns the named attribute.
   */
  public Object getAttribute(String name)
  {
    synchronized (_attributes) {
      Object value = _attributes.get(name);

      return value;
    }
  }

  /**
   * Returns an enumeration of the attribute names.
   */
  public Enumeration getAttributeNames()
  {
    synchronized (_attributes) {
      return Collections.enumeration(_attributes.keySet());
    }
  }

  /**
   * Sets an application attribute.
   *
   * @param name the name of the attribute
   * @param value the value of the attribute
   */
  public void setAttribute(String name, Object value)
  {
    Object oldValue;

    synchronized (_attributes) {
      if (value != null)
        oldValue = _attributes.put(name, value);
      else
        oldValue = _attributes.remove(name);
    }
    
    // Call any listeners
    if (_applicationAttributeListeners != null) {
      ServletContextAttributeEvent event;

      if (oldValue != null)
        event = new ServletContextAttributeEvent(this, name, oldValue);
      else
        event = new ServletContextAttributeEvent(this, name, value);
        
      for (int i = 0; i < _applicationAttributeListeners.size(); i++) {
        ServletContextAttributeListener listener;

        Object objListener = _applicationAttributeListeners.get(i);
        listener = (ServletContextAttributeListener) objListener;

        try {
          if (oldValue != null)
            listener.attributeReplaced(event);
          else
            listener.attributeAdded(event);
        } catch (Throwable e) {
          log.log(Level.FINE, e.toString(), e);
        }
      }
    }
  }

  /**
   * Removes an attribute from the servlet context.
   *
   * @param name the name of the attribute to remove.
   */
  public void removeAttribute(String name)
  {
    Object oldValue;
    
    synchronized (_attributes) {
      oldValue = _attributes.remove(name);
    }

    // Call any listeners
    if (_applicationAttributeListeners != null) {
      ServletContextAttributeEvent event;

      event = new ServletContextAttributeEvent(this, name, oldValue);
        
      for (int i = 0; i < _applicationAttributeListeners.size(); i++) {
        ServletContextAttributeListener listener;

        Object objListener = _applicationAttributeListeners.get(i);
        listener = (ServletContextAttributeListener) objListener;

        try {
          listener.attributeRemoved(event);
        } catch (Throwable e) {
          log.log(Level.FINE, e.toString(), e);
        }
      }
    }
  }

  /**
   * Maps from a URI to a real path.
   */
  public String getRealPath(String uri)
  {
    return getAppDir().lookup("./" + uri).getNativePath();
  }

  /**
   * Returns a resource for the given uri.
   *
   * <p>XXX: jdk 1.1.x doesn't appear to allow creation of private
   * URL streams.
   */
  public URL getResource(String name)
    throws java.net.MalformedURLException
  {
    if (! name.startsWith("/"))
      throw new java.net.MalformedURLException(name);
    
    String realPath = getRealPath(name);

    Path path = getAppDir().lookupNative(realPath);

    if (path.canRead())
      return new URL(path.getURL());

    return null;
  }

  /**
   * Returns the resource for a uripath as an input stream.
   */
  public InputStream getResourceAsStream(String uripath)
  {
    Path path = getAppDir().lookupNative(getRealPath(uripath));

    try {
      if (path.canRead())
        return path.openRead();
      else
        return null;
    } catch (IOException e) {
      log.log(Level.FINEST, e.toString(), e);

      return null;
    }
  }

  /**
    * Returns an enumeration of all the resources.
    */
  public Set getResourcePaths(String prefix)
  {
    if (! prefix.endsWith("/"))
      prefix = prefix + "/";

    Path path = getAppDir().lookup(getRealPath(prefix));

    HashSet<String> set = new HashSet<String>();

    try {
      String []list = path.list();

      for (int i = 0; i < list.length; i++) {
        if (path.lookup(list[i]).isDirectory())
          set.add(prefix + list[i] + '/');
        else
          set.add(prefix + list[i]);
      }
    } catch (IOException e) {
    }
     
    return set;
  }

  /**
   * Returns the servlet context for the name.
   */
  public ServletContext getContext(String uri)
  {
    return this;
  }

  /**
   * Returns the mime type for the name.
   */
  public String getMimeType(String uri)
  {
    return null;
  }

  /**
   * Returns the dispatcher.
   */
  public RequestDispatcher getRequestDispatcher(String uri)
  {
    return null;
  }

  /**
   * Returns a dispatcher for the named servlet.
   */
  public RequestDispatcher getNamedDispatcher(String servletName)
  {
    return null;
  }

  /**
   * Logging.
   */

  /**
   * Logs a message to the error file.
   *
   * @param msg the message to log
   */
  public final void log(String message) 
  {
    log(message, null);
  }

  /**
   * @deprecated
   */
  public final void log(Exception e, String msg)
  {
    log(msg, e);
  }

  /**
   * Error logging
   *
   * @param message message to log
   * @param e stack trace of the error
   */
  public void log(String message, Throwable e)
  {
    if (e != null)
      log.log(Level.WARNING, message, e);
    else
      log.info(message);
  }

  //
  // Deprecated methods
  //

  public Servlet getServlet(String name)
  {
    throw new UnsupportedOperationException("getServlet is deprecated");
  }

  public Enumeration getServletNames()
  {
    throw new UnsupportedOperationException("getServletNames is deprecated");
  }

  public Enumeration getServlets()
  {
    throw new UnsupportedOperationException("getServlets is deprecated");
  }
}
