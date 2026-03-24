package dev.bkjohnson.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.api.exception.UnprocessableEntityException;
import dev.bkjohnson.notification.model.entity.WebhookSubscription;
import dev.bkjohnson.notification.repository.WebhookSubscriptionRepository;
import dev.bkjohnson.notification.security.HmacSigner;
import dev.bkjohnson.notification.security.SsrfValidator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class WebhookSubscriptionServiceTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private WebhookSubscriptionRepository repository;
    @Mock
    private SsrfValidator ssrfValidator;
    @Mock
    private HmacSigner hmacSigner;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WebhookSubscriptionService service;

    @BeforeEach
    void setUp() {
        RequestContext.set(new RequestContext(
                "req-1", "trace-1", "v1", "client-1", "user-1",
                Set.of("ROLE"), Set.of("scope"), TENANT_ID,
                "127.0.0.1", null, Instant.now(), Map.of()));
        service = new WebhookSubscriptionService(
                repository, ssrfValidator, hmacSigner, objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testListSubscriptions() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "my-hook", "https://example.com/hook", "secret", null);
        Page<WebhookSubscription> page = new PageImpl<>(List.of(subscription));
        Pageable pageable = PageRequest.of(0, 10);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<WebhookSubscription> result = service.listSubscriptions(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("my-hook");
    }

    @Test
    void testGetSubscription() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "my-hook", "https://example.com/hook", "secret", null);

        when(repository.findById("sub-1")).thenReturn(Optional.of(subscription));

        WebhookSubscription result = service.getSubscription("sub-1");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("my-hook");
    }

    @Test
    void testGetSubscriptionNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSubscription("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void testCreateSubscription() {
        when(ssrfValidator.validate("https://example.com/hook")).thenReturn(List.of());
        when(hmacSigner.generateSecret()).thenReturn("generated-secret-hex");
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.createSubscription(
                "my-hook", "https://example.com/hook", List.of("order.created"));

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getName()).isEqualTo("my-hook");
        assertThat(result.getCallbackUrl()).isEqualTo("https://example.com/hook");
        assertThat(result.getSecretEncrypted()).isEqualTo("generated-secret-hex");
        verify(hmacSigner).generateSecret();
        verify(repository).save(any(WebhookSubscription.class));
    }

    @Test
    void testCreateSubscriptionInvalidUrl() {
        when(ssrfValidator.validate("http://localhost/hook"))
                .thenReturn(List.of("Callback URL must use HTTPS"));

        assertThatThrownBy(() -> service.createSubscription(
                "my-hook", "http://localhost/hook", List.of("order.created")))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Invalid callback URL");
    }

    @Test
    void testUpdateSubscription() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "old-name", "https://example.com/hook", "secret", null);

        when(repository.findById("sub-1")).thenReturn(Optional.of(subscription));
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.updateSubscription(
                "sub-1", "new-name", null, null, null);

        assertThat(result.getName()).isEqualTo("new-name");
        verify(repository).save(any(WebhookSubscription.class));
    }

    @Test
    void testUpdateSubscriptionCallbackUrl() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "my-hook", "https://example.com/hook", "secret", null);

        when(repository.findById("sub-1")).thenReturn(Optional.of(subscription));
        when(ssrfValidator.validate("https://new.example.com/hook")).thenReturn(List.of());
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.updateSubscription(
                "sub-1", null, "https://new.example.com/hook", null, null);

        assertThat(result.getCallbackUrl()).isEqualTo("https://new.example.com/hook");
        verify(ssrfValidator).validate("https://new.example.com/hook");
    }

    @Test
    void testDeleteSubscription() {
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "my-hook", "https://example.com/hook", "secret", null);

        when(repository.findById("sub-1")).thenReturn(Optional.of(subscription));
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.deleteSubscription("sub-1");

        ArgumentCaptor<WebhookSubscription> captor =
                ArgumentCaptor.forClass(WebhookSubscription.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void testUpdateSubscriptionAllFields() {
        // Cover all non-null branches in updateSubscription
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "old-name", "https://example.com/old", "secret", null);

        when(repository.findById("sub-1")).thenReturn(Optional.of(subscription));
        when(ssrfValidator.validate("https://example.com/new")).thenReturn(List.of());
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.updateSubscription(
                "sub-1", "new-name", "https://example.com/new",
                List.of("order.created", "order.shipped"), false);

        assertThat(result.getName()).isEqualTo("new-name");
        assertThat(result.getCallbackUrl()).isEqualTo("https://example.com/new");
        assertThat(result.getEventTypes()).contains("order.created");
        assertThat(result.isActive()).isFalse();
        verify(ssrfValidator).validate("https://example.com/new");
    }

    @Test
    void testUpdateSubscriptionNullFields() {
        // Cover all null branches in updateSubscription (nothing gets changed)
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "unchanged", "https://example.com/hook", "secret",
                "[\"test.event\"]");

        when(repository.findById("sub-1")).thenReturn(Optional.of(subscription));
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.updateSubscription(
                "sub-1", null, null, null, null);

        assertThat(result.getName()).isEqualTo("unchanged");
        assertThat(result.getCallbackUrl()).isEqualTo("https://example.com/hook");
        assertThat(result.getEventTypes()).isEqualTo("[\"test.event\"]");
        assertThat(result.isActive()).isTrue();
    }

    @Test
    void testCreateSubscriptionWithEmptyEventTypes() {
        // Cover serializeEventTypes with empty list (returns null)
        when(ssrfValidator.validate("https://example.com/hook")).thenReturn(List.of());
        when(hmacSigner.generateSecret()).thenReturn("generated-secret");
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.createSubscription(
                "my-hook", "https://example.com/hook", List.of());

        assertThat(result.getEventTypes()).isNull();
    }

    @Test
    void testGetSubscriptionDeletedReturnsNotFound() {
        // Covers the `s.getDeletedAt() == null` false branch
        WebhookSubscription subscription = new WebhookSubscription(
                TENANT_ID, "deleted-hook", "https://example.com/hook", "secret", null);
        subscription.setDeletedAt(java.time.Instant.now());

        when(repository.findById("sub-deleted")).thenReturn(Optional.of(subscription));

        assertThatThrownBy(() -> service.getSubscription("sub-deleted"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("sub-deleted");
    }

    @Test
    void testCreateSubscriptionWithNullEventTypes() {
        // Cover serializeEventTypes with null input (returns null)
        when(ssrfValidator.validate("https://example.com/hook")).thenReturn(List.of());
        when(hmacSigner.generateSecret()).thenReturn("generated-secret");
        when(repository.save(any(WebhookSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WebhookSubscription result = service.createSubscription(
                "my-hook", "https://example.com/hook", null);

        assertThat(result.getEventTypes()).isNull();
    }
}
