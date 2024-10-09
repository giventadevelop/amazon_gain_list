package com.amazon.gain.list;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amazon")
public class AmazonProperties {
    private String username;
    private String password;
    private String chromeDriverPath;

    private int waitTimeoutSeconds = 10; // Default value of 10 seconds

    // Getters and setters
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getChromeDriverPath() {
        return chromeDriverPath;
    }

    public void setChromeDriverPath(String chromeDriverPath) {
        this.chromeDriverPath = chromeDriverPath;
    }

    // Existing getters and setters...

    public int getWaitTimeoutSeconds() {
        return waitTimeoutSeconds;
    }

    public void setWaitTimeoutSeconds(int waitTimeoutSeconds) {
        this.waitTimeoutSeconds = waitTimeoutSeconds;
    }
}
