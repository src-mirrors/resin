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

package com.caucho.security;

import com.caucho.security.BasicPrincipal;
import com.caucho.util.Base64;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.logging.Level;

/**
 * Implements the "basic" auth-method.  Basic uses the
 * HTTP authentication with WWW-Authenticate and SC_UNAUTHORIZE.
 */
public class BasicLogin extends AbstractLogin {
  protected String _realm;
  
  /**
   * Sets the login realm.
   */
  public void setRealmName(String realm)
  {
    _realm = realm;
  }

  /**
   * Gets the realm.
   */
  public String getRealmName()
  {
    return _realm;
  }

  /**
   * Returns the authentication type.
   */
  public String getAuthType()
  {
    return "Basic";
  }

  /**
   * Returns the principal from a basic authentication
   *
   * @param auth the authenticator for this application.
   */
  @Override
  protected Principal getUserPrincipalImpl(HttpServletRequest request)
  {
    String value = request.getHeader("authorization");
    if (value == null)
      return null;
    
    int i = value.indexOf(' ');
    if (i <= 0)
      return null;

    String decoded = Base64.decode(value.substring(i + 1));

    int index = decoded.indexOf(':');
    if (index < 0)
      return null;

    String userName = decoded.substring(0, index);
    char []password = decoded.substring(index + 1).toCharArray();

    Authenticator auth = getAuthenticator();
    BasicPrincipal user = new BasicPrincipal(userName);

    Credentials credentials = new PasswordCredentials(password);
    Principal principal = auth.authenticate(user, credentials, request);

    if (log.isLoggable(Level.FINE))
      log.fine("basic: " + user + " -> " + principal); 

    return principal;
  }

  /**
   * Returns the principal from a basic authentication
   *
   * @param auth the authenticator for this application.
   */
  @Override
  protected boolean isSavedUserValid(HttpServletRequest request,
				     Principal savedUser)
  {
    String value = request.getHeader("authorization");
    if (value == null)
      return true;
    
    int i = value.indexOf(' ');
    if (i <= 0)
      return true;

    String decoded = Base64.decode(value.substring(i + 1));

    int index = decoded.indexOf(':');
    if (index < 0)
      return true;

    String userName = decoded.substring(0, index);

    return savedUser.getName().equals(userName);
  }

  /**
   * Sends a challenge for basic authentication.
   */
  @Override
  protected void loginChallenge(HttpServletRequest request,
				HttpServletResponse response)
    throws IOException
  {
    String realm = getRealmName();
    if (realm == null)
      realm = "resin";

    response.setHeader("WWW-Authenticate", "Basic realm=\"" + realm + "\"");
    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
  }
}
