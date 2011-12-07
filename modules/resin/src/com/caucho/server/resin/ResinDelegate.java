/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.server.resin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.caucho.cloud.license.LicenseClient;
import com.caucho.cloud.loadbalance.LoadBalanceFactory;
import com.caucho.cloud.network.ClusterServer;
import com.caucho.cloud.network.NetworkClusterSystem;
import com.caucho.cloud.security.SecurityService;
import com.caucho.cloud.topology.CloudServer;
import com.caucho.cloud.topology.CloudSystem;
import com.caucho.cloud.topology.TopologyService;
import com.caucho.config.ConfigException;
import com.caucho.env.health.HealthStatusService;
import com.caucho.env.log.LogSystem;
import com.caucho.env.repository.AbstractRepository;
import com.caucho.env.repository.RepositorySpi;
import com.caucho.env.shutdown.ExitCode;
import com.caucho.env.shutdown.ShutdownSystem;
import com.caucho.env.warning.WarningService;
import com.caucho.license.LicenseCheck;
import com.caucho.server.admin.Management;
import com.caucho.server.admin.StatSystem;
import com.caucho.server.cache.TempFileService;
import com.caucho.server.cluster.ClusterPod;
import com.caucho.server.cluster.Server;
import com.caucho.server.distcache.CacheStoreManager;
import com.caucho.server.distcache.DistCacheSystem;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;

/**
 * The Resin class represents the top-level container for Resin.
 * It exactly matches the &lt;resin> tag in the resin.xml
 */
public class ResinDelegate
{
  private static Logger log = Logger.getLogger(ResinDelegate.class.getName());
  private static L10N L = new L10N(ResinDelegate.class);

  private final Resin _resin;

  /**
   * Creates a new resin server.
   */
  public ResinDelegate(Resin resin)
  {
    _resin = resin;
  }

  /**
   * Creates a new Resin instance
   */
  public static ResinDelegate create(Resin resin)
  {
    String licenseErrorMessage = null;

    ResinDelegate delegate = null;

    try {
      Class<?> cl = Class.forName("com.caucho.server.resin.ProResinDelegate");
      Constructor<?> ctor = cl.getConstructor(new Class[] { Resin.class });

      delegate = (ResinDelegate) ctor.newInstance(resin);
    } catch (ConfigException e) {
      log.log(Level.FINER, e.toString(), e);

      licenseErrorMessage = e.getMessage();
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();

      log.log(Level.FINER, cause.toString(), cause);

      if (cause instanceof ConfigException) {
        licenseErrorMessage = cause.getMessage();
      }
      else {
        licenseErrorMessage= L.l("  Using Resin(R) Open Source under the GNU Public License (GPL).\n"
                                 + "\n"
                                 + "  See http://www.caucho.com for information on Resin Professional,\n"
                                 + "  including caching, clustering, JNI acceleration, and OpenSSL integration.\n"
                                 + "\n  Exception=" + cause + "\n");
      }
    } catch (Throwable e) {
      log.log(Level.FINER, e.toString(), e);

      String causeMsg = "";
      if (! (e instanceof ClassNotFoundException)) {
        causeMsg = "\n  Exception=" + e + "\n";
      }


      String msg = L.l("  Using Resin(R) Open Source under the GNU Public License (GPL).\n"
                       + "\n"
                       + "  See http://www.caucho.com for information on Resin Professional,\n"
                       + "  including caching, clustering, JNI acceleration, and OpenSSL integration.\n"
                       + causeMsg);

      licenseErrorMessage = msg;
    }

    if (delegate == null) {
      try {
        Class<?> cl = Class.forName("com.caucho.license.LicenseCheckImpl");
        LicenseCheck license = (LicenseCheck) cl.newInstance();

        license.requirePersonal(1);

        licenseErrorMessage = license.doLogging();
      } catch (ConfigException e) {
        licenseErrorMessage = e.getMessage();
      } catch (Throwable e) {
        // message should already be set above
      }

      delegate = new ResinDelegate(resin);
    }

    return delegate;
  }

