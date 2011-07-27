/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.server.admin;

import com.caucho.vfs.*;
import java.io.InputStream;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("serial")
public class DeploySendQuery implements java.io.Serializable
{
  private String _sha1;
  private StreamSource _source;

  @SuppressWarnings("unused")
  private DeploySendQuery()
  {
  }

  public DeploySendQuery(String sha1,
                         StreamSource source)
  {
    _sha1 = sha1;
    _source = source;
  }

  public String getSha1()
  {
    return _sha1;
  }

  public InputStream getInputStream()
    throws IOException
  {
    return _source.getInputStream();
  }

  @Override
  public String toString()
  {
    return (getClass().getSimpleName() + "[" + _sha1 + "]");
  }
}
