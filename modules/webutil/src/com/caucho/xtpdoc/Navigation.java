/*
 * Copyright (c) 1998-2000 Caucho Technology -- all rights reserved
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
 * @author Emil Ong
 */

package com.caucho.xtpdoc;

import java.io.PrintWriter;
import java.io.IOException;

import java.util.*;

import java.util.logging.Logger;

import com.caucho.vfs.Path;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;

public class Navigation {
  private static final Logger log 
    = Logger.getLogger(Navigation.class.getName());

  private int _depth;
  private Path _rootPath;
  private String _uri;
  private String _section;
  private Document _document;
  private boolean _threaded;
  private boolean _comment;
  private ArrayList<NavigationItem> _items 
    = new ArrayList<NavigationItem>();

  private NavigationItem _docItem;
  private NavigationItem _child;

  private HashMap<String,NavigationItem> _itemMap
    = new HashMap<String,NavigationItem>();

  public Navigation(Document document, String uri, Path path, int depth)
  {
    _document = document;
    _rootPath = path;
    _uri = uri;
    _depth = depth;
  }

  public void setChild(NavigationItem child)
  {
    _child = child;
  }

  public String getUri()
  {
    return _uri;
  }

  public Document getDocument()
  {
    return _document;
  }

  public void setSection(String section)
  {
    _section = section;
  }

  public void setComment(boolean comment)
  {
    _comment = comment;
  }

  public void setThreaded(boolean threaded)
  {
    _threaded = threaded;
  }

  public NavigationItem getRoot()
  {
    if (_items.size() > 0)
      return _items.get(0);
    else
      return null;
  }

  public NavigationItem createItem()
  {
    return new NavigationItem(this, null, _depth);
  }

  public void addItem(NavigationItem item)
  {
    _items.add(item);
  }

  public void putItem(String uri, NavigationItem item)
  {
    if (_child != null && _child.getUri().equals(uri)) {
      _child.setParent(item.getParent());
      _itemMap.put(uri, _child);
    }
    else {
      _itemMap.put(uri, item);
    }
  }

  public NavigationItem getItem(String uri)
  {
    return _itemMap.get(uri);
  }

  public NavigationItem getRootItem()
  {
    if (_items.size() > 0)
      return _items.get(0);
    else
      return null;
  }

  public void writeHtml(XMLStreamWriter out)
    throws XMLStreamException
  {
    writeHtml(out, "");
  }
  
  public void writeHtml(XMLStreamWriter out, String path)
    throws XMLStreamException
  {
    String depthString = (_depth == 0) ? "top" : ("" + _depth);

    /*
    out.writeStartElement("dl");
    out.writeAttribute("class", "atoc-toplevel atoc-toplevel-" + depthString);
    */

    for (NavigationItem item : _items)
      item.writeHtml(out, path);

    //out.writeEndElement(); // dl
  }

  public void writeLeftNav(XMLStreamWriter out)
    throws XMLStreamException
  {
    if (_items.size() > 0) {
      NavigationItem topItem = _items.get(0);

      System.out.println("PATH: " + _document.getURI());
    }
    
    for (NavigationItem item : _items)
      item.writeLeftNav(out);
  }

  public void writeLaTeX(PrintWriter writer)
    throws IOException
  {
  }
}
