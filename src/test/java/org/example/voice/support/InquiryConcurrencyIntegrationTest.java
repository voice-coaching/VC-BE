package org.example.voice.support;

import jakarta.persistence.EntityManager;
import org.example.voice.support.application.SupportService;
import org.example.voice.support.domain.model.SupportData.InquiryDraft;
import org.example.voice.support.domain.model.SupportData.CreatedInquiry;
import org.example.voice.user.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InquiryConcurrencyIntegrationTest {
    @Autowired SupportService service;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;

    @Test void simultaneousRetriesCreateExactlyOneInquiry() throws Exception {
        var tx = new TransactionTemplate(manager);
        Long userId = tx.execute(status -> {
            User user = User.createLocal(UUID.randomUUID() + "@example.invalid", "encoded", "동시 문의", OffsetDateTime.now());
            em.persist(user); em.flush();
            return user.getId();
        });
        var draft = new InquiryDraft("ANALYSIS", "동시 접수", "중복 생성을 방지합니다.", null, null);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<CreatedInquiry> submit = () -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return service.create(userId, draft, "concurrent-key");
            };
            var one = pool.submit(submit);
            var two = pool.submit(submit);
            start.countDown();
            assertThat(one.get(20, TimeUnit.SECONDS)).isEqualTo(two.get(20, TimeUnit.SECONDS));
            Long count = tx.execute(status -> em.createQuery(
                    "select count(i) from Inquiry i where i.userId = :id", Long.class)
                    .setParameter("id", userId).getSingleResult());
            assertThat(count).isEqualTo(1);
        } finally {
            tx.executeWithoutResult(status -> {
                em.createQuery("delete from Inquiry i where i.userId = :id").setParameter("id", userId).executeUpdate();
                em.createQuery("delete from User u where u.id = :id").setParameter("id", userId).executeUpdate();
            });
        }
    }
}
