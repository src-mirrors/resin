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
 * @author Emil Ong
 */

package com.caucho.bam;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The @PresenceUnavailable annotation marks a SimpleBamService method
 * as handling a presence unavailable notification.  Chat, multi-user
 * chat, and publish/subscribe applications use presence unavailable
 * packets to notify a service that a user has logged out.
 *
 * <code><pre>
 * @PresenceUnavailable
 * void unavailable(String to, String from, MyUnavailable value)
 * </pre></code>
 *
 * A logging presence unavailable handler would look like:
 *
 * <code><pre>
 * @PresenceUnavailable
 * public void pingUnavailable(String to,
 *                             String from,
 *                             MyUnavailable value)
 * {
 *   System.out.println("Ping: " + value
 *                      + " {to:" + to + ", from:" + from + "}");
 * }
 * </pre></code>
 */
@Target({METHOD})
@Retention(RUNTIME)
@Documented  
public @interface PresenceUnavailable {
}
