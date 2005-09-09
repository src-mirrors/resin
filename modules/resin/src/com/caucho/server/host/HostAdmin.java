/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

package com.caucho.server.host;

import java.util.ArrayList;

import javax.management.ObjectName;

import com.caucho.vfs.Path;

import com.caucho.server.deploy.DeployControllerAdmin;

import com.caucho.server.host.mbean.HostMBean;

import com.caucho.server.webapp.WebAppController;
import com.caucho.server.webapp.mbean.WebAppMBean;
import com.caucho.jmx.AdminAttributeCategory;
import com.caucho.jmx.IntrospectionAttributeDescriptor;
import com.caucho.jmx.IntrospectionOperationDescriptor;
import com.caucho.jmx.IntrospectionMBeanDescriptor;
import com.caucho.jmx.IntrospectionClosure;
import com.caucho.util.L10N;

/**
 * The admin implementation for a host.
 */
public class HostAdmin extends DeployControllerAdmin<HostController>
  implements HostMBean
{
  private static final L10N L = new L10N(HostAdmin.class);

  /**
   * Creates the admin.
   */
  public HostAdmin(HostController controller)
  {
    super(controller);
  }

  public void describe(IntrospectionMBeanDescriptor descriptor)
  {
    String hostName = getHostName();

    if (hostName == null || hostName.length() == 0)
      hostName = "*";

    descriptor.setTitle(L.l("Host {0}", hostName));
  }

  public String getName()
  {
    return getController().getName();
  }

  public String getHostName()
  {
    return getController().getHostName();
  }

  public void describeHostName(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setCategory(AdminAttributeCategory.CONFIGURATION);
    descriptor.setSortOrder(200);
  }

  /**
   * Returns the mbean object.
   */
  public ObjectName getObjectName()
  {
    return getController().getObjectName();
  }

  public void describeObjectName(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  public String getURL()
  {
    Host host = getHost();

    if (host != null)
      return host.getURL();
    else
      return null;
  }

  public void describeURL(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setCategory(AdminAttributeCategory.CONFIGURATION);
    descriptor.setSortOrder(210);
  }

  /**
   * Returns the host's document directory.
   */
  public String getRootDirectory()
  {
    Path path = null;

    Host host = getHost();

    if (host != null)
      path = host.getRootDirectory();

    if (path != null)
      return path.getNativePath();
    else
      return null;
  }

  public void describeRootDirectory(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setCategory(AdminAttributeCategory.CONFIGURATION);
    descriptor.setSortOrder(220);
  }

  /**
   * Returns the host's document directory.
   */
  public String getDocumentDirectory()
  {
    Path path = null;

    Host host = getHost();

    if (host != null)
      path = host.getDocumentDirectory();

    if (path != null)
      return path.getNativePath();
    else
      return null;
  }

  public void describeDocumentDirectory(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setCategory(AdminAttributeCategory.CONFIGURATION);

    descriptor.setSortOrder(230);

    boolean isIgnored = getDocumentDirectory().equals(getRootDirectory());

    descriptor.setIgnored(isIgnored);
  }

  /**
   * Returns the host's war directory.
   */
  public String getWarDirectory()
  {
    Path path = null;

    Host host = getHost();

    if (host != null)
      path = host.getWarDir();

    if (path != null)
      return path.getNativePath();
    else
      return null;
  }

  public void describeWarDirectory(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  public String getWarExpandDirectory()
  {
    Path path = null;

    Host host = getHost();

    if (host != null)
      path = host.getWarExpandDir();

    if (path != null)
      return path.getNativePath();
    else
      return null;
  }

  public void describeWarExpandDirectory(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  /**
   * Updates a .war deployment.
   */
  public void updateWebAppDeploy(String name)
    throws Throwable
  {
    Host host = getHost();

    if (host != null)
      host.updateWebAppDeploy(name);
  }

  public void describeUpdateWebAppDeploy(IntrospectionOperationDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  /**
   * Updates a .ear deployment.
   */
  public void updateEarDeploy(String name)
    throws Throwable
  {
    Host host = getHost();

    if (host != null)
      host.updateEarDeploy(name);
  }

  public void describeUpdateEarDeploy(IntrospectionOperationDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  /**
   * Expand a .ear deployment.
   */
  public void expandEarDeploy(String name)
  {
    Host host = getHost();

    if (host != null)
      host.expandEarDeploy(name);
  }

  public void describeExpandEarDeploy(IntrospectionOperationDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  /**
   * Start a .ear deployment.
   */
  public void startEarDeploy(String name)
  {
    Host host = getHost();

    if (host != null)
      host.startEarDeploy(name);
  }

  public void describeStartEarDeploy(IntrospectionOperationDescriptor descriptor)
  {
    descriptor.setIgnored(true);
  }

  /**
   * Returns the web-app names.
   */
  public ObjectName []getWebAppNames()
  {
    Host host = getHost();

    if (host == null)
      return new ObjectName[0];

    ArrayList<WebAppController> webappList = host.getApplicationList();

    ObjectName []names = new ObjectName[webappList.size()];

    for (int i = 0; i < names.length; i++) {
      WebAppController controller = webappList.get(i);

      names[i] = controller.getObjectName();
    }

    return names;
  }

  public void describeWebAppNames(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setCategory(AdminAttributeCategory.CHILD);
  }

  /**
   * Returns the webapps.
   */
  public WebAppMBean []getWebApps()
  {
    Host host = getHost();

    if (host == null)
      return new WebAppMBean[0];

    ArrayList<WebAppController> webappList = host.getApplicationList();

    WebAppMBean []webapps = new WebAppMBean[webappList.size()];

    for (int i = 0; i < webapps.length; i++) {
      WebAppController controller = webappList.get(i);

      webapps[i] = controller.getAdmin();
    }

    return webapps;
  }

  public void describeWebApps(IntrospectionAttributeDescriptor descriptor)
  {
    descriptor.setDeprecated("3.0.15 Use WebAppNames");
  }

  /**
   * Returns the host.
   */
  protected Host getHost()
  {
    return getController().getDeployInstance();
  }

  /**
   * Returns a string view.
   */
  public String toString()
  {
    return "HostAdmin[" + getName() + "]";
  }
}
