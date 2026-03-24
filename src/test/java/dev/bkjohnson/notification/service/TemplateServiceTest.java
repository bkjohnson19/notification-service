package dev.bkjohnson.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.bkjohnson.api.context.RequestContext;
import dev.bkjohnson.api.exception.ConflictException;
import dev.bkjohnson.api.exception.NotFoundException;
import dev.bkjohnson.notification.model.entity.Channel;
import dev.bkjohnson.notification.model.entity.NotificationTemplate;
import dev.bkjohnson.notification.repository.NotificationTemplateRepository;
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
class TemplateServiceTest {

    private static final String TENANT_ID = "tenant-1";

    @Mock
    private NotificationTemplateRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        RequestContext.set(new RequestContext(
                "req-1", "trace-1", "v1", "client-1", "user-1",
                Set.of("ROLE"), Set.of("scope"), TENANT_ID,
                "127.0.0.1", null, Instant.now(), Map.of()));
        templateService = new TemplateService(repository, objectMapper);
    }

    @AfterEach
    void tearDown() {
        RequestContext.clear();
    }

    @Test
    void testListTemplates() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);
        Page<NotificationTemplate> page = new PageImpl<>(List.of(template));
        Pageable pageable = PageRequest.of(0, 10);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Page<NotificationTemplate> result = templateService.listTemplates(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("welcome");
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void testGetTemplate() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));

        NotificationTemplate result = templateService.getTemplate("tmpl-1");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("welcome");
    }

    @Test
    void testGetTemplateNotFound() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.getTemplate("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void testGetTemplateDifferentTenant() {
        NotificationTemplate template = new NotificationTemplate(
                "other-tenant", "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> templateService.getTemplate("tmpl-1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tmpl-1");
    }

    @Test
    void testCreateTemplate() {
        when(repository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "welcome"))
                .thenReturn(false);
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.createTemplate(
                "welcome", "EMAIL", "Hello", "<p>Welcome</p>", null);

        assertThat(result.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(result.getName()).isEqualTo("welcome");
        assertThat(result.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(result.getSubject()).isEqualTo("Hello");
        assertThat(result.getBodyTemplate()).isEqualTo("<p>Welcome</p>");

        verify(repository).save(any(NotificationTemplate.class));
    }

    @Test
    void testCreateTemplateDuplicateName() {
        when(repository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "welcome"))
                .thenReturn(true);

        assertThatThrownBy(() -> templateService.createTemplate(
                "welcome", "EMAIL", "Hello", "<p>Welcome</p>", null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("welcome");
    }

    @Test
    void testUpdateTemplate() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "old-name", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "new-name"))
                .thenReturn(false);
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.updateTemplate(
                "tmpl-1", "new-name", null, null, null);

        assertThat(result.getName()).isEqualTo("new-name");
        verify(repository).save(any(NotificationTemplate.class));
    }

    @Test
    void testUpdateTemplateNameConflict() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "old-name", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "taken-name"))
                .thenReturn(true);

        assertThatThrownBy(() -> templateService.updateTemplate(
                "tmpl-1", "taken-name", null, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("taken-name");
    }

    @Test
    void testDeleteTemplate() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        templateService.deleteTemplate("tmpl-1");

        ArgumentCaptor<NotificationTemplate> captor =
                ArgumentCaptor.forClass(NotificationTemplate.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    void testFindByName() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);

        when(repository.findByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "welcome"))
                .thenReturn(Optional.of(template));

        NotificationTemplate result = templateService.findByName("welcome");

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("welcome");
    }

    @Test
    void testFindByNameNotFound() {
        when(repository.findByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> templateService.findByName("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void testUpdateTemplateNoChanges() {
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "unchanged", Channel.EMAIL, "Hello", "<p>Body</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.updateTemplate(
                "tmpl-1", null, null, null, null);

        assertThat(result.getName()).isEqualTo("unchanged");
        assertThat(result.getSubject()).isEqualTo("Hello");
        assertThat(result.getBodyTemplate()).isEqualTo("<p>Body</p>");
        assertThat(result.getVariables()).isNull();
        verify(repository).save(template);
    }

    @Test
    void testUpdateTemplateWithSubjectAndBodyAndVariables() {
        // Covers the non-null branches for subject, bodyTemplate, and variables in updateTemplate
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "original", Channel.EMAIL, "Old Subject", "<p>Old body</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.updateTemplate(
                "tmpl-1", null, "New Subject", "<p>New body</p>",
                List.of(Map.of("name", "userName", "type", "string")));

        assertThat(result.getSubject()).isEqualTo("New Subject");
        assertThat(result.getBodyTemplate()).isEqualTo("<p>New body</p>");
        assertThat(result.getVariables()).isNotNull();
        assertThat(result.getVariables()).contains("userName");
        verify(repository).save(template);
    }

    @Test
    void testUpdateTemplateWithEmptyVariablesList() {
        // Covers serializeVariables with empty list (returns null)
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "empty-vars", Channel.EMAIL, "Subject", "<p>Body</p>",
                "[{\"name\":\"old\"}]");

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.updateTemplate(
                "tmpl-1", null, null, null, List.of());

        // Empty list serializes to null
        assertThat(result.getVariables()).isNull();
        verify(repository).save(template);
    }

    @Test
    void testCreateTemplateWithVariables() {
        when(repository.existsByTenantIdAndNameAndDeletedAtIsNull(TENANT_ID, "with-vars"))
                .thenReturn(false);
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.createTemplate(
                "with-vars", "EMAIL", "Hello", "<p>Welcome</p>",
                List.of(Map.of("name", "userName", "type", "string")));

        assertThat(result.getVariables()).isNotNull();
        assertThat(result.getVariables()).contains("userName");
        verify(repository).save(any(NotificationTemplate.class));
    }

    @Test
    void testGetTemplateDeletedReturnsNotFound() {
        // Covers the `t.getDeletedAt() == null` false branch
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "deleted-one", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);
        template.setDeletedAt(java.time.Instant.now());

        when(repository.findById("tmpl-deleted")).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> templateService.getTemplate("tmpl-deleted"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("tmpl-deleted");
    }

    @Test
    void testUpdateTemplateWithSameName() {
        // Covers the `name != null && name.equals(template.getName())` branch
        // where name is provided but matches the current name — no rename needed
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "same-name", Channel.EMAIL, "Hello", "<p>Body</p>", null);

        when(repository.findById("tmpl-1")).thenReturn(Optional.of(template));
        when(repository.save(any(NotificationTemplate.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationTemplate result = templateService.updateTemplate(
                "tmpl-1", "same-name", null, null, null);

        assertThat(result.getName()).isEqualTo("same-name");
        // Should NOT check for conflicts since name didn't change
        verify(repository, org.mockito.Mockito.never())
                .existsByTenantIdAndNameAndDeletedAtIsNull(any(), any());
    }

    @Test
    void testListTemplatesWithSpecification() {
        // Cover the branch where spec != null in listTemplates
        NotificationTemplate template = new NotificationTemplate(
                TENANT_ID, "filtered", Channel.EMAIL, "Hello", "<p>Welcome</p>", null);
        Page<NotificationTemplate> page = new PageImpl<>(List.of(template));
        Pageable pageable = PageRequest.of(0, 10);

        when(repository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        Specification<NotificationTemplate> spec = (root, query, cb) ->
                cb.equal(root.get("name"), "filtered");

        Page<NotificationTemplate> result = templateService.listTemplates(spec, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(repository).findAll(any(Specification.class), eq(pageable));
    }
}
