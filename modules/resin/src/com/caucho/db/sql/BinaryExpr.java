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

package com.caucho.db.sql;

import com.caucho.log.Log;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Logger;

class BinaryExpr extends Expr {
  private static final Logger log = Log.open(BinaryExpr.class);

  private Expr _left;
  private Expr _right;
  private int _op;

  BinaryExpr(Expr left, Expr right, int op)
  {
    _left = left;
    _right = right;
    _op = op;
  }

  /**
   * Binds the expression to the actual tables.
   */
  protected Expr bind(Query query)
    throws SQLException
  {
    Expr newLeft = _left.bind(query);
    Expr newRight = _right.bind(query);

    switch (_op) {
    case '+':
    case '-':
    case '*':
    case '/':
    case '%':
      if (newLeft.isLong() && newRight.isLong())
	return new BinaryLongExpr(newLeft, newRight, _op);
      else
	return new BinaryDoubleExpr(newLeft, newRight, _op);
    }

    throw new SQLException("can't cope: " + newLeft + " " + newLeft.getType() + " " + newRight);
  }

  /**
   * Returns the cost based on the given FromList.
   */
  public long subCost(ArrayList<FromItem> fromList)
  {
    return _left.subCost(fromList) + _right.subCost(fromList);
  }

  /**
   * Returns the type of the expression.
   */
  public Class getType()
  {
    return Object.class;
  }

  /**
   * Evaluates aggregate functions during the group phase.
   *
   * @param state the current database tuple
   */
  public void evalGroup(QueryContext context)
    throws SQLException
  {
    _left.evalGroup(context);
    _right.evalGroup(context);
  }

  public String toString()
  {
    switch (_op) {
    case '+':
      return "(" + _left + " + " + _right + ")";

    case '-':
      return "(" + _left + " - " + _right + ")";

    case '*':
      return "(" + _left + " * " + _right + ")";

    case '/':
      return "(" + _left + " / " + _right + ")";

    case '%':
      return "(" + _left + " % " + _right + ")";

    default:
      throw new IllegalStateException("can't compare");
    }
  }
}
