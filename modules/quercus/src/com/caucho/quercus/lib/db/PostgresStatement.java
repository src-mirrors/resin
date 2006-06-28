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
 * @author Rodrigo Westrupp
 */

package com.caucho.quercus.lib.db;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.LongValue;

import com.caucho.util.L10N;

import java.util.ArrayList;

import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Postgres statement class. Since Postgres has no object oriented API,
 * this is essentially a JdbcStatementResource.
 */
public class PostgresStatement extends JdbcStatementResource {
  private static final Logger log = Logger.getLogger(PostgresStatement.class.getName());
  private static final L10N L = new L10N(PostgresStatement.class);

  // Map JDBC ?,?,? to any unsorted or duplicated params.
  // Ex: INSERT INTO test VALUES($2, $1) is mapped as [0]->2, [1]->1
  //     INSERT INTO test VALUES($1, $1) is mapped as [0]->1, [0]->1
  private ArrayList<LongValue> preparedMapping = new ArrayList<LongValue>();

  /**
   * Constructor for PostgresStatement
   *
   * @param conn a Postgres connection
   */
  PostgresStatement(Postgres conn)
  {
    super(conn);
  }

  /**
   * Executes a prepared Postgres Query.
   *
   * @param env the PHP executing environment
   * @return true on success or false on failure
   */
  public boolean execute(Env env)
  {
    try {

      int size = preparedMapping.size();

      for (int i = 0; i < size; i++) {
        LongValue param = preparedMapping.get(i);
        setString(i+1, getParam(param.toInt()-1).toString());
      }

      return executeStatement();

    } catch (Exception e) {
      env.warning(L.l(e.toString()));
      log.log(Level.FINE, e.toString(), e);
      return false;
    }
  }

  /**
   * Prepares this statement with the given query.
   *
   * @param query SQL query
   * @return true on success or false on failure
   */
  public boolean prepare(String query)
  {
    try {

      preparedMapping.clear();

      // Map any unsorted or duplicated params.
      // Ex: INSERT INTO test VALUES($2, $1) or
      //     INSERT INTO test VALUES($1, $1)
      Pattern pattern = Pattern.compile("\\$([0-9]+)");
      Matcher matcher = pattern.matcher(query);
      while (matcher.find()) {
        int phpParam;
        try {
          phpParam = Integer.parseInt(matcher.group(1));
        } catch(Exception ex) {
          preparedMapping.clear();
          return false;
        }
        preparedMapping.add(LongValue.create(phpParam));
      }

      // Make the PHP query a JDBC like query
      // replacing ($1 -> ?) with question marks.
      // XXX: replace this with Matcher.appendReplacement
      // above when StringBuilder is supported.
      query = query.replaceAll("\\$[0-9]+", "?");

      // Prepare the JDBC query
      return super.prepare(query);

    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
      return false;
    }
  }
}
