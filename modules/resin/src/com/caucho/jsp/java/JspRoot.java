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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jsp.java;

import java.io.IOException;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.jsp.*;

import com.caucho.util.*;

import com.caucho.jsp.JspParseException;

import com.caucho.vfs.WriteStream;

import com.caucho.xml.QName;
import com.caucho.xml.XmlChar;

/**
 * Represents the root node.
 */
public class JspRoot extends JspContainerNode {
  static final L10N L = new L10N(JspRoot.class);

  static final private QName VERSION = new QName("version");

  private HashMap<String,String> _namespaceMap = new HashMap<String,String>();
  
  /**
   * Adds an attribute.
   *
   * @param name the attribute name
   * @param value the attribute value
   */
  public void addAttribute(QName name, String value)
    throws JspParseException
  {
    if (VERSION.equals(name)) {
    }
    else {
      throw error(L.l("`{0}' is an unknown jsp:root attribute.  'version' is the only allowed JSP root value.",
                      name.getName()));
    }
  }

  /**
   * Adds a text node.
   */
  public JspNode addText(String text)
    throws JspParseException
  {
    for (int i = 0; i < text.length(); i++) {
      if (! XmlChar.isWhitespace(text.charAt(i))) {
	JspNode node = new StaticText(_gen, text, this);
	
        addChild(node);
	
        return node;
      }
    }

    return null;
  }

  /**
   * Called after all the attributes from the tag.
   */
  public void endAttributes()
    throws JspParseException
  {
    _gen.setOmitXmlDeclaration(true);
  }

  /**
   * Adds a namespace, e.g. from a prefix declaration.
   */
  public void addNamespace(String prefix, String value)
  {
    _namespaceMap.put(prefix, value);
  }
  
  /**
   * Set true if the node only has static text.
   */
  public boolean isStatic()
  {
    for (int i = 0; i < _children.size(); i++) {
      JspNode node = _children.get(i);

      if (! node.isStatic())
        return false;
    }

    return true;
  }

  /**
   * Generates the XML text representation for the tag validation.
   *
   * @param os write stream to the generated XML.
   */
  public void printXml(WriteStream os)
    throws IOException
  {
    os.print("<jsp:root xmlns:jsp=\"http://java.sun.com/JSP/Page\"");

    for (Map.Entry entry : _namespaceMap.entrySet()) {
      os.print(" xmlns:" + entry.getKey() + "=\"" + entry.getValue() + "\"");
    }
    os.print(">");
    printXmlChildren(os);
    os.print("</jsp:root>");
  }

  /**
   * Generates the code for the tag
   *
   * @param out the output writer for the generated java.
   */
  public void generate(JspJavaWriter out)
    throws Exception
  {
    generateChildren(out);
  }
}
