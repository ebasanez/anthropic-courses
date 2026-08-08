package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.ChatRequestVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;

import java.util.List;
import java.util.function.Consumer;

public interface SpecAdvisorFactoryService {

    List<Advisor> generateAdvisors();

    List<Consumer<ChatClient.AdvisorSpec>> advise(ChatRequestVO request);

}
