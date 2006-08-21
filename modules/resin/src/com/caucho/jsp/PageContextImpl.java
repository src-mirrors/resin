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

package com.caucho.jsp;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.text.*;

import javax.el.*;

import javax.servlet.*;
import javax.servlet.http.*;
import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;
import javax.servlet.jsp.jstl.core.*;
import javax.servlet.jsp.jstl.fmt.*;

import javax.servlet.jsp.el.ExpressionEvaluator;
import javax.servlet.jsp.el.VariableResolver;
import javax.servlet.jsp.el.ELException;

import org.w3c.dom.Node;

import com.caucho.el.*;
import com.caucho.util.*;

import com.caucho.vfs.Path;
import com.caucho.vfs.WriteStream;
import com.caucho.vfs.WriterStreamImpl;
import com.caucho.vfs.TempCharBuffer;
import com.caucho.vfs.FlushBuffer;
import com.caucho.vfs.ClientDisconnectException;

import com.caucho.java.LineMap;
import com.caucho.java.LineMapException;

import com.caucho.el.Expr;
import com.caucho.el.ExprEnv;
import com.caucho.el.ELParseException;
import com.caucho.el.EL;

import com.caucho.log.Log;

import com.caucho.xpath.VarEnv;

import com.caucho.server.webapp.WebApp;

import com.caucho.server.connection.AbstractHttpRequest;
import com.caucho.server.connection.ResponseWriter;
import com.caucho.server.connection.CauchoRequest;
import com.caucho.server.connection.CauchoResponse;
import com.caucho.server.connection.ResponseAdapter;
import com.caucho.server.connection.ToCharResponseAdapter;
import com.caucho.server.connection.RequestAdapter;
import com.caucho.server.connection.AbstractResponseStream;

import com.caucho.server.webapp.RequestDispatcherImpl;

import com.caucho.jsp.cfg.JspPropertyGroup;

import com.caucho.jsp.el.ExpressionEvaluatorImpl;

import com.caucho.jstl.JstlPageContext;

