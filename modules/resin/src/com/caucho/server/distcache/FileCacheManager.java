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

package com.caucho.server.distcache;

import com.caucho.cluster.CacheEntry;
import com.caucho.cluster.CacheSerializer;
import com.caucho.config.ConfigException;
import com.caucho.server.cache.TempFileManager;
import com.caucho.server.cluster.Server;
import com.caucho.server.resin.Resin;
import com.caucho.util.Alarm;
import com.caucho.util.LruCache;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.StreamSource;
import com.caucho.vfs.TempOutputStream;
import com.caucho.vfs.Vfs;
import com.caucho.vfs.WriteStream;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.logging.*;
import java.security.MessageDigest;
import java.security.DigestOutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Manages the distributed cache
 */
public class FileCacheManager extends DistributedCacheManager
{
  private static final Logger log
    = Logger.getLogger(FileCacheManager.class.getName());
  
  private static final L10N L = new L10N(FileCacheManager.class);
  
  private TempFileManager _tempFileManager;

  private CacheMapBacking _cacheMapBacking;
  private DataBacking _dataBacking;

  private final LruCache<HashKey,CacheMapEntry> _entryCache
    = new LruCache<HashKey,CacheMapEntry>(8 * 1024);
  
  public FileCacheManager(Server server)
  {
    super(server);

    try {
      _tempFileManager = server.getTempFileManager();

      Path adminPath = server.getAdminPath();
      String serverId = server.getServerId();

      _cacheMapBacking = new CacheMapBacking(adminPath, serverId);
      _dataBacking = new DataBacking(serverId, _cacheMapBacking);
    } catch (Exception e) {
      throw ConfigException.create(e);
    }
  }

  /**
   * Gets a cache entry
   */
  public CacheEntry getEntry(HashKey key, CacheConfig config)
  {
    CacheMapEntry entry = _entryCache.get(key);

    if (entry == null) {
      entry = _cacheMapBacking.load(key);

      CacheMapEntry oldEntry = _entryCache.putIfNew(key, entry);

      if (entry.getVersion() < oldEntry.getVersion())
	entry = oldEntry;
    }

    return entry;
  }

  /**
   * Gets a cache entry
   */
  public Object get(HashKey key, CacheConfig config)
  {
    CacheMapEntry entry = getLocalEntry(key);

    if (entry == null)
      return null;
    
    Object value = entry.getValue();

    if (value != null)
      return value;

    HashKey valueHash = entry.getValueHashKey();

    if (valueHash == null)
      return null;

    value = readData(valueHash, config.getValueSerializer());

    // use the old value if it's been overwritten
    if (entry.getValue() == null)
      entry.setValue(value);

    return value;
  }

  /**
   * Gets a cache entry
   */
  public boolean get(HashKey key, OutputStream os, CacheConfig config)
  {
    return false;
  }

  /**
   * Sets a cache entry
   */
  public void put(HashKey key, Object value, CacheConfig config)
  {
    long timeout = 60000L;
    
    CacheMapEntry oldEntry = _entryCache.get(key);

    HashKey oldValueHash
      = oldEntry != null ? oldEntry.getValueHashKey() : null;
    
    HashKey valueHash = writeData(oldValueHash, value,
				  config.getValueSerializer());

    if (valueHash.equals(oldValueHash))
      return;

    long version = oldEntry != null ? oldEntry.getVersion() + 1 : 1;

    long expireTime = Alarm.getExactTime() + 100L;

    CacheMapEntry entry = new CacheMapEntry(valueHash, value, version,
					    expireTime, true);

    // the failure cases are not errors because this put() could
    // be immediately followed by an overwriting put()

    if (! _entryCache.compareAndPut(oldEntry, key, entry)) {
      log.fine(this + " entry update failed due to timing conflict"
	       + " (key=" + key + ")");
      
      return;
    }

    if (oldEntry == null) {
      if (_cacheMapBacking.insert(key, valueHash, version, timeout)) {
      }
      else {
	log.fine(this + " db insert failed due to timing conflict"
		 + "(key=" + key + ")");
      }
    }
    else {
      if (_cacheMapBacking.updateSave(key, valueHash, timeout, version)) {
      }
      else {
	log.fine(this + " db update failed due to timing conflict"
		 + "(key=" + key + ")");
      }
    }
  }

