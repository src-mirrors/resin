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

package com.caucho.git;

import com.caucho.util.*;
import com.caucho.vfs.*;

import java.io.*;
import java.security.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

/**
 * Top-level class for a repository
 */
public class GitRepository {
  private static final L10N L = new L10N(GitRepository.class);
  private static final Logger log
    = Logger.getLogger(GitRepository.class.getName());
  
  private static final int OBJ_NONE = 0;
  private static final int OBJ_COMMIT = 1;
  private static final int OBJ_TREE = 2;
  private static final int OBJ_BLOB = 3;
  private static final int OBJ_TAG = 4;
  
  private Path _root;

  public GitRepository(Path root)
  {
    _root = root;
  }

  public void initDb()
    throws IOException
  {
    if (_root.lookup("HEAD").canRead())
      return;

    _root.mkdirs();

    _root.lookup("refs").mkdir();
    _root.lookup("refs/heads").mkdir();
    
    _root.lookup("objects").mkdir();
    _root.lookup("objects/info").mkdir();
    _root.lookup("objects/pack").mkdir();
    
    _root.lookup("branches").mkdir();
    
    _root.lookup("tmp").mkdir();

    WriteStream out = _root.lookup("HEAD").openWrite();
    try {
      out.println("ref: refs/heads/master");
    } finally {
      out.close();
    }
  }

  public String getMaster()
  {
    try {
      Path path = _root.lookup("refs/heads/master");
      ReadStream is = path.openRead();

      try {
	return is.readLine();
      } finally {
	is.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public Object objectType(String sha1)
    throws IOException
  {
    GitObjectStream is = open(sha1);
    try {
      return is.getType();
    } finally {
      is.close();
    }
  }
  
  public String getTag(String tag)
  {
    Path path = _root.lookup("refs").lookup(tag);

    if (! path.canRead())
      return null;

    ReadStream is = null;
    try {
      is = path.openRead();

      String hex = is.readLine();

      return hex.trim();
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);

      return null;
    } finally {
      if (is != null)
	is.close();
    }
  }

  public String []listRefs(String dir)
  {
    try {
      Path path = _root.lookup("refs").lookup(dir);

      if (path.isDirectory())
	return path.list();
      else
	return new String[0];
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);

      return new String[0];
    }
  }

  public Path getRefPath(String path)
  {
    return _root.lookup("refs").lookup(path);
  }

  public void writeTag(String tag, String hex)
  {
    Path path = _root.lookup("refs").lookup(tag);

    try {
      path.getParent().mkdirs();
    } catch (IOException e) {
      log.log(Level.FINEST, e.toString(), e);
    }

    WriteStream out = null;
    try {
      out = path.openWrite();

      out.println(hex);
    } catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    } finally {
      try {
	if (out != null)
	  out.close();
      } catch (Exception e) {
	log.log(Level.FINEST, e.toString(), e);
      }
    }
  }

  public GitCommit parseCommit(String sha1)
    throws IOException
  {
    GitObjectStream is = open(sha1);
    try {
      if (! "commit".equals(is.getType()))
	throw new IOException(L.l("'{0}' is an unexpected type, expected 'commit'",
				  is.getType()));
      
      return is.parseCommit();
    } finally {
      is.close();
    }
  }

  public GitTree parseTree(String sha1)
    throws IOException
  {
    GitObjectStream is = open(sha1);
    try {
      if (! "tree".equals(is.getType()))
	throw new IOException(L.l("'{0}' is an unexpected type, expected 'commit'",
				  is.getType()));
      
      return is.parseTree();
    } finally {
      is.close();
    }
  }

  public void copyToFile(Path path, String sha1)
    throws IOException
  {
    GitObjectStream is = open(sha1);
    
    try {
      if (! "blob".equals(is.getType()))
	throw new IOException(L.l("'{0}' is an unexpected type, expected 'blot'",
				  is.getType()));

      WriteStream os = path.openWrite();

      try {
	os.writeStream(is.getInputStream());
      } finally {
	os.close();
      }
    } finally {
      is.close();
    }
  }

  public boolean contains(String sha1)
  {
    String prefix = sha1.substring(0, 2);
    String suffix = sha1.substring(2);

    Path path = _root.lookup("objects").lookup(prefix).lookup(suffix);

    return path.exists();
  }

  private GitObjectStream open(String sha1)
    throws IOException
  {
    String prefix = sha1.substring(0, 2);
    String suffix = sha1.substring(2);

    Path path = _root.lookup("objects").lookup(prefix).lookup(suffix);

    return new GitObjectStream(path);
  }

