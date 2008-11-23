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

package com.caucho.config.gen;

import com.caucho.java.JavaWriter;
import com.caucho.util.L10N;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.annotation.security.*;
import javax.ejb.*;
import javax.interceptor.*;

/**
 * Represents a filter for invoking a method
 */
public interface EjbCallChain {
  /**
   * Returns true if this filter will generate code.
   */
  public boolean isEnhanced();

  /**
   * Introspects the method for the default values
   */
  public void introspect(Method apiMethod, Method implMethod);

  /**
   * Generates the static class prologue
   */
  public void generatePrologue(JavaWriter out, HashMap map)
    throws IOException;

  /**
   * Generates initialization in the constructor
   */
  public void generateConstructor(JavaWriter out, HashMap map)
    throws IOException;

  /**
   * Generates the method interception code
   */
  public void generateCall(JavaWriter out)
    throws IOException;
}
