/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.sql.DataSource;

import com.caucho.db.jdbc.DataSourceImpl;
import com.caucho.util.CurrentTime;
import com.caucho.util.FreeList;
import com.caucho.util.HashKey;
import com.caucho.util.Hex;
import com.caucho.util.JdbcUtil;


/**
 * Manages backing for the cache map.
 */
public class MnodeStore {
  private static final Logger log
    = Logger.getLogger(MnodeStore.class.getName());

  private FreeList<CacheMapConnection> _freeConn
    = new FreeList<CacheMapConnection>(32);

  private final String _serverName;
  
  private final String _tableName;

  private DataSource _dataSource;
  private boolean _isLocalDataSource;

  private String _loadQuery;

  private String _insertQuery;
  private String _updateSaveQuery;
  private String _updateUpdateTimeQuery;

  private String _updateVersionQuery;

  private String _expireQuery;
  private String _selectExpireQuery;

  private String _countQuery;
  private String _updatesSinceQuery;
  private String _remoteUpdatesSinceQuery;
  private String _deleteQuery;

  private long _serverVersion;
  private long _startupLastUpdateTime;
  
  private AtomicLong _entryCount = new AtomicLong();
  
  // private long _expireReaperTimeout = 60 * 60 * 1000L;
  private long _expireReaperTimeout = 15 * 60 * 1000L;

  public MnodeStore(DataSource dataSource,
                    String tableName,
                    String serverName)
    throws Exception
  {
    _dataSource = dataSource;
    
    _isLocalDataSource = dataSource instanceof DataSourceImpl;
    
    _serverName = serverName;
    _tableName = tableName;

    if (dataSource == null)
      throw new NullPointerException();

    if (_tableName == null)
      throw new NullPointerException();

    _dataSource = dataSource;
  }

  /**
   * Returns the data source.
   */
  public DataSource getDataSource()
  {
    return _dataSource;
  }

  /**
   * Returns the data source.
   */
  public String getTableName()
  {
    return _tableName;
  }

  /**
   * Returns the max update time detected on startup.
   */
  public long getStartupLastUpdateTime()
  {
    return _startupLastUpdateTime;
  }

  /**
   * Returns the max update time detected on startup.
   */
  public long getStartupLastUpdateTime(HashKey cacheKey)
  {
    Connection conn = null;
    ResultSet rs = null;

    try {
      conn = _dataSource.getConnection();
      
      String sql = ("SELECT MAX(update_time)"
                    + " FROM " + _tableName
                    + " WHERE cache_id=?");
        
      PreparedStatement pStmt = conn.prepareStatement(sql);
      
      pStmt.setBytes(1, cacheKey.getHash());

      rs = pStmt.executeQuery(sql);
      
      if (rs.next()) {
        return rs.getLong(1);
      }
      
      return 0;
    } catch (SQLException e) {
      log.log(Level.WARNING, e.toString(), e);
      
      return 0;
    } finally {
      JdbcUtil.close(rs);
      JdbcUtil.close(conn);
    }
  }

  //
  // lifecycle
  //

  protected void init()
    throws Exception
  {
    _loadQuery = ("SELECT value,value_index,value_length,cache_id,flags,server_version,item_version,access_timeout,update_timeout,update_time"
                  + " FROM " + _tableName
                  + " WHERE id=?");

    _insertQuery = ("INSERT into " + _tableName
                    + " (id,value,value_index,value_length,cache_id,flags,"
                    + "  item_version,server_version,"
                    + "  access_timeout,update_timeout,"
                    + "  update_time)"
                    + " VALUES (?,?,?,?,?,?,?,?,?,?,?)");

    _updateSaveQuery
      = ("UPDATE " + _tableName
         + " SET value=?,value_index=?,value_length=?,"
         + "     server_version=?,item_version=?,"
         + "     access_timeout=?,update_time=?"
         + " WHERE id=? AND item_version<=?");

    _updateUpdateTimeQuery
      = ("UPDATE " + _tableName
         + " SET access_timeout=?,update_time=?"
         + " WHERE id=? AND item_version=?");

    _updateVersionQuery = ("UPDATE " + _tableName
                           + " SET update_time=?, server_version=?"
                           + " WHERE id=? AND value=?");

    _expireQuery = ("DELETE FROM " + _tableName
                     + " WHERE update_time + 5 * access_timeout / 4 < ?"
                     + " OR update_time + update_timeout < ?");

    _selectExpireQuery = ("SELECT id,value_index FROM " + _tableName
                          + " WHERE update_time + 5 * access_timeout / 4 < ?"
                          + " OR update_time + update_timeout < ?");

    _deleteQuery = ("DELETE FROM " + _tableName
                    + " WHERE id=?");

    _countQuery = "SELECT count(*) FROM " + _tableName;

    _updatesSinceQuery = ("SELECT id,value,value_index,value_length,cache_id,flags,item_version,update_time,access_timeout,update_timeout"
                          + " FROM " + _tableName
                          + " WHERE ? <= update_time"
                          + "   AND bitand(flags, " + CacheConfig.FLAG_TRIPLICATE + ") <> 0"
                          + " LIMIT 1024");

    _remoteUpdatesSinceQuery = ("SELECT id,value,value_index,value_length,cache_id,flags,item_version,update_time,access_timeout,update_timeout"
                                + " FROM " + _tableName
                                + " WHERE ? = cache_id AND ? <= update_time"
                                + " LIMIT 1024");

    initDatabase();

    _serverVersion = initVersion();
    _startupLastUpdateTime = initLastUpdateTime();
    
    long initCount = getCountImpl();
    
    if (initCount > 0) {
      _entryCount.set(initCount);
    }
  }

