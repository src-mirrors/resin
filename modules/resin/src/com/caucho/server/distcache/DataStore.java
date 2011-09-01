/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.server.distcache;

import static java.sql.ResultSet.CONCUR_UPDATABLE;
import static java.sql.ResultSet.TYPE_FORWARD_ONLY;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.caucho.db.index.SqlIndexAlreadyExistsException;
import com.caucho.util.Alarm;
import com.caucho.util.AlarmListener;
import com.caucho.util.ConcurrentArrayList;
import com.caucho.util.FreeList;
import com.caucho.util.HashKey;
import com.caucho.util.IoUtil;
import com.caucho.util.JdbcUtil;
import com.caucho.vfs.StreamSource;
import com.caucho.vfs.WriteStream;


/**
 * Manages the backing for the file database objects
 */
public class DataStore {
  private static final Logger log
    = Logger.getLogger(DataStore.class.getName());

  private FreeList<DataConnection> _freeConn
    = new FreeList<DataConnection>(32);

  private final String _tableName;
  private final String _mnodeTableName;

  // remove unused data after 15 minutes
  // private long _expireTimeout = 60 * 60L * 1000L;
  private long _expireTimeout = 15 * 60L * 1000L;

  private DataSource _dataSource;

  private final String _insertQuery;
  private final String _loadQuery;
  private final String _dataAvailableQuery;
  private final String _updateExpiresQuery;
  private final String _updateAllExpiresQuery;
  private final String _selectOrphanQuery;
  private final String _deleteTimeoutQuery;
  private final String _validateQuery;

  private final String _countQuery;
  
  private final ConcurrentArrayList<MnodeOrphanListener> _orphanListeners
    = new ConcurrentArrayList<MnodeOrphanListener>(MnodeOrphanListener.class);

  private Alarm _alarm;

  public DataStore(String serverName,
                   MnodeStore mnodeStore)
    throws Exception
  {
    _dataSource = mnodeStore.getDataSource();
    _mnodeTableName = mnodeStore.getTableName();

    _tableName = "data";

    if (_tableName == null)
      throw new NullPointerException();

    _loadQuery = ("SELECT data"
                  + " FROM " + _tableName
                  + " WHERE id=?");

    _dataAvailableQuery = ("SELECT 1"
                           + " FROM " + _tableName
                           + " WHERE id=?");

    _insertQuery = ("INSERT into " + _tableName
                    + " (id,expire_time,data) "
                    + "VALUES(?,?,?)");

    // XXX: add random component to expire time?
    _updateExpiresQuery = ("UPDATE " + _tableName
                           + " SET expire_time=?"
                           + " WHERE id=?");

    /*
    _updateAllExpiresQuery = ("SELECT d.expire_time, d.resin_oid, m.value"
                              + " FROM " + _mnodeTableName + " AS m,"
                              + "  " + _tableName + " AS d"
                              + " WHERE m.value = d.id");
                              */
    _updateAllExpiresQuery = ("SELECT d.expire_time, d.resin_oid, m.value"
                              + " FROM " + _mnodeTableName + " AS m"
                              + " LEFT JOIN " + _tableName + " AS d"
                              + " ON(m.value = d.id)");

    _selectOrphanQuery = ("SELECT m.value, d.id"
                              + " FROM " + _mnodeTableName + " AS m"
                              + " LEFT JOIN " + _tableName + " AS d"
                              + " ON(m.value=d.id)");

    _deleteTimeoutQuery = ("DELETE FROM " + _tableName
                           + " WHERE expire_time < ?");

    _validateQuery = ("VALIDATE " + _tableName);

    _countQuery = "SELECT count(*) FROM " + _tableName;
  }

  DataSource getDataSource()
  {
    return _dataSource;
  }

  protected void init()
    throws Exception
  {
    initDatabase();

    _alarm = new Alarm(new ExpireAlarm());
    // _alarm.queue(_expireTimeout);

    _alarm.queue(0);
  }

