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

package com.caucho.server.connection;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.io.OutputStream;
import java.io.IOException;

import javax.servlet.ServletContext;

import com.caucho.util.L10N;

import com.caucho.vfs.WriteStream;
import com.caucho.vfs.ClientDisconnectException;

import com.caucho.log.Log;

import com.caucho.server.webapp.Application;

class ResponseStream extends ToByteResponseStream {
  static final Logger log = Log.open(ResponseStream.class);
  
  static final L10N L = new L10N(ResponseStream.class);

  private static final int _tailChunkedLength = 7;
  private static final byte []_tailChunked =
    new byte[] {'\r', '\n', '0', '\r', '\n', '\r', '\n'};
  
  private final AbstractHttpResponse _response;
  
  private WriteStream _next;
  
  private OutputStream _cacheStream;
  // used for the direct copy and caching
  private int _bufferStartOffset;
  
  private boolean _chunkedEncoding;
  
  private int _bufferSize;
  private boolean _disableAutoFlush;
  
  // bytes actually written
  private int _contentLength;
  // True for the first chunk
  private boolean _isFirst;
  private boolean _isDisconnected;
  private boolean _isCommitted;

  private boolean _allowFlush = true;
  private boolean _isHead = false;
  private boolean _isClosed = false;
  
  private final byte []_buffer = new byte[16];

  ResponseStream(AbstractHttpResponse response)
  {
    _response = response;
  }

  public void init(WriteStream next)
  {
    _next = next;
  }
  
  /**
   * initializes the Response stream at the beginning of a request.
   */
  public void start()
  {
    super.start();
    
    _chunkedEncoding = false;

    _contentLength = 0;
    _allowFlush = true;
    _disableAutoFlush = false;
    _isClosed = false;
    _isHead = false;
    _cacheStream = null;
    _isDisconnected = false;
    _isCommitted = false;
    _isFirst = true;
    _bufferStartOffset = 0;
  }

  /**
   * Returns true for a Caucho response stream.
   */
  public boolean isCauchoResponseStream()
  {
    return true;
  }

  /**
   * Sets the underlying cache stream for a cached request.
   *
   * @param cache the cache stream.
   */
  public void setByteCacheStream(OutputStream cacheStream)
  {
    _cacheStream = cacheStream;
  }

  /**
   * Response stream is a writable stream.
   */
  public boolean canWrite()
  {
    return true;
  }

  void setFlush(boolean flush)
  {
    _allowFlush = flush;
  }

  public void setAutoFlush(boolean isAutoFlush)
  {
    setDisableAutoFlush(! isAutoFlush);
  }

  void setDisableAutoFlush(boolean disable)
  {
    _disableAutoFlush = disable;
  }

  public void setHead()
  {
    _isHead = true;
    _bufferSize = 0;
  }

  public boolean isHead()
  {
    return _isHead;
  }

  public int getContentLength()
  {
    return _contentLength;
  }

  public void setBufferSize(int size)
  {
    if (isCommitted())
      throw new IllegalStateException(L.l("Buffer size cannot be set after commit"));

    super.setBufferSize(size);
  }

  public boolean isCommitted()
  {
    // jsp/17ec
    return _isCommitted || _isClosed;
  }

  public void clear()
  {
    clearBuffer();
  }
  
  public void clearBuffer()
  {
    super.clearBuffer();

    if (! _isCommitted) {
      // jsp/15la
      _isFirst = true;
      _bufferStartOffset = 0;
      _response.setHeaderWritten(false);
    }

    _next.setBufferOffset(_bufferStartOffset);
  }

  /**
   * Clear the closed state, because of the NOT_MODIFIED
   */
  public void clearClosed()
  {
    _isClosed = false;
  }

  private void writeHeaders(int length)
    throws IOException
  {
    _chunkedEncoding = _response.writeHeaders(_next, length);
  }

  /**
   * Returns the byte buffer.
   */
  public byte []getBuffer()
    throws IOException
  {
    flushBuffer();

    return _next.getBuffer();
  }

  /**
   * Returns the byte offset.
   */
  public int getBufferOffset()
    throws IOException
  {
    byte []buffer;
    int offset;

    flushBuffer();

    offset = _next.getBufferOffset();

    if (! _chunkedEncoding) {
      _bufferStartOffset = offset;
      return offset;
    }
    else if (_bufferStartOffset > 0) {
      return offset;
    }

    // chunked allocates 8 bytes for the chunk header
    buffer = _next.getBuffer();
    if (buffer.length - offset < 8) {
      _isCommitted = true;
      _next.flushBuffer();
      
      buffer = _next.getBuffer();
      offset = _next.getBufferOffset();
    }

    _bufferStartOffset = offset + 8;
    _next.setBufferOffset(offset + 8);

    return _bufferStartOffset;
  }

