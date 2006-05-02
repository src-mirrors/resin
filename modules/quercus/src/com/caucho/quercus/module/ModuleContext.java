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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.io.InputStream;
import java.io.IOException;

import java.net.URL;

import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Enumeration;

import java.util.logging.*;

import com.caucho.config.ConfigException;

import com.caucho.loader.EnvironmentLocal;

import com.caucho.quercus.QuercusRuntimeException;

import com.caucho.quercus.env.*;

import com.caucho.quercus.program.ClassDef;
import com.caucho.quercus.program.JavaClassDef;

import com.caucho.util.L10N;

import com.caucho.vfs.ReadStream;
import com.caucho.vfs.Vfs;

/**
 * Class-loader specific context for loaded PHP.
 */
public class ModuleContext {
  private static L10N L = new L10N(ModuleContext.class);
  private static final Logger log
    = Logger.getLogger(ModuleContext.class.getName());

  private static EnvironmentLocal<ModuleContext> _localModuleContext
    = new  EnvironmentLocal<ModuleContext>();

  private ClassLoader _loader;
  
  private HashMap<String, QuercusModule> _modules
    = new HashMap<String, QuercusModule>();
  
  private HashMap<String, ModuleInfo> _moduleInfoMap
    = new HashMap<String, ModuleInfo>();

  private HashSet<String> _extensionSet
    = new HashSet<String>();

  private HashMap<String, Value> _constMap
    = new HashMap<String, Value>();

  private HashMap<String, StaticFunction> _staticFunctions
    = new HashMap<String, StaticFunction>();

  private HashMap<String, ClassDef> _staticClasses
    = new HashMap<String, ClassDef>();

  private HashMap<String, ClassDef> _lowerStaticClasses
    = new HashMap<String, ClassDef>();

  private HashMap<String, JavaClassDef> _javaClassWrappers
    = new HashMap<String, JavaClassDef>();

  private HashMap<String, JavaClassDef> _lowerJavaClassWrappers
    = new HashMap<String, JavaClassDef>();

  private HashMap<String, StringValue> _iniMap
    = new HashMap<String, StringValue>();

  /**
   * Constructor.
   */
  public ModuleContext()
  {
    this(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Constructor.
   */
  public ModuleContext(ClassLoader loader)
  {
    _loader = loader;
  }
  
  public static ModuleContext getLocalContext(ClassLoader loader)
  {
    ModuleContext context = _localModuleContext.getLevel(loader);

    if (context == null) {
      context = new ModuleContext(loader);
      _localModuleContext.set(context, loader);
    }

    return context;
  }
  
  /**
   * Adds a module
   */
  public static ModuleInfo addModule(String name, QuercusModule module)
    throws ConfigException
  {
    ClassLoader loader = module.getClass().getClassLoader();

    ModuleContext context = getLocalContext(loader);

    return context.addModuleInfo(name, module);
  }
  
  /**
   * Adds a class
   */
  public static JavaClassDef addClass(String name, Class type, String ext)
    throws ConfigException, IllegalAccessException, InstantiationException
  {
    ClassLoader loader = type.getClassLoader();

    ModuleContext context = getLocalContext(loader);

    return context.addClassInfo(name, type, ext);
  }

  /**
   * Adds module info.
   */
  private ModuleInfo addModuleInfo(String name, QuercusModule module)
    throws ConfigException
  {
    ModuleInfo info = _moduleInfoMap.get(name);

    if (info == null) {
      info = new ModuleInfo(this, name, module);
      _moduleInfoMap.put(name, info);
    }

    return info;
  }
  
  private JavaClassDef addClassInfo(String name, Class type,
				    String extension)
    throws IllegalAccessException, InstantiationException
  {
    JavaClassDef def = _javaClassWrappers.get(name);

    if (def == null)
      def = addJavaClass(name, type, extension);

    return def;
  }

  /**
   * Adds a java class
   */
  public JavaClassDef getJavaClassDefinition(String className)
  {
    JavaClassDef def = _javaClassWrappers.get(className);

    if (def != null)
      return def;

    try {
      Class type;

      try {
        type = Class.forName(className, false, _loader);
      }
      catch (ClassNotFoundException e) {
        throw new ClassNotFoundException(L.l("`{0}' not valid {1}", className, e.toString()));

      }

      def = new JavaClassDef(this, className, type);

      _javaClassWrappers.put(className, def);

      def.introspect(this);

      return def;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new QuercusRuntimeException(e);
    }
  }

  /**
   * Finds the java class wrapper.
   */
  public ClassDef findJavaClassWrapper(String name)
  {
    ClassDef def = _javaClassWrappers.get(name);

    if (def != null)
      return def;

    return _lowerJavaClassWrappers.get(name.toLowerCase());
  }

  /**
   * Returns an array of the defined functions.
   */
  public ArrayValue getDefinedFunctions()
  {
    ArrayValue internal = new ArrayValueImpl();

    for (String name : _staticFunctions.keySet()) {
      internal.put(name);
    }

    return internal;
  }

  /**
   * Returns the class with the given name.
   */
  public ClassDef findClass(String name)
  {
    ClassDef def = _staticClasses.get(name);

    if (def == null)
      def = _lowerStaticClasses.get(name.toLowerCase());

    return def;
  }

  /**
   * Returns the class maps.
   */
  public HashMap<String, ClassDef> getClassMap()
  {
    return _staticClasses;
  }

  /**
   * Returns the module with the given name.
   */
  public QuercusModule findModule(String name)
  {
    return _modules.get(name);
  }

  /**
   * Returns true if an extension is loaded.
   */
  public boolean isExtensionLoaded(String name)
  {
    return _extensionSet.contains(name);
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
      return new StringValueImpl((String) obj);
    } else {
      // XXX: unknown types, e.g. Character?

      return null;
    }
  }

  /**
   * Introspects the module class for functions.
   *
   * @param name the php class name
   * @param type the class to introspect.
   * @param extension the extension provided by the class, or null
   */
  private JavaClassDef addJavaClass(String name, Class type, String extension)
    throws IllegalAccessException, InstantiationException
  {
    if (log.isLoggable(Level.FINEST)) {
      if (extension == null)
        log.finest(L.l("PHP loading class {0} with type {1}", name, type.getName()));
      else
        log.finest(L.l("PHP loading class {0} with type {1} providing extension {2}", name, type.getName(), extension));
    }

    JavaClassDef def = new JavaClassDef(this, name, type);

    _javaClassWrappers.put(name, def);
    _lowerJavaClassWrappers.put(name.toLowerCase(), def);

    _staticClasses.put(name, def);
    _lowerStaticClasses.put(name.toLowerCase(), def);

    def.introspect(this);

    if (extension != null)
      _extensionSet.add(extension);

    return def;
  }
}

