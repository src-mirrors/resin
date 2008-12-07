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

package com.caucho.quercus.env;

import com.caucho.quercus.*;
import com.caucho.quercus.expr.Expr;
import com.caucho.quercus.lib.ErrorModule;
import com.caucho.quercus.lib.VariableModule;
import com.caucho.quercus.lib.OptionsModule;
import com.caucho.quercus.lib.file.FileModule;
import com.caucho.quercus.lib.file.PhpStderr;
import com.caucho.quercus.lib.file.PhpStdin;
import com.caucho.quercus.lib.file.PhpStdout;
import com.caucho.quercus.lib.string.StringModule;
import com.caucho.quercus.lib.string.StringUtility;
import com.caucho.quercus.module.ModuleContext;
import com.caucho.quercus.module.ModuleStartupListener;
import com.caucho.quercus.module.IniDefinition;
import com.caucho.quercus.page.QuercusPage;
import com.caucho.quercus.program.*;
import com.caucho.quercus.resources.StreamContextResource;
import com.caucho.util.*;
import com.caucho.vfs.ByteToChar;
import com.caucho.vfs.Encoding;
import com.caucho.vfs.NullPath;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.WriteStream;
import com.caucho.vfs.TempBuffer;
import com.caucho.vfs.i18n.EncodingReader;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.servlet.ServletContext;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.IdentityHashMap;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents the Quercus environment.
 */
public class Env {
  private static final L10N L = new L10N(Env.class);
  private static final Logger log
    = Logger.getLogger(Env.class.getName());

  public static final int B_ERROR = 0;
  public static final int B_WARNING = 1;
  public static final int B_PARSE = 2;
  public static final int B_NOTICE = 3;
  public static final int B_CORE_ERROR = 4;
  public static final int B_CORE_WARNING = 5;
  public static final int B_COMPILE_ERROR = 6;
  public static final int B_COMPILE_WARNING = 7;
  public static final int B_USER_ERROR = 8;
  public static final int B_USER_WARNING = 9;
  public static final int B_USER_NOTICE = 10;
  public static final int B_STRICT = 11;
  public static final int B_RECOVERABLE_ERROR = 12;
  
  public static final int B_LAST = B_RECOVERABLE_ERROR;

  public static final int E_ERROR = 1 << B_ERROR;
  public static final int E_WARNING = 1 << B_WARNING;
  public static final int E_PARSE = 1 << B_PARSE;
  public static final int E_NOTICE = 1 << B_NOTICE;
  public static final int E_CORE_ERROR = 1 << B_CORE_ERROR;
  public static final int E_CORE_WARNING = 1 << B_CORE_WARNING;
  public static final int E_COMPILE_ERROR = 1 << B_COMPILE_ERROR;
  public static final int E_COMPILE_WARNING = 1 << B_COMPILE_WARNING;
  public static final int E_USER_ERROR = 1 << B_USER_ERROR;
  public static final int E_USER_WARNING = 1 << B_USER_WARNING;
  public static final int E_USER_NOTICE = 1 << B_USER_NOTICE;
  public static final int E_ALL = 6143; //(4096 + 2048 - 1)
  public static final int E_STRICT = 1 << B_STRICT;
  public static final int E_RECOVERABLE_ERROR = 1 << B_RECOVERABLE_ERROR;

  public static final int E_DEFAULT = E_ALL & ~E_NOTICE;

  private static final int _SERVER = 1;
  private static final int _GET = 2;
  private static final int _POST = 3;
  private static final int _COOKIE = 4;
  private static final int _GLOBAL = 5;
  private static final int _REQUEST = 6;
  private static final int _SESSION = 7;
  private static final int HTTP_GET_VARS = 8;
  private static final int HTTP_POST_VARS = 9;
  private static final int HTTP_COOKIE_VARS = 10;
  private static final int PHP_SELF = 11;
  private static final int _FILES = 12;
  private static final int HTTP_POST_FILES = 13;
  private static final int _ENV = 14;
  private static final int HTTP_SERVER_VARS = 15;
  private static final int HTTP_RAW_POST_DATA = 16;

  private static final IntMap SPECIAL_VARS = new IntMap();

  private static final StringValue PHP_SELF_STRING
    = new StringBuilderValue("PHP_SELF");

  private static final StringValue UTF8_STRING
    = new StringBuilderValue("utf-8");

  public static final Value []EMPTY_VALUE = new Value[0];

  private static final
    LruCache<ClassKey,SoftReference<QuercusClass>> _classCache
    = new LruCache<ClassKey,SoftReference<QuercusClass>>(4096);

  /*
  private static final
    LruCache<IncludeKey,SoftReference<IncludeCache>> _includeCache
    = new LruCache<IncludeKey,SoftReference<IncludeCache>>(4096);
  */

  private static ThreadLocal<Env> _threadEnv = new ThreadLocal<Env>();

  private static final FreeList<AbstractFunction[]> _freeFunList
    = new FreeList<AbstractFunction[]>(256);

  private static final FreeList<ClassDef[]> _freeClassDefList
    = new FreeList<ClassDef[]>(256);

  private static final FreeList<QuercusClass[]> _freeClassList
    = new FreeList<QuercusClass[]>(256);

  protected final Quercus _quercus;
  private final boolean _isUnicodeSemantics; 
  private QuercusPage _page;

  private Value _this = NullThisValue.NULL;

  private ArrayList<EnvCleanup> _cleanupList
    = new ArrayList<EnvCleanup>();

  private ArrayList<ObjectExtValue> _objCleanupList
    = new ArrayList<ObjectExtValue>();
  
  private ArrayList<Shutdown> _shutdownList
    = new ArrayList<Shutdown>();

  private final HashMap<String, Var> _globalMap
    = new HashMap<String, Var>(1024);
  
  private HashMap<String, Var> _staticMap
    = new HashMap<String, Var>();
  
  private HashMap<String, Var> _map = _globalMap;

  private HashMap<String, Value> _constMap
    = new HashMap<String, Value>(1024);

  private HashMap<String, Value> _lowerConstMap
    = new HashMap<String, Value>(1024);

  private HashMap<String, QuercusClass> _classMap
    = new HashMap<String, QuercusClass>();

  private HashMap<String, QuercusClass> _lowerClassMap
    = new HashMap<String, QuercusClass>();

  private HashSet<String> _initializedClassSet
    = new HashSet<String>();

  // Function map
  public AbstractFunction []_fun;
  
  // anonymous functions created by create_function()
  public HashMap<String, AbstractFunction> _anonymousFunMap;
  
  // Class map
  public ClassDef []_classDef;
  public QuercusClass []_qClass;

  private IdentityHashMap<String, Value> _iniMap;

  // specialMap is used for implicit resources like the mysql link
  private HashMap<String, Object> _specialMap
    = new HashMap<String, Object>();

  // include_path ini
  private int _iniCount = 1;

  private String _defaultIncludePath;
  private String _includePath;
  private int _includePathIniCount;
  private ArrayList<String> _includePathList;
  private HashMap<Path,ArrayList<Path>> _includePathMap;

  // php/
  // XXX: may require a LinkedHashMap for ordering?
  private HashMap<Path,QuercusPage> _includeMap
    = new HashMap<Path,QuercusPage>();
  
  private boolean _isAllowUrlInclude;
  private boolean _isAllowUrlFopen;
  
  private HashMap<StringValue,Path> _lookupCache
    = new HashMap<StringValue,Path>();

  private HashMap<ConnectionEntry,ConnectionEntry> _connMap
    = new HashMap<ConnectionEntry,ConnectionEntry>();

  private AbstractFunction _autoload;
  private HashSet<String> _autoloadClasses
    = new HashSet<String>();
  
  private ArrayList<Callback> _autoloadList;

  private long _startTime;
  private long _timeLimit = 600000L;

  private Expr [] _callStack = new Expr[256];
  private Value [] _callThisStack = new Value[256];
  private Value [][] _callArgStack = new Value[256][];
  private int _callStackTop;
  
  private QuercusClass _callingClass;

  private Value [] _functionArgs;
  
  private LinkedList<FieldGetEntry> _fieldGetList
    = new LinkedList<FieldGetEntry>();

  private Path _selfPath;
  private Path _selfDirectory;
  private Path _pwd;
  private Path _uploadPath;
  private Path _tmpPath;
  private ArrayList<Path> _removePaths;

  private final boolean _isStrict;

  private HttpServletRequest _request;
  private HttpServletResponse _response;

  private ArrayValue _postArray;
  private StringValue _inputData;
  
  private ArrayValue _files;
  private SessionArrayValue _session;
  private HttpSession _javaSession;

  private ScriptContext _scriptContext;

  private WriteStream _originalOut;
  private OutputBuffer _outputBuffer;

  private WriteStream _out;

  private LocaleInfo _locale;

  private Callback [] _prevErrorHandlers = new Callback[B_LAST + 1];
  private Callback [] _errorHandlers = new Callback[B_LAST + 1];

  private Callback _prevExceptionHandler;
  private Callback _exceptionHandler;

  private SessionCallback _sessionCallback;

  private StreamContextResource _defaultStreamContext;

  // XXX: need to look this up from the module itself
  private int _errorMask = E_DEFAULT;

  private int _objectId = 0;

  private Logger _logger;

  // hold special Quercus php import statements
  private ImportMap _importMap;

  private TimeZone _defaultTimeZone;

  private Object _gzStream;

  private Env _oldThreadEnv;
  
  public Env(Quercus quercus,
             QuercusPage page,
             WriteStream out,
             HttpServletRequest request,
             HttpServletResponse response)
  {
    _quercus = quercus;

    _isStrict = quercus.isStrict();
    _isUnicodeSemantics = quercus.isUnicodeSemantics();
    
    _isAllowUrlInclude = quercus.isAllowUrlInclude();
    _isAllowUrlFopen = quercus.isAllowUrlFopen();

    _page = page;

    // XXX: grab initial from page
    // _defState = new DefinitionState(quercus);

    AbstractFunction []defFuns = quercus.getFunctionMap();
    _fun = _freeFunList.allocate();
    if (_fun == null || _fun.length != defFuns.length)
      _fun = new AbstractFunction[defFuns.length];
    System.arraycopy(defFuns, 0, _fun, 0, defFuns.length);

    ClassDef []defClasses = quercus.getClassDefMap();

    _classDef = _freeClassDefList.allocate();
    if (_classDef == null || _classDef.length != defClasses.length)
      _classDef = new ClassDef[defClasses.length];
    else {
      // list should have been zeroed on call to free
    }

    _qClass = _freeClassList.allocate();
    if (_qClass == null || _qClass.length != defClasses.length)
      _qClass = new QuercusClass[defClasses.length];
    else {
      // list should have been zeroed on call to free
    }
    
    _originalOut = out;
    _out = out;

    _request = request;
    _response = response;

    if (_page != null) {
      _page.init(this);

      _page.importDefinitions(this);
    }

    setPwd(_quercus.getPwd());

    if (_page != null) {
      setSelfPath(_page.getSelfPath(null));

      // php/0b32
      _includeMap.put(_selfPath, _page);
    }
    
    if (_request != null && _request.getMethod().equals("POST")) {
      _postArray = new ArrayValueImpl();
      _files = new ArrayValueImpl();
      Post.fillPost(this,
                    _postArray,
                    _files,
                    _request,
                    getIniBoolean("magic_quotes_gpc"));
    } else if (_request != null && ! _request.getMethod().equals("GET")) {
      InputStream is = null;
      
      try {
        is = _request.getInputStream();
      } catch (IOException e) {
        warning(e);
      }
      
      StringValue bb = createBinaryBuilder();
      bb.appendReadAll(is, Integer.MAX_VALUE);
      
      setInputData(bb);
    }

    // Define the constant string PHP_VERSION

    addConstant("PHP_VERSION", OptionsModule.phpversion(this, null), true);
    
    // STDIN, STDOUT, STDERR
    // php://stdin, php://stdout, php://stderr
    if (response == null) {
      addConstant("STDOUT", wrapJava(new PhpStdout()), false);
      addConstant("STDERR", wrapJava(new PhpStderr()), false);
      addConstant("STDIN", wrapJava(new PhpStdin(this)), false);
    }
  }

  public Env(Quercus quercus)
  {
    this(quercus, null, null, null, null);
  }

  public static Env getInstance()
  {
    return _threadEnv.get();
  }

  //
  // i18n
  //

  /**
   * Returns true if unicode.semantics is on.
   */
  public boolean isUnicodeSemantics()
  {
    return _isUnicodeSemantics;
  }

  /**
   * Returns the encoding used for scripts.
   */
  public String getScriptEncoding()
  {
    StringValue encoding = getIni("unicode.script_encoding");

    if (encoding.length() == 0) {
      encoding = getIni("unicode.fallback_encoding");

      if (encoding.length() == 0)
        return getQuercus().getScriptEncoding();
    }

    return encoding.toString();
  }

  /**
   * Returns the encoding used for runtime conversions, e.g. files
   * XXX: ISO-8859-1 when unicode.semantics is OFF
   */
  public String getRuntimeEncoding()
  {
    if (! _isUnicodeSemantics)
      return "iso-8859-1";
    
    StringValue encoding = getIni("unicode.runtime_encoding");

    if (encoding.length() == 0) {
      encoding = getIni("unicode.fallback_encoding");

      if (encoding.length() == 0)
        encoding = UTF8_STRING;
    }

    return encoding.toString();
  }

  /**
   * Sets the encoding used for runtime conversions.
   */
  public Value setRuntimeEncoding(String encoding)
  {
    return setIni("unicode.runtime_encoding", encoding);
  }

  /**
   * Returns the encoding used for runtime conversions, e.g. files
   */
  public EncodingReader getRuntimeEncodingFactory()
    throws IOException
  {
    return Encoding.getReadFactory(getRuntimeEncoding());
  }

  /**
   * Returns the encoding used for input, i.e. post,
   * null if unicode.semantics is off.
   */
  public String getHttpInputEncoding()
  {
    if (! _isUnicodeSemantics)
      return null;
    
    StringValue encoding = getIni("unicode.http_input_encoding");

    if (encoding.length() == 0) {
      encoding = getIni("unicode.fallback_encoding");

      if (encoding.length() == 0)
        encoding = UTF8_STRING;
    }

    return encoding.toString();
  }

