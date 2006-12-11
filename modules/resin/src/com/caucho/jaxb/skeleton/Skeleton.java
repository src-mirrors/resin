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
 * @author Adam Megacz
 */

package com.caucho.jaxb.skeleton;

import com.caucho.jaxb.JAXBContextImpl;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

public abstract class Skeleton {
  public static final String XML_SCHEMA_NS = "http://www.w3.org/2001/XMLSchema";
  public static final String XML_SCHEMA_PREFIX = "xsd";

  private static final Logger log = Logger.getLogger(Skeleton.class.getName());

  protected JAXBContextImpl _context;
  protected QName _typeName;

  protected LinkedHashMap<String,Accessor> _attributeAccessors
    = new LinkedHashMap<String,Accessor>();

  protected LinkedHashMap<String,Accessor> _elementAccessors 
    = new LinkedHashMap<String,Accessor>();

  protected Skeleton(JAXBContextImpl context)
  {
    _context = context;
  }

  public QName getTypeName()
  {
    return _typeName;
  }

  public abstract Object read(Unmarshaller u, XMLStreamReader in)
    throws IOException, XMLStreamException, JAXBException;
  
  public abstract void write(Marshaller m, XMLStreamWriter out,
                             Object obj, QName fieldName)
    throws IOException, XMLStreamException, JAXBException;

  protected Accessor getAccessor(QName q)
  {
    return _elementAccessors.get(q.getLocalPart());
  }

  public abstract void generateSchema(XMLStreamWriter out)
    throws JAXBException, XMLStreamException;

  public QName getElementName(Object object)
  {
    return null;
  }
}
