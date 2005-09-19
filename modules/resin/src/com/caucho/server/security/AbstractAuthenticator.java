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
 */

package com.caucho.server.security;

import java.lang.ref.SoftReference;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.security.*;

import javax.servlet.http.*;
import javax.servlet.*;

import com.caucho.util.*;
import com.caucho.vfs.*;

import com.caucho.log.Log;

import com.caucho.security.BasicPrincipal;

import com.caucho.server.webapp.Application;

import com.caucho.server.session.SessionImpl;
import com.caucho.server.session.SessionManager;

/**
 * All applications should extend AbstractAuthenticator to implement
 * their custom authenticators.  While this isn't absolutely required,
 * it protects implementations from API changes.
 *
 * <p>The AbstractAuthenticator provides a single-signon cache.  Users
 * logged into one web-app will share the same principal.
 */
public class AbstractAuthenticator implements ServletAuthenticator {
  static final Logger log = Log.open(AbstractAuthenticator.class);
  static final L10N L = new L10N(AbstractAuthenticator.class);
  
  public static final String LOGIN_NAME = "com.caucho.servlet.login.name";
  
  
  protected int _principalCacheSize = 4096;
  protected LruCache<String,PrincipalEntry> _principalCache;

  protected String _passwordDigestAlgorithm = "MD5-base64";
  protected String _passwordDigestRealm = "resin";
  protected PasswordDigest _passwordDigest;

  private boolean _logoutOnTimeout = true;

  /**
   * Returns the size of the principal cache.
   */
  public int getPrincipalCacheSize()
  {
    return _principalCacheSize;
  }

  /**
   * Sets the size of the principal cache.
   */
  public void setPrincipalCacheSize(int size)
  {
    _principalCacheSize = size;
  }

  /**
   * Returns the password digest
   */
  public PasswordDigest getPasswordDigest()
  {
    return _passwordDigest;
  }

  /**
   * Sets the password digest.  The password digest of the form:
   * "algorithm-format", e.g. "MD5-base64".
   */
  public void setPasswordDigest(PasswordDigest digest)
  {
    _passwordDigest = digest;
  }

  /**
   * Returns the password digest algorithm
   */
  public String getPasswordDigestAlgorithm()
  {
    return _passwordDigestAlgorithm;
  }

  /**
   * Sets the password digest algorithm.  The password digest of the form:
   * "algorithm-format", e.g. "MD5-base64".
   */
  public void setPasswordDigestAlgorithm(String digest)
  {
    _passwordDigestAlgorithm = digest;
  }

  /**
   * Returns the password digest realm
   */
  public String getPasswordDigestRealm()
  {
    return _passwordDigestRealm;
  }

  /**
   * Sets the password digest realm.
   */
  public void setPasswordDigestRealm(String realm)
  {
    _passwordDigestRealm = realm;
  }

  /**
   * Returns true if the user should be logged out on a session timeout.
   */
  public boolean getLogoutOnSessionTimeout()
  {
    return _logoutOnTimeout;
  }

  /**
   * Sets true if the principal should logout when the session times out.
   */
  public void setLogoutOnSessionTimeout(boolean logout)
  {
    _logoutOnTimeout = logout;
  }

  /**
   * Adds a role mapping.
   */
  public void addRoleMapping(Principal principal, String role)
  {
  }

  /**
   * Initialize the authenticator with the application.
   */
  public void init()
    throws ServletException
  {
    if (_principalCacheSize > 0)
      _principalCache = new LruCache<String,PrincipalEntry>(_principalCacheSize);

    if (_passwordDigest != null) {
      if (_passwordDigest.getAlgorithm() == null ||
	  _passwordDigest.getAlgorithm().equals("none"))
	_passwordDigest = null;
    }
    else if (_passwordDigestAlgorithm == null ||
	     _passwordDigestAlgorithm.equals("none")) {
    }
    else {
      int p = _passwordDigestAlgorithm.indexOf('-');

      if (p > 0) {
        String algorithm = _passwordDigestAlgorithm.substring(0, p);
        String format = _passwordDigestAlgorithm.substring(p + 1);

        _passwordDigest = new PasswordDigest();
        _passwordDigest.setAlgorithm(algorithm);
        _passwordDigest.setFormat(format);
        _passwordDigest.setRealm(_passwordDigestRealm);

        _passwordDigest.init();
      }
    }
  }

