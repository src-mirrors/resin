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

package com.caucho.webbeans.manager;

import com.caucho.config.*;
import com.caucho.config.j2ee.*;
import com.caucho.util.*;
import com.caucho.webbeans.cfg.*;
import com.caucho.webbeans.component.*;
import com.caucho.webbeans.event.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Set;
import javax.webbeans.Observer;

/**
 * Matches bindings
 */
public class ObserverMap {
  private static final L10N L = new L10N(ObserverMap.class);
  
  private Class _type;

  private ArrayList<ObserverEntry> _observerList
    = new ArrayList<ObserverEntry>();

  public ObserverMap(Class type)
  {
    _type = type;
  }

  public void addObserver(Observer observer, Annotation []bindings)
  {
    ObserverEntry entry = new ObserverEntry(observer, bindings);

    _observerList.add(entry);
  }

  public <T> void resolveObservers(Set<Observer<T>> set, Annotation []bindings)
  {
    for (int i = 0; i < _observerList.size(); i++) {
      ObserverEntry observer = _observerList.get(i);

      if (observer.isMatch(bindings)) {
	set.add(observer.getObserver());
      }
    }
  }

  public void fireEvent(Object event, Annotation []bindings)
  {
    for (int i = 0; i < _observerList.size(); i++) {
      ObserverEntry observer = _observerList.get(i);

      if (observer.isMatch(bindings)) {
	observer.getObserver().notify(event);
      }
    }
  }

  static class ObserverEntry {
    private final Observer _observer;
    private final Binding []_bindings;

    ObserverEntry(Observer observer, Annotation []bindings)
    {
      _observer = observer;

      _bindings = new Binding[bindings.length];
      for (int i = 0; i < bindings.length; i++) {
	_bindings[i] = new Binding(bindings[i]);
      }
    }

    Observer getObserver()
    {
      return _observer;
    }

    boolean isMatch(Annotation []bindings)
    {
      if (bindings.length < _bindings.length)
	return false;
      
      for (Binding binding : _bindings) {
	if (! binding.isMatch(bindings))
	  return false;
      }

      return true;
    }
  }
}
