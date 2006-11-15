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

package com.caucho.log;

import java.util.ArrayList;
import java.util.Collections;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

import java.util.zip.ZipOutputStream;
import java.util.zip.GZIPOutputStream;

import java.io.OutputStream;
import java.io.IOException;

import com.caucho.loader.Environment;
import com.caucho.loader.CloseListener;

import com.caucho.config.types.Period;
import com.caucho.config.types.Bytes;
import com.caucho.config.types.InitProgram;
import com.caucho.config.ConfigException;

import com.caucho.util.*;
import com.caucho.vfs.*;

/**
 * Abstract class for a log that rolls over based on size or period.
 */
public class AbstractRolloverLog {
  protected static final L10N L = new L10N(AbstractRolloverLog.class);

  // Milliseconds in an hour
  private static final long HOUR = 3600L * 1000L;
  // Milliseconds in a day
  private static final long DAY = 24L * 3600L * 1000L;

  // Default maximum log size = 2G
  private static final long DEFAULT_ROLLOVER_SIZE = Bytes.INFINITE;
  // How often to check size
  private static final long DEFAULT_ROLLOVER_CHECK_PERIOD = 600L * 1000L;

  // prefix for the rollover
  private String _rolloverPrefix;

  // template for the archived files
  private String _archiveFormat;
  // .gz or .zip
  private String _archiveSuffix = "";
  
  // How often the logs are rolled over.
  private long _rolloverPeriod = Period.INFINITE;

  // Maximum size of the log.
  private long _rolloverSize = DEFAULT_ROLLOVER_SIZE;

  // How often the rolloverSize should be checked
  private long _rolloverCheckPeriod = DEFAULT_ROLLOVER_CHECK_PERIOD;

  // How many archives are allowed.
  private int _rolloverCount;

  private QDate _calendar = QDate.createLocal();

  private Path _pwd = Vfs.lookup();
  
  protected Path _path;

  protected String _pathFormat;

  private String _format;

  // The time of the next period-based rollover
  private long _nextPeriodEnd = -1;
  private long _nextRolloverCheckTime = -1;

  private boolean _isRollingOver;
  private Path _savedPath;
  private TempStream _tempStream;
  private ArchiveTask _archiveTask = new ArchiveTask();

  private WriteStream _os;
  private WriteStream _zipOut;

  /**
   * Returns the access-log's path.
   */
  public Path getPath()
  {
    return _path;
  }

  /**
   * Sets the access-log's path.
   */
  public void setPath(Path path)
  {
    _path = path;
  }

  /**
   * Returns the pwd for the rollover log
   */
  public Path getPwd()
  {
    return _pwd;
  }

  /**
   * Returns the formatted path
   */
  public String getPathFormat()
  {
    return _pathFormat;
  }

  /**
   * Sets the formatted path.
   */
  public void setPathFormat(String pathFormat)
    throws ConfigException
  {
    _pathFormat = pathFormat;
    
    if (pathFormat.endsWith(".zip")) {
      throw new ConfigException(L.l(".zip extension to path-format is not supported."));
    }
  }

  /**
   * Sets the archive name format
   */
  public void setArchiveFormat(String format)
  {
    if (format.endsWith(".gz")) {
      _archiveFormat = format.substring(0, format.length() - ".gz".length());
      _archiveSuffix = ".gz";
    }
    else if (format.endsWith(".zip")) {
      _archiveFormat = format.substring(0, format.length() - ".zip".length());
      _archiveSuffix = ".zip";
    }
    else {
      _archiveFormat = format;
      _archiveSuffix = "";
    }
  }

  /**
   * Sets the archive name format
   */
  public String getArchiveFormat()
  {
    if (_archiveFormat == null)
      return _rolloverPrefix + ".%Y%m%d.%H%M";
    else
      return _archiveFormat;
  }

  /**
   * Sets the log rollover period, rounded up to the nearest hour.
   *
   * @param period the new rollover period in milliseconds.
   */
  public void setRolloverPeriod(Period period)
  {
    _rolloverPeriod = period.getPeriod();
    
    if (_rolloverPeriod > 0) {
      _rolloverPeriod += 3600000L - 1;
      _rolloverPeriod -= _rolloverPeriod % 3600000L;
    }
    else
      _rolloverPeriod = Period.INFINITE;
  }

  /**
   * Sets the log rollover period, rounded up to the nearest hour.
   *
   * @return the new period in milliseconds.
   */
  public long getRolloverPeriod()
  {
    return _rolloverPeriod;
  }

