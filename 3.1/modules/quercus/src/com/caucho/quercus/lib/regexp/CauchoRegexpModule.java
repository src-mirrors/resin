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

package com.caucho.quercus.lib.regexp;

import com.caucho.quercus.QuercusException;
import com.caucho.quercus.QuercusRuntimeException;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.Reference;
import com.caucho.quercus.annotation.UsesSymbolTable;
import com.caucho.quercus.env.*;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.util.L10N;
import com.caucho.util.LruCache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

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

  // #2526, possible JIT/OS problem with max comparison
  private static final long LONG_MAX = Long.MAX_VALUE - 1;

  public static final boolean [] PREG_QUOTE = new boolean[256];

  private static final LruCache<StringValue, Regexp> _regexpCache
  = new LruCache<StringValue, Regexp>(1024);

  private static final LruCache<StringValue, ArrayList<Replacement>> _replacementCache
  = new LruCache<StringValue, ArrayList<Replacement>>(1024);

  private static final HashMap<String, Value> _constMap
  = new HashMap<String, Value>();

  @Override
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
          Value pattern,
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
          Value pattern,
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
                                  Value rawPattern,
                                  StringValue string,
                                  Value regsV,
                                  boolean isCaseInsensitive)
  {
    // php/1511 : error when pattern argument is null or an empty string

    if (rawPattern.length() == 0) {
      env.warning(L.l("empty pattern argument"));
      return BooleanValue.FALSE;
    }

    // php/1512 : non-string pattern argument is converted to
    // an integer value and formatted as a string.

    StringValue rawPatternStr;

    if (! (rawPattern instanceof StringValue)) {
      rawPatternStr = rawPattern.toLongValue().toStringValue();
    } else {
      rawPatternStr = rawPattern.toStringValue();
    }

    StringValue cleanPattern = cleanEregRegexp(env, rawPatternStr, false);

    if (isCaseInsensitive)
      cleanPattern = addDelimiters(env, cleanPattern, "/", "/i");
    else
      cleanPattern = addDelimiters(env, cleanPattern, "/", "/");

    try {
      Regexp regexp = getRegexp(env, cleanPattern);
      RegexpState regexpState = new RegexpState(env, regexp, string);

      if (! regexpState.find())
        return BooleanValue.FALSE;

      if (regsV != null && ! (regsV instanceof NullValue)) {
        ArrayValue regs = new ArrayValueImpl();
        regsV.set(regs);

        regs.put(LongValue.ZERO, regexpState.group(env));
        int count = regexpState.groupCount();

        for (int i = 1; i < count; i++) {
          StringValue group = regexpState.group(env, i);

          Value value;
          if (group == null || group.length() == 0)
            value = BooleanValue.FALSE;
          else
            value = group;

          regs.put(new LongValue(i), value);
        }

        int len = regexpState.end() - regexpState.start();

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

      StringValue empty = subject.getEmptyString();
      
      Regexp regexp = getRegexp(env, regexpValue);
      RegexpState regexpState = new RegexpState(env, regexp, subject);

      ArrayValue regs;

      if (matchRef instanceof DefaultValue)
        regs = null;
      else
        regs = new ArrayValueImpl();

      if (regexpState == null || regexpState.exec(env, subject, offset) < 0) {
        matchRef.set(regs);
        return LongValue.ZERO;
      }

      boolean isOffsetCapture = (flags & PREG_OFFSET_CAPTURE) != 0;

      if (regs != null) {
        if (isOffsetCapture) {
          ArrayValueImpl part = new ArrayValueImpl();
          part.append(regexpState.group(env));
          part.append(LongValue.create(regexpState.start()));

          regs.put(LongValue.ZERO, part);
        }
        else
          regs.put(LongValue.ZERO, regexpState.group(env));

        int count = regexpState.groupCount();
        for (int i = 1; i < count; i++) {
          if (! regexpState.isMatchedGroup(i))
            continue;
          
          StringValue group = regexpState.group(env, i);

          if (isOffsetCapture) {
            // php/151u
            // add unmatched groups first
            for (int j = regs.getSize(); j < i; j++) {
              ArrayValue part = new ArrayValueImpl();

              part.append(empty);
              part.append(LongValue.MINUS_ONE);

              regs.put(LongValue.create(j), part);
            }

            ArrayValueImpl part = new ArrayValueImpl();
            part.append(group);
            part.append(LongValue.create(regexpState.start(i)));

            StringValue name = regexpState.getGroupName(i);
            if (name != null)
              regs.put(name, part);

            regs.put(LongValue.create(i), part);
          }
          else {
            // php/151u
            // add unmatched groups first
            for (int j = regs.getSize(); j < i; j++) {
              regs.put(LongValue.create(j), empty);
            }

            StringValue name = regexp.getGroupName(i);
            if (name != null)
              regs.put(name, group);

            regs.put(LongValue.create(i), group);
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
   * Returns the number of full pattern matches or FALSE on error.
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

      Regexp regexp = getRegexp(env, rawRegexp);
      RegexpState regexpState = new RegexpState(env, regexp, subject);

      ArrayValue matches;

      if (matchRef instanceof ArrayValue)
	matches = (ArrayValue) matchRef;
      else
	matches = new ArrayValueImpl();

      matches.clear();

      matchRef.set(matches);

      if ((flags & PREG_PATTERN_ORDER) != 0) {
	return pregMatchAllPatternOrder(env,
					regexpState,
					subject,
					matches,
					flags,
					offset);
      }
      else if ((flags & PREG_SET_ORDER) != 0) {
	return pregMatchAllSetOrder(env,
				    regexp,
				    regexpState,
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
						   RegexpState regexpState,
						   StringValue subject,
						   ArrayValue matches,
						   int flags,
						   int offset)
  {
    int groupCount = regexpState == null ? 0 : regexpState.groupCount();

    ArrayValue []matchList = new ArrayValue[groupCount + 1];

    StringValue emptyStr = subject.getEmptyString();
    
    for (int j = 0; j < groupCount; j++) {
      ArrayValue values = new ArrayValueImpl();

      Value patternName = regexpState.getGroupName(j);
      
      // XXX: named subpatterns causing conflicts with array indexes?
      if (patternName != null)
        matches.put(patternName, values);

      matches.put(values);
      matchList[j] = values;
    }

    int count = 0;

    while (regexpState.find()) {
      count++;

      for (int j = 0; j < groupCount; j++) {
        ArrayValue values = matchList[j];
        
        if (! regexpState.isMatchedGroup(j)) {
          /*
          if (j == groupCount || (flags & PREG_OFFSET_CAPTURE) == 0)
            values.put(emptyStr);
          else {
            Value result = new ArrayValueImpl();
            
            result.put(emptyStr);
            result.put(LongValue.MINUS_ONE);
            
            values.put(result);
          }
          */
          
          values.put(emptyStr);
            
          continue;
        }

        StringValue groupValue = regexpState.group(env, j);

        Value result = NullValue.NULL;

        if (groupValue != null) {
          if ((flags & PREG_OFFSET_CAPTURE) != 0) {
            result = new ArrayValueImpl();
            result.put(groupValue);

            result.put(LongValue.create(regexpState.getBegin(j)));
          } else {
            result = groupValue;
          }
        }

        values.put(result);
      }
    }

    return LongValue.create(count);
  }

  /**
   * Returns the index of the first match.
   *
   * @param env the calling environment
   */
  private static LongValue pregMatchAllSetOrder(Env env,
						Regexp regexp,
						RegexpState regexpState,
						StringValue subject,
						ArrayValue matches,
						int flags,
						int offset)
  {
    if (regexpState == null || ! regexpState.find()) {
      return LongValue.ZERO;
    }

    StringValue empty = subject.getEmptyString();
    
    int count = 0;

    do {
      count++;

      ArrayValue matchResult = new ArrayValueImpl();
      matches.put(matchResult);

      for (int i = 0; i < regexpState.groupCount(); i++) {
        int start = regexpState.start(i);
        int end = regexpState.end(i);

        // group is unmatched, skip
        if (end - start <= 0)
          continue;

        StringValue groupValue = regexpState.group(env, i);

        Value result = NullValue.NULL;

        if (groupValue != null) {

          if ((flags & PREG_OFFSET_CAPTURE) != 0) {

            // php/152n
            // add unmatched groups first
            for (int j = matchResult.getSize(); j < i; j++) {
              ArrayValue part = new ArrayValueImpl();

              part.append(empty);
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
              matchResult.put(LongValue.create(j), empty);
            }

            result = groupValue;
          }
        }

        matchResult.put(result);
      }
    } while (regexpState.find());

    return LongValue.create(count);
  }

  /**
   * Quotes regexp values
   */
  public static StringValue preg_quote(StringValue string,
				       @Optional StringValue delim)
  {
    StringValue sb = string.createStringBuilder();

    boolean []extra = null;

    if (delim != null && delim.length() > 0) {
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

    return sb;
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
  @UsesSymbolTable
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
        return env.createEmptyString();
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
      limit = LONG_MAX;

    if (patternValue.isArray() && replacement.isArray()) {
      ArrayValue patternArray = (ArrayValue) patternValue;
      ArrayValue replacementArray = (ArrayValue) replacement;

      Iterator<Value> patternIter = patternArray.values().iterator();
      Iterator<Value> replacementIter = replacementArray.values().iterator();

      while (patternIter.hasNext()) {
        StringValue replacementStr;

        if (replacementIter.hasNext())
          replacementStr = replacementIter.next().toStringValue();
        else
          replacementStr = env.createEmptyString();

        string = pregReplaceString(env,
				   patternIter.next().toStringValue(),
				   replacementStr,
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
    StringValue empty = subject.getEmptyString();
    
    long numberOfMatches = 0;

    if (limit < 0)
      limit = LONG_MAX;

    Regexp regexp = getRegexp(env, patternString);
    RegexpState regexpState = new RegexpState(env, regexp, subject);

    StringValue result = patternString.createStringBuilder();
    int tail = 0;

    while (regexpState.find() && numberOfMatches < limit) {
      // Increment countV (note: if countV != null, then it should be a Var)
      if (countV != null && countV instanceof Var) {
        long count = ((Var) countV).getRawValue().toLong();
        countV.set(LongValue.create(count + 1));
      }

      int start = regexpState.start();
      if (tail < start)
        result = result.append(regexpState.substring(env, tail, start));

      ArrayValue regs = new ArrayValueImpl();

      for (int i = 0; i < regexpState.groupCount(); i++) {
        StringValue group = regexpState.group(env, i);

        if (group != null)
          regs.put(group);
        else
          regs.put(empty);
      }

      Value replacement = fun.call(env, regs);

      result = result.append(replacement);

      tail = regexpState.end();

      numberOfMatches++;
    }

    if (tail < subject.length())
      result = result.append(regexpState.substring(env, tail));

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
    Regexp regexp = getRegexp(env, patternString);
    RegexpState regexpState = new RegexpState(env, regexp, subject);

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
				 regexpState,
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
          Value pattern,
          Value replacement,
          StringValue subject)
  {
    return eregReplaceImpl(env, pattern, replacement, subject, false);
  }

  /**
   * Replaces values using regexps
   */
  public static Value eregi_replace(Env env,
          Value pattern,
          Value replacement,
          StringValue subject)
  {
    return eregReplaceImpl(env, pattern, replacement, subject, true);
  }

  /**
   * Replaces values using regexps
   */

  protected static Value eregReplaceImpl(Env env,
                                  Value pattern,
                                  Value replacement,
                                  StringValue subject,
                                  boolean isCaseInsensitive)
  {
    StringValue patternStr;
    StringValue replacementStr;

    // php/1511 : error when pattern argument is null or an empty string

    if (pattern.length() == 0) {
      env.warning(L.l("empty pattern argument"));
      return BooleanValue.FALSE;
    }

    // php/150u : If a non-string type argument is passed
    // for the pattern or replacement argument, it is
    // converted to a string of length 1 that contains
    // a single character.

    if (pattern instanceof StringValue) {
      patternStr = pattern.toStringValue();
    } else {
      patternStr = env.createString(
        String.valueOf((char) pattern.toLong()));
    }

    if (replacement instanceof NullValue) {
      replacementStr = env.createEmptyString();
    } else if (replacement instanceof StringValue) {
      replacementStr = replacement.toStringValue();
    } else {
      replacementStr = env.createString(
        String.valueOf((char) replacement.toLong()));
    }

    try {
      patternStr = cleanEregRegexp(env, patternStr, false);
      if (isCaseInsensitive)
        patternStr = addDelimiters(env, patternStr, "/", "/i");
      else
        patternStr = addDelimiters(env, patternStr, "/", "/");

      Regexp regexp = getRegexp(env, patternStr);
      RegexpState regexpState = new RegexpState(env, regexp, subject);

      ArrayList<Replacement> replacementProgram
        = _replacementCache.get(replacementStr);

      if (replacementProgram == null) {
        replacementProgram = compileReplacement(env, replacementStr, false);
        _replacementCache.put(replacementStr, replacementProgram);
      }

      return pregReplaceStringImpl(env,
				   regexp,
				   regexpState,
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
  private static StringValue
    pregReplaceStringImpl(Env env,
			  Regexp regexp,
			  RegexpState regexpState,
			  ArrayList<Replacement> replacementProgram,
			  StringValue subject,
			  long limit,
			  Value countV,
			  boolean isEval)
  {
    if (limit < 0)
      limit = LONG_MAX;

    int length = subject.length();

    StringValue result = subject.createStringBuilder();

    int tail = 0;
    boolean isMatched = false;
    
    int replacementLen = replacementProgram.size();

    while (limit-- > 0 && regexpState.find()) {
      isMatched = true;
      
      // Increment countV (note: if countV != null, then it should be a Var)
      if (countV != null && countV instanceof Var) {
        countV.set(LongValue.create(countV.toLong() + 1));
      }

      // append all text up to match
      int start = regexpState.start();
      if (tail < start)
        result = result.append(regexpState.substring(env, tail, start));
      
      // if isEval then append replacement evaluated as PHP code
      // else append replacement string
      if (isEval) {
        StringValue evalString = subject.createStringBuilder();

        for (int i = 0; i < replacementLen; i++) {
          Replacement replacement = replacementProgram.get(i);

          evalString = replacement.eval(env, evalString, regexpState);
        }

        try {
          if (evalString.length() > 0) { // php/152z
            result = result.append(env.evalCode(evalString.toString()));
          }
        } catch (IOException e) {
          throw new QuercusException(e);
        }
        
      } else {
        for (int i = 0; i < replacementLen; i++) {
          Replacement replacement = replacementProgram.get(i);
          
          result = replacement.eval(env, result, regexpState);
        }
      }

      tail = regexpState.end();
    }
    
    if (! isMatched)
      return subject;
    
    if (tail < length)
      result = result.append(regexpState.substring(env, tail));

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

      } else if (subject.isset()) {
        return pregReplaceCallback(env,
            pattern.toStringValue(),
            fun,
            subject.toStringValue(),
            limit,
            count);
      } else {
        return env.createEmptyString();
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
      limit = LONG_MAX;

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

    } else if (subject.isset()) {
      return pregReplaceCallbackImpl(env,
              patternValue.toStringValue(),
              fun,
              subject,
              limit,
              countV);
    } else {
      return env.createEmptyString();
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
	limit = LONG_MAX;

      StringValue empty = patternString.getEmptyString();
    
      Regexp regexp = getRegexp(env, patternString);
      RegexpState regexpState = new RegexpState(env, regexp, string);

      ArrayValue result = new ArrayValueImpl();

      int head = 0;
      long count = 0;

      boolean allowEmpty = (flags & PREG_SPLIT_NO_EMPTY) == 0;
      boolean isCaptureOffset = (flags & PREG_SPLIT_OFFSET_CAPTURE) != 0; 
      boolean isCaptureDelim = (flags & PREG_SPLIT_DELIM_CAPTURE) != 0;

      GroupNeighborMap neighborMap
	= new GroupNeighborMap(regexp.getPattern(), regexpState.groupCount());
    
      while (regexpState.find()) {
	int startPosition = head;
	StringValue unmatched;

	// Get non-matching sequence
	if (count == limit - 1) {
	  unmatched = string.substring(head);
	  head = string.length();
	}
	else {
	  unmatched = string.substring(head, regexpState.start());
	  head = regexpState.end();
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
	  for (int i = 1; i < regexpState.groupCount(); i++) {
	    int start = regexpState.start(i);
	    int end = regexpState.end(i);

	    // Skip empty groups
	    if (! regexpState.isMatchedGroup(i)) {
	      continue;
	    }

	    // Append empty OR neighboring groups that were skipped
	    // php/152r
	    if (allowEmpty) {
	      int group = i;
	      while (neighborMap.hasNeighbor(group)) {
		group = neighborMap.getNeighbor(group);

		if (regexpState.isMatchedGroup(group))
		  break;

		if (isCaptureOffset) {
		  ArrayValue part = new ArrayValueImpl();

		  part.put(empty);
		  part.put(LongValue.create(startPosition));

		  result.put(part);
		}
		else
		  result.put(empty);
	      }
	    }

	    if (end - start <= 0 && ! allowEmpty) {
	      continue;
	    }

	    StringValue groupValue = regexpState.group(env, i);

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
  public static StringValue sql_regcase(StringValue string)
  {
    StringValue sb = string.createStringBuilder();

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

    return sb;
  }

  /**
   * Returns an array of strings produces from splitting the passed string
   * around the provided pattern.  The pattern is case sensitive.
   *
   * @param patternString the pattern
   * @param string the string to split
   * @param limit if specified, the maximum number of elements in the array
   * @return an array of strings split around the pattern string
   */
  public static Value split(Env env,
			    StringValue patternString,
			    StringValue string,
			    @Optional("-1") long limit)
  {
    return splitImpl(env, patternString, string, limit, false);
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
    return splitImpl(env, patternString, string, limit, true);
  }

  /**
   * Split string into array by regular expression
   *
   * @param env the calling environment
   */

  private static Value splitImpl(Env env,
			    StringValue patternString,
			    StringValue string,
			    long limit,
                            boolean isCaseInsensitive)
  {
    try {
      if (limit < 0)
        limit = LONG_MAX;

      // php/151c

      if (isCaseInsensitive)
        patternString = addDelimiters(env, patternString, "/", "/i");
      else
        patternString = addDelimiters(env, patternString, "/", "/");

      Regexp regexp = getRegexp(env, patternString);
      RegexpState regexpState = new RegexpState(env, regexp, string);

      ArrayValue result = new ArrayValueImpl();

      long count = 0;
      int head = 0;

      while (regexpState.find() && count < limit) {
        StringValue value;
        if (count == limit - 1) {
          value = regexpState.substring(env, head);
          head = string.length();
        } else {
          value = regexpState.substring(env, head, regexpState.start());
          head = regexpState.end();
        }

        result.put(value);

        count++;
      }

      if (head <= string.length() && count != limit) {
        result.put(regexpState.substring(env, head));
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
      RegexpState regexpState = new RegexpState(regexp);

      ArrayValue matchArray = new ArrayValueImpl();

      for (Map.Entry<Value, Value> entry : input.entrySet()) {
        Value entryValue = entry.getValue();
        Value entryKey = entry.getKey();

        boolean found = regexpState.find(env, entryValue.toStringValue());

        if (! found && flag == PREG_GREP_INVERT)
          matchArray.append(entryKey, entryValue);
        else if (found && flag != PREG_GREP_INVERT)
          matchArray.append(entryKey, entryValue);
      }

      return matchArray;
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
    return getRegexp(env, rawRegexp);
  }

  private static Regexp getRegexp(Env env,
                                  StringValue rawRegexp)
    throws IllegalRegexpException
  {
    Regexp regexp = _regexpCache.get(rawRegexp);

    if (regexp != null)
      return regexp;

    regexp = new Regexp(env, rawRegexp);

    _regexpCache.put(rawRegexp, regexp);

    return regexp;
  }
  
  private static StringValue addDelimiters(Env env,
                                           StringValue str,
                                           String startDelim,
                                           String endDelim)
  {
    StringValue sb = str.createStringBuilder();
    
    sb = sb.appendBytes(startDelim);
    sb = sb.append(str);
    sb = sb.appendBytes(endDelim);
    
    return sb;
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

          if (i + 1 < replacement.length()
	      && '0' <= (digit = replacement.charAt(i + 1)) && digit <= '9') {
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

          while (i < replacement.length()
		 && '0' <= (digit = replacement.charAt(i)) && digit <= '9') {
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

  /**
   * Cleans the regexp from valid values that the Java regexps can't handle.
   * Ereg has a different syntax so need to handle it differently from preg.
   */
  private static StringValue cleanEregRegexp(Env env,
                                             StringValue regexp,
                                             boolean isComments)
  {
    int len = regexp.length();

    StringValue sb = regexp.createStringBuilder();
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
          sb = sb.appendByte('\\');
          sb = sb.appendByte('\\');
          continue;
        }

        if (i + 1 < len) {
          i++;

          ch = regexp.charAt(i);

          if (ch == '0' ||
                  '1' <= ch && ch <= '3' && i + 1 < len && '0' <= regexp.charAt(i + 1) && ch <= '7') {
            // Java's regexp requires \0 for octal

            sb = sb.appendByte('\\');
            sb = sb.appendByte('0');
            sb = sb.appendByte(ch);
          }
          else if (ch == 'x' && i + 1 < len && regexp.charAt(i + 1) == '{') {
            sb = sb.appendByte('\\');

            int tail = regexp.indexOf('}', i + 1);

            if (tail > 0) {
              StringValue hex = regexp.substring(i + 2, tail);

              int length = hex.length();

              if (length == 1)
                sb = sb.appendBytes("x0" + hex);
              else if (length == 2)
                sb = sb.appendBytes("x" + hex);
              else if (length == 3)
                sb = sb.appendBytes("u0" + hex);
              else if (length == 4)
                sb = sb.appendBytes("u" + hex);
              else
                throw new QuercusRuntimeException(L.l("illegal hex escape"));

              i = tail;
            }
            else {
              sb = sb.appendByte('\\');
              sb = sb.appendByte('x');
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
              sb = sb.appendByte('\\');
              sb = sb.appendByte(ch);
              break;
            default:
              sb = sb.appendByte(ch);
            }
          }
          else {
            sb = sb.appendByte('\\');
            sb = sb.appendByte(ch);
          }
        }
        else
          sb = sb.appendByte('\\');
        break;

      case '[':
        if (quote == '[') {
          if (i + 1 < len && regexp.charAt(i + 1) == ':') {
            sb = sb.appendByte('[');
          }
          else {
            sb = sb.appendByte('\\');
            sb = sb.appendByte('[');
          }
        }
        else if (i + 1 < len && regexp.charAt(i + 1) == '['
          && ! (i + 2 < len && regexp.charAt(i + 2) == ':')) {
          // XXX: check regexp grammar
          // php/151n
          sb = sb.appendByte('[');
          sb = sb.appendByte('\\');
          sb = sb.appendByte('[');
          i += 1;
        }
        /*
        else if (i + 2 < len &&
                regexp.charAt(i + 1) == '^' &&
                regexp.charAt(i + 2) == ']') {
          sb.append("[^\\]");
          i += 2;
        }
        */
        else
          sb = sb.appendByte('[');

        if (quote == 0)
          quote = '[';
        break;

      case '#':
        if (quote == '[') {
          sb = sb.appendByte('\\');
          sb = sb.appendByte('#');
        }
        else if (isComments) {
          sb = sb.appendByte(ch);

          for (i++; i < len; i++) {
            ch = regexp.charAt(i);

            sb = sb.appendByte(ch);

            if (ch == '\n' || ch == '\r')
              break;
          }
        }
        else {
          sb = sb.appendByte(ch);
        }

        break;

      case ']':
        sb = sb.appendByte(ch);

        if (quote == '[')
          quote = 0;
        break;

      case '{':
        if (i + 1 < len &&
                ('0' <= (ch = regexp.charAt(i + 1)) && ch <= '9' || ch == ',')) {
          sb = sb.appendByte('{');
          for (i++;
          i < len &&
          ('0' <= (ch = regexp.charAt(i)) && ch <= '9' || ch == ',');
          i++) {
            sb = sb.appendByte(ch);
          }

          if (i < len)
            sb = sb.appendByte(regexp.charAt(i));
        }
        else {
          sb = sb.appendByte('\\');
          sb = sb.appendByte('{');
        }
        break;

      case '}':
        sb = sb.appendByte('\\');
        sb = sb.appendByte('}');
        break;

      case '|':
        // php/152o
        // php ignores subsequent vertical bars
        //
        // to accomodate drupal bug http://drupal.org/node/123750
        if (! sawVerticalBar) {
          sb = sb.appendByte('|');
          sawVerticalBar = true; 
        }
        break;

      default:
        sb = sb.appendByte(ch);
      }
    }

    return sb;
  }

  abstract static class Replacement {
    abstract StringValue eval(Env env,
			      StringValue sb,
			      RegexpState regexpState);

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
    StringValue eval(Env env,
                     StringValue sb,
                     RegexpState regexpState)
    {
      return sb.appendBytes(_text, 0, _text.length);
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
    StringValue eval(Env env,
                     StringValue sb,
                     RegexpState regexpState)
    {
      if (_group < regexpState.groupCount())
        sb = sb.append(regexpState.group(env, _group));
      
      return sb;
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
    StringValue eval(Env env,
                     StringValue sb,
                     RegexpState regexpState)
    {
      if (_group < regexpState.groupCount()) {
        StringValue group = regexpState.group(env, _group);

        int len = group.length();

        for (int i = 0; i < len; i++) {
          char ch = group.charAt(i);

          if (ch == '\'') {
            sb = sb.appendByte('\\');
            sb = sb.appendByte('\'');
          }
          else if (ch == '\"') {
            sb = sb.appendByte('\\');
            sb = sb.appendByte('\"');
          }
          else
            sb = sb.appendByte(ch);
        }
      }
      
      return sb;
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
