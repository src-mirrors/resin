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

package com.caucho.quercus.lib;

import java.util.Map;
import java.util.HashMap;

import java.util.logging.Logger;

import com.caucho.util.L10N;

import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.module.Optional;
import com.caucho.quercus.module.Reference;

import com.caucho.quercus.env.Value;
import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.LongValue;

/**
 * Information about PHP network
 */
public class QuercusNetworkModule extends AbstractQuercusModule {
  private static final L10N L = new L10N(QuercusNetworkModule.class);
  private static final Logger log
    = Logger.getLogger(QuercusNetworkModule.class.getName());

  private static final HashMap<String,Value> _constMap =
          new HashMap<String,Value>();

  /**
   * Adds the constant to the PHP engine's constant map.
   *
   * @return the new constant chain
   */
  public Map<String,Value> getConstMap()
  {
    return _constMap;
  }

  /**
   * Opens a socket
   */
  public static Value fsockopen(String url,
                                @Optional int port,
                                @Optional @Reference Value errno,
                                @Optional @Reference Value errstr,
                                @Optional double timeout)
  {
    return NullValue.NULL;
  }

  /**
   * Converts string to long
   */
  public static Value ip2long(String ip)
  {
    long v = 0;

    int p = 0;
    int len = ip.length();
    for (int i = 0; i < 4; i++) {
      int digit = 0;
      char ch = 0;

      for (; p < len && '0' <= (ch = ip.charAt(p)) && ch <= '9'; p++) {
        digit = 10 * digit + ch - '0';
      }

      if (p < len && ch != '.')
        return BooleanValue.FALSE;
      else if (p == len && i < 3)
        return BooleanValue.FALSE;

      p++;

      v = 256 * v + digit;
    }

    return new LongValue(v);
  }
}

