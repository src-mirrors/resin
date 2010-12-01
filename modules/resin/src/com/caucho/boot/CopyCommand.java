/*
 * Copyright (c) 1998-2010 Caucho Technology -- all rights reserved
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

import com.caucho.config.ConfigException;
import com.caucho.env.repository.CommitBuilder;
import com.caucho.server.admin.WebAppDeployClient;
import com.caucho.util.L10N;

public class CopyCommand extends AbstractRepositoryCommand {
  private static final L10N L = new L10N(CopyCommand.class);
  
  @Override
  public void doCommand(WatchdogArgs args,
                        WatchdogClient client)
  {
    WebAppDeployClient deployClient = getDeployClient(args, client);

    String sourceContext = args.getArg("-source");
    if (sourceContext == null)
      throw new ConfigException("must specify -source attribute");


    String sourceHost = args.getArg("-source-host");
    
    if (sourceHost == null)
      sourceHost = "default";

    CommitBuilder source = new CommitBuilder();
    source.type("webapp");

    String sourceStage = args.getArg("-source-stage");

    if (sourceStage != null)
      source.stage(sourceStage);

    source.tagKey(sourceHost + "/" + sourceContext);

    String version = args.getArg("-source-version");
    if (version != null)
      fillInVersion(source, version);

    String targetContext = args.getArg("-target");

    String targetHost = args.getArg("-target-host");

    if (targetHost == null)
      targetHost = sourceHost;

    CommitBuilder target = new CommitBuilder();
    target.type("webapp");

    String targetStage = args.getArg("-target-stage");

    if (targetStage != null)
      target.stage(targetStage);

    target.tagKey(targetHost + "/" + targetContext);

    String message = args.getArg("-m");

    if (message == null)
      message = args.getArg("-message");

    if (message == null)
      message = L.l("copy '{0}' to '{1}'", source.getTagKey(), target.getTagKey());

    target.message(message);

    target.attribute("user", System.getProperty("user.name"));

    String targetVersion = args.getArg("-target-version");
    if (targetVersion != null)
      fillInVersion(target, targetVersion);

    deployClient.copyTag(target, source);

    deployClient.close();

    System.out.println("copied " + source.getId() + " to " + target.getId());
  }
}
