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

package com.caucho.db.index;

import com.caucho.db.Database;
import com.caucho.db.store.Block;
import com.caucho.db.store.BlockManager;
import com.caucho.db.store.Lock;
import com.caucho.db.store.Store;
import com.caucho.db.store.Transaction;
import com.caucho.sql.SQLExceptionWrapper;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Structure of the table:
 *
 * <pre>
 * b4 - flags
 * b4 - length
 * b8 - parent
 * b8 - next
 * tuples*
 * </pre>
 * 
 * Structure of a tuple:
 *
 * <pre>
 * b8  - ptr to the actual data
 * key - the tuple's key
 * </pre>
 */
public final class BTree {
  private final static L10N L = new L10N(BTree.class);
  private final static Logger log
    = Logger.getLogger(BTree.class.getName());
  
  public final static long FAIL = 0;
  private final static int BLOCK_SIZE = Store.BLOCK_SIZE;
  private final static int PTR_SIZE = 8;

  private final static int FLAGS_OFFSET = 0;
  private final static int LENGTH_OFFSET = FLAGS_OFFSET + 4;
  private final static int PARENT_OFFSET = LENGTH_OFFSET + 4;
  private final static int NEXT_OFFSET = PARENT_OFFSET + PTR_SIZE;
  private final static int HEADER_SIZE = NEXT_OFFSET + PTR_SIZE;

  private final static int LEAF_FLAG = 1;

  private BlockManager _blockManager;

  private final Lock _lock;
  private Store _store;
  
  private long _rootBlockId;
  private Block _rootBlock;
  
  private int _keySize;
  private int _tupleSize;
  private int _n;
  private int _minN;
  
  private KeyCompare _keyCompare;

  private int _blockCount;

  private volatile boolean _isStarted;

  /**
   * Creates a new BTree with the given backing.
   *
   * @param store the underlying store containing the btree.
   */
  public BTree(Store store,
	       long rootBlockId,
	       int keySize,
	       KeyCompare keyCompare)
    throws IOException
  {
    if (keyCompare == null)
      throw new NullPointerException();
    
    _store = store;
    _blockManager = _store.getBlockManager();
    
    _rootBlockId = rootBlockId;
    _rootBlock = store.readBlock(rootBlockId);
      
    _lock = new Lock("index:" + store.getName());
    
    if (BLOCK_SIZE < keySize + HEADER_SIZE)
      throw new IOException(L.l("BTree key size `{0}' is too large.",
                                keySize));

    _keySize = keySize;

    _tupleSize = keySize + PTR_SIZE;

    _n = (BLOCK_SIZE - HEADER_SIZE) / _tupleSize;
    _minN = (_n + 1) / 2;
    if (_minN < 0)
      _minN = 1;

    _keyCompare = keyCompare;
  }

  /**
   * Returns the index root.
   */
  public long getIndexRoot()
  {
    return _rootBlockId;
  }

  /**
   * Creates and initializes the btree.
   */
  public void create()
    throws IOException
  {
  }
  
  public long lookup(byte []keyBuffer,
		     int keyOffset,
		     int keyLength,
		     Transaction xa)
    throws IOException, SQLException
  {
    return lookup(keyBuffer, keyOffset, keyLength, xa, _rootBlockId);
  }
  
  private long lookup(byte []keyBuffer,
		     int keyOffset,
		     int keyLength,
		     Transaction xa,
		     long blockId)
    throws IOException, SQLException
  {
    Block block;

    if (blockId == _rootBlockId) {
      block = _rootBlock;
      block.allocate();
    }
    else
      block = _store.readBlock(blockId);

    try {
      Lock blockLock = block.getLock();
      xa.lockRead(blockLock);

      try {
	byte []buffer = block.getBuffer();

	boolean isLeaf = isLeaf(buffer);
      
	long value = lookupTuple(blockId, buffer,
				 keyBuffer, keyOffset, keyLength,
				 isLeaf);

	if (isLeaf || value == FAIL)
	  return value;
	else
	  return lookup(keyBuffer, keyOffset, keyLength,
			xa, value);
      } finally {
        xa.unlockRead(blockLock);
      }
    } finally {
      block.free();
    }
  }
  
  /**
   * Inserts the new value for the given key.
   *
   * @return false if the block needs to be split
   */
  public void insert(byte []keyBuffer,
		     int keyOffset,
		     int keyLength,
		     long value,
		     Transaction xa,
		     boolean isOverride)
    throws SQLException
  {
    try {
      while (! insert(keyBuffer, keyOffset, keyLength,
		      value, xa, isOverride,
		      _rootBlockId)) {
	splitRoot(_rootBlockId, xa);
      }
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      
      throw new SQLExceptionWrapper(e.toString(), e);
    }
  }

  /**
   * Inserts the new value for the given key.
   *
   * @return false if the block needs to be split
   */
  private boolean insert(byte []keyBuffer,
			 int keyOffset,
			 int keyLength,
			 long value,
			 Transaction xa,
			 boolean isOverride,
			 long blockId)
    throws IOException, SQLException
  {
    Block block;

    if (blockId == _rootBlockId) {
      block = _rootBlock;
      block.allocate();
    }
    else
      block = _store.readBlock(blockId);
    
    try {
      Lock blockLock = block.getLock();
      xa.lockRead(blockLock);
      
      try {
	byte []buffer = block.getBuffer();

	int length = getLength(buffer);

	if (length == _n) {
	  // return false if the block needs to be split
	  return false;
	}
	
	if (isLeaf(buffer)) {
	  insertValue(keyBuffer, keyOffset, keyLength,
		      value, xa, isOverride, block);

	  return true;
	}

	long childBlockId = lookupTuple(blockId, buffer,
					keyBuffer, keyOffset, keyLength,
					false);

	while (! insert(keyBuffer, keyOffset, keyLength,
			value, xa, isOverride,
			childBlockId)) {
	  split(block, childBlockId, xa);

	  childBlockId = lookupTuple(blockId, buffer,
				     keyBuffer, keyOffset, keyLength,
				     false);
	}

	return true;
      } finally {
	xa.unlockRead(blockLock);
      }
    } finally {
      block.free();
    }
  }
    
