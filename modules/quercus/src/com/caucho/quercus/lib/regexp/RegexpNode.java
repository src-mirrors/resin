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

import com.caucho.util.*;
import com.caucho.quercus.env.StringValue;

class RegexpNode {
  private static final L10N L = new L10N(RegexpNode.class);
  
  static final int RC_END = 0;
  static final int RC_NULL = 1;
  static final int RC_STRING = 2;
  static final int RC_SET = 3;
  static final int RC_NSET = 4;
  static final int RC_BEG_GROUP = 5;
  static final int RC_END_GROUP = 6;
  
  static final int RC_GROUP_REF = 7;
  static final int RC_LOOP = 8;
  static final int RC_LOOP_INIT = 9;
  static final int RC_LOOP_SHORT = 10;
  static final int RC_LOOP_UNIQUE = 11;
  static final int RC_LOOP_SHORT_UNIQUE = 12;
  static final int RC_LOOP_LONG = 13;
  
  static final int RC_OR = 64;
  static final int RC_OR_UNIQUE = 65;
  static final int RC_POS_LOOKAHEAD = 66;
  static final int RC_NEG_LOOKAHEAD = 67;
  static final int RC_POS_LOOKBEHIND = 68;
  static final int RC_NEG_LOOKBEHIND = 69;
  static final int RC_LOOKBEHIND_OR = 70;
  
  static final int RC_WORD = 73;
  static final int RC_NWORD = 74;
  static final int RC_BLINE = 75;
  static final int RC_ELINE = 76;
  static final int RC_BSTRING = 77;
  static final int RC_ESTRING = 78;
  static final int RC_ENSTRING = 79;
  static final int RC_GSTRING = 80;
  
  // conditionals
  static final int RC_COND = 81;
  
  // ignore case
  static final int RC_STRING_I = 128;
  static final int RC_SET_I = 129;
  static final int RC_NSET_I = 130;
  static final int RC_GROUP_REF_I = 131;

  static final int RC_LEXEME = 256;
  
  // unicode properties
  static final int RC_UNICODE = 512;
  static final int RC_NUNICODE = 513;

  // unicode properties sets
  static final int RC_C = 1024;
  static final int RC_L = 1025;
  static final int RC_M = 1026;
  static final int RC_N = 1027;
  static final int RC_P = 1028;
  static final int RC_S = 1029;
  static final int RC_Z = 1030;
  
  // negated unicode properties sets
  static final int RC_NC = 1031;
  static final int RC_NL = 1032;
  static final int RC_NM = 1033;
  static final int RC_NN = 1034;
  static final int RC_NP = 1035;
  static final int RC_NS = 1036;
  static final int RC_NZ = 1037;
  
  // POSIX character classes
  static final int RC_CHAR_CLASS = 2048;
  static final int RC_ALNUM = 1;
  static final int RC_ALPHA = 2;
  static final int RC_BLANK = 3;
  static final int RC_CNTRL = 4;
  static final int RC_DIGIT = 5;
  static final int RC_GRAPH = 6;
  static final int RC_LOWER = 7;
  static final int RC_PRINT = 8;
  static final int RC_PUNCT = 9;
  static final int RC_SPACE = 10;
  static final int RC_UPPER = 11;
  static final int RC_XDIGIT = 12;
  
  /*
  static final int RC_C = 512;
  static final int RC_CC = 513;
  static final int RC_CF = 514;
  static final int RC_CN = 515;
  static final int RC_CO = 516;
  static final int RC_CS = 517;
  static final int RC_L = 518;
  static final int RC_LL = 519;
  static final int RC_LM = 520;
  static final int RC_LO = 521;
  static final int RC_LT = 522;
  static final int RC_LU = 523;
  static final int RC_M = 524;
  static final int RC_MC = 525;
  static final int RC_ME = 526;
  static final int RC_MN = 527;
  static final int RC_N = 528;
  static final int RC_ND = 529;
  static final int RC_NL = 530;
  static final int RC_NO = 531;
  static final int RC_P = 532;
  static final int RC_PC = 533;
  static final int RC_PD = 534;
  static final int RC_PE = 535;
  static final int RC_PF = 536;
  static final int RC_PI = 537;
  static final int RC_PO = 538;
  static final int RC_PS = 539;
  static final int RC_S = 540;
  static final int RC_SC = 541;
  static final int RC_SK = 542;
  static final int RC_SM = 543;
  static final int RC_SO = 544;
  static final int RC_Z = 545;
  static final int RC_ZL = 546;
  static final int RC_ZP = 547;
  static final int RC_ZS = 548;
  */
  
  public static final int FAIL = -1;
  public static final int SUCCESS = 0;
  
  static final RegexpNode N_END = new End();
  
  static final RegexpNode ANY_CHAR;
  
  //for lookbehind
  int _length;
  
  /**
   * Creates a node with a code
   */
  protected RegexpNode()
  {
  }

  //
  // parsing constructors
  //

  RegexpNode concat(RegexpNode next)
  {
    return new Concat(this, next);
  }

  /**
   * '?' operator
   */
  RegexpNode createOptional(Regcomp parser)
  {
    return createLoop(parser, 0, 1);
  }

  /**
   * '*' operator
   */
  RegexpNode createStar(Regcomp parser)
  {
    return createLoop(parser, 0, Integer.MAX_VALUE);
  }

  /**
   * '+' operator
   */
  RegexpNode createPlus(Regcomp parser)
  {
    return createLoop(parser, 1, Integer.MAX_VALUE);
  }

  /**
   * Any loop
   */
  RegexpNode createLoop(Regcomp parser, int min, int max)
  {
    return new LoopHead(parser, this, min, max);
  }

  /**
   * Any loop
   */
  RegexpNode createLoopUngreedy(Regcomp parser, int min, int max)
  {
    return new LoopHeadUngreedy(parser, this, min, max);
  }

  /**
   * Possessive loop
   */
  RegexpNode createPossessiveLoop(int min, int max)
  {
    return new PossessiveLoop(getHead(), min, max);
  }

