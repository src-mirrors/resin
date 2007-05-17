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

package com.caucho.config.j2ee;

import com.caucho.amber.manager.EntityManagerProxy;
import com.caucho.config.BuilderProgram;
import com.caucho.config.ConfigException;
import com.caucho.util.L10N;
import com.caucho.util.Log;

import org.omg.CORBA.ORB;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.annotation.Resources;
import javax.ejb.EJB;
import javax.ejb.SessionContext;
import javax.ejb.MessageDrivenContext;
import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.persistence.PersistenceContext;
import javax.persistence.PersistenceUnit;
import javax.transaction.UserTransaction;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceRef;
import java.beans.Introspector;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.*;

/**
 * Analyzes a bean for @Inject tags.
 */
public class InjectIntrospector {
  private static final L10N L = new L10N(InjectIntrospector.class);
  private static final Logger log = Log.open(InjectIntrospector.class);

  /**
   * Analyzes a bean for @Inject tags, building an init program for them.
   */
  public static void configure(Object obj)
    throws Throwable
  {
    if (obj != null) {
      for (BuilderProgram program : introspect(obj.getClass())) {
        program.configure(obj);
      }
    }
  }

  /**
   * Analyzes a bean for @Inject tags, building an init program for them.
   */
  public static InjectProgram introspectProgram(Class type)
    throws ConfigException
  {
    return new InjectProgram(introspect(type));
  }

  /**
   * Analyzes a bean for @Inject tags, building an init program for them.
   */
  public static ArrayList<BuilderProgram> introspectStatic(Class type)
    throws ConfigException
  {
    return introspect(type);
  }

  /**
   * Analyzes a bean for @Inject tags, building an init program for them.
   */
  public static ArrayList<BuilderProgram> introspect(Class type)
    throws ConfigException
  {
    ArrayList<BuilderProgram> initList = new ArrayList<BuilderProgram>();

    try {
      introspectImpl(initList, type);

      introspectConstruct(initList, type);
    } catch (ClassNotFoundException e) {
    } catch (Error e) {
    }

    return initList;
  }

  /**
   * Analyzes a bean for @Inject tags, building an init program for them.
   */
  public static ArrayList<BuilderProgram> introspectNoInit(Class type)
    throws ConfigException
  {
    ArrayList<BuilderProgram> initList = new ArrayList<BuilderProgram>();

    try {
      introspectImpl(initList, type);
    } catch (ClassNotFoundException e) {
    } catch (Error e) {
    }

    return initList;
  }

  private static void introspectImpl(ArrayList<BuilderProgram> initList,
                                     Class type)
    throws ConfigException, ClassNotFoundException
  {
    if (type == null || type.equals(Object.class))
      return;

    introspectImpl(initList, type.getSuperclass());

    configureClassResources(initList, type);

    for (Method method : type.getDeclaredMethods()) {
      String fieldName = method.getName();
      Class []param = method.getParameterTypes();

      if (param.length != 1)
        continue;

      if (fieldName.startsWith("set") && fieldName.length() > 3) {
        fieldName = fieldName.substring(3);

        char ch = fieldName.charAt(0);

        if (Character.isUpperCase(ch) &&
            (fieldName.length() == 1 ||
             Character.isLowerCase(fieldName.charAt(1)))) {
          fieldName = Character.toLowerCase(ch) + fieldName.substring(1);
        }
      }

      configure(initList, method, fieldName, param[0]);
    }
  }

  public static void
    introspectConstruct(ArrayList<BuilderProgram> initList, Class type)
    throws ConfigException
  {
    if (type == null || type.equals(Object.class))
      return;

    introspectConstruct(initList, type.getSuperclass());

    for (Method method : type.getDeclaredMethods()) {
      if (method.isAnnotationPresent(PostConstruct.class)) {
        if (method.getParameterTypes().length != 0)
          throw new ConfigException(L.l("{0}: @PostConstruct is requires zero arguments",
                                        method.getName()));

        PostConstructProgram initProgram
          = new PostConstructProgram(method);

        if (! initList.contains(initProgram))
          initList.add(initProgram);
      }

      if (method.isAnnotationPresent(PreDestroy.class)) {
        if (method.getParameterTypes().length != 0)
          throw new ConfigException(L.l("{0}: @PreDestroy is requires zero arguments",
                                        method.getName()));

        initList.add(new PreDestroyProgram(method));
      }
    }
  }

