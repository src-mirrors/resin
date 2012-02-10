/*
 * Copyright (c) 1998-2012 Caucho Technology -- all rights reserved
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

package com.caucho.server.admin;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;

import com.caucho.bam.Query;
import com.caucho.bam.actor.SimpleActor;
import com.caucho.bam.mailbox.MultiworkerMailbox;
import com.caucho.cloud.bam.BamSystem;
import com.caucho.cloud.deploy.CopyTagQuery;
import com.caucho.cloud.deploy.RemoveTagQuery;
import com.caucho.cloud.deploy.SetTagQuery;
import com.caucho.config.ConfigException;
import com.caucho.env.deploy.DeployControllerService;
import com.caucho.env.deploy.DeployException;
import com.caucho.env.deploy.DeployTagItem;
import com.caucho.env.git.GitTree;
import com.caucho.env.repository.RepositorySpi;
import com.caucho.env.repository.RepositorySystem;
import com.caucho.env.repository.RepositoryTagEntry;
import com.caucho.jmx.Jmx;
import com.caucho.lifecycle.LifecycleState;
import com.caucho.management.server.DeployControllerMXBean;
import com.caucho.management.server.EAppMXBean;
import com.caucho.management.server.WebAppMXBean;
import com.caucho.server.cluster.Server;
import com.caucho.server.host.HostController;
import com.caucho.server.webapp.WebAppContainer;
import com.caucho.server.webapp.WebAppController;
import com.caucho.util.IoUtil;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.StreamSource;
import com.caucho.vfs.Vfs;

public class DeployActor extends SimpleActor
{
  public static final String address = "deploy@resin.caucho";

  private static final Logger log
    = Logger.getLogger(DeployActor.class.getName());

  private static final L10N L = new L10N(DeployActor.class);

  private Server _server;

  private RepositorySpi _repository;

  private AtomicBoolean _isInit = new AtomicBoolean();

  public DeployActor()
  {
    super(address, BamSystem.getCurrentBroker());
  }
  
  /*
  @Override
  public ManagedBroker getBroker()
  {
    return _server.getAdminBroker();
  }
  */

  @PostConstruct
  public void init()
  {
    if (_isInit.getAndSet(true))
      return;

    _server = Server.getCurrent();

    if (_server == null)
      throw new ConfigException(L.l("resin:DeployService requires an active Server.\n  {0}",
                                    Thread.currentThread().getContextClassLoader()));

    _repository = RepositorySystem.getCurrentRepositorySpi();

    setBroker(getBroker());
    MultiworkerMailbox mailbox
      = new MultiworkerMailbox(getActor().getAddress(), 
                               getActor(), getBroker(), 2);
    
    getBroker().addMailbox(mailbox);
  }

  @Query
  public boolean commitList(long id, String to, String from,
                            DeployCommitListQuery commitList)
  {
    ArrayList<String> uncommittedList = new ArrayList<String>();

    if (commitList.getCommitList() != null) {
      for (String commit : commitList.getCommitList()) {
        if (! _repository.exists(commit))
          uncommittedList.add(commit);
      }
    }

    DeployCommitListQuery resultList
      = new DeployCommitListQuery(uncommittedList);

    getBroker().queryResult(id, from, to, resultList);

    return true;
  }

  @Query
  public void getFile(long id, String to, String from,
                         DeployGetFileQuery getFile)
    throws IOException
  {
    String tag = getFile.getTag();
    String fileName = getFile.getFileName();
    
    if (log.isLoggable(Level.FINER))
      log.finer(this + " query " + getFile + "\n  to:" + to + " from:" + from);
    
    while (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }
    
    RepositoryTagEntry entry = _repository.getTagMap().get(tag);
    
    if (entry == null) {
      throw new ConfigException(L.l("'{0}' is an unknown repository tag",
                                    tag));
    }
    
    String sha1 = entry.getRoot();
    
    String fileSha = findFile(sha1, fileName, fileName);

    BlobStreamSource iss = new BlobStreamSource(_repository, fileSha);
    
    StreamSource source = new StreamSource(iss);
    
    DeployGetFileQuery resultFile = new DeployGetFileQuery(tag, sha1, source);
    
    getBroker().queryResult(id, from, to, resultFile);
  }
  
  private String findFile(String sha1, String fullFilename, String fileName)
    throws IOException
  {
    if (fileName.equals("")) {
      if (_repository.isBlob(sha1))
        return sha1;
      else {
        throw new ConfigException(L.l("'{0}' is not a file", fullFilename));
      }
    }
    
    int p = fileName.indexOf('/');
    String tail = "";
    
    if (p > 0) {
      tail = fileName.substring(p + 1);
      fileName = fileName.substring(0, p);
    }
    
    if (! _repository.isTree(sha1))
      throw new ConfigException(L.l("'{0}' is an invalid path", fullFilename));
    
    GitTree tree = _repository.readTree(sha1);
    
    String childSha1 = tree.getHash(fileName);
    
    if (childSha1 == null)
      throw new ConfigException(L.l("'{0}' is an unknown file",
                                    fullFilename));
   
    return findFile(childSha1, fullFilename, tail);
  }

  @Query
  public void getFileList(long id, String to, String from,
                      DeployListFilesQuery listFile)
    throws IOException
  {
    String tag = listFile.getTag();
    String fileName = listFile.getFileName();
    
    if (log.isLoggable(Level.FINER))
      log.finer(this + " query " + listFile + "\n  to:" + to + " from:" + from);
    
    RepositoryTagEntry entry = _repository.getTagMap().get(tag);
    
    if (entry == null) {
      throw new ConfigException(L.l("'{0}' is an unknown repository tag",
                                    tag));
    }
    
    String sha1 = entry.getRoot();
    
    ArrayList<String> fileList = new ArrayList<String>();
    
    listFiles(fileList, sha1, "");
    
    Collections.sort(fileList);
    
    String []files = new String[fileList.size()];
    
    fileList.toArray(files);
    
    DeployListFilesQuery result
      = new DeployListFilesQuery(tag, fileName, files);
    
    getBroker().queryResult(id, from, to, result);
  }
  
  private void listFiles(ArrayList<String> files, 
                         String sha1,
                         String prefix)
    throws IOException
  {
    if (sha1 == null)
      return;
    
    if (_repository.isBlob(sha1)) {
      files.add(prefix);
      return;
    }
    
    if (! _repository.isTree(sha1))
      throw new ConfigException(L.l("'{0}' is an invalid path", prefix));
    
    GitTree tree = _repository.readTree(sha1);
    
    for (String key : tree.getMap().keySet()) {
      String name;
      
      if ("".equals(prefix))
        name = key;
      else
        name = prefix + "/" + key;
      
      listFiles(files, tree.getHash(key), name);
    }
  }
  
  @Query
  public void tagCopy(long id,
                      String to,
                      String from,
                      CopyTagQuery query)
  {
    String tag = query.getTag();
    String sourceTag = query.getSourceTag();

    RepositoryTagEntry entry = _repository.getTagMap().get(sourceTag);

    if (entry == null) {
      log.fine(this + " copyError dst='" + query.getTag() + "' src='" + query.getSourceTag() + "'");

      throw new DeployException(L.l("deploy-copy: '{0}' is an unknown source tag.",
                                    query.getSourceTag()));
      /*
      getBroker().queryError(id, from, to, query,
                             new BamError(BamError.TYPE_CANCEL,
                                          BamError.ITEM_NOT_FOUND,
                             "unknown tag"));
      return;
      */
    }

    log.fine(this + " copy dst='" + query.getTag() + "' src='" + query.getSourceTag() + "'");
    
    String server = "default";
    
    TreeMap<String,String> metaDataMap = new TreeMap<String,String>();
    
    if (query.getAttributes() != null)
      metaDataMap.putAll(query.getAttributes());
    
    if (server != null)
      metaDataMap.put("server", server);

    boolean result = _repository.putTag(tag,
                                        entry.getRoot(),
                                        metaDataMap);

    getBroker().queryResult(id, from, to, result);
  }

  @Query
  public void tagState(long id,
                       String to,
                       String from,
                       TagStateQuery query)
  {
    // XXX: just ping the tag?
    // updateDeploy();
    
    String tag = query.getTag();
    
    DeployControllerService deploy = DeployControllerService.getCurrent();
    DeployTagItem item = null;
    
    if (deploy != null) {
      deploy.update(tag);
      item = deploy.getTagItem(tag);
    }
    
    if (item != null) {
      TagStateQuery result = new TagStateQuery(tag, item.getStateName(),
                                               item.getDeployException());
      
      getBroker().queryResult(id, from, to, result);
    }
    else
      getBroker().queryResult(id, from, to, null);
  }

  @Query
  public Boolean removeTag(RemoveTagQuery query)
  {
    if (log.isLoggable(Level.FINE))
      log.fine(this + " query " + query);
    
    String server = "default";
    
    HashMap<String,String> commitMetaData = new HashMap<String,String>();
    
    if (query.getAttributes() != null)
      commitMetaData.putAll(query.getAttributes());
    
    commitMetaData.put("server", server);

    return _repository.removeTag(query.getTag(), commitMetaData);
  }

  @Query
  public boolean sendFileQuery(long id, String to, String from,
                               DeploySendQuery query)
  {
    String sha1 = query.getSha1();

    if (log.isLoggable(Level.FINER))
      log.finer(this + " sendFileQuery sha1=" + sha1);

    InputStream is = null;
    try {
      is = query.getInputStream();

      _repository.writeRawGitFile(sha1, is);

      getBroker().queryResult(id, from, to, true);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);

      getBroker().queryResult(id, from, to, false);
    } finally {
      IoUtil.close(is);
    }

    return true;
  }

  @Query
  public boolean setTagQuery(long id, String to, String from, SetTagQuery query)
  {
    String tagName = query.getTag();
    String contentHash = query.getContentHash();
    
    if (contentHash == null)
      throw new NullPointerException();

    String server = "default";
    
    TreeMap<String,String> commitMetaData = new TreeMap<String,String>();
    
    if (query.getAttributes() != null)
      commitMetaData.putAll(query.getAttributes());
    
    commitMetaData.put("server", server);
    
    boolean result = _repository.putTag(tagName, 
                                        contentHash,
                                        commitMetaData);

    getBroker().queryResult(id, from, to, String.valueOf(result));

    return true;
  }

  @Query
  public boolean queryTags(long id,
                           String to,
                           String from,
                           QueryTagsQuery tagsQuery)
  {
    ArrayList<TagResult> tags = new ArrayList<TagResult>();

    Pattern pattern = Pattern.compile(tagsQuery.getPattern());

    for (Map.Entry<String, RepositoryTagEntry> entry :
         _repository.getTagMap().entrySet()) {
      String tag = entry.getKey();

      if (pattern.matcher(tag).matches())
        tags.add(new TagResult(tag, entry.getValue().getRoot()));
    }

    getBroker()
      .queryResult(id, from, to, tags.toArray(new TagResult[tags.size()]));

    return true;
  }

  /**
   * @deprecated
   */
  @Query
  public boolean controllerDeploy(long id,
                                  String to,
                                  String from,
                                  ControllerDeployQuery query)
  {
    String status = deploy(query.getTag());

    log.fine(this + " deploy '" + query.getTag() + "' -> " + status);

    getBroker().queryResult(id, from, to, true);

    return true;
  }

  private String deploy(String gitPath)
  {
    LifecycleState state = start(gitPath);

    return state.getStateName();
  }

  /**
   * @deprecated
   */
  @Query
  public ControllerStateActionQueryResult controllerStart(long id,
                                                          String to,
                                                          String from,
                                                          ControllerStartQuery query)
  {
    LifecycleState state = start(query.getTag());

    ControllerStateActionQueryResult result
      = new ControllerStateActionQueryResult(query.getTag(), state);

    log.fine(this + " start '" + query.getTag() + "' -> " + state.getStateName());

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  private LifecycleState start(String tag)
  {
    DeployControllerService service = DeployControllerService.getCurrent();
    
    DeployTagItem controller = service.getTagItem(tag);

    if (controller == null)
      throw new IllegalArgumentException(L.l("'{0}' is an unknown controller",
                                             tag));

    controller.toStart();

    return controller.getState();
  }

  /**
   * @deprecated
   */
  @Query
  public ControllerStateActionQueryResult controllerStop(long id,
                                                         String to,
                                                         String from,
                                                         ControllerStopQuery query)
  {
    LifecycleState state = stop(query.getTag());

    ControllerStateActionQueryResult result
      = new ControllerStateActionQueryResult(query.getTag(), state);

    log.fine(this + " stop '" + query.getTag() + "' -> " + state.getStateName());

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  private LifecycleState stop(String tag)
  {
    DeployControllerService service = DeployControllerService.getCurrent();

    DeployTagItem controller = service.getTagItem(tag);

    if (controller == null)
      throw new IllegalArgumentException(L.l("'{0}' is an unknown controller",
                                             tag));
    controller.toStop();

    return controller.getState();
  }

  @Query
  public ControllerStateActionQueryResult controllerRestart(long id,
                                                            String to,
                                                            String from,
                                                            ControllerRestartQuery query)
  {
    LifecycleState state = restart(query.getTag());

    ControllerStateActionQueryResult result
      = new ControllerStateActionQueryResult(query.getTag(), state);

    log.fine(this
             + " restart '"
             + query.getTag()
             + "' -> "
             + state.getStateName());

    getBroker().queryResult(id, from, to, result);

    return result;
  }

  private LifecycleState restart(String tag)
  {
    DeployControllerService service = DeployControllerService.getCurrent();

    DeployTagItem controller = service.getTagItem(tag);

    if (controller == null)
      throw new IllegalArgumentException(L.l("'{0}' is an unknown controller",
                                             tag));

    controller.toRestart();

    return controller.getState();
  }

  /**
   * @deprecated
   */
  @Query
  public boolean controllerUndeploy(long id,
                                    String to,
                                    String from,
                                    ControllerUndeployQuery query)
  {
    String status = undeploy(query.getTag());

    log.fine(this + " undeploy '" + query.getTag() + "' -> " + status);

    getBroker().queryResult(id, from, to, true);

    return true;
  }

  /**
   * @deprecated
   */
  private String undeploy(String tag)
  {
    DeployControllerMXBean controller = findController(tag);

    if (controller == null)
      return L.l("'{0}' is an unknown controller", controller);

    try {
      Path root = Vfs.lookup(controller.getRootDirectory());

      root.removeAll();

      controller.stop();

      if (controller.destroy())
        return "undeployed";
      else
        return L.l("'{0}' failed to undeploy application '{1}'",
                   controller,
                   tag);
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return e.toString();
    }
  }

  /**
   * @deprecated
   */
  @Query
  public boolean controllerUndeploy(long id,
                                    String to,
                                    String from,
                                    UndeployQuery query)
  {
    if (true)
      throw new UnsupportedOperationException();
    
    String status = null;//undeploy(query.getTag(), query.getUser(), query.getMessage());

    log.fine(this + " undeploy '" + query.getTag() + "' -> " + status);

    getBroker().queryResult(id, from, to, true);

    return true;
  }

  private String undeploy(String tag, Map<String,String> commitMetaData)
  {
    DeployControllerMXBean controller = findController(tag);

    if (controller == null)
      return L.l("'{0}' is an unknown controller", controller);

    try {
      Path root = Vfs.lookup(controller.getRootDirectory());

      root.removeAll();

      controller.stop();

      if (controller.destroy()) {
        String server = "default";
        
        HashMap<String,String> metaDataCopy = new HashMap<String,String>();
        
        if (commitMetaData != null)
          metaDataCopy.putAll(commitMetaData);
        
        metaDataCopy.put("server", server);
        
        _repository.removeTag(tag, metaDataCopy);

        return "undeployed";
      }
      else
        return L.l("'{0}' failed to remove application '{1}'",
                   controller,
                   tag);
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return e.toString();
    }
  }

  /**
   * @deprecated
   */
  @Query
  public boolean sendAddFileQuery(long id, String to, String from,
                                  DeployAddFileQuery query)
  {
    String tag = query.getTag();
    String name = query.getName();
    String contentHash = query.getHex();

    try {
      DeployControllerMXBean deploy = findController(tag);

      if (deploy == null) {
        if (log.isLoggable(Level.FINE))
          log.fine(this + " sendAddFileQuery '" + tag + "' is an unknown DeployController");

        getBroker().queryResult(id, from, to, "no-deploy: " + tag);

        return true;
      }

      Path root = Vfs.lookup(deploy.getRootDirectory());
      root = root.createRoot();

      Path path = root.lookup(name);

      if (! path.getParent().exists())
        path.getParent().mkdirs();

      _repository.expandToPath(contentHash, path);

      getBroker().queryResult(id, from, to, "ok");

      return true;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      getBroker().queryResult(id, from, to, "fail");

      return true;
    }
  }

  /**
   * @deprecated
   **/
  @Query
  public boolean listWebApps(long id,
                             String to,
                             String from,
                             ListWebAppsQuery listQuery)
  {
    ArrayList<WebAppQuery> apps = new ArrayList<WebAppQuery>();

    String stage = _server.getStage();

    for (HostController host : _server.getHostControllers()) {
      if (listQuery.getHost().equals(host.getName())) {
        WebAppContainer webAppContainer
          = host.getDeployInstance().getWebAppContainer();
        
        for (WebAppController webApp : webAppContainer.getWebAppList()) {
          WebAppQuery q = new WebAppQuery();
          String name = webApp.getId();

          if (name.startsWith("/"))
            name = name.substring(1);

          q.setTag("wars/" + stage + "/" + host.getName() + "/" + name);

          q.setHost(host.getName());
          q.setUrl(webApp.getURL());

          apps.add(q);
        }
      }
    }

    getBroker()
      .queryResult(id, from, to, apps.toArray(new WebAppQuery[apps.size()]));

    return true;
  }

  /**
   * @deprecated
   **/
  @Query
  public boolean listTags(long id,
                          String to,
                          String from,
                          ListTagsQuery listQuery)
  {
    ArrayList<TagQuery> tags = new ArrayList<TagQuery>();

    for (String tag : _repository.getTagMap().keySet()) {
      if (tag.startsWith("wars/default") || tag.startsWith("ears/default")) {
        int p = "wars/default/".length();
        int q = tag.indexOf('/', p + 1);

        if (q < 0)
          continue;

        String host = tag.substring(p, q);
        // String name = tag.substring(q + 1);

        tags.add(new TagQuery(host, tag));
      }
    }

    getBroker()
      .queryResult(id, from, to, tags.toArray(new TagQuery[tags.size()]));

    return true;
  }

  /**
   * @deprecated
   **/
  @Query
  public boolean listHosts(long id,
                           String to,
                           String from,
                           ListHostsQuery query)
  {
    List<HostQuery> hosts = new ArrayList<HostQuery>();

    for (HostController controller : _server.getHostControllers()) {
      if ("admin.resin".equals(controller.getName()))
         continue;

      HostQuery q = new HostQuery();
      q.setName(controller.getName());

      hosts.add(q);
    }

    getBroker()
      .queryResult(id, from, to, hosts.toArray(new HostQuery[hosts.size()]));

    return true;
  }

  /**
   * @deprecated
   **/
  @Query
  public boolean status(long id,
                        String to,
                        String from,
                        StatusQuery query)
  {
    String tag = query.getTag();

    String errorMessage = statusMessage(tag);
    String state = null;

    StatusQuery result = new StatusQuery(tag, state, errorMessage);

    getBroker().queryResult(id, from, to, result);

    return true;
  }

  private String statusMessage(String tag)
  {
    int p = tag.indexOf('/');
    int q = tag.indexOf('/', p + 1);
    int r = tag.lastIndexOf('/');

    if (p < 0 || q < 0 || r < 0)
      return L.l("'{0}' is an unknown type", tag);

    String type = tag.substring(0, p);
    // String stage = tag.substring(p + 1, q);
    String host = tag.substring(q + 1, r);
    String name = tag.substring(r + 1);

    // String state = null;
    String errorMessage = tag + " is an unknown resource";

    try {
      if (type.equals("ears")) {
        String pattern
          = "resin:type=EApp,Host=" + host + ",name=" + name;

        EAppMXBean ear = (EAppMXBean) Jmx.findGlobal(pattern);

        if (ear != null) {
          ear.update();
          // state = ear.getState();
          errorMessage = ear.getErrorMessage();

          return errorMessage;
        }
        else
          return L.l("'{0}' is an unknown ear", tag);
      }
      else if (type.equals("wars")) {
        String pattern
          = "resin:type=WebApp,Host=" + host + ",name=/" + name;

        WebAppMXBean war = (WebAppMXBean) Jmx.findGlobal(pattern);

        if (war != null) {
          war.update();
          // state = war.getState();
          errorMessage = war.getErrorMessage();

          return errorMessage;
        }
        else
          return L.l("'{0}' is an unknown war", tag);
      }
      else
        return L.l("'{0}' is an unknown tag", tag);
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);
    }

    return errorMessage;
  }

  private DeployControllerMXBean findController(String tag)
  {
    int p = tag.indexOf('/');
    int q = tag.indexOf('/', p + 1);
    int r = tag.lastIndexOf('/');

    if (p < 0 || q < 0 || r < 0)
      return null;

    String type = tag.substring(0, p);
    // String stage = tag.substring(p + 1, q);

    String host;
    String name;

    if (q < r) {
      host = tag.substring(q + 1, r);
      name = tag.substring(r + 1);
    }
    else {
      host = tag.substring(q + 1);
      name = "";
    }

    try {
      if (type.equals("ears")) {
        String pattern
          = "resin:type=EApp,Host=" + host + ",name=" + name;

        EAppMXBean ear = (EAppMXBean) Jmx.findGlobal(pattern);

        return ear;
      }
      else if (type.equals("wars")) {
        String pattern
          = "resin:type=WebApp,Host=" + host + ",name=/" + name;

        WebAppMXBean war = (WebAppMXBean) Jmx.findGlobal(pattern);

        return war;
      }
    } catch (Exception e) {
      log.log(Level.FINEST, e.toString(), e);
    }

    return null;
  }
  
  static class BlobStreamSource extends StreamSource {
    private RepositorySpi _repository;
    private String _sha1;
    
    BlobStreamSource(RepositorySpi repository, String sha1)
    {
      _repository = repository;
      _sha1 = sha1;
    }
    
    /**
     * Returns an input stream, freeing the results
     */
    @Override
    public InputStream getInputStream()
      throws IOException
    {
        return _repository.openRawGitFile(_sha1);
    }

    /**
     * Returns an input stream, without freeing the results
     */
    @Override
    public InputStream openInputStream()
      throws IOException
    {
      return _repository.openRawGitFile(_sha1);
    }
  }
}
