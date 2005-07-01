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

package com.caucho.jsp;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;
import java.util.jar.*;
import java.beans.*;

import javax.servlet.*;
import javax.servlet.jsp.tagext.*;

import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.log.Log;
import com.caucho.server.http.*;
import com.caucho.jsp.cfg.*;
import com.caucho.loader.DynamicClassLoader;

import com.caucho.relaxng.CompactVerifierFactoryImpl;

import com.caucho.config.NodeBuilder;
import com.caucho.config.ConfigException;

import com.caucho.config.types.FileSetType;

import com.caucho.server.webapp.Application;

/**
 * Stores the entire information for a tag library.
 */
public class TaglibManager {
  static final L10N L = new L10N(TaglibManager.class);
  private static final Logger log = Log.open(TaglibManager.class);

  private static ArrayList<TldTaglib> _cauchoTaglibs;
  
  private JspResourceManager _resourceManager;
  private Application _application;

  private TldManager _tldManager;

  private String _tldDir;
  private FileSetType _tldFileSet;

  private HashMap<String,String> _uriLocationMap =
    new HashMap<String,String>();
  private HashMap<String,TldTaglib> _tldMap = new HashMap<String,TldTaglib>();
  private HashMap<String,Taglib> _taglibMap = new HashMap<String,Taglib>();
  private HashMap<String,Taglib> _taglibDirMap = new HashMap<String,Taglib>();
  
  private JspParseException _loadAllTldException;

  private TagAnalyzer _tagAnalyzer = new TagAnalyzer();

  private volatile boolean _isInit;

  public TaglibManager(JspResourceManager resourceManager,
		       Application application)
    throws JspParseException, IOException
  {
    _resourceManager = resourceManager;
    _application = application;

    _tldManager = TldManager.create(resourceManager, application);
  }

  /**
   * Sets the application.
   */
  void setApplication(Application application)
  {
    _application = application;
  }

  public void setTldDir(String tldDir)
  {
    _tldDir = tldDir;
  }
    

  public void setTldFileSet(FileSetType fileSet)
  {
    _tldManager.setTldFileSet(fileSet);
  }

  /**
   * Adds a URI to location map.
   */
  public void addLocationMap(String uri, String location)
  {
    _uriLocationMap.put(uri, location);
  }

  /**
   * Loads all the .tld files in the WEB-INF and the META-INF for
   * the entire classpath.
   */
  public synchronized void init()
    throws JspParseException, IOException
  {
    if (_isInit)
      return;
    _isInit = true;
  }

  /**
   * Analyze tag.
   */
  AnalyzedTag analyzeTag(Class cl)
  {
    return _tagAnalyzer.analyze(cl);
  }

  /**
   * Returns the taglib with the given prefix and uri.
   */
  public synchronized Taglib getTaglib(String prefix,
                                       String uri,
                                       String location)
    throws JspParseException
  {
    try {
      init();
    } catch (IOException e) {
      throw new JspParseException(e);
    }
    
    Taglib taglib = _taglibMap.get(uri);

    if (taglib != null)
      return taglib;

    // jsp/188u
    String mapLocation = _uriLocationMap.get(uri);

    if (mapLocation != null)
      location = mapLocation;

    taglib = readTaglib(prefix, uri, location);

    if (taglib != null)
      _taglibMap.put(uri, taglib);

    return taglib;
  }

  /**
   * Returns the taglib with the given prefix and uri.
   */
  public synchronized Taglib getTaglibDir(String prefix, String dir)
    throws JspParseException
  {
    try {
      init();
    } catch (IOException e) {
      throw new JspParseException(e);
    }
    
    Taglib taglib = _taglibDirMap.get(dir);

    if (taglib != null)
      return taglib;

    TldTaglib tldTaglib = new TldTaglib();
    
    taglib = new Taglib(prefix, "urn:jsptagdir:" + dir, tldTaglib);

    if (taglib != null)
      _taglibDirMap.put(dir, taglib);

    return taglib;
  }

  /**
   * Returns the taglib with the given prefix and uri.
   */
  private Taglib readTaglib(String prefix, String uri, String location)
    throws JspParseException
  {
    try {
      TldTaglib tldTaglib = _tldMap.get(uri);

      if (tldTaglib != null) {
      }
      else {
	String mapLocation = _uriLocationMap.get(uri);

	if ((location == null || location.equals("")) &&
	    (mapLocation == null || mapLocation.equals("")))
	  return null;

        tldTaglib = _tldManager.parseTld(uri, mapLocation, location);

        _tldMap.put(uri, tldTaglib);
      }

      if (tldTaglib != null) {
	if (tldTaglib.getConfigException() != null)
	  throw JspParseException.create(tldTaglib.getConfigException());
	
	return new Taglib(prefix, uri, tldTaglib);
      }
      else
	return null;
    } catch (JspParseException e) {
      throw e;
    } catch (Exception e) {
      throw new JspParseException(e);
    }
  }

  /**
   * Finds the path to the jar specified by the location.
   *
   * @param appDir the application directory
   * @param location the tag-location specified in the web.xml
   *
   * @return the found jar or null
   */
  private Path findJar(String location)
  {
    Path path;

    if (location.startsWith("file:"))
      path = Vfs.lookup(location);
    else if (location.startsWith("/"))
      path = _resourceManager.resolvePath("." + location);
    else
      path = _resourceManager.resolvePath(location);

    if (path.exists())
      return path;

    DynamicClassLoader loader;
    loader = (DynamicClassLoader) Thread.currentThread().getContextClassLoader();
    String classPath = loader.getClassPath();
    char sep = CauchoSystem.getPathSeparatorChar();

    int head = 0;
    int tail = 0;
    
    while ((tail = classPath.indexOf(sep, head)) >= 0) {
      String sub = classPath.substring(head, tail);

      path = Vfs.lookup(sub);
      
      if (sub.endsWith(location) && path.exists())
        return path;

      head = tail + 1;
    }

    if (classPath.length() <= head)
      return null;
    
    String sub = classPath.substring(head);

    path = Vfs.lookup(sub);
      
    if (sub.endsWith(location) && path.exists())
      return path;
    else
      return null;
  }
}
