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

package com.caucho.servlets;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;
import javax.servlet.*;

import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.vfs.CaseInsensitive;
import com.caucho.server.webapp.Application;

import com.caucho.server.connection.CauchoRequest;
import com.caucho.server.connection.CauchoResponse;
import com.caucho.server.connection.AbstractHttpResponse;

/**
 * Serves static files.  The cache headers are automatically set on these
 * files.
 */
public class FileServlet extends GenericServlet {
  private Path _context;
  private byte []_buffer = new byte[1024];
  private Application _app;
  private RequestDispatcher _dir;
  private LruCache<String,Cache> _pathCache;
  private QDate _calendar = new QDate();
  private boolean _isCaseInsensitive;
  private boolean _isEnableRange = true;
  private String _characterEncoding;

  public FileServlet()
  {
    _isCaseInsensitive = CaseInsensitive.isCaseInsensitive();
  }

  /**
   * Flag to disable the "Range" header.
   */
  public void setEnableRange(boolean isEnable)
  {
    _isEnableRange = isEnable;
  }

  /**
   * Sets the character encoding.
   */
  public void setCharacterEncoding(String encoding)
  {
    _characterEncoding = encoding;
  }
  
  public void init(ServletConfig conf)
    throws ServletException
  {
    super.init(conf);

    _app = (Application) getServletContext();
    _context = _app.getAppDir();

    try {
      _dir = _app.getNamedDispatcher("directory");
    } catch (Throwable e) {
    }
      
    _pathCache = new LruCache<String,Cache>(1024);

    String enable = getInitParameter("enable-range");
    if (enable != null && enable.equals("false"))
      _isEnableRange = false;

    String encoding = getInitParameter("character-encoding");
    if (encoding != null && ! "".equals(encoding))
      _characterEncoding = encoding;
  }

  private RequestDispatcher getDirectoryServlet()
  {
    if (_dir == null)
      _dir = _app.getNamedDispatcher("directory");

    return _dir;
  }

