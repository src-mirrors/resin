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
 */

package com.caucho.config;

import com.caucho.config.program.NodeBuilderChildProgram;
import com.caucho.config.program.ConfigProgram;
import com.caucho.config.types.Validator;
import com.caucho.config.type.*;
import com.caucho.config.attribute.*;
import com.caucho.el.ELParser;
import com.caucho.el.Expr;
import com.caucho.loader.*;
import com.caucho.util.*;
import com.caucho.vfs.*;
import com.caucho.webbeans.component.ComponentImpl;
import com.caucho.webbeans.context.DependentScope;
import com.caucho.webbeans.context.ScopeContext;
import com.caucho.xml.*;

import org.w3c.dom.*;

import javax.el.*;
import java.io.Closeable;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The ConfigContext contains the state of the current configuration.
 */
public class ConfigContext {
  private final static L10N L = new L10N(ConfigContext.class);
  private final static Logger log
    = Logger.getLogger(ConfigContext.class.getName());

  private final static QName RESIN_TYPE = new QName("resin:type", null);
  private final static QName RESIN_TYPE_NS
    = new QName("resin:type", "http://caucho.com/ns/resin/core");

  private final static QName RESIN_CLASS = new QName("resin:class", null);
  private final static QName RESIN_CLASS_NS
    = new QName("resin:class", "http://caucho.com/ns/resin/core");
  
  private final static QName TEXT = new QName("#text");
  private final static QName VALUE = new QName("value");

  private final static Object NULL = new Object();

  private final static HashSet<QName> _resinClassSet = new HashSet<QName>();

  private static ThreadLocal<ConfigContext> _currentBuilder
    = new ThreadLocal<ConfigContext>();

  private Config _config;

  private ArrayList<ValidatorEntry> _validators
    = new ArrayList<ValidatorEntry>();

  private ConfigELContext _elContext = new ConfigELContext();
  
  private DependentScope _dependentScope;

  private ArrayList<Dependency> _dependList;
  private Document _dependDocument;

  public ConfigContext()
  {
  }

  public ConfigContext(ComponentImpl component,
		       Object value,
		       ScopeContext scope)
  {
    this();

    _dependentScope = new DependentScope(component, value, scope);
  }

  public ConfigContext(ScopeContext scope)
  {
    this();

    _dependentScope = new DependentScope(scope);
  }
  
  ConfigContext(Config config)
  {
    _config = config;
  }
  
  public static ConfigContext create()
  {
    ConfigContext env = _currentBuilder.get();
    
    if (env != null)
      return env;
    else
      return new ConfigContext();
  }

  public static ConfigContext createForProgram()
  {
    return new ConfigContext();
  }

  public static ConfigContext getCurrentBuilder()
  {
    return _currentBuilder.get();
  }

  public static ConfigContext getCurrent()
  {
    return _currentBuilder.get();
  }

  // s/b private?
  static void setCurrentBuilder(ConfigContext builder)
  {
    _currentBuilder.set(builder);
  }

  /**
   * WebBeans method
   * 
   * @param aThis
   * @param value
   */
  public void addDestructor(ComponentImpl comp, Object value)
  {
    if (_dependentScope != null)
      _dependentScope.addDestructor(comp, value);
    else if (comp instanceof Closeable)
      Environment.addCloseListener((Closeable) comp);
  }

  public boolean canInject(ScopeContext scope)
  {
    return _dependentScope == null || _dependentScope.canInject(scope);
  }

  /**
   * Returns the component value for the dependent scope
   * 
   * @param aThis
   * @return
   */
  public Object get(ComponentImpl comp)
  {
    if (_dependentScope != null)
      return _dependentScope.get(comp);
    else
      return null;
  }

  public Config getConfig()
  {
    return _config;
  }

  /**
   * WebBeans dependent scope setting
   * 
   * @param aThis
   * @param obj
   */
  public void put(ComponentImpl comp, Object obj)
  {
    if (_dependentScope == null)
      _dependentScope = new DependentScope();

    _dependentScope.put(comp, obj);
  }

  /**
   * Returns true if EL expressions are used.
   */
  private boolean isEL()
  {
    // server/26b6
    return _config == null || _config.isEL();
  }

  public boolean isIgnoreEnvironment()
  {
    return _config != null && _config.isIgnoreEnvironment();
  }

