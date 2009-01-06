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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.db.table;

import com.caucho.db.index.BTree;
import com.caucho.db.index.IntKeyCompare;
import com.caucho.db.index.KeyCompare;
import com.caucho.db.sql.Expr;
import com.caucho.db.sql.QueryContext;
import com.caucho.db.sql.SelectResult;
import com.caucho.db.store.Transaction;

import java.sql.SQLException;

/**
 * Represents a 32-bit integer column.
 */
class IntColumn extends Column {
  /**
   * Creates a utf-8 string column.
   *
   * @param columnOffset the offset within the row
   * @param maxLength the maximum length of the string
   */
  IntColumn(Row row, String name)
  {
    super(row, name);
  }

  /**
   * Returns the column's type code.
   */
  public int getTypeCode()
  {
    return INT;
  }

  /**
   * Returns the column's Java type.
   */
  public Class getJavaType()
  {
    return int.class;
  }
  
  /**
   * Returns the column's declaration size.
   */
  public int getDeclarationSize()
  {
    return 4;
  }

  /**
   * Returns the column's size.
   */
  public int getLength()
  {
    return 4;
  }

  /**
   * Returns the key compare for the column.
   */
  public KeyCompare getIndexKeyCompare()
  {
    return new IntKeyCompare();
  }
  
  /**
   * Returns a String value from the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   */
  public String getString(byte []block, int rowOffset)
  {
    if (isNull(block, rowOffset))
      return null;
    else
      return String.valueOf(getInteger(block, rowOffset));
  }
  
  /**
   * Sets a string value in the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param value the value to store
   */
  void setString(Transaction xa, byte []block, int rowOffset, String str)
  {
    if (str == null)
      setNull(block, rowOffset);
    else
      setInteger(xa, block, rowOffset, (int) Long.parseLong(str));
  }
  
  /**
   * Returns a int value from the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   */
  public int getInteger(byte []block, int rowOffset)
  {
    if (isNull(block, rowOffset))
      return 0;
    
    int offset = rowOffset + _columnOffset;
    int value = 0;
    
    value = (block[offset++] & 0xff) << 24;
    value |= (block[offset++] & 0xff) << 16;
    value |= (block[offset++] & 0xff) << 8;
    value |= (block[offset++] & 0xff);

    return value;
  }
  
  /**
   * Sets an integer value in the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param value the value to store
   */
  void setInteger(Transaction xa, byte []block, int rowOffset, int value)
  {
    int offset = rowOffset + _columnOffset;
    
    block[offset++] = (byte) (value >> 24);
    block[offset++] = (byte) (value >> 16);
    block[offset++] = (byte) (value >> 8);
    block[offset++] = (byte) (value);

    setNonNull(block, rowOffset);
  }

  /**
   * Sets a long value in the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param value the value to store
   */
  void setLong(Transaction xa, byte []block, int rowOffset, long value)
  {
    setInteger(xa, block, rowOffset, (int) value);
  }
  
  /**
   * Returns a long value from the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   */
  public long getLong(byte []block, int rowOffset)
  {
    return getInteger(block, rowOffset);
  }
  
  /**
   * Sets the column based on an expression.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param expr the expression to store
   */
  void setExpr(Transaction xa,
	       byte []block, int rowOffset,
	       Expr expr, QueryContext context)
    throws SQLException
  {
    if (expr.isNull(null))
      setNull(block, rowOffset);
    else
      setInteger(xa, block, rowOffset, (int) expr.evalLong(context));
  }

  /**
   * Evaluates the column to a stream.
   */
  public void evalToResult(byte []block, int rowOffset, SelectResult result)
  {
    if (isNull(block, rowOffset)) {
      result.writeNull();
      return;
    }

    int startOffset = rowOffset + _columnOffset;
    
    result.write(Column.INT);
    result.write(block, startOffset, 4);
  }
  
  /**
   * Evaluate to a buffer.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param buffer the result buffer
   * @param buffer the result buffer offset
   *
   * @return the length of the value
   */
  int evalToBuffer(byte []block, int rowOffset,
		   byte []buffer, int bufferOffset)
    throws SQLException
  {
    if (isNull(block, rowOffset))
      return 0;

    int startOffset = rowOffset + _columnOffset;
    int len = 4;

    System.arraycopy(block, startOffset, buffer, bufferOffset, len);

    return len;
  }

  /**
   * Returns true if the items in the given rows match.
   */
  public boolean isEqual(byte []block1, int rowOffset1,
			 byte []block2, int rowOffset2)
  {
    if (isNull(block1, rowOffset1) != isNull(block2, rowOffset2))
      return false;

    int startOffset1 = rowOffset1 + _columnOffset;
    int startOffset2 = rowOffset2 + _columnOffset;

    return (block1[startOffset1 + 0] == block2[startOffset2 + 0] &&
	    block1[startOffset1 + 1] == block2[startOffset2 + 1] &&
	    block1[startOffset1 + 2] == block2[startOffset2 + 2] &&
	    block1[startOffset1 + 3] == block2[startOffset2 + 3]);
  }
  
  /**
   * Sets any index for the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param rowAddr the address of the row
   */
  void setIndex(Transaction xa,
		byte []block, int rowOffset,
		long rowAddr, QueryContext context)
    throws SQLException
  {
    BTree index = getIndex();

    if (index == null)
      return;

    index.insert(block, rowOffset + _columnOffset, 4, rowAddr, xa, false);
  }

  /**
   * Sets based on an iterator.
   */
  public void set(TableIterator iter, Expr expr, QueryContext context)
    throws SQLException
  {
    iter.setDirty();
    setInteger(iter.getTransaction(),
	       iter.getBuffer(), iter.getRowOffset(),
	       (int) expr.evalLong(context));
  }
  
  /**
   * Deleting the row, based on the column.
   *
   * @param block the block's buffer
   * @param rowOffset the offset of the row in the block
   * @param expr the expression to store
   */
  @Override
  void deleteIndex(Transaction xa, byte []block, int rowOffset)
    throws SQLException
  {
    BTree index = getIndex();

    if (index != null)
      index.remove(block, rowOffset + _columnOffset, 4, xa);
  }
}