  /**
   * Returns the encoding used for output, null if unicode.semantics is off.
   */
  public String getOutputEncoding()
  {
    if (! _isUnicodeSemantics)
      return null;

    String encoding = Quercus.INI_UNICODE_OUTPUT_ENCODING.getAsString(this);

    if (encoding == null)
      encoding = Quercus.INI_UNICODE_FALLBACK_ENCODING.getAsString(this);

    if (encoding == null)
      encoding = "utf-8";

    return encoding;
  }

  /**
   * Creates a binary builder.
   */
  public StringValue createBinaryBuilder()
  {
    if (_isUnicodeSemantics)
      return new BinaryBuilderValue();
    else
      return new StringBuilderValue();
  }

  /**
   * Creates a binary builder for large things like files.
   */
  public StringValue createLargeBinaryBuilder()
  {
    if (_isUnicodeSemantics)
      return new BinaryBuilderValue();
    else
      return new LargeStringBuilderValue();
  }

  /**
   * Creates a binary builder.
   */
  public StringValue createBinaryBuilder(int length)
  {
    if (_isUnicodeSemantics)
      return new BinaryBuilderValue(length);
    else
      return new StringBuilderValue(length);
  }

  /**
   * Creates a binary builder.
   */
  public StringValue createBinaryBuilder(byte []buffer, int offset, int length)
  {
    if (_isUnicodeSemantics)
      return new BinaryBuilderValue(buffer, offset, length);
    else
      return new StringBuilderValue(buffer, offset, length);
  }

  /**
   * Creates a binary builder.
   */
  public StringValue createBinaryBuilder(byte []buffer)
  {
    if (_isUnicodeSemantics)
      return new BinaryBuilderValue(buffer, 0, buffer.length);
    else
      return new StringBuilderValue(buffer, 0, buffer.length);
  }

  /**
   * Creates a unicode builder.
   */
  public StringValue createUnicodeBuilder()
  {
    if (_isUnicodeSemantics)
      return new UnicodeBuilderValue();
    else
      return new StringBuilderValue();
  }

  public TimeZone getDefaultTimeZone()
  {
    return _defaultTimeZone;
  }
  
  public void setDefaultTimeZone(String id)
  {
    _defaultTimeZone = TimeZone.getTimeZone(id);
  }
  
  public void setDefaultTimeZone(TimeZone zone)
  {
    _defaultTimeZone = zone;
  }

  /*
   * Returns the ServletContext.
   */
  public ServletContext getServletContext()
  {
    return _quercus.getServletContext();
  }
  
  /*
   * Sets the ScriptContext.
   */
  public void setScriptContext(ScriptContext context)
  {
    _scriptContext = context;
  }
  
  /*
   * Returns the input (POST, PUT) data.
   */
  public StringValue getInputData()
  {
    return _inputData;
  }
  
  /*
   * Sets the post data.
   */
  public void setInputData(StringValue data)
  {
    _inputData = data;
  }

  /**
   * Returns true for strict mode.
   */
  public final boolean isStrict()
  {
    return _isStrict;
  }
  
  /*
   * Returns true if allowed to include urls.
   */
  public boolean isAllowUrlInclude()
  {
    return _isAllowUrlInclude;
  }
  
  /*
   * Returns true if allowed to fopen urls.
   */
  public boolean isAllowUrlFopen()
  {
    return _isAllowUrlFopen;
  }

  /**
   * Returns the connection status
   */
  public int getConnectionStatus()
  {
    // always returns a valid value for now
    return 0;
  }
  
  public void start()
  {
    _oldThreadEnv = _threadEnv.get();
    
    _startTime = Alarm.getCurrentTime();
    _timeLimit = getIniLong("max_execution_time") * 1000;
    
    _threadEnv.set(this);
    
    // quercus/1b06
    String encoding = getOutputEncoding();
    
    String type = getIniString("default_mimetype");
    
    if ("".equals(type) || _response == null) {
    }
    else if (encoding != null)
      _response.setContentType(type + "; charset=" + encoding);
    else
      _response.setContentType(type);

    if (_out != null && encoding != null) {
      try {
        _out.setEncoding(encoding);
      } catch (Exception e) {
	log.log(Level.WARNING, e.toString(), e);
      }
    }
	
    HashSet<ModuleStartupListener> listeners
      = _quercus.getModuleStartupListeners(); 

    for (ModuleStartupListener listener : listeners)
      listener.startup(this);
  }

  /**
   * add resource to the list of refrences that are
   * cleaned up when finished with this environment.
   */
  public void addCleanup(EnvCleanup envCleanup)
  {
    _cleanupList.add(envCleanup);
  }

  /**
   * add an object with a destructor to the list of references that are
   * cleaned up when finished with this environment.
   */
  public void addObjectCleanup(ObjectExtValue objCleanup)
  {
    _objCleanupList.add(objCleanup);
  }

  /**
   * remove resource from the list of references that are
   * cleaned up when finished with this environment.
   *
   * @param resource
   */
  public void removeCleanup(EnvCleanup envCleanup)
  {
    for (int i = _cleanupList.size() - 1; i >= 0; i--) {
      EnvCleanup res = _cleanupList.get(i);

      if (envCleanup.equals(res)) {
        _cleanupList.remove(i);
        break;
      }
    }
  }

  /**
   * Returns the owning PHP engine.
   */
  public Quercus getQuercus()
  {
    return _quercus;
  }

  /**
   * Returns the owning PHP engine.
   */
  public ModuleContext getModuleContext()
  {
    return _quercus.getModuleContext();
  }

  /**
   * Returns the configured database.
   */
  public DataSource getDatabase()
  {
    return _quercus.getDatabase();
  }

  protected final DataSource findDatabase(String driver, String url)
    throws Exception
  {
    return _quercus.findDatabase(driver, url);
  }
  
  /**
   * Returns a connection to the given database. If there is
   * already a connection to this specific database, then
   * return the connection from the pool. Otherwise, create
   * a new connection and add it to the pool.
   */
  public ConnectionEntry getConnection(String driver, String url,
                                       String userName, String password,
                                       boolean isReuse)
    throws Exception
  {
    // XXX: connections might not be reusable (see gallery2), because
    // of case with two reuses and one closes because of CREATE or
    // catalog
    isReuse = false;
    
    DataSource database = _quercus.getDatabase();

    if (database != null) {
      userName = null;
      password = null;
    }
    else {
      database = findDatabase(driver, url);

      if (database == null)
        return null;
    }
    
    ConnectionEntry entry = new ConnectionEntry(this);
    entry.init(database, userName, password);

    ConnectionEntry oldEntry = null;

    if (isReuse)
      oldEntry = _connMap.get(entry);

    if (oldEntry != null && oldEntry.isReusable())
      return oldEntry;

    entry.connect(isReuse);

    if (isReuse)
      _connMap.put(entry, entry);

    return entry;
  }

  /**
   * Returns the configured database.
   */
  public DataSource getDataSource(String driver, String url)
    throws Exception
  {
    DataSource database = _quercus.getDatabase();

    if (database != null)
      return database;
    else
      return findDatabase(driver, url);
  }

  /**
   * Sets the time limit.
   */
  public void setTimeLimit(long ms)
  {
    if (ms <= 0)
      ms = Long.MAX_VALUE / 2;

    _timeLimit = ms;
  }

  /**
   * Checks for the program timeout.
   */
  public final void checkTimeout()
  {
    long now = Alarm.getCurrentTime();

    if (_timeLimit > 0 && _startTime + _timeLimit < now)
      throw new QuercusRuntimeException(L.l("script timed out"));
  }

  public void resetTimeout()
  {
    _startTime = Alarm.getCurrentTime();
  }
  
  /**
   * Returns the writer.
   */
  public WriteStream getOut()
  {
    return _out;
  }

  /**
   * Returns the writer.
   */
  public WriteStream getOriginalOut()
  {
    return _originalOut;
  }

