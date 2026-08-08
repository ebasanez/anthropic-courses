package com.bprojects.courses.claude.service;

import com.bprojects.courses.claude.ChatRequestVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@Profile("rag")
public class RagAdvisorFactoryService implements SpecAdvisorFactoryService {

    private final QuestionAnswerAdvisor ragAdvisor;

    // Inside the `rag` profile RagConfig always supplies this bean.
    public RagAdvisorFactoryService(QuestionAnswerAdvisor ragAdvisor){
        this.ragAdvisor = ragAdvisor;
    }

    /**
     * Deliberately empty: the retrieval advisor must NOT be a default advisor. Defaults are a
     * floor — {@code AdvisorSpec} can only append, never remove — so a default advisor would
     * retrieve on every request and an empty document selection could not switch RAG off.
     * {@link #advise(ChatRequestVO)} attaches it per request instead.
     */
    @Override
    public List<Advisor> generateAdvisors() {
        return List.of();
    }

    /**
     * Attach the retrieval advisor and scope it to the caller's document selection:
     *   - absent (null)          -> attach unscoped: retrieve across all documents
     *   - present with valid ids -> attach + docId filter
     *   - present but empty      -> attach nothing: no advisor in the chain, no vector-store query
     */
    @Override
    public List<Consumer<ChatClient.AdvisorSpec>> advise(ChatRequestVO request) {

        if (request.ragDocumentIds() == null) {
            return List.of(a -> a.advisors(ragAdvisor));
        }
        String inList = request.ragDocumentIds() .stream()
                .filter(id -> id != null && id.matches("[0-9a-fA-F-]{36}"))   // UUIDs only, block injection
                .map(id -> "'" + id + "'")
                .collect(Collectors.joining(", "));
        if (inList.isBlank()) {
            return List.of();  // empty selection -> advisor not attached -> RAG off, no DB query
        }
        // QuestionAnswerAdvisor reparses FILTER_EXPRESSION as filter text -> pass a DSL string.
        String docFilter = "docId in [" + inList + "]";
        return List.of(a -> a.advisors(ragAdvisor)
                .param(QuestionAnswerAdvisor.FILTER_EXPRESSION, docFilter));
    }

}