  /**
   * Create the database, initializing if necessary.
   */
  protected void initDatabase()
    throws Exception
  {
    Connection conn = _dataSource.getConnection();

    try {
      Statement stmt = conn.createStatement();

      try {
        String sql = ("SELECT id, value, value_index, value_length, cache_id, flags,"
                      + "     access_timeout, update_timeout,"
                      + "     update_time,"
                      + "     server_version, item_version"
                      + " FROM " + _tableName + " WHERE 1=0");

        ResultSet rs = stmt.executeQuery(sql);
        rs.next();
        rs.close();

        return;
      } catch (Exception e) {
        log.log(Level.ALL, e.toString(), e);
        log.finer(this + " " + e.toString());
      }

      try {
        stmt.executeQuery("DROP TABLE " + _tableName);
      } catch (Exception e) {
        log.log(Level.ALL, e.toString(), e);
        log.finer(this + " " + e.toString());
      }

      String sql = ("CREATE TABLE " + _tableName + " (\n"
                    + "  id BINARY(32) PRIMARY KEY,\n"
                    + "  value BINARY(32),\n"
                    + "  value_index BIGINT,\n"
                    + "  value_length BIGINT,\n"
                    + "  cache_id BINARY(32),\n"
                    + "  access_timeout BIGINT,\n"
                    + "  update_timeout BIGINT,\n"
                    + "  update_time BIGINT,\n"
                    + "  item_version BIGINT,\n"
                    + "  flags BIGINT,\n"
                    + "  server_version INTEGER)");

      log.fine(sql);

      stmt.executeUpdate(sql);
    } finally {
      conn.close();
    }
  }

  /**
   * Returns the version
   */
  private int initVersion()
    throws Exception
  {
    Connection conn = _dataSource.getConnection();
    ResultSet rs = null;

    try {
      Statement stmt = conn.createStatement();

      String sql = ("SELECT MAX(server_version)"
                    + " FROM " + _tableName);

      rs = stmt.executeQuery(sql);
      
      if (rs.next())
        return rs.getInt(1) + 1;
    } finally {
      JdbcUtil.close(rs);
      conn.close();
    }

    return 1;
  }

  /**
   * Returns the maximum update time on startup
   */
  private long initLastUpdateTime()
    throws Exception
  {
    Connection conn = _dataSource.getConnection();
    ResultSet rs = null;

    try {
      Statement stmt = conn.createStatement();

      String sql = ("SELECT MAX(update_time)"
                    + " FROM " + _tableName);

      rs = stmt.executeQuery(sql);
      if (rs.next())
        return rs.getLong(1);
    } finally {
      JdbcUtil.close(rs);
      
      conn.close();
    }

    return 0;
  }

  public void close()
  {
  }

