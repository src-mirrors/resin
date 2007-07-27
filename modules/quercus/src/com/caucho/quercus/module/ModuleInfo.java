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

package com.caucho.quercus.module;

import com.caucho.config.ConfigException;
import com.caucho.quercus.env.*;
import com.caucho.quercus.program.*;
import com.caucho.util.L10N;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class-loader specific context for loaded PHP.
 */
public class ModuleInfo {
  private static L10N L = new L10N(ModuleInfo.class);
  private static final Logger log
    = Logger.getLogger(ModuleInfo.class.getName());

  private ModuleContext _context;

  private String _name;
  private QuercusModule _module;

  private HashSet<String> _extensionSet
    = new HashSet<String>();

  private HashMap<String, Value> _constMap
    = new HashMap<String, Value>();

  private HashMap<String, AbstractFunction> _staticFunctions
    = new HashMap<String, AbstractFunction>();

  private IniDefinitions _iniDefinitions = new IniDefinitions();

  /**
   * Constructor.
   */
  public ModuleInfo(ModuleContext context, String name, QuercusModule module)
    throws ConfigException
  {
    _context = context;
    
    _name = name;
    _module = module;

    try {
      introspectPhpModuleClass(module.getClass());
    } catch (Exception e) {
      throw new ConfigException(e);
    }
  }

  public String getName()
  {
    return _name;
  }

  public QuercusModule getModule()
  {
    return _module;
  }
  
  /**
   * Returns true if an extension is loaded.
   */
  public HashSet<String> getLoadedExtensions()
  {
    return _extensionSet;
  }

  public HashMap<String, Value> getConstMap()
  {
    return _constMap;
  }

  /**
   * Returns a named constant.
   */
  public Value getConstant(String name)
  {
    return _constMap.get(name);
  }

  /**
   * Returns the functions.
   */
  public HashMap<String,AbstractFunction> getFunctions()
  {
    return _staticFunctions;
  }

  public IniDefinitions getIniDefinitions()
  {
    return _iniDefinitions;
  }

  /**
   * Introspects the module class for functions.
   *
   * @param cl the class to introspect.
   */
  private void introspectPhpModuleClass(Class cl)
    throws IllegalAccessException, InstantiationException
  {
    for (String ext : _module.getLoadedExtensions()) {
      _extensionSet.add(ext);
    }

    Map<String, Value> map = _module.getConstMap();

    if (map != null)
      _constMap.putAll(map);

    for (Field field : cl.getFields()) {
      if (! Modifier.isPublic(field.getModifiers()))
        continue;

      if (! Modifier.isStatic(field.getModifiers()))
        continue;

      if (! Modifier.isFinal(field.getModifiers()))
        continue;

      Object obj = field.get(null);

      Value value = objectToValue(obj);

      if (value != null)
        _constMap.put(field.getName(), value);
    }

    IniDefinitions iniDefinitions = _module.getIniDefinitions();

    if (map != null)
      _iniDefinitions.addAll(iniDefinitions);

    for (Method method : cl.getMethods()) {
      if (method.getDeclaringClass().equals(Object.class))
	continue;
      
      if (method.getDeclaringClass().isAssignableFrom(AbstractQuercusModule.class))
	continue;
      
      if (! Modifier.isPublic(method.getModifiers()))
        continue;

      // XXX: removed for php/0c2o.qa
      /**
       Class retType = method.getReturnType();

      if (void.class.isAssignableFrom(retType))
        continue;
       */

      Class []params = method.getParameterTypes();

      // php/1a10
      if ("getLoadedExtensions".equals(method.getName()))
        continue;

      if (hasCheckedException(method)) {
	log.warning(L.l("Module method '{0}.{1}' may not throw checked exceptions",
			method.getDeclaringClass().getName(),
			method.getName()));
	continue;
      }

      try {
        StaticFunction function
	  = _context.createStaticFunction(_module, method);

        String methodName = method.getName();

        if (methodName.startsWith("quercus_"))
          methodName = methodName.substring(8);

        AbstractJavaMethod oldFunction
          = (AbstractJavaMethod) _staticFunctions.get(methodName);

        if (oldFunction != null)
          _staticFunctions.put(methodName, oldFunction.overload(function));
        else
          _staticFunctions.put(methodName, function);
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
  }

  private static boolean hasCheckedException(Method method)
  {
    for (Class exnCl : method.getExceptionTypes()) {
      if (! RuntimeException.class.isAssignableFrom(exnCl))
	return true;
    }

    return false;
  }

  public static Value objectToValue(Object obj)
  {
    if (obj == null)
      return NullValue.NULL;
    else if (Byte.class.equals(obj.getClass()) ||
             Short.class.equals(obj.getClass()) ||
             Integer.class.equals(obj.getClass()) ||
             Long.class.equals(obj.getClass())) {
      return LongValue.create(((Number) obj).longValue());
    } else if (Float.class.equals(obj.getClass()) ||
               Double.class.equals(obj.getClass())) {
      return DoubleValue.create(((Number) obj).doubleValue());
    } else if (String.class.equals(obj.getClass())) {
      return new UnicodeValueImpl((String) obj);
    } else {
      // XXX: unknown types, e.g. Character?

      return null;
    }
  }
}

