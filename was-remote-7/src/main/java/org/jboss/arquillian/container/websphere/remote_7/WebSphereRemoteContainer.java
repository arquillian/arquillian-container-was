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
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;

import javax.jms.IllegalStateException;
import javax.management.NotificationFilterSupport;
import javax.management.ObjectName;

import org.jboss.arquillian.protocol.servlet.ServletMethodExecutor;
import org.jboss.arquillian.spi.client.container.DeployableContainer;
import org.jboss.arquillian.spi.client.container.DeploymentException;
import org.jboss.arquillian.spi.client.container.LifecycleException;
import org.jboss.arquillian.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.spi.client.protocol.metadata.HTTPContext;
import org.jboss.arquillian.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.arquillian.spi.client.protocol.metadata.Servlet;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.exporter.ZipExporter;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.AdminClientFactory;
import com.ibm.websphere.management.application.AppConstants;
import com.ibm.websphere.management.application.AppManagement;
import com.ibm.websphere.management.application.AppManagementProxy;
import com.ibm.websphere.management.application.AppNotification;
import com.ibm.websphere.management.application.client.AppDeploymentController;

/**
 * WebSphereRemoteContainer
 *
 * @author <a href="mailto:aslak@redhat.com">Aslak Knutsen</a>
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WebSphereRemoteContainer implements DeployableContainer<WebSphereRemoteContainerConfiguration>
{
   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private WebSphereRemoteContainerConfiguration containerConfiguration;

   private AdminClient adminClient;

   //-------------------------------------------------------------------------------------||
   // Required Implementations - DeployableContainer -------------------------------------||
   //-------------------------------------------------------------------------------------||

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#setup(org.jboss.arquillian.spi.Context, org.jboss.arquillian.spi.Configuration)
    */
   public void setup(WebSphereRemoteContainerConfiguration configuration)
   {
	   this.containerConfiguration = configuration;
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#start(org.jboss.arquillian.spi.Context)
    */
   public void start() throws LifecycleException
   {
      Properties wasServerProps = new Properties();
      wasServerProps.setProperty(AdminClient.CONNECTOR_HOST, containerConfiguration.getRemoteServerAddress());
      wasServerProps.setProperty(AdminClient.CONNECTOR_PORT, String.valueOf(containerConfiguration.getRemoteServerSoapPort()));
      wasServerProps.setProperty(AdminClient.CONNECTOR_TYPE, AdminClient.CONNECTOR_TYPE_SOAP);
      wasServerProps.setProperty(AdminClient.CONNECTOR_SECURITY_ENABLED, "false");
      wasServerProps.setProperty(AdminClient.USERNAME, containerConfiguration.getUsername());
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
   public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException
   {
      String appName = createDeploymentName(archive.getName());
      String appExtension = createDeploymentExtension(archive.getName());
      
      File exportedArchiveLocation = null;

      try
      {
         exportedArchiveLocation = File.createTempFile(appName, appExtension);
         archive.as(ZipExporter.class).exportTo(exportedArchiveLocation, true);
         
         Hashtable<Object, Object> prefs = new Hashtable<Object, Object>();
         
         prefs.put(AppConstants.APPDEPL_LOCALE, Locale.getDefault());

         Properties props = new Properties();
         prefs.put (AppConstants.APPDEPL_DFLTBNDG, props);
         props.put (AppConstants.APPDEPL_DFLTBNDG_VHOST, "default_host");

         // Prepare application for deployment to WebSphere Application Server
         AppDeploymentController controller = AppDeploymentController
         	.readArchive(exportedArchiveLocation.getAbsolutePath(), prefs);

         String[] validationResult = controller.validate();
         if (validationResult != null && validationResult.length > 0) {
            throw new DeploymentException("Unable to complete all task data for deployment preparation. Reason: " + Arrays.toString(validationResult));
         }
         
         controller.saveAndClose();
         
         Hashtable<Object, Object> module2Server = new Hashtable<Object, Object>();
         ObjectName serverMBean = adminClient.getServerMBean();
         
         String targetServer = "WebSphere:cell=" + serverMBean.getKeyProperty("cell")
                              + ",node=" + serverMBean.getKeyProperty("node")
                              + ",server=" + serverMBean.getKeyProperty("process");
   
         module2Server.put("*",targetServer);
         
         prefs.put(AppConstants.APPDEPL_MODULE_TO_SERVER, module2Server);
         prefs.put(AppConstants.APPDEPL_ARCHIVE_UPLOAD, Boolean.TRUE);
         
         AppManagement appManagementProxy = AppManagementProxy.getJMXProxyForClient(adminClient);
         
         NotificationFilterSupport filterSupport = new NotificationFilterSupport();
         filterSupport.enableType(AppConstants.NotificationType);
         DeploymentNotificationListener listener = new DeploymentNotificationListener(
                  adminClient, 
                  filterSupport, 
                  "Install " + appName,
                  AppNotification.INSTALL);
         
         appManagementProxy.installApplication(
               exportedArchiveLocation.getAbsolutePath(),
               appName, 
               prefs,
               null);
         
         synchronized(listener) 
         {
            listener.wait();
         }
         if(listener.isSuccessful())
         {
        	 // TODO: Wait for rollout of the application before starting it
        	 Thread.sleep(2000);
        	 appManagementProxy.startApplication(appName, null, null);
        	 // TODO: Check whether starting the app is blocking or if it needs another notification.
        	 Thread.sleep(2000);
         }
         else
         {
            throw new IllegalStateException("Application not sucessfully deployed: " + listener.getMessage());
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

      ProtocolMetaData metaData = new ProtocolMetaData();
      
   	HTTPContext httpContext = new HTTPContext(
   			containerConfiguration.getRemoteServerAddress(),
   			containerConfiguration.getRemoteServerHttpPort());
      httpContext.add(new Servlet(ServletMethodExecutor.ARQUILLIAN_SERVLET_NAME, "arquillian-protocol"));
      metaData.addContext(httpContext);
      
      return metaData;
   }

   /* (non-Javadoc)
    * @see org.jboss.arquillian.spi.DeployableContainer#undeploy(org.jboss.arquillian.spi.Context, org.jboss.shrinkwrap.api.Archive)
    */
   public void undeploy(final Archive<?> archive) throws DeploymentException
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
   public void stop() throws LifecycleException
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

	public Class<WebSphereRemoteContainerConfiguration> getConfigurationClass() {
		// TODO Auto-generated method stub
		return WebSphereRemoteContainerConfiguration.class;
	}
	
	public ProtocolDescription getDefaultProtocol() {
		return new ProtocolDescription("Servlet 2.5");
	}
	
	public void deploy(Descriptor descriptor) throws DeploymentException {
		// TODO Auto-generated method stub
		
	}
	
	public void undeploy(Descriptor descriptor) throws DeploymentException {
		// TODO Auto-generated method stub
		
	}
}
