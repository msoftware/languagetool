/* JLanguageTool, a natural language style checker 
 * Copyright (C) 2005 Daniel Naber (http://www.danielnaber.de)
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package de.danielnaber.languagetool.openoffice;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import javax.swing.JOptionPane;
import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.frame.XController;
import com.sun.star.frame.XDesktop;
import com.sun.star.frame.XModel;
import com.sun.star.lang.Locale;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.lang.XServiceInfo;
import com.sun.star.lang.XSingleComponentFactory;
import com.sun.star.lib.uno.helper.Factory;
import com.sun.star.lib.uno.helper.WeakBase;
import com.sun.star.registry.XRegistryKey;
import com.sun.star.task.XJobExecutor;
import com.sun.star.text.XText;
import com.sun.star.text.XTextDocument;
import com.sun.star.text.XTextViewCursor;
import com.sun.star.text.XTextViewCursorSupplier;
import com.sun.star.uno.UnoRuntime;
import com.sun.star.uno.XComponentContext;

import de.danielnaber.languagetool.JLanguageTool;
import de.danielnaber.languagetool.Language;
import de.danielnaber.languagetool.gui.Configuration;

/**
 * Basic OpenOffice.org integration.
 * 
 * @author Daniel Naber
 */
public class Main {

  public static final String version = "0.5";

  public static class _Main extends WeakBase implements XJobExecutor, XServiceInfo {

    static private final String __serviceName = "de.danielnaber.languagetool.openoffice.Main";

    private XTextDocument xTextDoc;
    private XTextViewCursor xViewCursor;
    
    public _Main(XComponentContext xCompContext) {
      try {
        XMultiComponentFactory xMCF = xCompContext.getServiceManager();
        Object desktop = xMCF.createInstanceWithContext("com.sun.star.frame.Desktop", xCompContext);
        XDesktop xDesktop = (XDesktop) UnoRuntime.queryInterface(XDesktop.class, desktop);
        XComponent xComponent = xDesktop.getCurrentComponent();
        xTextDoc = (XTextDocument) UnoRuntime.queryInterface(XTextDocument.class, xComponent);
      } catch (Throwable e) {
        e.printStackTrace();
      }
    }

    public void trigger(String sEvent) {
      if (sEvent.equals("execute")) {
        try {
          String text = getText();
          checkText(text);
        } catch (Throwable e) {
          e.printStackTrace();
        }
      } else {
        System.err.println("Sorry, don't know what to do, sEvent = " + sEvent);
      }
    }

    public void initialize(Object[] object) {
      if (object == null) object = null;        // avoid compiler warning
    }

    public String[] getSupportedServiceNames() {
      return getServiceNames();
    }

    public static String[] getServiceNames() {
      String[] sSupportedServiceNames = { __serviceName };
      return sSupportedServiceNames;
    }

    public boolean supportsService(String sServiceName) {
      return sServiceName.equals(__serviceName);
    }

    public String getImplementationName() {
      return _Main.class.getName();
    }

    private Language getLanguage() throws UnknownPropertyException, WrappedTargetException {
      // just look at the current position(?) in the document and assume that this character's
      // language is the language of the whole document:
      XPropertySet xCursorProps = (XPropertySet) UnoRuntime.queryInterface(XPropertySet.class,
          xTextDoc.getText().createTextCursor());
      Locale charLocale = (Locale) xCursorProps.getPropertyValue("CharLocale");
      boolean langIsSupported = false;
      for (int i = 0; i < Language.LANGUAGES.length; i++) {
        if (Language.LANGUAGES[i].getShortName().equals(charLocale.Language)) {
          langIsSupported= true;
          break;
        }
      }
      if (!langIsSupported) {
        JOptionPane.showMessageDialog(null, "Error: Sorry, the document language '" +charLocale.Language+ 
            "' is not supported by LanguageTool.");
        throw new IllegalArgumentException("Language is not supported: " + charLocale.Language);
      }
      return Language.getLanguageforShortName(charLocale.Language);
    }
    
    private String getText() {
      XModel xModel = (XModel)UnoRuntime.queryInterface(XModel.class, xTextDoc); 
      XController xController = xModel.getCurrentController(); 
      XTextViewCursorSupplier xViewCursorSupplier = 
        (XTextViewCursorSupplier)UnoRuntime.queryInterface(XTextViewCursorSupplier.class, xController); 
      //XTextViewCursor xViewCursor = xViewCursorSupplier.getViewCursor();
      xViewCursor = xViewCursorSupplier.getViewCursor();
      String textToCheck = xViewCursor.getString();     // user's current selection
      if (textToCheck.equals("")) {     // no selection = check complete text
        XText text = xTextDoc.getText();
        textToCheck = text.getString();
        xViewCursor = null;
      }
      return textToCheck;
    }

    private void checkText(String text) throws IOException, ParserConfigurationException, SAXException, UnknownPropertyException, WrappedTargetException {
      // TODO: show splash screen / progress bar, as init takes some time?
      Configuration config = new Configuration();
      Language docLanguage = getLanguage();
      JLanguageTool langTool = new JLanguageTool(docLanguage);
      langTool.activateDefaultPatternRules();
      for (Iterator iter = config.getDisabledRuleIds().iterator(); iter.hasNext();) {
        String id = (String) iter.next();
        langTool.disableRule(id);
      }
      List ruleMatches = langTool.check(text);
      if (ruleMatches.size() == 0) {
        JOptionPane.showMessageDialog(null, "No errors and warnings found (document language: " +
            docLanguage.getName() + ")");
        // TODO: display number of active rules etc?
      } else {
        OOoDialog dialog = new OOoDialog(config, langTool.getAllRules(), xTextDoc, ruleMatches, text, xViewCursor);
        dialog.show();
      }
    }
    
  }

  public static XSingleComponentFactory __getComponentFactory(String sImplName) {
    XSingleComponentFactory xFactory = null;
    if (sImplName.equals(_Main.class.getName()))
      xFactory = Factory.createComponentFactory(_Main.class, _Main.getServiceNames());
    return xFactory;
  }

  public static boolean __writeRegistryServiceInfo(XRegistryKey regKey) {
    return Factory.writeRegistryServiceInfo(_Main.class.getName(), _Main.getServiceNames(), regKey);
  }

}
