package dev.bkjohnson.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class DeliveryQueryServiceTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private NotificationDeliveryRepository repository;

    private DeliveryQueryService queryService;

    @BeforeEach
    void setUp() {
        RequestContext.set(new RequestContext(
                "req-1", "trace-1", "v1", "client-1", "user-1",
                Set.of("ROLE"), Set.of("scope"), TENANT_ID,
                "127.0.0.1", null, Instant.now(), Map.of()));
        queryService = new DeliveryQueryService(repository);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testGetDelivery() {
        NotificationDelivery delivery = new NotificationDelivery(
                TENANT_ID, Channel.EMAIL, "user@example.com");

        when(repository.findById("del-1")).thenReturn(Optional.of(delivery));

        NotificationDelivery result = queryService.getDelivery("del-1");

        assertThat(result).isNotNull();
        assertThat(result.getRecipientAddress()).isEqualTo("user@example.com");
    }

    @Test
    void testGetDeliveryNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryService.getDelivery("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void testGetDeliveryDifferentTenant() {
        NotificationDelivery delivery = new NotificationDelivery(
                "other-tenant", Channel.EMAIL, "user@example.com");

        when(repository.findById("del-1")).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> queryService.getDelivery("del-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("del-1");
    }

    @Test
    void testListDeliveries() {
        NotificationDelivery delivery = new NotificationDelivery(
                TENANT_ID, Channel.EMAIL, "user@example.com");
        Page<NotificationDelivery> page = new PageImpl<>(List.of(delivery));
        Pageable pageable = PageRequest.of(0, 20);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<NotificationDelivery> result = queryService.listDeliveries(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTenantId()).isEqualTo(TENANT_ID);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }
}
