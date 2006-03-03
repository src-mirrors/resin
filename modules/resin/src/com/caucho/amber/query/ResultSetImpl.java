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
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.amber.query;

import java.util.Calendar;
import java.util.Map;
import java.util.ArrayList;

import java.io.Reader;
import java.io.InputStream;

import java.net.URL;

import java.math.BigDecimal;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Time;
import java.sql.Ref;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Array;
import java.sql.SQLWarning;
import java.sql.SQLException;

import com.caucho.util.L10N;

import com.caucho.amber.entity.AmberEntityHome;
import com.caucho.amber.entity.EntityItem;
import com.caucho.amber.entity.Entity;

import com.caucho.amber.manager.AmberConnection;

/**
 * The JDBC statement implementation.
 */
public class ResultSetImpl implements ResultSet {
  private static final L10N L = new L10N(ResultSetImpl.class);

  public static final int CACHE_CHUNK_SIZE = 64;

  private UserQuery _userQuery; 
  private ResultSet _rs;
  private ArrayList<FromItem> _fromList;
  private ArrayList<AmberExpr> _resultList;
  private AmberConnection _session;

  private QueryCacheKey _cacheKey;
  private ResultSetCacheChunk _cacheChunk;
  private boolean _isCache;

  private int _firstResult;
  private int _maxResults = Integer.MAX_VALUE / 2;
  private int _row;

  public ResultSetImpl()
  {
  }

  /**
   * Sets the user query
   */
  public void setUserQuery(UserQuery userQuery)
  {
    _userQuery = userQuery;
  }

  /**
   * Sets the result set.
   */
  public void setResultSet(ResultSet rs)
  {
    _rs = rs;
  }

  /**
   * Sets the query.
   */
  public void setQuery(SelectQuery query)
  {
    _fromList = query.getFromList();
    _resultList = query.getResultList();
  }

  /**
   * Sets the session.
   */
  public void setSession(AmberConnection aConn)
  {
    _session = aConn;
  }

  /**
   * Sets the first cache chunk
   */
  public void setCacheChunk(ResultSetCacheChunk cacheChunk)
  {
    _cacheChunk = cacheChunk;
    _isCache = true;
  }

  /**
   * Sets the first result.
   */
  public void setFirstResult(int first)
  {
    _firstResult = first;
  }

  /**
   * Sets the max result.
   */
  public void setMaxResults(int max)
  {
    if (max < 0)
      _maxResults = Integer.MAX_VALUE / 2;
    else
      _maxResults = max;
  }

  /**
   * Fills the cache chunk.
   */
  public void fillCacheChunk(ResultSetCacheChunk cacheChunk)
    throws SQLException
  {
    int size = CACHE_CHUNK_SIZE;
    int maxSize = Integer.MAX_VALUE / 2;
    int i = 0;

    ResultSetCacheChunk tail = cacheChunk;

    // max length of the cached value
    for (; maxSize-- > 0; i++) {
      if (_rs.next()) {
	if (size <= i) {
	  i = 0;
	  
	  ResultSetCacheChunk next = new ResultSetCacheChunk(tail);
	  tail.setNext(next);
	  tail = next;
	}

	tail.newRow();

	int len = _resultList.size();
	for (int j = 0; j < len; j++) {
	  int index = getColumn(j + 1);
	  
	  AmberExpr expr = _resultList.get(j);
	  
	  tail.setValue(i, j, expr.getCacheObject(_session, _rs, index));
	}
      }
      else {
	tail.setLast(true);
	return;
      }
    }

    /*
    if (! _rs.next()) {
      tail.setLast(true);
    }
    */
  }

  /**
   * Initialize
   */
  public void init()
    throws SQLException
  {
    _row = 0;
    while (_row < _firstResult && next()) {
    }
  }

  /**
   * Returns the current row number.
   */
  public int getRow()
    throws SQLException
  {
    return _rs.getRow();
  }

  /**
   * Returns true before the first row.
   */
  public boolean isBeforeFirst()
    throws SQLException
  {
    return _rs.isBeforeFirst();
  }

