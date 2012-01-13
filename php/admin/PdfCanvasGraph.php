<?php

import java.lang.System;

class PdfCanvasGraph 
{
  private $canvas;
  
  private $title;
  
  private $x_range;
  private $y_range;
  
  private $pixel_size;
  private $ppu; // pixels per unit
  
  private $valid = true;

  public function PdfCanvasGraph($canvas,
                                 $title,
                                 $pixel_size,
                                 $x_range,
                                 $y_range)
  {
    $this->canvas = $canvas;
    
    $this->title = $title;
    
    $this->pixel_size = $pixel_size;
    
    $this->x_range = $x_range;
    $this->y_range = $y_range;
    
    if ($this->y_range->size() == 0.0)
      $this->setInvalid("y_range was 0");

    $this->ppu = new Size();
    $this->ppu->width = $this->pixel_size->width / $this->x_range->size();
    $this->ppu->height = $this->pixel_size->height / $this->y_range->size();

    if ($this->ppu->width > 0.0 && $this->ppu->height > 0.0) {
      $this->xOffsetPixels = $this->x_range->start * $this->ppu->width;
      $this->yOffsetPixels = $this->y_range->start * $this->ppu->height;
    } else {
      $this->setInvalid("pixels per unit was width={$this->ppu->width} height={$this->ppu->height}");
    }
  }

  public function __toString() 
  {
    return "PdfCanvasGraph($this->title,x_range={$this->x_range},y_range={$this->y_range},size={$this->pixel_size},ppu={$this->ppu})";
  }
  
  public function setInvalid($msg)
  {
    $this->valid = false;
    $this->debug("WARNING: Graph {$this->title} is invalid: $msg");
  }
  
  public function debug($text) 
  {
    $this->canvas->debug($text);
  }
  
  public function end() 
  {
    $this->canvas->endGraph();
  }

  public function convertPoint($point) 
  {
    $convertedPoint = new Point();
    
    $convertedPoint->x = intval(($point->x  * $this->ppu->width) - $this->xOffsetPixels);
    $convertedPoint->y = intval(($point->y  * $this->ppu->height) - $this->yOffsetPixels);
    
    if ($convertedPoint->x > 1000 || $convertedPoint->x < 0)
       $this->setInvalid("Point out of range x axis: {$convertedPoint->x}");
    
    if ($convertedPoint->y > 1000 || $convertedPoint->y < 0)
       $this->setInvalid("Point out of range y axis: {$convertedPoint->y}");
       
    $this->debug("convertPoint $point to $convertedPoint");
    
    return $convertedPoint;
  }
  
  public function setFont($font_name, $font_size)
  {
    $this->canvas->setFont($font_name, $font_size);
  }

  public function setColor($name)
  {
    $this->canvas->setColor($name);
  }
  
  public function setRGBColor($color)
  {
    $this->canvas->setRGBColor($color);
  }
  
  public function setFontAndColor($font_name, $font_size, $color_name)
  {
    $this->canvas->setFontAndColor($font_name, $font_size, $color_name);
  }
  
  public function setLineWidth($width)
  {
    $this->canvas->setLineWidth($width);
  }
    
  public function drawTitle($color) 
  {
    $this->debug("drawTitle:color=$color");
    
    $this->setFontAndColor("Helvetica", 12, $color);

    $x = 0.0;
    $y = $this->pixel_size->height + $this->canvas->getLineHeight()/2;
    
    if ($this->valid) {
       $this->canvas->writeTextXY($x, $y, $this->title);
    } else {
      $this->debug("drawTitle failed: no data");
      $this->canvas->writeTextXY($x, $y, "{$this->title} no data");
    }
  }

  public function drawLegends($legends, $point=new Point(0.0, -20)) 
  {
    if (! $this->valid)
       return;

    $this->debug("drawLegends");

    $col2 =   (double) $this->pixel_size->width / 2;
    $index = 0;
    $yinc = -7;
    $initialYLoc = -20;
    $yloc = $initialYLoc;

    foreach ($legends as $legend) {
      if ($index % 2 == 0) {
	      $xloc = 0.0;
      } else {
	      $xloc = $col2;
      }
    
      $row = floor($index / 2);
      
      $yloc = ((($row) * $yinc) + $initialYLoc);

      $this->drawLegend($legend->color, $legend->name, new Point($xloc, $yloc));
      $index++;
    }
  }

  public function drawLegend($color, $name, $point=new Point(0.0, -20)) 
  {
    if (! $this->valid)
       return;
 
    $this->debug("drawLegend $name");

    $x = $point->x;
    $y = $point->y;

    $this->canvas->setColor($color);
    
    $this->canvas->moveToXY($x, $y+2.5);
    $this->canvas->lineToXY($x+5, $y+5);
    $this->canvas->lineToXY($x+10, $y+2.5);
    $this->canvas->lineToXY($x+15, $y+2.5);
    $this->canvas->stroke();

    $this->setFontAndColor("Helvetica-Bold", 6, "black");
    $this->canvas->writeTextXY($x+20, $y, $name);
  }
  
