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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.jstl.el;

import com.caucho.el.Expr;
import com.caucho.jsp.PageContextImpl;
import com.caucho.util.L10N;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.jstl.fmt.LocalizationContext;
import javax.servlet.jsp.tagext.TagSupport;

/**
 * Sets the i18n localization bundle for the current page.
 */
public class SetBundleTag extends TagSupport {
  private static L10N L = new L10N(SetBundleTag.class);
  
  private Expr _basenameExpr;
  private String _var = "javax.servlet.jsp.jstl.fmt.localizationContext";
  private String _scope;

  /**
   * Sets the JSP-EL expression for the basename.
   */
  public void setBasename(Expr basename)
  {
    _basenameExpr = basename;
  }

  /**
   * Sets variable to store the bundle.
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
    PageContextImpl pageContext = (PageContextImpl) this.pageContext;

    String basename = _basenameExpr.evalString(pageContext.getELContext());
      
    LocalizationContext bundle = pageContext.getBundle(basename);

    CoreSetTag.setValue(pageContext, _var, _scope, bundle);

    return SKIP_BODY;
  }
}
