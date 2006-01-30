/*
 * Copyright (c) 1998-2005 Caucho Technology -- all rights reserved
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
 * @author Charles Reich
 */

package com.caucho.quercus.lib;

import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.SimpleXMLElementValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.module.NotNull;
import com.caucho.quercus.module.Optional;
import com.caucho.vfs.Path;

/**
 * PHP SimpleXML
 */
public class QuercusSimpleXMLModule extends AbstractQuercusModule {

  
  public SimpleXMLElementValue simplexml_load_string(Env env,
                                                     @NotNull String data,
                                                     @Optional String className,
                                                     @Optional int options)
  {
 
    return new SimpleXMLElementValue(data, className, options);
  }
  /*
  public Value simplexml_load_string(Env env,
                                     @NotNull String data,
                                     @Optional String className,
                                     @Optional int options)
  {
     return env.wrapJava(new SimpleXMLElementClass(data, className, options));
  }*/

  public SimpleXMLElementValue simplexml_load_file(@NotNull Path file,
                                                   @Optional String className,
                                                   @Optional int options)
  {
    return new SimpleXMLElementValue(file, className, options);
  }
  
  public Value simplexml_attributes(@NotNull SimpleXMLElementValue xmlElement,
                                    @Optional String data)
  {
    if (xmlElement == null)
      return BooleanValue.FALSE;
    
    return xmlElement.attributes(data);
  }
  
  public Value simplexml_children(@NotNull SimpleXMLElementValue xmlElement,
                                  @Optional String nsprefix)
  {
    if (xmlElement == null)
      return BooleanValue.FALSE;
    
    return xmlElement.children(nsprefix);
  }
  
  //@todo simplexml_import_dom -- Skip until (XXX. DOM Functions implemented)
}
