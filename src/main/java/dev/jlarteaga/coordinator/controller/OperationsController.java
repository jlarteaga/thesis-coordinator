package dev.jlarteaga.coordinator.controller;

import dev.jlarteaga.coordinator.controller.dto.OperationResponse;
import dev.jlarteaga.coordinator.messaging.datasetmanager.TextEventHandler;
import dev.jlarteaga.coordinator.messaging.nlpmanager.NlpCoordinator;
import dev.jlarteaga.coordinator.utils.ModelValidator;
import dev.jlarteaga.coordinator.webclient.DatasetManagerService;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.util.LinkedList;
import java.util.List;

@RestController
@RequestMapping("/operations")
public class OperationsController {

    private final DatasetManagerService datasetManagerService;
    private final TextEventHandler textEventHandler;
    private final NlpCoordinator nlpCoordinator;

    public OperationsController(
            DatasetManagerService datasetManagerService,
            TextEventHandler textEventHandler,
            NlpCoordinator nlpCoordinator
    ) {
        this.datasetManagerService = datasetManagerService;
        this.textEventHandler = textEventHandler;
        this.nlpCoordinator = nlpCoordinator;
    }

    @PostMapping("/process-text/texts/{uuid}")
    public Mono<OperationResponse> processTextById(
            @PathVariable("uuid") String uuid,
            @Header("x-silent") String silentHeader
    ) {
        return this.datasetManagerService.getText(uuid)
                .flatMap(text -> {
                    if (ModelValidator.hasValidTranslationText(text)) {
                        return this.textEventHandler.startProcessingText(text);
                    } else {
                        return Mono.error(new IllegalArgumentException("The text is not valid for processing"));
                    }
                })
                .map(success -> new OperationResponse(true, "The text is being processed"))
                .onErrorResume(error -> Mono.just(new OperationResponse(false, error.toString())));
    }

    @PostMapping("/process-text/questions/{uuid}")
    public Mono<OperationResponse> processQuestionTextById(
            @PathVariable("uuid") String uuid,
            @Header("x-silent") String silentHeader
    ) {
        return this.datasetManagerService.getTextByQuestion(uuid)
                .flatMap(text -> {
                    if (ModelValidator.hasValidTranslationText(text)) {
                        return this.textEventHandler.startProcessingText(text);
                    } else {
                        return Mono.error(new IllegalArgumentException("The text is not valid for processing"));
                    }
                })
                .map(success -> new OperationResponse(true, "The text is being processed"))
                .onErrorResume(error -> Mono.just(new OperationResponse(false, error.toString())));
    }

    @PostMapping("/process-text/student-answers/{uuid}")
    public Mono<OperationResponse> processStudentAnswerTextById(
            @PathVariable("uuid") String uuid,
            @Header("x-silent") String silentHeader
    ) {
        return this.datasetManagerService.getTextByStudentAnswer(uuid)
                .flatMap(text -> {
                    if (ModelValidator.hasValidTranslationText(text)) {
                        return this.textEventHandler.startProcessingText(text);
                    } else {
                        return Mono.error(new IllegalArgumentException("The text is not valid for processing"));
                    }
                })
                .map(success -> new OperationResponse(true, "The text is being processed"))
                .onErrorResume(error -> Mono.just(new OperationResponse(false, error.toString())));
    }

    @PostMapping("/similarity-matrices/texts/{uuid}")
    public Mono<OperationResponse> calculateSimilarityMatricesByText(
            @PathVariable("uuid") String uuid
    ) {
        return this.nlpCoordinator.processSimilarityMatrixByText(uuid);

    }

