/*
 * Copyright (c) 2001-2007 Caucho Technology, Inc.  All rights reserved.
 *
 * The Apache Software License, Version 1.1
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution, if
 *    any, must include the following acknowlegement:
 *       "This product includes software developed by the
 *        Caucho Technology (http://www.caucho.com/)."
 *    Alternately, this acknowlegement may appear in the software itself,
 *    if and wherever such third-party acknowlegements normally appear.
 *
 * 4. The names "Hessian", "Resin", and "Caucho" must not be used to
 *    endorse or promote products derived from this software without prior
 *    written permission. For written permission, please contact
 *    info@caucho.com.
 *
 * 5. Products derived from this software may not be called "Resin"
 *    nor may "Resin" appear in their names without prior written
 *    permission of Caucho Technology.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL CAUCHO TECHNOLOGY OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * @author Scott Ferguson
 */

using System;
using System.IO;

/**
 * Output stream for Hessian requests.
 *
 * <pre>
 * OutputStream is = ...; // from http connection
 * HessianOutput in = new HessianInput(is);
 * String value;
 *
 * in.startReply();         // read reply header
 * value = in.readString(); // read string value
 * in.completeReply();      // read reply footer
 * </pre>
 */

namespace Hessian {

public class Hessian2Output
{
  public const int INT_DIRECT_MIN = -0x10;
  public const int INT_DIRECT_MAX = 0x2f;
  public const int INT_ZERO = 0x90;

  public const int INT_BYTE_MIN = -0x800;
  public const int INT_BYTE_MAX = 0x7ff;
  public const int INT_BYTE_ZERO = 0xc8;

  public const int INT_SHORT_MIN = -0x40000;
  public const int INT_SHORT_MAX = 0x3ffff;
  public const int INT_SHORT_ZERO = 0xd4;

  private Stream _os;

  public Hessian2Output(Stream os)
  {
    _os = os;
  }

  public void WriteBoolean(bool v)
  {
    _os.WriteByte(v ? (byte) 'T' : (byte) 'F');
  }

  public void WriteInt(int v)
  {
    if (INT_DIRECT_MIN <= v && v <= INT_DIRECT_MAX) {
      _os.WriteByte((byte) (INT_ZERO + v));
    }
    else {
      _os.WriteByte((byte) 'I');
      _os.WriteByte((byte) (v >> 24));
      _os.WriteByte((byte) (v >> 16));
      _os.WriteByte((byte) (v >> 8));
      _os.WriteByte((byte) (v));
    }
  }
}

}
