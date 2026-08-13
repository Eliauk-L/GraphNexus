package com.graphnexus.api.mastery.controller;

import com.graphnexus.application.mastery.model.MasteryView;
import com.graphnexus.application.mastery.service.MasteryQueryService;
import com.graphnexus.application.mastery.service.MasteryUpdateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.graphnexus.common.exception.BusinessException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MasteryControllerTest {
    private MockMvc mockMvc;
    private MasteryQueryService queryService;
    private MasteryUpdateService updateService;

    @BeforeEach
    void setUp() {
        queryService = mock(MasteryQueryService.class);
        updateService = mock(MasteryUpdateService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                new MasteryController(queryService, updateService)).build();
    }

    @Test
    void getsCurrentMastery() throws Exception {
        when(queryService.current("S001", "数学")).thenReturn(List.of(
                new MasteryView("kp-axis", "对称轴", 0.65, 0.63, 3,
                        "E003", LocalDateTime.of(2026, 8, 1, 0, 0))));

        mockMvc.perform(get("/api/v1/mastery/S001").param("subject", "数学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].knowledgePointName").value("对称轴"))
                .andExpect(jsonPath("$.data[0].weight").value(0.65));
    }

    @Test
    void rebuildsMastery() throws Exception {
        when(updateService.rebuild("S001", "数学")).thenReturn(6);

        mockMvc.perform(post("/api/v1/mastery/rebuild")
                        .param("studentNo", "S001").param("subject", "数学"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eventCount").value(6));
        verify(updateService).rebuild("S001", "数学");
    }

    @Test
    void studentCannotReadAnotherStudentsMastery() {
        MasteryController controller = new MasteryController(queryService, updateService);
        var authentication = new UsernamePasswordAuthenticationToken("S001", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        assertThrows(BusinessException.class,
                () -> controller.current("S002", "数学", authentication));
    }
}