  /**
   * Creates a new Resin instance
   */
  public static ResinDelegate createOpenSource(Resin resin)
  {
    return new ResinDelegate(resin);
  }
  
  protected Resin getResin()
  {
    return _resin;
  }
  
  protected String getLicenseMessage()
  {
    return null;
  }
  
  protected String getLicenseErrorMessage()
  {
    return null;
  }
  
  protected DistCacheSystem createDistCacheService()
  {
    return DistCacheSystem.
      createAndAddService(new CacheStoreManager(_resin.getResinSystem()));
  }

  /**
   * 
   */
  protected void addServices()
  {
    TempFileService.createAndAddService();
  }

  /**
   * @param clusterService
   * @return
   */
  protected Server createServer()
  {
    return new Server(getResin());
  }

  protected Management createResinManagement()
  {
    return null;
  }

  public StatSystem createStatSystem()
  {
    throw new ConfigException("StatSystem is available with Resin Professional");
  }

  protected String getResinName()
  {
    return "Resin";
  }

  /**
   * @return
   */
  public boolean isProfessional()
  {
    // TODO Auto-generated method stub
    return false;
  }

  /**
   * @return
   */
  public LogSystem createLogSystem()
  {
    throw new ConfigException("LogSystem is available with Resin Professional");
  }

  public AbstractRepository createRepository(RepositorySpi localRepository)
  {
    return (AbstractRepository) localRepository;
  }

  protected CloudServer joinCluster(CloudSystem system)
  {
    throw new ConfigException(L.l("-join-cluster requires Resin Professional"));
  }

  /**
   * @param pod
   * @param dynId
   * @param dynAddress
   * @param dynPort
   * @return
   */
  protected ClusterServer loadDynamicServer(ClusterPod pod, String dynId,
                                            String dynAddress, int dynPort)
  {
    throw new ConfigException(L.l("dynamic-server requires Resin Professional"));
  }

  /**
   * 
   */
  protected void validateServerCluster()
  {
    if (getResin().getSelfServer().getPod().getServerLength() <= 1)
      return;
      
    if (loadCloudLicenses()) {
      ShutdownSystem.shutdownActive(ExitCode.MODIFIED,
                                    L.l("{0} has loaded new licenses, and requires a restart.",
                                        getResin()));
    }
      
    throw new ConfigException(L.l("{0} does not support multiple <server> instances in a cluster.\nFor clustered servers, please use Resin Professional with a valid license.",
                                  getResin()));
  }
  
  protected boolean loadCloudLicenses()
  {
    try {
      Class<?> cl = Class.forName("com.caucho.cloud.license.LicenseClientImpl");
      LicenseClient client = (LicenseClient) cl.newInstance();
      
      Path licenses = getResin().getServerDataDirectory().lookup("licenses");
      
      return client.loadLicenses(licenses, getResin().getSelfServer().getPod());
    } catch (ClassNotFoundException e) {
      log.log(Level.ALL, e.toString(), e);
    } catch (Exception e) {
      log.log(Level.FINER, e.toString(), e);
    }
    
    return false;
  }

  /**
   * 
   */
  public void dumpThreads()
  {
    // TODO Auto-generated method stub
    
  }

  /**
   * 
   */
  public void dumpHeapOnExit()
  {
  }

  protected LoadBalanceFactory createLoadBalanceFactory()
  {
    return new LoadBalanceFactory();
  }

  /**
   * 
   */
  protected void addPreTopologyServices()
  {
    WarningService.createAndAddService();
    
    ShutdownSystem.createAndAddService(getResin().isEmbedded());
    
    HealthStatusService.createAndAddService();
    
    TopologyService.createAndAddService(getResin().getServerId());
    
    SecurityService.createAndAddService();

    createDistCacheService();
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[]";
  }
}
