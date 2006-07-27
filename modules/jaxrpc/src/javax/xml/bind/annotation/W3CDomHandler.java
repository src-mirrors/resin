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

package javax.xml.bind.annotation;
import javax.xml.transform.*;
import org.w3c.dom.*;
import javax.xml.transform.dom.*;
import javax.xml.parsers.*;
import javax.xml.bind.*;
import org.w3c.dom.Element;

/** XXX */
public class W3CDomHandler implements DomHandler<org.w3c.dom.Element, DOMResult> {

  /** XXX */
  public W3CDomHandler()
  {
    throw new UnsupportedOperationException();
  }


  /** XXX */
  public W3CDomHandler(DocumentBuilder builder)
  {
    throw new UnsupportedOperationException();
  }


  /** XXX */
  public DOMResult createUnmarshaller(ValidationEventHandler errorHandler)
  {
    throw new UnsupportedOperationException();
  }

  public DocumentBuilder getBuilder()
  {
    throw new UnsupportedOperationException();
  }


  /** XXX */
  public Element getElement(DOMResult r)
  {
    throw new UnsupportedOperationException();
  }


  /** XXX */
  public Source marshal(Element element, ValidationEventHandler errorHandler)
  {
    throw new UnsupportedOperationException();
  }

  public void setBuilder(DocumentBuilder builder)
  {
    throw new UnsupportedOperationException();
  }

}

