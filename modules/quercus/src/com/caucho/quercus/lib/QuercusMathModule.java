/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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

import com.caucho.util.L10N;

import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.module.Optional;

import com.caucho.quercus.env.*;

import com.caucho.util.RandomUtil;

/**
 * PHP math routines.
 */
public class QuercusMathModule extends AbstractQuercusModule {
  private static final L10N L = new L10N(QuercusMathModule.class);

  public static final double M_PI = Math.PI;
  public static final double M_E = Math.E;

  public static final long RAND_MAX = Integer.MAX_VALUE;

  public static final double M_LOG2E = log2(Math.E);
  public static final double M_LOG10E = Math.log10(Math.E);
  public static final double M_LN2 = Math.log(2);
  public static final double M_LN10 = Math.log(10);
  public static final double M_PI_2 = Math.PI / 2;
  public static final double M_PI_4 = Math.PI / 4;
  public static final double M_1_PI = 1 / Math.PI;
  public static final double M_2_PI = 2 / Math.PI;
  public static final double M_SQRTPI = Math.sqrt(Math.PI);
  public static final double M_2_SQRTPI = 2 / Math.sqrt(Math.PI);
  public static final double M_SQRT2 = Math.sqrt(2);
  public static final double M_SQRT3 = Math.sqrt(3);
  public static final double M_SQRT1_2 = 1 / Math.sqrt(2);
  public static final double M_LNPI = Math.log(Math.PI);
  public static final double M_EULER = 0.57721566490153286061;

  private static double log2(double v)
  {
    return Math.log(v) / Math.log(2);
  }

  public static Value abs(Env env, Value value)
  {
    if (value instanceof DoubleValue)
      return new DoubleValue(Math.abs(value.toDouble()));
    else
      return new LongValue(Math.abs(value.toLong()));
  }

  public static double acos(double value)
  {
    return Math.acos(value);
  }

  public static Value acosh(Env env, Value value)
  {
    throw new UnsupportedOperationException();
  }

  public static Value asin(Value value)
  {
    return new DoubleValue(Math.asin(value.toDouble()));
  }

  public static Value asinh(Value value)
  {
    throw new UnsupportedOperationException();
  }

  public static double atan2(double yV, double xV)
  {
    return Math.atan2(yV, xV);
  }

  public static double atan(double value)
  {
    return Math.atan(value);
  }

  public static Value atanh(Value value)
  {
    throw new UnsupportedOperationException();
  }

  public static Value base_convert(Env env, String number, int fromBase, int toBase)
  {
    if (fromBase < 2 || fromBase > 36) {
      env.warning(L.l("invalid `{0}' ({1})", "from base", fromBase));
      return BooleanValue.FALSE;
    }

    if (toBase < 2 || toBase > 36) {
      env.warning(L.l("invalid `{0}' ({1})", "to base", toBase));
      return BooleanValue.FALSE;
    }

    long result = 0;

    int len = number.length();

    for (int i = 0; i < len; i++) {
      int ch = number.charAt(i);

      int value;

      if ('0' <= ch && ch <= '9')
        value = ch - '0';
      else if ('a' <= ch && ch <= 'z')
        value = ch - 'a' + 10;
      else if ('A' <= ch && ch <= 'Z')
        value = ch - 'a' + 10;
      else
        continue;

      if (fromBase <= value)
        continue;

      result = result * fromBase;

      result += value;
    }

    if (result == 0)
      return new StringValue("0");

    StringBuffer sb = new StringBuffer();

    do {
      int d = (int) (result % toBase);
      result = result / toBase;

      if (d < 10)
        sb.append((char) (d + '0'));
      else
        sb.append((char) (d - 10 + 'a'));
    } while (result != 0);

    sb.reverse();

    return new StringValue(sb.toString());
  }

