/*
* Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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
* @author Emil Ong
*/

package com.caucho.xml.saaj;

import java.util.*;

import javax.xml.XMLConstants;
import javax.xml.namespace.*;
import javax.xml.soap.*;

import org.w3c.dom.*;

import com.caucho.xml.XmlUtil;

public abstract class SOAPNodeImpl 
  implements javax.xml.soap.Node 
{
  protected SOAPFactory _factory;

  protected Document _owner;

  protected SOAPElementImpl _parent;

  protected SOAPNodeImpl _firstChild;
  protected SOAPNodeImpl _lastChild;

  protected SOAPNodeImpl _next;
  protected SOAPNodeImpl _previous;

  protected NameImpl _name;

  protected SOAPNodeImpl(SOAPFactory factory, NameImpl name)
  {
    _factory = factory;
    _name = name;
  }

  protected SOAPNodeImpl(SOAPFactory factory, NameImpl name, Document owner)
  {
    _factory = factory;
    _name = name;
    _owner = owner;
  }

  public void setOwner(Document owner)
  {
    _owner = owner;
  }
  
  // javax.xml.soap.Node

  public void detachNode()
  {
    if (getParentNode() != null)
      getParentNode().removeChild(this);
  }

  public SOAPElement getParentElement()
  {
    return (SOAPElement) getParentNode();
  }

  public void setParentElement(SOAPElement parent) 
    throws SOAPException
  {
    if (parent instanceof SOAPElementImpl)
      _parent = (SOAPElementImpl) parent;
    else
      _parent = new SOAPElementImpl(_factory, parent);
  }

  public String getValue()
  {
    if (_firstChild == null || _firstChild != _lastChild)
      return null;

    if (_firstChild instanceof javax.xml.soap.Text)
      return ((javax.xml.soap.Text) _firstChild).getValue();

    return null;
  }

  public void setValue(String value)
  {
    if (_firstChild == null)
      appendChild(new TextImpl(value));
    else {
      if (_firstChild != _lastChild)
        throw new IllegalStateException("Element has more than one child");

      if (! (_firstChild instanceof javax.xml.soap.Text))
        throw new IllegalStateException("Child is not a Text node");

      ((javax.xml.soap.Text) _firstChild).setValue(value);
    }
  }

  public void recycleNode()
  {
  }

  // org.w3c.dom.Node
  
  public NamedNodeMap getAttributes()
  {
    return null;
  }
  
  public Document getOwnerDocument()
  {
    return _owner;
  }

  public org.w3c.dom.Node appendChild(org.w3c.dom.Node newChild)
  {
    SOAPNodeImpl node = (SOAPNodeImpl) newChild;

    if (_lastChild == null)
      _firstChild = _lastChild = node;
    else {
      _lastChild._next = node;
      node._previous = _lastChild;
      _lastChild = node;
    }

    return newChild;
  }

  public org.w3c.dom.Node cloneNode(boolean deep)
  {
    throw new UnsupportedOperationException();
  }

  public short compareDocumentPosition(org.w3c.dom.Node other)
  {
    throw new UnsupportedOperationException();
  }

  public String getBaseURI()
  {
    return null;
  }

  public NodeList getChildNodes()
  {
    return new NodeListImpl();
  }

  public Object getFeature(String feature, String version)
  {
    return null;
  }

  public org.w3c.dom.Node getFirstChild()
  {
    return _firstChild;
  }

  public org.w3c.dom.Node getLastChild()
  {
    return _lastChild;
  }

  public String getPrefix()
  {
    return _name.getPrefix();
  }

  public String getLocalName()
  {
    return _name.getLocalName();
  }

  public String getNamespaceURI()
  {
    return _name.getURI();
  }

  public org.w3c.dom.Node getNextSibling()
  {
    return _next;
  }

  public org.w3c.dom.Node getParentNode()
  {
    return _parent;
  }

  public org.w3c.dom.Node getPreviousSibling()
  {
    return _previous;
  }

  public String getTextContent()
  {
    return XmlUtil.textValue(this);
  }

  public Object getUserData(String key)
  {
    return null;
  }

  public Object setUserData(String key, Object data, UserDataHandler handler)
  {
    return null;
  }

  public boolean hasAttributes()
  {
    return false;
  }

  public boolean hasChildNodes()
  {
    return _firstChild != null;
  }

  public org.w3c.dom.Node insertBefore(org.w3c.dom.Node newChild, 
                                       org.w3c.dom.Node refChild)
  {
    throw new UnsupportedOperationException();
  }

  public boolean isDefaultNamespace(String namespaceURI)
  {
    throw new UnsupportedOperationException();
  }

  public boolean isEqualNode(org.w3c.dom.Node arg)
  {
    return equals(arg);
  }

  public boolean isSameNode(org.w3c.dom.Node other)
  {
    return this == other;
  }

  public boolean isSupported(String feature, String version)
  {
    return false;
  }

  public String lookupNamespaceURI(String prefix)
  {
    return null;
  }

  public String lookupPrefix(String namespaceURI)
  {
    return null;
  }

  public void normalize()
  {
  }

  public org.w3c.dom.Node removeChild(org.w3c.dom.Node oldChild)
  {
    if (oldChild.getParentNode() != this) {
      throw new DOMException(DOMException.NOT_FOUND_ERR,
                             "Child does not belong to this node");
    }

    SOAPElementImpl element = (SOAPElementImpl) oldChild;
    element._parent = null;

    if (element._previous != null)
      element._previous._next = element._next;

    if (element._next != null)
      element._next._previous = element._previous;

    element._previous = null;
    element._next = null;

    return element;
  }

  public org.w3c.dom.Node replaceChild(org.w3c.dom.Node newChild, 
                                       org.w3c.dom.Node oldChild)
  {
    throw new UnsupportedOperationException();
  }

  public void setNodeValue(String nodeValue)
  {
  }

  public void setPrefix(String prefix)
  {
  }

  public void setTextContent(String textContent)
  {
    throw new UnsupportedOperationException();
  }

  protected class NodeListImpl 
    implements NodeList 
  {
    public int getLength()
    {
      int length = 0; 

      for (org.w3c.dom.Node node = _firstChild; 
           node != null; 
           node = node.getNextSibling()) 
        length++;

      return length;
    }

    public org.w3c.dom.Node item(int i)
    {
      int j = 0;

      for (org.w3c.dom.Node node = _firstChild; 
           node != null; 
           node = node.getNextSibling()) {
        if (i == j)
          return node;

        j++;
      }

      return null;
    }
  }
}