  /**
   * Sets the next buffer
   */
  public byte []nextBuffer(int offset)
    throws IOException
  {
    if (_isClosed)
      return _next.getBuffer();
    
    _isCommitted = true;
    
    int startOffset = _bufferStartOffset;
    _bufferStartOffset = 0;

    int length = offset - startOffset;
    long lengthHeader = _response.getContentLengthHeader();

    if (lengthHeader > 0 && lengthHeader < _contentLength + length) {
      lengthException(_next.getBuffer(), startOffset, length, lengthHeader);

      length = (int) (lengthHeader - _contentLength);
      offset = startOffset + length;
    }

    _contentLength += length;

    try {
      if (_isHead) {
	return _next.getBuffer();
      }
      else if (_chunkedEncoding) {
	if (length == 0)
	  throw new IllegalStateException();
      
	byte []buffer = _next.getBuffer();

	writeChunk(buffer, startOffset, length);

	buffer = _next.nextBuffer(offset);
	      
	if (log.isLoggable(Level.FINE))
	  log.fine("[" + dbgId() + "] write-chunk(" + offset + ")");

	_bufferStartOffset = 8 + _next.getBufferOffset();
	_next.setBufferOffset(_bufferStartOffset);

	return buffer;
      }
      else {
	if (_cacheStream != null)
	  writeCache(_next.getBuffer(), startOffset, length);
	
	byte []buffer = _next.nextBuffer(offset);
	      
	if (log.isLoggable(Level.FINE))
	  log.fine("[" + dbgId() + "] write-chunk(" + offset + ")");

	return buffer;
      }
    } catch (ClientDisconnectException e) {
      _response.killCache();

      if (_response.isIgnoreClientDisconnect()) {
        _isDisconnected = true;
	return _next.getBuffer();
      }
      else
        throw e;
    } catch (IOException e) {
      _response.killCache();
      
      throw e;
    }
  }

  /**
   * Sets the byte offset.
   */
  public void setBufferOffset(int offset)
    throws IOException
  {
    if (_isClosed)
      return;
    
    int startOffset = _bufferStartOffset;
    if (offset == startOffset)
      return;
    
    int length = offset - startOffset;
    long lengthHeader = _response.getContentLengthHeader();

    if (lengthHeader > 0 && lengthHeader < _contentLength + length) {
      lengthException(_next.getBuffer(), startOffset, length, lengthHeader);

      length = (int) (lengthHeader - _contentLength);
      offset = startOffset + length;
    }

    _contentLength += length;
    
    if (_cacheStream != null && ! _chunkedEncoding) {
      _bufferStartOffset = offset;
      writeCache(_next.getBuffer(), startOffset, length);
    }

    if (! _isHead) {
      _next.setBufferOffset(offset);
    }
  }

