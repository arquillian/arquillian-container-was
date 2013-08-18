/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012, Red Hat Middleware LLC, and individual contributors
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
   private String serverName;
   private int httpPort;
   
   private boolean allowConnectingToRunningServer = Boolean.parseBoolean(
         System.getProperty("org.jboss.arquillian.container.was.wlp_managed_8_5.allowConnectingToRunningServer",  "false"));
   
   @Override
   public void validate() throws ConfigurationException {
      // Validate wlpHome
      File wlpHomeDir = new File(wlpHome);
      if (!wlpHomeDir.isDirectory())
         throw new ConfigurationException("wlpHome provided is not valid: " + wlpHome);
      
      // Validate serverName
      if (!serverName.matches("^[A-Za-z][A-Za-z0-9]*$"))
         throw new ConfigurationException("serverName provided is not valid: '" + serverName + "'");
      
      // Validate httpPort
      if (httpPort > 65535 || httpPort <= 0)
         throw new ConfigurationException("httpPort provided is not valid: " + httpPort);
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

   public boolean isAllowConnectingToRunningServer() {
      return allowConnectingToRunningServer;
   }
}
