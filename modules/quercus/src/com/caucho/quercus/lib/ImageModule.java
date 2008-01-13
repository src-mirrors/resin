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

package com.caucho.quercus.lib;

import com.caucho.quercus.QuercusException;
import com.caucho.quercus.QuercusModuleException;
import com.caucho.quercus.annotation.NotNull;
import com.caucho.quercus.annotation.Optional;
import com.caucho.quercus.annotation.ReturnNullAsFalse;
import com.caucho.quercus.env.*;
import com.caucho.quercus.module.AbstractQuercusModule;
import com.caucho.util.L10N;
import com.caucho.vfs.Path;
import com.caucho.vfs.ReadStream;
import com.caucho.vfs.WriteStream;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorConvertOp;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.RescaleOp;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * PHP image
 */
public class ImageModule extends AbstractQuercusModule {
  private static final Logger log
    = Logger.getLogger(ImageModule.class.getName());
  private static final L10N L = new L10N(ImageModule.class);

  public static final long IMG_GIF = 0x1;
  public static final long IMG_JPG = 0x2;
  public static final long IMG_JPEG = 0x2;
  public static final long IMG_PNG = 0x4;
  public static final long IMG_WBMP = 0x8;
  public static final long IMG_XPM = 0x10;

  public static final int IMAGETYPE_GIF = 1;
  public static final int IMAGETYPE_JPG = 2;
  public static final int IMAGETYPE_JPEG = 2;
  public static final int IMAGETYPE_PNG = 3;
  public static final int IMAGETYPE_SWF = 4;
  public static final int IMAGETYPE_PSD = 5;
  public static final int IMAGETYPE_BMP = 6;
  public static final int IMAGETYPE_TIFF_II = 7;
  public static final int IMAGETYPE_TIFF_MM = 8;
  public static final int IMAGETYPE_JPC = 9;
  public static final int IMAGETYPE_JP2 = 10;
  public static final int IMAGETYPE_JPX = 11;
  public static final int IMAGETYPE_JB2 = 12;
  public static final int IMAGETYPE_SWC = 13;
  public static final int IMAGETYPE_IFF = 14;
  public static final int IMAGETYPE_WBMP = 15;
  public static final int IMAGETYPE_XBM = 16;

  public static final int IMG_COLOR_STYLED = -2;
  public static final int IMG_COLOR_BRUSHED = -3;

  private static final int PNG_IHDR = pngCode("IHDR");

  public static final int IMG_ARC_PIE = 0;
  public static final int IMG_ARC_CHORD = 1;
  public static final int IMG_ARC_NOFILL = 2;
  public static final int IMG_ARC_EDGED = 4;

  public static final int IMG_FILTER_NEGATE = 0;
  public static final int IMG_FILTER_GRAYSCALE = 1;
  public static final int IMG_FILTER_BRIGHTNESS = 2;
  public static final int IMG_FILTER_CONTRAST = 3;
  public static final int IMG_FILTER_COLORIZE = 4;
  public static final int IMG_FILTER_EDGEDETECT = 5;
  public static final int IMG_FILTER_EMBOSS = 6;
  public static final int IMG_FILTER_GAUSSIAN_BLUR = 7;
  public static final int IMG_FILTER_SELECTIVE_BLUR = 8;
  public static final int IMG_FILTER_MEAN_REMOVAL = 9;
  public static final int IMG_FILTER_SMOOTH = 10;

  public String []getLoadedExtensions()
  {
    return new String[] { "gd" };
  }

  /**
   * Returns the environment value.
   */
  public Value getimagesize(Env env,
			    Path file,
			    @Optional ArrayValue imageArray)
  {
    if (! file.canRead())
      return BooleanValue.FALSE;

    ImageInfo info = new ImageInfo();

    ReadStream is = null;

    try {
      is = file.openRead();

      if (! parseImageSize(is, info))
	return BooleanValue.FALSE;
    } catch (Exception e) {
      log.log(Level.FINE, e.toString(), e);

      return BooleanValue.FALSE;
    } finally {
      is.close();
    }

    if (imageArray == null)
      imageArray = new ArrayValueImpl();

    imageArray.put(new LongValue(info._width));
    imageArray.put(new LongValue(info._height));
    imageArray.put(new LongValue(info._type));
    imageArray.put(env.createString("width=\"" + info._width +
				   "\" height=\"" + info._height + "\""));

    if (info._bits >= 0)
      imageArray.put(env.createString("bits"), new LongValue(info._bits));

    if (info._type == IMAGETYPE_JPEG)
      imageArray.put("channels", 3);

    if (info._mime != null)
      imageArray.put("mime", info._mime);

    return imageArray;
  }

  /**
   * Parses the image size from the file.
   */
  private static boolean parseImageSize(ReadStream is, ImageInfo info)
    throws IOException
  {
    int ch;

    ch = is.read();

    if (ch == 137) {
      // PNG - http://www.libpng.org/pub/png/spec/iso/index-object.html
      if (is.read() != 'P' ||
          is.read() != 'N' ||
          is.read() != 'G' ||
          is.read() != '\r' ||
          is.read() != '\n' ||
          is.read() != 26 ||
          is.read() != '\n')
        return false;

      return parsePNGImageSize(is, info);
    }
    else if (ch == 'G') {
      // GIF
      if (is.read() != 'I' ||
          is.read() != 'F' ||
          is.read() != '8' ||
          ((ch = is.read()) != '7' && ch != '9') ||
          is.read() != 'a')
        return false;

      return parseGIFImageSize(is, info);
    }
    else if (ch == 0xff) {
      // JPEG
      if (is.read() != 0xd8)
        return false;

      return parseJPEGImageSize(is, info);
    }
    else
      return false;
  }

  /**
   * Parses the image size from the PNG file.
   */
  private static boolean parsePNGImageSize(ReadStream is, ImageInfo info)
    throws IOException
  {
    int length;

    while ((length = readInt(is)) > 0) {
      int type = readInt(is);

      if (type == PNG_IHDR) {
	int width = readInt(is);
	int height = readInt(is);
	int depth = is.read() & 0xff;
	int color = is.read() & 0xff;
	int compression = is.read() & 0xff;
	int filter = is.read() & 0xff;
	int interlace = is.read() & 0xff;

	info._width = width;
	info._height = height;
	info._type = IMAGETYPE_PNG;

	info._bits = depth;

	info._mime = "image/png";

	return true;
      }
      else {
	for (int i = 0; i < length; i++) {
	  if (is.read() < 0)
	    return false;
	}
      }

      int crc = readInt(is);
    }

    return false;
  }