  /**
   * Writes the next chunk of data to the response stream.
   *
   * @param buf the buffer containing the data
   * @param offset start offset into the buffer
   * @param length length of the data in the buffer
   */
  protected void writeNext(byte []buf, int offset, int length,
			   boolean isFinished)
    throws IOException
  {
    try {
      if (_isClosed)
	return;

      if (_disableAutoFlush && ! isFinished)
	throw new IOException(L.l("auto-flushing has been disabled"));
      
      boolean isFirst = _isFirst;
      _isFirst = false;

      if (! isFirst) {
      }
      else if (isFinished)
	writeHeaders(getBufferLength());
      else
	writeHeaders(-1);

      int bufferStart = _bufferStartOffset;
      int bufferOffset = _next.getBufferOffset();

      // server/05e2
      if (length == 0 && ! isFinished && bufferStart == bufferOffset)
        return;

      long contentLengthHeader = _response.getContentLengthHeader();
      // Can't write beyond the content length
      if (0 < contentLengthHeader &&
          contentLengthHeader < length + _contentLength) {
	if (lengthException(buf, offset, length, contentLengthHeader))
	  return;

	length = (int) (contentLengthHeader - _contentLength);
      }

      if (_next != null && ! _isHead) {
	if (length > 0 && log.isLoggable(Level.FINE)) {
	  String id;
	  if (_response.getRequest() instanceof AbstractHttpRequest) {
	    Connection conn = ((AbstractHttpRequest) _response.getRequest()).getConnection();
	    if (conn != null)
	      id = String.valueOf(conn.getId());
	    else
	      id = "jni";
	  }
	  else
	    id = "inc";
        
	  log.fine("[" + id + "] chunk: " + length);
	}
	
	if (! _chunkedEncoding) {
	  byte []nextBuffer = _next.getBuffer();
	  int nextOffset = _next.getBufferOffset();

	  if (nextOffset + length < nextBuffer.length) {
	    System.arraycopy(buf, offset, nextBuffer, nextOffset, length);
	    _next.setBufferOffset(nextOffset + length);
	  }
	  else {
	    _isCommitted = true;
	    _next.write(buf, offset, length);

	    if (log.isLoggable(Level.FINE))
	      log.fine("[" + dbgId() + "] write-data(" + _tailChunkedLength + ")");
	  }

	  if (_cacheStream != null)
	    writeCache(buf, offset, length);
	}
	else {
	  byte []buffer = _next.getBuffer();
	  int writeLength = length;

	  if (bufferStart == 0 && writeLength > 0) {
	    bufferStart = bufferOffset + 8;
	    bufferOffset = bufferStart;
	  }

	  while (writeLength > 0) {
	    int sublen = buffer.length - bufferOffset;

	    if (writeLength < sublen)
	      sublen = writeLength;

	    System.arraycopy(buf, offset, buffer, bufferOffset, sublen);

	    writeLength -= sublen;
	    offset += sublen;
	    bufferOffset += sublen;

	    if (writeLength > 0) {
	      int delta = bufferOffset - bufferStart;
	      writeChunk(buffer, bufferStart, delta);
			   
	      _isCommitted = true;
	      buffer = _next.nextBuffer(bufferOffset);
	      
	      if (log.isLoggable(Level.FINE))
		log.fine("[" + dbgId() + "] write-chunk(" + bufferOffset + ")");
	      
	      bufferStart = _next.getBufferOffset() + 8;
	      bufferOffset = bufferStart;
	    }
	  }

	  _next.setBufferOffset(bufferOffset);
	  _bufferStartOffset = bufferStart;
	}
      }

      if (! _isDisconnected)
        _contentLength += length;
    } catch (ClientDisconnectException e) {
      // server/183c
      _response.killCache();

      if (_response.isIgnoreClientDisconnect())
        _isDisconnected = true;
      else {
        throw e;
      }
    }
  }
  
  private boolean lengthException(byte []buf, int offset, int length,
				  long contentLengthHeader)
  {
    if (_isDisconnected || _isHead || _isClosed) {
    }
    else if (contentLengthHeader < _contentLength) {
      CauchoRequest request = _response.getRequest();
      ServletContext app = request.getApplication();
      
      Exception exn =
	  new IllegalStateException(L.l("{0}: tried to write {1} bytes with content-length {2}.",
					request.getRequestURL(),
					"" + (length + _contentLength),
					"" + contentLengthHeader));

      if (app != null)
	app.log(exn.getMessage(), exn);
      else
	exn.printStackTrace();

      return false;
    }
    
    for (int i = (int) (offset + contentLengthHeader - _contentLength);
	 i < offset + length;
	 i++) {
      int ch = buf[i];

      if (ch != '\r' && ch != '\n' && ch != ' ' && ch != '\t') {
	CauchoRequest request = _response.getRequest();
	ServletContext app = request.getApplication();
	String graph = "";
	    
	if (Character.isLetterOrDigit((char) ch))
	  graph = "'" + (char) ch + "', ";
	    
	Exception exn =
	  new IllegalStateException(L.l("{0}: tried to write {1} bytes with content-length {2} (At {3}char={4}).",
					request.getRequestURL(),
					"" + (length + _contentLength),
					"" + contentLengthHeader,
					graph,
					"" + ch));

	if (app != null)
	  app.log(exn.getMessage(), exn);
	else
	  exn.printStackTrace();
	break;
      }
    }
        
    length = (int) (contentLengthHeader - _contentLength);
    return (length <= 0);
  }

  /**
   * Flushes the buffered response to the output stream.
   */
  public void flush()
    throws IOException
  {
    try {
      _disableAutoFlush = false;
      _isCommitted = true;
      
      if (_allowFlush && ! _isClosed) {
        flushBuffer();

	if (_chunkedEncoding) {
	  int bufferStart = _bufferStartOffset;
	  _bufferStartOffset = 0;

	  if (bufferStart > 0) {
	    int bufferOffset = _next.getBufferOffset();

	    if (bufferStart != bufferOffset) {
	      writeChunk(_next.getBuffer(), bufferStart,
			 bufferOffset - bufferStart);
	    }
	    else
	      _next.setBufferOffset(bufferStart - 8);
	  }
	}
	else {
	  // jsp/01cf
	  _bufferStartOffset = 0;
	}
	
        if (_next != null)
          _next.flush();
      }
    } catch (ClientDisconnectException e) {
      if (_response.isIgnoreClientDisconnect())
        _isDisconnected = true;
      else
        throw e;
    }
  }

