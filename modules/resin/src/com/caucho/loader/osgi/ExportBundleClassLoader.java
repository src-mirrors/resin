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

package com.caucho.loader.osgi;

import com.caucho.loader.*;
import com.caucho.util.*;

/**
 * OSGi exported bundle class loader
 */
public class ExportBundleClassLoader extends DynamicClassLoader
{
  private static final L10N L = new L10N(ExportBundleClassLoader.class);

  private String _symbolicName;
  private String _version;

  private String _packageName;
  
  /**
   * Creates a new export class loader.
   */
  protected ExportBundleClassLoader(ClassLoader parent,
				    String symbolicName,
				    String version)
  {
    super(parent);

    if (symbolicName == null)
      throw new IllegalArgumentException(L.l("ExportBundleClassLoader requires a symbolicName."));

    _symbolicName = symbolicName;
    _version = version;
  }

  /**
   * Creates a new environment class loader.
   */
  public static ExportBundleClassLoader create(ClassLoader parent,
					       String symbolicName,
					       String version)
  {
    return new ExportBundleClassLoader(parent,
				       symbolicName,
				       version);
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName()
	    + "[" + _symbolicName
	    + "," + _version + "]");
  }
}
