package com.voxelport.mod.logic;

import org.slf4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

public final class VoxelPortConfig {
    private static final String SETTINGS_FILE = "settings.properties";
    private static final String ENCRYPTION_KEY_FILE = ".vp_key";
    // B2 fix: use AES/GCM/NoPadding (authenticated, non-deterministic) instead of ECB
    private static final String ENCRYPTION_ALGORITHM = "AES";
    private static final String ENCRYPTION_CIPHER     = "AES/GCM/NoPadding";
    private static final int    GCM_IV_LEN            = 12; // bytes
    private static final int    GCM_TAG_LEN           = 128; // bits

    private final Path dataFolder;
    private final Logger logger;
    private final SecretKey encryptionKey;

    private volatile String relayUrl = "";
    private volatile String serverToken = "";
    private volatile String publicHost = "play.voxelport.in";
    private volatile String serverHost = "127.0.0.1";
    private volatile int maxConnections = 200;
    private volatile boolean proxyProtocol = false;
    private volatile java.util.List<String> blockedIps = java.util.List.of();
    private volatile String corruptedTokenBackup = null;

    public VoxelPortConfig(Path dataFolder, Logger logger, SecretKey encryptionKey) {
        this.dataFolder    = Objects.requireNonNull(dataFolder);
        this.logger        = Objects.requireNonNull(logger);
        this.encryptionKey = Objects.requireNonNull(encryptionKey);
    }

    /**
     * B6 fix: factory method — performs disk I/O (key load/generate) outside the constructor
     * so the constructor itself stays side-effect free and testable.
     */
    public static VoxelPortConfig create(Path dataFolder, Logger logger) {
        SecretKey key = loadOrCreateEncryptionKey(dataFolder, logger);
        return new VoxelPortConfig(dataFolder, logger, key);
    }

