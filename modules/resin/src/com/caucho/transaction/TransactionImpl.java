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

package com.caucho.transaction;

import com.caucho.jca.UserTransactionImpl;
import com.caucho.jca.UserTransactionSuspendState;
import com.caucho.log.Log;
import com.caucho.transaction.xalog.AbstractXALogStream;
import com.caucho.util.Alarm;
import com.caucho.util.AlarmListener;
import com.caucho.util.CharBuffer;
import com.caucho.util.L10N;

import javax.transaction.*;
import javax.transaction.xa.XAException;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implementation of the transaction.  Transactions are normally
 * associated with a single thread.
 */
public class TransactionImpl implements Transaction, AlarmListener {
  private static final Logger log = Log.open(TransactionImpl.class);
  private static final L10N L = new L10N(TransactionImpl.class);

  private final static long DEFAULT_TIMEOUT = 60000;
  private final static long EXTRA_TIMEOUT = 60000;
  private final static long MAX_TIMEOUT = 24 * 3600 * 1000L;

  // flag when the resource is active (between getConnection() and close())
  private final static int RES_ACTIVE    = 0x1;
  // flag when the resource shares another resource manager
  private final static int RES_SHARED_RM = 0x2;
  // flag when the resource is suspended
  private final static int RES_SUSPENDED = 0x4;
  // flag when the resource wants to commit
  private final static int RES_COMMIT = 0x8;

  private TransactionManagerImpl _manager;

  // state of the transaction
  private int _status;

  // The transaction id for the resource
  private XidImpl _xid;

  // true if the transaction is suspended.
  private boolean _isSuspended;
  private boolean _isDead;

  // How many resources are available
  private int _resourceCount;
  // The current resources in the transaction
  private XAResource []_resources;
  // The xids for the resources
  private XidImpl []_resourceXid;
  // Whether the resources are active (between begin/end) or not
  private int []_resourceState;

  private UserTransactionImpl _userTransaction;
  private UserTransactionSuspendState _suspendState;

  private HashMap<String,Object> _props;

  private ArrayList<Synchronization> _syncList;

  private Throwable _rollbackException;

  private AbstractXALogStream _xaLog;

  private long _timeout = 0;
  private Alarm _alarm;

  /**
   * Creates a new transaction.
   *
   * @param manager the owning transaction manager
   */
  TransactionImpl(TransactionManagerImpl manager)
  {
    _manager = manager;
    _timeout = _manager.getTimeout();
    _status = Status.STATUS_NO_TRANSACTION;
    _alarm = new Alarm("xa-timeout", this, ClassLoader.getSystemClassLoader());
  }

  /**
   * Sets the user transaction.
   */
  public void setUserTransaction(UserTransactionImpl ut)
  {
    _userTransaction = ut;
  }

  /**
   * Returns true if the transaction has any associated resources.
   */
  boolean hasResources()
  {
    return _resourceCount > 0;
  }

  /**
   * Returns true if the transaction is currently suspended.
   */
  boolean isSuspended()
  {
    return _isSuspended;
  }

  /**
   * Returns true if the transaction is dead, i.e. failed for some reason.
   */
  boolean isDead()
  {
    return _isDead;
  }

  /**
   * Return true if the transaction has no resources.
   */
  public boolean isEmpty()
  {
    if (_isDead)
      return true;
    else if (_resourceCount > 0)
      return false;
    // XXX: ejb/3692 because TransactionContext adds itself
    else if (_syncList != null && _syncList.size() > 1)
      return false;
    else
      return true;
  }

  /**
   * Start a transaction.
   */
  void begin()
    throws SystemException, NotSupportedException
  {
    if (_status != Status.STATUS_NO_TRANSACTION) {
      int status = _status;

      try {
        rollback();
      } catch (Throwable e) {
        log.log(Level.WARNING, e.toString(), e);
      }

      throw new NotSupportedException(L.l("Nested transactions are not supported. The previous transaction for this thread did not commit() or rollback(). Check that every UserTransaction.begin() has its commit() or rollback() in a finally block.\nStatus was {0}.",
                                          xaState(status)));
    }

    if (_isDead)
      throw new IllegalStateException(L.l("Error trying to use dead transaction."));

    _status = Status.STATUS_ACTIVE;

    _rollbackException = null;

    if (_xid == null)
      _xid = _manager.createXID();

    if (log.isLoggable(Level.FINE))
      log.fine("begin Transaction" + _xid);

    if (_timeout > 0) {
      _alarm.queue(_timeout + EXTRA_TIMEOUT);
    }
  }

