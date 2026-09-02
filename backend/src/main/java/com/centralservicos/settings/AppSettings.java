package com.centralservicos.settings;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "app_settings")
class AppSettings {

    static final int SINGLETON_ID = 1;

    @Id
    private Integer id;
    private String institutionName;
    private String supportEmail;
    private String supportPhone;
    private String timezoneName;
    private int attachmentLimitMb;
    private int reopenDays;
    private int deadlineWarningHours;
    private String primaryColor;
    private String accentColor;
    private String sidebarColor;
    private String canvasColor;
    private String loginBackgroundPath;
    private String loginBackgroundMediaType;
    private String smtpHost;
    private Integer smtpPort;
    private boolean smtpTls;
    private String smtpFromName;
    private String smtpFromAddress;
    private String smtpUsername;
    private String smtpPasswordEnc;
    @Version
    private Long rowVersion;
    private Instant updatedAt;

    protected AppSettings() {
    }

    Integer id() { return id; }
    String institutionName() { return institutionName; }
    String supportEmail() { return supportEmail; }
    String supportPhone() { return supportPhone; }
    String timezoneName() { return timezoneName; }
    int attachmentLimitMb() { return attachmentLimitMb; }
    int reopenDays() { return reopenDays; }
    int deadlineWarningHours() { return deadlineWarningHours; }
    String primaryColor() { return primaryColor; }
    String accentColor() { return accentColor; }
    String sidebarColor() { return sidebarColor; }
    String canvasColor() { return canvasColor; }
    String loginBackgroundPath() { return loginBackgroundPath; }
    String loginBackgroundMediaType() { return loginBackgroundMediaType; }
    String smtpHost() { return smtpHost; }
    Integer smtpPort() { return smtpPort; }
    boolean smtpTls() { return smtpTls; }
    String smtpFromName() { return smtpFromName; }
    String smtpFromAddress() { return smtpFromAddress; }
    String smtpUsername() { return smtpUsername; }
    String smtpPasswordEnc() { return smtpPasswordEnc; }
    Long rowVersion() { return rowVersion; }
    Instant updatedAt() { return updatedAt; }

    void updateGeneral(String institutionName, String supportEmail, String supportPhone, String timezoneName,
                       int attachmentLimitMb, int reopenDays, int deadlineWarningHours) {
        this.institutionName = institutionName.trim();
        this.supportEmail = blankToNull(supportEmail);
        this.supportPhone = blankToNull(supportPhone);
        this.timezoneName = timezoneName.trim();
        this.attachmentLimitMb = attachmentLimitMb;
        this.reopenDays = reopenDays;
        this.deadlineWarningHours = deadlineWarningHours;
        touch();
    }

    void updateTheme(String primaryColor, String accentColor, String sidebarColor, String canvasColor) {
        this.primaryColor = primaryColor;
        this.accentColor = accentColor;
        this.sidebarColor = sidebarColor;
        this.canvasColor = canvasColor;
        touch();
    }

    void updateLoginBackground(String path, String mediaType) {
        this.loginBackgroundPath = path;
        this.loginBackgroundMediaType = mediaType;
        touch();
    }

    void updateSmtp(String host, Integer port, boolean tls, String fromName, String fromAddress,
                    String username, String passwordEnc, boolean passwordSupplied) {
        this.smtpHost = blankToNull(host);
        this.smtpPort = port;
        this.smtpTls = tls;
        this.smtpFromName = blankToNull(fromName);
        this.smtpFromAddress = blankToNull(fromAddress);
        this.smtpUsername = blankToNull(username);
        if (passwordSupplied) {
            this.smtpPasswordEnc = blankToNull(passwordEnc);
        }
        touch();
    }

    boolean smtpConfigured() {
        return smtpHost != null && smtpPort != null && smtpFromAddress != null;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
