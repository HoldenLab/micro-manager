///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.quickaccess.internal.controls;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;
import java.awt.Frame;

import java.io.File;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;

import net.miginfocom.swing.MigLayout;

import org.micromanager.quickaccess.QuickAccessPlugin;
import org.micromanager.quickaccess.WidgetPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.dialogs.AcqControlDlg;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMScriptException;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Implements the "Run Saved MDA" button, which loads a specific MDA settings
 * file and then runs the MDA.
 */
@Plugin(type = WidgetPlugin.class)
public class SavedMDAButton extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Run Saved Acq.";
   }

   @Override
   public String getHelpText() {
      return "Run a saved acquisition (as generated by clicking on the \"Save\" button in the Multi-D Acq. dialog)";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Open Imaging, Inc.";
   }

   @Override
   public ImageIcon getIcon() {
      return new ImageIcon(IconLoader.loadFromResource(
            "/org/micromanager/icons/film_file@2x.png"));
   }

   @Override
   public boolean getCanCustomizeIcon() {
      return true;
   }

   @Override
   public JComponent createControl(PropertyMap config) {
      final File file = new File(config.getString("settingsPath", ""));
      // Size it to mostly fill its frame.
      JButton result = new JButton(file.getName(),
            studio_.quickAccess().getCustomIcon(config,
               IconLoader.getIcon("/org/micromanager/icons/film_file.png"))) {
         @Override
         public Dimension getPreferredSize() {
            return QuickAccessPlugin.getPaddedCellSize();
         }
      };
      result.setFont(GUIUtils.buttonFont);
      result.setMargin(new Insets(0, 0, 0, 0));
      result.setOpaque(false);
      result.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            if (!file.exists()) {
               studio_.logs().showError("Unable to find settings file at " +
                  file.getAbsolutePath());
            }
            else {
               runAcquisition(file.getAbsolutePath());
            }
         }
      });
      return result;
   }

   /**
    * This is just here to make our indentation level a bit less insane.
    */
   private void runAcquisition(final String path) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               studio_.compat().loadAcquisition(path);
               studio_.compat().runAcquisition();
            }
            catch (MMScriptException e) {
               studio_.logs().showError(e, "Unable to run acquisition");
            }
         }
      }).start();
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      File file = FileDialogs.openFile(parent,
            "Choose acquisition settings file",
            AcqControlDlg.ACQ_SETTINGS_FILE);
      if (file == null) {
         return null;
      }
      return studio_.data().getPropertyMapBuilder()
         .putString("settingsPath", file.getAbsolutePath()).build();
   }
}