  /**
   * Enlists a resource with the current transaction.  Example
   * resources are database or JMS connections.
   *
   * @return true if successful
   */
  public boolean enlistResource(XAResource resource)
    throws RollbackException, SystemException
  {
    if (resource == null)
      throw new IllegalArgumentException(L.l("Resource must not be null in enlistResource"));

    if (_isSuspended)
      throw new IllegalStateException(L.l("can't enlist with suspended transaction"));
    if (_status == Status.STATUS_ACTIVE) {
      // normal
    }
    else {
      // validate the status
      if (_status != Status.STATUS_MARKED_ROLLBACK) {
      }
      else if (_rollbackException != null)
        throw RollbackExceptionWrapper.create(_rollbackException);
      else
        throw new RollbackException(L.l("can't enlist with rollback-only transaction"));

      if (_status == Status.STATUS_NO_TRANSACTION)
        throw new IllegalStateException(L.l("can't enlist with inactive transaction"));

      throw new IllegalStateException(L.l("can't enlist with transaction in '{0}' state",
                                          xaState(_status)));
    }

    // creates enough space in the arrays for the resource
    if (_resources == null) {
      _resources = new XAResource[16];
      _resourceState = new int[16];
      _resourceXid = new XidImpl[16];
    }
    else if (_resources.length <= _resourceCount) {
      int oldLength = _resources.length;
      int newLength = 2 * oldLength;

      XAResource []resources = new XAResource[newLength];
      int []resourceState = new int[newLength];
      XidImpl []resourceXid = new XidImpl[newLength];

      System.arraycopy(_resources, 0, resources, 0, oldLength);
      System.arraycopy(_resourceState, 0, resourceState, 0, oldLength);
      System.arraycopy(_resourceXid, 0, resourceXid, 0, oldLength);

      _resources = resources;
      _resourceState = resourceState;
      _resourceXid = resourceXid;
    }

    int flags = XAResource.TMNOFLAGS;

    // Active transaction will call the XAResource.start() method
    // to let the resource manager know that the resource is managed.
    //
    // If the resource uses the same resource manager as one of the
    // current resources, issue a TMJOIN message.
    XidImpl xid = _xid;
    boolean hasNewResource = true;

    for (int i = 0; i < _resourceCount; i++) {
      if (_resources[i] != resource) {
      }
      else if ((_resourceState[i] & RES_ACTIVE) != 0) {
        IllegalStateException exn;
        exn = new IllegalStateException(L.l("Can't enlist same resource twice.  Delist is required before calling enlist with an old resource."));

        setRollbackOnly(exn);
        throw exn;
      }

      try {
        if (_resources[i].isSameRM(resource)) {
          flags = XAResource.TMJOIN;
          xid = _resourceXid[i];

          if ((_resourceState[i] & RES_ACTIVE) == 0) {
            _resources[i] = resource;
            _resourceState[i] |= RES_ACTIVE;
            hasNewResource = false;
          }

          break;
        }
      } catch (Exception e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    if (_resourceCount > 0 && flags != XAResource.TMJOIN)
      xid = new XidImpl(_xid, _resourceCount + 1);

    try {
      if (_timeout > 0)
        resource.setTransactionTimeout((int) (_timeout / 1000L));

      if (log.isLoggable(Level.FINER)) {
        if (flags == XAResource.TMJOIN)
          log.finer("join-XA " + xid + " " + resource);
        else
          log.finer("start-XA " + xid + " " + resource);
      }

      resource.start(xid, flags);
    } catch (XAException e) {
      setRollbackOnly(e);
      throw new SystemException(e);
    }

    if (hasNewResource) {
      _resources[_resourceCount] = resource;
      _resourceState[_resourceCount] = RES_ACTIVE;

      if (flags == XAResource.TMJOIN)
        _resourceState[_resourceCount] |= RES_SHARED_RM;

      _resourceXid[_resourceCount] = xid;
      _resourceCount++;
    }

    return true;
  }

  /**
   * Returns true if the local transaction optimization would be allowed.
   */
  public boolean allowLocalTransactionOptimization()
  {
    // XXX: can also check if all are non-local
    return _resourceCount == 0;
  }

  /**
   * Returns the current number of resources.
   */
  public int getEnlistedResourceCount()
  {
    return _resourceCount;
  }

  /**
   * delists a resource from the current transaction
   *
   * @param resource the resource to delist
   * @param flag XXX: ???
   *
   * @return true if successful
   */
  public boolean delistResource(XAResource resource, int flag)
    throws SystemException
  {
    if (_isSuspended)
      throw new IllegalStateException(L.l("transaction is suspended"));

    if (_resourceCount == 0)
      return true;

    int index;

    for (index = _resourceCount - 1; index >= 0; index--) {
      if (_resources[index].equals(resource))
        break;
    }

    if (index < 0)
      return false;

    // If there is no current transaction,
    // remove it from the resource list entirely.
    if (_status == Status.STATUS_NO_TRANSACTION) {
      for (; index + 1 < _resourceCount; index++) {
        _resources[index] = _resources[index + 1];
        _resourceState[index] = _resourceState[index + 1];
        _resourceXid[index] = _resourceXid[index + 1];
      }

      _resourceCount--;

      return true;
    }

    if (_status == Status.STATUS_MARKED_ROLLBACK)
      flag = XAResource.TMFAIL;

    _resourceState[index] &= ~RES_ACTIVE;

    try {
      resource.end(_resourceXid[index], flag);
    } catch (XAException e) {
      setRollbackOnly(e);
      throw new SystemException(e);
    }

    return true;
  }

  /**
   * Adds an attribute.
   */
  public void setAttribute(String var, Object value)
  {
    if (_props == null)
      _props = new HashMap<String,Object>();

    _props.put(var, value);
  }

  /**
   * Gets an attribute.
   */
  public Object getAttribute(String var)
  {
    if (_props != null)
      return _props.get(var);
    else
      return null;
  }

  public Xid getXid()
  {
    return _xid;
  }

  /**
   * Returns the status of this transaction
   */
  public int getStatus()
  {
    return _status;
  }

  /**
   * Suspend the transaction.  The timeouts are stopped.
   */
  void suspend()
    throws SystemException
  {
    if (_isSuspended)
      throw new IllegalStateException(L.l("can't suspend already-suspended transaction"));

    // _alarm.dequeue();

    _isSuspended = true;

    for (int i = _resourceCount - 1; i >= 0; i--) {
      if ((_resourceState[i] & (RES_ACTIVE|RES_SUSPENDED)) == RES_ACTIVE) {
        try {
          XAResource resource = _resources[i];

          resource.end(_resourceXid[i], XAResource.TMSUSPEND);
        } catch (Exception e) {
          setRollbackOnly(e);
        }
      }
    }

    if (_userTransaction != null)
      _suspendState = _userTransaction.suspend();

    if (log.isLoggable(Level.FINER))
      log.fine(_xid + " suspended");
  }

  /**
   * Resume the transaction and requeue the timeout.
   */
  void resume()
    throws SystemException
  {
    if (! _isSuspended)
      throw new IllegalStateException(L.l("can't resume non-suspended transaction"));

    _alarm.queue(_timeout + EXTRA_TIMEOUT);

    for (int i = _resourceCount - 1; i >= 0; i--) {
      if ((_resourceState[i] & (RES_ACTIVE|RES_SUSPENDED)) == RES_ACTIVE) {
        try {
          XAResource resource = _resources[i];

          resource.start(_resourceXid[i], XAResource.TMRESUME);
        } catch (Exception e) {
          setRollbackOnly(e);
        }
      }
    }

    if (_userTransaction != null)
      _userTransaction.resume(_suspendState);

    _isSuspended = false;

    if (log.isLoggable(Level.FINE))
      log.fine(_xid + " resume");
  }

  /**
   * Register a synchronization callback
   */
  public void registerSynchronization(Synchronization sync)
  {
    if (_syncList == null)
      _syncList = new ArrayList<Synchronization>();

    _syncList.add(sync);
  }

  /**
   * Force any completion to be a rollback.
   */
  public void setRollbackOnly()
    throws SystemException
  {
    if (_status != Status.STATUS_ACTIVE &&
        _status != Status.STATUS_MARKED_ROLLBACK)
      throw new IllegalStateException(L.l("can't set rollback-only"));

    _status = Status.STATUS_MARKED_ROLLBACK;

    _timeout = 0;

    if (log.isLoggable(Level.FINER))
      log.finer("setting rollback-only");
  }

  /**
   * Force any completion to be a rollback.
   */
  public void setRollbackOnly(Throwable exn)
  {
    if (_status != Status.STATUS_ACTIVE &&
        _status != Status.STATUS_MARKED_ROLLBACK)
      throw new IllegalStateException(L.l("can't set rollback-only"));

    _status = Status.STATUS_MARKED_ROLLBACK;

    if (_rollbackException == null)
      _rollbackException = exn;

    if (log.isLoggable(Level.FINER))
      log.log(Level.FINER, "setting rollback-only: " + exn.toString(), exn);
  }

  /**
   * Commit the transaction.
   */
  public void commit()
    throws RollbackException, HeuristicMixedException,
           HeuristicRollbackException, SystemException
  {
    _alarm.dequeue();

    Exception heuristicExn = null;

    try {
      callBeforeCompletion();
    } catch (RollbackException e) {
      throw e;
    } catch (Throwable e) {
      setRollbackOnly(e);
    }

    try {
      if (_status != Status.STATUS_ACTIVE) {
        switch (_status) {
        case Status.STATUS_MARKED_ROLLBACK:
          rollbackInt();
          if (_rollbackException != null)
            throw new RollbackExceptionWrapper(_rollbackException);
          else
            throw new RollbackException(L.l("Transaction has been marked rolled back."));

        case Status.STATUS_NO_TRANSACTION:
          throw new IllegalStateException(L.l("Can't commit outside of a transaction.  Either the UserTransaction.begin() is missing or the transaction has already been committed or rolled back."));

        default:
          rollbackInt();
          throw new IllegalStateException(L.l("can't commit {0}", String.valueOf(_status)));
        }
      }

      if (log.isLoggable(Level.FINE))
        log.fine("committing Transaction" + _xid);

      if (_resourceCount > 0) {
        _status = Status.STATUS_PREPARING;

        AbstractXALogStream xaLog = _manager.getXALogStream();
        boolean hasPrepare = false;
        boolean allowSinglePhase = false;

        for (int i = _resourceCount - 1; i >= 0; i--) {
          XAResource resource = (XAResource) _resources[i];

          if (i == 0 && (xaLog == null || ! hasPrepare)) {
            // server/1601
            _resourceState[0] |= RES_COMMIT;

            allowSinglePhase = true;
            break;
          }

          if ((_resourceState[i] & RES_SHARED_RM) == 0) {
            try {
              int prepare = resource.prepare(_resourceXid[i]);

              if (prepare == XAResource.XA_RDONLY) {
              }
              else if (prepare == XAResource.XA_OK) {
                hasPrepare = true;
                _resourceState[i] |= RES_COMMIT;
              }
              else {
                log.finer("unexpected prepare result " + prepare);
                rollbackInt();
              }
            } catch (XAException e) {
              heuristicExn = heuristicException(heuristicExn, e);
              rollbackInt();
              throw new RollbackExceptionWrapper(L.l("all commits rolled back"),
                                                 heuristicExn);
            }
          }
        }

        if (hasPrepare && xaLog != null) {
          _xaLog = xaLog;

          xaLog.writeTMCommit(_xid);
        }

        _status = Status.STATUS_COMMITTING;

        if (allowSinglePhase) {
          try {
            XAResource resource = (XAResource) _resources[0];

            if ((_resourceState[0] & RES_COMMIT) != 0)
              resource.commit(_xid, true);

            if (_timeout > 0)
              resource.setTransactionTimeout(0);
          } catch (XAException e) {
            log.log(Level.FINE, e.toString(), e);

            heuristicExn = heuristicException(heuristicExn, e);
          }
        }

        for (int i = 0; i < _resourceCount; i++) {
          XAResource resource = (XAResource) _resources[i];

          if (i == 0 && allowSinglePhase)
            continue;

          if ((_resourceState[i] & RES_SHARED_RM) != 0)
            continue;
          if ((_resourceState[i] & RES_COMMIT) == 0)
            continue;

          if (heuristicExn == null) {
            try {
              resource.commit(_resourceXid[i], false);

              if (_timeout > 0)
                resource.setTransactionTimeout(0);
            } catch (XAException e) {
              heuristicExn = e;
              log.log(Level.FINE, e.toString(), e);
            }
          }
          else {
            try {
              resource.rollback(_resourceXid[i]);

              if (_timeout > 0)
                resource.setTransactionTimeout(0);
            } catch (XAException e) {
              log.log(Level.FINE, e.toString(), e);
            }
          }
        }
      }

      if (heuristicExn != null && log.isLoggable(Level.FINE))
        log.fine("failed Transaction[" + _xid + "]: " + heuristicExn);

      if (heuristicExn == null)
        _status = Status.STATUS_COMMITTED;
      else if (heuristicExn instanceof RollbackException) {
        _status = Status.STATUS_ROLLEDBACK;
        throw (RollbackException) heuristicExn;
      }
      else if (heuristicExn instanceof HeuristicMixedException) {
        _status = Status.STATUS_ROLLEDBACK;
        throw (HeuristicMixedException) heuristicExn;
      }
      else if (heuristicExn instanceof HeuristicRollbackException) {
        _status = Status.STATUS_ROLLEDBACK;
        throw (HeuristicRollbackException) heuristicExn;
      }
      else if (heuristicExn instanceof SystemException) {
        _status = Status.STATUS_ROLLEDBACK;
        throw (SystemException) heuristicExn;
      }
      else {
        _status = Status.STATUS_ROLLEDBACK;
        throw RollbackExceptionWrapper.create(heuristicExn);
      }
    } finally {
      callAfterCompletion();
    }
  }

  /**
   * Rollback the transaction.
   */
  public void rollback()
  {
    _alarm.dequeue();

    try {
      callBeforeCompletion();
    } catch (Throwable e) {
      setRollbackOnly(e);
    }

    try {
      switch (_status) {
      case Status.STATUS_ACTIVE:
      case Status.STATUS_MARKED_ROLLBACK:
        // fall through to normal completion
        break;

      case Status.STATUS_NO_TRANSACTION:
        throw new IllegalStateException(L.l("Can't rollback outside of a transaction.  Either the UserTransaction.begin() is missing or the transaction has already been committed or rolled back."));

      default:
        rollbackInt();
        throw new IllegalStateException(L.l("Can't rollback in state: {0}", String.valueOf(_status)));
      }

      _status = Status.STATUS_MARKED_ROLLBACK;

      rollbackInt();
    } finally {
      callAfterCompletion();
    }
  }

  /**
   * Calculates the heuristic exception based on the resource manager's
   * exception.
   */
  private Exception heuristicException(Exception oldException,
                                       XAException xaException)
  {
    switch (xaException.errorCode) {
    case XAException.XA_HEURHAZ:
    case XAException.XA_HEURCOM:
      return oldException;

    case XAException.XA_HEURRB:
      if (oldException instanceof HeuristicMixedException)
        return oldException;
      else if (oldException instanceof HeuristicRollbackException)
        return oldException;
      else if (oldException instanceof RollbackException)
        return oldException;
      else
        return new HeuristicRollbackException(getXAMessage(xaException));

    default:
      if (oldException instanceof SystemException)
        return oldException;
      else
        return new SystemExceptionWrapper(getXAMessage(xaException),
                                          xaException);
    }
  }

  /**
   * Rollback the transaction.
   */
  private void rollbackInt()
  {
    _status = Status.STATUS_ROLLING_BACK;

    if (log.isLoggable(Level.FINE))
      log.fine("rollback Transaction[" + _xid + "]");

    for (int i = 0; i < _resourceCount; i++) {
      XAResource resource = (XAResource) _resources[i];

      if ((_resourceState[i] & RES_SHARED_RM) != 0)
        continue;

      try {
        resource.rollback(_resourceXid[i]);

        if (_timeout > 0)
          resource.setTransactionTimeout(0);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    _status = Status.STATUS_ROLLEDBACK;
  }

  /**
   * Call all the Synchronization listeners before the commit()/rollback()
   * starts.
   */
  private void callBeforeCompletion()
    throws RollbackException
  {
    int length = _syncList == null ? 0 : _syncList.size();

    for (int i = 0; i < length; i++) {
      Synchronization sync = _syncList.get(i);

      try {
        sync.beforeCompletion();
      } catch (RuntimeException e) {
        throw new RollbackException(e);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    // tell the resources everything's done
    for (int i = _resourceCount -  1; i >= 0; i--) {
      XAResource resource = _resources[i];

      int flag;

      if (_status == Status.STATUS_MARKED_ROLLBACK)
        flag = XAResource.TMFAIL;
      else
        flag = XAResource.TMSUCCESS;

      try {
        if ((_resourceState[i] & RES_ACTIVE) != 0)
          resource.end(_resourceXid[i], flag);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
        setRollbackOnly(e);
      }
    }
  }

  /**
   * Call all the Synchronization listeners after the commit()/rollback()
   * complete.
   */
  private void callAfterCompletion()
  {
    ArrayList<Synchronization> syncList = _syncList;
    _syncList = null;

    _userTransaction = null;

    XidImpl xid = _xid;
    _xid = null;

    int status = _status;
    _status = Status.STATUS_NO_TRANSACTION;

    _rollbackException = null;

    // remove the resources which have officially delisted
    for (int i = _resourceCount - 1; i >= 0; i--)
      _resources[i] = null;

    _resourceCount = 0;

    AbstractXALogStream xaLog = _xaLog;
    _xaLog = null;

    if (xaLog != null) {
      try {
        xaLog.writeTMFinish(xid);
      } catch (Throwable e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }

    int length = syncList == null ? 0 : syncList.size();
    for (int i = 0; i < length; i++) {
      Synchronization sync = (Synchronization) syncList.get(i);

      try {
        sync.afterCompletion(status);
      } catch (Throwable e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    if (_props != null)
      _props.clear();
  }

  /**
   * sets the timeout for the transaction
   */
  public void setTransactionTimeout(int seconds)
    throws SystemException
  {
    if (seconds == 0)
      _timeout = _manager.getTimeout();
    else if (seconds < 0)
      _timeout = MAX_TIMEOUT;
    else {
      _timeout = 1000L * (long) seconds;
    }
  }

  /**
   * sets the timeout for the transaction
   */
  public int getTransactionTimeout()
    throws SystemException
  {
    if (_timeout < 0)
      return -1;
    else
      return (int) (_timeout / 1000L);
  }

  public void handleAlarm(Alarm alarm)
  {
    try {
      log.warning(L.l("{0}: timed out after {1} seconds.",
                      this,
                      String.valueOf(getTransactionTimeout())));

      setRollbackOnly();

      // should not close at this point because there could be following
      // statements that also need to be rolled back
      // server/16a7
      // close();
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }
  }

  /**
   * Close the transaction, rolling back everything and removing all
   * enlisted resources.
   */
  public void close()
  {
    _isDead = true;
    _alarm.dequeue();

    try {
      if (_status != Status.STATUS_NO_TRANSACTION)
        rollback();
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }

    if (_syncList != null)
      _syncList.clear();

    for (int i = _resourceCount - 1; i >= 0; i--)
      _resources[i] = null;

    _resourceCount = 0;

    _xid = null;
  }

  /**
   * Printable version of the transaction.
   */
  public String toString()
  {
    if (_xid == null)
      return "Transaction[]";

    CharBuffer cb = new CharBuffer();

    cb.append("Transaction[");

    byte []branch = _xid.getBranchQualifier();

    addByte(cb, branch[0]);

    cb.append(":");

    byte []global = _xid.getGlobalTransactionId();
    for (int i = 24; i < 28; i++)
      addByte(cb, global[i]);

    cb.append("]");

    return cb.toString();
  }

  /**
   * Adds hex for debug
   */
  private void addByte(CharBuffer cb, int b)
  {
    int h = (b / 16) & 0xf;
    int l = b & 0xf;

    if (h >= 10)
      cb.append((char) ('a' + h - 10));
    else
      cb.append((char) ('0' + h));

    if (l >= 10)
      cb.append((char) ('a' + l - 10));
    else
      cb.append((char) ('0' + l));
  }

  /**
   * Converts XA error code to a message.
   */
  private static String getXAMessage(XAException xaException)
  {
    if (xaException.getMessage() != null &&
        ! xaException.getMessage().equals(""))
      return xaException.getMessage();
    else
      return (xaName(xaException.errorCode) + ": " +
              xaMessage(xaException.errorCode));
  }

  /**
   * Converts XA state code to string.
   */
  private static String xaState(int xaState)
  {
    switch (xaState) {
    case Status.STATUS_ACTIVE:
      return "ACTIVE";
    case Status.STATUS_MARKED_ROLLBACK:
      return "MARKED-ROLLBACK";
    case Status.STATUS_PREPARED:
      return "PREPARED";
    case Status.STATUS_COMMITTED:
      return "COMITTED";
    case Status.STATUS_ROLLEDBACK:
      return "ROLLEDBACK";
    case Status.STATUS_PREPARING:
      return "PREPARING";
    case Status.STATUS_COMMITTING:
      return "COMMITTING";
    case Status.STATUS_ROLLING_BACK:
      return "ROLLING_BACK";
    default:
      return "XA-STATE(" + xaState + ")";
    }
  }

  /**
   * Converts XA error code to string.
   */
  private static String xaName(int xaCode)
  {
    switch (xaCode) {
      // rollback codes
    case XAException.XA_RBROLLBACK:
      return "XA_RBROLLBACK";
    case XAException.XA_RBCOMMFAIL:
      return "XA_RBCOMMFAIL";
    case XAException.XA_RBDEADLOCK:
      return "XA_RBDEADLOCK";
    case XAException.XA_RBINTEGRITY:
      return "XA_RBINTEGRITY";
    case XAException.XA_RBOTHER:
      return "XA_RBOTHER";
    case XAException.XA_RBPROTO:
      return "XA_RBPROTO";
    case XAException.XA_RBTIMEOUT:
      return "XA_RBTIMEOUT";
    case XAException.XA_RBTRANSIENT:
      return "XA_RBTRANSIENT";

      // suspension code
    case XAException.XA_NOMIGRATE:
      return "XA_NOMIGRATE";

      // heuristic completion codes
    case XAException.XA_HEURHAZ:
      return "XA_HEURHAZ";
    case XAException.XA_HEURCOM:
      return "XA_HEURCOM";
    case XAException.XA_HEURRB:
      return "XA_HEURRB";
    case XAException.XA_HEURMIX:
      return "XA_HEURMIX";
    case XAException.XA_RDONLY:
      return "XA_RDONLY";

      // errors
    case XAException.XAER_RMERR:
      return "XA_RMERR";
    case XAException.XAER_NOTA:
      return "XA_NOTA";
    case XAException.XAER_INVAL:
      return "XA_INVAL";
    case XAException.XAER_PROTO:
      return "XA_PROTO";
    case XAException.XAER_RMFAIL:
      return "XA_RMFAIL";
    case XAException.XAER_DUPID:
      return "XA_DUPID";
    case XAException.XAER_OUTSIDE:
      return "XA_OUTSIDE";

    default:
      return "XA(" + xaCode + ")";
    }
  }

  /**
   * Converts XA error code to a message.
   */
  private static String xaMessage(int xaCode)
  {
    switch (xaCode) {
      // rollback codes
    case XAException.XA_RBROLLBACK:
    case XAException.XA_RBOTHER:
      return L.l("Resource rolled back for an unspecified reason.");
    case XAException.XA_RBCOMMFAIL:
      return L.l("Resource rolled back because of a communication failure.");
    case XAException.XA_RBDEADLOCK:
      return L.l("Resource rolled back because of a deadlock.");
    case XAException.XA_RBINTEGRITY:
      return L.l("Resource rolled back because of an integrity check failure.");
    case XAException.XA_RBPROTO:
      return L.l("Resource rolled back because of a protocol error in the resource manager.");
    case XAException.XA_RBTIMEOUT:
      return L.l("Resource rolled back because of a timeout.");
    case XAException.XA_RBTRANSIENT:
      return L.l("Resource rolled back because of transient error.");

      // suspension code
    case XAException.XA_NOMIGRATE:
      return L.l("Resumption must occur where the suspension occurred.");

      // heuristic completion codes
    case XAException.XA_HEURHAZ:
      return L.l("Resource may have been heuristically completed.");
    case XAException.XA_HEURCOM:
      return L.l("Resource has been heuristically committed.");
    case XAException.XA_HEURRB:
      return L.l("Resource has been heuristically rolled back.");
    case XAException.XA_HEURMIX:
      return L.l("Resource has been heuristically committed and rolled back.");
    case XAException.XA_RDONLY:
      return L.l("Resource was read-only and has been heuristically committed.");

      // errors
    case XAException.XAER_RMERR:
      return L.l("Resource manager error.");
    case XAException.XAER_NOTA:
      return L.l("The XID (transaction identifier) was invalid.");
    case XAException.XAER_INVAL:
      return L.l("Invalid arguments were given.");
    case XAException.XAER_PROTO:
      return L.l("Method called in an invalid context.");
    case XAException.XAER_RMFAIL:
      return L.l("Resource manager is unavailable.");
    case XAException.XAER_DUPID:
      return L.l("Duplicate XID (transaction identifier).");
    case XAException.XAER_OUTSIDE:
      return L.l("Resource manager called outside of transaction.");

    default:
      return "";
    }
  }
}
