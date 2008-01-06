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

class Row {
  // bit of the first null mask, i.e. skipping the allocation bits
  private static final int NULL_OFFSET = 2;

  private Column []_columns = new Column[0];
  private int _rowLength = 1;
  private int _nullOffset = 0;

  /**
   * Returns the current row length
   */
  int getLength()
  {
    return _rowLength;
  }

  /**
   * Returns the current null offset.
   */
  int getNullOffset()
  {
    return _nullOffset;
  }

  /**
   * Returns the current null mask.
   */
  byte getNullMask()
  {
    return (byte) (1 << ((_columns.length + NULL_OFFSET) % 8));
  }

  /**
   * Returns the columns.
   */
  Column []getColumns()
  {
    return _columns;
  }

  /**
   * Returns the named column.
   */
  Column getColumn(String name)
  {
    for (int i = 0; i < _columns.length; i++)
      if (name.equals(_columns[i].getName()))
        return _columns[i];

    return null;
  }

  /**
   * Allocates space for a column.
   */
  void allocateColumn()
  {
    if ((_columns.length + NULL_OFFSET) % 8 == 0) {
      _nullOffset = _rowLength;
      _rowLength++;
    }
  }

  /**
   * Adds a new column to the table.
   */
  Column addColumn(Column column)
  {
    Column []newColumns = new Column[_columns.length + 1];

    System.arraycopy(_columns, 0, newColumns, 0, _columns.length);
    _columns = newColumns;

    _columns[_columns.length - 1] = column;

    _rowLength += column.getLength();

    return column;
  }
}