  /**
   * Inserts into the next block given the current block and the given key.
   */
  private void insertValue(byte []keyBuffer,
			   int keyOffset,
			   int keyLength,
			   long value,
			   Transaction xa,
			   boolean isOverride,
			   Block block)
    throws IOException, SQLException
  {
    byte []buffer = block.getBuffer();

    Lock blockLock = block.getLock();
    xa.lockWrite(blockLock);
    try {
      block.setFlushDirtyOnCommit(false);
      block.setDirty(0, Store.BLOCK_SIZE);
	    
      insertLeafBlock(block.getBlockId(), buffer,
		      keyBuffer, keyOffset, keyLength,
		      value,
		      isOverride);
    } finally {
      xa.unlockWrite(blockLock);
    }
  }

  /**
   * Inserts into the next block given the current block and the given key.
   */
  private long insertLeafBlock(long blockId,
                               byte []buffer,
                               byte []keyBuffer,
                               int keyOffset,
                               int keyLength,
                               long value,
			       boolean isOverride)
    throws IOException, SQLException
  {
    int offset = HEADER_SIZE;
    int tupleSize = _tupleSize;
    int length = getLength(buffer);

    for (int i = 0; i < length; i++) {
      int cmp = _keyCompare.compare(keyBuffer, keyOffset,
				    buffer, offset + PTR_SIZE,
				    keyLength);

      if (0 < cmp) {
        offset += tupleSize;
        continue;
      }
      else if (cmp == 0) {
	if (! isOverride) {
	  long oldValue = getPointer(buffer, offset);

	  if (value != oldValue)
	    throw new SqlIndexAlreadyExistsException(L.l("'{0}' insert of key '{1}' fails index uniqueness.",
				       _store,
				       _keyCompare.toString(keyBuffer, keyOffset, keyLength)));
	}
	
        setPointer(buffer, offset, value);
        //writeBlock(blockIndex, block);
        
        return 0;
      }
      else if (length < _n) {
        return addKey(blockId, buffer, offset, i, length,
                      keyBuffer, keyOffset, keyLength, value);
      }
      else {
	throw new IllegalStateException("ran out of key space");
      }
    }

    if (length < _n) {
      return addKey(blockId, buffer, offset, length, length,
                    keyBuffer, keyOffset, keyLength, value);
    }

    throw new IllegalStateException();

    // return split(blockIndex, block);
  }

  private long addKey(long blockId, byte []buffer, int offset,
		      int index, int length,
                      byte []keyBuffer, int keyOffset, int keyLength,
                      long value)
    throws IOException
  {
    int tupleSize = _tupleSize;

    if (index < length) {
      if (offset + tupleSize < HEADER_SIZE)
	throw new IllegalStateException();
      
      System.arraycopy(buffer, offset,
                       buffer, offset + tupleSize,
                       (length - index) * tupleSize);
    }
    
    setPointer(buffer, offset, value);
    setLength(buffer, length + 1);

    if (log.isLoggable(Level.FINEST))
      log.finest("btree insert at " + debugId(blockId) + ":" + offset + " value:" + debugId(value));

    if (offset + PTR_SIZE < HEADER_SIZE)
      throw new IllegalStateException();
      
    System.arraycopy(keyBuffer, keyOffset,
		     buffer, offset + PTR_SIZE,
		     keyLength);
          
    for (int j = PTR_SIZE + keyLength; j < tupleSize; j++)
      buffer[offset + j] = 0;

    return -value;
  }

  /**
   * The length in lBuf is assumed to be the length of the buffer.
   */
  private void split(Block parent,
		     long blockId,
		     Transaction xa)
    throws IOException, SQLException
  {
    Lock parentLock = parent.getLock();
    xa.lockWrite(parentLock);
    
    try {
      Block block = _store.readBlock(blockId);

      try {
	Lock blockLock = block.getLock();
	xa.lockReadAndWrite(blockLock);
	
	try {
	  split(parent, block, xa);
	} finally {
	  xa.unlockReadAndWrite(blockLock);
	}
      } finally {
	block.free();
      }
    } finally {
      xa.unlockWrite(parentLock);
    }
  }