  /**
   * Writes a file to the repository
   */
  public String writeFile(Path path)
    throws IOException
  {
    InputStream is = path.openRead();
    
    try {
      TempOutputStream os = new TempOutputStream();
      String type = "blob";

      String hex = writeData(os, type, is, path.getLength());

      return writeFile(os, hex);
    } finally {
      is.close();
    }
  }

  /**
   * Writes a file to the repository
   */
  public String writeTree(GitTree tree)
    throws IOException
  {
    TempOutputStream treeOut = new TempOutputStream();

    tree.toData(treeOut);
    
    int treeLength = treeOut.getLength();
    
    InputStream is = treeOut.openRead();
    
    try {
      TempOutputStream os = new TempOutputStream();
      String type = "tree";

      String hex = writeData(os, type, is, treeLength);

      return writeFile(os, hex);
    } finally {
      is.close();
    }
  }

  /**
   * Writes a file to the repository
   */
  public String writeCommit(GitCommit commit)
    throws IOException
  {
    TempStream commitOut = new TempStream();
    WriteStream out = new WriteStream(commitOut);

    out.print("tree ");
    out.println(commit.getTree());

    /*
    if (user != null) {
      out.print("author ");
      out.print(user);
      out.print(" ");
      out.print(timestamp / 1000);
      out.println(" +0000");
      
      out.print("committer ");
      out.print(user);
      out.print(" ");
      out.print(timestamp / 1000);
      out.println(" +0000");
    }
    */

    String parent = commit.getParent();

    if (parent != null) {
      out.print("parent ");
      out.println(parent);
    }

    HashMap<String,String> attr = commit.getAttributeMap();
    if (attr != null) {
      ArrayList<String> keys = new ArrayList<String>(attr.keySet());
      Collections.sort(keys);

      for (String key : keys) {
	out.print(key);
	out.print(' ');
	out.print(attr.get(key));
	out.println();
      }
    }

    out.println();

    if (commit.getMessage() != null)
      out.println(commit.getMessage());
    
    out.close();
    
    int commitLength = commitOut.getLength();
    
    InputStream is = commitOut.openRead();
    
    try {
      TempOutputStream os = new TempOutputStream();
      String type = "commit";

      String hex = writeData(os, type, is, commitLength);

      return writeFile(os, hex);
    } finally {
      is.close();
    }
  }

  public String writeFile(TempOutputStream os, String hex)
    throws IOException
  {
    Path objectPath = lookupPath(hex);

    if (objectPath.exists())
      return hex;

    objectPath.getParent().mkdirs();
    
    Path tmpDir = _root.lookup("tmp");
    tmpDir.mkdirs();

    Path tmp = _root.lookup("tmp").lookup("tmp." + hex);

    WriteStream tmpOs = tmp.openWrite();
    try {
      tmpOs.writeStream(os.openRead());
    } finally {
      tmpOs.close();
    }

    tmp.renameTo(objectPath);
      
    return hex;
  }
  
  private Path lookupPath(String sha1)
  {
    String prefix = sha1.substring(0, 2);
    String suffix = sha1.substring(2);
    
    return _root.lookup("objects").lookup(prefix).lookup(suffix);
  }

  private String writeData(OutputStream os, String type,
			   InputStream is, long length)
    throws IOException
  {
    TempBuffer buf = TempBuffer.allocate();

    try {
      DeflaterOutputStream out = new DeflaterOutputStream(os);

      MessageDigest md = MessageDigest.getInstance("SHA-1");

      for (int i = 0; i < type.length(); i++) {
	int ch = type.charAt(i);
	out.write(ch);
	md.update((byte) ch);
      }

      out.write(' ');
      md.update((byte) ' ');
      
      String lengthString = String.valueOf(length);

      for (int i = 0; i < lengthString.length(); i++) {
	int ch = lengthString.charAt(i);
	out.write(ch);
	md.update((byte) ch);
      }
      
      out.write(0);
      md.update((byte) 0);

      long readLength = 0;
      
      int len;

      byte []buffer = buf.getBuffer();
      while ((len = is.read(buffer, 0, buffer.length)) > 0) {
	out.write(buffer, 0, len);
	md.update(buffer, 0, len);

	readLength += len;
      }

      out.close();

      if (readLength != length)
	throw new IOException(L.l("written length does not match data"));

      return Hex.toHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    } finally {
      TempBuffer.free(buf);
    }
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _root + "]";
  }
}