  public static void
    configureClassResources(ArrayList<BuilderProgram> initList,
                            Class type)
    throws ConfigException
  {
    Resources resources = (Resources) type.getAnnotation(Resources.class);
    if (resources != null) {
      for (Resource resource : resources.value()) {
        introspectClassResource(initList, type, resource);
      }
    }

    Resource resource = (Resource) type.getAnnotation(Resource.class);
    if (resource != null) {
      introspectClassResource(initList, type, resource);
    }

    PersistenceContext pc
      = (PersistenceContext) type.getAnnotation(PersistenceContext.class);
    if (pc != null) {
      String foreignName = findPersistenceContextName(pc.name(),
                                                      pc.unitName());

      if (! foreignName.equals(pc.name()) && ! "".equals(pc.name()))
        initList.add(new JndiBindProgram(pc.name(), foreignName,
                                         javax.persistence.EntityManager.class));
    }

    for (Field field : type.getDeclaredFields()) {
      configure(initList, field, field.getName(), field.getType());
    }
  }

  private static void
    introspectClassResource(ArrayList<BuilderProgram> initList,
                            Class type,
                            Resource resource)
    throws ConfigException
  {
    String name = resource.name();

    Field field = findField(type, name);

    if (field != null) {
      initList.add(configureResource(field, field.getName(), field.getType(),
                                     resource.name(),
                                     resource.type().getName(),
                                     resource.name()));

      return;
    }

    Method method = findMethod(type, name);

    if (method != null) {
      initList.add(configureResource(method, method.getName(),
                                     method.getParameterTypes()[0],
                                     resource.name(),
                                     resource.type().getName(),
                                     resource.name()));

      return;
    }
  }

  private static Field findField(Class type, String name)
  {
    for (Field field : type.getDeclaredFields()) {
      if (field.getName().equals(name))
        return field;
    }

    return null;
  }

  private static Method findMethod(Class type, String name)
  {
    for (Method method : type.getDeclaredMethods()) {
      if (method.getParameterTypes().length != 1)
        continue;

      String methodName = method.getName();
      if (! methodName.startsWith("set"))
        continue;

      methodName = Introspector.decapitalize(methodName.substring(3));

      if (name.equals(methodName))
        return method;
    }

    return null;
  }

  public static void configure(ArrayList<BuilderProgram> initList,
                               AccessibleObject field,
                               String fieldName,
                               Class fieldType)
    throws ConfigException
  {
    if (field.isAnnotationPresent(Resource.class))
      configureResource(initList, field, fieldName, fieldType);
    else if (field.isAnnotationPresent(EJB.class))
      configureEJB(initList, field, fieldName, fieldType);
    else if (field.isAnnotationPresent(PersistenceUnit.class))
      configurePersistenceUnit(initList, field, fieldName, fieldType);
    else if (field.isAnnotationPresent(PersistenceContext.class))
      configurePersistenceContext(initList, field, fieldName, fieldType);
    else if (field.isAnnotationPresent(WebServiceRef.class))
      configureWebServiceRef(initList, field, fieldName, fieldType);
  }

  private static void configureResource(ArrayList<BuilderProgram> initList,
                                        AccessibleObject field,
                                        String fieldName,
                                        Class fieldType)
    throws ConfigException
  {
    Resource resource = field.getAnnotation(Resource.class);

    initList.add(configureResource(field,
                                   fieldName, fieldType,
                                   resource.name(),
                                   resource.type().getName(),
                                   resource.name()));
  }

  public static BuilderProgram
    introspectResource(AccessibleObject field,
                       String fieldName,
                       Class fieldType)
    throws ConfigException
  {
    Resource resource = field.getAnnotation(Resource.class);

    if (resource != null)
      return configureResource(field,
                               fieldName, fieldType,
                               resource.name(),
                               resource.type().getName(),
                               resource.name());
    else
      return null;
  }

