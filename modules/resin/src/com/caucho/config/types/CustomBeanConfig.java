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

package com.caucho.config.types;

import com.caucho.config.*;
import com.caucho.config.program.*;
import com.caucho.config.type.*;
import com.caucho.config.j2ee.*;
import com.caucho.jca.program.*;
import com.caucho.naming.*;
import com.caucho.util.*;
import com.caucho.webbeans.*;
import com.caucho.webbeans.cfg.*;
import com.caucho.webbeans.component.*;
import com.caucho.webbeans.context.*;
import com.caucho.webbeans.manager.WebBeansContainer;
import com.caucho.xml.QName;

import java.util.*;
import java.util.logging.*;
import java.lang.reflect.*;
import java.lang.annotation.*;

import javax.annotation.*;

import javax.resource.spi.*;

import javax.webbeans.*;

import org.w3c.dom.Node;

/**
 * Custom bean configured by namespace
 */
public class CustomBeanConfig {
  private static final Logger log
    = Logger.getLogger(CustomBeanConfig.class.getName());
  
  private static final L10N L = new L10N(CustomBeanConfig.class);

  private static final String RESIN_NS
    = "http://caucho.com/ns/resin";

  private Class _class;
  private ClassComponent _component;
  private ConfigType _configType;

  private ArrayList<ConfigProgram> _args;

  private QName _name;

  private String _filename;
  private int _line;

  private ContainerProgram _init;

  public CustomBeanConfig(QName name, Class cl)
  {
    _name = name;

    _class = cl;

    if (! Annotation.class.isAssignableFrom(cl)) {
      _component = new ClassComponent();
      _component.setTargetType(cl);
      // _component.setScopeClass(Dependent.class);
    }

    _configType = TypeFactory.getCustomBeanType(cl);
  }

  public ConfigType getConfigType()
  {
    return _configType;
  }

  public Class getClassType()
  {
    return _class;
  }
  
  public void setConfigLocation(String filename, int line)
  {
    _filename = filename;
    _line = line;
  }

  public String getFilename()
  {
    return _filename;
  }

  public int getLine()
  {
    return _line;
  }

  /*
  public void setClass(Class cl)
  {
    _component.setInstanceClass(cl);
  }

  public void setScope(String scope)
  {
    _component.setScope(scope);
  }
  */

  public void addArg(ConfigProgram arg)
  {
    if (_args == null)
      _args = new ArrayList<ConfigProgram>();

    _args.add(arg);
  }

  public void addInitProgram(ConfigProgram program)
  {
    if (_init == null) {
      _init = new ContainerProgram();

      if (_component != null)
	_component.setInit(_init);
    }

    _init.addProgram(program);
  }
  
  public void addBuilderProgram(ConfigProgram program)
  {
    QName name = program.getQName();

    if (name == null) {
      addInitProgram(program);

      return;
    }
    
    Class cl = createClass(name);

    if (cl == null) {
    }
    else if (Annotation.class.isAssignableFrom(cl)) {
      ConfigType type = TypeFactory.getType(cl);

      Object bean = type.create(null, name);

      Node node = getProgramNode(program);

      if (node != null)
	ConfigContext.getCurrent().configureNode(node, bean, type);
      
      Annotation ann = (Annotation) type.replaceObject(bean);

      addAnnotation(ann);

      return;
    }
    
    if (name.getNamespaceURI().equals(_name.getNamespaceURI())) {
      if (_configType.getAttribute(name) == null)
	throw new ConfigException(L.l("'{0}' is an unknown field for '{1}'",
				      name.getLocalName(), _class.getName()));
      
      addInitProgram(program);
    }

    else
      throw new ConfigException(L.l("'{0}' is an unknown field name.  Fields must belong to the same namespace as the class",
				    name.getCanonicalName()));
  }

  private Node getProgramNode(ConfigProgram program)
  {
    if (program instanceof NodeBuilderChildProgram)
      return ((NodeBuilderChildProgram) program).getNode();
    return null;
  }

  public void addAnnotation(Annotation ann)
  {
    Class type = ann.annotationType();

    Class metaType = null;

    if (type.isAnnotationPresent(ScopeType.class)) {
      metaType = ScopeType.class;

      _component.setScopeType(type);
    }
    
    if (type.isAnnotationPresent(DeploymentType.class)) {
      if (metaType != null)
	throw new ConfigException(L.l("@{0} is an illegal @DeploymentType because it also has a @{1} annotation",
				      type.getName(), metaType.getName()));
      
      metaType = DeploymentType.class;

      _component.setDeploymentType(type);
    }
    
    if (type.isAnnotationPresent(BindingType.class)) {
      if (metaType != null)
	throw new ConfigException(L.l("@{0} is an illegal @BindingType because it also has a @{1} annotation",
				      type.getName(), metaType.getName()));
      
      metaType = BindingType.class;

      _component.addBinding(ann);
    }

    if (type.equals(Named.class)) {
      metaType = Named.class;

      _component.setName(((Named) ann).value());
    }
    
    if (type.isAnnotationPresent(Stereotype.class)) {
      metaType = Stereotype.class;

      addStereotype(type);
    }

    if (metaType == null)
      throw new ConfigException(L.l("'{0}' is an invalid annotation.  An annotation must be a @BindingType, @ScopeType, @DeploymentType",
				    ann));
  }

  private void addStereotype(Class type)
  {
    for (Annotation ann : type.getAnnotations()) {
      Class annType = ann.annotationType();
      
      if (annType.equals(Named.class)) {
	if (_component.getName() == null)
	  _component.setName("");
      }
      else if (annType.isAnnotationPresent(DeploymentType.class)) {
	if (_component.getDeploymentType() == null)
	  _component.setDeploymentType(annType);
      }
      else if (annType.isAnnotationPresent(ScopeType.class)) {
	if (_component.getScopeType() == null)
	  _component.setScopeType(annType);
      }
      else if (annType.isAnnotationPresent(BindingType.class)) {
	_component.addBinding(ann);
      }
    }
  }

  private Class createClass(QName name)
  {
    String uri = name.getNamespaceURI();

    if (uri.equals(RESIN_NS)) {
      return createResinClass(name.getLocalName());
    }

    if (! uri.startsWith("urn:java:"))
      return null;

    String pkg = uri.substring("urn:java:".length());

    String className = pkg + "." + name.getLocalName();

    try {
      ClassLoader loader = Thread.currentThread().getContextClassLoader();

      Class cl = Class.forName(className, false, loader);

      return cl;
    } catch (ClassNotFoundException e) {
      log.log(Level.FINEST, e.toString(), e);

      return null;
    }
  }

  private Class createResinClass(String name)
  {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();

    try {
      String className = "javax.webbeans." + name;
      
      Class cl = Class.forName(className, false, loader);

      return cl;
    } catch (ClassNotFoundException e) {
      log.log(Level.FINEST, e.toString(), e);
    }
    
    try {
      String className = "com.caucho.config." + name;
      
      Class cl = Class.forName(className, false, loader);

      return cl;
    } catch (ClassNotFoundException e) {
      log.log(Level.FINEST, e.toString(), e);
    }

    return null;
  }

  public ComponentImpl getComponent()
  {
    return _component;
  }

  @PostConstruct
  public void init()
  {
    if (_component != null) {
      initComponent();
    
      WebBeansContainer webBeans = WebBeansContainer.create();

      webBeans.addBean(_component);
    }
  }

  public void initComponent()
  {
    if (_args != null)
      _component.setNewArgs(_args);

    _component.init();
  }
}