  /**
   * External call to configure a bean based on a top-level node.
   * The init() and replaceObject() are not called.
   *
   * @param bean the object to be configured.
   */
  public Object configure(Object bean, Node top)
    throws LineConfigException
  {
    if (bean == null)
      throw new NullPointerException();
    
    ConfigContext oldBuilder = _currentBuilder.get();
    try {
      _currentBuilder.set(this);

      ConfigType type = TypeFactory.getType(bean.getClass());

      configureBean(bean, top);

      type.init(bean);

      return type.replaceObject(bean);
    } catch (LineConfigException e) {
      throw e;
    } catch (Exception e) {
      throw error(e, top);
    } finally {
      _currentBuilder.set(oldBuilder);
    }
  }

  /**
   * External call to configure a bean based on a top-level node, calling
   * init() and replaceObject() when done.
   *
   * @param bean the bean to be configured
   * @param top the top-level XML configuration node
   * @return the configured object, or the factory generated object
   */
  public void configureBean(Object bean, Node top)
    throws LineConfigException
  {
    ConfigContext oldBuilder = _currentBuilder.get();
    // Object oldFile = _elContext.getValue("__FILE__");
    ArrayList<Dependency> oldDependList = _dependList;

    try {
      _currentBuilder.set(this);

      if (top instanceof QNode) {
        QNode qNode = (QNode) top;
        
	// _elContext.setValue("__FILE__", qNode.getBaseURI());
      }

      _dependList = getDependencyList(top);

      ConfigType type = TypeFactory.getType(bean.getClass());

      configureNode(top, bean, type);
    } finally {
      _currentBuilder.set(oldBuilder);

      _dependList = oldDependList;
      // _elContext.setValue("__FILE__", oldFile);
    }
  }

  /**
   * External call to configure a bean's attribute.
   *
   * @param bean the bean to be configured
   * @param attribute the node representing the configured attribute
   * @throws LineConfigException
   */
  public void configureAttribute(Object bean, Node attribute)
    throws LineConfigException
  {
    String attrName = attribute.getNodeName();

    if (attrName.equals("resin:type"))
      return;
    else if (attrName.startsWith("xmlns"))
      return;

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    ConfigContext oldBuilder = getCurrentBuilder();
    try {
      setCurrentBuilder(this);
      
      ConfigType type = TypeFactory.getType(bean.getClass());

      QName qName = ((QAbstractNode) attribute).getQName();
      
      type.beforeConfigure(this, bean, attribute);

      configureChildNode(attribute, qName, bean, type);
      
      type.afterConfigure(this, bean);
    }
    catch (LineConfigException e) {
      throw e;
    }
    catch (Exception e) {
      throw error(e, attribute);
    } finally {
      setCurrentBuilder(oldBuilder);
      thread.setContextClassLoader(oldLoader);
    }
  }

  /**
   * Configures a bean, calling its init() and replaceObject() methods.
   *
   * @param typeStrategy the strategy for handling the bean's type
   * @param bean the bean instance
   * @param top the configuration top
   * @return the configured bean, possibly the replaced object
   * @throws LineConfigException
   */
  private Object configureNode(Node node,
                               Object bean,
                               ConfigType type)
    throws LineConfigException
  {
    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    
    try {
      type.beforeConfigure(this, bean, node);
      type.beforeConfigureBean(this, bean, node);
      
      configureNodeAttributes(node, bean, type);

      for (Node childNode = node.getFirstChild();
           childNode != null;
           childNode = childNode.getNextSibling()) {
        QName qName = ((QAbstractNode) childNode).getQName();
        
        configureChildNode(childNode, qName, bean, type);
      }

      type.afterConfigure(this, bean);
    } catch (LineConfigException e) {
      throw e;
    } catch (Exception e) {
      throw error(e, node);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }

    return bean;
  }

  /**
   * Configures a bean, calling its init() and replaceObject() methods.
   *
   * @param typeStrategy the strategy for handling the bean's type
   * @param bean the bean instance
   * @param top the configuration top
   * @return the configured bean, possibly the replaced object
   * @throws LineConfigException
   */
  private void configureNodeAttributes(Node node,
                                       Object bean,
                                       ConfigType type)
    throws Exception
  {
    if (node instanceof QAttributedNode) {
      Node child = ((QAttributedNode) node).getFirstAttribute();

      for (; child != null; child = child.getNextSibling()) {
        Attr attr = (Attr) child;
        QName qName = ((QNode) attr).getQName();
        
        configureChildNode(attr, qName, bean, type);
      }
    }
    else {
      NamedNodeMap attrList = node.getAttributes();
      if (attrList != null) {
        int length = attrList.getLength();
        for (int i = 0; i < length; i++) {
          Attr attr = (Attr) attrList.item(i);
          QName qName = ((QNode) attr).getQName();

          configureChildNode(attr, qName, bean, type);
        }
      }
    }
  }
  
