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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.j2ee.deployclient;

import com.caucho.server.admin.DeployClient;
import com.caucho.server.admin.HostQuery;
import com.caucho.server.admin.WebAppQuery;
import com.caucho.server.admin.StatusQuery;
import com.caucho.util.L10N;
import com.caucho.vfs.*;
import com.caucho.xml.*;
import com.caucho.xpath.XPath;

import org.w3c.dom.*;

import javax.enterprise.deploy.model.DeployableObject;
import javax.enterprise.deploy.shared.DConfigBeanVersionType;
import javax.enterprise.deploy.shared.ModuleType;
import javax.enterprise.deploy.spi.DeploymentConfiguration;
import javax.enterprise.deploy.spi.DeploymentManager;
import javax.enterprise.deploy.spi.Target;
import javax.enterprise.deploy.spi.TargetModuleID;
import javax.enterprise.deploy.spi.exceptions.DConfigBeanVersionUnsupportedException;
import javax.enterprise.deploy.spi.exceptions.DeploymentManagerCreationException;
import javax.enterprise.deploy.spi.exceptions.InvalidModuleException;
import javax.enterprise.deploy.spi.exceptions.TargetException;
import javax.enterprise.deploy.spi.status.ProgressObject;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manager for the deployments.
 */
public class DeploymentManagerImpl implements DeploymentManager {
  private static final L10N L = new L10N(DeploymentManagerImpl.class);
  private static final Logger log
    = Logger.getLogger(DeploymentManagerImpl.class.getName());

  private DeployClient _deployClient;
  private String _user;

  private String _uri;

  DeploymentManagerImpl(String uri)
  {
    int p = uri.indexOf("http");
    
    if (p < 0)
      throw new IllegalArgumentException(L.l("'{0}' is an illegal URI for DeploymentManager.", uri));

    _uri = uri;

    int hostIdx = uri.indexOf("://") + 3;

    int portIdx = uri.indexOf(':', hostIdx);

    int fileIdx = uri.indexOf('/', portIdx + 1);

    String host = uri.substring(hostIdx, portIdx);

    final int port;

    if (fileIdx > -1)
     port  = Integer.parseInt(uri.substring(portIdx + 1, fileIdx));
    else
      port = Integer.parseInt(uri.substring(portIdx + 1));

    _deployClient = new DeployClient(host, port);
  }

  /**
   * Connect to the manager.
   */
  void connect(String user, String password)
    throws DeploymentManagerCreationException
  {
    _user = user;
  }

  /**
   * Returns the targets supported by the manager.
   */
  public Target []getTargets()
    throws IllegalStateException
  {
    HostQuery []hosts = _deployClient.listHosts();

    if (hosts == null)
      throw new IllegalStateException(L.l("'{0}' does not return any hosts",
					  _deployClient));

    Target []targets = new Target[hosts.length];

    for (int i = 0; i < hosts.length; i++) {
      HostQuery host = hosts[i];

      Target target = new TargetImpl(host.getName(), null);

      targets[i] = target;
    }

    return targets;
  }

  /**
   * Returns the current running modules.
   */
  public TargetModuleID []getRunningModules(ModuleType moduleType,
                                            Target []targetList)
    throws TargetException, IllegalStateException
  {
    return new TargetModuleID[0];
  }

  /**
   * Returns the current non-running modules.
   */
  public TargetModuleID []getNonRunningModules(ModuleType moduleType,
                                               Target []targetList)
    throws TargetException, IllegalStateException
  {
    return new TargetModuleID[0];
  }

  /**
   * Returns all available modules.
   */
  public TargetModuleID []getAvailableModules(ModuleType moduleType,
                                              Target []targetList)
    throws TargetException, IllegalStateException
  {
    String[] hosts = new String[targetList.length];

    for (int i = 0; i < targetList.length; i++) {
      Target target = targetList[i];

      hosts[i] = target.getName();
    }

    WebAppQuery []apps = _deployClient.listWebApps(hosts);

    TargetModuleID []result = new TargetModuleID[apps.length];

    for (int i = 0; i < apps.length; i++) {
      WebAppQuery app = apps[i];

      result[i] = new TargetModuleIDImpl(new TargetImpl(app.getHost(), null),
                                         app.getWebAppId());
    }

    return result;
  }

