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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 *
 * $Id: DuplicateKeyWrapper.java,v 1.2 2004/09/29 00:13:00 cvs Exp $
 */

package com.caucho.ejb;

import java.io.*;
import java.util.*;

import com.caucho.util.*;

/**
 * Wraps the actual exception with an EJB exception
 */
public class DuplicateKeyWrapper extends javax.ejb.DuplicateKeyException
  implements ExceptionWrapper {
  private Throwable rootCause;

  /**
   * Create a basic DuplicateKeyWrapper with a message.
   *
   * @param msg the exception message.
   */
  public DuplicateKeyWrapper(String msg)
  {
    super(msg);
  }

  /**
   * Create a DuplicateKeyWrapper wrapping a root exception.
   *
   * @param rootCause the underlying wrapped exception.
   */
  public DuplicateKeyWrapper(Throwable rootCause)
  {
    super(rootCause.getMessage());

    this.rootCause = rootCause;
  }

  /**
   * Returns the root exception if it exists.
   *
   * @return the underlying wrapped exception.
   */
  public Throwable getRootCause()
  {
    return rootCause;
  }

  /**
   * Returns the appropriate exception message.
   */
  public String getMessage()
  {
    if (rootCause != null)
      return rootCause.getMessage();
    else
      return super.getMessage();
  }

  /**
   * Print the exception as a string.
   */
  public String toString()
  {
    if (rootCause == null)
      return super.toString();
    else
      return getClass().getName() + ": " + rootCause;
  }
}

