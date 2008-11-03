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

package com.caucho.webbeans.component;

import com.caucho.config.*;
import com.caucho.config.j2ee.*;
import com.caucho.config.program.ConfigProgram;
import com.caucho.config.type.*;
import com.caucho.config.types.*;
import com.caucho.ejb3.gen.*;
import com.caucho.util.*;
import com.caucho.webbeans.*;
import com.caucho.webbeans.bytecode.*;
import com.caucho.webbeans.cfg.*;
import com.caucho.webbeans.context.*;
import com.caucho.webbeans.event.*;
import com.caucho.webbeans.manager.*;

import java.lang.reflect.*;
import java.lang.annotation.*;
import java.util.*;

import javax.annotation.*;
import javax.webbeans.*;
import javax.webbeans.manager.Bean;

/**
 * SimpleBean represents a POJO Java bean registered as a WebBean.
 */
public class SimpleBean extends ComponentImpl
{
  private static final L10N L = new L10N(SimpleBean.class);

  private static final Object []NULL_ARGS = new Object[0];
  
  private boolean _isBound;

  private Class _instanceClass;

  private Constructor _ctor;
  private ConfigProgram []_newArgs;
  private Arg []_ctorArgs;

  private Object _scopeAdapter;

  private HashMap<Method,ArrayList<WbInterceptor>> _interceptorMap;

  private String _mbeanName;
  private Class _mbeanInterface;

  public SimpleBean(WebBeansContainer webBeans)
  {
    super(webBeans);
  }

  public SimpleBean()
  {
    this(WebBeansContainer.create());
  }

  public SimpleBean(Class type)
  {
    this(WebBeansContainer.create());

    validateType(type);

    setTargetType(type);
  }

  private void validateType(Class type)
  {
    if (type.isInterface())
      throw new ConfigException(L.l("'{0}' is an invalid SimpleBean because it is an interface",
				    type));
    
    Type []typeParam = type.getTypeParameters();
    if (typeParam != null && typeParam.length > 0) {
      StringBuilder sb = new StringBuilder();
      sb.append(type.getName());
      sb.append("<");
      for (int i = 0; i < typeParam.length; i++) {
	if (i > 0)
	  sb.append(",");
	sb.append(typeParam[i]);
      }
      sb.append(">");
      
      throw new ConfigException(L.l("'{0}' is an invalid SimpleBean class because it defines type variables",
				    sb));
    }
  }

  public void setConstructor(Constructor ctor)
  {
    _ctor = ctor;
  }

  public void setMBeanName(String name)
  {
    _mbeanName = name;
  }

  public Class getMBeanInterface()
  {
    return _mbeanInterface;
  }

  private Class getInstanceClass()
  {
    return _instanceClass;
  }

  /**
   * Sets the init program.
   */
  public void setNewArgs(ArrayList<ConfigProgram> args)
  {
    if (args != null) {
      _newArgs = new ConfigProgram[args.size()];
      args.toArray(_newArgs);
    }
  }

  /**
   * Called for implicit introspection.
   */
  public void introspect()
  {
    Class cl = getTargetClass();
    Class scopeClass = null;

    introspectTypes(cl);

    introspectClass(cl);

    if ("".equals(getName())) {
      String name = cl.getSimpleName();

      name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
	
      setName(name);
    }

    introspectConstructor();
    introspectProduces(cl);

    if (getBindingTypes().size() == 0)
      introspectBindings();
    
    introspectMBean();
  }

