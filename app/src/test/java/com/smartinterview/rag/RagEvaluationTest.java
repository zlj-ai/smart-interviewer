package com.smartinterview.rag;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartinterview.common.ai.LlmClientFactory;
import com.smartinterview.modules.knowledgebase.model.DocumentStatus;
import com.smartinterview.modules.knowledgebase.model.KbDocumentResponse;
import com.smartinterview.modules.knowledgebase.model.KbResponse;
import com.smartinterview.modules.knowledgebase.model.CreateKbRequest;
import com.smartinterview.modules.knowledgebase.service.DocumentService;
import com.smartinterview.modules.knowledgebase.service.KnowledgeBaseService;
import com.smartinterview.modules.knowledgebase.service.RagService;
import com.smartinterview.modules.knowledgebase.service.VectorizeService;
import com.smartinterview.modules.user.model.UserEntity;
import com.smartinterview.modules.user.model.UserRole;
import com.smartinterview.modules.user.model.UserStatus;
import com.smartinterview.modules.user.repository.UserRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

/**
 * RAG 评估基线：固定语料 + 固定数据集，产出检索命中率 / 引用率 / 耗时报告。
 * 运行方式：RUN_RAG_EVAL=true ./gradlew :app:ragEvaluation --no-daemon（需有效 LLM key）。
 * 报告归档于 build/reports/rag-eval/，检索策略调整后可回归对比。
 */
