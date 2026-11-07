package example.cafe.payment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.ListCrudRepository;

public interface PaymentRepository extends ListCrudRepository<Payment, Long> {

    List<Payment> findByBookedFalse();

    List<Payment> findByMonetaEntryReferenceIn(Collection<String> references);

    List<Payment> findByBillIdOrderByFirstSeenAt(Long billId);

    List<Payment> findByBillIdIsNullOrderByFirstSeenAtDesc();

    List<Payment> findAllByOrderByFirstSeenAtDesc();
}
