package com.example.util;

import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for password hashing and verification using BCrypt
 */
public class PasswordUtil {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordUtil.class);
    
    /**
     * Hash a password using BCrypt
     * @param plainPassword the plain text password
     * @return the hashed password
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        
        try {
            String hashedPassword = BCrypt.hashpw(plainPassword, BCrypt.gensalt());
            logger.info("Password hashed successfully");
            return hashedPassword;
        } catch (Exception e) {
            logger.error("Error hashing password: {}", e.getMessage());
            throw new RuntimeException("Failed to hash password", e);
        }
    }
    
    /**
     * Verify a password against a hash
     * @param plainPassword the plain text password to verify
     * @param hashedPassword the stored hash to check against
     * @return true if the password matches the hash, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) {
            return false;
        }
        
        try {
            boolean isValid = BCrypt.checkpw(plainPassword, hashedPassword);
            logger.debug("Password verification result: {}", isValid);
            return isValid;
        } catch (Exception e) {
            logger.error("Error verifying password: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a password meets security requirements
     * @param password the password to check
     * @return true if the password meets requirements, false otherwise
     */
    public static boolean isPasswordValid(String password) {
        if (password == null || password.length() < 6) {
            return false;
        }
        
        // Add more validation rules as needed
        return true;
    }
}
