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

package com.caucho.quercus.lib;

import com.caucho.quercus.env.*;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;

import java.io.IOException;
import java.util.logging.Logger;

public final class UnserializeReader {
  private static final L10N L = new L10N(UnserializeReader.class);
  private static final Logger log
    = Logger.getLogger(UnserializeReader.class.getName());

  private static final LruCache<StringKey,StringValue> _keyCache
    = new LruCache<StringKey,StringValue>(4096);

  private final char []_buffer;
  private final int _length;

  private int _index;
  private StringKey _key = new StringKey();

  public UnserializeReader(StringValue s)
  {
    _buffer = s.toCharArray();
    _length = s.length();
  }

  public UnserializeReader(String s)
  {
    _buffer = s.toCharArray();
    _length = s.length();
  }

  public Value unserialize(Env env)
    throws IOException
  {
    int ch = read();

    switch (ch) {
    case 'b':
      {
        expect(':');
        long v = readInt();
        expect(';');

        return v == 0 ? BooleanValue.FALSE : BooleanValue.TRUE;
      }

    case 's':
    case 'S':
      {
        expect(':');
        int len = (int) readInt();
        expect(':');
        expect('"');

        StringValue s = readStringValue(env, len);

        expect('"');
        expect(';');

        return s;
      }
    case 'u':
    case 'U':
      {
        expect(':');
        int len = (int) readInt();
        expect(':');
        expect('"');

        StringValue s = readUnicodeValue(env, len);

        expect('"');
        expect(';');

        return s;
      }

    case 'i':
      {
        expect(':');

        long value = readInt();

        expect(';');

        return LongValue.create(value);
      }

    case 'd':
      {
        expect(':');

        StringBuilder sb = new StringBuilder();
        for (ch = read(); ch >= 0 && ch != ';'; ch = read()) {
          sb.append((char) ch);
        }

        if (ch != ';')
          throw new IOException(L.l("expected ';'"));

        return new DoubleValue(Double.parseDouble(sb.toString()));
      }

    case 'a':
      {
        expect(':');
        int len = (int) readInt();
        expect(':');
        expect('{');
        
        ArrayValue array = new ArrayValueImpl((int) len);
        for (int i = 0; i < len; i++) {
          Value key = unserializeKey(env);
          Value value = unserialize(env);

          array.put(key, value);
        }

        expect('}');

        return array;
      }

    case 'O':
      {
        expect(':');
        int len = (int) readInt();
        expect(':');
        expect('"');

        String className = readString(len);

        expect('"');
        expect(':');
        int count = (int) readInt();
        expect(':');
        expect('{');

        QuercusClass qClass = env.findClass(className);
        Value obj;

        if (qClass != null)
          obj = qClass.callNew(env, Env.EMPTY_VALUE);
        else {
          log.fine(L.l("{0} is an undefined class in unserialize",
                   className));
          
          obj = env.createObject();
          obj.putField(env,
		       "__Quercus_Incomplete_Class_name",
		       env.createString(className));
        }
	
        for (int i = 0; i < count; i++) {
          String key = unserializeString();
          Value value = unserialize(env);

          obj.putField(env, key, value);
        }

        expect('}');

        return obj;
      }

    case 'N':
      {
        expect(';');

        return NullValue.NULL;
      }

    default:
      return BooleanValue.FALSE;
    }
  }

  public Value unserializeKey(Env env)
    throws IOException
  {
    int ch = read();

    switch (ch) {
    case 's':
      {
        expect(':');
        int len = (int) readInt();
        expect(':');
        expect('"');

        StringValue v;

        if (len < 32) {
          _key.init(_buffer, _index, len);

          v = _keyCache.get(_key);

          if (v != null) {
            _index += len;
          }
          else {
            StringKey key = new StringKey(_buffer, _index, len);

            v = readStringValue(env, len);

            _keyCache.put(key, v);
          }
        }
        else {
          v = readStringValue(env, len);
        }

        expect('"');
        expect(';');

        return v;
      }

    case 'i':
      {
        expect(':');

        long value = readInt();

        expect(';');

        return LongValue.create(value);
      }

    default:
      return BooleanValue.FALSE;
    }
  }

  private String unserializeString()
    throws IOException
  {
    expect('s');
    expect(':');
    int len = (int) readInt();
    expect(':');
    expect('"');

    String s = readString(len);

    expect('"');
    expect(';');

    return s;
  }

  public final void expect(int expectCh)
    throws IOException
  {
    if (_length <= _index)
      throw new IOException(L.l("expected '{0}' at end of string",
                                String.valueOf((char) expectCh)));

    int ch = _buffer[_index++];

    if (ch != expectCh) {
      throw new IOException(L.l("expected '{0}' at '{1}' (0x{2})",
                                String.valueOf((char) expectCh),
                                String.valueOf((char) ch),
				Integer.toHexString(ch)));
    }
  }

  public final long readInt()
  {
    int ch = read();

    long sign = 1;
    long value = 0;

    if (ch == '-') {
      sign = -1;
      ch = read();
    }
    else if (ch == '+') {
      ch = read();
    }

    for (; '0' <= ch && ch <= '9'; ch = read()) {
      value = 10 * value + ch - '0';
    }

    unread();

    return sign * value;
  }

  public final String readString(int len)
  {
    String s = new String(_buffer, _index, len);

    _index += len;

    return s;
  }

  public final StringValue readStringValue(Env env, int len)
  {
    StringValue s = env.createString(_buffer, _index, len);

    _index += len;

    return s;
  }
  
  public final StringValue readUnicodeValue(Env env, int len)
  {
    StringValue s = new UnicodeBuilderValue(_buffer, _index, len);

    _index += len;

    return s;
  }

  public final int read()
  {
    if (_index < _length)
      return _buffer[_index++];
    else
      return -1;
  }

  public final int read(char []buffer, int offset, int length)
  {
    System.arraycopy(_buffer, _index, buffer, offset, length);

    _index += length;

    return length;
  }

  public final void unread()
  {
    _index--;
  }

  public final static class StringKey {
    char []_buffer;
    int _offset;
    int _length;

    StringKey()
    {
    }

    StringKey(char []buffer, int offset, int length)
    {
      _buffer = new char[length];
      System.arraycopy(buffer, offset, _buffer, 0, length);
      _offset = 0;
      _length = length;
    }

    void init(char []buffer, int offset, int length)
    {
      _buffer = buffer;
      _offset = offset;
      _length = length;
    }

    public int hashCode()
    {
      char []buffer = _buffer;
      int offset = _offset;
      int end = offset + _length;
      int hash = 17;

      for (; offset < end; offset++)
        hash = 65521 * hash + buffer[offset];

      return hash;
    }

    public boolean equals(Object o)
    {
      if (! (o instanceof StringKey))
        return false;

      StringKey key = (StringKey) o;

      int length = _length;

      if (length != key._length)
        return false;

      char []aBuf = _buffer;
      char []bBuf = key._buffer;

      int aOffset = _offset;
      int bOffset = key._offset;

      int aEnd = aOffset + length;

      while (aOffset < aEnd) {
        if (aBuf[aOffset++] != bBuf[bOffset++])
          return false;
      }

      return true;
    }
  }
}


