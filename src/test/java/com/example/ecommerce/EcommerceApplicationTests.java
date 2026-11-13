package com.example.ecommerce;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class EcommerceApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsWithHibernateValidateAndMainConfiguration() {
        // The context only starts if ddl-auto=validate passes against the Flyway schema.
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
        // Test override from classpath:/config/application.properties: a per-context random H2 database.
        assertThat(environment.getProperty("spring.datasource.url"))
                .startsWith("jdbc:h2:mem:").contains("MODE=MySQL").doesNotContain("mem:shop;");
    }

    @Test
    void flywayShouldApplySchemaAndSeedMigrations() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true and version in ('1', '2')",
                Integer.class);
        assertThat(applied).isEqualTo(2);

        assertThat(jdbc.queryForObject("select count(*) from category", Integer.class)).isEqualTo(4);
        Map<String, Object> laptop = jdbc.queryForMap(
                "select name, price, stock_quantity, active from product where sku = 'ELEC-001'");
        assertThat(laptop.get("name")).isEqualTo("Aurora 4K Laptop");
        assertThat(jdbc.queryForObject("select count(*) from product where active = true", Integer.class))
                .isGreaterThanOrEqualTo(16);
        for (String sku : new String[] {"ELEC-001", "BOOK-001", "HOME-LOW", "BOOK-OOS", "CLOTH-OFF", "ELEC-LAST",
                "HOME-PCT"}) {
            assertThat(jdbc.queryForObject("select count(*) from product where sku = ?", Integer.class, sku))
                    .as(sku).isEqualTo(1);
        }
    }

    @Test
    void shouldNotGenerateDefaultSecurityPassword(CapturedOutput output) {
        assertThat(context.getBeansOfType(UserDetailsService.class)).hasSize(1);
        UserDetailsService users = context.getBean(UserDetailsService.class);
        assertThat(users).isInstanceOf(InMemoryUserDetailsManager.class);
        assertThat(((InMemoryUserDetailsManager) users).userExists("user")).isFalse();
        assertThat(output.getAll()).doesNotContain("Using generated security password");
    }
}
