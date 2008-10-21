/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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
 * @author Alex Rojkov
 */
using System;
using System.Collections;
using System.Diagnostics;
using System.DirectoryServices;
using System.Text;
using System.IO;
using Microsoft.Win32;


namespace Caucho
{
  public class Util
  {
    public static String FindResinInRegistry() {
      RegistryKey key
        = Registry.LocalMachine.OpenSubKey("Software\\Caucho Technology\\Resin\\CurrentVersion");
      
      if (key == null)
        return null;
      else
        return (String)key.GetValue("Resin Home");
    }
    
    public static String Canonicalize(String path) {
      char[] buf = path.ToCharArray();
      
      for (int i = 0; i < buf.Length - 1; i++) {
        if (buf[i] == '/')
          buf[i] = '\\';
      }
      
      if (buf[buf.Length - 1] == '/' || buf[buf.Length - 1] == '\\')
        return new String(buf, 0, buf.Length - 1);
      else
        return new String(buf, 0, buf.Length);
    }

    public static String GetResinHome(String resinHome, String path) {
      if (resinHome != null)
        return resinHome;
      
      resinHome = Environment.GetEnvironmentVariable("RESIN_HOME");
      
      if (resinHome != null && ! "".Equals(resinHome))
        return Canonicalize(resinHome);
      
      resinHome = GetParent(GetCanonicalPath(path), 1);
      
      if (File.Exists(resinHome + "\\lib\\resin.jar"))
        return Canonicalize(resinHome);
      
      resinHome = GetParent(resinHome, 1);
      
      if (File.Exists(resinHome + "\\lib\\resin.jar"))
        return Canonicalize(resinHome);
      
      resinHome = FindResinInRegistry();
      
      if (resinHome != null)
        return Canonicalize(resinHome);
      
      String [] dirs = Directory.GetDirectories("\\", "resin*");
      
      foreach(String dir in dirs) {
        if (File.Exists(dir + "\\lib\\resin.jar"))
          resinHome = dir;
      }
      
      if (resinHome != null)
        return Canonicalize(Directory.GetCurrentDirectory().Substring(0, 2) + resinHome);
      else
        return null;
    }
    
    public static String GetCanonicalPath(String path) {
      String currentDirectory = Directory.GetCurrentDirectory();
      StringBuilder path_ = new StringBuilder();
      
      if ((path[0] == '/' || path[0] == '\\') &&
          (path[1] == '/' || path[1] == '\\')) {
        path_.Append(path);
      } else if ((path[0] == '/' || path[0] == '\\')) {
        path_.Append(currentDirectory.Substring(0, 2)).Append(path);
      } else if ((path[0] >= 'a' && path[0] <= 'z' ||
                  path[0] >= 'A' && path[0] <= 'Z') &&
                 path[1] == ':') {
        path_.Append(path);
      } else {
        path_.Append(currentDirectory).Append('\\').Append(path);
      }
      
      char[] buf = new char[path_.Length + 3];
      
      path_.CopyTo(0, buf, 0, path_.Length);
      
      int cursor = 0;
      int parentIdx = 0;
      for (int i = 0; i < buf.Length - 3;) {
        if(buf[i] == '/')
          buf[i] = '\\';
        
        if ((i + 2) < buf.Length && buf[i] == '\\' &&
            buf[i + 1] == '.' && (buf[i + 2] == '\\' || buf[i + 2] == '/')) {
          i = i + 2;
        } else if ((i + 3) < buf.Length &&
                   buf[i] == '\\' &&
                   buf[i + 1] == '.' &&
                   buf[i + 2] == '.' &&
                   (buf[i + 3] == '\\' || buf[i + 3] == '/')) {
          cursor = parentIdx;
          
          i += 4;
        } else if (buf[i] == '\\'){
          buf[cursor++] = buf[i++];
          
          parentIdx = cursor;
        } else {
          buf[cursor++] = buf[i++];
        }
      }
      
      if (buf[cursor - 1]== '\\')
        return new String(buf, 0, cursor - 1);
      else
        return new String(buf, 0, cursor);
    }
    
    public static String GetParent(String path, int depth) {
      for (int i = path.Length - 1; i >= 0; i--) {
        if (path[i] == '\\' && --depth == 0)
          return path.Substring(0, i);
      }
      
      throw new ArgumentOutOfRangeException("depth is out of range");
    }

    
  }
}