  public function drawLineGraph($dataLine, $rgbColor, $lineWidth)
  {
    if (! $this->valid)
       return;
    
    $this->debug("drawLineGraph:dataLine=$dataLine,rgbColor=$rgbColor,lineWidth=$lineWidth");
    
    $this->setRGBColor($rgbColor);
    $this->setLineWidth($lineWidth);
    
    $this->canvas->moveToPoint($this->convertPoint($dataLine[0]));

    for ($index = 1; $index < sizeof($dataLine); $index++) {
      $p = $this->convertPoint($dataLine[$index]);
      
      if (! $this->valid)
      	 break;
      
      $this->canvas->lineToPoint($p);
    }
    
    $this->canvas->stroke();
  }

  public function drawGrid($color)
  {
    $this->debug("drawGrid:color=$color");
    
    $this->canvas->setLineWidth(1);
    $this->setColor($color);
    
    $width = (double) $this->pixel_size->width;
    $height = (double) $this->pixel_size->height;
    
    $this->canvas->moveToXY(0.0, 0.0);
    $this->canvas->lineToXY($width, 0.0);
    $this->canvas->lineToXY($width, $height);
    $this->canvas->lineToXY(0.0, $height);
    $this->canvas->lineToXY(0.0, 0.0);
    $this->canvas->stroke();
  }

  public function drawGridLines($xstep, $ystep, $color)
  {
    $this->setLineWidth(0.5);
    $this->setColor($color);
    
    if (! $ystep)
      $this->setInvalid("No ystep was passed");

    if (!$this->valid)
       return;

    $this->debug("drawGridLines:xstep=$xstep,ystep=$ystep,color=$color");

    $width = intval($this->pixel_size->width);
    $height = intval($this->pixel_size->height);

    $xstep_width = $xstep * $this->ppu->width;
    $ystep_width = $ystep * $this->ppu->height;

    if ($xstep_width <= 0.0 || $ystep_width <= 0.0) {
       $this->setInvalid("Step width was 0");
       return;
    }

    for ($index = 0; $width >= (($index)*$xstep_width); $index++) {
      $currentX = intval($index*$xstep_width);
      $this->canvas->moveToXY($currentX, 0.0);
      $this->canvas->lineToXY($currentX, $height);
      $this->canvas->stroke();
    }    

    for ($index = 0; $height >= ($index*$ystep_width); $index++) {
      $currentY = intval($index*$ystep_width);
      $this->canvas->moveToXY(0.0, $currentY);
      $this->canvas->lineToXY($width, $currentY);
      $this->canvas->stroke();
    }    
  }

  public function drawXGridLabels($xstep, $func=null)
  {
    if (! $this->valid)
       return;

    $width = (double) $this->pixel_size->width;
    $xstep_width = ($xstep) * $this->ppu->width;

    $this->debug("drawXGridLabels:xstep=$xstep,func=$func,width=$width,xstep_width=$xstep_width");
    
    $this->setFontAndColor("Helvetica", 9, "black");
    
    for ($index = 0; $width >= ($index*$xstep_width); $index++) {
      $currentX = $index*$xstep_width;
      $stepValue = (int) $index * $xstep;
      $currentValue = $stepValue + (int) $this->x_range->start;
      $currentValue = intval($currentValue);

      if (!$func) {
      	$currentLabel = $currentValue;
      } else {
	      $currentLabel = $this->$func($currentValue);
      }
      
      $this->canvas->writeTextXY($currentX-3, -10, $currentLabel);
    }
  }

  public function drawYGridLabels($ystep, $func=null, $xpos=-28) 
  {
    if (! $this->valid)
       return;

    $this->debug("drawYGridLabels:ystep=$ystep,func=$func,xpos=$xpos");

    $this->setFontAndColor("Helvetica", 9, "black");
    $height = (double) $this->pixel_size->height;

    $step_width = ($ystep) * $this->ppu->height;

    for ($index = 0; $height >= ($index*$step_width); $index++) {
      $currentYPixel = $index*$step_width;
      $currentYValue =	($index * $ystep) + $this->y_range->start;
      
      if ($func) {
	      $currentLabel = $func($currentYValue);
      } else {
      	if ($currentYValue >      1000000000) {
	        $currentLabel = "" . $currentYValue / 1000000000 . "G";
	      } elseif ($currentYValue > 1000000) {
	        $currentLabel = "" . $currentYValue / 1000000 . "M";
	      } elseif ($currentYValue > 1000) {
	        $currentLabel = "" . $currentYValue / 1000 . "K";
	      } else {
	        $currentLabel = $currentYValue; 
	      }
      }

      $x = -5;
      
      $this->canvas->writeTextXYRight($x, $currentYPixel - 3, $currentLabel);
    }
  }
  
  function formatTime($ms)
  {
    $time = $ms / 1000;
    $tz = date_offset_get(new DateTime);
   
    if (($time + $tz) % (24 * 3600) == 0) {
      return strftime("%m-%d", $time);
    } else {
      return strftime("%H:%M", $time);
    }
  }
}

class GraphData 
{
  private $name;
  private $dataLine;
  private $max;
  private $yincrement;
  private $color;

  public function __set($name, $value)
  {
    $this->$name = $value;
  }

  public function __get($name)
  {
    return $this->$name;
  }
  
  public function __toString() 
  {
    return "GraphData(name={$this->name},dataLine={$this->dataLine},max={$this->max},yincrement={$this->yincrement},color=$color)";
  }

  public function validate() 
  {
    if (sizeof($this->dataLine) == 0) {
      return false;
    }

    if ($this->max == 0) {
      $this->max=10;
      $this->yincrement=1;
    }
    
    return true;
  }
}

?>