  private static void configureEJB(ArrayList<BuilderProgram> initList,
                                   AccessibleObject field,
                                   String fieldName,
                                   Class fieldType)
    throws ConfigException
  {
    EJB ejb = (EJB) field.getAnnotation(javax.ejb.EJB.class);

    String name = ejb.name();
    String beanName = ejb.beanName();
    String mappedName = ejb.mappedName();

    // ejb/0f62
    /*
      if ("".equals(jndiName))
      jndiName = fieldName;
    */

    AccessibleInject inject;

    if (field instanceof Field)
      inject = new FieldInject((Field) field);
    else
      inject = new PropertyInject((Method) field);

    BuilderProgram program;

    program = new EjbInjectProgram(name, beanName, mappedName,
                                   fieldType, inject);

    initList.add(program);
  }

  private static void
    configureWebServiceRef(ArrayList<BuilderProgram> initList,
                           AccessibleObject field,
                           String fieldName,
                           Class fieldType)
    throws ConfigException
  {
    WebServiceRef ref
      = (WebServiceRef) field.getAnnotation(WebServiceRef.class);

    String name = ref.name();
    name = ref.name();

    if ("".equals(name))
      name = fieldName;

    name = toFullName(name);
    // XXX: types

    AccessibleInject inject;

    if (field instanceof Field)
      inject = new FieldInject((Field) field);
    else
      inject = new PropertyInject((Method) field);

    BuilderProgram program;

    if (Service.class.isAssignableFrom(fieldType)) {
      program = new ServiceInjectProgram(name,
                                         fieldType,
                                         inject);
    }
    else {
      program = new ServiceProxyInjectProgram(name,
                                              fieldType,
                                              inject);
    }

    initList.add(program);
  }

