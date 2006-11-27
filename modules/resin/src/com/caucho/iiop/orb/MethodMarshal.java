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

package com.caucho.iiop.orb;

import java.io.*;
import java.util.*;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/**
 * Proxy implementation for ORB clients.
 */
public class MethodMarshal {
  private String _name;
  private Marshal []_args;
  private Marshal _ret;
  
  MethodMarshal(Method method)
  {
    MarshalFactory factory = MarshalFactory.create();

    _name = method.getName();
    
    Class []params = method.getParameterTypes();

    _args = new Marshal[params.length];

    for (int i = 0; i < params.length; i++)
      _args[i] = factory.create(params[i]);

    _ret = factory.create(method.getReturnType());
  }

  public Object invoke(org.omg.CORBA.Object obj,
		       Object []args)
    throws Throwable
  {
    org.omg.CORBA_2_3.portable.OutputStream os = null;
    /*
      = ((org.omg.CORBA_2_3.portable.OutputStream) obj._create_output_stream(_name));
    */
    
    for (int i = 0; i < _args.length; i++) {
      _args[i].marshal(os, args[i]);
    }

    org.omg.CORBA_2_3.portable.InputStream is = null;
    /*
    org.omg.CORBA_2_3.portable.InputStream is
      = ((org.omg.CORBA_2_3.portable.InputStream)
	 obj._invoke(os));
    */

    return _ret.unmarshal(is);
  }
}
