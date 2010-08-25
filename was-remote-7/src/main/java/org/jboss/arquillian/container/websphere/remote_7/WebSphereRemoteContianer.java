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
package org.jboss.arquillian.container.websphere.remote_7;

import java.io.File;
import java.net.URL;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;

import javax.jms.IllegalStateException;
import javax.management.NotificationFilterSupport;
import javax.management.ObjectName;

import org.jboss.arquillian.protocol.servlet_2_5.ServletMethodExecutor;
import org.jboss.arquillian.spi.Configuration;
import org.jboss.arquillian.spi.ContainerMethodExecutor;
import org.jboss.arquillian.spi.Context;
import org.jboss.arquillian.spi.DeployableContainer;
import org.jboss.arquillian.spi.DeploymentException;
import org.jboss.arquillian.spi.LifecycleException;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagement;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;

/**
 * WebSphereRemoteContianer
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @version $Revision: $
 */
public class WebSphereRemoteContianer implements DeployableContainer
{
   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private WebSphereRemoteContainerConfiguraiton containerConfiguraiton;

   private AdminClient adminClient;

   //-------------------------------------------------------------------------------------||
   // Required Implementations - DeployableContainer -------------------------------------||
   //-------------------------------------------------------------------------------------||

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#setup(org.jboss.arquillian.spi.Context, org.jboss.arquillian.spi.Configuration)
    */
   public void setup(Context context, Configuration configuration)
   {
      this.containerConfiguraiton = configuration.getContainerConfig(WebSphereRemoteContainerConfiguraiton.class);
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#start(org.jboss.arquillian.spi.Context)
    */
   public void start(Context context) throws LifecycleException
   {
      Properties wasServerProps = new Properties();
      wasServerProps.setProperty(AdminClient.CONNECTOR_HOST, containerConfiguraiton.getRemoteServerAddress());
      wasServerProps.setProperty(AdminClient.CONNECTOR_PORT, String.valueOf(containerConfiguraiton.getRemoteServerSoapPort()));
      wasServerProps.setProperty(AdminClient.CONNECTOR_TYPE, AdminClient.CONNECTOR_TYPE_SOAP);
      wasServerProps.setProperty(AdminClient.CONNECTOR_SECURITY_ENABLED, "false");
      wasServerProps.setProperty(AdminClient.USERNAME, containerConfiguraiton.getUsername());
//      wasServerProps.setProperty(AdminClient.PASSWORD, "admin");
      
      try
      {
         adminClient = AdminClientFactory.createAdminClient(wasServerProps);
      } 
      catch (Exception e) 
      {
         throw new LifecycleException("Could not create AdminClient", e);
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#deploy(org.jboss.arquillian.spi.Context, org.jboss.shrinkwrap.api.Archive)
    */
   public ContainerMethodExecutor deploy(Context context, Archive<?> archive) throws DeploymentException
   {
      String appName = createDeploymentName(archive.getName());
      String appExtension = createDeploymentExtension(archive.getName());
      
      File exportedArchiveLocation = null;

      try
      {
         exportedArchiveLocation = File.createTempFile(appName, appExtension);
         archive.as(ZipExporter.class).exportZip(exportedArchiveLocation, true);
         
//         Session configSession = new Session(containerConfiguraiton.getUsername(), false);
//         ConfigServiceProxy configProxy = new ConfigServiceProxy(adminClient);

         Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();
//         prefs.put(AppConstants.APPDEPL_WEB_CONTEXTROOT, appName);
//         prefs.put(AppConstants.APPDEPL_PRECOMPILE_JSP, Boolean.FALSE);
//         prefs.put(AppConstants.APPDEPL_DISTRIBUTE_APP, Boolean.TRUE);
//         prefs.put(AppConstants.APPDEPL_USE_BINARY_CONFIG, Boolean.FALSE);
//         prefs.put(AppConstants.APPDEPL_DEPLOYEJB_CMDARG, Boolean.FALSE);
//         prefs.put(AppConstants.APPDEPL_FILETRANSFER_UPLOAD , Boolean.TRUE);
//         prefs.put(AppConstants.APPDEPL_VALIDATE_APP, Boolean.FALSE);
//         prefs.put(AppConstants.APPDEPL_MBEANFORRES, Boolean.TRUE);
//         prefs.put(AppConstants.APPDEPL_FILEPERMISSION, ".*\\.dll=755#.*\\.so=755#.*\\.a=755#.*\\.sl=755");
         
   
         prefs.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

         Hashtable<Object, Object> module2Server = new Hashtable<Object, Object>();
         ObjectName serverMBean = adminClient.getServerMBean();
         
         String targetServer = "WebSphere:cell=" + serverMBean.getKeyProperty("cell")
                              + ",node=" + serverMBean.getKeyProperty("node")
                              + ",server=" + serverMBean.getKeyProperty("process");
   
         module2Server.put("arquillian-protocol.war,WEB-INF/web.xml",targetServer);
         module2Server.put("test.jar,META-INF/ejb-jar.xml",targetServer);
         
         prefs.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2Server);
         prefs.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
//         
//         Hashtable<Object, Object> mapWebModToVH = new Hashtable<Object, Object>();
//         mapWebModToVH.put("arquillian-protocol.war,WEB-INF/web.xml", "default_host");
//         prefs.put(AppConstants.APPDEPL_VIRTUAL_HOST, mapWebModToVH);
   
         NotificationFilterSupport filterSupport = new NotificationFilterSupport();
         filterSupport.enableType(AppConstants.NotificationType);
         DeploymentNotificationListener listener = new DeploymentNotificationListener(
                  adminClient, 
                  filterSupport, 
                  "Install " + appName,
                  AppNotification.INSTALL);
         
         AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(adminClient);
         
         appManagementProxy.installApplication(
               exportedArchiveLocation.getAbsolutePath(),
               appName, 
               prefs,
               null);
//               configSession.getSessionId());
         
         synchronized(listener) 
         {
            listener.wait();
         }
         if(listener.isSuccessful())
         {
//            configProxy.save(configSession, true);
         }
         else
         {
            throw new IllegalStateException("Application not sucessfully deployed: " + listener.getMessage());
            //configProxy.discard(configSession);
         }
      } 
      catch (Exception e) 
      {
         throw new DeploymentException("Could not deploy application", e);
      }
      finally
      {
         if(exportedArchiveLocation != null) 
         {  
            exportedArchiveLocation.delete();
         }
      }
      try 
      {
         return new ServletMethodExecutor(
               new URL(
                     "http",
                     containerConfiguraiton.getRemoteServerAddress(),
                     containerConfiguraiton.getRemoteServerHttpPort(), 
                     "/")
               );
      } 
      catch (Exception e) 
      {
         throw new RuntimeException("Could not create ContianerMethodExecutor", e);
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#undeploy(org.jboss.arquillian.spi.Context, org.jboss.shrinkwrap.api.Archive)
    */
   public void undeploy(Context context, Archive<?> archive) throws DeploymentException
   {
      String appName = createDeploymentName(archive.getName());
      
      try
      {
//         Session configSession = new Session(containerConfiguraiton.getUsername(), false);
//         ConfigServiceProxy configProxy = new ConfigServiceProxy(adminClient);

         Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();

         NotificationFilterSupport filterSupport = new NotificationFilterSupport();
         filterSupport.enableType(AppConstants.NotificationType);
         DeploymentNotificationListener listener = new DeploymentNotificationListener(
                  adminClient, 
                  filterSupport, 
                  "Uninstall " + appName,
                  AppNotification.UNINSTALL);
         
         AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(adminClient);
         
         appManagementProxy.uninstallApplication(
               appName, 
               prefs,
               null);
//               configSession.getSessionId());
         
         synchronized(listener) 
         {
            listener.wait();
         }
         if(listener.isSuccessful())
         {
            //configProxy.save(configSession, true);
         }
         else
         {
            throw new IllegalStateException("Application not sucessfully undeployed: " + listener.getMessage());
            //configProxy.discard(configSession);
         }
      } 
      catch (Exception e) 
      {
         throw new DeploymentException("Could not undeploy application", e);
      }
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#stop(org.jboss.arquillian.spi.Context)
    */
   public void stop(Context context) throws LifecycleException
   {
   }

   //-------------------------------------------------------------------------------------||
   // Internal Helper Methods ------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private String createDeploymentName(String archiveName) 
   {
      return archiveName.substring(0, archiveName.lastIndexOf("."));
   }

   private String createDeploymentExtension(String archiveName) 
   {
      return archiveName.substring(archiveName.lastIndexOf("."));
   }
}
