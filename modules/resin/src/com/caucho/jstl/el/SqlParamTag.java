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

package com.caucho.jstl.el;

import java.io.*;
import java.util.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;
import javax.servlet.jsp.jstl.core.*;
import javax.servlet.jsp.jstl.fmt.*;
import javax.servlet.jsp.jstl.sql.SQLExecutionTag;
import javax.servlet.jsp.el.ELException;

import com.caucho.vfs.*;
import com.caucho.util.*;
import com.caucho.jsp.PageContextImpl;
import com.caucho.el.*;
import com.caucho.jstl.ParamContainerTag;

/**
 * Looks up an i18n message from a bundle and prints it.
 */
public class SqlParamTag extends BodyTagSupport {
  private static L10N L = new L10N(SqlParamTag.class);
  
  private Expr _valueExpr;

  /**
   * Sets the value
   *
   * @param value the JSP-EL expression for the value.
   */
  public void setValue(Expr value)
  {
    _valueExpr = value;
  }

  /**
   * Process the tag.
   */
  public int doStartTag()
    throws JspException
  {
    try {
      if (_valueExpr == null)
	return EVAL_BODY_BUFFERED;

      PageContextImpl pageContext = (PageContextImpl) this.pageContext;
    
      Object value = _valueExpr.evalObject(pageContext);

      Tag parent = getParent();
      for (;
	   parent != null && ! (parent instanceof SQLExecutionTag);
	   parent = parent.getParent()) {
      }

      if (parent == null)
	throw new JspException(L.l("sql:param requires sql:query parent.")); 

      SQLExecutionTag tag = (SQLExecutionTag) parent;

      tag.addSQLParameter(value);
    
      return SKIP_BODY;
    } catch (ELException e) {
      throw new JspException(e);
    }
  }

  /**
   * Process the tag.
   */
  public int doEndTag()
    throws JspException
  {
    if (_valueExpr != null)
      return EVAL_PAGE;
      
    String value;

    if (bodyContent != null)
      value = bodyContent.getString().trim();
    else
      value = "";

    Object parent = getParent();
    if (! (parent instanceof ParamContainerTag))
      throw new JspException(L.l("sql:param requires sql:query parent.")); 

    ParamContainerTag tag = (ParamContainerTag) parent;

    tag.addParam(value);
    
    return EVAL_PAGE;
  }
}