  /**
   * Sets the log rollover size, rounded up to the megabyte.
   *
   * @param bytes maximum size of the log file
   */
  public void setRolloverSize(Bytes bytes)
  {
    long size = bytes.getBytes();
    
    if (size < 0)
      _rolloverSize = Bytes.INFINITE;
    else
      _rolloverSize = size;
  }

  /**
   * Sets the log rollover size, rounded up to the megabyte.
   *
   * @return maximum size of the log file
   */
  public long getRolloverSize()
  {
    return _rolloverSize;
  }

  /**
   * Sets how often the log rollover will be checked.
   *
   * @param period how often the log rollover will be checked.
   */
  public void setRolloverCheckPeriod(long period)
  {
    if (period > 1000)
      _rolloverCheckPeriod = period;
    else if (period > 0)
      _rolloverCheckPeriod = 1000;
  }

  /**
   * Sets how often the log rollover will be checked.
   *
   * @return how often the log rollover will be checked.
   */
  public long getRolloverCheckPeriod()
  {
    return _rolloverCheckPeriod;
  }

  /**
   * Sets the max rollover files.
   */
  public void setRolloverCount(int count)
  {
    _rolloverCount = count;
  }
  
  /**
   * Initialize the log.
   */
  public void init()
    throws IOException
  {
    long now = Alarm.getCurrentTime();
    
    _nextRolloverCheckTime = now + _rolloverCheckPeriod;

    Path path = getPath();

    if (path != null) {
      path.getParent().mkdirs();
    
      _rolloverPrefix = path.getTail();

      long lastModified = path.getLastModified();
      if (lastModified <= 0)
	lastModified = now;
    
      _calendar.setGMTTime(lastModified);

      _nextPeriodEnd = Period.periodEnd(lastModified, getRolloverPeriod());
    }
    else
      _nextPeriodEnd = Period.periodEnd(now, getRolloverPeriod());

    if (_nextPeriodEnd < _nextRolloverCheckTime && _nextPeriodEnd > 0)
      _nextRolloverCheckTime = _nextPeriodEnd;

    if (_archiveFormat != null || getRolloverPeriod() <= 0) {
    }
    else if (getRolloverPeriod() % DAY == 0)
      _archiveFormat = _rolloverPrefix + ".%Y%m%d";
    else if (getRolloverPeriod() % HOUR == 0)
      _archiveFormat = _rolloverPrefix + ".%Y%m%d.%H";
    else
      _archiveFormat = _rolloverPrefix + ".%Y%m%d.%H%M";

    rolloverLog(now);
  }

  public long getNextRolloverCheckTime()
  {
    if (_nextPeriodEnd < _nextRolloverCheckTime)
      return _nextPeriodEnd;
    else
      return _nextRolloverCheckTime;
  }

  public boolean isRollover()
  {
    long now = Alarm.getCurrentTime();
    
    return _nextPeriodEnd <= now || _nextRolloverCheckTime <= now;
  }

  public boolean rollover()
  {
    long now = Alarm.getCurrentTime();

    if (_nextPeriodEnd <= now || _nextRolloverCheckTime <= now) {
      rolloverLog(now);
      return true;
    }
    else
      return false;
  }

  /**
   * Writes to the underlying log.
   */
  protected void write(byte []buffer, int offset, int length)
    throws IOException
  {
    synchronized (this) {
      if (! _isRollingOver) {
	if (_os == null)
	  openLog();

	if (_os != null)
	  _os.write(buffer, offset, length);
      }
      else {
	if (_tempStream == null)
	  _tempStream = new TempStream();

	_tempStream.write(buffer, offset, length, false);
      }
    }
  }

  /**
   * Writes to the underlying log.
   */
  protected void flush()
    throws IOException
  {
    synchronized (this) {
      if (_os != null)
	_os.flush();
    }
  }