  /**
   * The length in lBuf is assumed to be the length of the buffer.
   */
  private void split(Block parentBlock,
		     Block block,
		     Transaction xa)
    throws IOException, SQLException
  {
    long parentId = parentBlock.getBlockId();
    long blockId = block.getBlockId();
    
    log.finer("btree splitting " + debugId(blockId));
    
    block.setFlushDirtyOnCommit(false);
    block.setDirty(0, Store.BLOCK_SIZE);

    byte []buffer = block.getBuffer();
    int length = getLength(buffer);

    // Check length to avoid possible timing issue, since we release the
    // read lock for the block between the initial check in insert() and
    // getting it back in split()
    if (length < _n / 2)
      return;

    if (length < 2)
      throw new IllegalStateException(L.l("illegal length '{0}' for block {1}",
					  length, debugId(blockId)));
      
    //System.out.println("INDEX SPLIT: " + debugId(blockId) + " " + length + " " + block + " " + buffer);

    Block leftBlock = null;

    try {
      parentBlock.setFlushDirtyOnCommit(false);
      parentBlock.setDirty(0, Store.BLOCK_SIZE);
    
      byte []parentBuffer = parentBlock.getBuffer();
      int parentLength = getLength(parentBuffer);
    
      leftBlock = _store.allocateIndexBlock();
      leftBlock.setFlushDirtyOnCommit(false);
      leftBlock.setDirty(0, Store.BLOCK_SIZE);
      
      byte []leftBuffer = leftBlock.getBuffer();
      long leftBlockId = leftBlock.getBlockId();

      int pivot = length / 2;

      int pivotSize = pivot * _tupleSize;
      int pivotEnd = HEADER_SIZE + pivotSize;

      System.arraycopy(buffer, HEADER_SIZE,
		       leftBuffer, HEADER_SIZE,
		       pivotSize);

      setInt(leftBuffer, FLAGS_OFFSET, getInt(buffer, FLAGS_OFFSET));
      setLength(leftBuffer, pivot);
      // XXX: NEXT_OFFSET needs to work with getRightIndex
      setPointer(leftBuffer, NEXT_OFFSET, 0);
      setPointer(leftBuffer, PARENT_OFFSET, parentId);

      System.arraycopy(buffer, pivotEnd,
		       buffer, HEADER_SIZE,
		       length * _tupleSize - pivotEnd);

      setLength(buffer, length - pivot);

      insertLeafBlock(parentId, parentBuffer,
		      leftBuffer, pivotEnd - _tupleSize + PTR_SIZE, _keySize,
		      leftBlockId,
		      true);
    } finally {
      if (leftBlock != null)
	leftBlock.free();
    }
  }

  /**
   * The length in lBuf is assumed to be the length of the buffer.
   */
  private void splitRoot(long rootBlockId,
			 Transaction xa)
    throws IOException, SQLException
  {
    Block rootBlock = _rootBlock; // store.readBlock(rootBlockId);
    rootBlock.allocate();

    try {
      Lock rootLock = rootBlock.getLock();
      xa.lockReadAndWrite(rootLock);
      
      try {
	splitRoot(rootBlock, xa);
      } finally {
	xa.unlockReadAndWrite(rootLock);
      }
    } finally {
      rootBlock.free();
    }
  }

  /**
   * Splits the current leaf into two.  Half of the entries go to the
   * left leaf and half go to the right leaf.
   */
  private void splitRoot(Block parentBlock, Transaction xa)
    throws IOException
  {
    long parentId = parentBlock.getBlockId();
    
    //System.out.println("INDEX SPLIT ROOT: " + (parentId / BLOCK_SIZE));
    log.finer("btree splitting root " + (parentId / BLOCK_SIZE));

    Block leftBlock = null;
    Block rightBlock = null;

    try {
      parentBlock.setFlushDirtyOnCommit(false);
      parentBlock.setDirty(0, Store.BLOCK_SIZE);

      byte []parentBuffer = parentBlock.getBuffer();

      int parentFlags = getInt(parentBuffer, FLAGS_OFFSET);

      leftBlock = _store.allocateIndexBlock();
      leftBlock.setFlushDirtyOnCommit(false);
      leftBlock.setDirty(0, Store.BLOCK_SIZE);
      
      long leftBlockId = leftBlock.getBlockId();
    
      rightBlock = _store.allocateIndexBlock();
      rightBlock.setFlushDirtyOnCommit(false);
      rightBlock.setDirty(0, Store.BLOCK_SIZE);
      
      long rightBlockId = rightBlock.getBlockId();

      int length = getLength(parentBuffer);

      int pivot = (length - 1) / 2;

      if (length <= 2 || _n < length || pivot < 1 || length <= pivot)
	throw new IllegalStateException(length + " is an illegal length, or pivot " + pivot + " is bad, with n=" + _n);

      int pivotOffset = HEADER_SIZE + pivot * _tupleSize;
      long pivotValue = getPointer(parentBuffer, pivotOffset);

      byte []leftBuffer = leftBlock.getBuffer();

      System.arraycopy(parentBuffer, HEADER_SIZE,
		       leftBuffer, HEADER_SIZE,
		       pivotOffset + _tupleSize - HEADER_SIZE);
      setInt(leftBuffer, FLAGS_OFFSET, parentFlags);
      setLength(leftBuffer, pivot + 1);
      setPointer(leftBuffer, PARENT_OFFSET, parentId);
      setPointer(leftBuffer, NEXT_OFFSET, rightBlockId);

      byte []rightBuffer = rightBlock.getBuffer();

      if (length - pivot - 1 < 0)
	throw new IllegalStateException("illegal length " + pivot + " " + length);

      System.arraycopy(parentBuffer, pivotOffset + _tupleSize,
		       rightBuffer, HEADER_SIZE,
		       (length - pivot - 1) * _tupleSize);

      setInt(rightBuffer, FLAGS_OFFSET, parentFlags);
      setLength(rightBuffer, length - pivot - 1);
      setPointer(rightBuffer, PARENT_OFFSET, parentId);
      setPointer(rightBuffer, NEXT_OFFSET,
		 getPointer(parentBuffer, NEXT_OFFSET));

      System.arraycopy(parentBuffer, pivotOffset,
		       parentBuffer, HEADER_SIZE,
		       _tupleSize);
      setPointer(parentBuffer, HEADER_SIZE, leftBlockId);

      setInt(parentBuffer, FLAGS_OFFSET, LEAF_FLAG);
      setLength(parentBuffer, 1);
      setPointer(parentBuffer, NEXT_OFFSET, rightBlockId);
    } finally {
      if (leftBlock != null)
	leftBlock.free();
      
      if (rightBlock != null)
	rightBlock.free();
    }
  }
  