  /**
   * Returns the long value of an binary number.
   *
   * @param bin A string represeantion of an binary number.
   * @return the decimal equivalent of the binary number
   * @throws ArithmeticException if the binary number cannot fit in a long
   */
  public static long bindec(String bin)
  {
    // This implementation deliberately differs from the php implementation,
    // which returns an int or a float.  Quercus uses long's to represent
    // integral values and the long represenation is more accurate than a float.
    long result = 0;

    int len = bin.length();

    for (int i = 0; i < len; i++) {
      char ch = bin.charAt(i);

      if ('0' == ch)
        result = 2 * result;
      else if ('1' == ch) {
        result = 2 * result;
        result += 1;
      }

      if (result < 0)
        throw new ArithmeticException(L.l("overflow"));
    }

    return result;
  }

  public static double ceil(double value)
  {
    return Math.ceil(value);
  }

  public static double cos(double value)
  {
    return Math.cos(value);
  }

  public static double cosh(double value)
  {
    return Math.cosh(value);
  }

  /**
   * Returns a binary representation of a number.
   * @param value the number
   */
  public static String decbin(long value)
  {
    if (value == 0)
      return "0";

    StringBuilder sb = new StringBuilder();

    while (value != 0) {
      int d = (int) (value & 1);
      value = value / 2;

      if (d == 0)
        sb.append('0');
      else
        sb.append('1');
    }

    sb.reverse();

    return sb.toString();
  }

  /**
   * Returns a hexadecimal representation of a number.
   * @param value the number
   */
  public static String dechex(long value)
  {
    if (value == 0)
      return "0";

    StringBuilder sb = new StringBuilder();

    while (value != 0) {
      int d = (int) (value & 0xf);
      value = value / 16;

      if (d < 10)
        sb.append((char) (d + '0'));
      else
        sb.append((char) (d + 'a' - 10));
    }

    sb.reverse();

    return sb.toString();
  }

  /**
   * Returns an octal representation of a number.
   * @param value the number
   */
  public static String decoct(long value)
  {
    if (value == 0)
      return "0";

    StringBuilder sb = new StringBuilder();

    while (value != 0) {
      int d = (int) (value & 7);
      value = value / 8;

      sb.append((char) (d + '0'));
    }

    sb.reverse();

    return sb.toString();
  }

  public static double deg2rad(double value)
  {
    return value * Math.PI / 180;
  }

  public static Value exp(Value value)
  {
    return new DoubleValue(Math.exp(value.toDouble()));
  }

  public static Value expm1(Value value)
  {
    return new DoubleValue(Math.expm1(value.toDouble()));
  }

  public static Value floor(Value value)
  {
    return new DoubleValue(Math.floor(value.toDouble()));
  }

  public static double fmod(double xV, double yV)
  {
    return Math.IEEEremainder(xV, yV);
  }

  public static long hexdec(String s)
  {
    long v = 0;
    int len = s.length();

    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);