  /**
   * Check to see if we need to rollover the log.
   *
   * @param now current time in milliseconds.
   */
  protected void rolloverLog(long now)
  {
    boolean isRollingOver = false;
    
    try {
      Path savedPath = null;

      synchronized (this) {
	if (_isRollingOver || now <= _nextRolloverCheckTime)
	  return;

	_isRollingOver = isRollingOver = true;
      
	_nextRolloverCheckTime = now + _rolloverCheckPeriod;

	long lastPeriodEnd = _nextPeriodEnd;
	_nextPeriodEnd = Period.periodEnd(now, getRolloverPeriod());

	Path path = getPath();

	if (lastPeriodEnd < now) {
	  closeLogStream();
      
	  if (getPathFormat() == null) {
	    savedPath = getArchivePath(lastPeriodEnd - 1);
	  }

	  /*
	    if (log.isLoggable(Level.FINE))
	    log.fine(getPath() + ": next rollover at " +
	    QDate.formatLocal(_nextPeriodEnd));
	  */
	}
	else if (path != null && getRolloverSize() <= path.getLength()) {
	  closeLogStream();
      
	  if (getPathFormat() == null) {
	    savedPath = getArchivePath(now);
	  }
	}

	long nextPeriodEnd = _nextPeriodEnd;
	if (_nextPeriodEnd < _nextRolloverCheckTime && _nextPeriodEnd > 0)
	  _nextRolloverCheckTime = _nextPeriodEnd;
      }

      // archiving of path is outside of the synchronized block to
      // avoid freezing during archive
      if (savedPath != null) {
	_savedPath = savedPath;
	isRollingOver = false;
	ThreadPool.getThreadPool().start(_archiveTask);
	Thread.yield();
      }
    } finally {
      synchronized (this) {
	if (isRollingOver)
	  _isRollingOver = false;
      }
    }
  }

  /**
   * Tries to open the log.
   */
  private void openLog()
  {
    closeLogStream();
    
    try {
      WriteStream os = _os;
      _os = null;

      if (os != null)
	os.close();
    } catch (Throwable e) {
      // can't log in log routines
    }
      
    Path path = getPath();

    if (path == null) {
      path = getPath(Alarm.getCurrentTime());
    }
    
    try {
      if (! path.getParent().isDirectory())
	path.getParent().mkdirs();
    } catch (Throwable e) {
      logWarning(L.l("Can't create log directory {0}", path.getParent()), e);
    }

    Exception exn = null;
    
    for (int i = 0; i < 3 && _os == null; i++) {
      try {
	_os = path.openAppend();
      } catch (IOException e) {
	exn = e;
      }
    }

    String pathName = path.getPath();

    try {
      if (pathName.endsWith(".gz")) {
	_zipOut = _os;
	_os = Vfs.openWrite(new GZIPOutputStream(_zipOut));
      }
      else if (pathName.endsWith(".zip")) {
	throw new ConfigException("Can't support .zip in path-format");
      }
    } catch (Exception e) {
      if (exn == null)
	exn = e;
    }

    if (exn != null)
      logWarning(L.l("Can't create log directory {0}", path), exn);
  }

  private void movePathToArchive(Path savedPath)
  {
    if (savedPath == null)
      return;
    
    closeLogStream();
    
    Path path = getPath();
    
    String savedName = savedPath.getTail();

    logInfo(L.l("Archiving access log to {0}.", savedName));
	     
    try {
      WriteStream os = _os;
      _os = null;
      if (os != null)
	os.close();
    } catch (IOException e) {
      // can't log in log routines
    }

    try {
      if (! savedPath.getParent().isDirectory())
	savedPath.getParent().mkdirs();
    } catch (Throwable e) {
      logWarning(L.l("Can't open archive directory {0}",
		     savedPath.getParent()),
		 e);
    }
        
    try {
      WriteStream os = savedPath.openWrite();
      OutputStream out;

      if (savedName.endsWith(".gz"))
	out = new GZIPOutputStream(os);
      else if (savedName.endsWith(".zip")) 
	out = new ZipOutputStream(os);
      else
	out = os;

      try {
	path.writeToStream(out);
      } finally {
	try {
	  out.close();
	} catch (Throwable e) {
	  // can't log in log rotation routines
	}

	try {
	  if (out != os)
	    os.close();
	} catch (Throwable e) {
	  // can't log in log rotation routines
	}

	try {
	  if (! path.truncate())
	    path.remove();
	} catch (IOException e) {
	  path.remove();

	  throw e;
	}
      }
    } catch (Throwable e) {
      logWarning(L.l("Error rotating logs"), e);
    }

    if (_rolloverCount > 0)
      removeOldLogs();
  }

