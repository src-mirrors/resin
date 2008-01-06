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

package javax.persistence;

/**
 * The main application interface to the persistence context.
 */
public interface EntityManager {
  /**
   * Makes an object managed and persistent.
   */
  public void persist(Object entity);

  /**
   * Merge the state of the entity to the current context.
   */
  public <T> T merge(T entity);

  /**
   * Removes the instance.
   */
  public void remove(Object entity);

  /**
   * Find based on the primary key.
   */
  public <T> T find(Class<T> entityCLass, Object primaryKey);

  /**
   * Gets an instance whose state may be lazily fetched.
   */
  public <T> T getReference(Class<T> entityClass, Object primaryKey);

  /**
   * Synchronize the context with the database.
   */
  public void flush();

  /**
   * Sets the flush mode for all objects in the context.
   */
  public void setFlushMode(FlushModeType flushMode);

  /**
   * Returns the flush mode for the objects in the context.
   */
  public FlushModeType getFlushMode();

  /**
   * Sets the lock mode for an entity.
   */
  public void lock(Object entity, LockModeType lockMode);

  /**
   * Update the state of the instance from the database.
   */
  public void refresh(Object entity);

  /**
   * Clears the context, causing all entities to become detached.
   */
  public void clear();

  /**
   * Check if the instance belongs to the current context.
   */
  public boolean contains(Object entity);

  /**
   * Creates a new query.
   */
  public Query createQuery(String ql);

  /**
   * Creates a named query.
   */
  public Query createNamedQuery(String name);

  /**
   * Creates a native SQL query.
   */
  public Query createNativeQuery(String sql);

  /**
   * Creates a native SQL query.
   */
  public Query createNativeQuery(String sql, Class resultClass);

  /**
   * Creates a query for SQL.
   */
  public Query createNativeQuery(String sql, String resultSEtMapping);

  /**
   * Joins the transaction.
   */
  public void joinTransaction();

  /**
   * Gets the delegate.
   */
  public Object getDelegate();

  /**
   * Closes the entity manager.
   */
  public void close();

  /**
   * Returns true if the entity manager is open.
   */
  public boolean isOpen();

  /**
   * Returns the transaction manager object.
   */
  public EntityTransaction getTransaction();
}
