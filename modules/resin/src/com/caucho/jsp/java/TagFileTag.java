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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jsp.java;

import java.io.*;
import java.util.*;

import java.lang.reflect.*;
import java.beans.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import com.caucho.vfs.*;
import com.caucho.util.*;
import com.caucho.xml.QName;

import com.caucho.jsp.*;

/**
 * Represents a custom tag.
 */
public class TagFileTag extends GenericTag {
  private boolean _oldScriptingInvalid;
  private JspBody _body;
  private int _maxFragmentIndex;
  private String _contextVarName;

  /**
   * Called when the attributes end.
   */
  public void endAttributes()
  {
    _oldScriptingInvalid = _parseState.isScriptingInvalid();
    _parseState.setScriptingInvalid(true);
  }
  
  /**
   * Adds a child node.
   */
  public void endElement()
    throws Exception
  {
    super.endElement();
    
    _parseState.setScriptingInvalid(_oldScriptingInvalid);
    
    if (_children == null || _children.size() == 0)
      return;

    for (int i = 0; i < _children.size(); i++) {
      JspNode node = _children.get(i);

      if (node instanceof JspBody) {
	if (_body != null)
	  throw error(L.l("Only one <jsp:body> is allowed as a child of a tag."));
        _body = (JspBody) node;
        _children.remove(i);
        return;
      }
    }

    for (int i = 0; i < _children.size(); i++) {
      JspNode node = _children.get(i);

      if (! (node instanceof JspAttribute)) {
	if (_body == null) {
	  _body = new JspBody();
	  _body.setParent(this);
	  _body.setGenerator(_gen);
	  _body.endAttributes();
	}
	
        _body.addChild(node);
      }
    }
    _body.endElement();
    _children = null;
  }

  /**
   * Generates the code for a custom tag.
   *
   * @param out the output writer for the generated java.
   */
  public void generateDeclaration(JspJavaWriter out)
    throws IOException
  {
    super.generateDeclaration(out);

    /*
    out.println();
    out.println("private static final com.caucho.jsp.java.JspTagFileSupport " + name + " = ");
    out.println("  new " + className + "();");
    */
  }

  /**
   * Returns true if the tag file invocation contains a child tag.
   */
  public boolean hasTag()
  {
    return super.hasTag() || _body != null && _body.hasTag();
  }
  /**
   * Returns null, since tag files aren't parent tags.
   */
  public String getCustomTagName()
  {
    return null;
  }
  
  /**
   * Generates code before the actual JSP.
   */
  public void generatePrologue(JspJavaWriter out)
    throws Exception
  {
    super.generatePrologue(out);

    if (_body != null) {
      _body.setJspFragment(true);
      _body.generateFragmentPrologue(out);
    }
  }
  
  /**
   * Generates the code for a custom tag.
   *
   * @param out the output writer for the generated java.
   */
  public void generate(JspJavaWriter out)
    throws Exception
  {
    String className = _tagInfo.getTagClassName();
    Class cl = _tagClass;
    
    String name = className;

    String childContext = fillTagFileAttributes(out, name);

    out.print(name + ".doTag(pageContext, " + childContext + ", out, ");
    if (_body != null)
      generateFragment(out, _body, "pageContext");
    else
      out.print("null");
    
    out.println(");");

    printVarDeclaration(out, VariableInfo.AT_END);
  }
  
  public String fillTagFileAttributes(JspJavaWriter out, String tagName)
    throws Exception
  {
    _contextVarName = "_jsp_context" + _gen.uniqueId();

    String name = _contextVarName;

    out.println("com.caucho.jsp.PageContextWrapper " + name);
    out.println("  = com.caucho.jsp.PageContextWrapper.create(pageContext);");

    TagAttributeInfo attrs[] = _tagInfo.getAttributes();

    // clear any attributes mentioned in the taglib that aren't set
    for (int i = 0; attrs != null && i < attrs.length; i++) {
      int p = indexOf(_attributeNames, attrs[i].getName());
      
      if (p < 0 && attrs[i].isRequired()) {
	throw error(L.l("required attribute `{0}' missing from <{1}>",
                        attrs[i].getName(),
                        getTagName()));
      }
    }

    boolean isDynamic = _tagInfo.hasDynamicAttributes();
    String mapAttribute = null;
    String mapName = null;

    if (isDynamic) {
      TagInfoExt tagInfoImpl = (TagInfoExt) _tagInfo;
      mapAttribute = tagInfoImpl.getDynamicAttributesName();
    }
    
    // fill all mentioned attributes
    for (int i = 0; i < _attributeNames.size(); i++) {
      QName attrName = _attributeNames.get(i);
      Object value = _attributeValues.get(i);
      
      TagAttributeInfo attribute = null;
      int j = 0;
      for (j = 0; attrs != null && j < attrs.length; j++) {
	if (attrs[j].getName().equals(attrName.getName())) {
          attribute = attrs[j];
	  break;
        }
      }

      if (attribute == null && ! isDynamic)
	throw error(L.l("unexpected attribute `{0}' in <{1}>",
                        attrName.getName(), getTagName()));

      boolean rtexprvalue = true;

      Class cl = null;

      if (attribute != null) {
	cl = _gen.loadBeanClass(attribute.getTypeName());

	rtexprvalue = attribute.canBeRequestTime();
      }
      
      if (cl == null)
	cl = String.class;

      if (attribute == null) {
	if (mapName == null) {
	  mapName = "_jsp_map_" + _gen.uniqueId();
	  out.println("java.util.HashMap " + mapName + " = new java.util.HashMap(8);");
	  out.println(name + ".setAttribute(\"" + mapAttribute + "\", " + mapName + ");");
	}

	out.print(mapName + ".put(\"" + attrName.getName() + "\", ");
      }
      else
	out.print(name + ".setAttribute(\"" + attrName.getName() + "\", ");

      if (value instanceof JspNode) {
	JspFragmentNode frag = (JspFragmentNode) value;

	if (attribute != null && 
	    attribute.getTypeName().equals(JspFragment.class.getName())) {
	  out.println(generateFragment(frag, "pageContext") + ");");
	}
	else
	  out.println(frag.generateValue() + ");");
      }
      else {
	String convValue = generateParameterValue(cl,
						  (String) value,
						  rtexprvalue,
						  attribute);
      
	//					attribute.allowRtexpr());

	out.println(toObject(cl, convValue) + ");");
      }
    }
    
    return name;
  }

  private int indexOf(ArrayList<QName> names, String name)
  {
    for (int i = 0; i < names.size(); i++) {
      if (names.get(i).getName().equals(name))
	return i;
    }

    return -1;
  }

  private String toObject(Class cl, String value)
  {
    if (boolean.class.equals(cl))
      return "new Boolean(" + value + ")";
    else if (byte.class.equals(cl))
      return "new Byte(" + value + ")";
    else if (short.class.equals(cl))
      return "new Short(" + value + ")";
    else if (int.class.equals(cl))
      return "new Integer(" + value + ")";
    else if (long.class.equals(cl))
      return "new Long(" + value + ")";
    else if (char.class.equals(cl))
      return "new Character(" + value + ")";
    else if (float.class.equals(cl))
      return "new Float(" + value + ")";
    else if (double.class.equals(cl))
      return "new Double(" + value + ")";
    else
      return value;
  }
}
