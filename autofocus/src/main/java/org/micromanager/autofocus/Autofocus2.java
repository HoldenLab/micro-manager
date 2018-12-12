///////////////////////////////////////////////////////////////////////////////
//FILE:           Autofocus2_.java
//PROJECT:        Micro-Manager
//SUBSYSTEM:      Autofocusing plug-in for mciro-manager and ImageJ
//-----------------------------------------------------------------------------
//
//AUTHOR:         Pakpoom Subsoontorn & Hernan Garcia, June, 2007
//                Nenad Amodaj, nenad@amodaj.com
//
//MODIFIED BY:    Kevin Whitley, 2018. Added option to use different exposure
//                time for autofocus routine, and different maximisation based
//                on StDev rather than on image sharpness.
//
//COPYRIGHT:      California Institute of Technology
//                University of California San Francisco
//                100X Imaging Inc, www.100ximaging.com
//                
//LICENSE:        This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
//CVS:            $Id: MetadataDlg.java 1275 2008-06-03 21:31:24Z nenad $
package org.micromanager.autofocus;

import com.google.common.eventbus.Subscribe;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Color;
import java.util.Arrays;
import java.util.Date;

import mmcorej.CMMCore;
import mmcorej.StrVector;

import org.micromanager.AutofocusPlugin;
import org.micromanager.Studio;
import org.micromanager.events.AutofocusPluginShouldInitializeEvent;
import org.micromanager.internal.utils.AutofocusBase;
import org.micromanager.internal.utils.PropertyItem;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/*
 * Created on June 2nd 2007
 * author: Pakpoom Subsoontorn & Hernan Garcia
 */

/**
 * ImageJ plugin wrapper for uManager.
 */

/* This plugin take a stack of snapshots and computes their sharpness

 */
@Plugin(type = AutofocusPlugin.class)
public class Autofocus extends AutofocusBase implements AutofocusPlugin, SciJavaPlugin  {

   private static final String KEY_SIZE_FIRST = "1st step size";
   private static final String KEY_NUM_FIRST = "1st step number";
   private static final String KEY_SIZE_SECOND = "2nd step size";
   private static final String KEY_NUM_SECOND = "2nd step number";
   private static final String KEY_THRES    = "Threshold";
   private static final String KEY_CROP_SIZE = "Crop ratio";
   private static final String KEY_CHANNEL = "Channel";
   private static final String KEY_EXPOSURE_TIME = "Exposure Time";
   private static final String KEY_SCORING_METHOD = "Maximize";
   private static final String NOCHANNEL = "";
   //private static final String AF_SETTINGS_NODE = "micro-manager/extensions/autofocus";
   
   private static final String AF_DEVICE_NAME = "JAF(H&P)";

   private Studio app_;
   private CMMCore core_;
   private ImageProcessor ipCurrent_ = null;

   public double SIZE_FIRST = 2;//
   public int NUM_FIRST = 1; // +/- #of snapshot
   public  double SIZE_SECOND = 0.2;
   public  int NUM_SECOND = 5;
   public double THRES = 0.02;
   public double CROP_SIZE = 0.2;
   public int EXPOSURE_TIME = 500; // ms
   public static String[] SCORING_METHOD = {"Edges","StDev"};
   private String scoringMethod = "Edges";
  
   public String CHANNEL="";

   private final double indx = 0; //snapshot show new window iff indx = 1 

   private boolean verbose_ = true; // displaying debug info or not

   private String channelGroup_;
   private double curDist;
   private double baseDist;
   private double bestDist;
   private double curSh;
   private double bestSh;
   private long t0;
   private long tPrev;
   private long tcur;

   public Autofocus() {
      super.createProperty(KEY_SIZE_FIRST, Double.toString(SIZE_FIRST));
      super.createProperty(KEY_NUM_FIRST, Integer.toString(NUM_FIRST));
      super.createProperty(KEY_SIZE_SECOND, Double.toString(SIZE_SECOND));
      super.createProperty(KEY_NUM_SECOND, Integer.toString(NUM_SECOND));
      super.createProperty(KEY_THRES, Double.toString(THRES));
      super.createProperty(KEY_CROP_SIZE, Double.toString(CROP_SIZE));
      super.createProperty(KEY_EXPOSURE_TIME, Integer.toString(EXPOSURE_TIME));
      super.createProperty(KEY_SCORING_METHOD, scoringMethod, SCORING_METHOD);
      super.createProperty(KEY_CHANNEL, CHANNEL);
   }

   @Subscribe
   public void onInitialize(AutofocusPluginShouldInitializeEvent event) {
      loadSettings();
   }

