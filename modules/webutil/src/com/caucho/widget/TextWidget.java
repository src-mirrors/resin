/*
 * Copyright (c) 1998-2004 Caucho Technology -- all rights reserved
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
 * @author Sam
 */


package com.caucho.widget;

import com.caucho.util.L10N;

import java.io.IOException;
import java.util.logging.Logger;

public class TextWidget
  extends Widget
{
  private static L10N L = new L10N( TextWidget.class );

  static protected final Logger log = 
    Logger.getLogger( TextWidget.class.getName() );

  public TextWidget()
  {
  }

  public TextWidget( String id )
  {
    super( id );
  }

  public TextWidget( Widget parent )
  {
    super( parent );
  }

  public TextWidget( Widget parent, String id )
  {
    super( parent, id );
  }
  
  public Object put(Object key, Object value)
  {
    throw new UnsupportedOperationException();
  }

  protected TextWidgetState createState( WidgetConnection connection )
    throws WidgetException
  {
    return new TextWidgetState();
  }

  protected boolean isActionParameter( WidgetState state )
  {
    return state.getWidgetMode().equals( WidgetMode.EDIT );
  }

  public void renderTextHtml( WidgetConnection connection,
                              TextWidgetState widgetState )
    throws WidgetException, IOException
  {
    WidgetMode widgetMode = widgetState.getWidgetMode();

    if ( widgetMode.equals( WidgetMode.HIDDEN ) ) {
      return;
    }

    WidgetWriter out = connection.getWriter();

    String value = widgetState.getValue();

    out.startElement( "span" );

    out.writeAttribute( "id", getClientId() );
    out.writeAttribute( "class",getCssClass() );

    if ( widgetState.getWidgetMode().equals( WidgetMode.EDIT ) ) {

      out.startElement( "input" );

      out.writeAttribute( "name", getParameterName() );

      if ( value != null )
        out.writeAttribute( "value", value );

      out.endElement( "input" );
    }
    else {
      if ( value != null ) 
        out.writeText( value );
    }

    out.endElement( "span" );
  }
}