  /**
   * Returns true if this is the first row.
   */
  public boolean isFirst()
    throws SQLException
  {
    return _rs.isFirst();
  }

  /**
   * Returns true if this is the last row.
   */
  public boolean isLast()
    throws SQLException
  {
    return _rs.isLast();
  }

  /**
   * Returns true if this is after the last row.
   */
  public boolean isAfterLast()
    throws SQLException
  {
    return _rs.isAfterLast();
  }

  /**
   * Returns the statement for the result.
   */
  public java.sql.Statement getStatement()
    throws SQLException
  {
    return _rs.getStatement();
  }

  /**
   * Returns the metadata.
   */
  public java.sql.ResultSetMetaData getMetaData()
    throws SQLException
  {
    return _rs.getMetaData();
  }

  /**
   * Returns the warnings.
   */
  public SQLWarning getWarnings()
    throws SQLException
  {
    return _rs.getWarnings();
  }

  /**
   * Clears the warnings.
   */
  public void clearWarnings()
    throws SQLException
  {
    _rs.clearWarnings();
  }

  /**
   * Returns the cursor name.
   */
  public String getCursorName()
    throws SQLException
  {
    return _rs.getCursorName();
  }

  /**
   * Sets the fetch size.
   */
  public void setFetchSize(int size)
    throws SQLException
  {
    _rs.setFetchSize(size);
  }

  /**
   * Gets the fetch size.
   */
  public int getFetchSize()
    throws SQLException
  {
    return _rs.getFetchSize();
  }

  /**
   * Gets the fetch direction.
   */
  public int getFetchDirection()
    throws SQLException
  {
    return _rs.getFetchDirection();
  }

  /**
   * Sets the fetch direction.
   */
  public void setFetchDirection(int dir)
    throws SQLException
  {
    _rs.setFetchDirection(dir);
  }

  /**
   * Gets the concurrency.
   */
  public int getConcurrency()
    throws SQLException
  {
    return _rs.getConcurrency();
  }

  /**
   * Returns the next row.
   */
  public boolean next()
    throws SQLException
  {
    if (_firstResult + _maxResults <= _row)
      return false;

    int row = _row++;
    ResultSetCacheChunk cacheChunk = _cacheChunk;
    
    if (cacheChunk == null)
      return _rs.next();
    else if (row < cacheChunk.getRowCount()) {
      return true;
    }
    else {
      ResultSetCacheChunk next = cacheChunk.getNext();

      if (next != null) {
	_cacheChunk = next;
	return true;
      }
      
      _isCache = false;
      _cacheChunk = null;
      
      if (cacheChunk.isLast()) {
	_maxResults = 0;
	return false;
      }
      else if (_rs != null)
	return true;
      else if (_userQuery != null) {
	_rs = _userQuery.executeQuery(row, -1);

	return _rs.next();
      }
      else {
	return false;
      }
    }
  }

  /**
   * Returns the previous row.
   */
  public boolean previous()
    throws SQLException
  {
    if (_row <= _firstResult)
      return false;

    _row--;
    
    return _rs.previous();
  }

  /**
   * Move relative.
   */
  public boolean relative(int delta)
    throws SQLException
  {
    return _rs.relative(delta);
  }

  /**
   * Move absolute.
   */
  public boolean absolute(int delta)
    throws SQLException
  {
    return _rs.absolute(delta);
  }

  /**
   * Moves before the first row.
   */
  public void beforeFirst()
    throws SQLException
  {
     _rs.beforeFirst();
  }

  /**
   * Move to first
   */
  public boolean first()
    throws SQLException
  {
    return _rs.first();
  }

  /**
   * Move to last
   */
  public boolean last()
    throws SQLException
  {
    return _rs.last();
  }

  /**
   * Moves after the last row.
   */
  public void afterLast()
    throws SQLException
  {
     _rs.afterLast();
  }

  /**
   * Returns true if the last column read was null.
   */
  public boolean wasNull()
    throws SQLException
  {
    return _rs.wasNull();
  }

  /**
   * Returns the type of the last column.
   */
  public int getType()
    throws SQLException
  {
    return _rs.getType();
  }