  /**
   * Returns the maximum update time on startup
   */
  public ArrayList<CacheData> getUpdates(long updateTime,
                                          int offset)
  {
    Connection conn = null;
    ResultSet rs = null;

    try {
      conn = _dataSource.getConnection();

      String sql;

      sql = _updatesSinceQuery;
      /*
      sql = ("SELECT id,value,flags,item_version,update_time"
             + " FROM " + _tableName
             + " WHERE ?<=update_time"
             + " LIMIT 1024");
      */

      PreparedStatement pstmt = conn.prepareStatement(sql);

      pstmt.setLong(1, updateTime);

      ArrayList<CacheData> entryList = new ArrayList<CacheData>();

      rs = pstmt.executeQuery();

      rs.relative(offset);
      while (rs.next()) {
        byte []keyHash = rs.getBytes(1);
        byte []valueHash = rs.getBytes(2);
        long valueIndex = rs.getLong(3);
        long valueLength = rs.getLong(4);
        byte []cacheHash = rs.getBytes(5);
        long flags = rs.getLong(6);
        long version = rs.getLong(7);
        long itemUpdateTime = rs.getLong(8);
        long accessTimeout = rs.getLong(9);
        long updateTimeout = rs.getLong(10);

        HashKey value = valueHash != null ? new HashKey(valueHash) : null;
        HashKey cacheKey = cacheHash != null ? new HashKey(cacheHash) : null;

        if (keyHash == null)
          continue;

        entryList.add(new CacheData(new HashKey(keyHash),
                                    value, valueIndex, valueLength, version,
                                    cacheKey,
                                    flags,
                                    itemUpdateTime,
                                    accessTimeout,
                                    updateTimeout));
      }

      if (entryList.size() > 0)
        return entryList;
      else
        return null;
    } catch (SQLException e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      JdbcUtil.close(rs);
      JdbcUtil.close(conn);
    }

    return null;
  }

  /**
   * Returns the maximum update time on startup
   */
  public ArrayList<CacheData> getUpdates(HashKey cacheKey,
                                         long updateTime,
                                         int offset)
  {
    Connection conn = null;
    ResultSet rs = null;

    try {
      conn = _dataSource.getConnection();

      String sql;

      sql = _remoteUpdatesSinceQuery;

      PreparedStatement pstmt = conn.prepareStatement(sql);

      pstmt.setBytes(1, cacheKey.getHash());
      pstmt.setLong(2, updateTime);

      ArrayList<CacheData> entryList = new ArrayList<CacheData>();

      rs = pstmt.executeQuery();

      rs.relative(offset);
      while (rs.next()) {
        byte []keyHash = rs.getBytes(1);

        byte []valueHash = rs.getBytes(2);
        long valueIndex = rs.getLong(3);
        long valueLength = rs.getLong(4);
        byte []cacheHash = rs.getBytes(5);
        long flags = rs.getLong(6);
        long version = rs.getLong(7);
        long itemUpdateTime = rs.getLong(8);
        long accessTimeout = rs.getLong(9);
        long updateTimeout = rs.getLong(10);
        
        HashKey value = valueHash != null ? new HashKey(valueHash) : null;
        /*
        HashKey cacheKey = cacheHash != null ? new HashKey(cacheHash) : null;
        */

        if (keyHash == null)
          continue;

        entryList.add(new CacheData(new HashKey(keyHash),
                                    value,
                                    valueIndex,
                                    valueLength,
                                    version,
                                    HashKey.create(cacheHash),
                                    flags,
                                    itemUpdateTime,
                                    accessTimeout,
                                    updateTimeout));
      }

      if (entryList.size() > 0)
        return entryList;
      else
        return null;
    } catch (SQLException e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      JdbcUtil.close(rs);
      JdbcUtil.close(conn);
    }

    return null;
  }

  /**
   * Reads the object from the data store.
   *
   * @param id the hash identifier for the data
   * @return true on successful load
   */
  public MnodeEntry load(HashKey id)
  {
    CacheMapConnection conn = null;
    ResultSet rs = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareLoad();
      pstmt.setBytes(1, id.getHash());

      rs = pstmt.executeQuery();

      if (rs.next()) {
        byte []valueHash = rs.getBytes(1);
        long valueIndex = rs.getLong(2);
        long valueLength = rs.getLong(3);

        byte []cacheHash = rs.getBytes(4);
        long flags = rs.getLong(5);
        long serverVersion = rs.getLong(6);
        long itemVersion = rs.getLong(7);
        long accessedExpireTimeout = rs.getLong(8);
        long modifiedExpireTimeout = rs.getLong(9);
        long updateTime = rs.getLong(10);
        long accessTime = CurrentTime.getCurrentTime();
        
        HashKey cacheHashKey
          = cacheHash != null ? new HashKey(cacheHash) : null;

        HashKey valueHashKey
          = valueHash != null ? new HashKey(valueHash) : null;
          
          long leaseTimeout = 0;

        MnodeEntry entry;
        entry = new MnodeEntry(valueHashKey, valueIndex, valueLength, 
                               itemVersion, null,
                               cacheHashKey,
                               flags,
                               accessedExpireTimeout, modifiedExpireTimeout,
                               leaseTimeout,
                               accessTime, updateTime,
                               serverVersion == _serverVersion,
                               false);
        
        if (log.isLoggable(Level.FINER))
          log.finer(this + " load " + id + " " + entry);
        
        return entry;
      }

      if (log.isLoggable(Level.FINEST))
        log.finest(this + " load: no mnode for cache key " + id);

      return null;
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      JdbcUtil.close(rs);
      
      if (conn != null)
        conn.close();
    }