  private void configureChildNode(Node childNode,
                                  QName qName,
                                  Object bean,
                                  ConfigType type)
    throws Exception
  {
    if (childNode instanceof Attr
        && (qName.getName().startsWith("xmlns")
            || _resinClassSet.contains(qName))) {
      return;
    }

    Attribute attrStrategy;

    try {
      attrStrategy = type.getAttribute(qName);

      if (attrStrategy == null)
	attrStrategy = type.getProgramAttribute();

      if (attrStrategy == null)
	attrStrategy = TypeFactory.getFactory().getEnvironmentAttribute(qName);

      if (attrStrategy == null) {
	if (childNode instanceof Element || childNode instanceof Attr) {
	  throw error(L.l("'{0}' is an unknown property of '{1}'.",
			  qName.getName(), type.getTypeName()),
		      childNode);
	}

	return;
      }

      if (attrStrategy.isProgram()) {
	attrStrategy.setValue(bean, qName,
			      buildProgram(attrStrategy, childNode));
	return;
      }
      else if (attrStrategy.isNode()) {
	attrStrategy.setValue(bean, qName, childNode);
	return;
      }

      ConfigType childType = null;

      if (childNode instanceof Element)
	childType = createResinType(attrStrategy, (Element) childNode);

      Object childBean;

      if (childType != null)
	childBean = childType.create(bean);
      else if ((childBean = getELValue(attrStrategy, childNode)) != null) {
	// ioc/2410
	if (childBean != NULL)
	  attrStrategy.setValue(bean, qName, childBean);
	else
	  attrStrategy.setValue(bean, qName, null);

	return;
      }
      else
	childBean = attrStrategy.create(bean);

      if (childBean != null) {
	ConfigType childBeanType = TypeFactory.getType(childBean.getClass());
	
	if (childNode instanceof Element)
	  configureNode(childNode, childBean, childBeanType);
	else
	  configureChildNode(childNode, TEXT, childBean, childBeanType);

	childBeanType.init(childBean);

	childBean = childBeanType.replaceObject(childBean);
	childBean = attrStrategy.replaceObject(childBean);

	attrStrategy.setValue(bean, qName, childBean);
      }
      else if ((childBean = getElementValue(attrStrategy, childNode)) != null) {
	if (childBean != NULL)
	  attrStrategy.setValue(bean, qName, childBean);
	else
	  attrStrategy.setValue(bean, qName, null);
      }
      else {
	ConfigType attrType = attrStrategy.getConfigType();
	
	String textValue;

	if (attrType.isNoTrim())
	  textValue = textValueNoTrim(childNode);
	else
	  textValue = textValue(childNode);

	if (isEL() && attrType.isEL() && textValue.indexOf("${") >= 0) {
	  childType = attrStrategy.getConfigType();
	  
	  Object value = childType.valueOf(evalObject(textValue));
	  
	  attrStrategy.setValue(bean, qName, value);
	}
	else
	  attrStrategy.setText(bean, qName, textValue);
      }
    } catch (LineConfigException e) {
      throw e;
    } catch (Exception e) {
      throw error(e, childNode);
    }
  }

  private ConfigProgram buildProgram(Attribute attr, Node node)
  {
    return new NodeBuilderChildProgram(node);
  }
  
  private void configureChildAttribute(Attr childNode,
				       QName qName,
				       Object bean,
				       ConfigType type)
    throws Exception
  {
    if (qName.getName().startsWith("xmlns")
	|| _resinClassSet.contains(qName)) {
      return;
    }

    Attribute attrStrategy;

    try {
      attrStrategy = type.getAttribute(qName);

      if (attrStrategy == null) {
	throw error(L.l("'{0}' is an unknown property of '{1}'.",
			qName.getName(), type.getTypeName()),
		    childNode);
      }

      if (attrStrategy.isProgram()) {
	attrStrategy.setValue(bean, qName,
			      buildProgram(attrStrategy, childNode));
	return;
      }
      else if (attrStrategy.isNode()) {
	attrStrategy.setValue(bean, qName, childNode);
	return;
      }

      String textValue = childNode.getValue();

      attrStrategy.setText(bean, qName, textValue);
    } catch (LineConfigException e) {
      throw e;
    } catch (Exception e) {
      throw error(e, childNode);
    }
  }

