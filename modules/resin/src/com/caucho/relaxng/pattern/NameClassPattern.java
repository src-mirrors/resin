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

package com.caucho.relaxng.pattern;

import com.caucho.util.L10N;

import com.caucho.xml.QName;

import com.caucho.config.BeanConfigException;

import com.caucho.relaxng.RelaxException;

import com.caucho.relaxng.program.Item;

/**
 * Relax pattern
 */
abstract public class NameClassPattern extends Pattern {
  protected static final L10N L = new L10N(NameClassPattern.class);
  
  /**
   * Sets the parent.
   */
  public void setParent(Pattern parent)
    throws RelaxException
  {
    if (! (parent instanceof NameClassPattern) &&
        ! (parent instanceof ElementPattern) &&
        ! (parent instanceof AttributePattern)) {
      throw new RelaxException(L.l("{0} is not allowed as a child of {1}",
                                   getTagName(), parent.getTagName()));
    }

    super.setParent(parent);
  }
}
