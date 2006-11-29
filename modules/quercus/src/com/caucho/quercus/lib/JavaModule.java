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

package com.caucho.quercus.lib;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.JavaValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.program.JavaClassDef;
import com.caucho.util.L10N;

import java.util.logging.Logger;

/**
 * Java functions
 */
public class JavaModule extends AbstractQuercusModule {
  private static final Logger log =
    Logger.getLogger(JavaModule.class.getName());

  private static final L10N L = new L10N(JavaModule.class);

  /**
   * Call the Java constructor and return the wrapped Java object.
   */
  public static Object java(Env env,
			   String className,
			   Value []args)
  {
    try {
      JavaClassDef def = env.getJavaClassDefinition(className);

      if (def != null)
	return def.callNew(env, args);
      
      ClassLoader loader = Thread.currentThread().getContextClassLoader();
      
      Class cl = Class.forName(className, false, loader);

      try {
	return cl.newInstance();
      } catch (Throwable e) {
      }

      return new JavaValue(env, null, def);
    } catch (Throwable e) {
      env.warning(e);

      return null;
    }
  }

  /**
   * Call the Java constructor and return the wrapped Java object.
   */
  public static Object java_class(Env env,
				  String className)
  {
    try {
      JavaClassDef def = env.getJavaClassDefinition(className);

      return new JavaValue(env, null, def);
    } catch (Throwable e) {
      env.warning(e);

      return null;
    }
  }
}

