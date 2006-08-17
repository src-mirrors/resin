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

package com.caucho.jstl.el;

import java.io.*;
import java.util.*;

import javax.el.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;
import javax.servlet.jsp.jstl.core.*;
import javax.servlet.jsp.jstl.fmt.*;

import com.caucho.vfs.*;
import com.caucho.util.*;

import com.caucho.jsp.PageContextImpl;

import com.caucho.el.*;

/**
 * Sets the time zone for the current page.
 */
public class SetTimeZoneTag extends TagSupport {
  private static L10N L = new L10N(SetTimeZoneTag.class);
  
  private Expr _valueExpr;
  private String _var;
  private String _scope;

  /**
   * Sets the JSP-EL expression for the locale value.
   */
  public void setValue(Expr value)
  {
    _valueExpr = value;
  }

  /**
   * Sets the variable containing the time zone.
   */
  public void setVar(String var)
  {
    _var = var;
  }

  /**
   * Sets the scope to store the bundle.
   */
  public void setScope(String scope)
  {
    _scope = scope;
  }

  /**
   * Process the tag.
   */
  public int doStartTag()
    throws JspException
  {
    try {
      PageContextImpl pageContext = (PageContextImpl) this.pageContext;

      Object valueObj = _valueExpr.evalString(pageContext.getELContext());
      TimeZone timeZone = null;

      if (valueObj instanceof TimeZone) {
	timeZone = (TimeZone) valueObj;
      }
      else if (valueObj instanceof String) {
	String string = (String) valueObj;
	string = string.trim();

	if (string.equals(""))
	  timeZone = TimeZone.getTimeZone("GMT");
	else
	  timeZone = TimeZone.getTimeZone(string);
      }
      else
	timeZone = TimeZone.getTimeZone("GMT");

      String var = _var;
      if (var == null)
	var = Config.FMT_TIME_ZONE;
    
      CoreSetTag.setValue(pageContext, var, _scope, timeZone);

      return SKIP_BODY;
    } catch (ELException e) {
      throw new JspException(e);
    }
  }
}