  /**
   * Returns the external column id corresponding to the column name.
   */
  public int findColumn(String columnName)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the boolean value for the column.
   */
  public boolean getBoolean(String column)
    throws SQLException
  {
    return _rs.getBoolean(getColumn(column));
  }

  /**
   * Returns the boolean value for the column.
   */
  public boolean getBoolean(int column)
    throws SQLException
  {
    return _rs.getBoolean(getColumn(column));
  }

  /**
   * Returns the byte value for the column.
   */
  public byte getByte(String column)
    throws SQLException
  {
    return _rs.getByte(getColumn(column));
  }

  /**
   * Returns the byte value for the column.
   */
  public byte getByte(int column)
    throws SQLException
  {
    return _rs.getByte(getColumn(column));
  }

  /**
   * Returns the short value for the column.
   */
  public short getShort(String column)
    throws SQLException
  {
    return _rs.getShort(getColumn(column));
  }

  /**
   * Returns the short value for the column.
   */
  public short getShort(int column)
    throws SQLException
  {
    return _rs.getShort(getColumn(column));
  }

  /**
   * Returns the int value for the column.
   */
  public int getInt(String columnName)
    throws SQLException
  {
    int column = getColumn(columnName);
      
    if (_cacheChunk != null)
      return _cacheChunk.getInt(_row - 1, column - 1);
    else
      return _rs.getInt(column);
  }

  /**
   * Returns the int value for the column.
   */
  public int getInt(int column)
    throws SQLException
  {
    if (_cacheChunk != null)
      return _cacheChunk.getInt(_row - 1, column - 1);
    else
      return _rs.getInt(column);
  }

  /**
   * Returns the long value for the column.
   */
  public long getLong(String columnName)
    throws SQLException
  {
    int column = getColumn(columnName);
    
    if (_cacheChunk != null)
      return _cacheChunk.getLong(_row - 1, column - 1);
    else
      return _rs.getLong(column);
  }

  /**
   * Returns the long value for the column.
   */
  public long getLong(int column)
    throws SQLException
  {
    if (_cacheChunk != null)
      return _cacheChunk.getLong(_row - 1, column - 1);
    else
      return _rs.getLong(column);
  }

  /**
   * Returns the float value for the column.
   */
  public float getFloat(String column)
    throws SQLException
  {
    return _rs.getFloat(getColumn(column));
  }

  /**
   * Returns the float value for the column.
   */
  public float getFloat(int column)
    throws SQLException
  {
    return _rs.getFloat(getColumn(column));
  }

  /**
   * Returns the double value for the column.
   */
  public double getDouble(String column)
    throws SQLException
  {
    return _rs.getDouble(getColumn(column));
  }

  /**
   * Returns the double value for the column.
   */
  public double getDouble(int column)
    throws SQLException
  {
    return _rs.getDouble(getColumn(column));
  }


  /**
   * Returns the string value for the column.
   */
  public String getString(int column)
    throws SQLException
  {
    return _rs.getString(getColumn(column));
  }

  /**
   * Returns the string value for the column.
   */
  public String getString(String column)
    throws SQLException
  {
    return _rs.getString(getColumn(column));
  }
  
  /**
   * Returns the bytes value for the column.
   */
  public byte []getBytes(int column)
    throws SQLException
  {
    return _rs.getBytes(getColumn(column));
  }

  /**
   * Returns the bytes value for the column.
   */
  public byte []getBytes(String column)
    throws SQLException
  {
    return _rs.getBytes(getColumn(column));
  }

  /**
   * Returns the column value as a date.
   */
  public java.sql.Date getDate(int column)
    throws SQLException
  {
    return _rs.getDate(getColumn(column));
  }

  /**
   * Returns the column value as a date.
   */
  public java.sql.Date getDate(String column)
    throws SQLException
  {
    return _rs.getDate(getColumn(column));
  }

  /**
   * Returns the column value as a date.
   */
  public java.sql.Date getDate(int column, Calendar cal)
    throws SQLException
  {
    return _rs.getDate(getColumn(column), cal);
  }