    return null;
  }

  /**
   * Stores the data, returning true on success
   *
   * @param id the key hash
   * @param value the value hash
   * @param idleTimeout the item's timeout
   */
  public boolean insert(HashKey id,
                        MnodeValue mnodeUpdate)
  {
    CacheMapConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement stmt = conn.prepareInsert();
      stmt.setBytes(1, id.getHash());

      stmt.setBytes(2, mnodeUpdate.getValueHash());
      stmt.setLong(3, mnodeUpdate.getValueIndex());
      stmt.setLong(4, mnodeUpdate.getValueLength());
      stmt.setBytes(5, mnodeUpdate.getCacheHash());

      stmt.setLong(6, mnodeUpdate.getFlags());
      stmt.setLong(7, mnodeUpdate.getVersion());
      stmt.setLong(8, _serverVersion);
      stmt.setLong(9, mnodeUpdate.getAccessedExpireTimeout());
      stmt.setLong(10, mnodeUpdate.getModifiedExpireTimeout());
      stmt.setLong(11, CurrentTime.getCurrentTime());

      int count = stmt.executeUpdate();

      if (log.isLoggable(Level.FINER)) {
        log.finer(this + " insert key=" + id + " " + mnodeUpdate + " count=" + count);
      }
      
      if (count > 0) {
        _entryCount.addAndGet(1);
      }

      return true;
    } catch (SQLException e) {
      log.log(Level.FINER, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Stores the data, returning true on success
   *
   * @param id the key hash
   * @param value the value hash
   * @param idleTimeout the item's timeout
   */
  public boolean updateSave(byte []key,
                            MnodeValue mnodeUpdate)
  {
    CacheMapConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement stmt = conn.prepareUpdateSave();
      
      stmt.setBytes(1, mnodeUpdate.getValueHash());
      stmt.setLong(2, mnodeUpdate.getValueIndex());
      stmt.setLong(3, mnodeUpdate.getValueLength());
      
      stmt.setLong(4, _serverVersion);
      stmt.setLong(5, mnodeUpdate.getVersion());
      stmt.setLong(6, mnodeUpdate.getAccessedExpireTimeout());
      stmt.setLong(7, CurrentTime.getCurrentTime());

      stmt.setBytes(8, key);
      stmt.setLong(9, mnodeUpdate.getVersion());

      int count = stmt.executeUpdate();

      if (log.isLoggable(Level.FINER))
        log.finer(this + " save " + HashKey.create(key) + " " + mnodeUpdate);

      return count > 0;
    } catch (SQLException e) {
      log.log(Level.FINER, e.toString(), e);
    } finally {
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Updates the update time, returning true on success
   *
   * @param id the key hash
   * @param itemVersion the value version
   * @param idleTimeout the item's timeout
   * @param updateTime the item's timeout
   */
  public boolean updateUpdateTime(HashKey id,
                                  long itemVersion,
                                  long accessTimeout,
                                  long updateTime)
  {
    CacheMapConnection conn = null;

    try {
      conn = getConnection();

      PreparedStatement stmt = conn.prepareUpdateUpdateTime();
      stmt.setLong(1, accessTimeout);
      stmt.setLong(2, updateTime);

      stmt.setBytes(3, id.getHash());
      stmt.setLong(4, itemVersion);

      int count = stmt.executeUpdate();

      if (log.isLoggable(Level.FINER))
        log.finer(this + " updateUpdateTime key=" + id);

      return count > 0;
    } catch (SQLException e) {
      log.log(Level.FINER, e.toString(), e);
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
   * @return true on successful load
   */
  public boolean remove(byte []key)
  {
    CacheMapConnection conn = null;
    ResultSet rs = null;

    try {
      conn = getConnection();

      PreparedStatement pstmt = conn.prepareDelete();
      pstmt.setBytes(1, key);

      int count = pstmt.executeUpdate();
      
      if (count > 0) {
        _entryCount.addAndGet(-1);
        
        return true;
      }
      else {
        return false;
      }
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      JdbcUtil.close(rs);
      
      if (conn != null)
        conn.close();
    }

    return false;
  }

  /**
   * Clears the expired data
   */
  public ArrayList<ExpiredMnode> selectExpiredData()
  {
    ArrayList<ExpiredMnode> expireList = new ArrayList<ExpiredMnode>();
    
    CacheMapConnection conn = null;
 
    try {
      conn = getConnection();
      PreparedStatement pstmt = conn.prepareSelectExpire();

      long now = CurrentTime.getCurrentTime();

      pstmt.setLong(1, now);
      pstmt.setLong(2, now);
      
      ResultSet rs = pstmt.executeQuery();
      
      while (rs.next()) {
        byte []key = rs.getBytes(1);
        long dataId = rs.getLong(2);
        
        expireList.add(new ExpiredMnode(key, dataId));
      }
    } catch (Exception e) {
      e.printStackTrace();
      log.log(Level.FINE, e.toString(), e);
    } finally {
      conn.close();
    }
    
    return expireList;
  }

  //
  // statistics
  //
  
  public long getCount()
  {
    return _entryCount.get();
  }

  private long getCountImpl()
  {
    CacheMapConnection conn = null;
    ResultSet rs = null;

    try {
      conn = getConnection();
      PreparedStatement stmt = conn.prepareCount();

      rs = stmt.executeQuery();

      if (rs != null && rs.next()) {
        long value = rs.getLong(1);

        rs.close();

        return value;
      }

      return -1;
    } catch (SQLException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      JdbcUtil.close(rs);
      
      conn.close();
    }

    return -1;
  }

  public void destroy()
  {
    _dataSource = null;
    _freeConn = null;
  }

  private CacheMapConnection getConnection()
    throws SQLException
  {
    CacheMapConnection cConn = _freeConn.allocate();

    if (cConn == null) {
      Connection conn = _dataSource.getConnection();
      cConn = new CacheMapConnection(conn);
    }

    return cConn;
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() +  "[" + _serverName + "]";
  }

  class CacheMapConnection {
    private Connection _conn;

    private PreparedStatement _loadStatement;

    private PreparedStatement _insertStatement;
    private PreparedStatement _updateSaveStatement;
    private PreparedStatement _updateUpdateTimeStatement;

    private PreparedStatement _updateVersionStatement;

    private PreparedStatement _expireStatement;
    private PreparedStatement _selectExpireStatement;
    private PreparedStatement _deleteStatement;

    private PreparedStatement _countStatement;

    CacheMapConnection(Connection conn)
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

    PreparedStatement prepareInsert()
      throws SQLException
    {
      if (_insertStatement == null)
        _insertStatement = _conn.prepareStatement(_insertQuery);

      return _insertStatement;
    }

    PreparedStatement prepareUpdateSave()
      throws SQLException
    {
      if (_updateSaveStatement == null)
        _updateSaveStatement = _conn.prepareStatement(_updateSaveQuery);

      return _updateSaveStatement;
    }

    PreparedStatement prepareUpdateUpdateTime()
      throws SQLException
    {
      if (_updateUpdateTimeStatement == null) {
        _updateUpdateTimeStatement
          = _conn.prepareStatement(_updateUpdateTimeQuery);
      }

      return _updateUpdateTimeStatement;
    }

    PreparedStatement prepareUpdateVersion()
      throws SQLException
    {
      if (_updateVersionStatement == null)
        _updateVersionStatement = _conn.prepareStatement(_updateVersionQuery);

      return _updateVersionStatement;
    }

    PreparedStatement prepareExpire()
      throws SQLException
    {
      if (_expireStatement == null)
        _expireStatement = _conn.prepareStatement(_expireQuery);

      return _expireStatement;
    }

    PreparedStatement prepareSelectExpire()
      throws SQLException
    {
      if (_selectExpireStatement == null)
        _selectExpireStatement = _conn.prepareStatement(_selectExpireQuery);

      return _selectExpireStatement;
    }

    PreparedStatement prepareDelete()
      throws SQLException
    {
      if (_deleteStatement == null)
        _deleteStatement = _conn.prepareStatement(_deleteQuery);

      return _deleteStatement;
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
      if (! _isLocalDataSource
          || _freeConn == null
          || ! _freeConn.freeCareful(this)) {
        try {
          _conn.close();
        } catch (SQLException e) {
        }
      }
    }
  }
  
  public static final class ExpiredMnode {
    private final byte []_key;
    private final long _dataId;
    
    ExpiredMnode(byte []key, long dataId)
    {
      _key = key;
      _dataId = dataId;
    }
    
    public final byte []getKey()
    {
      return _key;
    }
    
    public final long getDataId()
    {
      return _dataId;
    }
    
    public String toString()
    {
      return (getClass().getSimpleName()
          + "[" + Hex.toHex(_key, 0, 4)
          + "," + Long.toHexString(_dataId) + "]");
    }
  }
}