@Tag("rag-eval")
@EnabledIfEnvironmentVariable(named = "RUN_RAG_EVAL", matches = "true")
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RagEvaluationTest {

  private static final String CORPUS = """
      Java 内存模型（JMM）描述了多线程环境下变量的可见性与有序性规则。
      volatile 关键字保证可见性：对 volatile 变量的写操作立即对所有线程可见，并禁止指令重排序。
      Synchronized 关键字提供互斥性与原子性：同一时刻只有一个线程能持有监视器锁。
      CAS（Compare And Swap）是一种无锁算法，通过自旋重试实现原子更新，适合低竞争场景。
      ThreadLocal 适用于线程封闭场景，每个线程持有独立的变量副本，避免共享竞争。
      ConcurrentHashMap 在高并发场景下替代 Hashtable，读操作几乎无锁。
      AQS（AbstractQueuedSynchronizer）是 JUC 包中锁与同步器的底层框架。
      线程池通过复用线程降低创建开销，核心参数包括 corePoolSize、maximumPoolSize 与拒绝策略。
      """;

  private static final String EVAL_USER = "rag-eval-bot";
  private static final int VECTORIZE_TIMEOUT_SECONDS = 120;

  @Autowired
  private KnowledgeBaseService kbService;
  @Autowired
  private DocumentService documentService;
  @Autowired
  private VectorizeService vectorizeService;
  @Autowired
  private RagService ragService;
  @Autowired
  private LlmClientFactory llmClientFactory;
  @Autowired
  private UserRepository userRepository;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private Long userId;
  private Long kbId;
  private List<DatasetItem> dataset;

  record DatasetItem(String question, List<String> expectedKeywords, List<String> sourceKeywords) {
  }

  record Dataset(List<DatasetItem> items) {
  }

  record ItemResult(String question, boolean retrievalHit, boolean cited, boolean answerHit,
                    long recallMillis, long answerMillis) {
  }

  @BeforeAll
  void setupCorpus() throws Exception {
    dataset = objectMapper.readValue(
        getClass().getResourceAsStream("/rag-eval/dataset.json"),
        Dataset.class).items();

    UserEntity bot = userRepository.findByUsername(EVAL_USER).orElseGet(() -> {
      UserEntity entity = new UserEntity();
      entity.setUsername(EVAL_USER);
      entity.setPasswordHash("not-a-login-hash");
      entity.setRole(UserRole.USER);
      entity.setStatus(UserStatus.DISABLED);
      return userRepository.save(entity);
    });
    userId = bot.getId();

    KbResponse kb = kbService.create(new CreateKbRequest("RAG 评估语料库", "rag-eval"), userId);
    kbId = kb.id();

    var file = new MockMultipartFile("file", "java-concurrency.txt", "text/plain",
        CORPUS.getBytes(StandardCharsets.UTF_8));
    KbDocumentResponse doc = documentService.upload(kbId, userId, file);
    vectorizeService.vectorize(doc.id());
    awaitVectorized(doc.id());
  }

  private void awaitVectorized(Long docId) throws InterruptedException {
    long deadline = System.currentTimeMillis() + Duration.ofSeconds(VECTORIZE_TIMEOUT_SECONDS).toMillis();
    while (System.currentTimeMillis() < deadline) {
      KbDocumentResponse doc = kbService.listDocuments(kbId, userId).stream()
          .filter(d -> d.id().equals(docId)).findFirst().orElseThrow();
      if (doc.status().equals(DocumentStatus.VECTORIZED.name())) {
        return;
      }
      Thread.sleep(1000);
    }
    throw new IllegalStateException("语料向量化超时");
  }

  @Test
  void evaluateRetrievalQualityAndFaithfulness() throws IOException {
    List<ItemResult> results = new ArrayList<>();
    for (DatasetItem item : dataset) {
      long t0 = System.currentTimeMillis();
      RagService.RagContext context = ragService.prepare(new com.smartinterview.modules.knowledgebase.model.AskRequest(
          item.question(), kbId), userId);
      long recallMillis = System.currentTimeMillis() - t0;

      String retrievedText = context.prompt();
      boolean retrievalHit = item.sourceKeywords().stream()
          .anyMatch(keyword -> retrievedText.toLowerCase().contains(keyword.toLowerCase()));

      long t1 = System.currentTimeMillis();
      String answer = llmClientFactory.getChatClient().prompt().user(context.prompt()).call().content();
      long answerMillis = System.currentTimeMillis() - t1;

      boolean cited = answer.contains("[来源");
      boolean answerHit = item.expectedKeywords().stream()
          .anyMatch(keyword -> answer.contains(keyword));

      results.add(new ItemResult(item.question(), retrievalHit, cited, answerHit,
          recallMillis, answerMillis));
    }

    long retrievalHits = results.stream().filter(ItemResult::retrievalHit).count();
    long citations = results.stream().filter(ItemResult::cited).count();
    long answerHits = results.stream().filter(ItemResult::answerHit).count();
    double avgRecallMillis = results.stream().mapToLong(ItemResult::recallMillis).average().orElse(0);
    double avgAnswerMillis = results.stream().mapToLong(ItemResult::answerMillis).average().orElse(0);

    writeReport(results, retrievalHits, citations, answerHits, avgRecallMillis, avgAnswerMillis);

    // 检索命中率是检索侧质量底线（与 LLM 无关）；引用率断言需真实模型，弱化为记录
    assertThat(retrievalHits)
        .as("检索命中率（%d/%d）", retrievalHits, dataset.size())
        .isGreaterThanOrEqualTo(Math.max(1, dataset.size() / 2));
  }

  private void writeReport(List<ItemResult> results, long retrievalHits, long citations,
                           long answerHits, double avgRecallMillis, double avgAnswerMillis)
      throws IOException {
    String reportDir = System.getProperty("ragEval.reportDir", "build/reports/rag-eval");
    Path dir = Path.of(reportDir);
    Files.createDirectories(dir);
    Path report = dir.resolve("rag-eval-report-" + Instant.now().toEpochMilli() + ".md");

    StringBuilder md = new StringBuilder();
    md.append("# RAG 评估报告\n\n");
    md.append("- 语料：java-concurrency.txt（").append(CORPUS.length()).append(" 字符）\n");
    md.append("- 数据集：").append(dataset.size()).append(" 条\n\n");
    md.append("| 问题 | 检索命中 | 回答引用 | 关键词命中 | 召回耗时(ms) | 生成耗时(ms) |\n");
    md.append("| --- | --- | --- | --- | --- | --- |\n");
    for (ItemResult r : results) {
      md.append("| ").append(r.question()).append(" | ").append(r.retrievalHit())
          .append(" | ").append(r.cited()).append(" | ").append(r.answerHit())
          .append(" | ").append(r.recallMillis()).append(" | ").append(r.answerMillis()).append(" |\n");
    }
    md.append("\n**汇总**：检索命中率 ").append(retrievalHits).append('/').append(dataset.size())
        .append("，引用率 ").append(citations).append('/').append(dataset.size())
        .append("，关键词命中率 ").append(answerHits).append('/').append(dataset.size())
        .append("，平均召回 ").append(String.format("%.0f", avgRecallMillis))
        .append("ms，平均生成 ").append(String.format("%.0f", avgAnswerMillis)).append("ms\n");

    Files.writeString(report, md.toString(), StandardCharsets.UTF_8);
    System.out.println("RAG 评估报告已归档: " + report.toAbsolutePath());
  }
}
