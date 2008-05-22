/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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
 * @author Alex Rojkov
 */
package javax.faces.application;

import javax.faces.context.FacesContext;
import java.io.IOException;

public abstract class ResourceHandler {
  
  public static final String RESOURCE_IDENTIFIER = "/javax.faces.resource";

  public static final String LOCALE_PREFIX = "javax.faces.resource.localePrefix";

  public static final String RESOURCE_EXCLUDES_PARAM_NAME = "javax.faces.RESOURCE_EXCLUDES";

  public static final String RESOURCE_EXCLUDES_DEFAULT_VALUE = ".class .jsp .jspx .properties .xhtml";

  public abstract Resource createResource(String resourceName);

  public abstract Resource createResource(String resourceName, String libraryName);

  public abstract Resource createResource(String resourceName, String libraryName, String contentType);

  public abstract void handleResourceRequest(FacesContext context)
    throws IOException;

  public abstract boolean isResourceRequest(FacesContext context);

  public abstract String getRendererTypeForResourceName(String resourceName);
}
