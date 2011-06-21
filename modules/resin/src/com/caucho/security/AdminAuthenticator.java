/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

import com.caucho.config.Admin;
import com.caucho.config.CauchoDeployment;
import com.caucho.config.Service;
import com.caucho.config.types.Period;
import com.caucho.distcache.AbstractCache;
import com.caucho.distcache.ClusterByteStreamCache;
import com.caucho.util.Base64;
import com.caucho.util.Crc64;
import com.caucho.util.L10N;

import javax.enterprise.inject.Default;
import javax.inject.Named;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The admin authenticator provides authentication for Resin admin/management
 * purposes.  It's typically defined at the &lt;resin> level.
 *
 * <code><pre>
 * &lt;resin:AdminAuthenticator path="WEB-INF/admin-users.xml"/>
 * </pre></code>
 *
 * <p>The format of the static file is as follows:
 *
 * <code><pre>
 * &lt;users>
 *   &lt;user name="h.potter" password="quidditch" roles="user,captain"/>
 *   ...
 * &lt;/users>
 * </pre></code>
 */
@Service
@Admin
@Named("resin-admin-authenticator")
@Default
@CauchoDeployment  
@SuppressWarnings("serial")
public class AdminAuthenticator extends XmlAuthenticator
{
  private static final Logger log
    = Logger.getLogger(AdminAuthenticator.class.getName());
  private static final L10N L = new L10N(AdminAuthenticator.class);

  private String _remoteCookie;
  private boolean _isComplete;

  private Hashtable<String, PasswordUser> _userMap
    = new Hashtable<String, PasswordUser>();

  private AbstractCache _authStore;

  public AdminAuthenticator()
  {
  }

  public final void initStore()
  {
    if (_authStore != null)
      return;

    AbstractCache authStore = AbstractCache.getMatchingCache(
      "resin:authenticator");

    if (authStore == null) {
      authStore = new ClusterByteStreamCache();

      authStore.setIdleTimeoutMillis(Period.FOREVER);
      authStore.setExpireTimeoutMillis(Period.FOREVER);

      authStore.setName("resin:authenticator");
      authStore.setScopeMode(AbstractCache.Scope.POD);
      authStore.setBackup(true);
      authStore.setTriplicate(true);

      authStore = authStore.createIfAbsent();
    }

    _authStore = authStore;
  }

  private void loadFromStore()
  {
    Hashtable<String, PasswordUser> userMap
      = (Hashtable<String, PasswordUser>) _authStore.get(
      "resin-admin-authenticator-map");

    if (userMap != null)
      _userMap = userMap;

    if (log.isLoggable(Level.FINEST))
      log.log(Level.FINEST, "admin authenticator loaded " + userMap);
  }

  private void updateStore() {
    _authStore.put("resin-admin-authenticator-map", _userMap);
  }

  public boolean isComplete()
  {
    return _isComplete;
  }
  
  public void setComplete(boolean isComplete)
  {
    _isComplete = true;
  }

  public void addUser(String userName, char []password, String []roles)
  {
    loadFromStore();

    if (super.getUserMap().containsKey(userName))
      throw new IllegalArgumentException(L.l("user `{0}' already exists",
                                             userName));

    if (_userMap.containsKey(userName))
      throw new IllegalArgumentException(L.l("user `{0}' already exists",
                                             userName));

    char []passwordDigest = getPasswordDigest(userName, password);

    PasswordUser user = new PasswordUser(userName,passwordDigest, roles);

    _userMap.put(userName, user);

    updateStore();
  }

  public void removeUser(String userName)
  {
    loadFromStore();

    if (super.getUserMap().containsKey(userName))
      throw new IllegalArgumentException(L.l("can not delete user `{0}'", userName));

    if (! _userMap.containsKey(userName))
      throw new IllegalArgumentException(L.l("unknown user `{0}'", userName));

    _userMap.remove(userName);

    updateStore();
  }

  @Override
  public Hashtable<String, PasswordUser> getUserMap()
  {
    loadFromStore();

    Hashtable<String, PasswordUser> userMap
      = new Hashtable<String, PasswordUser>();

    userMap.putAll(super.getUserMap());

    userMap.putAll(_userMap);

    return userMap;
  }

  /**
   * Abstract method to return a user based on the name
   *
   * @param userName the string user name
   *
   * @return the populated PasswordUser value
   */
  @Override
  protected PasswordUser getPasswordUser(String userName)
  {
    if ("admin.resin".equals(userName)) {
      String hash = getHash();
      PasswordDigest digest = getPasswordDigest();

      if (digest != null)
        hash = digest.getPasswordDigest(userName, hash);
      
      return new PasswordUser(userName, hash);
    }

    loadFromStore();

    PasswordUser user = super.getPasswordUser(userName);

    if (user == null)
      user = _userMap.get(userName);

    return user;
  }

  /**
   * Creates a cookie based on the user hash.
   */
  public String getHash()
  {
    if (_remoteCookie == null) {
      long crc64 = 0;

      for (PasswordUser user : getUserMap().values()) {
        if (user.isDisabled())
          continue;

        crc64 = Crc64.generate(crc64, user.getPrincipal().getName());
        crc64 = Crc64.generate(crc64, ":");
        crc64 = Crc64.generate(crc64, new String(user.getPassword()));
      }

      if (crc64 != 0) {
        StringBuilder cb = new StringBuilder();
        Base64.encode(cb, crc64);

        _remoteCookie = cb.toString();
      }
    }

    return _remoteCookie;
  }

  @Override
  public String getDefaultGroup()
  {
    return "resin-admin";
  }
}