  /**
   * Logs the user in with any appropriate password.
   */
  public Principal login(HttpServletRequest request,
                         HttpServletResponse response,
                         ServletContext app,
                         String user, String password)
    throws ServletException
  {
    String digestPassword = getPasswordDigest(request, response, app,
					      user, password);

    Principal principal = loginImpl(request, response, app, user,
				    digestPassword);

    if (principal != null) {
      SessionImpl session = (SessionImpl) request.getSession();
      session.setUser(principal);

      if (_principalCache != null) {
	PrincipalEntry entry = new PrincipalEntry(principal);
	entry.addSession(session);
	
        _principalCache.put(session.getId(), entry);

	System.out.println("PC-ADD: " + session.getId());
      }
    }

    return principal;
  }

  /**
   * Returns the digest view of the password.  The default
   * uses the PasswordDigest class if available, and returns the
   * plaintext password if not.
   */
  public String getPasswordDigest(HttpServletRequest request,
				  HttpServletResponse response,
				  ServletContext app,
				  String user, String password)
    throws ServletException
  {

    if (_passwordDigest != null)
      return _passwordDigest.getPasswordDigest(request, response, app,
					       user, password);
    else
      return password;
  }

  /**
   * Authenticate (login) the user.
   */
  protected Principal loginImpl(HttpServletRequest request,
                                HttpServletResponse response,
                                ServletContext application,
                                String user, String password)
    throws ServletException
  {
    return null;
  }

  
  /**
   * Validates the user when using HTTP Digest authentication.
   * DigestLogin will call this method.  Most other AbstractLogin
   * implementations, like BasicLogin and FormLogin, will use
   * getUserPrincipal instead.
   *
   * <p>The HTTP Digest authentication uses the following algorithm
   * to calculate the digest.  The digest is then compared to
   * the client digest.
   *
   * <code><pre>
   * A1 = MD5(username + ':' + realm + ':' + password)
   * A2 = MD5(method + ':' + uri)
   * digest = MD5(A1 + ':' + nonce + A2)
   * </pre></code>
   *
   * @param request the request trying to authenticate.
   * @param response the response for setting headers and cookies.
   * @param app the servlet context
   * @param user the username
   * @param realm the authentication realm
   * @param nonce the nonce passed to the client during the challenge
   * @param uri te protected uri
   * @param qop
   * @param nc
   * @param cnonce the client nonce
   * @param clientDigest the client's calculation of the digest
   *
   * @return the logged in principal if successful
   */
  public Principal loginDigest(HttpServletRequest request,
                               HttpServletResponse response,
                               ServletContext app,
                               String user, String realm,
                               String nonce, String uri,
                               String qop, String nc, String cnonce,
                               byte []clientDigest)
    throws ServletException
  {
    Principal principal = loginDigestImpl(request, response, app,
                                          user, realm, nonce, uri,
                                          qop, nc, cnonce,
                                          clientDigest);

    if (principal != null) {
      SessionImpl session = (SessionImpl) request.getSession();
      session.setUser(principal);

      if (_principalCache != null) {
	PrincipalEntry entry = new PrincipalEntry(principal);
	entry.addSession(session);
	
        _principalCache.put(session.getId(), entry);
      }
    }

    return principal;
  }
  
