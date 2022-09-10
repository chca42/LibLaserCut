/*
  This file is part of LibLaserCut.
  Copyright (C) 2011 - 2014 Thomas Oster <mail@thomas-oster.de>

  LibLaserCut is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  LibLaserCut is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with LibLaserCut. If not, see <http://www.gnu.org/licenses/>.

 */
package de.thomas_oster.liblasercut.drivers;

import de.thomas_oster.liblasercut.IllegalJobException;
import de.thomas_oster.liblasercut.LaserJob;
import de.thomas_oster.liblasercut.ProgressListener;
import de.thomas_oster.liblasercut.platform.Util;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * This class implements a driver for Grbl based firmwares.
 *
 * @author Michael Adams <zap@michaeladams.org>
 */
public class GrblCompact extends Grbl
{
  public GrblCompact()
  {
    //set some grbl-specific defaults
    setLineend("LF");
    setIdentificationLine("Grbl");
    // Grbl uses "ok" flow control
    setWaitForOKafterEachLine(true);
    setPreJobGcode("G21,G90,G92X0Y0,G91,M4");
    // turn off laser before returning to home position
    setPostJobGcode("M5,G90,G0X0Y0");
    // Grbl & MrBeam use 1000 for 100% PWM on the spindle/laser
    setSpindleMax(1000.0f);
    // Grbl doesn't turn off laser during G0 rapids
    setBlankLaserDuringRapids(true);
    // grbl can take a while to answer if doing a lot of slow moves
    setSerialTimeout(30000);
    // set better resolutions for short coordinated in incremental mode
    setSupportedResolutions("127,254");
    // enable shorter numbers in gcode
    setGCodeDigits(2);
    // use bidirectional rastering by default to increase speed
    setUseBidirectionalRastering(true);
    
    // blank these so that connect automatically uses serial first
    setHttpUploadUrl(null);
    setHost(null);
  }
  
  protected static final String SETTING_AUTO_HOME = "Automatically home laser cutter";
  
  @Override
  public String[] getPropertyKeys()
  {
    List<String> result = new LinkedList<>(Arrays.asList(super.getPropertyKeys()));
    // allow using octoprint
    result.add(GenericGcodeDriver.SETTING_HOST);
    // allow setting travel speed
    result.add(GenericGcodeDriver.SETTING_TRAVEL_SPEED);
    // allow setting line ending
    result.add(GenericGcodeDriver.SETTING_LINEEND);
    return result.toArray(new String[0]);
  }
  
  @Override
  public String getModelName()
  {
    return "Grbl Compact Gcode Driver";
  }
  
  protected double lastX = 0;
  protected double lastY = 0;
  
  @Override
  protected void move(PrintStream out, double x, double y, double resolution) throws IOException {
    x = isFlipXaxis() ? getBedWidth() - Util.px2mm(x, resolution) : Util.px2mm(x, resolution);
    y = isFlipYaxis() ? getBedHeight() - Util.px2mm(y, resolution) : Util.px2mm(y, resolution);
    currentSpeed = getTravel_speed();
    
    // incremental mode (G91)
    double tmpX = x;
    double tmpY = y;
    x = x - this.lastX;
    y = y - this.lastY;
    this.lastX = tmpX;
    this.lastY = tmpY;

    String sx = "";
    String sy = "";
    if((x > 0.001) || (x < -0.001))
      sx = "X" + formatDouble(x, getGCodeDigits());
    if((y > 0.001) || (y < -0.001))
      sy = "Y" + formatDouble(y, getGCodeDigits());

    if (blankLaserDuringRapids)
    {
      currentPower = -1; // set to invalid value to force new S-value at next G1
      sendLine("G0" + sx + sy + "S0");
    }
    else
    {
      sendLine("G0" + sx + sy);
    }
  }
  
  @Override
   protected void line(PrintStream out, double x, double y, double resolution) throws IOException {
    x = isFlipXaxis() ? getBedWidth() - Util.px2mm(x, resolution) : Util.px2mm(x, resolution);
    y = isFlipYaxis() ? getBedHeight() - Util.px2mm(y, resolution) : Util.px2mm(y, resolution);
    String append = "";

    if (super.nextPower != super.currentPower)
    {
      append += String.format(FORMAT_LOCALE, "S%s", formatDouble(super.nextPower, getSCodeDigits()));
      super.currentPower = super.nextPower;
    }
    if (super.nextSpeed != super.currentSpeed)
    {
      append += String.format(FORMAT_LOCALE, "F%d", (int) (max_speed*super.nextSpeed/100.0));
      super.currentSpeed = super.nextSpeed;
    }
    
    // incremental mode (G91)
    double tmpX = x;
    double tmpY = y;
    x = x - this.lastX;
    y = y - this.lastY;
    this.lastX = tmpX;
    this.lastY = tmpY;
    
    String sx = "";
    String sy = "";
    if((x > 0.001) || (x < -0.001))
      sx = "X" + formatDouble(x, getGCodeDigits());
    if((y > 0.001) || (y < -0.001))
      sy = "Y" + formatDouble(y, getGCodeDigits());
    
    sendLine("G1" + sx + sy + append);
  }

  @Override
  public void saveJob(OutputStream fileOutputStream, LaserJob job) throws IllegalJobException, Exception {
    this.lastX = 0;
    this.lastY = 0;
    super.saveJob(fileOutputStream, job);
  }

  @Override
  public void sendJob(LaserJob job, ProgressListener pl, List<String> warnings) throws IllegalJobException, Exception {
    this.lastX = 0;
    this.lastY = 0;
    super.sendJob(job, pl, warnings);
  }

  @Override
  public GrblCompact clone()
  {
    GrblCompact clone = new GrblCompact();
    clone.copyProperties(this);
    return clone;
  }
}
