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

package com.caucho.config.cfg;

import com.caucho.bytecode.*;
import com.caucho.config.*;
import com.caucho.config.inject.AbstractBean;
import com.caucho.config.inject.InjectManager;
import com.caucho.config.inject.DecoratorBean;
import com.caucho.config.inject.InterceptorBean;
import com.caucho.config.inject.ManagedBeanImpl;
import com.caucho.config.inject.ProducesBean;
import com.caucho.config.scope.ScopeContext;
import com.caucho.config.types.CustomBeanConfig;
import com.caucho.util.*;
import com.caucho.vfs.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

import javax.annotation.PostConstruct;
import javax.decorator.Decorator;
import javax.enterprise.inject.deployment.DeploymentType;
import javax.enterprise.inject.deployment.Standard;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.Interceptor;

/**
 * Configuration for a classloader root containing webbeans
 */
public class BeansConfig {
  private static final L10N L = new L10N(BeansConfig.class);
  private static final Logger log
    = Logger.getLogger(BeansConfig.class.getName());
  
  private InjectManager _injectManager;
  private Path _root;
  
  private Path _beansFile;
  
  private ArrayList<AbstractBean> _pendingComponentList
    = new ArrayList<AbstractBean>();
  
  private ArrayList<AbstractBean> _pendingBindList
    = new ArrayList<AbstractBean>();

  private ArrayList<Interceptor> _interceptorList;
  
  private ArrayList<Class> _decoratorList
    = new ArrayList<Class>();

  private ArrayList<Class> _pendingClasses
    = new ArrayList<Class>();

  private boolean _isConfigured;

  public BeansConfig(InjectManager injectManager, Path root)
  {
    _injectManager = injectManager;
    
    _root = root;
    _beansFile = root.lookup("META-INF/beans.xml");
    _beansFile.setUserPath(_beansFile.getURL());
  }

  public void setSchemaLocation(String schema)
  {
  }

  /**
   * returns the owning container.
   */
  public InjectManager getContainer()
  {
    return _injectManager;
  }

  /**
   * Returns the owning classloader.
   */
  public ClassLoader getClassLoader()
  {
    return getContainer().getClassLoader();
  }
  
  /**
   * Gets the web beans root directory
   */
  public Path getRoot()
  {
    return _root;
  }

  /**
   * Adds a scanned class
   */
  public void addScannedClass(Class cl)
  {
    _pendingClasses.add(cl);
  }

  /**
   * True if the configuration file has been passed.
   */
  public boolean isConfigured()
  {
    return _isConfigured;
  }

  /**
   * True if the configuration file has been passed.
   */
  public void setConfigured(boolean isConfigured)
  {
    _isConfigured = isConfigured;
  }

  //
  // web-beans syntax
  //

  /**
   * Adds a namespace bean
   */
  public void addCustomBean(CustomBeanConfig bean)
  {
  }

  /**
   * Adds a deploy
   */
  @TagName("Deploy")
  public DeployConfig createDeploy()
  {
    return new DeployConfig();
  }

  /**
   * Adds the interceptors
   */
  @TagName("Interceptors")
  public Interceptors createInterceptors()
  {
    return new Interceptors();
  }

  /**
   * Adds the decorators
   */
  @TagName("Decorators")
  public Decorators createDecorators()
  {
    return new Decorators();
  }

  /**
   * Initialization and validation on parse completion.
   */
  @PostConstruct
  public void init()
  {
    for (Class cl : _decoratorList) {
      DecoratorBean decorator = new DecoratorBean(_injectManager, cl);

      _injectManager.addDecorator(decorator);
    }
    _decoratorList.clear();

    update();
    
    if (_interceptorList != null) {
      _injectManager.setInterceptorList(_interceptorList);
      _interceptorList = null;
    }
  }

  public void update()
  {
    InjectManager injectManager = _injectManager;

    try {
      if (_pendingClasses.size() > 0) {
	ArrayList<Class> pendingClasses
	  = new ArrayList<Class>(_pendingClasses);
	_pendingClasses.clear();

	for (Class cl : pendingClasses) {
	  if (injectManager.getWebComponent(cl) != null)
	    continue;

	  ManagedBeanImpl<?> bean;

	  /*
	  if (cl.isAnnotationPresent(Singleton.class))
	    component = new SingletonClassComponent(cl);
	  else
	  */
	  /*
	  component = new SimpleBean(cl);

	  component.setFromClass(true);
	  component.init();
	  */
	  bean = injectManager.createManagedBean(cl);
	  
	  injectManager.addBean(bean);

	  for (ProducesBean producerBean : bean.getProducerBeans()) {
	    injectManager.addBean(producerBean);
	  }

	  //_pendingComponentList.add(component);
	}
      }
    } catch (Exception e) {
      throw LineConfigException.create(_beansFile.getURL(), 1, e);
    }
  }

  public ScopeContext getScopeContext(Class cl)
  {
    return _injectManager.getScopeContext(cl);
  }

  public void addInterceptor(Class cl)
  {
    if (_interceptorList == null)
      _interceptorList = new ArrayList<Interceptor>();

    InterceptorBean bean = new InterceptorBean(_injectManager, cl);
    bean.init();

    _interceptorList.add(bean);
  }

  @Override
  public String toString()
  {
    if (_root != null)
      return getClass().getSimpleName() + "[" + _root.getURL() + "]";
    else
      return getClass().getSimpleName() + "[]";
  }

  public class Interceptors {
    public void addCustomBean(CustomBeanConfig config)
    {
      Class cl = config.getClassType();
      
      if (cl.isInterface())
	throw new ConfigException(L.l("'{0}' is not valid because <Interceptors> can only contain interceptor implementations",
				      cl.getName()));

      if (! cl.isAnnotationPresent(javax.interceptor.Interceptor.class))
	throw new ConfigException(L.l("'{0}' must have an @Interceptor annotation because it is an interceptor implementation",
				      cl.getName()));

      addInterceptor(cl);
    }
  }

  public class Decorators {
    private String _location;

    public void setConfigLocation(String location)
    {
      _location = location;
    }
    
    public void addCustomBean(CustomBeanConfig config)
    {
      Class cl = config.getClassType();
      
      if (cl.isInterface())
	throw new ConfigException(L.l("'{0}' is not valid because <Decorators> can only contain decorator implementations",
				      cl.getName()));
      
      /*
      if (! comp.isAnnotationPresent(Decorator.class)) {
	throw new ConfigException(L.l("'{0}' must have an @Decorator annotation because it is a decorator implementation",
				      cl.getName()));
      }
      */

      _decoratorList.add(cl);
    }
  }

  public class DeployConfig {
    private String _location;
    
    private ArrayList<Class> _deployList
      = new ArrayList<Class>();

    public void setConfigLocation(String location)
    {
      _location = location;
    }

    public void addAnnotation(Annotation ann)
    {
      Class cl = ann.annotationType();

      /*
      if (! cl.isAnnotationPresent(DeploymentType.class))
	throw new ConfigException(L.l("'{0}' must have a @DeploymentType annotation because because <Deploy> can only contain @DeploymentType annotations",
				      cl.getName()));
      */

      _deployList.add(cl);
    }
    
    @PostConstruct
    public void init()
    {
      _injectManager.setDeploymentTypes(_deployList);
    }
  }
}
