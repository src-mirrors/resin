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

package com.caucho.resources;

import com.caucho.config.ConfigException;
import com.caucho.config.types.*;
import com.caucho.loader.*;
import com.caucho.util.*;
import com.caucho.webbeans.manager.*;
import com.caucho.server.connection.*;
import com.caucho.server.webapp.*;

import javax.annotation.PostConstruct;
import javax.el.*;
import javax.resource.spi.work.Work;
import javax.servlet.*;
import javax.webbeans.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.logging.*;

/**
 * The cron resources starts application Work tasks at cron-specified
 * intervals.
 */
public class ScheduledTask extends BeanConfig
  implements AlarmListener, EnvironmentListener
{
  private static final L10N L = new L10N(ScheduledTask.class);
  private static final Logger log
    = Logger.getLogger(ScheduledTask.class.getName());

  @In
  private Executor _threadPool;

  private ClassLoader _loader;
  
  private Trigger _trigger;
  
  private TimerTrigger _timerTrigger = new TimerTrigger();
  
  private Runnable _task;
  private MethodExpression _method;
  
  private String _url;
  private WebApp _webApp;
  
  private Alarm _alarm;

  private volatile boolean _isActive;

  /**
   * Constructor.
   */
  public ScheduledTask()
  {
    _loader = Thread.currentThread().getContextClassLoader();

    setBeanConfigClass(Runnable.class);
  }

  /**
   * Sets the delay
   */
  public void setDelay(Period delay)
  {
    _trigger = _timerTrigger;

    _timerTrigger.setFirstTime(Alarm.getExactTime() + delay.getPeriod());
  }

  /**
   * Sets the period
   */
  public void setPeriod(Period period)
  {
    _trigger = _timerTrigger;

    _timerTrigger.setPeriod(period.getPeriod());
  }

  /**
   * Sets the cron interval.
   */
  public void setCron(String cron)
  {
    _trigger = new CronType(cron);
  }

  /**
   * Sets the method expression as a task
   */
  public void setMethod(MethodExpression method)
  {
    _method = method;
  }

  /**
   * Sets the url expression as a task
   */
  public void setUrl(String url)
  {
    if (! url.startsWith("/"))
      throw new ConfigException(L.l("url '{0}' must be absolute", url));
    
    _url = url;

    ComponentFactory comp
      = WebBeansContainer.create().resolveByType(WebApp.class);

    if (comp == null)
      throw new ConfigException(L.l("relative url '{0}' requires web-app context",
				    url));

    _webApp = (WebApp) comp.get();
  }

  /**
   * Sets the work task.
   */
  @Deprecated
  public void setWork(Runnable work)
  {
    setTask(work);
  }

  /**
   * Sets the task.
   */
  public void setTask(Runnable task)
  {
    _task = task;

    setClass(task.getClass());
  }

  /**
   * Initialization.
   */
  @PostConstruct
  public void init()
    throws ConfigException
  {
    if (_method != null) {
      _task = new MethodTask(_method);
    }
    else if (_url != null) {
    }
    else
      super.init();

    if (_trigger == null) {
      _timerTrigger.setFirstTime(Long.MAX_VALUE / 2);
      _trigger = _timerTrigger;
    }

    Environment.addEnvironmentListener(this);
  }

  private void start()
  {
    long now = Alarm.getExactTime();
    
    long nextTime = _trigger.nextTime(now + 500);

    _isActive = true;

    if (_url != null)
      _task = new ServletTask(_url, _webApp);

    if (_task == null)
      _task = (Runnable) getObject();

    assert(_task != null);

    _alarm = new Alarm("cron-resource", this, nextTime - now);

    if (log.isLoggable(Level.FINER))
      log.finer(this + " started. Next event at " + new Date(nextTime));
  }

  private void stop()
  {
    _isActive = false;
    Alarm alarm = _alarm;
    _alarm = null;
    
    if (alarm != null)
      alarm.dequeue();

    if (_task instanceof Work)
      ((Work) _task).release();
    else if (_task instanceof TimerTask)
      ((TimerTask) _task).cancel();
  }

  /**
   * The runnable.
   */
  public void handleAlarm(Alarm alarm)
  {
    if (! _isActive)
      return;

    Thread thread = Thread.currentThread();
    ClassLoader oldLoader = thread.getContextClassLoader();
    try {
      thread.setContextClassLoader(_loader);
      
      log.fine(this + " executing " + _task);

      _threadPool.execute(_task);

      // XXX: needs QA
      long now = Alarm.getExactTime();
      long nextTime = _trigger.nextTime(now + 500);

      if (_isActive)
	alarm.queue(nextTime - now);
    } catch (Exception e) {
      log.log(Level.WARNING, e.toString(), e);
    } finally {
      thread.setContextClassLoader(oldLoader);
    }
  }

  public void environmentConfigure(EnvironmentClassLoader loader)
    throws ConfigException
  {
  }

  public void environmentBind(EnvironmentClassLoader loader)
    throws ConfigException
  {
  }
  
  public void environmentStart(EnvironmentClassLoader loader)
  {
    start();
  }
  
  public void environmentStop(EnvironmentClassLoader loader)
  {
    stop();
  }

  public String toString()
  {
    return getClass().getSimpleName() + "[" + _task + "," + _trigger + "]";
  }

  public static class MethodTask implements Runnable {
    private static final Object[] _args = new Object[0];
    
    private ELContext _elContext;
    private MethodExpression _method;

    MethodTask(MethodExpression method)
    {
      _method = method;
      _elContext = WebBeansContainer.create().getELContext();
    }

    public void run()
    {
      _method.invoke(_elContext, _args);
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[" + _method + "]";
    }
  }

  public class ServletTask implements Runnable {
    private String _url;
    private WebApp _webApp;

    ServletTask(String url, WebApp webApp)
    {
      _url = url;
      _webApp = webApp;
    }

    public void run()
    {
      StubServletRequest req = new StubServletRequest();
      StubServletResponse res = new StubServletResponse();

      try {
	RequestDispatcher dispatcher = _webApp.getRequestDispatcher(_url);
	
	dispatcher.forward(req, res);
      } catch (Exception e) {
	log.log(Level.FINE, e.toString(), e);
      }
    }

    public String toString()
    {
      return getClass().getSimpleName() + "[" + _url + "," + _webApp + "]";
    }
  }
}