  /**
   * Returns the column value as a date.
   */
  public java.sql.Date getDate(String column, Calendar cal)
    throws SQLException
  {
    return _rs.getDate(getColumn(column), cal);
  }

  /**
   * Returns the time value for the column.
   */
  public Time getTime(int column)
    throws SQLException
  {
    return _rs.getTime(getColumn(column));
  }

  /**
   * Returns the time value for the column.
   */
  public Time getTime(String column)
    throws SQLException
  {
    return _rs.getTime(getColumn(column));
  }

  /**
   * Returns the time value for the column.
   */
  public Time getTime(int column, Calendar cal)
    throws SQLException
  {
    return _rs.getTime(getColumn(column), cal);
  }

  /**
   * Returns the time value for the column.
   */
  public Time getTime(String column, Calendar cal)
    throws SQLException
  {
    return _rs.getTime(getColumn(column), cal);
  }

  /**
   * Returns the column as a timestamp.
   */
  public Timestamp getTimestamp(int column)
    throws SQLException
  {
    return _rs.getTimestamp(getColumn(column));
  }

  /**
   * Returns the column as a timestamp.
   */
  public Timestamp getTimestamp(String column)
    throws SQLException
  {
    return _rs.getTimestamp(getColumn(column));
  }

  /**
   * Returns the column as a timestamp.
   */
  public Timestamp getTimestamp(int column, Calendar cal)
    throws SQLException
  {
    return _rs.getTimestamp(getColumn(column), cal);
  }

  /**
   * Returns the column as a timestamp.
   */
  public Timestamp getTimestamp(String column, Calendar cal)
    throws SQLException
  {
    return _rs.getTimestamp(getColumn(column), cal);
  }

  /**
   * Returns a ref value for the column.
   */
  public Ref getRef(int column)
    throws SQLException
  {
    return _rs.getRef(getColumn(column));
  }

  /**
   * Returns a ref value for the column.
   */
  public Ref getRef(String column)
    throws SQLException
  {
    return _rs.getRef(getColumn(column));
  }

  /**
   * Returns a clob value for the column.
   */
  public Clob getClob(int column)
    throws SQLException
  {
    return _rs.getClob(getColumn(column));
  }

  /**
   * Returns a clob value for the column.
   */
  public Clob getClob(String column)
    throws SQLException
  {
    return _rs.getClob(getColumn(column));
  }

  /**
   * Returns a blob value for the column.
   */
  public Blob getBlob(int column)
    throws SQLException
  {
    return _rs.getBlob(getColumn(column));
  }

  /**
   * Returns a blob value for the column.
   */
  public Blob getBlob(String column)
    throws SQLException
  {
    return _rs.getBlob(getColumn(column));
  }

  /**
   * Returns a character stream for the column.
   */
  public Reader getCharacterStream(int column)
    throws SQLException
  {
    return _rs.getCharacterStream(getColumn(column));
  }

  /**
   * Returns a character stream for the column.
   */
  public Reader getCharacterStream(String column)
    throws SQLException
  {
    return _rs.getCharacterStream(getColumn(column));
  }

  /**
   * Returns a binary stream for the column.
   */
  public InputStream getBinaryStream(int column)
    throws SQLException
  {
    return _rs.getBinaryStream(getColumn(column));
  }

  /**
   * Returns a binary stream for the column.
   */
  public InputStream getBinaryStream(String column)
    throws SQLException
  {
    return _rs.getBinaryStream(getColumn(column));
  }

  /**
   * Returns an ascii stream for the column.
   */
  public InputStream getAsciiStream(int column)
    throws SQLException
  {
    return _rs.getAsciiStream(getColumn(column));
  }

  /**
   * Returns an ascii stream for the column.
   */
  public InputStream getAsciiStream(String column)
    throws SQLException
  {
    return _rs.getAsciiStream(getColumn(column));
  }

  /**
   * Returns a unicode stream for the column.
   */
  public InputStream getUnicodeStream(int column)
    throws SQLException
  {
    return _rs.getUnicodeStream(getColumn(column));
  }

