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

package com.caucho.soap.reflect;

import java.lang.reflect.*;

import javax.jws.*;

import com.caucho.config.ConfigException;

import com.caucho.soap.marshall.*;
import com.caucho.soap.skeleton.*;

import com.caucho.util.*;

/**
 * Introspects a web service
 */
public class WebServiceIntrospector {
  public static final L10N L = new L10N(WebServiceIntrospector.class);
  /**
   * Introspects the class
   */
  public DirectSkeleton introspect(Class type)
    throws ConfigException
  {
    if (! type.isAnnotationPresent(WebService.class))
      throw new ConfigException(L.l("{0}: needs a @WebService annotation.  WebServices need a @WebService annotation.",
				    type.getName()));

    MarshallFactory marshallFactory = new MarshallFactory();

    WebService webService = (WebService)type.getAnnotation(WebService.class);

    DirectSkeleton skel = new DirectSkeleton(type);

    Method []methods = type.getMethods();

    for (int i = 0; i < methods.length; i++) {

      if ((methods[i].getModifiers() & Modifier.PUBLIC) == 0)
	continue;

      WebMethod webMethod = methods[i].getAnnotation(WebMethod.class);

      if (webService == null && webMethod == null)
	continue;

      if (webMethod == null && methods[i].getDeclaringClass() != type)
	continue;

      // XXX: needs test
      if (webMethod != null && webMethod.exclude())
	continue;

      PojoMethodSkeleton methodSkel
	= new PojoMethodSkeleton(methods[i], marshallFactory);

      String name = webMethod==null ? "" : webMethod.operationName();
      if (name.equals(""))
          name = methods[i].getName();

      skel.addAction(name, methodSkel);
    }
    
    return skel;
  }
}


