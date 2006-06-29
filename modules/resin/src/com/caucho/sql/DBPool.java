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

package com.caucho.sql;

import com.caucho.config.ConfigException;
import com.caucho.config.types.InitParam;
import com.caucho.config.types.Period;
import com.caucho.jca.ConnectionPool;
import com.caucho.jca.ResourceManagerImpl;
import com.caucho.loader.EnvironmentLocal;
import com.caucho.log.Log;
import com.caucho.mbeans.j2ee.J2EEAdmin;
import com.caucho.mbeans.j2ee.JDBCResource;
import com.caucho.mbeans.j2ee.JDBCDataSource;
import com.caucho.naming.Jndi;
import com.caucho.transaction.TransactionManagerImpl;
import com.caucho.util.L10N;

import javax.resource.spi.ManagedConnectionFactory;
import javax.sql.ConnectionPoolDataSource;
import javax.sql.DataSource;
import javax.sql.XADataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Manages a pool of database connections.  In addition, DBPool configures
 * the database connection from a configuration file.
 *
 * <p>Like JDBC 2.0 pooling, DBPool returns a wrapped Connection.
 * Applications can use that connection just like an unpooled connection.
 * It is more important than ever to <code>close()</code> the connection,
 * because the close returns the connection to the connection pool.
 *
 * <h4>Example using DataSource JNDI style (recommended)</h4>
 *
 * <pre><code>
 * Context env = (Context) new InitialContext().lookup("java:comp/env");
 * DataSource pool = (DataSource) env.lookup("jdbc/test");
 * Connection conn = pool.getConnection();
 * try {
 *   ... // normal connection stuff
 * } finally {
 *   conn.close();
 * }
 * </code></pre>
 *
 * <h4>Configuration</h4>
 *
 * <pre><code>
 * &lt;database name='jdbc/test'>
 *   &lt;init>
 *     &lt;driver>postgresql.Driver&lt;/driver>
 *     &lt;url>jdbc:postgresql://localhost/test&lt;/url>
 *     &lt;user>ferg&lt;/user>
 *     &lt;password>foobar&lt;/password>
 *   &lt;/init>
 * &lt;/database>
 * </code></pre>
 *
 * <h4>Pool limits and timeouts</h4>
 *
 * The pool will only allow getMaxConnections() connections alive at a time.
 * If <code>getMaxConnection</code> connections are already active,
 * <code>getPooledConnection</code> will block waiting for an available
 * connection.  The wait is timed.  If connection-wait-time passes
 * and there is still no connection, <code>getPooledConnection</code>
 * create a new connection anyway.
 *
 * <p>Connections will only stay in the pool for about 5 seconds.  After
 * that they will be removed and closed.  This reduces the load on the DB
 * and also protects against the database dropping old connections.
 */
public class DBPool implements DataSource {
  protected static final Logger log = Log.open(DBPool.class);
  private static final L10N L = new L10N(DBPool.class);

  private EnvironmentLocal<DBPoolImpl> _localPoolImpl;
  private EnvironmentLocal<DataSource> _localDataSourceImpl;

  private String _var;
  private String _jndiName;

  private ResourceManagerImpl _resourceManager;
  private ConnectionPool _connectionPool;

  private DBPoolImpl _poolImpl;
  private DataSource _dataSource;
  private DataSourceImpl _resinDataSource;

  /**
   * Null constructor for the Driver interface; called by the JNDI
   * configuration.  Applications should not call this directly.
   */
  public DBPool()
  {
    _poolImpl = new DBPoolImpl();

    _resourceManager = ResourceManagerImpl.createLocalManager();
    _connectionPool = _resourceManager.createConnectionPool();
  }

  /**
   * Returns the Pool's name
   */
  public String getJndiName()
  {
    return _jndiName;
  }

  /**
   * Sets the Pool's JNDI name.  Also puts the pool in the classloader's
   * list of pools.
   */
  public void setJndiName(String name)
  {
    _jndiName = name;

    if (_var == null)
      getPool().setName(name);
  }

