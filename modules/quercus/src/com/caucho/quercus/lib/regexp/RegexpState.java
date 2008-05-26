/*
 * Copyright (c) 1998-2007 Caucho Technology -- all rights reserved
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

package com.caucho.quercus.lib.regexp;

import java.util.*;
import java.util.logging.*;

import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.StringValue;
import com.caucho.util.*;

public class RegexpState {
  private static final Logger log
    = Logger.getLogger(RegexpState.class.getName());
  
  private static final L10N L = new L10N(Regexp.class);
  
  public static final int FAIL = -1;
  public static final int SUCCESS = 0;

  private Regexp _regexp;

  StringValue _subject;
  
  boolean _isGlobal;

  int _first;
  int _start;

  // optim stuff
  CharBuffer _prefix; // initial string
  int _minLength; // minimum length possible for this regexp

  boolean _isUnicode;
  boolean _isPHP5String;
  
  boolean _isUTF8;
  boolean _isEval;

  int _groupLength;
  int []_groupBegin;
  int []_groupEnd;
  
  int []_loopCount;
  int []_loopOffset;
  
  public RegexpState(Env env, Regexp regexp, StringValue subject)
  {
    this(regexp);

    subject = _regexp.convertSubject(env, subject);
    
    _subject = subject;
  }
  
  public RegexpState(Regexp regexp)
  {
    _regexp = regexp;

    int nGroup = _regexp._nGroup;
    _groupBegin = new int[nGroup];
    _groupEnd = new int[nGroup];

    int nLoop = _regexp._nLoop;
    _loopCount = new int[nLoop];
    _loopOffset = new int[nLoop];
  }

  public boolean find()
  {
    if (log.isLoggable(Level.FINEST))
      log.finest(this + " find()");
    
    int minLength = _regexp._minLength;
    boolean []firstSet = _regexp._firstSet;

    int length = _subject.length();

    for (; _first + minLength <= length; _first++) {
      if (firstSet != null && _first < length) {
        char firstChar = _subject.charAt(_first);
	
        if (firstChar < 256 && ! firstSet[firstChar])
          continue;
      }

      clearGroup();
      int offset = _regexp._prog.match(_subject, length, _first, this);

      if (offset >= 0) {
        _groupBegin[0] = _first;
        _groupEnd[0] = offset;

        if (_first < offset)
          _first = offset;
        else
          _first += 1;
          
        return true;
      }
    }

    _first = length + 1;
    
    return false;
  }

  public boolean find(Env env, StringValue subject)
  {
    subject = _regexp.convertSubject(env, subject);
    
    _subject = subject;
    _first = 0;

    return find();
  }

  public int find(Env env, StringValue subject, int first)
  {
    if (log.isLoggable(Level.FINEST))
      log.finest(this + " find(" + subject + ")");
    
    subject = _regexp.convertSubject(env, subject);
    
    _subject = subject;

    _first = first;
    clearGroup();

    return _regexp._prog.match(_subject, subject.length(), first, this);
  }
  
  /**
   * XXX: not proper behaviour with /g
   */
  public int exec(Env env, StringValue subject, int start)
  { 
    if (log.isLoggable(Level.FINEST))
      log.finest(this + " exec(" + subject + ")");
    
    subject = _regexp.convertSubject(env, subject);

    clearGroup();
    
    _start = start;
    _first = start;

    _subject = subject;
    int subjectLength = subject.length();

    int minLength = _regexp._minLength;
    boolean []firstSet = _regexp._firstSet;
    int end = subject.length() - minLength;
    RegexpNode prog = _regexp._prog;

    for (; start <= end; start++) {
      if (firstSet != null && (start < end || minLength > 0)) {
	char firstChar = subject.charAt(start);
	
	if (firstChar < 256 && ! firstSet[firstChar])
	  continue;
      }
      
      int value = prog.match(subject, subjectLength, start, this);

      if (value >= 0) {
	_groupBegin[0] = start;
	_groupEnd[0] = value;
	
	return start;
      }
    }

    return -1;
  }

  private void clearGroup()
  {
    _groupLength = 0;

    for (int i = _groupBegin.length - 1; i > 0; i--) {
      _groupBegin[i] = -1;
      _groupEnd[i] = -1;
    }
  }
  
  public int getBegin(int i)
  {
    return _groupBegin[i];
  }

  public int getEnd(int i)
  {
    return _groupEnd[i];
  }
  
  public void setBegin(int i, int v)
  {
    _groupBegin[i] = v;
  }

  public void setEnd(int i, int v)
  {
    _groupEnd[i] = v;
  }

  public int getLength()
  {
    return _groupLength;
  }

  public void setLength(int length)
  {
    _groupLength = length;
  }
  
  public int length()
  {
    return _groupLength;
  }
  
  public int start()
  {
    return getBegin(0);
  }
  
  public int start(int i)
  {
    return getBegin(i);
  }
  
  public int end()
  {
    return getEnd(0);
  }
  
  public int end(int i)
  {
    return getEnd(i);
  }
  
  public int groupCount()
  {
    return _regexp._nGroup;
  }
  
  public boolean isMatchedGroup(int i)
  {
    return i <= _groupLength;
  }
  
  public StringValue group(Env env)
  {
    return group(env, 0);
  }

  public StringValue group(Env env, int i)
  {
    int begin = getBegin(i);
    int end = getEnd(i);

    StringValue s = _subject.substring(begin, end);

    return _regexp.convertResult(env, s);
  }
  
  public StringValue getGroupName(int i)
  {
    StringValue []groupNames = _regexp._groupNames;

    if (groupNames == null || groupNames.length <= i)
      return null;
    else
      return groupNames[i];
  }
  
  public StringValue substring(Env env, int start)
  {
    StringValue result = _subject.substring(start);

    return _regexp.convertResult(env, result);
  }
  
  public StringValue substring(Env env, int start, int end)
  {
    StringValue result = _subject.substring(start, end);

    return _regexp.convertResult(env, result);
  }

  @Override
  public String toString()
  {
    return getClass().getSimpleName() + "[" + _regexp + "]";
  }
}
