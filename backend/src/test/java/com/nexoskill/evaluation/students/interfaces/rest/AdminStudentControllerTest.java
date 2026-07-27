package com.nexoskill.evaluation.students.interfaces.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nexoskill.evaluation.organizations.application.TenantContextResolver;
import com.nexoskill.evaluation.organizations.domain.model.TenantContext;
import com.nexoskill.evaluation.students.application.StudentService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminStudentControllerTest {
    @Test
    void shouldReturnOkAndAnEmptyPageWhenTheOrganizationHasNoStudents() throws Exception {
        StudentService service = mock(StudentService.class);
        TenantContextResolver tenantResolver = mock(TenantContextResolver.class);
        TenantContext tenant = TenantContext.organization(20L, "org-public", "ACME", true);
        when(tenantResolver.resolve(any())).thenReturn(tenant);
        when(service.search(any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenReturn(new StudentService.PageResult(List.of(), 0, 10, 0, 0));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminStudentController(service, tenantResolver))
                .build();

        mockMvc.perform(get("/api/v1/admin/students")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10));
    }
}
