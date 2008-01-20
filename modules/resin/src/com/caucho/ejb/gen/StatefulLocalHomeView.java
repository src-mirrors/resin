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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.ejb.gen;

import com.caucho.config.*;
import com.caucho.ejb.cfg.*;
import com.caucho.java.JavaWriter;
import com.caucho.util.L10N;

import javax.ejb.*;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

/**
 * Represents a public interface to a bean, e.g. a local stateful view
 */
public class StatefulLocalHomeView extends StatefulLocalView {
  private static final L10N L = new L10N(StatefulLocalHomeView.class);

  public StatefulLocalHomeView(StatefulGenerator bean, ApiClass api)
  {
    super(bean, api);
  }

  protected String getViewClassName()
  {
    return getApi().getSimpleName() + "__EJBLocalHome";
  }

  @Override
  protected StatefulMethod createMethod(ApiMethod apiMethod, int index)
  {
    if (apiMethod.getName().equals("create")) {
      ApiMethod implMethod = getEjbClass().getMethod("ejbCreate",
						     apiMethod.getParameterTypes());

      if (implMethod == null)
	throw ConfigException.create(apiMethod.getMethod(),
				     L.l("can't find ejbCreate"));

      View localView = getSessionBean().getView(apiMethod.getReturnType());

      if (localView == null)
	throw ConfigException.create(apiMethod.getMethod(),
				     L.l("'{0}' is an unknown object interface",
					 apiMethod.getReturnType()));

      /*
      return new StatefulCreateMethod(getEjbClass(),
				      localView,
				      apiMethod.getMethod(),
				      implMethod.getMethod(),
				      index);
      */
      return null;
    }
    else {
      return super.createMethod(apiMethod, index);
    }
  }

  protected ApiMethod findImplMethod(ApiMethod apiMethod)
  {
    if (apiMethod.getName().equals("create"))
      return getEjbClass().getMethod("ejbCreate", apiMethod.getParameterTypes());
    else
      return super.findImplMethod(apiMethod);
  }

  protected void generateSuper(JavaWriter out, String serverVar)
    throws IOException
  {
    out.println("super(" + serverVar + ");");
  }

  @Override
  protected void generateExtends(JavaWriter out)
    throws IOException
  {
    out.println("  extends StatefulHome");
  }
}