  public void remove(byte []keyBuffer,
		      int keyOffset,
		      int keyLength,
		      Transaction xa)
    throws SQLException
  {
    try {
      Block rootBlock = _rootBlock; // _store.readBlock(_rootBlockId);
      rootBlock.allocate();

      try {
	Lock rootLock = rootBlock.getLock();
	xa.lockRead(rootLock);

	try {
	  remove(rootBlock, keyBuffer, keyOffset, keyLength, xa);
	} finally {
	  xa.unlockRead(rootLock);
	}
      } finally {
	rootBlock.free();
      }
    } catch (IOException e) {
      throw new SQLExceptionWrapper(e.toString(), e);
    }
  }

  /**
   * Recursively remove a key from the index.
   *
   * block is read-locked by the parent.
   */
  private boolean remove(Block block,
			 byte []keyBuffer,
			 int keyOffset,
			 int keyLength,
			 Transaction xa)
    throws IOException, SQLException
  {
    byte []buffer = block.getBuffer();
    long blockId = block.getBlockId();

    boolean isLeaf = isLeaf(buffer);
      
    if (isLeaf) {
      Lock blockLock = block.getLock();
      
      xa.lockWrite(blockLock);

      try {
	block.setFlushDirtyOnCommit(false);
	block.setDirty(0, Store.BLOCK_SIZE);

	removeLeafEntry(blockId, buffer,
			keyBuffer, keyOffset, keyLength);
      } finally {
	xa.unlockWrite(blockLock);
      }
    }
    else {
      long childId;
	
      childId = lookupTuple(blockId, buffer,
			    keyBuffer, keyOffset, keyLength,
			    isLeaf);

      if (childId == FAIL)
	return true;

      Block childBlock = _store.readBlock(childId);
      try {
	boolean isJoin = false;

	Lock childLock = childBlock.getLock();
	xa.lockRead(childLock);

	try {
	  isJoin = ! remove(childBlock,
			    keyBuffer, keyOffset, keyLength,
			    xa);
	} finally {
	  xa.unlockRead(childLock);
	}

	if (isJoin) {
	  if (joinBlocks(block, childBlock, xa)) {
	    xa.deallocateBlock(childBlock);
	  }
	}
      } finally {
	childBlock.free();
      }
    }
      
    return _minN <= getLength(buffer);
  }

  /**
   * Performs any block-merging cleanup after the delete.
   *
   * parent is read-locked by the parent.
   * block is not locked.
   *
   * @return true if the block should be deleted/freed
   */
  private boolean joinBlocks(Block parent,
			     Block block,
			     Transaction xa)
    throws IOException, SQLException
  {
    byte []parentBuffer = parent.getBuffer();
    int parentLength = getLength(parentBuffer);

    long blockId = block.getBlockId();
    byte []buffer = block.getBuffer();
    
    //System.out.println("INDEX JOIN: " + debugId(blockId));

    Lock parentLock = parent.getLock();
    xa.lockWrite(parentLock);
    try {
      long leftBlockId = getLeftBlockId(parent, blockId);
      long rightBlockId = getRightBlockId(parent, blockId);

      // try to shift from left and right first
      if (leftBlockId > 0) {
	Block leftBlock = _store.readBlock(leftBlockId);

	try {
	  byte []leftBuffer = leftBlock.getBuffer();
	
	  Lock leftLock = leftBlock.getLock();
	  xa.lockReadAndWrite(leftLock);
	  
	  try {
	    int leftLength = getLength(leftBuffer);

	    Lock blockLock = block.getLock();
	    xa.lockReadAndWrite(blockLock);
	    
	    try {
	      if (_minN < leftLength
		  && isLeaf(buffer) == isLeaf(leftBuffer)) {
		parent.setFlushDirtyOnCommit(false);
		parent.setDirty(0, Store.BLOCK_SIZE);
	    
		leftBlock.setFlushDirtyOnCommit(false);
		leftBlock.setDirty(0, Store.BLOCK_SIZE);
	  
		//System.out.println("MOVE_FROM_LEFT: " + debugId(blockId) + " from " + debugId(leftBlockId));
		moveFromLeft(parentBuffer, leftBuffer, buffer, blockId);

		return false;
	      }
	    } finally {
	      xa.unlockReadAndWrite(blockLock);
	    }
	  } finally {
	    xa.unlockReadAndWrite(leftLock);
	  }
	} finally {
	  leftBlock.free();
	}
      }

      if (rightBlockId > 0) {
	Block rightBlock = _store.readBlock(rightBlockId);

	try {
	  byte []rightBuffer = rightBlock.getBuffer();
	
	  Lock blockLock = block.getLock();
	  xa.lockReadAndWrite(blockLock);

	  try {
	    Lock rightLock = rightBlock.getLock();
	    xa.lockReadAndWrite(rightLock);
	      
	    try {
	      int rightLength = getLength(rightBuffer);

	      if (_minN < rightLength
		  && isLeaf(buffer) == isLeaf(rightBuffer)) {
		parent.setFlushDirtyOnCommit(false);
		parent.setDirty(0, Store.BLOCK_SIZE);
	    
		rightBlock.setFlushDirtyOnCommit(false);
		rightBlock.setDirty(0, Store.BLOCK_SIZE);

		//System.out.println("MOVE_FROM_RIGHT: " + debugId(blockId) + " from " + debugId(rightBlockId));
	    
		moveFromRight(parentBuffer, buffer, rightBuffer, blockId);

		return false;
	      }
	    } finally {
	      xa.unlockReadAndWrite(rightLock);
	    }
	  } finally {
	    xa.unlockReadAndWrite(blockLock);
	  }
	} finally {
	  rightBlock.free();
	}
      }

      if (parentLength < 2)
	return false;
    
      if (leftBlockId > 0) {
	Block leftBlock = _store.readBlock(leftBlockId);
      
	try {
	  byte []leftBuffer = leftBlock.getBuffer();
	  
	  Lock leftLock = leftBlock.getLock();
	  xa.lockReadAndWrite(leftLock);

	  try {
	    int leftLength = getLength(leftBuffer);
	    
	    Lock blockLock = block.getLock();
	    xa.lockReadAndWrite(blockLock);

	    try {
	      int length = getLength(buffer);
	      
	      if (isLeaf(leftBuffer) == isLeaf(buffer)
		  && length + leftLength <= _n) {
		parent.setFlushDirtyOnCommit(false);
		parent.setDirty(0, Store.BLOCK_SIZE);
	  
		leftBlock.setFlushDirtyOnCommit(false);
		leftBlock.setDirty(0, Store.BLOCK_SIZE);
      
		//System.out.println("MERGE_LEFT: " + debugId(blockId) + " from " + debugId(leftBlockId));
	    
		mergeLeft(parentBuffer, leftBuffer, buffer, blockId);

		return true;
	      }
	    } finally {
	      xa.unlockReadAndWrite(blockLock);
	    }
	  } finally {
	    xa.unlockReadAndWrite(leftLock);
	  }
	} finally {
	  leftBlock.free();
	}
      }
    
      if (rightBlockId > 0) {
	Block rightBlock = _store.readBlock(rightBlockId);

	try {
	  byte []rightBuffer = rightBlock.getBuffer();

	  Lock blockLock = block.getLock();
	  xa.lockReadAndWrite(blockLock);

	  try {
	    Lock rightLock = rightBlock.getLock();
	    xa.lockReadAndWrite(rightLock);

	    try {
	      int length = getLength(buffer);
	      int rightLength = getLength(rightBuffer);
	      
	      if (isLeaf(rightBuffer) == isLeaf(buffer)
		  && length + rightLength <= _n) {
		rightBlock.setFlushDirtyOnCommit(false);
		rightBlock.setDirty(0, Store.BLOCK_SIZE);
	  
		parent.setFlushDirtyOnCommit(false);
		parent.setDirty(0, Store.BLOCK_SIZE);
	  
		//System.out.println("MERGE_RIGHT: " + debugId(blockId) + " from " + debugId(rightBlockId));
	    
		mergeRight(parentBuffer, buffer, rightBuffer, blockId);

		return true;
	      }
	    } finally {
	      xa.unlockReadAndWrite(rightLock);
	    }
	  } finally {
	    xa.unlockReadAndWrite(blockLock);
	  }
	} finally {
	  rightBlock.free();
	}
      }

      return false;
    } finally {
      xa.unlockWrite(parentLock);
    }
  }