  /**
   * Parses the image size from the PNG file.
   */
  private static boolean parseGIFImageSize(ReadStream is, ImageInfo info)
    throws IOException
  {
    int length;

    int width = (is.read() & 0xff) + 256 * (is.read() & 0xff);
    int height = (is.read() & 0xff) + 256 * (is.read() & 0xff);

    int flags = is.read() & 0xff;

    info._width = width;
    info._height = height;
    info._type = IMAGETYPE_GIF;

    info._bits = flags & 0x7;

    info._mime = "image/gif";

    return true;
  }

  /**
   * Parses the image size from the PNG file.
   */
  private static boolean parseJPEGImageSize(ReadStream is, ImageInfo info)
    throws IOException
  {
    int ch;

    while ((ch = is.read()) == 0xff) {
      ch = is.read();

      if (ch == 0xff) {
	is.unread();
      }
      else if (0xd0 <= ch && ch <= 0xd9) {
	// rst
      }
      else if (0x01 == ch) {
	// rst
      }
      else if (ch == 0xc0) {
	int len = 256 * is.read() + is.read();

	int bits = is.read();
	int height = 256 * is.read() + is.read();
	int width = 256 * is.read() + is.read();

	info._width = width;
	info._height = height;
	info._type = IMAGETYPE_JPEG;

	info._bits = bits;

	info._mime = "image/jpeg";

	return true;
      }
      else {
	int len = 256 * is.read() + is.read();

	is.skip(len - 2);
      }
    }


    return false;
  }

  private static int pngCode(String code)
  {
    return ((code.charAt(0) << 24) |
	    (code.charAt(1) << 16) |
	    (code.charAt(2) << 8) |
	    (code.charAt(3)));
  }

  private static int readInt(ReadStream is)
    throws IOException
  {
    return (((is.read() & 0xff) << 24) |
	    ((is.read() & 0xff) << 16) |
	    ((is.read() & 0xff) << 8) |
	    ((is.read() & 0xff)));
  }

