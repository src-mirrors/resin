/*
 * Copyright (c) 1998-2005 Caucho Technology -- all rights reserved
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
 * @author Charles Reich
 */

package com.caucho.quercus.lib;

import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.module.NotNull;
import com.caucho.quercus.module.Optional;
import com.caucho.util.ByteBuffer;
import com.caucho.util.L10N;
import com.caucho.util.Log;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * PHP ZLib
 */
public class QuercusZlibModule extends AbstractQuercusModule {

  private static final Logger log = Log.open(QuercusZlibModule.class);
  private static final L10N L = new L10N(QuercusZlibModule.class);

  public static final int FORCE_GZIP = 0x1;
  public static final int FORCE_DEFLATE = 0x2;

  /**
   * Returns true for the Zlib extension.
   */
  public boolean isExtensionLoaded(String name)
  {
    return "zlib".equals(name);
  }

  /**
   *
   * @param env
   * @param fileName
   * @param mode
   * @param useIncludePath always on
   * @return ZlibClass
   */
  public Value gzopen(Env env,
                      @NotNull String fileName,
                      @NotNull String mode,
                      @Optional("0") int useIncludePath)
  {
    if (fileName == null)
      return BooleanValue.FALSE;

    ZlibClass zlib = new ZlibClass(env, fileName, mode, useIncludePath);
    return env.wrapJava(zlib);
  }

  /**
   * 
   * @param env
   * @param fileName
   * @param useIncludePath
   * @return array of uncompressed lines from fileName
   */
  public Value gzfile(Env env,
                      @NotNull String fileName,
                      @Optional("0") int useIncludePath)
    throws IOException, DataFormatException
  {
    if (fileName == null)
      return BooleanValue.FALSE;
    
    ZlibClass zlib = new ZlibClass(env,fileName,"r",useIncludePath);
    return zlib.gzfile();
  }
  
  /**
   * outputs uncompressed bytes directly to browser
   * 
   * @param env
   * @param fileName
   * @param useIncludePath
   * @return
   * @throws IOException
   * @throws DataFormatException
   */
  public boolean readgzfile(Env env,
                            @NotNull String fileName,
                            @Optional("0") int useIncludePath)
    throws IOException, DataFormatException
  {
    if (fileName == null)
      return false;
    
    ZlibClass zlib = new ZlibClass(env, fileName,"r",useIncludePath);
    env.getOut().writeStream(zlib.readgzfile());
    return true;
  }
  
  public int gzwrite(Env env,
                     @NotNull ZlibClass zp,
                     @NotNull String s,
                     @Optional("0") int length)
  {
    if ((zp == null) || (s == null) || (s == ""))
      return 0;

    return zp.gzwrite(env, s,length);
  }

  /**
   *
   * @param env
   * @param zp
   * @param s
   * @param length
   * @return alias of gzwrite
   */
  public int gzputs(Env env,
                    @NotNull ZlibClass zp,
                    @NotNull String s,
                    @Optional("0") int length)
  {
    return gzwrite(env, zp, s, length);
  }

  public boolean gzclose(@NotNull ZlibClass zp)
  {
    if (zp == null)
      return false;

    return zp.gzclose();
  }

  public boolean gzeof(@NotNull ZlibClass zp)
    throws IOException
  {
    if (zp == null)
      return false;

    return zp.gzeof();
  }

  public Value gzgetc(@NotNull ZlibClass zp)
    throws IOException, DataFormatException
  {
    if (zp == null)
      return BooleanValue.FALSE;

    return zp.gzgetc();
  }

  public Value gzread(@NotNull ZlibClass zp,
                      int length)
    throws IOException, DataFormatException
  {
    if (zp == null)
      return BooleanValue.FALSE;
    
    return zp.gzread(length);
  }
  
  public Value gzgets(@NotNull ZlibClass zp,
                      int length)
    throws IOException, DataFormatException
  {
    if (zp == null)
      return BooleanValue.FALSE;

    return zp.gzgets(length);
  }

  public Value gzgetss(@NotNull ZlibClass zp,
                       int length,
                       @Optional String allowedTags)
    throws IOException, DataFormatException
  {
    if (zp == null)
      return BooleanValue.FALSE;

    return zp.gzgetss(length,allowedTags);
  }