  /**
   * Sets a cache entry
   */
  public CacheEntry put(HashKey hashKey,
			InputStream is,
			long idleTimeout,
			CacheConfig config)
    throws IOException
  {
    return null;
  }

  /**
   * Sets a cache entry
   */
  public boolean remove(HashKey key)
  {
    long timeout = 60000L;
    
    CacheMapEntry oldEntry = _entryCache.get(key);

    HashKey oldValueHash
      = oldEntry != null ? oldEntry.getValueHashKey() : null;

    long version = oldEntry != null ? oldEntry.getVersion() + 1 : 1;
    
    long expireTime = Alarm.getCurrentTime() + 100L;
    CacheMapEntry entry = new CacheMapEntry(null, null, version,
					    expireTime, true);

    // the failure cases are not errors because this put() could
    // be immediately followed by an overwriting put()

    if (! _entryCache.compareAndPut(oldEntry, key, entry)) {
      log.fine(this + " entry remove failed due to timing conflict"
	       + " (key=" + key + ")");
      
      return oldValueHash != null;
    }

    if (oldEntry == null) {
      log.fine(this + " db remove failed due to timing conflict"
	       + "(key=" + key + ")");
    }
    else {
      if (_cacheMapBacking.updateSave(key, null, timeout, version)) {
      }
      else {
	log.fine(this + " db remove failed due to timing conflict"
		 + "(key=" + key + ")");
      }
    }

    return oldValueHash != null;
  }
  
  CacheMapEntry getLocalEntry(HashKey key)
  {
    CacheMapEntry entry = _entryCache.get(key);

    if (entry == null) {
      entry = _cacheMapBacking.load(key);

      if (entry == null)
	return null;

      CacheMapEntry oldEntry = _entryCache.putIfNew(key, entry);

      if (entry.getVersion() < oldEntry.getVersion())
	entry = oldEntry;
    }

    return entry;
  }

  protected HashKey writeData(HashKey oldValueHash,
			      Object value,
			      CacheSerializer serializer)
  {
    TempOutputStream os = null;

    try {
      os = new TempOutputStream();

      MessageDigestOutputStream mOut = new MessageDigestOutputStream(os);
      DeflaterOutputStream gzOut = new DeflaterOutputStream(mOut);

      serializer.serialize(value, gzOut);

      gzOut.finish();
      mOut.close();
      
      byte []hash = mOut.getDigest();

      HashKey valueHash = new HashKey(hash);

      if (valueHash.equals(oldValueHash))
	return valueHash;

      int length = os.getLength();
      
      StreamSource source = new StreamSource(os);
      if (! _dataBacking.save(valueHash, source, length))
	throw new RuntimeException(L.l("Can't save the data '{0}'",
				       valueHash));

      return valueHash;
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (os != null)
	os.close();
    }
  }

  protected Object readData(HashKey valueKey, CacheSerializer serializer)
  {
    TempOutputStream os = null;

    try {
      os = new TempOutputStream();

      WriteStream out = Vfs.openWrite(os);

      if (! _dataBacking.load(valueKey, out)) {
	out.close();
	// XXX: error?  since we have the value key, it should exist
	return null;
      }

      out.close();

      InputStream is = os.openInputStream();

      try {
	InflaterInputStream gzIn = new InflaterInputStream(is);

	Object value = serializer.deserialize(gzIn);

	gzIn.close();

	return value;
      } finally {
	is.close();
      }
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      if (os != null)
	os.close();
    }
  }
}