  /**
   * Introspects the constructor
   */
  protected void introspectConstructor()
  {
    if (_ctor != null)
      return;
    
    try {
      Class cl = getInstanceClass();

      if (cl == null)
	cl = getTargetClass();
      
      Constructor best = null;
      Constructor second = null;

      for (Constructor ctor : cl.getDeclaredConstructors()) {
	if (_newArgs != null
	    && ctor.getParameterTypes().length != _newArgs.length) {
	  continue;
	}
	else if (best == null) {
	  best = ctor;
	}
	else if (hasBindingAnnotation(ctor)) {
	  if (best != null && hasBindingAnnotation(best))
	    throw new ConfigException(L.l("WebBean {0} has two constructors with binding annotations.",
					  ctor.getDeclaringClass().getName()));
	  best = ctor;
	  second = null;
	}
	else if (ctor.getParameterTypes().length == 0) {
	  best = ctor;
	}
	else if (best.getParameterTypes().length == 0) {
	}
	else {
	  second = ctor;
	}
      }

      if (second != null)
	throw new ConfigException(L.l("{0}: WebBean does not have a unique constructor.  One constructor must be marked with @In or have a binding annotation.",
				      cl.getName()));

      if (best == null)
	throw new ConfigException(L.l("{0}: no constructor found",
				      cl.getName()));

      _ctor = best;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  /**
   * Introspects for MBeans annotation
   */
  private void introspectMBean()
  {
    if (getTargetClass() == null)
      return;
    else if (_mbeanInterface != null)
      return;
    
    for (Class iface : getTargetClass().getInterfaces()) {
      if (iface.getName().endsWith("MBean")
	  || iface.getName().endsWith("MXBean")) {
	_mbeanInterface = iface;
	return;
      }
    }
  }

  protected String getMBeanName()
  {
    if (_mbeanName != null)
      return _mbeanName;

    if (_mbeanInterface == null)
      return null;
    
    String typeName = _mbeanInterface.getSimpleName();

    if (typeName.endsWith("MXBean"))
      typeName = typeName.substring(0, typeName.length() - "MXBean".length());
    else if (typeName.endsWith("MBean"))
      typeName = typeName.substring(0, typeName.length() - "MBean".length());

    String name = getName();

    if (name == null)
      return "type=" + typeName;
    else if (name.equals(""))
      return "type=" + typeName + ",name=default";
    else
      return "type=" + typeName + ",name=" + name;
  }

  /**
   * Creates a new instance of the component.
   */
  @Override
  public Object get(ConfigContext env)
  {
    try {
      Object value;
      boolean isNew = false;

      if (env.canInject(_scope)) {
	if (_scope != null) {
	  value = _scope.get(this, false);
	  
	  if (value != null)
	    return value;
	}
	else {
	  value = env.get(this);
	  
	  if (value != null)
	    return value;
	}
      
	value = createNew(env);
	
	if (_scope != null) {
	  _scope.put(this, value);
	  env = new ConfigContext(this, value, _scope);
	}
	else
	  env.put(this, value);
	
	init(value, env);
      }
      else {
	if (env != null) {
	  value = env.get(this);
	  if (value != null)
	    return value;
	}

	value = _scopeAdapter;
	if (value == null) {
	  ScopeAdapter scopeAdapter = ScopeAdapter.create(getTargetClass());
	  _scopeAdapter = scopeAdapter.wrap(this);
	  value = _scopeAdapter;
	}

	env.put(this, value);
      }

      return value;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected Object createNew(ConfigContext env)
  {
    try {
      if (! _isBound)
	bind();
      
      Object []args;
      if (_ctorArgs != null && _ctorArgs.length > 0) {
	args = new Object[_ctorArgs.length];

	for (int i = 0; i < args.length; i++)
	  args[i] = _ctorArgs[i].eval(env);
      }
      else
	args = NULL_ARGS;
      
      Object value = _ctor.newInstance(args);

      if (isSingleton())
	SerializationAdapter.setHandle(value, getHandle());

      if (env != null)
	env.put(this, value);

      return value;
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Binds parameters
   */
  public void bind()
  {
    synchronized (this) {
      if (_isBound)
	return;
      _isBound = true;

      Class cl = getTargetClass();

      ArrayList<ConfigProgram> injectList = new ArrayList<ConfigProgram>();
      InjectIntrospector.introspectInject(injectList, cl);
      _injectProgram = new ConfigProgram[injectList.size()];
      injectList.toArray(_injectProgram);
      
      ArrayList<ConfigProgram> initList = new ArrayList<ConfigProgram>();
      InjectIntrospector.introspectInit(initList, cl);
      _initProgram = new ConfigProgram[initList.size()];
      initList.toArray(_initProgram);
      
      ArrayList<ConfigProgram> destroyList = new ArrayList<ConfigProgram>();
      InjectIntrospector.introspectDestroy(destroyList, cl);
      _destroyProgram = new ConfigProgram[destroyList.size()];
      destroyList.toArray(_destroyProgram);
      
      if (_ctor == null)
	introspectConstructor();

      if (_ctor != null) {
	String loc = _ctor.getDeclaringClass().getName() + "(): ";
	Type []param = _ctor.getGenericParameterTypes();
	Annotation [][]paramAnn = _ctor.getParameterAnnotations();

	Arg []ctorArgs = new Arg[param.length];

	for (int i = 0; i < param.length; i++) {
	  ComponentImpl arg;

	  if (_newArgs != null && i < _newArgs.length) {
	    ConfigProgram argProgram = _newArgs[i];
	    ConfigType type = TypeFactory.getType(param[i]);

	    ctorArgs[i] = new ProgramArg(type, argProgram);
	  }

	  if (ctorArgs[i] == null) {
	    ctorArgs[i] = new BeanArg(loc, param[i], paramAnn[i]);
	  }
	}
	
	_ctorArgs = ctorArgs;
      }

      introspectObservers(getTargetClass());

      PojoBean bean = new PojoBean(getTargetClass());
      bean.setSingleton(isSingleton());
      bean.introspect();

      Class instanceClass = bean.generateClass();

      if (instanceClass == _instanceClass && isSingleton())
	instanceClass = SerializationAdapter.gen(_instanceClass);

      if (instanceClass != null && instanceClass != _instanceClass) {
	try {
	  if (_ctor != null)
	    _ctor = instanceClass.getConstructor(_ctor.getParameterTypes());
	  
	  _instanceClass = instanceClass;
	} catch (Exception e) {
	  throw ConfigException.create(e);
	}
      }
    }
  }

  protected ComponentImpl createArg(ConfigType type, ConfigProgram program)
  {
    Object value = program.configure(type);

    if (value != null)
      return new SingletonBean(getWebBeans(), value);
    else
      return null;
  }
  
  /**
   * Introspects the methods for any @Produces
   */
  protected void introspectBindings()
  {
    introspectBindings(getTargetClass().getAnnotations());
  }

  abstract static class Arg {
    public void bind()
    {
    }
    
    abstract public Object eval(ConfigContext env);
  }

  class BeanArg extends Arg {
    private String _loc;
    private Type _type;
    private Annotation []_bindings;
    private Bean _bean;

    BeanArg(String loc, Type type, Annotation []bindings)
    {
      _loc = loc;
      _type = type;
      _bindings = bindings;
      bind();
    }

    public void bind()
    {
      if (_bean == null) {
	_bean = bindParameter(_loc, _type, _bindings);

	if (_bean == null)
	  throw new ConfigException(L.l("{0}: {1} does not have valid arguments",
					_loc, _ctor));
      }
    }
    
    public Object eval(ConfigContext env)
    {
      if (_bean == null)
	bind();
      
      return _webBeans.getInstance(_bean, env);
    }
  }

  static class ProgramArg extends Arg {
    private ConfigType _type;
    private ConfigProgram _program;

    ProgramArg(ConfigType type, ConfigProgram program)
    {
      _type = type;
      _program = program;
    }
    
    public Object eval(ConfigContext env)
    {
      return _program.configure(_type, env);
    }
  }
}
