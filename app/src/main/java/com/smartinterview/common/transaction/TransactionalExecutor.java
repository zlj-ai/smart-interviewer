package com.smartinterview.common.transaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * 事务执行模板：以 Bean 方式包一层执行事务逻辑，
 * 解决同类内部调用 @Transactional 不生效的问题（Spring AOP 代理不拦截 this 调用）。
 * 典型场景：异步消费者在事务外完成 LLM 调用后，将落库与状态更新收敛为一个短事务。
 */
@Component
public class TransactionalExecutor {

  private final TransactionTemplate transactionTemplate;

  public TransactionalExecutor(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public <T> T inTransaction(Supplier<T> action) {
    return transactionTemplate.execute(status -> action.get());
  }

  public void inTransaction(Runnable action) {
    transactionTemplate.executeWithoutResult(status -> action.run());
  }
}
