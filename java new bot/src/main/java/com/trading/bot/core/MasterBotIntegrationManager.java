package com.trading.bot.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Master Bot Integration Manager - Part 1: Core System
 * Integrates all bot functionalities into a unified system
 */
public class MasterBotIntegrationManager {
    private static final Logger logger = LoggerFactory.getLogger(MasterBotIntegrationManager.class);
    
    // Core Components Map
    private final Map<String, Object> botComponents = new ConcurrentHashMap<>();
    private final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    // System Status
    private volatile boolean systemRunning = false;
    private final Map<String, Boolean> componentStatus = new ConcurrentHashMap<>();
    
    // Available Bot Instances
    private BotLauncher botLauncher;
    private ActiveBot activeBot;
    private UnifiedBotCommandHandler commandHandler;
    private HonestBotTester botTester;
    
    public MasterBotIntegrationManager() {
        initializeComponents();
        logger.info("Master Bot Integration Manager initialized");
    }
    
    private void initializeComponents() {
        try {
            // Initialize core components
            this.botLauncher = new BotLauncher();
            this.activeBot = new ActiveBot();
            this.commandHandler = new UnifiedBotCommandHandler();
            this.botTester = new HonestBotTester();
            
            // Register components
            botComponents.put("launcher", botLauncher);
            botComponents.put("active", activeBot);
            botComponents.put("command", commandHandler);
            botComponents.put("tester", botTester);
            
            // Initialize component status
            componentStatus.put("launcher", false);
            componentStatus.put("active", false);
            componentStatus.put("command", false);
            componentStatus.put("tester", false);
            
            logger.info("All bot components initialized successfully");
        } catch (Exception e) {
            logger.error("Error initializing components: {}", e.getMessage());
        }
    }
    
    /**
     * Start the entire bot system with all components
     */
    public synchronized void startSystem() {
        if (systemRunning) {
            logger.warn("System is already running");
            return;
        }
        
        try {
            logger.info("Starting Master Bot Integration System...");
            
            // Start command handler first
            startComponent("command", () -> {
                // Command handler is always ready
                return true;
            });
            
            // Start bot launcher
            startComponent("launcher", () -> {
                botLauncher.start();
                return true;
            });
            
            // Start active bot
            startComponent("active", () -> {
                activeBot.start();
                return true;
            });
            
            systemRunning = true;
            logger.info("Master Bot Integration System started successfully");
            
        } catch (Exception e) {
            logger.error("Error starting system: {}", e.getMessage());
            stopSystem();
        }
    }
    
    /**
     * Stop the entire bot system
     */
    public synchronized void stopSystem() {
        if (!systemRunning) {
            logger.warn("System is not running");
            return;
        }
        
        try {
            logger.info("Stopping Master Bot Integration System...");
            
            // Stop all running tasks
            runningTasks.values().forEach(task -> task.cancel(true));
            runningTasks.clear();
            
            // Stop components in reverse order
            stopComponent("active");
            stopComponent("launcher");
            stopComponent("command");
            
            systemRunning = false;
            logger.info("Master Bot Integration System stopped successfully");
            
        } catch (Exception e) {
            logger.error("Error stopping system: {}", e.getMessage());
        }
    }
    
    /**
     * Execute command through unified handler
     */
    public void executeCommand(String command, String... args) {
        if (!systemRunning) {
            logger.warn("System is not running. Please start the system first.");
            return;
        }
        
        try {
            commandHandler.executeCommand(command, args);
        } catch (Exception e) {
            logger.error("Error executing command {}: {}", command, e.getMessage());
        }
    }
    
    /**
     * Get system status
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("systemRunning", systemRunning);
        status.put("componentStatus", new HashMap<>(componentStatus));
        status.put("activeComponents", botComponents.keySet());
        status.put("runningTasks", runningTasks.size());
        status.put("timestamp", new Date());
        
        return status;
    }
    
    /**
     * Run comprehensive system test
     */
    public void runSystemTest() {
        logger.info("Running comprehensive system test...");
        
        Future<?> testTask = executorService.submit(() -> {
            try {
                // Test bot accuracy
                botTester.runHonestTest();
                
                // Test command execution
                commandHandler.executeCommand("status");
                commandHandler.executeCommand("health-check");
                
                logger.info("System test completed successfully");
            } catch (Exception e) {
                logger.error("System test failed: {}", e.getMessage());
            }
        });
        
        runningTasks.put("system-test", testTask);
    }
    
    /**
     * Start a specific component
     */
    private void startComponent(String componentName, ComponentStarter starter) {
        Future<?> task = executorService.submit(() -> {
            try {
                boolean started = starter.start();
                componentStatus.put(componentName, started);
                logger.info("Component {} started: {}", componentName, started);
            } catch (Exception e) {
                logger.error("Error starting component {}: {}", componentName, e.getMessage());
                componentStatus.put(componentName, false);
            }
        });
        
        runningTasks.put("start-" + componentName, task);
    }
    
    /**
     * Stop a specific component
     */
    private void stopComponent(String componentName) {
        try {
            Object component = botComponents.get(componentName);
            if (component != null) {
                if (component instanceof BotLauncher) {
                    ((BotLauncher) component).shutdown();
                } else if (component instanceof ActiveBot) {
                    ((ActiveBot) component).stop();
                } else if (component instanceof UnifiedBotCommandHandler) {
                    ((UnifiedBotCommandHandler) component).shutdown();
                }
                
                componentStatus.put(componentName, false);
                logger.info("Component {} stopped", componentName);
            }
        } catch (Exception e) {
            logger.error("Error stopping component {}: {}", componentName, e.getMessage());
        }
    }
    
    /**
     * Component starter interface
     */
    @FunctionalInterface
    private interface ComponentStarter {
        boolean start() throws Exception;
    }
    
    /**
     * Shutdown the integration manager
     */
    public void shutdown() {
        stopSystem();
        executorService.shutdown();
        logger.info("Master Bot Integration Manager shutdown complete");
    }
    
    // Main method for standalone execution
    public static void main(String[] args) {
        MasterBotIntegrationManager manager = new MasterBotIntegrationManager();
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(manager::shutdown));
        
        if (args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "start":
                    manager.startSystem();
                    break;
                case "test":
                    manager.startSystem();
                    manager.runSystemTest();
                    break;
                case "status":
                    System.out.println("System Status: " + manager.getSystemStatus());
                    break;
                default:
                    manager.executeCommand(args[0], Arrays.copyOfRange(args, 1, args.length));
            }
        } else {
            // Interactive mode
            Scanner scanner = new Scanner(System.in);
            System.out.println("Master Bot Integration Manager");
            System.out.println("Commands: start, stop, status, test, or any bot command");
            System.out.println("Type 'exit' to quit");
            
            while (true) {
                System.out.print("bot> ");
                String input = scanner.nextLine().trim();
                
                if ("exit".equalsIgnoreCase(input)) {
                    break;
                } else if ("start".equalsIgnoreCase(input)) {
                    manager.startSystem();
                } else if ("stop".equalsIgnoreCase(input)) {
                    manager.stopSystem();
                } else if ("status".equalsIgnoreCase(input)) {
                    System.out.println("System Status: " + manager.getSystemStatus());
                } else if ("test".equalsIgnoreCase(input)) {
                    manager.runSystemTest();
                } else if (!input.isEmpty()) {
                    String[] parts = input.split("\\s+");
                    manager.executeCommand(parts[0], Arrays.copyOfRange(parts, 1, parts.length));
                }
            }
            
            scanner.close();
        }
        
        manager.shutdown();
    }
}