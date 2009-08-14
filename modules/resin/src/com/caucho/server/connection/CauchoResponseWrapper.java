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

package com.caucho.server.connection;

import com.caucho.vfs.*;
import com.caucho.server.webapp.WebApp;
import com.caucho.server.cache.AbstractCacheEntry;
import com.caucho.server.cache.AbstractCacheFilterChain;

import java.io.*;
import java.util.*;
import java.security.*;
import javax.servlet.*;
import javax.servlet.http.*;

public class CauchoResponseWrapper implements CauchoResponse {
  // the wrapped response
  private HttpServletResponse _response;
  
  public CauchoResponseWrapper()
  {
  }
  
  public CauchoResponseWrapper(HttpServletResponse response)
  {
    if (response == null)
      throw new IllegalArgumentException();
    
    _response = response;
  }

  //
  // ServletResponse
  //
  
  public void setContentType(String type)
  {
    _response.setContentType(type);
  }
  
  public String getContentType()
  {
    return _response.getContentType();
  }

  public String getCharacterEncoding()
  {
    return _response.getCharacterEncoding();
  }
  
  public void setCharacterEncoding(String charset)
  {
    _response.setCharacterEncoding(charset);
  }
  
  public void setLocale(Locale locale)
  {
    _response.setLocale(locale);
  }
  
  public Locale getLocale()
  {
    return _response.getLocale();
  }
  
  public ServletOutputStream getOutputStream()
    throws IOException
  {
    return _response.getOutputStream();
  }
  
  public PrintWriter getWriter()
    throws IOException
  {
    return _response.getWriter();
  }
  
  public void setBufferSize(int size)
  {
    _response.setBufferSize(size);
  }
  
  public int getBufferSize()
  {
    return _response.getBufferSize();
  }
  
  public void flushBuffer()
    throws IOException
  {
    _response.flushBuffer();
  }
  
  public boolean isCommitted()
  {
    return _response.isCommitted();
  }
  
  public void reset()
  {
    _response.reset();
  }
  
  public void resetBuffer()
  {
    _response.resetBuffer();
  }
  
  public void setContentLength(int len)
  {
    _response.setContentLength(len);
  }
  
  public void disable()
  {
    _response.disable();
  }
  
  public void enable()
  {
    _response.enable();
  }
  
  public boolean isDisabled()
  {
    return _response.isDisabled();
  }

  //
  // HttpServletResponse
  //
  
  public void setStatus(int sc)
  {
    _response.setStatus(sc);
  }
  
  public void sendError(int sc, String msg)
    throws IOException
  {
    _response.sendError(sc, msg);
  }
  
  public void sendError(int sc)
    throws IOException
  {
    _response.sendError(sc);
  }
  
  public void sendRedirect(String location)
    throws IOException
  {
    _response.sendRedirect(location);
  }
  
  public void setHeader(String name, String value)
  {
    _response.setHeader(name, value);
  }
  
  public void addHeader(String name, String value)
  {
    _response.addHeader(name, value);
  }
  
  public boolean containsHeader(String name)
  {
    return _response.containsHeader(name);
  }
  
  public void setDateHeader(String name, long date)
  {
    _response.setDateHeader(name, date);
  }
  
  public void addDateHeader(String name, long date)
  {
    _response.addDateHeader(name, date);
  }
  
  public void setIntHeader(String name, int value)
  {
    _response.setIntHeader(name, value);
  }
  
  public void addIntHeader(String name, int value)
  {
    _response.addIntHeader(name, value);
  }
  
  public void addCookie(Cookie cookie)
  {
    _response.addCookie(cookie);
  }
  
  public String encodeURL(String url)
  {
    return _response.encodeURL(url);
  }
  
  public String encodeRedirectURL(String name)
  {
    return _response.encodeRedirectURL(name);
  }
  
  public void setStatus(int sc, String msg)
  {
    _response.setStatus(sc, msg);
  }
  
  public String encodeUrl(String url)
  {
    return _response.encodeUrl(url);
  }
  
  public String encodeRedirectUrl(String url)
  {
    return _response.encodeRedirectUrl(url);
  }
  
  public int getStatus()
  {
    return _response.getStatus();
  }
  
  public String getHeader(String name)
  {
    return _response.getHeader(name);
  }
  
  public Iterable<String> getHeaders(String name)
  {
    return _response.getHeaders(name);
  }
  
  public Iterable<String> getHeaderNames()
  {
    return _response.getHeaderNames();
  }

  //
  // CauchoResponse
  //

  public AbstractResponseStream getResponseStream()
  {
    if (_response instanceof CauchoResponse) {
      CauchoResponse cResponse = (CauchoResponse) _response;

      return cResponse.getResponseStream();
    }
    else
      return null;
  }
  
  public void setResponseStream(AbstractResponseStream os)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setResponseStream(os);
  }
  
  public boolean isCauchoResponseStream()
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    return cResponse.isCauchoResponseStream();
  }
  
  public void setFooter(String key, String value)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setFooter(key, value);
  }
  
  public void addFooter(String key, String value)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.addFooter(key, value);
  }
  
  public void close() throws IOException
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.close();
  }
  
  public boolean getForbidForward()
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    return cResponse.getForbidForward();
  }
  
  public void setForbidForward(boolean forbid)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setForbidForward(forbid);
  }
  
  public String getStatusMessage()
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    return cResponse.getStatusMessage();
  }
  
  public boolean hasError()
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    return cResponse.hasError();
  }
  
  public void setHasError(boolean error)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setHasError(error);
  }
  
  public void setSessionId(String id)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setSessionId(id);
  }
  
  public void killCache()
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.killCache();
  }
  
  public void setNoCache(boolean killCache)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setNoCache(killCache);
  }
  
  public void setPrivateCache(boolean isPrivate)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    cResponse.setPrivateCache(isPrivate);
  }
  
  public AbstractHttpResponse getAbstractHttpResponse()
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    return cResponse.getAbstractHttpResponse();
  }
  
  public void setCacheInvocation(AbstractCacheFilterChain cacheFilterChain)
  {
  }
  
  public void setMatchCacheEntry(AbstractCacheEntry cacheEntry)
  {
  }
  
  public ServletResponse getResponse()
  {
    return _response;
  }
  
  public TcpDuplexController upgradeProtocol(TcpDuplexHandler handler)
  {
    CauchoResponse cResponse = (CauchoResponse) _response;
    
    return cResponse.upgradeProtocol(handler);
  }
  
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _response + "]";
  }
}
