/*
 * JBoss, Home of Professional Open Source
 * Copyright 2011, Red Hat Middleware LLC, and individual contributors
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
package org.jboss.arquillian.container.was.embedded_8;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.jboss.arquillian.container.spi.client.container.DeployableContainer;
import org.jboss.arquillian.container.spi.client.container.DeploymentException;
import org.jboss.arquillian.container.spi.client.container.LifecycleException;
import org.jboss.arquillian.container.spi.client.protocol.ProtocolDescription;
import org.jboss.arquillian.container.spi.client.protocol.metadata.ProtocolMetaData;
import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.descriptor.api.Descriptor;

/**
 * WebSphereEmbeddedContainer
 *
 * @author <a href="mailto:gerhard.poul@gmail.com">Gerhard Poul</a>
 * @version $Revision: $
 */
public class WebSphereEmbeddedContainer implements DeployableContainer<WebSphereEmbeddedContainerConfiguration>
{
   //-------------------------------------------------------------------------------------||
   // Instance Members -------------------------------------------------------------------||
   //-------------------------------------------------------------------------------------||
   
   private static final String className = WebSphereEmbeddedContainer.class.getName();
   
   private static Logger log = Logger.getLogger(className);
   
   private WebSphereEmbeddedContainerConfiguration containerConfiguration;

   //-------------------------------------------------------------------------------------||
   // Required Implementations - DeployableContainer -------------------------------------||
   //-------------------------------------------------------------------------------------||

   public void setup(WebSphereEmbeddedContainerConfiguration configuration)
   {
      if (log.isLoggable(Level.FINER)) {
            log.entering(className, "setup");
      }
	   
      this.containerConfiguration = configuration;
	   
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "setup");
      }
   }

   public void start() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "start");
      }
      
      // TODO stuff
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "start");
      }
   }

   public ProtocolMetaData deploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "deploy");
         
         log.finer("Archive provided to deploy method: " + archive.toString(true));
      }
      
      // TODO stuff
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "deploy");
      }
      
      return new ProtocolMetaData();
   }

   public void undeploy(final Archive<?> archive) throws DeploymentException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "undeploy");
      }
      
      // TODO stuff
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "undeploy");
      }
   }

   public void stop() throws LifecycleException
   {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "stop");
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "stop");
      }
   }

	public ProtocolDescription getDefaultProtocol() {
      if (log.isLoggable(Level.FINER)) {
         log.entering(className, "getDefaultProtocol");
      }
      
      if (log.isLoggable(Level.FINER)) {
         log.exiting(className, "getDefaultProtocol");
      }
      
		return new ProtocolDescription("local");
	}

   public Class<WebSphereEmbeddedContainerConfiguration> getConfigurationClass() {
      // TODO Auto-generated method stub
      return null;
   }

   public void deploy(Descriptor descriptor) throws DeploymentException {
      // TODO Auto-generated method stub
      
   }

   public void undeploy(Descriptor descriptor) throws DeploymentException {
      // TODO Auto-generated method stub
      
   }
}
