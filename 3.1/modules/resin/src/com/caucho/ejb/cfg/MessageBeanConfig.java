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

package com.caucho.ejb.cfg;

import java.util.*;
import java.util.logging.*;

import javax.annotation.*;
import javax.jms.*;
import javax.resource.spi.*;

import com.caucho.config.*;
import com.caucho.config.types.*;
import com.caucho.ejb.manager.*;
import com.caucho.webbeans.cfg.AbstractBeanConfig;
import com.caucho.webbeans.component.ComponentImpl;
import com.caucho.webbeans.manager.WebBeansContainer;

import com.caucho.util.*;

/**
 * ejb-message-bean configuration
 */
public class MessageBeanConfig extends AbstractBeanConfig
{
  private static final L10N L = new L10N(MessageBeanConfig.class);
  private static final Logger log
    = Logger.getLogger(MessageBeanConfig.class.getName());

  private ActivationSpec _activationSpec;
  
  private Class _destinationType;
  private String _destinationName;
  private Object _destination;
  private int _messageConsumerMax;

  /**
   * Sets the activation spec
   */
  public void setActivationSpec(ActivationSpec spec)
  {
    _activationSpec = spec;
  }
  
  public void setDestinationType(Class type)
  {
    _destinationType = type;
  }

  public void setDestinationName(String name)
  {
    _destinationName = name;
  }

  public void setDestination(Object destination)
  {
    _destination = destination;

    if (destination == null)
      throw new NullPointerException();
  }

  public void setMessageConsumerMax(int messageConsumerMax)
  {
    _messageConsumerMax = messageConsumerMax;
  }

  @PostConstruct
  public void init()
  {
    if (getInstanceClass() == null)
      throw new ConfigException(L.l("ejb-message-bean requires a 'class' attribute"));
    
    EjbContainer ejbContainer = EjbContainer.create();
    EjbConfigManager configManager = ejbContainer.getConfigManager();

    EjbMessageBean bean = new EjbMessageBean(configManager, "config");
    bean.setConfigLocation(getFilename(), getLine());
    
    bean.setEJBClass(getInstanceClass());

    String name = getName();
    
    if (name == null)
      name = getJndiName();

    if (name == null)
      name = getInstanceClass().getSimpleName();

    bean.setEJBName(name);

    if (getInit() != null)
      bean.setInit(getInit());

    String loc = getInstanceClass().getName() + ": ";
    WebBeansContainer webBeans = WebBeansContainer.create();

    bean.setMessageConsumerMax(_messageConsumerMax);
    
    if (_destination != null) {
      bean.setDestinationValue((Destination) _destination);
    }
    else if (_activationSpec != null) {
      bean.setActivationSpec(_activationSpec);
    }
    else {
      ComponentImpl destComp;

      Class destinationType = _destinationType;
      
      if (_destinationType == null)
	destinationType = Destination.class;

      if (_destinationName != null)
	destComp = webBeans.bind(loc, destinationType, _destinationName);
      else
	destComp = webBeans.bind(loc, destinationType);

      if (destComp == null)
	throw new ConfigException(L.l("'{0}' is an unknown destination type '{1}'",
				      _destinationName,
				      _destinationType.getName()));

      bean.setDestinationValue((Destination) destComp.get());

      ComponentImpl comp = webBeans.bind(loc, ConnectionFactory.class);

      if (comp == null)
	throw new ConfigException(L.l("ejb-message-bean requires a configured JMS ConnectionFactory"));
      bean.setConnectionFactoryValue((ConnectionFactory) comp.get());
    }

    bean.init();

    configManager.setBeanConfig(name, bean);

    // XXX: timing?
    // configManager.start();
  }
}

