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

package com.caucho.loader;

import com.caucho.vfs.Depend;
import com.caucho.vfs.Dependency;
import com.caucho.vfs.Path;

import com.caucho.server.util.CauchoSystem;

import java.security.Permission;
import java.util.ArrayList;

/**
 * Static utility classes.
 */
public class Environment {
  private static ArrayList<EnvironmentListener> _globalEnvironmentListeners =
    new ArrayList<EnvironmentListener>();
  
  private static ArrayList<ClassLoaderListener> _globalLoaderListeners =
    new ArrayList<ClassLoaderListener>();
  
  /**
   * Returns the local environment.
   */
  public static EnvironmentClassLoader getEnvironmentClassLoader()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader)
        return (EnvironmentClassLoader) loader;
    }

    return null;
  }
  
  /**
   * Returns the local environment.
   */
  public static EnvironmentClassLoader
    getEnvironmentClassLoader(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader)
        return (EnvironmentClassLoader) loader;
    }

    return null;
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment start/stop
   */
  public static void addEnvironmentListener(EnvironmentListener listener)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    addEnvironmentListener(listener, loader);
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addEnvironmentListener(EnvironmentListener listener,
					    ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).addListener(listener);
        return;
      }
    }

    _globalEnvironmentListeners.add(listener);
  }
  
  /**
   * Remove listener.
   *
   * @param listener object to listen for environment start/stop
   */
  public static void removeEnvironmentListener(EnvironmentListener listener)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    removeEnvironmentListener(listener, loader);
  }
  
  /**
   * Remove listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void removeEnvironmentListener(EnvironmentListener listener,
					    ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).removeListener(listener);
        return;
      }
    }

    _globalEnvironmentListeners.remove(listener);
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addChildEnvironmentListener(EnvironmentListener listener)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    addChildEnvironmentListener(listener, loader);
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addChildEnvironmentListener(EnvironmentListener listener,
						 ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).addChildListener(listener);
        return;
      }
    }
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addChildLoaderListener(AddLoaderListener listener)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    addChildLoaderListener(listener, loader);
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addChildLoaderListener(AddLoaderListener listener,
					    ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).addLoaderListener(listener);
        return;
      }
    }
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   */
  public static void addClassLoaderListener(ClassLoaderListener listener)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    addClassLoaderListener(listener, loader);
  }
  
  /**
   * Add listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addClassLoaderListener(ClassLoaderListener listener,
                                            ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).addListener(listener);
        return;
      }
    }

    _globalLoaderListeners.add(listener);
  }
  
  /**
   * Add close listener.
   *
   * @param listener object to listen for environment create/destroy
   * @param loader the context class loader
   */
  public static void addCloseListener(Object obj)
  {
    addClassLoaderListener(new CloseListener(obj));
  }

  /**
   * Starts the current environment.
   */
  public static void init()
  {
    init(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Starts the current environment.
   */
  public static void init(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).init();
        return;
      }
    }

    for (int i = 0; i < _globalLoaderListeners.size(); i++) {
      ClassLoaderListener listener = _globalLoaderListeners.get(i);

      listener.classLoaderInit(null);
    }
  }

  /**
   * Starts the current environment.
   */
  public static void start()
    throws Throwable
  {
    start(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Starts the current environment.
   */
  public static void start(ClassLoader loader)
    throws Throwable
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).start();
        return;
      }
    }

    init(loader);
    
    for (int i = 0; i < _globalEnvironmentListeners.size(); i++) {
      EnvironmentListener listener = _globalEnvironmentListeners.get(i);

      listener.environmentStart(null);
    }
  }

  /**
   * Starts the current environment.
   */
  public static void stop()
  {
    stop(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Starts the current environment.
   */
  public static void stop(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).stop();
        return;
      }
    }

    ArrayList<EnvironmentListener> listeners;
    listeners = new ArrayList<EnvironmentListener>();
    listeners.addAll(_globalEnvironmentListeners);
    _globalEnvironmentListeners.clear();
    
    for (int i = 0; i < listeners.size(); i++) {
      EnvironmentListener listener = listeners.get(i);

      listener.environmentStop(null);
    }
  }
  
  /**
   * Adds a dependency to the current environment.
   *
   * @param depend the dependency to add
   */
  public static void addDependency(Dependency depend)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    addDependency(depend, loader);
  }

  /**
   * Adds a dependency to the current environment.
   *
   * @param depend the dependency to add
   * @param loader the context loader
   */
  public static void addDependency(Dependency depend,
                                   ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        ((EnvironmentClassLoader) loader).addDependency(depend);
        return;
      }
    }
  }

  /**
   * Returns the topmost dynamic class loader.
   */
  public static DynamicClassLoader getDynamicClassLoader()
  {
    Thread thread = Thread.currentThread();
    
    return getDynamicClassLoader(thread.getContextClassLoader());
  }

  /**
   * Returns the topmost dynamic class loader.
   *
   * @param loader the context loader
   */
  public static DynamicClassLoader getDynamicClassLoader(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof DynamicClassLoader) {
        return (DynamicClassLoader) loader;
      }
    }

    return null;
  }
  
  /**
   * Adds a dependency to the current environment.
   *
   * @param depend the dependency to add
   */
  public static void addDependency(Path path)
  {
    addDependency(new Depend(path));
  }

  /**
   * Adds a dependency to the current environment.
   *
   * @param path the dependency to add
   * @param loader the context loader
   */
  public static void addDependency(Path path, ClassLoader loader)
  {
    addDependency(new Depend(path), loader);
  }

  /**
   * Gets a local variable for the current environment.
   *
   * @param name the attribute name
   *
   * @return the attribute value
   */
  public static Object getAttribute(String name)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    return getAttribute(name, loader);
  }

  /**
   * Returns the current dependency check interval.
   */
  public static long getDependencyCheckInterval()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof DynamicClassLoader)
        return ((DynamicClassLoader) loader).getDependencyCheckInterval();
    }

    return DynamicClassLoader.getGlobalDependencyCheckInterval();
  }

  /**
   * Returns the current dependency check interval.
   */
  public static long getDependencyCheckInterval(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof DynamicClassLoader)
        return ((DynamicClassLoader) loader).getDependencyCheckInterval();
    }

    return DynamicClassLoader.getGlobalDependencyCheckInterval();
  }

  /**
   * Gets a local variable for the current environment.
   *
   * @param name the attribute name
   * @param loader the context loader
   *
   * @return the attribute value
   */
  public static Object getAttribute(String name, ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        Object value = ((EnvironmentClassLoader) loader).getAttribute(name);

        if (value != null)
          return value;
      }
    }

    return null;
  }

  /**
   * Gets a local variable for the current environment.
   *
   * @param name the attribute name
   *
   * @return the attribute value
   */
  public static Object getLevelAttribute(String name)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    return getLevelAttribute(name, loader);
  }

  /**
   * Gets a local variable for the current environment.
   *
   * @param name the attribute name
   * @param loader the context loader
   *
   * @return the attribute value
   */
  public static Object getLevelAttribute(String name, ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        return ((EnvironmentClassLoader) loader).getAttribute(name);
      }
    }

    return null;
  }

  /**
   * Sets a local variable for the current environment.
   *
   * @param name the attribute name
   * @param value the new attribute value
   *
   * @return the old attribute value
   */
  public static Object setAttribute(String name, Object value)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    return setAttribute(name, value, loader);
  }

  /**
   * Sets a local variable for the current environment.
   *
   * @param name the attribute name
   * @param value the new attribute value
   * @param loader the context loader
   *
   * @return the old attribute value
   */
  public static Object setAttribute(String name,
                                    Object value,
                                    ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

        Object oldValue = envLoader.getAttribute(name);

        envLoader.setAttribute(name, value);
        
        if (oldValue != null)
          return oldValue;
      }
    }

    return null;
  }

  /**
   * Adds a permission to the current environment.
   *
   * @param perm the permission to add.
   *
   * @return the old attribute value
   */
  public static void addPermission(Permission perm)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    addPermission(perm, loader);
  }

  /**
   * Adds a permission to the current environment.
   *
   * @param perm the permission to add.
   *
   * @return the old attribute value
   */
  public static void addPermission(Permission perm, ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
        EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

	envLoader.addPermission(perm);
      }
    }
  }

  /**
   * Gets the class loader owner.
   */
  public static Object getOwner()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    return getOwner(loader);
  }

  /**
   * Gets the class loader owner.
   */
  public static Object getOwner(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

	Object owner = envLoader.getOwner();
	
	if (owner != null)
	  return owner;
      }
    }

    return null;
  }

  /**
   * Sets a configuration exception.
   */
  public static void setConfigException(Throwable e)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

	envLoader.setConfigException(e);

	return;
      }
    }
  }

  /**
   * Returns any configuration exception.
   */
  public static Throwable getConfigException()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	EnvironmentClassLoader envLoader = (EnvironmentClassLoader) loader;

	if (envLoader.getConfigException() != null)
	  return envLoader.getConfigException();
      }
    }

    return null;
  }
  
  /**
   * Returns the environment name.
   */
  public static String getEnvironmentName()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	String name = ((EnvironmentClassLoader) loader).getId();

	if (name != null)
	  return name;
	else
	  return "";
      }
    }

    return Thread.currentThread().getContextClassLoader().toString();
  }

  /**
   * Returns the classpath for the environment level.
   */
  public static String getLocalClassPath()
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    return getLocalClassPath(loader);
  }
    
  /**
   * Returns the classpath for the environment level.
   */
  public static String getLocalClassPath(ClassLoader loader)
  {
    for (; loader != null; loader = loader.getParent()) {
      if (loader instanceof EnvironmentClassLoader) {
	return ((EnvironmentClassLoader) loader).getLocalClassPath();
      }
    }

    return CauchoSystem.getClassPath();
  }

  /**
   * destroys the current environment.
   */
  public static void closeGlobal()
  {
    ArrayList<ClassLoaderListener> listeners;
    listeners = new ArrayList<ClassLoaderListener>();
    listeners.addAll(_globalLoaderListeners);
    _globalLoaderListeners.clear();
    
    for (int i = 0; i < listeners.size(); i++) {
      ClassLoaderListener listener = listeners.get(i);

      listener.classLoaderDestroy(null);
    }
  }
}
