#! perl
#
# Copyright(c) 1998-2002 Caucho Technology
#
# Path normalization code contributed by Mike Wynholds
#
# Helpful for getting file path information
#
use File::Basename;
use Socket;
use POSIX;
#
#
# The following variables are usually automatically set or set from the
# command line.  They can be set here if necessary.
#
# Location of the Resin directories, usually scripts can find it
#
$RESIN_HOME="";
#
# Location of the server directories, defaults to RESIN_HOME.
#
$SERVER_ROOT="";
#
# Location of JAVA_HOME, usually scripts can find it
#
$JAVA_HOME="";
#
# Location of java executable, usually scripts can find it
#
$JAVA_EXE="";
#
# Additional args to pass to java before command-line args.
#
$JAVA_ARGS="";
#
# Additional args to pass to java after command-line args.
#
$EXTRA_JAVA_ARGS="-Djava.util.logging.manager=com.caucho.log.LogManagerImpl";
$EXTRA_JAVA_ARGS.=" -Djavax.management.builder.initial=com.caucho.jmx.MBeanServerBuilderImpl";

#
# Default stack size.  The 1m is a good tradeoff between stack size and
# allowing more threads.  The default stack size doesn't allow enough
# threads for several operating systems.
#
$DEFAULT_STACK_SIZE="-Xss1m";
#
# Additional args to pass to Resin
#
$RESIN_ARGS="";
#
# Initial classpath.  Usually filled with the environment or the command line.
#
$CLASSPATH="";
#
# Library path
#
$LIBRARY_PATH="";
#
# How long wrapper.pl sleeps before trying to restart the JVM
#
$sleep_time = 10;
#
# How long to wait for the client to connect to the socket
#
$accept_time = 60;
#
# How long to wait for a nice exit before a forced exit
#
$kill_time = 60;
#
# The pid file to use for start/stop (defaults to httpd.pid)
#
$pid_file="";

#
# Find the real location of a file, tracing symlinks.
#
sub find_real_path {
    my($name) = shift;
    ($base, $path, $type) = fileparse($name);
    return $path;
}

sub usage {
    print "usage: wrapper.pl [flags] [cmd] [args to the java main]\n";
    print "flags:\n";
    print "  -help           : this usage message\n";
    print "  -verbose        : echo variables and argument before execution\n";
    print "  -java_home dir  : set JAVA_HOME\n";
    print "  -resin-home dir : set RESIN_HOME\n";
    print "  -server-root dir : set SERVER_ROOT\n";
    print "  -conf <resin.conf> : changes the configuration file\n";
    print "  -classpath cp   : set CLASSPATH\n";
    print "  -native         : force native threads\n";
    print "  -green          : force green threads\n";
    print "  -nojit          : no jit\n";
    print "  -stdout <file>  : stdout log\n";
    print "  -stderr <file>  : stderr log\n";
    print "  -jvm-log <file> : jvm log\n";
    print "  -pid <file>     : file for the pid\n";
    print "  -no-auto-restart : disable automatic server restart\n";
    print "cmd:\n";
    print "  start           : start $name\n";
    print "  stop            : stop $name\n";
    print "  restart         : restart $name\n";
    print "  <default>       : exec $name\n";
}


$verbose=0;
$thread="";
$conf="";
$exe=$0;
$nojit="";
$chdir="";
$keepalive=1;
$depth="";
$stdout_log="";
$stderr_log="";
$jvm_log="";
$cmd = "exec";
$|=1;