  Object configureValue(Node node)
  {
    String value = textValue(node);

    if (isEL() && value != null
        && value.startsWith("${") && value.endsWith("}")) {
      return evalObject(value);
    }
    else
      return value;
  }

  public void setDependentScope(DependentScope scope)
  {
    _dependentScope = scope;
  }

  public DependentScope getDependentScope()
  {
    if (_dependentScope == null)
      _dependentScope = new DependentScope();

    return _dependentScope;
  }

  public ArrayList<Dependency> getDependencyList()
  {
    return _dependList;
  }
    
  ArrayList<Dependency> getDependencyList(Node node)
  {
    ArrayList<Dependency> dependList = null;

    if (node instanceof QElement) {
      QElement qelt = (QElement) node;

      /* XXX: line #
      builder.setLocation(bean, qelt.getBaseURI(),
                          qelt.getFilename(), qelt.getLine());
      builder.setNode(bean, qelt);
      */

      QDocument doc = (QDocument) qelt.getOwnerDocument();

      if (doc == null)
	return null;
      else if (doc == _dependDocument)
        return _dependList;

      _dependDocument = doc;

      ArrayList<Path> pathList;
      pathList = doc.getDependList();

      if (pathList != null) {
        dependList = new ArrayList<Dependency>();

        for (int i = 0; i < pathList.size(); i++) {
          dependList.add(new Depend(pathList.get(i)));
        }
      }

      _dependList = dependList;
    }

    return dependList;
  }

  /**
   * Create a custom resin:type value.
   */
  ConfigType createResinType(Attribute attrStrategy, Element node)
    throws Exception
  {
    ConfigType childType = attrStrategy.getConfigType();
    
    String type = null;

    if (node instanceof QAttributedNode) {
      Node child = ((QAttributedNode) node).getFirstAttribute();

      for (; child != null; child = child.getNextSibling()) {
        Attr attr = (Attr) child;
        QName qName = ((QNode) attr).getQName();

	if (_resinClassSet.contains(qName)) {
	  type = attr.getValue();
	  break;
	}
      }
    }
    else {
      NamedNodeMap attrList = node.getAttributes();
      if (attrList != null) {
        int length = attrList.getLength();
        for (int i = 0; i < length; i++) {
          Attr attr = (Attr) attrList.item(i);
          QName qName = ((QNode) attr).getQName();

	  if (_resinClassSet.contains(qName)) {
	    type = attr.getValue();
	    break;
	  }
        }
      }
    }

    if (type != null) {
      try {
	ClassLoader loader = Thread.currentThread().getContextClassLoader();
	
	Class cl = Class.forName(type, false, loader);

	return TypeFactory.getType(cl);
      } catch (Exception e) {
	throw ConfigException.create(e);
      }
    }
    
    return null;
  }

  /**
   * Configures a new object given the object's type.
   *
   * @param type the expected type of the object
   * @param node the configuration node
   * @return the configured object
   * @throws Exception
   */
  Object configureCreate(Class type, Node node)
    throws Exception
  {
    Object value = type.newInstance();

    return configure(value, node);
  }

  /**
   * Returns the variable resolver.
   */
  public ConfigELContext getELContext()
  {
    return _elContext;
  }

  void addValidator(Validator validator)
  {
    if (_validators == null)
      _validators = new ArrayList<ValidatorEntry>();
    
    _validators.add(new ValidatorEntry(validator));
  }

  static boolean hasChildren(Node node)
  {
    Node ptr;

    if (node instanceof QAttributedNode) {
      Node attr = ((QAttributedNode) node).getFirstAttribute();

      for (; attr != null; attr = attr.getNextSibling()) {
        if (! attr.getNodeName().startsWith("xml"))
          return true;
      }
    }
    else if (node instanceof Element) {
      NamedNodeMap attrList = node.getAttributes();
      if (attrList != null) {
        for (int i = 0; i < attrList.getLength(); i++) {
          if (! attrList.item(i).getNodeName().startsWith("xml"))
            return true;
        }
      }
    }

    for (ptr = node.getFirstChild(); ptr != null; ptr = ptr.getNextSibling()) {
      if (ptr instanceof Element)
	return true;
    }

    return false;
  }