  /**
   * Retrieve information about the currently installed GD library
   */
  public static Value gd_info()
  {
    return (new ArrayValueImpl()
	    .append(StringValue.create("GD Version"), // ] => 2.0 or higher
		    StringValue.create("2.0 or higher"))
	    .append(StringValue.create("FreeType Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("FreeType Linkage"), // ] => with freetype
		    StringValue.create("with freetype"))
	    .append(StringValue.create("T1Lib Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("GIF Read Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("GIF Create Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("JPG Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("PNG Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("WBMP Support"), // ] => 1
		    BooleanValue.TRUE)
	    .append(StringValue.create("XPM Support"), // ] => 
		    BooleanValue.FALSE)
	    .append(StringValue.create("XBM Support"), // ] => 
		    BooleanValue.FALSE)
	    .append(StringValue.create("JIS-mapped Japanese Font Support"), // ] => 
		    BooleanValue.FALSE));
  }

  /**
   * Returns the imagetypes.
   */
  public static long imagetypes()
  {
    return IMG_GIF | IMG_JPG | IMG_PNG;
  }

  /**
   * Get file extension for image type
   */
  public static Value image_type_to_extension(int imageType, boolean dot)
  {
    switch(imageType) {
      case IMAGETYPE_GIF:     return StringValue.create(dot ? ".gif" : "gif");
      case IMAGETYPE_JPG:     return StringValue.create(dot ? ".jpg" : "jpg");
      case IMAGETYPE_PNG:     return StringValue.create(dot ? ".png" : "png");
      case IMAGETYPE_SWF:     return StringValue.create(dot ? ".swf" : "swf");
      case IMAGETYPE_PSD:     return StringValue.create(dot ? ".psd" : "psd");
      case IMAGETYPE_BMP:     return StringValue.create(dot ? ".bmp" : "bmp");
      case IMAGETYPE_TIFF_II: return StringValue.create(dot ? ".tiff" : "tiff");
      case IMAGETYPE_TIFF_MM: return StringValue.create(dot ? ".tiff" : "tiff");
      case IMAGETYPE_JPC:     return StringValue.create(dot ? ".jpc" : "jpc");
      case IMAGETYPE_JP2:     return StringValue.create(dot ? ".jp2" : "jp2");
      case IMAGETYPE_JPX:     return StringValue.create(dot ? ".jpf" : "jpf");
      case IMAGETYPE_JB2:     return StringValue.create(dot ? ".jb2" : "jb2");
      case IMAGETYPE_SWC:     return StringValue.create(dot ? ".swc" : "swc");
      case IMAGETYPE_IFF:     return StringValue.create(dot ? ".iff" : "iff");
      case IMAGETYPE_WBMP:    return StringValue.create(dot ? ".wbmp" : "wbmp");
      case IMAGETYPE_XBM:     return StringValue.create(dot ? ".xbm" : "xbm");
    }
    throw new QuercusException("unknown imagetype " + imageType);
  }

  /**
   * Get Mime-Type for image-type returned by getimagesize, exif_read_data,
   * exif_thumbnail, exif_imagetype
   */
  public static Value image_type_to_mime_type(int imageType)
  {
    switch(imageType) {
      case IMAGETYPE_GIF:
	return StringValue.create("image/gif");
      case IMAGETYPE_JPG:
	return StringValue.create("image/jpeg");
      case IMAGETYPE_PNG:
	return StringValue.create("image/png");
      case IMAGETYPE_SWF:
	return StringValue.create("application/x-shockwave-flash");
      case IMAGETYPE_PSD:
	return StringValue.create("image/psd");
      case IMAGETYPE_BMP:
	return StringValue.create("image/bmp");
      case IMAGETYPE_TIFF_II:
	return StringValue.create("image/tiff");
      case IMAGETYPE_TIFF_MM:
	return StringValue.create("image/tiff");
      case IMAGETYPE_JPC:
	return StringValue.create("application/octet-stream");
      case IMAGETYPE_JP2:
	return StringValue.create("image/jp2");
      case IMAGETYPE_JPX:
	return StringValue.create("application/octet-stream");
      case IMAGETYPE_JB2:
	return StringValue.create("application/octet-stream");
      case IMAGETYPE_SWC:
	return StringValue.create("application/x-shockwave-flash");
      case IMAGETYPE_IFF:
	return StringValue.create("image/iff");
      case IMAGETYPE_WBMP:
	return StringValue.create("image/vnd.wap.wbmp");
      case IMAGETYPE_XBM:
	return StringValue.create("image/xbm");
    }
    throw new QuercusException("unknown imageType " + imageType);
  }


  /**
   * Output image to browser or file
   */
  public static boolean imagegif(Env env, QuercusImage image)
  {
    try {
      ImageIO.write(image._bufferedImage, "gif", env.getOut());
      return true;
    }
    catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Output a PNG image to either the browser or a file
   */
  public static boolean imagepng(Env env, QuercusImage image)
  {
    try {
      ImageIO.write(image._bufferedImage, "png", env.getOut());
      return true;
    }
    catch (IOException e) {
      throw new QuercusModuleException(e);
    }
  }

  /**
   * Output image to browser or file
   */
  public static boolean imagejpeg(Env env,
				  QuercusImage image,
				  @Optional Path path,
				  @Optional int quality)
  {
    try {
      if (path != null) {
	WriteStream os = path.openWrite();
	try {
	  ImageIO.write(image._bufferedImage, "jpeg", os);
	} finally {
	  os.close();
	}
      }
      else
	ImageIO.write(image._bufferedImage, "jpeg", env.getOut());
      return true;
    }
    catch (IOException e) {
      log.log(Level.FINE, e.toString(), e);
      
      return false;
    }
  }

  /**
   * Set the blending mode for an image
   */
  public static boolean imagealphablending(QuercusImage image,
					   boolean useAlphaBlending)
  {
    image.getGraphics().setComposite(useAlphaBlending
				     ? AlphaComposite.SrcOver
				     : AlphaComposite.Src);
    return true;
  }

  /**
   * Should antialias functions be used or not
   */
  public static boolean imageantialias(QuercusImage image,
				       boolean useAntiAliasing)
  {
    image.getGraphics().setRenderingHint(RenderingHints.KEY_ANTIALIASING,
					 useAntiAliasing
					 ? RenderingHints.VALUE_ANTIALIAS_ON
					 : RenderingHints.VALUE_ANTIALIAS_OFF);
    return true;
  }

  /**
   * Allocate a color for an image
   */
  public static long imagecolorallocate(QuercusImage image,
					int r, int g, int b)
  {
    return (( 0x7f      << 24) |
	    ((r & 0xff) << 16) |
	    ((g & 0xff) <<  8) |
	    ((b & 0xff) <<  0));
  }

  /**
   * Allocate a color for an image
   */
  public static long imagecolorallocatealpha(QuercusImage image,
					     int r, int g, int b, int a)
  {
    // PHP's alpha values are inverted and only 7 bits.
    int alpha = 0x7f - (a & 0xff);
    return ((alpha      << 24) |
	    ((r & 0xff) << 16) |
	    ((g & 0xff) <<  8) |
	    ((b & 0xff) <<  0) );
}

  /**
   * De-allocate a color for an image
   */
  public static boolean imagecolordeallocate(QuercusImage image, int rgb)
  {
    // no-op
    return true;
  }

  /** 
   * Get the index of the color of a pixel
   */
  public static long imagecolorat(QuercusImage image, int x, int y)
  {
    return image.getPixel(x, y);
  }

  /**
   * Get the index of the closest color to the specified color
   */
  public static long imagecolorclosest(QuercusImage image, int r, int g, int b)
  {
    return imagecolorallocate(image, r, g, b);
  }

  /**
   * Get the index of the closest color to the specified color + alpha
   */
  public static long imagecolorclosestalpha(QuercusImage image,
					     int r, int g, int b, int a)
  {
    return imagecolorallocatealpha(image, r, g, b, a);
  }

  /**
   * Get the index of the specified color
   */
  public static long imagecolorexact(QuercusImage image, int r, int g, int b)
  {
    return imagecolorallocate(image, r, g, b);
  }

  /**
   * Get the index of the specified color + alpha
   */
  public static long imagecolorexactalpha(QuercusImage image,
					   int r, int g, int b, int a)
  {
    return imagecolorallocatealpha(image, r, g, b, a);
  }

  /**
   * Makes the colors of the palette version of an image more closely
   * match the true color version
   */
  public static boolean imagecolormatch(QuercusImage image1,
					QuercusImage image2)
  {
    // no-op
    return true;
  }

  /**
   * Get the index of the specified color or its closest possible alternative
   */
  public static long imagecolorresolve(QuercusImage image, int r, int g, int b)
  {
    return imagecolorallocate(image, r, g, b);
  }

  /**
   * Get the index of the specified color + alpha or its closest possible
   * alternative
   */
  public static long imagecolorresolvealpha(QuercusImage image,
					     int r, int g, int b, int a)
  {
    return imagecolorallocatealpha(image, r, g, b, a);
  }

  /**
   * Get the colors for an index
   */
  public static ArrayValue imagecolorsforindex(QuercusImage image, int argb)
  {
    ArrayValue arrayValue = new ArrayValueImpl();
    arrayValue.put("red", (argb >> 16) & 0xff);
    arrayValue.put("green", (argb >>  8) & 0xff);
    arrayValue.put("blue", (argb >>  0) & 0xff);

    // PHP's alpha is backwards from the rest of the world...
    int alpha = 0x7f - ((argb >> 24) & 0xff);
    arrayValue.put("alpha", alpha);
    return arrayValue;
  }

  /**
   * Find out the number of colors in an image's palette
   */
  public static Value imagecolorstotal()
  {
    return LongValue.create(0);
  }

  /**
   * Create a new palette based image
   */
  public static Value imagecreate(int width, int height)
  {
    return new QuercusImage(width, height);
  }

  /**
   * Create a new image from file or URL
   */
  public static QuercusImage imagecreatefromgif(Env env, Path filename)
  {
    return new QuercusImage(env, filename);
  }

  /**
   * Create a new image from file or URL
   */
  @ReturnNullAsFalse
  public static QuercusImage imagecreatefromjpeg(Env env, Path filename)
  {
    try {
      return new QuercusImage(env, filename);
    } catch (Exception e) {
      env.warning(L.l("Can't open {0} as a jpeg image", filename));
      log.log(Level.FINE, e.toString(), e);

      return null;
    }
  }

  /**
   * Create a new image from file or URL
   */
  public static QuercusImage imagecreatefrompng(Env env, Path filename)
  {
    return new QuercusImage(env, filename);
  }

  /**
   * Create a new image from file or URL
   */
  public static Value imagecreatefromxbm(Env env, Path filename)
  {
    return new QuercusImage(env, filename);
  }

  /**
   * Create a new image from file or URL
   */
  public static QuercusImage imagecreatefromxpm(Env env, Path filename)
  {
    return new QuercusImage(env, filename);
  }

  /**
   * Create a new image from file or URL
   */
  public static QuercusImage imagecreatefromwbmp(Env env, Path filename)
  {
    return new QuercusImage(env, filename);
  }

  /**
   * Create a new image from the image stream in the string
   */
  public static QuercusImage imagecreatefromstring(Env env, InputStream data)
  {
    return new QuercusImage(data);
  }

  /**
   * Create a new true color image
   */
  public static Value imagecreatetruecolor(int width, int height)
  {
    return new QuercusImage(width, height);
  }

  /**
   * Destroy an image
   */
  public static boolean imagedestroy(QuercusImage image)
  {
    // no-op
    return true;
  }

  /**
   * Finds whether an image is a truecolor image
   */
  public static boolean imageistruecolor(QuercusImage image)
  {
    return true;
  }

  /**
   * Enable or disable interlace
   */
  public static boolean imageinterlace(QuercusImage image,
				       @Optional Boolean enable)
  {
    if (enable != null)
      image.setInterlace(enable);
    
    // no-op, can safely ignore (just makes images that load top-down)
    return true;
  }


  // Shapes ///////////////////////////////////////////////////////////

  /**
   * Set a single pixel
   */
  public static boolean imagesetpixel(QuercusImage image,
				      int x, int y, int color)
  {
    image.setPixel(x, y, color);
    return true;
  }

  /**
   * Draw a line
   */
  public static boolean imageline(QuercusImage image,
				  int x1, int y1, int x2, int y2, int color)
  {
    image.stroke(new Line2D.Float(x1, y1, x2, y2), color);
    return true;
  }

  /**
   * Draw a dashed line
   */
  public static boolean imagedashedline(QuercusImage image,
					int x1, int y1, int x2, int y2,
					int color)
  {
    Graphics2D g = image.getGraphics();
    Stroke stroke = g.getStroke();
    g.setColor(intToColor(color));
    g.setStroke(new BasicStroke(1, BasicStroke.JOIN_ROUND,
				BasicStroke.CAP_ROUND, 1,
				new float[] { 5, 5 }, 0));
    g.draw(new Line2D.Float(x1, y1, x2, y2));
    g.setStroke(stroke);
    return true;
  }

  /**
   * Draw a partial ellipse
   */
  public static boolean imagearc(QuercusImage image,
				 double cx, double cy,
				 double width, double height,
				 double start, double end,
				 int color)
  {
    Arc2D arc = new Arc2D.Double(cx-width/2, cy-height/2,
				 width, height, -1 * start, -1 *(end-start),
				 Arc2D.OPEN);
    image.stroke(arc, color);
    return true;
  }

  /**
   * Draw a partial ellipse and fill it
   */
  public static boolean imagefilledarc(QuercusImage image,
				       double cx, double cy,
				       double width, double height,
				       double start, double end,
				       int color,
				       int style)
  {
    int type = Arc2D.PIE;
    if ((style & IMG_ARC_CHORD)!=0) type = Arc2D.CHORD;
    if ((style & IMG_ARC_PIE)!=0)   type = Arc2D.PIE;
    Arc2D arc =
      new Arc2D.Double(cx-width/2, cy-height/2,
		       width, height, -1 * start,
		       -1 *(end-start), type);
    if ((style & IMG_ARC_NOFILL)==0) image.fill(arc, color);
    if ((style & IMG_ARC_EDGED)!=0)  image.stroke(arc, color);
    return true;
  }

  /**
   * Draw an ellipse
   */
  public static boolean imageellipse(QuercusImage image,
				     double cx, double cy,
				     double width, double height,
				     int color)
  {
    Shape shape = new Ellipse2D.Double(cx-width/2, cy-height/2, width, height);
    image.stroke(shape, color);
    return true;
  }

  /**
   * Draw a filled ellipse
   */
  public static boolean imagefilledellipse(QuercusImage image,
					   double cx, double cy,
					   double width, double height,
					   int color)
  {
    Ellipse2D ellipse =
      new Ellipse2D.Double(cx-width/2, cy-height/2, width, height);
    image.fill(ellipse, color);
    return true;
  }

  private static Polygon arrayToPolygon(ArrayValue points, int numPoints)
  {
    Polygon polygon = new Polygon();
    ArrayValue.Entry entry = points.getHead();
    for(int i=0; i<numPoints; i++) {
      int x = entry.getValue().toInt();
      entry = entry.getNext();
      int y = entry.getValue().toInt();
      entry = entry.getNext();
      polygon.addPoint(x, y);
    }
    return polygon;
  }

  /**
   * Draw a polygon
   */
  public static boolean imagepolygon(QuercusImage image, ArrayValue points,
				     int numPoints, int color)
  {
    image.stroke(arrayToPolygon(points, numPoints), color);
    return true;
  }

  /**
   * Draw a filled polygon
   */
  public static boolean imagefilledpolygon(QuercusImage image,
					   ArrayValue points,
					   int numPoints, int color)
  {
    image.fill(arrayToPolygon(points, numPoints), color);
    return true;
  }

  /**
   * Draw a rectangle
   */
  public static boolean imagerectangle(QuercusImage image, int x1, int y1,
				       int x2, int y2, int color)
  {
    if (x2 < x1) { int tmp = x1; x1 = x2; x2 = tmp; }
    if (y2 < y1) { int tmp = y1; y1 = y2; y2 = tmp; }
    image.stroke(new Rectangle2D.Float(x1, y1, x2-x1, y2-y1), color);
    return true;
  }

  /**
   * Draw a filled rectangle
   */
  public static boolean imagefilledrectangle(QuercusImage image, int x1, int y1,
					     int x2, int y2, int color)
  {
    image.fill(new Rectangle2D.Float(x1, y1, x2-x1+1, y2-y1+1), color);
    return true;
  }


  // Text ///////////////////////////////////////////////////////////

  /**
   * Draw a character horizontally
   */
  public static boolean imagechar(QuercusImage image, int font,
				  int x, int y, String c, int color)
  {
    Graphics2D g = image.getGraphics();
    g.setColor(intToColor(color));
    Font awtfont = image.getFont(font);
    int height = image.getGraphics().getFontMetrics(awtfont).getAscent();
    g.setFont(awtfont);
    g.drawString(c.substring(0, 1), x, y+height);
    return true;
  }

  /**
   * Draw a string horizontally
   */
  public static boolean imagestring(QuercusImage image, int font,
				  int x, int y, String s, int color)
  {
    Graphics2D g = image.getGraphics();
    g.setColor(intToColor(color));
    Font awtfont = image.getFont(font);
    int height = image.getGraphics().getFontMetrics(awtfont).getAscent();
    g.setFont(awtfont);
    g.drawString(s, x, y+height);
    
    return true;
  }

  /**
   * Draw a character vertically
   */
  public static boolean imagecharup(QuercusImage image, int font,
				    int x, int y, String c, int color)
  {
    Graphics2D g = (Graphics2D)image.getGraphics().create();
    g.rotate(-1 * Math.PI / 2);
    g.setColor(intToColor(color));
    Font awtfont = image.getFont(font);
    int height = image.getGraphics().getFontMetrics(awtfont).getAscent();
    g.setFont(awtfont);
    g.drawString(c.substring(0, 1), -1 * y, x+height);
    return true;
  }

  /**
   * Returns the width of the image.
   */
  public static int imagesx(@NotNull QuercusImage image)
  {
    if (image == null)
      return 0;
    
    return image.getWidth();
  }

  /**
   * Returns the height of the image.
   */
  public static int imagesy(@NotNull QuercusImage image)
  {
    if (image == null)
      return 0;
    
    return image.getHeight();

  }

  /**
   * Get font height.
   *
   * @param font a font previously loaded with {@link #imageloadfont},
   *             or 1 -5 for built-in fonts
   */
  public static int imagefontheight(int font)
  {
    if (font < 1)
      return 8;
    else if (font == 1)
      return 8;
    else if (font == 2)
      return 13;
    else if (font == 3)
      return 13;
    else if (font == 4)
      return 16;
    else if (font == 5)
      return 15;
    else
      return 15;
  }

  /**
   * Get font width.
   *
   * @param font a font previously loaded with {@link #imageloadfont},
   *             or 1 -5 for built-in fonts
   */
  public static int imagefontwidth(int font)
  {
    if (font < 1)
      return 5;
    else if (font == 1)
      return 5;
    else if (font == 2)
      return 6;
    else if (font == 3)
      return 7;
    else if (font == 4)
      return 8;
    else if (font == 5)
      return 9;
    else
      return 9;
  }


  // BitBlts //////////////////////////////////////////////////////////

  /**
   * Copy part of an image
   */
  public static boolean imagecopy(QuercusImage dest, QuercusImage src,
				  int dx, int dy, int sx, int sy, int w, int h)
  {
    dest.getGraphics().drawImage(src._bufferedImage,
				 dx, dy, dx+w, dy+h,
				 sx, sy, sx+w, sy+h, null);
    return true;
  }

  /**
   * Copy and merge part of an image
   */
  public static boolean imagecopymerge(QuercusImage dest, QuercusImage src,
				       int dx, int dy, int sx, int sy,
				       int w, int h, int pct)
  {
    BufferedImage rgba =
      new BufferedImage(dest.getWidth(), dest.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
    rgba.getGraphics().drawImage(src._bufferedImage, 0, 0, null);
    BufferedImageOp rescaleOp = 
      new RescaleOp(new float[] { 1, 1, 1, ((float)pct)/100 },
		    new float[] { 0, 0, 0, 0 },
		    null);
    BufferedImage rescaledImage =
      rescaleOp.filter(rgba, null);
    Graphics2D g = (Graphics2D)dest.getGraphics().create();
    g.setComposite(AlphaComposite.SrcOver);
    g.drawImage(rescaledImage,
		dx, dy, dx+w, dy+h,
		sx, sy, sx+w, sy+h, null);
    return true;
  }

  /**
   * Copy and merge part of an image with gray scale
   */
  public static boolean imagecopymergegray(QuercusImage dest, QuercusImage src,
					   int dx, int dy, int sx, int sy,
					   int w, int h, int pct)
  {
    BufferedImage rgba =
      new BufferedImage(dest.getWidth(), dest.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
    rgba.getGraphics().drawImage(src._bufferedImage, 0, 0, null);
    BufferedImageOp rescaleOp = 
      new RescaleOp(new float[] { 1, 1, 1, ((float)pct)/100 },
		    new float[] { 0, 0, 0, 0 },
		    null);
    BufferedImage rescaledImage =
      rescaleOp.filter(rgba, null);

    ColorConvertOp colorConvertOp =
      new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_GRAY), null);
    colorConvertOp.filter(dest._bufferedImage, dest._bufferedImage);

    Graphics2D g = (Graphics2D)dest.getGraphics().create();
    g.setComposite(AlphaComposite.SrcOver);
    g.drawImage(rescaledImage,
		dx, dy, dx+w, dy+h,
		sx, sy, sx+w, sy+h, null);
    return true;
  }

  /**
   * Copy and resize part of an image with resampling
   */
  public static boolean imagecopyresampled(QuercusImage dest, QuercusImage src,
					   int dx, int dy, int sx, int sy, 
					   int dw, int dh, int sw, int sh)
  {
    Graphics2D g = (Graphics2D)dest.getGraphics().create();
    g.setRenderingHint(RenderingHints.KEY_RENDERING,
		       RenderingHints.VALUE_RENDER_QUALITY);
    g.drawImage(src._bufferedImage,
		dx, dy, dx+dw, dy+dh,
		sx, sy, sx+sw, sy+sh, null);
    g.setRenderingHint(RenderingHints.KEY_RENDERING,
		       RenderingHints.VALUE_RENDER_DEFAULT);
    return true;
  }

  /**
   * Copy and resize part of an image
   */
  public static boolean imagecopyresized(QuercusImage dest, QuercusImage src,
					 int dx, int dy, int sx, int sy, 
					 int dw, int dh, int sw, int sh)
  {
    Graphics2D g = (Graphics2D)dest.getGraphics().create();
    g.drawImage(src._bufferedImage,
		dx, dy, dx+dw, dy+dh,
		sx, sy, sx+sw, sy+sh, null);
    return true;
  }


  // Drawing Styles ///////////////////////////////////////////////////////

  /**
   * Set the brush image for line drawing
   */
  public static boolean imagesetbrush(QuercusImage image, QuercusImage brush)
  {
    image.setBrush(brush);
    return true;
  }

  /**
   * Set the style for line drawing
   */
  public static boolean imagesetstyle(QuercusImage image, ArrayValue style)
  {
    image.setStyle(style);
    return true;
  }

  /**
   * Set the thickness for line
   */
  public static boolean imagesetthickness(QuercusImage image, int thickness)
  {
    image.setThickness(thickness);
    return true;
  }


  // Palette Management //////////////////////////////////////////////////

  /**
   * Copy the palette from one image to another
   */
  public static boolean imagepalettecopy(QuercusImage source,
					 QuercusImage dest)
  {
    return true;
  }


  // Non-TrueColor / Full-Channel-Alpha ////////////////////////////////////

  /**
   * Set the flag to save full alpha channel information (as opposed to
   * single-color transparency) when saving PNG images
   */
  public static boolean imagesavealpha(QuercusImage image, boolean set)
  {
    // no-op since we currently only support true-color, full-alpha channel
    return true;
  }

  /**
   * Set the color for the specified palette index
   */
  public static boolean imagecolorset(QuercusImage image, int index,
				      int r, int g, int b)
  {
    // no-op since we currently only support true-color, full-alpha channel
    return true;
  }

  /**
   * Define a color as transparent
   */
  public static long imagecolortransparent(QuercusImage image,
					   @Optional int color)
  {
    // form that includes the optional argument is a no-op since we
    // currently only support true-color, full-alpha channel
    return 0xFF000000;
  }


  // Fills //////////////////////////////////////////////////////////

  /**
   * Flood fill
   */
  public static boolean imagefill(QuercusImage image, int x, int y, int color)
  {
    image.flood(x, y, color);
    return true;
  }

  /**
   * Flood fill to specific color
   */
  public static boolean imagefilltoborder(QuercusImage image, int x, int y,
					  int border, int color)
  {
    image.flood(x, y, color, border);
    return true;
  }


  // Filters /////////////////////////////////////////////////////////

  /**
   * Apply a 3x3 convolution matrix, using coefficient div and offset
   */
  public static boolean imageconvolution(QuercusImage image, ArrayValue matrix,
					 double div, double offset)
  {
    // XXX: implement div and offset
    float[] kernelValues = new float[9];
    ArrayValue.Entry entry = matrix.getHead();
    for(int y=0; y<3; y++)
      for(int x=0; x<3; x++)
	{
	  kernelValues[x+y*3] =
	    (float)matrix
	    .get(LongValue.create(y))
	    .get(LongValue.create(x)).toDouble();
	}
    ConvolveOp convolveOp =
      new ConvolveOp(new Kernel(3, 3, kernelValues),
		     ConvolveOp.EDGE_NO_OP, null);
    BufferedImage bufferedImage =
      convolveOp.filter(image._bufferedImage, null);
    image._bufferedImage.getGraphics().drawImage(bufferedImage, 1, 0, null);
    return true;
  }

  /**
   * Applies a filter to an image
   */
  public static boolean imagefilter(Env env, QuercusImage image, int filterType,
				    @Optional int arg1, @Optional int arg2,
				    @Optional int arg3)
  {
    switch(filterType)
      {
	case IMG_FILTER_NEGATE:
	  // Reverses all colors of the image.
	  env.warning(L.l("imagefilter(IMG_FILTER_NEGATE) unimplemented"));
	  return false;

	case IMG_FILTER_GRAYSCALE:
	  // Converts the image into grayscale.
	  env.warning(L.l("imagefilter(IMG_FILTER_GRAYSCALE) unimplemented"));
	  return false;

	case IMG_FILTER_BRIGHTNESS:
	  // Changes brightness of the image. Arg1 sets level of brightness.
	  env.warning(L.l("imagefilter(IMG_FILTER_BRIGHTNESS) unimplementetd"));
	  return false;

	case IMG_FILTER_CONTRAST:
	  // Changes contrast of the image. Use arg1 to set level of contrast.
	  env.warning(L.l("imagefilter(IMG_FILTER_CONTRAST) unimplementetd"));
	  return false;

	case IMG_FILTER_COLORIZE:
	  // Like IMG_FILTER_GRAYSCALE, except you can specify the color. Use
	  // arg1, arg2 and arg3 in the form of red, blue, green. The range
	  // for each color is 0 to 255.
	  env.warning(L.l("imagefilter(IMG_FILTER_COLORIZE) unimplemented"));
	  return false;

	case IMG_FILTER_EDGEDETECT:
	  // Uses edge detection to highlight the edges in the image.
	  env.warning(L.l("imagefilter(IMG_FILTER_EDGEDETECT) unimplemented"));
	  return false;

	case IMG_FILTER_EMBOSS:
	  // Embosses the image.
	  env.warning(L.l("imagefilter(IMG_FILTER_EMBOSS) unimplemented"));
	  return false;

	case IMG_FILTER_GAUSSIAN_BLUR:
	  // Blurs the image using the Gaussian method.
	  env.warning(L.l("imagefilter(IMG_FILTER_GAUSSIAN_BLUR) "+
			  "unimplemented"));
	  return false;

	case IMG_FILTER_SELECTIVE_BLUR:
	  // Blurs the image.
	  env.warning(L.l("imagefilter(IMG_FILTER_SELECTIVE_BLUR) "+
			  "unimplemented"));
	  return false;

	case IMG_FILTER_MEAN_REMOVAL:
	  // Uses mean removal to achieve a "sketchy" effect.
	  env.warning(L.l("imagefilter(IMG_FILTER_MEAN_REMOVAL) "+
			  "unimplemented"));
	  return false;

	case IMG_FILTER_SMOOTH:
	  // Makes the image smoother. Use arg1 to set the level of smoothness.
	  env.warning(L.l("imagefilter(IMG_FILTER_SMOOTH) unimplemented"));
	  return false;

	default:
	  throw new QuercusException("unknown filterType in imagefilter()");
      }
  }


  // Other ///////////////////////////////////////////////////////////

  /**
   * Embe into single tags.
   */
  public static boolean iptcembed(String iptcdata, String jpegFileName,
				  @Optional int spool)
  {
    throw new QuercusException("iptcembed is not [yet] supported");
  }

  /**
   * Apply a gamma correction to a GD image
   */
  public static boolean imagegammacorrect(QuercusImage image,
					  float gammaBefore, float gammaAfter)
  {
    // this is a no-op in PHP; apparently the GD library dropped
    // support for gamma correction between v1.8 and v2.0
    return true;
  }

  /**
   * Rotate an image with a given angle
   */
  public static boolean imagerotate(QuercusImage image, float angle,
				    int backgroundColor,
				    @Optional int ignoreTransparent)
  {
    // this function is broken on most PHP installs: "Note: This
    // function is only available if PHP is compiled with the bundled
    // version of the GD library."
    return false;
  }


  // PHP Documentation Lacking /////////////////////////////////////////

  /**
   * Get the index of the color which has the hue, white and blackness
   * nearest to the given color
   */
  public static long imagecolorclosesthwb(QuercusImage image,
					  int r, int g, int b)
  {
    throw new QuercusException("imagecolorclosesthwb is not supported");
  }

  /**
   * Set the alpha blending flag to use the bundled libgd layering effects
   */
  public static boolean imagelayereffect(QuercusImage image, int effect)
  {
    // XXX: there is no documentation for how this function ought to work
    // http://us3.php.net/manual/en/function.imagelayereffect.php
    return false;
  }


  // Postscript/Truetype Fonts ////////////////////////////////////////////

  /**
   * Give the bounding box of a text using fonts via freetype2
   */
  public static ArrayValue imageftbbox(float size, float angle, Path fontFile,
				       String text,
				       @Optional ArrayValue extraInfo)
  {
    throw new QuercusException("imageftbbox() not implemented");
  }

  /**
   * Write text to the image using fonts using FreeType 2
   */
  public static ArrayValue imagefttext(QuercusImage image, float size,
				       float angle, int x, int y, int col,
				       Path fontFile, String text,
				       ArrayValue extraInfo)
  {
    throw new QuercusException("imagefttext() not implemented");
  }

  /**
   * Load a new font
   */
  public static long imageloadfont(Path file)
  {
    throw new QuercusException("imageloadfont() not implemented");
  }

  /**
   * Give the bounding box of a text rectangle using PostScript Type1 fonts
   */
  public static ArrayValue imagepsbbox(String text, int font, int size,
				       @Optional int space,
				       @Optional int tightness,
				       @Optional float angle)
  {
    throw new QuercusException("imagepsbbox() not implemented");
  }

  /**
   * Make a copy of an already loaded font for further modification
   */
  public static int imagepscopyfont(Value fontIndex)
  {
    throw new QuercusException("imagepscopyfont() not implemented");
  }

  /**
   * Change the character encoding vector of a font
   */
  public static boolean imagepsencodefont(Value fontIndex, Path encodingFile)
  {
    throw new QuercusException("imagepsencodefont() not implemented");
  }

  /**
   * Extend or condense a font
   */
  public static boolean imagepsextendfont(int fontIndex, float extend)
  {
    throw new QuercusException("imagepsextendfont() not implemented");
  }

  /**
   * Free memory used by a PostScript Type 1 font
   */
  public static boolean imagepsfreefont(Value fontIndex)
  {
    throw new QuercusException("imagepsfreefont() not implemented");
  }

  /**
   * Load a PostScript Type 1 font from file
   */
  public static Value imagepsloadfont(Path fontFile)
  {
    throw new QuercusException("imagepsloadfont() not implemented");
  }

  /**
   * Slant a font
   */
  public static boolean imagepsslantfont(Value fontIndex, float slant)
  {
    throw new QuercusException("imagepsslantfont() not implemented");
  }

  /**
   * To draw a text string over an image using PostScript Type1 fonts
   */
  public static ArrayValue imagepstext(QuercusImage image,
				       String text,
				       Value fontIndex,
				       int size, int fg, int bg, int x, int y,
				       @Optional int space,
				       @Optional int tightness,
				       @Optional float angle,
				       @Optional int antialias_steps)
  {
    throw new QuercusException("imagepstext() not implemented");
  }


  // WBMP Images ////////////////////////////////////////////////////////////

  /**
   * Output image to browser or file
   */
  public static void image2wbmp(QuercusImage image,
				@Optional Path filename,
				@Optional int threshhold)
  {
    throw new QuercusException("not supported");
  }

  /**
   * Convert JPEG image file to WBMP image file
   */
  public static void jpeg2wbmp(String jpegFilename,
			       String wbmpName,
			       int d_height,
			       int d_width,
			       int threshhold)
  {
    throw new QuercusException("not supported");
  }

  /**
   * Convert PNG image file to WBM
   */
  public static void png2wbmp(String pngFilename,
			      String wbmpName,
			      int d_height,
			      int d_width,
			      int threshhold)
  {
    throw new QuercusException("not supported");
  }



  // [Currently] Unsupported Image Formats //////////////////////////////////

  /**
   * Create a new image from GD2 file or URL
   */
  public static void imagecreatefromgd2(Path file)
  {
    throw new QuercusException(".gd images are not supported");
  }

  /**
   * Create a new image from a given part of GD2 file or URL
   */
  public static void imagecreatefromgd2part(Path file,
					    int srcX, int srcY,
					    int width, int height)
  {
    throw new QuercusException(".gd images are not supported");
  }

  /**
   * Create a new image from GD file or URL
   */
  public static void imagecreatefromgd(Path file)
  {
    throw new QuercusException(".gd images are not supported");
  }

  /**
   * Output GD2 image to browser or file
   */
  public static void imagegd2(QuercusImage image, @Optional Path file)
  {
    throw new QuercusException("imagegd2 is not implemented");
  }

  /**
   * Output GD image to browser or file
   */
  public static void imagegd(QuercusImage image, @Optional Path file)
  {
    throw new QuercusException("imagegd is not implemented");
  }


  // Private Helpers ////////////////////////////////////////////////////////

  private static Color intToColor(int argb)
  {
    // don't forget: PHP alpha channel is only 7 bits
    int alpha = argb >> 24;
    alpha <<= 1;
    alpha |= ((alpha & 0x2) >> 1);  // copy bit #2 to LSB

    return new Color((argb >> 16) & 0xff,
		     (argb >>  8) & 0xff,
		     (argb >>  0) & 0xff,
		     alpha);
  }

  // Inner Classes ////////////////////////////////////////////////////////

  static class ImageInfo {
    int _width;
    int _height;
    int _type;

    int _bits;

    String _mime;
  }

  public static class QuercusImage extends ResourceValue {
    private int _width;
    private int _height;
    BufferedImage _bufferedImage;
    private Graphics2D _graphics;
    private boolean _isInterlace;

    private BufferedImage _brush;
    private int[] _style;
    private int _thickness;

    public QuercusImage(int width, int height)
    {
      _width = width;
      _height = height;
      _bufferedImage =
	new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
      _graphics = (Graphics2D)_bufferedImage.getGraphics();
    }

    public QuercusImage(InputStream inputStream)
    {
      try {
	_bufferedImage = ImageIO.read(inputStream);
	_width = _bufferedImage.getWidth(null);
	_height = _bufferedImage.getHeight(null);
	_graphics = (Graphics2D)_bufferedImage.getGraphics();
      }
      catch (IOException e) {
	throw new QuercusException(e);
      }
    }

    public QuercusImage(Env env, Path filename)
    {
      try {
	_bufferedImage = ImageIO.read(filename.openRead());
	_width = _bufferedImage.getWidth(null);
	_height = _bufferedImage.getHeight(null);
	_graphics = (Graphics2D)_bufferedImage.getGraphics();
      }
      catch (IOException e) {
	throw new QuercusException(e);
      }
    }

    public String toString()
    {
      return "resource(Image)";
    }

    public void setInterlace(boolean isInterlace)
    {
      _isInterlace = isInterlace;
    }

    public boolean isInterlace()
    {
      return _isInterlace;
    }

    public int getPixel(int x, int y)
    {
      return _bufferedImage.getRGB(x, y);
    }

    public void setPixel(int x, int y, int color)
    {
      _bufferedImage.setRGB(x, y, color);
    }

    public Graphics2D getGraphics()
    {
      return _graphics;
    }

    public Font getFont(int font)
    {
      if (font <= 1)
	return new Font("sansserif", 0, 8);
      switch(font) {
	case 2: return new Font("sansserif", 0, 10);
	case 3: return new Font("sansserif", 0, 11);
	case 4: return new Font("sansserif", 0, 12);
	default:
	  return new Font("sansserif", 0, 14);
      }
    }

    public int getWidth()
    {
      return _bufferedImage.getWidth(null);
    }

    public int getHeight()
    {
      return _bufferedImage.getHeight(null);
    }

    public void fill(Shape shape, int color)
    {
      _graphics.setColor(intToColor(color));
      _graphics.fill(shape);
    }

    public void stroke(Shape shape, int color)
    {
      switch(color)
	{
	  case IMG_COLOR_STYLED:
	    strokeStyled(shape);
	    break;
	  case IMG_COLOR_BRUSHED:
	    strokeBrushed(shape);
	    break;
	  default:
	    _graphics.setColor(intToColor(color));
	    _graphics.setStroke(new BasicStroke(_thickness));
	    _graphics.draw(shape);
	    break;
	}
    }
    
    private void strokeStyled(Shape shape)
    {
      for(int i=0; i<_style.length; i++)
	{
	  _graphics.setColor(intToColor(_style[i]));
	  Stroke stroke =
	    new BasicStroke(_thickness,
			    BasicStroke.JOIN_ROUND, BasicStroke.CAP_ROUND, 1,
			    new float[] { 1, _style.length-1 },
			    i);
	  _graphics.setStroke(stroke);
	  _graphics.draw(shape);
	}
    }

    private void strokeBrushed(Shape shape)
    {
      // XXX: support "styled brushes" (see imagesetstyle() example on php.net)
      Graphics2D g = _graphics;
      FlatteningPathIterator fpi =
	new FlatteningPathIterator(shape.getPathIterator(g.getTransform()), 1);
      float[] floats = new float[6];
      fpi.currentSegment(floats);
      float last_x = floats[0];
      float last_y = floats[1];
      while(! fpi.isDone())
	{
	  fpi.currentSegment(floats);
	  int distance = (int)Math.sqrt((floats[0]-last_x)*(floats[0]-last_x)+
					(floats[1]-last_y)*(floats[1]-last_y));
	  if (distance <= 1) distance = 1;
	  for(int i=1; i<=distance; i++)
	    {
	      int x = (int)(floats[0]*i+last_x*(distance-i))/distance;
	      x -= _brush.getWidth() / 2;
	      int y = (int)(floats[1]*i+last_y*(distance-i))/distance;
	      y -= _brush.getHeight() / 2;
	      g.drawImage(_brush, x, y, null);
	    }
	  last_x = floats[0];
	  last_y = floats[1];
	  fpi.next();
	}
    }

    public void setThickness(int thickness)
    {
      _style = null;
      _thickness = thickness;
    }

    public void setStyle(ArrayValue colors)
    {
      _style = new int[colors.getSize()];
      ArrayValue.Entry e = colors.getHead();
      for(int i=0; i<_style.length; i++)
	{
	  _style[i] = e.getValue().toInt();
	  e = e.getNext();
	}
    }

    public void setBrush(QuercusImage image)
    {
      _brush = image._bufferedImage;
    }

    public BufferedImage getBrush()
    {
      return _brush;
    }

    public void flood(int x, int y, int color)
    {
      flood(x, y, color, 0, false);
    }

    public void flood(int x, int y, int color, int border)
    {
      flood(x, y, color, border, true);
    }

    private void flood(int startx, int starty, int color, int border, boolean useBorder)
    {
      java.util.Queue<Integer> xq = new LinkedList<Integer>();
      java.util.Queue<Integer> yq = new LinkedList<Integer>();
      xq.add(startx);
      yq.add(starty);
      color &= 0x00ffffff;
      border &= 0x00ffffff;
      
      int height = getHeight();

      while(xq.size() > 0)
      {
        int x = xq.poll();
        int y = yq.poll();
        int p = (getPixel(x, y) & 0x00ffffff);
        if (useBorder ? (p==border||p==color) : (p != 0)) continue;
        setPixel(x, y, color);

        for(int i = x - 1; i >= 0; i--)
        {
          p = (getPixel(i, y) & 0x00ffffff);
          if (useBorder ? (p==border||p==color) : (p!= 0)) break;
          setPixel(i, y, color);

          if (y + 1 < height) {
            xq.add(i);
            yq.add(y+1);
          }
          
          if (y - 1 >= 0) {
            xq.add(i);
            yq.add(y-1);
          }
        }

        for(int i = x + 1; i < getWidth(); i++)
        {
          p = (getPixel(i, y) & 0x00ffffff);
          if (useBorder ? (p==border||p==color) : (p != 0)) break;
          setPixel(i, y, color);
          
          if (y + 1 < height) {
            xq.add(i);
            yq.add(y+1);
          }
          
          if (y - 1 >= 0) {
            xq.add(i);
            yq.add(y-1);
          }
        }

        if (y + 1 < height) {
          xq.add(x);
          yq.add(y+1);
        }
        
        if (y - 1 >= 0) {
          xq.add(x);
          yq.add(y-1);
        }
      }
    }

  }


}

