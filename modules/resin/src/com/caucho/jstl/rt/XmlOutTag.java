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

package com.caucho.jstl.rt;

import java.io.*;
import java.util.logging.*;

import javax.servlet.jsp.*;
import javax.servlet.jsp.tagext.*;

import org.w3c.dom.Node;

import com.caucho.log.Log;
import com.caucho.vfs.*;
import com.caucho.jsp.PageContextImpl;
import com.caucho.xpath.*;

public class XmlOutTag extends TagSupport {
  private static final Logger log = Log.open(XmlOutTag.class);
  private Expr _select;
  private boolean _escapeXml = true;

  /**
   * Sets the JSP-EL expression value.
   */
  public void setSelect(Expr select)
  {
    _select = select;
  }

  /**
   * Sets true if XML should be escaped.
   */
  public void setEscapeXml(boolean escapeXml)
  {
    _escapeXml = escapeXml;
  }

  /**
   * Process the tag.
   */
  public int doStartTag()
    throws JspException
  {
    try {
      PageContextImpl pageContext = (PageContextImpl) this.pageContext;
      
      JspWriter out = pageContext.getOut();

      Env env = XPath.createEnv();
      env.setVarEnv(pageContext.getVarEnv());

      Node node = pageContext.getNodeEnv();

      String value = _select.evalString(node, env);

      env.free();

      if (_escapeXml)
        com.caucho.el.Expr.toStreamEscaped(out, value);
      else
        out.print(value);      
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return SKIP_BODY;
  }
}
