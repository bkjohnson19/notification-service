package dev.bkjohnson.notification.service;

import dev.bkjohnson.notification.model.entity.NotificationDelivery;
import dev.bkjohnson.notification.repository.NotificationDeliveryRepository;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for querying notification delivery records.
 */
@Service
public class DeliveryQueryService {

  private final NotificationDeliveryRepository repository;

  public DeliveryQueryService(NotificationDeliveryRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true)
  public NotificationDelivery getDelivery(String deliveryId) {
    return repository.findById(deliveryId)
        .filter(d -> d.getTenantId().equals(currentTenantId()))
        .orElseThrow(() -> new NotFoundException(
            "Delivery not found: " + deliveryId));
  }

  @Transactional(readOnly = true)
  public Page<NotificationDelivery> listDeliveries(
      Specification<NotificationDelivery> spec, Pageable pageable) {
    Specification<NotificationDelivery> tenantSpec = tenantScope();
    if (spec != null) {
      tenantSpec = tenantSpec.and(spec);
    }
    return repository.findAll(tenantSpec, pageable);
  }

  private String currentTenantId() {
    return RequestContext.current().tenantId();
  }

  private Specification<NotificationDelivery> tenantScope() {
    return (root, query, cb) ->
        cb.equal(root.get("tenantId"), currentTenantId());
  }
}
