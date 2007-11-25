/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
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

package com.caucho.webbeans.context;

import com.caucho.util.*;
import com.caucho.webbeans.component.*;
import com.caucho.server.dispatch.ServletInvocation;

import java.util.*;

import javax.faces.*;
import javax.faces.context.*;
import javax.faces.component.*;
import javax.servlet.*;
import javax.servlet.http.*;
import javax.webbeans.*;

/**
 * The conversation scope value
 */
public class ConversationScope extends ScopeContext
  implements Conversation, java.io.Serializable
{
  private static final L10N L = new L10N(ConversationScope.class);

  public ConversationScope()
  {
  }

  /**
   * Returns the current value of the component in the conversation scope.
   */
  public <T> T get(ComponentFactory<T> component, boolean create)
  {
    FacesContext facesContext = FacesContext.getCurrentInstance();

    if (facesContext == null)
      throw new IllegalStateException(L.l("@ConversationScoped is not available because JSF is not active"));

    ExternalContext extContext = facesContext.getExternalContext();
    Map<String,Object> sessionMap = extContext.getSessionMap();

    Scope scope = (Scope) sessionMap.get("caucho.conversation");

    // XXX: create
    if (scope == null)
      return null;

    UIViewRoot root = facesContext.getViewRoot();
    String id = root.getViewId();

    HashMap map = scope._conversationMap.get(id);

    if (map == null) {
      map = scope._extendedConversation;

      if (map != null)
	scope._conversationMap.put(id, map);
    }

    if (map != null)
      return (T) map.get(((ComponentImpl) component).getScopeId());
    else
      return null;
  }
  
  /**
   * Sets the current value of the component in the conversation scope.
   */
  public <T> void put(ComponentFactory<T> component, T value)
  {
    FacesContext facesContext = FacesContext.getCurrentInstance();

    if (facesContext == null)
      throw new IllegalStateException(L.l("@ConversationScoped is not available because JSF is not active"));

    ExternalContext extContext = facesContext.getExternalContext();
    Map<String,Object> sessionMap = extContext.getSessionMap();

    Scope scope = (Scope) sessionMap.get("caucho.conversation");

    if (scope == null) {
      scope = new Scope();
      sessionMap.put("caucho.conversation", scope);
    }

    UIViewRoot root = facesContext.getViewRoot();
    String id = root.getViewId();

    HashMap map = scope._conversationMap.get(id);

    if (map == null) {
      if (scope._extendedConversation != null)
	map = scope._extendedConversation;
      else
	map = new HashMap(8);
      
      scope._conversationMap.put(id, map);
    }
    
    map.put(((ComponentImpl) component).getScopeId(), value);
  }
  
  /**
   * Removes the current value of the component in the conversation scope.
   */
  public <T> void remove(ComponentFactory<T> component)
  {
    FacesContext facesContext = FacesContext.getCurrentInstance();

    if (facesContext == null)
      throw new IllegalStateException(L.l("@ConversationScoped is not available because JSF is not active"));

    ExternalContext extContext = facesContext.getExternalContext();
    Map<String,Object> sessionMap = extContext.getSessionMap();

    Scope scope = (Scope) sessionMap.get("caucho.conversation");

    if (scope == null)
      return;

    UIViewRoot root = facesContext.getViewRoot();
    String id = root.getViewId();

    HashMap map = scope._conversationMap.get(id);

    if (map != null)
      map.remove(((ComponentImpl) component).getScopeId());
  }

  /**
   * returns true if the argument scope can be safely injected in a
   * scope-instance.
   */
  @Override
  public boolean canInject(ScopeContext scope)
  {
    return (scope instanceof SingletonScope
	    || scope instanceof ApplicationScope
	    || scope instanceof SessionScope
	    || scope instanceof ConversationScope);
  }

  //
  // Conversation API
  //

  /**
   * Begins an extended conversation
   */
  public void begin()
  {
    FacesContext facesContext = FacesContext.getCurrentInstance();

    if (facesContext == null)
      throw new IllegalStateException(L.l("@ConversationScoped is not available because JSF is not active"));

    ExternalContext extContext = facesContext.getExternalContext();
    Map<String,Object> sessionMap = extContext.getSessionMap();

    Scope scope = (Scope) sessionMap.get("caucho.conversation");

    if (scope == null) {
      scope = new Scope();
      sessionMap.put("caucho.conversation", scope);
    }

    UIViewRoot root = facesContext.getViewRoot();
    String id = root.getViewId();

    HashMap map = scope._conversationMap.get(id);
    
    if (map == null) {
      map = new HashMap(8);
      scope._conversationMap.put(id, map);
    }

    scope._extendedConversation = map;
  }

  /**
   * Ends an extended conversation
   */
  public void end()
  {
    FacesContext facesContext = FacesContext.getCurrentInstance();

    if (facesContext == null)
      throw new IllegalStateException(L.l("@ConversationScoped is not available because JSF is not active"));

    ExternalContext extContext = facesContext.getExternalContext();
    Map<String,Object> sessionMap = extContext.getSessionMap();

    Scope scope = (Scope) sessionMap.get("caucho.conversation");

    if (scope == null)
      return;

    scope._extendedConversation = null;
  }

  static class Scope implements java.io.Serializable {
    final HashMap<String,HashMap> _conversationMap
      = new HashMap<String,HashMap>();

    HashMap _extendedConversation;
  }
}
