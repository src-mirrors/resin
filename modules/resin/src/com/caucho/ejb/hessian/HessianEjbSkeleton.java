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

package com.caucho.ejb.hessian;

import com.caucho.hessian.io.HessianInput;
import com.caucho.hessian.io.HessianOutput;
import com.caucho.hessian.server.HessianSkeleton;
import com.caucho.ejb.protocol.EjbProtocolManager;
import com.caucho.ejb.protocol.Skeleton;
import com.caucho.hessian.io.HessianRemoteResolver;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

/**
 * Base class for any bean skeleton capable of handling a Hessian request.
 *
 * <p/>Once selected, the calling servlet will dispatch the request through
 * the <code>_service</code> call.  After parsing the request headers,
 * <code>_service</code> calls the generated entry <code>_execute</code>
 * to execute the request.
 */
public class HessianEjbSkeleton extends Skeleton {
  protected static Logger log
    = Logger.getLogger(HessianEjbSkeleton.class.getName());

  private Object _object;
  private HessianSkeleton _skel;
  private HessianRemoteResolver _resolver;

  public HessianEjbSkeleton(Object object,
			   HessianSkeleton skel,
			   HessianRemoteResolver resolver)
  {
    assert(object != null);
      
    _object = object;
    _skel = skel;
    _resolver = resolver;
  }

  @Override
  public void _service(InputStream is, OutputStream os)
    throws Exception
  {
    HessianInput in = new HessianReader(is);
    HessianOutput out = new HessianWriter(os);

    in.setRemoteResolver(_resolver);
    
    String oldProtocol = EjbProtocolManager.setThreadProtocol("hessian");

    try {
      _skel.invoke(_object, in, out);
    } finally {
      EjbProtocolManager.setThreadProtocol(oldProtocol);
    }
  }
}
