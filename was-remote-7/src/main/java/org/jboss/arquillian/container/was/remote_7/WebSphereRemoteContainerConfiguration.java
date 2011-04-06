/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.remote_7;

import org.jboss.arquillian.spi.ConfigurationException;
import org.jboss.arquillian.spi.ContainerProfile;
import org.jboss.arquillian.spi.client.container.ContainerConfiguration;

/**
 * WebSphereRemoteConfiguraiton
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class WebSphereRemoteContainerConfiguration implements ContainerConfiguration
{
   private String remoteServerAddress = "localhost";
   private Integer remoteServerSoapPort = 8880;
   
   private Integer remoteServerHttpPort = 9080;
   
   private boolean securityEnabled = false;

   private String username = "admin";
   private String password = "admin";
   
   private String sslTrustStore = "";
   private String sslKeyStore = "";
   private String sslTrustStorePassword = "WebAS";
   private String sslKeyStorePassword = "WebAS";
   
   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.ContainerConfiguration#getContainerProfile()
    */
   public ContainerProfile getContainerProfile()
   {
      return ContainerProfile.CLIENT;
   }

   /**
    * @return the remoteServerAddress
    */
   public String getRemoteServerAddress()
   {
      return remoteServerAddress;
   }

   /**
    * @param remoteServerAddress the remoteServerAddress to set
    */
   public void setRemoteServerAddress(String remoteServerAddress)
   {
      this.remoteServerAddress = remoteServerAddress;
   }

   /**
    * @return the remoteServerSoapPort
    */
   public Integer getRemoteServerSoapPort()
   {
      return remoteServerSoapPort;
   }

   /**
    * @param remoteServerSoapPort the remoteServerSoapPort to set
    */
   public void setRemoteServerSoapPort(Integer remoteServerSoapPort)
   {
      this.remoteServerSoapPort = remoteServerSoapPort;
   }

   /**
    * @return the remoteServerHttpPort
    */
   public Integer getRemoteServerHttpPort()
   {
      return remoteServerHttpPort;
   }

   /**
    * @param remoteServerHttpPort the remoteServerHttpPort to set
    */
   public void setRemoteServerHttpPort(Integer remoteServerHttpPort)
   {
      this.remoteServerHttpPort = remoteServerHttpPort;
   }

   public void setSecurityEnabled(boolean securityEnabled) {
      this.securityEnabled = securityEnabled;
   }

   public boolean getSecurityEnabled() {
      return securityEnabled;
   }

   /**
    * @return the username
    */
   public String getUsername()
   {
      return username;
   }
   
   /**
    * @param username the username to set
    */
   public void setUsername(String username)
   {
      this.username = username;
   }

	public void setPassword(String password) {
      this.password = password;
   }

   public String getPassword() {
      return password;
   }

   public void setSslTrustStore(String sslTrustStore) {
      this.sslTrustStore = sslTrustStore;
   }

   public String getSslTrustStore() {
      return sslTrustStore;
   }

   public void setSslKeyStore(String sslKeyStore) {
      this.sslKeyStore = sslKeyStore;
   }

   public String getSslKeyStore() {
      return sslKeyStore;
   }

   public void setSslTrustStorePassword(String sslTrustStorePassword) {
      this.sslTrustStorePassword = sslTrustStorePassword;
   }

   public String getSslTrustStorePassword() {
      return sslTrustStorePassword;
   }

   public void setSslKeyStorePassword(String sslKeyStorePassword) {
      this.sslKeyStorePassword = sslKeyStorePassword;
   }

   public String getSslKeyStorePassword() {
      return sslKeyStorePassword;
   }

   public void validate() throws ConfigurationException {
		// TODO Auto-generated method stub
		
	}
}
