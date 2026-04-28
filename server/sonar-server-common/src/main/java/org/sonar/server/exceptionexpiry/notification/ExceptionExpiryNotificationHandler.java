/*
 * SonarQube
 * Copyright (C) 2009-2024 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.exceptionexpiry.notification;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import org.sonar.server.notification.EmailNotificationHandler;
import org.sonar.server.notification.NotificationDispatcherMetadata;
import org.sonar.server.notification.email.EmailNotificationChannel;
import org.sonar.server.notification.email.EmailNotificationChannel.EmailDeliveryRequest;

// to make changable per project notification
public class ExceptionExpiryNotificationHandler extends EmailNotificationHandler<ExceptionExpiryNotification> {
    private static final String KEY = "ExceptionExpiry";
    private static final NotificationDispatcherMetadata METADATA = NotificationDispatcherMetadata.create(KEY)
      .setProperty(NotificationDispatcherMetadata.GLOBAL_NOTIFICATION, String.valueOf(true))
      .setProperty(NotificationDispatcherMetadata.PER_PROJECT_NOTIFICATION, String.valueOf(true));

    public ExceptionExpiryNotificationHandler(EmailNotificationChannel emailNotificationChannel) {
      super(emailNotificationChannel);
    }

    @Override
    public Optional<NotificationDispatcherMetadata> getMetadata() {
      return Optional.of(METADATA);
    }

    public static NotificationDispatcherMetadata newMetadata() {
      return METADATA;
    }

    @Override
    public Class<ExceptionExpiryNotification> getNotificationClass() {
      return ExceptionExpiryNotification.class;
    }

    @Override
    public Set<EmailDeliveryRequest> toEmailDeliveryRequests(Collection<ExceptionExpiryNotification> notifications) {
      return Collections.emptySet();
    }
}
