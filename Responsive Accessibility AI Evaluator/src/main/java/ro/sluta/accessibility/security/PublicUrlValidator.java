package ro.sluta.accessibility.security;

import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import ro.sluta.accessibility.domain.SnapshotErrorCode;

@Component
public class PublicUrlValidator {
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final Set<String> BLOCKED_HOSTS = Set.of("localhost", "localhost.localdomain",
            "metadata", "metadata.google.internal", "instance-data.ec2.internal");
    private static final Pattern IPV4_LITERAL = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private final boolean allowLoopbackFixtures;

    public PublicUrlValidator() {
        this.allowLoopbackFixtures = false;
    }

    @Autowired
    public PublicUrlValidator(Environment environment) {
        this.allowLoopbackFixtures = environment.acceptsProfiles(Profiles.of("test"));
    }

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) throw new UrlValidationException("URL-ul este obligatoriu.");
        final URI uri;
        try { uri = URI.create(rawUrl.trim()); }
        catch (IllegalArgumentException e) { throw new UrlValidationException("URL-ul nu are un format valid."); }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT)))
            throw new UrlValidationException("Sunt permise numai protocoalele HTTP și HTTPS.");
        if (host == null || host.isBlank()) throw new UrlValidationException("URL-ul trebuie să conțină un host valid.");
        if (uri.getUserInfo() != null) throw new UrlValidationException("URL-urile cu autentificare nu sunt permise.");
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (allowLoopbackFixtures && isLoopbackFixture(normalizedHost)) return uri;
        if (BLOCKED_HOSTS.contains(normalizedHost) || normalizedHost.endsWith(".localhost")
                || isExplicitlyBlockedAddress(normalizedHost))
            throw new UrlValidationException(SnapshotErrorCode.BLOCKED_ADDRESS,
                    "Adresa indică o destinație locală sau privată și este blocată.");
        return uri;
    }

    private boolean isLoopbackFixture(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost")) return true;
        try { return InetAddress.getByName(host).isLoopbackAddress(); }
        catch (Exception ignored) { return false; }
    }

    private boolean isExplicitlyBlockedAddress(String host) {
        String literal = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        if (!IPV4_LITERAL.matcher(literal).matches() && !literal.contains(":")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(literal);
            return address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || isCloudMetadataAddress(address.getHostAddress());
        } catch (Exception exception) {
            throw new UrlValidationException("Adresa IP nu are un format valid.");
        }
    }

    private boolean isCloudMetadataAddress(String address) {
        return address.equals("169.254.169.254") || address.equals("100.100.100.200");
    }
}
