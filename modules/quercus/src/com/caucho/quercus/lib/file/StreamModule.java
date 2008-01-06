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

package com.caucho.quercus.lib.file;

import com.caucho.quercus.QuercusModuleException;
import com.caucho.quercus.UnimplementedException;
import com.caucho.quercus.annotation.NotNull;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.Reference;
import com.caucho.quercus.env.*;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.quercus.resources.StreamContextResource;
import com.caucho.util.L10N;
import com.caucho.vfs.TempBuffer;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Handling the PHP Stream API
 */
public class StreamModule extends AbstractQuercusModule {
  private static final L10N L = new L10N(StreamModule.class);
  private static final Logger log
    = Logger.getLogger(StreamModule.class.getName());

  public static final int STREAM_FILTER_READ = 1;
  public static final int STREAM_FILTER_WRITE = 2;
  public static final int STREAM_FILTER_ALL = 3;

  public static final int PSFS_PASS_ON = 2;
  public static final int PSFS_FEED_ME = 1;
  public static final int PSFS_ERR_FATAL = 0;

  public static final int STREAM_USE_PATH = 1;
  public static final int STREAM_REPORT_ERRORS = 8;

  public static final int STREAM_CLIENT_ASYNC_CONNECT = 2;
  public static final int STREAM_CLIENT_CONNECT = 4;
  public static final int STREAM_CLIENT_PERSISTENT = 1;

  public static final int STREAM_SERVER_BIND = 4;
  public static final int STREAM_SERVER_LISTEN = 8;

  public static final int STREAM_URL_STAT_LINK = 1;
  public static final int STREAM_URL_STAT_QUIET = 2;

  private static final HashMap<String,Value> _constMap 
    = new HashMap<String,Value>();

  private static final HashMap<String,ProtocolWrapper> _wrapperMap 
    = new HashMap<String,ProtocolWrapper>();

  private static final HashMap<String,ProtocolWrapper> _unregisteredWrapperMap 
    = new HashMap<String,ProtocolWrapper>();

  private static final ArrayValue _wrapperArray = new ArrayValueImpl();

  /**
   * Adds the constant to the PHP engine's constant map.
   *
   * @return the new constant chain
   */
  public Map<String,Value> getConstMap()
  {
    return _constMap;
  }

  /*
  public static void stream_bucket_append(Env env, 
                                          @NotNull StreamBucketBrigade brigade,
                                          @NotNull StreamBucket bucket)
  {
    brigade.append(bucket);
  }

  @ReturnNullAsFalse
  public static Value stream_bucket_make_writable(Env env, 
      @NotNull StreamBucketBrigade brigade)
  {
    return brigade.popTop();
  }
  */                                       

  /**
   * Creates a stream context.
   */
  public static Value stream_context_create(Env env, 
                                            @Optional ArrayValue options)
  {
    return new StreamContextResource(options);
  }