  /**
   * Returns a configuration for the deployable object.
   */
  public DeploymentConfiguration createConfiguration(DeployableObject dObj)
    throws InvalidModuleException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Deploys the object.
   */
  public ProgressObject distribute(Target []targetList,
                                   File archive,
                                   File deploymentPlan)
    throws IllegalStateException
  {
    return distributeImpl(targetList, archive, null, deploymentPlan, null);
  }

  /**
   * Deploys the object.
   */
  public ProgressObject distribute(Target []targetList,
                                   InputStream archive,
                                   InputStream deploymentPlan)
    throws IllegalStateException
  {
    return distributeImpl(targetList, null, archive, null, deploymentPlan);
  }


  /**
   * Deploys the object.
   */
  public ProgressObject distributeImpl(Target []targetList,
				       File archive,
				       InputStream archiveStream,
				       File deploymentPlan,
				       InputStream deploymentPlanStream)
    throws IllegalStateException
  {
    try {
      QDocument doc = new QDocument();

      DOMBuilder builder = new DOMBuilder();

      builder.init(doc);

      Xml xml = new Xml();
      xml.setOwner(doc);
      xml.setNamespaceAware(false);
      xml.setContentHandler(builder);
      xml.setCoalescing(true);

      if (deploymentPlan != null)
	xml.parse(Vfs.lookup(deploymentPlan.getAbsolutePath()));
      else
	xml.parse(deploymentPlanStream);

      String type = XPath.evalString("/deployment-plan/archive-type", doc);
      String name = XPath.evalString("/deployment-plan/name", doc);

      String tag = type + "s/default/" + name;

      if (archive != null)
	_deployClient.deployJar(Vfs.lookup(archive.getAbsolutePath()),
				tag, _user, "", null, null);
      else
	_deployClient.deployJar(archiveStream,
				tag, _user, "", null, null);

      deployExtraFiles(tag, doc);

      TargetModuleID []targetModules = new TargetModuleID[targetList.length];

      for (int i = 0; i < targetList.length; i++) {
        Target target = targetList[i];

        targetModules[i] = new TargetModuleIDImpl((TargetImpl) target,
                                                  '/' + name);
      }

      ProgressObjectImpl result = new ProgressObjectImpl(targetModules);

      StatusQuery status = _deployClient.status(tag);

      if (status.getMessage() == null)
	result.completed(L.l("application {0} deployed from {1}",
			     name, archive));
      else
	result.failed(L.l("application {0} failed from {1}: {2}",
			  name, archive, status.getMessage()));

      return result;
    }
    catch (Exception e) {
      IllegalStateException ex;

      ex = new IllegalStateException(e.getMessage());
      ex.initCause(e);

      throw ex;
    }
  }

