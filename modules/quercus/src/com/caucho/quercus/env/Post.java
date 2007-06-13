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

package com.caucho.quercus.env;

import com.caucho.quercus.lib.string.StringModule;
import com.caucho.quercus.lib.string.StringUtility;
import com.caucho.quercus.lib.file.FileModule;
import com.caucho.vfs.FilePath;
import com.caucho.vfs.MultipartStream;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.VfsStream;
import com.caucho.vfs.WriteStream;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

/**
 * Handling of POST requests.
 */
public class Post {
  static void fillPost(Env env,
                       ArrayValue post, ArrayValue files,
                       HttpServletRequest request,
                       boolean addSlashesToValues)
  {
    InputStream is = null;

    try {
      if (request.getMethod().equals("POST")) {
        String encoding = request.getCharacterEncoding();
        
        if (encoding == null)
          encoding = env.getHttpInputEncoding().toString();

        String contentType = request.getHeader("Content-Type");

        if (contentType == null) {
        }
        else if (contentType.startsWith("application/x-www-form-urlencoded")) {
          is = request.getInputStream();
          
          StringBuilder value = new StringBuilder();
          int ch;

          while ((ch = is.read()) >= 0) {
            value.append((char) ch);
          }
          
          StringUtility.parseStr(env, value.toString(), post, false, encoding);
        }
        else if (contentType.startsWith("multipart/form-data")) {
          String boundary = getBoundary(contentType);

          is = request.getInputStream();

          ReadStream rs = new ReadStream(new VfsStream(is, null));
          MultipartStream ms = new MultipartStream(rs, boundary);
          ms.setEncoding(encoding);

          readMultipartStream(env, ms, post, files, addSlashesToValues);

          rs.close();
        }
        
        if (post.getSize() == 0) {
          // needs to be last or else this function will consume the inputstream
          putRequestMap(env, post, files, request, addSlashesToValues);
        }
      }
      
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      try {
        if (is != null)
          is.close();
      } catch (IOException e) {
      }
    }
  }

  private static void readMultipartStream(Env env,
                                          MultipartStream ms,
                                          ArrayValue post,
                                          ArrayValue files,
                                          boolean addSlashesToValues)
    throws IOException
  {
    ReadStream is;

    while ((is = ms.openRead()) != null) {
      String attr = (String) ms.getAttribute("content-disposition");

      if (attr == null || ! attr.startsWith("form-data")) {
        // XXX: is this an error?
        continue;
      }

      String name = getAttribute(attr, "name");
      String filename = getAttribute(attr, "filename");

      if (filename == null) {
        StringBuilder value = new StringBuilder();
        int ch;

        while ((ch = is.read()) >= 0) {
          value.append((char) ch);
        }

        addFormValue(post, name, new StringValueImpl(value.toString()), null, addSlashesToValues);
      }
      else {
        Path tmpPath = env.getUploadDirectory().createTempFile("php", "tmp");

        env.addRemovePath(tmpPath);

        WriteStream os = tmpPath.openWrite();
        try {
          os.writeStream(is);
        } finally {
          os.close();
        }

        addFormFile(env,
                    files,
                    name,
                    filename,
                    tmpPath.getFullPath(),
                    getAttribute(attr, "mime-type"),
                    tmpPath.getLength(),
                    addSlashesToValues);
      }
    }
  }

  private static void addFormFile(Env env,
                                  ArrayValue files,
                                  String name,
                                  String fileName,
                                  String tmpName,
                                  String mimeType,
                                  long fileLength,
                                  boolean addSlashesToValues)
  {
    int p = name.indexOf('[');
    String index = "";
    if (p >= 0) {
      index = name.substring(p);
      name = name.substring(0, p);
    }

    StringValue nameValue = new StringValueImpl(name);
    Value v = files.get(nameValue).toValue();
    ArrayValue entry = null;
    if (v instanceof ArrayValue)
      entry = (ArrayValue) v;

    if (entry == null) {
      entry = new ArrayValueImpl();
      files.put(nameValue, entry);
    }

    int error;

    // php/1667
    long uploadMaxFilesize = env.getIniBytes("upload_max_filesize", 2 * 1024 * 1024);
    
    if (fileLength > uploadMaxFilesize)
      error = FileModule.UPLOAD_ERR_INI_SIZE;
    else
      error = FileModule.UPLOAD_ERR_OK;

    addFormValue(entry, "name" + index, new StringValueImpl(fileName),
            null, addSlashesToValues);

    long size;

    if (error != FileModule.UPLOAD_ERR_INI_SIZE) {
      size = fileLength;
    }
    else {
      mimeType = "";
      tmpName = "";
      size = 0;
    }

    if (mimeType != null) {
      addFormValue(entry, "type" + index, new StringValueImpl(mimeType),
              null, addSlashesToValues);

      entry.put("type", mimeType);
    }

    addFormValue(entry, "tmp_name" + index, new StringValueImpl(tmpName),
            null, addSlashesToValues);

    addFormValue(entry, "error" + index, new LongValue(error),
            null, addSlashesToValues);

    addFormValue(entry, "size" + index, new LongValue(size),
            null, addSlashesToValues);

    addFormValue(files, name, entry, null, addSlashesToValues);
  }
  
  public static void addFormValue(ArrayValue array,
                                  String key,
                                  String []formValueList,
                                  boolean addSlashesToValues)
  {
    // php/081b
    Value value = new StringValueImpl(formValueList[formValueList.length - 1]);

    addFormValue(array, key,
                 value,
                 formValueList,
                 addSlashesToValues);
  }