  /**
   * Returns the options from a stream context.
   */
  public static Value stream_context_get_options(Env env, Value resource)
  {
    if (resource instanceof StreamContextResource) {
      return ((StreamContextResource) resource).getOptions();
    }
    else {
      env.warning(L.l("expected resource at '{0}'", resource));

      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the default stream context.
   */
  public static Value stream_context_get_default(Env env,
                                                 @Optional ArrayValue options)
  {
    StreamContextResource context = env.getDefaultStreamContext();

    if (options != null)
      context.setOptions(options);

    return context;
  }

  /**
   * Set an options for a stream context.
   */
  public static boolean stream_context_set_option(Env env,
                                                  Value resource,
                                                  String wrapper,
                                                  String option,
                                                  Value value)
  {
    if (resource instanceof StreamContextResource) {
      StreamContextResource context = (StreamContextResource) resource;

      context.setOption(wrapper, option, value);

      return true;
    }
    else {
      env.warning(L.l("expected resource at '{0}'", resource));

      return false;
    }
  }

  /**
   * Sets parameters for the context
   */
  public static boolean stream_context_set_params(Env env,
                                                  Value resource,
                                                  ArrayValue value)
  {
    if (resource instanceof StreamContextResource) {
      StreamContextResource context = (StreamContextResource) resource;

      context.setParameters(value);

      return true;
    }
    else {
      env.warning(L.l("expected resource at '{0}'", resource));

      return false;
    }
  }

  /**
   * Copies from an input stream to an output stream
   */
  public static long stream_copy_to_stream(Env env,
                                           @NotNull BinaryInput in,
                                           @NotNull BinaryOutput out,
                                           @Optional("-1") int length,
                                           @Optional int offset)
  {
    try {
      if (in == null)
        return -1;

      if (out == null)
        return -1;

      TempBuffer temp = TempBuffer.allocate();
      byte []buffer = temp.getBuffer();

      while (offset-- > 0)
        in.read();

      if (length < 0)
        length = Integer.MAX_VALUE;

      long bytesWritten = 0;

      while (length > 0) {
        int sublen = buffer.length;

        if (length < sublen)
          sublen = (int) length;

        sublen = in.read(buffer, 0, sublen);
        if (sublen < 0)
          return bytesWritten;

        out.write(buffer, 0, sublen);

        bytesWritten += sublen;
        length -= sublen;
      }

      TempBuffer.free(temp);

      return bytesWritten;
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Returns the rest of the file as a string
   *
   * @param filename the file's name
   * @param useIncludePath if true, use the include path
   * @param context the resource context
   */
  public static Value stream_get_contents(Env env,
                                          @NotNull BinaryInput in,
                                          @Optional("-1") long maxLen,
                                          @Optional long offset)
  {
    try {
      if (in == null)
        return BooleanValue.FALSE;

      StringBuilder sb = new StringBuilder();

      int ch;

      if (maxLen < 0)
        maxLen = Integer.MAX_VALUE;

      while (offset-- > 0)
        in.read();

      while (maxLen-- > 0 && (ch = in.read()) >= 0) {
        sb.append((char) ch);
      }

      // XXX: handle offset and maxlen

      return env.createString(sb.toString());
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Returns the next line
   */
  public static Value stream_get_line(Env env,
                                      @NotNull BinaryInput file,
                                      @Optional("-1") long length)
  {
    try {
      if (file == null)
        return BooleanValue.FALSE;

      if (length < 0)
        length = Integer.MAX_VALUE;

      StringValue line = file.readLine(length);


      if (line == null)
        return BooleanValue.FALSE;

      int lineLength = line.length();
      if (lineLength == 0)
        return line;

      char tail = line.charAt(lineLength - 1);

      if (tail == '\n')
        return line.substring(0, line.length() - 1);
      else if (tail == '\r')
        return line.substring(0, line.length() - 1);
      else
        return line;
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Returns the metadata of this stream.
   * 
   * XXX: TODO
   */
  public static Value stream_get_meta_data(Env env,
                                           BinaryStream stream )
  {
    if (stream == null)
      return BooleanValue.FALSE;
    
    env.stub("stream_get_meta_data");
    
    ArrayValue array = new ArrayValueImpl();    
    array.put(env.createString("timed_out"), BooleanValue.FALSE);
    
    return array;
  }

  /**
   * Returns the available transports.
   */
  public static Value stream_get_transports(Env env)
  {
    ArrayValue value = new ArrayValueImpl();

    value.append(env.createString("tcp"));
    value.append(env.createString("udp"));

    return value;
  }

  /**
   * Returns the available wrappers.
   */
  public static Value stream_get_wrappers(Env env)
  {
    return _wrapperArray;
  }

  public static boolean stream_register_wrapper(Env env, StringValue protocol,
                                                String className)
  {
    return stream_wrapper_register(env, protocol, className);
  }

  /**
   * stream_set_blocking is stubbed since Quercus should wait for
   * any stream (unless a non-web-server Quercus is developed.)
   */
  public static boolean stream_set_blocking(Env env,
                                            @NotNull Value stream,
                                            int mode)
  {
    env.stub("stream_set_blocking()");
    
    if (stream == null)
      return false;
    else
      return true;
  }
  
  public static boolean stream_set_timeout(Env env,
                                           @NotNull Value stream,
                                           int seconds,
                                           @Optional("-1") int milliseconds)
  {
    if (stream == null)
      return false;

    Object obj = stream.toJavaObject();

    if (obj instanceof AbstractBinaryInputOutput)
      ((AbstractBinaryInputOutput) obj).setTimeout(1000L * seconds);

    return true;
  }

  /**
   * Sets the write buffer.
   */
  public static int stream_set_write_buffer(Env env, BinaryOutput stream,
                                            int bufferSize)
  {
    return 0;
  }
  
  /*
   * Opens an Internet connection.
   */
  public static Value stream_socket_client(Env env,
                                  @NotNull String remoteSocket,
                                  @Optional @Reference Value errorInt,
                                  @Optional @Reference Value errorStr,
                                  @Optional("120.0") double timeout,
                                  @Optional("STREAM_CLIENT_CONNECT") int flags,
                                  @Optional StreamContextResource context)
  {
    try {
      if (remoteSocket == null) {
        env.warning("socket to connect to must not be null");
        return BooleanValue.FALSE;
      }
      
      if (flags != STREAM_CLIENT_CONNECT) {
        env.stub("unsupported stream_socket_client flag");
      }
      
      boolean isSecure = false;
      remoteSocket = remoteSocket.trim();

      int typeIndex = remoteSocket.indexOf("://");
      
      if (typeIndex > 0) {
        String type = remoteSocket.substring(0, typeIndex);
        remoteSocket = remoteSocket.substring(typeIndex + 3);

        if (type.equals("tcp")) {
        }
        else if (type.equals("ssl")) {
          isSecure = true;
        }
        else {
          env.warning(L.l("unrecognized socket transport: {0}", type));
          
          return BooleanValue.FALSE;
        }
      }

      int colonIndex = remoteSocket.lastIndexOf(':');
      
      String host = remoteSocket;
      int port = 80;
      
      if (colonIndex > 0) {
        host = remoteSocket.substring(0, colonIndex);
        
        port = 0;
        
        for (int i = colonIndex + 1; i < remoteSocket.length(); i++) {
          char ch = remoteSocket.charAt(i);
          
          if ('0' <= ch && ch <= '9')
            port = port * 10 + ch - '0';
          else
            break;
        }
      }

      Socket socket;
      
      if (isSecure)
        socket = SSLSocketFactory.getDefault().createSocket(host, port);
      else
        socket = SocketFactory.getDefault().createSocket(host, port);

      socket.setSoTimeout((int) (timeout * 1000));

      SocketInputOutput stream
        = new SocketInputOutput(env, socket, SocketInputOutput.Domain.AF_INET);

      stream.init();

      return env.wrapJava(stream);
    }
    catch (UnknownHostException e) {
      errorStr.set(env.createString(e.getMessage()));
      
      return BooleanValue.FALSE;
    }
    catch (IOException e) {
      errorStr.set(env.createString(e.getMessage()));
      
      return BooleanValue.FALSE;
    }
  }

  public static void stream_wrapper_register(StringValue protocol, 
                                             ProtocolWrapper wrapper)
  {
    _wrapperMap.put(protocol.toString(), wrapper);
    
    _wrapperArray.append(protocol);
  }

  /**
   * Register a wrapper for a protocol.
   */
  public static boolean stream_wrapper_register(Env env, StringValue protocol,
                                                String className)
  {
    if (_wrapperMap.containsKey(protocol.toString()))
      return false;

    QuercusClass qClass = env.getClass(className);

    stream_wrapper_register(protocol, new ProtocolWrapper(qClass));
    
    return true;
  }

  /**
   * Register a wrapper for a protocol.
   */
  public static boolean stream_wrapper_restore(Env env, StringValue protocol)
  {
    if (! _unregisteredWrapperMap.containsKey(protocol.toString()))
      return false;

    ProtocolWrapper oldWrapper = 
      _unregisteredWrapperMap.remove(protocol.toString());

    stream_wrapper_register(protocol, oldWrapper);

    return true;
  }

  /**
   * Register a wrapper for a protocol.
   */
  public static boolean stream_wrapper_unregister(Env env, StringValue protocol)
  {
    if (! _wrapperMap.containsKey(protocol.toString()))
      return false;

    _unregisteredWrapperMap.put(protocol.toString(), 
                                _wrapperMap.remove(protocol.toString()));

    _wrapperArray.remove(protocol);

    return true;
  }

  protected static ProtocolWrapper getWrapper(String protocol)
  {
    return _wrapperMap.get(protocol);
  }

  static {
    _wrapperArray.append(new StringBuilderValue("quercus"));
    _wrapperArray.append(new StringBuilderValue("file"));
    _wrapperArray.append(new StringBuilderValue("http"));
    _wrapperArray.append(new StringBuilderValue("ftp"));
  }

  static {
    _constMap.put("STREAM_URL_STAT_LINK", new LongValue(STREAM_URL_STAT_LINK));
    _constMap.put("STREAM_URL_STAT_QUIET", 
                  new LongValue(STREAM_URL_STAT_QUIET));

    _constMap.put("STREAM_FILTER_READ", new LongValue(STREAM_FILTER_READ));
    _constMap.put("STREAM_FILTER_WRITE", new LongValue(STREAM_FILTER_WRITE));
    _constMap.put("STREAM_FILTER_ALL", new LongValue(STREAM_FILTER_ALL));

    _constMap.put("PSFS_PASS_ON", new LongValue(PSFS_PASS_ON));
    _constMap.put("PSFS_FEED_ME", new LongValue(PSFS_FEED_ME));
    _constMap.put("PSFS_ERR_FATAL", new LongValue(PSFS_ERR_FATAL));

    _constMap.put("STREAM_USE_PATH", new LongValue(STREAM_USE_PATH));
    _constMap.put("STREAM_REPORT_ERRORS", new LongValue(STREAM_REPORT_ERRORS));

    _constMap.put("STREAM_CLIENT_ASYNC_CONNECT",
                  new LongValue(STREAM_CLIENT_ASYNC_CONNECT));
    _constMap.put("STREAM_CLIENT_CONNECT",
                  new LongValue(STREAM_CLIENT_CONNECT));
    _constMap.put("STREAM_CLIENT_PERSISTENT",
                  new LongValue(STREAM_CLIENT_PERSISTENT));

    _constMap.put("STREAM_SERVER_BIND",
                  new LongValue(STREAM_SERVER_BIND));
    _constMap.put("STREAM_SERVER_LISTEN",
                  new LongValue(STREAM_SERVER_LISTEN));
  }
}

