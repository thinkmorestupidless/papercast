package com.example.application;

import akka.Done;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.StepName;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.domain.PodcastCreationState;
import com.typesafe.config.Config;

import static java.time.Duration.ofSeconds;

@Component(id = "podcast-creation")
public class PodcastCreationWorkflow extends Workflow<PodcastCreationState> {

    public record CreateCommand(String query) {}

    private final ComponentClient componentClient;
    private final PaperSearchService paperSearchService;

    public PodcastCreationWorkflow(ComponentClient componentClient, Config config) {
        this.componentClient = componentClient;
        this.paperSearchService = new PaperSearchService(config);
    }

    @Override
    public WorkflowSettings settings() {
        return WorkflowSettings.builder()
                .defaultStepTimeout(ofSeconds(30))
                .stepTimeout(PodcastCreationWorkflow::summarisePapersStep, ofSeconds(60))
                .stepTimeout(PodcastCreationWorkflow::generateScriptStep, ofSeconds(60))
                .defaultStepRecovery(maxRetries(1).failoverTo(PodcastCreationWorkflow::failedStep))
                .build();
    }

    // --- Command handlers ---

    public Effect<Done> create(CreateCommand command) {
        if (command.query() == null || command.query().isBlank()) {
            return effects().error("Query must not be blank");
        }
        return effects()
                .updateState(PodcastCreationState.initial(command.query()).withSearching())
                .transitionTo(PodcastCreationWorkflow::searchPapersStep)
                .thenReply(Done.getInstance());
    }

    public ReadOnlyEffect<PodcastCreationState> getStatus() {
        if (currentState() == null) {
            return effects().error("Podcast session not found");
        }
        return effects().reply(currentState());
    }

    // --- Steps ---

    @StepName("search-papers")
    private StepEffect searchPapersStep() {
        var papers = paperSearchService.searchPapers(currentState().query());
        if (papers.isEmpty()) {
            return stepEffects()
                    .updateState(currentState().withError("No papers found for query: " + currentState().query()))
                    .thenTransitionTo(PodcastCreationWorkflow::failedStep);
        }
        return stepEffects()
                .updateState(currentState().withPapers(papers))
                .thenTransitionTo(PodcastCreationWorkflow::summarisePapersStep);
    }

    @StepName("summarise-papers")
    private StepEffect summarisePapersStep() {
        var request = new PaperSummaryAgent.SummariseRequest(currentState().papers());
        var response = componentClient
                .forAgent()
                .inSession(sessionId())
                .method(PaperSummaryAgent::summarise)
                .invoke(request);

        if (response.summaries().isEmpty()) {
            return stepEffects()
                    .updateState(currentState().withError("Failed to generate summaries"))
                    .thenTransitionTo(PodcastCreationWorkflow::failedStep);
        }
        return stepEffects()
                .updateState(currentState().withSummaries(response.summaries()))
                .thenTransitionTo(PodcastCreationWorkflow::generateScriptStep);
    }

    @StepName("generate-script")
    private StepEffect generateScriptStep() {
        var request = new PodcastScriptAgent.ScriptRequest(
                currentState().query(), currentState().summaries());
        var script = componentClient
                .forAgent()
                .inSession(sessionId())
                .method(PodcastScriptAgent::generateScript)
                .invoke(request);

        if (script.segments().isEmpty()) {
            return stepEffects()
                    .updateState(currentState().withError("Failed to generate podcast script"))
                    .thenTransitionTo(PodcastCreationWorkflow::failedStep);
        }
        return stepEffects()
                .updateState(currentState().withScript(script))
                .thenEnd();
    }

    @StepName("failed")
    private StepEffect failedStep() {
        PodcastCreationState state = currentState();
        if (state.status() != PodcastCreationState.Status.FAILED) {
            state = state.withError("Step execution failed");
        }
        return stepEffects()
                .updateState(state)
                .thenEnd();
    }

    private String sessionId() {
        return commandContext().workflowId();
    }
}
