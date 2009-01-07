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

package com.caucho.server.security;

import com.caucho.security.BasicPrincipal;
import com.caucho.security.PasswordCredentials;
import com.caucho.server.session.SessionImpl;
import com.caucho.server.session.SessionManager;
import com.caucho.server.webapp.Application;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;
import com.caucho.webbeans.component.*;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.lang.ref.SoftReference;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * All applications should extend AbstractAuthenticator to implement
 * their custom authenticators.  While this isn't absolutely required,
 * it protects implementations from API changes.
 *
 * <p>The AbstractAuthenticator provides a single-signon cache.  Users
 * logged into one web-app will share the same principal.
 */
public class AbstractAuthenticator
  extends com.caucho.security.AbstractAuthenticator
{

  //
  // basic password authentication
  //

  /**
   * Main authenticator API.
   */
  @Override
  public Principal authenticate(Principal principal,
				PasswordCredentials cred,
				Object details)
  {
    HttpServletRequest request = (HttpServletRequest) details;

    String userName = principal.getName();
    String password = new String(cred.getPassword());

    ServletContext webApp = request.getServletContext();
    HttpServletResponse response
      = (HttpServletResponse) request.getServletResponse();

    return loginImpl(request, response, webApp, userName, password);
  }

  /**
   * Backward compatiblity call
   */
  protected Principal loginImpl(HttpServletRequest request,
				HttpServletResponse response,
				ServletContext app,
				String userName, String password)
  {
    return getUserPrincipal(request, response, app);
  }

  /**
   * Backward compatiblity call
   */
  protected Principal getUserPrincipal(HttpServletRequest request,
				       HttpServletResponse response,
				       ServletContext app)
  {
    return null;
  }
}
