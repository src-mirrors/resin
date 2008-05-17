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

package com.caucho.db.sql;

import com.caucho.log.Log;
import com.caucho.util.QDate;

import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

class ParamExpr extends Expr {
  private static final Logger log = Log.open(ParamExpr.class);
  
  private static final int NULL = 0;
  private static final int BOOLEAN = NULL + 1;
  private static final int STRING = BOOLEAN + 1;
  private static final int LONG = STRING + 1;
  private static final int DOUBLE = LONG + 1;
  private static final int DATE = DOUBLE + 1;
  private static final int BINARY = DATE + 1;
  private static final int BYTES = BINARY + 1;

  private int _index;
  
  private int _type = NULL;
  
  private String _stringValue;
  private long _longValue;
  private double _doubleValue;
  
  private InputStream _binaryStream;
  private int _streamLength;
  private byte []_bytes;

  ParamExpr(int index)
  {
    _index = index;
  }

  /**
   * Returns the type of the expression.
   */
  public Class getType()
  {
    switch (_type) {
    case NULL:
      return Object.class;

    case BOOLEAN:
      return boolean.class;

    case STRING:
      return String.class;

    case LONG:
      return long.class;

    case DOUBLE:
      return double.class;

    case DATE:
      return java.util.Date.class;

    case BINARY:
      return java.io.InputStream.class;

    case BYTES:
      return byte[].class;
      
    default:
      return Object.class;
    }
  }

  /**
   * Returns the subcost based on the given FromList.
   */
  public long subCost(ArrayList<FromItem> fromList)
  {
    return 0;
  }

  /**
   * Clers the value.
   */
  public void clear()
  {
    _type = NULL;
  }

  /**
   * Sets the value as a string.
   */
  public void setString(String value)
  {
    if (value == null)
      _type = NULL;
    else {
      _type = STRING;
      _stringValue = value;
    }
  }

  /**
   * Sets the value as a boolean.
   */
  public void setBoolean(boolean value)
  {
    _type = BOOLEAN;
    _longValue = value ? 1 : 0;
  }

  /**
   * Sets the value as a long.
   */
  public void setLong(long value)
  {
    _type = LONG;
    _longValue = value;
  }

  /**
   * Sets the value as a double.
   */
  public void setDouble(double value)
  {
    _type = DOUBLE;
    _doubleValue = value;
  }

  /**
   * Sets the value as a date.
   */
  public void setDate(long value)
  {
    _type = DATE;
    _longValue = value;
  }

  /**
   * Sets the value as a stream.
   */
  public void setBinaryStream(InputStream is, int length)
  {
    _type = BINARY;
    _binaryStream = is;
    _streamLength = length;
  }

  /**
   * Sets the value as a stream.
   */
  public void setBytes(byte []bytes)
  {
    _type = BYTES;
    _bytes = bytes;
  }

  /**
   * Checks if the value is null
   *
   * @param rows the current database tuple
   *
   * @return the string value
   */
  public boolean isNull(QueryContext context)
    throws SQLException
  {
    return _type == NULL;
  }

  /**
   * Evaluates the expression as a string.
   *
   * @param rows the current database tuple
   *
   * @return the string value
   */
  public String evalString(QueryContext context)
    throws SQLException
  {
    switch (_type) {
    case NULL:
      return null;
      
    case BOOLEAN:
      return _longValue != 0 ? "1" : "0";
      
    case STRING:
      return _stringValue;
      
    case LONG:
      return String.valueOf(_longValue);
      
    case DATE:
      return QDate.formatISO8601(_longValue);
      
    case DOUBLE:
      return String.valueOf(_doubleValue);

    default:
      throw new UnsupportedOperationException(String.valueOf(_type));
    }
  }

  /**
   * Evaluates the expression as a boolean.
   *
   * @param rows the current database tuple
   *
   * @return the boolean value
   */
  public int evalBoolean(QueryContext context)
    throws SQLException
  {
    switch (_type) {
    case NULL:
      return UNKNOWN;
      
    case BOOLEAN:
    case LONG:
      return _longValue != 0 ? TRUE : FALSE;
      
    case DOUBLE:
      return _doubleValue != 0 ? TRUE : FALSE;

    default:
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Evaluates the expression as a long.
   *
   * @param rows the current database tuple
   *
   * @return the long value
   */
  public long evalLong(QueryContext context)
    throws SQLException
  {
    switch (_type) {
    case NULL:
      return 0;
      
    case BOOLEAN:
    case LONG:
    case DATE:
      return _longValue;

    case DOUBLE:
      return (long) _doubleValue;

    case STRING:
      return Long.parseLong(_stringValue);

    default:
      throw new UnsupportedOperationException("" + _type);
    }
  }

  /**
   * Evaluates the expression as a double.
   *
   * @param rows the current database tuple
   *
   * @return the double value
   */
  public double evalDouble(QueryContext context)
    throws SQLException
  {
    switch (_type) {
    case NULL:
      return 0;
      
    case LONG:
    case DATE:
      return _longValue;

    case DOUBLE:
      return _doubleValue;

    case STRING:
      return Double.parseDouble(_stringValue);

    default:
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Evaluates the expression as a date
   *
   * @param rows the current database tuple
   *
   * @return the date value
   */
  public long evalDate(QueryContext context)
    throws SQLException
  {
    switch (_type) {
    case NULL:
      return 0;
      
    case LONG:
    case DATE:
      return _longValue;

    case DOUBLE:
      return (long) _doubleValue;

    default:
      throw new UnsupportedOperationException();
    }
  }

  /**
   * Evaluates the expression as a stream.
   *
   * @param rows the current database tuple
   *
   * @return the string value
   */
  public InputStream evalStream(QueryContext context)
    throws SQLException
  {
    switch (_type) {
    case NULL:
      return null;
      
    case BINARY:
      return _binaryStream;

    default:
      throw new UnsupportedOperationException();
    }
  }
  
  /**
   * Evaluates the expression to a buffer
   *
   * @param result the result buffer
   *
   * @return the length of the result
   */
  public int evalToBuffer(QueryContext context,
			  byte []buffer,
			  int offset)
    throws SQLException
  {
    if (_type == BYTES) {
      System.arraycopy(_bytes, 0, buffer, offset, _bytes.length);

      return _bytes.length;
    }
    else
      return evalToBuffer(context, buffer, offset, _type);
  }
  
  /**
   * Evaluates the expression to a buffer
   *
   * @param result the result buffer
   *
   * @return the length of the result
   */
  public int evalToBuffer(QueryContext context,
			  byte []buffer,
			  int offset,
			  int typecode)
    throws SQLException
  {
    if (_type == BYTES) {
      System.arraycopy(_bytes, 0, buffer, offset, _bytes.length);

      return _bytes.length;
    }
    else
      return evalToBuffer(context, buffer, offset, typecode);
  }

  public String toString()
  {
    return "?" + _index;
  }
}
