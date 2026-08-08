package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.ChatRequestVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class ConversationMemoryAdvisorFactoryService implements SpecAdvisorFactoryService{

    public List<Advisor> generateAdvisors(){
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
        return List.of(MessageChatMemoryAdvisor.builder(chatMemory).build());
    }

    @Override
    public List<Consumer<ChatClient.AdvisorSpec>> advise(ChatRequestVO request) {

        final String finalConversationId = Optional.ofNullable(request.conversationId()).orElse(String.valueOf(UUID.randomUUID()));

        return List.of(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId));
    }

}