  /**
   * Flushes the output buffer.
   */
  public final void flush()
  {
    try {
      getOut().flush();
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a string
   */
  public final void print(String v)
  {
    try {
      getOut().print(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a character buffer.
   */
  public final void print(char []buffer, int offset, int length)
  {
    try {
      getOut().print(buffer, offset, length);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a char
   */
  public final void print(char v)
  {
    try {
      getOut().print(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a long
   */
  public final void print(long v)
  {
    try {
      getOut().print(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a double
   */
  public final void print(double v)
  {
    try {
      long longV = (long) v;
      
      if (v == longV)
	getOut().print(longV);
      else
	getOut().print(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints an object
   */
  public final void print(Object v)
  {
    try {
      getOut().print(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a value
   */
  public final void print(Value v)
  {
    v.print(this);
  }

  /**
   * Prints a string
   */
  public final void println()
  {
    try {
      getOut().println();
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a string
   */
  public final void println(String v)
  {
    try {
      getOut().println(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a string
   */
  public final void println(Value v)
  {
    try {
      v.print(this);
      getOut().println();
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints and object.
   */
  public final void println(Object v)
  {
    try {
      getOut().println(v);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Prints a byte buffer.
   */
  public final void write(byte []buffer, int offset, int length)
  {
    try {
      getOut().write(buffer, offset, length);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Returns the current output buffer.
   */
  public OutputBuffer getOutputBuffer()
  {
    return _outputBuffer;
  }

  /**
   * Returns the writer.
   */
  public void pushOutputBuffer(Callback callback, int chunkSize, boolean erase)
  {
    if (_outputBuffer == null) {
      _outputBuffer = 
        new OutputBuffer(_outputBuffer, this, callback, chunkSize, erase);
    } 
    else
      _outputBuffer = 
        new OutputBuffer(_outputBuffer, this, callback, chunkSize, erase);

    _out = _outputBuffer.getOut();
  }

  /**
   * Pops the output buffer
   */
  public boolean popOutputBuffer()
  {
    OutputBuffer outputBuffer = _outputBuffer;

    if (outputBuffer == null)
      return false;

    outputBuffer.close();

    _outputBuffer = outputBuffer.getNext();

    if (_outputBuffer != null)
      _out = _outputBuffer.getOut();
    else {
      _out = _originalOut;
    }

    return true;
  }

  /**
   * Returns the current directory.
   */
  public Path getPwd()
  {
    return _pwd;
  }

  /**
   * Returns the current directory.
   */
  public Path getWorkDir()
  {
    return _quercus.getWorkDir();
  }

  /**
   * Sets the current directory.
   */
  public void setPwd(Path path)
  {
    _pwd = path;
    _lookupCache.clear();
  }

  /**
   * Returns the initial directory.
   */
  public Path getSelfPath()
  {
    return _selfPath;
  }

  /**
   * Returns the initial directory.
   */
  public Path getSelfDirectory()
  {
    return _selfDirectory;
  }

  /**
   * Sets the initial directory.
   */
  public void setSelfPath(Path path)
  {
    _selfPath = path;
    _selfDirectory = _selfPath.getParent();
  }

  /**
   * Returns the upload directory.
   */
  public Path getUploadDirectory()
  {
    if (_uploadPath == null) {
      String realPath = getIniString("upload_tmp_dir");

      if (realPath == null)
        realPath = getRequest().getRealPath("/WEB-INF/upload");

      _uploadPath = _quercus.getPwd().lookup(realPath);

      try {
        if (! _uploadPath.isDirectory())
          _uploadPath.mkdirs();
      }
      catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);
      }

      _uploadPath = _uploadPath.createRoot();
    }

    return _uploadPath;
  }
  
  /*
   * Returns the temp directory (used by tmpfile()).
   */
  public Path getTempDirectory()
  {
    String realPath;
    
    if (_tmpPath == null) {
      if (getRequest() != null)
        realPath = getRequest().getRealPath("/WEB-INF/tmp");
      else
        realPath = "file:/tmp";
      
      _tmpPath = getPwd().lookup(realPath);
      
      try {
        if (! _tmpPath.isDirectory())
          _tmpPath.mkdirs();
      }
      catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }
    
    return _tmpPath;
  }

  /**
   * Adds an auto-remove path.
   */
  public void addRemovePath(Path path)
  {
    if (_removePaths == null)
      _removePaths = new ArrayList<Path>();

    _removePaths.add(path);
  }

  /**
   * Returns the request.
   */
  public HttpServletRequest getRequest()
  {
    return _request;
  }

  /**
   * Returns the most recently modified time of all of the {@link Path}'s that
   * have been used for this Env, or 0 if that cannot be determined.
   */
  /*
  public long getLastModified()
  {
    long lastModified = 0;

    if (_page != null) {
      Path pagePath = _page.getSelfPath(this);

      if (pagePath != null)
        lastModified = pagePath.getLastModified();
    }

    for (Path includePath : _includeSet) {
      long includeLastModified = includePath.getLastModified();

      if (lastModified < includeLastModified)
        lastModified = includeLastModified;
    }

    return lastModified;
  }
  */

  /**
   * Returns the response.
   */
  public HttpServletResponse getResponse()
  {
    return _response;
  }

  /**
   * Sets the session callback.
   */
  public void setSessionCallback(SessionCallback callback)
  {
    _sessionCallback = callback;
  }

  /**
   * Gets the session callback.
   */
  public SessionCallback getSessionCallback()
  {
    return _sessionCallback;
  }

  /**
   * Returns the session.
   */
  public SessionArrayValue getSession()
  {
    return _session;
  }

  /**
   * Returns the Java Http session.
   */
  public HttpSession getJavaSession()
  {
    return _javaSession;
  }

  /**
   * Sets the session.
   */
  public void setSession(SessionArrayValue session)
  {
    _session = session;

    if (session != null) {
      Value var = getGlobalVar("_SESSION");
      
      if (! (var instanceof SessionVar)) {
	var = new SessionVar();
	setGlobalValue("_SESSION", var);
      }

      var.set(session);

      setGlobalValue("HTTP_SESSION_VARS", session);

      session.addUse();
    }
    else {
      // php/1k0v
      Value v = getGlobalVar("_SESSION");

      if (v != null)
        v.set(UnsetValue.UNSET);

      v = getGlobalVar("HTTP_SESSION_VARS");

      if (v != null)
        v.set(UnsetValue.UNSET);
    }
  }

  /**
   * Returns a new session id.
   */
  public String generateSessionId()
  {
    String sessionId = 
      _quercus.getQuercusSessionManager().createSessionId(this);

    if (_javaSession != null)
      sessionId = _javaSession.getId().substring(0, 3) + sessionId.substring(3);

    return sessionId;
  }

  /**
   * Create the session.
   */
  public SessionArrayValue createSession(String sessionId, boolean create)
  {
    long now = Alarm.getCurrentTime();

    SessionCallback callback = getSessionCallback();

    _javaSession = _request.getSession(true);

    if (create && _javaSession.getId().length() >= 3
               && sessionId.length() >= 3)
      sessionId = _javaSession.getId().substring(0, 3) + sessionId.substring(3);

    SessionArrayValue session = _quercus.loadSession(this, sessionId);

    if (callback != null) {
      StringValue value = callback.read(this, sessionId);

      if (value != null && value.length() != 0) {
        Value unserialize = VariableModule.unserialize(this, value);

        if (unserialize instanceof ArrayValue) {
          ArrayValue arrayValue = (ArrayValue) unserialize;

          session.reset(now);
          session.putAll(arrayValue);
        }
      }
    }

    setSession(session);

    return session;
  }

  /**
   * Destroy the session.
   */
  public void destroySession(String sessionId)
  {
    SessionCallback callback = getSessionCallback();

    if (callback != null) {
      callback.destroy(this, sessionId);
    }
    else {
      _quercus.destroySession(sessionId);
    }

    setSession(null);
  }

  /**
   * Returns the logger used for syslog.
   */
  public Logger getLogger()
  {
    if (_logger == null)
      _logger = Logger.getLogger("quercus.quercus");

    return _logger;
  }

  /**
   * Returns the configuration value of an init var.
   */
  public Value getConfigVar(String name)
  {
    return getIniDefinition(name).getValue(_quercus);
  }

  /**
   * Returns a map of the ini values that have been explicitly set.
   */
  public IdentityHashMap<String, Value> getIniMap(boolean create)
  {
    if (_iniMap == null && create)
      _iniMap = new IdentityHashMap<String,Value>();

    return _iniMap;
  }

  /**
   * Sets an ini value.
   */
  public StringValue setIni(String name, Value value)
  {
    _iniCount++;
    
    StringValue oldValue = getIni(name);

    getIniDefinition(name).set(this, value);

    return oldValue;
  }

  /**
   * Sets an ini value.
   */
  public StringValue setIni(String name, String value)
  {
    _iniCount++;
    
    StringValue oldValue = getIni(name);

    getIniDefinition(name).set(this, value);

    return oldValue;
  }

  /**
   * Returns an ini value.
   */
  public StringValue getIni(String name)
  {
    return getIniDefinition(name).getAsStringValue(this);
  }

  private IniDefinition getIniDefinition(String name)
  {
    return _quercus.getIniDefinitions().get(name);
  }

  /**
   * Returns an ini value.
   */
  public boolean getIniBoolean(String name)
  {
    return getIniDefinition(name).getAsBoolean(this);
  }

  /**
   * Returns an ini value as a long.
   */
  public long getIniLong(String name)
  {
    return getIniDefinition(name).getAsLong(this);
  }

  /**
   * Returns an ini value as a string, null for missing or empty string
   */
  public String getIniString(String name)
  {
    return getIniDefinition(name).getAsString(this);
  }

  /**
   * Returns an ini value.
   */
  public long getIniBytes(String name, long deflt)
  {
    return getIniDefinition(name).getAsLongBytes(this, deflt);
  }

  /**
   * Returns the ByteToChar converter.
   */
  public ByteToChar getByteToChar()
  {
    return ByteToChar.create();
  }

  /**
   * Returns the 'this' value.
   */
  public Value getThis()
  {
    return _this;
  }

  /**
   * Sets the 'this' value, returning the old value.
   */
  public Value setThis(Value value)
  {
    Value oldThis = _this;

    _this = value.toValue();

    return oldThis;
  }

  /**
   * Gets a value.
   */
  public Value getValue(String name)
  {
    Var var = getRef(name);

    if (var != null)
      return var.toValue();
    else
      return NullValue.NULL;
  }

  /**
   * Gets a special value, a special value is used to store and retrieve module
   * specific values in the env using a unique name.
   */
  public Object getSpecialValue(String name)
  {
    return _specialMap.get(name);
  }

  /**
   * Gets a global
   */
  public Value getGlobalValue(String name)
  {
    Var var = getGlobalRef(name);

    if (var != null)
      return var.toValue();
    else
      return NullValue.NULL;
  }

  /**
   * Gets a variable
   *
   * @param name the variable name
   * @param var the current value of the variable
   */
  public final Var getVar(String name, Value value)
  {
    if (value != null)
      return (Var) value;
    
    Var var = _map.get(name);

    if (var != null)
      return var;

    var = getRef(name);

    if (var == null) {
      var = new Var();

      if (_map == _globalMap)
        var.setGlobal();

      _map.put(name, var);
    }

    return var;
  }

  /**
   * Gets a variable
   *
   * @param name the variable name
   * @param value the current value of the variable
   */
  public final Var getGlobalVar(String name, Value value)
  {
    if (value != null)
      return (Var) value;

    Var var = _globalMap.get(name);

    if (var != null)
      return var;

    var = getSuperGlobalRef(name);

    if (var == null)
      var = getGlobalScriptContextRef(name);

    if (var == null) {
      var = new Var();
      var.setGlobal();
    }
    
    _globalMap.put(name, var);

    return var;
  }

  /**
   * Gets a static variable name.
   */
  public final String createStaticName()
  {
    return _quercus.createStaticName();
  }

  /**
   * Gets a static variable
   *
   * @param name the variable name
   */
  public final Var getStaticVar(String name)
  {
    Var var = _staticMap.get(name);

    if (var == null) {
      var = new Var();
      var.setGlobal();
      _staticMap.put(name, var);
    }

    return var;
  }

  /**
   * Unsets variable
   *
   * @param name the variable name
   */
  public final Var unsetVar(String name)
  {
    _map.remove(name);

    return null;
  }

  /**
   * Gets a variable
   *
   * @param name the variable name
   * @param value the current value of the variable
   */
  public final Var setVar(String name, Value value)
  {
    Var var;

    if (value instanceof Var) {
      var = (Var) value;

      if (_map == _globalMap)
        var.setGlobal();
    }
    else
      var = new Var(value.toValue());

    _map.put(name, var);

    return var;
  }

  /**
   * Unsets variable
   *
   * @param name the variable name
   */
  public final Var unsetLocalVar(String name)
  {
    _map.remove(name);

    return null;
  }

  /**
   * Unsets variable
   *
   * @param name the variable name
   */
  public final Var unsetGlobalVar(String name)
  {
    Var oldVar = _globalMap.remove(name);

    /* php/323a
    if (oldVar != null)
      oldVar.set(UnsetValue.UNSET);
    */

    return null;
  }

  /**
   * Gets a local
   *
   * @param var the current value of the variable
   */
  public static final Value getLocalVar(Value var)
  {
    if (var == null)
      var = new Var();

    return var;
  }

  /**
   * Gets a local value
   *
   * @param var the current value of the variable
   */
  public static final Value getLocalValue(Value var)
  {
    if (var != null)
      return var;
    else
      return NullValue.NULL;
  }

  /**
   * Gets a local
   *
   * @param var the current value of the variable
   */
  public static final Value setLocalVar(Value var, Value value)
  {
    value = value.toValue();

    if (var instanceof Var)
      var.set(value);

    return value;
  }

  /**
   * Gets a value.
   */
  public Var getRef(String name)
  {
    Var var = _map.get(name);

    // required for $$ref where $ref is the name of a superglobal
    if (var == null) {
      // php/0809
      if (Quercus.isSuperGlobal(name))
	return getGlobalRef(name);
    }

    return var;
  }

  /**
   * Returns the raw global lookup.
   */
  public Var getGlobalRaw(String name)
  {
    return _globalMap.get(name);
  }
  
  /**
   * Gets a global value.
   */
  public Var getGlobalRef(String name)
  {
    Var var = _globalMap.get(name);

    if (var == null) {
      var = getSuperGlobalRef(name);

      if (var == null)
        var = getGlobalScriptContextRef(name);
      
      if (var == null)
        var = new Var();
      
      _globalMap.put(name, var);
    }

    return var;
  }

  /**
   * Returns a superglobal.
   */
  private Var getSuperGlobalRef(String name)
  {
    Var var;
    
    switch (SPECIAL_VARS.get(name)) {
      case _ENV: {
        var = new Var();

        _globalMap.put(name, var);

        var.set(new ArrayValueImpl());

        return var;
      }

      case HTTP_POST_VARS:
        if (! Quercus.INI_REGISTER_LONG_ARRAYS.getAsBoolean(this))
          return null;
      case _POST: {
        var = new Var();

        _globalMap.put(name, var);

        ArrayValue post = new ArrayValueImpl();

        var.set(post);

        if (_request == null)
          return null;

        if (! "POST".equals(_request.getMethod()))
          return var;

        if (_postArray != null) {
          for (Map.Entry<Value, Value> entry : _postArray.entrySet()) {
            post.put(entry.getKey(), entry.getValue());
          }
        }
        
        return var;
      }

      case HTTP_POST_FILES:
        if (! Quercus.INI_REGISTER_LONG_ARRAYS.getAsBoolean(this))
          return null;
      case _FILES: {
        var = new Var();

        _globalMap.put(name, var);

        ArrayValue files = new ArrayValueImpl();

        if (_files != null) {
          for (Map.Entry<Value, Value> entry : _files.entrySet()) {
            files.put(entry.getKey(), entry.getValue());
          }
        }

        var.set(files);
        
        return var;
      }

      case HTTP_GET_VARS:
        if (! Quercus.INI_REGISTER_LONG_ARRAYS.getAsBoolean(this))
          return null;
      
      case _GET: {
        var = new Var();

        ArrayValue array = new ArrayValueImpl();

        var.set(array);
        _globalMap.put(name, var);

        if (_request == null)
          return var;
        
        String queryString = _request.getQueryString();
        if (queryString == null)
          return var;

        StringUtility.parseStr(this,
                               queryString,
                               array,
                               true,
                               getHttpInputEncoding());
        
        return var;
      }
      
      case _REQUEST: {
        var = new Var();
        
        ArrayValue array = new ArrayValueImpl();

        var.set(array);

        _globalMap.put(name, var);

        if (_request == null)
          return var;
        
        try {
          String encoding = getHttpInputEncoding();
          
          if (encoding == null)
            encoding = "iso-8859-1";
          
          _request.setCharacterEncoding(encoding);
        } catch (Exception e) {
          log.log(Level.FINE, e.toString(), e);
        }
        
        ArrayList<String> keys = new ArrayList<String>();
        keys.addAll(_request.getParameterMap().keySet());

        Collections.sort(keys);

        boolean isMagicQuotes = getIniBoolean("magic_quotes_gpc");
        
        for (String key : keys) {
          String []value = _request.getParameterValues(key);

          Post.addFormValue(this,
                            array,
                            key,
                            value,
                            isMagicQuotes);
        }

        if (name.equals("_REQUEST") && _postArray != null) {
          
          for (Map.Entry<Value, Value> entry : _postArray.entrySet()) {
            Value key = entry.getKey();
            Value value = entry.getValue();
            
            Value existingValue = array.get(key);
            
            if (existingValue.isArray() && value.isArray())
             existingValue.toArrayValue(this).putAll(value.toArrayValue(this));
            else
              array.put(entry.getKey(), entry.getValue().copy());
          }
        }

        Cookie []cookies = _request.getCookies();
        for (int i = 0; cookies != null && i < cookies.length; i++) {   
          Cookie cookie = cookies[i];

          String decodedValue = decodeValue(cookie.getValue());

          Post.addFormValue(this,
                            array,
                            cookie.getName(),
                            new String[] { decodedValue },
                            isMagicQuotes);
        }
        
        return var;
      }

      case HTTP_RAW_POST_DATA: {
        if (! Quercus.INI_ALWAYS_POPULATE_RAW_POST_DATA.getAsBoolean(this))
          return null;
        
        if (_inputData == null)
          return null;
        
        var = new Var();
        
        _globalMap.put(name, var);
        
        var.set(_inputData);
        
        return var;
      }
    
      case HTTP_SERVER_VARS:
        if (! Quercus.INI_REGISTER_LONG_ARRAYS.getAsBoolean(this))
          return null;
      
      case _SERVER: {
        var = new Var();

        _globalMap.put(name, var);

        var.set(new ServerArrayValue(this));

        return var;
      }

      case _GLOBAL: {
        var = new Var();

        _globalMap.put(name, var);

        var.set(new GlobalArrayValue(this));

        return var;
      }

      case HTTP_COOKIE_VARS:
        if (! Quercus.INI_REGISTER_LONG_ARRAYS.getAsBoolean(this))
          return null;
      
      case _COOKIE: {
        var = new Var();
        _globalMap.put(name, var);

        if (_request == null)
          return var;
        
        ArrayValue array = new ArrayValueImpl();

        Cookie []cookies = _request.getCookies();
        if (cookies != null) {
          for (int i = 0; i < cookies.length; i++) {
            Cookie cookie = cookies[i];

            String value = decodeValue(cookie.getValue());

            StringValue valueAsValue = createString(value);

            if (getIniBoolean("magic_quotes_gpc")) // php/0876
              valueAsValue = StringModule.addslashes(valueAsValue);

            array.append(createString(cookie.getName()), valueAsValue);
          }
        }

        var.set(array);

        return var;
      }
      
      case _SESSION: {
        var = _globalMap.get("_SESSION");

        return var;
      }

      case PHP_SELF: {
        var = new Var();
        _globalMap.put(name, var);

        var.set(getGlobalVar("_SERVER").get(PHP_SELF_STRING));

        return var;
      }
      
      default:
        return null;
    }
  }

  /**
   * Gets a value.
   */
  protected Var getGlobalSpecialRef(String name)
  {
    Var var = null;

    if (_map != _globalMap) {
      var = _globalMap.get(name);
      
      if (var != null)
        return var;
    }

    var = getSuperGlobalRef(name);

    if (var != null)
      _globalMap.put(name, var);

    return var;
  }

  protected Var getGlobalScriptContextRef(String name)
  {
    if (_scriptContext == null)
      return null;

    Var var = _globalMap.get(name);
    
    if (var != null)
      return var;
    
    Object value = _scriptContext.getAttribute(name);

    if (value == null) {
      Bindings bindings
      = _scriptContext.getBindings(ScriptContext.ENGINE_SCOPE);

      if (bindings != null)
        value = bindings.get(name);
    }

    if (value == null) {
      Bindings bindings
      = _scriptContext.getBindings(ScriptContext.GLOBAL_SCOPE);

      if (bindings != null)
        value = bindings.get(name);
    }

    if (value != null) {
      var = new Var();
      _globalMap.put(name, var);

      var.set(wrapJava(value));
    }

    return var;
  }

  private static String decodeValue(String s)
  {
    int len = s.length();
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < len; i++) {
      char ch = s.charAt(i);

      if (ch == '%' && i + 2 < len) {
        int d1 = s.charAt(i + 1);
        int d2 = s.charAt(i + 2);

        int v = 0;

        if ('0' <= d1 && d1 <= '9')
          v = 16 * (d1 - '0');
        else if ('a' <= d1 && d1 <= 'f')
          v = 16 * (d1 - 'a' + 10);
        else if ('A' <= d1 && d1 <= 'F')
          v = 16 * (d1 - 'A' + 10);
        else {
          sb.append('%');
          continue;
        }

        if ('0' <= d2 && d2 <= '9')
          v += (d2 - '0');
        else if ('a' <= d2 && d2 <= 'f')
          v += (d2 - 'a' + 10);
        else if ('A' <= d2 && d2 <= 'F')
          v += (d2 - 'A' + 10);
        else {
          sb.append('%');
          continue;
        }

        i += 2;
        sb.append((char) v);
      }
      else if (ch == '+')
        sb.append(' ');
      else
        sb.append(ch);
    }

    return sb.toString();
  }

  /**
   * Gets a value.
   */
  public Var getVar(String name)
  {
    Var var = _map.get(name);

    if (var != null)
      return var;
    
    var = getRef(name);

    if (var == null) {
      var = new Var();

      if (_map == _globalMap) {
        // php/379c
        var.setGlobal();
      }

      _map.put(name, var);
    }

    return var;
  }

  /**
   * Gets a value.
   */
  public Var getGlobalVar(String name)
  {
    Var var = getGlobalRef(name);

    if (var == null) {
      var = new Var();
      var.setGlobal();
      _globalMap.put(name, var);
    }

    return var;
  }

  public boolean isGlobalEnv()
  {
    return _map == _globalMap;
  }

  /**
   * Sets a value.
   */
  public Value setValue(String name, Value value)
  {
    if (value instanceof Var)
      _map.put(name, (Var) value);
    else {
      Var var = getVar(name);
      var.set(value);
    }

    return value;
  }

  /**
   * Sets a special value, a special value is used to store and retrieve module
   * specific values in the env using a unique name.
   */
  public Object setSpecialValue(String name, Object value)
  {
    _specialMap.put(name, value);

    return value;
  }

  /**
   * Sets a value.
   */
  public Value setGlobalValue(String name, Value value)
  {
    if (value instanceof Var)
      _globalMap.put(name, (Var) value);
    else {
      Var var = getGlobalVar(name);
      var.set(value);
    }

    return value;
  }

  /**
   * Gets a static class field.
   *
   * @param className of the owning class 
   * @param name of the variable
   */
  public Value getStaticClassFieldValue(String className, String name)
  {
    Var var = getStaticClassFieldVar(className, name);
    
    if (var != null) {
      Value val = var.toValue();
        
      return val;
    }
    else
      return NullValue.NULL;
  }

  /**
   * Gets a static field from a class.
   *
   * @param className of the owning class 
   * @param name of the variable
   */
  public final Var getStaticClassFieldVar(String className, String name)
  {
    QuercusClass cl = findAbstractClass(className);
    
    Var var = cl.getStaticField(this, name);
    
    if (var == null) {
      error(L.l("{0}::${1} is an undeclared static property", className, name));
    }

    return var;
  }

  /**
   * Sets the calling function expression.
   */
  public void pushCall(Expr call, Value obj, Value []args)
  {
    if (_callStack.length <= _callStackTop) {
      Expr []newStack = new Expr[2 * _callStack.length];
      System.arraycopy(_callStack, 0, newStack, 0, _callStack.length);
      _callStack = newStack;
      
      Value []newThisStack = new Value[2 * _callThisStack.length];
      System.arraycopy(_callThisStack,
		       0, newThisStack,
		       0, _callThisStack.length);
      
      Value []newArgStack = new Value[2 * _callArgStack.length];
      System.arraycopy(_callArgStack,
		       0, newArgStack,
		       0, _callArgStack.length);
      
      _callThisStack = newThisStack;
    }

    _callStack[_callStackTop] = call;
    _callThisStack[_callStackTop] = obj;
    _callArgStack[_callStackTop] = args;

    _callStackTop++;
  }

  /**
   * Pops the top call.
   */
  public Expr popCall()
  {
    return _callStack[--_callStackTop];
  }

  /**
   * Returns the stack depth.
   */
  public int getCallDepth()
  {
    return _callStackTop;
  }
  
  /**
   * Peeks at the the top call.
   */
  public Expr peekCall(int depth)
  {
    if (_callStackTop - depth > 0)
      return _callStack[_callStackTop - depth - 1];
    else
      return null;
  }
  
  /**
   * Peeks at the "this" top call.
   */
  public Value peekCallThis(int depth)
  {
    if (_callStackTop - depth > 0)
      return _callThisStack[_callStackTop - depth - 1];
    else
      return null;
  }
  
  /**
   * Peeks at the the top call.
   */
  public Value []peekArgs(int depth)
  {
    if (_callStackTop - depth > 0)
      return _callArgStack[_callStackTop - depth - 1];
    else
      return null;
  }
  
  /*
   * Returns true if <code>name</code> doesn't already exist on the
   * field __get() stack.
   */
  public boolean pushFieldGet(String className, StringValue fieldName)
  {
    FieldGetEntry entry = new FieldGetEntry(className, fieldName);
    
    if (_fieldGetList.contains(entry))
      return false;
    else {
      _fieldGetList.add(entry);
      
      return true;
    }
  }
  
  public void popFieldGet(String className, StringValue fieldName)
  {
    FieldGetEntry entry = new FieldGetEntry(className, fieldName);
    
    if (_fieldGetList.size() > 0)
      _fieldGetList.removeLast();
  }
  
  /*
   * Returns the calling class.
   */
  public QuercusClass getCallingClass()
  {
    return _callingClass;
  }
  
  /*
   * Sets the calling class.
   */
  public QuercusClass setCallingClass(QuercusClass cls)
  {
    QuercusClass oldCallingClass = _callingClass;
    
    _callingClass = cls;
    
    return oldCallingClass;
  }

  public ArrayList<String> getStackTrace()
  {
    ArrayList<String> trace = new ArrayList<String>();

    for (int i = _callStackTop - 1; i >= 0; i--) {
      String entry;
      Location location = _callStack[i].getLocation();
      String loc;

      if (location != null && location.getFileName() != null) {
	loc = (" (at " + location.getFileName()
	       + ":" + location.getLineNumber() + ")");
      }
      else
	loc = "";
      
      if (_callThisStack[i] != null
	  && ! "".equals(_callThisStack[i].toString())) {
	entry = _callThisStack[i] + "." + _callStack[i].toString() + loc;
      }
      else
	entry = _callStack[i].toString() + loc;

      trace.add(entry);
    }

    return trace;
  }

  /**
   * Pushes a new environment.
   */
  public HashMap<String, Var> pushEnv(HashMap<String, Var> map)
  {
    HashMap<String, Var> oldEnv = _map;

    _map = map;

    return oldEnv;
  }

  /**
   * Restores the old environment.
   */
  public void popEnv(HashMap<String, Var> oldEnv)
  {
    _map = oldEnv;
  }

  /**
   * Returns the current environment.
   */
  public HashMap<String, Var> getEnv()
  {
    return _map;
  }

  /**
   * Returns the current environment.
   */
  public HashMap<String, Var> getGlobalEnv()
  {
    return _globalMap;
  }

  /**
   * Pushes a new environment.
   */
  public final Value []setFunctionArgs(Value []args)
  {
    Value []oldArgs = _functionArgs;

    Value []newArgs = new Value[args.length];

    for (int i = 0; args != null && i < args.length; i++) {
      // php/3715, 3768
      newArgs[i] = args[i].toValue().toArgValue();
    }

    _functionArgs = newArgs;

    return oldArgs;
  }

  /**
   * Pushes a new environment.
   */
  public final Value []setFunctionArgsNoCopy(Value []args)
  {
    Value []oldArgs = _functionArgs;

    for (int i = 0; args != null && i < args.length; i++)
      args[i] = args[i].toValue();

    _functionArgs = args;

    return oldArgs;
  }

  /**
   * Pushes a new environment.
   */
  public final void restoreFunctionArgs(Value []args)
  {
    _functionArgs = args;
  }

  /**
   * Returns the function args.
   */
  public final Value []getFunctionArgs()
  {
    return _functionArgs;
  }

  /**
   * Removes a specialValue
   */
  public Object removeSpecialValue(String name)
  {
    return _specialMap.remove(name);
  }

  /**
   * Returns a constant.
   */
  public Value getConstant(String name)
  {
    Value value = getConstantImpl(name);

    if (value != null)
      return value;

    /* XXX:
       notice(L.l("Converting undefined constant '{0}' to string.",
       name));
    */

    value = createString(name);

    return value;
  }

  /**
   * Returns true if the constant is defined.
   */
  public boolean isDefined(String name)
  {
    return getConstantImpl(name) != null;
  }

  /**
   * Returns a constant.
   */
  private Value getConstantImpl(String name)
  {
    Value value = _constMap.get(name);

    if (value != null)
      return value;

    value = _quercus.getConstant(name);
    if (value != null)
      return value;

    if (_lowerConstMap != null) {
      value = _lowerConstMap.get(name.toLowerCase());

      if (value != null)
	return value;
    }

    return null;
  }
  
  /**
   * Removes a constant.
   */
  public Value removeConstant(String name)
  {
    return _constMap.remove(name);
  }

  /**
   * Sets a constant.
   */
  public Value addConstant(String name,
                           Value value,
                           boolean isCaseInsensitive)
  {
    Value oldValue = _constMap.get(name);

    if (oldValue != null)
      return oldValue;

    _constMap.put(name, value);

    if (_lowerConstMap != null && isCaseInsensitive)
      _lowerConstMap.put(name.toLowerCase(), value);

    return value;
  }

  /**
   * Returns an array of the defined functions.
   */
  public ArrayValue getDefinedConstants()
  {
    ArrayValue result = new ArrayValueImpl();

    for (Map.Entry<String, Value> entry : _quercus.getConstMap().entrySet()) {
      result.put(createString(entry.getKey()), entry.getValue());
    }

    for (Map.Entry<String, Value> entry : _constMap.entrySet()) {
      result.put(createString(entry.getKey()), entry.getValue());
    }

    return result;
  }

  /**
   * Returns true if an extension is loaded.
   */
  public boolean isExtensionLoaded(String name)
  {
    return getQuercus().isExtensionLoaded(name);
  }

  /**
   * Returns true if an extension is loaded.
   */
  public HashSet<String> getLoadedExtensions()
  {
    return getQuercus().getLoadedExtensions();
  }

  /**
   * Returns true if an extension is loaded.
   */
  public Value getExtensionFuncs(String name)
  {
    return getQuercus().getExtensionFuncs(name);
  }

  /**
   * Returns the default stream resource.
   */
  public StreamContextResource getDefaultStreamContext()
  {
    if (_defaultStreamContext == null)
      _defaultStreamContext = new StreamContextResource();

    return _defaultStreamContext;
  }
  
  public ArrayValue getDefinedFunctions()
  {
    ArrayValueImpl funs = new ArrayValueImpl();
    ArrayValueImpl system = new ArrayValueImpl();
    ArrayValueImpl user = new ArrayValueImpl();

    AbstractFunction []systemFuns = _quercus.getFunctionMap();
    AbstractFunction []envFuns = _fun;

    for (int i = 0; i < envFuns.length; i++) {
      if (i < systemFuns.length
	  && systemFuns[i] != null
	  && ! (systemFuns[i] instanceof UndefinedFunction)) {
	system.append(createString(systemFuns[i].getName()));
      }
      else if (envFuns[i] != null
	       && ! (envFuns[i] instanceof UndefinedFunction)) {
	user.append(createString(envFuns[i].getName()));
      }
    }

    funs.append(createString("internal"), system);
    funs.append(createString("user"), user);

    return funs;
  }

  /**
   * Returns the function with a given name.
   *
   * Compiled mode normally uses the _fun array directly, so this call
   * is rare.
   */
  public AbstractFunction findFunction(String name)
  {
    int id = _quercus.findFunctionId(name);

    if (id >= 0) {
      if (id < _fun.length && ! (_fun[id] instanceof UndefinedFunction))
	return _fun[id];
      else
	return null;
    }

    AbstractFunction fun = _quercus.findFunctionImpl(name);

    if (fun != null)
      return fun;

    if (isStrict())
      return null;

    name = name.toLowerCase();
    
    id = _quercus.findFunctionId(name);

    if (id >= 0) {
      if (id < _fun.length && ! (_fun[id] instanceof UndefinedFunction))
	return _fun[id];
      else
        return null;
    }
    
    fun = _quercus.findLowerFunctionImpl(name);
    
    if (fun != null)
      return fun;
    
    if (_anonymousFunMap != null)
      return _anonymousFunMap.get(name);
      
    return null;
  }
  
  public AbstractFunction getFunction(String name)
  {
    AbstractFunction fun = findFunction(name);

    if (fun != null)
      return fun;
    else
      throw createErrorException(L.l("'{0}' is an unknown function.", name));
  }
  
  public void updateFunction(int id, AbstractFunction fun)
  {
    if (_fun.length <= id) {
      AbstractFunction []oldFun = _fun;
      
      _fun = new AbstractFunction[id + 256];
      System.arraycopy(oldFun, 0, _fun, 0, oldFun.length);
    }

    if (_fun[id] == null)
      _fun[id] = fun;
  }
  
  /*
  public int getFunctionId(String name)
  {
    int id = _quercus.getFunctionId(name);

    if (_fun.length <= id) {
      AbstractFunction []oldFun = _fun;
      
      _fun = new AbstractFunction[id + 256];
      System.arraycopy(oldFun, 0, _fun, 0, oldFun.length);
    }

    AbstractFunction []defFuns = _quercus.getFunctionMap();

    if (_fun[id] == null)
      _fun[id] = defFuns[id];

    return id;
  }
  
  public int getFunctionIdCount()
  {
    return _quercus.getFunctionIdCount();
  }
  */

  /**
   * Finds the java reflection method for the function with the given name.
   *
   * @param name the method name
   * @return the found method or null if no method found.
   */
  public AbstractFunction getFunction(Value name)
  {
    name = name.toValue();

    if (name instanceof CallbackFunction)
      return ((CallbackFunction) name).getFunction();

    return getFunction(name.toString());
  }

  /*
  public DefinitionState getDefinitionState()
  {
    return _defState;
  }
  */
  
  public Value addFunction(String name, AbstractFunction fun)
  {
    AbstractFunction staticFun
      = _quercus.findLowerFunctionImpl(name.toLowerCase());

    if (staticFun != null)
      throw new QuercusException(L.l("can't redefine function {0}", name));
    
    int id = _quercus.getFunctionId(name);

    // XXX: anonymous/generated functions(?), e.g. like foo2431

    if (_fun.length <= id) {
      AbstractFunction []funMap = new AbstractFunction[id + 256];
      System.arraycopy(_fun, 0, funMap, 0, _fun.length);
      _fun = funMap;
    }

    if (_fun[id] != null && ! (_fun[id] instanceof UndefinedFunction))
      throw new QuercusException(L.l("can't redefine function {0}", name));

    _fun[id] = fun;

    return BooleanValue.TRUE;
  }
  
  public AbstractFunction createAnonymousFunction(String args, String code)
    throws IOException
  {
    log.log(Level.FINE, code);

    if (_anonymousFunMap == null)
      _anonymousFunMap = new HashMap<String, AbstractFunction>();

    // PHP naming style for anonymous functions
    String name = "\u0000lamba" + (_anonymousFunMap.size() + 1);
    
    AbstractFunction fun = getQuercus().parseFunction(name, args, code);

    _anonymousFunMap.put(name, fun);
    return fun;
  }

  /**
   * Adds a function from a compiled include
   *
   * @param name the function name, must be an intern() string
   * @param lowerName the function name, must be an intern() string
   */
  public Value addFunctionFromPage(String name, String lowerName,
				   AbstractFunction fun)
  {
    // XXX: skip the old function check since the include for compiled
    // pages is already verified.  Might have a switch here?
    /*
    AbstractFunction oldFun = _lowerFunMap.get(lowerName);

    if (oldFun == null)
      oldFun = _quercus.findLowerFunctionImpl(lowerName);

    if (oldFun != null) {
      throw new QuercusException(L.l("can't redefine function {0}", name));
    }

    _funMap.put(name, fun);

    if (! isStrict())
      _lowerFunMap.put(lowerName, fun);
    */

    return BooleanValue.TRUE;
  }

  /**
   * Finds the java reflection method for the function with the given name.
   *
   * @param className the class name
   * @param methodName the method name
   * @return the found method or null if no method found.
   */
  public AbstractFunction findMethod(String className, String methodName)
  {
    QuercusClass cl = findClass(className);

    if (cl == null) {
      error(L.l("'{0}' is an unknown class.", className));
      return null;
    }

    AbstractFunction fun = cl.findFunction(methodName);

    if (fun == null) {
      error(L.l("'{0}::{1}' is an unknown method.",
                className, methodName));
      return null;
    }

    return fun;
  }

  /**
   * Compiles and calluates the given code
   *
   * @param code the code to calluate
   * @return the result
   */
  public Value evalCode(String code)
    throws IOException
  {
    if (log.isLoggable(Level.FINER))
      log.finer(code);

    Quercus quercus = getQuercus();

    QuercusProgram program = quercus.parseEvalExpr(code);

    Value value = program.execute(this);
    
    if (value == null)
      return NullValue.NULL;
    else
      return value;
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @return the function value
   */
  public Value call(String name)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.call(this);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @return the function value
   */
  public Value call(String name, Value a0)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.call(this, a0);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @return the function value
   */
  public Value call(String name, Value a0, Value a1)
  {
    return getFunction(name).call(this, a0, a1);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @return the function value
   */
  public Value call(String name, Value a0, Value a1, Value a2)
  {
    return getFunction(name).call(this, a0, a1, a2);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   * @return the function value
   */
  public Value call(String name, Value a0, Value a1, Value a2, Value a3)
  {
    return getFunction(name).call(this, a0, a1, a2, a3);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   * @param a4 the fifth argument
   * @return the function value
   */
  public Value call(String name, Value a0, Value a1,
                    Value a2, Value a3, Value a4)
  {
    return getFunction(name).call(this, a0, a1, a2, a3, a4);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param args the arguments
   * @return the function value
   */
  public Value call(String name, Value []args)
  {
    return getFunction(name).call(this, args);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @return the function value
   */
  public Value callRef(String name)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this);
  }

  /**
   * EvalRefuates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @return the function value
   */
  public Value callRef(String name, Value a0)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this, a0);
  }

  /**
   * EvalRefuates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @return the function value
   */
  public Value callRef(String name, Value a0, Value a1)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this, a0, a1);
  }

  /**
   * EvalRefuates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @return the function value
   */
  public Value callRef(String name, Value a0, Value a1, Value a2)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this, a0, a1, a2);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   * @return the function value
   */
  public Value callRef(String name, Value a0, Value a1, Value a2, Value a3)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this, a0, a1, a2, a3);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param a0 the first argument
   * @param a1 the second argument
   * @param a2 the third argument
   * @param a3 the fourth argument
   * @param a4 the fifth argument
   * @return the function value
   */
  public Value callRef(String name, Value a0, Value a1,
                       Value a2, Value a3, Value a4)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this, a0, a1, a2, a3, a4);
  }

  /**
   * Evaluates the named function.
   *
   * @param name the function name
   * @param args the arguments
   * @return the function value
   */
  public Value callRef(String name, Value []args)
  {
    AbstractFunction fun = findFunction(name);

    if (fun == null)
      return error(L.l("'{0}' is an unknown function.", name));

    return fun.callRef(this, args);
  }

  /**
   * Adds a class, e.g. from an include.
   */
  public void addClassDef(String name, ClassDef cl)
  {
    int id = _quercus.getClassId(name);

    if (_classDef.length <= id) {
      ClassDef []def = new ClassDef[id + 256];
      System.arraycopy(_classDef, 0, def, 0, _classDef.length);
      _classDef = def;
    }
    
    if (_classDef[id] == null)
      _classDef[id] = cl;
  }

  public ClassDef findClassDef(String name)
  {
    int id = _quercus.getClassId(name);

    if (id < _classDef.length)
      return _classDef[id];
    else
      return null;
  }

  /**
   * Creates a stdClass object.
   */
  public ObjectValue createObject()
  {
    try {
      return (ObjectValue) _quercus.getStdClass().createObject(this);
    }
    catch (Exception e) {
      throw new QuercusModuleException(e);
    }
  }
  
  /**
   * Creates a stdClass object.
   */
  public ObjectValue createIncompleteObject(String name)
  {
    try {
      ObjectValue obj
        = (ObjectValue) _quercus.getStdClass().createObject(this);
      
      obj.setIncompleteObjectName(name);
      
      return obj;
      
    }
    catch (Exception e) {
      throw new QuercusModuleException(e);
    }
  }

  /*
   * Creates an empty string.
   */
  public StringValue getEmptyString()
  {
    if (_isUnicodeSemantics)
      return UnicodeBuilderValue.EMPTY;
    else
      return StringBuilderValue.EMPTY;
  }

  /*
   * Creates an empty string builder.
   */
  public StringValue createStringBuilder()
  {
    if (_isUnicodeSemantics)
      return new UnicodeBuilderValue();
    else
      return new StringBuilderValue();
  }
  
  /**
   * Creates a PHP string from a byte buffer.
   */
  public StringValue createString(byte []buffer, int offset, int length)
  {
    if (_isUnicodeSemantics)
      return new UnicodeValueImpl(new String(buffer, offset, length));
    else
      return new StringBuilderValue(buffer, offset, length);
  }
  
  /**
   * Creates a PHP string from a byte buffer.
   */
  public StringValue createString(char []buffer, int length)
  {
    if (_isUnicodeSemantics)
      return new UnicodeBuilderValue(buffer, length);
    else
      return new StringBuilderValue(buffer, length);
  }
  
  /**
   * Creates a PHP string from a char buffer.
   */
  public StringValue createString(char []buffer, int offset, int length)
  {
    if (_isUnicodeSemantics)
      return new UnicodeBuilderValue(buffer, offset, length);
    else
      return new StringBuilderValue(buffer, offset, length);
  }

  /**
   * Creates a PHP string from a java String.
   */
  public StringValue createString(String s)
  {
    if (s == null || s.length() == 0) {
      return (_isUnicodeSemantics
	      ? UnicodeBuilderValue.EMPTY
	      : StringBuilderValue.EMPTY);
    }
    else if (_isUnicodeSemantics)
      return new UnicodeBuilderValue(s);
    else
      return new StringBuilderValue(s);
  }

  /**
   * Creates a string from a byte.
   */
  public StringValue createString(char ch)
  {
    if (_isUnicodeSemantics)
      return UnicodeValueImpl.create(ch);
    else
      return StringBuilderValue.create(ch);
  }

  /**
   * Creates a PHP string from a buffer.
   */
  public StringValue createBinaryString(TempBuffer head)
  {
    StringValue string;
    
    if (_isUnicodeSemantics)
      string = new BinaryBuilderValue();
    else
      string = new StringBuilderValue();

    for (; head != null; head = head.getNext()) {
      string.append(head.getBuffer(), 0, head.getLength());
    }

    return string;
  }

  /**
   * Creates a PHP Exception.
   */
  public Value createException(Throwable e)
  {
    QuercusClass cls = findClass("Exception");
    
    StringValue message = createString(e.getMessage());
    Value []args = { message };

    Value value = cls.callNew(this, args);

    StackTraceElement elt = e.getStackTrace()[0];

    value.putField(this, "file", createString(elt.getFileName()));
    value.putField(this, "line", LongValue.create(elt.getLineNumber()));
    value.putField(this, "trace", ErrorModule.debug_backtrace(this));

    return value;
  }
  
  /**
   * Generate an object id.
   */
  public int generateId()
  {
    return ++_objectId;
  }

  /**
   * Returns an introspected Java class defintion.
   */
  public JavaClassDef getJavaClassDefinition(String className)
  {
    JavaClassDef def = getJavaClassDefinition(className, true);
    
    if (def != null)
      return def;
    else
      throw createErrorException(L.l("'{0}' class definition not found", className));
  }

  /*
   * Returns an introspected Java class definition.
   */
  public JavaClassDef getJavaClassDefinition(Class type)
  { 
    JavaClassDef def = _quercus.getJavaClassDefinition(type, type.getName());

    return def;
  }
  
  private JavaClassDef getJavaClassDefinition(String className,
					      boolean useImport)
  { 
    JavaClassDef def = null;
    
    try {
      def = _quercus.getJavaClassDefinition(className);
      
      if (def == null && useImport) {
        useImport = false;
        def = importJavaClass(className);
      }
    }
    catch (Throwable e) {
      if (useImport)
        def = importJavaClass(className);
      else
        log.log(Level.FINER, e.toString(), e);
    }

    return def;
  }

  /**
   * Imports a Java class.
   * 
   * @param className name of class to import
   * @return class definition of imported class, null if class not found
   */
  public JavaClassDef importJavaClass(String className)
  {
    if (_importMap == null)
      return null;
    
    String fullName = _importMap.getQualified(className);
    
    if (fullName != null) {
      return getJavaClassDefinition(fullName, false);
    }
    else {
      ArrayList<String> wildcardList
        = _importMap.getWildcardList();
  
      for (String entry : wildcardList) {
        fullName = entry + '.' + className;
          
        JavaClassDef def = getJavaClassDefinition(fullName, false);
        
        if (def != null) {
          _importMap.putQualified(className, fullName);
          return def;
        }
      }
    }

    return null;
  }

  /**
   * Adds a Quercus class import.
   * 
   * @param javaName fully qualified class import string
   */
  public void putQualifiedImport(String javaName)
  {
    if (_importMap == null)
      _importMap = new ImportMap();
    
    String phpName = _importMap.putQualified(javaName);
  }
  
  /**
   * Adds a Quercus class import.
   * 
   * @param name wildcard class import string minus '*' at the end (i.e. java.util.)
   */
  public void addWildcardImport(String name)
  {
    if (_importMap == null)
      _importMap = new ImportMap();
    
    _importMap.addWildcardImport(name);
  }

  /**
   * Returns a PHP value for a Java object
   *
   * @param isNullAsFalse what to return if <i>obj</i> is null, if true return
   * {@link BooleanValue.FALSE} otherwise return {@link NullValue.NULL)
   */
  public Value wrapJava(Object obj, boolean isNullAsFalse)
  {
    if (obj == null) {
      if (isNullAsFalse)
        return BooleanValue.FALSE;
      else
        return NullValue.NULL;
    }

    return wrapJava(obj);
  }

  /**
   * Returns a PHP value for a Java object
   *
   * @param isNullAsFalse what to return if <i>obj</i> is null, if true return
   * {@link BooleanValue.FALSE} otherwise return {@link NullValue.NULL)
   */
  public Value wrapJava(Object obj, JavaClassDef def, boolean isNullAsFalse)
  {
    if (obj == null) {
      if (isNullAsFalse)
        return BooleanValue.FALSE;
      else
        return NullValue.NULL;
    }

    return wrapJava(obj, def);
  }

  /**
   * Returns a PHP value for a Java object
   */
  public Value wrapJava(Object obj)
  {
    if (obj == null)
      return NullValue.NULL;

    if (obj instanceof Value)
      return (Value) obj;
    
    JavaClassDef def = getJavaClassDefinition(obj.getClass());

    return def.wrap(this, obj);
  }

  /**
   * Returns a PHP value for a Java object
   *
   * @param isNullAsFalse what to return if <i>obj</i> is null, if true return
   * {@link BooleanValue.FALSE} otherwise return {@link NullValue.NULL)
   */
  public Value wrapJava(Object obj, JavaClassDef def)
  {
    if (obj == null)
      return NullValue.NULL;

    if (obj instanceof Value)
      return (Value) obj;

    // XXX: why is this logic here?  The def should be correct on the call
    // logic is for JavaMarshal, where can avoid the lookup call
    if (def.getType() != obj.getClass()) {
      // XXX: what if types are incompatible, does it matter?
      // if it doesn't matter, simplify this to one if with no else
      def = getJavaClassDefinition(obj.getClass());
    }

    return def.wrap(this, obj);
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   * @return the found class or null if no class found.
   */
  public QuercusClass findClass(String name)
  {
    return findClass(name, true, true);
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   * @param useAutoload use autoload to locate the class if necessary
   * @return the found class or null if no class found.
   */
  public QuercusClass findClass(String name,
                                boolean useAutoload,
                                boolean useImport)
  {
    QuercusClass cl = _classMap.get(name);

    if (cl != null)
      return cl;

    cl = _lowerClassMap.get(name.toLowerCase());

    if (cl != null)
      return cl;

    cl = createClassFromCache(name, useAutoload, useImport);
    
    if (cl != null) {
      _classMap.put(cl.getName(), cl);
      _lowerClassMap.put(cl.getName().toLowerCase(), cl);

      // php/09b7
      cl.init(this);

      return cl;
    }
    else
      return findClassExt(name, useAutoload, useImport);
  }
  
  private QuercusClass findClassExt(String name,
                                    boolean useAutoload,
                                    boolean useImport)
  {
    if (useAutoload) {
      StringValue nameString = createString(name);
      
      if (! _autoloadClasses.contains(name)) {
        try {
          _autoloadClasses.add(name);

          int size = _autoloadList != null ? _autoloadList.size() : 0;

          for (int i = 0; i < size; i++) {
            Callback cb = _autoloadList.get(i);

            cb.call(this, nameString);
            
            // php/0977
            QuercusClass cls = findClass(name, false, useImport);
            
            if (cls != null)
              return cls;
          }

          if (size == 0) {
            if (_autoload == null)
              _autoload = findFunction("__autoload");
            
            if (_autoload != null) {
              _autoload.call(this, nameString);

              // php/0976
              return findClass(name, false, useImport);
            }
          }
        } finally {
          _autoloadClasses.remove(name);
        }
      }
    }
    
    if (useImport) {
      if (importPhpClass(name)) {
        return findClass(name, false, false);
      }
      else {
        try {
          JavaClassDef javaClassDef = getJavaClassDefinition(name, true);

          if (javaClassDef != null) {
            QuercusClass cls = createQuercusClass(javaClassDef, null);
            
            _classMap.put(cls.getName(), cls);
            _lowerClassMap.put(cls.getName().toLowerCase(), cls);
            
            cls.init(this);
            
            return cls;
          }
        }
        catch (Exception e) {
          log.log(Level.FINER, e.toString(), e);
        }
      }
    }
    
    return null;
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   * @param useAutoload use autoload to locate the class if necessary
   * @return the found class or null if no class found.
   */
  public void addClass(String name, ClassDef def)
  {
    // php/0cn2 - make sure interfaces have a QuercusClass
    for (String iface : def.getInterfaces()) {
      QuercusClass cl = findClass(iface);
    }

    QuercusClass parentClass = null;
    
    // php/1d1o
    if (findClass(name, false, false) == null) {
      String parentName = def.getParentName();
      
      if (parentName != null) {
        parentClass = findAbstractClass(parentName);
      }
      
      QuercusClass qClass = createQuercusClass(def, parentClass);
      
      // php/09p9
      qClass.init(this);

      _classMap.put(name, qClass);
      _lowerClassMap.put(name.toLowerCase(), qClass);
    }
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   * @param useAutoload use autoload to locate the class if necessary
   * @param useImport import the class if necessary
   * 
   * @return the found class or null if no class found.
   */
  private QuercusClass createClassFromCache(String name,
                                       boolean useAutoload,
                                       boolean useImport)
  {
    int id = _quercus.getClassId(name);
    
    ClassDef classDef = _classDef[id];

    if (classDef != null) {
      String parentName = classDef.getParentName();

      QuercusClass parent = null;

      if (parentName != null)
        parent = findClass(parentName);

      if (parentName == null || parent != null)
        return createQuercusClass(classDef, parent);
      else
        return null; // php/
    }

    ClassDef staticClass = _quercus.findClass(name);

    if (staticClass != null)
      return createQuercusClass(staticClass, null); // XXX: cache
    else
      return null;
  }
  
  /*
   * Registers an SPL autoload function.
   */
  public void addAutoloadFunction(Callback fun)
  {
    if (fun == null)
      throw new NullPointerException();
    
    if (_autoloadList == null)
      _autoloadList = new ArrayList<Callback>();

    _autoloadList.add(fun);
  }
  
  /*
   * Unregisters an SPL autoload function.
   */
  public void removeAutoloadFunction(Callback fun)
  {
    if (_autoloadList != null) {
      _autoloadList.remove(fun);
      
      //restore original __autoload functionality
      if (_autoloadList.size() == 0)
        _autoloadList = null;
    }
  }
  
  /*
   * Returns the registered SPL autoload functions.
   */
  public ArrayList<Callback> getAutoloadFunctions()
  {
    return _autoloadList;
  }

  /**
   * Imports a PHP class.
   *
   * @param name of the PHP class
   * 
   * @return true if matching php file was found and included.
   */
  public boolean importPhpClass(String name)
  {
    if (_importMap == null)
      return false;
    
    String fullName = _importMap.getQualifiedPhp(name);
    
    URL url = null;
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    
    if (fullName != null) {
      url = loader.getResource(fullName);
    }
    else {
      for (String entry : _importMap.getWildcardPhpList()) {

        url = loader.getResource(entry + '/' + name + ".php");
        
        if (url != null)
          break;
      }
    }

    if (url != null) {
      includeOnce(new StringBuilderValue(url.toString()));
      return true;
    }
    else {
      return false;
    }
  }

  /**
   * Returns the declared classes.
   *
   * @return an array of the declared classes()
   */
  public Value getDeclaredClasses()
  {
    ArrayList<String> list = new ArrayList<String>();

    for (Map.Entry<String, ClassDef> entry : _quercus.getClassMap().entrySet()) {
      list.add(entry.getKey());
    }
    
    Collections.sort(list);
    
    ArrayValue array = new ArrayValueImpl();
    
    Iterator<String> iter = list.iterator();
    while (iter.hasNext()) {
      array.put(iter.next());
    }
    
    return array;
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   * @return the found class or null if no class found.
   */
  public QuercusClass findAbstractClass(String name)
  {
    QuercusClass cl = findClass(name, true, true);

    if (cl != null)
      return cl;

    throw createErrorException(L.l("'{0}' is an unknown class name.", name));
    /*
    // return _quercus.findJavaClassWrapper(name);

    return null;
    */
  }

  /**
   * Finds the class with the given name.
   *
   * @param name the class name
   * @return the found class
   * @throws QuercusRuntimeException if the class is not found
   */
  public QuercusClass getClass(String name)
  {
    QuercusClass cl = findClass(name);

    if (cl != null)
      return cl;
    else
      throw createErrorException(L.l("'{0}' is an unknown class.", name));
  }

  QuercusClass createQuercusClass(ClassDef def, QuercusClass parent)
  {
    ClassKey key = new ClassKey(def, parent);

    SoftReference<QuercusClass> qClassRef = _classCache.get(key);

    QuercusClass qClass = null;

    if (qClassRef != null) {
      qClass = qClassRef.get();

      if (qClass != null) {
	return new QuercusClass(qClass, parent);
      }
    }

    qClass = new QuercusClass(getModuleContext(), def, parent);
    qClass.validate(this);

    _classCache.put(key, new SoftReference<QuercusClass>(qClass));

    return qClass;
  }

  /**
   * Returns true if class has already been initialized.
   */
  public boolean isInitializedClass(String name)
  {
    return _initializedClassSet.contains(name);
  }
  
  /**
   * Mark this class as being initialized.
   */
  public void addInitializedClass(String name)
  {
    _initializedClassSet.add(name);
  }

  /**
   * Finds the class and method.
   *
   * @param className the class name
   * @param methodName the method name
   * @return the found method or null if no method found.
   */
  public AbstractFunction findFunction(String className, String methodName)
  {
    QuercusClass cl = findClass(className);

    if (cl == null)
      throw new QuercusRuntimeException(L.l("'{0}' is an unknown class",
                                            className));

    return cl.findFunction(methodName);
  }

  /**
   * Returns the appropriate callback.
   */
  public Callback createCallback(Value value)
  {
    if (value == null || value.isNull())
      return null;

    value = value.toValue();
  
    if (value instanceof Callback) {
      return (Callback) value;
    }
    else if (value instanceof StringValue) {
      // php/1h0o
      if (value.isEmpty())
        return null;

      String s = value.toString();

      int p = s.indexOf("::");

      if (p < 0)
	return new CallbackFunction(this, s);
      else {
	String className = s.substring(0, p);
	String methodName = s.substring(p + 2);
	
        QuercusClass cl = findClass(className);

        if (cl == null)
          throw new IllegalStateException(L.l("can't find class {0}",
                                              className));

        return new CallbackFunction(cl.getFunction(methodName));
      }
    }
    else if (value instanceof ArrayValue) {
      Value obj = value.get(LongValue.ZERO);
      Value name = value.get(LongValue.ONE);

      if (! (name instanceof StringValue))
        throw new IllegalStateException(L.l("unknown callback name {0}", name));

      if (obj instanceof StringValue) {
        QuercusClass cl = findClass(obj.toString());

        if (cl == null)
          throw new IllegalStateException(L.l("can't find class {0}",
                                              obj.toString()));

        return new CallbackFunction(cl.getFunction(name.toString()));
      }
      else {
        return new CallbackObjectMethod(this, obj, name.toString());
      }
    }
    else
      return null;
  }

  /**
   * Evaluates an included file.
   */
  public Value requireOnce(StringValue include)
  {
    return include(getSelfDirectory(), include, true, true);
  }

  /**
   * Evaluates an included file.
   */
  public Value require(StringValue include)
  {
    return include(getSelfDirectory(), include, true, false);
  }

  /**
   * Evaluates an included file.
   */
  public Value include(StringValue include)
  {
    return include(getSelfDirectory(), include, false, false);
  }

  /**
   * Evaluates an included file.
   */
  public Value includeOnce(StringValue include)
  {
    return include(getSelfDirectory(), include, false, true);
  }

  /**
   * Evaluates an included file.
   */
  public Value includeOnce(Path scriptPwd, StringValue include,
			   boolean isRequire)
  {
    return include(scriptPwd, include, isRequire, true);
  }

  /**
   * Evaluates an included file.
   */
  public Value include(Path scriptPwd, StringValue include,
		       boolean isRequire, boolean isOnce)
  {
    try {
      Path pwd = getPwd();

      Path path = lookupInclude(include, pwd, scriptPwd);

      if (path != null) {
      }
      else if (isRequire) {
        error(L.l("'{0}' is not a valid path", include));
        return BooleanValue.FALSE;
      }
      else {
        warning(L.l("'{0}' is not a valid path", include));
        return BooleanValue.FALSE;
      }

      // php/0b2d
      if (! _isAllowUrlInclude && isUrl(path)) {
        String msg = (L.l("not allowed to include url {0}", path.getURL()));
	
        log.warning(dbgId() + msg);
        error(msg);

        return BooleanValue.FALSE;
      }

      QuercusPage page = _includeMap.get(path);
      
      if (page != null && isOnce)
        return BooleanValue.TRUE;
      else if (page == null || page.isModified()) {
        page = _quercus.parse(path);
        
        page.init(this);
        
        page.importDefinitions(this);
        
        _includeMap.put(path, page);
      }

      return page.execute(this);
    } catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }
  
  /*
   * Returns true if this path is likely to be a URL.
   */
  private boolean isUrl(Path path)
  {
    String scheme = path.getScheme();
    
    if ("".equals(scheme)
        || "file".equals(scheme)
        || "memory".equals(scheme))
      return false;
    
    // XXX: too restrictive for filters
    return ! "php".equals(scheme)
           || "php://input".equals(path.toString())
           || path.toString().startsWith("php://filter");
  }

  /**
   * Looks up based on the pwd.
   */
  public Path lookupPwd(Value relPathV)
  {
    if (! relPathV.isset())
      return null;

    StringValue relPath = relPathV.toStringValue();

    if (relPath.length() == 0)
      return null;
    
    Path path = _lookupCache.get(relPath);

    if (path == null) {
      path = getPwd().lookup(relPath.toString());
      _lookupCache.put(relPath, path);
    }

    return path;
  }

  /**
   * Looks up the path.
   */
  public Path lookup(StringValue relPath)
  {
    return lookupInclude(getSelfDirectory(), relPath.toString());
  }

  /**
   * Looks up the path.
   */
  public Path lookupInclude(StringValue relPath)
  {
    return lookupInclude(relPath, getPwd(), getSelfDirectory());
  }

  private Path lookupInclude(StringValue include, Path pwd, Path scriptPwd)
  {
    String includePath = getDefaultIncludePath();
    
    Path path = _quercus.getIncludeCache(include, includePath, pwd, scriptPwd);

    if (path == null) {
      path = lookupIncludeImpl(include, pwd, scriptPwd);

      /*
      if (path == null)
	path = NullPath.NULL;
      */

      if (path != null)
	_quercus.putIncludeCache(include, includePath, pwd, scriptPwd, path);
    }

    if (path == NullPath.NULL)
      path = null;

    _includePath = includePath;
    _includePathIniCount = _iniCount;
    
    return path;
  }
  
  private String getDefaultIncludePath()
  {
    String includePath = _includePath;

    if (_includePathIniCount != _iniCount) {
      includePath = Quercus.INI_INCLUDE_PATH.getAsString(this);
      _includePath = null;
      _includePathList = null;
    }
    
    if (includePath == null)
      includePath = ".";
    
    return includePath;
  }
    
  private Path lookupIncludeImpl(StringValue includeValue,
				 Path pwd,
				 Path scriptPwd)
  {
    String include = includeValue.toString();
    
    // php/0b0g

    Path path = lookupInclude(pwd, include);

    if (path == null) {
      // php/0b0l
      path = lookupInclude(scriptPwd, include);
    }

    if (path == null) {
      // php/0b21
      path = scriptPwd.lookup(include);

      if (! includeExists(path))
	path = null;
    }

    return path;
  }

  /**
   * Looks up the path.
   */
  private Path lookupInclude(Path pwd, String relPath)
  {
    ArrayList<Path> pathList = getIncludePath(pwd);

    for (int i = 0; i < pathList.size(); i++) {
      Path path = pathList.get(i).lookup(relPath);

      if (path.canRead() && ! path.isDirectory()) {
        return path;
      }
    }

    return null;
  }

  private boolean includeExists(Path path)
  {
    if (path.canRead() && ! path.isDirectory())
      return true;
    else if (! getQuercus().isRequireSource())
      return getQuercus().includeExists(path);
    else
      return false;
  }
  /**
   * Returns the include path.
   */
  private ArrayList<Path> getIncludePath(Path pwd)
  {
    String includePath = getDefaultIncludePath();

    if (_includePathList == null) {
      _includePathList = new ArrayList<String>();
      _includePathMap = new HashMap<Path,ArrayList<Path>>();

      int head = 0;
      int tail;

      String pathSeparator = FileModule.PATH_SEPARATOR;
      int length = pathSeparator.length();

      while ((tail = includePath.indexOf(pathSeparator, head)) >= 0) {
        String subpath = includePath.substring(head, tail);
        
        _includePathList.add(subpath);

        head = tail + length;
      }

      String subpath = includePath.substring(head);

      _includePathList.add(subpath);

      _includePath = includePath;
      _includePathIniCount = _iniCount;
    }

    ArrayList<Path> pathList = _includePathMap.get(pwd);

    if (pathList == null) {
      pathList = new ArrayList<Path>();

      for (int i = 0; i < _includePathList.size(); i++) {
	pathList.add(pwd.lookup(_includePathList.get(i)));
      }

      _includePathMap.put(pwd, pathList);
    }

    return pathList;
  }

  /**
   * Sets the include path.
   */
  public String setIncludePath(String path)
  {
    String prevIncludePath = Quercus.INI_INCLUDE_PATH.getAsString(this);

    if (_defaultIncludePath == null)
      _defaultIncludePath = prevIncludePath;

    Quercus.INI_INCLUDE_PATH.set(this, path);
    
    // reset include path cache count
    _includePathIniCount = -1;

    return prevIncludePath;
  }

  /**
   * Restores the default include path.
   */
  public void restoreIncludePath()
  {
    Quercus.INI_INCLUDE_PATH.set(this, _defaultIncludePath);
  }
  
  /**
   * Returns all the included files.
   */
  public ArrayValue getIncludedFiles()
  {
    ArrayValue array = new ArrayValueImpl();

    ArrayList<String> list = new ArrayList<String>();

    for (Path path : _includeMap.keySet()) {
      list.add(path.toString());
    }
      
    Collections.sort(list);
    
    for (String pathName : list) {
      array.put(createString(pathName));
    }
    
    return array;
  }

  /**
   * Handles error suppression.
   */
  public Value suppress(int errorMask, Value value)
  {
    setErrorMask(errorMask);

    return value;
  }

  /**
   * Handles exit/die
   */
  public Value exit(Value msg)
  {
    if (msg.isNull() || msg instanceof LongValue)
      return exit();

    try {
      getOut().print(msg.toString());
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    throw new QuercusExitException(msg.toString());
  }

  /**
   * Handles exit/die
   */
  public Value exit()
  {
    throw new QuercusExitException();
  }

  /**
   * Handles exit/die
   */
  public Value die(String msg)
  {
    try {
      getOut().print(msg);
    } catch (IOException e) {
      log.log(Level.WARNING, e.toString(), e);
    }

    throw new QuercusDieException(msg);
  }

  /**
   * Handles exit/die
   */
  public Value die()
  {
    throw new QuercusDieException();
  }

  /**
   * Handles exit/die
   */
  public Value cast(Class cl, Value value)
  {
    value = value.toValue();

    if (value.isNull())
      return null;
    else if (cl.isAssignableFrom(value.getClass()))
      return value;
    else {
      // php/3cr2
      warning(L.l("{0} ({1}) is not assignable to {2}",
                value, value.getClass().getName(), cl.getName()));

      return null;
    }
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, double a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static long first(long value, Value a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static double first(double value, Value a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static long first(long value, double a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static double first(double value, double a1)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2, Value a3)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2, Value a3,
                            Value a4)
  {
    return value;
  }

  /**
   * Returns the first value
   */
  public static Value first(Value value, Value a1, Value a2, Value a3,
                            Value a4, Value a5)
  {
    return value;
  }

  /**
   * Check for type hinting
   */
  public void checkTypeHint(Value value,
			    String type,
			    String argName,
			    String functionName)
  {
    if (value.isNull()) {
      error(L.l("'{0}' is an unexpected value for arg '{1}' in function '{2}', expected '{3}'",
		value, argName, functionName, type));
    }
  }
      
  /**
   * A fatal runtime error.
   */
  public Value error(String msg)
  {
    return error(B_ERROR, "", msg + getFunctionLocation());
  }

  /**
   * A fatal runtime error.
   */
  public Value error(Location location, String msg)
  {
    return error(B_ERROR, location, msg + getFunctionLocation());
  }
  
  /**
   * A fatal runtime error.
   */
  public Value error(String loc, String msg)
  {
    return error(B_ERROR, loc, msg + getFunctionLocation());
  }

  /**
   * A warning with an exception.
   */
  public Value error(String msg, Throwable e)
  {
    log.log(Level.WARNING, e.toString(), e);

    return error(msg);
  }

  /**
   * A warning with an exception.
   */
  public Value error(Throwable e)
  {
    log.log(Level.WARNING, e.toString(), e);

    return error(e.toString());
  }

  /**
   * A fatal runtime error.
   */
  public QuercusRuntimeException createErrorException(String msg)
    throws QuercusRuntimeException
  {
    return createErrorException(null, msg);
  }

  /**
   * A fatal runtime error.
   */
  public QuercusRuntimeException createErrorException(Location location, String msg)
    throws QuercusRuntimeException
  {
    if (location == null)
      location = getLocation();

    String prefix = location.getMessagePrefix();

    String fullMsg = msg + getFunctionLocation();

    error(B_ERROR, location, fullMsg);

    String exMsg = prefix + fullMsg;

    return new QuercusRuntimeException(fullMsg);
  }

  /**
   * A runtime warning.
   */
  public Value warning(String msg)
  {
    if (log.isLoggable(Level.FINER)) {
      QuercusException e = new QuercusException(msg);

      log.log(Level.FINER, e.toString(), e);
    }

    return error(B_WARNING, "", msg + getFunctionLocation());
  }
  
  /**
   * A runtime warning.
   */
  public Value warning(Location location, String msg)
  {
    if (log.isLoggable(Level.FINER)) {
      QuercusException e = new QuercusException(msg);

      log.log(Level.FINER, e.toString(), e);
    }

    return error(B_WARNING, location, "", msg + getFunctionLocation());
  }

  /**
   * A warning with an exception.
   */
  public Value warning(String msg, Throwable e)
  {
    log.log(Level.FINE, e.toString(), e);

    return warning(msg);
  }
  
  /**
   * A warning with an exception.
   */
  public Value warning(Location location, String msg, Throwable e)
  {
    log.log(Level.FINE, e.toString(), e);

    return warning(location, msg);
  }

  /**
   * A warning with an exception.
   */
  public Value warning(Throwable e)
  {
    return warning(e.toString(), e);
  }
  
  /**
   * A warning with an exception.
   */
  public Value warning(Location location, Throwable e)
  {
    return warning(location, e.toString(), e);
  }
  
  /**
   * A runtime strict warning.
   */
  public Value strict(String msg)
  {
    if (log.isLoggable(Level.FINER)) {
      QuercusException e = new QuercusException(msg);

      log.log(Level.FINER, e.toString(), e);
    }

    return error(B_STRICT, "", msg + getFunctionLocation());
  }

  /**
   * A warning about an invalid argument passed to a function.
   */
  public Value invalidArgument(String name, Object value)
  {
    return warning(L.l("invalid value `{0}' for `{1}'", value, name));
  }

  /**
   * A warning about an deprecated argument passed to a function.
   */
  public Value deprecatedArgument(String name)
  {
    return strict(L.l("argument `{1}' is deprecated", name));
  }

  /**
   * A notice.
   */
  public Value notice(String msg)
  {
    return error(B_NOTICE, "", msg + getFunctionLocation());
  }

  /**
   * A notice with an exception.
   */
  public Value notice(String msg, Throwable e)
  {
    log.log(Level.FINE, e.toString(), e);

    return notice(msg);
  }

  /**
   * A stub notice.
   */
  public Value stub(String msg)
  {
    if (log.isLoggable(Level.FINE))
      log.fine(getLocation().getMessagePrefix() + msg);

    return NullValue.NULL;
  }

  public static Value nullAsFalse(Value value)
  {
    return value == null || value.isNull() ? BooleanValue.FALSE : value;
  }

  /**
   * A parse error
   */
  public Value parse(String msg)
    throws Exception
  {
    return error(B_PARSE, "", msg);
  }

  /**
   * A parse error
   */
  public Value compileError(String msg)
  {
    return error(B_COMPILE_ERROR, "", msg);
  }

  /**
   * A parse warning
   */
  public Value compileWarning(String msg)
  {
    return error(B_COMPILE_WARNING, "", msg);
  }

  /**
   * Returns the error mask.
   */
  public int getErrorMask()
  {
    return _errorMask;
  }

  /**
   * Sets the error mask.
   */
  public int setErrorMask(int mask)
  {
    int oldMask = _errorMask;

    _errorMask = mask;

    return oldMask;
  }

  /**
   * Sets an error handler
   */
  public void setErrorHandler(int mask, Callback fun)
  {
    for (int i = 0; i < _errorHandlers.length; i++)
      _prevErrorHandlers[i] = _errorHandlers[i];

    if ((mask & E_ERROR) != 0)
      _errorHandlers[B_ERROR] = fun;

    if ((mask & E_WARNING) != 0)
      _errorHandlers[B_WARNING] = fun;

    if ((mask & E_PARSE) != 0)
      _errorHandlers[B_PARSE] = fun;

    if ((mask & E_NOTICE) != 0)
      _errorHandlers[B_NOTICE] = fun;

    if ((mask & E_USER_ERROR) != 0)
      _errorHandlers[B_USER_ERROR] = fun;

    if ((mask & E_USER_WARNING) != 0)
      _errorHandlers[B_USER_WARNING] = fun;

    if ((mask & E_USER_NOTICE) != 0)
      _errorHandlers[B_USER_NOTICE] = fun;

    if ((mask & E_STRICT) != 0)
      _errorHandlers[B_STRICT] = fun;
    
    if ((mask & E_RECOVERABLE_ERROR) != 0)
      _errorHandlers[B_RECOVERABLE_ERROR] = fun;
  }

  /**
   * Sets an error handler
   */
  public void restoreErrorHandler()
  {
    for (int i = 0; i < _errorHandlers.length; i++)
      _errorHandlers[i] = _prevErrorHandlers[i];
  }
  
  /**
   * Gets the exception handler
   */
  public Callback getExceptionHandler()
  {
    return _exceptionHandler;
  }
  
  /**
   * Sets an exception handler
   */
  public Value setExceptionHandler(Callback fun)
  {
    _prevExceptionHandler = _exceptionHandler;
    
    _exceptionHandler = fun;

    if (_prevExceptionHandler != null)
      return _prevExceptionHandler.toStringValue();
    else
      return NullValue.NULL;
  }
  
  /**
   * Restore an exception handler
   */
  public void restoreExceptionHandler()
  {
    _exceptionHandler = _prevExceptionHandler;
  }

  /*
   * Writes an error.
   */
  public Value error(int code, String locString, String msg)
  {
    return error(code, null, locString, msg);
  }
  
  /*
   * Writes an error.
   */
  public Value error(int code, Location location, String msg)
  {
    return error(code, location, "", msg);
  }
  
  /**
   * Writes an error.
   */
  public Value error(int code, Location location, String loc, String msg)
  {
    int mask = 1 << code;

    if (log.isLoggable(Level.FINEST)) {
      QuercusException e = new QuercusException(loc + msg);

      log.log(Level.FINEST, e.toString(), e);
    }
    else if (log.isLoggable(Level.FINE))
      log.fine(this + " " + loc + msg);

    if (code >= 0 && code < _errorHandlers.length
	&& _errorHandlers[code] != null) {
      Callback handler = _errorHandlers[code];

      try {
        _errorHandlers[code] = null;

        Value fileNameV = NullValue.NULL;

	if (location == null)
	  location = getLocation();

        String fileName = location.getFileName();

        if (fileName != null)
          fileNameV = createString(fileName);

        Value lineV = NullValue.NULL;
        int line = location.getLineNumber();
        if (line > 0)
          lineV = new LongValue(line);

        Value context = NullValue.NULL;

        handler.call(this, new LongValue(mask), createString(msg),
                     fileNameV, lineV, context);

        return NullValue.NULL;
      }
      catch (RuntimeException e) {
        throw e;
      }
      catch (Throwable e) {
        throw new RuntimeException(e);
      }
      finally {
        _errorHandlers[code] = handler;
      }
    }

    if ((_errorMask & mask) != 0) {
      try {
	String fullMsg = (getLocationPrefix(location, loc)
			  + getCodeName(mask) + msg);

	if (getIniBoolean("track_errors"))
	  setGlobalValue("php_errormsg", createString(fullMsg));
	
	if ("stderr".equals(getIniString("display_errors")))
	  System.err.println(fullMsg);
	else if (getIniBoolean("display_errors"))
	  getOut().println(fullMsg);

	if (getIniBoolean("log_errors"))
	  log.info(fullMsg);
      }
      catch (IOException e) {
        log.log(Level.FINE, e.toString(), e);
      }
    }

    if ((mask & (E_ERROR | E_CORE_ERROR | E_COMPILE_ERROR | E_USER_ERROR)) != 0)
    {
      String locPrefix = getLocationPrefix(location, loc);
      
      if (! "".equals(locPrefix)) {
	/*
        throw new QuercusLineExitException(getLocation() +
                                              getCodeName(mask) +
                                              msg);
	*/
        throw new QuercusErrorException(locPrefix
					+ getCodeName(mask)
					+ msg);
      }
      else
        throw new QuercusErrorException(msg);
    }

    return NullValue.NULL;
  }

  /**
   * Returns the displayable location prefix.  This may be slow
   * for compiled-mode because of the need to match line numbers.
   */
  private String getLocationPrefix(Location location, String loc)
  {
    if (loc != null && ! "".equals(loc))
      return loc;
    
    if (location == null)
      location = getLocation();

    return location.getMessagePrefix();
  }

  /**
   * Returns the error code name.
   */
  private String getCodeName(int code)
  {
    switch (code) {
    case E_ERROR:
      return "Fatal Error: ";
    case E_WARNING:
      return "Warning: ";
    case E_PARSE:
      return "Parse Error: ";
    case E_NOTICE:
      return "Notice: ";
    case E_CORE_ERROR:
      return "Fatal Error: ";
    case E_CORE_WARNING:
      return "Warning: ";
    case E_COMPILE_ERROR:
      return "Fatal Error: ";
    case E_COMPILE_WARNING:
      return "Warning : ";
    case E_USER_ERROR:
      return "Fatal Error: ";
    case E_USER_WARNING:
      return "Warning: ";
    case E_USER_NOTICE:
      return "Notice: ";
    case E_STRICT:
      return "Notice: ";
    case E_RECOVERABLE_ERROR:
      return "Error: ";

    default:
      return String.valueOf("ErrorCode(" + code + ")");
    }
  }

  /**
   * Returns the source of an error line.
   */
  public static String []getSourceLine(Path path, int sourceLine, int length)
  {
    if (path == null)
      return null;
    else if (path instanceof NullPath) {
      // for QuercusScriptEngine.eval() where only a Reader is passed in
      // XXX: not too pretty
      return null;
    }

    ReadStream is = null;

    try {
      is = path.openRead();
      int ch;
      boolean hasCr = false;
      int line = 1;

      while (line < sourceLine) {
        ch = is.read();

        if (ch < 0)
          return null;
        else if (ch == '\r') {
          hasCr = true;
          line++;
        }
        else if (ch == '\n') {
          if (! hasCr)
            line++;
          hasCr = false;
        }
        else
          hasCr = false;
      }

      String []result = new String[length];

      int i = 0;
      StringBuilder sb = new StringBuilder();
      while (i < length && (ch = is.read()) > 0) {
        if (ch == '\n' && hasCr) {
          hasCr = false;
          continue;
        }
        else if (ch == '\r') {
          hasCr = true;
          result[i++] = sb.toString();
          sb.setLength(0);
        }
        else if (ch == '\n') {
          hasCr = false;
          result[i++] = sb.toString();
          sb.setLength(0);
        }
        else {
          hasCr = false;
          sb.append((char) ch);
        }
      }

      if (i < length)
        result[i] = sb.toString();

      return result;
    }
    catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
    }
    finally {
      if (is != null)
	is.close();
    }

    return null;
  }

  /**
   * Returns the current execution location.
   *
   * Use with care, for compiled code this can be a relatively expensive
   * operation.
   */
  public Location getLocation()
  {
    Expr call = peekCall(0);

    if (call != null)
      return call.getLocation();

    return Location.UNKNOWN;
  }

  public int getSourceLine(String className, int javaLine)
  {
    return javaLine;
  }

  /**
   * Returns the current function.
   */
  public String getFunctionLocation()
  {
    // XXX: need to work with compiled code, too
    Expr call = peekCall(0);

    if (call != null)
      return call.getFunctionLocation();
    else
      return "";
  }

  /**
   * Converts a boolean to the boolean value
   */
  public static Value toValue(boolean value)
  {
    return value ? BooleanValue.TRUE : BooleanValue.FALSE;
  }

  /**
   * Converts a boolean to the boolean value
   */
  public static Value toValue(long value)
  {
    return new LongValue(value);
  }

  /**
   * Converts to a variable
   */
  public static Var toVar(Value value)
  {
    if (value instanceof Var)
      return (Var) value;
    else if (value == null)
      return new Var();
    else
      return new Var(value);
  }

  /**
   * Sets a vield variable
   */
  public static Value setFieldVar(Value oldValue, Value value)
  {
    if (value instanceof Var)
      return value;
    else if (oldValue instanceof Var)
      return new Var(value);
    else
      return value;
  }

  /**
   * Sets a reference
   */
  public static Value setRef(Value oldValue, Value value)
  {
    // php/3243
    if (value instanceof Var)
      return value;
    /*
    else if (oldValue instanceof Var) {
      oldValue.set(value);

      return oldValue;
    }
    */
    else
      return new Var(value);
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1)
  {
    return a1;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1, Value a2)
  {
    return a2;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1, Value a2, Value a3)
  {
    return a3;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(Value a0, Value a1, Value a2, Value a3, Value a4)
  {
    return a4;
  }

  // long comma

  /**
   * Returns the last value.
   */
  public static long comma(Value a0, long a1)
  {
    return a1;
  }

  /**
   * Returns the last value.
   */
  public static long comma(long a0, long a1)
  {
    return a1;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(long a0, Value a1)
  {
    return a1;
  }

  //
  // comma
  //

  /**
   * Returns the last value.
   */
  public static double comma(Value a0, double a1)
  {
    return a1;
  }

  /**
   * Returns the last value.
   */
  public static double comma(double a0, double a1)
  {
    return a1;
  }

  /**
   * Returns the last value.
   */
  public static Value comma(double a0, Value a1)
  {
    return a1;
  }

  public String toString()
  {
    return "Env[]";
  }

  /**
   * Returns ifNull if condition.isNull(), otherwise returns ifNotNull.
   */
  public Value ifNull(Value condition, Value ifNull, Value ifNotNull)
  {
    return condition.isNull() ? ifNull : ifNotNull;
  }

  /**
   * Returns the locale info.
   */
  public LocaleInfo getLocaleInfo()
  {
    if (_locale == null)
      _locale = new LocaleInfo();

    return _locale;
  }

  /**
   * Registers a shutdown function.
   */
  public void addShutdown(Callback callback, Value []args)
  {
    _shutdownList.add(new Shutdown(callback, args));
  }

  // XXX: hack until can clean up
  public void setGzStream(Object obj)
  {
    _gzStream = obj;
  }

  // XXX: hack until can clean up
  public Object getGzStream()
  {
    return _gzStream;
  }

  /**
   * Called when the Env is no longer needed.
   */
  public void close()
  {
    try {
      // php/1l0t
      // output buffers callbacks may throw an exception
      while (_outputBuffer != null) {
        popOutputBuffer();
      }
    }
    //catch (Exception e) {
      //throw new RuntimeException(e);
    //}
    finally {
      cleanup();
    }
  }

  private void cleanup()
  {
    // cleanup is in reverse order of creation
    for (int i = _objCleanupList.size() - 1; i >= 0; i--) {
      ObjectExtValue objCleanup = _objCleanupList.get(i);
      try {
	if (objCleanup != null)
	  objCleanup.cleanup(this);
      }
      catch (Throwable e) {
	log.log(Level.FINER, e.toString(), e);
      }
    }
      
    try {
      for (int i = 0; i < _shutdownList.size(); i++)
	_shutdownList.get(i).call(this);
    }
    catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }

    try {
      sessionWriteClose();
    } catch (Throwable e) {
      log.log(Level.FINE, e.toString(), e);
    }

    ArrayList<EnvCleanup> cleanupList
      = new ArrayList<EnvCleanup>(_cleanupList);

    // cleanup is in reverse order of creation
    for (int i = cleanupList.size() - 1; i >= 0; i--) {
      EnvCleanup envCleanup = cleanupList.get(i);
      try {
	if (envCleanup != null)
	  envCleanup.cleanup();
      }
      catch (Throwable e) {
	log.log(Level.FINER, e.toString(), e);
      }
    }

    _threadEnv.set(_oldThreadEnv);

    for (int i = 0; _removePaths != null && i < _removePaths.size(); i++) {
      Path path = _removePaths.get(i);

      try {
	path.remove();
      }
      catch (IOException e) {
	log.log(Level.FINER, e.toString(), e);
      }
    }

    AbstractFunction []fun = _fun;
    _fun = null;
    if (fun != null) {
      for (int i = 0; i < fun.length; i++) {
        fun[i] = null;
      }
      
      _freeFunList.free(fun);
    }

    ClassDef []classDef = _classDef;
    _classDef = null;
    if (classDef != null) {
      // php/0b3b
      for (int i = 0; i < classDef.length; i++) {
        classDef[i] = null;
      }
      
      _freeClassDefList.free(classDef);
    }

    QuercusClass []qClass = _qClass;
    _qClass = null;
    if (qClass != null) {
      for (int i = 0; i < qClass.length; i++) {
        qClass[i] = null;
      }
      
      _freeClassList.free(qClass);
    }
  }

  public void sessionWriteClose()
  {
    SessionArrayValue session = _session;

    _session = null;

    if (session != null) {
      SessionCallback callback = getSessionCallback();

      if (callback != null) {
        String value;
        
        // php/1k6e
        if (session.getSize() > 0)
          value = VariableModule.serialize(this, session.getArray());
        else
          value = "";

        callback.write(this, session.getId(), value);

        callback.close(this);
      }
      else {
        _quercus.saveSession(this, session);

        Value sessionCopy = session.copy(this);
	
        setGlobalValue("_SESSION", sessionCopy);
        setGlobalValue("HTTP_SESSION_VARS", sessionCopy);
      }
    }
  }

  public String dbgId()
  {
    return "Quercus[" + _selfPath + "] ";
  }
  
  static class FieldGetEntry {
    private final String _className;
    private final StringValue _fieldName;
    
    FieldGetEntry(String className, StringValue fieldName)
    {
      _className = className;
      _fieldName = fieldName;
    }
    
    public boolean equals(Object o)
    {
      if (! (o instanceof FieldGetEntry))
        return false;
      
      FieldGetEntry entry = (FieldGetEntry) o;
      
      return entry._className.equals(_className)
             && entry._fieldName.equals(_fieldName);
    }
  }

  static class ClassKey {
    private final WeakReference<ClassDef> _defRef;
    private final WeakReference<QuercusClass> _parentRef;

    private final int _hash;

    ClassKey(ClassDef def, QuercusClass parent)
    {
      _defRef = new WeakReference<ClassDef>(def);

      if (parent != null)
	_parentRef = new WeakReference<QuercusClass>(parent);
      else
	_parentRef = null;

      // hash needs to be precalculated so losing a weak references won't
      // change the result
      
      int hash = 37;

      if (def != null)
	hash = 65521 * hash + def.hashCode();

      if (parent != null)
	hash = 65521 * hash + parent.hashCode();

      _hash = hash;
    }

    public int hashCode()
    {
      return _hash;
    }

    public boolean equals(Object o)
    {
      ClassKey key = (ClassKey) o;

      ClassDef aDef = _defRef.get();
      ClassDef bDef = key._defRef.get();

      if (aDef != bDef)
	return false;

      if (_parentRef == key._parentRef)
	return true;
      
      else if (_parentRef == null || key._parentRef == null)
	return false;

      QuercusClass aParent = _parentRef.get();
      QuercusClass bParent = key._parentRef.get();

      return (aParent != null && aParent.equals(bParent));
    }

    @Override
    public String toString()
    {
      return (getClass().getSimpleName()
	      + "[" + _defRef.get() + ","
	      + (_parentRef != null ? _parentRef.get() : null) + "]");
    }
  }

  static {
    SPECIAL_VARS.put("GLOBALS", _GLOBAL);
    SPECIAL_VARS.put("_SERVER", _SERVER);
    SPECIAL_VARS.put("_GET", _GET);
    SPECIAL_VARS.put("_POST", _POST);
    SPECIAL_VARS.put("_FILES", _FILES);
    SPECIAL_VARS.put("_REQUEST", _REQUEST);
    SPECIAL_VARS.put("_COOKIE", _COOKIE);
    SPECIAL_VARS.put("_SESSION", _SESSION);
    SPECIAL_VARS.put("_ENV", _ENV);
    SPECIAL_VARS.put("HTTP_GET_VARS", HTTP_GET_VARS);
    SPECIAL_VARS.put("HTTP_POST_VARS", HTTP_POST_VARS);
    SPECIAL_VARS.put("HTTP_POST_FILES", HTTP_POST_FILES);
    SPECIAL_VARS.put("HTTP_COOKIE_VARS", HTTP_COOKIE_VARS);
    SPECIAL_VARS.put("HTTP_SERVER_VARS", HTTP_SERVER_VARS);
    SPECIAL_VARS.put("PHP_SELF", PHP_SELF);
    SPECIAL_VARS.put("HTTP_RAW_POST_DATA", HTTP_RAW_POST_DATA);
  }
}
