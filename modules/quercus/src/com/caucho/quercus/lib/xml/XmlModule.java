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

package com.caucho.quercus.lib.xml;

import com.caucho.quercus.QuercusModuleException;
import com.caucho.quercus.annotation.NotNull;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.Reference;
import com.caucho.quercus.env.BooleanValue;
import com.caucho.quercus.env.Env;
import com.caucho.quercus.env.LongValue;
import com.caucho.quercus.env.StringValue;
import com.caucho.quercus.env.StringValueImpl;
import com.caucho.quercus.env.Value;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.util.L10N;

/**
 * PHP XML
 */
public class XmlModule extends AbstractQuercusModule {
  private static final L10N L = new L10N(XmlModule.class);


  public static final int XML_OPTION_CASE_FOLDING = 0x0;
  public static final int XML_OPTION_SKIP_TAGSTART = 0x1;
  public static final int XML_OPTION_SKIP_WHITE = 0x2;
  public static final int XML_OPTION_TARGET_ENCODING = 0x3;

  public static final int XML_ERROR_NONE = 0;
  public static final int XML_ERROR_NO_MEMORY = 1;
  public static final int XML_ERROR_SYNTAX = 2;
  public static final int XML_ERROR_NO_ELEMENTS = 3;
  public static final int XML_ERROR_INVALID_TOKEN = 4;
  public static final int XML_ERROR_UNCLOSED_TOKEN = 5;
  public static final int XML_ERROR_PARTIAL_CHAR = 6;
  public static final int XML_ERROR_TAG_MISMATCH = 7;
  public static final int XML_ERROR_DUPLICATE_ATTRIBUTE = 8;
  public static final int XML_ERROR_JUNK_AFTER_DOC_ELEMENT = 9;
  public static final int XML_ERROR_PARAM_ENTITY_REF = 10;
  public static final int XML_ERROR_UNDEFINED_ENTITY = 11;
  public static final int XML_ERROR_RECURSIVE_ENTITY_REF = 12;
  public static final int XML_ERROR_ASYNC_ENTITY = 13;
  public static final int XML_ERROR_BAD_CHAR_REF = 14;
  public static final int XML_ERROR_BINARY_ENTITY_REF = 15;
  public static final int XML_ERROR_ATTRIBUTE_EXTERNAL_ENTITY_REF = 16;
  public static final int XML_ERROR_MISPLACED_XML_PI = 17;
  public static final int XML_ERROR_UNKNOWN_ENCODING = 18;
  public static final int XML_ERROR_INCORRECT_ENCODING = 19;
  public static final int XML_ERROR_UNCLOSED_CDATA_SECTION = 20;
  public static final int XML_ERROR_EXTERNAL_ENTITY_HANDLING = 21;
  public static final int XML_ERROR_NOT_STANDALONE = 22;
  public static final int XML_ERROR_UNEXPECTED_STATE = 23;
  public static final int XML_ERROR_ENTITY_DECLARED_IN_PE = 24;
  public static final int XML_ERROR_FEATURE_REQUIRES_XML_DTD = 25;
  public static final int XML_ERROR_CANT_CHANGE_FEATURE_ONCE_PARSING = 26;
  public static final int XML_ERROR_UNBOUND_PREFIX = 27;
  public static final int XML_ERROR_UNDECLARING_PREFIX = 28;
  public static final int XML_ERROR_INCOMPLETE_PE = 29;
  public static final int XML_ERROR_XML_DECL = 30;
  public static final int XML_ERROR_TEXT_DECL = 31;
  public static final int XML_ERROR_PUBLICID = 32;
  public static final int XML_ERROR_SUSPENDED = 33;
  public static final int XML_ERROR_NOT_SUSPENDED = 34;
  public static final int XML_ERROR_ABORTED = 35;
  public static final int XML_ERROR_FINISHED = 36;
  public static final int XML_ERROR_SUSPEND_PE = 37;

  public String []getLoadedExtensions()
  {
    return new String[] { "xml" };
  }
  
  /**
   * Converts from iso-8859-1 to utf8
   */
  public static Value utf8_encode(String str)
  {
    // XXX: need to make a marker for the string

    return new StringValueImpl(str);
  }

  /**
   * Converts from utf8 to iso-8859-1
   */
  public static Value utf8_decode(String str)
  {
    // XXX: need to make a marker for the string

    return new StringValueImpl(str);
  }

  /**
   * Returns the error code for xml parser
   */
  public Value xml_get_error_code(Xml parser)
  {
    if (parser == null)
      return BooleanValue.FALSE;

    return LongValue.create(parser.getErrorCode());
  }

  /**
   * Returns the error string for xml parser
   */
  public Value xml_error_string(int code)
  {
    switch (code) {
    case XML_ERROR_NONE:
      return StringValue.create("No error");
      
    case XML_ERROR_SYNTAX:
      return StringValue.create("syntax error");

    default:
      return BooleanValue.FALSE;
    }
  }

  /**
   * @see boolean Xml.xml_parse
   *
   * @param parser
   * @param data
   * @param isFinal
   * @return false if parser == null
   * @throws Exception
   */
  public boolean xml_parse(Env env,
			   @NotNull Xml parser,
                           String data,
                           @Optional("true") boolean isFinal)
  {
    if (parser == null)
      return false;

    try {
      return parser.xml_parse(env, data, isFinal);
    } catch (Exception e) {
      throw QuercusModuleException.create(e);
    }
  }

