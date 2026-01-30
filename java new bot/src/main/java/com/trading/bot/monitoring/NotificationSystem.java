package com.trading.bot.monitoring;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notification System for sending alerts via multiple channels
 */
public class NotificationSystem {
    
    private static final Logger logger = LoggerFactory.getLogger(NotificationSystem.class);
    
    private final BlockingQueue<NotificationMessage> notificationQueue;
    private final ExecutorService notificationExecutor;
    private final List<NotificationChannel> channels;
    private final List<NotificationMessage> notificationHistory;
    
    private volatile boolean isActive = false;
    
    public NotificationSystem() {
        this.notificationQueue = new LinkedBlockingQueue<>();
        this.notificationExecutor = Executors.newSingleThreadExecutor();
        this.channels = new ArrayList<>();
        this.notificationHistory = Collections.synchronizedList(new ArrayList<>());
        
        // Add default channels
        addChannel(new ConsoleNotificationChannel());
        addChannel(new LogNotificationChannel());
        
        logger.info("📢 Notification System initialized");
    }
    
    public void start() {
        if (isActive) return;
        
        isActive = true;
        
        // Start notification processor
        notificationExecutor.submit(this::processNotifications);
        
        logger.info("✅ Notification System started");
    }
    
    public void stop() {
        isActive = false;
        
        if (notificationExecutor != null && !notificationExecutor.isShutdown()) {
            notificationExecutor.shutdown();
        }
        
        logger.info("🛑 Notification System stopped");
    }
    
    public void addChannel(NotificationChannel channel) {
        channels.add(channel);
        logger.info("📢 Added notification channel: {}", channel.getChannelName());
    }
    
    public void sendNotification(String title, String message) {
        sendNotification(title, message, NotificationPriority.NORMAL);
    }
    
    public void sendNotification(String title, String message, NotificationPriority priority) {
        try {
            NotificationMessage notification = new NotificationMessage(
                UUID.randomUUID().toString(),
                title,
                message,
                priority,
                LocalDateTime.now()
            );
            
            notificationQueue.offer(notification);
            logger.debug("📤 Queued notification: {}", title);
            
        } catch (Exception e) {
            logger.error("Error queuing notification: {}", e.getMessage());
        }
    }
    
    public void sendUrgentNotification(String title, String message) {
        sendNotification(title, message, NotificationPriority.URGENT);
    }
    
    public void sendInfoNotification(String title, String message) {
        sendNotification(title, message, NotificationPriority.INFO);
    }
    
    private void processNotifications() {
        logger.info("📢 Notification processor started");
        
        while (isActive) {
            try {
                NotificationMessage notification = notificationQueue.take();
                
                // Add to history
                notificationHistory.add(notification);
                
                // Keep history limited
                if (notificationHistory.size() > 1000) {
                    notificationHistory.remove(0);
                }
                
                // Send via all channels
                for (NotificationChannel channel : channels) {
                    try {
                        channel.sendNotification(notification);
                    } catch (Exception e) {
                        logger.error("Error sending notification via {}: {}", 
                            channel.getChannelName(), e.getMessage());
                    }
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error processing notification: {}", e.getMessage());
            }
        }
        
        logger.info("📢 Notification processor stopped");
    }
    
    // Getters for monitoring
    public List<NotificationMessage> getRecentNotifications(int count) {
        int size = notificationHistory.size();
        int fromIndex = Math.max(0, size - count);
        return new ArrayList<>(notificationHistory.subList(fromIndex, size));
    }
    
    public int getPendingNotificationCount() {
        return notificationQueue.size();
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * Notification message class
     */
    public static class NotificationMessage {
        private final String id;
        private final String title;
        private final String message;
        private final NotificationPriority priority;
        private final LocalDateTime timestamp;
        
        public NotificationMessage(String id, String title, String message, 
                                 NotificationPriority priority, LocalDateTime timestamp) {
            this.id = id;
            this.title = title;
            this.message = message;
            this.priority = priority;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public NotificationPriority getPriority() { return priority; }
        public LocalDateTime getTimestamp() { return timestamp; }
        
        public String getFormattedMessage() {
            return String.format("[%s] %s: %s", 
                timestamp.format(DateTimeFormatter.ofPattern("HH:mm:ss")), title, message);
        }
        
        @Override
        public String toString() {
            return String.format("Notification[%s: %s (%s)]", title, priority, timestamp);
        }
    }
    
    /**
     * Notification priority enum
     */
    public enum NotificationPriority {
        INFO("ℹ️"),
        NORMAL("📢"),
        URGENT("🚨"),
        CRITICAL("🔴");
        
        private final String emoji;
        
        NotificationPriority(String emoji) {
            this.emoji = emoji;
        }
        
        public String getEmoji() {
            return emoji;
        }
    }
    
    /**
     * Base notification channel interface
     */
    public interface NotificationChannel {
        void sendNotification(NotificationMessage notification) throws Exception;
        String getChannelName();
        boolean isEnabled();
    }
    
    /**
     * Console notification channel
     */
    public static class ConsoleNotificationChannel implements NotificationChannel {
        
        @Override
        public void sendNotification(NotificationMessage notification) {
            String formatted = String.format("%s [%s] %s: %s",
                notification.getPriority().getEmoji(),
                notification.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                notification.getTitle(),
                notification.getMessage()
            );
            
            System.out.println(formatted);
        }
        
        @Override
        public String getChannelName() {
            return "Console";
        }
        
        @Override
        public boolean isEnabled() {
            return true;
        }
    }
    
    /**
     * Log file notification channel
     */
    public static class LogNotificationChannel implements NotificationChannel {
        private static final Logger notificationLogger = LoggerFactory.getLogger("NOTIFICATION");
        
        @Override
        public void sendNotification(NotificationMessage notification) {
            String message = String.format("%s: %s", notification.getTitle(), notification.getMessage());
            
            switch (notification.getPriority()) {
                case CRITICAL, URGENT -> notificationLogger.error(message);
                case NORMAL -> notificationLogger.info(message);
                case INFO -> notificationLogger.debug(message);
            }
        }
        
        @Override
        public String getChannelName() {
            return "Log";
        }
        
        @Override
        public boolean isEnabled() {
            return true;
        }
    }
    
    /**
     * Telegram notification channel (can be implemented for Telegram bot integration)
     */
    public static class TelegramNotificationChannel implements NotificationChannel {
        private final String botToken;
        private final String chatId;
        private boolean enabled;
        
        public TelegramNotificationChannel(String botToken, String chatId) {
            this.botToken = botToken;
            this.chatId = chatId;
            this.enabled = (botToken != null && chatId != null);
        }
        
        @Override
        public void sendNotification(NotificationMessage notification) throws Exception {
            if (!enabled) return;
            
            // TODO: Implement actual Telegram API call
            String telegramMessage = String.format("%s *%s*\n%s", 
                notification.getPriority().getEmoji(),
                notification.getTitle(),
                notification.getMessage()
            );
            
            // For now, just log - implement actual Telegram sending
            logger.info("📱 Telegram: {}", telegramMessage);
        }
        
        @Override
        public String getChannelName() {
            return "Telegram";
        }
        
        @Override
        public boolean isEnabled() {
            return enabled;
        }
        
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}