    public synchronized void load() {
        Path file = dataFolder.resolve(SETTINGS_FILE);
        if (!Files.isRegularFile(file)) return;
        Properties props = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(reader);

            // Read all values locally first, then assign atomically under this lock
            // to prevent a concurrent reader from seeing a half-loaded config.
            String newRelayUrl = props.getProperty("relay_url", "").trim();

            // SECURITY FIX: Decrypt token from storage
            String encryptedToken = props.getProperty("server_token", "").trim();
            String newServerToken = "";
            String newCorruptedBackup = null;
            if (!encryptedToken.isEmpty()) {
                try {
                    newServerToken = decryptToken(encryptedToken);
                } catch (Exception e) {
                    logger.warn("Failed to decrypt token: {}", e.getMessage());
                    newCorruptedBackup = encryptedToken;
                }
            }

            String newPublicHost = props.getProperty("public_host", publicHost).trim();
            String newServerHost = props.getProperty("server_host", serverHost).trim();
            int newMaxConnections = parsePositiveInt(props.getProperty("max_connections"), maxConnections);
            boolean newProxyProtocol = Boolean.parseBoolean(props.getProperty("proxy_protocol", "false").trim());
            String blocked = props.getProperty("blocked_ips", "").trim();
            java.util.List<String> newBlockedIps = blocked.isEmpty()
                    ? java.util.List.of()
                    : java.util.Arrays.stream(blocked.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(java.util.stream.Collectors.toList());

            // Assign all fields together while the lock is held.
            serverToken = newServerToken;
            corruptedTokenBackup = newCorruptedBackup;
            publicHost = newPublicHost;
            serverHost = newServerHost;
            maxConnections = newMaxConnections;
            proxyProtocol = newProxyProtocol;
            blockedIps = newBlockedIps;

            // SECURITY FIX: Validate relay URL format (also updates relayUrl field).
            validateAndSetRelayUrl(newRelayUrl.isBlank() ? null : newRelayUrl);
            logger.info("VoxelPort config loaded. Custom relay: {}", relayUrl.isBlank() ? "(default)" : relayUrl);
        } catch (IOException e) {
            logger.warn("Failed to load VoxelPort settings: {}", e.getMessage());
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFolder);
            setSecureDirectoryPermissions(dataFolder);
            Properties props = new Properties();
            props.setProperty("relay_url", relayUrl);
            
            // SECURITY FIX: Encrypt token before storing
            String encryptedToken = "";
            if (corruptedTokenBackup != null && serverToken.isEmpty()) {
                encryptedToken = corruptedTokenBackup;
            } else {
                try {
                    encryptedToken = serverToken.isEmpty() ? "" : encryptToken(serverToken);
                } catch (Exception e) {
                    logger.warn("Failed to encrypt token: {}", e.getMessage());
                    if (corruptedTokenBackup != null) encryptedToken = corruptedTokenBackup;
                }
            }
            props.setProperty("server_token", encryptedToken);
            
            props.setProperty("public_host", publicHost);
            props.setProperty("server_host", serverHost);
            props.setProperty("max_connections", Integer.toString(maxConnections));
            props.setProperty("proxy_protocol", Boolean.toString(proxyProtocol));
            props.setProperty("blocked_ips", String.join(",", blockedIps));
            Path file = dataFolder.resolve(SETTINGS_FILE);
            try (OutputStream out = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "VoxelPort Settings — edit in-game via the Settings screen");
            }
            setSecureFilePermissions(file);
        } catch (IOException e) {
            logger.warn("Failed to save VoxelPort settings: {}", e.getMessage());
        }
    }

    public String getRelayUrl() {
        return relayUrl.isBlank() ? null : relayUrl.trim();
    }

    public void setRelayUrl(String url) {
        validateAndSetRelayUrl(url);
    }
    
    private void validateAndSetRelayUrl(String url) {
        if (url == null || url.isBlank()) {
            this.relayUrl = "";
            RelayUrlResolver.setOverride(null);
            return;
        }

        String normalizedUrl = RelayUrlResolver.normalizeOfficialRelayUrl(url);
        
        // SECURITY FIX: Validate URL format and scheme to prevent SSRF
        try {
            URI uri = new URI(normalizedUrl);
            String scheme = uri.getScheme();
            if (scheme == null) {
                logger.warn("Relay URL must include scheme (wss:// or ws://): {}", url);
                this.relayUrl = "";
                return;
            }
            
            if (!scheme.equalsIgnoreCase("wss") && !scheme.equalsIgnoreCase("ws")) {
                logger.warn("Relay URL scheme must be wss:// or ws://, not {}", scheme);
                this.relayUrl = "";
                return;
            }
            
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                logger.warn("Relay URL must include a valid hostname: {}", url);
                this.relayUrl = "";
                return;
            }

            if (RelayUrlResolver.isOfficialHost(host) && uri.getPort() != -1 && uri.getPort() != 443) {
                logger.warn("Official VoxelPort relay WebSocket uses wss://play.voxelport.in on port 443; assigned player ports are not relay WebSocket ports: {}", url);
                this.relayUrl = "";
                RelayUrlResolver.setOverride(null);
                return;
            }

            if (scheme.equalsIgnoreCase("ws")) {
                if (!isPrivateOrLocalIp(host)) {
                    logger.warn("Insecure ws:// scheme is only permitted for localhost/LAN. For remote relays, use wss://: {}", url);
                    this.relayUrl = "";
                    return;
                }
                logger.warn("Using insecure ws:// scheme for local/LAN relay connection: {}", url);
            }

            if (RelayUrlResolver.isDefaultRelayUrl(normalizedUrl)) {
                this.relayUrl = "";
                RelayUrlResolver.setOverride(null);
                return;
            }
            
            this.relayUrl = normalizedUrl.trim();
            RelayUrlResolver.setOverride(this.relayUrl);
        } catch (URISyntaxException e) {
            logger.warn("Invalid relay URL: {}", e.getMessage());
            this.relayUrl = "";
        }
    }
    
    private boolean isPrivateOrLocalIp(String host) {
        // Strip IPv6 brackets before parsing
        String bare = (host.startsWith("[") && host.endsWith("]"))
                ? host.substring(1, host.length() - 1) : host;
        try {
            java.net.InetAddress addr = java.net.InetAddress.getByName(bare);
            return addr.isLoopbackAddress()      // 127.x.x.x, ::1
                    || addr.isSiteLocalAddress() // 10.x, 172.16-31.x, 192.168.x, fc00::/7
                    || addr.isLinkLocalAddress() // 169.254.x, fe80::/10
                    || addr.isAnyLocalAddress(); // 0.0.0.0, ::
        } catch (java.net.UnknownHostException ignored) {
            // Unresolvable hostnames are not considered private
        }
        return host.equalsIgnoreCase("localhost");
    }

    public String getServerToken() {
        return serverToken.isBlank() ? null : serverToken.trim();
    }

    public void setServerToken(String token) {
        this.serverToken = (token == null) ? "" : token.trim();
        this.corruptedTokenBackup = null;
    }

    /**
     * Ensures a device token exists. VoxelPort no longer requires a Discord-issued
     * token — on first run the mod generates its own {@code vp_…} token locally and
     * persists it. The relay accepts any well-formed token, so this "just works"
     * with no signup. Call once after {@link #load()}.
     */
    public synchronized void ensureDeviceToken() {
        if (!serverToken.isBlank() || corruptedTokenBackup != null) return;
        this.serverToken = generateDeviceToken();
        save();
        logger.info("VoxelPort: generated a new device token (no Discord or signup needed).");
    }

    /** Produces a URL-safe {@code vp_}-prefixed token with ~144 bits of entropy. */
    public static String generateDeviceToken() {
        byte[] raw = new byte[18];
        new SecureRandom().nextBytes(raw);
        return "vp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public String getPublicHost() {
        return publicHost.isBlank() ? "play.voxelport.in" : publicHost.trim();
    }

    public String getServerHost() {
        return serverHost.isBlank() ? "127.0.0.1" : serverHost.trim();
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public boolean isProxyProtocol() {
        return proxyProtocol;
    }

    public java.util.List<String> getBlockedIps() {
        return blockedIps;
    }

    private static int parsePositiveInt(String value, int fallback) {
        if (value == null || value.isBlank()) return fallback;
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void setSecureDirectoryPermissions(Path path) {
        try {
            var views = path.getFileSystem().supportedFileAttributeViews();
            if (views.contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
            } else if (views.contains("acl")) {
                restrictToOwnerAcl(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to set secure directory permissions: " + e.getMessage(), e);
        }
    }

    private static void setSecureFilePermissions(Path path) {
        try {
            var views = path.getFileSystem().supportedFileAttributeViews();
            if (views.contains("posix")) {
                Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            } else if (views.contains("acl")) {
                restrictToOwnerAcl(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to set secure file permissions: " + e.getMessage(), e);
        }
    }

    // Restrict a file/directory to the current user only on ACL-based filesystems (Windows).
    // Reads the existing owner, builds a single full-access ACL entry for that owner,
    // and replaces the ACL — removing any inherited or group entries.
    private static void restrictToOwnerAcl(Path path) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView == null) return;
        UserPrincipal owner = Files.getOwner(path);
        AclEntry ownerEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                .build();
        aclView.setAcl(List.of(ownerEntry));
    }
    
    // SECURITY FIX: Add token encryption/decryption
    // B6 fix: now static so it can be called from the factory method before constructing the object
    private static SecretKey loadOrCreateEncryptionKey(Path dataFolder, Logger logger) {
        Path keyFile = dataFolder.resolve(ENCRYPTION_KEY_FILE);
        try {
            Files.createDirectories(dataFolder);
            
            if (Files.exists(keyFile)) {
                try (BufferedReader reader = Files.newBufferedReader(keyFile, StandardCharsets.UTF_8)) {
                    String line = reader.readLine();
                    if (line != null) {
                        byte[] decodedKey = Base64.getDecoder().decode(line);
                        return new SecretKeySpec(decodedKey, 0, decodedKey.length, ENCRYPTION_ALGORITHM);
                    }
                }
            }
            
            // Generate new key — use strongest entropy source available
            KeyGenerator keyGen = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
            keyGen.init(256, SecureRandom.getInstanceStrong()); // Bug-2 fix
            SecretKey key = keyGen.generateKey();
            
            // Save key (also encrypted with file permissions)
            String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
            try (BufferedWriter writer = Files.newBufferedWriter(keyFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                writer.write(encodedKey);
            }
            setSecureFilePermissions(keyFile);
            
            return key;
        } catch (Exception e) {
            logger.warn("Failed to load/create encryption key: {}", e.getMessage());
            // Fallback: generate key in memory only (will be lost on restart)
            try {
                KeyGenerator keyGen = KeyGenerator.getInstance(ENCRYPTION_ALGORITHM);
                keyGen.init(256);
                return keyGen.generateKey();
            } catch (Exception ex) {
                throw new RuntimeException("Failed to generate encryption key", ex);
            }
        }
    }
    
    private String encryptToken(String plaintext) throws Exception {
        if (plaintext == null || plaintext.isEmpty()) return "";
        // B2 fix: AES/GCM — generates a fresh random IV per encryption
        byte[] iv = new byte[GCM_IV_LEN];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance(ENCRYPTION_CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        // Store as versioned format: gcm:v1:<base64-iv>:<base64-ciphertext>
        String b64Iv = Base64.getEncoder().encodeToString(iv);
        String b64Enc = Base64.getEncoder().encodeToString(encrypted);
        return "gcm:v1:" + b64Iv + ":" + b64Enc;
    }
    
    private String decryptToken(String ciphertext) throws Exception {
        if (ciphertext == null || ciphertext.isEmpty()) return "";
        
        if (ciphertext.startsWith("gcm:v1:")) {
            String[] parts = ciphertext.split(":");
            if (parts.length != 4) throw new IllegalArgumentException("Invalid gcm:v1 token format");
            byte[] iv = Base64.getDecoder().decode(parts[2]);
            byte[] encrypted = Base64.getDecoder().decode(parts[3]);
            Cipher cipher = Cipher.getInstance(ENCRYPTION_CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, StandardCharsets.UTF_8);
        } else {
            // Fallback for older ECB-encrypted tokens
            try {
                Cipher cipher = Cipher.getInstance(ENCRYPTION_ALGORITHM);
                cipher.init(Cipher.DECRYPT_MODE, encryptionKey);
                byte[] decoded = Base64.getDecoder().decode(ciphertext);
                byte[] decrypted = cipher.doFinal(decoded);
                logger.info("Successfully decrypted old ECB token format. It will be upgraded to GCM on next config save.");
                return new String(decrypted, StandardCharsets.UTF_8);
            } catch (Exception ex) {
                logger.warn("Failed to decrypt legacy token as ECB.");
                throw ex;
            }
        }
    }
}
