/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

package com.caucho.env.repository;

import com.caucho.env.distcache.DistCacheService;
import com.caucho.env.service.AbstractResinService;
import com.caucho.env.service.ResinSystem;
import com.caucho.util.L10N;

/**
 * Public API for the persistent .git repository
 */
public class RepositoryService extends AbstractResinService
{
  private static final L10N L = new L10N(RepositoryService.class);
  
  public static final int START_PRIORITY = START_PRIORITY_CLUSTER_SERVICE;
  
  private AbstractRepository _repository;

  public RepositoryService(AbstractRepository repository)
  {
    _repository = repository;
    
    if (repository == null)
      throw new NullPointerException();
  }

  public RepositoryService()
  {
    this(new FileRepository());
  }
  
  public static RepositoryService getCurrent()
  {
    return ResinSystem.getCurrentService(RepositoryService.class);
  }
  
  public static RepositoryService create()
  {
    ResinSystem system = ResinSystem.getCurrent();
    
    if (system == null) {
      throw new IllegalStateException(L.l("{0} requires an active {1}",
                                          RepositoryService.class.getSimpleName(),
                                          ResinSystem.class.getSimpleName()));
    }

    RepositoryService service = system.getService(RepositoryService.class);
    
    if (service == null) {
      service = new RepositoryService();
      
      system.addServiceIfAbsent(service);
      
      service = system.getService(RepositoryService.class);
    }
    
    return service;
  }
  
  public static Repository getCurrentRepository()
  {
    RepositoryService service = getCurrent();
    
    if (service == null)
      throw new IllegalStateException(L.l("RepositoryService is not available in this context"));
    
    return service.getRepository();
  }
  
  public static RepositorySpi getCurrentRepositorySpi()
  {
    RepositoryService service = getCurrent();
    
    if (service == null)
      throw new IllegalStateException(L.l("RepositoryService is not available in this context"));
    
    return service.getRepositorySpi();
  }
  
  public Repository getRepository()
  {
    return _repository;
  }
  
  public RepositorySpi getRepositorySpi()
  {
    return _repository;
  }
  
  @Override
  public int getStartPriority()
  {
    return START_PRIORITY;
  }
  
  @Override
  public void start()
  {
    _repository.start();
  }
  
  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _repository + "]";
  }
}
