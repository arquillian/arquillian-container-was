/*
 * JBoss, Home of Professional Open Source
 * Copyright 2010-2011, Red Hat Middleware LLC, and individual contributors
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

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.application.AppNotification;

public class DeploymentNotificationListener implements NotificationListener
{
   private static Logger log = Logger.getLogger(DeploymentNotificationListener.class.getName());
   private AdminClient adminClient;
   private NotificationFilterSupport filterSupport;
   private ObjectName objectName;
   private String eventTypeToCheck;
   private boolean successful = true;
   private String message = "";
   private Properties notificationProps = new Properties();

   public DeploymentNotificationListener(AdminClient adminClient, NotificationFilterSupport support, Object handBack, String eventTypeToCheck) 
      throws Exception
   {
      super();
      this.adminClient = adminClient;
      this.filterSupport = support;
      this.eventTypeToCheck = eventTypeToCheck;
      this.objectName = (ObjectName) adminClient.queryNames(new ObjectName("WebSphere:type=AppManagement,*"), null)
            .iterator().next();
      adminClient.addNotificationListener(objectName, this, filterSupport, handBack);
   }

   public void handleNotification(Notification notification, Object handback)
   {
      AppNotification appNotification = (AppNotification) notification.getUserData();
      if (log.isLoggable(Level.FINEST)) {
         log.finest("handleNotification message: " + appNotification.message);
         log.finest("handleNotification taskName: " + appNotification.taskName);
         log.finest("handleNotification taskStatus: " + appNotification.taskStatus);
         log.finest("handleNotification eventProps: " + appNotification.props);
      }
      message = message += "\n" + appNotification.message;
      if (
            appNotification.taskName.equals(eventTypeToCheck) && 
            (appNotification.taskStatus.equals(AppNotification.STATUS_COMPLETED) || 
                  appNotification.taskStatus.equals(AppNotification.STATUS_FAILED)))
      {
         try
         {
            adminClient.removeNotificationListener(objectName, this);
            if (appNotification.taskStatus.equals(AppNotification.STATUS_FAILED))
            {
               successful = false;
            } else {
               notificationProps = appNotification.props;
            }
               
            synchronized (this)
            {
               notifyAll();
            }
         }
         catch (Exception e)
         {
         }
      }
   }

   public String getMessage()
   {
      return message;
   }
   
   public Properties getNotificationProps()
   {
      return notificationProps;
   }
   
   public boolean isSuccessful()
   {
      return successful;
   }
}