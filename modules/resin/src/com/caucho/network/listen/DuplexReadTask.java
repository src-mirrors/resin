/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is software; you can redistribute it and/or modify
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

package com.caucho.network.listen;

import java.io.IOException;
import java.util.logging.Logger;

import com.caucho.inject.Module;
import com.caucho.vfs.ReadStream;

/**
 * A protocol-independent TcpConnection.  TcpConnection controls the
 * TCP Socket and provides buffered streams.
 *
 * <p>Each TcpConnection has its own thread.
 */
@Module
class DuplexReadTask extends ConnectionReadTask {
  private static final Logger log
    = Logger.getLogger(DuplexReadTask.class.getName());
  
  private final SocketLinkDuplexController _duplex;

  DuplexReadTask(TcpSocketLink socketLink,
                 SocketLinkDuplexController duplex)
  {
    super(socketLink);

    _duplex = duplex;
  }
  
  @Override
  public void run()
  {
    SocketLinkThreadLauncher launcher = getLauncher();
    
    launcher.onChildThreadResume();
    try {
      super.run();
    } finally {
      launcher.onChildThreadEnd();
    }
  }

  @Override
  public RequestState doTask()
    throws IOException
  {
    TcpSocketLink socketLink = getSocketLink();
    
    socketLink.toDuplexActive();

    RequestState result;

    ReadStream readStream = socketLink.getReadStream();

    while ((result = socketLink.processKeepalive()) == RequestState.REQUEST) {
      long position = readStream.getPosition();

      _duplex.serviceRead();

      if (position == readStream.getPosition()) {
        log.warning(_duplex + " was not processing any data. Shutting down.");
        socketLink.close();

        return RequestState.EXIT;
      }
    }

    return result;
  }
}