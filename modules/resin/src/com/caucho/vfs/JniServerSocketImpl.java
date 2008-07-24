/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * @author Scott Ferguson
 */

package com.caucho.vfs;

import com.caucho.config.ConfigException;
import com.caucho.util.Alarm;
import com.caucho.util.L10N;

import java.io.IOException;
import java.net.InetAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Abstract socket to handle both normal sockets and bin/resin sockets.
 */
public class JniServerSocketImpl extends QServerSocket {
  private static final L10N L = new L10N(JniServerSocketImpl.class);
  private static final Logger log
    = Logger.getLogger(JniServerSocketImpl.class.getName());
  
  private static boolean _hasInitJni;
  private long _fd;

  /**
   * Creates the new server socket.
   */
  private JniServerSocketImpl(String host, int port)
    throws IOException
  {
    _fd = bindPort(host, port);

    if (_fd == 0)
      throw new IOException(L.l("bind failed to {0}:{1}",
				host, port));
  }

  /**
   * Creates the new server socket.
   */
  private JniServerSocketImpl(int fd, int port, boolean isOpen)
    throws IOException
  {
    _fd = nativeOpenPort(fd, port);

    if (_fd == 0)
      throw new java.net.BindException(L.l("bind failed to port {0} fd {1}", port, fd));
  }

  /**
   * Returns the file descriptor to OpenSSLFactory.  No other classes
   * may use the file descriptor.
   */
  public long getFd()
  {
    return _fd;
  }
  
  public boolean isJNI()
  {
    return true;
  }

  public boolean setSaveOnExec()
  {
    return nativeSetSaveOnExec(_fd);
  }

  public int getSystemFD()
  {
    return nativeGetSystemFD(_fd);
  }

  /**
   * Sets the socket's listen backlog.
   */
  @Override
  public void listen(int backlog)
  {
    nativeListen(_fd, backlog);
  }

  public static QServerSocket create(String host, int port)
    throws IOException
  {
    if (! _hasInitJni) {
      throw new IOException(L.l("Can't use JNI to listen to port '{0}:{1}" +
				"' because JNI support has not been compiled.\n" +
				"  On Unix, run ./configure; make; make install.  On Windows, check for resin.dll.",
				host, port));
    }

    return new JniServerSocketImpl(host, port);
  }

  public static QServerSocket open(int fd, int port)
    throws IOException
  {
    if (! _hasInitJni) {
      throw new IOException(L.l("Can't open JNI port '{0}' fd '{1}'" +
				" because JNI support has not been compiled.\n" +
				"  On Unix, run ./configure; make; make install.",
				port, fd));
    }

    return new JniServerSocketImpl(fd, port, true);
  }

  /**
   * Returns the host IP for the InetAddress.
   */
  private static long inetToIP(InetAddress host)
  {
    if (host == null)
      return 0;
    
    byte []addr = host.getAddress();
    long ip = 0;

    for (int i = 0; i < addr.length; i++) {
      ip = 256 * ip + addr[i];
    }

    return ip;
  }

  /**
   * Sets the connection read timeout.
   */
  @Override
  public void setConnectionSocketTimeout(int ms)
  {
    nativeSetConnectionSocketTimeout(_fd, ms);
  }

  /**
   * Accepts a new connection from the socket.
   *
   * @param socket the socket connection structure
   *
   * @return true if the accept returns a new socket.
   */
  public boolean accept(QSocket socket)
    throws IOException
  {
    JniSocketImpl jniSocket = (JniSocketImpl) socket;
    
    if (_fd == 0)
      throw new IOException("accept from closed socket");

    if (nativeAccept(_fd, jniSocket.getFd())) {
      jniSocket.initFd();

      return true;
    }
    else
      return false;
  }

  /**
   * Factory method creating an instance socket.
   */
  public QSocket createSocket()
    throws IOException
  {
    return new JniSocketImpl();
  }

  public InetAddress getLocalAddress()
  {
    try {
      return InetAddress.getLocalHost();
    } catch (Exception e) {
      return null;
    }
  }

  public int getLocalPort()
  {
    return getLocalPort(_fd);
  }

  public boolean isClosed()
  {
    return _fd == 0;
  }

  /**
   * Closes the socket.
   */
  public void close()
    throws IOException
  {
    long fd;

    synchronized (this) {
      fd = _fd;
      _fd = 0;
    }

    if (fd != 0)
      closeNative(fd);
  }

  public void finalize()
  {
    try {
      close();
    } catch (Throwable e) {
    }
  }

  /**
   * Binds the port.
   */
  static native long bindPort(String ip, int port);

  /**
   * Open the port.
   */
  static native long nativeOpenPort(int fd, int port);

  /**
   * Sets the connection read timeout.
   */
  native void nativeSetConnectionSocketTimeout(long fd, int timeout);

  /**
   * Sets the listen backlog
   */
  native void nativeListen(long fd, int listen);

  /**
   * Accepts a new connection from the accept socket.
   */
  private native boolean nativeAccept(long fd, long connFd)
    throws IOException;

  /**
   * Returns the server's local port.
   */
  private native int getLocalPort(long fd);

  /**
   * Returns the OS file descriptor.
   */
  private native int nativeGetSystemFD(long fd);

  /**
   * Save across an exec.
   */
  private native boolean nativeSetSaveOnExec(long fd);

  /**
   * Closes the server socket.
   */
  native int closeNative(long fd)
    throws IOException;


  static {
    try {
      System.loadLibrary("resin_os");
      _hasInitJni = true;
    } catch (Throwable e) {
      log.info("Socket JNI library is not available.\nResin will still run but performance will be slower.\nTo compile the Socket JNI library on Unix, use ./configure; make; make install.");
      log.log(Level.FINER, e.toString(), e);
    }
  }
}

