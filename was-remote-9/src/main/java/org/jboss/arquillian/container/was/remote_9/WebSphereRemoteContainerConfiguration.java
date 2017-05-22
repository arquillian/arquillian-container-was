/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009-2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.remote_9;

import com.ibm.websphere.management.application.AppConstants;
import org.jboss.arquillian.container.spi.ConfigurationException;
import org.jboss.arquillian.container.spi.client.container.ContainerConfiguration;

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
   
   private boolean securityEnabled = false;

   private String username = "admin";
   private String password = "admin";
   
   private String sslTrustStore = "";
   private String sslKeyStore = "";
   private String sslTrustStorePassword = "WebAS";
   private String sslKeyStorePassword = "WebAS";
   private String sslTrustStoreType = null;
   private String sslKeyStoreType = null;

   /** Enables or disables the upload of the deployable archive to the server
    * (AppConstants.APPDEPL_ARCHIVE_UPLOAD). Can be false for local servers and speeds
    * deployment for large archives. */
   private boolean archiveUploadEnabled = true;

   /**
    * Specifies the classloading mode for deployed application ({@link AppConstants#APPDEPL_CLASSLOADINGMODE}):
    * <ul>
    *   <li>parent-first ({@link AppConstants#APPDEPL_CLASSLOADINGMODE_PARENTFIRST}) - the default,</li>
    *   <li>parent-last ({@link AppConstants#APPDEPL_CLASSLOADINGMODE_PARENTLAST}).</li>
    * </ul>
    */
   private String deploymentClassLoadingMode = AppConstants.APPDEPL_CLASSLOADINGMODE_PARENTFIRST;

   /**
    * Specifies the classloader policy for deployed application ({@link AppConstants#APPDEPL_CLASSLOADERPOLICY}):
    * <ul>
    *   <li>multiple classloaders for each WAR within the EAR
    *       ({@link AppConstants#APPDEPL_CLASSLOADERPOLICY_MULTIPLE}) - the default,</li>
    *   <li>single classloader for the whole EAR ({@link AppConstants#APPDEPL_CLASSLOADERPOLICY_SINGLE}).</li>
    * </ul>
    */
   private String deploymentClassLoaderPolicy = AppConstants.APPDEPL_CLASSLOADERPOLICY_MULTIPLE;

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

   public void setSslTrustStoreType(String sslTrustStoreType) {
       this.sslTrustStoreType = sslTrustStoreType;
   }

   public String getSslTrustStoreType() {
       return this.sslTrustStoreType;
   }

   public void setSslKeyStoreType(String sslKeyStoreType) {
       this.sslKeyStoreType = sslKeyStoreType;
   }

   public String getSslKeyStoreType() {
       return this.sslKeyStoreType;
   }

   @Override
   public void validate() throws ConfigurationException {
       if (!AppConstants.APPDEPL_CLASSLOADINGMODE_PARENTFIRST.equals(deploymentClassLoadingMode)
               && !AppConstants.APPDEPL_CLASSLOADINGMODE_PARENTLAST.equals(deploymentClassLoadingMode)) {

           throw new ConfigurationException(String.format("Illegal value %s for deploymentClassLoadingMode. "
                                                          + "Possible values: %s, %s",
                                                          deploymentClassLoadingMode,
                                                          AppConstants.APPDEPL_CLASSLOADINGMODE_PARENTFIRST,
                                                          AppConstants.APPDEPL_CLASSLOADINGMODE_PARENTLAST));
       }

       if (!AppConstants.APPDEPL_CLASSLOADERPOLICY_MULTIPLE.equals(deploymentClassLoaderPolicy)
               && !AppConstants.APPDEPL_CLASSLOADERPOLICY_SINGLE.equals(deploymentClassLoaderPolicy)) {

           throw new ConfigurationException(String.format("Illegal value %s for deploymentClassLoaderPolicy. "
                                                          + "Possible values: %s, %s",
                                                          deploymentClassLoaderPolicy,
                                                          AppConstants.APPDEPL_CLASSLOADERPOLICY_MULTIPLE,
                                                          AppConstants.APPDEPL_CLASSLOADERPOLICY_SINGLE));
       }
   }

   public void setArchiveUploadEnabled(boolean enabled) {
      this.archiveUploadEnabled = enabled;
   }

   public boolean isArchiveUploadEnabled() {
      return this.archiveUploadEnabled;
   }

   public String getDeploymentClassLoadingMode() {
       return this.deploymentClassLoadingMode;
   }

   public void setDeploymentClassLoadingMode(final String deploymentClassLoadingMode) {
       this.deploymentClassLoadingMode = deploymentClassLoadingMode;
   }

   public String getDeploymentClassLoaderPolicy() {
       return this.deploymentClassLoaderPolicy;
   }

   public void setDeploymentClassLoaderPolicy(final String deploymentClassLoaderPolicy) {
       this.deploymentClassLoaderPolicy = deploymentClassLoaderPolicy;
   }
}