public class PageContextImpl extends PageContext
  implements ExprEnv, JstlPageContext, VariableResolver {
  private static final Logger log = Log.open(PageContextImpl.class);
  static final L10N L = new L10N(PageContextImpl.class);
  
  private JspWriterAdapter _jspAdapter = new JspWriterAdapter();
  private JspServletOutputStream _jspOutputStream =
    new JspServletOutputStream(this);

  private Map<String,Object> _attributes;
  private Servlet _servlet;
  private HttpServletRequest _request;

  private CauchoResponse _response;
  private ToCharResponseAdapter _responseAdapter;
  
  private WebApp _webApp;
  private HttpSession _session;
  private JspWriter _topOut;
  private JspWriter _out;
  private String _errorPage;
  protected boolean _isFilled;
  
  private AbstractResponseStream _responseStream;

  private BodyResponseStream _bodyResponseStream;
  
  private JspPrintWriter _jspPrintWriter;
  
  private boolean autoFlush;
  private BodyContentImpl _bodyOut;

  private BundleManager _bundleManager;

  private VarEnv _varEnv;
  private Node _nodeEnv;

  private final CharBuffer _cb = new CharBuffer();

  private VariableResolver _varResolver;
  private ELContext _elContext;
  private ELResolver _elResolver;
  private boolean _hasException;

  private ExpressionEvaluatorImpl _expressionEvaluator;

  PageContextImpl()
  {
    _attributes = new HashMapImpl<String,Object>();
    
    _bodyResponseStream = new BodyResponseStream();
    _bodyResponseStream.start();
    
    _jspPrintWriter = new JspPrintWriter();
  }

  public void initialize(Servlet servlet,
			 ServletRequest request,
			 ServletResponse response,
			 String errorPage,
			 boolean needsSession,
			 int bufferSize,
			 boolean autoFlush)
  {
    HttpSession session = null;
    
    if (needsSession)
      session = ((HttpServletRequest) request).getSession(true);

    ServletConfig config = servlet.getServletConfig();
    WebApp app = (WebApp) config.getServletContext();

    initialize(servlet, app, request, response,
	       errorPage, session, bufferSize, autoFlush,
	       false);
  }

  public void initialize(Servlet servlet,
			 WebApp app,
			 ServletRequest request,
			 ServletResponse response,
			 String errorPage,
			 HttpSession session,
			 int bufferSize,
			 boolean autoFlush,
			 boolean isPrintNullAsBlank)
  {
    _servlet = servlet;
    _request = (HttpServletRequest) request;

    if (response instanceof CauchoResponse &&
	bufferSize <= TempCharBuffer.SIZE) {
      _response = (CauchoResponse) response;
      _responseAdapter = null;
    }
    else {
      // JSP.12.2.3 - JSP must use PrintWriter
      _responseAdapter = ToCharResponseAdapter.create((HttpServletResponse) response);
      _response = _responseAdapter;
    }
    
    _responseStream = _response.getResponseStream();
    _topOut = _jspAdapter;
    _responseStream.setAutoFlush(autoFlush);
    _jspAdapter.init(null, _responseStream);
    _jspAdapter.setPrintNullAsBlank(isPrintNullAsBlank);

    if (bufferSize != TempCharBuffer.SIZE) {
      try {
	_responseStream.setBufferSize(bufferSize);
      } catch (Throwable e) {
	log.log(Level.FINE, e.toString(), e);
      }
    }
    
    // needed for includes from static pages

    this.autoFlush = autoFlush;
    _session = session;

    _out = _topOut;
    
    _errorPage = errorPage;
    _webApp = app;

    //_topOut.init(this, bufferSize, autoFlush);

    // jsp/17ee
    //_jspPrintWriter.init(_topOut);
    //_response.setPrintWriter(_jspPrintWriter);

    // not quite correct
    //  _writerStreamImpl.setWriter(_topOut);
    // _response.setPrintWriter(body.getWriter());

    /*
    _elContext = null;
    _elResolver = null;
    */

    _hasException = false;
    //if (_attributes.size() > 0)
    //  _attributes.clear();
    _isFilled = false;
    _bundleManager = null;
    _varResolver = null;
    _nodeEnv = null;
  }

  protected void setOut(JspWriter out)
  {
    _out = out;
  }

  protected void clearAttributes()
  {
    _attributes.clear();
  }

  /**
   * Returns the page attribute with the given name.
   *
   * @param name the attribute name.
   *
   * @return the attribute's value.
   */
  public Object getAttribute(String name)
  {
    if (name == null)
      throw new NullPointerException(L.l("getAttribute must have a non-null name"));
    
    Object value = _attributes.get(name);
    if (value != null)
      return value;
    else if (! _isFilled) {
      fillAttribute();
      value = _attributes.get(name);
    }

    if (value != null) {
    }
    else if (name.equals(OUT)) {
      // jsp/162d
      return _out;
    }

    return value;
  }

  /**
   * Sets the page attribute with the given name.
   *
   * @param name the attribute name.
   * @param value the new value
   */
  public void setAttribute(String name, Object attribute)
  {
    if (name == null)
      throw new NullPointerException(L.l("setAttribute must have a non-null name"));
    
    if (attribute != null)
      _attributes.put(name, attribute);
    else
      _attributes.remove(name);
  }

  /**
   * Sets the page attribute with the given name.
   *
   * @param name the attribute name.
   * @param value the new value
   */
  public Object putAttribute(String name, Object attribute)
  {
    if (name == null)
      throw new NullPointerException(L.l("putAttribute must have a non-null name"));
    
    if (attribute != null)
      return _attributes.put(name, attribute);
    else
      return _attributes.remove(name);
  }

  /**
   * Removes a named attribute from the page context.
   *
   * @param name the name of the attribute to remove
   */
  public void removeAttribute(String name)
  {
    if (name == null)
      throw new NullPointerException(L.l("removeAttribute must have a non-null name"));
    
    _attributes.remove(name);
    // jsp/162b
    if (_request != null)
      _request.removeAttribute(name);

    if (_session != null) {
      try {
	_session.removeAttribute(name);
      } catch (IllegalStateException e) {
	// jsp/162f
	log.log(Level.FINE, e.toString(), e);
      }
    }
    
    if (_webApp != null)
      _webApp.removeAttribute(name);
  }

  public Enumeration<String> getAttributeNames()
  {
    if (! _isFilled)
      fillAttribute();

    return Collections.enumeration(_attributes.keySet());
  }

  /**
   * Fills the predefined page content _attributes with their values.
   */
  protected void fillAttribute()
  {
    _isFilled = true;
    _attributes.put(PAGE, _servlet);
    _attributes.put(PAGECONTEXT, this);
    _attributes.put(REQUEST, getCauchoRequest());
    _attributes.put(RESPONSE, getCauchoResponse());
    if (_servlet != null)
      _attributes.put(CONFIG, _servlet.getServletConfig());
    if (getSession() != null)
      _attributes.put(SESSION, getSession());
    _attributes.put(APPLICATION, getApplication());
  }

  public Object getAttribute(String name, int scope)
  {
    switch (scope) {
    case PAGE_SCOPE:
      return getAttribute(name);
    case REQUEST_SCOPE:
      return getCauchoRequest().getAttribute(name);
    case SESSION_SCOPE:
      {
	HttpSession session = getSession();
	return session != null ? session.getValue(name) : null;
      }
    case APPLICATION_SCOPE:
      return getApplication().getAttribute(name);

    default:
      throw new IllegalArgumentException();
    }
  }

  public void setAttribute(String name, Object value, int scope)
  {
    switch (scope) {
    case PAGE_SCOPE:
      setAttribute(name, value);
      break;

    case REQUEST_SCOPE:
      getCauchoRequest().setAttribute(name, value);
      break;

    case SESSION_SCOPE:
      if (getSession() != null)
        getSession().putValue(name, value);
      break;

    case APPLICATION_SCOPE:
      getApplication().setAttribute(name, value);
      break;

    default:
      throw new IllegalArgumentException();
    }
  }

  public void removeAttribute(String name, int scope)
  {
    switch (scope) {
    case PAGE_SCOPE:
      if (name != null)
	_attributes.remove(name);
      break;

    case REQUEST_SCOPE:
      getCauchoRequest().removeAttribute(name);
      break;

    case SESSION_SCOPE:
      if (getSession() != null)
        getSession().removeValue(name);
      break;

    case APPLICATION_SCOPE:
      getApplication().removeAttribute(name);
      break;

    default:
      throw new IllegalArgumentException();
    }
  }

  public Enumeration getAttributeNames(int scope)
  {
    switch (scope) {
    case PAGE_SCOPE:
      return getAttributeNames();

    case REQUEST_SCOPE:
      return getCauchoRequest().getAttributeNames();

    case SESSION_SCOPE:
      if (getSession() != null)
        return new StringArrayEnum(getSession().getValueNames());
      else
        return NullEnumeration.create();

    case APPLICATION_SCOPE:
      return getApplication().getAttributeNames();

    default:
      throw new IllegalArgumentException();
    }
  }

  public Enumeration getAttributeNamesInScope(int scope)
  {
    return getAttributeNames(scope);
  }

  /**
   * Finds an attribute in any of the scopes from page to webApp.
   *
   * @param name the attribute name.
   *
   * @return the attribute value
   */
  public Object findAttribute(String name)
  {
    Object value;

    if ((value = getAttribute(name)) != null)
      return value;

    if ((value = getCauchoRequest().getAttribute(name)) != null)
      return value;

    HttpSession session = getSession();
    if (session != null) {
      try {
	value = session.getAttribute(name);
      } catch (IllegalStateException e) {
	// jsp/162e
	log.log(Level.FINE, e.toString(), e);
      }

      if (value != null)
	return value;
    }

    return getServletContext().getAttribute(name);
  }

  /**
   * Return the scope of the named attribute.
   *
   * @param name the name of the attribute.
   *
   * @return the scope of the attribute
   */
  public int getAttributesScope(String name)
  {
    if (getAttribute(name) != null)
      return PAGE_SCOPE;

    if (getCauchoRequest().getAttribute(name) != null)
      return REQUEST_SCOPE;

    HttpSession session = getSession();
    if (session != null && session.getValue(name) != null)
      return SESSION_SCOPE;

    if (getApplication().getAttribute(name) != null)
      return APPLICATION_SCOPE;

    return 0;
  }

  /**
   * Sets the attribute map.
   */
  public Map<String,Object> setMap(Map<String,Object> map)
  {
    Map<String,Object> oldMap = _attributes;
    _attributes = map;
    return oldMap;
  }

  /**
   * Returns the current writer.
   */
  public JspWriter getOut()
  {
    return _out;
  }

  /**
   * Pushes a new BodyContent onto the JspWriter stack.
   */
  public BodyContent pushBody()
  {
    BodyContentImpl body;
    if (_bodyOut != null) {
      body = _bodyOut;
      _bodyOut = null;
    }
    else
      body = BodyContentImpl.allocate();

    CauchoResponse response = getCauchoResponse();
    
    body.init(_out);
    
    _out = body;

    response.setForbidForward(true);
    try {
      _bodyResponseStream.flushBuffer();
    } catch (IOException e) {
    }
    _bodyResponseStream.start();
    _bodyResponseStream.setWriter(body);
    _bodyResponseStream.setEncoding(response.getCharacterEncoding());
    response.setResponseStream(_bodyResponseStream);
    
    return body;
  }

  /**
   * Pushes a new writer onto the JspWriter stack.
   */
  public JspWriter pushBody(Writer writer)
  {
    if (writer == _out)
      return null;
    
    JspWriter oldWriter = _out;
    
    StreamJspWriter jspWriter;

    jspWriter = new StreamJspWriter();
    jspWriter.init(_out, writer);
    
    _out = jspWriter;

    getCauchoResponse().setForbidForward(true);

    _bodyResponseStream.setWriter(writer);
    getCauchoResponse().setResponseStream(_bodyResponseStream);

    return oldWriter;
  }

  /**
   * Pops the BodyContent from the JspWriter stack.
   *
   * @return the enclosing writer
   */
  public JspWriter popBody()
  {
    BodyContentImpl bodyOut = (BodyContentImpl) _out;
    _out = bodyOut.getEnclosingWriter();

    try {
      _bodyResponseStream.flushBuffer();
      //if (_writeStream != null)
      // _writeStream.flushBuffer();
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }
    
    if (_out instanceof StreamJspWriter) {
      StreamJspWriter writer = (StreamJspWriter) _out;

      _bodyResponseStream.setWriter(writer.getWriter());
    }
    else if (_out instanceof JspWriterAdapter) {
      if (getCauchoResponse() != null) {
	getCauchoResponse().setResponseStream(_responseStream);
	getCauchoResponse().setForbidForward(false);
      }
    }
    else if (_out instanceof BodyContentImpl) {
      BodyContentImpl body = (BodyContentImpl) _out;
      
      _bodyResponseStream.setWriter(body.getWriter());
    }

    return _out;
  }

  /**
   * Pops the BodyContent from the JspWriter stack.
   *
   * @return the enclosing writer
   */
  public JspWriter popAndReleaseBody()
    throws IOException
  {
    BodyContentImpl body = (BodyContentImpl) getOut();

    JspWriter out = popBody();

    releaseBody(body);

    return out;
  }

  public void releaseBody(BodyContentImpl out)
    throws IOException
  {
    if (_bodyOut == null) {
      out.releaseNoFree();
      _bodyOut = out;
    }
    else
      out.release();
  }

  /**
   * Pops the BodyContent from the JspWriter stack.
   *
   * @param oldWriter the old writer
   */
  public JspWriter setWriter(JspWriter oldWriter)
  {
    if (_out == oldWriter)
      return oldWriter;

    /*
    if (_out instanceof FlushBuffer) {
      try {
	((FlushBuffer) _out).flushBuffer();
      } catch (IOException e) {
      }
    }
    */
    try {
      if (_out instanceof FlushBuffer)
	((FlushBuffer) _out).flushBuffer();
    } catch (IOException e) {
    }
    
    _out = oldWriter;

    // jsp/18eg
    if (_out instanceof StreamJspWriter) {
      StreamJspWriter writer = (StreamJspWriter) _out;

      _bodyResponseStream.setWriter(writer.getWriter());
    }
    else if (_out instanceof JspWriterAdapter) {
      if (getCauchoResponse() != null) {
	getCauchoResponse().setResponseStream(_responseStream);
	getCauchoResponse().setForbidForward(false);
      }
    }
    else if (_out instanceof BodyContentImpl) {
      BodyContentImpl body = (BodyContentImpl) _out;
      
      _bodyResponseStream.setWriter(body.getWriter());
    }

    return oldWriter;

    // getCauchoResponse().setWriter(_os);
  }

  /**
   * Returns the top writer.
   */
  public PrintWriter getTopWriter()
    throws IOException
  {
    CauchoResponse response = getCauchoResponse();
      
    AbstractResponseStream currentStream = response.getResponseStream();
    
    response.setResponseStream(_responseStream);

    try {
      return response.getWriter();
    } finally {
      response.setResponseStream(currentStream);
    }
  }

  /**
   * Returns the response output stream.
   */
  ServletOutputStream getOutputStream()
  {
    try {
      return getCauchoResponse().getOutputStream();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the underlying servlet for the page.
   */
  public Object getPage()
  {
    return _servlet;
  }

  /**
   * Returns the servlet request for the page.
   */
  public ServletRequest getRequest()
  {
    return _request;
  }
  
  /**
   * Returns the servlet response for the page.
   */
  public ServletResponse getResponse()
  {
    return getCauchoResponse();
  }
  
  /**
   * Returns the servlet response for the page.
   */
  public CauchoResponse getCauchoResponse()
  {
    return _response;
  }
  
  /**
   * Returns the servlet response for the page.
   */
  public HttpServletRequest getCauchoRequest()
  {
    return _request;
  }

  public HttpSession getSession()
  {
    if (_session == null)
      _session = getCauchoRequest().getSession(false);
    
    return _session;
  }

  /**
   * Returns the session, throwing an IllegalStateException if it's
   * not available.
   */
  public HttpSession getSessionScope()
  {
    if (_session == null)
      _session = getCauchoRequest().getSession(false);

    if (_session == null)
      throw new IllegalStateException(L.l("session is not available"));
    
    return _session;
  }

  public ServletConfig getServletConfig()
  {
    return _servlet.getServletConfig();
  }

  /**
   * Returns the page's servlet context.
   */
  public ServletContext getServletContext()
  {
    return _webApp;
  }

  /**
   * Returns the page's webApp.
   */
  public WebApp getApplication()
  {
    return _webApp;
  }

  /**
   * Returns the page's error page.
   */
  public String getErrorPage()
  {
    return _errorPage;
  }

  /**
   * Sets the page's error page.
   */
  public void setErrorPage(String errorPage)
  {
    _errorPage = errorPage;
  }
  
  public Exception getException()
  {
    return (Exception) getThrowable();
  }

  /**
   * Returns the Throwable stored by the error page.
   */
  public Throwable getThrowable()
  {
    Throwable exn = (Throwable) getCauchoRequest().getAttribute(EXCEPTION);
    
    if (exn == null)
      exn = (Throwable) getCauchoRequest().getAttribute("javax.servlet.error.exception_type");
    if (exn == null)
      exn = (Throwable) getCauchoRequest().getAttribute("javax.servlet.jsp:jspException");
    
    return exn;
  }

  public void include(String relativeUrl)
    throws ServletException, IOException
  {
    include(relativeUrl, true);
  }
  
  /**
   * Include another servlet into the current output stream.
   *
   * @param relativeUrl url relative to the current request.
   */
  public void include(String relativeUrl, String query, boolean flush)
    throws ServletException, IOException
  {
    if ("".equals(query)) {
    }
    else if (relativeUrl.indexOf('?') > 0)
      relativeUrl = relativeUrl + '&' + query;
    else
      relativeUrl = relativeUrl + '?' + query;

    include(relativeUrl, flush);
  }
  
  /**
   * Include another servlet into the current output stream.
   *
   * @param relativeUrl url relative to the current request.
   */
  public void include(String relativeUrl, boolean flush)
    throws ServletException, IOException
  {
    RequestDispatcher rd = null;

    HttpServletRequest req = (HttpServletRequest) getCauchoRequest();
    HttpServletResponse res = (HttpServletResponse) getResponse();

    if (relativeUrl != null && ! relativeUrl.startsWith("/")) {
      String path = RequestAdapter.getPageServletPath(req);
      if (path == null)
        path = RequestAdapter.getPagePathInfo(req);
      if (path == null)
        path = "/";
      int p = path.lastIndexOf('/');
      if (p >= 0) {
	_cb.clear();
	_cb.append(path, 0, p + 1);
	_cb.append(relativeUrl);
        rd = getServletContext().getRequestDispatcher(_cb.toString());
      }
    }

    if (rd == null)
      rd = req.getRequestDispatcher(relativeUrl);

    if (rd == null)
      throw new ServletException(L.l("unknown including page `{0}'.",
                                     relativeUrl));

    // the FlushBuffer needs to happen to deal with OpenSymphony (Bug#1710)
    // jsp/17e9
    if (_out instanceof FlushBuffer)
      ((FlushBuffer) _out).flushBuffer();
    else if (flush)
      _out.flush();

    rd.include(req, res);
  }
  
  /**
   * Include another servlet into the current output stream.
   *
   * @param relativeUrl url relative to the current request.
   */
  public void forward(String relativeUrl, String query)
    throws ServletException, IOException
  {
    if ("".equals(query)) {
    }
    else if (relativeUrl.indexOf('?') > 0)
      relativeUrl = relativeUrl + '&' + query;
    else
      relativeUrl = relativeUrl + '?' + query;

    forward(relativeUrl);
  }

  /**
   * Forward a subrequest relative to the current url.  Absolute URLs
   * are relative to the context root.
   *
   * @param relativeUrl url relative to the current file
   */
  public void forward(String relativeUrl)
    throws ServletException, IOException
  {
    _out.clearBuffer();

    RequestDispatcher rd = null;

    HttpServletRequest req = (HttpServletRequest) getCauchoRequest();
    HttpServletResponse res = (HttpServletResponse) getResponse();

    if (relativeUrl != null && ! relativeUrl.startsWith("/")) {
      String servletPath = RequestAdapter.getPageServletPath(req);
      int p = servletPath.lastIndexOf('/');
      if (p >= 0) {
	_cb.clear();
	_cb.append(servletPath, 0, p + 1);
	_cb.append(relativeUrl);
        rd = getServletContext().getRequestDispatcher(_cb.toString());
      }
    }

    if (rd == null)
      rd = req.getRequestDispatcher(relativeUrl);

    if (rd == null)
      throw new ServletException(L.l("unknown forwarding page: `{0}'",
                                     relativeUrl));
    rd.forward(req, res);
  }

  /**
   * Handles an exception caught in the JSP page.
   *
   * @param e the caught exception
   */
  public void handlePageException(Exception e)
    throws ServletException, IOException
  {
    handlePageException((Throwable) e);
  }

  /**
   * Handles an exception caught in the JSP page.
   *
   * @param e the caught exception
   */
  public void handlePageException(Throwable e)
    throws ServletException, IOException
  {
    if (e instanceof SkipPageException)
      return;

    HttpServletRequest request = getCauchoRequest();
    
    request.setAttribute("javax.servlet.jsp.jspException", e);

    CauchoResponse response = getCauchoResponse();
    
    response.setForbidForward(false);
    response.setResponseStream(_responseStream);
    response.killCache();
    response.setNoCache(true);

    _hasException = true;

    if (e instanceof ClientDisconnectException)
      throw (ClientDisconnectException) e;

    if (! (_servlet instanceof Page)) {
    }
    else if (getApplication() == null
	     || getApplication().getJsp() == null
	     || ! getApplication().getJsp().isRecompileOnError()) {
    }
    else if (e instanceof OutOfMemoryError) {
    }
    else if (e instanceof Error) {
      try {
	Path workDir = getApplication().getAppDir().lookup("WEB-INF/work");
	String className = _servlet.getClass().getName();
	Path path = workDir.lookup(className.replace('.', '/') + ".class");
	
	log.warning("Removing " + path + " due to " + e);
		    
	path.remove();
      } catch (Exception e1) {
      }
      Page page = (Page) _servlet;

      page._caucho_unload();
      if (! page.isDead()) {
	page.setDead();
	page.destroy();
      }
    }

    _topOut.clearBuffer();
    
    if (_errorPage != null) {
      getApplication().log(e.toString(), e);

      getCauchoRequest().setAttribute(EXCEPTION, e);
      getCauchoRequest().setAttribute("javax.servlet.error.exception_type", e);
      getCauchoRequest().setAttribute("javax.servlet.error.request_uri",
                            getCauchoRequest().getRequestURI());

      try {
	RequestDispatcher rd = getCauchoRequest().getRequestDispatcher(_errorPage);

        if (rd instanceof RequestDispatcherImpl) {
	  getCauchoResponse().setHasError(true);
	  
          ((RequestDispatcherImpl) rd).error(getCauchoRequest(), getCauchoResponse());
        }
        else {
	  if (rd != null) {
	    getCauchoResponse().killCache();
	    getCauchoResponse().setNoCache(true);
	    rd.forward(getCauchoRequest(), getCauchoResponse());
	  }
	  else {
	    log.log(Level.FINE, e.toString(), e);
	    throw new ServletException(L.l("`{0}' is an unknown error page.  The JSP errorPage directive must refer to a valid URL relative to the current web-app.",
					   _errorPage));
	  }
        }
        
      } catch (FileNotFoundException e2) {
        log.log(Level.WARNING, e.toString(), e2);
        throw new ServletException(L.l("`{0}' is an unknown error page.  The JSP errorPage directive must refer to a valid URL relative to the current web-app.",
                                       _errorPage));
      } catch (IOException e2) {
        log.log(Level.FINE, e.toString(), e2);
      }

      return;
    }

    /*
    if (_servlet instanceof Page && ! (e instanceof LineMapException)) {
      LineMap lineMap = ((Page) _servlet)._caucho_getLineMap();
      
      if (lineMap != null)
        e = new JspLineException(e, lineMap);        
    }
    */
    
    if (e instanceof ServletException) {
      throw (ServletException) e;
    }
    else if (e instanceof IOException) {
      throw (IOException) e;
    }
    else if (e instanceof RuntimeException) {
      throw (RuntimeException) e;
    }
    else if (e instanceof Error) {
      throw (Error) e;
    }
    else {
      throw new ServletException(e);
    }
  }

  /**
   * Returns the error data
   */
  public ErrorData getErrorData()
  {
    String uri = (String) getCauchoRequest().getAttribute(AbstractHttpRequest.ERROR_URI);

    if (uri == null)
      return null;

    Integer status = (Integer) getCauchoRequest().getAttribute(AbstractHttpRequest.STATUS_CODE);

    return new ErrorData(getThrowable(),
			 status == null ? 0 : status.intValue(),
			 (String) getCauchoRequest().getAttribute(AbstractHttpRequest.ERROR_URI),
			 (String) getCauchoRequest().getAttribute(AbstractHttpRequest.SERVLET_NAME));
  }

  /**
   * Returns the variable resolver
   */
  public javax.servlet.jsp.el.VariableResolver getVariableResolver()
  {
    return this;
  }

  /**
   * Returns the expression evaluator
   */
  public ExpressionEvaluator getExpressionEvaluator()
  {
    if (_expressionEvaluator == null)
      _expressionEvaluator = new ExpressionEvaluatorImpl();
    
    return _expressionEvaluator;
  }

  /**
   * Returns the expression evaluator
   */
  public ELContext getELContext()
  {
    if (_elContext == null) {
      _elContext = new PageELContext();
      _elResolver = new PageELResolver();
    }
    
    return _elContext;
  }

  /**
   * Given a relative url, return the absolute url.
   *
   * @param value the relative url
   *
   * @return the absolute url.
   */
  private String getRelativeUrl(String value)
  {
    if (value.length() > 0 && value.charAt(0) == '/')
      return value;

    ServletContext context = getServletContext();
    String contextPath = RequestAdapter.getPageContextPath(getCauchoRequest());
    String uri = RequestAdapter.getPageURI(getCauchoRequest());
    String relPath = uri.substring(contextPath.length());

    int p = relPath.lastIndexOf('/');
    String urlPwd = p <= 0 ? "/" : relPath.substring(0, p + 1);
    
    return urlPwd + value;
  }

  /**
   * Releases the context.
   */
  public void release()
  {
    try {
      _servlet = null;
      
      if (_attributes.size() > 0)
	_attributes.clear();

      /* XXX:
      if (! autoFlush && response instanceof Response)
        ((Response) response).setDisableAutoFlush(false);
      */

      getCauchoResponse().setResponseStream(_responseStream);
      getCauchoResponse().setFlushBuffer(null);

      _request = null;
      _webApp = null;
      _session = null;
      while (_out instanceof AbstractJspWriter) {
	if (_out instanceof AbstractJspWriter)
	  _out = ((AbstractJspWriter) _out).popWriter();
      }

      JspWriter out = _out;
      _out = null;
      _topOut = null;
      _nodeEnv = null;
      _jspOutputStream.release();
      AbstractResponseStream responseStream = _responseStream;
      _responseStream = null;

      if (_responseAdapter != null) {
        // jsp/15l3
        _responseAdapter.finish();
	// _responseAdapter.close();
	ToCharResponseAdapter resAdapt = _responseAdapter;
	ToCharResponseAdapter.free(resAdapt);
      }

      /*
	// server/137q
      if (! _hasException && responseStream != null)
      	responseStream.close();
      */
	
      _response = null;
    } catch (IOException e) {
      _out = null;
    }
  }
  
  /**
   * Returns the localized message appropriate for the current context.
   */
  public String getLocalizedMessage(String key,
                                    Object []args,
                                    String basename)
  {
    Object lc = getAttribute("caucho.bundle");
    if (lc == null)
      lc = Config.find(this, Config.FMT_LOCALIZATION_CONTEXT);
    
    return getLocalizedMessage(lc, key, args, basename);
  }

  /**
   * Returns the localized message appropriate for the current context.
   */
  public String getLocalizedMessage(Object lc,
                                    String key,
                                    Object []args,
                                    String basename)
  {
    String bundleString = null;

    // jsp/1c51, jsp/1c54
    String prefix = (String) getAttribute("caucho.bundle.prefix");

    if (prefix != null)
      key = prefix + key;

    if (lc == null) {
      lc = getAttribute("caucho.bundle");
      if (lc == null)
        lc = Config.find(this, Config.FMT_LOCALIZATION_CONTEXT);
    }

    Locale locale = null;

    if (lc instanceof LocalizationContext) {
      ResourceBundle bundle = ((LocalizationContext) lc).getResourceBundle();
      locale = ((LocalizationContext) lc).getLocale();

      try {
        if (bundle != null)
          bundleString = bundle.getString(key);
      } catch (Exception e) {
      }
    }
    else if (lc instanceof String) {
      LocalizationContext loc = getBundle((String) lc);
      locale = loc.getLocale();
      
      ResourceBundle bundle = loc.getResourceBundle();
      
      try {
        if (bundle != null)
          bundleString = bundle.getString(key);
      } catch (Exception e) {
      }
    }

    if (bundleString == null)
      return "???" + key + "???";
    else if (args == null || args.length == 0)
      return bundleString;

    if (locale == null)
      locale = getLocale();

    if (locale != null)
      return new MessageFormat(bundleString, locale).format(args);
    else
      return new MessageFormat(bundleString).format(args);
  }

  /**
   * Returns the localized message appropriate for the current context.
   */
  public LocalizationContext getBundle(String name)
  {
    Object localeObj = Config.find(this, Config.FMT_LOCALE);
    LocalizationContext bundle = null;
    BundleManager manager = getBundleManager();

    if (localeObj instanceof Locale) {
      Locale locale = (Locale) localeObj;

      bundle = manager.getBundle(name, locale);
    }
    else if (localeObj instanceof String) {
      Locale locale = getLocale((String) localeObj, null);

      bundle = manager.getBundle(name, locale);
    }
    else {
      String acceptLanguage = getCauchoRequest().getHeader("Accept-Language");

      if (acceptLanguage != null) {
	String cacheKey = name + acceptLanguage; 
	bundle = manager.getBundle(name, cacheKey, getCauchoRequest().getLocales());
      }
    }

    if (bundle != null)
      return bundle;

    Object fallback = Config.find(this, Config.FMT_FALLBACK_LOCALE);

    if (fallback instanceof Locale) {
      Locale locale = (Locale) fallback;

      bundle = manager.getBundle(name, locale);
    }
    else if (fallback instanceof String) {
      String localeName = (String) fallback;
      Locale locale = getLocale(localeName, null);

      bundle = manager.getBundle(name, locale);
    }

    if (bundle != null)
      return bundle;
    
    bundle = manager.getBundle(name);

    if (bundle != null)
      return bundle;
    else
      return BundleManager.NULL_BUNDLE;
  }

  /**
   * Returns the currently active locale.
   */
  public Locale getLocale()
  {
    Object localeObj = Config.find(this, Config.FMT_LOCALE);

    if (localeObj instanceof Locale)
      return (Locale) localeObj;
    else if (localeObj instanceof String)
      return getLocale((String) localeObj, null);

    LocalizationContext lc;
    lc = (LocalizationContext) getAttribute("caucho.bundle");

    Locale locale = null;
    if (lc != null)
      locale = lc.getLocale();

    if (locale != null)
      return locale;
    
    String acceptLanguage = getCauchoRequest().getHeader("Accept-Language");

    if (acceptLanguage != null) {
      Enumeration e = getCauchoRequest().getLocales();

      if (e != null && e.hasMoreElements())
	locale = (Locale) e.nextElement();
    }
    
    return locale;
  }

  public static Locale getLocale(String value, String variant)
  {
    Locale locale = null;
    int len = value.length();
    
    CharBuffer cb = new CharBuffer();
    int i = 0;
    char ch = 0;
    for (; i < len && (ch = value.charAt(i)) != '_' && ch != '-'; i++)
      cb.append(ch);

    String language = cb.toString();

    if (ch == '_' || ch == '-') {
      cb.clear();

      for (i++; i < len && (ch = value.charAt(i)) != '_' && ch != '-'; i++)
        cb.append(ch);

      String country = cb.toString();

      if (variant != null && ! variant.equals(""))
	return new Locale(language, country, variant);
      else
	return new Locale(language, country);
    }
    else if (variant != null && ! variant.equals(""))
      return new Locale(language, "", variant);
    else
      return new Locale(language, "");
  }


  public static void printBody(BodyContentImpl body, boolean isEscaped)
    throws IOException
  {
    JspWriter out = body.getEnclosingWriter();
    CharBuffer string = body.getCharBuffer();
    char []cBuf = string.getBuffer();

    int length = string.length() - 1;

    for (; length >= 0; length--) {
      char ch = cBuf[length];
      if (ch != ' ' && ch != '\n' && ch != '\t' && ch != '\r')
        break;
    }
    length++;

    int i;

    for (i = 0; i < length; i++) {
      char ch = cBuf[i];
      if (ch != ' ' && ch != '\n' && ch != '\t' && ch != '\r')
        break;
    }

    if (! isEscaped) {
      out.write(cBuf, i, length - i);
      return;
    }

    for (; i < length; i++) {
      char ch = cBuf[i];

      switch (ch) {
      case '<':
        out.write("&lt;");
        break;
      case '>':
        out.write("&gt;");
        break;
      case '&':
        out.write("&amp;");
        break;
      case '\'':
        out.write("&#039;");
        break;
      case '"':
        out.write("&#034;");
        break;
      default:
        out.write((char) ch);
        break;
      }
    }
  }

  /**
   * Evaluates the fragment, returing the string value.
   */
  public String invoke(JspFragment fragment)
    throws JspException, IOException
  {
    if (fragment == null)
      return "";
    
    BodyContentImpl body = (BodyContentImpl) pushBody();

    try {
      fragment.invoke(body);
      return body.getString();
    } finally {
      popBody();
      body.release();
    }
  }

  /**
   * Evaluates the fragment, returing the string value.
   */
  public String invokeTrim(JspFragment fragment)
    throws JspException, IOException
  {
    if (fragment == null)
      return "";
    
    BodyContentImpl body = (BodyContentImpl) pushBody();

    try {
      fragment.invoke(body);
      return body.getTrimString();
    } finally {
      popBody();
      body.release();
    }
  }

  /**
   * Evaluates the fragment, returing a reader
   */
  public Reader invokeReader(JspFragment fragment)
    throws JspException, IOException
  {
    if (fragment == null)
      return null;
    
    BodyContentImpl body = (BodyContentImpl) pushBody();

    try {
      fragment.invoke(body);

      return body.getReader();
    } finally {
      popBody();
      body.release();
    }
  }

  /**
   * Parses a boolean value.
   */
  static public boolean toBoolean(String value)
  {
    if (value == null)
      return false;
    else if (value.equalsIgnoreCase("true"))
      return true;
    else if (value.equalsIgnoreCase("false"))
      return false;
    else
      return value.trim().equalsIgnoreCase("true");
  }

  /**
   * Set/Remove a page attribute.
   */
  public void pageSetOrRemove(String var, Object value)
  {
    if (value != null)
      putAttribute(var, value);
    else
      removeAttribute(var);
  }

  /**
   * Set/Remove a request attribute.
   */
  public void requestSetOrRemove(String var, Object value)
  {
    if (value != null)
      getCauchoRequest().setAttribute(var, value);
    else
      getCauchoRequest().removeAttribute(var);
  }
  
  /**
   * Set/Remove a session attribute.
   */
  public void sessionSetOrRemove(String var, Object value)
  {
    if (value != null)
      getSession().setAttribute(var, value);
    else
      getSession().removeAttribute(var);
  }

  /**
   * Set/Remove an webApp attribute.
   */
  public void webAppSetOrRemove(String var, Object value)
  {
    if (value != null)
      getApplication().setAttribute(var, value);
    else
      getApplication().removeAttribute(var);
  }

  /**
   * Returns true if the EL ignores exceptions
   */
  public boolean isIgnoreException()
  {
    JspPropertyGroup jsp = getApplication().getJsp();

    return (jsp == null || jsp.isIgnoreELException());
  }

  /**
   * Returns the XPath variable environment corresponding to this page
   */
  public VarEnv getVarEnv()
  {
    if (_varEnv == null)
      _varEnv = new PageVarEnv();

    return _varEnv;
  }

  /**
   * Returns the XPath node environment corresponding to this page
   */
  public Node getNodeEnv()
  {
    return _nodeEnv;
  }

  /**
   * Returns the XPath node environment corresponding to this page
   */
  public void setNodeEnv(Node node)
  {
    _nodeEnv = node;
  }
  
  /**
   * Finds an attribute in any of the scopes from page to webApp.
   *
   * @param name the attribute name.
   *
   * @return the attribute value
   */
  public Object resolveVariable(String name)
    throws ELException
  {
    Object value = findAttribute(name);

    if (value != null)
      return value;

    if (_elContext == null)
      _elContext = EL.getEnvironment();

    return _elContext.getELResolver().getValue(_elContext, name, null);
  }

  /**
   * Returns the bundle manager.
   */
  private BundleManager getBundleManager()
  {
    if (_bundleManager == null)
      _bundleManager = BundleManager.create();

    return _bundleManager;
  }

  /**
   * Represents the XPath environment for this page.
   */
  public class PageVarEnv extends VarEnv {
    /**
     * Returns the value corresponding to the name.
     */
    public Object getValue(String name)
    {
      Object value = findAttribute(name);

      if (value != null)
        return value;

      int p = name.indexOf(':');
      if (p < 0)
        return null;

      String prefix = name.substring(0, p);
      String suffix = name.substring(p + 1);

      if (prefix.equals("param"))
        return getCauchoRequest().getParameter(suffix);
      else if (prefix.equals("header"))
        return ((HttpServletRequest) getCauchoRequest()).getHeader(suffix);
      else if (prefix.equals("cookie")) {
        Cookie cookie;
        HttpServletRequest request = (HttpServletRequest) getCauchoRequest();

        if (request instanceof CauchoRequest)
          cookie = ((CauchoRequest) request).getCookie(suffix);
        else
          cookie = null;

        if (cookie != null)
          return cookie.getValue();
        else
          return null;
      }
      else if (prefix.equals("initParam"))
        return getApplication().getInitParameter(suffix);
      else if (prefix.equals("pageScope"))
        return getAttribute(suffix);
      else if (prefix.equals("requestScope"))
        return getCauchoRequest().getAttribute(suffix);
      else if (prefix.equals("sessionScope"))
        return getSession().getAttribute(suffix);
      else if (prefix.equals("applicationScope"))
        return getApplication().getAttribute(suffix);
      else
        return null;
    }
  }
  
  static class StringArrayEnum implements Enumeration
  {
    private int _index;
    private String []_values;

    StringArrayEnum(String []values)
    {
      _values = values;
    }

    public boolean hasMoreElements()
    {
      return _index < _values.length;
    }
    
    public Object nextElement()
    {
      return _values[_index++];
    }
  }

  class PageELContext extends ELContext {
    public ELResolver getELResolver()
    {
      return _elResolver;
    }

    public FunctionMapper getFunctionMapper()
    {
      throw new UnsupportedOperationException();
    }

    public VariableMapper getVariableMapper()
    {
      return null;
    }
  }

  class PageELResolver extends AbstractVariableResolver {
    public Object getValue(ELContext env, Object base, Object property)
    {
      if (property != null)
	return null;
      else if (base instanceof String)
	return getAttribute(base.toString());
      else
	return null;
    }
    
    public void setValue(ELContext env,
			 Object base,
			 Object property,
			 Object value)
    {
      if (property != null) {
      }
      else if (base instanceof String)
	setAttribute(base.toString(), value);
    }
  }
}