  /**
   * Returns the index to the left of the current one
   */
  private long getLeftBlockId(Block parent, long blockId)
  {
    byte []buffer = parent.getBuffer();
    
    int length = getLength(buffer);

    if (length < 1)
      throw new IllegalStateException("zero length for " + debugId(parent.getBlockId()));

    int offset = HEADER_SIZE;
    int tupleSize = _tupleSize;
    int end = offset + length * tupleSize;

    for (; offset < end; offset += tupleSize) {
      long pointer = getPointer(buffer, offset);

      if (pointer == blockId) {
	if (HEADER_SIZE < offset) {
	  return getPointer(buffer, offset - tupleSize);
	}
	else
	  return -1;
      }
    }
    
    long pointer = getPointer(buffer, NEXT_OFFSET);
    
    if (pointer == blockId)
      return getPointer(buffer, HEADER_SIZE + (length - 1) * tupleSize);
    else
      throw new IllegalStateException("Can't find " + debugId(blockId) + " in parent " + debugId(parent.getBlockId()));
  }

  /**
   * Takes the last entry from the left block and moves it to the
   * first entry in the current block.
   *
   * @param parentBuffer the parent block buffer
   * @param leftBuffer the left block buffer
   * @param buffer the block's buffer
   * @param index the index of the block
   */
  private void moveFromLeft(byte []parentBuffer,
			    byte []leftBuffer,
			    byte []buffer,
			    long blockId)
  {
    int parentLength = getLength(parentBuffer);

    int tupleSize = _tupleSize;
    int parentOffset = HEADER_SIZE;
    int parentEnd = parentOffset + parentLength * tupleSize;

    int leftLength = getLength(leftBuffer);

    int length = getLength(buffer);

    // pointer in the parent to the left defaults to the tail - 1
    int parentLeftOffset = -1;

    if (blockId == getPointer(parentBuffer, NEXT_OFFSET)) {
      // parentLeftOffset = parentOffset - tupleSize;
      parentLeftOffset = parentEnd - tupleSize;
    }
    else {
      for (parentOffset += tupleSize;
	   parentOffset < parentEnd;
	   parentOffset += tupleSize) {
	long pointer = getPointer(parentBuffer, parentOffset);

	if (pointer == blockId) {
	  parentLeftOffset = parentOffset - tupleSize;
	  break;
	}
      }
    }

    if (parentLeftOffset < 0) {
      log.warning("Can't find parent left in deletion borrow left ");
      return;
    }

    // shift the data in the buffer
    System.arraycopy(buffer, HEADER_SIZE,
		     buffer, HEADER_SIZE + tupleSize,
		     length * tupleSize);

    // copy the last item in the left to the buffer
    System.arraycopy(leftBuffer, HEADER_SIZE + (leftLength - 1) * tupleSize,
		     buffer, HEADER_SIZE,
		     tupleSize);

    // add the buffer length
    setLength(buffer, length + 1);

    // subtract from the left length
    leftLength -= 1;
    setLength(leftBuffer, leftLength);

    // copy the entry from the new left tail to the parent
    System.arraycopy(leftBuffer,
		     HEADER_SIZE + (leftLength - 1) * tupleSize + PTR_SIZE,
		     parentBuffer, parentLeftOffset + PTR_SIZE,
		     tupleSize - PTR_SIZE);
  }