while ( $#ARGV >= 0) {
    if ($ARGV[0] eq "-v" || $ARGV[0] eq "-verbose") {
	$verbose=true;
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "start" || $ARGV[0] eq "-start") {
	$cmd = "start";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "stop" || $ARGV[0] eq "-stop") {
	$cmd = "stop";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "restart" || $ARGV[0] eq "-restart") {
	$cmd = "restart";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-native") {
	$JAVA_ARGS .= " -native";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-green") { 
	$JAVA_ARGS .= " -green";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-classic") { 
	$JAVA_ARGS .= " -classic";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-nojit") {
	$nojit = 1;
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-java_home" || $ARGV[0] eq "-java-home") {
	$JAVA_HOME=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-java_exe" || $ARGV[0] eq "-java-exe") {
	$JAVA_EXE=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-resin_home" || $ARGV[0] eq "-resin-home") {
	$RESIN_HOME=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-server_root" || $ARGV[0] eq "-server-root") {
	$SERVER_ROOT=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-conf" || $ARGV[0] eq "-config") {
	$conf="-conf " . &normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-cp" || $ARGV[0] eq "-classpath") {
	$CLASSPATH=&normalize_classpath($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-stdout") {
	$stdout_log=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-stderr") {
	$stderr_log=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-jvm-log") {
	$jvm_log=&normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-pid") {
	$pid_file = &normalize_path($ARGV[1]);
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-no-keepalive" ||
	   $ARGV[0] eq "-no-auto-restart") {
	$keepalive = 0;
	shift(@ARGV);
    }
    elsif ($ARGV[0] =~ "^-J") {
        $val = substr($ARGV[0], 2);
	$JAVA_ARGS .= " " . $val;

        if ($val =~ "^-Xss") {
          $DEFAULT_STACK_SIZE = "";
        }

	shift(@ARGV);
    }
    elsif ($ARGV[0] =~ "^-D") {
	$JAVA_ARGS .= " " . $ARGV[0];
	shift(@ARGV);
    }
    elsif ($ARGV[0] =~ "^-X") {
        $val = $ARGV[0];
	$JAVA_ARGS .= " " . $val;

        if ($val =~ "^-Xss") {
          $DEFAULT_STACK_SIZE = "";
        }

	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-h" || $ARGV[0] eq "-help" || $ARGV[0] eq "help") {
	&usage();
	exit(0);
    }

    # Undocumented arguments
    elsif ($ARGV[0] eq "-jre") {
	$VMTYPE="jre";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-kaffe") {
	$VMTYPE="kaffe";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-prof") {
	$VMTYPE = "prof";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-hprof") {
	$VMTYPE = "hprof";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-cpuprof") {
	$VMTYPE = "cpuprof";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-cpuprof-ascii") {
	$VMTYPE = "cpuprof-ascii";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-hprof-ascii") {
	$VMTYPE = "hprof-ascii";
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-prof-depth") {
	$depth = $ARGV[1];
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-class") {
	$class = $ARGV[1];
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-name") {
	$name = $ARGV[1];
	shift(@ARGV);
	shift(@ARGV);
    }
    elsif ($ARGV[0] eq "-chdir") {
	$chdir = 1;
	shift(@ARGV);
    }
    elsif (! $class && ($ARGV[0] !~ "-.*")) {
	$class=$ARGV[0];
	shift(@ARGV);
    }
    else {
	$RESIN_ARGS .= " '" . $ARGV[0] . "'";
	shift(@ARGV);
    }
}

#
# Add the default values.
#
if ($DEFAULT_STACK_SIZE) {
  $JAVA_ARGS .= " " . $DEFAULT_STACK_SIZE;
}

# 
# Find the resin libraries
#
# 1) $RESIN_HOME
# 2) The directory the executable is in
# 3) /usr/lib/resin
# 4) /usr/local/lib/resin
#
if (! $RESIN_HOME) {
    $RESIN_HOME=$ENV{"RESIN_HOME"};
}

if (! $RESIN_HOME) {
    $path=&find_real_path($exe);

    if (-d $path) {
	$RESIN_HOME=`cd $path/.. >/dev/null 2>&1 && pwd`;
	chop($RESIN_HOME);
    }

    if (! -d "$RESIN_HOME/lib") {
	$RESIN_HOME='/usr/lib/resin';
    }

    if (! -d "$RESIN_HOME/lib" ) {
	$RESIN_HOME='/usr/local/lib/resin';
    }
}

if (! $RESIN_HOME || ! -d "$RESIN_HOME/lib") {
    die "Can't find valid RESIN_HOME ($RESIN_HOME)";
}

if (! $SERVER_ROOT) {
    $SERVER_ROOT = $RESIN_HOME;
}

if (! $LIBRARY_PATH) {
    $LIBRARY_PATH = "${RESIN_HOME}/libexec";
}

$ENV{"LD_LIBRARY_PATH"} = $ENV{"LD_LIBRARY_PATH"} . ":$LIBRARY_PATH";
$ENV{"DYLD_LIBRARY_PATH"} = $ENV{"DYLD_LIBRARY_PATH"} . ":$LIBRARY_PATH";

# 
# Find JAVA_HOME
#
# 1) $JAVA_HOME
# 2) The directory the java executable is in
# 3) The directory the jre executable is in
# 4) The directory java is in
# 5) /usr/java
# 6) /usr/local/java
# 7) /usr/jre
# 8) /usr/local/jre
# 9) $RESIN_HOME/jre
#
if (! $JAVA_HOME) {
    $JAVA_HOME=$ENV{"JAVA_HOME"};
}

if (! $JAVA_HOME) {
  # Find the real directory containing the java executable
  $path=&find_real_path("java");

  if (! $path) {
    $path=&find_real_path("jre");
  }

  # Get the parent directory.
  if ($path) {
      $JAVA_HOME=`cd $path/.. >/dev/null 2>&1 && pwd`;
      chop($JAVA_HOME);
  }

  if (! -f "$JAVA_HOME/bin/java") {
      $JAVA_HOME='/usr/java';
  }

  if (! -f "$JAVA_HOME/bin/java") {
      $JAVA_HOME='/usr/local/java';
  }

  if (! -f "$JAVA_HOME/bin/java") {
      $JAVA_HOME='/usr/jre';
  }

  if (! -f "$JAVA_HOME/bin/java") {
      $JAVA_HOME='/usr/local/jre';
  }

  if (! -f "$JAVA_HOME/bin/java") {
      $JAVA_HOME="$RESIN_HOME/jre";
  }

#  if (! -f "$JAVA_HOME/bin/java" && ! -f "$JAVA_HOME/bin/jre") {
#    die "Can't find valid JAVA_HOME ($JAVA_HOME).";
#  }
}

if (! $pid_file) {
    $pid_file = "$SERVER_ROOT/${name}.pid";
}

if ($pid_file =~ "^[^/]") {
    $pid_file = "$SERVER_ROOT/$pid_file";
}
#
# If desired, close the old server
#
if ($cmd eq "stop" || $cmd eq "restart") {
    if (-f "$pid_file") {
	$pid=`cat $pid_file`;
	chop($pid);

	if ($cmd eq "stop") {
	    print("Stopping ${name}\n");
	}
	kill(15, $pid);
	unlink($pid_file);
    }
    elsif ($cmd eq "stop") {
	print("No ${name} has been started\n");
    }
}

#
# Set the classpath
#
if (! $CLASSPATH) {
    $CLASSPATH=$ENV{"CLASSPATH"};
}
if (! $CLASSPATH) {
    $CLASSPATH=".";
}

if (-d "$RESIN_HOME/classes") {
    $CLASSPATH="$CLASSPATH:$RESIN_HOME/classes";
}

#
# These jars match the resin.manifest.  They're the minimal set that
# Resin needs to start.  Other jars in lib are loaded dynamically
# with a separate class loader.
#

#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/jsdk-24.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/jta-101.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/jca-15.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/jstl-11.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/isorelax.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/jmx-12.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/portlet-10.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/resin.jar";
#$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/license.jar";

opendir(RESINLIB, "$RESIN_HOME/lib");
while ($file = readdir(RESINLIB)) {
    if ($file =~ /\.jar$/ || $file =~ /\.zip$/) {
	$CLASSPATH="$CLASSPATH:$RESIN_HOME/lib/$file";
    }
}
closedir(RESINLIB);

#
# Add the tools.jar
#

if (-e "$JAVA_HOME/lib/tools.jar") {
  $CLASSPATH="$CLASSPATH:$JAVA_HOME/lib/tools.jar";
}

#
# Set the proper executable
#
if (! $depth) {
    $depth = 4;
}
if ($JAVA_EXE) {
} elsif ($VMTYPE eq "prof") {
    $JAVA_EXE="$JAVA_HOME/bin/java_g";
    $JAVA_ARGS .= " -prof $thread";
} elsif ($VMTYPE eq "hprof") {
    $JAVA_EXE="$JAVA_HOME/bin/java";
    $JAVA_ARGS .= " -Xrunhprof:format=b $thread";
} elsif ($VMTYPE eq "hprof-ascii") {
    $JAVA_EXE="$JAVA_HOME/bin/java";
    $JAVA_ARGS .= " -Xrunhprof:heap=sites $thread";
} elsif ($VMTYPE eq "cpuprof") {
    $JAVA_EXE="$JAVA_HOME/bin/java";
    $JAVA_ARGS .= " -Xrunhprof:format=b,cpu=samples $thread";
} elsif ($VMTYPE eq "cpuprof-ascii") {
    $JAVA_EXE="$JAVA_HOME/bin/java";
    $JAVA_ARGS .= "  -Xrunhprof:cpu=samples,heap=sites,depth=$depth $thread";
#    $JAVA_EXE="$JAVA_HOME/bin/java -Xrunhprof:cpu=samples,depth=$depth $thread";
} elsif (-x "$JAVA_HOME/sh/java") {        # hack for AIX
    $JAVA_EXE="$JAVA_HOME/sh/java";
    $JAVA_ARGS .= " $thread";
} elsif (-x "$JAVA_HOME/jre/sh/java") {    # hack for AIX
    $JAVA_EXE="$JAVA_HOME/jre/sh/java";
    $JAVA_ARGS .= " $thread";
} elsif (-x "$JAVA_HOME/bin/java") {
    $JAVA_EXE="$JAVA_HOME/bin/java";
    $JAVA_ARGS .= " $thread";
} elsif (-x "$JAVA_HOME/bin/jre") {
    $JAVA_EXE="$JAVA_HOME/bin/jre";
    $JAVA_ARGS .= " $thread";
} else {
    $JAVA_EXE="java";
  #  die "Cannot find java executable in $JAVA_HOME";
}

if ($nojit) {
    $JAVA_ARGS .= " -Djava.compiler=NONE";
}

#
# The pid is stored in a file so scripts can easily stop the process
#
sub handler {
    if ($child > 0) {
	$SIG{CHLD} = 'IGNORE';
    }

    print "\n\nResin wrapper.pl closing.\n\n";
    
    close(S);
    close(C); # closing the accepted socket should start orderly shutdown
    # unlink needs to happen relatively soon so restart's pid won't
    # get unlinked

    $pid=`cat $pid_file`;
    chop($pid);

    if ($pid == $child) {
      unlink($pid_file);
    }
    
    if ($child > 0) {
	# let it die gracefully in 60 seconds
	while ($kill_time-- > 0 and kill(0, $child)) {
	    sleep(1);
	}

	if ($kill_time <= 0) {
	    kill(-$child);
	}
    }

    exit(1);
}

$JAVA_ARGS .= " -Dresin.home=$SERVER_ROOT $EXTRA_JAVA_ARGS";

if ($cmd eq "start" || $cmd eq "restart") {
  mkdir("$SERVER_ROOT/log", 0755);

  if (! $stdout_log) {
      $stdout_log = "$SERVER_ROOT/log/stdout.log";
  }

  if (! $stderr_log) {
      $stderr_log = "$SERVER_ROOT/log/stderr.log";
  }

  if (! $jvm_log) {
      $jvm_log = "$SERVER_ROOT/log/jvm.log";
  }
}

if ($stdout_log) {
    $RESIN_ARGS .= " -stdout $stdout_log";

    open(TOUCH, ">> $stdout_log") || die "Can't create $stdout_log.\n";
    close(TOUCH);
}

if ($stderr_log) {
    $RESIN_ARGS .= " -stderr $stderr_log";
  
    open(TOUCH, ">> $stderr_log") || die "Can't create $stderr_log.\n";
    close(TOUCH);
}

if ($jvm_log) {
    open(TOUCH, ">> $jvm_log") || die "Can't create $jvm_log.\n";
    close(TOUCH);
}

if ($verbose) {
    print "JAVA_HOME:\t$JAVA_HOME\n";
    print "RESIN_HOME:\t$RESIN_HOME\n";
    print "SERVER_ROOT:\t$SERVER_ROOT\n";
    
    print "CLASSPATH:\n";
    foreach $c (split(/:/, $CLASSPATH)) {
	if ($c) {
	    print "\t$c\n";
	}
    }
    
    print "LD_LIBRARY_PATH:\n";
    foreach $c (split(/:/, $ENV{"LD_LIBRARY_PATH"})) {
	if ($c) {
	    print "\t$c\n";
	}
    }
    print "java:        $JAVA_EXE\n";
    print "java args:   $JAVA_ARGS\n";
    print "class:       $class\n";
    print "resin args:  $RESIN_ARGS\n";

    print "\n";
    print "command-line: $JAVA_EXE $JAVA_ARGS $class $conf $RESIN_ARGS\n";
}

if ($chdir) {
    chdir($SERVER_ROOT);
}

$ENV{"CLASSPATH"} = $CLASSPATH;
if ($JAVA_HOME) {
  $ENV{"JAVA_HOME"} = $JAVA_HOME;
}

#
# exec just executes the process
#
      
if ($cmd eq "exec") {
  exec("$JAVA_EXE $JAVA_ARGS $class $conf $RESIN_ARGS");
  die("Can't start java: $JAVA_EXE $JAVA_ARGS $class $conf $RESIN_ARGS");
}

#
# start restarts the process, unless it exits with the special code
# 66.  The wrapper uses the Java process's stdin as a keepalive.  When
# the wrapper dies, stdin will close and the Java process will close
# gracefully
#
if ($cmd eq "start" || $cmd eq "restart") {
  $oldpid = `cat $pid_file 2>/dev/null`;
  chop($oldpid);
  if ($oldpid && kill(0, $oldpid)) {
      print("${name} has already started\n");
      exit(1);
  }

  if (fork()) {
      exit(0);
  }

  setpgrp;
  $SIG{"HUP"} = 'IGNORE';

  if (fork()) {
      exit(0);
  }

  if ($pid_file) {
      open(OUT, ">$pid_file") || die("Can't write $pid_file");
      print(OUT "$$\n");
      close(OUT);
  }

  close(STDIN);
  # close(STDOUT);
  # close(STDERR);

  $SIG{"INT"} = 'handler';
  $SIG{"QUIT"} = 'handler';
  $SIG{"KILL"} = 'handler';
  $SIG{"TERM"} = 'handler';

  if ($cmd eq "restart") {
      sleep(5); # let the old guy close gracefully
  }
  
  do {
      $date = `date`;
      chop($date);

      print "Resin $name $cmd at $date\n";

      # create a keepalive socket
      # when the wrapper dies, the httpd class will detect that and
      # close gracefully
      $addr = pack("S n a4 x8", AF_INET, 0, "\0\0\0");
      ($protoname, $aliases, $proto) = getprotobyname('tcp');
      socket(S, AF_INET, SOCK_STREAM, $proto) || die "socket: $!";
      bind(S, $addr) || die "bind: $!";
      $myaddr = getsockname(S);
      ($fam, $port, $addr) = unpack("S n a4 x8", $myaddr);
      listen(S, 5) || die "connect: $!";

      if (($child = fork()) == 0) {
	  close(S);
          close(STDOUT);
          close(STDERR);

          open(STDOUT, ">>$jvm_log");
          open(STDERR, ">&STDOUT");

	  exec("$JAVA_EXE $JAVA_ARGS $class -socketwait $port $conf $RESIN_ARGS");
          print(STDERR "Can't start java: $JAVA_EXE $JAVA_ARGS $class $conf $RESIN_ARGS");
	  exit(66);
      }
      elsif ($child < 0) {
	  print "Resin $name fork failed at $date\n";
      }
      else {
          $hasAccept = 0;

          for ($i = 0; ! $hasAccept && $i < 5; $i++) {
  	    $rin = '';
	    $win = '';
	    vec($rin, fileno(S), 1) = 1;
	    $timeout = $accept_time / 5;

	    if (select($rin, $win, $rin, $timeout) > 0) {
	      accept(C, S);
              $hasAccept = 1;

              close(STDOUT);
              close(STDERR);
	    }
          }

	  close(S);
      }
      
      wait();
      $status = $? >> 8;
      close(C);
      sleep($sleep_time);
      kill(-$child);
  } while ($status != 66 && $keepalive);

  if ($pid_file) {
      unlink($pid_file);
  }

  print("Server died unexpectedly.\n");
  print("Check $stdout_log and $stderr_log.\n");
  exit(1);
}

#
# Normalize relative paths to absolute paths.
#
sub normalize_path
{
  my $file = shift;
  my $pwd  = getcwd();
  my $dir;
  my $isDir;
  my $f;

  if (-d $file)
  {
    $dir = $file;
    chdir $dir or return $file;
    my $ret = getcwd();
    chdir $pwd or die "Oops: $!";
    return $ret;
  }
  elsif (-f $file)
  {
    $dir = &dirname( $file );
    $f   = &basename ( $file );
    chdir $dir or return $file;
    my $ret = getcwd();
    chdir $pwd or die "Oops: $!";
    return "$ret/$f";
  }
  else
  {
    return $file;
  }
}

#
# Normalize the classpath to use absolute paths instead of relative paths.
#
sub normalize_classpath
{
  my $cp = shift;
  my $newcp = "";
  foreach $i ( split /:/, $cp )
  {
    $newcp .= &normalize_path($i) . ":";
  }
  chop $newcp;
  return $newcp;
}