  /**
   * Sets the Pool's var.
   */
  public void setVar(String var)
  {
    _var = var;

    getPool().setName(var);
  }

  /**
   * Returns the Pool's name
   */
  public String getName()
  {
    if (_var != null)
      return _var;
    else
      return getJndiName();
  }

  /**
   * Sets a custom driver (or data source)
   */
  public DriverConfig createDriver()
    throws ConfigException
  {
    return getPool().createDriver();
  }

  /**
   * Sets a custom driver (or data source)
   */
  public DriverConfig createBackupDriver()
    throws ConfigException
  {
    return getPool().createBackupDriver();
  }

  /**
   * Sets a driver parameter (compat).
   */
  public void setInitParam(InitParam init)
  {
    getPool().setInitParam(init);
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setJDBCDriver(Driver jdbcDriver)
    throws SQLException
  {
    getPool().setJDBCDriver(jdbcDriver);
  }

  /**
   * Configure the initial connection
   */
  public ConnectionConfig createConnection()
    throws ConfigException
  {
    return getPool().createConnection();
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setPoolDataSource(ConnectionPoolDataSource poolDataSource)
    throws SQLException
  {
    getPool().setPoolDataSource(poolDataSource);
  }

  /**
   * Sets the jdbc-driver config.
   */
  public void setXADataSource(XADataSource xaDataSource)
    throws SQLException
  {
    getPool().setXADataSource(xaDataSource);
  }

  /**
   * Sets the jdbc-driver URL
   */
  public void setURL(String url)
    throws ConfigException
  {
    getPool().setURL(url);
  }

  /**
   * Returns the connection's user (compat).
   */
  public String getUser()
  {
    return getPool().getUser();
  }

  /**
   * Sets the connection's user.
   */
  public void setUser(String user)
  {
    getPool().setUser(user);
  }

  /**
   * Returns the connection's password
   */
  public String getPassword()
  {
    return getPool().getPassword();
  }

  /**
   * Sets the connection's password
   */
  public void setPassword(String password)
  {
    getPool().setPassword(password);
  }

  /**
   * Get the maximum number of pooled connections.
   */
  public int getMaxConnections()
  {
    return _connectionPool.getMaxConnections();
  }

  /**
   * Sets the maximum number of pooled connections.
   */
  public void setMaxConnections(int maxConnections)
    throws ConfigException
  {
    _connectionPool.setMaxConnections(maxConnections);
  }

  /**
   * Get the total number of connections
   */
  public int getTotalConnections()
  {
    return _connectionPool.getConnectionCount();
  }

  /**
   * Sets the time to wait for a connection when all are used.
   */
  public void setConnectionWaitTime(Period waitTime)
  {
    _connectionPool.setConnectionWaitTime(waitTime);
  }

  /**
   * Gets the time to wait for a connection when all are used.
   */
  public long getConnectionWaitTime()
  {
    return _connectionPool.getConnectionWaitTime();
  }

  /**
   * The number of connections to overflow if the connection pool fills
   * and there's a timeout.
   */
  public void setMaxOverflowConnections(int maxOverflowConnections)
  {
    _connectionPool.setMaxOverflowConnections(maxOverflowConnections);
  }

  /**
   * The number of connections to create at any one time.
   */
  public void setMaxCreateConnections(int maxCreateConnections)
    throws ConfigException
  {
    _connectionPool.setMaxCreateConnections(maxCreateConnections);
  }

  /**
   * Set true if the stack trace should be saved on allocation.
   */
  public void setSaveAllocationStackTrace(boolean save)
  {
    _connectionPool.setSaveAllocationStackTrace(save);
  }

  /**
   * The number of connections to overflow if the connection pool fills
   * and there's a timeout.
   */
  public int getMaxOverflowConnections()
  {
    return _connectionPool.getMaxOverflowConnections();
  }

  /**
   * Get the total number of connections in use by the program.
   */
  public int getActiveConnections()
  {
    return _connectionPool.getActiveConnectionCount();
  }

  /**
   * Get the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public long getMaxIdleTime()
  {
    return _connectionPool.getMaxIdleTime();
  }

  /**
   * Set the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public void setMaxIdleTime(Period idleTime)
  {
    _connectionPool.setMaxIdleTime(idleTime.getPeriod());
  }

  /**
   * Get the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public long getMaxPoolTime()
  {
    return _connectionPool.getMaxPoolTime();
  }

  /**
   * Set the time in milliseconds a connection will remain in the pool before
   * being closed.
   */
  public void setMaxPoolTime(Period maxPoolTime)
  {
    _connectionPool.setMaxPoolTime(maxPoolTime.getPeriod());
  }

  /**
   * Get the time in milliseconds a connection can remain active.
   */
  public long getMaxActiveTime()
  {
    return _connectionPool.getMaxActiveTime();
  }

  /**
   * Set the time in milliseconds a connection can remain active.
   */
  public void setMaxActiveTime(Period maxActiveTime)
  {
    _connectionPool.setMaxActiveTime(maxActiveTime.getPeriod());
  }

  /**
   * Get the table to 'ping' to see if the connection is still live.
   */
  public String getPingTable()
  {
    return getPool().getPingTable();
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   *
   * @param pingTable name of the SQL table to ping.
   */
  public void setPingTable(String pingTable)
  {
    getPool().setPingTable(pingTable);
  }

  /**
   * If true, the pool will ping when attempting to reuse a connection.
   */
  public boolean getPingOnReuse()
  {
    return getPool().getPingOnReuse();
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   */
  public void setPingOnReuse(boolean pingOnReuse)
  {
    getPool().setPingOnReuse(pingOnReuse);
  }

  /**
   * If true, the pool will ping in the idle pool.
   */
  public boolean getPingOnIdle()
  {
    return getPool().getPingOnIdle();
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   */
  public void setPingOnIdle(boolean pingOnIdle)
  {
    getPool().setPingOnIdle(pingOnIdle);
  }

  /**
   * Set the table to 'ping' to see if the connection is still live.
   */
  public void setPing(boolean ping)
  {
    getPool().setPing(ping);
  }

  /**
   * Sets the time to ping for ping-on-idle
   */
  public void setPingInterval(Period interval)
  {
    getPool().setPingInterval(interval);
  }

  /**
   * Gets how often the ping for ping-on-idle
   */
  public long getPingInterval()
 {
    return getPool().getPingInterval();
  }

  /**
   * Returns the prepared statement cache size.
   */
  public int getPreparedStatementCacheSize()
  {
    return getPool().getPreparedStatementCacheSize();
  }

  /**
   * Sets the prepared statement cache size.
   */
  public void setPreparedStatementCacheSize(int size)
  {
    getPool().setPreparedStatementCacheSize(size);
  }

  /**
   * Set the transaction manager for this pool.
   */
  public void setTransactionManager(TransactionManagerImpl tm)
  {
    getPool().setTransactionManager(tm);
  }

  /**
   * Set true if statement should be wrapped.
   */
  public void setWrapStatements(boolean isWrap)
  {
    getPool().setWrapStatements(isWrap);
  }

  /**
   * Returns the transaction manager.
   */
  /*
  public TransactionManager getTransactionManager()
  {
    return getPool().getTransactionManager();
  }
  */

  /**
   * Sets the transaction timeout.
   */
  public void setTransactionTimeout(Period period)
  {
    getPool().setTransactionTimeout(period);
  }

  /**
   * Returns true if this is transactional.
   */
  public boolean isXA()
  {
    return getPool().isXA();
  }

  /**
   * Returns true if this is transactional.
   */
  public void setXA(boolean isTransactional)
  {
    getPool().setXA(isTransactional);
  }

  /**
   * Returns true if this is transactional.
   */
  public void setXAForbidSameRM(boolean isXAForbidSameRM)
  {
    getPool().setXAForbidSameRM(isXAForbidSameRM);
  }

  /**
   * Set the output for spying.
   */
  public void setSpy(boolean isSpy)
    throws IOException
  {
    getPool().setSpy(isSpy);
  }

  /**
   * Initialize the pool.
   */
  public void init()
    throws Exception
  {
    _localPoolImpl = new EnvironmentLocal<DBPoolImpl>("caucho.db-pool." + getName());
    _localPoolImpl.set(_poolImpl);

    _poolImpl.init();

    _connectionPool.setName(getName());

    _connectionPool.setShareable(true);
    _connectionPool.setXATransaction(_poolImpl.isXATransaction());
    _connectionPool.setLocalTransaction(_poolImpl.isLocalTransaction());

    ManagedConnectionFactory mcf = _poolImpl.getManagedConnectionFactory();
    
    
    _dataSource = (DataSource) _connectionPool.init(mcf);
    _connectionPool.start();

    _localDataSourceImpl = new EnvironmentLocal<DataSource>("caucho.data-source." + getName());
    _localDataSourceImpl.set(_dataSource);

    if (_jndiName != null) {
      String name = _jndiName;
      if (! name.startsWith("java:"))
        name = "java:comp/env/" + name;

      log.config("database " + name + " starting");

      Jndi.bindDeep(name, this);
    }

    J2EEAdmin.register(new JDBCResource(this));
    J2EEAdmin.register(new JDBCDataSource(this));
  }

  /**
   * Returns a new or pooled connection.
   */
  public Connection getConnection() throws SQLException
  {
    return getDataSource().getConnection();
  }

  /**
   * Return a connection.  The connection will only be pooled if
   * user and password match the configuration.  In general, applications
   * should use the null-argument getConnection().
   *
   * @param user database user
   * @param password database password
   * @return a database connection
   */
  public Connection getConnection(String user, String password)
    throws SQLException
  {
    return getDataSource().getConnection(user, password);
  }

  /**
   * Returns the login timeout
   */
  public int getLoginTimeout() throws SQLException
  {
    return getDataSource().getLoginTimeout();
  }

  /**
   * Sets the login timeout
   */
  public void setLoginTimeout(int timeout) throws SQLException
  {
    getDataSource().setLoginTimeout(timeout);
  }

  /**
   * Gets the log writer
   */
  public PrintWriter getLogWriter() throws SQLException
  {
    return getDataSource().getLogWriter();
  }

  /**
   * Sets the log writer
   */
  public void setLogWriter(PrintWriter log) throws SQLException
  {
    getDataSource().setLogWriter(log);
  }

  /**
   * Returns the underlying pool.
   */
  private DBPoolImpl getPool()
  {
    if (_poolImpl == null || _poolImpl.isClosed()) {
      _poolImpl = _localPoolImpl.get();

      if (_poolImpl == null)
        throw new IllegalStateException(L.l("DBPool `{0}' no longer exists.",
                                            getName()));
    }

    return _poolImpl;
  }

  /**
   * Returns the underlying data source.
   */
  private DataSource getDataSource()
  {
    if (_dataSource == null ||
	_resinDataSource != null && _resinDataSource.isClosed()) {
      _dataSource = _localDataSourceImpl.get();

      if (_dataSource instanceof DataSourceImpl)
	_resinDataSource = (DataSourceImpl) _dataSource;
      else
	_resinDataSource = null;

      if (_dataSource == null)
        throw new IllegalStateException(L.l("DBPool `{0}' no longer exists.",
                                            getName()));
    }

    return _dataSource;
  }

  /**
   * Returns a string description of the pool.
   */
  public String toString()
  {
    return "DBPool[" + getName() + "]";
  }
}