  static String getValue(QName name, Node node, String defaultValue)
  {
    /*
    NamedNodeMap attrList = node.getAttributes();
    if (attrList != null) {
      for (int i = 0; i < attrList.getLength(); i++) {
	if (attrList.item(i).getNodeName().equals(name.getName()))
	  return attrList.item(i).getNodeValue();
      }
    }
    */

    if (node instanceof Element) {
      String value = ((Element) node).getAttribute(name.getName());

      if (! "".equals(value))
        return value;
    }

    Node ptr;

    for (ptr = node.getFirstChild(); ptr != null; ptr = ptr.getNextSibling()) {
      QName qName = ((QAbstractNode) ptr).getQName();

      if (name.equals(qName))
	return textValue(ptr);
    }

    return defaultValue;
  }

  /**
   * Returns the text value of the node.
   */
  Object getELValue(Attribute attr, Node node)
  {
    if (! (node instanceof Element))
      return null;

    Element elt = (Element) node;
    Element childElt = null;

    for (Node child = elt.getFirstChild();
	 child != null;
	 child = child.getNextSibling()) {
      if (child instanceof Element) {
	if (childElt != null)
	  return null;
	
	childElt = (Element) child;
      }
      
      else if (child instanceof CharacterData
	       && ! XmlUtil.isWhitespace(((CharacterData) child).getData())) {
	String data = ((CharacterData) child).getData();

	if (isEL() && attr.isEL() && childElt == null
	    && child.getNextSibling() == null
	    && (data.indexOf("#{") >= 0 || data.indexOf("${") >= 0)) {
	  ELContext elContext = getELContext();
    
	  ELParser parser = new ELParser(elContext, data.trim());
    
	  Expr expr = parser.parse();

	  Object value = attr.getConfigType().valueOf(elContext, expr);

	  if (value != null)
	    return value;
	  else
	    return NULL;
	}
	
	return null;
      }
    }

    return null;
  }

  /**
   * Returns the text value of the node.
   */
  Object getElementValue(Attribute attr, Node node)
  {
    if (! (node instanceof Element))
      return null;

    Element elt = (Element) node;
    Element childElt = null;

    for (Node child = elt.getFirstChild();
	 child != null;
	 child = child.getNextSibling()) {
      if (child instanceof Element) {
	if (childElt != null)
	  return null;
	
	childElt = (Element) child;
      }
      
      else if (child instanceof CharacterData
	       && ! XmlUtil.isWhitespace(((CharacterData) child).getData())) {
	String data = ((CharacterData) child).getData();

	if (isEL() && attr.isEL() && childElt == null
	    && child.getNextSibling() == null
	    && (data.indexOf("#{") >= 0 || data.indexOf("${") >= 0)) {
	  ELContext elContext = getELContext();
    
	  ELParser parser = new ELParser(elContext, data.trim());
    
	  Expr expr = parser.parse();

	  Object value = attr.getConfigType().valueOf(elContext, expr);

	  if (value != null)
	    return value;
	  else
	    return NULL;
	}
	
	return null;
      }
    }

    if (childElt == null)
      return null;

    TypeFactory factory = TypeFactory.getFactory();

    ConfigType childType
      = factory.getEnvironmentType(((QElement) childElt).getQName());

    if (childType != null) {
      Object childBean = childType.create(null);
      
      configureNode(childElt, childBean, childType);
      
      childType.init(childBean);

      Object value = childType.replaceObject(childBean);

      if (value != null)
	return value;
      else
	return NULL;
    }

    return null;
  }

  /**
   * Returns the text value of the node.
   */
  static String textValue(Node node)
  {
    if (node instanceof Attr)
      return node.getNodeValue();
    else {
      String value = XmlUtil.textValue(node);

      if (value == null || value.equals(""))
	return "";
      else if (node instanceof Element) {
	String space = ((Element) node).getAttribute("xml:space");

	if (! space.equals(""))
	  return value;
      }

      return value.trim();
    }
  }

  /**
   * Returns the text value of the node.
   */
  static String textValueNoTrim(Node node)
  {
    if (node instanceof Attr)
      return node.getNodeValue();
    else {
      String value = XmlUtil.textValue(node);

      if (value == null)
	return "";

      return value;
    }
  }