    @PostMapping("/process-text/questions")
    public Mono<OperationResponse> processAllQuestionsForDataset() {
        return this.datasetManagerService.getQuestions()
                .filter(question -> ModelValidator.hasValidTranslationText(question.getAnswer()))
                .flatMap(question -> this.datasetManagerService.getTextByQuestion(question.getUuid())
                        .flatMap(this.textEventHandler::startProcessingText)
                        .map(result -> Tuples.of(question.getUuid(), result))
                )
                .collectList()
                .map(tuples -> {
                    List<String> success = new LinkedList<>();
                    List<String> error = new LinkedList<>();
                    tuples.forEach(tuple -> {
                        if (tuple.getT2().getSuccess()) {
                            success.add(tuple.getT1());
                        } else {
                            error.add(tuple.getT1());
                        }
                    });
                    return new OperationResponse(
                            error.isEmpty(),
                            error.isEmpty()
                                    ? "Processed: [" + String.join(",", success) + "]"
                                    : "Failed: [" + String.join(",", error) + "]"
                    );
                });
    }

    @PostMapping("/process-text/questions/student-answers")
    public Mono<OperationResponse> processAllStudentAnswersForDataset() {
        return this.datasetManagerService.getQuestions()
                .flatMap(question -> this.datasetManagerService.getStudentAnswersByQuestion(question.getUuid())
                        .filter(studentAnswer -> ModelValidator.hasValidTranslationText(studentAnswer.getText()))
                        .flatMap(
                                studentAnswer -> this.datasetManagerService.getTextByStudentAnswer(studentAnswer.getUuid())
                                        .flatMap(this.textEventHandler::startProcessingText)
                                        .map(result -> Tuples.of(studentAnswer.getUuid(), result))
                        )
                )
                .collectList()
                .map(tuples -> {
                    List<String> success = new LinkedList<>();
                    List<String> error = new LinkedList<>();
                    tuples.forEach(tuple -> {
                        if (tuple.getT2().getSuccess()) {
                            success.add(tuple.getT1());
                        } else {
                            error.add(tuple.getT1());
                        }
                    });
                    return new OperationResponse(
                            error.isEmpty(),
                            error.isEmpty()
                                    ? "Processed: [" + String.join(",", success) + "]"
                                    : "Failed: [" + String.join(",", error) + "]"
                    );
                });
    }

    @PostMapping("/similarity-matrices/student-answers")
    public Mono<OperationResponse> processAllSimilarityMatricesForDataset() {
        return this.datasetManagerService.getQuestions()
                .filter(question -> ModelValidator.hasValidTranslationText(question.getAnswer()))
                .flatMap(question -> this.nlpCoordinator.processSimilarityMatrixByText(question.getAnswer().getUuid())
                        .map(result -> Tuples.of(question.getUuid(), result)))
                .collectList()
                .map(tuples -> {
                    List<String> success = new LinkedList<>();
                    List<String> error = new LinkedList<>();
                    tuples.forEach(tuple -> {
                        if (tuple.getT2().getSuccess()) {
                            success.add(tuple.getT1());
                        } else {
                            error.add(tuple.getT1());
                        }
                    });
                    return new OperationResponse(
                            error.isEmpty(),
                            error.isEmpty()
                                    ? "Processed: [" + String.join(",", success) + "]"
                                    : "Failed: [" + String.join(",", error) + "]"
                    );
                });
    }

    @PostMapping("/process-text/questions/{uuid}/student-answers")
    public Mono<OperationResponse> processStudentAnswerTextById(
            @PathVariable("uuid") String uuid
    ) {
        return this.datasetManagerService.getStudentAnswersByQuestion(uuid)
                .filter(studentAnswer -> ModelValidator.hasValidTranslationText(studentAnswer.getText()))
                .flatMap(studentAnswer -> this.datasetManagerService.getTextByStudentAnswer(studentAnswer.getUuid())
                        .flatMap(this.textEventHandler::startProcessingText)
                        .map(result -> Tuples.of(studentAnswer.getUuid(), result))
                )
                .collectList()
                .map(tuples -> {
                    List<String> success = new LinkedList<>();
                    List<String> error = new LinkedList<>();
                    tuples.forEach(tuple -> {
                        if (tuple.getT2().getSuccess()) {
                            success.add(tuple.getT1());
                        } else {
                            error.add(tuple.getT1());
                        }
                    });
                    return new OperationResponse(
                            error.isEmpty(),
                            error.isEmpty()
                                    ? "Processed: [" + String.join(",", success) + "]"
                                    : "Failed: [" + String.join(",", error) + "]"
                    );
                });
    }
}
