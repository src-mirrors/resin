/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.quercus.marshal;

import com.caucho.bytecode.CodeWriterAttribute;
import com.caucho.quercus.expr.ExprGenerator;
import com.caucho.quercus.gen.PhpWriter;

import java.io.IOException;

public class ProIntegerMarshal extends IntegerMarshal
  implements ProMarshal
{
  public static final Marshal MARSHAL = new ProIntegerMarshal();

  public void generate(PhpWriter out, ExprGenerator expr, Class argClass)
    throws IOException
  {
    // php/33da
    out.print("(int) (");
    expr.generateLong(out);
    out.print(")");
  }

  public void generateResultStart(PhpWriter out)
    throws IOException
  {
    out.print("LongValue.create(");
  }

  public void generateResultEnd(PhpWriter out)
    throws IOException
  {
    out.print(")");
  }

  public boolean isByteCodeGenerator()
  {
    return false;
  }

  public void generateMarshal(CodeWriterAttribute code, int argIndex)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public void generateUnmarshal(CodeWriterAttribute code)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
}
