package org.jboss.arquillian.container.websphere.remote_7;

import javax.management.Notification;
import javax.management.NotificationFilterSupport;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import com.ibm.websphere.management.AdminClient;
import com.ibm.websphere.management.application.AppNotification;

public class DeploymentNotificationListener implements NotificationListener
{
   private AdminClient adminClient;
   private NotificationFilterSupport filterSupport;
   private ObjectName objectName;
   private String eventTypeToCheck;
   private boolean successful = true;
   private String message = "";

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
   
   public boolean isSuccessful()
   {
      return successful;
   }
}