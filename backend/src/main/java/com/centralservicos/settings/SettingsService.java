package com.centralservicos.settings;

import com.centralservicos.attachments.AttachmentService;
import com.centralservicos.attachments.StoredResource;
import com.centralservicos.audit.AuditService;
import com.centralservicos.shared.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.mail.internet.InternetAddress;

@Service
public class SettingsService {

    private static final Pattern HEX_COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");
    private final AppSettingsRepository repository;
    private final AttachmentService attachments;
    private final SecretCipher cipher;
    private final AuditService audit;

    SettingsService(AppSettingsRepository repository, AttachmentService attachments,
                    SecretCipher cipher, AuditService audit) {
        this.repository = repository;
        this.attachments = attachments;
        this.cipher = cipher;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PublicSettingsView publicSettings() {
        return toPublic(required());
    }

    @Transactional(readOnly = true)
    public AdminSettingsView adminSettings() {
        return toAdmin(required());
    }

    @Transactional(readOnly = true)
    public TicketPolicy ticketPolicy() {
        var settings = required();
        return new TicketPolicy(settings.attachmentLimitMb(), settings.reopenDays(),
                settings.deadlineWarningHours(), ZoneId.of(settings.timezoneName()));
    }

    @Transactional
    public AdminSettingsView updateGeneral(UpdateGeneralRequest request, UUID actorId) {
        var settings = required();
        assertVersion(settings, request.version());
        validateGeneral(request.institutionName(), request.supportEmail(), request.supportPhone(),
                request.timezoneName(), request.attachmentLimitMb(), request.reopenDays(),
                request.deadlineWarningHours());
        settings.updateGeneral(request.institutionName(), request.supportEmail(), request.supportPhone(),
                request.timezoneName(), request.attachmentLimitMb(), request.reopenDays(),
                request.deadlineWarningHours());
        audit.record(actorId, "SETTINGS_GENERAL_UPDATED", "AppSettings", AppSettings.SINGLETON_ID, null);
        return toAdmin(settings);
    }

    @Transactional
    public AdminSettingsView updateTheme(UpdateThemeRequest request, UUID actorId) {
        var settings = required();
        assertVersion(settings, request.version());
        var validation = validateTheme(request.theme());
        if (!validation.valid()) {
            throw DomainException.unprocessable(String.join(" ", validation.warnings()));
        }
        settings.updateTheme(request.theme().primaryColor(), request.theme().accentColor(),
                request.theme().sidebarColor(), request.theme().canvasColor());
        audit.record(actorId, "SETTINGS_THEME_UPDATED", "AppSettings", AppSettings.SINGLETON_ID, null);
        return toAdmin(settings);
    }

    @Transactional(readOnly = true)
    public ThemeValidation previewTheme(ThemeView theme) {
        return validateTheme(theme);
    }

    @Transactional
    public AdminSettingsView updateLoginBackground(MultipartFile file, UUID actorId) {
        var settings = required();
        var stored = attachments.storeBrandingImage(file, settings.attachmentLimitMb());
        settings.updateLoginBackground(stored.key(), stored.mediaType());
        audit.record(actorId, "SETTINGS_LOGIN_BACKGROUND_UPDATED", "AppSettings", AppSettings.SINGLETON_ID,
                "{\"sha256\":\"" + stored.sha256() + "\"}");
        return toAdmin(settings);
    }

    @Transactional
    public AdminSettingsView updateSmtp(UpdateSmtpRequest request, UUID actorId) {
        var settings = required();
        assertVersion(settings, request.version());
        validateSmtp(request);
        String encrypted = null;
        var passwordSupplied = request.password() != null && !request.password().isBlank();
        if (passwordSupplied) {
            encrypted = cipher.encrypt(request.password());
        }
        settings.updateSmtp(request.host(), request.port(), request.tls(), request.fromName(),
                request.fromAddress(), request.username(), encrypted, passwordSupplied);
        audit.record(actorId, "SETTINGS_SMTP_UPDATED", "AppSettings", AppSettings.SINGLETON_ID, null);
        return toAdmin(settings);
    }

    @Transactional(readOnly = true)
    public StoredResource loadLoginBackground() {
        var settings = required();
        if (settings.loginBackgroundPath() == null || settings.loginBackgroundMediaType() == null) {
            throw DomainException.notFound("Imagem de login não configurada.");
        }
        return attachments.loadStored(settings.loginBackgroundPath(), settings.loginBackgroundMediaType(),
                "login-background");
    }

    @Transactional(readOnly = true)
    public SmtpSnapshot smtpSnapshot() {
        var settings = required();
        if (!settings.smtpConfigured()) {
            throw DomainException.unprocessable("SMTP ainda não está configurado.");
        }
        var password = settings.smtpPasswordEnc() == null ? null : cipher.decrypt(settings.smtpPasswordEnc());
        return new SmtpSnapshot(settings.smtpHost(), settings.smtpPort(), settings.smtpTls(),
                settings.smtpFromName(), settings.smtpFromAddress(), settings.smtpUsername(), password);
    }

    private AppSettings required() {
        return repository.findById(AppSettings.SINGLETON_ID)
                .orElseThrow(() -> DomainException.notFound("Configurações não encontradas."));
    }

    private void validateGeneral(String institutionName, String supportEmail, String supportPhone,
                                 String timezoneName, int attachmentLimitMb, int reopenDays,
                                 int deadlineWarningHours) {
        if (institutionName == null || institutionName.isBlank() || institutionName.length() > 160) {
            throw DomainException.unprocessable("Informe o nome da instituição com até 160 caracteres.");
        }
        try {
            ZoneId.of(timezoneName);
        } catch (Exception exception) {
            throw DomainException.unprocessable("Fuso horário inválido.");
        }
        if (attachmentLimitMb < 1 || attachmentLimitMb > 25) {
            throw DomainException.unprocessable("O limite por anexo deve ficar entre 1 e 25 MiB.");
        }
        if (reopenDays < 1 || reopenDays > 30) {
            throw DomainException.unprocessable("A janela de reabertura deve ficar entre 1 e 30 dias.");
        }
        if (deadlineWarningHours < 1 || deadlineWarningHours > 168) {
            throw DomainException.unprocessable("A antecedência de prazo deve ficar entre 1 e 168 horas.");
        }
        if (supportEmail != null && !supportEmail.isBlank()) {
            validateEmail(supportEmail, "E-mail de contato inválido.");
        }
        if (supportPhone != null && supportPhone.length() > 40) {
            throw DomainException.unprocessable("O telefone de contato deve ter até 40 caracteres.");
        }
    }

    private void validateSmtp(UpdateSmtpRequest request) {
        if (request.host() == null || request.host().isBlank()) {
            return;
        }
        if (request.host().length() > 255) {
            throw DomainException.unprocessable("Host SMTP inválido.");
        }
        if (request.port() == null || request.port() < 1 || request.port() > 65_535) {
            throw DomainException.unprocessable("Porta SMTP inválida.");
        }
        if (request.fromName() != null && request.fromName().length() > 160) {
            throw DomainException.unprocessable("Nome do remetente inválido.");
        }
        validateEmail(request.fromAddress(), "E-mail do remetente inválido.");
        if (request.username() != null && request.username().length() > 254) {
            throw DomainException.unprocessable("Usuário SMTP inválido.");
        }
        if (request.password() != null && request.password().length() > 512) {
            throw DomainException.unprocessable("Senha SMTP inválida.");
        }
    }

    private void validateEmail(String value, String message) {
        try {
            if (value == null || value.isBlank() || value.length() > 254) {
                throw new IllegalArgumentException();
            }
            new InternetAddress(value, true);
        } catch (Exception exception) {
            throw DomainException.unprocessable(message);
        }
    }

    private ThemeValidation validateTheme(ThemeView theme) {
        var warnings = new ArrayList<String>();
        if (theme == null || !validColor(theme.primaryColor()) || !validColor(theme.accentColor())
                || !validColor(theme.sidebarColor()) || !validColor(theme.canvasColor())) {
            warnings.add("Use cores no formato hexadecimal #RRGGBB.");
            return new ThemeValidation(theme, warnings);
        }
        if (contrast(theme.primaryColor(), "#FFFFFF") < 4.5 && contrast(theme.primaryColor(), "#111827") < 4.5) {
            warnings.add("A cor primária precisa manter contraste WCAG AA com texto claro ou escuro.");
        }
        if (contrast(theme.sidebarColor(), "#FFFFFF") < 4.5) {
            warnings.add("A cor da barra lateral precisa manter contraste WCAG AA com texto branco.");
        }
        if (contrast(theme.canvasColor(), "#111827") < 4.5) {
            warnings.add("O fundo geral precisa manter contraste WCAG AA com texto escuro.");
        }
        return new ThemeValidation(theme, warnings);
    }

    private boolean validColor(String value) {
        return value != null && HEX_COLOR.matcher(value).matches();
    }

    private double contrast(String left, String right) {
        var a = relativeLuminance(left);
        var b = relativeLuminance(right);
        var lighter = Math.max(a, b);
        var darker = Math.min(a, b);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private double relativeLuminance(String color) {
        int r = Integer.parseInt(color.substring(1, 3), 16);
        int g = Integer.parseInt(color.substring(3, 5), 16);
        int b = Integer.parseInt(color.substring(5, 7), 16);
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
    }

    private double channel(int value) {
        var sRgb = value / 255.0;
        return sRgb <= 0.03928 ? sRgb / 12.92 : Math.pow((sRgb + 0.055) / 1.055, 2.4);
    }

    private void assertVersion(AppSettings settings, Long expected) {
        if (expected != null && !Objects.equals(settings.rowVersion(), expected)) {
            throw DomainException.conflict("As configurações mudaram. Recarregue antes de salvar.");
        }
    }

    private PublicSettingsView toPublic(AppSettings settings) {
        var loginUrl = settings.loginBackgroundPath() == null ? null : "/api/v1/public/settings/login-background";
        return new PublicSettingsView(settings.institutionName(), settings.supportEmail(), settings.supportPhone(),
                settings.timezoneName(), theme(settings), loginUrl, settings.rowVersion(), settings.updatedAt());
    }

    private AdminSettingsView toAdmin(AppSettings settings) {
        return new AdminSettingsView(settings.institutionName(), settings.supportEmail(), settings.supportPhone(),
                settings.timezoneName(), settings.attachmentLimitMb(), settings.reopenDays(),
                settings.deadlineWarningHours(), theme(settings), settings.loginBackgroundPath() != null,
                new AdminSettingsView.SmtpView(settings.smtpHost(), settings.smtpPort(), settings.smtpTls(),
                        settings.smtpFromName(), settings.smtpFromAddress(), settings.smtpUsername(),
                        settings.smtpPasswordEnc() != null),
                settings.rowVersion(), settings.updatedAt());
    }

    private ThemeView theme(AppSettings settings) {
        return new ThemeView(settings.primaryColor(), settings.accentColor(),
                settings.sidebarColor(), settings.canvasColor());
    }

    public record UpdateGeneralRequest(String institutionName, String supportEmail, String supportPhone,
                                       String timezoneName, int attachmentLimitMb, int reopenDays,
                                       int deadlineWarningHours, Long version) {
    }

    public record UpdateThemeRequest(ThemeView theme, Long version) {
    }

    public record UpdateSmtpRequest(String host, Integer port, boolean tls, String fromName,
                                    String fromAddress, String username, String password, Long version) {
    }

    public record SmtpSnapshot(String host, Integer port, boolean tls, String fromName, String fromAddress,
                               String username, String password) {
    }
}