  /**
   * Create an or expression
   */
  RegexpNode createOr(RegexpNode node)
  {
    return Or.create(getHead(), node.getHead());
  }

  //
  // optimization functions
  //

  int minLength()
  {
    return 0;
  }

  String prefix()
  {
    return "";
  }

  int firstChar()
  {
    return -1;
  }

  boolean isNullable()
  {
    return false;
  }

  boolean []firstSet(boolean []firstSet)
  {
    return null;
  }

  RegexpNode getTail()
  {
    return this;
  }

  RegexpNode getHead()
  {
    return this;
  }

  //
  // matching
  //
  
  int match(StringValue string, int offset, RegexpState state)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }
  
  int match(CharCursor cursor, Regexp state)
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public Object clone()
  {
    throw new UnsupportedOperationException(getClass().getName());
  }

  public String toString()
  {
    String name = getClass().getName();
    int p = name.lastIndexOf('$');

    if (p < 0)
      p = name.lastIndexOf('.');
    
    return name.substring(p + 1) + "[]";
  }

  /**
   * A node with exactly one character matches.
   */
  static class AbstractCharNode extends RegexpNode {
    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      return new CharLoop(this, min, max);
    }

    @Override
    int minLength()
    {
      return 1;
    }
  }
    
  static class CharNode extends AbstractCharNode {
    private char _ch;

    CharNode(char ch)
    {
      _ch = ch;
    }

    @Override
    int firstChar()
    {
      return _ch;
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      if (firstSet != null && _ch < firstSet.length) {
	firstSet[_ch] = true;
      
	return firstSet;
      }
      else
	return null;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length() && string.charAt(offset) == _ch)
	return offset + 1;
      else
	return -1;
    }
  }

  static final AnchorBegin ANCHOR_BEGIN = new AnchorBegin();
  static final AnchorBeginOrNewline ANCHOR_BEGIN_OR_NEWLINE
    = new AnchorBeginOrNewline();
  static final AnchorEnd ANCHOR_END = new AnchorEnd();
  static final AnchorEndOnly ANCHOR_END_ONLY = new AnchorEndOnly();
  static final AnchorEndOrNewline ANCHOR_END_OR_NEWLINE
    = new AnchorEndOrNewline();
  
  private static class AnchorBegin extends NullableNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == 0)
	  return offset;
	else
	  return -1;
    }
  }
  
  private static class AnchorBeginOrNewline extends NullableNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == 0 || string.charAt(offset - 1) == '\n')
	  return offset;
	else
	  return -1;
    }
  }
  
  private static class AnchorEnd extends NullableNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == string.length() ||
	  offset + 1 == string.length() && string.charAt(offset) == '\n')
	return offset;
      else
	return -1;
    }
  }
  
  private static class AnchorEndOnly extends NullableNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == string.length())
	return offset;
      else
	return -1;
    }
  }
  
  private static class AnchorEndOrNewline extends NullableNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == string.length() || string.charAt(offset) == '\n')
	return offset;
      else
	return -1;
    }
  }

  static final RegexpNode DIGIT = RegexpSet.DIGIT.createNode();
  static final RegexpNode NOT_DIGIT = RegexpSet.DIGIT.createNotNode();

  static final RegexpNode DOT = RegexpSet.DOT.createNotNode();
  static final RegexpNode NOT_DOT = RegexpSet.DOT.createNode();

  static final RegexpNode SPACE = RegexpSet.SPACE.createNode();
  static final RegexpNode NOT_SPACE = RegexpSet.SPACE.createNotNode();

  static final RegexpNode S_WORD = RegexpSet.WORD.createNode();
  static final RegexpNode NOT_S_WORD = RegexpSet.WORD.createNotNode();
    
  static class AsciiSet extends AbstractCharNode {
    private final boolean []_set;

    AsciiSet()
    {
      _set = new boolean[128];
    }

    AsciiSet(boolean []set)
    {
      _set = set;
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      if (firstSet == null)
	return null;

      for (int i = 0; i < _set.length; i++) {
	if (_set[i])
	  firstSet[i] = true;
      }

      return null;
    }

    void setChar(char ch)
    {
      _set[ch] = true;
    }

    void clearChar(char ch)
    {
      _set[ch] = false;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (string.length() <= offset)
	return -1;

      char ch = string.charAt(offset);

      if (ch < 128 && _set[ch])
	return offset + 1;
      else
	return -1;
    }
  }
    
  static class AsciiNotSet extends AbstractCharNode {
    private final boolean []_set;

    AsciiNotSet()
    {
      _set = new boolean[128];
    }

    AsciiNotSet(boolean []set)
    {
      _set = set;
    }

    void setChar(char ch)
    {
      _set[ch] = true;
    }

    void clearChar(char ch)
    {
      _set[ch] = false;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (string.length() <= offset)
	return -1;

      char ch = string.charAt(offset);

      if (ch < 128 && _set[ch])
	return -1;
      else
	return offset + 1;
    }
  }
  
  static class CharLoop extends RegexpNode {
    private final RegexpNode _node;
    private RegexpNode _next = N_END;

    private int _min;
    private int _max;

    CharLoop(RegexpNode node, int min, int max)
    {
      _node = node.getHead();
      _min = min;
      _max = max;

      if (_min < 0)
	throw new IllegalStateException();
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      if (next == null)
	throw new NullPointerException();
      
      if (_next != null)
	_next = _next.concat(next);
      else
	_next = next.getHead();

      return this;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      if (min == 0 && max == 1) {
	_min = 0;
      
	return this;
      }
      else
	return new LoopHead(parser, this, min, max);
    }

    @Override
    int minLength()
    {
      return _min;
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      firstSet = _node.firstSet(firstSet);

      if (_min > 0 && ! _node.isNullable())
	return firstSet;

      firstSet = _next.firstSet(firstSet);
      
      return firstSet;
    }

    //
    // match functions
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      RegexpNode next = _next;
      RegexpNode node = _node;
      int min = _min;
      int max = _max;

      int i;
      
      int headOffset = offset;
      
      for (i = 0; i < min; i++) {
	if (node.match(string, offset + i, state) < 0)
	  return -1;
      }

      for (; i < max; i++) {
	if (node.match(string, offset + i, state) < 0) {
	  break;
	}
      }

      for (; min <= i; i--) {
	int tail = next.match(string, offset + i, state);

	if (tail >= 0)
	  return tail;
      }

      return -1;
    }

    public String toString()
    {
      return "CharLoop[" + _node + ", " + _next + "]";
    }
  }
  
  static class Concat extends RegexpNode {
    private final RegexpNode _head;
    private RegexpNode _next;

    Concat(RegexpNode head, RegexpNode next)
    {
      if (head == null || next == null)
	throw new NullPointerException();
      
      _head = head;
      _next = next;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      _next = _next.concat(next);

      return this;
    }

    //
    // optim functions
    //

    @Override
    int minLength()
    {
      return _head.minLength() + _next.minLength();
    }

    @Override
    int firstChar()
    {
      return _head.firstChar();
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      firstSet = _head.firstSet(firstSet);

      if (_head.isNullable())
	firstSet = _next.firstSet(firstSet);

      return firstSet;
    }

    @Override
    String prefix()
    {
      return _head.prefix();
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      offset = _head.match(string, offset, state);

      if (offset < 0)
	return -1;
      else
	return _next.match(string, offset, state);
    }

    public String toString()
    {
      return "Concat[" + _head + ", " + _next + "]";
    }
  }
  
  static class ConditionalHead extends RegexpNode {
    private RegexpNode _first;
    private RegexpNode _second;
    private RegexpNode _tail;
    private final int _group;

    ConditionalHead(int group)
    {
      _group = group;

      _tail = new ConditionalTail(this);
    }

    void setFirst(RegexpNode first)
    {
      _first = first;
    }

    void setSecond(RegexpNode second)
    {
      _second = second;
    }

    void setTail(RegexpNode tail)
    {
      _tail = tail;
    }

    RegexpNode getTail()
    {
      return _tail;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      _tail.concat(next);

      return this;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      return _tail.createLoop(parser, min, max);
    }

    /**
     * Create an or expression
     */
    @Override
    RegexpNode createOr(RegexpNode node)
    {
      return _tail.createOr(node);
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int begin = state.getBegin(_group);
      int end = state.getEnd(_group);

      if (_group <= state.getLength() && begin <= end) {
	return _first.match(string, offset, state);
      }
      else if (_second != null)
	return _second.match(string, offset, state);
      else
	return _tail.match(string, offset, state);
    }
  }
  
  static class ConditionalTail extends RegexpNode {
    private RegexpNode _head;
    private RegexpNode _next;

    ConditionalTail(ConditionalHead head)
    {
      _next = N_END;
      _head = head;
      head.setTail(this);
    }

    RegexpNode getHead()
    {
      return _head;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      if (_next != null)
	_next = _next.concat(next);
      else
	_next = next;

      return _head;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      LoopHead head = new LoopHead(parser, _head, min, max);

      _next = _next.concat(head.getTail());
      
      return head;
    }

    @Override
    RegexpNode createLoopUngreedy(Regcomp parser, int min, int max)
    {
      LoopHeadUngreedy head = new LoopHeadUngreedy(parser, _head, min, max);

      _next = _next.concat(head.getTail());
      
      return head;
    }

    /**
     * Create an or expression
     */
    @Override
    RegexpNode createOr(RegexpNode node)
    {
      _next = _next.createOr(node);

      return getHead();
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      return _next.match(string, offset, state);
    }
  }
  
  static class End extends RegexpNode {
    @Override
    RegexpNode concat(RegexpNode next)
    {
      return next;
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      return offset;
    }
  }

  
  static class Group extends RegexpNode {
    private final RegexpNode _node;
    private final int _group;

    Group(RegexpNode node, int group)
    {
      _node = node.getHead();
      _group = group;
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int oldBegin = state.getBegin(_group);
      int oldEnd = state.getEnd(_group);
      
      state.setBegin(_group, offset);
      
      int tail = _node.match(string, offset, state);

      if (tail >= 0) {
	state.setEnd(_group, tail);
	return tail;
      }
      else {
	state.setBegin(_group, oldBegin);

	return -1;
      }
    }
  }
  
  static class GroupHead extends RegexpNode {
    private RegexpNode _node;
    private RegexpNode _tail;
    private final int _group;

    GroupHead(int group)
    {
      _group = group;
      _tail = new GroupTail(group, this);
    }

    void setNode(RegexpNode node)
    {
      _node = node.getHead();

      // php/4eh1
      if (_node == this)
	_node = _tail;
    }

    RegexpNode getTail()
    {
      return _tail;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      _tail.concat(next);

      return this;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      return _tail.createLoop(parser, min, max);
    }

    @Override
    RegexpNode createLoopUngreedy(Regcomp parser, int min, int max)
    {
      return _tail.createLoopUngreedy(parser, min, max);
    }

    /**
     * Create an or expression
     */
    @Override
    RegexpNode createOr(RegexpNode node)
    {
      return _tail.createOr(node);
    }

    @Override
    int minLength()
    {
      return _node.minLength();
    }

    @Override
    int firstChar()
    {
      return _node.firstChar();
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      return _node.firstSet(firstSet);
    }

    @Override
    String prefix()
    {
      return _node.prefix();
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int oldBegin = state.getBegin(_group);
      state.setBegin(_group, offset);

      int tail = _node.match(string, offset, state);

      if (tail >= 0)
	return tail;
      else {
	state.setBegin(_group, oldBegin);
	return tail;
      }
    }

    public String toString()
    {
      return "GroupHead[" + _group + ", " + _node + "]";
    }
  }
  
  static class GroupTail extends RegexpNode {
    private RegexpNode _head;
    private RegexpNode _next;
    private final int _group;

    private GroupTail(int group, GroupHead head)
    {
      _next = N_END;
      _head = head;
      _group = group;
    }

    RegexpNode getHead()
    {
      return _head;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      if (_next != null)
	_next = _next.concat(next);
      else
	_next = next;

      return _head;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      LoopHead head = new LoopHead(parser, _head, min, max);

      _next = head.getTail();
      
      return head;
    }

    @Override
    RegexpNode createLoopUngreedy(Regcomp parser, int min, int max)
    {
      LoopHeadUngreedy head = new LoopHeadUngreedy(parser, _head, min, max);

      _next = head.getTail();
      
      return head;
    }

    /**
     * Create an or expression
     */
    @Override
    RegexpNode createOr(RegexpNode node)
    {
      _next = _next.createOr(node);

      return getHead();
    }

    @Override
    int minLength()
    {
      return _next.minLength();
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int oldEnd = state.getEnd(_group);
      int oldLength = state.getLength();
      
      if (_group > 0) {
	state.setEnd(_group, offset);

	if (oldLength < _group)
	  state.setLength(_group);
      }

      int tail = _next.match(string, offset, state);

      if (tail < 0) {
	state.setEnd(_group, oldEnd);
	state.setLength(oldLength);
	
	return -1;
      }
      else
	return tail;
    }

    public String toString()
    {
      return "GroupTail[" + _group + ", " + _next + "]";
    }
  }
  
  static class GroupRef extends RegexpNode {
    private final int _group;

    GroupRef(int group)
    {
      _group = group;
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (state.getLength() < _group)
	return -1;
      
      int begin = state.getBegin(_group);
      int length = state.getEnd(_group) - begin;

      if (string.regionMatches(offset, string, begin, length)) {
	return offset + length;
      }
      else
	return -1;
    }
  }
  
  static class Lookahead extends RegexpNode {
    private final RegexpNode _head;

    Lookahead(RegexpNode head)
    {
      _head = head;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (_head.match(string, offset, state) >= 0)
	return offset;
      else
	return -1;
    }
  }
  
  static class NotLookahead extends RegexpNode {
    private final RegexpNode _head;

    NotLookahead(RegexpNode head)
    {
      _head = head;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (_head.match(string, offset, state) < 0)
	return offset;
      else
	return -1;
    }
  }
  
  static class Lookbehind extends RegexpNode {
    private final RegexpNode _head;

    Lookbehind(RegexpNode head)
    {
      _head = head.getHead();
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int length = _head.minLength();

      if (offset < length)
	return -1;
      else if (_head.match(string, offset - length, state) >= 0)
	return offset;
      else
	return -1;
    }
  }
  
  static class NotLookbehind extends RegexpNode {
    private final RegexpNode _head;

    NotLookbehind(RegexpNode head)
    {
      _head = head;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int length = _head.minLength();

      if (offset < length)
	return offset;
      else if (_head.match(string, offset - length, state) < 0)
	return offset;
      else
	return -1;
    }
  }

  /**
   * A nullable node can match an empty string.
   */
  abstract static class NullableNode extends RegexpNode {
    @Override
    boolean isNullable()
    {
      return true;
    }
  }
  
  static class LoopHead extends RegexpNode {
    private final int _index;
    
    final RegexpNode _node;
    private final RegexpNode _tail;

    private int _min;
    private int _max;

    LoopHead(Regcomp parser, RegexpNode node, int min, int max)
    {
      _index = parser.nextLoopIndex();
      _tail = new LoopTail(_index, this);
      _node = node.concat(_tail);
      _min = min;
      _max = max;
    }

    @Override
    RegexpNode getTail()
    {
      return _tail;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      _tail.concat(next);

      return this;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      if (min == 0 && max == 1) {
	_min = 0;
      
	return this;
      }
      else
	return new LoopHead(parser, this, min, max);
    }

    @Override
    int minLength()
    {
      return _min * _node.minLength() + _tail.minLength();
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      firstSet = _node.firstSet(firstSet);

      if (_min > 0 && ! _node.isNullable())
	return firstSet;

      firstSet = _tail.firstSet(firstSet);
      
      return firstSet;
    }

    //
    // match functions
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      state._loopCount[_index] = 0;
      
      RegexpNode next = _tail;
      RegexpNode node = _node;
      int min = _min;
      
      for (int i = 0; i < min; i++) {
	state._loopCount[_index] = i;
      
	offset = node.match(string, offset, state);

	if (offset < 0)
	  return -1;
      }

      if (min < _max) {
	state._loopCount[_index] = min;
	state._loopOffset[_index] = offset;
	int tail = node.match(string, offset, state);
	if (tail >= 0)
	  return tail;
      }

      return _tail.match(string, offset, state);
    }

    public String toString()
    {
      return "LoopHead[" + _min + ", " + _max + ", " + _node + "]";
    }
  }
  
  static class LoopTail extends RegexpNode {
    private final int _index;

    private LoopHead _head;
    private RegexpNode _next;

    LoopTail(int index, LoopHead head)
    {
      _index = index;
      _head = head;
      _next = N_END;
    }

    RegexpNode getHead()
    {
      return _head;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      if (_next != null)
	_next = _next.concat(next);
      else
	_next = next;

      if (_next == this)
	throw new IllegalStateException();

      return this;
    }

    //
    // match functions
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int oldCount = state._loopCount[_index];

      if (oldCount < _head._min)
	return offset;
      else if (oldCount + 1 < _head._max) {
	int oldOffset = state._loopOffset[_index];
	
	if (oldOffset != offset) {
	  state._loopCount[_index] = oldCount + 1;
	  state._loopOffset[_index] = offset;
			    
	  int tail = _head._node.match(string, offset, state);
	  if (tail >= 0)
	    return tail;

	  state._loopCount[_index] = oldCount;
	  state._loopOffset[_index] = oldOffset;
	}
      }
      
      return _next.match(string, offset, state);
    }

    public String toString()
    {
      return "LoopTail[" + _next + "]";
    }
  }
  
  static class LoopHeadUngreedy extends RegexpNode {
    private final int _index;
    
    final RegexpNode _node;
    private final LoopTailUngreedy _tail;

    private int _min;
    private int _max;

    LoopHeadUngreedy(Regcomp parser, RegexpNode node, int min, int max)
    {
      _index = parser.nextLoopIndex();
      _min = min;
      _max = max;

      _tail = new LoopTailUngreedy(_index, this);
      _node = node.getTail().concat(_tail).getHead();
    }

    @Override
    RegexpNode getTail()
    {
      return _tail;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      _tail.concat(next);

      return this;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      if (min == 0 && max == 1) {
	_min = 0;
      
	return this;
      }
      else
	return new LoopHead(parser, this, min, max);
    }

    @Override
    int minLength()
    {
      return _min * _node.minLength() + _tail.minLength();
    }

    //
    // match functions
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      state._loopCount[_index] = 0;
      
      RegexpNode next = _tail;
      RegexpNode node = _node;
      int min = _min;
      
      for (int i = 0; i < min; i++) {
	state._loopCount[_index] = i;
	state._loopOffset[_index] = offset;
      
	offset = node.match(string, offset, state);

	if (offset < 0)
	  return -1;
      }

      int tail = _tail._next.match(string, offset, state);
      if (tail >= 0)
	return tail;

      if (min < _max) {
	state._loopCount[_index] = min;
	state._loopOffset[_index] = offset;
      
	return node.match(string, offset, state);
      }
      else
	return -1;
    }

    public String toString()
    {
      return "LoopHeadUngreedy[" + _min + ", " + _max + ", " + _node + "]";
    }
  }
  
  static class LoopTailUngreedy extends RegexpNode {
    private final int _index;

    private LoopHeadUngreedy _head;
    private RegexpNode _next;

    LoopTailUngreedy(int index, LoopHeadUngreedy head)
    {
      _index = index;
      _head = head;
      _next = N_END;
    }

    RegexpNode getHead()
    {
      return _head;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      if (_next != null)
	_next = _next.concat(next);
      else
	_next = next;

      if (_next == this)
	throw new IllegalStateException();

      return this;
    }

    //
    // match functions
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      int i = state._loopCount[_index];
      int oldOffset = state._loopOffset[_index];

      if (i < _head._min)
	return offset;

      if (offset == oldOffset)
	return -1;
      
      int tail = _next.match(string, offset, state);
      if (tail >= 0)
	return tail;
      
      if (i + 1 < _head._max) {
	state._loopCount[_index] = i + 1;
	state._loopOffset[_index] = offset;

	tail = _head._node.match(string, offset, state);

	state._loopCount[_index] = i;
	state._loopOffset[_index] = oldOffset;

	return tail;
      }
      else
	return -1;
    }

    public String toString()
    {
      return "LoopTailUngreedy[" + _next + "]";
    }
  }
  
  static class Or extends RegexpNode {
    private RegexpNode _left;
    private Or _right;

    private Or(RegexpNode left, Or right)
    {
      _left = left;
      _right = right;
    }

    static Or create(RegexpNode left, RegexpNode right)
    {
      if (left instanceof Or)
	return ((Or) left).append(right);
      else if (right instanceof Or)
	return new Or(left, (Or) right);
      else
	return new Or(left, new Or(right, null));
    }

    private Or append(RegexpNode right)
    {
      if (_right != null)
	_right = _right.append(right);
      else if (right instanceof Or)
	_right = (Or) right;
      else
	_right = new Or(right, null);

      return this;
    }

    @Override
    int minLength()
    {
      if (_right != null)
	return Math.min(_left.minLength(), _right.minLength());
      else
	return _left.minLength();
    }

    @Override
    int firstChar()
    {
      if (_right == null)
	return _left.firstChar();

      int leftChar = _left.firstChar();
      int rightChar = _right.firstChar();

      if (leftChar == rightChar)
	return leftChar;
      else
	return -1;
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      if (_right == null)
	return _left.firstSet(firstSet);

      firstSet = _left.firstSet(firstSet);
      firstSet = _right.firstSet(firstSet);

      return firstSet;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      for (Or ptr = this; ptr != null; ptr = ptr._right) {
	int value = ptr._left.match(string, offset, state);

	if (value >= 0)
	  return value;
      }

      return -1;
    }
  }
  
  static class PossessiveLoop extends RegexpNode {
    private final RegexpNode _node;
    private RegexpNode _next = N_END;

    private int _min;
    private int _max;

    PossessiveLoop(RegexpNode node, int min, int max)
    {
      _node = node.getHead();
      
      _min = min;
      _max = max;
    }

    @Override
    RegexpNode concat(RegexpNode next)
    {
      if (next == null)
	throw new NullPointerException();
      
      if (_next != null)
	_next = _next.concat(next);
      else
	_next = next;

      return this;
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      if (min == 0 && max == 1) {
	_min = 0;
      
	return this;
      }
      else
	return new LoopHead(parser, this, min, max);
    }

    //
    // match functions
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      RegexpNode next = _next;
      RegexpNode node = _node;
      
      int min = _min;
      int max = _max;

      int i;
      
      int headOffset = offset;
      
      for (i = 0; i < min; i++) {
	offset = node.match(string, offset, state);

	if (offset < 0)
	  return -1;
      }

      for (; i < max; i++) {
	int tail = node.match(string, offset, state);

	if (tail < 0 || tail == offset)
	  return _next.match(string, offset, state);

	offset = tail;
      }
      
      return _next.match(string, offset, state);
    }

    public String toString()
    {
      return "PossessiveLoop[" + _min + ", " + _max + ", " + _node + ", " + _next + "]";
    }
  }

  static final PropC PROP_C = new PropC();
  static final PropNotC PROP_NOT_C = new PropNotC();
  
  static final Prop PROP_Cc = new Prop(Character.CONTROL);
  static final PropNot PROP_NOT_Cc = new PropNot(Character.CONTROL);

  static final Prop PROP_Cf = new Prop(Character.FORMAT);
  static final PropNot PROP_NOT_Cf = new PropNot(Character.FORMAT);

  static final Prop PROP_Cn = new Prop(Character.UNASSIGNED);
  static final PropNot PROP_NOT_Cn = new PropNot(Character.UNASSIGNED);

  static final Prop PROP_Co = new Prop(Character.PRIVATE_USE);
  static final PropNot PROP_NOT_Co = new PropNot(Character.PRIVATE_USE);

  static final Prop PROP_Cs = new Prop(Character.SURROGATE);
  static final PropNot PROP_NOT_Cs = new PropNot(Character.SURROGATE);

  static final PropL PROP_L = new PropL();
  static final PropNotL PROP_NOT_L = new PropNotL();

  static final Prop PROP_Ll = new Prop(Character.LOWERCASE_LETTER);
  static final PropNot PROP_NOT_Ll = new PropNot(Character.LOWERCASE_LETTER);

  static final Prop PROP_Lm = new Prop(Character.MODIFIER_LETTER);
  static final PropNot PROP_NOT_Lm = new PropNot(Character.MODIFIER_LETTER);

  static final Prop PROP_Lo = new Prop(Character.OTHER_LETTER);
  static final PropNot PROP_NOT_Lo = new PropNot(Character.OTHER_LETTER);

  static final Prop PROP_Lt = new Prop(Character.TITLECASE_LETTER);
  static final PropNot PROP_NOT_Lt = new PropNot(Character.TITLECASE_LETTER);

  static final Prop PROP_Lu = new Prop(Character.UPPERCASE_LETTER);
  static final PropNot PROP_NOT_Lu = new PropNot(Character.UPPERCASE_LETTER);

  static final PropM PROP_M = new PropM();
  static final PropNotM PROP_NOT_M = new PropNotM();
  
  static final Prop PROP_Mc = new Prop(Character.COMBINING_SPACING_MARK);
  static final PropNot PROP_NOT_Mc
    = new PropNot(Character.COMBINING_SPACING_MARK);
  
  static final Prop PROP_Me = new Prop(Character.ENCLOSING_MARK);
  static final PropNot PROP_NOT_Me = new PropNot(Character.ENCLOSING_MARK);
  
  static final Prop PROP_Mn = new Prop(Character.NON_SPACING_MARK);
  static final PropNot PROP_NOT_Mn = new PropNot(Character.NON_SPACING_MARK);

  static final PropN PROP_N = new PropN();
  static final PropNotN PROP_NOT_N = new PropNotN();
  
  static final Prop PROP_Nd = new Prop(Character.DECIMAL_DIGIT_NUMBER);
  static final PropNot PROP_NOT_Nd
    = new PropNot(Character.DECIMAL_DIGIT_NUMBER);
  
  static final Prop PROP_Nl = new Prop(Character.LETTER_NUMBER);
  static final PropNot PROP_NOT_Nl = new PropNot(Character.LETTER_NUMBER);
  
  static final Prop PROP_No = new Prop(Character.OTHER_NUMBER);
  static final PropNot PROP_NOT_No = new PropNot(Character.OTHER_NUMBER);

  static final PropP PROP_P = new PropP();
  static final PropNotP PROP_NOT_P = new PropNotP();
  
  static final Prop PROP_Pc = new Prop(Character.CONNECTOR_PUNCTUATION);
  static final PropNot PROP_NOT_Pc
    = new PropNot(Character.CONNECTOR_PUNCTUATION);
  
  static final Prop PROP_Pd = new Prop(Character.DASH_PUNCTUATION);
  static final PropNot PROP_NOT_Pd = new PropNot(Character.DASH_PUNCTUATION);
  
  static final Prop PROP_Pe = new Prop(Character.END_PUNCTUATION);
  static final PropNot PROP_NOT_Pe = new PropNot(Character.END_PUNCTUATION);
  
  static final Prop PROP_Pf = new Prop(Character.FINAL_QUOTE_PUNCTUATION);
  static final PropNot PROP_NOT_Pf
    = new PropNot(Character.FINAL_QUOTE_PUNCTUATION);
  
  static final Prop PROP_Pi = new Prop(Character.INITIAL_QUOTE_PUNCTUATION);
  static final PropNot PROP_NOT_Pi
    = new PropNot(Character.INITIAL_QUOTE_PUNCTUATION);
  
  static final Prop PROP_Po = new Prop(Character.OTHER_PUNCTUATION);
  static final PropNot PROP_NOT_Po = new PropNot(Character.OTHER_PUNCTUATION);
  
  static final Prop PROP_Ps = new Prop(Character.START_PUNCTUATION);
  static final PropNot PROP_NOT_Ps = new PropNot(Character.START_PUNCTUATION);

  static final PropS PROP_S = new PropS();
  static final PropNotS PROP_NOT_S = new PropNotS();
  
  static final Prop PROP_Sc = new Prop(Character.CURRENCY_SYMBOL);
  static final PropNot PROP_NOT_Sc = new PropNot(Character.CURRENCY_SYMBOL);
  
  static final Prop PROP_Sk = new Prop(Character.MODIFIER_SYMBOL);
  static final PropNot PROP_NOT_Sk = new PropNot(Character.MODIFIER_SYMBOL);
  
  static final Prop PROP_Sm = new Prop(Character.MATH_SYMBOL);
  static final PropNot PROP_NOT_Sm = new PropNot(Character.MATH_SYMBOL);
  
  static final Prop PROP_So = new Prop(Character.OTHER_SYMBOL);
  static final PropNot PROP_NOT_So = new PropNot(Character.OTHER_SYMBOL);

  static final PropZ PROP_Z = new PropZ();
  static final PropNotZ PROP_NOT_Z = new PropNotZ();
  
  static final Prop PROP_Zl = new Prop(Character.LINE_SEPARATOR);
  static final PropNot PROP_NOT_Zl = new PropNot(Character.LINE_SEPARATOR);
  
  static final Prop PROP_Zp = new Prop(Character.PARAGRAPH_SEPARATOR);
  static final PropNot PROP_NOT_Zp
    = new PropNot(Character.PARAGRAPH_SEPARATOR);
  
  static final Prop PROP_Zs = new Prop(Character.SPACE_SEPARATOR);
  static final PropNot PROP_NOT_Zs = new PropNot(Character.SPACE_SEPARATOR);
  
  private static class Prop extends AbstractCharNode {
    private final int _category;
    
    Prop(int category)
    {
      _category = category;
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	if (Character.getType(ch) == _category)
	  return offset + 1;
      }

      return -1;
    }
  }
  
  private static class PropNot extends AbstractCharNode {
    private final int _category;
    
    PropNot(int category)
    {
      _category = category;
    }
    
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	if (Character.getType(ch) != _category)
	  return offset + 1;
      }

      return -1;
    }
  }

  static class PropC extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.CONTROL
	    || value == Character.FORMAT
	    || value == Character.UNASSIGNED
	    || value == Character.PRIVATE_USE
	    || value == Character.SURROGATE) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropNotC extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (! (value == Character.CONTROL
	       || value == Character.FORMAT
	       || value == Character.UNASSIGNED
	       || value == Character.PRIVATE_USE
	       || value == Character.SURROGATE)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropL extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.LOWERCASE_LETTER
	    || value == Character.MODIFIER_LETTER
	    || value == Character.OTHER_LETTER
	    || value == Character.TITLECASE_LETTER
	    || value == Character.UPPERCASE_LETTER) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropNotL extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (! (value == Character.LOWERCASE_LETTER
	       || value == Character.MODIFIER_LETTER
	       || value == Character.OTHER_LETTER
	       || value == Character.TITLECASE_LETTER
	       || value == Character.UPPERCASE_LETTER)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropM extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.COMBINING_SPACING_MARK
	    || value == Character.ENCLOSING_MARK
	    || value == Character.NON_SPACING_MARK) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropNotM extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (! (value == Character.COMBINING_SPACING_MARK
	       || value == Character.ENCLOSING_MARK
	       || value == Character.NON_SPACING_MARK)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropN extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.DECIMAL_DIGIT_NUMBER
	    || value == Character.LETTER_NUMBER
	    || value == Character.OTHER_NUMBER) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropNotN extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
        
	if (! (value == Character.DECIMAL_DIGIT_NUMBER
	       || value == Character.LETTER_NUMBER
	       || value == Character.OTHER_NUMBER)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropP extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.CONNECTOR_PUNCTUATION
	    || value == Character.DASH_PUNCTUATION
	    || value == Character.END_PUNCTUATION
	    || value == Character.FINAL_QUOTE_PUNCTUATION
	    || value == Character.INITIAL_QUOTE_PUNCTUATION
	    || value == Character.OTHER_PUNCTUATION
	    || value == Character.START_PUNCTUATION) {
	  return offset + 1;
	}
      }
  

      return -1;
    }
  }

  static class PropNotP extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (! (value == Character.CONNECTOR_PUNCTUATION
	       || value == Character.DASH_PUNCTUATION
	       || value == Character.END_PUNCTUATION
	       || value == Character.FINAL_QUOTE_PUNCTUATION
	       || value == Character.INITIAL_QUOTE_PUNCTUATION
	       || value == Character.OTHER_PUNCTUATION
	       || value == Character.START_PUNCTUATION)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropS extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.CURRENCY_SYMBOL
	    || value == Character.MODIFIER_SYMBOL
	    || value == Character.MATH_SYMBOL
	    || value == Character.OTHER_SYMBOL) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropNotS extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (! (value == Character.CURRENCY_SYMBOL
	       || value == Character.MODIFIER_SYMBOL
	       || value == Character.MATH_SYMBOL
	       || value == Character.OTHER_SYMBOL)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropZ extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (value == Character.LINE_SEPARATOR
	    || value == Character.PARAGRAPH_SEPARATOR
	    || value == Character.SPACE_SEPARATOR) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }

  static class PropNotZ extends AbstractCharNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset < string.length()) {
	char ch = string.charAt(offset);
	
	int value = Character.getType(ch);
        
	if (! (value == Character.LINE_SEPARATOR
	       || value == Character.PARAGRAPH_SEPARATOR
	       || value == Character.SPACE_SEPARATOR)) {
	  return offset + 1;
	}
      }

      return -1;
    }
  }
    
  static class Set extends AbstractCharNode {
    private final boolean []_asciiSet;
    private final IntSet _range;

    Set(boolean []set, IntSet range)
    {
      _asciiSet = set;
      _range = range;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (string.length() <= offset)
	return -1;

      char ch = string.charAt(offset);

      if (ch < 128)
	return _asciiSet[ch] ? offset + 1 : -1;
      else
	return _range.contains(ch) ? offset + 1 : -1;
    }
  }
    
  static class NotSet extends AbstractCharNode {
    private final boolean []_asciiSet;
    private final IntSet _range;

    NotSet(boolean []set, IntSet range)
    {
      _asciiSet = set;
      _range = range;
    }

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (string.length() <= offset)
	return -1;

      char ch = string.charAt(offset);

      if (ch < 128)
	return _asciiSet[ch] ? -1 : offset + 1;
      else
	return _range.contains(ch) ? -1 : offset + 1;
    }
  }
  
  static class StringNode extends RegexpNode {
    private final char []_buffer;
    private final int _length;

    StringNode(CharBuffer value)
    {
      _length = value.length();
      _buffer = new char[_length];

      if (_length == 0)
	throw new IllegalStateException("empty string");
      
      System.arraycopy(value.getBuffer(), 0, _buffer, 0, _buffer.length);
    }

    StringNode(char []buffer, int length)
    {
      _length = length;
      _buffer = buffer;

      if (_length == 0)
	throw new IllegalStateException("empty string");
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      if (_length == 1)
	return new CharLoop(this, min, max);
      else {
	char ch = _buffer[_length - 1];
	
	RegexpNode head = new StringNode(_buffer, _length - 1);

	return head.concat(new CharNode(ch).createLoop(parser, min, max));
      }
    }

    @Override
    RegexpNode createLoopUngreedy(Regcomp parser, int min, int max)
    {
      if (_length == 1)
	return super.createLoopUngreedy(parser, min, max);
      else {
	char ch = _buffer[_length - 1];
	
	RegexpNode head = new StringNode(_buffer, _length - 1);

	return head.concat(new CharNode(ch).createLoopUngreedy(parser, min, max));
      }
    }

    @Override
    RegexpNode createPossessiveLoop(int min, int max)
    {
      if (_length == 1)
	return super.createPossessiveLoop(min, max);
      else {
	char ch = _buffer[_length - 1];
	
	RegexpNode head = new StringNode(_buffer, _length - 1);

	return head.concat(new CharNode(ch).createPossessiveLoop(min, max));
      }
    }

    //
    // optim functions
    //

    @Override
    int minLength()
    {
      return _length;
    }

    @Override
    int firstChar()
    {
      if (_length > 0)
	return _buffer[0];
      else
	return -1;
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      if (firstSet != null && _length > 0 && _buffer[0] < firstSet.length) {
	firstSet[_buffer[0]] = true;

	return firstSet;
      }
      else
	return null;
    }

    @Override
    String prefix()
    {
      return new String(_buffer, 0, _length);
    }

    //
    // match function
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (string.regionMatches(offset, _buffer, 0, _length))
	return offset + _length;
      else
	return -1;
    }

    public String toString()
    {
      return "StringNode[" + new String(_buffer, 0, _length) + "]";
    }
  }
  
  static class StringIgnoreCase extends RegexpNode {
    private final char []_buffer;
    private final int _length;

    StringIgnoreCase(CharBuffer value)
    {
      _length = value.length();
      _buffer = new char[_length];

      if (_length == 0)
	throw new IllegalStateException("empty string");
      
      System.arraycopy(value.getBuffer(), 0, _buffer, 0, _buffer.length);
    }

    StringIgnoreCase(char []buffer, int length)
    {
      _length = length;
      _buffer = buffer;

      if (_length == 0)
	throw new IllegalStateException("empty string");
    }

    @Override
    RegexpNode createLoop(Regcomp parser, int min, int max)
    {
      if (_length == 1)
	return new CharLoop(this, min, max);
      else {
	char ch = _buffer[_length - 1];
	
	RegexpNode head = new StringIgnoreCase(_buffer, _length - 1);
	RegexpNode tail = new StringIgnoreCase(new char[] { ch }, 1);

	return head.concat(tail.createLoop(parser, min, max));
      }
    }

    @Override
    RegexpNode createLoopUngreedy(Regcomp parser, int min, int max)
    {
      if (_length == 1)
	return super.createLoopUngreedy(parser, min, max);
      else {
	char ch = _buffer[_length - 1];
	
	RegexpNode head = new StringIgnoreCase(_buffer, _length - 1);
	RegexpNode tail = new StringIgnoreCase(new char[] { ch }, 1);

	return head.concat(tail.createLoopUngreedy(parser, min, max));
      }
    }

    @Override
    RegexpNode createPossessiveLoop(int min, int max)
    {
      if (_length == 1)
	return super.createPossessiveLoop(min, max);
      else {
	char ch = _buffer[_length - 1];
	
	RegexpNode head = new StringIgnoreCase(_buffer, _length - 1);
	RegexpNode tail = new StringIgnoreCase(new char[] { ch }, 1);

	return head.concat(tail.createPossessiveLoop(min, max));
      }
    }

    //
    // optim functions
    //

    @Override
    int minLength()
    {
      return _length;
    }

    @Override
    int firstChar()
    {
      if (_length > 0
	  && (Character.toLowerCase(_buffer[0])
	      == Character.toUpperCase(_buffer[0])))
	return _buffer[0];
      else
	return -1;
    }

    @Override
    boolean []firstSet(boolean []firstSet)
    {
      if (_length > 0 && firstSet != null) {
	char lower = Character.toLowerCase(_buffer[0]);
	char upper = Character.toUpperCase(_buffer[0]);

	if (lower < firstSet.length && upper < firstSet.length) {
	  firstSet[lower] = true;
	  firstSet[upper] = true;

	  return firstSet;
	}
      }

      return null;
    }

    @Override
    String prefix()
    {
      return new String(_buffer, 0, _length);
    }

    //
    // match function
    //

    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (string.regionMatchesIgnoreCase(offset, _buffer, 0, _length))
	return offset + _length;
      else
	return -1;
    }
  }

  static final StringBegin STRING_BEGIN = new StringBegin();
  static final StringEnd STRING_END = new StringEnd();
  static final StringFirst STRING_FIRST = new StringFirst();
  static final StringNewline STRING_NEWLINE = new StringNewline();

  private static class StringBegin extends RegexpNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == state._start)
	  return offset;
	else
	  return -1;
    }
  }

  private static class StringEnd extends RegexpNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == string.length())
	  return offset;
	else
	  return -1;
    }
  }

  private static class StringFirst extends RegexpNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == state._first)
	  return offset;
	else
	  return -1;
    }
  }

  private static class StringNewline extends RegexpNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if (offset == string.length()
	  || string.charAt(offset) == '\n' && offset + 1 == string.length())
	  return offset;
	else
	  return -1;
    }
  }

  static final Word WORD = new Word();
  static final NotWord NOT_WORD = new NotWord();

  private static class Word extends RegexpNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if ((state._start < offset
	   && RegexpSet.WORD.match(string.charAt(offset - 1)))
	  != (offset < string.length()
	      && RegexpSet.WORD.match(string.charAt(offset))))
	return offset;
      else
	return -1;
    }
  }

  private static class NotWord extends RegexpNode {
    @Override
    int match(StringValue string, int offset, RegexpState state)
    {
      if ((state._start < offset
	   && RegexpSet.WORD.match(string.charAt(offset - 1)))
	  == (offset < string.length()
	      && RegexpSet.WORD.match(string.charAt(offset))))
	return offset;
      else
	return -1;
    }
  }

  static {
    ANY_CHAR = new AsciiNotSet();
  }
}
