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
 * Represents the security interception
 */
public class SecurityCallChain extends AbstractCallChain {
  private static final L10N L = new L10N(SecurityCallChain.class);

  private BusinessMethodGenerator _bizMethod;
  private EjbCallChain _next;

  private String []_roles;
  private String _roleVar;

  private String _runAs;

  public SecurityCallChain(BusinessMethodGenerator bizMethod,
			   EjbCallChain next)
  {
    super(next);
    
    _bizMethod = bizMethod;
    _next = next;
  }
  
  /**
   * Returns true if the business method has any active XA annotation.
   */
  public boolean isEnhanced()
  {
    if (_roles != null)
      return true;
    else if (_runAs != null)
      return true;
    else
      return false;
  }

  /**
   * Sets the transaction type
   */
  public void setRoles(ArrayList<String> roles)
  {
    _roles = new String[roles.size()];

    roles.toArray(_roles);
  }

  /**
   * Introspect EJB security annotations:
   *   @RunAs
   *   @RolesAllowed
   *   @PermitAll
   *   @DenyAll
   */
  public void introspect(Method apiMethod, Method implMethod)
  {
    Class cl = apiMethod.getDeclaringClass();
    
    RunAs runAs = (RunAs) cl.getAnnotation(RunAs.class);

    if (runAs != null)
      _runAs = runAs.value();
    
    RolesAllowed rolesAllowed
      = (RolesAllowed) cl.getAnnotation(RolesAllowed.class);
    
    if (rolesAllowed != null)
      _roles = rolesAllowed.value();
    
    PermitAll permitAll = (PermitAll) cl.getAnnotation(PermitAll.class);

    if (permitAll != null)
      _roles = null;
    
    DenyAll denyAll = (DenyAll) cl.getAnnotation(DenyAll.class);

    if (denyAll != null)
      _roles = new String[0];

    // 
    
    rolesAllowed = apiMethod.getAnnotation(RolesAllowed.class);

    if (rolesAllowed != null)
      _roles = rolesAllowed.value();
    
    permitAll = (PermitAll) apiMethod.getAnnotation(PermitAll.class);

    if (permitAll != null)
      _roles = null;
    
    denyAll = (DenyAll) apiMethod.getAnnotation(DenyAll.class);

    if (denyAll != null)
      _roles = new String[0];
  }

  /**
   * Generates the static class prologue
   */
  public void generatePrologue(JavaWriter out, HashMap map)
    throws IOException
  {
    if (_roles != null) {
      _roleVar = "_role_" + out.generateId();

      out.print("private static String []" + _roleVar + " = new String[] {");

      for (int i = 0; i < _roles.length; i++) {
	if (i != 0)
	  out.print(", ");

	out.print("\"");
	out.printJavaString(_roles[i]);
	out.print("\"");
      }

      out.println("};");
    }

    _next.generatePrologue(out, map);
  }

  /**
   * Generates the method interceptor code
   */
  public void generateCall(JavaWriter out)
    throws IOException
  {
    if (_roleVar != null) {
      out.println("com.caucho.security.SecurityContext.checkUserInRole(" + _roleVar + ");");
      out.println();
    }

    if (_runAs != null) {
      out.print("String oldRunAs ="
		+ " com.caucho.security.SecurityContext.runAs(\"");
      out.printJavaString(_runAs);
      out.println("\");");

      out.println("try {");
      out.pushDepth();
    }

    _next.generateCall(out);

    if (_runAs != null) {
      out.popDepth();
      out.println("} finally {");
      out.println("  com.caucho.security.SecurityContext.runAs(oldRunAs);");
      out.println("}");
    }
  }
}