  private static void configurePersistenceUnit(ArrayList<BuilderProgram> initList,
                                               AccessibleObject field,
                                               String fieldName,
                                               Class fieldType)
    throws ConfigException
  {
    PersistenceUnit pUnit = field.getAnnotation(PersistenceUnit.class);

    String jndiPrefix = "java:comp/env/persistence/_amber_PersistenceUnit";

    String jndiName = null;
    String unitName = pUnit.unitName();

    try {
      if (! unitName.equals(""))
        jndiName = jndiPrefix + '/' + unitName;
      else {
        InitialContext ic = new InitialContext();

        NamingEnumeration<NameClassPair> iter = ic.list(jndiPrefix);

        if (iter == null) {
          log.warning("Can't find configured PersistenceUnit");
          return; // XXX: error?
        }

        String ejbJndiName = null;
        while (iter.hasMore()) {
          NameClassPair pair = iter.next();

          if (pair.getName().equals("resin-ejb"))
            ejbJndiName = jndiPrefix + '/' + pair.getName();
          else {
            jndiName = jndiPrefix + '/' + pair.getName();
            break;
          }
        }

        if (jndiName == null)
          jndiName = ejbJndiName;
      }

      initList.add(configureResource(field, fieldName, fieldType,
                                     unitName,
                                     "javax.persistence.EntityManagerFactory",
                                     jndiName));
    } catch (Throwable e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  private static void configurePersistenceContext(ArrayList<BuilderProgram> initList,
                                                  AccessibleObject field,
                                                  String fieldName,
                                                  Class fieldType)
    throws ConfigException
  {
    PersistenceContext pContext = field.getAnnotation(PersistenceContext.class);

    String jndiPrefix = "java:comp/env/persistence";

    String jndiName = null;
    String unitName = pContext.unitName();

    try {
      if (! unitName.equals(""))
        jndiName = jndiPrefix + '/' + unitName;
      else {
        InitialContext ic = new InitialContext();

        NamingEnumeration<NameClassPair> iter = ic.list(jndiPrefix);

        if (iter == null) {
          log.warning("Can't find configured PersistenceContext");
          return; // XXX: error?
        }

        String ejbJndiName = null;
        while (iter.hasMore()) {
          NameClassPair pair = iter.next();

          // Skip reserved prefixes.
          // See com.caucho.amber.manager.AmberContainer
          if (pair.getName().startsWith("_amber"))
            continue;

          if (pair.getName().equals("resin-ejb"))
            ejbJndiName = jndiPrefix + '/' + pair.getName();
          else {
            jndiName = jndiPrefix + '/' + pair.getName();
            break;
          }
        }

        if (jndiName == null)
          jndiName = ejbJndiName;
      }

      if (pContext.type() == javax.persistence.PersistenceContextType.EXTENDED) {
        Object value = new InitialContext().lookup(jndiName);
        ((EntityManagerProxy) value).setExtended(true);
      }

      initList.add(configureResource(field, fieldName, fieldType,
                                     unitName,
                                     "javax.persistence.EntityManager",
                                     jndiName));
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    }
  }

  private static String findPersistenceContextName(String jndiName,
                                                   String unitName)
    throws ConfigException
  {
    String jndiPrefix = "java:comp/env/persistence";

    try {
      if (! unitName.equals(""))
        return jndiPrefix + '/' + unitName;
      else {
        InitialContext ic = new InitialContext();

        NamingEnumeration<NameClassPair> iter = ic.list(jndiPrefix);

        if (iter == null) {
          throw new ConfigException(L.l("Can't find configured PersistenceContext"));
        }

        String ejbJndiName = null;
        while (iter.hasMore()) {
          NameClassPair pair = iter.next();

          // Skip reserved prefixes.
          // See com.caucho.amber.manager.AmberContainer
          if (pair.getName().startsWith("_amber"))
            continue;

          if (pair.getName().equals("resin-ejb"))
            ejbJndiName = jndiPrefix + '/' + pair.getName();
          else {
            jndiName = jndiPrefix + '/' + pair.getName();
            break;
          }
        }

        if (jndiName == null)
          jndiName = ejbJndiName;

        return jndiName;
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigException(e);
    }
  }

  private static
    BuilderProgram configureResource(AccessibleObject field,
                                     String fieldName,
                                     Class fieldType,
                                     String name,
                                     String resourceType,
                                     String jndiName)
    throws ConfigException
  {
    String prefix = "";

    if (name.equals(""))
      name = fieldName;

    if (resourceType.equals("") || resourceType.equals("java.lang.Object"))
      resourceType = fieldType.getName();

    AccessibleInject inject;

    if (field instanceof Field)
      inject = new FieldInject((Field) field);
    else
      inject = new PropertyInject((Method) field);

    if (Executor.class.equals(fieldType)
        || ExecutorService.class.equals(fieldType)
        || ScheduledExecutorService.class.equals(fieldType)) {
      return new ExecutorInjectProgram(inject);
    }

    if (true) {
      // TCK indicates this logic is incorrect
    }
    else if (resourceType.equals("javax.sql.DataSource"))
      prefix = "jdbc/";
    else if (resourceType.startsWith("javax.jms."))
      prefix = "jms/";
    else if (resourceType.startsWith("javax.mail."))
      prefix = "mail/";
    else if (resourceType.equals("java.net.URL"))
      prefix = "url/";
    else if (resourceType.startsWith("javax.ejb."))
      prefix = "ejb/";

    if (! jndiName.equals("")) {
    }
    else if (UserTransaction.class.equals(fieldType)) {
      jndiName = "java:comp/UserTransaction";
    }
    else if (ORB.class.equals(fieldType)) {
      jndiName = "java:comp/ORB";
    }
    else {
      jndiName = prefix + name;
    }

    if (SessionContext.class.equals(fieldType)) {
      jndiName = "java:comp/env/sessionContext";
    }
    else if (MessageDrivenContext.class.equals(fieldType)) {
      jndiName = "java:comp/env/messageDrivenContext";
    }

    int colon = jndiName.indexOf(':');
    int slash = jndiName.indexOf('/');

    if (colon < 0 || slash > 0 && slash < colon)
      jndiName = "java:comp/env/" + jndiName;

    BuilderProgram program;

    PersistenceContext pc
      = (PersistenceContext) field.getAnnotation(PersistenceContext.class);

    if (pc != null)
      program = new PersistenceContextInjectProgram(jndiName, field, pc);
    else if (field instanceof Method)
      program = new JndiInjectProgram(jndiName, (Method) field);
    else
      program = new JndiFieldInjectProgram(jndiName, (Field) field);

    if (log.isLoggable(Level.FINEST))
      log.log(Level.FINEST,  String.valueOf(program));

    return program;
  }

  private static String toFullName(String jndiName)
  {
    int colon = jndiName.indexOf(':');
    int slash = jndiName.indexOf('/');

    if (colon < 0 || slash > 0 && slash < colon)
      jndiName = "java:comp/env/" + jndiName;

    return jndiName;
  }
}
