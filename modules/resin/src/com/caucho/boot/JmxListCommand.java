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
 * @author Alex Rojkov
 */

package com.caucho.boot;

import com.caucho.server.admin.ManagerClient;
import com.caucho.util.L10N;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public class JmxListCommand extends JmxCommand
{
  private static final L10N L = new L10N(JmxListCommand.class);

  @Override
  public void doCommand(WatchdogArgs args, WatchdogClient client)
  {
    String pattern = args.getDefaultArg();
    if (pattern != null)

      try {
        ObjectName.getInstance(pattern);
      } catch (MalformedObjectNameException e) {
        System.err.println(e.getMessage());

        return;
      }

    boolean isPrintAttributes = args.hasOption("-attributes");
    boolean isPrintOperations = args.hasOption("-operations");
    boolean isPrintValues = args.hasOption("-values");
    if (isPrintValues)
      isPrintAttributes = true;
    boolean isAll = args.hasOption("-all");
    boolean isPlatform = args.hasOption("-platform");

    ManagerClient manager = getManagerClient(args, client);

    String jmxResult = manager.listJmx(pattern,
                                       isPrintAttributes,
                                       isPrintValues,
                                       isPrintOperations,
                                       isAll,
                                       isPlatform);

    System.out.print(jmxResult);
  }

  @Override
  public void usage()
  {
    System.err.println(L.l("usage: java -jar resin.jar [-conf <file>] jmx-list -user <user> -password <password> [-attributes] [-values] [-operations] [-all] [-platform] [<pattern>]"));
    System.err.println(L.l(""));
    System.err.println(L.l("description:"));
    System.err.println(L.l("   lists beans registered with JMX and matching <pattern>. <pattern> is optional and adheres\n"
                           + "to the rules defined for javax.managment.ObjectName (default resin:*)"));
    System.err.println(L.l(""));
    System.err.println(L.l("options:"));
    System.err.println(L.l("   -attributes            : prints MBean's attributes"));
    System.err.println(L.l("   -values                : prints attribute values"));
    System.err.println(L.l("   -operations            : prints operations"));
    System.err.println(L.l("   -all                   : when <pattern> not specified sets the wildcard pattern (*:*)"));
    System.err.println(L.l("   -platform              : when <pattern> not specified sets the pattern to (java.lang:*)"));
  }
}