  public void service(ServletRequest request, ServletResponse response)
    throws ServletException, IOException
  {
    CauchoRequest cauchoReq = null;
    HttpServletRequest req;
    HttpServletResponse res;

    if (request instanceof CauchoRequest) {
      cauchoReq = (CauchoRequest) request;
      req = cauchoReq;
    }
    else
      req = (HttpServletRequest) request;
    
    res = (HttpServletResponse) response;
    
    String method = req.getMethod();
    if (! method.equalsIgnoreCase("GET") &&
	! method.equalsIgnoreCase("HEAD") &&
	! method.equalsIgnoreCase("POST")) {
      res.sendError(res.SC_NOT_IMPLEMENTED, "Method not implemented");
      return;
    }

    boolean isInclude = false;
    String uri;

    uri = (String) req.getAttribute("javax.servlet.include.request_uri");
    if (uri != null)
      isInclude = true;
    else
      uri = req.getRequestURI();

    Cache cache = _pathCache.get(uri);

    String filename = null;

    if (cache == null) {
      CharBuffer cb = new CharBuffer();
      String servletPath;

      if (cauchoReq != null)
        servletPath = cauchoReq.getPageServletPath();
      else if (isInclude)
        servletPath = (String) req.getAttribute("javax.servlet.include.servlet_path");
      else
        servletPath = req.getServletPath();
        
      if (servletPath != null)
        cb.append(servletPath);
      
      String pathInfo;
      if (cauchoReq != null)
        pathInfo = cauchoReq.getPagePathInfo();
      else if (isInclude)
        pathInfo = (String) req.getAttribute("javax.servlet.include.path_info");
      else
        pathInfo = req.getPathInfo();
        
      if (pathInfo != null)
        cb.append(pathInfo);

      String relPath = cb.toString();

      if (_isCaseInsensitive)
        relPath = relPath.toLowerCase();

      filename = getServletContext().getRealPath(relPath);
      Path path = _context.lookupNative(filename);
      int lastCh;

      // only top-level requests are checked
      if (cauchoReq == null || cauchoReq.getRequestDepth(0) != 0) {
      }
      else if (relPath.regionMatches(true, 0, "/web-inf", 0, 8) &&
               (relPath.length() == 8 ||
                ! Character.isLetterOrDigit(relPath.charAt(8)))) {
        res.sendError(res.SC_NOT_FOUND);
        return;
      }
      else if (relPath.regionMatches(true, 0, "/meta-inf", 0, 9) &&
               (relPath.length() == 9 ||
                ! Character.isLetterOrDigit(relPath.charAt(9)))) {
        res.sendError(res.SC_NOT_FOUND);
        return;
      }

      if (relPath.endsWith(".DS_store")) {
        // MacOS-X security hole with trailing '.'
        res.sendError(res.SC_NOT_FOUND);
        return;
      }
      else if (! CauchoSystem.isWindows() || relPath.length() == 0) {
      }
      else if (path.isDirectory()) {
      }
      else {
        String lower = path.getPath().toLowerCase();
        
        if ((lastCh = relPath.charAt(relPath.length() - 1)) == '.' ||
            lastCh == ' ' || lastCh == '*' || lastCh == '?' ||
            lastCh == '/' || lastCh == '\\'
            || lower.endsWith("::$data")
            || isWindowsSpecial(lower, "/con")
            || isWindowsSpecial(lower, "/aux")
            || isWindowsSpecial(lower, "/prn")
            || isWindowsSpecial(lower, "/nul")
            || isWindowsSpecial(lower, "/com1")
            || isWindowsSpecial(lower, "/com2")
            || isWindowsSpecial(lower, "/com3")
            || isWindowsSpecial(lower, "/com4")
            || isWindowsSpecial(lower, "/lpt1")
            || isWindowsSpecial(lower, "/lpt2")
            || isWindowsSpecial(lower, "/lpt3")) {
          // Windows security hole with trailing '.'
          res.sendError(res.SC_NOT_FOUND);
          return;
        }
      }

      // A null will cause problems.
      for (int i = relPath.length() - 1; i >= 0; i--) {
        char ch = relPath.charAt(i);
          
        if (ch == 0) {
          res.sendError(res.SC_NOT_FOUND);
          return;
        }
      }

      ServletContext app = getServletContext();

      cache = new Cache(_calendar, path, relPath, app.getMimeType(relPath));

      _pathCache.put(uri, cache);
    }
  
    cache.update();

    if (cache.isDirectory()) {
      if (_dir != null)
	_dir.forward(req, res);
      else
	res.sendError(res.SC_NOT_FOUND);
      return;
    }

    if (! cache.canRead()) {
      if (isInclude)
        throw new FileNotFoundException(uri);
      else
        res.sendError(res.SC_NOT_FOUND);
      return;
    }

    String ifMatch = req.getHeader("If-None-Match");
    String etag = cache.getEtag();
    if (ifMatch != null && ifMatch.equals(etag)) {
      res.addHeader("ETag", etag);
      res.sendError(res.SC_NOT_MODIFIED);
      return;
    }

    String lastModified = cache.getLastModifiedString();

    if (ifMatch == null) {
      String ifModified = req.getHeader("If-Modified-Since");

      boolean isModified = true;

      if (ifModified == null) {
      }
      else if (ifModified.equals(lastModified)) {
	isModified = false;
      }
      else {
	long ifModifiedTime;

	synchronized (_calendar) {
	  try {
	    ifModifiedTime = _calendar.parseDate(ifModified);
	  } catch (Throwable e) {
	    ifModifiedTime = 0;
	  }
	}

	isModified = ifModifiedTime != cache.getLastModified();
      }

      if (! isModified) {
	if (etag != null)
	  res.addHeader("ETag", etag);
	res.sendError(res.SC_NOT_MODIFIED);
	return;
      }
    }

    res.addHeader("ETag", etag);
    res.addHeader("Last-Modified", lastModified);
    if (_isEnableRange && cauchoReq != null && cauchoReq.isTop())
      res.addHeader("Accept-Ranges", "bytes");
    
    if (_characterEncoding != null)
      res.setCharacterEncoding(_characterEncoding);
    
    String mime = cache.getMimeType();
    if (mime != null)
      res.setContentType(mime);

    if (method.equalsIgnoreCase("HEAD")) {
      res.setContentLength((int) cache.getLength());
      return;
    }

    if (_isEnableRange) {
      String range = req.getHeader("Range");

      if (range != null) {
	String ifRange = req.getHeader("If-Range");

	if (ifRange != null && ! ifRange.equals(etag)) {
	}
	else if (handleRange(req, res, cache, range, mime))
	  return;
      }
    }

    res.setContentLength((int) cache.getLength());

    if (res instanceof CauchoResponse) {
      CauchoResponse cRes = (CauchoResponse) res;

      cRes.getResponseStream().sendFile(cache.getPath(), cache.getLength());
    }
    else {
      OutputStream os = res.getOutputStream();
      cache.getPath().writeToStream(os);
    }
  }

  private boolean isWindowsSpecial(String lower, String test)
  {
    int p = lower.indexOf(test);

    if (p < 0)
      return false;

    int lowerLen = lower.length();
    int testLen = test.length();
    char ch;

    if (lowerLen == p + testLen
        || (ch = lower.charAt(p + testLen)) == '/' || ch == '.')
      return true;
    else
      return false;
  }