  /**
   * Flushes the buffered response to the output stream.
   */
  public void flushByte()
    throws IOException
  {
    flush();
  }

  /**
   * Flushes the buffered response to the writer.
   */
  public void flushChar()
    throws IOException
  {
    flush();
  }

  /**
   * Flushes the buffered response to the output stream.
   */
  /*
  public void flushBuffer()
    throws IOException
  {
    super.flushBuffer();

    // jsp/15la
    // _isCommitted = true;
  }
  */
  
  /**
   * Complete the request.
   */
  public void finish()
    throws IOException
  {
    boolean isClosed = _isClosed;

    if (_next == null || isClosed) {
      _isClosed = true;
      return;
    }

    _disableAutoFlush = false;

    flushCharBuffer();

    _isFinished = true;
    _allowFlush = true;
    
    flushBuffer();

    int bufferStart = _bufferStartOffset;
    _bufferStartOffset = 0;
    _isClosed = true;
    
    // flushBuffer can force 304 and then a cache write which would
    // complete the finish.
    if (isClosed || _next == null) {
      return;
    }
    
    try {
      if (_chunkedEncoding) {
	int bufferOffset = _next.getBufferOffset();

	if (bufferStart > 0 && bufferOffset != bufferStart) {
	  byte []buffer = _next.getBuffer();

	  writeChunk(buffer, bufferStart, bufferOffset - bufferStart);
	}
	
	_isCommitted = true;
	_next.write(_tailChunked, 0, _tailChunkedLength);

	if (log.isLoggable(Level.FINE))
          log.fine("[" + dbgId() + "] write-chunk(" + _tailChunkedLength + ")");
      }

      CauchoRequest req = _response.getRequest();
      if (! req.allowKeepalive()) {
        if (log.isLoggable(Level.FINE)) {
          String id;
          if (req instanceof AbstractHttpRequest) {
            Connection conn = ((AbstractHttpRequest) req).getConnection();
            if (conn != null)
              id = String.valueOf(conn.getId());
            else
              id = "jni";
          }
          else
            id = "inc";
          log.fine("[" + id + "] close stream");
        }
      
        _next.close();
      }
      /*
      else if (flush) {
        //_next.flush();
        _next.flushBuffer();
      }
      */
    } catch (ClientDisconnectException e) {
      if (_response.isIgnoreClientDisconnect())
        _isDisconnected = true;
      else
        throw e;
    }
  }

  /**
   * Fills the chunk header.
   */
  private void writeChunk(byte []buffer, int start, int length)
    throws IOException
  {
    buffer[start - 8] = (byte) '\r';
    buffer[start - 7] = (byte) '\n';
    buffer[start - 6] = hexDigit(length >> 12);
    buffer[start - 5] = hexDigit(length >> 8);
    buffer[start - 4] = hexDigit(length >> 4);
    buffer[start - 3] = hexDigit(length);
    buffer[start - 2] = (byte) '\r';
    buffer[start - 1] = (byte) '\n';

    if (_cacheStream != null)
      writeCache(buffer, start, length);
  }

  /**
   * Returns the hex digit for the value.
   */
  private static byte hexDigit(int value)
  {
    value &= 0xf;

    if (value <= 9)
      return (byte) ('0' + value);
    else
      return (byte) ('a' + value - 10);
  }

  private void writeCache(byte []buf, int offset, int length)
    throws IOException
  {
    if (length == 0)
      return;
    
    CauchoRequest req = _response.getRequest();
    Application app = req.getApplication();
    if (app != null &&
	app.getCacheMaxLength() < _contentLength) {
      _cacheStream = null;
      _response.killCache();
    }
    else {
      _cacheStream.write(buf, offset, length);
    }
  }

  private String dbgId()
  {
    Object req = _response.getRequest();
    
    if (req instanceof AbstractHttpRequest) {
      Connection conn = ((AbstractHttpRequest) req).getConnection();
      if (conn != null)
	return String.valueOf(conn.getId());
      else
	return "jni";
    }
    else
      return "inc";
  }

  /**
   * Closes the stream.
   */
  public void close()
    throws IOException
  {
    finish();
  }
}