  /**
   * Removes logs passing the rollover count.
   */
  private void removeOldLogs()
  {
    try {
      Path path = getPath();
      Path parent = path.getParent();

      String []list = parent.list();

      ArrayList<String> matchList = new ArrayList<String>();

      Pattern archiveRegexp = getArchiveRegexp();
      for (int i = 0; i < list.length; i++) {
	Matcher matcher = archiveRegexp.matcher(list[i]);

	if (matcher.matches())
	  matchList.add(list[i]);
      }

      Collections.sort(matchList);

      if (_rolloverCount <= 0 || matchList.size() < _rolloverCount)
	return;

      for (int i = 0; i + _rolloverCount < matchList.size(); i++) {
	try {
	  parent.lookup(matchList.get(i)).remove();
	} catch (Throwable e) {
	}
      }
    } catch (Throwable e) {
    }
  }

  private Pattern getArchiveRegexp()
  {
    StringBuilder sb = new StringBuilder();

    String archiveFormat = getArchiveFormat();

    for (int i = 0; i < archiveFormat.length(); i++) {
      char ch = archiveFormat.charAt(i);

      switch (ch) {
      case '.':  case '\\': case '*': case '?': case '+':
      case '(': case ')': case '{': case '}': case '|':
	sb.append("\\");
	sb.append(ch);
	break;
      case '%':
	sb.append(".+");
	i++;
	break;
      default:
	sb.append(ch);
	break;
      }
    }

    return Pattern.compile(sb.toString());
  }

  /**
   * Returns the path of the format file
   *
   * @param time the archive date
   */
  protected Path getPath(long time)
  {
    String formatString = getPathFormat();

    if (formatString == null)
      throw new IllegalStateException(L.l("getPath requires a format path"));
    
    String pathString = getFormatName(formatString, time);

    return getPwd().lookup(pathString);
  }

  /**
   * Returns the name of the archived file
   *
   * @param time the archive date
   */
  protected Path getArchivePath(long time)
  {
    Path path = getPath();

    String archiveFormat = getArchiveFormat();

    String name = getFormatName(archiveFormat + _archiveSuffix, time);
    Path newPath = path.getParent().lookup(name);

    if (newPath.exists()) {
      if (archiveFormat.indexOf("%H") < 0)
	archiveFormat = archiveFormat + ".%H%M";
      else if (archiveFormat.indexOf("%M") < 0)
	archiveFormat = archiveFormat + ".%M";

      for (int i = 0; i < 100; i++) {
	String suffix;

	if (i == 0)
	  suffix = _archiveSuffix;
	else
	  suffix = "." + i + _archiveSuffix;
	
	name = getFormatName(archiveFormat + suffix, time);

	newPath = path.getParent().lookup(name);

	if (! newPath.exists())
	  break;
      }
    }

    return newPath;
  }

  /**
   * Returns the name of the archived file
   *
   * @param time the archive date
   */
  protected String getFormatName(String format, long time)
  {
    if (time <= 0)
      time = Alarm.getCurrentTime();
    
    if (format != null)
      return _calendar.formatLocal(time, format);
    else if (getRolloverPeriod() % (24 * 3600 * 1000L) == 0)
      return _rolloverPrefix + "." + _calendar.formatLocal(time, "%Y%m%d");
    else
      return _rolloverPrefix + "." + _calendar.formatLocal(time, "%Y%m%d.%H");
  }

  /**
   * error messages from the log itself
   */
  private void logInfo(String msg)
  {
    EnvironmentStream.logStderr(msg);
  }

  /**
   * error messages from the log itself
   */
  private void logWarning(String msg, Throwable e)
  {
    EnvironmentStream.logStderr(msg, e);
  }

  /**
   * Closes the log, flushing the results.
   */
  public synchronized void close()
    throws IOException
  {
    closeLogStream();
  }

  /**
   * Tries to close the log.
   */
  private void closeLogStream()
  {
    try {
      WriteStream os = _os;
      _os = null;

      if (os != null)
	os.close();
    } catch (Throwable e) {
      // can't log in log routines
    }

    try {
      WriteStream zipOut = _zipOut;
      _zipOut = null;

      if (zipOut != null)
	zipOut.close();
    } catch (Throwable e) {
      // can't log in log routines
    }
  }

  class ArchiveTask implements Runnable {
    public void run()
    {
      try {
	movePathToArchive(_savedPath);
      } finally {
	// Write any new data from the temp stream to the log.
	synchronized (this) {
	  if (_os == null)
	    openLog();
	
	  _isRollingOver = false;
	  TempStream ts = _tempStream;
	  _tempStream = null;

	  if (ts != null) {
	    try {
	      ReadStream is = ts.openRead();

	      try {
		is.writeToStream(_os);
	      } finally {
		is.close();
	      }
	    } catch (IOException e) {
	      e.printStackTrace();
	    }
	  }
	}
      }
    }
  }
}
