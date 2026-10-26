package com.idxintelligence.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BackendApplicationTests extends EmbeddedPostgresTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void migrationsCreateTables() {
		List<String> tables = jdbc.sql("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' ORDER BY table_name
				""").query(String.class).list();

		assertThat(tables).contains("stock", "daily_price", "flyway_schema_history");
	}

	@Test
	void adminImportRejectsMissingOrWrongToken() throws Exception {
		mockMvc.perform(post("/api/admin/import/prices")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/import/brokers")).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/admin/stats")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/import/prices").header("X-Admin-Token", "wrong"))
				.andExpect(status().isForbidden());
	}

	@Test
	void healthReportsDatabaseUp() throws Exception {
		mockMvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"))
				.andExpect(jsonPath("$.database").value("UP"));
	}

}