  /**
   * Create the database, initializing if necessary.
   */
  private void initDatabase()
    throws Exception
  {
    Connection conn = _dataSource.getConnection();

    try {
      Statement stmt = conn.createStatement();

      try {
        String sql = ("SELECT id, expire_time, data"
                      + " FROM " + _tableName + " WHERE 1=0");

        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        rs.close();

        return;
      } catch (Exception e) {
        log.log(Level.FINEST, e.toString(), e);
        log.finer(this + " " + e.toString());
      }

      try {
        stmt.executeQuery("DROP TABLE " + _tableName);
      } catch (Exception e) {
        log.log(Level.FINEST, e.toString(), e);
      }

      String sql = ("CREATE TABLE " + _tableName + " (\n"
                    + "  id BINARY(32) PRIMARY KEY,\n"
                    + "  expire_time BIGINT,\n"
                    + "  data BLOB)");


      log.fine(sql);

      stmt.executeUpdate(sql);
    } finally {
      conn.close();
    }
  }
  
  public void addOrphanListener(MnodeOrphanListener listener)
  {
    _orphanListeners.add(listener);
  }
  
  public void removeOrphanListener(MnodeOrphanListener listener)
  {
    _orphanListeners.remove(listener);
  }

  /**
   * Reads the object from the data store.
   *
   * @param id the hash identifier for the data
   * @param os the WriteStream to hold the data
   *
   * @return true on successful load
   */
  public boolean load(HashKey id, WriteStream os)
  {
    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareLoad();
      pstmt.setBytes(1, id.getHash());

      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        InputStream is = rs.getBinaryStream(1);

        if (is == null)
          return false;

        try {
          os.writeStream(is);
        } finally {
          is.close();
        }

        if (log.isLoggable(Level.FINER))
          log.finer(this + " load " + id + " length:" + os.getPosition());

        return true;
      }

      if (log.isLoggable(Level.FINER))
        log.finer(this + " no data loaded for " + id);
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Reads the object from the data store.
   *
   * @param id the hash identifier for the data
   * @param os the WriteStream to hold the data
   *
   * @return true on successful load
   */
  public boolean load(HashKey id, LoadDataCallback cb)
  {
    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareLoad();
      pstmt.setBytes(1, id.getHash());

      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        InputStream is = rs.getBinaryStream(1);

        if (is == null)
          return false;

        try {
          cb.onLoad(id, is);
        } finally {
          is.close();
        }

        if (log.isLoggable(Level.FINER))
          log.finer(this + " load " + id + " " + cb);

        return true;
      }

      if (log.isLoggable(Level.FINER))
        log.finer(this + " no data loaded for " + id);
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Checks if we have the data
   *
   * @param id the hash identifier for the data
   *
   * @return true on successful load
   */
  public boolean isDataAvailable(HashKey id)
  {
    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareLoad();
      pstmt.setBytes(1, id.getHash());

      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        return true;
      }
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Reads the object from the data store.
   *
   * @param id the hash identifier for the data
   * @param os the WriteStream to hold the data
   *
   * @return true on successful load
   */
  public InputStream openInputStream(HashKey id)
  {
    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareLoad();
      pstmt.setBytes(1, id.getHash());

      ResultSet rs = pstmt.executeQuery();

      if (rs.next()) {
        InputStream is = rs.getBinaryStream(1);

        InputStream dataInputStream = new DataInputStream(conn, rs, is);
        conn = null;

        return dataInputStream;
      }
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return null;
  }

  /**
   * Saves the data, returning true on success.
   *
   * @param id the object's unique id.
   * @param is the input stream to the serialized object
   * @param length the length object the serialized object
   */
  public boolean save(HashKey id, StreamSource source, int length)
    throws IOException
  {
    // try updating first to avoid the exception for an insert
    if (updateExpires(id)) {
      return true;
    }
    else if (insert(id, source.openInputStream(), length)) {
      return true;
    }
    else {
      log.warning(this + " can't save data '" + id + "'");

      return false;
    }
  }

  /**
   * Saves the data, returning true on success.
   *
   * @param id the object's unique id.
   * @param is the input stream to the serialized object
   * @param length the length object the serialized object
   */
  public boolean save(HashKey id, InputStream is, int length)
    throws IOException
  {
    // try updating first to avoid the exception for an insert
    if (updateExpires(id)) {
      return true;
    }
    else if (insert(id, is, length)) {
      return true;
    }
    else {
      log.warning(this + " can't save data '" + id + "'");

      return false;
    }
  }