      if ('0' <= ch && ch <= '9')
        v = 16 * v + ch - '0';
      else if ('a' <= ch && ch <= 'f')
        v = 16 * v + ch - 'a' + 10;
      else if ('A' <= ch && ch <= 'F')
        v = 16 * v + ch - 'A' + 10;
    }

    return v;
  }

  public static double hypot(double a, double b)
  {
    return Math.hypot(a, b);
  }

  public static boolean is_finite(Value value)
  {
    if (value instanceof LongValue)
      return true;
    else if (value instanceof DoubleValue) {
      double v = value.toDouble();

      return ! Double.isInfinite(v);
    }
    else
      return false;
  }

  public static Value is_infinite(Value value)
  {
    if (value instanceof LongValue)
      return BooleanValue.FALSE;
    else if (value instanceof DoubleValue) {
      double v = value.toDouble();

      return Double.isInfinite(v) ? BooleanValue.TRUE : BooleanValue.FALSE;
    }
    else
      return BooleanValue.FALSE;
  }

  public static Value is_nan(Value value)
  {
    if (value instanceof LongValue)
      return BooleanValue.FALSE;
    else if (value instanceof DoubleValue) {
      double v = value.toDouble();

      return Double.isNaN(v) ? BooleanValue.TRUE : BooleanValue.FALSE;
    }
    else
      return BooleanValue.FALSE;
  }

  public static double log(double value)
  {
    return Math.log(value);
  }

  public static double log10(double value)
  {
    return Math.log10(value);
  }

  public static double log1p(double value)
  {
    return Math.log1p(value);
  }

  public static Value getrandmax()
  {
    return mt_getrandmax();
  }

  public static Value max(Value []args)
  {
    if (args.length == 1 && args[0] instanceof ArrayValue) {
      Value array = args[0];
      Value max = NullValue.NULL;
      double maxValue = Double.MIN_VALUE;

      for (Value key : array.getIndices()) {
        Value value = array.get(key);
        double dValue = value.toDouble();

        if (maxValue < dValue) {
          maxValue = dValue;
          max = value;
        }
      }

      return max;
    }
    else {
      double maxValue = Double.MIN_VALUE;
      Value max = NullValue.NULL;

      for (int i = 0; i < args.length; i++) {
        double value = args[i].toDouble();

        if (maxValue < value) {
          maxValue = value;
          max = args[i];
        }
      }

      return max;
    }
  }

  public static Value min(Value []args)
  {
    if (args.length == 1 && args[0] instanceof ArrayValue) {
      Value array = args[0];
      Value min = NullValue.NULL;
      double minValue = Double.MAX_VALUE;

      for (Value key : array.getIndices()) {
        Value value = array.get(key);
        double dValue = value.toDouble();

        if (dValue < minValue) {
          minValue = dValue;
          min = value;
        }
      }

      return min;
    }
    else {
      double minValue = Double.MAX_VALUE;
      Value min = NullValue.NULL;

      for (int i = 0; i < args.length; i++) {
        double value = args[i].toDouble();

        if (value < minValue) {
          minValue = value;
          min = args[i];
        }
      }

      return min;
    }
  }

  public static Value mt_getrandmax()
  {
    return new LongValue(RAND_MAX);
  }

  public static long mt_rand(@Optional("0") long min,
                             @Optional("RAND_MAX") long max)
  {
    long range = max - min + 1;

    if (range <= 0)
      return min;

    long value = RandomUtil.getRandomLong();
    if (value < 0)
      value = - value;

    return min + value % range;
  }

  public static Value mt_srand(@Optional long seed)
  {
    return NullValue.NULL;
  }

  /**
   * Returns the long value of an octal number.
   *
   * @param oct A string represeantion of an octal number.
   * @return the decimal equivalent of the octal number
   * @throws ArithmeticException if the octal number cannot fit in a long
   */
  public static long octdec(String oct)
  {
    // This implementation deliberately differs from the php implementation,
    // which returns an int or a float.  Quercus uses long's to represent
    // integral values and the long represenation is more accurate than a float.
    long result = 0;

    int len = oct.length();

    for (int i = 0; i < len; i++) {
      int ch = oct.charAt(i);

      if ('0' <= ch && ch <= '7') {
        result = result << 3;
        result += ch - '0';

        if (result < 0)
          throw new ArithmeticException(L.l("overflow"));
      }
    }

    return result;
  }


  public static double pi()
  {
    return M_PI;
  }

  public static double pow(double base, double exp)
  {
    return Math.pow(base, exp);
  }

  public static double rad2deg(double value)
  {
    return 180 * value / Math.PI;
  }

  public static long rand(@Optional int min,
			  @Optional("RAND_MAX") int max)
  {
    return mt_rand(min, max);
  }

  public static double round(double value, @Optional int precision)
  {
    if (precision > 0) {
      double exp = Math.pow(10, precision);

      return Math.round(value * exp) / exp;
    }
    else
      return Math.round(value);
  }

  public static double sin(double value)
  {
    return Math.sin(value);
  }

  public static Value sinh(Value value)
  {
    return new DoubleValue(Math.sinh(value.toDouble()));
  }

  public static double sqrt(double value)
  {
    return Math.sqrt(value);
  }

  public static Value srand(@Optional long seed)
  {
    return NullValue.NULL;
  }

  public static double tan(double value)
  {
    return Math.tan(value);
  }

  public static double tanh(double value)
  {
    return Math.tanh(value);
  }
}
