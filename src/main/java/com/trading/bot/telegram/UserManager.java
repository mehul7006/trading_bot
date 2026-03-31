package com.trading.bot.telegram;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages bot user access: admin, approved users, and pending join requests.
 *
 * Roles:
 *   ADMIN         — full access (hardcoded + env override)
 *   APPROVED USER — can use trading commands, receives signals
 *   UNKNOWN USER  — can only /start (join prompt) and /join
 *
 * Approved users are persisted in approved_users.txt (one chatId per line)
 * so access survives bot restarts.
 * Pending requests are in-memory only (cleared on restart).
 */
public class UserManager {

    private static final Logger logger = LoggerFactory.getLogger(UserManager.class);

    // ── Admin chat ID ─────────────────────────────────────────────────────────
    // Override via ADMIN_CHAT_ID env variable; falls back to hardcoded value.
    private static final long ADMIN_CHAT_ID = resolveAdminId();

    private static long resolveAdminId() {
        String env = System.getenv("ADMIN_CHAT_ID");
        if (env != null && !env.isBlank()) {
            try {
                return Long.parseLong(env.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid ADMIN_CHAT_ID env value '{}', using default", env);
            }
        }
        return 457623834L;
    }

    // ── Storage ───────────────────────────────────────────────────────────────
    private static final String APPROVED_USERS_FILE = "approved_users.txt";

    /** Approved chat IDs (persisted to disk). */
    private final Set<Long> approvedUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * Pending join requests: chatId → display name.
     * In-memory only — clears on restart.
     */
    private final Map<Long, String> pendingRequests = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public UserManager() {
        loadApprovedUsers();
        logger.info("UserManager initialized. Admin: {} | Approved: {}", ADMIN_CHAT_ID, approvedUsers.size());
    }

    // ── Role checks ───────────────────────────────────────────────────────────

    public boolean isAdmin(long chatId) {
        return chatId == ADMIN_CHAT_ID;
    }

    /**
     * Returns true when the user has trading access (admin OR explicitly approved).
     */
    public boolean isApproved(long chatId) {
        return isAdmin(chatId) || approvedUsers.contains(chatId);
    }

    public boolean hasPendingRequest(long chatId) {
        return pendingRequests.containsKey(chatId);
    }

    // ── Pending request management ────────────────────────────────────────────

    /**
     * Queue a join request.
     * @param displayName human-readable name shown to admin (e.g. Telegram first name)
     */
    public void addPendingRequest(long chatId, String displayName) {
        pendingRequests.put(chatId, displayName);
        logger.info("Join request queued: {} ({})", displayName, chatId);
    }

    /**
     * Admin approves a pending user.
     * @return true if the user was in pending and is now approved; false if not found.
     */
    public boolean approveUser(long chatId) {
        String name = pendingRequests.remove(chatId);
        if (name == null) return false;
        approvedUsers.add(chatId);
        saveApprovedUsers();
        logger.info("User approved: {} ({})", name, chatId);
        return true;
    }

    /**
     * Admin rejects a pending request (removes from queue, does NOT ban).
     * @return true if the user was in pending; false if not found.
     */
    public boolean rejectUser(long chatId) {
        String name = pendingRequests.remove(chatId);
        if (name == null) return false;
        logger.info("Join request rejected: {} ({})", name, chatId);
        return true;
    }

    /**
     * Admin revokes an approved user's access.
     * @return true if the user was approved and has now been removed; false if not found.
     */
    public boolean revokeUser(long chatId) {
        boolean removed = approvedUsers.remove(chatId);
        if (removed) {
            saveApprovedUsers();
            logger.info("Access revoked for chatId: {}", chatId);
        }
        return removed;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public long getAdminChatId() {
        return ADMIN_CHAT_ID;
    }

    public Set<Long> getApprovedUsers() {
        return Collections.unmodifiableSet(approvedUsers);
    }

    public Map<Long, String> getPendingRequests() {
        return Collections.unmodifiableMap(pendingRequests);
    }

    /** Returns the display name stored at request time, or the raw chatId as string. */
    public String getPendingName(long chatId) {
        return pendingRequests.getOrDefault(chatId, String.valueOf(chatId));
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void loadApprovedUsers() {
        try {
            Path path = Path.of(APPROVED_USERS_FILE);
            if (!path.toFile().exists()) return;
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    try {
                        approvedUsers.add(Long.parseLong(trimmed));
                    } catch (NumberFormatException ignored) {
                        logger.warn("Skipping invalid line in {}: '{}'", APPROVED_USERS_FILE, trimmed);
                    }
                }
            }
            logger.info("Loaded {} approved users from disk", approvedUsers.size());
        } catch (IOException e) {
            logger.warn("Could not load approved_users.txt: {}", e.getMessage());
        }
    }

    private void saveApprovedUsers() {
        try {
            StringBuilder sb = new StringBuilder();
            for (Long id : approvedUsers) {
                sb.append(id).append("\n");
            }
            Files.writeString(Path.of(APPROVED_USERS_FILE), sb.toString());
        } catch (IOException e) {
            logger.error("Failed to save approved_users.txt: {}", e.getMessage());
        }
    }
}
