package ro.sluta.accessibility.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import org.junit.jupiter.api.Test;
import ro.sluta.accessibility.domain.SnapshotErrorCode;

class DefaultNetworkAccessPolicyTest {
    @Test
    void acceptsOnlyPublicDnsResults() throws Exception {
        DefaultNetworkAccessPolicy policy = new DefaultNetworkAccessPolicy(
                host -> List.of(InetAddress.getByName("93.184.216.34")), false);
        policy.verify(URI.create("https://example.com/page"), true);
        assertThat(policy.allowsLoopbackFixtures()).isFalse();
    }

    @Test
    void blocksMixedDnsResults() throws Exception {
        DefaultNetworkAccessPolicy policy = new DefaultNetworkAccessPolicy(
                host -> List.of(InetAddress.getByName("93.184.216.34"),
                        InetAddress.getByName("127.0.0.1")), false);
        assertThatThrownBy(() -> policy.verify(URI.create("https://mixed.example/page"), true))
                .isInstanceOfSatisfying(NetworkPolicyException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(SnapshotErrorCode.BLOCKED_ADDRESS));
    }

    @Test
    void blocksPrivateReservedMetadataAndUnsupportedProtocols() throws Exception {
        DefaultNetworkAccessPolicy privatePolicy = new DefaultNetworkAccessPolicy(
                host -> List.of(InetAddress.getByName("192.168.1.10")), false);
        assertThatThrownBy(() -> privatePolicy.verify(URI.create("http://private.example"), false))
                .isInstanceOfSatisfying(NetworkPolicyException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(SnapshotErrorCode.BLOCKED_REQUEST));
        assertThatThrownBy(() -> privatePolicy.verify(URI.create("ftp://private.example/file"), false))
                .isInstanceOf(NetworkPolicyException.class);
        assertThatThrownBy(() -> privatePolicy.verify(URI.create("http://metadata.google.internal"), true))
                .isInstanceOf(NetworkPolicyException.class);
    }

    @Test
    void mapsDnsFailureExplicitly() {
        DefaultNetworkAccessPolicy policy = new DefaultNetworkAccessPolicy(host -> {
            throw new UnknownHostException(host);
        }, false);
        assertThatThrownBy(() -> policy.verify(URI.create("https://missing.example"), true))
                .isInstanceOfSatisfying(NetworkPolicyException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(SnapshotErrorCode.DNS_RESOLUTION_FAILED));
    }

    @Test
    void testModeAllowsOnlyLoopbackNotOtherPrivateAddresses() throws Exception {
        DefaultNetworkAccessPolicy loopback = new DefaultNetworkAccessPolicy(
                host -> List.of(InetAddress.getByName("127.0.0.1")), true);
        loopback.verify(URI.create("http://127.0.0.1:8080/fixture"), true);

        DefaultNetworkAccessPolicy privateNetwork = new DefaultNetworkAccessPolicy(
                host -> List.of(InetAddress.getByName("10.0.0.1")), true);
        assertThatThrownBy(() -> privateNetwork.verify(URI.create("http://fixture.internal"), true))
                .isInstanceOf(NetworkPolicyException.class);
    }
}