  /**
   * Returns a unicode stream for the column.
   */
  public InputStream getUnicodeStream(String column)
    throws SQLException
  {
    return _rs.getUnicodeStream(getColumn(column));
  }

  /**
   * Returns an array value for the column.
   */
  public Array getArray(int column)
    throws SQLException
  {
    return _rs.getArray(getColumn(column));
  }

  /**
   * Returns an array value for the column.
   */
  public Array getArray(String column)
    throws SQLException
  {
    return _rs.getArray(getColumn(column));
  }

  /**
   * Returns a URL value for the column.
   */
  public URL getURL(int column)
    throws SQLException
  {
    return _rs.getURL(getColumn(column));
  }

  /**
   * Returns a URL value for the column.
   */
  public URL getURL(String column)
    throws SQLException
  {
    return _rs.getURL(getColumn(column));
  }

  /**
   * Returns a big decimal value for the column.
   */
  public BigDecimal getBigDecimal(int column)
    throws SQLException
  {
    return _rs.getBigDecimal(getColumn(column));
  }

  /**
   * Returns a big decimal value for the column.
   */
  public BigDecimal getBigDecimal(String column)
    throws SQLException
  {
    return _rs.getBigDecimal(getColumn(column));
  }

  /**
   * Returns a big decimal value for the column.
   */
  public BigDecimal getBigDecimal(int column, int digit)
    throws SQLException
  {
    return _rs.getBigDecimal(getColumn(column), digit);
  }

  /**
   * Returns a big decimal value for the column.
   */
  public BigDecimal getBigDecimal(String column, int digit)
    throws SQLException
  {
    return _rs.getBigDecimal(getColumn(column), digit);
  }

  /**
   * Returns the object value for the column.
   */
  public Object getObject(int column)
    throws SQLException
  {
    ResultSetCacheChunk cacheChunk = _cacheChunk;

    if (cacheChunk != null) {
      Object obj = cacheChunk.getObject(_row - 1, column - 1);

      if (obj instanceof EntityItem) {
	EntityItem entityItem = (EntityItem) obj;
	Entity entity = entityItem.getEntity();

	Object value = _session.loadProxy(entity.__caucho_getEntityType(),
					  entity.__caucho_getPrimaryKey());
	
	return value;
      }
      else
	return obj;
    }
    else {
      int index = getColumn(column);

      AmberExpr expr = _resultList.get(column - 1);

      Object value = expr.getObject(_session, _rs, index);

      return value;
    }
  }

  /**
   * Returns the object value for the column.
   */
  public EntityItem findEntityItem(int column)
    throws SQLException
  {
    ResultSetCacheChunk cacheChunk = _cacheChunk;

    if (cacheChunk != null) {
      Object obj = cacheChunk.getObject(_row - 1, column - 1);

      if (obj instanceof EntityItem) {
	return (EntityItem) obj;
      }
      else
	throw new SQLException(L.l("'{0}' is an unexpected type.",
				   obj));
    }
    else {
      int index = getColumn(column);

      AmberExpr expr = _resultList.get(column - 1);

      EntityItem item = expr.findItem(_session, _rs, index);

      return item;
    }
    /*
    FromItem item = _fromList.get(column - 1);
    AmberEntityHome home = item.getEntityHome();

    return home.load(_session, _rs, index);
    */
  }

  /**
   * Returns the object value for the column.
   */
  public Object getObject(String column)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the object value for the column.
   */
  public Object getKey(int column)
    throws SQLException
  {
    int index = getColumn(column);
    
    FromItem item = _fromList.get(column - 1);
    AmberEntityHome home = item.getEntityHome();

    Object key = home.getEntityType().getId().getObject(_rs, index);

    return key;
  }

