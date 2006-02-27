package com.caucho.quercus.lib;

import com.caucho.quercus.env.NullValue;
import com.caucho.quercus.env.Value;

import org.w3c.dom.Element;

/**
 * Created by IntelliJ IDEA. User: Charles Reich Date: Feb 15, 2006 Time:
 * 2:47:14 PM To change this template use File | Settings | File Templates.
 */
public class DOMElementValue extends DOMNodeValue {

  private Element _element;
  
  public DOMElementValue(Element element)
  {
    _element = element;
  }
  
  @Override
  public Value setNodeValue(Value value)
  {
    if (_element == null)
      return NullValue.NULL;
    
    _element.setNodeValue(value.toString());
    return value;
  }
  
  //PROPERTIES
  //@todo schemaTypeInfo (boolean)
  //@todo tagName (String)

  //METHODS
  //@todo construct()
  //@todo getAttribute()
  //@todo getAttributeNode()
  //@todo getAttributeNodeNS()
  //@todo getAttributeNS()
  //@todo getElementsByTagName()
  //@todo getElementsByTagNameNS()
  //@todo hasAttribute()
  //@todo hasAttributeNS()
  //@todo removeAttribute()
  //@todo removeAttributeNode()
  //@todo removeAttributeNS()
  //@todo setAttribute()
  //@todo setAttributeNode()
  //@todo setAttributeNodeNS()
  //@todo setAttributeNS()
}
