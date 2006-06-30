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

package javax.xml.bind;

/**
 * This exception indicates that an error has occurred while performing a
 * validate operation. The ValidationEventHandler can cause this exception to
 * be thrown during the validate operations. See
 * ValidationEventHandler.handleEvent(ValidationEvent). Since: JAXB1.0 Version:
 * $Revision: 1.1 $ Author: Ryan Shoemaker, Sun Microsystems, Inc. See
 * Also:JAXBException, Validator, Serialized Form
 */
public class ValidationException extends JAXBException {

  /**
   * Construct an ValidationException with the specified detail message. The
   * errorCode and linkedException will default to null. Parameters:message - a
   * description of the exception
   */
  public ValidationException(String message)
  {
    throw new UnsupportedOperationException();
  }


  /**
   * Construct an ValidationException with the specified detail message and
   * vendor specific errorCode. The linkedException will default to null.
   * Parameters:message - a description of the exceptionerrorCode - a string
   * specifying the vendor specific error code
   */
  public ValidationException(String message, String errorCode)
  {
    throw new UnsupportedOperationException();
  }


  /**
   * Construct an ValidationException with the specified detail message, vendor
   * specific errorCode, and linkedException. Parameters:message - a
   * description of the exceptionerrorCode - a string specifying the vendor
   * specific error codeexception - the linked exception
   */
  public ValidationException(String message, String errorCode, Throwable exception)
  {
    throw new UnsupportedOperationException();
  }


  /**
   * Construct an ValidationException with the specified detail message and
   * linkedException. The errorCode will default to null. Parameters:message -
   * a description of the exceptionexception - the linked exception
   */
  public ValidationException(String message, Throwable exception)
  {
    throw new UnsupportedOperationException();
  }


  /**
   * Construct an ValidationException with a linkedException. The detail
   * message and vendor specific errorCode will default to null.
   * Parameters:exception - the linked exception
   */
  public ValidationException(Throwable exception)
  {
    throw new UnsupportedOperationException();
  }

}

