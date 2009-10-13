/*
 * Copyright (c) 1998-2008 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2
 * as published by the Free Software Foundation.
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

package com.caucho.admin;

import java.util.concurrent.ConcurrentHashMap;

public class ProbeManager {
  private static ProbeManager _manager = new ProbeManager();

  private final ConcurrentHashMap<String,Probe> _probeMap
    = new ConcurrentHashMap<String,Probe>();

  protected ProbeManager()
  {
  }

  protected void setManager(ProbeManager manager)
  {
    _manager = manager;
  }

  public static ProbeManager getCurrent()
  {
    return _manager;
  }

  public static AverageTimeProbe createAverageTimeProbe(String name)
  {
    return _manager.createAverageTimeProbeImpl(name);
  }

  private AverageTimeProbe createAverageTimeProbeImpl(String name)
  {
    Probe probe = _probeMap.get(name);

    if (probe == null) {
      probe = createProbe(new AverageTimeProbe(name));
    }
    
    return (AverageTimeProbe) probe;
  }

  public static SampleCountProbe createSampleCountProbe(String name)
  {
    return _manager.createSampleCountProbeImpl(name);
  }

  private SampleCountProbe createSampleCountProbeImpl(String name)
  {
    Probe probe = _probeMap.get(name);

    if (probe == null) {
      probe = createProbe(new SampleCountProbe(name));
    }
    
    return (SampleCountProbe) probe;
  }

  public static TimeProbe createTimeProbe(String name)
  {
    return _manager.createTimeProbeImpl(name);
  }

  private TimeProbe createTimeProbeImpl(String name)
  {
    Probe probe = _probeMap.get(name);

    if (probe == null) {
      probe = createProbe(new TimeProbe(name));
    }
    
    return (TimeProbe) probe;
  }

  public static TimeRangeProbe createTimeRangeProbe(String baseName)
  {
    return _manager.createTimeRangeProbeImpl(baseName);
  }

  private TimeRangeProbe createTimeRangeProbeImpl(String baseName)
  {
    String timeName = baseName + " Time";
    
    Probe probe = _probeMap.get(timeName);

    if (probe == null) {
      probe = createProbe(new TimeRangeProbe(timeName));
      
      TimeRangeProbe timeRangeProbe = (TimeRangeProbe) probe;

      String countName = baseName + " Count";
      createProbe(timeRangeProbe.createCount(countName));
      
      String maxName = baseName + " Max";
      createProbe(timeRangeProbe.createMax(maxName));
    }
    
    return (TimeRangeProbe) probe;
  }

  public static AverageProbe createAverageProbe(String name, String type)
  {
    return _manager.createAverageProbeImpl(name, type);
  }

  private AverageProbe createAverageProbeImpl(String baseName, String type)
  {
    String name = baseName + " " + type;
    
    Probe probe = _probeMap.get(name);

    if (probe == null) {
      probe = createProbe(new AverageProbe(name));
      
      AverageProbe averageProbe = (AverageProbe) probe;

      String countName = baseName + " Count";
      createProbe(averageProbe.createCount(countName));
      
      String sigmaName = name + " 95%";
      createProbe(averageProbe.createSigma(sigmaName, 3));
      
      String maxName = name + " Max";
      createProbe(averageProbe.createMax(maxName));
    }
    
    return (AverageProbe) probe;
  }

  public static ActiveTimeProbe createActiveTimeProbe(String name)
  {
    return _manager.createActiveTimeProbeImpl(name, "Time", null);
  }

  public static ActiveTimeProbe createActiveTimeProbe(String name,
                                                      String type,
                                                      String subName)
  {
    return _manager.createActiveTimeProbeImpl(name, type, subName);
  }

  private ActiveTimeProbe
    createActiveTimeProbeImpl(String baseName,
                              String type,
                              String subName)
  {
    if (subName != null)
      subName = "|" + subName;
    else
      subName = "";
    
    String name = baseName + " " + type + subName;
    
    Probe probe = _probeMap.get(name);

    if (probe == null) {
      probe = createProbe(new ActiveTimeProbe(name));
      
      ActiveTimeProbe activeTimeProbe = (ActiveTimeProbe) probe;

      /*
      String activeCountName = baseName + " Active" + subName;
      createProbe(activeTimeProbe.createActiveCount(activeCountName));
      */

      String activeMaxName = baseName + " Active Max" + subName;
      createProbe(activeTimeProbe.createActiveCountMax(activeMaxName));

      String totalCountName = baseName + " Total" + subName;
      createProbe(activeTimeProbe.createTotalCount(totalCountName));
      
      String sigmaName = baseName + " " + type + " 95%" + subName;
      createProbe(activeTimeProbe.createSigma(sigmaName, 3));
      
      String maxName = baseName + " " + type + " Max" + subName;
      createProbe(activeTimeProbe.createMax(maxName));
    }
    
    return (ActiveTimeProbe) probe;
  }

  public static SemaphoreProbe createSimpleSemaphoreProbe(String name)
  {
    return _manager.createSemaphoreProbeImpl(name, false);
  }

  /**
   * Creates a semaphore probe and generate Count, Min, and Max probe.
   */
  public static SemaphoreProbe createSemaphoreProbe(String name)
  {
    return _manager.createSemaphoreProbeImpl(name, true);
  }

  private SemaphoreProbe createSemaphoreProbeImpl(String baseName,
                                                  boolean isExtended)
  {
    String name = baseName;
    
    Probe probe = _probeMap.get(name);

    if (probe == null)
      probe = createProbe(new SemaphoreProbe(name));
      
    SemaphoreProbe semaphoreProbe = (SemaphoreProbe) probe;

    if (! isExtended)
      return semaphoreProbe;

    String countName = baseName + " Acquire";
    createProbe(semaphoreProbe.createCount(countName));
      
    String maxName = name + " Max";
    createProbe(semaphoreProbe.createMax(maxName));
      
    String minName = name + " Min";
    createProbe(semaphoreProbe.createMin(minName));
    
    return (SemaphoreProbe) probe;
  }

  protected Probe createProbe(Probe newProbe)
  {
    Probe probe = _probeMap.putIfAbsent(newProbe.getName(), newProbe);

    if (probe != null) {
      return probe;
    }
    else {
      registerProbe(newProbe);

      return newProbe;
    }
  }

  protected void registerProbe(Probe probe)
  {
  }
}
