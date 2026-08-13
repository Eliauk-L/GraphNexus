package com.graphnexus.application.agent.model;

public record AgentRequest(String question, String studentNo, String subject,
                           Integer dailyMinutes, Integer days) {}