  /**
   * Evaluate as an object
   */
  public Object evalObject(String exprString)
    throws ELException
  {
    if (exprString.indexOf("${") >= 0 && isEL()) {
      ELParser parser = new ELParser(getELContext(), exprString);
      parser.setCheckEscape(true);
      Expr expr = parser.parse();

      return expr.getValue(getELContext());
    }
    else
      return exprString;
  }

  public static RuntimeException error(String msg, Node node)
  {
    String systemId = null;
    String filename = null;
    int line = 0;

    if (node instanceof QAbstractNode) {
      QAbstractNode qnode = (QAbstractNode) node;
      
      systemId = qnode.getBaseURI();
      filename = qnode.getFilename();
      line = qnode.getLine();
    }

    if (systemId != null) {
      String sourceLines = getSourceLines(systemId, line);
      
      msg = msg + sourceLines;
    }
      
    if (filename != null)
      return new LineConfigException(filename, line, msg);
    else
      return new LineConfigException(msg);
  }
  
  public static RuntimeException error(Throwable e, Node node)
  {
    String systemId = null;
    String filename = null;
    int line = 0;

    if (e instanceof RuntimeException
	&& e instanceof DisplayableException
	&& ! ConfigException.class.equals(e.getClass())) {
      return (RuntimeException) e;
    }

    if (node instanceof QAbstractNode) {
      QAbstractNode qnode = (QAbstractNode) node;
      
      systemId = qnode.getBaseURI();
      filename = qnode.getFilename();
      line = qnode.getLine();
    }

    for (; e.getCause() != null; e = e.getCause()) {
      if (e instanceof LineCompileException)
        break;
      else if (e instanceof LineConfigException)
        break;
      else if (e instanceof CompileException)
        break;
    }

    if (e instanceof LineConfigException)
      return (LineConfigException) e;
    else if (e instanceof LineCompileException) {
      return new LineConfigException(e.getMessage(), e);
    }
    else if (e instanceof ConfigException
	     && e.getMessage() != null
	     && filename != null) {
      String sourceLines = getSourceLines(systemId, line);

      return new LineConfigException(filename, line,
				     e.getMessage() + sourceLines,
				     e);
    }
    else if (e instanceof CompileException && e.getMessage() != null) {
      return new LineConfigException(filename, line, e);
    }
    else {
      String sourceLines = getSourceLines(systemId, line);
      
      String msg = filename + ":" + line + ": " + e + sourceLines;

      if (e instanceof RuntimeException) {
	throw new LineConfigException(msg, e);
      }
      else if (e instanceof Error) {
	// server/1711
	throw new LineConfigException(msg, e);
	// throw (Error) e;
      }
      else
	return new LineConfigException(msg, e);
    }
  }

  private static String getSourceLines(String systemId, int errorLine)
  {
    if (systemId == null)
      return "";
    
    ReadStream is = null;
    try {
      is = Vfs.lookup().lookup(systemId).openRead();
      int line = 0;
      StringBuilder sb = new StringBuilder("\n\n");
      String text;
      while ((text = is.readLine()) != null) {
	line++;

	if (errorLine - 2 <= line && line <= errorLine + 2) {
	  sb.append(line);
	  sb.append(": ");
	  sb.append(text);
	  sb.append("\n");
	}
      }

      return sb.toString();
    } catch (IOException e) {
      log.log(Level.FINEST, e.toString(), e);

      return "";
    } finally {
      if (is != null)
	is.close();
    }
  }

  static class ValidatorEntry {
    private Validator _validator;
    private ClassLoader _loader;

    ValidatorEntry(Validator validator)
    {
      _validator = validator;

      _loader = Thread.currentThread().getContextClassLoader();
    }

    void validate()
      throws ConfigException
    {
      Thread thread = Thread.currentThread();
      ClassLoader oldLoader = thread.getContextClassLoader();

      try {
	thread.setContextClassLoader(_loader);

	_validator.validate();
      } finally {
	thread.setContextClassLoader(oldLoader);
      }
    }
  }

  static {
    _resinClassSet.add(RESIN_CLASS_NS);
    _resinClassSet.add(RESIN_CLASS);
    _resinClassSet.add(RESIN_TYPE_NS);
    _resinClassSet.add(RESIN_TYPE);
    _resinClassSet.add(new QName("resin:type", "http://caucho.com/ns/resin"));
    _resinClassSet.add(new QName("resin:class", "http://caucho.com/ns/resin"));
  }
}