   @Override
   public void applySettings() {
      try {
         SIZE_FIRST = Double.parseDouble(getPropertyValue(KEY_SIZE_FIRST));
         NUM_FIRST = Integer.parseInt(getPropertyValue(KEY_NUM_FIRST));
         SIZE_SECOND = Double.parseDouble(getPropertyValue(KEY_SIZE_SECOND));
         NUM_SECOND = Integer.parseInt(getPropertyValue(KEY_NUM_SECOND));
         THRES = Double.parseDouble(getPropertyValue(KEY_THRES));
         CROP_SIZE = Double.parseDouble(getPropertyValue(KEY_CROP_SIZE));
         EXPOSURE_TIME = Integer.parseInt(getPropertyValue(KEY_EXPOSURE_TIME));
         scoringMethod = getPropertyValue(KEY_SCORING_METHOD);
         CHANNEL = getPropertyValue(KEY_CHANNEL);
      
      } catch (NumberFormatException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      } catch (Exception e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
   }

   public void run(String arg) {
      t0 = System.currentTimeMillis();
      bestDist = 5000;
      bestSh = 0;
      //############# CHECK INPUT ARG AND CORE ########
      verbose_ = arg.compareTo("silent") != 0;

      if (arg.compareTo("options") == 0){
         app_.app().showAutofocusDialog();
      }

      if (core_ == null) {
         // if core object is not set attempt to get its global handle
         core_ = app_.getCMMCore();
      }

      if (core_ == null) {
         IJ.error("Unable to get Micro-Manager Core API handle.\n" +
         "If this module is used as ImageJ plugin, Micro-Manager Studio must be running first!");
         return;
      }
      
      applySettings();

      //######################## START THE ROUTINE ###########

      try{
         IJ.log("Autofocus started.");
         boolean shutterOpen = core_.getShutterOpen();
         core_.setShutterOpen(true);
         boolean autoShutter = core_.getAutoShutter();
         core_.setAutoShutter(false);


         //########System setup##########
         if (!CHANNEL.equals(NOCHANNEL))
            core_.setConfig(channelGroup_, CHANNEL);
         core_.waitForSystem();
         if (core_.getShutterDevice().trim().length() > 0)
         {
            core_.waitForDevice(core_.getShutterDevice());
         }
         //delay_time(3000);


         //Snapshot, zdistance and sharpNess before AF 
         /* curDist = core_.getPosition(core_.getFocusDevice());
         indx =1;
         snapSingleImage();
         indx =0;

         tPrev = System.currentTimeMillis();
         curSh = sharpNess(ipCurrent_);
         tcur = System.currentTimeMillis()-tPrev;*/

         //set exposure time, save old one. -kw 181211
         int oldExposure = core_.getExposure();
         core_.setExposure(EXPOSURE_TIME);

         //set z-distance to the lowest z-distance of the stack
         curDist = core_.getPosition(core_.getFocusDevice());
         baseDist = curDist-SIZE_FIRST*NUM_FIRST;
         core_.setPosition(core_.getFocusDevice(),baseDist);
         core_.waitForDevice(core_.getFocusDevice());
         delay_time(300);

         IJ.log(" Before rough search: " +String.valueOf(curDist));


         //Rough search
         for(int i = 0; i < 2*NUM_FIRST+1; i++ ){
            tPrev = System.currentTimeMillis();

            core_.setPosition(core_.getFocusDevice(),baseDist+i*SIZE_FIRST);
            core_.waitForDevice(core_.getFocusDevice());


            curDist = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;    



            curSh = computeScore(ipCurrent_);

            if(curSh > bestSh){
               bestSh = curSh;
               bestDist = curDist;
            } 
            else if (bestSh - curSh > THRES*bestSh && bestDist < 5000) {
               break;
            }
            tcur = System.currentTimeMillis()-tPrev;

            //===IJ.log(String.valueOf(curDist)+" "+String.valueOf(curSh)+" " +String.valueOf(tcur));	
         }


         //===IJ.log("BEST_DIST_FIRST"+String.valueOf(bestDist)+" BEST_SH_FIRST"+String.valueOf(bestSh));

         baseDist = bestDist-SIZE_SECOND*NUM_SECOND;
         core_.setPosition(core_.getFocusDevice(),baseDist);
         delay_time(100);

         bestSh = 0;

         //Fine search
         for(int i = 0; i < 2*NUM_SECOND+1; i++ ){
            tPrev = System.currentTimeMillis();
            core_.setPosition(core_.getFocusDevice(),baseDist+i*SIZE_SECOND);
            core_.waitForDevice(core_.getFocusDevice());

            curDist = core_.getPosition(core_.getFocusDevice());
            // indx =1;
            snapSingleImage();
            // indx =0;    

            curSh = computeScore(ipCurrent_);

            if(curSh > bestSh){
               bestSh = curSh;
               bestDist = curDist;
            } 
            else if (bestSh - curSh > THRES*bestSh && bestDist < 5000){
               break;
            }
            tcur = System.currentTimeMillis()-tPrev;

            //===IJ.log(String.valueOf(curDist)+" "+String.valueOf(curSh)+" "+String.valueOf(tcur));
         }


         IJ.log("BEST_DIST_SECOND"+String.valueOf(bestDist)+" BEST_SH_SECOND"+String.valueOf(bestSh));

         core_.setPosition(core_.getFocusDevice(),bestDist);
         // indx =1;
         snapSingleImage();
         // indx =0;  
         core_.setShutterOpen(shutterOpen);
         core_.setAutoShutter(autoShutter);

         core_.setExposure(oldExposure); //set back to oringal value. -kw 181211
         IJ.log("Total Time: "+ String.valueOf(System.currentTimeMillis()-t0));
      }
      catch(Exception e)
      {
         IJ.error(e.getMessage());
      }     
   }

   //take a snapshot and save pixel values in ipCurrent_
   private boolean snapSingleImage() {

      try {
         core_.snapImage();
         Object img = core_.getImage();
         ImagePlus implus = newWindow();// this step will create a new window iff indx = 1
         implus.getProcessor().setPixels(img);
         ipCurrent_ = implus.getProcessor();
      } catch (Exception e) {
         IJ.log(e.getMessage());
         IJ.error(e.getMessage());
         return false;
      }

      return true;
   }

   //waiting    
   private void delay_time(double delay){
      Date date = new Date();
      long sec = date.getTime();
      while(date.getTime()<sec+delay){
         date = new Date();
      }
   }

   /*calculate the sharpness of a given image (in "impro").*/
   private double sharpNessp(ImageProcessor impro){


      int width =  (int)(CROP_SIZE*core_.getImageWidth());
      int height = (int)(CROP_SIZE*core_.getImageHeight());
      int ow = (int)(((1-CROP_SIZE)/2)*core_.getImageWidth());
      int oh = (int)(((1-CROP_SIZE)/2)*core_.getImageHeight());

      double[][] medPix = new double[width][height];
      double sharpNess = 0;
      double[] windo = new double[9];

      /*Apply 3x3 median filter to reduce noise*/
      for (int i=0; i<width; i++){
         for (int j=0; j<height; j++){

            windo[0] = (double)impro.getPixel(ow+i-1,oh+j-1);
            windo[1] = (double)impro.getPixel(ow+i,oh+j-1);
            windo[2] = (double)impro.getPixel(ow+i+1,oh+j-1);
            windo[3] = (double)impro.getPixel(ow+i-1,oh+j);
            windo[4] = (double)impro.getPixel(ow+i,oh+j);
            windo[5] = (double)impro.getPixel(ow+i+1,oh+j);
            windo[6] = (double)impro.getPixel(ow+i-1,oh+j+1);
            windo[7] = (double)impro.getPixel(ow+i,oh+j+1);
            windo[8] = (double)impro.getPixel(ow+i+1,oh+j+1);

            medPix[i][j] = median(windo);
         } 
      }

      /*Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp*/

      for (int k=1; k<width-1; k++){
         for (int l=1; l<height-1; l++){

            sharpNess = sharpNess + Math.pow((-2*medPix[k-1][l-1]- medPix[k][l-1]-medPix[k-1][l]+medPix[k+1][l]+medPix[k][l+1]+2*medPix[k+1][l+1]),2);

         } 
      }
      return sharpNess;
   }


   /**
    * calculate the sharpness of a given image (in "impro").
    * @param impro ImageJ Processor
    * @return sharpness score
    */
   @Override
   public double computeScore(final ImageProcessor impro){


      int width =  (int)(CROP_SIZE*core_.getImageWidth());
      int height = (int)(CROP_SIZE*core_.getImageHeight());
      int ow = (int)(((1-CROP_SIZE)/2)*core_.getImageWidth());
      int oh = (int)(((1-CROP_SIZE)/2)*core_.getImageHeight());

      // double[][] medPix = new double[width][height];
      double sharpNess = 0;
      //double[] windo = new double[9];

      /*Apply 3x3 median filter to reduce noise*/

      /*   for (int i=0; i<width; i++){
         for (int j=0; j<height; j++){

	    windo[0] = (double)impro.getPixel(ow+i-1,oh+j-1);
	    windo[1] = (double)impro.getPixel(ow+i,oh+j-1);
            windo[2] = (double)impro.getPixel(ow+i+1,oh+j-1);
            windo[3] = (double)impro.getPixel(ow+i-1,oh+j);
            windo[4] = (double)impro.getPixel(ow+i,oh+j);
            windo[5] = (double)impro.getPixel(ow+i+1,oh+j);
            windo[6] = (double)impro.getPixel(ow+i-1,oh+j+1);
            windo[7] = (double)impro.getPixel(ow+i,oh+j+1);
            windo[8] = (double)impro.getPixel(ow+i+1,oh+j+1);

            medPix[i][j] = median(windo);
         } 
	 }*/        

      //tPrev = System.currentTimeMillis();     

      
      impro.medianFilter();
      int[] ken = {2, 1, 0, 1, 0, -1, 0, -1, -2};
      impro.convolve3x3(ken);
      for (int i=0; i<width; i++){
         for (int j=0; j<height; j++){

            sharpNess = sharpNess + Math.pow(impro.getPixel(ow+i,oh+j),2);
         } 
      }

      // tcur = System.currentTimeMillis()-tPrev;

      /*Edge detection using a 3x3 filter: [-2 -1 0; -1 0 1; 0 1 2]. Then sum all pixel values. Ideally, the sum is large if most edges are sharp*/

      /*  for (int k=1; k<width-1; k++){
         for (int l=1; l<height-1; l++){

	     sharpNess = sharpNess + Math.pow((-2*medPix[k-1][l-1]- medPix[k][l-1]-medPix[k-1][l]+medPix[k+1][l]+medPix[k][l+1]+2*medPix[k+1][l+1]),2);

         } 
	 }*/

      return sharpNess;
   }


   //making a new window for a new snapshot.
   private ImagePlus newWindow(){
      ImagePlus implus;
      ImageProcessor ip;
      long byteDepth = core_.getBytesPerPixel();

      if (byteDepth == 1){
         ip = new ByteProcessor((int)core_.getImageWidth(),(int)core_.getImageHeight());
      } else  {
         ip = new ShortProcessor((int)core_.getImageWidth(), (int)core_.getImageHeight());
      }
      ip.setColor(Color.black);
      ip.fill();

      implus = new ImagePlus(String.valueOf(curDist), ip);
      if(indx == 1){
         if (verbose_) {
            // create image window if we are in the verbose mode
            ImageWindow imageWin = new ImageWindow(implus);
         }
      }
      return implus;
   }

   private double median(double[] arr){ 
      double [] newArray = Arrays.copyOf(arr, arr.length);
      Arrays.sort(newArray);
      int middle = newArray.length/2;
      return (newArray.length%2 == 1) ? newArray[middle] : (newArray[middle-1] + newArray[middle]) / 2.0;
   }

   @Override
   public double fullFocus() {
      run("silent");
      return 0;
   }

   @Override
   public String getVerboseStatus() {
      return "OK";
   }

   @Override
   public double incrementalFocus() {
      run("silent");
      return 0;
   }

   @Override
   public PropertyItem[] getProperties() {
      // use default dialog
      // make sure we have the right list of channels
            
      channelGroup_ = core_.getChannelGroup();
      StrVector channels = core_.getAvailableConfigs(channelGroup_);
      String allowedChannels[] = new String[(int)channels.size() + 1];
      allowedChannels[0] = NOCHANNEL;

      try {
         PropertyItem p = getProperty(KEY_CHANNEL);
         boolean found = false;
         for (int i=0; i<channels.size(); i++) {
            allowedChannels[i+1] = channels.get(i);
            if (p.value.equals(channels.get(i)))
               found = true;
         }
         p.allowed = allowedChannels;
         if (!found)
            p.value = allowedChannels[0];
         setProperty(p);
      } catch (Exception e1) {
         // TODO Auto-generated catch block
         e1.printStackTrace();
      }

      return super.getProperties();
   }
      
   void setCropSize(double cs) {
      CROP_SIZE = cs;
   }
   
   void setThreshold(double thr) {
      THRES = thr;
   }

   @Override
   public double getCurrentFocusScore() {
      // TODO Auto-generated method stub
      return 0;
   }

   @Override
   public int getNumberOfImages() {
      // TODO Auto-generated method stub
      return 0;
   }

   public void setContext(Studio app) {
      app_ = app;
      core_ = app.getCMMCore();
      app_.events().registerForEvents(this);
   }

   @Override
   public String getName() {
      return AF_DEVICE_NAME;
   }

   @Override
   public String getHelpText() {
      return AF_DEVICE_NAME;
   }

   @Override
   public String getCopyright() {
      return "Copyright UCSF, 100x Imaging 2007";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }
}   
