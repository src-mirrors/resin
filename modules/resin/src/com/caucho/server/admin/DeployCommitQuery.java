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

package com.caucho.server.admin;

import java.util.*;

public class DeployCommitQuery implements java.io.Serializable
{
  private String _tag;
  private String _hex;
  private String _user;
  private String _message;
  private String _version;
  private HashMap<String,String> _attr;

  private DeployCommitQuery()
  {
  }

  public DeployCommitQuery(String tag,
			   String hex,
			   String user,
			   String message,
			   String version,
			   HashMap<String,String> attr)
  {
    _tag = tag;
    _hex = hex;
    _user = user;
    _message = message;
    _version = version;
    _attr = attr;
  }

  public String getTag()
  {
    return _tag;
  }

  public String getHex()
  {
    return _hex;
  }

  public String getUser()
  {
    return _user;
  }

  public String getMessage()
  {
    return _message;
  }

  public String getVersion()
  {
    return _version;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _tag + "," + _hex + "]";
  }
}
