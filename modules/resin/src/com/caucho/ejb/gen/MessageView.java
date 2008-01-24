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
 * Represents a public interface to a stateful bean, e.g. a stateful view
 */
public class MessageView extends View {
  private static final L10N L = new L10N(MessageView.class);

  private MessageGenerator _messageBean;
  
  private ArrayList<BusinessMethodGenerator> _businessMethods
    = new ArrayList<BusinessMethodGenerator>();

  public MessageView(MessageGenerator bean, ApiClass api)
  {
    super(bean, api);

    _messageBean = bean;
  }

  public MessageGenerator getMessageBean()
  {
    return _messageBean;
  }

  public String getContextClassName()
  {
    return getMessageBean().getClassName();
  }

  /**
   * Returns the introspected methods
   */
  public ArrayList<? extends BusinessMethodGenerator> getMethods()
  {
    return _businessMethods;
  }

  /**
   * Introspects the APIs methods, producing a business method for
   * each.
   */
  @Override
  public void introspect()
  {
    ApiClass implClass = getEjbClass();
    ApiClass apiClass = getApi();

    for (ApiMethod apiMethod : apiClass.getMethods()) {
      if (apiMethod.getDeclaringClass().equals(Object.class))
	continue;
      if (apiMethod.getDeclaringClass().getName().startsWith("javax.ejb.")
	  && ! apiMethod.getName().equals("remove"))
	continue;

      int index = _businessMethods.size();
      
      BusinessMethodGenerator bizMethod = createMethod(apiMethod, index);
      
      if (bizMethod != null) {
	bizMethod.introspect(bizMethod.getApiMethod(),
			     bizMethod.getImplMethod());
	
	_businessMethods.add(bizMethod);
      }
    }
  }

  /**
   * Generates the view code.
   */
  public void generate(JavaWriter out)
    throws IOException
  {
    HashMap map = new HashMap();
    map.put("caucho.ejb.xa", "done");
    
    for (BusinessMethodGenerator bizMethod : _businessMethods) {
      bizMethod.generatePrologueTop(out, map);
    }
    
    for (BusinessMethodGenerator bizMethod : _businessMethods) {
      bizMethod.generate(out, map);
    }
  }

  protected BusinessMethodGenerator
    createMethod(ApiMethod apiMethod, int index)
  {
    ApiMethod implMethod = findImplMethod(apiMethod);

    if (implMethod == null)
      return null;
    
    BusinessMethodGenerator bizMethod
      = new MessageMethod(this,
			  apiMethod.getMethod(),
			  implMethod.getMethod(),
			  index);

    return bizMethod;
  }
  
  protected ApiMethod findImplMethod(ApiMethod apiMethod)
  {
    ApiMethod implMethod = getEjbClass().getMethod(apiMethod);

    if (implMethod != null)
      return implMethod;
  
    throw ConfigException.create(apiMethod.getMethod(),
				 L.l("api method has no corresponding implementation in '{0}'",
				     getEjbClass().getName()));
  }
}
