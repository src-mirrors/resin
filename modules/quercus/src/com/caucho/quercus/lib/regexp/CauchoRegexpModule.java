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

import com.caucho.quercus.QuercusException;
import com.caucho.quercus.QuercusModuleException;
import com.caucho.quercus.QuercusRuntimeException;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.Reference;
import com.caucho.quercus.env.*;
import com.caucho.quercus.lib.JavaModule;
import com.caucho.quercus.lib.regexp.JavaRegexpModule.GroupNeighborMap;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;

import java.io.CharConversionException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CauchoRegexpModule
  extends AbstractQuercusModule
{
  private static final Logger log =
    Logger.getLogger(RegexpModule.class.getName());
  
  private static final L10N L = new L10N(RegexpModule.class);

  public static final int PREG_REPLACE_EVAL = 0x01;
  public static final int PCRE_UTF8 = 0x02;

  public static final int PREG_PATTERN_ORDER = 0x01;
  public static final int PREG_SET_ORDER = 0x02;
  public static final int PREG_OFFSET_CAPTURE = 0x04;

  public static final int PREG_SPLIT_NO_EMPTY = 0x01;
  public static final int PREG_SPLIT_DELIM_CAPTURE = 0x02;
  public static final int PREG_SPLIT_OFFSET_CAPTURE = 0x04;

  public static final int PREG_GREP_INVERT = 1;

  public static final boolean [] PREG_QUOTE = new boolean[256];

  private static final LruCache<StringValue, Regexp> _regexpCache
  = new LruCache<StringValue, Regexp>(1024);

  private static final LruCache<StringValue, ArrayList<Replacement>> _replacementCache
  = new LruCache<StringValue, ArrayList<Replacement>>(1024);

  private static final HashMap<String, Value> _constMap
  = new HashMap<String, Value>();

  public String []getLoadedExtensions()
  {
    return new String[] { "pcre" };
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value ereg(Env env,
          StringValue pattern,
          StringValue string,
          @Optional @Reference Value regsV)
  {
    return eregImpl(env, pattern, string, regsV, false);
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value eregi(Env env,
          StringValue pattern,
          StringValue string,
          @Optional @Reference Value regsV)
  {
    return eregImpl(env, pattern, string, regsV, true);
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  protected static Value eregImpl(Env env,
                                  StringValue rawPattern,
                                  StringValue string,
                                  Value regsV,
                                  boolean isCaseInsensitive)
  {
    StringValue cleanPattern = cleanEregRegexp(rawPattern, false);
    
    if (isCaseInsensitive)
      cleanPattern = addDelimiters(env, cleanPattern, "/", "/i");
    else
      cleanPattern = addDelimiters(env, cleanPattern, "/", "/");

    try {
      Regexp regexp = getRegexp(env, cleanPattern, string);

      if (! (regexp.find())) {
        return BooleanValue.FALSE;
      }

      if (regsV != null && ! (regsV instanceof NullValue)) {
        ArrayValue regs = new ArrayValueImpl();
        regsV.set(regs);

        regs.put(LongValue.ZERO, regexp.group(env));
        int count = regexp.groupCount();

        for (int i = 1; i <= count; i++) {
          StringValue group = regexp.group(env, i);

          Value value;
          if (group == null)
            value = BooleanValue.FALSE;
          else
            value = group;

          regs.put(new LongValue(i), value);
        }

        int len = regexp.end() - regexp.start();

        if (len == 0)
          return LongValue.ONE;
        else
          return new LongValue(len);
      }
      else {
        return LongValue.ONE;
      }
    } catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the index of the first match.
   *
   * php/151u
   * The array that preg_match (PHP 5) returns does not have trailing unmatched
   * groups. Therefore, an unmatched group should not be added to the array
   * unless a matched group appears after it.  A couple applications like
   * Gallery2 expect this behavior in order to function correctly.
   * 
   * Only preg_match and preg_match_all(PREG_SET_ORDER) exhibits this odd
   * behavior.
   *
   * @param env the calling environment
   */
  public static Value preg_match(Env env,
                               StringValue regexpValue,
                               StringValue subject,
                               @Optional @Reference Value matchRef,
                               @Optional int flags,
                               @Optional int offset)
  {
    try {
      if (regexpValue.length() < 2) {
        env.warning(L.l("Regexp pattern must have opening and closing delimiters"));
        return LongValue.ZERO;
      }

      Regexp regexp = getRegexp(env, regexpValue, subject);

      ArrayValue regs;

      if (matchRef instanceof DefaultValue)
        regs = null;
      else
        regs = new ArrayValueImpl();

      if ((regexp == null) || (! (regexp.find(offset)))) {
        matchRef.set(regs);
        return LongValue.ZERO;
      }

      boolean isOffsetCapture = (flags & PREG_OFFSET_CAPTURE) != 0;

      if (regs != null) {
        if (isOffsetCapture) {
          ArrayValueImpl part = new ArrayValueImpl();
          part.append(regexp.group(env));
          part.append(new LongValue(regexp.start()));

          regs.put(LongValue.ZERO, part);
        }
        else
          regs.put(LongValue.ZERO, regexp.group(env));

        int count = regexp.groupCount();

        for (int i = 1; i <= count; i++) {
          StringValue group = regexp.group(env, i);

          if (group == null)
            continue;

          if (isOffsetCapture) {
            // php/151u
            // add unmatched groups first
            for (int j = regs.getSize(); j < i; j++) {
              ArrayValue part = new ArrayValueImpl();

              part.append(StringValue.EMPTY);
              part.append(LongValue.MINUS_ONE);

              regs.put(new LongValue(j), part);
            }

            ArrayValueImpl part = new ArrayValueImpl();
            part.append(group);
            part.append(new LongValue(regexp.start(i)));

            StringValue name = regexp.getGroupName(i);
            if (name != null)
              regs.put(name, part);

            regs.put(new LongValue(i), part);
          }
          else {
            // php/151u
            // add unmatched groups first
            for (int j = regs.getSize(); j < i; j++) {
              regs.put(new LongValue(j), StringValue.EMPTY);
            }

            StringValue name = regexp.getGroupName(i);
            if (name != null)
              regs.put(name, group);

            regs.put(new LongValue(i), group);
          }
        }

        matchRef.set(regs);
      }

      return LongValue.ONE;
    }
    catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value preg_match_all(Env env,
          StringValue rawRegexp,
          StringValue subject,
          @Reference Value matchRef,
          @Optional("PREG_PATTERN_ORDER") int flags,
          @Optional int offset)
  {
    try {
    if (rawRegexp.length() < 2) {
      env.warning(L.l("Pattern must have at least opening and closing delimiters"));
      return BooleanValue.FALSE;
    }

    if ((flags & PREG_PATTERN_ORDER) == 0) {
      // php/152m
      if ((flags & PREG_SET_ORDER) == 0) {
        flags = flags | PREG_PATTERN_ORDER;
      }
    }
    else {
      if ((flags & PREG_SET_ORDER) != 0) {
        env.warning((L.l("Cannot combine PREG_PATTER_ORDER and PREG_SET_ORDER")));
        return BooleanValue.FALSE;
      }
    }

    Regexp regexp = getRegexp(env, rawRegexp, subject);

    ArrayValue matches;

    if (matchRef instanceof ArrayValue)
      matches = (ArrayValue) matchRef;
    else
      matches = new ArrayValueImpl();

    matches.clear();

    matchRef.set(matches);

    if ((flags & PREG_PATTERN_ORDER) != 0) {
      return pregMatchAllPatternOrder(env,
              regexp,
              subject,
              matches,
              flags,
              offset);
    }
    else if ((flags & PREG_SET_ORDER) != 0) {
      return pregMatchAllSetOrder(env,
              regexp,
              subject,
              matches,
              flags,
              offset);
    }
    else
      throw new UnsupportedOperationException();
    }
    catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static LongValue pregMatchAllPatternOrder(Env env,
          Regexp regexp,
          StringValue subject,
          ArrayValue matches,
          int flags,
          int offset)
  {
    int groupCount = regexp == null ? 0 : regexp.groupCount();

    ArrayValue []matchList = new ArrayValue[groupCount + 1];

    for (int j = 0; j <= groupCount; j++) {
      ArrayValue values = new ArrayValueImpl();

      StringValue groupName = regexp.getGroupName(j);
      // XXX: named subpatterns causing conflicts with array indexes?
      if (groupName != null)
        matches.put(groupName, values);

      matches.put(values);
      matchList[j] = values;
    }

    if (regexp == null || (! (regexp.find()))) {
      return LongValue.ZERO;
    }

    int count = 0;

    do {
      count++;

      for (int j = 0; j <= groupCount; j++) {
        ArrayValue values = matchList[j];

        int start = regexp.start(j);
        int end = regexp.end(j);

        StringValue groupValue = subject.substring(start, end);

        if (groupValue != null)
          groupValue = groupValue.toUnicodeValue(env);

        Value result = NullValue.NULL;

        if (groupValue != null) {
          if ((flags & PREG_OFFSET_CAPTURE) != 0) {
            result = new ArrayValueImpl();
            result.put(groupValue);
            result.put(LongValue.create(start));
          } else {
            result = groupValue;
          }
        }

        values.put(result);
      }
    } while (regexp.find());

    return LongValue.create(count);
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  private static LongValue pregMatchAllSetOrder(Env env,
          Regexp regexp,
          StringValue subject,
          ArrayValue matches,
          int flags,
          int offset)
  {
    if ((regexp == null) || (! (regexp.find()))) {
      return LongValue.ZERO;
    }

    int count = 0;

    do {
      count++;

      ArrayValue matchResult = new ArrayValueImpl();
      matches.put(matchResult);

      for (int i = 0; i <= regexp.groupCount(); i++) {
        int start = regexp.start(i);
        int end = regexp.end(i);

        // group is unmatched, skip
        if (end - start <= 0)
          continue;

        StringValue groupValue = subject.substring(start, end);

        if (groupValue != null)
          groupValue = groupValue.toUnicodeValue(env);

        Value result = NullValue.NULL;

        if (groupValue != null) {

          if ((flags & PREG_OFFSET_CAPTURE) != 0) {

            // php/152n
            // add unmatched groups first
            for (int j = matchResult.getSize(); j < i; j++) {
              ArrayValue part = new ArrayValueImpl();

              part.append(StringValue.EMPTY);
              part.append(LongValue.MINUS_ONE);

              matchResult.put(LongValue.create(j), part);
            }


            result = new ArrayValueImpl();
            result.put(groupValue);
            result.put(LongValue.create(start));
          } else {


            // php/
            // add unmatched groups that was skipped
            for (int j = matchResult.getSize(); j < i; j++) {
              matchResult.put(LongValue.create(j), StringValue.EMPTY);
            }

            result = groupValue;
          }
        }

        matchResult.put(result);
      }
    } while (regexp.find());

    return LongValue.create(count);
  }

  /**
   * Quotes regexp values
   */
  public static String preg_quote(StringValue string,
          @Optional String delim)
  {
    StringBuilder sb = new StringBuilder();

    boolean []extra = null;

    if (delim != null && ! delim.equals("")) {
      extra = new boolean[256];

      for (int i = 0; i < delim.length(); i++)
        extra[delim.charAt(i)] = true;
    }

    int length = string.length();
    for (int i = 0; i < length; i++) {
      char ch = string.charAt(i);

      if (ch >= 256)
        sb.append(ch);
      else if (PREG_QUOTE[ch]) {
        sb.append('\\');
        sb.append(ch);
      }
      else if (extra != null && extra[ch]) {
        sb.append('\\');
        sb.append(ch);
      }
      else
        sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Loops through subject if subject is array of strings
   *
   * @param env
   * @param pattern string or array
   * @param replacement string or array
   * @param subject string or array
   * @param limit
   * @param count
   * @return
   */
  public static Value preg_replace(Env env,
          Value pattern,
          Value replacement,
          Value subject,
          @Optional("-1") long limit,
          @Optional @Reference Value count)
  {
    try {
      if (subject instanceof ArrayValue) {
        ArrayValue result = new ArrayValueImpl();

        for (Value value : ((ArrayValue) subject).values()) {
          result.put(pregReplace(env,
                     pattern,
                     replacement,
                     value.toStringValue(),
                     limit,
                     count));
        }

        return result;

      }
      else if (subject.isset()) {
        return pregReplace(env, pattern, replacement, subject.toStringValue(),
                           limit, count);
      } else
        return StringValue.EMPTY;
    }
    catch (IllegalRegexpException e) {
      e.printStackTrace();
      
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }

  }

  /**
   * Replaces values using regexps
   */
  private static Value pregReplace(Env env,
          Value patternValue,
          Value replacement,
          StringValue subject,
          @Optional("-1") long limit,
          Value countV)
    throws IllegalRegexpException
  {
    StringValue string = subject;

    if (limit < 0)
      limit = Long.MAX_VALUE;

    if (patternValue.isArray() && replacement.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;
      ArrayValue replacementArray = (ArrayValue) replacement;

      Iterator<Value> patternIter = patternArray.values().iterator();
      Iterator<Value> replacementIter = replacementArray.values().iterator();

      while (patternIter.hasNext() && replacementIter.hasNext()) {
        string = pregReplaceString(env,
                patternIter.next().toStringValue(),
                replacementIter.next().toStringValue(),
                string,
                limit,
                countV);
      }
    } else if (patternValue.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;

      for (Value value : patternArray.values()) {
        string = pregReplaceString(env,
                value.toStringValue(),
                replacement.toStringValue(),
                string,
                limit,
                countV);
      }
    } else {
      return pregReplaceString(env,
              patternValue.toStringValue(),
              replacement.toStringValue(),
              string,
              limit,
              countV);
    }

    return string;
  }

  /**
   * replaces values using regexps and callback fun
   * @param env
   * @param patternString
   * @param fun
   * @param subject
   * @param limit
   * @param countV
   * @return subject with everything replaced
   */
  private static StringValue pregReplaceCallbackImpl(Env env,
          StringValue patternString,
          Callback fun,
          StringValue subject,
          long limit,
          Value countV)
    throws IllegalRegexpException
  {

    long numberOfMatches = 0;

    if (limit < 0)
      limit = Long.MAX_VALUE;

    Regexp regexp = getRegexp(env, patternString, subject);

    StringValue result = new UnicodeBuilderValue();
    int tail = 0;

    while (regexp.find() && numberOfMatches < limit) {
      // Increment countV (note: if countV != null, then it should be a Var)
      if ((countV != null) && (countV instanceof Var)) {
        long count = ((Var) countV).getRawValue().toLong();
        countV.set(LongValue.create(count + 1));
      }

      if (tail < regexp.start())
        result = result.append(subject.substring(tail, regexp.start()));

      ArrayValue regs = new ArrayValueImpl();

      for (int i = 0; i <= regexp.groupCount(); i++) {
        StringValue group = regexp.group(env, i);

        if (group != null)
          regs.put(group);
        else
          regs.put(StringValue.EMPTY);
      }

      Value replacement = fun.call(env, regs);

      result = result.append(replacement);

      tail = regexp.end();

      numberOfMatches++;
    }

    if (tail < subject.length())
      result = result.append(subject.substring(tail));

    return result;
  }

  /**
   * Replaces values using regexps
   */
  private static StringValue pregReplaceString(Env env,
          StringValue patternString,
          StringValue replacement,
          StringValue subject,
          long limit,
          Value countV)
    throws IllegalRegexpException
  {
    Regexp regexp = getRegexp(env, patternString, subject);

    // check for e modifier in patternString
    boolean isEval = regexp.isEval();

    ArrayList<Replacement> replacementProgram
    = _replacementCache.get(replacement);

    if (replacementProgram == null) {
      replacementProgram = compileReplacement(env, replacement, isEval);
      _replacementCache.put(replacement, replacementProgram);
    }

    return pregReplaceStringImpl(env,
            regexp,
            replacementProgram,
            subject,
            limit,
            countV,
            isEval);
  }

  /**
   * Replaces values using regexps
   */
  public static Value ereg_replace(Env env,
          StringValue patternString,
          StringValue replacement,
          StringValue subject)
  {
    try {
      patternString = cleanEregRegexp(patternString, false);
      patternString = addDelimiters(env, patternString, "/", "/");
      
      Regexp regexp = getRegexp(env, patternString, subject);

      ArrayList<Replacement> replacementProgram
        = _replacementCache.get(replacement);

      if (replacementProgram == null) {
        replacementProgram = compileReplacement(env, replacement, false);
        _replacementCache.put(replacement, replacementProgram);
      }

      return pregReplaceStringImpl(env,
              regexp,
              replacementProgram,
              subject,
              -1,
              NullValue.NULL,
              false);
    }
    catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Replaces values using regexps
   */
  public static Value eregi_replace(Env env,
          StringValue patternString,
          StringValue replacement,
          StringValue subject)
  {
    try {
      patternString = cleanEregRegexp(patternString, false);
      patternString = addDelimiters(env, patternString, "/", "/i");
      
      Regexp regexp = getRegexp(env, patternString, subject);

      ArrayList<Replacement> replacementProgram
        = _replacementCache.get(replacement);

      if (replacementProgram == null) {
        replacementProgram = compileReplacement(env, replacement, false);
        _replacementCache.put(replacement, replacementProgram);
      }

      return pregReplaceStringImpl(env, regexp, replacementProgram,
          subject, -1, NullValue.NULL, false);
    }
    catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Replaces values using regexps
   */
  private static StringValue pregReplaceStringImpl(Env env,
          Regexp regexp,
          ArrayList<Replacement> replacementProgram,
          StringValue subject,
          long limit,
          Value countV,
          boolean isEval)
  {
    if (limit < 0)
      limit = Long.MAX_VALUE;

    int length = subject.length();

    UnicodeBuilderValue result = null;
    int tail = 0;

    int replacementLen = replacementProgram.size();

    while (regexp.find() && limit-- > 0) {
      if (result == null)
        result = new UnicodeBuilderValue();

      // Increment countV (note: if countV != null, then it should be a Var)
      if ((countV != null) && (countV instanceof Var)) {
        countV.set(LongValue.create(countV.toLong() + 1));
      }

      // append all text up to match
      if (tail < regexp.start())
        result.append(subject, tail, regexp.start());

      // if isEval then append replacement evaluated as PHP code
      // else append replacement string
      if (isEval) {
        UnicodeBuilderValue evalString = new UnicodeBuilderValue();

        for (int i = 0; i < replacementLen; i++) {
          Replacement replacement = replacementProgram.get(i);

          replacement.eval(evalString, subject, regexp);
        }

        try {
          if (evalString.length() > 0) // php/152z
            result.append(env.evalCode(evalString.toString()));
        } catch (IOException e) {
          throw new QuercusException(e);
        }
      } else {
        for (int i = 0; i < replacementLen; i++) {
          Replacement replacement = replacementProgram.get(i);

          replacement.eval(result, subject, regexp);
        }
      }

      tail = regexp.end();
    }

    if (result == null)
      return subject;

    if (tail < length)
      result.append(subject, tail, length);

    return result;
  }

  /**
   * Loops through subject if subject is array of strings
   *
   * @param env
   * @param pattern
   * @param fun
   * @param subject
   * @param limit
   * @param count
   * @return
   */
  public static Value preg_replace_callback(Env env,
          Value pattern,
          Callback fun,
          Value subject,
          @Optional("-1") long limit,
          @Optional @Reference Value count)
  {
    try {
      if (subject instanceof ArrayValue) {
        ArrayValue result = new ArrayValueImpl();

        for (Value value : ((ArrayValue) subject).values()) {
          result.put(pregReplaceCallback(env,
              pattern.toStringValue(),
              fun,
              value.toStringValue(),
              limit,
              count));
        }

        return result;

      } else if (subject instanceof StringValue) {
        return pregReplaceCallback(env,
            pattern.toStringValue(),
            fun,
            subject.toStringValue(),
            limit,
            count);
      } else {
        return NullValue.NULL;
      }
    }
    catch (IllegalRegexpException e) { 
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Replaces values using regexps
   */
  private static Value pregReplaceCallback(Env env,
          Value patternValue,
          Callback fun,
          StringValue subject,
          @Optional("-1") long limit,
          @Optional @Reference Value countV)
    throws IllegalRegexpException
  {
    if (limit < 0)
      limit = Long.MAX_VALUE;

    if (patternValue.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;

      for (Value value : patternArray.values()) {
        subject = pregReplaceCallbackImpl(env,
                value.toStringValue(),
                fun,
                subject,
                limit,
                countV);
      }

      return subject;

    } else if (patternValue instanceof StringValue) {
      return pregReplaceCallbackImpl(env,
              patternValue.toStringValue(),
              fun,
              subject,
              limit,
              countV);
    } else {
      return NullValue.NULL;
    }
  }

  /**
   * Returns array of substrings or
   * of arrays ([0] => substring [1] => offset) if
   * PREG_SPLIT_OFFSET_CAPTURE is set
   *
   * @param env the calling environment
   */
  public static Value preg_split(Env env,
          StringValue patternString,
          StringValue string,
          @Optional("-1") long limit,
          @Optional int flags)
  {
    try {
    
    if (limit <= 0)
      limit = Long.MAX_VALUE;

    Regexp regexp = getRegexp(env, patternString, string);

    ArrayValue result = new ArrayValueImpl();

    int head = 0;
    long count = 0;

    boolean allowEmpty = (flags & PREG_SPLIT_NO_EMPTY) == 0;
    boolean isCaptureOffset = (flags & PREG_SPLIT_OFFSET_CAPTURE) != 0; 
    boolean isCaptureDelim = (flags & PREG_SPLIT_DELIM_CAPTURE) != 0;

    GroupNeighborMap neighborMap
      = new GroupNeighborMap(regexp.getPattern(), regexp.groupCount());
    
    while (regexp.find()) {
      int startPosition = head;
      StringValue unmatched;

      // Get non-matching sequence
      if (count == limit - 1) {
        unmatched = string.substring(head);
        head = string.length();
      }
      else {
        unmatched = string.substring(head, regexp.start());
        head = regexp.end();
      }

      // Append non-matching sequence
      if (unmatched.length() != 0 || allowEmpty) {
        if (isCaptureOffset) {
          ArrayValue part = new ArrayValueImpl();

          part.put(unmatched);
          part.put(LongValue.create(startPosition));

          result.put(part);
        }
        else {
          result.put(unmatched);
        }

        count++;
      }

      if (count == limit)
        break;

      // Append parameterized delimiters
      if (isCaptureDelim) {
        for (int i = 1; i <= regexp.groupCount(); i++) {
          int start = regexp.start(i);
          int end = regexp.end(i);

          // Skip empty groups
          if (! regexp.isGroupMatched(i)) {
            continue;
          }

          // Append empty OR neighboring groups that were skipped
          // php/152r
          if (allowEmpty) {
            for (int j = i - 1; j >= 1; j--) {
              if (regexp.isGroupMatched(j))
                break;

              if (isCaptureOffset) {
                ArrayValue part = new ArrayValueImpl();

                part.put(StringValue.EMPTY);
                part.put(LongValue.create(startPosition));

                result.put(part);
              }
              else
                result.put(StringValue.EMPTY);
            }
          }

          if (end - start <= 0 && ! allowEmpty) {
            continue;
          }

          StringValue groupValue = string.substring(start, end);

          if (isCaptureOffset) {
            ArrayValue part = new ArrayValueImpl();

            part.put(groupValue);
            part.put(LongValue.create(startPosition));

            result.put(part);
          }
          else {
            result.put(groupValue);
          }
        }
      }
    }

    // Append non-matching sequence at the end
    if (count < limit && (head < string.length() || allowEmpty)) {
      if (isCaptureOffset) {
        ArrayValue part = new ArrayValueImpl();

        part.put(string.substring(head));
        part.put(LongValue.create(head));

        result.put(part);
      }
      else {
        result.put(string.substring(head));
      }
    }

    return result;
    }
    catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Makes a regexp for a case-insensitive match.
   */
  public static String sql_regcase(String string)
  {
    StringBuilder sb = new StringBuilder();

    int len = string.length();
    for (int i = 0; i < len; i++) {
      char ch = string.charAt(i);

      if (Character.isLowerCase(ch)) {
        sb.append('[');
        sb.append(Character.toUpperCase(ch));
        sb.append(ch);
        sb.append(']');
      }
      else if (Character.isUpperCase(ch)) {
        sb.append('[');
        sb.append(ch);
        sb.append(Character.toLowerCase(ch));
        sb.append(']');
      }
      else
        sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  public static Value split(Env env,
          StringValue patternString,
          StringValue string,
          @Optional("-1") long limit)
  {
    try {
      if (limit < 0)
        limit = Long.MAX_VALUE;

      patternString = addDelimiters(env, patternString, "/", "/");
      
      Regexp regexp = getRegexp(env, patternString, string);

      ArrayValue result = new ArrayValueImpl();

      long count = 0;
      int head = 0;

      while ((regexp.find()) && (count < limit)) {
        StringValue value;
        if (count == limit - 1) {
          value = regexp.substring(env, head);
          head = string.length();
        } else {
          value = regexp.substring(env, head, regexp.start());
          head = regexp.end();
        }

        result.put(value);

        count++;
      }

      if ((head <= string.length() && (count != limit))) {
        result.put(regexp.substring(env, head));
      }

      return result;

    } catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns an array of all the values that matched the given pattern if the
   * flag no flag is passed.  Otherwise it will return an array of all the
   * values that did not match.
   *
   * @param patternString the pattern
   * @param input the array to check the pattern against
   * @param flag 0 for matching and 1 for elements that do not match
   * @return an array of either matching elements are non-matching elements
   */
  public static Value preg_grep(Env env,
          StringValue patternString,
          ArrayValue input,
          @Optional("0") int flag)
  {
    try {
      if (input == null)
        return NullValue.NULL;

      Regexp regexp = getRegexp(env, patternString);

      ArrayValue matchArray = new ArrayValueImpl();

      for (Map.Entry<Value, Value> entry : input.entrySet()) {
        Value entryValue = entry.getValue();
        Value entryKey = entry.getKey();

        regexp.init(env, entryValue.toStringValue());

        boolean found = regexp.find();

        if (!found && (flag == PREG_GREP_INVERT))
          matchArray.append(entryKey, entryValue);
        else if (found && (flag != PREG_GREP_INVERT))
          matchArray.append(entryKey, entryValue);
      }

      return matchArray;
    } catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }

  /**
   * Returns an array of strings produces from splitting the passed string
   * around the provided pattern.  The pattern is case insensitive.
   *
   * @param patternString the pattern
   * @param string the string to split
   * @param limit if specified, the maximum number of elements in the array
   * @return an array of strings split around the pattern string
   */
  public static Value spliti(Env env,
          StringValue patternString,
          StringValue string,
          @Optional("-1") long limit)
  {
    try {
      if (limit < 0)
        limit = Long.MAX_VALUE;

      // php/151c

      patternString = addDelimiters(env, patternString, "/", "/i");
      
      Regexp regexp = getRegexp(env, patternString, string);

      ArrayValue result = new ArrayValueImpl();

      long count = 0;
      int head = 0;

      while ((regexp.find()) && (count < limit)) {
        StringValue value;
        if (count == limit - 1) {
          value = string.substring(head);
          head = string.length();
        } else {
          value = string.substring(head, regexp.start());
          head = regexp.end();
        }

        result.put(value);

        count++;
      }

      if ((head <= string.length()) && (count != limit)) {
        result.put(string.substring(head));
      }

      return result;

    } catch (IllegalRegexpException e) {
      log.log(Level.FINE, e.getMessage(), e);
      env.warning(e);
      
      return BooleanValue.FALSE;
    }
  }
  
  private static Regexp getRegexp(Env env,
                                  StringValue rawRegexp,
                                  StringValue subject)
    throws IllegalRegexpException
  {
    Regexp regexp = getRegexp(env, rawRegexp);

    regexp.init(env, subject);

    return regexp;
  }

  private static Regexp getRegexp(Env env,
                                  StringValue rawRegexp)
    throws IllegalRegexpException
  {
    Regexp regexp = _regexpCache.get(rawRegexp);

    if (regexp != null)
      return regexp.clone();

    regexp = new Regexp(env, rawRegexp);

    _regexpCache.put(rawRegexp, regexp);

    return regexp;
  }
  
  private static StringValue addDelimiters(Env env,
                                           StringValue str,
                                           String startDelim,
                                           String endDelim)
  {
    if (str.isUnicode()) {
      UnicodeBuilderValue sb = new UnicodeBuilderValue();

      sb.append(startDelim);
      sb.append(str);
      sb.append(endDelim);

      return sb;
    }
    else {
      BinaryBuilderValue sb = new BinaryBuilderValue();

      sb.append(startDelim.getBytes());
      sb.append(str.toBinaryValue(env).toBytes());
      sb.append(endDelim.getBytes());
      
      return sb;
    }
  }

  private static ArrayList<Replacement>
  compileReplacement(Env env, StringValue replacement, boolean isEval)
  {
    ArrayList<Replacement> program = new ArrayList<Replacement>();
    StringBuilder text = new StringBuilder();

    for (int i = 0; i < replacement.length(); i++) {
      char ch = replacement.charAt(i);

      if ((ch == '\\' || ch == '$') && i + 1 < replacement.length()) {
        char digit;

        if ('0' <= (digit = replacement.charAt(i + 1)) && digit <= '9') {
          int group = digit - '0';
          i++;

          if (i + 1 < replacement.length() &&
                  '0' <= (digit = replacement.charAt(i + 1)) && digit <= '9') {
            group = 10 * group + digit - '0';
            i++;
          }

          if (text.length() > 0) {
            program.add(new TextReplacement(text));
          }

          if (isEval)
            program.add(new GroupEscapeReplacement(group));
          else
            program.add(new GroupReplacement(group));

          text.setLength(0);
        }
        else if (ch == '\\') {
          i++;

          if (digit != '\\') {
            text.append('\\');
          }
          text.append(digit);
          // took out test for ch == '$' because must be true
          //} else if (ch == '$' && digit == '{') {
        } else if (digit == '{') {
          i += 2;

          int group = 0;

          while (i < replacement.length() &&
                  '0' <= (digit = replacement.charAt(i)) && digit <= '9') {
            group = 10 * group + digit - '0';

            i++;
          }

          if (digit != '}') {
            env.warning(L.l("bad regexp {0}", replacement));
            throw new QuercusException("bad regexp");
          }

          if (text.length() > 0)
            program.add(new TextReplacement(text));

          if (isEval)
            program.add(new GroupEscapeReplacement(group));
          else
            program.add(new GroupReplacement(group));

          text.setLength(0);
        }
        else
          text.append(ch);
      }
      else
        text.append(ch);
    }

    if (text.length() > 0)
      program.add(new TextReplacement(text));

    return program;
  }

  private static final String [] POSIX_CLASSES = {
    "[:alnum:]", "[:alpha:]", "[:blank:]", "[:cntrl:]",
    "[:digit:]", "[:graph:]", "[:lower:]", "[:print:]",
    "[:punct:]", "[:space:]", "[:upper:]", "[:xdigit:]"
  };

  private static final String [] REGEXP_CLASSES = {
    "\\p{Alnum}", "\\p{Alpha}", "\\p{Blank}", "\\p{Cntrl}",
    "\\p{Digit}", "\\p{Graph}", "\\p{Lower}", "\\p{Print}",
    "\\p{Punct}", "\\p{Space}", "\\p{Upper}", "\\p{XDigit}"
  };

  /**
   * Cleans the regexp from valid values that the Java regexps can't handle.
   * Ereg has a different syntax so need to handle it differently from preg.
   */
  private static StringValue cleanEregRegexp(StringValue regexp,
                                             boolean isComments)
  {
    int len = regexp.length();

    StringBuilder sb = new StringBuilder();
    char quote = 0;

    boolean sawVerticalBar = false;

    for (int i = 0; i < len; i++) {
      char ch = regexp.charAt(i);

      if (sawVerticalBar) {
        if ((! Character.isWhitespace(ch)) &&
                ch != '#' &&
                ch != '|')
          sawVerticalBar = false;
      }

      switch (ch) {
      case '\\':
        if (quote == '[') {
          sb.append('\\');
          sb.append('\\');
          continue;
        }

        if (i + 1 < len) {
          i++;

          ch = regexp.charAt(i);

          if (ch == '0' ||
                  '1' <= ch && ch <= '3' && i + 1 < len && '0' <= regexp.charAt(i + 1) && ch <= '7') {
            // Java's regexp requires \0 for octal

            sb.append('\\');
            sb.append('0');
            sb.append(ch);
          }
          else if (ch == 'x' && i + 1 < len && regexp.charAt(i + 1) == '{') {
            sb.append('\\');

            int tail = regexp.indexOf('}', i + 1);

            if (tail > 0) {
              StringValue hex = regexp.substring(i + 2, tail);

              int length = hex.length();

              if (length == 1)
                sb.append("x0" + hex);
              else if (length == 2)
                sb.append("x" + hex);
              else if (length == 3)
                sb.append("u0" + hex);
              else if (length == 4)
                sb.append("u" + hex);
              else
                throw new QuercusRuntimeException(L.l("illegal hex escape"));

              i = tail;
            }
            else {
              sb.append("\\x");
            }
          }
          else if (Character.isLetter(ch)) {
            switch (ch) {
            case 'a':
            case 'c':
            case 'e':
            case 'f':
            case 'n':
            case 'r':
            case 't':
            case 'x':
            case 'd':
            case 'D':
            case 's':
            case 'S':
            case 'w':
            case 'W':
            case 'b':
            case 'B':
            case 'A':
            case 'Z':
            case 'z':
            case 'G':
            case 'p': //XXX: need to translate PHP properties to Java ones
            case 'P': //XXX: need to translate PHP properties to Java ones
            case 'X':
              //case 'C': byte matching, not supported
              sb.append('\\');
              sb.append(ch);
              break;
            default:
              sb.append(ch);
            }
          }
          else {
            sb.append('\\');
            sb.append(ch);
          }
        }
        else
          sb.append('\\');
        break;

      case '[':
        if (quote == '[') {
          if (i + 1 < len && regexp.charAt(i + 1) == ':') {
            String test = regexp.substring(i).toString();
            boolean hasMatch = false;

            for (int j = 0; j < POSIX_CLASSES.length; j++) {
              if (test.startsWith(POSIX_CLASSES[j])) {
                hasMatch = true;

                sb.append(REGEXP_CLASSES[j]);

                i += POSIX_CLASSES[j].length() - 1;
              }
            }

            if (! hasMatch)
              sb.append("\\[");
          }
          else
            sb.append("\\[");
        }
        else if (i + 1 < len && regexp.charAt(i + 1) == '['
          && ! (i + 2 < len && regexp.charAt(i + 2) == ':')) {
          // XXX: check regexp grammar
          // php/151n
          sb.append("[\\[");
          i += 1;
        }
        else if (i + 2 < len &&
                regexp.charAt(i + 1) == '^' &&
                regexp.charAt(i + 2) == ']') {
          sb.append("[^\\]");
          i += 2;
        }
        else
          sb.append('[');

        if (quote == 0)
          quote = '[';
        break;

      case '#':
        if (quote == '[') {
          sb.append("\\#");
        }
        else if (isComments) {
          sb.append(ch);

          for (i++; i < len; i++) {
            ch = regexp.charAt(i);

            sb.append(ch);

            if (ch == '\n' || ch == '\r')
              break;
          }
        }
        else {
          sb.append(ch);
        }

        break;

      case ']':
        sb.append(ch);

        if (quote == '[')
          quote = 0;
        break;

      case '{':
        if (i + 1 < len &&
                ('0' <= (ch = regexp.charAt(i + 1)) && ch <= '9' || ch == ',')) {
          sb.append("{");
          for (i++;
          i < len &&
          ('0' <= (ch = regexp.charAt(i)) && ch <= '9' || ch == ',');
          i++) {
            sb.append(ch);
          }

          if (i < len)
            sb.append(regexp.charAt(i));
        }
        else {
          sb.append("\\{");
        }
        break;

      case '}':
        sb.append("\\}");
        break;

      case '|':
        // php/152o
        // php ignores subsequent vertical bars
        //
        // to accomodate drupal bug http://drupal.org/node/123750
        if (! sawVerticalBar) {
          sb.append('|');
          sawVerticalBar = true; 
        }
        break;

      default:
        sb.append(ch);
      }
    }

    String cleanPattern = sb.toString();

    if (regexp.isUnicode()) {
      return new UnicodeValueImpl(cleanPattern);
    }
    else {
      BinaryBuilderValue bb = new BinaryBuilderValue();
      
      for (int i = 0; i < cleanPattern.length(); i++) {
        bb.appendByte(cleanPattern.charAt(i));
      }
      
      return bb;
    }
  }

  static class Replacement {
    void eval(UnicodeBuilderValue sb, StringValue subject, Regexp regexp)
    {
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[]";
    }
  }

  static class TextReplacement
  extends Replacement
  {
    private char []_text;

    TextReplacement(StringBuilder text)
    {
      int length = text.length();

      _text = new char[length];

      text.getChars(0, length, _text, 0);
    }

    @Override
    void eval(UnicodeBuilderValue sb, StringValue subject, Regexp regexp)
    {
      sb.append(_text, 0, _text.length);
    }

    public String toString()
    {
      StringBuilder sb = new StringBuilder();
      sb.append(getClass().getSimpleName());

      sb.append('[');

      for (char ch : _text)
        sb.append(ch);

      sb.append(']');

      return sb.toString();
    }
  }

  static class GroupReplacement
  extends Replacement
  {
    private int _group;

    GroupReplacement(int group)
    {
      _group = group;
    }

    @Override
    void eval(UnicodeBuilderValue sb, StringValue subject, Regexp regexp)
    {
      if (_group <= regexp.groupCount())
        sb.append(subject.substring(regexp.start(_group),
                regexp.end(_group)));
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[" + _group + "]";
    }
  }

  static class GroupEscapeReplacement
  extends Replacement
  {
    private int _group;

    GroupEscapeReplacement(int group)
    {
      _group = group;
    }

    @Override
    void eval(UnicodeBuilderValue sb, StringValue subject, Regexp regexp)
    {
      if (_group <= regexp.groupCount()) {
        StringValue group = subject.substring(regexp.start(_group),
                regexp.end(_group));;
                int len = group.length();

                for (int i = 0; i < len; i++) {
                  char ch = group.charAt(i);

                  if (ch == '\'')
                    sb.append("\\\'");
                  else if (ch == '\"')
                    sb.append("\\\"");
                  else
                    sb.append(ch);
                }
      }
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[" + _group + "]";
    }
  }

  /**
   * Holds information about the left neighbor of a particular group.
   */
  static class GroupNeighborMap
  {
    private int []_neighborMap;

    private static int UNSET = -1;

    public GroupNeighborMap(CharSequence regexp, int groups)
    { 
      _neighborMap = new int[groups + 1];

      for (int i = 1; i <= groups; i++) {
        _neighborMap[i] = UNSET;
      }

      boolean sawEscape = false;
      boolean sawVerticalBar = false;
      boolean isLiteral = false;

      int group = 0;
      int parent = UNSET;
      int length = regexp.length();

      ArrayList<Boolean> openParenStack = new ArrayList<Boolean>(groups);

      for (int i = 0; i < length; i++) {
        char ch = regexp.charAt(i);

        if (ch == ' ' || ch == '\t' || ch == '\n' || ch == 'r' || ch == '\f') {
          continue;
        }
        else if (ch == '\\') {
          sawEscape = ! sawEscape;
          continue;
        }
        else if (ch == '[' && ! sawEscape) {
          isLiteral = true;
        }
        else if (ch == ']' && ! sawEscape) {
          isLiteral = false;
        }
        else if (isLiteral || sawEscape) {
          sawEscape = false;
        }
        else if (ch == '(') {
          if (i + 1 < length && regexp.charAt(i + 1) == '?') {
            openParenStack.add(true);
            continue;
          }

          openParenStack.add(false);
          group++;

          if (sawVerticalBar) {
            sawVerticalBar = false;
            _neighborMap[group] = group - 1;
          }
          else {
            _neighborMap[group] = parent;
            parent = group;
          }
        }
        else if (ch == ')') {
          if (openParenStack.remove(openParenStack.size() - 1))
            continue;

          sawVerticalBar = false;
        }
        else if (ch == '|') {
          sawVerticalBar = true;
        }
        else {
        }
      }
    }

    public boolean hasNeighbor(int group)
    {
      return _neighborMap[group] != UNSET;
    }

    public int getNeighbor(int group)
    {
      return _neighborMap[group];
    }
  }
  
  static {
    PREG_QUOTE['\\'] = true;
    PREG_QUOTE['+'] = true;
    PREG_QUOTE['*'] = true;
    PREG_QUOTE['?'] = true;
    PREG_QUOTE['['] = true;
    PREG_QUOTE['^'] = true;
    PREG_QUOTE[']'] = true;
    PREG_QUOTE['$'] = true;
    PREG_QUOTE['('] = true;
    PREG_QUOTE[')'] = true;
    PREG_QUOTE['{'] = true;
    PREG_QUOTE['}'] = true;
    PREG_QUOTE['='] = true;
    PREG_QUOTE['!'] = true;
    PREG_QUOTE['<'] = true;
    PREG_QUOTE['>'] = true;
    PREG_QUOTE['|'] = true;
    PREG_QUOTE[':'] = true;
    PREG_QUOTE['.'] = true;

  }
}