  /**
   * Returns the index to the left of the current one
   */
  private void mergeLeft(byte []parentBuffer,
			 byte []leftBuffer,
			 byte []buffer,
			 long blockId)
  {
    int leftLength = getLength(leftBuffer);
    int length = getLength(buffer);

    int parentLength = getLength(parentBuffer);

    int tupleSize = _tupleSize;
    int parentOffset = HEADER_SIZE;
    int parentEnd = parentOffset + parentLength * tupleSize;

    for (parentOffset += tupleSize;
	 parentOffset < parentEnd;
	 parentOffset += tupleSize) {
      long pointer = getPointer(parentBuffer, parentOffset);

      if (pointer == blockId) {
	int leftOffset = HEADER_SIZE + leftLength * tupleSize;

	// copy the pointer from the left pointer
	setPointer(parentBuffer, parentOffset,
		   getPointer(parentBuffer, parentOffset - tupleSize));

	if (parentOffset - tupleSize < HEADER_SIZE)
	  throw new IllegalStateException();
		   
	// shift the parent
	System.arraycopy(parentBuffer, parentOffset,
			 parentBuffer, parentOffset - tupleSize,
			 parentEnd - parentOffset);
	setLength(parentBuffer, parentLength - 1);

	// the new left.next value is the buffer's next value
	setPointer(leftBuffer, NEXT_OFFSET,
		   getPointer(buffer, NEXT_OFFSET));

	if (leftOffset < HEADER_SIZE)
	  throw new IllegalStateException();
	
	// append the buffer to the left buffer
	// XXX: leaf vs non-leaf?
	System.arraycopy(buffer, HEADER_SIZE,
			 leftBuffer, leftOffset,
			 length * tupleSize);

	setLength(leftBuffer, leftLength + length);

	return;
      }
    }

    long pointer = getPointer(parentBuffer, NEXT_OFFSET);

    if (pointer != blockId) {
      log.warning("BTree remove can't find matching block: " + debugId(blockId));
      return;
    }
    
    int leftOffset = HEADER_SIZE + (parentLength - 1) * tupleSize;
    
    long leftPointer = getPointer(parentBuffer, leftOffset);

    setPointer(parentBuffer, NEXT_OFFSET, leftPointer);
    setLength(parentBuffer, parentLength - 1);

    // XXX: leaf vs non-leaf?
    
    // the new left.next value is the buffer's next value
    setPointer(leftBuffer, NEXT_OFFSET,
	       getPointer(buffer, NEXT_OFFSET));

    // append the buffer to the left buffer
    System.arraycopy(buffer, HEADER_SIZE,
		     leftBuffer, HEADER_SIZE + leftLength * tupleSize,
		     length * tupleSize);

    setLength(leftBuffer, leftLength + length);
  }

  /**
   * Returns the index to the right of the current one
   */
  private long getRightBlockId(Block parent, long blockId)
  {
    byte []buffer = parent.getBuffer();
    
    int length = getLength(buffer);

    int offset = HEADER_SIZE;
    int tupleSize = _tupleSize;
    int end = offset + length * tupleSize;

    for (; offset < end; offset += tupleSize) {
      long pointer = getPointer(buffer, offset);

      if (pointer == blockId) {
	if (offset + tupleSize < end) {
	  return getPointer(buffer, offset + tupleSize);
	}
	else
	  return getPointer(buffer, NEXT_OFFSET);
      }
    }

    return -1;
  }

  /**
   * Takes the first entry from the right block and moves it to the
   * last entry in the current block.
   *
   * @param parentBuffer the parent block buffer
   * @param rightBuffer the right block buffer
   * @param buffer the block's buffer
   * @param index the index of the block
   */
  private void moveFromRight(byte []parentBuffer,
			     byte []buffer,
			     byte []rightBuffer,
			     long blockId)
  {
    int parentLength = getLength(parentBuffer);

    int tupleSize = _tupleSize;
    int parentOffset = HEADER_SIZE;
    int parentEnd = parentOffset + parentLength * tupleSize;

    int rightLength = getLength(rightBuffer);

    int length = getLength(buffer);

    for (;
	 parentOffset < parentEnd;
	 parentOffset += tupleSize) {
      long pointer = getPointer(parentBuffer, parentOffset);

      if (pointer == blockId)
	break;
    }

    if (parentEnd <= parentOffset) {
      log.warning("Can't find buffer in deletion borrow right ");
      return;
    }

    // copy the first item in the right to the buffer
    System.arraycopy(rightBuffer, HEADER_SIZE,
		     buffer, HEADER_SIZE + length * tupleSize,
		     tupleSize);

    // shift the data in the right buffer
    System.arraycopy(rightBuffer, HEADER_SIZE + tupleSize,
		     rightBuffer, HEADER_SIZE,
		     (rightLength - 1) * tupleSize);

    // add the buffer length
    setLength(buffer, length + 1);

    // subtract from the right length
    setLength(rightBuffer, rightLength - 1);

    // copy the entry from the new buffer tail to the parent
    System.arraycopy(buffer,
		     HEADER_SIZE + length * tupleSize + PTR_SIZE,
		     parentBuffer, parentOffset + PTR_SIZE,
		     tupleSize - PTR_SIZE);
  }