  private void deployExtraFiles(String tag, Node doc)
  {
    try {
      Iterator iter = XPath.select("/deployment-plan/ext-file", doc);

      while (iter.hasNext()) {
	Node node = (Node) iter.next();

	String name = XPath.evalString("name", node);
	Node data = XPath.find("data", node);

	if (data != null) {
	  data = data.getFirstChild();

	  TempOutputStream os = new TempOutputStream();

	  XmlPrinter printer = new XmlPrinter(os);

	  printer.printXml(data);

	  os.close();

	  long length = os.getLength();

	  if (length == 0)
	    continue;
	  
	  InputStream is = os.openInputStreamNoFree();

	  String sha1 = _deployClient.calculateFileDigest(is, length);

	  _deployClient.sendFile(sha1, length, os.openInputStream());
      
	  _deployClient.addDeployFile(tag, name, sha1);
	}
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Starts the modules.
   */
  public ProgressObject start(TargetModuleID []moduleIDList)
    throws IllegalStateException
  {
    String [][]apps = new String[moduleIDList.length][2];

    for (int i = 0; i < moduleIDList.length; i++) {
      TargetModuleID targetModuleID = moduleIDList[i];

      apps[i][0] = targetModuleID.getTarget().getName();
      apps[i][1] = targetModuleID.getModuleID();
    }
    
    _deployClient.start(apps);

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < apps.length; i++) {
      String []app = apps[i];

      sb.append(app[0]).append(':').append(app[1]).append(' ');
    }

    ProgressObjectImpl result = new ProgressObjectImpl(moduleIDList);

    result.completed(L.l("modules ${0} started", sb.toString()));

    return result;
  }

  /**
   * Stops the modules.
   */
  public ProgressObject stop(TargetModuleID []moduleIDList)
    throws IllegalStateException
  {
    String [][]apps = new String[moduleIDList.length][2];

    for (int i = 0; i < moduleIDList.length; i++) {
      TargetModuleID targetModuleID = moduleIDList[i];

      apps[i][0] = targetModuleID.getTarget().getName();
      apps[i][1] = targetModuleID.getModuleID();
    }

    _deployClient.stop(apps);

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < apps.length; i++) {
      String []app = apps[i];

      sb.append(app[0]).append(':').append(app[1]).append(' ');
    }

    ProgressObjectImpl result = new ProgressObjectImpl(moduleIDList);

    result.completed(L.l("modules ${0} stop", sb.toString()));

    return result;
  }

  /**
   * Undeploys the modules.
   */
  public ProgressObject undeploy(TargetModuleID []moduleIDList)
    throws IllegalStateException
  {
    String [][]apps = new String[moduleIDList.length][2];

    for (int i = 0; i < moduleIDList.length; i++) {
      TargetModuleID targetModuleID = moduleIDList[i];

      apps[i][0] = targetModuleID.getTarget().getName();
      apps[i][1] = targetModuleID.getModuleID();
    }

    _deployClient.undeploy(apps);

    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < apps.length; i++) {
      String []app = apps[i];

      sb.append(app[0]).append(':').append(app[1]).append(' ');
    }

    ProgressObjectImpl result = new ProgressObjectImpl(moduleIDList);

    result.completed(L.l("modules ${0} undeployed", sb.toString()));

    return result;
  }

  /**
   * Returns true if the redeploy is supported.
   */
  public boolean isRedeploySupported()
  {
    return false;
  }

  /**
   * Redeploys the object.
   */
  public ProgressObject redeploy(TargetModuleID []targetList,
                                 File archive,
                                 File deploymentPlan)
    throws IllegalStateException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Redeploys the object.
   */
  public ProgressObject redeploy(TargetModuleID []targetList,
                                 InputStream archive,
                                 InputStream deploymentPlan)
    throws IllegalStateException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Frees any resources.
   */
  public void release()
  {
  }

  /**
   * Returns the default locale.
   */
  public Locale getDefaultLocale()
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the current locale.
   */
  public Locale getCurrentLocale()
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Sets the default locale.
   */
  public void setLocale(Locale locale)
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the supported locales.
   */
  public Locale []getSupportedLocales()
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns true if the locale is supported.
   */
  public boolean isLocaleSupported(Locale locale)
  {
    return false;
  }

  /**
   * Returns the bean's J2EE version.
   */
  public DConfigBeanVersionType getDConfigBeanVersion()
  {
    return DConfigBeanVersionType.V1_4;
  }

  /**
   * Returns true if the given version is supported.
   */
  public boolean isDConfigBeanVersionSupported(DConfigBeanVersionType version)
  {
    return true;
  }

  /**
   * Sets true if the given version is supported.
   */
  public void setDConfigBeanVersionSupported(DConfigBeanVersionType version)
    throws DConfigBeanVersionUnsupportedException
  {
  }

  /**
   * Return the debug view of the manager.
   */
  public String toString()
  {
    return "DeploymentManagerImpl[" + _uri + "]";
  }

  public ProgressObject distribute(Target[] arg0,
                                   ModuleType arg1,
                                   InputStream arg2,
                                   InputStream arg3)
    throws IllegalStateException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  public void setDConfigBeanVersion(DConfigBeanVersionType arg0)
    throws DConfigBeanVersionUnsupportedException
  {
    throw new UnsupportedOperationException("Not supported yet.");
  }
}
