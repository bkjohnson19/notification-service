package dev.bkjohnson.notification.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationTemplateTest {

    @Test
    void testConstructorSetsFields() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "welcome", Channel.EMAIL, "Hello", "<p>Welcome</p>", "[{\"name\":\"user\"}]");

        assertThat(template.getTenantId()).isEqualTo("tenant-1");
        assertThat(template.getName()).isEqualTo("welcome");
        assertThat(template.getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(template.getSubject()).isEqualTo("Hello");
        assertThat(template.getBodyTemplate()).isEqualTo("<p>Welcome</p>");
        assertThat(template.getVariables()).isEqualTo("[{\"name\":\"user\"}]");
    }

    @Test
    void testConstructorAllowsNullSubjectAndVariables() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "hook-template", Channel.WEBHOOK, null, "{}", null);

        assertThat(template.getSubject()).isNull();
        assertThat(template.getVariables()).isNull();
    }

    @Test
    void testConstructorRejectsNullTenantId() {
        assertThatThrownBy(() -> new NotificationTemplate(null, "name", Channel.EMAIL, "s", "b", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
    }

    @Test
    void testConstructorRejectsNullName() {
        assertThatThrownBy(() -> new NotificationTemplate("t", null, Channel.EMAIL, "s", "b", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("name");
    }

    @Test
    void testConstructorRejectsNullChannel() {
        assertThatThrownBy(() -> new NotificationTemplate("t", "n", null, "s", "b", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("channel");
    }

    @Test
    void testConstructorRejectsNullBodyTemplate() {
        assertThatThrownBy(() -> new NotificationTemplate("t", "n", Channel.EMAIL, "s", null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("bodyTemplate");
    }

    @Test
    void testSetTenantId() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "name", Channel.EMAIL, "s", "b", null);

        template.setTenantId("tenant-2");

        assertThat(template.getTenantId()).isEqualTo("tenant-2");
    }

    @Test
    void testSetName() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "old-name", Channel.EMAIL, "s", "b", null);

        template.setName("new-name");

        assertThat(template.getName()).isEqualTo("new-name");
    }

    @Test
    void testSetChannel() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "name", Channel.EMAIL, "s", "b", null);

        template.setChannel(Channel.WEBHOOK);

        assertThat(template.getChannel()).isEqualTo(Channel.WEBHOOK);
    }

    @Test
    void testSetSubject() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "name", Channel.EMAIL, "old", "b", null);

        template.setSubject("new subject");

        assertThat(template.getSubject()).isEqualTo("new subject");
    }

    @Test
    void testSetBodyTemplate() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "name", Channel.EMAIL, "s", "<p>old</p>", null);

        template.setBodyTemplate("<p>new</p>");

        assertThat(template.getBodyTemplate()).isEqualTo("<p>new</p>");
    }

    @Test
    void testSetVariables() {
        NotificationTemplate template = new NotificationTemplate(
                "tenant-1", "name", Channel.EMAIL, "s", "b", null);

        template.setVariables("[{\"name\":\"email\"}]");

        assertThat(template.getVariables()).isEqualTo("[{\"name\":\"email\"}]");
    }
}