  private boolean handleRange(HttpServletRequest req,
                              HttpServletResponse res,
                              Cache cache,
			      String range,
			      String mime)
    throws IOException
  {
    // This is duplicated in CacheInvocation.  Possibly, it should be
    // completely removed although it's useful even without caching.
    int length = range.length();

    boolean hasMore = range.indexOf(',') > 0;

    int head = 0;
    ServletOutputStream os = res.getOutputStream();
    boolean isFirstChunk = true;
    String boundary = null;
    int off = range.indexOf("bytes=", head);

    if (off < 0)
      return false;

    off += 6;

    while (off > 0 && off < length) {
      boolean hasFirst = false;
      long first = 0;
      boolean hasLast = false;
      long last = 0;
      int ch = -1;;

      // Skip whitespace
      for (; off < length && (ch = range.charAt(off)) == ' '; off++) {
      }

      // read range start (before '-')
      for (;
	   off < length && (ch = range.charAt(off)) >= '0' && ch <= '9';
	   off++) {
	first = 10 * first + ch - '0';
	hasFirst = true;
      }

      if (length <= off && ! isFirstChunk)
	break;
      else if (ch != '-')
	return false;

      // read range end (before '-')
      for (off++;
	   off < length && (ch = range.charAt(off)) >= '0' && ch <= '9';
	   off++) {
	last = 10 * last + ch - '0';
	hasLast = true;
      }

      // Skip whitespace
      for (; off < length && (ch = range.charAt(off)) == ' '; off++) {
      }

      head = off;

      long cacheLength = cache.getLength();

      if (! hasLast) {
	if (first == 0)
	  return false;
	
	last = cacheLength - 1;
      }

      // suffix
      if (! hasFirst) {
	first = cacheLength - last;
	last = cacheLength - 1;
      }

      if (last < first)
	break;
    
      if (cacheLength <= last) {
	// XXX: actually, an error
	break;
      }
    
      res.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

      CharBuffer cb = new CharBuffer();
      cb.append("bytes ");
      cb.append(first);
      cb.append('-');
      cb.append(last);
      cb.append('/');
      cb.append(cacheLength);
      String chunkRange = cb.toString();

      if (hasMore) {
	if (isFirstChunk) {
	  CharBuffer cb1 = new CharBuffer();

	  cb1.append("--");
	  Base64.encode(cb1, RandomUtil.getRandomLong());
	  boundary = cb1.toString();

	  res.setContentType("multipart/byteranges; boundary=" + boundary);
	}
	else {
	  os.write('\r');
	  os.write('\n');
	}

	isFirstChunk = false;
	
	os.write('-');
	os.write('-');
	os.print(boundary);
	os.print("\r\nContent-Type: ");
	os.print(mime);
	os.print("\r\nContent-Range: ");
	os.print(chunkRange);
	os.write('\r');
	os.write('\n');
	os.write('\r');
	os.write('\n');
      }
      else {
	res.setContentLength((int) (last - first + 1));
      
	res.addHeader("Content-Range", chunkRange);
      }

      ReadStream is = null;
      try {
	is = cache.getPath().openRead();
	is.skip(first);

	os = res.getOutputStream();
	is.writeToStream(os, (int) (last - first + 1));
      } finally {
	if (is != null)
	  is.close();
      }

      for (off--; off < length && range.charAt(off) != ','; off++) {
      }

      off++;
    }

    if (hasMore) {
      os.write('\r');
      os.write('\n');
      os.write('-');
      os.write('-');
      os.print(boundary);
      os.write('-');
      os.write('-');
      os.write('\r');
      os.write('\n');
    }

    return true;
  }

  static class Cache {
    private final static long UPDATE_INTERVAL = 2000L;
    
    QDate _calendar;
    Path _path;
    boolean _isDirectory;
    boolean _canRead;
    long _length;
    long _lastCheck;
    long _lastModified = 0xdeadbabe1ee7d00dL;
    String _relPath;
    String _etag;
    String _lastModifiedString;
    String _mimeType;
    
    Cache(QDate calendar, Path path, String relPath, String mimeType)
    {
      _calendar = calendar;
      _path = path;
      _relPath = relPath;
      _mimeType = mimeType;

      update();
    }

    Path getPath()
    {
      return _path;
    }

    boolean canRead()
    {
      return _canRead;
    }

    boolean isDirectory()
    {
      return _isDirectory;
    }

    long getLength()
    {
      return _length;
    }

    String getRelPath()
    {
      return _relPath;
    }

    String getEtag()
    {
      return _etag;
    }

    long getLastModified()
    {
      return _lastModified;
    }

    String getLastModifiedString()
    {
      return _lastModifiedString;
    }

    String getMimeType()
    {
      return _mimeType;
    }

    void update()
    {
      long now = Alarm.getCurrentTime();
      if (_lastCheck + UPDATE_INTERVAL < now) {
        synchronized (this) {
	  if (now <= _lastCheck + UPDATE_INTERVAL)
	    return;

	  if (_lastCheck == 0) {
	    updateData();
	    _lastCheck = now;
	    return;
	  }

	  _lastCheck = now;
	}

	updateData();
      }
    }

    private void updateData()
    {
      long lastModified = _path.getLastModified();
      long length = _path.getLength();

      if (lastModified != _lastModified || length != _length) {
	_lastModified = lastModified;
	_length = length;
	_canRead = _path.canRead();
	_isDirectory = _path.isDirectory();
	    
	CharBuffer cb = new CharBuffer();
	cb.append('"');
	long hash = lastModified;
	hash = hash * 0x5deece66dl + 0xbl + (hash >>> 32) * 137;
	hash += length;
	Base64.encode(cb, hash);
	cb.append('"');
	_etag = cb.close();

	synchronized (_calendar) {
	  _calendar.setGMTTime(lastModified);
	  _lastModifiedString = _calendar.printDate();
	}
      }
	  
      if (lastModified == 0) {
	_canRead = false;
	_isDirectory = false;
      }
    }
  }
}
