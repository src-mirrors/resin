/*
 * Copyright (c) 1998-2000 Caucho Technology -- all rights reserved
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

package com.caucho.xtpdoc;

import java.io.PrintWriter;
import java.io.IOException;

import java.util.ArrayList;

import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.XMLStreamException;

public class Table extends Node implements ContentItem {
  private static int _count = 0;

  private int _myCount = _count++;
  private Document _document;
  protected String _title;
  protected int _columns = 0;
  protected ArrayList<TableRow> _rows = new ArrayList<TableRow>();

  public Table(Document document)
  {
    _document = document;
  }

  public void setTitle(String title)
  {
    _title = title;
  }

  public TableRow createTr()
  {
    TableRow row = new TableRow(_document);
    _rows.add(row);
    return row;
  }

  public void writeHtml(XMLStreamWriter out)
    throws XMLStreamException
  {
    out.writeStartElement("table");

    for (TableRow row : _rows)
      row.writeHtml(out);

    out.writeEndElement();
  }

  protected void writeRows(PrintWriter out)
    throws IOException
  {
    for (TableRow row : _rows)
      row.writeLaTeX(out);
  }

  public void writeLaTeX(PrintWriter out)
    throws IOException
  {
    for (TableRow row : _rows)
      _columns = Math.max(_columns, row.getNumberOfColumns());

    out.println("\\begin{filecontents}{ltx" + _myCount + ".tex}");
    out.print("\\begin{longtable}");

    out.print("{");

    for (int i = 0; i < _columns; i++)
      out.print("X");

    out.println("}");

    writeRows(out);

    out.println("\\end{longtable}");
    out.println("\\end{filecontents}");


    out.println("\\begin{center}");

    out.println("\\LTXtable{\\linewidth}{ltx" + _myCount + "}");

    if (_title != null)
      out.println("\\textbf{" + LaTeXUtil.escapeForLaTeX(_title) + "}");

    out.println("\\end{center}");
  }
}
