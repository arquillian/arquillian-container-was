/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, 2013, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.arquillian.container.was.wlp_managed_8_5;

import java.io.File;

import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

/**
 * WLPManagedContainerConfiguration
 *
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WLPManagedContainerConfiguration implements
      ContainerConfiguration {

   private String wlpHome;
   private String serverName = "defaultServer";
   private int httpPort = 0;
   private int serverStartTimeout = 30;
   private int appDeployTimeout = 20;
   private int appUndeployTimeout = 2;
   private String sharedLib = null;
   private String deployType = "dropins";
   private String javaVmArguments = "";
   private boolean addLocalConnector;
   private String securityConfiguration;
   private boolean failSafeUndeployment = false;

   private boolean allowConnectingToRunningServer = Boolean.parseBoolean(
         System.getProperty("org.jboss.arquillian.container.was.wlp_managed_8_5.allowConnectingToRunningServer",  "false"));

   private boolean outputToConsole = true;

   @Override
   public void validate() throws ConfigurationException {
      // Validate wlpHome
      if (wlpHome != null) {
         File wlpHomeDir = new File(wlpHome);
         File bsAgentJar = new File(wlpHome + "/lib/bootstrap-agent.jar");
         File wsLaunchJar = new File(wlpHome + "/lib/ws-launch.jar");
         if (!(wlpHomeDir.isDirectory() && bsAgentJar.isFile() && wsLaunchJar.isFile()))
            throw new ConfigurationException("wlpHome provided is not valid: " + wlpHome);
      } else {
         // If wlpHome is null, throw exception
         throw new ConfigurationException("wlpHome is required for initialization");
      }

      if(securityConfiguration != null && securityConfiguration.trim().length() > 0) {
    	  File securityConfigFile = new File(securityConfiguration);
    	  if(!securityConfigFile.exists())
    		  throw new ConfigurationException("securityConfiguration provided is not valid: " + securityConfiguration);
      }

      // Validate serverName
      if (!serverName.matches("^[A-Za-z][A-Za-z0-9]*$"))
         throw new ConfigurationException("serverName provided is not valid: '" + serverName + "'");

      // Validate httpPort
      if (httpPort > 65535 || httpPort < 0)
         throw new ConfigurationException("httpPort provided is not valid: " + httpPort);

      // Validate deployType
      if (!deployType.equalsIgnoreCase("xml") && !deployType.equalsIgnoreCase("dropins"))
         throw new ConfigurationException("deployType provided is not valid: " + deployType + ".  deployType should be xml or dropins.");

      //Validate sharedLib
      if (sharedLib != null) {
         if (!sharedLib.isEmpty()) {
            if (!deployType.equalsIgnoreCase("xml"))
               throw new ConfigurationException("deployType must be set to xml when sharedLib is not empty");
         }
      }
   }

   public String getWlpHome() {
      return wlpHome;
   }

   public void setWlpHome(String wlpHome) {
      this.wlpHome = wlpHome;
   }

   public String getServerName() {
      return serverName;
   }

   public void setServerName(String serverName) {
      this.serverName = serverName;
   }

   public int getHttpPort() {
      return httpPort;
   }

   public void setHttpPort(int httpPort) {
      this.httpPort = httpPort;
   }

   public void setSharedLib(String sharedLib) {
      this.sharedLib = sharedLib;
   }

   public String getSharedLib() {
      return sharedLib;
   }

   public void setDeployType(String deployType) {
      this.deployType = deployType;
   }

   public String getDeployType() {
      return deployType;
   }

   public boolean isAllowConnectingToRunningServer() {
      return allowConnectingToRunningServer;
   }

   public void setAllowConnectingToRunningServer(boolean allowConnectingToRunningServer) {
       this.allowConnectingToRunningServer = allowConnectingToRunningServer;
   }

   public boolean isOutputToConsole() {
      return outputToConsole;
   }

   public void setOutputToConsole(boolean outputToConsole) {
      this.outputToConsole = outputToConsole;
   }

   public int getServerStartTimeout() {
      return serverStartTimeout;
   }

   public void setServerStartTimeout(int serverStartTimeout) {
      this.serverStartTimeout = serverStartTimeout;
   }

   public int getAppDeployTimeout() {
      return appDeployTimeout;
   }

   public void setAppDeployTimeout(int appDeployTimeout) {
      this.appDeployTimeout = appDeployTimeout;
   }

   public int getAppUndeployTimeout() {
      return appUndeployTimeout;
   }

   public void setAppUndeployTimeout(int appUndeployTimeout) {
      this.appUndeployTimeout = appUndeployTimeout;
   }

   public boolean isDeployTypeXML() {
      if (deployType.equalsIgnoreCase("xml"))
         return true;
      else
         return false;
   }

   public boolean isDeployTypeDropins() {
      if (deployType.equalsIgnoreCase("dropins"))
         return true;
      else
         return false;
   }

   public String getJavaVmArguments() {
      return javaVmArguments;
   }

   public void setJavaVmArguments(String javaVmArguments) {
      this.javaVmArguments = javaVmArguments;
   }

   public boolean isAddLocalConnector() {
       return addLocalConnector;
   }

   public void setAddLocalConnector(boolean addLocalConnector) {
       this.addLocalConnector = addLocalConnector;
   }

   public String getSecurityConfiguration() {
	   return securityConfiguration;
   }

   public void setSecurityConfiguration(String securityConfiguration) {
	   this.securityConfiguration = securityConfiguration;
   }

   public boolean isFailSafeUndeployment() {
	   return failSafeUndeployment;
   }

   public void setFailSafeUndeployment(boolean failSafeUndeployment) {
	   this.failSafeUndeployment = failSafeUndeployment;
   }

}
