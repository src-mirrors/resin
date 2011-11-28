/*
 * Copyright (c) 1998-2011 Caucho Technology -- all rights reserved
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

package com.caucho.boot;

import com.caucho.VersionFactory;
import com.caucho.config.ConfigException;
import com.caucho.license.*;
import com.caucho.server.resin.ResinELContext;
import com.caucho.server.util.CauchoSystem;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

class WatchdogArgs
{
  private static L10N _L;
  private static final Logger log
    = Logger.getLogger(WatchdogArgs.class.getName());
  
  private static final HashMap<String,BootCommand> _commandMap
    = new HashMap<String,BootCommand>();

  private Path _javaHome;
  private Path _resinHome;
  private Path _rootDirectory;
  private Path _dataDirectory;
  private String[] _argv;
  private Path _resinConf;
  private Path _logDirectory;
  private String _serverId = null;
  private String _clusterId;
  private int _watchdogPort;
  private boolean _isVerbose;
  private boolean _isHelp;
  private BootCommand _command;

  private ArrayList<String> _tailArgs = new ArrayList<String>();

  private boolean _isDynamicServer;
  private String _dynamicCluster;
  private String _dynamicAddress;
  private int _dynamicPort;

  private boolean _is64bit;

  WatchdogArgs(String[] argv)
  {
    this(argv, true);
  }

  WatchdogArgs(String[] argv, boolean isTop)
  {
    String logLevel = System.getProperty("resin.log.level");

    if (isTop)
      setLogLevel(logLevel);

    _resinHome = calculateResinHome();
    _rootDirectory = calculateResinRoot(_resinHome);

    _javaHome = Vfs.lookup(System.getProperty("java.home"));

    _argv = fillArgv(argv);

    _resinConf = _resinHome.lookup("conf/resin.conf");
    if (! _resinConf.canRead())
      _resinConf = _resinHome.lookup("conf/resin.xml");

    _is64bit = CauchoSystem.is64Bit();

    parseCommandLine(_argv);
  }

  String []getRawArgv()
  {
    return _argv;
  }

  Path getJavaHome()
  {
    return _javaHome;
  }

  Path getResinHome()
  {
    return _resinHome;
  }

  Path getRootDirectory()
  {
    return _rootDirectory;
  }

  Path getDataDirectory()
  {
    return _dataDirectory;
  }

  Path getLogDirectory()
  {
    return _logDirectory;
  }

  Path getResinConf()
  {
    return _resinConf;
  }

  String getServerId()
  {
    return _serverId;
  }
  
  void setDynamicServerId(String serverId)
  {
    if (serverId != null)
      _serverId = serverId;
  }

  String getClusterId()
  {
    return _clusterId;
  }

  String[] getArgv()
  {
    return _argv;
  }

  boolean isDynamicServer()
  {
    return _isDynamicServer;
  }

  String getDynamicCluster()
  {
    return _dynamicCluster;
  }

  String getDynamicAddress()
  {
    if (_dynamicAddress != null)
      return _dynamicAddress;
    else {
      try {
        return InetAddress.getLocalHost().getHostAddress();
      } catch (Exception e) {
        return null;
      }
    }
  }

  int getDynamicPort()
  {
    if (_dynamicPort > 0)
      return _dynamicPort;
    else
      return 6830;
  }

  String getDynamicServerId()
  {
    if (_serverId != null)
      return _serverId;
    else
      return "dyn-" + getDynamicAddress() + ":" + getDynamicPort();
  }

  boolean isVerbose()
  {
    return _isVerbose;
  }

  void setWatchdogPort(int port)
  {
    _watchdogPort = port;
  }

  int getWatchdogPort()
  {
    return _watchdogPort;
  }

  void setResinHome(Path resinHome)
  {
    _resinHome = resinHome;
  }

  boolean is64Bit()
  {
    return _is64bit;
  }

  BootCommand getCommand()
  {
    return _command;
  }

  public ArrayList<String> getTailArgs()
  {
    return _tailArgs;
  }

  public String getArg(String arg)
  {
    for (int i = 0; i + 1 < _argv.length; i++) {
      if (_argv[i].equals(arg)
          || _argv[i].equals("-" + arg))
        return _argv[i + 1];
    }

    return null;
  }

  public String getArgFlag(String arg)
  {
    for (int i = 0; i < _argv.length; i++) {
      if (_argv[i].equals(arg)
          || _argv[i].equals("-" + arg))
        return _argv[i];
      
      else if (_argv[i].startsWith(arg + "=")) {
        return _argv[i].substring(arg.length() + 1);
      }
      else if (_argv[i].startsWith("-" + arg + "=")) {
        return _argv[i].substring(arg.length() + 2);
      }
    }

    return null;
  }
  
  public boolean getArgBoolean(String arg, boolean defaultValue)
  {
    String value = getArgFlag(arg);

    if (value == null)
      return defaultValue;
    
    if ("no".equals(value) || "false".equals(value))
      return false;
    else
      return true;
  }
  
  public int getArgInt(String arg, int defaultValue)
  {
    String value = getArg(arg);

    if (value == null)
      return defaultValue;
    
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      NumberFormatException e1
        = new NumberFormatException(L().l("{0} argument is not a number '{1}'",
                                        arg, value));
      e1.setStackTrace(e.getStackTrace());

      throw e;
    }
  }

  public boolean hasOption(String option)
  {
    for (String arg : _argv) {
      if (option.equals(arg))
        return true;
    }

    return false;
  }

  public ResinELContext getELContext()
  {
    return new ResinBootELContext();
  }

  /**
   * finds first argument that follows no dash prefixed token
   * @return
   */
  public String getDefaultArg()
  {
    ArrayList<String> tailArgs = getTailArgs();

    for (int i = 0; i < tailArgs.size(); i++) {
      String arg = tailArgs.get(i);

      if (arg.startsWith("-")) {
        i++;
        continue;
      }

      return arg;
    }

    return null;
  }

  /**
   * returns all trailing following no dash prefixed token args
   */
  public String []getTrailingArgs(Set<String> options)
  {
    LinkedList<String> result = new LinkedList<String>();
    ArrayList<String> tailArgs = getTailArgs();

    for (int i = tailArgs.size() - 1; i >= 0; i--) {
      String arg = tailArgs.get(i);

      if (! arg.startsWith("-")) {
        result.addFirst(arg);
      }
      else if (options.contains(arg)) {
        break;
      }
      else if (! result.isEmpty()) {
        result.removeFirst();
      }
    }

    return result.toArray(new String[result.size()]);
  }

  public boolean isHelp()
  {
    return _isHelp;
  }

  private void setLogLevel(String levelName)
  {
    Level level = Level.INFO;

    if ("off".equals(levelName))
      level = Level.OFF;
    else if ("all".equals(levelName))
      level = Level.ALL;
    else if ("severe".equals(levelName))
      level = Level.SEVERE;
    else if ("warning".equals(levelName))
      level = Level.WARNING;
    else if ("info".equals(levelName))
      level = Level.INFO;
    else if ("config".equals(levelName))
      level = Level.CONFIG;
    else if ("fine".equals(levelName))
      level = Level.FINE;
    else if ("finer".equals(levelName))
      level = Level.FINER;
    else if ("finest".equals(levelName))
      level = Level.FINEST;

    Logger.getLogger("").setLevel(level);
  }

  private void parseCommandLine(String[] argv)
  {
    String resinConf = null;

    for (int i = 0; i < argv.length; i++) {
      String arg = argv[i];

      if ("-conf".equals(arg) || "--conf".equals(arg)) {
        resinConf = argv[i + 1];
        i++;
      }
      else if ("-join-cluster".equals(arg)
               || "--join-cluster".equals(arg)) {
        _isDynamicServer = true;
        _dynamicCluster = argv[i + 1];

        i++;
      }
      else if ("-fine".equals(arg) || "--fine".equals(arg)) {
        _isVerbose = true;
        Logger.getLogger("").setLevel(Level.FINE);
      }
      else if ("-finer".equals(arg) || "--finer".equals(arg)) {
        _isVerbose = true;
        Logger.getLogger("").setLevel(Level.FINER);
      }
      else if ("-log-directory".equals(arg) || "--log-directory".equals(arg)) {
        _logDirectory = _rootDirectory.lookup(argv[i + 1]);
        i++;
      }
      else if ("-resin-home".equals(arg) || "--resin-home".equals(arg)) {
        _resinHome = Vfs.lookup(argv[i + 1]);
        argv[i + 1] = _resinHome.getFullPath();
        i++;
      }
      else if ("-root-directory".equals(arg) || "--root-directory".equals(arg)) {
        _rootDirectory = Vfs.lookup(argv[i + 1]);
        argv[i + 1] = _rootDirectory.getFullPath();
        i++;
      }
      else if ("-data-directory".equals(arg) || "--data-directory".equals(arg)) {
        _dataDirectory = Vfs.lookup(argv[i + 1]);
        argv[i + 1] = _dataDirectory.getFullPath();
        i++;
      }
      else if ("-server".equals(arg) || "--server".equals(arg)) {
        _serverId = argv[i + 1];

        i++;
      }
      else if ("-cluster".equals(arg) || "--cluster".equals(arg)) {
        _clusterId = argv[i + 1];
        i++;
      }
      else if ("-server-root".equals(arg) || "--server-root".equals(arg)) {
        _rootDirectory = Vfs.lookup(argv[i + 1]);
        argv[i + 1] = _rootDirectory.getFullPath();
        i++;
      }
      else if ("-stage".equals(arg) || "--stage".equals(arg)) {
        // skip stage
        i++;
      }
      else if ("-preview".equals(arg) || "--preview".equals(arg)) {
        // pass to server
      }
      else if ("-watchdog-port".equals(arg) || "--watchdog-port".equals(arg)) {
        _watchdogPort = Integer.parseInt(argv[i + 1]);
        i++;
      }
      else if (arg.startsWith("-J")
               || arg.startsWith("-D")
               || arg.startsWith("-X")) {
      }
      else if (arg.equals("-d64")) {
        _is64bit = true;
      }
      else if (arg.equals("-d32")) {
        _is64bit = false;
      }
      else if ("-debug-port".equals(arg) || "--debug-port".equals(arg)) {
        i++;
      }
      else if ("-jmx-port".equals(arg) || "--jmx-port".equals(arg)) {
        i++;
      }
      else if ("--dump-heap-on-exit".equals(arg)) {
      }
      else if ("-verbose".equals(arg) || "--verbose".equals(arg)) {
        _isVerbose = true;
        Logger.getLogger("").setLevel(Level.CONFIG);
      }
      else if ("help".equals(arg)) {
        _isHelp = true;
      }
      else if ("version".equals(arg)) {
        System.out.println(VersionFactory.getFullVersion());
        System.exit(0);
      }
      else if (_commandMap.get(arg) != null) {
        _command = _commandMap.get(arg);
      }
      else if (_command != null) {
        _tailArgs.add(arg);
      }
      else if (_isHelp) {
      }
/*
      else {
        System.out.println(L().l("unknown argument '{0}'", argv[i]));
        System.out.println();
        usage();
        System.exit(1);
      }
*/  //#4605 (support before / after command option placement)
    }

    if (_isHelp && _command == null) {
      usage();

      System.exit(1);
    }
    else if (_command == null) {
      System.out.println(L().l("Resin requires a command:{0}",
                               getCommandList()));
      System.exit(1);
    }

    if (resinConf != null) {
      _resinConf = Vfs.getPwd().lookup(resinConf);

      if (! _resinConf.exists() && _rootDirectory != null)
        _resinConf = _rootDirectory.lookup(resinConf);

      if (! _resinConf.exists() && _resinHome != null)
        _resinConf = _resinHome.lookup(resinConf);

      if (! _resinConf.exists())
        throw new ConfigException(L().l("Resin/{0} can't find configuration file '{1}'", VersionFactory.getVersion(), _resinConf.getNativePath()));
    }
  }

  private static void usage()
  {
    System.err.println(L().l("usage: bin/resin.sh [-options] <command> [values]"));
    System.err.println(L().l("       bin/resin.sh help <command>"));
    System.err.println(L().l(""));
    System.err.println(L().l("where command is one of:"));
    System.err.println(getCommandList());
  }

  private static String getCommandList()
  {
    StringBuilder sb = new StringBuilder();
    
    ArrayList<BootCommand> commands = new ArrayList<BootCommand>();
    commands.addAll(_commandMap.values());
    
    Collections.sort(commands, new CommandNameComparator());
    
    BootCommand lastCommand = null;
    
    for (BootCommand command : commands) {
      if (lastCommand != null && lastCommand.getClass() == command.getClass())
        continue;
      
      sb.append("\n  ");
      sb.append(command.getName());
      sb.append(" - ");
      sb.append(command.getDescription());
      if (command.isProOnly())
        sb.append(" (Resin-Pro)");
      
      lastCommand = command;
    }
    
    sb.append("\n  help <command> - prints command usage message");
    sb.append("\n  version - prints version");
    
    return sb.toString();
  }

  private String []fillArgv(String []argv)
  {
    ArrayList<String> args = new ArrayList<String>();

    try {
      MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
      ObjectName name = new ObjectName("java.lang:type=Runtime");

      String []jvmArgs
        = (String []) mbeanServer.getAttribute(name, "InputArguments");

      if (jvmArgs != null) {
        for (int i = 0; i < jvmArgs.length; i++) {
          String arg = jvmArgs[i];

          if (args.contains(arg))
            continue;

          if (arg.startsWith("-Djava.class.path=")) {
            // IBM JDK
          }
          else if (arg.startsWith("-D")) {
            int eqlSignIdx = arg.indexOf('=');
            if (eqlSignIdx == -1) {
              args.add("-J" + arg);
            } else {
              String key = arg.substring(2, eqlSignIdx);
              String value = System.getProperty(key);

              if (value == null)
                value = "";

              args.add("-J-D" + key + "=" + value);
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    for (int i = 0; i < argv.length; i++)
      args.add(argv[i]);

    argv = new String[args.size()];

    args.toArray(argv);

    return argv;
  }

  private static L10N L()
  {
    if (_L == null)
      _L = new L10N(WatchdogArgs.class);

    return _L;
  }

  //
  // Utility static methods
  //

  static Path calculateResinHome()
  {
    String resinHome = System.getProperty("resin.home");

    if (resinHome != null) {
      return Vfs.lookup(resinHome);
    }

    // find the resin.jar as described by the classpath
    // this may differ from the value given by getURL() because of
    // symbolic links
    String classPath = System.getProperty("java.class.path");

    if (classPath.indexOf("resin.jar") >= 0) {
      int q = classPath.indexOf("resin.jar") + "resin.jar".length();
      int p = classPath.lastIndexOf(File.pathSeparatorChar, q - 1);

      String resinJar;

      if (p >= 0)
        resinJar = classPath.substring(p + 1, q);
      else
        resinJar = classPath.substring(0, q);

      return Vfs.lookup(resinJar).lookup("../..");
    }

    ClassLoader loader = ClassLoader.getSystemClassLoader();

    URL url = loader.getResource("com/caucho/boot/ResinBoot.class");

    String path = url.toString();

    if (! path.startsWith("jar:"))
      throw new RuntimeException(L().l("Resin/{0}: can't find jar for ResinBoot in {1}",
                                 VersionFactory.getVersion(), path));

    int p = path.indexOf(':');
    int q = path.indexOf('!');

    path = path.substring(p + 1, q);

    Path pwd = Vfs.lookup(path).getParent().getParent();

    return pwd;
  }

  static Path calculateResinRoot(Path resinHome)
  {
    String resinRoot = System.getProperty("resin.root");

    if (resinRoot != null)
      return Vfs.lookup(resinRoot);

    resinRoot = System.getProperty("server.root");

    if (resinRoot != null)
      return Vfs.lookup(resinRoot);

    return resinHome;
  }

  static String calculateClassPath(Path resinHome)
    throws IOException
  {
    ArrayList<String> classPath = new ArrayList<String>();

    return calculateClassPath(classPath, resinHome);
  }

  static String calculateClassPath(ArrayList<String> classPath,
                                   Path resinHome)
    throws IOException
  {
    String oldClassPath = System.getProperty("java.class.path");
    if (oldClassPath != null) {
      for (String item : oldClassPath.split("[" + File.pathSeparatorChar + "]")) {
        addClassPath(classPath, item);
      }
    }

    oldClassPath = System.getenv("CLASSPATH");
    if (oldClassPath != null) {
      for (String item : oldClassPath.split("[" + File.pathSeparatorChar + "]")) {
        addClassPath(classPath, item);
      }
    }

    Path javaHome = Vfs.lookup(System.getProperty("java.home"));

    if (javaHome.lookup("lib/tools.jar").canRead())
      addClassPath(classPath, javaHome.lookup("lib/tools.jar").getNativePath());
    else if (javaHome.getTail().startsWith("jre")) {
      String tail = javaHome.getTail();
      tail = "jdk" + tail.substring(3);
      Path jdkHome = javaHome.getParent().lookup(tail);

      if (jdkHome.lookup("lib/tools.jar").canRead())
        addClassPath(classPath, jdkHome.lookup("lib/tools.jar").getNativePath());
    }

    if (javaHome.lookup("../lib/tools.jar").canRead())
      addClassPath(classPath, javaHome.lookup("../lib/tools.jar").getNativePath());

    Path resinLib = resinHome.lookup("lib");

    if (resinLib.lookup("pro.jar").canRead())
      addClassPath(classPath, resinLib.lookup("pro.jar").getNativePath());
    addClassPath(classPath, resinLib.lookup("resin.jar").getNativePath());
    //    addClassPath(classPath, resinLib.lookup("jaxrpc-15.jar").getNativePath());

    String []list = resinLib.list();

    for (int i = 0; i < list.length; i++) {
      if (! list[i].endsWith(".jar"))
        continue;

      Path item = resinLib.lookup(list[i]);

      String pathName = item.getNativePath();

      if (! classPath.contains(pathName))
        addClassPath(classPath, pathName);
    }

    String cp = "";

    for (int i = 0; i < classPath.size(); i++) {
      if (! "".equals(cp))
        cp += File.pathSeparatorChar;

      cp += classPath.get(i);
    }

    return cp;
  }

  private static void addClassPath(ArrayList<String> cp, String item)
  {
    if (! cp.contains(item))
      cp.add(item);
  }

  public class ResinBootELContext
    extends ResinELContext
  {
    private boolean _isLicenseCheck;
    private boolean _isResinProfessional;

    @Override
    public Path getResinHome()
    {
      return WatchdogArgs.this.getResinHome();
    }

    @Override
    public Path getRootDirectory()
    {
      return WatchdogArgs.this.getRootDirectory();
    }

    @Override
    public Path getLogDirectory()
    {
      return WatchdogArgs.this.getLogDirectory();
    }

    @Override
    public Path getResinConf()
    {
      return WatchdogArgs.this.getResinConf();
    }

    @Override
    public String getServerId()
    {
      return WatchdogArgs.this.getServerId();
    }

    @Override
    public boolean isResinProfessional()
    {
      return isProfessional();
    }

    public boolean isProfessional()
    {
      loadLicenses();

      return _isResinProfessional;
    }

    private void loadLicenses()
    {
      if (_isLicenseCheck)
        return;

      _isLicenseCheck = true;

      LicenseCheck license;

      try {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        Class<?> cl = Class.forName("com.caucho.license.LicenseCheckImpl",
            false, loader);

        license = (LicenseCheck) cl.newInstance();

        license.requireProfessional(1);

        Vfs.initJNI();

        _isResinProfessional = true;

        // license.doLogging(1);
      } catch (Exception e) {
        log.log(Level.FINER, e.toString(), e);
      }
    }
  }
  
  private static void addCommand(BootCommand command)
  {
    _commandMap.put(command.getName(), command);
  }
  
  static {
    addCommand(new ConsoleCommand());
    addCommand(new DeployCopyCommand());
    addCommand(new DeployCommand());
    addCommand(new DeployConfigCommand());
    addCommand(new DeployListCommand());
    addCommand(new DeployRestartCommand());
    addCommand(new DeployStartCommand());
    addCommand(new DeployStopCommand());
    addCommand(new DisableCommand());
    addCommand(new DisableSoftCommand());
    addCommand(new DeployStartCommand());
    addCommand(new EnableCommand());
    
    addCommand(new GuiCommand());
    addCommand(new GeneratePasswordCommand());
    
    addCommand(new HeapDumpCommand());
    addCommand(new JmxCallCommand());
    addCommand(new JmxDumpCommand());
    addCommand(new JmxListCommand());
    addCommand(new JmxSetCommand());
    addCommand(new JspcCommand());
    addCommand(new KillCommand());
    addCommand(new LicenseAddCommand());
    addCommand(new ListRestartsCommand());
    addCommand(new LogLevelCommand());
    
    addCommand(new PdfReportCommand());
    addCommand(new ProfileCommand());
    
    addCommand(new RestartCommand());
    
    addCommand(new ShutdownCommand());
    addCommand(new StartCommand());
    addCommand(new StartAllCommand());
    addCommand(new StartWithForegroundCommand());
    addCommand(new StatusCommand());
    addCommand(new StopCommand());
    
    addCommand(new ThreadDumpCommand());
    
    addCommand(new UndeployCommand());
    addCommand(new UserAddCommand());
    addCommand(new UserListCommand());
    addCommand(new UserRemoveCommand());

    addCommand(new WatchdogCommand());
    
    _commandMap.put("copy", new DeployCopyCommand());
    _commandMap.put("list", new DeployListCommand());
    
    _commandMap.put("start-webapp", new DeployStartCommand());
    _commandMap.put("stop-webapp", new DeployStopCommand());
    _commandMap.put("restart-webapp", new DeployRestartCommand());
  }
  
  static class CommandNameComparator implements Comparator<BootCommand> {
    public int compare(BootCommand a, BootCommand b)
    {
      return a.getName().compareTo(b.getName());
    }
  }
}