  /**
   * Merges the buffer with the right-most one.
   */
  private void mergeRight(byte []parentBuffer,
			  byte []buffer,
			  byte []rightBuffer,
			  long blockId)
  {
    int parentLength = getLength(parentBuffer);

    int tupleSize = _tupleSize;
    int parentOffset = HEADER_SIZE;
    int parentEnd = parentOffset + parentLength * tupleSize;

    int rightLength = getLength(rightBuffer);

    int length = getLength(buffer);

    for (;
	 parentOffset < parentEnd;
	 parentOffset += tupleSize) {
      long pointer = getPointer(parentBuffer, parentOffset);

      if (pointer == blockId) {
	// add space in the right buffer
	System.arraycopy(rightBuffer, HEADER_SIZE,
			 rightBuffer, HEADER_SIZE + length * tupleSize,
			 rightLength * tupleSize);
	
	// add the buffer to the right buffer
	System.arraycopy(buffer, HEADER_SIZE,
			 rightBuffer, HEADER_SIZE,
			 length * tupleSize);

	setLength(rightBuffer, length + rightLength);

	if (parentOffset < HEADER_SIZE)
	  throw new IllegalStateException();

	// remove the buffer's pointer from the parent
	System.arraycopy(parentBuffer, parentOffset + tupleSize,
			 parentBuffer, parentOffset,
			 parentEnd - parentOffset - tupleSize);

	setLength(parentBuffer, parentLength - 1);

	return;
      }
    }

    log.warning("BTree merge right can't find matching index: " + debugId(blockId));
  }

  /**
   * Looks up the next block given the current block and the given key.
   */
  private long lookupTuple(long blockId,
			   byte []buffer,
                           byte []keyBuffer,
                           int keyOffset,
                           int keyLength,
			   boolean isLeaf)
    throws IOException
  {
    int length = getLength(buffer);

    int offset = HEADER_SIZE;
    int tupleSize = _tupleSize;
    int end = offset + length * tupleSize;

    long value;

    while (length > 0) {
      int tail = offset + tupleSize * length;
      int delta = tupleSize * (length / 2);
      int newOffset = offset + delta;

      if (newOffset < 0) {
	System.out.println("UNDERFLOW: " + debugId(blockId)  + " LENGTH:" + length + " STU:" + getLength(buffer) + " DELTA:" + delta);
	throw new IllegalStateException("lookupTuple underflow newOffset:" + newOffset);
			   
      }
      else if (newOffset > 65536) {
	System.out.println("OVERFLOW: " + debugId(blockId)  + " LENGTH:" + length + " STU:" + getLength(buffer) + " DELTA:" + delta);
	throw new IllegalStateException("lookupTuple overflow newOffset:" + newOffset);
			   
      }

      int cmp = _keyCompare.compare(keyBuffer, keyOffset,
				    buffer, PTR_SIZE + newOffset, keyLength);
      
      if (cmp == 0) {
        value = getPointer(buffer, newOffset);

	if (value == 0 && ! isLeaf)
	  throw new IllegalStateException("illegal 0 value at " + newOffset + " for block " + debugId(blockId));

	return value;
      }
      else if (cmp > 0) {
        offset = newOffset + tupleSize;
	length = (tail - offset) / tupleSize;
      }
      else if (cmp < 0) {
	length = length / 2;
      }

      if (length > 0) {
      }
      else if (isLeaf)
	return 0;
      else if (cmp < 0) {
	value = getPointer(buffer, newOffset);

	if (value == 0 && ! isLeaf)
	  throw new IllegalStateException("illegal 0 value at " + newOffset + " for block " + debugId(blockId));

	return value;
      }
      else if (offset == end) {
	value = getPointer(buffer, NEXT_OFFSET);

	if (value == 0 && ! isLeaf)
	  throw new IllegalStateException("illegal 0 value at " + newOffset + " for block " + debugId(blockId));

	return value;
      }
      else {
	value = getPointer(buffer, offset);

	if (value == 0 && ! isLeaf)
	  throw new IllegalStateException("illegal 0 value at " + newOffset + " for block " + debugId(blockId));

	return value;
      }
    }

    if (isLeaf)
      return 0;
    else {
      value = getPointer(buffer, NEXT_OFFSET);

      if (value == 0 && ! isLeaf)
	throw new IllegalStateException("illegal 0 value at NEXT_OFFSET for block " + debugId(blockId));

      return value;
    }
  }

  /**
   * Removes from the next block given the current block and the given key.
   */
  private long removeLeafEntry(long blockIndex,
                               byte []buffer,
                               byte []keyBuffer,
                               int keyOffset,
                               int keyLength)
    throws IOException
  {
    int offset = HEADER_SIZE;
    int tupleSize = _tupleSize;
    int length = getLength(buffer);

    for (int i = 0; i < length; i++) {
      int cmp = _keyCompare.compare(keyBuffer, keyOffset,
				    buffer, offset + PTR_SIZE,
				    keyLength);
      
      if (0 < cmp) {
        offset += tupleSize;
        continue;
      }
      else if (cmp == 0) {
	int tupleLength = length * tupleSize;

	if (offset + tupleSize < HEADER_SIZE + tupleLength) {
	  if (offset < HEADER_SIZE)
	    throw new IllegalStateException();
	  
	  System.arraycopy(buffer, offset + tupleSize,
			   buffer, offset,
			   HEADER_SIZE + tupleLength - offset - tupleSize);
	}

	setLength(buffer, length - 1);
        
        return i;
      }
      else {
        return 0;
      }
    }

    return 0;
  }