  /**
   * returns a new Xml Parser
   */
  public Xml xml_parser_create(Env env,
                                    @Optional String outputEncoding)
  {
    return new Xml(env,outputEncoding,null);
  }

  /**
   * XXX: Should we return warning if separator is
   * anything but ":"???
   *
   * @param env
   * @param outputEncoding
   * @param separator
   * @return namespace aware Xml Parser
   */
  public Xml xml_parser_create_ns(Env env,
                                       @Optional String outputEncoding,
                                       @Optional("':'") String separator)
  {
    return new Xml(env,outputEncoding,separator);
  }

  /**
   *
   * @see boolean Xml.xml_parser_set_option
   *
   * @param parser
   * @param option
   * @param value
   * @return false if parser == null
   */
  public boolean xml_parser_set_option(@NotNull Xml parser,
                                       @NotNull int option,
                                       @NotNull Value value)
  {
    if (parser == null)
      return false;

    return parser.xml_parser_set_option(option, value);
  }

  /**
   * @see boolean Xml.xml_parser_get_option
   *
   * @param parser
   * @param option
   * @return false if parser == null
   */
  public Value xml_parser_get_option(@NotNull Xml parser,
                                       @NotNull int option)
  {
    if (parser == null)
      return BooleanValue.FALSE;

    return parser.xml_parser_get_option(option);
  }

  /**
   * @see boolean Xml.xml_set_element_handler
   *
   * @param parser
   * @param startElementHandler
   * @param endElementHandler
   * @return false if parser == null
   */
  public boolean xml_set_element_handler(@NotNull Xml parser,
                                         @NotNull Value startElementHandler,
                                         @NotNull Value endElementHandler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_element_handler(startElementHandler, endElementHandler);
  }

  /**
   * @see boolean Xml.xml_set_character_data_handler
   *
   * @param parser
   * @param handler
   * @return false if parser == null
   */
  public boolean xml_set_character_data_handler(@NotNull Xml parser,
                                                @NotNull Value handler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_character_data_handler(handler);
  }


  /**
   * @see boolean Xml.xml_set_start_namespace_decl_handler
   *
   * @param parser
   * @param startNamespaceDeclHandler
   * @return false if parser == null
   */
  public boolean xml_set_start_namespace_decl_handler(@NotNull Xml parser,
                                                      @NotNull Value startNamespaceDeclHandler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_start_namespace_decl_handler(startNamespaceDeclHandler);
  }

  /**
   *
   * @param parser
   * @param obj
   * @return false if parser == null
   */
  public boolean xml_set_object(@NotNull Xml parser,
                                @NotNull Value obj)
  {
    if (parser == null)
      return false;

    return parser.xml_set_object(obj);
  }

  /**
   *
   * @param parser
   * @param handler
   * @return false if parser == null
   */
  public boolean xml_set_processing_instruction_handler(@NotNull Xml parser,
                                                        @NotNull Value handler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_processing_instruction_handler(handler);
  }

  /**
   *
   * @param parser
   * @param handler
   * @return false if parser == null
   */
  public boolean xml_set_default_handler(@NotNull Xml parser,
                                         @NotNull Value handler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_default_handler(handler);
  }

  /**
   * @see boolean Xml.xml_set_notation_decl_handler
   *
   * @param parser
   * @param handler
   * @return false is parser == null
   */
  public boolean xml_set_notation_decl_handler(@NotNull Xml parser,
                                               @NotNull Value handler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_notation_decl_handler(handler);
  }

  /**
   *
   * @param parser
   * @param handler
   * @return false if parser == null
   */
  public boolean xml_set_end_namespace_decl_handler(@NotNull Xml parser,
                                                    @NotNull Value handler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_end_namespace_decl_handler(handler);
  }

  /**
   *
   * @param parser
   * @param data
   * @param valueArray
   * @param indexArray
   * @return false if parser == null
   * @throws Exception
   */
  public int xml_parse_into_struct(@NotNull Xml parser,
                                   @NotNull String data,
                                   @Reference Value valueArray,
                                   @Optional @Reference Value indexArray)
  {
    try {
      if (parser == null)
	return 0;

      return parser.xml_parse_into_struct(data, valueArray, indexArray);
    } catch (Exception e) {
      throw QuercusModuleException.create(e);
    }
  }

  /**
   * stub function.  parser_free taken care of by garbage collection
   *
   * @param parser
   * @return false if parser == null, otherwise true
   */
  public boolean xml_parser_free(@NotNull Xml parser)
  {
    if (parser == null)
      return false;
    else
      return true;
  }

  /**
   * @see boolean Xml.xml_set_unparsed_entity_decl_handler
   *
   * @param parser
   * @param handler
   * @return false if parser == null, otherwise true
   */
  public boolean xml_set_unparsed_entity_decl_handler(@NotNull Xml parser,
                                                      @NotNull Value handler)
  {
    if (parser == null)
      return false;

    return parser.xml_set_unparsed_entity_decl_handler(handler);
  }

  // @todo xml_error_string
  // @todo xml_get_current_byte_index
  // @todo xml_get_current_column_number
  // @todo xml_get_current_line_number
  // @todo xml_get_error_code
  // @todo xml_set_external_entity_ref_handler
}