  /**
   * Returns the object value for the column.
   */
  public Object getObject(int column, Map<String,Class<?>> map)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Returns the object value for the column.
   */
  public Object getObject(String column, Map<String,Class<?>> map)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a string column in the row.
   */
  public void updateString(String column, String value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a string column in the row.
   */
  public void updateString(int column, String value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an object column in the row.
   */
  public void updateObject(String column, Object value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an object column in the row.
   */
  public void updateObject(int column, Object value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an object column in the row.
   */
  public void updateObject(String column, Object value, int scale)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an object column in the row.
   */
  public void updateObject(int column, Object value, int scale)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a timestamp column in the row.
   */
  public void updateTimestamp(String column, Timestamp timestamp)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a timestamp column in the row.
   */
  public void updateTimestamp(int column, Timestamp timestamp)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a time column in the row.
   */
  public void updateTime(String column, Time time)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a time column in the row.
   */
  public void updateTime(int column, Time time)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a date column in the row.
   */
  public void updateDate(String column, java.sql.Date date)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a date column in the row.
   */
  public void updateDate(int column, java.sql.Date date)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a clob column in the row.
   */
  public void updateClob(String column, Clob clob)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a clob column in the row.
   */
  public void updateClob(int column, Clob clob)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a blob column in the row.
   */
  public void updateBlob(String column, Blob blob)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a blob column in the row.
   */
  public void updateBlob(int column, Blob blob)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an array column in the row.
   */
  public void updateArray(String column, Array array)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an array column in the row.
   */
  public void updateArray(int column, Array array)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a big decimal column in the row.
   */
  public void updateBigDecimal(String column, BigDecimal decimal)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a big decimal column in the row.
   */
  public void updateBigDecimal(int column, BigDecimal decimal)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a ref column in the row.
   */
  public void updateRef(String column, Ref ref)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a ref column in the row.
   */
  public void updateRef(int column, Ref ref)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a character stream.
   */
  public void updateCharacterStream(String column, Reader x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a character stream.
   */
  public void updateCharacterStream(int column, Reader x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a binary stream.
   */
  public void updateBinaryStream(String column, InputStream x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a binary stream.
   */
  public void updateBinaryStream(int column, InputStream x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an ascii stream.
   */
  public void updateAsciiStream(String column, InputStream x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating an ascii stream.
   */
  public void updateAsciiStream(int column, InputStream x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a unicode stream.
   */
  public void updateUnicodeStream(String column, InputStream x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating a unicode stream.
   */
  public void updateUnicodeStream(int column, InputStream x, int length)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating bytes.
   */
  public void updateBytes(String column, byte []value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating bytes.
   */
  public void updateBytes(int column, byte []value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating boolean.
   */
  public void updateBoolean(String column, boolean value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating boolean.
   */
  public void updateBoolean(int column, boolean value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating byte.
   */
  public void updateByte(String column, byte value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating byte.
   */
  public void updateByte(int column, byte value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating short.
   */
  public void updateShort(String column, short value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating short.
   */
  public void updateShort(int column, short value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating int.
   */
  public void updateInt(String column, int value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating int.
   */
  public void updateInt(int column, int value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating long.
   */
  public void updateLong(String column, long value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating long.
   */
  public void updateLong(int column, long value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating float.
   */
  public void updateFloat(String column, float value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating float.
   */
  public void updateFloat(int column, float value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating double.
   */
  public void updateDouble(String column, double value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating double.
   */
  public void updateDouble(int column, double value)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating null.
   */
  public void updateNull(String column)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating null.
   */
  public void updateNull(int column)
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * updates the row
   */
  public void updateRow()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * cancels the row updates.
   */
  public void cancelRowUpdates()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * refreshes the row
   */
  public void refreshRow()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * move to the current row
   */
  public void moveToCurrentRow()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * Updating
   */
  public boolean rowUpdated()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * True if the row was inserted.
   */
  public boolean rowInserted()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * move to insert the row
   */
  public void moveToInsertRow()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * insert the row
   */
  public void insertRow()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * True if the row was deleted.
   */
  public boolean rowDeleted()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  /**
   * delete the row
   */
  public void deleteRow()
    throws SQLException
  {
    throw new UnsupportedOperationException();
  }

  private int getColumn(String name)
  {
    throw new UnsupportedOperationException();
  }

  private int getColumn(int index)
  {
    return index;
  }

  public void close()
    throws SQLException
  {
    ResultSet rs = _rs;
    _rs = null;

    if (rs != null)
      rs.close();
  }
}