  /**
   * Validates the user when HTTP Digest authentication.
   * The HTTP Digest authentication uses the following algorithm
   * to calculate the digest.  The digest is then compared to
   * the client digest.
   *
   * <code><pre>
   * A1 = MD5(username + ':' + realm + ':' + password)
   * A2 = MD5(method + ':' + uri)
   * digest = MD5(A1 + ':' + nonce + A2)
   * </pre></code>
   *
   * @param request the request trying to authenticate.
   * @param response the response for setting headers and cookies.
   * @param app the servlet context
   * @param user the username
   * @param realm the authentication realm
   * @param nonce the nonce passed to the client during the challenge
   * @param uri te protected uri
   * @param qop
   * @param nc
   * @param cnonce the client nonce
   * @param clientDigest the client's calculation of the digest
   *
   * @return the logged in principal if successful
   */
  public Principal loginDigestImpl(HttpServletRequest request,
                                   HttpServletResponse response,
                                   ServletContext app,
                                   String user, String realm,
                                   String nonce, String uri,
                                   String qop, String nc, String cnonce,
                                   byte []clientDigest)
    throws ServletException
  {
    
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");
      
      byte []a1 = getDigestSecret(request, response, app,
                                  user, realm, "MD5");

      if (a1 == null)
        return null;

      digestUpdateHex(digest, a1);
      
      digest.update((byte) ':');
      for (int i = 0; i < nonce.length(); i++)
        digest.update((byte) nonce.charAt(i));

      if (qop != null) {
        digest.update((byte) ':');
        for (int i = 0; i < nc.length(); i++)
          digest.update((byte) nc.charAt(i));

        digest.update((byte) ':');
        for (int i = 0; cnonce != null && i < cnonce.length(); i++)
          digest.update((byte) cnonce.charAt(i));
        
        digest.update((byte) ':');
        for (int i = 0; qop != null && i < qop.length(); i++)
          digest.update((byte) qop.charAt(i));
      }
      digest.update((byte) ':');

      byte []a2 = digest(request.getMethod() + ":" + uri);

      digestUpdateHex(digest, a2);

      byte []serverDigest = digest.digest();

      if (clientDigest == null || clientDigest.length != serverDigest.length)
        return null;

      for (int i = 0; i < clientDigest.length; i++) {
        if (serverDigest[i] != clientDigest[i])
          return null;
      }

      return new BasicPrincipal(user);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  private void digestUpdateHex(MessageDigest digest, byte []bytes)
  {
    for (int i = 0; i < bytes.length; i++) {
      int b = bytes[i];
      int d1 = (b >> 4) & 0xf;
      int d2 = b & 0xf;

      if (d1 < 10)
        digest.update((byte) (d1 + '0'));
      else
        digest.update((byte) (d1 + 'a' - 10));

      if (d2 < 10)
        digest.update((byte) (d2 + '0'));
      else
        digest.update((byte) (d2 + 'a' - 10));
    }
  }

  private String digestToString(byte []digest)
  {
    if (digest == null)
      return "null";

    CharBuffer cb = CharBuffer.allocate();
    for (int i = 0; i < digest.length; i++) {
      int ch = digest[i];

      int d1 = (ch >> 4) & 0xf;
      int d2 = ch & 0xf;

      if (d1 < 10)
        cb.append((char) (d1 + '0'));
      else
        cb.append((char) (d1 + 'a' - 10));
        
      if (d2 < 10)
        cb.append((char) (d2 + '0'));
      else
        cb.append((char) (d2 + 'a' - 10));
    }

    return cb.close();
  }

  /**
   * Returns the digest secret for Digest authentication.
   */
  protected byte []getDigestSecret(HttpServletRequest request,
                                   HttpServletResponse response,
                                   ServletContext application,
                                   String username, String realm,
                                   String algorithm)
    throws ServletException
  {
    String password = getDigestPassword(request, response, application,
                                        username, realm);
    
    if (password == null)
      return null;

    if (_passwordDigest != null)
      return _passwordDigest.stringToDigest(password);

    try {
      MessageDigest digest = MessageDigest.getInstance(algorithm);

      String string = username + ":" + realm + ":" + password;
      byte []data = string.getBytes("UTF8");
      return digest.digest(data);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  protected byte []digest(String value)
    throws ServletException
  {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");

      byte []data = value.getBytes("UTF8");
      return digest.digest(data);
    } catch (Exception e) {
      throw new ServletException(e);
    }
  }

  /**
   * Returns the password for authenticators too lazy to calculate the
   * digest.
   */
  protected String getDigestPassword(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ServletContext application,
                                     String username, String realm)
    throws ServletException
  {
    return null;
  }

  /**
   * Grab the user from the request, assuming the user has
   * already logged in.  In other words, overriding methods could
   * use cookies or the session to find the logged in principal, but
   * shouldn't try to log the user in with form parameters.
   *
   * @param request the servlet request.
   *
   * @return a Principal representing the user or null if none has logged in.
   */
  public Principal getUserPrincipal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    ServletContext application)
    throws ServletException
  {
    SessionImpl session = (SessionImpl) request.getSession(false);
    Principal user = null;

    if (session != null)
      user = session.getUser();
    
    if (user != null)
      return user;

    PrincipalEntry entry = null;
    
    if (_principalCache == null) {
    }
    else if (session != null)
      entry = _principalCache.get(session.getId());
    else if (request.getRequestedSessionId() != null)
      entry = _principalCache.get(request.getRequestedSessionId());

    if (entry != null) {
      user = entry.getPrincipal();

      if (session == null)
	session = (SessionImpl) request.getSession(true);
      
      session.setUser(user);
      entry.addSession(session);
      
      return user;
    }

    user = getUserPrincipalImpl(request, application);

    if (user == null) {
    }
    else if (session != null) {
      entry = new PrincipalEntry(user);
      
      session.setUser(user);
      entry.addSession(session);
      
      _principalCache.put(session.getId(), entry);
    }
    else if (request.getRequestedSessionId() != null) {
      entry = new PrincipalEntry(user);
      
      _principalCache.put(request.getRequestedSessionId(), entry);
    }

    return user;
  }
  
  /**
   * Gets the user from a persistent cookie, uaing authenticateCookie
   * to actually look the cookie up.
   */
  protected Principal getUserPrincipalImpl(HttpServletRequest request,
                                           ServletContext application)
    throws ServletException
  {
    return null;
  }

  /**
   * Returns true if the user plays the named role.
   *
   * @param request the servlet request
   * @param user the user to test
   * @param role the role to test
   */
  public boolean isUserInRole(HttpServletRequest request,
                              HttpServletResponse response,
                              ServletContext application,
                              Principal user, String role)
    throws ServletException
  {
    return false;
  }

  /**
   * Logs the user out from the session.
   *
   * @param application the application
   * @param timeoutSession the session timing out, null if not a timeout logout
   * @param user the logged in user
   */
  public void logout(ServletContext application,
		     HttpSession timeoutSession,
                     String sessionId,
                     Principal user)
    throws ServletException
  {
    if (timeoutSession == null)
      Thread.dumpStack();
    
    log.fine("logout " + user);

    if (sessionId != null) {
      System.out.println("PE: " + _principalCache);
      if (_principalCache == null) {
      }
      else if (timeoutSession != null) {
	PrincipalEntry entry =  _principalCache.get(sessionId);
	
	if (entry != null && entry.logout(timeoutSession)) {
	  _principalCache.remove(sessionId);
	}
      }
      else {
	PrincipalEntry entry =  _principalCache.remove(sessionId);

	if (entry != null)
	  entry.logout();
      }

      Application app = (Application) application;
      SessionManager manager = app.getSessionManager();

      if (manager != null) {
	SessionImpl session = manager.getSession(sessionId,
						 Alarm.getCurrentTime(),
						 false, true);

	if (session != null)
	  session.logout();
      }
    }
  }

  /**
   * Logs the user out from the session.
   *
   * @param request the servlet request
   * @deprecated
   */
  public void logout(HttpServletRequest request,
                     HttpServletResponse response,
                     ServletContext application,
                     Principal user)
    throws ServletException
  {
    logout(application, null, request.getRequestedSessionId(), user);
  }

  /**
   * Logs the user out from the session.
   *
   * @param request the servlet request
   * @deprecated
   */
  public void logout(ServletContext application,
		     String sessionId,
                     Principal user)
    throws ServletException
  {
    logout(application, null, sessionId, user);
  }

  static class PrincipalEntry {
    private Principal _principal;
    private ArrayList<SoftReference<SessionImpl>> _sessions;

    PrincipalEntry(Principal principal)
    {
      _principal = principal;
    }

    Principal getPrincipal()
    {
      return _principal;
    }

    void addSession(SessionImpl session)
    {
      if (_sessions == null)
	_sessions = new ArrayList<SoftReference<SessionImpl>>();
      
      _sessions.add(new SoftReference<SessionImpl>(session));
    }

    /**
     * Logout only the given session, returning true if it's the
     * last session to logout.
     */
    boolean logout(HttpSession timeoutSession)
    {
      ArrayList<SoftReference<SessionImpl>> sessions = _sessions;
      System.out.println("PE: " + timeoutSession + " " + sessions);

      if (sessions == null)
	return true;

      boolean isEmpty = true;
      for (int i = sessions.size() - 1; i >= 0; i--) {
	SoftReference<SessionImpl> ref = sessions.get(i);
	SessionImpl session = ref.get();

	try {
	  if (session == timeoutSession) {
	    sessions.remove(i);
	    session.logout();
	  }
	  else if (session != null)
	    isEmpty = false;
	  else
	    sessions.remove(i);
	} catch (Throwable e) {
	  log.log(Level.WARNING, e.toString(), e);
	}
      }

      return isEmpty;
    }
      
    void logout()
    {
      ArrayList<SoftReference<SessionImpl>> sessions = _sessions;
      _sessions = null;
      
      for (int i = 0; sessions != null && i < sessions.size(); i++) {
	SoftReference<SessionImpl> ref = sessions.get(i);
	SessionImpl session = ref.get();

	try {
	  if (session != null)
	    session.logout();
	} catch (Throwable e) {
	  log.log(Level.WARNING, e.toString(), e);
	}
      }
    }
  }
}