  public static void addFormValue(ArrayValue array,
                                  String key,
                                  Value formValue,
                                  String []formValueList,
                                  boolean addSlashesToValues)
  {
    int p = key.indexOf('[');
    int q = key.indexOf(']', p);

    if (p >= 0 && p < q) {
      String index = key;
      
      Value keyValue;
      Value existingValue;

      if (p > 0) {
	key = key.substring(0, p);

	keyValue = new StringValueImpl(key);
	existingValue = array.get(keyValue);

	if (existingValue == null || ! existingValue.isset()) {
	  existingValue = new ArrayValueImpl();
	  array.put(keyValue, existingValue);
	}
	else if (! existingValue.isArray()) {
	  existingValue = new ArrayValueImpl().put(existingValue);
	  array.put(keyValue, existingValue);
	}

	array = (ArrayValue) existingValue;
      }

      int p1;
      while ((p1 = index.indexOf('[', q)) > 0) {
        key = index.substring(p + 1, q);

        if (key.equals("")) {
          existingValue = new ArrayValueImpl();
          array.put(existingValue);
        }
        else {
          keyValue = new StringValueImpl(key);
          existingValue = array.get(keyValue);

          if (existingValue == null || ! existingValue.isset()) {
            existingValue = new ArrayValueImpl();
            array.put(keyValue, existingValue);
          }
          else if (! existingValue.isArray()) {
            existingValue = new ArrayValueImpl().put(existingValue);
            array.put(keyValue, existingValue);
          }
        }

        array = (ArrayValue) existingValue;

        p = p1;
        q = index.indexOf(']', p);
      }

      if (q > 0)
        index = index.substring(p + 1, q);
      else
        index = index.substring(p + 1);

      if (index.equals("")) {
        if (formValueList != null) {
          for (int i = 0; i < formValueList.length; i++) {
            put(array, null, new StringValueImpl(formValueList[i]), addSlashesToValues);
          }
        }
        else
          array.put(formValue);
      }
      else if ('0' <= index.charAt(0) && index.charAt(0) <= '9')
        put(array, new LongValue(StringValue.toLong(index)), formValue, addSlashesToValues);
      else
        put(array, new StringValueImpl(index), formValue, addSlashesToValues);
    }
    else {
      put(array, new StringValueImpl(key), formValue, addSlashesToValues);
    }
  }

  private static void put(ArrayValue array, Value key, Value value, boolean addSlashes)
  {
    if (addSlashes && (value instanceof StringValue)) {
      value = StringModule.addslashes(value.toStringValue());
    }

    if (key == null)
      array.put(value);
    else
      array.put(key, value);
  }

  private static String getBoundary(String contentType)
  {
    int i = contentType.indexOf("boundary=");
    if (i < 0)
      return null;

    i += "boundary=".length();

    int length = contentType.length();

    char ch;

    if (length <= i)
      return null;
    else if ((ch = contentType.charAt(i)) == '\'') {
      StringBuilder sb = new StringBuilder();

      for (i++; i < length && (ch = contentType.charAt(i)) != '\''; i++) {
        sb.append(ch);
      }

      return sb.toString();
    }
    else if (ch == '"') {
      StringBuilder sb = new StringBuilder();

      for (i++; i < length && (ch = contentType.charAt(i)) != '"'; i++) {
        sb.append(ch);
      }

      return sb.toString();
    }
    else {
      StringBuilder sb = new StringBuilder();

      for (; i < length && (ch = contentType.charAt(i)) != ' ' && ch != ';'; i++) {
        sb.append(ch);
      }

      return sb.toString();
    }
  }

  private static String getAttribute(String attr, String name)
  {
    if (attr == null)
      return null;

    int length = attr.length();
    int i = attr.indexOf(name);
    if (i < 0)
      return null;

    for (i += name.length(); i < length && attr.charAt(i) != '='; i++) {
    }

    for (i++; i < length && attr.charAt(i) == ' '; i++) {
    }

    StringBuilder value = new StringBuilder();

    if (i < length && attr.charAt(i) == '\'') {
      for (i++; i < length && attr.charAt(i) != '\''; i++)
        value.append(attr.charAt(i));
    }
    else if (i < length && attr.charAt(i) == '"') {
      for (i++; i < length && attr.charAt(i) != '"'; i++)
        value.append(attr.charAt(i));
    }
    else if (i < length) {
      char ch;
      for (; i < length && (ch = attr.charAt(i)) != ' ' && ch != ';'; i++)
        value.append(ch);
    }

    return value.toString();
  }
  
  private static void putRequestMap(Env env,
                                    ArrayValue post,
                                    ArrayValue files,
                                    HttpServletRequest request,
                                    boolean addSlashesToValues)
  {
    Map<String,String[]> map = request.getParameterMap();

    if (map == null)
      return;
    
    for (Map.Entry<String,String[]> entry : map.entrySet()) {
      String key = entry.getKey();

      int len = key.length();

      if (len < 10 || ! key.endsWith(".filename"))
        continue;

      String name = key.substring(0, len - 9);

      String []fileNames = request.getParameterValues(name + ".filename");
      String []tmpNames = request.getParameterValues(name + ".file");
      String []mimeTypes = request.getParameterValues(name + ".content-type");

      for (int i = 0; i < fileNames.length; i++) {
        long fileLength = new FilePath(tmpNames[i]).getLength();

        addFormFile(env, files, name, fileNames[i], tmpNames[i], mimeTypes[i], fileLength, addSlashesToValues);
      }
    }

    ArrayList<String> keys = new ArrayList<String>();

    keys.addAll(request.getParameterMap().keySet());

    Collections.sort(keys);

    for (String key : keys) {   
      String []value = request.getParameterValues(key);
      
      Post.addFormValue(post, key, value, addSlashesToValues);
    }
  }
}