  /**
   * Stores the data, returning true on success
   *
   * @param id the object's unique id.
   * @param is the input stream to the serialized object
   * @param length the length object the serialized object
   */
  private boolean insert(HashKey id, InputStream is, int length)
  {
    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement stmt = conn.prepareInsert();
      stmt.setBytes(1, id.getHash());
      stmt.setLong(2, _expireTimeout + Alarm.getCurrentTime());
      stmt.setBinaryStream(3, is, length);

      if (is == null)
        Thread.dumpStack();
      
      int count = stmt.executeUpdate();

      if (log.isLoggable(Level.FINER))
        log.finer(this + " insert " + id + " length:" + length);

      // System.out.println("INSERT: " + id);

      return count > 0;
    } catch (SqlIndexAlreadyExistsException e) {
      // the data already exists in the cache, so this is okay
      log.finer(this + " " + e.toString());
      log.log(Level.FINEST, e.toString(), e);

      return true;
    } catch (SQLException e) {
      e.printStackTrace();
      log.finer(this + " " + e.toString());
      log.log(Level.FINEST, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Updates the expires time for the data.
   *
   * @param id the hash identifier for the data
   *
   * @return true if the database contains the id
   */
  public boolean updateExpires(HashKey id)
  {
    DataConnection conn = null;

    try {
      conn = getConnection();
      PreparedStatement pstmt = conn.prepareUpdateExpires();

      long expireTime = _expireTimeout + Alarm.getCurrentTime();

      pstmt.setLong(1, expireTime);
      pstmt.setBytes(2, id.getHash());

      int count = pstmt.executeUpdate();

      if (log.isLoggable(Level.FINER))
        log.finer(this + " updateExpires " + id);

      return count > 0;
      /*
    } catch (LockTimeoutException e) {
      if (log.isLoggable(Level.FINER))
        log.log(Level.FINER, e.toString(), e);
      else
        log.info(e.toString());
      */
    } catch (SQLException e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Clears the expired data
   */
  public void removeExpiredData()
  {
    validateDatabase();

    long now = Alarm.getCurrentTime();

    updateExpire(now);
    
    // selectOrphans();

    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareDeleteTimeout();

      pstmt.setLong(1, now);

      int count = pstmt.executeUpdate();

      if (count > 0)
        log.finer(this + " expired " + count + " old data");

      // System.out.println(this + " EXPIRE: " + count);
    } catch (SQLException e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }
  }

  /**
   * Update used expire times.
   */
  private void updateExpire(long now)
  {
    DataConnection conn = null;

    boolean isValid = false;
    
    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareUpdateAllExpires();

      long expires = now + _expireTimeout;

      ResultSet rs = pstmt.executeQuery();

      try {
        while (rs.next()) {
          long oid = rs.getLong(2);
          
          if (oid > 0) {
            rs.updateLong(1, expires);
          }
          else {
            try {
              notifyOrphan(rs.getBytes(3));
            } catch (Exception e) {
              e.printStackTrace();
              log.log(Level.WARNING, e.toString(), e);
            }
          }
        }
      } finally {
        rs.close();
      }
      
      isValid = true;
    } catch (SQLException e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    } catch (Throwable e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (isValid)
        conn.close();
      else
        conn.destroy();
    }
  }
  
  private void notifyOrphan(byte []valueHash)
  {
    if (valueHash == null)
      return;
    
    for (MnodeOrphanListener listener : _orphanListeners) {
      listener.onOrphanValue(new HashKey(valueHash));
    }
  }

  /**
   * Clears the expired data
   */
  public void validateDatabase()
  {
    DataConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareValidate();

      pstmt.executeUpdate();
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }
  }

  //
  // statistics
  //

  public long getCount()
  {
    DataConnection conn = null;

    try {
      conn = getConnection();
      PreparedStatement stmt = conn.prepareCount();

      ResultSet rs = stmt.executeQuery();

      if (rs != null && rs.next()) {
        long value = rs.getLong(1);

        rs.close();

        return value;
      }

      return -1;
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return -1;
  }

  public void destroy()
  {
    _dataSource = null;
    _freeConn = null;

    Alarm alarm = _alarm;
    _alarm = null;

    if (alarm != null)
      alarm.dequeue();
  }

  private DataConnection getConnection()
    throws SQLException
  {
    DataConnection cConn = _freeConn.allocate();

    if (cConn == null) {
      Connection conn = _dataSource.getConnection();
      cConn = new DataConnection(conn);
    }

    return cConn;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() +  "[" + _tableName + "]";
  }

  class ExpireAlarm implements AlarmListener {
    public void handleAlarm(Alarm alarm)
    {
      if (_dataSource != null) {
        try {
          removeExpiredData();
        } finally {
          alarm.queue(_expireTimeout / 2);
        }
      }
    }
  }

  class DataInputStream extends InputStream {
    private DataConnection _conn;
    private ResultSet _rs;
    private InputStream _is;

    DataInputStream(DataConnection conn, ResultSet rs, InputStream is)
    {
      _conn = conn;
      _rs = rs;
      _is = is;
    }

    public int read()
      throws IOException
    {
      return _is.read();
    }

    public int read(byte []buffer, int offset, int length)
      throws IOException
    {
      return _is.read(buffer, offset, length);
    }

    public void close()
    {
      DataConnection conn = _conn;
      _conn = null;

      ResultSet rs = _rs;
      _rs = null;

      InputStream is = _is;
      _is = null;

      IoUtil.close(is);

      JdbcUtil.close(rs);

      if (conn != null)
        conn.close();
    }
  }

  class DataConnection {
    private Connection _conn;

    private PreparedStatement _loadStatement;
    private PreparedStatement _dataAvailableStatement;
    private PreparedStatement _insertStatement;
    private PreparedStatement _updateAllExpiresStatement;
    private PreparedStatement _selectOrphanStatement;
    private PreparedStatement _updateExpiresStatement;
    private PreparedStatement _deleteTimeoutStatement;
    private PreparedStatement _validateStatement;

    private PreparedStatement _countStatement;

    DataConnection(Connection conn)
    {
      _conn = conn;
    }

    PreparedStatement prepareLoad()
      throws SQLException
    {
      if (_loadStatement == null)
        _loadStatement = _conn.prepareStatement(_loadQuery);

      return _loadStatement;
    }

    PreparedStatement prepareDataAvailable()
      throws SQLException
    {
      if (_dataAvailableStatement == null)
        _dataAvailableStatement = _conn.prepareStatement(_dataAvailableQuery);

      return _dataAvailableStatement;
    }

    PreparedStatement prepareInsert()
      throws SQLException
    {
      if (_insertStatement == null)
        _insertStatement = _conn.prepareStatement(_insertQuery);

      return _insertStatement;
    }

    PreparedStatement prepareUpdateAllExpires()
      throws SQLException
    {
      if (_updateAllExpiresStatement == null)
        _updateAllExpiresStatement = _conn.prepareStatement(_updateAllExpiresQuery,
                                                            TYPE_FORWARD_ONLY,
                                                            CONCUR_UPDATABLE);

      return _updateAllExpiresStatement;
    }

    PreparedStatement prepareSelectOrphan()
      throws SQLException
    {
      if (_selectOrphanStatement == null)
        _selectOrphanStatement = _conn.prepareStatement(_selectOrphanQuery);

      return _selectOrphanStatement;
    }

    PreparedStatement prepareUpdateExpires()
      throws SQLException
    {
      if (_updateExpiresStatement == null)
        _updateExpiresStatement = _conn.prepareStatement(_updateExpiresQuery);

      return _updateExpiresStatement;
    }

    PreparedStatement prepareDeleteTimeout()
      throws SQLException
    {
      if (_deleteTimeoutStatement == null)
        _deleteTimeoutStatement = _conn.prepareStatement(_deleteTimeoutQuery);

      return _deleteTimeoutStatement;
    }

    PreparedStatement prepareValidate()
      throws SQLException
    {
      if (_validateStatement == null)
        _validateStatement = _conn.prepareStatement(_validateQuery);

      return _validateStatement;
    }

    PreparedStatement prepareCount()
      throws SQLException
    {
      if (_countStatement == null)
        _countStatement = _conn.prepareStatement(_countQuery);

      return _countStatement;
    }

    void close()
    {
      if (_freeConn == null || ! _freeConn.freeCareful(this)) {
        destroy();
      }
    }

    void destroy()
    {
      try {
        _conn.close();
      } catch (SQLException e) {
      }
    }
  }
}
