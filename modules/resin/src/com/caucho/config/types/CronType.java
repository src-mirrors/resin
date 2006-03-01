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
 *   Free SoftwareFoundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.config.types;

import java.util.regex.Pattern;

import com.caucho.util.CauchoSystem;
import com.caucho.util.L10N;
import com.caucho.util.QDate;

import com.caucho.vfs.Path;
import com.caucho.vfs.Vfs;

import com.caucho.config.ConfigException;

/**
 * Class-loading TypeBuilder
 */
public class CronType {
  static L10N L = new L10N(CronType.class);

  private static final long DAY = 24L * 3600L * 1000L;

  private final QDate _localCalendar = QDate.createLocal();
  
  private boolean []_minutes;
  private boolean []_hours;
  private boolean []_days;
  private boolean []_months;
  private boolean []_daysOfWeek;

  public CronType()
  {
  }
  
  /**
   * Sets the text.
   */
  public void addText(String text)
    throws ConfigException
  {
    text = text.trim();
    
    String []split = Pattern.compile("\\s+").split(text);

    if (split.length > 0)
      _minutes = parseRange(split[0], 0, 59);
    
    if (split.length > 1)
      _hours = parseRange(split[1], 0, 23);
    else
      _hours = parseRange("*", 0, 23);
    
    if (split.length > 2)
      _days = parseRange(split[2], 1, 31);
    
    if (split.length > 3)
      _months = parseRange(split[3], 1, 12);
    
    if (split.length > 4)
      _daysOfWeek = parseRange(split[4], 0, 7);
  }

  /**
   * parses a range, following cron rules.
   */
  private boolean []parseRange(String range, int rangeMin, int rangeMax)
    throws ConfigException
  {
    boolean []values = new boolean[rangeMax + 1];
    
    int j = 0;
    while (j < range.length()) {
      char ch = range.charAt(j);

      int min = 0;
      int max = 0;
      int step = 1;
      
      if (ch == '*') {
	min = rangeMin;
	max = rangeMax;
	j++;
      }
      else if ('0' <= ch && ch <= '9') {
	for (;
	     j < range.length() && '0' <= (ch = range.charAt(j)) && ch <= '9';
	     j++) {
	  min = 10 * min + ch - '0';
	}

	if (j < range.length() && ch == '-') {
	  for (j++;
	       j < range.length() && '0' <= (ch = range.charAt(j)) && ch <= '9';
	       j++) {
	    max = 10 * max + ch - '0';
	  }
	}
	else
	  max = min;
      }
      else
	throw new ConfigException(L.l("`{0}' is an illegal cron range",
				      range));

      if (min < rangeMin)
	throw new ConfigException(L.l("`{0}' is an illegal cron range (min value is too small)",
				      range));
      else if (rangeMax < max)
	throw new ConfigException(L.l("`{0}' is an illegal cron range (max value is too large)",
				      range));

      if (j < range.length() && (ch = range.charAt(j)) == '/') {
	step = 0;
	
	for (j++;
	     j < range.length() && '0' <= (ch = range.charAt(j)) && ch <= '9';
	     j++) {
	  step = 10 * step + ch - '0';
	}

	if (step == 0)
	  throw new ConfigException(L.l("`{0}' is an illegal cron range",
					range));
      }

      if (range.length() <= j) {
      }
      else if (ch == ',')
	j++;
      else {
	throw new ConfigException(L.l("`{0}' is an illegal cron range",
				      range));
      }

      for (; min <= max; min += step)
	values[min] = true;
    }

    return values;
  }

  public long nextTime(long now)
  {
    QDate cal = _localCalendar;

    now = now + 60000 - now % 60000;
    
    synchronized (cal) {
      cal.setGMTTime(now);

      int minute = nextInterval(_minutes, cal.getMinute());

      if (minute < 0) {
	minute = nextInterval(_minutes, 0);
	
	cal.setHour(cal.getHour() + 1);
      }

      int hour = nextInterval(_hours, cal.getHour());
      if (hour < 0) {
	hour = nextInterval(_hours, 0);
	minute = nextInterval(_minutes, 0);
	
	cal.setDayOfMonth(cal.getDayOfMonth() + 1);
      }

      int day = cal.getDayOfMonth();

      if (_days != null) {
	day = nextInterval(_days, cal.getDayOfMonth());

	if (day < 0) {
	  cal.setMonth(cal.getMonth() + 1);
	  cal.setDayOfMonth(1);

	  day = nextInterval(_days, cal.getDayOfMonth());
	  hour = nextInterval(_hours, 0);
	  minute = nextInterval(_minutes, 0);
	}
      }

      if (_daysOfWeek != null) {
	int oldDayOfWeek = cal.getDayOfWeek();
	int dayOfWeek = nextInterval(_daysOfWeek, oldDayOfWeek);

	if (dayOfWeek > 0) {
	  day += (dayOfWeek - oldDayOfWeek);
	}
	else {
	  dayOfWeek = nextInterval(_daysOfWeek, 0);
	  
	  day += (dayOfWeek - oldDayOfWeek + 7);
	}
      }

      int month = cal.getMonth();
      int year = (int) cal.getYear();
      
      return nextTime(year, month, day, hour, minute);
    }
  }

  private long nextTime(int year, int month, int day, int hour, int minute)
  {
    QDate cal = _localCalendar;
    
    cal.setLocalTime(0);

    cal.setYear(year);
    cal.setMonth(month);
    cal.setDayOfMonth(day);
    cal.setHour(hour);
    cal.setMinute(minute);

    return cal.getGMTTime();
  }
    
  public int nextInterval(boolean []values, int now)
  {
    for (; now < values.length; now++) {
      if (values[now])
	return now;
    }

    return -1;
  }
}