  public boolean gzrewind(@NotNull ZlibClass zp)
    throws IOException
  {
    if (zp == null)
      return false;
    
    return zp.gzrewind();
  }
  
  /**
   * compresses data using zlib
   * XXX: does not work, need to find a way to pass byte arrays.
   * 
   * @param data
   * @param level (default is Deflater.DEFAULT_COMPRESSION)
   * @return compressed string
   */
  public Value gzcompress(String data,
                          @Optional("-1") int level)
    throws DataFormatException
  {
    if (level == -1)
      level = Deflater.DEFAULT_COMPRESSION;

    Deflater deflater = new Deflater(level);
    byte[] input = data.getBytes();
    deflater.setInput(input);
    deflater.finish();

    byte[] output = new byte[input.length];
    ByteBuffer buf = new ByteBuffer();
    int compressedDataLength;
    int fullCompressedDataLength = 0;
    while (!deflater.finished()) {
      compressedDataLength = deflater.deflate(output);
      fullCompressedDataLength += compressedDataLength;
      buf.append(output,0,compressedDataLength);
    }
    
    return new StringValue(new String(buf.getBuffer(),0,fullCompressedDataLength));
  }

  /**
   * XXX: Does not work.  Need to find a way to pass byte arrays.
   * 
   * @param data
   * @param length (maximum length of string returned)
   * @return uncompressed string
   */
  public Value gzuncompress(String data,
                            @Optional("0") int length)
    throws DataFormatException
  {
    byte[] input = data.getBytes();
    byte[] output = new byte[input.length];
    Inflater inflater = new Inflater();
    inflater.setInput(input,0,input.length);
    int uncompressedLength = 0;
    int fullUncompressedLength = 0;
    ByteBuffer buf = new ByteBuffer();

    while(!inflater.finished()) {
      uncompressedLength = inflater.inflate(output);
      fullUncompressedLength += uncompressedLength;
      buf.append(output,0,uncompressedLength);
    }

    if (length == 0)
      length = fullUncompressedLength;
    else
      length = Math.min(fullUncompressedLength, length);

    return new StringValue(new String(output,0,length));
  }

  /**
   * XXX: Does not work.  Need to find a way to pass byte arrays
   * 
   * @param data
   * @param level
   * @return compressed using DEFLATE algorithm
   */
  public Value gzdeflate(String data,
                         @Optional("-1") int level)
   throws DataFormatException
  {
    if (level == -1)
      level = Deflater.DEFAULT_COMPRESSION;

    Deflater deflater = new Deflater(level, true);
    byte[] input = data.getBytes();
    deflater.setInput(input);
    deflater.finish();

    byte[] output = new byte[input.length];
    ByteBuffer buf = new ByteBuffer();
    int compressedDataLength;
    int fullCompressedDataLength = 0;
    while (!deflater.finished()) {
      compressedDataLength = deflater.deflate(output);
      fullCompressedDataLength += compressedDataLength;
      buf.append(output,0,compressedDataLength);
    }
    
    return new StringValue(new String(buf.getBuffer(),0,fullCompressedDataLength));
}

  /**
   * XXX: does not work.  Need to find a way to pass byte arrays
   * 
   * @param data compressed using Deflate algorithm
   * @param length (maximum length of string returned)
   * @return uncompressed string
   */
  public Value gzinflate(String data,
                         @Optional("0") int length)
    throws DataFormatException, IOException
  {
    byte[] input = data.getBytes(); 
    byte[] output = new byte[input.length];
    Inflater inflater = new Inflater(true);
    inflater.setInput(input,0,input.length);
    int uncompressedLength = 0;
    int fullUncompressedLength = 0;
    ByteBuffer buf = new ByteBuffer();

    while(!inflater.finished()) {
      uncompressedLength = inflater.inflate(output);
      fullUncompressedLength += uncompressedLength;
      buf.append(output,0,uncompressedLength);
    }

    if (length == 0)
      length = fullUncompressedLength;
    else
      length = Math.min(fullUncompressedLength, length);

    return new StringValue(new String(output,0,length));
  }

  // @todo gzencode() -- XXX Skip for now (until solve byte[] problem)
  // @todo gzpassthru()
  // @todo gzseek()
  // @todo gztell()
  // @todo zlib_get_coding_type()
}