  private boolean isLeaf(byte []buffer)
  {
    return (getInt(buffer, FLAGS_OFFSET) & LEAF_FLAG) == 0;
  }

  private void setLeaf(byte []buffer, boolean isLeaf)
  {
    if (isLeaf)
      setInt(buffer, FLAGS_OFFSET, getInt(buffer, FLAGS_OFFSET) & ~LEAF_FLAG);
    else
      setInt(buffer, FLAGS_OFFSET, getInt(buffer, FLAGS_OFFSET) | LEAF_FLAG);
  }
  
  /**
   * Reads an int
   */
  private int getInt(byte []buffer, int offset)
  {
    return (((buffer[offset + 0] & 0xff) << 24) +
            ((buffer[offset + 1] & 0xff) << 16) +
            ((buffer[offset + 2] & 0xff) << 8) +
            ((buffer[offset + 3] & 0xff)));
  }

  /**
   * Reads a pointer.
   */
  private long getPointer(byte []buffer, int offset)
  {
    return (((buffer[offset + 0] & 0xffL) << 56) +
            ((buffer[offset + 1] & 0xffL) << 48) +
            ((buffer[offset + 2] & 0xffL) << 40) +
            ((buffer[offset + 3] & 0xffL) << 32) +
            ((buffer[offset + 4] & 0xffL) << 24) +
            ((buffer[offset + 5] & 0xffL) << 16) +
            ((buffer[offset + 6] & 0xffL) << 8) +
            ((buffer[offset + 7] & 0xffL)));
  }

  /**
   * Sets an int
   */
  private void setInt(byte []buffer, int offset, int value)
  {
    buffer[offset + 0] = (byte) (value >> 24);
    buffer[offset + 1] = (byte) (value >> 16);
    buffer[offset + 2] = (byte) (value >> 8);
    buffer[offset + 3] = (byte) (value);
  }

  /**
   * Sets the length
   */
  private void setLength(byte []buffer, int value)
  {
    if (value < 0 || BLOCK_SIZE / _tupleSize < value) {
      System.out.println("BAD-LENGTH: " + value);
      throw new IllegalArgumentException("BTree: bad length " + value);
    }

    setInt(buffer, LENGTH_OFFSET, value);
  }

  /**
   * Sets the length
   */
  private int getLength(byte []buffer)
  {
    int value = getInt(buffer, LENGTH_OFFSET);
    
    if (value < 0 || value > 65536) {
      System.out.println("BAD-LENGTH: " + value);
      throw new IllegalArgumentException("BTree: bad length " + value);
    }

    return value;
  }

  /**
   * Sets a pointer.
   */
  private void setPointer(byte []buffer, int offset, long value)
  {
    if (offset <= LENGTH_OFFSET)
      System.out.println("BAD_POINTER: " + offset);
    
    buffer[offset + 0] = (byte) (value >> 56);
    buffer[offset + 1] = (byte) (value >> 48);
    buffer[offset + 2] = (byte) (value >> 40);
    buffer[offset + 3] = (byte) (value >> 32);
    buffer[offset + 4] = (byte) (value >> 24);
    buffer[offset + 5] = (byte) (value >> 16);
    buffer[offset + 6] = (byte) (value >> 8);
    buffer[offset + 7] = (byte) (value);
  }

  /**
   * Opens the BTree.
   */
  private void start()
    throws IOException
  {
    synchronized (this) {
      if (_isStarted)
	return;

      _isStarted = true;
    }
  }
  
  /**
   * Testing: returns the keys for a block
   */
  public ArrayList<String> getBlockKeys(long blockIndex)
    throws IOException
  {
    long blockId = _store.addressToBlockId(blockIndex * BLOCK_SIZE);

    if (_store.isIndexBlock(blockId))
      return null;
    
    Block block = _store.readBlock(blockId);

    block.read();
    byte []buffer = block.getBuffer();
      
    int length = getInt(buffer, LENGTH_OFFSET);
    int offset = HEADER_SIZE;
    int tupleSize = _tupleSize;

    ArrayList<String> keys = new ArrayList<String>();
    for (int i = 0; i < length; i++) {
      keys.add(_keyCompare.toString(buffer,
				    offset + i * tupleSize + PTR_SIZE,
				    tupleSize - PTR_SIZE));
    }

    block.free();
    
    return keys;
  }

  public static BTree createTest(Path path, int keySize)
    throws IOException, java.sql.SQLException
  {
    Database db = new Database();
    db.setPath(path);
    db.init();

    Store store = new Store(db, "test", null);
    store.create();

    Block block = store.allocateIndexBlock();
    long blockId = block.getBlockId();
    block.free();

    return new BTree(store, blockId, keySize, new KeyCompare());
  }

  public static BTree createStringTest(Path path, int keySize)
    throws IOException, java.sql.SQLException
  {
    Store store = Store.create(path);

    Block block = store.allocateIndexBlock();
    long blockId = block.getBlockId();
    block.free();

    return new BTree(store, blockId, keySize, new StringKeyCompare());
  }

  private String debugId(long blockId)
  {
    return "" + (blockId % Store.BLOCK_SIZE) + ":" + (blockId / Store.BLOCK_SIZE);
  }

  public void close()
  {
    Block rootBlock = _rootBlock;
    _rootBlock = null;
    
    if (rootBlock != null)
      rootBlock.free();
  }

  public String toString()
  {
    return "BTree[" + _store + "," + (_rootBlockId / BLOCK_SIZE) + "]";
  }
}
